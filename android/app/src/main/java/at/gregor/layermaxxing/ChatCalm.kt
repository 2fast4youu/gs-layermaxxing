package at.gregor.layermaxxing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One optional summary, never feature cards among messages. */
internal object ChatCalm {
    fun hint(band: List<SealBadge>, requests: Int, outgoingProposal: Boolean, openQuests: Int): String? =
        listOfNotNull(
            band.filter { it.bucket == LetterBucket.READY }.sumOf { it.count }.takeIf { it > 0 }?.let { "$it Briefe bereit" },
            band.filter { it.bucket == LetterBucket.WAITING_ON_YOU }.sumOf { it.count }.takeIf { it > 0 }?.let { "$it Briefe warten auf dich" },
            requests.takeIf { it > 0 }?.let { "$it Anfragen" },
            "Regelvorschlag wartet".takeIf { outgoingProposal },
            openQuests.takeIf { it > 0 }?.let { "$it Quests offen" },
        ).joinToString(" · ").ifBlank { null }
}

@Composable
internal fun ChatActionHint(text: String?, onOpen: () -> Unit) {
    if (text != null) Text(text, Modifier.fillMaxWidth().clickable(onClickLabel = "Freundesansicht öffnen", onClick = onOpen)
        .padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 12.sp,
        maxLines = 1, overflow = TextOverflow.Ellipsis, color = Harbour.palette().seaDeep)
}

@Composable
internal fun ChatAttachmentMenu(plan: ComposerPlan, extras: ComposerExtras?, onLetter: () -> Unit,
    onQuest: (() -> Unit)?, onClose: () -> Unit) {
    if (plan.inputEnabled && extras != null) {
        extras.onCamera?.let { camera -> SheetAction(R.drawable.ico_camera, "Kamera") { onClose(); camera() } }
        SheetAction(R.drawable.ico_camera, "Galerie") { onClose(); extras.onPhoto() }
        SheetAction(R.drawable.ico_emoji, "Sticker") { onClose(); extras.onStickers() }
    }
    if (plan.sealVisible) SheetAction(R.drawable.ico_letter, "Brief schreiben", onClick = onLetter)
    onQuest?.let { SheetAction(R.drawable.ico_points, "Neue Quest", onClick = it) }
}
