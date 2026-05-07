package page.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UndoGroupTrackerTest {

    private class Clock(var now: Long = 0L) {
        operator fun invoke(): Long = now
    }

    private fun tracker(clock: Clock, idleMs: Long = 500L) =
        UndoGroupTracker(idleBreakMs = idleMs, nowProvider = { clock.now })

    @Test
    fun firstChangeAlwaysBreaks() {
        val clock = Clock()
        val t = tracker(clock)
        assertTrue(t.onTextChange("", "a"))
    }

    @Test
    fun continuousLetterTypingMerges() {
        val clock = Clock(1000L)
        val t = tracker(clock)
        assertTrue(t.onTextChange("", "a"))
        clock.now += 50
        assertFalse(t.onTextChange("a", "ab"))
        clock.now += 50
        assertFalse(t.onTextChange("ab", "abc"))
    }

    @Test
    fun whitespaceAfterWordKeepsCurrentGroupAndBreaksNext() {
        val clock = Clock(1000L)
        val t = tracker(clock)
        assertTrue(t.onTextChange("", "a"))
        clock.now += 50
        assertFalse(t.onTextChange("a", "ab"))
        clock.now += 50
        assertFalse(t.onTextChange("ab", "ab "))
        clock.now += 50
        assertTrue(t.onTextChange("ab ", "ab c"))
    }

    @Test
    fun punctuationBreaks() {
        val clock = Clock(1000L)
        val t = tracker(clock)
        assertTrue(t.onTextChange("", "f"))
        clock.now += 30
        assertFalse(t.onTextChange("f", "fo"))
        clock.now += 30
        assertFalse(t.onTextChange("fo", "foo"))
        clock.now += 30
        assertFalse(t.onTextChange("foo", "foo("))
        clock.now += 30
        assertTrue(t.onTextChange("foo(", "foo()"))
    }

    @Test
    fun idleAboveThresholdBreaks() {
        val clock = Clock(1000L)
        val t = tracker(clock, idleMs = 500L)
        assertTrue(t.onTextChange("", "a"))
        clock.now += 200
        assertFalse(t.onTextChange("a", "ab"))
        clock.now += 800
        assertTrue(t.onTextChange("ab", "abc"))
    }

    @Test
    fun insertAfterDeleteBreaks() {
        val clock = Clock(1000L)
        val t = tracker(clock)
        assertTrue(t.onTextChange("", "abc"))
        clock.now += 50
        assertTrue(t.onTextChange("abc", "ab"))
        clock.now += 50
        assertTrue(t.onTextChange("ab", "abx"))
    }

    @Test
    fun continuousBackspaceMergesThroughWhitespaceUntilNextWord() {
        val clock = Clock(1000L)
        val t = tracker(clock)
        assertTrue(t.onTextChange("abc def", "abc de"))
        clock.now += 50
        assertFalse(t.onTextChange("abc de", "abc d"))
        clock.now += 50
        assertFalse(t.onTextChange("abc d", "abc "))
        clock.now += 50
        assertFalse(t.onTextChange("abc ", "abc"))
        clock.now += 50
        assertTrue(t.onTextChange("abc", "ab"))
    }

    @Test
    fun pasteIsLargeOpAndBreaks() {
        val clock = Clock(1000L)
        val t = tracker(clock)
        assertTrue(t.onTextChange("", "hello"))
        clock.now += 50
        assertTrue(t.onTextChange("hello", "hello world"))
    }

    @Test
    fun replaceForcesBreak() {
        val clock = Clock(1000L)
        val t = tracker(clock)
        assertTrue(t.onTextChange("", "a"))
        clock.now += 50
        assertTrue(t.onTextChange("a", "X"))
        clock.now += 50
        assertTrue(t.onTextChange("X", "Xb"))
    }

    @Test
    fun explicitMarkBreakForcesNextBreak() {
        val clock = Clock(1000L)
        val t = tracker(clock)
        assertTrue(t.onTextChange("", "a"))
        t.markBreak()
        clock.now += 30
        assertTrue(t.onTextChange("a", "ab"))
    }

    @Test
    fun resetClearsState() {
        val clock = Clock(1000L)
        val t = tracker(clock)
        assertTrue(t.onTextChange("", "a"))
        t.reset()
        clock.now += 30
        assertTrue(t.onTextChange("a", "ab"))
    }

    @Test
    fun newlineBreaks() {
        val clock = Clock(1000L)
        val t = tracker(clock)
        assertTrue(t.onTextChange("", "a"))
        clock.now += 30
        assertFalse(t.onTextChange("a", "a\n"))
        clock.now += 30
        assertTrue(t.onTextChange("a\n", "a\nb"))
    }

    @Test
    fun firstCharIsBreakCharStillBreaks() {
        val clock = Clock(1000L)
        val t = tracker(clock)
        assertTrue(t.onTextChange("", " "))
        clock.now += 30
        assertTrue(t.onTextChange(" ", " a"))
    }

    @Test
    fun deltaSelectionInsertReportsLargeOp() {
        val clock = Clock(1000L)
        val t = tracker(clock)
        assertTrue(t.onTextChange("", "abc"))
        clock.now += 50
        val replaced = t.onTextChange("abc", "Xbc")
        assertTrue(replaced)
        assertEquals(true, replaced)
    }
}
