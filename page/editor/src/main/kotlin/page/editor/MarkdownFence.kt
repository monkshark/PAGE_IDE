package page.editor

object MarkdownFence {
    fun isInsideFence(text: String, caret: Int): Boolean {
        val safeCaret = caret.coerceIn(0, text.length)
        var inFence = false
        var fenceChar: Char? = null
        var fenceLen = 0
        var i = 0
        val len = text.length
        while (i < len) {
            val lineStart = i
            var j = i
            while (j < len && text[j] != '\n') j++
            val lineEnd = j
            val containsCaret = safeCaret in lineStart..lineEnd
            val fence = parseFenceLine(text, lineStart, lineEnd)

            if (!inFence) {
                if (containsCaret) return false
                if (fence != null) {
                    inFence = true
                    fenceChar = fence.first
                    fenceLen = fence.second
                }
            } else {
                val isCloser = fence != null && fence.first == fenceChar && fence.second >= fenceLen
                if (containsCaret) return !isCloser
                if (isCloser) {
                    inFence = false
                    fenceChar = null
                    fenceLen = 0
                }
            }
            i = lineEnd + 1
        }
        return inFence
    }

    private fun parseFenceLine(text: String, lineStart: Int, lineEnd: Int): Pair<Char, Int>? {
        var i = lineStart
        var lead = 0
        while (i < lineEnd && text[i] == ' ' && lead < 3) {
            i++
            lead++
        }
        if (i >= lineEnd) return null
        val c = text[i]
        if (c != '`' && c != '~') return null
        var count = 0
        while (i < lineEnd && text[i] == c) {
            i++
            count++
        }
        if (count < 3) return null
        return c to count
    }
}
