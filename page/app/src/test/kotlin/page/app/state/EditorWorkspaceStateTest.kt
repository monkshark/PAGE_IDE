package page.app.state

import page.app.PaneSide
import page.editor.TabBook
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class EditorWorkspaceStateTest {
    private fun bookWith(count: Int, active: Int): TabBook {
        var book = TabBook()
        for (i in 0 until count) {
            book = book.openOrFocus(Path.of("f$i.txt"), "")
        }
        return book.activate(active)
    }

    private fun state(book: TabBook, focused: PaneSide = PaneSide.PRIMARY) = EditorWorkspaceState().apply {
        focusedPane = focused
        when (focused) {
            PaneSide.PRIMARY -> primaryPane = primaryPane.copy(book = book)
            PaneSide.SECONDARY -> secondaryPane = secondaryPane.copy(book = book)
        }
    }

    @Test
    fun nextTabAdvancesActiveIndex() {
        val s = state(bookWith(3, active = 0))
        s.activateAdjacentTab(1)
        assertEquals(1, s.primaryPane.book.activeIndex)
    }

    @Test
    fun prevTabWrapsToLast() {
        val s = state(bookWith(3, active = 0))
        s.activateAdjacentTab(-1)
        assertEquals(2, s.primaryPane.book.activeIndex)
    }

    @Test
    fun nextTabWrapsToFirst() {
        val s = state(bookWith(3, active = 2))
        s.activateAdjacentTab(1)
        assertEquals(0, s.primaryPane.book.activeIndex)
    }

    @Test
    fun singleTabIsNoOp() {
        val s = state(bookWith(1, active = 0))
        s.activateAdjacentTab(1)
        assertEquals(0, s.primaryPane.book.activeIndex)
    }

    @Test
    fun actsOnFocusedPaneOnly() {
        val s = state(bookWith(3, active = 0), focused = PaneSide.SECONDARY)
        s.activateAdjacentTab(1)
        assertEquals(1, s.secondaryPane.book.activeIndex)
        assertEquals(-1, s.primaryPane.book.activeIndex)
    }
}
