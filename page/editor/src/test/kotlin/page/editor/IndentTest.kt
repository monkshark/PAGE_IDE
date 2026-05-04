package page.editor

import kotlin.test.Test
import kotlin.test.assertEquals

class IndentTest {
    @Test
    fun `tab inserts spaces to next tab stop at column 0`() {
        val r = Indent.handleTab(TextEdit("abc", 0))
        assertEquals("    abc", r.text)
        assertEquals(4, r.caret)
    }

    @Test
    fun `tab inserts spaces to next tab stop at column 1`() {
        val r = Indent.handleTab(TextEdit("abc", 1))
        assertEquals("a   bc", r.text)
        assertEquals(4, r.caret)
    }

    @Test
    fun `tab inserts spaces to next tab stop at column 4`() {
        val r = Indent.handleTab(TextEdit("    abc", 4))
        assertEquals("        abc", r.text)
        assertEquals(8, r.caret)
    }

    @Test
    fun `tab replaces single line selection with four spaces`() {
        val r = Indent.handleTab(TextEdit("foo bar", 4, 7))
        assertEquals("foo     ", r.text)
        assertEquals(8, r.caret)
    }

    @Test
    fun `tab indents both lines of multi-line selection`() {
        val r = Indent.handleTab(TextEdit("abc\ndef", 0, 7))
        assertEquals("    abc\n    def", r.text)
        assertEquals(0, r.selectionStart)
        assertEquals(15, r.selectionEnd)
    }

    @Test
    fun `tab on multi-line selection includes only lines that contain selected chars`() {
        val r = Indent.handleTab(TextEdit("a\nb\nc", 0, 4))
        assertEquals("    a\n    b\nc", r.text)
        assertEquals(0, r.selectionStart)
        assertEquals(12, r.selectionEnd)
    }

    @Test
    fun `tab on selection ending at line start excludes that line`() {
        val r = Indent.handleTab(TextEdit("a\nb\nc", 0, 2))
        assertEquals("    a\nb\nc", r.text)
        assertEquals(0, r.selectionStart)
        assertEquals(6, r.selectionEnd)
    }

    @Test
    fun `shift-tab removes four leading spaces`() {
        val r = Indent.handleShiftTab(TextEdit("    abc", 7))
        assertEquals("abc", r.text)
        assertEquals(3, r.caret)
    }

    @Test
    fun `shift-tab removes only available leading spaces under tab unit`() {
        val r = Indent.handleShiftTab(TextEdit("  abc", 5))
        assertEquals("abc", r.text)
        assertEquals(3, r.caret)
    }

    @Test
    fun `shift-tab clamps caret when caret was inside removed indent`() {
        val r = Indent.handleShiftTab(TextEdit("    abc", 2))
        assertEquals("abc", r.text)
        assertEquals(0, r.caret)
    }

    @Test
    fun `shift-tab is a no-op when line has no leading whitespace`() {
        val r = Indent.handleShiftTab(TextEdit("abc", 1))
        assertEquals("abc", r.text)
        assertEquals(1, r.caret)
    }

    @Test
    fun `shift-tab unindents both lines of multi-line selection`() {
        val r = Indent.handleShiftTab(TextEdit("    abc\n    def", 0, 15))
        assertEquals("abc\ndef", r.text)
        assertEquals(0, r.selectionStart)
        assertEquals(7, r.selectionEnd)
    }

    @Test
    fun `shift-tab treats a single tab character as one indent unit`() {
        val r = Indent.handleShiftTab(TextEdit("\tabc", 4))
        assertEquals("abc", r.text)
        assertEquals(3, r.caret)
    }

    @Test
    fun `enter preserves leading indent of current line`() {
        val r = Indent.handleEnter(TextEdit("    abc", 7))
        assertEquals("    abc\n    ", r.text)
        assertEquals(12, r.caret)
    }

    @Test
    fun `enter with no indent inserts plain newline`() {
        val r = Indent.handleEnter(TextEdit("abc", 3))
        assertEquals("abc\n", r.text)
        assertEquals(4, r.caret)
    }

    @Test
    fun `enter after open brace adds extra indent`() {
        val r = Indent.handleEnter(TextEdit("if {", 4))
        assertEquals("if {\n    ", r.text)
        assertEquals(9, r.caret)
    }

    @Test
    fun `enter after open paren adds extra indent`() {
        val r = Indent.handleEnter(TextEdit("    foo(", 8))
        assertEquals("    foo(\n        ", r.text)
        assertEquals(17, r.caret)
    }

    @Test
    fun `enter after colon adds extra indent`() {
        val r = Indent.handleEnter(TextEdit("def f():", 8))
        assertEquals("def f():\n    ", r.text)
        assertEquals(13, r.caret)
    }

    @Test
    fun `enter between empty brace pair splits with caret on indented middle line`() {
        val r = Indent.handleEnter(TextEdit("{}", 1))
        assertEquals("{\n    \n}", r.text)
        assertEquals(6, r.caret)
    }

    @Test
    fun `enter between empty paren pair preserves outer indent on closing line`() {
        val r = Indent.handleEnter(TextEdit("    foo()", 8))
        assertEquals("    foo(\n        \n    )", r.text)
        assertEquals(17, r.caret)
    }

    @Test
    fun `enter with selection replaces selection then applies indent`() {
        val r = Indent.handleEnter(TextEdit("abc XYZ def", 4, 7))
        assertEquals("abc \n def", r.text)
        assertEquals(5, r.caret)
    }

    @Test
    fun `closing brace on whitespace-only line unindents one level`() {
        val old = TextEdit("if {\n    ", 9)
        val new = TextEdit("if {\n    }", 10)
        val r = Indent.maybeUnindentClosingBrace(old, new)
        assertEquals("if {\n}", r.text)
        assertEquals(6, r.caret)
    }

    @Test
    fun `closing brace at column 8 unindents to column 4`() {
        val old = TextEdit("        ", 8)
        val new = TextEdit("        }", 9)
        val r = Indent.maybeUnindentClosingBrace(old, new)
        assertEquals("    }", r.text)
        assertEquals(5, r.caret)
    }

    @Test
    fun `closing bracket on whitespace-only line unindents`() {
        val old = TextEdit("[\n    ", 6)
        val new = TextEdit("[\n    ]", 7)
        val r = Indent.maybeUnindentClosingBrace(old, new)
        assertEquals("[\n]", r.text)
        assertEquals(3, r.caret)
    }

    @Test
    fun `closing paren on whitespace-only line unindents`() {
        val old = TextEdit("(\n    ", 6)
        val new = TextEdit("(\n    )", 7)
        val r = Indent.maybeUnindentClosingBrace(old, new)
        assertEquals("(\n)", r.text)
        assertEquals(3, r.caret)
    }

    @Test
    fun `closing brace does not unindent when content sits before it`() {
        val old = TextEdit("    abc", 7)
        val new = TextEdit("    abc}", 8)
        val r = Indent.maybeUnindentClosingBrace(old, new)
        assertEquals("    abc}", r.text)
        assertEquals(8, r.caret)
    }

    @Test
    fun `closing brace at column zero is a no-op`() {
        val old = TextEdit("", 0)
        val new = TextEdit("}", 1)
        val r = Indent.maybeUnindentClosingBrace(old, new)
        assertEquals("}", r.text)
        assertEquals(1, r.caret)
    }

    @Test
    fun `non-closer typing is a no-op for unindent helper`() {
        val old = TextEdit("    ", 4)
        val new = TextEdit("    x", 5)
        val r = Indent.maybeUnindentClosingBrace(old, new)
        assertEquals("    x", r.text)
        assertEquals(5, r.caret)
    }

    @Test
    fun `multi-char insert is a no-op for unindent helper`() {
        val old = TextEdit("    ", 4)
        val new = TextEdit("    ab", 6)
        val r = Indent.maybeUnindentClosingBrace(old, new)
        assertEquals("    ab", r.text)
        assertEquals(6, r.caret)
    }
}
