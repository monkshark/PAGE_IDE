package page.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GenericProcessBackendTest {

    @Test
    fun dotnetSingletonHasExpectedMetadata() {
        val dotnet = GenericProcessBackend.DOTNET
        assertEquals("dotnet-runtime", dotnet.languageId)
        assertEquals(".NET SDK", dotnet.displayName)
    }

    @Test
    fun forExtensionMapsCsToDotnet() {
        val backend = GenericProcessBackend.forExtension("cs")
        assertNotNull(backend)
        assertEquals("dotnet-runtime", backend.languageId)
    }

    @Test
    fun forExtensionMapsFsToDotnet() {
        val backend = GenericProcessBackend.forExtension("fs")
        assertNotNull(backend)
        assertEquals("dotnet-runtime", backend.languageId)
    }

    @Test
    fun forExtensionReturnsNullForUnknown() {
        assertNull(GenericProcessBackend.forExtension("py"))
        assertNull(GenericProcessBackend.forExtension("java"))
        assertNull(GenericProcessBackend.forExtension("rs"))
        assertNull(GenericProcessBackend.forExtension(""))
    }

    @Test
    fun forIdLooksUpByLanguageId() {
        assertNotNull(GenericProcessBackend.forId("dotnet-runtime"))
        assertNull(GenericProcessBackend.forId("rust-runtime"))
        assertNull(GenericProcessBackend.forId("unknown"))
    }

    @Test
    fun dotnetInstallEmitsFailedProgress() {
        var failed = false
        GenericProcessBackend.DOTNET.install(null) { progress ->
            if (progress is LspInstaller.Progress.Failed) failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun registryExposesDotnetRuntime() {
        assertTrue(LspInstallers.supports("dotnet-runtime"))
        val installer = LspInstallers.forId("dotnet-runtime")
        assertNotNull(installer)
        assertTrue(installer is GenericProcessBackend)
        assertEquals("dotnet-runtime", installer.languageId)
    }

    @Test
    fun dotnetRunTemplateExists() {
        val template = LanguageRunDefaults.forExtension("cs")
        assertNotNull(template)
        assertEquals("dotnet", template.command)
        assertEquals(listOf("run"), template.argTemplate)
    }

    @Test
    fun buildConfigForCsProjectUsesDotnet() {
        val cfg = LanguageRunDefaults.buildConfig(
            java.nio.file.Path.of("/proj/Program.cs"),
            java.nio.file.Path.of("/proj"),
        )
        assertNotNull(cfg)
        assertTrue(cfg.command.contains("dotnet"), "command should contain dotnet, got: ${cfg.command}")
        assertEquals(listOf("run"), cfg.args)
    }
}
