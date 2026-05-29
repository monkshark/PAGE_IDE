package page.app.utils

import page.core.PageIdentity
import page.editor.TabBook
import java.nio.file.Path

internal fun windowTitle(path: Path?): String {
    val name = path?.fileName?.toString() ?: "untitled"
    return "$name — ${PageIdentity.NAME}"
}

internal fun applyReplaceToBook(book: TabBook, updates: Map<Path, String>): TabBook {
    if (book.tabs.none { updates.containsKey(it.path) }) return book
    val newTabs = book.tabs.map { tab ->
        val newText = updates[tab.path] ?: return@map tab
        val caret = tab.caret.coerceAtMost(newText.length)
        tab.copy(text = newText, savedText = newText, caret = caret)
    }
    return book.copy(tabs = newTabs)
}

internal fun offsetToLineChar(text: String, offset: Int): Pair<Int, Int> {
    val end = offset.coerceIn(0, text.length)
    var line = 0
    var col = 0
    for (i in 0 until end) {
        if (text[i] == '\n') { line += 1; col = 0 } else col += 1
    }
    return line to col
}

internal fun lineCharToOffset(text: String, line: Int, character: Int): Int {
    var lineIdx = 0
    var i = 0
    while (i < text.length && lineIdx < line) {
        if (text[i] == '\n') lineIdx += 1
        i += 1
    }
    return (i + character.coerceAtLeast(0)).coerceAtMost(text.length)
}

internal fun isKotlinSource(path: Path): Boolean {
    val name = path.fileName?.toString()?.lowercase() ?: return false
    return name.endsWith(".kt") || name.endsWith(".kts")
}
