package page.app

object LspInstallers {

    private val registry: Map<String, () -> LspInstaller> = mapOf(
        "kotlin" to { KlsLspInstaller() },
        "rust" to ::rustAnalyzerInstaller,
        "c" to ::clangdInstaller,
        "cpp" to ::clangdInstaller,
        "lua" to ::luaLanguageServerInstaller,
        "markdown" to ::marksmanInstaller,
        "zig" to ::zlsInstaller,
        "elixir" to ::elixirLsInstaller,
        "clojure" to ::clojureLspInstaller,
        "java" to ::jdtlsInstaller,
    )

    fun forId(languageId: String): LspInstaller? = registry[languageId]?.invoke()

    fun supports(languageId: String): Boolean = registry.containsKey(languageId)

    private fun rustAnalyzerInstaller(): LspInstaller = GitHubReleaseInstaller(
        GitHubReleaseDescriptor(
            languageId = "rust",
            displayName = "rust-analyzer",
            owner = "rust-lang",
            repo = "rust-analyzer",
            perOs = mapOf(
                "macos" to OsAsset(
                    url = "https://github.com/rust-lang/rust-analyzer/releases/download/{tag}/rust-analyzer-aarch64-apple-darwin.gz",
                    executableRelative = "rust-analyzer",
                    archiveType = ArchiveType.GZ_BINARY,
                ),
                "linux" to OsAsset(
                    url = "https://github.com/rust-lang/rust-analyzer/releases/download/{tag}/rust-analyzer-x86_64-unknown-linux-gnu.gz",
                    executableRelative = "rust-analyzer",
                    archiveType = ArchiveType.GZ_BINARY,
                ),
                "windows" to OsAsset(
                    url = "https://github.com/rust-lang/rust-analyzer/releases/download/{tag}/rust-analyzer-x86_64-pc-windows-msvc.gz",
                    executableRelative = "rust-analyzer.exe",
                    archiveType = ArchiveType.GZ_BINARY,
                ),
            ),
        ),
    )

    private fun clangdInstaller(): LspInstaller = GitHubReleaseInstaller(
        GitHubReleaseDescriptor(
            languageId = "clangd",
            displayName = "clangd",
            owner = "clangd",
            repo = "clangd",
            perOs = mapOf(
                "macos" to OsAsset(
                    url = "https://github.com/clangd/clangd/releases/download/{tag}/clangd-mac-{tag}.zip",
                    executableRelative = "bin/clangd",
                    archiveType = ArchiveType.ZIP,
                    flatten = 1,
                ),
                "linux" to OsAsset(
                    url = "https://github.com/clangd/clangd/releases/download/{tag}/clangd-linux-{tag}.zip",
                    executableRelative = "bin/clangd",
                    archiveType = ArchiveType.ZIP,
                    flatten = 1,
                ),
                "windows" to OsAsset(
                    url = "https://github.com/clangd/clangd/releases/download/{tag}/clangd-windows-{tag}.zip",
                    executableRelative = "bin/clangd.exe",
                    archiveType = ArchiveType.ZIP,
                    flatten = 1,
                ),
            ),
        ),
    )

    private fun luaLanguageServerInstaller(): LspInstaller = GitHubReleaseInstaller(
        GitHubReleaseDescriptor(
            languageId = "lua",
            displayName = "lua-language-server",
            owner = "LuaLS",
            repo = "lua-language-server",
            perOs = mapOf(
                "macos" to OsAsset(
                    url = "https://github.com/LuaLS/lua-language-server/releases/download/{tag}/lua-language-server-{versionNoV}-darwin-arm64.tar.gz",
                    executableRelative = "bin/lua-language-server",
                    archiveType = ArchiveType.TAR_GZ,
                ),
                "linux" to OsAsset(
                    url = "https://github.com/LuaLS/lua-language-server/releases/download/{tag}/lua-language-server-{versionNoV}-linux-x64.tar.gz",
                    executableRelative = "bin/lua-language-server",
                    archiveType = ArchiveType.TAR_GZ,
                ),
                "windows" to OsAsset(
                    url = "https://github.com/LuaLS/lua-language-server/releases/download/{tag}/lua-language-server-{versionNoV}-win32-x64.zip",
                    executableRelative = "bin/lua-language-server.exe",
                    archiveType = ArchiveType.ZIP,
                ),
            ),
        ),
    )

    private fun marksmanInstaller(): LspInstaller = GitHubReleaseInstaller(
        GitHubReleaseDescriptor(
            languageId = "markdown",
            displayName = "marksman",
            owner = "artempyanykh",
            repo = "marksman",
            perOs = mapOf(
                "macos" to OsAsset(
                    url = "https://github.com/artempyanykh/marksman/releases/download/{tag}/marksman-macos",
                    executableRelative = "marksman",
                    archiveType = ArchiveType.RAW_BINARY,
                ),
                "linux" to OsAsset(
                    url = "https://github.com/artempyanykh/marksman/releases/download/{tag}/marksman-linux-x64",
                    executableRelative = "marksman",
                    archiveType = ArchiveType.RAW_BINARY,
                ),
                "windows" to OsAsset(
                    url = "https://github.com/artempyanykh/marksman/releases/download/{tag}/marksman.exe",
                    executableRelative = "marksman.exe",
                    archiveType = ArchiveType.RAW_BINARY,
                ),
            ),
        ),
    )

    private fun zlsInstaller(): LspInstaller = GitHubReleaseInstaller(
        GitHubReleaseDescriptor(
            languageId = "zig",
            displayName = "zls",
            owner = "zigtools",
            repo = "zls",
            perOs = mapOf(
                "macos" to OsAsset(
                    url = "https://github.com/zigtools/zls/releases/download/{tag}/zls-aarch64-macos.tar.xz",
                    executableRelative = "zls",
                    archiveType = ArchiveType.RAW_BINARY,
                ),
                "linux" to OsAsset(
                    url = "https://github.com/zigtools/zls/releases/download/{tag}/zls-x86_64-linux.tar.xz",
                    executableRelative = "zls",
                    archiveType = ArchiveType.RAW_BINARY,
                ),
                "windows" to OsAsset(
                    url = "https://github.com/zigtools/zls/releases/download/{tag}/zls-x86_64-windows.zip",
                    executableRelative = "zls.exe",
                    archiveType = ArchiveType.ZIP,
                ),
            ),
        ),
    )

    private fun elixirLsInstaller(): LspInstaller = GitHubReleaseInstaller(
        GitHubReleaseDescriptor(
            languageId = "elixir",
            displayName = "elixir-ls",
            owner = "elixir-lsp",
            repo = "elixir-ls",
            perOs = mapOf(
                "macos" to OsAsset(
                    url = "https://github.com/elixir-lsp/elixir-ls/releases/download/{tag}/elixir-ls-{versionNoV}.zip",
                    executableRelative = "language_server.sh",
                    archiveType = ArchiveType.ZIP,
                ),
                "linux" to OsAsset(
                    url = "https://github.com/elixir-lsp/elixir-ls/releases/download/{tag}/elixir-ls-{versionNoV}.zip",
                    executableRelative = "language_server.sh",
                    archiveType = ArchiveType.ZIP,
                ),
                "windows" to OsAsset(
                    url = "https://github.com/elixir-lsp/elixir-ls/releases/download/{tag}/elixir-ls-{versionNoV}.zip",
                    executableRelative = "language_server.bat",
                    archiveType = ArchiveType.ZIP,
                ),
            ),
        ),
    )

    private fun clojureLspInstaller(): LspInstaller = GitHubReleaseInstaller(
        GitHubReleaseDescriptor(
            languageId = "clojure",
            displayName = "clojure-lsp",
            owner = "clojure-lsp",
            repo = "clojure-lsp",
            perOs = mapOf(
                "macos" to OsAsset(
                    url = "https://github.com/clojure-lsp/clojure-lsp/releases/download/{tag}/clojure-lsp-native-macos-amd64.zip",
                    executableRelative = "clojure-lsp",
                    archiveType = ArchiveType.ZIP,
                ),
                "linux" to OsAsset(
                    url = "https://github.com/clojure-lsp/clojure-lsp/releases/download/{tag}/clojure-lsp-native-linux-amd64.zip",
                    executableRelative = "clojure-lsp",
                    archiveType = ArchiveType.ZIP,
                ),
                "windows" to OsAsset(
                    url = "https://github.com/clojure-lsp/clojure-lsp/releases/download/{tag}/clojure-lsp-native-windows-amd64.zip",
                    executableRelative = "clojure-lsp.exe",
                    archiveType = ArchiveType.ZIP,
                ),
            ),
        ),
    )

    private fun jdtlsInstaller(): LspInstaller = JdtlsInstaller()
}
