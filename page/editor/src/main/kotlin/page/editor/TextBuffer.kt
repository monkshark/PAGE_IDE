package page.editor

class TextBuffer(initial: String = "") {
    private val sb = StringBuilder(initial)

    val length: Int get() = sb.length

    fun insert(offset: Int, text: String) {
        sb.insert(offset, text)
    }

    fun delete(start: Int, end: Int) {
        sb.delete(start, end)
    }

    fun text(): String = sb.toString()
}
