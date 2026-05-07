package page.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TreeSitterJavaLexerTest {
    private fun tokens(src: String) = TreeSitterJavaLexer.tokenize(src)

    private fun texts(src: String, kind: TokenKind) =
        tokens(src).filter { it.kind == kind }.map { src.substring(it.range.first, it.range.last + 1) }

    @Test
    fun `keywords are detected`() {
        val src = "public class Foo extends Bar {}"
        val kw = texts(src, TokenKind.KEYWORD)
        assertTrue("public" in kw)
        assertTrue("class" in kw)
        assertTrue("extends" in kw)
    }

    @Test
    fun `string literal`() {
        val src = "String s = \"hi\";"
        val strings = texts(src, TokenKind.STRING)
        assertEquals(listOf("\"hi\""), strings)
    }

    @Test
    fun `decimal integer literal`() {
        val src = "int x = 42;"
        val nums = texts(src, TokenKind.NUMBER)
        assertEquals(listOf("42"), nums)
    }

    @Test
    fun `line comment and block comment`() {
        val src = "// tail\n/* block */"
        val comments = texts(src, TokenKind.COMMENT)
        assertEquals(2, comments.size)
        assertTrue(comments.any { it.startsWith("//") })
        assertTrue(comments.any { it.startsWith("/*") })
    }

    @Test
    fun `annotation`() {
        val src = "@Override public void f() {}"
        val anns = texts(src, TokenKind.ANNOTATION)
        assertTrue(anns.any { it.startsWith("@") })
    }

    @Test
    fun `type identifier`() {
        val src = "List<String> xs;"
        val types = texts(src, TokenKind.TYPE)
        assertTrue("List" in types)
        assertTrue("String" in types)
    }

    @Test
    fun `boolean and null are keywords`() {
        val src = "boolean b = true; Object o = null;"
        val kw = texts(src, TokenKind.KEYWORD)
        assertTrue("true" in kw)
        assertTrue("null" in kw)
    }

    @Test
    fun `empty input returns empty`() {
        assertTrue(TreeSitterJavaLexer.tokenize("").isEmpty())
    }

    @Test
    fun `non-ascii text byte and char offsets stay aligned`() {
        val src = "// 한글 주석\nint x = 1;"
        val nums = tokens(src).filter { it.kind == TokenKind.NUMBER }
        assertEquals(1, nums.size)
        assertEquals("1", src.substring(nums[0].range.first, nums[0].range.last + 1))
        val comments = tokens(src).filter { it.kind == TokenKind.COMMENT }
        assertEquals(1, comments.size)
        assertEquals("// 한글 주석", src.substring(comments[0].range.first, comments[0].range.last + 1))
    }
}
