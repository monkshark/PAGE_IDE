package page.editor

enum class UndoGroupKind { None, Insert, Delete, Replace }

class UndoGroupTracker(
    private val idleBreakMs: Long = DEFAULT_IDLE_BREAK_MS,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    private var kind: UndoGroupKind = UndoGroupKind.None
    private var lastTime: Long = 0L
    private var endedOnBreakChar: Boolean = false
    private var broken: Boolean = false

    fun markBreak() {
        broken = true
    }

    fun reset() {
        kind = UndoGroupKind.None
        lastTime = 0L
        endedOnBreakChar = false
        broken = false
    }

    fun onTextChange(prevText: String, newText: String): Boolean {
        val delta = computeDelta(prevText, newText)
        val now = nowProvider()
        val shouldBreak = when {
            kind == UndoGroupKind.None -> true
            broken -> true
            kind != delta.kind -> true
            endedOnBreakChar -> true
            now - lastTime > idleBreakMs -> true
            delta.isLargeOp -> true
            else -> false
        }
        kind = delta.kind
        lastTime = now
        endedOnBreakChar = delta.endsOnBreakChar || delta.isLargeOp
        broken = false
        return shouldBreak
    }

    private data class Delta(
        val kind: UndoGroupKind,
        val isLargeOp: Boolean,
        val endsOnBreakChar: Boolean,
    )

    private fun computeDelta(prevText: String, newText: String): Delta {
        if (prevText == newText) {
            return Delta(UndoGroupKind.None, isLargeOp = false, endsOnBreakChar = false)
        }
        val prefix = commonPrefixLen(prevText, newText)
        val suffix = commonSuffixLen(prevText, newText, prefix)
        val removed = prevText.length - prefix - suffix
        val inserted = newText.length - prefix - suffix
        return when {
            inserted > 0 && removed == 0 -> {
                val text = newText.substring(prefix, prefix + inserted)
                Delta(
                    kind = UndoGroupKind.Insert,
                    isLargeOp = inserted > 1,
                    endsOnBreakChar = text.lastOrNull()?.let(::isBreakChar) ?: false,
                )
            }
            removed > 0 && inserted == 0 -> {
                val text = prevText.substring(prefix, prefix + removed)
                Delta(
                    kind = UndoGroupKind.Delete,
                    isLargeOp = removed > 1,
                    endsOnBreakChar = text.firstOrNull()?.let(::isBreakChar) ?: false,
                )
            }
            else -> Delta(UndoGroupKind.Replace, isLargeOp = true, endsOnBreakChar = true)
        }
    }

    private fun commonPrefixLen(a: String, b: String): Int {
        val n = minOf(a.length, b.length)
        var i = 0
        while (i < n && a[i] == b[i]) i++
        return i
    }

    private fun commonSuffixLen(a: String, b: String, prefixLen: Int): Int {
        val maxA = a.length - prefixLen
        val maxB = b.length - prefixLen
        val n = minOf(maxA, maxB)
        var i = 0
        while (i < n && a[a.length - 1 - i] == b[b.length - 1 - i]) i++
        return i
    }

    private fun isBreakChar(c: Char): Boolean = c.isWhitespace() || c in BREAK_PUNCT

    companion object {
        const val DEFAULT_IDLE_BREAK_MS = 500L
        private val BREAK_PUNCT = setOf(
            '.', ',', ';', ':', '!', '?',
            '(', ')', '[', ']', '{', '}',
            '<', '>', '"', '\'', '`', '/', '\\',
        )
    }
}
