package page.runtime

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LanguageRunDefaultsDartTest {

    private fun tempRoot(): Path = Files.createTempDirectory("page-dart-run-")

    @Test
    fun dartFileResolvesToDartTemplate() {
        assertNotNull(LanguageRunDefaults.forExtension("dart"))
        assertEquals("Dart", LanguageRunDefaults.forExtension("dart")?.displayName)
    }

    @Test
    fun plainDartProjectRunsDartRunWithFile() {
        val root = tempRoot().resolve("cli")
        root.resolve("bin").createDirectories()
        root.resolve("pubspec.yaml").writeText("name: cli\ndependencies:\n  args: ^2.0.0\n")
        val main = root.resolve("bin/main.dart").also { it.writeText("void main() {}") }

        val cfg = LanguageRunDefaults.buildConfig(main, workspaceRoot = root)
        assertNotNull(cfg)
        assertTrue(cfg.name.startsWith("Dart ·"), "name was ${cfg.name}")
        assertEquals(listOf("run", main.toString()), cfg.args)
    }

    @Test
    fun flutterProjectRunsFlutterRunFromProjectRoot() {
        val root = tempRoot().resolve("app")
        root.resolve("lib").createDirectories()
        root.resolve("pubspec.yaml").writeText(
            "name: app\ndependencies:\n  flutter:\n    sdk: flutter\n",
        )
        val main = root.resolve("lib/main.dart").also { it.writeText("void main() {}") }

        val cfg = LanguageRunDefaults.buildConfig(main, workspaceRoot = root)
        assertNotNull(cfg)
        assertTrue(cfg.name.startsWith("Flutter ·"), "name was ${cfg.name}")
        assertEquals(listOf("run"), cfg.args)
        assertEquals(root.toString(), cfg.workingDir)
    }

    @Test
    fun dartFileWithoutPubspecStaysDart() {
        val root = tempRoot()
        val loose = root.resolve("scratch.dart").also { it.writeText("void main() {}") }

        val cfg = LanguageRunDefaults.buildConfig(loose, workspaceRoot = root)
        assertNotNull(cfg)
        assertTrue(cfg.name.startsWith("Dart ·"), "name was ${cfg.name}")
        assertEquals(listOf("run", loose.toString()), cfg.args)
    }

    @Test
    fun swiftFileResolvesToSwiftTemplate() {
        assertEquals("Swift", LanguageRunDefaults.forExtension("swift")?.displayName)
    }

    @Test
    fun swiftFileRunsSwiftWithFile() {
        val root = tempRoot()
        val main = root.resolve("main.swift").also { it.writeText("print(\"hi\")") }

        val cfg = LanguageRunDefaults.buildConfig(main, workspaceRoot = root)
        assertNotNull(cfg)
        assertTrue(cfg.name.startsWith("Swift ·"), "name was ${cfg.name}")
        val prelaunch = cfg.prelaunch
        if (prelaunch != null) {
            assertTrue(cfg.args.isEmpty(), "args were ${cfg.args}")
            assertTrue("-use-ld=lld" in prelaunch, "prelaunch was $prelaunch")
        } else {
            assertEquals(listOf(main.toString()), cfg.args)
        }
    }
}
