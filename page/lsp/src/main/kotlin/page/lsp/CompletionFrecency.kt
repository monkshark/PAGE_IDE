package page.lsp

import java.util.concurrent.ConcurrentHashMap

object CompletionFrecency {

    private const val MAX_ENTRIES = 500
    private const val DECAY_HALF_LIFE_MS = 30 * 60 * 1000L

    private data class Entry(val count: Int, val lastUsedMs: Long)

    private val entries = ConcurrentHashMap<String, Entry>()

    fun recordSelection(label: String, kind: CompletionItemKind) {
        val key = "${kind.name}:$label"
        val now = System.currentTimeMillis()
        entries.compute(key) { _, existing ->
            if (existing == null) Entry(1, now)
            else Entry(existing.count + 1, now)
        }
        if (entries.size > MAX_ENTRIES) evict()
    }

    fun boost(items: List<CompletionItem>): List<CompletionItem> {
        if (entries.isEmpty()) return items
        val now = System.currentTimeMillis()
        return items.sortedWith(
            compareByDescending<CompletionItem> { score(it, now) }
                .thenBy { items.indexOf(it) }
        )
    }

    private fun score(item: CompletionItem, now: Long): Double {
        val key = "${item.kind.name}:${item.label}"
        val entry = entries[key] ?: return 0.0
        val ageMs = (now - entry.lastUsedMs).coerceAtLeast(0)
        val decay = Math.pow(0.5, ageMs.toDouble() / DECAY_HALF_LIFE_MS)
        return entry.count * decay
    }

    private fun evict() {
        val now = System.currentTimeMillis()
        val sorted = entries.entries.sortedBy { score(it.value, now) }
        val toRemove = sorted.take(sorted.size - MAX_ENTRIES / 2)
        toRemove.forEach { entries.remove(it.key) }
    }

    private fun score(entry: Entry, now: Long): Double {
        val ageMs = (now - entry.lastUsedMs).coerceAtLeast(0)
        val decay = Math.pow(0.5, ageMs.toDouble() / DECAY_HALF_LIFE_MS)
        return entry.count * decay
    }

    fun clear() = entries.clear()
}
