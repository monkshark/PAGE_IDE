package page.editor

object PageScroll {
    const val DEFAULT_FALLBACK_LINES = 10

    fun linesPerPage(
        viewportPx: Float,
        lineHeightPx: Float,
        fallback: Int = DEFAULT_FALLBACK_LINES,
    ): Int {
        if (viewportPx <= 0f || lineHeightPx <= 0f) return fallback
        val raw = (viewportPx / lineHeightPx).toInt() - 1
        return raw.coerceAtLeast(1)
    }
}
