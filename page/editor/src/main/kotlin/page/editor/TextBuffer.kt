package page.editor

data class LineCol(val line: Int, val col: Int)

class TextBuffer(initial: String = "") {
    private val tree = PieceTree(initial)

    val length: Int get() = tree.length
    val lineCount: Int get() = tree.lineCount

    fun text(): String = tree.text()

    fun lineAt(index: Int): String {
        require(index in 0 until lineCount) { "line $index out of bounds (lineCount=$lineCount)" }
        return tree.lineAt(index)
    }

    fun insert(offset: Int, text: String) {
        require(offset in 0..length) { "offset $offset out of bounds (length=$length)" }
        tree.insert(offset, text)
    }

    fun delete(start: Int, end: Int) {
        require(start in 0..length) { "start $start out of bounds (length=$length)" }
        require(end in start..length) { "end $end out of bounds (start=$start, length=$length)" }
        tree.delete(start, end)
    }

    fun insertAt(line: Int, col: Int, text: String) {
        insert(offsetOf(line, col), text)
    }

    fun deleteAt(startLine: Int, startCol: Int, endLine: Int, endCol: Int) {
        delete(offsetOf(startLine, startCol), offsetOf(endLine, endCol))
    }

    fun offsetOf(line: Int, col: Int): Int {
        require(line in 0 until lineCount) { "line $line out of bounds (lineCount=$lineCount)" }
        return tree.offsetOf(line, col)
    }

    fun lineColOf(offset: Int): LineCol {
        require(offset in 0..length) { "offset $offset out of bounds (length=$length)" }
        return tree.lineColOf(offset)
    }
}
