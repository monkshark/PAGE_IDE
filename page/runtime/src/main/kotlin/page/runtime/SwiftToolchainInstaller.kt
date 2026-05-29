package page.runtime

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class SwiftToolchainInstaller(
    private val osKey: String = LspInstaller.osKey(),
    private val archKey: String = ArchDetect.archKey(),
    private val isWindows: Boolean = LspInstaller.isWindows(),
    private val downloader: (url: String, target: Path, onProgress: (Long, Long) -> Unit) -> Unit = InstallerHttp::download,
    private val tarGzExtractor: (Path, Path, Int) -> Unit = { src, dst, flatten -> ArchiveExtractors.extractTarGz(src, dst, flatten) },
    private val assetsRepo: String = DEFAULT_ASSETS_REPO,
    private val releaseTag: String = DEFAULT_RELEASE_TAG,
    private val defaultSwiftVersion: String = DEFAULT_SWIFT_VERSION,
    private val versionsFetcher: (String, String, String) -> List<String> = { owner, repo, tag ->
        GitHubReleases.listAssetNames(owner, repo, tag)
    },
) : LspInstaller {

    override val languageId: String = "swift"
    override val displayName: String = "Swift toolchain (sourcekit-lsp)"
    override val precheck: LspInstaller.Precheck = LspInstaller.Precheck.Ok
    override val heavyInstall: LspInstaller.HeavyInstallEstimate = LspInstaller.HeavyInstallEstimate(
        sizeEstimate = "~500 MB to 1.2 GB",
        durationEstimate = "~3 to 8 min",
        notes = "PAGE downloads the Swift toolchain from page-ide-assets. Includes swift, swiftc, sourcekit-lsp.",
    )

    override fun isInstalled(): Boolean = executable() != null

    override fun executable(): Path? {
        val ver = currentInstalledVersion() ?: return null
        return sourcekitLspBinary(ver).takeIf { Files.exists(it) }
    }

    override fun defaultVersion(): String? = defaultSwiftVersion
    override fun installedVersion(): String? = currentInstalledVersion()

    override fun installedVersions(): List<String> {
        val base = installBase()
        if (!Files.isDirectory(base)) return emptyList()
        return runCatching {
            Files.list(base).use { stream ->
                stream
                    .filter { Files.isDirectory(it) && it.fileName.toString() != "CURRENT" }
                    .filter { Files.exists(sourcekitLspBinary(it.fileName.toString())) }
                    .map { it.fileName.toString() }
                    .toList()
                    .sortedWith(VERSION_DESC)
            }
        }.getOrDefault(emptyList())
    }

    override fun activeVersion(): String? = currentInstalledVersion()

    override fun applyVersion(version: String): Boolean {
        if (!Files.exists(sourcekitLspBinary(version))) return false
        writePointer(version)
        return true
    }

    override fun availableVersions(): List<String> {
        val bundled = discoverBundleVersions()
        val installed = installedVersions()
        return (bundled + defaultSwiftVersion + installed).filter { it.isNotBlank() }.distinct().sortedWith(VERSION_DESC)
    }

    private fun discoverBundleVersions(): List<String> {
        val parts = assetsRepo.split('/')
        if (parts.size != 2) return emptyList()
        val pattern = assetNamePattern() ?: return emptyList()
        return runCatching {
            versionsFetcher(parts[0], parts[1], releaseTag)
                .mapNotNull { pattern.find(it)?.groupValues?.get(1) }
        }.getOrDefault(emptyList())
    }

    private fun assetNamePattern(): Regex? {
        val arch = assetArch()
        return Regex("^page-swift-toolchain-$osKey-$arch-(.+?)\\.tar\\.gz$")
    }

    override fun install(version: String?, onProgress: (LspInstaller.Progress) -> Unit) {
        try {
            val resolved = version?.takeIf { it.isNotBlank() } ?: defaultSwiftVersion
            val root = installRoot(resolved)
            if (Files.exists(sourcekitLspBinary(resolved))) {
                writePointer(resolved)
                onProgress(LspInstaller.Progress.Done(sourcekitLspBinary(resolved)))
                return
            }
            if (Files.exists(root)) ArchiveExtractors.deleteRecursively(root)
            Files.createDirectories(root)

            val url = downloadUrl(resolved)
            val tmp = Files.createTempFile("page-swift-", ".tar.gz")
            try {
                onProgress(LspInstaller.Progress.CommandOutput("> GET $url"))
                downloader(url, tmp) { read, total ->
                    onProgress(LspInstaller.Progress.Downloading(read, total))
                }
                onProgress(LspInstaller.Progress.Extracting("Extracting Swift toolchain $resolved …"))
                tarGzExtractor(tmp, root, 0)
            } catch (t: Throwable) {
                throw IOException("Swift toolchain download failed ($url): ${t.message}", t)
            } finally {
                runCatching { Files.deleteIfExists(tmp) }
            }

            val bin = sourcekitLspBinary(resolved)
            if (!Files.exists(bin)) {
                throw IOException("sourcekit-lsp not found after extraction: $bin")
            }
            runCatching { bin.toFile().setExecutable(true, false) }
            writePointer(resolved)
            onProgress(LspInstaller.Progress.Done(bin))
        } catch (t: Throwable) {
            runCatching { ArchiveExtractors.deleteRecursively(installRoot(version?.takeIf { it.isNotBlank() } ?: defaultSwiftVersion)) }
            onProgress(LspInstaller.Progress.Failed(t))
        }
    }

    internal fun downloadUrl(version: String): String {
        val arch = assetArch()
        return "https://github.com/$assetsRepo/releases/download/$releaseTag/page-swift-toolchain-$osKey-$arch-$version.tar.gz"
    }

    private fun assetArch(): String = when (archKey) {
        "arm64" -> "aarch64"
        "amd64" -> "x86_64"
        else -> archKey
    }

    fun sourcekitLspBinary(version: String): Path {
        val name = if (isWindows) "sourcekit-lsp.exe" else "sourcekit-lsp"
        return installRoot(version).resolve("usr").resolve("bin").resolve(name)
    }

    fun installRoot(version: String): Path = installBase().resolve(sanitize(version))

    private fun installBase(): Path = LspInstaller.lspHome().resolve("swift")

    fun currentInstalledVersion(): String? {
        val pointer = installBase().resolve("CURRENT")
        val v = runCatching { Files.readString(pointer).trim().takeIf { it.isNotEmpty() } }.getOrNull() ?: return null
        return if (Files.exists(sourcekitLspBinary(v))) v else null
    }

    private fun writePointer(version: String) {
        val pointer = installBase().resolve("CURRENT")
        Files.createDirectories(pointer.parent)
        Files.writeString(pointer, version)
    }

    private fun sanitize(version: String): String = version.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    companion object {
        const val DEFAULT_ASSETS_REPO = "monkshark/page-ide-assets"
        const val DEFAULT_RELEASE_TAG = "swift-toolchain-bundle"
        const val DEFAULT_SWIFT_VERSION = "6.0.3"

        internal val VERSION_DESC: Comparator<String> = Comparator { a, b ->
            val pa = a.split('.').mapNotNull { it.toIntOrNull() }
            val pb = b.split('.').mapNotNull { it.toIntOrNull() }
            val len = maxOf(pa.size, pb.size)
            for (i in 0 until len) {
                val va = pa.getOrElse(i) { 0 }
                val vb = pb.getOrElse(i) { 0 }
                if (va != vb) return@Comparator vb.compareTo(va)
            }
            0
        }
    }
}
