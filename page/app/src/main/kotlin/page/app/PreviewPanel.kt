package page.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.svg.SVGDOM
import org.jetbrains.skia.svg.SVGLengthUnit
import page.editor.FileKind
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.min

private const val MIN_ZOOM = 0.1f
private const val MAX_ZOOM = 8f
private const val ZOOM_STEP = 1.25f
private const val INITIAL_FIT_FRACTION = 0.7f
private const val WHEEL_ZOOM_FACTOR = 1.1f

@Composable
fun PreviewPanel(
    path: Path,
    kind: FileKind,
    modifier: Modifier = Modifier,
) {
    val painter: Painter? = remember(path, kind) {
        runCatching {
            val bytes = Files.readAllBytes(path)
            when (kind) {
                FileKind.IMAGE -> BitmapPainter(SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap())
                FileKind.SVG -> SvgPainter(SVGDOM(Data.makeFromBytes(bytes)))
                FileKind.TEXT -> null
            }
        }.getOrNull()
    }
    var zoom by remember(path, kind) { mutableStateOf(1.0f) }

    Column(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (painter == null) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Cannot preview ${path.fileName}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                ImageViewer(
                    painter = painter,
                    zoom = zoom,
                    onZoom = { zoom = it.coerceIn(MIN_ZOOM, MAX_ZOOM) },
                )
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            thickness = 1.dp,
        )
        ZoomToolbar(
            zoom = zoom,
            enabled = painter != null,
            onZoomIn = { zoom = (zoom * ZOOM_STEP).coerceAtMost(MAX_ZOOM) },
            onZoomOut = { zoom = (zoom / ZOOM_STEP).coerceAtLeast(MIN_ZOOM) },
            onReset = { zoom = 1f },
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ImageViewer(
    painter: Painter,
    zoom: Float,
    onZoom: (Float) -> Unit,
) {
    val intrinsic = painter.intrinsicSize
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { event ->
                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (delta != 0f) {
                    val factor = if (delta > 0) 1f / WHEEL_ZOOM_FACTOR else WHEEL_ZOOM_FACTOR
                    onZoom(zoom * factor)
                    event.changes.forEach { it.consume() }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val panelWPx = constraints.maxWidth.toFloat()
        val panelHPx = constraints.maxHeight.toFloat()
        val baseline = computeBaseline(intrinsic, panelWPx, panelHPx)
        val effective = baseline * zoom
        val density = LocalDensity.current
        val widthDp = with(density) { (intrinsic.width * effective).toDp() }
        val heightDp = with(density) { (intrinsic.height * effective).toDp() }
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.size(widthDp, heightDp),
        )
    }
}

private class SvgPainter(private val dom: SVGDOM) : Painter() {
    override val intrinsicSize: Size = computeIntrinsicSize(dom)

    override fun DrawScope.onDraw() {
        drawIntoCanvas { canvas ->
            dom.setContainerSize(size.width, size.height)
            dom.render(canvas.nativeCanvas)
        }
    }

    private companion object {
        fun computeIntrinsicSize(dom: SVGDOM): Size {
            val root = dom.root ?: return Size.Unspecified
            val w = root.width
            val h = root.height
            val widthPx = if (w.unit != SVGLengthUnit.PERCENTAGE) w.value else 0f
            val heightPx = if (h.unit != SVGLengthUnit.PERCENTAGE) h.value else 0f
            if (widthPx > 0f && heightPx > 0f) return Size(widthPx, heightPx)
            val vb = root.viewBox
            if (vb != null && vb.width > 0f && vb.height > 0f) return Size(vb.width, vb.height)
            return Size.Unspecified
        }
    }
}

private fun computeBaseline(intrinsic: Size, panelW: Float, panelH: Float): Float {
    if (!intrinsic.width.isFinite() || !intrinsic.height.isFinite()) return INITIAL_FIT_FRACTION
    if (intrinsic.width <= 0f || intrinsic.height <= 0f) return INITIAL_FIT_FRACTION
    if (panelW <= 0f || panelH <= 0f) return INITIAL_FIT_FRACTION
    val fit = min(panelW / intrinsic.width, panelH / intrinsic.height)
    return min(1f, fit) * INITIAL_FIT_FRACTION
}

@Composable
private fun ZoomToolbar(
    zoom: Float,
    enabled: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(28.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            ZoomButton(symbol = "−", enabled = enabled, onClick = onZoomOut)
            Spacer(Modifier.width(4.dp))
            ZoomLabel(zoom = zoom, enabled = enabled, onClick = onReset)
            Spacer(Modifier.width(4.dp))
            ZoomButton(symbol = "+", enabled = enabled, onClick = onZoomIn)
        }
    }
}

@Composable
private fun ZoomButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    val bg =
        if (enabled && isHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        else Color.Transparent
    val fg =
        if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Box(
        modifier = Modifier
            .size(20.dp)
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            style = LocalTextStyle.current.copy(
                color = fg,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun ZoomLabel(zoom: Float, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    val bg =
        if (enabled && isHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        else Color.Transparent
    val fg =
        if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Box(
        modifier = Modifier
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${(zoom * 100).toInt()}%",
            color = fg,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
