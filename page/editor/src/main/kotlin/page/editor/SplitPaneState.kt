package page.editor

enum class SplitOrientation { HORIZONTAL, VERTICAL }

data class SplitPaneState(
    val ratio: Float = 0.5f,
    val minRatio: Float = 0.1f,
    val maxRatio: Float = 0.9f,
) {
    init {
        require(minRatio in 0f..1f) { "minRatio out of [0,1]: $minRatio" }
        require(maxRatio in 0f..1f) { "maxRatio out of [0,1]: $maxRatio" }
        require(minRatio < maxRatio) { "minRatio($minRatio) must be < maxRatio($maxRatio)" }
    }

    val clamped: Float get() = ratio.coerceIn(minRatio, maxRatio)

    fun withRatio(newRatio: Float): SplitPaneState =
        copy(ratio = newRatio.coerceIn(minRatio, maxRatio))

    fun dragBy(deltaPx: Float, totalPx: Float): SplitPaneState {
        if (totalPx <= 0f) return this
        return withRatio(clamped + deltaPx / totalPx)
    }

    fun firstPanePx(totalPx: Float): Float = totalPx * clamped
    fun secondPanePx(totalPx: Float): Float = totalPx * (1f - clamped)
}
