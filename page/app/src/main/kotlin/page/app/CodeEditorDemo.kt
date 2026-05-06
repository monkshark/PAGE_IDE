package page.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.singleWindowApplication
import page.editor.EditorContent
import page.editor.SplitOrientation
import page.editor.SplitPaneState
import page.ui.CodeEditor
import page.ui.SplitPane

fun main() = singleWindowApplication(title = "CodeEditor Demo") {
    var leftContent by remember {
        mutableStateOf(
            EditorContent.of(
                """
                fun main() {
                    println("hello, code editor")
                    val items = listOf("foo", "bar", "baz")
                    items.forEach { println(it) }
                }
                """.trimIndent(),
                caret = 0,
            ),
        )
    }
    var rightContent by remember {
        mutableStateOf(
            EditorContent.of(
                """
                class Tree(val value: Int) {
                    var left: Tree? = null
                    var right: Tree? = null
                }
                """.trimIndent(),
                caret = 0,
            ),
        )
    }
    var split by remember { mutableStateOf(true) }
    var orientation by remember { mutableStateOf(SplitOrientation.HORIZONTAL) }
    var splitState by remember { mutableStateOf(SplitPaneState(ratio = 0.5f)) }

    val onShortcut: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = handler@{ event ->
        if (event.type != KeyEventType.KeyDown) return@handler false
        if (!event.isCtrlPressed) return@handler false
        when {
            event.key == Key.Backslash && event.isShiftPressed -> {
                orientation = if (orientation == SplitOrientation.HORIZONTAL)
                    SplitOrientation.VERTICAL else SplitOrientation.HORIZONTAL
                true
            }
            event.key == Key.Backslash -> {
                split = !split
                true
            }
            else -> false
        }
    }

    if (split) {
        SplitPane(
            state = splitState,
            onStateChange = { splitState = it },
            orientation = orientation,
            modifier = Modifier.fillMaxSize(),
            first = {
                CodeEditor(
                    content = leftContent,
                    onContentChange = { leftContent = it },
                    modifier = Modifier.fillMaxSize(),
                    onKeyShortcut = onShortcut,
                )
            },
            second = {
                CodeEditor(
                    content = rightContent,
                    onContentChange = { rightContent = it },
                    modifier = Modifier.fillMaxSize(),
                    onKeyShortcut = onShortcut,
                )
            },
        )
    } else {
        CodeEditor(
            content = leftContent,
            onContentChange = { leftContent = it },
            modifier = Modifier.fillMaxSize(),
            onKeyShortcut = onShortcut,
        )
    }
}
