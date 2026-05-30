package page.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import page.app.EditorPaneState
import page.app.EditorScrollSnapshot
import page.app.PaneSide
import page.editor.EditSnapshot
import page.editor.SplitOrientation
import page.editor.SplitPaneState
import page.editor.UndoGroupTracker
import java.nio.file.Path

class EditorWorkspaceState(
    private val undoTracker: (PaneSide) -> UndoGroupTracker,
) {
    var primaryPane by mutableStateOf(EditorPaneState())
    var secondaryPane by mutableStateOf(EditorPaneState())
    var focusedPane by mutableStateOf(PaneSide.PRIMARY)

    var splitEnabled by mutableStateOf(false)
    var splitOrientation by mutableStateOf(SplitOrientation.HORIZONTAL)
    var splitState by mutableStateOf(SplitPaneState(ratio = 0.5f))

    var editorScrollByPath by mutableStateOf(emptyMap<Path, EditorScrollSnapshot>())
    var foldByPath by mutableStateOf(emptyMap<String, Set<Int>>())

    fun paneOf(side: PaneSide): EditorPaneState = when (side) {
        PaneSide.PRIMARY -> primaryPane
        PaneSide.SECONDARY -> secondaryPane
    }

    fun setPane(side: PaneSide, value: EditorPaneState) {
        when (side) {
            PaneSide.PRIMARY -> primaryPane = value
            PaneSide.SECONDARY -> secondaryPane = value
        }
    }

    fun mutatePane(side: PaneSide, transform: (EditorPaneState) -> EditorPaneState) {
        setPane(side, transform(paneOf(side)))
    }

    fun mutateFocused(transform: (EditorPaneState) -> EditorPaneState) {
        mutatePane(focusedPane, transform)
    }

    fun focused(): EditorPaneState = paneOf(focusedPane)

    fun activateAdjacentTab(delta: Int) {
        val book = paneOf(focusedPane).book
        val n = book.tabs.size
        if (n <= 1) return
        val next = ((book.activeIndex + delta) % n + n) % n
        if (next != book.activeIndex) {
            mutatePane(focusedPane) { it.copy(book = it.book.activate(next)) }
        }
    }

    fun handleEditorChange(side: PaneSide, value: TextFieldValue) {
        mutatePane(side) {
            val priorText = it.editorValue.text
            val priorSelection = it.editorValue.selection
            val textChanged = value.text != priorText
            val tracker = undoTracker(side)
            val nextBook = if (textChanged) {
                val priorCaret = priorSelection.start
                val shouldPush = tracker.onTextChange(priorText, value.text)
                val withPush = if (shouldPush) {
                    it.book.pushHistoryOnActive(EditSnapshot(priorText, priorCaret))
                } else it.book
                withPush.updateActive(value.text, value.selection.start)
            } else {
                if (value.selection != priorSelection) tracker.markBreak()
                it.book.updateActive(value.text, value.selection.start)
            }
            val nextSearch = if (textChanged) it.search?.retarget(value.text) else it.search
            it.copy(editorValue = value, book = nextBook, search = nextSearch)
        }
    }

    fun activateTab(side: PaneSide, index: Int) {
        mutatePane(side) { it.copy(book = it.book.activate(index)) }
    }

    fun moveTab(side: PaneSide, from: Int, to: Int) {
        mutatePane(side) { it.copy(book = it.book.move(from, to)) }
    }

    fun moveTabAcross(source: PaneSide, index: Int) {
        if (!splitEnabled) return
        val tab = paneOf(source).book.tabs.getOrNull(index) ?: return
        val target = if (source == PaneSide.PRIMARY) PaneSide.SECONDARY else PaneSide.PRIMARY
        mutatePane(source) { it.copy(book = it.book.close(index)) }
        mutatePane(target) { it.copy(book = it.book.appendTab(tab)) }
        focusedPane = target
    }
}
