package page.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import page.lsp.LanguageDefinition
import page.ui.GlassTheme
import java.awt.Desktop
import java.net.URI

@Composable
internal fun InstallGuideDialog(
    definition: LanguageDefinition,
    attempted: List<String>,
    onOpenGuide: (String) -> Unit = { url -> runCatching { Desktop.getDesktop().browse(URI(url)) } },
    onDismiss: () -> Unit,
) {
    val initialOs = remember { InstallGuide.initialOsKey() }
    var selectedOs by remember { mutableStateOf(initialOs) }
    val targetWidth = 560.dp
    val targetHeight = 360.dp
    val state = rememberDialogState(
        position = WindowPosition.Aligned(Alignment.Center),
        width = targetWidth,
        height = targetHeight,
    )
    LaunchedEffect(Unit) {
        state.size = DpSize(targetWidth, targetHeight)
    }
    DialogWindow(
        onCloseRequest = onDismiss,
        state = state,
        title = "Install ${definition.displayName} LSP",
        resizable = false,
        undecorated = true,
        alwaysOnTop = true,
        onPreviewKeyEvent = { event ->
            if (event.type != KeyEventType.KeyDown) false
            else when (event.key) {
                Key.Escape -> { onDismiss(); true }
                Key.Enter, Key.NumPadEnter -> { onDismiss(); true }
                else -> false
            }
        },
    ) {
        GlassTheme {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                    Text(
                        text = "Install ${definition.displayName} LSP",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "PAGE could not find a language server for ${definition.displayName}. Pick your platform and follow the steps below.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = LocalTextStyle.current.copy(fontSize = 11.sp),
                    )
                    Spacer(Modifier.height(10.dp))
                    OsTabRow(selected = selectedOs, onSelect = { selectedOs = it })
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(12.dp),
                    ) {
                        val scroll = rememberScrollState()
                        Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll)) {
                            SectionLabel("Install")
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = InstallGuide.installText(definition, selectedOs),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = LocalTextStyle.current.copy(
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                ),
                            )
                            Spacer(Modifier.height(10.dp))
                            SectionLabel("Expected on PATH")
                            Spacer(Modifier.height(4.dp))
                            val bins = InstallGuide.expectedBinaries(definition, selectedOs)
                            Text(
                                text = bins.joinToString("  ·  "),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = LocalTextStyle.current.copy(
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                ),
                            )
                            if (attempted.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                SectionLabel("Tried")
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = InstallGuide.formatAttempted(attempted),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = LocalTextStyle.current.copy(
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "After install, restart PAGE.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = LocalTextStyle.current.copy(fontSize = 10.sp),
                        )
                        Spacer(Modifier.weight(1f))
                        InstallGuideButton(
                            label = "Open install guide",
                            primary = false,
                            onClick = { onOpenGuide(definition.installGuideUrl) },
                        )
                        Spacer(Modifier.width(8.dp))
                        InstallGuideButton(label = "Done", primary = true, onClick = onDismiss)
                    }
                }
            }
        }
    }
}

@Composable
private fun OsTabRow(selected: String, onSelect: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        for ((index, key) in InstallGuide.OS_KEYS.withIndex()) {
            if (index > 0) Spacer(Modifier.width(8.dp))
            OsTab(label = InstallGuide.osLabel(key), active = key == selected, onClick = { onSelect(key) })
        }
    }
}

@Composable
private fun OsTab(label: String, active: Boolean, onClick: () -> Unit) {
    val bg = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    else MaterialTheme.colorScheme.surface
    val fg = if (active) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant
    val borderAlpha = if (active) 0.7f else 0.4f
    Row(
        modifier = Modifier
            .height(26.dp)
            .background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = borderAlpha))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = fg,
            style = LocalTextStyle.current.copy(fontSize = 11.sp),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = LocalTextStyle.current.copy(fontSize = 10.sp),
    )
}

@Composable
private fun InstallGuideButton(label: String, primary: Boolean, onClick: () -> Unit) {
    val bg = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .height(28.dp)
            .background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = fg,
            style = LocalTextStyle.current.copy(fontSize = 11.sp),
        )
    }
}
