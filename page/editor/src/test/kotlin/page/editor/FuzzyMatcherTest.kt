package page.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FuzzyMatcherTest {

    @Test
    fun `empty query returns zero match`() {
        val r = FuzzyMatcher.match("", "anything")
        assertNotNull(r)
        assertEquals(0, r.indices.size)
    }

    @Test
    fun `single char match returns index`() {
        val r = FuzzyMatcher.match("a", "abc")
        assertNotNull(r)
        assertEquals(intArrayOf(0).toList(), r.indices.toList())
    }

    @Test
    fun `subsequence matches in order`() {
        val r = FuzzyMatcher.match("ace", "abcde")
        assertNotNull(r)
        assertEquals(intArrayOf(0, 2, 4).toList(), r.indices.toList())
    }

    @Test
    fun `non subsequence returns null`() {
        assertNull(FuzzyMatcher.match("xyz", "abc"))
    }

    @Test
    fun `query longer than target returns null`() {
        assertNull(FuzzyMatcher.match("abcd", "abc"))
    }

    @Test
    fun `case insensitive match`() {
        val r = FuzzyMatcher.match("FOO", "foo")
        assertNotNull(r)
        assertEquals(intArrayOf(0, 1, 2).toList(), r.indices.toList())
    }

    @Test
    fun `consecutive run scores higher than scattered`() {
        val tight = FuzzyMatcher.match("abc", "abcxx")
        val loose = FuzzyMatcher.match("abc", "axbxc")
        assertNotNull(tight)
        assertNotNull(loose)
        assertTrue(tight.score > loose.score)
    }

    @Test
    fun `word boundary scores higher than mid-word`() {
        val boundary = FuzzyMatcher.match("e", "foo/editor")
        val mid = FuzzyMatcher.match("e", "fooeditor")
        assertNotNull(boundary)
        assertNotNull(mid)
        assertTrue(boundary.score > mid.score)
    }

    @Test
    fun `start of string scores higher than later match`() {
        val start = FuzzyMatcher.match("a", "abc")
        val later = FuzzyMatcher.match("a", "xxa")
        assertNotNull(start)
        assertNotNull(later)
        assertTrue(start.score > later.score)
    }

    @Test
    fun `prefers earliest first letter when both viable`() {
        val r = FuzzyMatcher.match("ab", "ababab")
        assertNotNull(r)
        assertEquals(intArrayOf(0, 1).toList(), r.indices.toList())
    }

    @Test
    fun `path separator counts as boundary`() {
        val a = FuzzyMatcher.match("E", "src/EditorPanel.kt")
        val b = FuzzyMatcher.match("E", "srcXEditorPanel.kt")
        assertNotNull(a)
        assertNotNull(b)
        assertTrue(a.score > b.score)
    }

    @Test
    fun `camelHump counts as boundary`() {
        val r = FuzzyMatcher.match("EP", "EditorPanel")
        assertNotNull(r)
        assertEquals(intArrayOf(0, 6).toList(), r.indices.toList())
    }
}
