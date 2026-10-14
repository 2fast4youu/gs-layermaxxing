package at.gregor.layermaxxing

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
internal fun FriendSheet(
    friend: ApiClient.UserSummary,
    info: ApiClient.IslandInfo?,
    label: String?,
    onLabel: (String?) -> Unit,
    letters: List<ApiClient.Message>,
    opened: Map<Long, OpenedMessage>,
    quests: List<ApiClient.Quest>,
    topicCount: Int,
    onChat: () -> Unit,
    onLetter: () -> Unit,
    onOpenLetter: (ApiClient.Message) -> Unit,
    onLockedTap: (ApiClient.Message) -> Unit,
    onTopics: () -> Unit,
    onVisit: (() -> Unit)?,
    onNewQuest: () -> Unit,
    onQuestDone: (ApiClient.Quest, Boolean) -> Unit,
    letterEnabled: Boolean = true,
    onLetters: (() -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    val level = info?.level ?: 1
    var questsExpanded by rememberSaveable(friend.id) { mutableStateOf(true) }
    var completedExpanded by rememberSaveable(friend.id) { mutableStateOf(false) }
    var profileMenu by remember { mutableStateOf(false) }
    val activeQuests = quests.filter { it.completedAt == null }.sortedByDescending { it.createdAt }
    val completedQuests = quests.filter { it.completedAt != null }.sortedByDescending { it.completedAt }
    var lettersExpanded by rememberSaveable(friend.id) { mutableStateOf(false) }
    var extraExpanded by rememberSaveable(friend.id) { mutableStateOf(false) }
    var naming by rememberSaveable { mutableStateOf(false) }
    if (naming) LabelDialog(friend.name, label, onDismiss = { naming = false }) { onLabel(it); naming = false }
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).background(profileColor(friend.displayColor).copy(alpha = .18f), CircleShape), Alignment.Center) {
                    Text(friend.avatarEmoji, fontSize = 26.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(friend.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Isle.Ink)
                    Text(
                        (label?.let { "$it · " } ?: "") + "${Isle.levelName(level)} · Stufe $level",
                        color = Isle.Muted, fontSize = 13.sp,
                    )

                }
                Box {
                    TextButton(onClick = { profileMenu = true }) { AppIcon(R.drawable.ico_menu, "Profilaktionen", size = 22.dp) }
                    androidx.compose.material3.DropdownMenu(profileMenu, { profileMenu = false }) {
                        androidx.compose.material3.DropdownMenuItem(text = { Text("Eigenen Namen ändern") }, onClick = { profileMenu = false; naming = true })
                    }
                }
                HubIsland(info?.island?.decor.orEmpty(), Modifier.width(84.dp), labels = false, seed = friend.id, animate = false, life = info?.island, lifeTags = false)
            }
            Spacer(Modifier.height(10.dp))
            if (info != null) {
            LinearProgressIndicator(
                progress = { IsleLayout.progress(info) }, color = Isle.Teal, trackColor = Isle.Sand,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
            )
            Text(
                info?.nextAt?.let { "Noch ${it - info.score} Punkte bis „${Isle.levelName(level + 1)}“ · am schnellsten mit gemeinsamen Quests" }
                    ?: "Eure Insel ist voll ausgebaut",
                color = Isle.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
            )
            }
            Spacer(Modifier.height(14.dp))
            onVisit?.let { visit ->
                OutlinedButton(onClick = visit, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    AppIcon(R.drawable.ico_island, null, size = 18.dp, modifier = Modifier.padding(end = 8.dp)); Text("Insel von ${friend.name} besuchen")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onChat, Modifier.weight(1f).heightIn(min = 48.dp), colors = ButtonDefaults.buttonColors(containerColor = Isle.Teal)) { AppIcon(R.drawable.ico_chat, null, size = 18.dp, modifier = Modifier.padding(end = 8.dp)); Text("Chat") }
                if (letterEnabled) OutlinedButton(onClick = onLetter, Modifier.weight(1f).heightIn(min = 48.dp)) { AppIcon(R.drawable.ico_letter, null, size = 18.dp, modifier = Modifier.padding(end = 8.dp)); Text("Brief") }
            }
            if (activeQuests.isNotEmpty()) TextButton(onClick = { questsExpanded = !questsExpanded }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Offene Quests · ${activeQuests.size} ${if (questsExpanded) "▴" else "▾"}") }
            TextButton(onClick = onNewQuest) { Text("Neue Quest") }
        }
        items(if (questsExpanded) activeQuests else emptyList(), key = { "q${it.id}" }) { quest ->
            val fresh = quest.createdAt >= java.time.Instant.now().epochSecond - 86_400
            Column(Modifier.fillMaxWidth().background(if (fresh) Isle.Teal.copy(alpha = .08f) else Color.Transparent, RoundedCornerShape(16.dp))) {
                if (fresh) Text("NEU", fontSize = 11.sp, color = Isle.TealDark, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp, top = 6.dp))
                QuestRow(quest, onQuestDone, null)
            }
        }
        if (completedQuests.isNotEmpty()) item {
            TextButton(onClick = { completedExpanded = !completedExpanded }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Erledigte Quests · ${completedQuests.size} ${if (completedExpanded) "▴" else "▾"}") }
        }
        items(if (completedExpanded) completedQuests else emptyList(), key = { "done${it.id}" }) { QuestRow(it, onQuestDone, null) }
        item {
            val received = letters.filter { it.incoming }.sortedByDescending { it.createdAt }
            val sent = letters.filter { !it.incoming }.sortedByDescending { it.createdAt }
            if (letters.isNotEmpty()) TextButton(onClick = { lettersExpanded = !lettersExpanded }, Modifier.fillMaxWidth().heightIn(min = 48.dp).background(Color(0xFFFBEEE9), RoundedCornerShape(12.dp))) {
                AppIcon(R.drawable.ico_letter, null, tint = Color(0xFFA34F43), size = 20.dp, modifier = Modifier.padding(end = 8.dp))
                Text("Briefe · ${letters.size} ${if (lettersExpanded) "▴" else "▾"}", color = Color(0xFFA34F43))
            }
            if (lettersExpanded) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LetterColumn("Empfangen", received, opened, onOpenLetter, onLockedTap, Modifier.weight(1f))
                LetterColumn("Gesendet", sent, opened, onOpenLetter, onLockedTap, Modifier.weight(1f))
            }
            if (lettersExpanded && onLetters != null) TextButton(onClick = onLetters) { Text("Alle Briefe und Punkte öffnen") }
            if (topicCount > 0) TextButton(onClick = onTopics, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 48.dp).background(Isle.Teal.copy(alpha = .08f), RoundedCornerShape(12.dp))) {
                AppIcon(R.drawable.ico_topics, null, tint = Isle.TealDark, size = 18.dp, modifier = Modifier.padding(end = 8.dp))
                Text("Themen mit ${friend.name}${if (topicCount > 0) " · $topicCount offen" else ""}", color = Isle.TealDark)
            }
            extra?.let { content ->
                TextButton(onClick = { extraExpanded = !extraExpanded }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Weitere Optionen ${if (extraExpanded) "▴" else "▾"}", color = Isle.Muted, fontSize = 12.sp) }
                if (extraExpanded) content()
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LetterColumn(
    title: String, letters: List<ApiClient.Message>, opened: Map<Long, OpenedMessage>,
    onOpen: (ApiClient.Message) -> Unit, onLocked: (ApiClient.Message) -> Unit, modifier: Modifier,
) {
    Column(modifier.background(Isle.Sand, RoundedCornerShape(16.dp)).padding(10.dp)) {
        Text(title, fontWeight = FontWeight.Bold, color = Isle.Ink, fontSize = 13.sp)
        if (letters.isEmpty()) Text("–", color = Isle.Muted, fontSize = 13.sp)
        letters.take(4).forEach { m ->
            val state = Conversations.letterState(m, opened[m.id] != null)
            val boat = LetterBoat.forMode(m.mode)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { if (state.locked) onLocked(m) else onOpen(m) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(if (state.locked) boat.badge else if (state == LetterState.READY) R.drawable.ico_letter_ready else R.drawable.ico_letter_open, null, tint = Isle.TealDark, size = 17.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    Conversations.letterPreviewText(m), fontSize = 12.sp, color = Isle.Ink, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (state.locked) AppIcon(R.drawable.ico_lock, null, size = 12.dp)
            }
        }
        if (letters.size > 4) Text("+${letters.size - 4} weitere", fontSize = 11.sp, color = Isle.Muted)
    }
}

