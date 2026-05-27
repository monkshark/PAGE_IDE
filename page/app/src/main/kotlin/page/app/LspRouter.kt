package page.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import page.lsp.Diagnostic
import page.lsp.LanguageBackend
import page.lsp.LanguageRegistry
import page.lsp.LspBackends
import java.nio.file.Path

class LspRouter(
    private val workspaceRoot: Path?,
    private val parentScope: CoroutineScope,
) {
    private val controllers = mutableMapOf<String, LspController>()

    @Synchronized
    fun controllerFor(path: Path): LspController? {
        val ext = extractExtension(path) ?: return null
        val backend = LspBackends.forExtension(ext) ?: return null
        return controllers.getOrPut(backend.id) {
            val scope = CoroutineScope(SupervisorJob(parentScope.coroutineContext[Job]) + Dispatchers.Default)
            LspController(workspaceRoot, scope).also { it.ensureStarted(backend) }
        }
    }

    fun languageIdFor(path: Path): String? {
        val ext = extractExtension(path) ?: return null
        return LspBackends.forExtension(ext)?.id
    }

    @Synchronized
    fun controllerById(id: String): LspController? = controllers[id]

    val allDiagnosticsByUri: Map<String, List<Diagnostic>>
        @Synchronized get() = controllers.values
            .flatMap { it.diagnosticsByUri.entries }
            .associate { it.key to it.value }

    fun controllerForUri(uri: String): LspController? {
        if (!uri.startsWith("file:")) return null
        val path = runCatching { java.nio.file.Paths.get(java.net.URI(uri)) }.getOrNull() ?: return null
        return controllerFor(path)
    }

    fun applyExternalChange(uri: String, newText: String) {
        controllerForUri(uri)?.applyExternalChange(uri, newText)
    }

    fun notifyFilesRenamed(moves: List<Pair<Path, Path>>) {
        val affected = mutableSetOf<String>()
        for ((old, new) in moves) {
            extractExtension(old)?.let { LspBackends.forExtension(it)?.id }?.let { affected += it }
            extractExtension(new)?.let { LspBackends.forExtension(it)?.id }?.let { affected += it }
        }
        for (id in affected) {
            controllerById(id)?.notifyFilesRenamed(moves)
        }
    }

    @Synchronized
    fun shutdown() {
        controllers.values.forEach { it.shutdown() }
        controllers.clear()
    }

    private fun extractExtension(path: Path): String? {
        val name = path.fileName?.toString() ?: return null
        val dot = name.lastIndexOf('.')
        return if (dot >= 0 && dot < name.length - 1) name.substring(dot + 1) else null
    }
}

@Composable
fun rememberLspRouter(workspaceRoot: Path?): LspRouter {
    val router = remember(workspaceRoot) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        LspRouter(workspaceRoot, scope)
    }
    DisposableEffect(router) {
        onDispose { router.shutdown() }
    }
    return router
}
