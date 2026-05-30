package page.runtime

object LspInstallers {

    private val registry: Map<String, () -> LspInstaller> = mapOf(
        "kotlin" to { KlsLspInstaller() },
        "rust" to { RustAnalyzerInstaller() },
        "c" to ::clangdInstaller,
        "cpp" to ::clangdInstaller,
        "markdown" to ::marksmanInstaller,
        "java" to ::jdtlsInstaller,
        "typescript" to ::typescriptLanguageServerInstaller,
        "javascript" to ::typescriptLanguageServerInstaller,
        "html" to ::vscodeHtmlInstaller,
        "css" to ::vscodeCssInstaller,
        "json" to ::vscodeJsonInstaller,
        "yaml" to ::yamlLanguageServerInstaller,
        "bash" to ::bashLanguageServerInstaller,
        "python" to ::pyrightInstaller,
        "dockerfile" to ::dockerLangserverInstaller,
        "vue" to ::vueLanguageServerInstaller,
        "svelte" to ::svelteLanguageServerInstaller,
        "php" to ::intelephenseInstaller,
        "sql" to ::sqlLanguageServerInstaller,
        "ruby" to ::rubyInstaller,
        "go" to ::goInstaller,
        "swift" to ::swiftInstaller,
        "dart" to ::dartInstaller,
        "flutter" to ::flutterInstaller,
        "jdk" to { JdkInstaller() },
        "node" to { NodeInstaller() },
        "python-runtime" to { PythonInstaller() },
        "cpp-toolchain" to { CppToolchainInstaller() },
        "mingw-toolchain" to { MingwInstaller() },
        "go-sdk" to { GoSdkInstaller() },
        "rust-runtime" to { RustToolchainInstaller() },
        "dotnet-runtime" to { DotnetSdkInstaller() },
        "windows-sdk" to { WindowsSdkInstaller() },
    )

    fun forId(languageId: String): LspInstaller? = registry[languageId]?.invoke()

    fun supports(languageId: String): Boolean = registry.containsKey(languageId)

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

    private fun jdtlsInstaller(): LspInstaller = JdtlsInstaller()

    private fun typescriptLanguageServerInstaller(): LspInstaller = NpmGlobalInstaller(
        NpmPackageDescriptor(
            languageId = "typescript",
            displayName = "typescript-language-server",
            packageName = "typescript-language-server",
            binaryName = "typescript-language-server",
            peerPackages = listOf("typescript"),
        ),
    )

    private fun vscodeHtmlInstaller(): LspInstaller = NpmGlobalInstaller(
        NpmPackageDescriptor(
            languageId = "html",
            displayName = "vscode-html-language-server",
            packageName = "vscode-langservers-extracted",
            binaryName = "vscode-html-language-server",
        ),
    )

    private fun vscodeCssInstaller(): LspInstaller = NpmGlobalInstaller(
        NpmPackageDescriptor(
            languageId = "css",
            displayName = "vscode-css-language-server",
            packageName = "vscode-langservers-extracted",
            binaryName = "vscode-css-language-server",
        ),
    )

    private fun vscodeJsonInstaller(): LspInstaller = NpmGlobalInstaller(
        NpmPackageDescriptor(
            languageId = "json",
            displayName = "vscode-json-language-server",
            packageName = "vscode-langservers-extracted",
            binaryName = "vscode-json-language-server",
        ),
    )

    private fun yamlLanguageServerInstaller(): LspInstaller = NpmGlobalInstaller(
        NpmPackageDescriptor(
            languageId = "yaml",
            displayName = "yaml-language-server",
            packageName = "yaml-language-server",
            binaryName = "yaml-language-server",
        ),
    )

    private fun bashLanguageServerInstaller(): LspInstaller = NpmGlobalInstaller(
        NpmPackageDescriptor(
            languageId = "bash",
            displayName = "bash-language-server",
            packageName = "bash-language-server",
            binaryName = "bash-language-server",
        ),
    )

    private fun pyrightInstaller(): LspInstaller = NpmGlobalInstaller(
        NpmPackageDescriptor(
            languageId = "python",
            displayName = "pyright",
            packageName = "pyright",
            binaryName = "pyright-langserver",
        ),
    )

    private fun dockerLangserverInstaller(): LspInstaller = NpmGlobalInstaller(
        NpmPackageDescriptor(
            languageId = "dockerfile",
            displayName = "dockerfile-language-server-nodejs",
            packageName = "dockerfile-language-server-nodejs",
            binaryName = "docker-langserver",
        ),
    )

    private fun vueLanguageServerInstaller(): LspInstaller = NpmGlobalInstaller(
        NpmPackageDescriptor(
            languageId = "vue",
            displayName = "@vue/language-server",
            packageName = "@vue/language-server",
            binaryName = "vue-language-server",
        ),
    )

    private fun svelteLanguageServerInstaller(): LspInstaller = NpmGlobalInstaller(
        NpmPackageDescriptor(
            languageId = "svelte",
            displayName = "svelte-language-server",
            packageName = "svelte-language-server",
            binaryName = "svelteserver",
        ),
    )

    private fun intelephenseInstaller(): LspInstaller = NpmGlobalInstaller(
        NpmPackageDescriptor(
            languageId = "php",
            displayName = "intelephense",
            packageName = "intelephense",
            binaryName = "intelephense",
        ),
    )

    private fun sqlLanguageServerInstaller(): LspInstaller = NpmGlobalInstaller(
        NpmPackageDescriptor(
            languageId = "sql",
            displayName = "sql-language-server",
            packageName = "sql-language-server",
            binaryName = "sql-language-server",
        ),
    )

    private fun rubyInstaller(): LspInstaller = when (LspInstaller.osKey()) {
        "windows", "macos" -> RubyBootstrapInstaller()
        else -> shellRubyInstaller()
    }

    internal fun shellRubyInstaller(): LspInstaller = ShellPackageInstaller(
        ShellPackageDescriptor(
            languageId = "ruby",
            displayName = "solargraph",
            managerName = "gem",
            managerInstallUrl = "https://www.ruby-lang.org/en/downloads/",
            binaryName = "solargraph",
            packageName = "solargraph",
            heavyInstall = LspInstaller.HeavyInstallEstimate(
                sizeEstimate = "~80 MB",
                durationEstimate = "~30 sec to 2 min",
                notes = "gem downloads solargraph and its dependencies. Duration varies with network conditions.",
            ),
            buildInstallCommand = { mgr, pkg, _ -> listOf(mgr, "install", "--no-document", pkg) },
        ),
    )

    private fun goInstaller(): LspInstaller = GoplsInstaller()

    private fun dartInstaller(): LspInstaller = DartSdkInstaller()

    private fun flutterInstaller(): LspInstaller = FlutterSdkInstaller()

    private fun swiftInstaller(): LspInstaller = SwiftToolchainInstaller()
}
