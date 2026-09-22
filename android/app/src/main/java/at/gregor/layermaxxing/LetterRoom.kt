package at.gregor.layermaxxing

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Instant
import kotlin.math.max

/**
 * The Briefraum: the letter side of one friendship, as letters.
 *
 * Sealed letters are the significant genre of this app, so they get a place of
 * their own instead of drowning as dark cards between chat bubbles. The material
 * here is the envelope itself — the same `fief_env_*` seals the valley's board
 * uses, so a letter looks the same wherever it is met — laid out as stacks per
 * state with the opened history folded away at the end.
 *
 * It is one tap from the conversation and needs no valley, no castle flag and no
 * build ledger: it reads the same `Conversations` model the timeline reads.
 */

private val ROOM_BG = Color(0xFF241D14)
private val ROOM_PAPER = Color(0xFFEFE3C8)
private val ROOM_INK = Color(0xFF3B3122)
private val ROOM_INK_SOFT = Color(0xFF6C5F49)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LetterRoom(
    friendName: String,
    letters: List<ApiClient.Message>,
    opened: Map<Long, OpenedMessage>,
    action: LetterAction,
    token: String,
    api: ApiClient,
    act: ((suspend () -> Unit) -> Unit),
    creativeActive: Boolean,
    opportunities: Map<Long, EpOpportunity>,
    focusLetterId: Long?,
    onFocusConsumed: () -> Unit,
    onBack: () -> Unit,
    onCompose: () -> Unit,
    onManageRules: () -> Unit,
    onOpenLetter: (ApiClient.Message) -> Unit,
    onLockedTap: (ApiClient.Message) -> Unit,
    onProof: (ApiClient.Message) -> Unit,
    onProposeEp: (EpOpportunity) -> Unit,
) {
    var now by remember { mutableStateOf(Instant.now().epochSecond) }
    LaunchedEffect(Unit) { while (true) { now = Instant.now().epochSecond; delay(1_000) } }
    var detailId by remember { mutableStateOf<Long?>(null) }
    var historyOpen by remember { mutableStateOf(false) }
    val groups = Conversations.groupedLetters(letters, opened.keys)

    // Arriving from a letter mentioned in the chat: open exactly that letter.
    LaunchedEffect(focusLetterId) {
        focusLetterId?.let { detailId = it }
        if (focusLetterId != null) onFocusConsumed()
    }

    Column(Modifier.fillMaxSize().background(ROOM_BG)) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(50)).clickable(onClick = onBack)
                    .semantics { contentDescription = "Zurück ins Gespräch mit $friendName"; role = Role.Button },
                contentAlignment = Alignment.Center,
            ) { Text("←", fontSize = 22.sp, color = ROOM_PAPER) }
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Text("Briefraum", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = ROOM_PAPER)
                Text(
                    if (letters.isEmpty()) friendName else "$friendName · ${letters.size} Briefe",
                    fontSize = 12.sp, color = ROOM_INK_SOFT,
                )
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (letters.isEmpty()) item {
                Text(
                    "Noch keine Briefe mit $friendName.", Modifier.padding(vertical = 28.dp),
                    color = ROOM_INK_SOFT,
                )
            }
            groups.forEach { group ->
                val history = group.bucket == LetterBucket.OPENED_HISTORY
                item(key = "head-${group.bucket.name}") {
                    if (history) Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp)).clickable { historyOpen = !historyOpen }
                            .semantics {
                                contentDescription = "Verlauf, ${group.messages.size} Briefe"
                                role = Role.Button
                            }
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${if (historyOpen) "▾" else "▸"} Verlauf (${group.messages.size})",
                            color = ROOM_INK_SOFT, fontWeight = FontWeight.SemiBold,
                        )
                    } else Text(
                        group.bucket.label.uppercase(),
                        Modifier.padding(top = 16.dp, bottom = 2.dp, start = 4.dp),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp, color = ROOM_INK_SOFT,
                    )
                }
                if (!history || historyOpen) items(group.messages, key = { "room-${it.id}" }) { message ->
                    EnvelopeRow(
                        message = message,
                        state = Conversations.letterState(message, message.id in opened.keys),
                        now = now,
                        hasEpOffer = message.id in opportunities,
                        onTap = { detailId = message.id },
                        onLongPress = { onProof(message) },
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        if (action.enabled) Button(
            onClick = onCompose,
            modifier = Modifier.fillMaxWidth().padding(14.dp).height(52.dp),
        ) { Text(LetterAccess.LABEL_LONG, fontWeight = FontWeight.Bold) }
        else action.reason?.let { reason ->
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(reason, Modifier.weight(1f), fontSize = 12.sp, color = ROOM_INK_SOFT)
                Button(onClick = onManageRules, modifier = Modifier.heightIn(min = 48.dp)) { Text("Regeln") }
            }
        }
    }

    detailId?.let { id ->
        val message = letters.firstOrNull { it.id == id }
        if (message == null) detailId = null else ModalBottomSheet(onDismissRequest = { detailId = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                LetterCard(
                    message = message, opened = opened[message.id], now = now, token = token, api = api,
                    act = act, onOpenLetter = onOpenLetter, onLockedTap = onLockedTap, onProof = onProof,
                    creativeActive = creativeActive,
                )
                opportunities[message.id]?.let { opportunity ->
                    EpOpportunityCard(opportunity) { onProposeEp(it); detailId = null }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * One letter as the envelope it is: seal, title, and one short state tag.
 *
 * The image and the tag come from the same two functions the valley's board
 * uses, so the board and this room can never disagree about a letter's state.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EnvelopeRow(
    message: ApiClient.Message,
    state: LetterState,
    now: Long,
    hasEpOffer: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val asset = FiefScenes.envelopeAsset(state, message.incoming)
    val tag = FiefScenes.envelopeTag(state, max(0L, (message.releaseAt ?: now) - now))
    val title = message.title.ifBlank { Conversations.SEALED_FALLBACK }
    val direction = if (message.incoming) "von ${message.peerName}" else "an ${message.peerName}"
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .semantics {
                contentDescription = "$title, $direction, ${stateWord(state)}"
                role = Role.Button
            }
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(62.dp), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(envelopeDrawable(asset)),
                contentDescription = null,
                modifier = Modifier.size(54.dp),
                contentScale = ContentScale.Fit,
            )
        }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                title, color = ROOM_PAPER, maxLines = 1,
                fontWeight = if (state == LetterState.READY) FontWeight.Bold else FontWeight.SemiBold,
            )
            Text(direction, fontSize = 12.sp, color = ROOM_INK_SOFT)
            if (message.coverNote.isNotBlank()) Text(
                "„${message.coverNote}“", fontSize = 12.sp, color = ROOM_INK_SOFT,
                fontStyle = FontStyle.Italic, maxLines = 1,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (state == LetterState.READY) Surface(
                shape = RoundedCornerShape(7.dp), color = Color(0xF0D9A441), contentColor = Color(0xFF32270F),
            ) {
                Text(
                    "✦ bereit", Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                )
            } else tag?.let {
                Surface(shape = RoundedCornerShape(7.dp), color = ROOM_PAPER, contentColor = ROOM_INK) {
                    Text(
                        it, Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (hasEpOffer) Text("★", fontSize = 12.sp, color = Color(0xFFD9A441))
        }
    }
}

/**
 * The compact letter mark inside the normal chat flow.
 *
 * A letter is an event in the conversation, not a wall of card: this row says
 * that one happened, in which direction and what it is waiting for, and hands
 * over to the Briefraum in one tap. Anything released, waiting on me or opened
 * stays visible here, so nothing significant only lives behind another screen.
 */
@Composable
internal fun LetterActivityRow(
    message: ApiClient.Message,
    state: LetterState,
    now: Long,
    outgoing: Boolean,
    pulsing: Boolean,
    onOpenRoom: () -> Unit,
) {
    val tag = FiefScenes.envelopeTag(state, max(0L, (message.releaseAt ?: now) - now))
    val title = message.title.ifBlank { Conversations.SEALED_FALLBACK }
    val accent = when {
        state == LetterState.READY -> MaterialTheme.colorScheme.tertiary
        state == LetterState.LOCKED_MUTUAL_WAITING_ME -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline
    }
    Row(
        Modifier.fillMaxWidth(if (outgoing) .82f else .82f).heightIn(min = 56.dp)
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp))
            .background(accent.copy(alpha = if (pulsing) .30f else .12f))
            .clickable(onClick = onOpenRoom)
            .semantics {
                contentDescription = "$title, ${stateWord(state)}. Im Briefraum öffnen"
                role = Role.Button
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            FiefScenes.envelopeAsset(state, message.incoming).let(::envelopeGlyph),
            fontSize = 20.sp, modifier = Modifier.padding(end = 10.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, fontSize = 14.sp)
            Text(
                listOfNotNull(stateWord(state), tag?.takeIf { state != LetterState.READY }).joinToString(" · "),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One word for a letter's situation. Used for both the label and the reader. */
private fun stateWord(state: LetterState): String = when (state) {
    LetterState.READY -> "bereit zum Öffnen"
    LetterState.OPENED -> "geöffnet"
    LetterState.DELIVERED -> "zugestellt"
    LetterState.READ -> "gelesen"
    LetterState.LOCKED_MANUAL -> "wartet auf manuelle Freigabe"
    LetterState.LOCKED_MUTUAL_WAITING_ME -> "wartet auf deine Freigabe"
    LetterState.LOCKED_MUTUAL_WAITING_PEER -> "wartet auf die Gegenseite"
    LetterState.LOCKED_PRESENCE -> "wartet auf gemeinsame Anwesenheit"
    LetterState.LOCKED_RANDOM -> "im Zufallsfenster unterwegs"
    LetterState.LOCKED_TIMED -> "unterwegs"
}

/** The envelope drawables, resolved once. Mirrors the valley's own mapping. */
private fun envelopeDrawable(asset: String): Int = when (asset) {
    FiefAssets.ENV_READY -> R.drawable.fief_env_ready
    FiefAssets.ENV_OPEN -> R.drawable.fief_env_open
    FiefAssets.ENV_OUT -> R.drawable.fief_env_out
    else -> R.drawable.fief_env_sealed
}

/** The same four states as a glyph, for the one-line mark inside the chat. */
private fun envelopeGlyph(asset: String): String = when (asset) {
    FiefAssets.ENV_READY -> "✦"
    FiefAssets.ENV_OPEN -> "📖"
    FiefAssets.ENV_OUT -> "✉"
    else -> "🔒"
}
