package page.lsp

import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.ParameterInformation
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureInformation
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Tuple

data class SignatureHelpInfo(
    val signatures: List<SignatureInfo>,
    val activeSignature: Int,
    val activeParameter: Int,
) {
    val isEmpty: Boolean get() = signatures.isEmpty()

    val active: SignatureInfo? get() = signatures.getOrNull(activeSignature)

    fun effectiveActiveParameter(): Int {
        val sig = active ?: return activeParameter
        val overridden = sig.activeParameter
        if (overridden != null) return overridden
        return activeParameter
    }

    companion object {
        val EMPTY: SignatureHelpInfo = SignatureHelpInfo(emptyList(), 0, 0)

        fun fromLsp(help: SignatureHelp?): SignatureHelpInfo? {
            if (help == null) return null
            val sigs = help.signatures.orEmpty().mapNotNull(SignatureInfo::fromLsp)
            if (sigs.isEmpty()) return null
            val activeSig = help.activeSignature?.coerceIn(0, sigs.size - 1) ?: 0
            val activeParam = help.activeParameter?.coerceAtLeast(0) ?: 0
            return SignatureHelpInfo(sigs, activeSig, activeParam)
        }
    }
}

data class SignatureInfo(
    val label: String,
    val documentation: String?,
    val parameters: List<ParameterInfo>,
    val activeParameter: Int?,
) {
    companion object {
        fun fromLsp(info: SignatureInformation?): SignatureInfo? {
            if (info == null) return null
            val label = info.label ?: return null
            val docs = renderDocs(info.documentation)
            val params = info.parameters.orEmpty().mapNotNull { ParameterInfo.fromLsp(it, label) }
            val activeParam = info.activeParameter?.coerceAtLeast(0)
            return SignatureInfo(label, docs, params, activeParam)
        }
    }
}

data class ParameterInfo(
    val labelText: String,
    val labelRange: IntRange?,
    val documentation: String?,
) {
    companion object {
        fun fromLsp(p: ParameterInformation?, signatureLabel: String): ParameterInfo? {
            if (p == null) return null
            val docs = renderDocs(p.documentation)
            val lbl = p.label ?: return null
            return when {
                lbl.isLeft -> {
                    val text = lbl.left ?: return null
                    val idx = signatureLabel.indexOf(text)
                    val range = if (idx >= 0) idx until (idx + text.length) else null
                    ParameterInfo(text, range, docs)
                }
                lbl.isRight -> {
                    val pair: Tuple.Two<Int, Int> = lbl.right ?: return null
                    val s = pair.first ?: return null
                    val e = pair.second ?: return null
                    val start = s.coerceIn(0, signatureLabel.length)
                    val end = e.coerceIn(start, signatureLabel.length)
                    val text = signatureLabel.substring(start, end)
                    ParameterInfo(text, start until end, docs)
                }
                else -> null
            }
        }
    }
}

private fun renderDocs(doc: Either<String, MarkupContent>?): String? {
    if (doc == null) return null
    return when {
        doc.isLeft -> doc.left?.takeIf { it.isNotBlank() }
        doc.isRight -> doc.right?.value?.takeIf { it.isNotBlank() }
        else -> null
    }
}

object SignatureActiveParam {
    fun fromLineText(lineText: String, caretCol: Int): Int? {
        val col = caretCol.coerceIn(0, lineText.length)
        var depth = 0
        var openParenAt = -1
        var i = col - 1
        while (i >= 0) {
            when (lineText[i]) {
                ')', ']', '}' -> depth++
                '(' -> {
                    if (depth == 0) { openParenAt = i; break }
                    depth--
                }
                '[', '{' -> if (depth > 0) depth--
                '"' -> {
                    val end = i
                    var j = i - 1
                    while (j >= 0 && lineText[j] != '"') j--
                    i = j
                    if (i < 0) break
                }
                else -> Unit
            }
            i--
        }
        if (openParenAt < 0) return null
        var commas = 0
        var d = 0
        var k = openParenAt + 1
        var inString = false
        while (k < col) {
            val c = lineText[k]
            if (inString) {
                if (c == '\\' && k + 1 < col) { k += 2; continue }
                if (c == '"') inString = false
                k++
                continue
            }
            when (c) {
                '"' -> inString = true
                '(', '[', '{' -> d++
                ')', ']', '}' -> if (d > 0) d-- else return commas
                ',' -> if (d == 0) commas++
            }
            k++
        }
        return commas
    }
}
