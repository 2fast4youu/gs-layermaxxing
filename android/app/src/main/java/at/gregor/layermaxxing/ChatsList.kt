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
    @OptIn(ExperimentalMaterial3Api::class)
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            // WhatsApp layout: one search pill, one row of chips, then the list.
            // A brand-new account has nothing to search or filter: hide both.
            val firstRun = conversations.isEmpty() && requests.isEmpty()
            if (!firstRun) item {
                androidx.compose.material3.TextField(
                    search, { search = it },
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp).heightIn(min = 50.dp),
                    placeholder = { Text("Suchen", fontSize = 15.sp) }, leadingIcon = { Text("⌕", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontSize = 15.sp),
                    trailingIcon = if (search.isNotEmpty()) ({ TextButton(onClick = { search = "" }) { Text("✕") } }) else null,
                    singleLine = true, shape = RoundedCornerShape(26.dp),
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f),
                    ),
                )
            }
            if (!firstRun) item {
                // A soft fade on the right edge says "there is more" without clipping a chip hard.
                val fadeTo = MaterialTheme.colorScheme.surface
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = PaddingValues(start = 14.dp, end = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 4.dp).drawWithContent {
                        drawContent()
                        val w = 32.dp.toPx()
                        drawRect(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Transparent, fadeTo), startX = size.width - w, endX = size.width),
                            topLeft = androidx.compose.ui.geometry.Offset(size.width - w, 0f), size = androidx.compose.ui.geometry.Size(w, size.height),
                        )
                    },
                ) {
                    items(ChatTools.ListFilter.entries) { f ->
                        val count = if (f == ChatTools.ListFilter.UNREAD) conversations.count { it.hasNews } else 0
                        ListChip(if (count > 0) "${f.label} $count" else f.label, selected = filter == f) { filter = f }
                    }
                    // Filters only. Gruppen and Themen open their list; the Wörterbuch
                    // lives under "Mehr", the village has its own tab.
                    item { ListChip("Gruppen" + if (groupCount > 0) " $groupCount" else "", selected = false, onClick = onGroups) }
                    if (topicCount > 0) item { ListChip("Themen $topicCount", selected = false, onClick = onTopicsHub) }
                }
            }
            if (requests.isNotEmpty()) items(requests, key = { it.key }) { request ->
                Box(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) { RequestCard(request, onRespond) }
            }
            if (Sparks.entryVisible(sparkInbox, sparkSent)) item {
                Box(Modifier.padding(horizontal = 8.dp)) { SparkEntryRow(sparkInbox, sparkSent, onOpenSparks) }
            }
            if (conversations.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.height(60.dp))
                    Text("💬", fontSize = 42.sp)
                    Text("Noch keine Chats", style = MaterialTheme.typography.titleLarge)
                    Text("Füge eine Person über ihren Benutzernamen hinzu. Sobald sie annimmt, könnt ihr schreiben.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = onGoPeople, modifier = Modifier.height(48.dp)) { Text("＋  Person hinzufügen") }
                }
            } else if (matches.isEmpty()) item {
                Text(
                    if (search.isNotBlank()) "Keine Chats zu „${search.trim()}“." else "Hier ist gerade nichts.",
                    Modifier.fillMaxWidth().padding(28.dp), textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(matches, key = { "conv-${it.friendId}" }) { conversation -> ConversationRow(conversation, now, onOpen) }
            // Room so the last row is never trapped under the round actions.
            item { Spacer(Modifier.height(150.dp)) }
        }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (sparkEnabled) RoundAction(
                glyph = "✨",
                description = "Freundesfunke senden",
                container = MaterialTheme.colorScheme.surfaceVariant,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onSendSpark,
                size = 46,
            )
            if (conversations.isNotEmpty() || requests.isNotEmpty()) RoundAction(
                glyph = "＋",
                description = "Neuer Chat: Person finden oder Freundschaftsanfrage senden",
                container = MaterialTheme.colorScheme.primary,
                content = MaterialTheme.colorScheme.onPrimary,
                onClick = onGoPeople,
            )
        }
    }
    }
}

/** A filter or shortcut chip of the chat list. */
@Composable
private fun ListChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(50)).clickable(onClick = onClick)
            .semantics { role = Role.Button },
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(text, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
        }
    }
}

/** A round action of the overview. Never smaller than a 56 dp touch target. */
@Composable
private fun RoundAction(
    glyph: String,
    description: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    size: Int = 56,
) {
    Surface(
        modifier = Modifier.size(size.dp)
            .semantics { contentDescription = description; role = Role.Button }
            .clip(RoundedCornerShape(50)).clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = content,
        shadowElevation = 6.dp,
    ) { Box(contentAlignment = Alignment.Center) { Text(glyph, fontSize = 24.sp) } }
}

/**
 * The quiet entry to the spark inbox.
 *
 * It exists only once a spark has actually been received or sent, and it never
 * names a sender: the count is all there is to say.
 */
@Composable
private fun SparkEntryRow(inbox: List<SparkItem>, sent: List<SparkSent>, onOpen: () -> Unit) {
    val unopened = Sparks.unopenedCount(inbox)
    val underway = sent.count { !it.opened }
    val line = when {
        unopened > 0 -> if (unopened == 1) "1 neuer Funke" else "$unopened neue Funken"
        underway > 0 -> if (underway == 1) "1 Funke unterwegs" else "$underway Funken unterwegs"
        else -> "Alles gelesen"
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onOpen)
            .semantics { contentDescription = "Funken öffnen. $line"; role = Role.Button }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("✨", fontSize = 24.sp, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text("Funken", fontWeight = FontWeight.Bold)
            Text(line, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (unopened > 0) Chip("$unopened", MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
private fun RequestCard(request: PendingRequest, onRespond: (PendingRequest, Boolean) -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(request.headline, fontWeight = FontWeight.Bold)
            if (request.detail.isNotBlank()) Text(request.detail, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onRespond(request, true) }) { Text("Annehmen") }
                OutlinedButton(onClick = { onRespond(request, false) }) { Text("Ablehnen") }
            }
        }
    }
}

/**
 * One conversation in the overview.
 *
 * A flat row on the page rather than a grey rounded tile: the name and the
 * preview carry the hierarchy, a coloured avatar disc anchors the person, and
 * only the counts that mean something are drawn. Nothing here is a box inside a
 * box inside a box.
 */
@Composable
private fun ConversationRow(conversation: Conversation, now: Long, onOpen: (Long) -> Unit) {
    val news = conversation.hasNews
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable { onOpen(conversation.friendId) }
            .padding(start = 14.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = profileColor(conversation.displayColor).copy(alpha = .22f),
            modifier = Modifier.size(52.dp),
        ) { Box(contentAlignment = Alignment.Center) { Text(conversation.avatarEmoji, fontSize = 26.sp) } }
        Column(Modifier.weight(1f).padding(start = 13.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.friendName, Modifier.weight(1f), fontSize = 16.sp, maxLines = 1,
                    fontWeight = if (news) FontWeight.Bold else FontWeight.SemiBold,
                )
                Text(
                    ChatTools.listTime(conversation.lastActivityAt, now), fontSize = 12.sp,
                    color = if (news) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (news) FontWeight.Bold else FontWeight.Normal,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val preview = conversation.preview
                val line = when {
                    conversation.silenced -> "Briefe und Chat sind aus"
                    else -> (if (preview.fromMe) "Du: " else "") + (if (preview.sealed) "✦ " else "") +
                        (ChatTools.parseReply(preview.text).second.replace('\n', ' '))
                }
                Text(
                    line, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    fontStyle = if (preview.sealed) FontStyle.Italic else FontStyle.Normal,
                    color = if (conversation.silenced) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (conversation.lockedLetters > 0) Text("🔒${conversation.lockedLetters}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (conversation.readyLetters > 0) Chip("✦${conversation.readyLetters}", MaterialTheme.colorScheme.tertiary)
                    if (conversation.awaitingMe > 0) Chip("✓${conversation.awaitingMe}", MaterialTheme.colorScheme.secondary)
                    if (conversation.unreadChats > 0) UnreadDot(conversation.unreadChats, accent)
                }
            }
        }
    }
    HorizontalDivider(Modifier.padding(start = 79.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
}

/** The round unread counter of a chat row. */
@Composable
private fun UnreadDot(count: Int, color: Color) {
    Box(Modifier.sizeIn(minWidth = 22.dp, minHeight = 22.dp).background(color, RoundedCornerShape(50)).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
        Text(if (count > 99) "99+" else "$count", color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Surface(color = color, shape = RoundedCornerShape(50)) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------------------
// Thread: one person, letters and instant messages in one timeline
// ---------------------------------------------------------------------------

