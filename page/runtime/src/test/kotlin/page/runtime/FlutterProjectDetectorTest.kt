package page.runtime

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlutterProjectDetectorTest {

    private fun tempRoot(): Path = Files.createTempDirectory("page-flutter-detect-")

    private val flutterPubspec = """
        name: my_app
        environment:
          sdk: ">=3.0.0 <4.0.0"
        dependencies:
          flutter:
            sdk: flutter
          http: ^1.0.0
        flutter:
          uses-material-design: true
    """.trimIndent()

    private val plainDartPubspec = """
        name: my_cli
        environment:
          sdk: ">=3.0.0 <4.0.0"
        dependencies:
          args: ^2.0.0
          http: ^1.0.0
    """.trimIndent()

    @Test
    fun detectsFlutterFromSdkDependency() {
        assertTrue(FlutterProjectDetector.isFlutterPubspec(flutterPubspec))
    }

    @Test
    fun detectsFlutterFromTopLevelSection() {
        val onlySection = "name: app\nflutter:\n  uses-material-design: true\n"
        assertTrue(FlutterProjectDetector.isFlutterPubspec(onlySection))
    }

    @Test
    fun plainDartIsNotFlutter() {
        assertFalse(FlutterProjectDetector.isFlutterPubspec(plainDartPubspec))
    }

    @Test
    fun findsPubspecWalkingUpFromNestedFile() {
        val root = tempRoot().resolve("proj")
        root.resolve("lib/src").createDirectories()
        root.resolve("pubspec.yaml").writeText(plainDartPubspec)
        val nested = root.resolve("lib/src/main.dart").also { it.writeText("void main() {}") }

        val found = FlutterProjectDetector.findPubspec(nested)
        assertEquals(root.resolve("pubspec.yaml").toAbsolutePath().normalize(), found)
    }

    @Test
    fun findPubspecStopsAtCeiling() {
        val tmp = tempRoot()
        tmp.resolve("pubspec.yaml").writeText(plainDartPubspec)
        val root = tmp.resolve("proj").also { it.createDirectories() }
        val nested = root.resolve("lib/main.dart")
        nested.parent.createDirectories()
        nested.writeText("void main() {}")

        assertNull(FlutterProjectDetector.findPubspec(nested, ceiling = root))
    }

    @Test
    fun flutterRootForReturnsProjectDirWhenFlutter() {
        val root = tempRoot().resolve("flutterapp")
        root.resolve("lib").createDirectories()
        root.resolve("pubspec.yaml").writeText(flutterPubspec)
        val main = root.resolve("lib/main.dart").also { it.writeText("void main() {}") }

        val flutterRoot = FlutterProjectDetector.flutterRootFor(main, workspaceRoot = root)
        assertEquals(root.toAbsolutePath().normalize(), flutterRoot)
    }

    @Test
    fun flutterRootForReturnsNullForPlainDart() {
        val root = tempRoot().resolve("dartcli")
        root.resolve("bin").createDirectories()
        root.resolve("pubspec.yaml").writeText(plainDartPubspec)
        val main = root.resolve("bin/main.dart").also { it.writeText("void main() {}") }

        assertNull(FlutterProjectDetector.flutterRootFor(main, workspaceRoot = root))
    }
}
