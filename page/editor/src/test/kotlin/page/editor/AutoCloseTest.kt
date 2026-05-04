package page.editor

import kotlin.test.Test
import kotlin.test.assertEquals

class AutoCloseTest {
    @Test
    fun `inserts closing paren after typing open paren`() {
        val old = TextEdit("foo", 3)
        val new = TextEdit("foo(", 4)
        val result = AutoClose.apply(old, new)
        assertEquals("foo()", result.text)
        assertEquals(4, result.caret)
    }

    @Test
    fun `inserts closing bracket`() {
        val old = TextEdit("", 0)
        val new = TextEdit("[", 1)
        val result = AutoClose.apply(old, new)
        assertEquals("[]", result.text)
        assertEquals(1, result.caret)
    }

    @Test
    fun `inserts closing brace`() {
        val old = TextEdit("if ", 3)
        val new = TextEdit("if {", 4)
        val result = AutoClose.apply(old, new)
        assertEquals("if {}", result.text)
        assertEquals(4, result.caret)
    }

    @Test
    fun `inserts closing double quote`() {
        val old = TextEdit("x = ", 4)
        val new = TextEdit("x = \"", 5)
        val result = AutoClose.apply(old, new)
        assertEquals("x = \"\"", result.text)
        assertEquals(5, result.caret)
    }

    @Test
    fun `inserts closing single quote`() {
        val old = TextEdit("x = ", 4)
        val new = TextEdit("x = '", 5)
        val result = AutoClose.apply(old, new)
        assertEquals("x = ''", result.text)
        assertEquals(5, result.caret)
    }

    @Test
    fun `skips over closing paren when next char already matches`() {
        val old = TextEdit("()", 1)
        val new = TextEdit("())", 2)
        val result = AutoClose.apply(old, new)
        assertEquals("()", result.text)
        assertEquals(2, result.caret)
    }

    @Test
    fun `skips over closing brace`() {
        val old = TextEdit("{}", 1)
        val new = TextEdit("{}}", 2)
        val result = AutoClose.apply(old, new)
        assertEquals("{}", result.text)
        assertEquals(2, result.caret)
    }

    @Test
    fun `skips over double quote`() {
        val old = TextEdit("\"\"", 1)
        val new = TextEdit("\"\"\"", 2)
        val result = AutoClose.apply(old, new)
        assertEquals("\"\"", result.text)
        assertEquals(2, result.caret)
    }

    @Test
    fun `does not auto-close when next char is letter`() {
        val old = TextEdit("abc", 0)
        val new = TextEdit("(abc", 1)
        val result = AutoClose.apply(old, new)
        assertEquals("(abc", result.text)
        assertEquals(1, result.caret)
    }

    @Test
    fun `does not auto-close when next char is digit`() {
        val old = TextEdit("123", 0)
        val new = TextEdit("(123", 1)
        val result = AutoClose.apply(old, new)
        assertEquals("(123", result.text)
        assertEquals(1, result.caret)
    }

    @Test
    fun `does not auto-close quote inside word`() {
        val old = TextEdit("don", 3)
        val new = TextEdit("don'", 4)
        val result = AutoClose.apply(old, new)
        assertEquals("don'", result.text)
        assertEquals(4, result.caret)
    }

    @Test
    fun `auto-closes paren before space`() {
        val old = TextEdit("foo bar", 3)
        val new = TextEdit("foo( bar", 4)
        val result = AutoClose.apply(old, new)
        assertEquals("foo() bar", result.text)
        assertEquals(4, result.caret)
    }

    @Test
    fun `does not trigger on multi-character insert`() {
        val old = TextEdit("", 0)
        val new = TextEdit("(x", 2)
        val result = AutoClose.apply(old, new)
        assertEquals("(x", result.text)
        assertEquals(2, result.caret)
    }

    @Test
    fun `does not trigger on deletion`() {
        val old = TextEdit("(abc", 4)
        val new = TextEdit("(ab", 3)
        val result = AutoClose.apply(old, new)
        assertEquals("(ab", result.text)
        assertEquals(3, result.caret)
    }

    @Test
    fun `does not trigger on selection-only change`() {
        val old = TextEdit("foo", 0)
        val new = TextEdit("foo", 3)
        val result = AutoClose.apply(old, new)
        assertEquals("foo", result.text)
        assertEquals(3, result.caret)
    }

    @Test
    fun `does not trigger on plain letter typing`() {
        val old = TextEdit("foo", 3)
        val new = TextEdit("foox", 4)
        val result = AutoClose.apply(old, new)
        assertEquals("foox", result.text)
        assertEquals(4, result.caret)
    }
}
