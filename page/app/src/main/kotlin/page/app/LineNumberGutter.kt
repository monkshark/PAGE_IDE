package page.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun LineNumberGutter(
    lineCount: Int,
    currentLine: Int,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val total = lineCount.coerceAtLeast(1)
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val activeColor = MaterialTheme.colorScheme.onBackground
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .width(IntrinsicSize.Max)
            .padding(top = 16.dp, bottom = 16.dp),
    ) {
        for (line in 0 until total) {
            val color = if (line == currentLine) activeColor else mutedColor
            Text(
                text = (line + 1).toString(),
                style = textStyle.copy(color = color),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                textAlign = TextAlign.End,
            )
        }
    }
}
