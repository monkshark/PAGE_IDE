package page.editor

data class TextEdit(val text: String, val caret: Int)

object AutoClose {
    private val pairs = mapOf(
        '(' to ')',
        '[' to ']',
        '{' to '}',
        '"' to '"',
        '\'' to '\'',
    )
    private val closers = pairs.values.toSet()

    fun apply(old: TextEdit, new: TextEdit): TextEdit {
        if (new.text.length != old.text.length + 1) return new
        if (new.caret != old.caret + 1) return new
        val cursor = new.caret
        val inserted = new.text.getOrNull(cursor - 1) ?: return new

        if (inserted in closers) {
            val charAtOldCaret = old.text.getOrNull(old.caret)
            if (charAtOldCaret == inserted) {
                return TextEdit(old.text, cursor)
            }
        }

        val closer = pairs[inserted] ?: return new

        val nextChar = new.text.getOrNull(cursor)
        if (nextChar != null && (nextChar.isLetterOrDigit() || nextChar == '_')) return new

        if (inserted == '"' || inserted == '\'') {
            val prevChar = new.text.getOrNull(cursor - 2)
            if (prevChar != null && (prevChar.isLetterOrDigit() || prevChar == '_')) return new
        }

        val withCloser = new.text.substring(0, cursor) + closer + new.text.substring(cursor)
        return TextEdit(withCloser, cursor)
    }
}
