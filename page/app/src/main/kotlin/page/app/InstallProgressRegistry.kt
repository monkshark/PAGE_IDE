package page.app

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import page.runtime.LspInstaller

/**
 * Global tracker for in-flight LSP installer runs.
 *
 * Used to bridge the InstallGuideDialog (which can be minimized) with the status bar progress
 * row — both read from this registry so install progress survives a dialog dismiss.
 */
object InstallProgressRegistry {

    data class Entry(
        val installerId: String,
        val displayName: String,
        val startedAtMs: Long,
        val progress: LspInstaller.Progress?,
    )

    private val _entries: SnapshotStateMap<String, Entry> = mutableStateMapOf()

    val entries: Map<String, Entry> get() = _entries

    fun start(installerId: String, displayName: String) {
        _entries[installerId] = Entry(
            installerId = installerId,
            displayName = displayName,
            startedAtMs = System.currentTimeMillis(),
            progress = null,
        )
    }

    fun update(installerId: String, progress: LspInstaller.Progress) {
        val existing = _entries[installerId] ?: return
        _entries[installerId] = existing.copy(progress = progress)
    }

    fun finish(installerId: String) {
        _entries.remove(installerId)
    }

    fun get(installerId: String): Entry? = _entries[installerId]
}
