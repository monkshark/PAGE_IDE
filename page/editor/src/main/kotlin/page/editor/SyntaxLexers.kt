package page.editor

import java.nio.file.Path
import java.util.Locale

object SyntaxLexers {
    private const val TREE_SITTER_FLAG = "page.editor.treesitter"

    init {
        val raw = System.getProperty(TREE_SITTER_FLAG) ?: System.getenv("PAGE_EDITOR_TREESITTER")
        if (!raw.isNullOrBlank()) {
            println("[SyntaxLexers] Tree-sitter toggle = '$raw'")
        }
    }

    fun forPath(path: Path): SyntaxLexer? {
        val name = path.fileName?.toString()?.lowercase(Locale.ROOT) ?: return null
        val ext = name.substringAfterLast('.', missingDelimiterValue = "")
        return when (ext) {
            "kt", "kts" -> KotlinLexer
            "java" -> if (treeSitterEnabled("java")) TreeSitterJavaLexer else JavaLexer
            "json" -> JsonLexer
            else -> null
        }
    }

    private fun treeSitterEnabled(language: String): Boolean {
        val v = System.getProperty(TREE_SITTER_FLAG) ?: System.getenv("PAGE_EDITOR_TREESITTER") ?: return false
        if (v.equals("true", ignoreCase = true) || v == "1" || v.equals("all", ignoreCase = true)) return true
        return v.split(',').any { it.trim().equals(language, ignoreCase = true) }
    }
}
