@file:Suppress("DEPRECATION")

package page.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.text.input.EditProcessor
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TextInputSession

/**
 * Compose Multiplatform Desktop 1.7.3 의 새 IME API (PlatformTextInputModifierNode +
 * establishTextInputSession) 는 caret rectangle 을 IME 에 노출하는 통로가 없음.
 * caret rect 가 없으면 Windows WInputMethod 가 후보창 위치 계산 시 NPE.
 * legacy TextInputService 만이 notifyFocusedRect(Rect) 를 받는다 — 그래서 이 모듈은
 * deprecated legacy API 를 의도적으로 사용. 새 API 가 caret rect 를 받는 hook 을
 * 추가하면 그때 마이그레이션.
 */
fun Modifier.imeInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    caretRect: () -> Rect,
): Modifier = this then ImeInputElement(value, onValueChange, caretRect)

private data class ImeInputElement(
    val value: TextFieldValue,
    val onValueChange: (TextFieldValue) -> Unit,
    val caretRect: () -> Rect,
) : ModifierNodeElement<ImeInputNode>() {
    override fun create(): ImeInputNode = ImeInputNode(value, onValueChange, caretRect)
    override fun update(node: ImeInputNode) {
        node.onValueChange = onValueChange
        node.caretRect = caretRect
        node.syncValue(value)
        node.notifyCaret()
    }
}

private class ImeInputNode(
    initialValue: TextFieldValue,
    var onValueChange: (TextFieldValue) -> Unit,
    var caretRect: () -> Rect,
) : Modifier.Node(),
    GlobalPositionAwareModifierNode,
    CompositionLocalConsumerModifierNode {

    private val processor = EditProcessor().apply { reset(initialValue, null) }
    private var session: TextInputSession? = null
    private var coords: LayoutCoordinates? = null
    private var lastValue: TextFieldValue = initialValue

    fun syncValue(newValue: TextFieldValue) {
        if (newValue == lastValue) return
        val old = lastValue
        lastValue = newValue
        processor.reset(newValue, null)
        session?.updateState(old, newValue)
    }

    fun notifyCaret() {
        val s = session ?: return
        val c = coords ?: return
        if (!c.isAttached) return
        val r = runCatching { caretRect() }.getOrNull() ?: return
        val tl = c.localToWindow(Offset(r.left, r.top))
        val br = c.localToWindow(Offset(r.right, r.bottom))
        s.notifyFocusedRect(Rect(tl.x, tl.y, br.x, br.y))
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        coords = coordinates
        notifyCaret()
    }

    override fun onAttach() {
        super.onAttach()
        if (session != null) return
        val service = currentValueOf(LocalTextInputService) ?: return
        processor.reset(lastValue, null)
        session = service.startInput(
            value = lastValue,
            imeOptions = ImeOptions.Default,
            onEditCommand = { commands ->
                val newValue = processor.apply(commands)
                lastValue = newValue
                onValueChange(newValue)
            },
            onImeActionPerformed = { },
        )
        notifyCaret()
    }

    override fun onDetach() {
        session?.dispose()
        session = null
    }
}
