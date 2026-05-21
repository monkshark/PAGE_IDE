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
        assertNull(LspInstallers.forId("haskell"))
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
}
