package page.runtime

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class WindowsSdkInstaller(
    private val processRunner: ProcessRunner = DefaultProcessRunner,
    private val archKey: String = ArchDetect.archKey(),
    private val isWindows: Boolean = LspInstaller.isWindows(),
    private val downloader: (url: String, target: Path, onProgress: (Long, Long) -> Unit) -> Unit = InstallerHttp::download,
    private val tarGzExtractor: (Path, Path, Int) -> Unit = { src, dst, flatten -> ArchiveExtractors.extractTarGz(src, dst, flatten) },
    private val defaultXwinVersion: String = DEFAULT_XWIN_VERSION,
) : LspInstaller {

    override val languageId: String = "windows-sdk"
    override val displayName: String = "Windows SDK (MSVC CRT + headers)"
    override val precheck: LspInstaller.Precheck =
        if (!isWindows) LspInstaller.Precheck.MissingTool(
            tool = "windows",
            installUrl = "https://developer.apple.com/swift/",
            message = "Windows SDK provisioning is only needed on Windows. Linux/macOS use the system SDK or Xcode.",
        ) else LspInstaller.Precheck.Ok
    override val heavyInstall: LspInstaller.HeavyInstallEstimate = LspInstaller.HeavyInstallEstimate(
        sizeEstimate = "~800 MB to 1.3 GB",
        durationEstimate = "~2 to 6 min",
        notes = "PAGE downloads the open-source xwin tool, then xwin fetches the MSVC CRT + Windows SDK " +
            "headers and import libraries directly from Microsoft into a per-user directory. The Microsoft " +
            "bits are not redistributable, so they are obtained on your machine rather than bundled.",
    )

    override fun isInstalled(): Boolean = currentInstalledVersion() != null

    override fun executable(): Path? =
        currentInstalledVersion()?.let { xwinExe(it) }?.takeIf { Files.exists(it) }

    override fun defaultVersion(): String? = defaultXwinVersion
    override fun installedVersion(): String? = currentInstalledVersion()

    override fun installedVersions(): List<String> {
        val base = installBase()
        if (!Files.isDirectory(base)) return emptyList()
        return runCatching {
            Files.list(base).use { stream ->
                stream
                    .filter { Files.isDirectory(it) && it.fileName.toString() != "CURRENT" }
                    .filter { splatComplete(splatOutput(it.fileName.toString())) }
                    .map { it.fileName.toString() }
                    .toList()
            }
        }.getOrDefault(emptyList())
    }

    override fun activeVersion(): String? = currentInstalledVersion()

    override fun applyVersion(version: String): Boolean {
        if (!splatComplete(splatOutput(version))) return false
        writePointer(version)
        return true
    }

    override fun availableVersions(): List<String> = listOf(defaultXwinVersion)

    override fun install(version: String?, onProgress: (LspInstaller.Progress) -> Unit) {
        if (!isWindows) {
            onProgress(LspInstaller.Progress.Failed(IOException("Windows SDK provisioning is only available on Windows")))
            return
        }
        val resolved = version?.takeIf { it.isNotBlank() } ?: defaultXwinVersion
        try {
            val splat = splatOutput(resolved)
            if (splatComplete(splat)) {
                writePointer(resolved)
                onProgress(LspInstaller.Progress.Done(xwinExe(resolved)))
                return
            }

            val xwin = ensureXwinTool(resolved, onProgress)
            runCatching { xwin.toFile().setExecutable(true, false) }

            if (Files.exists(splat)) ArchiveExtractors.deleteRecursively(splat)
            Files.createDirectories(splat)

            val command = listOf(
                xwin.toAbsolutePath().toString(),
                "--accept-license",
                "--arch", targetArch(),
                "splat",
                "--disable-symlinks",
                "--output", splat.toAbsolutePath().toString(),
            )
            onProgress(LspInstaller.Progress.Extracting("Fetching MSVC CRT + Windows SDK from Microsoft via xwin …"))
            onProgress(LspInstaller.Progress.CommandOutput("> " + command.joinToString(" ")))
            val exit = processRunner.runStreaming(command) { line ->
                onProgress(LspInstaller.Progress.CommandOutput(line))
            }
            if (exit != 0) throw IOException("xwin splat exited with code $exit")

            if (!splatComplete(splat)) {
                throw IOException(
                    "Windows SDK splat incomplete after xwin run: expected ${crtIncludeDir(splat)} and " +
                        "${sdkIncludeDir(splat, "um")} to exist.",
                )
            }
            writePointer(resolved)
            onProgress(LspInstaller.Progress.Done(xwinExe(resolved)))
        } catch (t: Throwable) {
            onProgress(LspInstaller.Progress.Failed(t))
        }
    }

    private fun ensureXwinTool(version: String, onProgress: (LspInstaller.Progress) -> Unit): Path {
        val exe = xwinExe(version)
        if (Files.exists(exe)) return exe
        val toolDir = toolDir(version)
        if (Files.exists(toolDir)) ArchiveExtractors.deleteRecursively(toolDir)
        Files.createDirectories(toolDir)

        val url = downloadUrl(version)
        val tmp = Files.createTempFile("page-xwin-", ".tar.gz")
        try {
            onProgress(LspInstaller.Progress.CommandOutput("> GET $url"))
            downloader(url, tmp) { read, total ->
                onProgress(LspInstaller.Progress.Downloading(read, total))
            }
            onProgress(LspInstaller.Progress.Extracting("Extracting xwin $version …"))
            tarGzExtractor(tmp, toolDir, 1)
        } catch (t: Throwable) {
            throw IOException("xwin tool download failed ($url): ${t.message}", t)
        } finally {
            runCatching { Files.deleteIfExists(tmp) }
        }

        if (!Files.exists(exe)) throw IOException("xwin.exe missing after extraction: $exe")
        return exe
    }

    internal fun downloadUrl(version: String): String =
        "https://github.com/Jake-Shadle/xwin/releases/download/$version/xwin-$version-${hostTriple()}.tar.gz"

    private fun hostTriple(): String = when (archKey) {
        "arm64" -> "aarch64-pc-windows-msvc"
        else -> "x86_64-pc-windows-msvc"
    }

    private fun targetArch(): String = when (archKey) {
        "arm64" -> "aarch64"
        else -> "x86_64"
    }

    internal fun includeDirs(splat: Path): List<Path> = listOf(
        crtIncludeDir(splat),
        sdkIncludeDir(splat, "ucrt"),
        sdkIncludeDir(splat, "shared"),
        sdkIncludeDir(splat, "um"),
    )

    internal fun libDirs(splat: Path): List<Path> {
        val arch = targetArch()
        return listOf(
            splat.resolve("crt").resolve("lib").resolve(arch),
            splat.resolve("sdk").resolve("lib").resolve("ucrt").resolve(arch),
            splat.resolve("sdk").resolve("lib").resolve("um").resolve(arch),
        )
    }

    internal fun buildEnv(splat: Path): Map<String, String> {
        val sep = File.pathSeparator
        return mapOf(
            "INCLUDE" to includeDirs(splat).joinToString(sep) { it.toAbsolutePath().toString() },
            "LIB" to libDirs(splat).joinToString(sep) { it.toAbsolutePath().toString() },
        )
    }

    fun envVars(): Map<String, String>? {
        val v = currentInstalledVersion() ?: return null
        return buildEnv(splatOutput(v))
    }

    fun ensureSwiftModulemaps(swiftSdkShare: Path): Boolean {
        val splat = currentInstalledVersion()?.let { splatOutput(it) } ?: return false
        if (!Files.isDirectory(splat)) return false
        return deployModulemaps(swiftSdkShare, splat)
    }

    internal fun deployModulemaps(share: Path, splat: Path): Boolean {
        if (!Files.isDirectory(share)) return false
        val ucrtInclude = sdkIncludeDir(splat, "ucrt")
        val patchUcrtMath = !Files.exists(ucrtInclude.resolve("corecrt_math.h"))
        val plan = listOf(
            Triple("ucrt.modulemap", ucrtInclude, patchUcrtMath),
            Triple("winsdk.modulemap", sdkIncludeDir(splat, "um"), false),
            Triple("vcruntime.modulemap", crtIncludeDir(splat), false),
        )
        var deployed = false
        for ((name, destDir, applyMathPatch) in plan) {
            val src = share.resolve(name)
            if (!Files.isRegularFile(src) || !Files.isDirectory(destDir)) continue
            val raw = runCatching { Files.readString(src) }.getOrNull() ?: continue
            val content = if (applyMathPatch) patchMissingCorecrtMath(raw) else raw
            val dest = destDir.resolve("module.modulemap")
            val current = runCatching { if (Files.exists(dest)) Files.readString(dest) else null }.getOrNull()
            if (current != content) runCatching { Files.writeString(dest, content) }
            deployed = true
        }
        return deployed
    }

    internal fun patchMissingCorecrtMath(modulemap: String): String =
        modulemap.replace(MISSING_CORECRT_MATH_SUBMODULE, "")

    fun splatHome(): Path? = currentInstalledVersion()?.let { splatOutput(it) }

    private fun crtIncludeDir(splat: Path): Path = splat.resolve("crt").resolve("include")

    private fun sdkIncludeDir(splat: Path, group: String): Path =
        splat.resolve("sdk").resolve("include").resolve(group)

    private fun splatComplete(splat: Path): Boolean =
        Files.isDirectory(crtIncludeDir(splat)) && Files.isDirectory(sdkIncludeDir(splat, "um"))

    fun xwinExe(version: String): Path = toolDir(version).resolve("xwin.exe")

    private fun toolDir(version: String): Path = installRoot(version).resolve("tool")

    private fun splatOutput(version: String): Path = installRoot(version).resolve("splat")

    fun installRoot(version: String): Path = installBase().resolve(sanitize(version))

    private fun installBase(): Path = LspInstaller.lspHome().resolve("windows-sdk")

    fun currentInstalledVersion(): String? {
        val pointer = installBase().resolve("CURRENT")
        val v = runCatching { Files.readString(pointer).trim().takeIf { it.isNotEmpty() } }.getOrNull() ?: return null
        return if (splatComplete(splatOutput(v))) v else null
    }

    private fun writePointer(version: String) {
        val pointer = installBase().resolve("CURRENT")
        Files.createDirectories(pointer.parent)
        Files.writeString(pointer, version)
    }

    private fun sanitize(version: String): String = version.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    companion object {
        const val DEFAULT_XWIN_VERSION = "0.9.0"

        internal val MISSING_CORECRT_MATH_SUBMODULE =
            Regex("""[ \t]*module[ \t]+math[ \t]*\{\s*header[ \t]+"corecrt_math\.h"\s*export[ \t]+\*\s*\}[ \t]*\r?\n?""")
    }
}
