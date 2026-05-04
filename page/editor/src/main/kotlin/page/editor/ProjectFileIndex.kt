package page.editor

import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayDeque

data class IndexedFile(val path: Path, val relative: String)

object ProjectFileIndex {

    private const val DEFAULT_LIMIT = 5000

    private val SKIP = setOf(
        ".git", ".idea", ".gradle", ".kotlin", ".vscode",
        "build", "out", "node_modules", "target", "dist", ".cache", "bin",
    )

    fun walk(root: Path, limit: Int = DEFAULT_LIMIT): List<IndexedFile> {
        if (!isDirectorySafe(root)) return emptyList()
        val out = ArrayList<IndexedFile>(256)
        val stack = ArrayDeque<Path>()
        stack.push(root)
        while (stack.isNotEmpty() && out.size < limit) {
            val dir = stack.pop()
            val children = listChildren(dir) ?: continue
            for (child in children) {
                if (out.size >= limit) break
                val name = child.fileName?.toString() ?: continue
                if (name.startsWith(".") && name !in setOf(".env")) continue
                if (isDirectorySafe(child)) {
                    if (name in SKIP) continue
                    stack.push(child)
                } else {
                    val rel = root.relativize(child).toString().replace('\\', '/')
                    out += IndexedFile(child, rel)
                }
            }
        }
        out.sortBy { it.relative.lowercase() }
        return out
    }

    private fun listChildren(dir: Path): List<Path>? {
        val stream = try {
            Files.list(dir)
        } catch (_: Exception) {
            return null
        }
        return try {
            stream.use { it.toList() }
        } catch (_: Exception) {
            null
        }
    }

    private fun isDirectorySafe(path: Path): Boolean = try {
        Files.isDirectory(path)
    } catch (_: Exception) {
        false
    }
}
