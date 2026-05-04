package page.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import page.core.PageIdentity
import page.editor.FileDocument
import page.ui.GlassTheme
import java.nio.file.Path

fun main() = application {
    val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
    var path: Path? by remember { mutableStateOf(null) }
    var value by remember { mutableStateOf(TextFieldValue("")) }

    val openFile: (java.awt.Frame) -> Unit = { parent ->
        FileDialogs.open(parent)?.let { picked ->
            value = TextFieldValue(FileDocument.load(picked))
            path = picked
        }
    }
    val saveFile: (java.awt.Frame) -> Unit = { parent ->
        val target = path ?: FileDialogs.saveAs(parent)
        if (target != null) {
            FileDocument.save(target, value.text)
            path = target
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = windowTitle(path),
    ) {
        val frame = window
        GlassTheme {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.isCtrlPressed) {
                            when (event.key) {
                                Key.O -> { openFile(frame); true }
                                Key.S -> { saveFile(frame); true }
                                else -> false
                            }
                        } else false
                    },
                color = MaterialTheme.colorScheme.background,
            ) {
                Shell(
                    path = path,
                    value = value,
                    onValueChange = { value = it },
                )
            }
        }
    }
}

private fun windowTitle(path: Path?): String {
    val name = path?.fileName?.toString() ?: "untitled"
    return "$name — ${PageIdentity.NAME}"
}

@Composable
private fun Shell(
    path: Path?,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TitleBar(path = path)
        Row(modifier = Modifier.fillMaxSize()) {
            FileTreePanel(modifier = Modifier.width(240.dp).fillMaxHeight())
            Divider()
            EditorPanel(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun TitleBar(path: Path?) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(36.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = PageIdentity.NAME,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "v${PageIdentity.VERSION}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(20.dp))
            Text(
                text = path?.toString() ?: "untitled",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FileTreePanel(modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Files",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "(empty)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outline),
    )
}
