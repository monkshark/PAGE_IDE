package page.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import page.app.EditorPaneState
import page.app.EditorScrollSnapshot
import page.app.PaneSide
import page.editor.SplitOrientation
import page.editor.SplitPaneState
import java.nio.file.Path

class EditorWorkspaceState {
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
}
