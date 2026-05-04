package page.editor

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownFenceTest {
    @Test
    fun `caret in plain text is not inside fence`() {
        assertFalse(MarkdownFence.isInsideFence("hello world", 5))
    }

    @Test
    fun `caret on opening fence line is not inside`() {
        val text = "```\nfoo\n```"
        assertFalse(MarkdownFence.isInsideFence(text, 1))
    }

    @Test
    fun `caret on content line inside fence is inside`() {
        val text = "```\nfoo\n```"
        assertTrue(MarkdownFence.isInsideFence(text, 5))
    }

    @Test
    fun `caret on closing fence line is not inside`() {
        val text = "```\nfoo\n```"
        assertFalse(MarkdownFence.isInsideFence(text, 9))
    }

    @Test
    fun `caret after closing fence is not inside`() {
        val text = "```\nfoo\n```\nbar"
        assertTrue(MarkdownFence.isInsideFence(text, 5))
        assertFalse(MarkdownFence.isInsideFence(text, 13))
    }

    @Test
    fun `caret on empty line inside fence is inside`() {
        val text = "```\n\n```"
        assertTrue(MarkdownFence.isInsideFence(text, 4))
    }

    @Test
    fun `tilde fence works the same`() {
        val text = "~~~\nfoo\n~~~"
        assertTrue(MarkdownFence.isInsideFence(text, 5))
        assertFalse(MarkdownFence.isInsideFence(text, 9))
    }

    @Test
    fun `caret in unclosed fence at EOF is inside`() {
        val text = "```\nfoo"
        assertTrue(MarkdownFence.isInsideFence(text, 7))
    }

    @Test
    fun `caret on language-tagged opener is not inside`() {
        val text = "```kotlin\nfoo\n```"
        assertFalse(MarkdownFence.isInsideFence(text, 5))
        assertTrue(MarkdownFence.isInsideFence(text, 11))
    }

    @Test
    fun `closing fence must match opener char`() {
        val text = "```\nfoo\n~~~\nbar\n```"
        assertTrue(MarkdownFence.isInsideFence(text, 5))
        assertTrue(MarkdownFence.isInsideFence(text, 13))
    }

    @Test
    fun `closing fence must be at least as long as opener`() {
        val text = "````\nfoo\n```\nbar\n````"
        assertTrue(MarkdownFence.isInsideFence(text, 6))
        assertTrue(MarkdownFence.isInsideFence(text, 14))
    }

    @Test
    fun `multiple fences alternate state`() {
        val text = "```\nA\n```\nmid\n```\nB\n```"
        assertTrue(MarkdownFence.isInsideFence(text, 5))
        assertFalse(MarkdownFence.isInsideFence(text, 11))
        assertTrue(MarkdownFence.isInsideFence(text, 19))
    }

    @Test
    fun `inline backticks are not fences`() {
        val text = "use `code` inline"
        assertFalse(MarkdownFence.isInsideFence(text, 6))
    }

    @Test
    fun `caret at end of empty file is not inside`() {
        assertFalse(MarkdownFence.isInsideFence("", 0))
    }
}
