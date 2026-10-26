package at.gregor.layermaxxing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
internal fun ShakeReportSettings(onClose: () -> Unit) {
    val reports = LocalShakeReports.current ?: return
    var confirm by remember { mutableStateOf(false) }
    Column {
        Text("Schüttelbericht · standardmäßig AUS")
        Text("Ein PNG des sichtbaren App-Fensters mit eingebetteter Diagnose. Sichtbare Briefe und Chats können enthalten sein. Diagnose ohne Tokens, Passwörter, Schlüssel oder Nachrichtentexte.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Lokal bei Schütteln speichern", Modifier.weight(1f))
            Switch(checked = reports.enabled, onCheckedChange = { reports.enableCapture(it) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Auto-Upload · Gerfried TESTserver", Modifier.weight(1f))
            Switch(checked = reports.autoUpload, enabled = reports.enabled, onCheckedChange = {
                if (it) confirm = true else reports.setUploadConfirmed(false)
            })
        }
        if (!reports.sensorAvailable) Text("Kein Bewegungssensor: manuelle Aufnahme verwenden.")
        Text(reports.status)
        TextButton(enabled = reports.captureAvailable, onClick = {
            onClose()
            // Let the settings dialog leave the window before capturing the real app UI.
            // No success toast or overlay precedes capture.
            reports.captureAfterDialog()
        }) { Text("App jetzt aufnehmen") }
        TextButton(onClick = { reports.clear() }) { Text("Lokale PNG-Berichte löschen") }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Auto-Upload ausdrücklich erlauben?") },
        text = { Text("Jeder neue Schüttelbericht wird nach lokalem Speichern an den authentifizierten Gerfried TESTserver gesendet, nur im Gerfried-Profil mit bestätigter TEST-Rolle. Das Screenshot-PNG kann sichtbare Briefe, Chats und andere Inhalte zeigen; nur die eingebettete Diagnose ist bereinigt. Serverberichte laufen nach 7 Tagen ab; die Bereinigung erfolgt bei laufendem Testserver regelmäßig (Quoten können ältere Berichte früher entfernen). Ausschalten stoppt neue und laufende Übertragungen soweit möglich; bereits gespeicherte Serverkopien bleiben bestehen.") },
        confirmButton = { TextButton(onClick = { reports.setUploadConfirmed(true); confirm = false }) { Text("Upload erlauben") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Abbrechen") } },
    )
}
