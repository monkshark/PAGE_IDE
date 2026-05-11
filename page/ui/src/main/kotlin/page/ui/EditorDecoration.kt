package page.ui

import androidx.compose.ui.graphics.Color

data class EditorDecoration(
    val startOffset: Int,
    val endOffset: Int,
    val color: Color,
    val style: Style,
) {
    enum class Style { WAVY_UNDERLINE, TABSTOP_ACTIVE, TABSTOP_PENDING }
}
