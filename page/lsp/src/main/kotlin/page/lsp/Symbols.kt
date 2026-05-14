package page.lsp

import org.eclipse.lsp4j.SymbolKind

data class SymbolRange(
    val startLine: Int,
    val startCharacter: Int,
    val endLine: Int,
    val endCharacter: Int,
)

data class SymbolLocation(
    val uri: String,
    val range: SymbolRange,
)

data class DocumentSymbolEntry(
    val name: String,
    val detail: String?,
    val kind: SymbolKind?,
    val range: SymbolRange,
    val selectionRange: SymbolRange,
    val containerName: String?,
    val children: List<DocumentSymbolEntry>,
) {
    fun flatten(parent: String? = null): List<DocumentSymbolEntry> {
        val acc = mutableListOf<DocumentSymbolEntry>()
        val display = if (parent.isNullOrBlank()) this else copy(containerName = parent)
        acc.add(display)
        val nextParent = if (parent.isNullOrBlank()) name else "$parent.$name"
        for (child in children) acc += child.flatten(nextParent)
        return acc
    }

    companion object {
        fun fromLspDocumentSymbol(s: org.eclipse.lsp4j.DocumentSymbol): DocumentSymbolEntry =
            DocumentSymbolEntry(
                name = s.name ?: "",
                detail = s.detail,
                kind = s.kind,
                range = s.range.toSymbolRange(),
                selectionRange = (s.selectionRange ?: s.range).toSymbolRange(),
                containerName = null,
                children = s.children.orEmpty().map(::fromLspDocumentSymbol),
            )

        @Suppress("DEPRECATION")
        fun fromLspSymbolInformation(s: org.eclipse.lsp4j.SymbolInformation): DocumentSymbolEntry {
            val r = s.location?.range?.toSymbolRange() ?: SymbolRange(0, 0, 0, 0)
            return DocumentSymbolEntry(
                name = s.name ?: "",
                detail = null,
                kind = s.kind,
                range = r,
                selectionRange = r,
                containerName = s.containerName,
                children = emptyList(),
            )
        }
    }
}

data class WorkspaceSymbolLocated(
    val name: String,
    val containerName: String?,
    val kind: SymbolKind?,
    val location: SymbolLocation?,
)

internal fun org.eclipse.lsp4j.Range.toSymbolRange(): SymbolRange = SymbolRange(
    startLine = start.line,
    startCharacter = start.character,
    endLine = end.line,
    endCharacter = end.character,
)
