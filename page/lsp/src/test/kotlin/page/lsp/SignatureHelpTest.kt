package page.lsp

import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.ParameterInformation
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureInformation
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Tuple
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignatureHelpTest {

    @Test
    fun `fromLsp returns null for null input`() {
        assertNull(SignatureHelpInfo.fromLsp(null))
    }

    @Test
    fun `fromLsp returns null when signatures empty`() {
        val help = SignatureHelp().apply { signatures = mutableListOf() }
        assertNull(SignatureHelpInfo.fromLsp(help))
    }

    @Test
    fun `fromLsp parses simple signature with string-form parameter labels`() {
        val help = SignatureHelp().apply {
            signatures = mutableListOf(
                SignatureInformation().apply {
                    label = "addInts(x: Int, y: Int): Int"
                    documentation = Either.forLeft("adds two ints")
                    parameters = mutableListOf(
                        ParameterInformation("x: Int"),
                        ParameterInformation("y: Int"),
                    )
                },
            )
            activeSignature = 0
            activeParameter = 1
        }
        val info = SignatureHelpInfo.fromLsp(help)
        assertNotNull(info)
        assertEquals(1, info!!.signatures.size)
        val sig = info.signatures[0]
        assertEquals("addInts(x: Int, y: Int): Int", sig.label)
        assertEquals("adds two ints", sig.documentation)
        assertEquals(2, sig.parameters.size)
        assertEquals("x: Int", sig.parameters[0].labelText)
        assertEquals(8 until 14, sig.parameters[0].labelRange)
        assertEquals("y: Int", sig.parameters[1].labelText)
        assertEquals(16 until 22, sig.parameters[1].labelRange)
        assertEquals(0, info.activeSignature)
        assertEquals(1, info.activeParameter)
        assertEquals(1, info.effectiveActiveParameter())
    }

    @Test
    fun `fromLsp parses tuple-form parameter label offsets`() {
        val help = SignatureHelp().apply {
            signatures = mutableListOf(
                SignatureInformation().apply {
                    label = "draw(x: Int, y: Int)"
                    parameters = mutableListOf(
                        ParameterInformation().apply { setLabel(tupleLabel(5, 11)) },
                        ParameterInformation().apply { setLabel(tupleLabel(13, 19)) },
                    )
                },
            )
            activeSignature = 0
            activeParameter = 0
        }
        val info = SignatureHelpInfo.fromLsp(help)
        assertNotNull(info)
        val params = info!!.signatures[0].parameters
        assertEquals("x: Int", params[0].labelText)
        assertEquals(5 until 11, params[0].labelRange)
        assertEquals("y: Int", params[1].labelText)
        assertEquals(13 until 19, params[1].labelRange)
    }

    @Test
    fun `fromLsp clamps tuple offsets that exceed signature label length`() {
        val help = SignatureHelp().apply {
            signatures = mutableListOf(
                SignatureInformation().apply {
                    label = "foo(a)"
                    parameters = mutableListOf(
                        ParameterInformation().apply { setLabel(tupleLabel(4, 999)) },
                    )
                },
            )
        }
        val info = SignatureHelpInfo.fromLsp(help)
        assertNotNull(info)
        val p = info!!.signatures[0].parameters[0]
        assertEquals("a)", p.labelText)
        assertEquals(4 until 6, p.labelRange)
    }

    private fun tupleLabel(start: Int, end: Int): Either<String, Tuple.Two<Int, Int>> =
        Either.forRight(Tuple.two(start, end))

    @Test
    fun `fromLsp coerces activeSignature into range`() {
        val help = SignatureHelp().apply {
            signatures = mutableListOf(
                SignatureInformation().apply { label = "a()" },
                SignatureInformation().apply { label = "b()" },
            )
            activeSignature = 9
            activeParameter = 0
        }
        val info = SignatureHelpInfo.fromLsp(help)
        assertNotNull(info)
        assertEquals(1, info!!.activeSignature)
        assertEquals("b()", info.active!!.label)
    }

    @Test
    fun `signature-level activeParameter overrides top-level`() {
        val help = SignatureHelp().apply {
            signatures = mutableListOf(
                SignatureInformation().apply {
                    label = "f(a, b, c)"
                    parameters = mutableListOf(
                        ParameterInformation("a"),
                        ParameterInformation("b"),
                        ParameterInformation("c"),
                    )
                    activeParameter = 2
                },
            )
            activeSignature = 0
            activeParameter = 0
        }
        val info = SignatureHelpInfo.fromLsp(help)
        assertNotNull(info)
        assertEquals(0, info!!.activeParameter)
        assertEquals(2, info.effectiveActiveParameter())
    }

    @Test
    fun `fromLsp renders MarkupContent docs`() {
        val help = SignatureHelp().apply {
            signatures = mutableListOf(
                SignatureInformation().apply {
                    label = "f()"
                    documentation = Either.forRight(MarkupContent(MarkupKind.MARKDOWN, "**bold**"))
                },
            )
        }
        val info = SignatureHelpInfo.fromLsp(help)
        assertEquals("**bold**", info!!.signatures[0].documentation)
    }

    @Test
    fun `fromLsp negative activeParameter coerced to zero`() {
        val help = SignatureHelp().apply {
            signatures = mutableListOf(SignatureInformation().apply { label = "x()" })
            activeSignature = 0
            activeParameter = -5
        }
        val info = SignatureHelpInfo.fromLsp(help)
        assertEquals(0, info!!.activeParameter)
    }

    @Test
    fun `SignatureActiveParam returns null when no open paren before caret`() {
        assertNull(SignatureActiveParam.fromLineText("abc xyz", 4))
    }

    @Test
    fun `SignatureActiveParam returns zero just after open paren`() {
        assertEquals(0, SignatureActiveParam.fromLineText("foo(", 4))
    }

    @Test
    fun `SignatureActiveParam counts commas at depth zero`() {
        assertEquals(1, SignatureActiveParam.fromLineText("foo(a, ", 7))
        assertEquals(2, SignatureActiveParam.fromLineText("foo(a, b, ", 10))
    }

    @Test
    fun `SignatureActiveParam ignores commas inside nested parens`() {
        assertEquals(2, SignatureActiveParam.fromLineText("foo(a, bar(x, y), ", 18))
    }

    @Test
    fun `SignatureActiveParam ignores commas inside string literals`() {
        assertEquals(1, SignatureActiveParam.fromLineText("foo(\"a, b\", ", 12))
    }

    @Test
    fun `SignatureActiveParam returns null when caret is past matching close paren`() {
        assertNull(SignatureActiveParam.fromLineText("foo(a, b) ", 10))
    }

    @Test
    fun `SignatureActiveParam returns commas count even when call is unclosed`() {
        assertEquals(2, SignatureActiveParam.fromLineText("greet(\"hi\", obj, ", 17))
    }

    @Test
    fun `EMPTY constants`() {
        assertTrue(SignatureHelpInfo.EMPTY.isEmpty)
        assertEquals(0, SignatureHelpInfo.EMPTY.signatures.size)
    }
}
