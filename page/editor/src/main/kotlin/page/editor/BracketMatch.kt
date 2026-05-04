package page.editor

object BracketMatch {
    private val openers = mapOf('(' to ')', '[' to ']', '{' to '}')
    private val closers = openers.entries.associate { (k, v) -> v to k }

    fun find(text: String, caret: Int): Pair<Int, Int>? {
        if (caret > 0) {
            val before = text[caret - 1]
            val m = matchAt(text, caret - 1, before)
            if (m != null) return m
        }
        if (caret < text.length) {
            val at = text[caret]
            val m = matchAt(text, caret, at)
            if (m != null) return m
        }
        return null
    }

    private fun matchAt(text: String, pos: Int, c: Char): Pair<Int, Int>? {
        val asOpener = openers[c]
        if (asOpener != null) {
            val match = scan(text, pos, c, asOpener, +1) ?: return null
            return pos to match
        }
        val asCloser = closers[c]
        if (asCloser != null) {
            val match = scan(text, pos, c, asCloser, -1) ?: return null
            return match to pos
        }
        return null
    }

    private fun scan(text: String, from: Int, same: Char, target: Char, dir: Int): Int? {
        var depth = 1
        var i = from + dir
        while (i in text.indices) {
            val c = text[i]
            if (c == same) depth++
            else if (c == target) {
                depth--
                if (depth == 0) return i
            }
            i += dir
        }
        return null
    }
}
