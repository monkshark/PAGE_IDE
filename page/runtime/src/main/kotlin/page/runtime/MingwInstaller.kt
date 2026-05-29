package page.runtime

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class MingwInstaller(
    private val osKey: String = LspInstaller.osKey(),
    private val archKey: String = ArchDetect.archKey(),
    private val isWindows: Boolean = LspInstaller.isWindows(),
    private val downloader: (url: String, target: Path, onProgress: (Long, Long) -> Unit) -> Unit = InstallerHttp::download,
    private val tarGzExtractor: (Path, Path, Int) -> Unit = { src, dst, flatten -> ArchiveExtractors.extractTarGz(src, dst, flatten) },
    private val assetsRepo: String = DEFAULT_ASSETS_REPO,
    private val releaseTag: String = DEFAULT_RELEASE_TAG,
    private val versionsFetcher: (String, String, String) -> List<String> = { owner, repo, tag ->
        GitHubReleases.listAssetNames(owner, repo, tag)
    },
) : LspInstaller {

    override val languageId: String = "mingw-toolchain"
    override val displayName: String = "MinGW-w64 (UCRT64) toolchain"
    override val precheck: LspInstaller.Precheck =
        if (!isWindows) LspInstaller.Precheck.MissingTool(
            tool = "windows",
            installUrl = "https://www.gnu.org/software/libc/",
            message = "MinGW is only needed on Windows. Linux/macOS provide libc system-wide.",
        ) else LspInstaller.Precheck.Ok
    override val heavyInstall: LspInstaller.HeavyInstallEstimate? = LspInstaller.HeavyInstallEstimate(
        sizeEstimate = "~250 MB",
        durationEstimate = "~1 to 3 min",
        notes = "Portable winlibs MinGW-w64 with gcc/g++/gdb. Provides libc headers (stdio.h, etc.) clangd needs on Windows.",
    )

    override fun isInstalled(): Boolean = executable() != null

    override fun executable(): Path? {
        val ver = currentInstalledVersion() ?: return null
        return gccBinary(ver).takeIf { Files.exists(it) }
    }

    override fun defaultVersion(): String? = null
    override fun installedVersion(): String? = currentInstalledVersion()

    override fun installedVersions(): List<String> {
        val base = installBase()
        if (!Files.isDirectory(base)) return emptyList()
        return runCatching {
            Files.list(base).use { stream ->
                stream
                    .filter { Files.isDirectory(it) && it.fileName.toString() != "CURRENT" }
                    .filter { Files.exists(gccBinary(it.fileName.toString())) }
                    .map { it.fileName.toString() }
                    .toList()
            }
        }.getOrDefault(emptyList())
    }

    override fun activeVersion(): String? = currentInstalledVersion()

    override fun applyVersion(version: String): Boolean {
        if (!Files.exists(gccBinary(version))) return false
        writePointer(version)
        return true
    }

    override fun availableVersions(): List<String> {
        val parts = assetsRepo.split('/')
        if (parts.size != 2) return emptyList()
        val pattern = Regex("^page-cpp-mingw-windows-${assetArch()}-(.+?)\\.tar\\.gz$")
        return runCatching {
            versionsFetcher(parts[0], parts[1], releaseTag)
                .mapNotNull { pattern.find(it)?.groupValues?.get(1) }
        }.getOrDefault(emptyList())
    }

    override fun install(version: String?, onProgress: (LspInstaller.Progress) -> Unit) {
        if (!isWindows) {
            onProgress(LspInstaller.Progress.Failed(IOException("MinGW is only available on Windows")))
            return
        }
        try {
            val resolved = version?.takeIf { it.isNotBlank() }
                ?: availableVersions().firstOrNull()
                ?: throw IOException("No MinGW versions available in $assetsRepo / $releaseTag")
            val root = installRoot(resolved)
            if (Files.exists(gccBinary(resolved))) {
                writePointer(resolved)
                onProgress(LspInstaller.Progress.Done(gccBinary(resolved)))
                return
            }
            if (Files.exists(root)) ArchiveExtractors.deleteRecursively(root)
            Files.createDirectories(root)

            val url = downloadUrl(resolved)
            val tmp = Files.createTempFile("page-mingw-", ".tar.gz")
            try {
                onProgress(LspInstaller.Progress.CommandOutput("> GET $url"))
                downloader(url, tmp) { read, total ->
                    onProgress(LspInstaller.Progress.Downloading(read, total))
                }
                onProgress(LspInstaller.Progress.Extracting("Extracting MinGW $resolved …"))
                tarGzExtractor(tmp, root, 0)
            } catch (t: Throwable) {
                throw IOException("MinGW download failed ($url): ${t.message}", t)
            } finally {
                runCatching { Files.deleteIfExists(tmp) }
            }

            if (!Files.exists(gccBinary(resolved))) {
                throw IOException("gcc.exe missing after extraction: ${gccBinary(resolved)}")
            }
            writePointer(resolved)
            onProgress(LspInstaller.Progress.Done(gccBinary(resolved)))
        } catch (t: Throwable) {
            runCatching {
                val v = version?.takeIf { it.isNotBlank() }
                if (v != null) ArchiveExtractors.deleteRecursively(installRoot(v))
            }
            onProgress(LspInstaller.Progress.Failed(t))
        }
    }

    internal fun downloadUrl(version: String): String =
        "https://github.com/$assetsRepo/releases/download/$releaseTag/page-cpp-mingw-windows-${assetArch()}-$version.tar.gz"

    private fun assetArch(): String = when (archKey) {
        "amd64" -> "x86_64"
        "arm64" -> "aarch64"
        else -> archKey
    }

    fun gccBinary(version: String): Path =
        installRoot(version).resolve("bin").resolve("gcc.exe")

    /** Header search root — winlibs uses `include/` directly under install root. */
    fun includeRoot(): Path? {
        val v = currentInstalledVersion() ?: return null
        return installRoot(v).resolve("include")
    }

    fun installRoot(version: String): Path = installBase().resolve(sanitize(version))

    private fun installBase(): Path = LspInstaller.lspHome().resolve("mingw")

    fun currentInstalledVersion(): String? {
        val pointer = installBase().resolve("CURRENT")
        val v = runCatching { Files.readString(pointer).trim().takeIf { it.isNotEmpty() } }.getOrNull() ?: return null
        return if (Files.exists(gccBinary(v))) v else null
    }

    private fun writePointer(version: String) {
        val pointer = installBase().resolve("CURRENT")
        Files.createDirectories(pointer.parent)
        Files.writeString(pointer, version)
    }

    private fun sanitize(version: String): String = version.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    companion object {
        const val DEFAULT_ASSETS_REPO = "monkshark/page-ide-assets"
        const val DEFAULT_RELEASE_TAG = "mingw-bundle"
    }
}
