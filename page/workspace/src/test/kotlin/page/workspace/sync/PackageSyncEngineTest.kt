package page.workspace.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import page.workspace.FolderPackageRename
import java.nio.file.Files
import java.nio.file.Path

class PackageSyncEngineTest {

    private val readText: (Path) -> String? = { p -> runCatching { Files.readString(p) }.getOrNull() }

    private fun write(path: Path, text: String): Path {
        Files.createDirectories(path.parent)
        Files.writeString(path, text)
        return path
    }

    @Test
    fun `folderRewrites returns empty when package map is empty`(@TempDir root: Path) {
        val newFolder = write(root.resolve("beta/A.kt"), "package alpha\n\nclass A\n").parent
        val entries = PackageSyncEngine.folderRewrites(newFolder, emptyMap(), root, readText)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `folderRewrites rewrites moved file package and external importer`(@TempDir root: Path) {
        val moved = write(root.resolve("beta/A.kt"), "package alpha\n\nclass A\n")
        val consumer = write(
            root.resolve("consumer/UseA.kt"),
            "package consumer\n\nimport alpha.A\n\nclass UseA { val a = A() }\n",
        )
        val newFolder = moved.parent

        val entries = PackageSyncEngine.folderRewrites(newFolder, mapOf("alpha" to "beta"), root, readText)

        val byPath = entries.associateBy { it.path.toAbsolutePath().normalize() }
        val movedEntry = byPath.getValue(moved.toAbsolutePath().normalize())
        val consumerEntry = byPath.getValue(consumer.toAbsolutePath().normalize())
        assertEquals(2, entries.size)
        assertTrue(movedEntry.rewritten.contains("package beta"), movedEntry.rewritten)
        assertTrue(consumerEntry.rewritten.contains("import beta.A"), consumerEntry.rewritten)
    }

    @Test
    fun `folderRewrites does not touch files on disk`(@TempDir root: Path) {
        val moved = write(root.resolve("beta/A.kt"), "package alpha\n\nclass A\n")
        PackageSyncEngine.folderRewrites(moved.parent, mapOf("alpha" to "beta"), root, readText)
        assertEquals("package alpha\n\nclass A\n", Files.readString(moved))
    }

    @Test
    fun `singleFileMoveRewrites updates self text and importers`(@TempDir root: Path) {
        val movedSelf = write(root.resolve("beta/Mover.kt"), "package alpha\n\nclass Mover\n")
        val consumer = write(
            root.resolve("consumer/UseMover.kt"),
            "package consumer\n\nimport alpha.Mover\n\nclass UseMover { val m = Mover() }\n",
        )
        val plan = FolderPackageRename.SingleFileMovePlan(
            oldPackage = "alpha",
            newPackage = "beta",
            stem = "Mover",
            newSelfText = "package beta\n\nclass Mover\n",
            importRewriteMap = mapOf("alpha.Mover" to "beta.Mover"),
        )

        val entries = PackageSyncEngine.singleFileMoveRewrites(movedSelf, plan, root, readText)

        val byPath = entries.associateBy { it.path.toAbsolutePath().normalize() }
        assertEquals("package beta\n\nclass Mover\n", byPath.getValue(movedSelf.toAbsolutePath().normalize()).rewritten)
        assertTrue(byPath.getValue(consumer.toAbsolutePath().normalize()).rewritten.contains("import beta.Mover"))
    }
}
