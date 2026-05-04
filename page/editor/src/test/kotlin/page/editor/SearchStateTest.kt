package page.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class SearchStateTest {

    @Test
    fun `empty query has no matches`() {
        val s = SearchState().withQuery("hello world", "")
        assertTrue(s.matches.isEmpty())
        assertEquals(-1, s.activeMatchIndex)
        assertFalse(s.isActive)
    }

    @Test
    fun `single occurrence is matched`() {
        val s = SearchState().withQuery("hello world", "world")
        assertEquals(listOf(6..10), s.matches)
        assertEquals(0, s.activeMatchIndex)
        assertEquals(6..10, s.active)
    }

    @Test
    fun `multiple occurrences are all matched`() {
        val s = SearchState().withQuery("aaaa", "aa")
        assertEquals(listOf(0..1, 2..3), s.matches)
        assertEquals(0, s.activeMatchIndex)
    }

    @Test
    fun `case insensitive by default`() {
        val s = SearchState().withQuery("Hello hello HELLO", "hello")
        assertEquals(3, s.matches.size)
    }

    @Test
    fun `case sensitive matches only exact`() {
        val s = SearchState(caseSensitive = true).withQuery("Hello hello HELLO", "hello")
        assertEquals(1, s.matches.size)
        assertEquals(6..10, s.matches.first())
    }

    @Test
    fun `withCaseSensitive recomputes matches`() {
        val s = SearchState()
            .withQuery("Hello hello", "hello")
        assertEquals(2, s.matches.size)
        val tightened = s.withCaseSensitive("Hello hello", true)
        assertEquals(1, tightened.matches.size)
    }

    @Test
    fun `next wraps around`() {
        val s = SearchState().withQuery("aaaa", "a")
        val s1 = s.next()
        val s2 = s1.next()
        val s3 = s2.next()
        val s4 = s3.next()
        assertEquals(1, s1.activeMatchIndex)
        assertEquals(2, s2.activeMatchIndex)
        assertEquals(3, s3.activeMatchIndex)
        assertEquals(0, s4.activeMatchIndex)
    }

    @Test
    fun `prev wraps around`() {
        val s = SearchState().withQuery("aaaa", "a")
        val s1 = s.prev()
        assertEquals(3, s1.activeMatchIndex)
    }

    @Test
    fun `next on empty matches is identity`() {
        val s = SearchState().withQuery("hello", "xyz")
        assertTrue(s.matches.isEmpty())
        assertEquals(-1, s.next().activeMatchIndex)
    }

    @Test
    fun `retarget keeps active near previous position`() {
        val s = SearchState()
            .withQuery("foo bar foo bar foo", "foo")
            .next()
        assertEquals(1, s.activeMatchIndex)
        val updated = s.retarget("foo bar foo bar foo extra foo")
        assertEquals(4, updated.matches.size)
        assertEquals(1, updated.activeMatchIndex)
    }

    @Test
    fun `retarget falls back to first when previous gone`() {
        val s = SearchState()
            .withQuery("foo foo foo", "foo")
            .next()
            .next()
        assertEquals(2, s.activeMatchIndex)
        val updated = s.retarget("foo")
        assertEquals(1, updated.matches.size)
        assertEquals(0, updated.activeMatchIndex)
    }

    @Test
    fun `retarget on no matches clears active`() {
        val s = SearchState().withQuery("foo foo", "foo")
        val updated = s.retarget("bar bar")
        assertTrue(updated.matches.isEmpty())
        assertEquals(-1, updated.activeMatchIndex)
        assertNull(updated.active)
    }

    @Test
    fun `query overlapping matches are skipped past length`() {
        val s = SearchState().withQuery("ababab", "ab")
        assertEquals(listOf(0..1, 2..3, 4..5), s.matches)
    }
}
