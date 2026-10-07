package at.gregor.layermaxxing

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.drawBehind
import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

/**
 * The chat overview: friends and live conversations are the list itself.
 *
 * Two round actions sit above it — find a person, and send a Freundesfunke.
 * Requests that wait for an answer appear contextually at the top; rare people
 * management stays behind the finding action.
 */
@Composable
internal fun ChatsScreen(
    conversations: List<Conversation>, requests: List<PendingRequest>,
    sparkInbox: List<SparkItem>, sparkSent: List<SparkSent>,
    onOpen: (Long) -> Unit, onRespond: (PendingRequest, Boolean) -> Unit, onGoPeople: () -> Unit,
    onOpenSparks: () -> Unit, onSendSpark: () -> Unit, sparkEnabled: Boolean,
    onGroups: () -> Unit, onTopicsHub: () -> Unit, onGlossary: () -> Unit, onValley: () -> Unit,
    groupCount: Int, topicCount: Int, showValley: Boolean,
    refreshing: Boolean = false, onRefresh: () -> Unit = {},
) {
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ChatTools.ListFilter.ALL) }
    val now = remember(conversations) { Instant.now().epochSecond }
    val matches = ChatTools.filterConversations(conversations, search, filter)
    val p = Harbour.palette()
    @OptIn(ExperimentalMaterial3Api::class)
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().harbourPaper(p)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 150.dp)) {
            val firstRun = conversations.isEmpty() && requests.isEmpty()
            if (!firstRun) item {
                // The harbour's logbook head: one line of news, search beneath it.
                val news = conversations.count { it.hasNews }
                Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 2.dp)) {
                    Text(
                        when { news == 0 -> "Alles ist gelesen"; news == 1 -> "1 Gespräch wartet auf dich"; else -> "$news Gespräche warten auf dich" },
                        fontSize = 13.sp, color = p.inkSoft, fontFamily = Kit.Body,
                    )
                }
            }
            if (!firstRun) item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp).heightIn(min = 46.dp)
                        .background(p.card.copy(alpha = if (p.dark) .9f else .85f), RoundedCornerShape(14.dp))
                        .border(1.dp, p.line, RoundedCornerShape(14.dp))
                        .padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(R.drawable.ico_search, null, tint = p.inkSoft, size = 20.dp)
                    androidx.compose.foundation.text.BasicTextField(
                        search, { search = it }, Modifier.weight(1f).padding(start = 10.dp, top = 12.dp, bottom = 12.dp),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = p.ink, fontFamily = Kit.Body),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(p.sea),
                        decorationBox = { inner ->
                            Box { if (search.isEmpty()) Text("Freunde und Nachrichten suchen", fontSize = 15.sp, color = p.inkSoft.copy(alpha = .8f), maxLines = 1); inner() }
                        },
                    )
                    if (search.isNotEmpty()) Box(
                        Modifier.size(40.dp).clip(CircleShape).clickable { search = "" }
                            .semantics { contentDescription = "Suche leeren"; role = Role.Button },
                        contentAlignment = Alignment.Center,
                    ) { AppIcon(R.drawable.ico_close, null, tint = p.inkSoft, size = 20.dp) }
                }
            }
            if (!firstRun) item {
                // Chips wrap instead of scrolling: nothing hides behind the right edge.
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val useful = ChatTools.ListFilter.entries.filter { f ->
                        f == ChatTools.ListFilter.ALL || f == filter || ChatTools.filterConversations(conversations, "", f).isNotEmpty()
                    }
                    useful.forEach { f ->
                        val count = if (f == ChatTools.ListFilter.UNREAD) conversations.count { it.hasNews } else 0
                        ListChip(if (count > 0) "${f.label} $count" else f.label, selected = filter == f, p = p) { filter = f }
                    }
                    // The shared places are always one tap from the main menu, even before the first one exists.
                    ListChip(if (groupCount > 0) "Gruppen $groupCount" else "+ Gruppe", selected = false, p = p, onClick = onGroups)
                    ListChip(if (topicCount > 0) "Themen $topicCount" else "+ Thema", selected = false, p = p, onClick = onTopicsHub)
                    ListChip("Wörterbuch", selected = false, p = p, onClick = onGlossary)
                    if (showValley) ListChip("Inseln", selected = false, p = p, onClick = onValley)
                }
            }
            if (requests.isNotEmpty()) items(requests, key = { it.key }) { request ->
                Box(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) { RequestCard(request, onRespond, p) }
            }
            if (Sparks.entryVisible(sparkInbox, sparkSent)) item {
                Box(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) { SparkEntryRow(sparkInbox, sparkSent, onOpenSparks, p) }
            }
            if (conversations.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Spacer(Modifier.height(48.dp))
                    IslandAvatar("⛵", p.sea, 92.dp, p)
                    Text("Dein Hafen ist noch still", style = Harbour.Title, color = p.ink, textAlign = TextAlign.Center)
                    Text(
                        "Füge eine Person über ihren Benutzernamen hinzu. Sobald sie annimmt, legt euer erstes Boot ab.",
                        color = p.inkSoft, textAlign = TextAlign.Center, fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = onGoPeople, modifier = Modifier.height(50.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = p.seaDeep, contentColor = Color.White),
                    ) { Text("+  Person hinzufügen", fontWeight = FontWeight.Bold) }
                }
            } else if (matches.isEmpty()) item {
                Text(
                    if (search.isNotBlank()) "Keine Chats zu „${search.trim()}“." else "Hier ist gerade nichts.",
                    Modifier.fillMaxWidth().padding(28.dp), textAlign = TextAlign.Center,
                    color = p.inkSoft,
                )
            }
            items(matches, key = { "conv-${it.friendId}" }) { conversation -> ConversationRow(conversation, now, onOpen, p) }
        }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (sparkEnabled) RoundAction(
                description = "Freundesfunke senden",
                container = p.card, content = p.gold, onClick = onSendSpark, size = 46, border = p.line,
            ) { AppIcon(R.drawable.ico_spark, null, tint = p.gold, size = 22.dp) }
            if (conversations.isNotEmpty() || requests.isNotEmpty()) Row(
                Modifier.heightIn(min = 56.dp)
                    .shadow(6.dp, RoundedCornerShape(28.dp))
                    .background(Brush.verticalGradient(listOf(p.sea, p.seaDeep)), RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp)).clickable(onClick = onGoPeople)
                    .semantics { contentDescription = "Neuer Chat: Person finden oder Freundschaftsanfrage senden"; role = Role.Button }
                    .padding(start = 18.dp, end = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(R.drawable.ico_add, null, tint = Color.White, size = 22.dp)
                Spacer(Modifier.width(8.dp))
                Text("Neuer Chat", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
    }
}

/** A filter or shortcut chip of the chat list: a small paper tab, sea-glass when chosen. */
@Composable
private fun ListChip(text: String, selected: Boolean, p: HarbourPalette, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) p.seaDeep else p.card.copy(alpha = .75f))
            .border(1.dp, if (selected) Color.Transparent else p.line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1,
            color = if (selected) Color.White else p.ink)
    }
}

/** A round action of the overview. */
@Composable
private fun RoundAction(
    description: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    size: Int = 56,
    border: Color = Color.Transparent,
    glyph: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.size(size.dp)
            .semantics { contentDescription = description; role = Role.Button }
            .clip(RoundedCornerShape(50)).clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = content,
        border = BorderStroke(1.dp, border),
        shadowElevation = 4.dp,
    ) { Box(contentAlignment = Alignment.Center) { glyph() } }
}

/**
 * The quiet entry to the spark inbox.
 *
 * It exists only once a spark has actually been received or sent, and it never
 * names a sender: the count is all there is to say.
 */
@Composable
private fun SparkEntryRow(inbox: List<SparkItem>, sent: List<SparkSent>, onOpen: () -> Unit, p: HarbourPalette) {
    val unopened = Sparks.unopenedCount(inbox)
    val underway = sent.count { !it.opened }
    val line = when {
        unopened > 0 -> if (unopened == 1) "1 neuer Funke" else "$unopened neue Funken"
        underway > 0 -> if (underway == 1) "1 Funke unterwegs" else "$underway Funken unterwegs"
        else -> "Alles gelesen"
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp))
            .background(p.card.copy(alpha = .8f)).border(1.dp, p.line, RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .semantics { contentDescription = "Funken öffnen. $line"; role = Role.Button }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(R.drawable.ico_spark, null, modifier = Modifier.padding(end = 12.dp), tint = p.gold, size = 24.dp)
        Column(Modifier.weight(1f)) {
            Text("Funken", fontWeight = FontWeight.Bold, color = p.ink)
            Text(line, fontSize = 13.sp, color = p.inkSoft)
        }
        if (unopened > 0) WaxSeal("$unopened", p.gold)
    }
}

@Composable
private fun RequestCard(request: PendingRequest, onRespond: (PendingRequest, Boolean) -> Unit, p: HarbourPalette) {
    Column(
        Modifier.fillMaxWidth().background(p.card, RoundedCornerShape(16.dp))
            .border(1.dp, p.gold.copy(alpha = .45f), RoundedCornerShape(16.dp)).padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(request.headline, fontWeight = FontWeight.Bold, color = p.ink)
        if (request.detail.isNotBlank()) Text(request.detail, fontSize = 13.sp, color = p.inkSoft)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onRespond(request, true) },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = p.seaDeep, contentColor = Color.White),
            ) { Text("Annehmen") }
            OutlinedButton(onClick = { onRespond(request, false) }) { Text("Ablehnen", color = p.ink) }
        }
    }
}

/**
 * One conversation in the overview.
 *
 * The friend appears as their little island; letters waiting at the harbour show
 * as a seal, unread chat lines as a sea-blue count. Unread rows carry a soft card
 * so the eye lands there first; quiet rows stay flat on the paper.
 */
@Composable
private fun ConversationRow(conversation: Conversation, now: Long, onOpen: (Long) -> Unit, p: HarbourPalette) {
    val news = conversation.hasNews
    Row(
        Modifier.fillMaxWidth()
            .clickable { onOpen(conversation.friendId) }
            .heightIn(min = 76.dp)
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IslandAvatar(conversation.avatarEmoji, profileColor(conversation.displayColor), 54.dp, p, news = news)
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.friendName, Modifier.weight(1f), fontSize = 16.sp, maxLines = 1,
                    fontWeight = if (news) FontWeight.ExtraBold else FontWeight.Bold, color = p.ink,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    ChatTools.listTime(conversation.lastActivityAt, now), fontSize = 12.sp,
                    color = if (news) p.sea else p.inkSoft,
                    fontWeight = if (news) FontWeight.Bold else FontWeight.Normal,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val preview = conversation.preview
                val body = ChatTools.parseReply(preview.text).second.replace('\n', ' ')
                val line = when {
                    conversation.silenced -> "Briefe und Chat sind aus"
                    preview.sealed -> (if (preview.fromMe) "Du: " else "") + "✉ " + body
                    else -> (if (preview.fromMe) "Du: " else "") + body
                }
                Text(
                    line, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    fontStyle = if (preview.sealed) FontStyle.Italic else FontStyle.Normal,
                    color = when {
                        conversation.silenced -> MaterialTheme.colorScheme.error
                        preview.sealed -> p.wax
                        news -> p.ink.copy(alpha = .85f)
                        else -> p.inkSoft
                    },
                )
                Row(Modifier.padding(start = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (conversation.lockedLetters > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(R.drawable.ico_lock, null, tint = p.inkSoft, size = 12.dp); Text("${conversation.lockedLetters}", fontSize = 11.sp, color = p.inkSoft)
                }
                    if (conversation.readyLetters > 0) WaxSeal("${conversation.readyLetters}", p.wax,
                        Modifier.semantics { contentDescription = "${conversation.readyLetters} Briefe bereit" })
                    if (conversation.awaitingMe > 0) WaxSeal("${conversation.awaitingMe}", p.gold,
                        Modifier.semantics { contentDescription = "${conversation.awaitingMe} Briefe warten auf deine Freigabe" })
                    if (conversation.unreadChats > 0) WaxSeal(if (conversation.unreadChats > 99) "99+" else "${conversation.unreadChats}", p.sea)
                }
            }
        }
    }
    HorizontalDivider(Modifier.padding(start = 82.dp, end = 16.dp), color = p.line)
}

// ---------------------------------------------------------------------------
// Thread: one person, letters and instant messages in one timeline
// ---------------------------------------------------------------------------

