package page.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LspInstallersRegistryTest {

    @Test
    fun supportsAllStep1Languages() {
        for (id in listOf("kotlin", "rust", "c", "cpp", "lua", "markdown", "zig", "elixir", "clojure", "java")) {
            assertTrue(LspInstallers.supports(id), "expected support for $id")
            assertNotNull(LspInstallers.forId(id), "expected installer for $id")
        }
    }

    @Test
    fun unsupportedLanguageReturnsNull() {
        assertNull(LspInstallers.forId("brainfuck"))
        assertNull(LspInstallers.forId(""))
    }

    @Test
    fun kotlinInstallerKeepsLegacyLanguageId() {
        val installer = LspInstallers.forId("kotlin")
        assertNotNull(installer)
        assertEquals("kotlin", installer.languageId)
        assertEquals("kotlin-language-server", installer.displayName)
    }

    @Test
    fun javaInstallerIsJdtls() {
        val installer = LspInstallers.forId("java")
        assertNotNull(installer)
        assertTrue(installer is JdtlsInstaller, "expected JdtlsInstaller, got ${installer::class}")
    }

    @Test
    fun gitHubReleaseInstallersHaveAllThreeOsBlocks() {
        val ids = listOf("rust", "c", "cpp", "lua", "markdown", "zig", "elixir", "clojure")
        for (id in ids) {
            val installer = LspInstallers.forId(id)
            assertTrue(installer is GitHubReleaseInstaller, "$id should be GitHubReleaseInstaller")
            val perOs = installer.descriptor.perOs
            assertTrue(perOs.containsKey("macos"), "$id missing macos descriptor")
            assertTrue(perOs.containsKey("linux"), "$id missing linux descriptor")
            assertTrue(perOs.containsKey("windows"), "$id missing windows descriptor")
        }
    }

    @Test
    fun gitHubReleaseInstallersOwnerRepoNonEmpty() {
        val ids = listOf("rust", "c", "cpp", "lua", "markdown", "zig", "elixir", "clojure")
        for (id in ids) {
            val installer = LspInstallers.forId(id) as GitHubReleaseInstaller
            assertTrue(installer.descriptor.owner.isNotBlank(), "$id missing owner")
            assertTrue(installer.descriptor.repo.isNotBlank(), "$id missing repo")
        }
    }

    @Test
    fun kotlinAdapterReportsExpectedExecutableShape() {
        val installer = LspInstallers.forId("kotlin")
        assertNotNull(installer)
        val version = installer.defaultVersion()
        assertEquals(KlsLspInstaller.labelOf(KlsInstaller.VERSION, KlsLspInstaller.FORK), version)
    }

    @Test
    fun kotlinAdapterParsesForkAndUpstreamLabels() {
        assertEquals("1.3.13-page-1" to "fork", KlsLspInstaller.parseLabel("1.3.13-page-1 (fork)"))
        assertEquals("1.3.13" to "upstream", KlsLspInstaller.parseLabel("1.3.13 (upstream)"))
        assertEquals("1.3.13" to "fork", KlsLspInstaller.parseLabel("1.3.13"))
    }

    @Test
    fun kotlinAdapterExposesMultiInstallApis() {
        val installer = LspInstallers.forId("kotlin")
        assertNotNull(installer)
        assertTrue(installer is KlsLspInstaller)
        installer.installedVersions()
        installer.activeVersion()
    }

    @Test
    fun supportsAllStep2NpmLanguages() {
        val npmIds = listOf("typescript", "javascript", "html", "css", "json", "yaml", "bash", "python", "dockerfile", "vue", "svelte")
        for (id in npmIds) {
            assertTrue(LspInstallers.supports(id), "expected support for $id")
            val installer = LspInstallers.forId(id)
            assertNotNull(installer, "expected installer for $id")
            assertTrue(installer is NpmGlobalInstaller, "$id should be NpmGlobalInstaller, got ${installer::class}")
        }
    }

    @Test
    fun npmDescriptorsHavePackageAndBinary() {
        val npmIds = listOf("typescript", "javascript", "html", "css", "json", "yaml", "bash", "python", "dockerfile", "vue", "svelte")
        for (id in npmIds) {
            val installer = LspInstallers.forId(id) as NpmGlobalInstaller
            assertTrue(installer.descriptor.packageName.isNotBlank(), "$id missing packageName")
            assertTrue(installer.descriptor.binaryName.isNotBlank(), "$id missing binaryName")
        }
    }

    @Test
    fun typescriptAndJavascriptShareSameInstaller() {
        val ts = LspInstallers.forId("typescript") as NpmGlobalInstaller
        val js = LspInstallers.forId("javascript") as NpmGlobalInstaller
        assertEquals(ts.descriptor.packageName, js.descriptor.packageName)
        assertEquals(ts.descriptor.installKey, js.descriptor.installKey)
    }

    @Test
    fun vscodeExtractedTriadSharesInstallKey() {
        val html = LspInstallers.forId("html") as NpmGlobalInstaller
        val css = LspInstallers.forId("css") as NpmGlobalInstaller
        val json = LspInstallers.forId("json") as NpmGlobalInstaller
        assertEquals(html.descriptor.installKey, css.descriptor.installKey)
        assertEquals(html.descriptor.installKey, json.descriptor.installKey)
        assertEquals(html.installRoot("4.10.0"), css.installRoot("4.10.0"))
    }

    @Test
    fun supportsAllStep3ShellLanguages() {
        val shellIds = mapOf(
            "ruby" to "gem",
            "ocaml" to "opam",
            "fsharp" to "dotnet",
            "perl" to "cpan",
            "r" to "Rscript",
            "haskell" to "ghcup",
            "go" to "go",
            "scala" to "cs",
        )
        for ((id, manager) in shellIds) {
            assertTrue(LspInstallers.supports(id), "expected support for $id")
            val installer = LspInstallers.forId(id)
            assertNotNull(installer, "expected installer for $id")
            assertTrue(installer is ShellPackageInstaller, "$id should be ShellPackageInstaller, got ${installer::class}")
            assertEquals(manager, installer.descriptor.managerName, "$id manager mismatch")
        }
    }

    @Test
    fun supportsToolchainDetectorLanguages() {
        for (id in listOf("dart", "swift")) {
            val installer = LspInstallers.forId(id)
            assertNotNull(installer, "expected installer for $id")
            assertTrue(installer is ToolchainDetectInstaller, "$id should be ToolchainDetectInstaller, got ${installer::class}")
        }
    }

    @Test
    fun registryCoversAllThirtyJsonLanguages() {
        val expected = listOf(
            "kotlin", "java", "python", "javascript", "typescript", "go", "rust", "c", "cpp", "dart",
            "bash", "ruby", "php", "swift", "scala", "haskell", "lua", "json", "yaml", "html",
            "css", "markdown", "sql", "r", "perl", "elixir", "clojure", "fsharp", "ocaml", "zig",
        )
        for (id in expected) {
            assertTrue(LspInstallers.supports(id), "registry missing language id: $id")
        }
    }
}
