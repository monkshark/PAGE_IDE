package page.app

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class GenericProcessBackend(
    override val languageId: String,
    override val displayName: String,
    private val runBinary: String,
    private val versionBinary: String = runBinary,
    private val versionArgs: List<String> = listOf("--version"),
    private val versionPattern: Regex = Regex("(\\d+\\.\\d+\\.\\d+)"),
    private val homeEnvVar: String? = null,
    private val installUrl: String = "",
) : LspInstaller {

    override val precheck: LspInstaller.Precheck
        get() = if (findBinary(runBinary) != null) LspInstaller.Precheck.Ok
        else LspInstaller.Precheck.MissingTool(
            runBinary, installUrl, "$displayName not found in PATH"
        )

    override fun isInstalled(): Boolean = findBinary(runBinary) != null

    override fun executable(): Path? = findBinary(runBinary)

    override fun activeVersion(): String? = runCatching {
        val bin = findBinary(versionBinary)?.toString() ?: versionBinary
        val cmd = listOf(bin) + versionArgs
        val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().use { it.readText().trim() }
        proc.waitFor()
        versionPattern.find(output)?.groupValues?.get(1)
    }.getOrNull()

    override fun install(version: String?, onProgress: (LspInstaller.Progress) -> Unit) {
        onProgress(
            LspInstaller.Progress.Failed(
                IllegalStateException("$displayName must be installed externally. Visit: $installUrl")
            )
        )
    }

    fun resolve(): Pair<String, Map<String, String>>? {
        val bin = findBinary(runBinary) ?: return null
        val env = mutableMapOf<String, String>()
        if (homeEnvVar != null) {
            val home = bin.parent?.parent
            if (home != null) env[homeEnvVar] = home.toAbsolutePath().toString()
        }
        return bin.toAbsolutePath().toString() to env
    }

    companion object {

        val DOTNET = GenericProcessBackend(
            languageId = "dotnet-runtime",
            displayName = ".NET SDK",
            runBinary = "dotnet",
            versionBinary = "dotnet",
            versionArgs = listOf("--version"),
            versionPattern = Regex("(\\d+\\.\\d+\\.\\d+)"),
            homeEnvVar = "DOTNET_ROOT",
            installUrl = "https://dotnet.microsoft.com/download",
        )

        private val ALL = listOf(DOTNET)

        fun forExtension(ext: String): GenericProcessBackend? = when (ext) {
            "cs", "fs", "vb" -> DOTNET
            else -> null
        }

        fun forId(id: String): GenericProcessBackend? = ALL.firstOrNull { it.languageId == id }

        fun findBinary(name: String): Path? {
            val exeName = if (LspInstaller.isWindows()) {
                if (name.endsWith(".exe")) name else "$name.exe"
            } else name
            val pathEnv = System.getenv("PATH") ?: return null
            for (dir in pathEnv.split(File.pathSeparator)) {
                val f = File(dir, exeName)
                if (f.exists() && f.canExecute()) return f.toPath()
            }
            return null
        }
    }
}
