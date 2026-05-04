package page.editor

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TabBookTest {

    private fun p(name: String): Path = Paths.get("/tmp/$name")

    @Test
    fun `empty book has no active tab`() {
        val book = TabBook()
        assertNull(book.active)
        assertEquals(-1, book.activeIndex)
        assertTrue(book.tabs.isEmpty())
    }

    @Test
    fun `openOrFocus on empty book adds and activates`() {
        val book = TabBook().openOrFocus(p("a.txt"), "alpha")
        assertEquals(1, book.tabs.size)
        assertEquals(0, book.activeIndex)
        assertEquals("alpha", book.active?.text)
    }

    @Test
    fun `openOrFocus on new path appends and activates last`() {
        val book = TabBook()
            .openOrFocus(p("a.txt"), "A")
            .openOrFocus(p("b.txt"), "B")
        assertEquals(2, book.tabs.size)
        assertEquals(1, book.activeIndex)
        assertEquals(p("b.txt"), book.active?.path)
    }

    @Test
    fun `openOrFocus on existing path focuses without duplicating`() {
        val book = TabBook()
            .openOrFocus(p("a.txt"), "A")
            .openOrFocus(p("b.txt"), "B")
            .openOrFocus(p("a.txt"), "ignored")
        assertEquals(2, book.tabs.size)
        assertEquals(0, book.activeIndex)
        assertEquals("A", book.active?.text)
    }

    @Test
    fun `close active middle tab activates next`() {
        val book = TabBook()
            .openOrFocus(p("a.txt"), "A")
            .openOrFocus(p("b.txt"), "B")
            .openOrFocus(p("c.txt"), "C")
            .activate(1)
            .close(1)
        assertEquals(2, book.tabs.size)
        assertEquals(1, book.activeIndex)
        assertEquals(p("c.txt"), book.active?.path)
    }

    @Test
    fun `close last active tab activates previous`() {
        val book = TabBook()
            .openOrFocus(p("a.txt"), "A")
            .openOrFocus(p("b.txt"), "B")
            .closeActive()
        assertEquals(1, book.tabs.size)
        assertEquals(0, book.activeIndex)
        assertEquals(p("a.txt"), book.active?.path)
    }

    @Test
    fun `close tab before active decrements activeIndex`() {
        val book = TabBook()
            .openOrFocus(p("a.txt"), "A")
            .openOrFocus(p("b.txt"), "B")
            .openOrFocus(p("c.txt"), "C")
            .close(0)
        assertEquals(2, book.tabs.size)
        assertEquals(1, book.activeIndex)
        assertEquals(p("c.txt"), book.active?.path)
    }

    @Test
    fun `close tab after active keeps activeIndex`() {
        val book = TabBook()
            .openOrFocus(p("a.txt"), "A")
            .openOrFocus(p("b.txt"), "B")
            .openOrFocus(p("c.txt"), "C")
            .activate(0)
            .close(2)
        assertEquals(2, book.tabs.size)
        assertEquals(0, book.activeIndex)
        assertEquals(p("a.txt"), book.active?.path)
    }

    @Test
    fun `close only tab leaves empty book`() {
        val book = TabBook()
            .openOrFocus(p("a.txt"), "A")
            .closeActive()
        assertTrue(book.tabs.isEmpty())
        assertEquals(-1, book.activeIndex)
        assertNull(book.active)
    }

    @Test
    fun `close out of range is ignored`() {
        val book = TabBook().openOrFocus(p("a.txt"), "A")
        assertSame(book, book.close(5))
        assertSame(book, book.close(-1))
    }

    @Test
    fun `activate sets the index`() {
        val book = TabBook()
            .openOrFocus(p("a.txt"), "A")
            .openOrFocus(p("b.txt"), "B")
            .activate(0)
        assertEquals(0, book.activeIndex)
        assertEquals(p("a.txt"), book.active?.path)
    }

    @Test
    fun `activate out of range is ignored`() {
        val book = TabBook().openOrFocus(p("a.txt"), "A")
        assertSame(book, book.activate(5))
        assertSame(book, book.activate(-1))
    }

    @Test
    fun `updateActive replaces text and caret on active tab`() {
        val book = TabBook()
            .openOrFocus(p("a.txt"), "A")
            .openOrFocus(p("b.txt"), "B")
            .updateActive("B-new", caret = 3)
        assertEquals("B-new", book.active?.text)
        assertEquals(3, book.active?.caret)
        assertEquals("A", book.tabs[0].text)
    }

    @Test
    fun `updateActive with same content returns same instance`() {
        val book = TabBook().openOrFocus(p("a.txt"), "A")
        assertSame(book, book.updateActive("A", caret = 0))
    }

    @Test
    fun `updateActive on empty book is ignored`() {
        val book = TabBook()
        assertSame(book, book.updateActive("x", caret = 1))
    }

    @Test
    fun `move active tab follows new position`() {
        val book = TabBook()
            .openOrFocus(p("a.txt"), "A")
            .openOrFocus(p("b.txt"), "B")
            .openOrFocus(p("c.txt"), "C")
            .activate(0)
            .move(0, 2)
        assertEquals(listOf(p("b.txt"), p("c.txt"), p("a.txt")), book.tabs.map { it.path })
        assertEquals(2, book.activeIndex)
        assertEquals(p("a.txt"), book.active?.path)
    }

    @Test
    fun `move right shifts active when crossed`() {
        val book = TabBook()
            .openOrFocus(p("a.txt"), "A")
            .openOrFocus(p("b.txt"), "B")
            .openOrFocus(p("c.txt"), "C")
            .activate(2)
            .move(0, 2)
        assertEquals(listOf(p("b.txt"), p("c.txt"), p("a.txt")), book.tabs.map { it.path })
        assertEquals(1, book.activeIndex)
        assertEquals(p("c.txt"), book.active?.path)
    }

    @Test
    fun `move left shifts active when crossed`() {
        val book = TabBook()
            .openOrFocus(p("a.txt"), "A")
            .openOrFocus(p("b.txt"), "B")
            .openOrFocus(p("c.txt"), "C")
            .activate(0)
            .move(2, 0)
        assertEquals(listOf(p("c.txt"), p("a.txt"), p("b.txt")), book.tabs.map { it.path })
        assertEquals(1, book.activeIndex)
        assertEquals(p("a.txt"), book.active?.path)
    }

    @Test
    fun `move keeps active outside the swap range`() {
        val book = TabBook()
            .openOrFocus(p("a.txt"), "A")
            .openOrFocus(p("b.txt"), "B")
            .openOrFocus(p("c.txt"), "C")
            .openOrFocus(p("d.txt"), "D")
            .activate(3)
            .move(0, 1)
        assertEquals(listOf(p("b.txt"), p("a.txt"), p("c.txt"), p("d.txt")), book.tabs.map { it.path })
        assertEquals(3, book.activeIndex)
    }

    @Test
    fun `move with same indices is identity`() {
        val book = TabBook()
            .openOrFocus(p("a.txt"), "A")
            .openOrFocus(p("b.txt"), "B")
        assertSame(book, book.move(1, 1))
    }

    @Test
    fun `move with out of range indices is identity`() {
        val book = TabBook()
            .openOrFocus(p("a.txt"), "A")
            .openOrFocus(p("b.txt"), "B")
        assertSame(book, book.move(0, 5))
        assertSame(book, book.move(-1, 1))
    }
}
