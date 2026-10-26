package at.gregor.layermaxxing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

/** Also used by the startup gate: content is not composed until the report is explicitly dismissed. */
@Composable
internal fun CrashDiagnosticsStartup(content: @Composable () -> Unit) {
    var pending by remember { mutableStateOf(CrashDiagnostics.pending()) }
    var preview by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    if (pending) {
        if (preview) CrashDiagnosticsDialog(onClose = {
            CrashDiagnostics.dismiss(); pending = false
        }) else AlertDialog(
            onDismissRequest = { CrashDiagnostics.dismiss(); pending = false },
            title = { Text("Absturzbericht vorhanden") },
            text = { Column {
                Text("Ein lokaler Java-Absturzbericht ist gespeichert. Du kannst ihn ohne Anmeldung ansehen und bewusst kopieren.")
                if (copied) Text("Log kopiert")
            } },
            confirmButton = { Column {
                TextButton(onClick = { preview = true }) { Text("Fehlerbericht ansehen") }
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(CrashDiagnostics.preview(context))); copied = true
                }) { Text("Log kopieren") }
            } },
            dismissButton = { TextButton(onClick = { CrashDiagnostics.dismiss(); pending = false }) { Text("Später") } },
        )
    } else content()
}

@Composable
internal fun CrashDiagnosticsDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf(CrashDiagnostics.preview(context)) }
    var copied by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf(false) }
    var clearFailed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Fehlerbericht / Log kopieren") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                if (copied) Text("Log kopiert")
                if (cleared) Text("Gespeichertes Log gelöscht")
                if (clearFailed) Text("Log konnte nicht vollständig gelöscht werden")
                ShakeReportSettings(onClose)
                SelectionContainer {
                    Text(text, Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()))
                }
            }
        },
        confirmButton = { TextButton(onClick = {
            clipboard.setText(AnnotatedString(text)); copied = true
        }) { Text("Log kopieren") } },
        dismissButton = {
            Column {
                TextButton(onClick = {
                    cleared = CrashDiagnostics.clear(); clearFailed = !cleared
                    text = CrashDiagnostics.preview(context); copied = false
                }) { Text("Gespeichertes Log löschen") }
                TextButton(onClick = onClose) { Text("Schließen") }
            }
        },
    )
}

@Composable
internal fun CrashDiagnosticsEntry() {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text("Fehlerbericht / Log kopieren") }
    if (open) CrashDiagnosticsDialog(onClose = { open = false })
}

/** Shared host boundary; recording runs before the child, even if its first composition fails. */
@Composable
internal fun CrashDiagnosticsIslandEntry(friends: Int, messages: Int, gridVersion: Int, content: @Composable () -> Unit) {
    remember(friends, messages, gridVersion) {
        CrashDiagnostics.island(CrashIslandView.ENTRY, friends, messages, gridVersion)
        true
    }
    content()
}
