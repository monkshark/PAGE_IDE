package page.app

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class JdtlsInstaller(
    private val owner: String = "eclipse-jdtls",
    private val repo: String = "eclipse.jdt.ls",
    private val defaultTag: String? = null,
) : LspInstaller {

    override val languageId: String = "jdtls"
    override val displayName: String = "Eclipse JDT Language Server"
    override val precheck: LspInstaller.Precheck =
        if (hasJavaOnPath()) LspInstaller.Precheck.Ok
        else LspInstaller.Precheck.MissingTool(
            tool = "java",
            installUrl = "https://adoptium.net/",
            message = "JDT Language Server requires Java 17+ to run. Install a JDK then retry.",
        )

    override fun isInstalled(): Boolean = executable() != null

    override fun executable(): Path? {
        val root = installRoot()
        if (!Files.isDirectory(root)) return null
        val launcher = root.resolve(launcherName())
        return launcher.takeIf { Files.exists(it) }
    }

    override fun defaultVersion(): String? = defaultTag

    override fun installedVersion(): String? = currentInstalledVersion()

    override fun availableVersions(): List<String> = runCatching {
        GitHubReleases.listReleases(owner, repo).map { it.tagName }
    }.getOrDefault(emptyList())

    override fun install(version: String?, onProgress: (LspInstaller.Progress) -> Unit) {
        try {
            val tag = version
                ?: defaultTag
                ?: GitHubReleases.latestTag(owner, repo)
                ?: throw IOException("cannot resolve JDT-LS release tag")
            val assetUrl = GitHubReleases.fetchAssetUrl(owner, repo, tag, ".tar.gz")
                ?: throw IOException("no .tar.gz asset on $tag")
            val target = installRoot(tag)
            val tmp = Files.createTempFile("jdtls-", ".tar.gz")
            try {
                downloadWithProgress(assetUrl, tmp, onProgress)
                onProgress(LspInstaller.Progress.Extracting())
                ArchiveExtractors.extractTarGz(tmp, target, flatten = 0)
                writeLauncher(target)
                writePointer(tag)
                val exe = target.resolve(launcherName())
                if (!Files.exists(exe)) throw IOException("launcher missing at $exe")
                runCatching { exe.toFile().setExecutable(true, false) }
                onProgress(LspInstaller.Progress.Done(exe))
            } finally {
                runCatching { Files.deleteIfExists(tmp) }
            }
        } catch (t: Throwable) {
            onProgress(LspInstaller.Progress.Failed(t))
        }
    }

    fun installRoot(version: String = currentInstalledVersion() ?: defaultTag ?: "latest"): Path =
        LspInstaller.lspHome().resolve(languageId).resolve(sanitize(version))

    private fun currentInstalledVersion(): String? {
        val pointer = LspInstaller.lspHome().resolve(languageId).resolve("CURRENT")
        return runCatching { Files.readString(pointer).trim().takeIf { it.isNotEmpty() } }.getOrNull()
    }

    private fun writePointer(version: String) {
        val pointer = LspInstaller.lspHome().resolve(languageId).resolve("CURRENT")
        Files.createDirectories(pointer.parent)
        Files.writeString(pointer, version)
    }

    private fun launcherName(): String = if (LspInstaller.isWindows()) "jdtls.bat" else "jdtls.sh"

    private fun writeLauncher(target: Path) {
        val configDir = configDirFor(target)
            ?: throw IOException("config dir not found under $target")
        val launcherJar = findLauncherJar(target)
            ?: throw IOException("equinox launcher jar not found under $target/plugins")
        val launcher = target.resolve(launcherName())
        val data = target.resolve("workspace")
        Files.createDirectories(data)
        val text = if (LspInstaller.isWindows()) {
            buildString {
                append("@echo off\r\n")
                append("setlocal\r\n")
                append("set JDTLS_HOME=%~dp0\r\n")
                append("java ^\r\n")
                append("  -Declipse.application=org.eclipse.jdt.ls.core.id1 ^\r\n")
                append("  -Dosgi.bundles.defaultStartLevel=4 ^\r\n")
                append("  -Declipse.product=org.eclipse.jdt.ls.core.product ^\r\n")
                append("  -Dlog.level=ALL ^\r\n")
                append("  -Xms1g ^\r\n")
                append("  --add-modules=ALL-SYSTEM ^\r\n")
                append("  --add-opens java.base/java.util=ALL-UNNAMED ^\r\n")
                append("  --add-opens java.base/java.lang=ALL-UNNAMED ^\r\n")
                append("  -jar \"%JDTLS_HOME%${target.relativize(launcherJar)}\" ^\r\n")
                append("  -configuration \"%JDTLS_HOME%${target.relativize(configDir)}\" ^\r\n")
                append("  -data \"%JDTLS_HOME%workspace\" %*\r\n")
            }
        } else {
            buildString {
                append("#!/usr/bin/env bash\n")
                append("set -e\n")
                append("HERE=\"$(cd \"$(dirname \"\${BASH_SOURCE[0]}\")\" && pwd)\"\n")
                append("exec java \\\n")
                append("  -Declipse.application=org.eclipse.jdt.ls.core.id1 \\\n")
                append("  -Dosgi.bundles.defaultStartLevel=4 \\\n")
                append("  -Declipse.product=org.eclipse.jdt.ls.core.product \\\n")
                append("  -Dlog.level=ALL \\\n")
                append("  -Xms1g \\\n")
                append("  --add-modules=ALL-SYSTEM \\\n")
                append("  --add-opens java.base/java.util=ALL-UNNAMED \\\n")
                append("  --add-opens java.base/java.lang=ALL-UNNAMED \\\n")
                append("  -jar \"\$HERE/${target.relativize(launcherJar).toString().replace('\\', '/')}\" \\\n")
                append("  -configuration \"\$HERE/${target.relativize(configDir).toString().replace('\\', '/')}\" \\\n")
                append("  -data \"\$HERE/workspace\" \"\$@\"\n")
            }
        }
        Files.writeString(launcher, text)
        runCatching { launcher.toFile().setExecutable(true, false) }
    }

    private fun configDirFor(target: Path): Path? {
        val candidate = when (LspInstaller.osKey()) {
            "macos" -> target.resolve("config_mac")
            "windows" -> target.resolve("config_win")
            else -> target.resolve("config_linux")
        }
        return candidate.takeIf { Files.isDirectory(it) }
    }

    private fun findLauncherJar(target: Path): Path? {
        val plugins = target.resolve("plugins")
        if (!Files.isDirectory(plugins)) return null
        Files.list(plugins).use { stream ->
            return stream
                .filter { it.fileName.toString().startsWith("org.eclipse.equinox.launcher_") }
                .filter { it.fileName.toString().endsWith(".jar") }
                .findFirst()
                .orElse(null)
        }
    }

    private fun sanitize(version: String): String =
        version.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    private fun hasJavaOnPath(): Boolean {
        val envPath = System.getenv("PATH") ?: return false
        val candidates = if (LspInstaller.isWindows()) listOf("java.exe") else listOf("java")
        for (segment in envPath.split(java.io.File.pathSeparatorChar)) {
            if (segment.isBlank()) continue
            val dir = runCatching { java.nio.file.Paths.get(segment) }.getOrNull() ?: continue
            for (name in candidates) {
                if (Files.exists(dir.resolve(name))) return true
            }
        }
        return false
    }

    private fun downloadWithProgress(url: String, target: Path, onProgress: (LspInstaller.Progress) -> Unit) {
        InstallerHttp.download(url, target) { read, total ->
            onProgress(LspInstaller.Progress.Downloading(read, total))
        }
    }
}
