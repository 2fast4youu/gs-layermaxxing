package at.gregor.layermaxxing

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId

/** Shared fields inside the existing composer paper, not a second send path. */
@Composable
internal fun ReleaseFields(draft: LetterDraft, minimumDelay: Long = 0) {
    if (draft.mode == ComposeMode.MANUAL) {
        Text("Du gibst diesen Brief später ausdrücklich frei. Kein automatischer Freigabezeitpunkt.", fontFamily = Kit.Display)
        return
    }
    Column(Modifier.fillMaxWidth().testTag("release-fields"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            TimeBasis.entries.forEach { basis ->
                TextButton(onClick = { draft.timeBasis = basis }, modifier = Modifier.weight(1f).testTag("release-basis-${basis.name}")) {
                    Text((if (draft.timeBasis == basis) "✓ " else "") + if (basis == TimeBasis.ABSOLUTE) "Absolut" else "Nach dem Senden",
                        fontFamily = Kit.Display, fontSize = 14.sp)
                }
            }
        }
        Text(if (draft.timeBasis == TimeBasis.ABSOLUTE) "Ortszeit · ${ZoneId.systemDefault().id} · eindeutige Uhrzeit erforderlich"
            else "Alle Dauern beginnen gemeinsam beim Senden, nicht beim Schreiben.", fontFamily = Kit.Display, fontSize = 12.sp)
        if (draft.mode == ComposeMode.RANDOM) {
            if (draft.timeBasis == TimeBasis.RELATIVE) {
                NumberAndUnit(draft.randomStart, { draft.randomStart = it }, draft.randomStartUnit, { draft.randomStartUnit = it }, "Beginn nach")
                NumberAndUnit(draft.randomEnd, { draft.randomEnd = it }, draft.randomEndUnit, { draft.randomEndUnit = it }, "Ende nach")
            } else {
                Column(Modifier.testTag("release-window-start")) { Text("Beginn", fontFamily = Kit.Display); DateTimeChooser(draft.windowStart) { draft.windowStart = it } }
                Column(Modifier.testTag("release-window-end")) { Text("Ende", fontFamily = Kit.Display); DateTimeChooser(draft.windowEnd) { draft.windowEnd = it } }
            }
            Text("Der Server wählt zufällig einen Zeitpunkt im Fenster (mindestens 1 Minute).", fontFamily = Kit.Display, fontSize = 12.sp)
        } else if (draft.timeBasis == TimeBasis.RELATIVE) {
            NumberAndUnit(draft.amount, { draft.amount = it }, draft.unit, { draft.unit = it }, "Freigabe nach")
        } else Column(Modifier.testTag("release-point-absolute")) { DateTimeChooser(draft.dateTime) { draft.dateTime = it } }
        Text(draft.releaseSummary(), fontFamily = Kit.Display, modifier = Modifier.testTag("release-summary"))
        runCatching { ReleaseSchedule.resolve(draft, java.time.Instant.now().epochSecond, ZoneId.systemDefault(), minimumDelay) }
            .exceptionOrNull()?.message?.let { Text(it, fontFamily = Kit.Display, modifier = Modifier.testTag("release-error")) }
    }
}
