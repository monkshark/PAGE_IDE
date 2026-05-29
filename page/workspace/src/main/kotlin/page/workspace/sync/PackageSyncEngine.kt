package page.workspace.sync

import page.workspace.FileOpHistory
import page.workspace.FolderPackageRename
import java.nio.file.Files
import java.nio.file.Path

object PackageSyncEngine {

    fun folderRewrites(
        newFolder: Path,
        packageMap: Map<String, String>,
        rootDir: Path?,
        readText: (Path) -> String?,
    ): List<FileOpHistory.RewriteEntry> {
        if (packageMap.isEmpty()) return emptyList()
        val newFolderAbs = newFolder.toAbsolutePath().normalize()
        val rewrites = LinkedHashMap<Path, Pair<String, String>>()
        runCatching {
            Files.walk(newFolder).use { stream ->
                stream.filter { Files.isRegularFile(it) && isKtFile(it) }
                    .forEach { p ->
                        val abs = p.toAbsolutePath().normalize()
                        val text = readText(abs) ?: return@forEach
                        val rewritten = FolderPackageRename.rewriteFileInRenamedFolder(text, packageMap) ?: return@forEach
                        rewrites[abs] = text to rewritten
                    }
            }
        }
        rootDir?.let { root ->
            runCatching {
                Files.walk(root).use { stream ->
                    stream.filter { Files.isRegularFile(it) && isKtFile(it) }
                        .forEach { p ->
                            val abs = p.toAbsolutePath().normalize()
                            if (abs.startsWith(newFolderAbs)) return@forEach
                            if (abs in rewrites) return@forEach
                            val text = readText(abs) ?: return@forEach
                            val rewritten = FolderPackageRename.rewriteImports(text, packageMap) ?: return@forEach
                            rewrites[abs] = text to rewritten
                        }
                }
            }
        }
        return rewrites.map { (path, pair) -> FileOpHistory.RewriteEntry(path, pair.first, pair.second) }
    }

    fun singleFileMoveRewrites(
        newPath: Path,
        plan: FolderPackageRename.SingleFileMovePlan,
        rootDir: Path?,
        readText: (Path) -> String?,
    ): List<FileOpHistory.RewriteEntry> {
        val rewrites = LinkedHashMap<Path, Pair<String, String>>()
        val newSelf = plan.newSelfText
        if (newSelf != null) {
            val abs = newPath.toAbsolutePath().normalize()
            val current = readText(abs)
            if (current != null) rewrites[abs] = current to newSelf
        }
        rootDir?.let { root ->
            val newPathAbs = newPath.toAbsolutePath().normalize()
            runCatching {
                Files.walk(root).use { stream ->
                    stream.filter { Files.isRegularFile(it) && isKtFile(it) }
                        .forEach { p ->
                            val abs = p.toAbsolutePath().normalize()
                            if (abs == newPathAbs) return@forEach
                            if (abs in rewrites) return@forEach
                            val text = readText(abs) ?: return@forEach
                            val rewritten = FolderPackageRename.rewriteImports(text, plan.importRewriteMap) ?: return@forEach
                            rewrites[abs] = text to rewritten
                        }
                }
            }
        }
        return rewrites.map { (path, pair) -> FileOpHistory.RewriteEntry(path, pair.first, pair.second) }
    }

    private fun isKtFile(p: Path): Boolean {
        val name = p.fileName?.toString().orEmpty()
        return name.endsWith(".kt") || name.endsWith(".kts")
    }
}
