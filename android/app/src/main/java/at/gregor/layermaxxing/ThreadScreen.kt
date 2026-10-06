package at.gregor.layermaxxing

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThreadScreen(
    friend: ApiClient.UserSummary, settings: ApiClient.FriendshipSettings?, ownUserId: Long?,
    token: String, api: ApiClient, letters: List<ApiClient.Message>, opened: Map<Long, OpenedMessage>,
    epLinkedLetterIds: Set<Long>, dismissedEpLetters: Map<Long, Boolean>,
    requests: List<PendingRequest>, onRespond: (PendingRequest, Boolean) -> Unit,
    act: ((suspend () -> Unit) -> Unit), onBack: () -> Unit, onComposeLetter: (Long) -> Unit,
    onOpenLetter: (ApiClient.Message) -> Unit,
    onLockedTap: (ApiClient.Message) -> Unit,
    onProof: (ApiClient.Message) -> Unit, onProposeEp: (EpOpportunity) -> Unit,
    topics: List<ApiClient.Topic>, epLine: String, creativeActive: Boolean,
) {
    val scope = rememberCoroutineScope()
    var chat by remember(friend.id, token) { mutableStateOf<List<ApiClient.ChatMessage>>(emptyList()) }
    var text by remember(friend.id, token) { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var threadError by remember(friend.id, token) { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(Instant.now().epochSecond) }
    // The Briefraum is a place of this conversation, not a sheet over it: letters
    // are the significant genre and deserve a room, one tap from here.
    var roomOpen by remember(friend.id, token) { mutableStateOf(false) }
    var roomFocusLetterId by remember(friend.id, token) { mutableStateOf<Long?>(null) }
    var infoOpen by remember(friend.id, token) { mutableStateOf(false) }
    var topicsOpen by remember(friend.id, token) { mutableStateOf(false) }
    // The bilateral rules are a sheet of this conversation, not a trip to
    // another tab: proposing, answering and withdrawing happen in place.
    var rulesOpen by remember(friend.id, token) { mutableStateOf(false) }
    var confirmRemove by remember(friend.id, token) { mutableStateOf(false) }
    var confirmBlock by remember(friend.id, token) { mutableStateOf(false) }
    var pulseLetterId by remember(friend.id, token) { mutableStateOf<Long?>(null) }
    var query by remember(friend.id, token) { mutableStateOf("") }
    var searchOpen by remember(friend.id, token) { mutableStateOf(false) }
    var selectedChat by remember(friend.id, token) { mutableStateOf<ApiClient.ChatMessage?>(null) }
    var quote by remember(friend.id, token) { mutableStateOf<String?>(null) }
    var sending by remember(friend.id, token) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val chatsEnabled = settings?.chatsEnabled == true
    val letterAction = LetterAccess.forSettings(settings)
    val listState = rememberLazyListState()

    // A sheet is a place: back closes it before it leaves the conversation.
    BackHandler(enabled = roomOpen || infoOpen || topicsOpen || rulesOpen) {
        roomOpen = false; infoOpen = false; topicsOpen = false; rulesOpen = false
    }

    suspend fun reloadChat() {
        if (!chatsEnabled) { chat = emptyList(); return }
        runCatching { api.chatMessages(token, friend.id) }.onSuccess { chat = it; threadError = null }
            .onFailure { threadError = it.message }
    }
    LaunchedEffect(friend.id, token, chatsEnabled) { while (true) { reloadChat(); delay(5_000) } }
    LaunchedEffect(friend.id) { while (true) { now = Instant.now().epochSecond; delay(20_000) } }

    val openedIds = opened.keys.toSet()
    val opportunities = EpOpportunities.forFriend(
        friend.id, friend.name, letters, settings?.epEnabled == true, epLinkedLetterIds, dismissedEpLetters.keys,
    ).associateBy { it.letterId }
    val conversationTopics = Conversations.topicsWith(friend.id, friend.name, topics)
    val plan = composerPlan(letterAction, chatsEnabled, conversationTopics.count { it.completedAt == null })
    val groups = Conversations.groupedLetters(letters, openedIds)
    val band = Conversations.sealBand(groups)
    val entries = Conversations.threadEntries(chat, letters, ownUserId, openedIds)
    val items = Conversations.withDayMarks(ChatTools.filterEntries(entries, query), now)

    var jumped by remember(friend.id, token) { mutableStateOf(false) }
    LaunchedEffect(items.size) {
        if (items.isEmpty()) return@LaunchedEffect
        // Entering lands at the newest line without a long scroll animation.
        if (!jumped) { listState.scrollToItem(items.size - 1); jumped = true }
        else listState.animateScrollToItem(items.size - 1)
    }
    LaunchedEffect(pulseLetterId) {
        if (pulseLetterId != null) { delay(1_400); pulseLetterId = null }
    }

    Column(Modifier.fillMaxSize()) {
        ThreadHeader(
            friend = friend,
            menu = menu,
            topicsBadge = conversationTopics.count { it.completedAt == null },
            lettersBadge = letters.size,
            lettersUrgent = band.any { it.bucket != LetterBucket.IN_TRANSIT },
            onMenu = { menu = it },
            onBack = onBack,
            onSearch = { searchOpen = true },
            onInfo = { infoOpen = true },
            onRules = { rulesOpen = true },
            onTopics = { topicsOpen = true },
            onLetterRoom = { roomFocusLetterId = null; roomOpen = true },
            onRemove = { confirmRemove = true },
            onBlock = { confirmBlock = true },
        )
        // Anything that waits for my answer sits directly under the head, in the
        // conversation it belongs to, and is answerable without any navigation.
        requests.forEach { request ->
            ThreadRequestCard(request, onRespond)
        }
        settings?.outgoingProposal?.let { proposal ->
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Dein Regelvorschlag wartet auf ${friend.name}", Modifier.weight(1f),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { act { api.withdrawFriendshipSettings(token, proposal.id) } }) {
                    Text("Zurückziehen", fontSize = 12.sp)
                }
            }
        }
        // Not a permanent second bar any more: the door to the Briefraum moved into
        // the head, so this strip only appears while a letter actually asks for
        // something — released, or waiting for exactly my approval.
        if (searchOpen) Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.TextField(
                query, { query = it }, Modifier.weight(1f), singleLine = true,
                placeholder = { Text("Im Chat suchen") }, leadingIcon = { Text("🔍", fontSize = 15.sp) },
                shape = RoundedCornerShape(24.dp),
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            TextButton(onClick = { searchOpen = false; query = "" }) { Text("Fertig") }
        }
        val actionable = band.filter { it.bucket != LetterBucket.IN_TRANSIT }
        if (actionable.isNotEmpty()) SealBandRow(
            band = actionable,
            onJump = { letterId ->
                Conversations.timelineIndexOf(items, letterId)?.let { index ->
                    pulseLetterId = letterId
                    scope.launch { listState.animateScrollToItem(index) }
                }
            },
        )
        Box(Modifier.weight(1f).fillMaxWidth().chatWallpaper()) {
        if (items.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .9f)) {
                Text(Conversations.EMPTY_THREAD, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        } else LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { Spacer(Modifier.height(6.dp)) }
            items(items, key = { it.key }) { item ->
                when (item) {
                    is TimelineItem.DayMark -> DayMark(item.label)
                    is TimelineItem.Entry -> when (val entry = item.entry) {
                        is ThreadEntry.Chat -> ThreadChatBubble(
                            entry.message, entry.outgoing,
                            onActions = { selectedChat = entry.message },
                            onSwipeReply = if (chatsEnabled) ({ quote = entry.message.text }) else null,
                        )
                        // A letter is an event in the flow, not a block in it: the
                        // row says what happened and hands over to the Briefraum,
                        // where the whole letter and all its actions live.
                        is ThreadEntry.Letter -> Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = if (entry.outgoing) Alignment.End else Alignment.Start,
                        ) {
                            LetterActivityRow(
                                message = entry.message,
                                state = entry.state,
                                now = now,
                                outgoing = entry.outgoing,
                                pulsing = pulseLetterId == entry.id,
                                onOpenRoom = { roomFocusLetterId = entry.id; roomOpen = true },
                            )
                            opportunities[entry.id]?.let { opportunity ->
                                EpOpportunityCard(opportunity, onProposeEp)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(6.dp)) }
        }
        }
        threadError?.let {
            Text(it, Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        quote?.let { quoted ->
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp)
                    .background(Harbour.palette().card, RoundedCornerShape(12.dp))
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(4.dp).fillMaxHeight().background(Harbour.palette().sea, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("Antwort", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Harbour.palette().sea)
                    Text(ChatTools.parseReply(quoted).second, maxLines = 2, fontSize = 13.sp, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(50)).clickable { quote = null }
                        .semantics { contentDescription = "Antwort verwerfen"; role = Role.Button },
                    contentAlignment = Alignment.Center,
                ) { Text("✕", fontSize = 16.sp) }
            }
        }
        Composer(
            plan = plan.copy(inputEnabled = plan.inputEnabled && !sending),
            text = text,
            onText = { text = it },
            onCompose = { onComposeLetter(friend.id) },
            onRules = { rulesOpen = true },
            onSend = {
                scope.launch {
                    val sent = text.trim()
                    if (sent.isNotEmpty() && !sending) {
                        sending = true
                        try {
                            runCatching { api.sendChat(token, friend.id, ChatTools.replyText(quote, sent)) }
                                .onSuccess { text = ""; quote = null; reloadChat() }.onFailure { threadError = it.message }
                        } finally { sending = false }
                    }
                }
            },
        )
    }

    selectedChat?.let { message ->
        ModalBottomSheet(onDismissRequest = { selectedChat = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(
                    ChatTools.parseReply(message.text).second.take(300), Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    maxLines = 4, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SheetAction("↩", "Antworten", enabled = chatsEnabled) { quote = message.text; selectedChat = null }
                SheetAction("⧉", "Kopieren") { clipboard.setText(AnnotatedString(ChatTools.parseReply(message.text).second)); selectedChat = null }
                Text(
                    "Gesendet ${socialDate(message.createdAt)}" + (message.readAt?.let { " · gelesen ${socialDate(it)}" } ?: ""),
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    if (roomOpen) LetterRoom(
        friendName = friend.name,
        letters = letters,
        opened = opened,
        action = letterAction,
        token = token,
        api = api,
        act = act,
        creativeActive = creativeActive,
        opportunities = opportunities,
        focusLetterId = roomFocusLetterId,
        onFocusConsumed = { roomFocusLetterId = null },
        onBack = { roomOpen = false },
        onCompose = { onComposeLetter(friend.id) },
        onManageRules = { roomOpen = false; rulesOpen = true },
        onOpenLetter = onOpenLetter,
        onLockedTap = onLockedTap,
        onProof = onProof,
        onProposeEp = { roomOpen = false; onProposeEp(it) },
    )

    if (infoOpen) ModalBottomSheet(onDismissRequest = { infoOpen = false }) {
        ThreadInfoSheet(friend, settings, epLine) { infoOpen = false; rulesOpen = true }
    }

    if (rulesOpen) ModalBottomSheet(onDismissRequest = { rulesOpen = false }) {
        FriendshipRulesEditor(
            friendName = friend.name,
            settings = settings,
            token = token,
            api = api,
            act = act,
            onDone = { rulesOpen = false },
        )
    }

    // Removing and blocking end the friendship; a slipped menu tap must not.
    if (confirmRemove) ConfirmDialog(
        "${friend.name} als Freund entfernen?",
        "Briefe und Chats dieser Freundschaft sind danach nicht mehr zugänglich.",
        { confirmRemove = false; act { api.removeFriend(token, friend.id); onBack() } },
    ) { confirmRemove = false }
    if (confirmBlock) ConfirmDialog(
        "${friend.name} blockieren?",
        "Die Freundschaft endet und ${friend.name} kann dich nicht mehr erreichen.",
        { confirmBlock = false; act { api.block(token, friend.id); onBack() } },
    ) { confirmBlock = false }

    // Topics are a sheet over the conversation, not a fourth room.
    if (topicsOpen) ModalBottomSheet(onDismissRequest = { topicsOpen = false }) {
        Box(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
            TopicsScreen(topics, friend, token, api, act, autoFocus = true)
        }
    }
}

/**
 * The 64 dp conversation head: back, who, Briefraum, Notizen, overflow.
 *
 * The three meaning spaces of a person — chat, letters, notes — are reachable
 * from exactly here: the chat is already underneath, and the other two are one
 * labelled, 48 dp target each. Rare management stays behind ⋮, so the head is
 * one calm row instead of a stack of strips.
 */
@Composable
internal fun ThreadHeader(
    friend: ApiClient.UserSummary,
    menu: Boolean,
    topicsBadge: Int,
    lettersBadge: Int,
    lettersUrgent: Boolean,
    onMenu: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onInfo: () -> Unit,
    onRules: () -> Unit,
    onTopics: () -> Unit,
    onLetterRoom: () -> Unit,
    onRemove: () -> Unit,
    onBlock: () -> Unit,
) {
    val p = Harbour.palette()
    Row(
        Modifier.fillMaxWidth().background(p.head).height(66.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(50)).clickable(onClick = onBack)
                .semantics { contentDescription = "Zurück zu Chats"; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) { Text("‹", fontSize = 30.sp, color = p.onHead) }
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable(onClick = onInfo)
                .semantics { contentDescription = "Infos zu ${friend.name}"; role = Role.Button }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IslandAvatar(friend.avatarEmoji, profileColor(friend.displayColor), 44.dp, p)
            Column(Modifier.padding(start = 10.dp)) {
                Text(friend.name, style = Harbour.Title.copy(fontSize = 19.sp), color = p.onHead, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(
                    if (topicsBadge > 0) "$topicsBadge offene Themen · Insel-Info" else "Insel-Info antippen",
                    fontSize = 11.sp, color = p.onHead.copy(alpha = .7f), maxLines = 1,
                )
            }
        }
        // Only when a letter actually waits for me does a seal show up here;
        // otherwise letters and topics live in the ⋮ menu (and in the timeline).
        if (lettersUrgent) Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(50)).clickable(onClick = onLetterRoom)
                .semantics { contentDescription = "Briefe – einer wartet auf dich"; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            Text("✉", fontSize = 21.sp, color = p.gold)
            Box(Modifier.align(Alignment.TopEnd).padding(top = 11.dp, end = 10.dp).size(8.dp).clip(CircleShape).background(p.wax))
        }
        Box {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(50)).clickable { onMenu(true) }
                    .semantics { contentDescription = "Weitere Aktionen"; role = Role.Button },
                contentAlignment = Alignment.Center,
            ) { Text("⋮", fontSize = 22.sp, color = p.onHead) }
            DropdownMenu(menu, { onMenu(false) }) {
                DropdownMenuItem(text = { Text(if (lettersBadge > 0) "✉  Briefe ($lettersBadge)" else "✉  Briefe") }, onClick = { onMenu(false); onLetterRoom() })
                DropdownMenuItem(text = { Text(if (topicsBadge > 0) "#  Themen ($topicsBadge offen)" else "#  Themen") }, onClick = { onMenu(false); onTopics() })
                DropdownMenuItem(text = { Text("⌕  Im Chat suchen") }, onClick = { onMenu(false); onSearch() })
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Regeln & Info") }, onClick = { onMenu(false); onInfo() })
                DropdownMenuItem(text = { Text("Freund entfernen") }, onClick = { onMenu(false); onRemove() })
                DropdownMenuItem(
                    text = { Text("Blockieren", color = MaterialTheme.colorScheme.error) },
                    onClick = { onMenu(false); onBlock() },
                )
            }
        }
    }
}

/** A request of exactly this friendship, answerable where it belongs. */
@Composable
private fun ThreadRequestCard(request: PendingRequest, onRespond: (PendingRequest, Boolean) -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(request.headline, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (request.detail.isNotBlank()) Text(request.detail, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onRespond(request, true) }, modifier = Modifier.height(36.dp)) { Text("Annehmen") }
                OutlinedButton(onClick = { onRespond(request, false) }, modifier = Modifier.height(36.dp)) { Text("Ablehnen") }
            }
        }
    }
}

/**
 * The seal band: jump marks for letters that ask for something right now.
 *
 * It only exists while there is something to act on, and it never prints a zero.
 */
@Composable
private fun SealBandRow(band: List<SealBadge>, onJump: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        band.forEach { badge ->
            val color = when (badge.bucket) {
                LetterBucket.WAITING_ON_YOU -> MaterialTheme.colorScheme.secondary
                LetterBucket.READY -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.outline
            }
            Surface(
                modifier = Modifier.height(48.dp).clickable { onJump(badge.oldestLetterId) }
                    .semantics {
                        contentDescription = "${badge.count} ${badge.bucket.label}"
                        role = Role.Button
                    },
                shape = RoundedCornerShape(50),
                color = color.copy(alpha = .16f),
                contentColor = color,
            ) {
                Box(Modifier.padding(horizontal = 13.dp), contentAlignment = Alignment.Center) {
                    Text("${sealGlyph(badge.bucket)} ${badge.count}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun sealGlyph(bucket: LetterBucket): String = when (bucket) {
    LetterBucket.WAITING_ON_YOU -> "✓"
    LetterBucket.READY -> "✦"
    LetterBucket.IN_TRANSIT -> "🕊"
    LetterBucket.OPENED_HISTORY -> "✉"
}

/**
 * One quiet composer row for two genres, with two fixed, labelled targets.
 *
 * The "✦ Brief" key is the only letter entry of the conversation and says so
 * in words; the send key exists whenever chatting is on and never swaps its
 * meaning — nothing here changes function while someone is aiming at it. A
 * switched-off genre leaves no dead button behind, only the way to the rules.
 */
@Composable
internal fun Composer(
    plan: ComposerPlan,
    text: String,
    onText: (String) -> Unit,
    onCompose: () -> Unit,
    onRules: () -> Unit,
    onSend: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().imePadding()) {
        plan.blockedReason?.let { reason ->
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(reason, Modifier.weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onRules, modifier = Modifier.height(48.dp)) { Text("Regeln vorschlagen") }
            }
            return@Column
        }
        val p = Harbour.palette()
        Row(
            Modifier.fillMaxWidth().background(p.paperDeep).padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // A paper capsule; the letter seal sits inside where an attach clip would be.
            Row(
                Modifier.weight(1f).heightIn(min = 50.dp)
                    .background(p.card, RoundedCornerShape(25.dp))
                    .border(1.dp, p.line, RoundedCornerShape(25.dp))
                    .padding(start = 6.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.TextField(
                    value = text,
                    onValueChange = onText,
                    modifier = Modifier.weight(1f),
                    maxLines = 5,
                    enabled = plan.inputEnabled,
                    placeholder = { Text(plan.placeholder, fontSize = 15.sp, maxLines = 1, color = p.inkSoft) },
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontSize = 15.sp, color = p.ink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                    // Messenger convention: sending keeps the keyboard and the focus.
                    keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) onSend() }),
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent, cursorColor = p.sea,
                    ),
                )
                if (plan.sealVisible) Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(50)).clickable(onClick = onCompose)
                        .semantics { contentDescription = LetterAccess.LABEL_LONG; role = Role.Button },
                    contentAlignment = Alignment.Center,
                ) { Text("✉", fontSize = 20.sp, color = p.wax, fontWeight = FontWeight.Bold) }
            }
            if (plan.inputEnabled) {
                val ready = text.isNotBlank()
                Box(
                    modifier = Modifier.size(48.dp)
                        .shadow(if (ready) 3.dp else 0.dp, CircleShape)
                        .background(
                            if (ready) Brush.verticalGradient(listOf(p.sea, p.seaDeep))
                            else Brush.verticalGradient(listOf(p.sea.copy(alpha = .4f), p.seaDeep.copy(alpha = .4f))),
                            CircleShape,
                        )
                        .clip(CircleShape)
                        .clickable(enabled = ready, onClick = onSend)
                        .semantics { contentDescription = "Senden"; role = Role.Button },
                    contentAlignment = Alignment.Center,
                ) { PaperBoat(Color.White, Modifier.size(26.dp)) }
            } else Surface(
                modifier = Modifier.heightIn(min = 50.dp).clip(RoundedCornerShape(25.dp)).clickable(onClick = onRules),
                shape = RoundedCornerShape(25.dp), color = p.card, border = BorderStroke(1.dp, p.line),
            ) { Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text("Regeln", fontSize = 13.sp, color = p.ink) } }
        }
    }
}

/**
 * The separate info surface of a conversation.
 *
 * Everything technical lives here — rules, EP, the long-press hint and the honest
 * crypto sentence — so the conversation itself carries no explanatory prose.
 */
@Composable
private fun ThreadInfoSheet(
    friend: ApiClient.UserSummary,
    settings: ApiClient.FriendshipSettings?,
    epLine: String,
    onRules: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("${friend.avatarEmoji} ${friend.name}", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        if (settings == null) Text(
            "Diese Freundschaft ist nicht mehr aktiv.",
            color = MaterialTheme.colorScheme.error,
        ) else {
            InfoRow("Briefe", settings.lettersEnabled)
            InfoRow("Chats", settings.chatsEnabled)
            InfoRow("Punkte", settings.epEnabled)
            if (settings.minLetterDelaySeconds > 0) Text(
                "Mindestdauer bis zur Freigabe: ${formatRemaining(settings.minLetterDelaySeconds)}",
                fontSize = 13.sp,
            )
        }
        Text(epLine, fontSize = 13.sp)
        HorizontalDivider()
        Text("Lange auf einen Brief drücken: Versiegelungs-Hash, Prüfschlüssel und Prüfdatei.", fontSize = 12.sp)
        Text(CRYPTO_HONESTY, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onRules, modifier = Modifier.fillMaxWidth()) { Text("Freundschaftsregeln") }
    }
}

@Composable
private fun InfoRow(label: String, enabled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = enabled, onCheckedChange = null, enabled = false)
        Spacer(Modifier.width(10.dp))
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * The letter stack: the same post, sorted.
 *
 * Only buckets that actually hold letters are drawn — the model already filters
 * them, and the screen finally honours that contract instead of printing "no
 * letters" four times. The opened history stays folded away until asked for.
 */
@Composable
internal fun LetterStack(
    letters: List<ApiClient.Message>,
    opened: Map<Long, OpenedMessage>,
    now: Long,
    token: String,
    api: ApiClient,
    act: ((suspend () -> Unit) -> Unit),
    onOpenLetter: (ApiClient.Message) -> Unit,
    onLockedTap: (ApiClient.Message) -> Unit,
    onProof: (ApiClient.Message) -> Unit,
    opportunities: Map<Long, EpOpportunity>,
    onProposeEp: (EpOpportunity) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    creativeActive: Boolean = false,
) {
    val groups = Conversations.groupedLetters(letters, opened.keys)
    var historyOpen by remember { mutableStateOf(false) }
    LazyColumn(
        modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)) }
        groups.forEach { group ->
            val history = group.bucket == LetterBucket.OPENED_HISTORY
            item {
                if (history) OutlinedButton(
                    onClick = { historyOpen = !historyOpen },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("${if (historyOpen) "▾" else "▸"} Verlauf (${group.messages.size})") }
                else Text(
                    group.bucket.label, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (!history || historyOpen) items(group.messages, key = { "stack-${it.id}" }) { message ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LetterCard(
                        message = message, opened = opened[message.id], now = now, token = token, api = api,
                        act = act, onOpenLetter = onOpenLetter, onLockedTap = onLockedTap, onProof = onProof,
                        creativeActive = creativeActive,
                    )
                    opportunities[message.id]?.let { opportunity -> EpOpportunityCard(opportunity, onProposeEp) }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

/** One letter, in the flow and in the stack: the same card, the same actions. */
@Composable
internal fun LetterCard(
    message: ApiClient.Message,
    opened: OpenedMessage?,
    now: Long,
    token: String,
    api: ApiClient,
    act: ((suspend () -> Unit) -> Unit),
    onOpenLetter: (ApiClient.Message) -> Unit,
    onLockedTap: (ApiClient.Message) -> Unit,
    onProof: (ApiClient.Message) -> Unit,
    pulsing: Boolean = false,
    creativeActive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        if (message.incoming) IncomingMessageCard(
            message, opened, now,
            onOpen = { onOpenLetter(message) },
            onApprove = { act { api.approve(token, message.id) } },
            onReact = { emoji -> act { api.react(token, message.id, emoji) } },
            onProof = { onProof(message) },
            onLockedTap = { onLockedTap(message) },
            pulsing = pulsing,
        ) else OutboxMessageCard(
            message, now,
            onRelease = { act { api.release(token, message.id) } },
            onApprove = { act { api.approve(token, message.id) } },
            onRetract = { act { api.retract(token, message.id) } },
            onProof = { onProof(message) },
            onLockedTap = { onLockedTap(message) },
            pulsing = pulsing,
        )
        // Test-only: pulls a purely time-based release into the present so a whole
        // letter journey can be walked in one sitting. The server re-checks role,
        // entitlement, friendship and mode, and rejects every other gate — a
        // mutual or manual letter never gets this action, here or there.
        if (CreativeMode.canAdvance(creativeActive, message.mode, message.unlocked)) TextButton(
            onClick = { act { api.advanceTestLetter(token, message.id) } },
            modifier = Modifier.heightIn(min = 48.dp)
                .semantics { contentDescription = "Testmodus: Freigabe dieses Briefs vorspulen" },
        ) { Text("⏩ Vorspulen (Test)", fontSize = 13.sp) }
    }
}

/**
 * The wax seal of a letter card: the material signal of the genre.
 *
 * Its glyph is the release gate, its colour the state — warm while a letter is
 * ready, flat once it is open. It replaces the old lock chip and the repeated
 * explanation lines around the card.
 */
@Composable
internal fun SealMark(message: ApiClient.Message, opened: Boolean, modifier: Modifier = Modifier) {
    val ready = message.unlocked && !opened
    val color = when {
        ready -> MaterialTheme.colorScheme.tertiary
        message.unlocked -> MaterialTheme.colorScheme.outlineVariant
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(
        modifier = modifier.size(26.dp),
        shape = RoundedCornerShape(50),
        color = color,
        contentColor = if (ready) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                if (ready) "✦" else sealGlyphFor(message.mode),
                fontSize = 12.sp, fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun sealGlyphFor(mode: String): String = when (mode) {
    "manual" -> "☝"
    "mutual" -> "✓✓"
    "presence" -> "●"
    "random" -> "?"
    else -> "◷"
}

@OptIn(ExperimentalFoundationApi::class)
/**
 * A WhatsApp-style bubble: tail toward the speaker, time and ticks inside the
 * bubble, quotes drawn as a block. Long-press or tap opens actions; swiping a
 * bubble to the right answers it.
 */
@Composable
internal fun ThreadChatBubble(message: ApiClient.ChatMessage, outgoing: Boolean, onActions: () -> Unit, onSwipeReply: (() -> Unit)?) {
    val (quoted, body) = ChatTools.parseReply(message.text)
    val p = Harbour.palette()
    val bubble = if (outgoing) p.mine else p.theirs
    val onBubble = if (outgoing) p.onMine else p.ink
    val shape = if (outgoing) RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
    val emojiOnly = ChatTools.isEmojiOnly(body)
    var drag by remember(message.id) { mutableStateOf(0f) }
    val swipe = Modifier.pointerInput(message.id, onSwipeReply) {
        if (onSwipeReply == null) return@pointerInput
        detectHorizontalDragGestures(
            onDragEnd = { if (drag > 64.dp.toPx()) onSwipeReply(); drag = 0f },
            onDragCancel = { drag = 0f },
        ) { _: androidx.compose.ui.input.pointer.PointerInputChange, amount: Float -> drag = (drag + amount).coerceIn(0f, 96.dp.toPx()) }
    }
    val time = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(message.createdAt))
    Row(
        Modifier.fillMaxWidth().then(swipe).graphicsLayer { translationX = drag }
            .padding(start = if (outgoing) 56.dp else 2.dp, end = if (outgoing) 2.dp else 56.dp),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
    ) {
        if (emojiOnly && quoted == null) {
            // A lone emoji is a sticker on the paper, not text in a box.
            Column(
                Modifier.clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = onActions, onLongClick = onActions).padding(4.dp),
                horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
            ) {
                Text(body, fontSize = 34.sp)
                BubbleMeta(time, outgoing, message.readAt != null, p.inkSoft, p)
            }
            return@Row
        }
        Box(
            Modifier.clip(shape).background(bubble)
                .border(1.dp, if (outgoing) Color.Transparent else p.line, shape)
                .combinedClickable(onClick = onActions, onLongClick = onActions),
        ) {
            Column(Modifier.padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 6.dp).width(IntrinsicSize.Max)) {
                quoted?.let {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 5.dp)
                            .background(onBubble.copy(alpha = .06f), RoundedCornerShape(10.dp)).height(IntrinsicSize.Min),
                    ) {
                        Box(Modifier.width(3.dp).fillMaxHeight().background(p.sea, RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp)))
                        Text(it, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), fontSize = 12.sp, maxLines = 2, color = onBubble.copy(alpha = .72f), overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
                Text(body, fontSize = 15.sp, lineHeight = 20.sp, color = onBubble, modifier = Modifier.widthIn(min = 40.dp))
                Box(Modifier.align(Alignment.End)) { BubbleMeta(time, outgoing, message.readAt != null, onBubble.copy(alpha = .55f), p) }
            }
        }
    }
}

/** Time and delivery marks of a bubble: one tick sent, two sea-blue ticks read. */
@Composable
private fun BubbleMeta(time: String, outgoing: Boolean, read: Boolean, muted: Color, p: HarbourPalette) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(time, fontSize = 10.sp, color = muted)
        if (outgoing) Text(
            if (read) " ✓✓" else " ✓", fontSize = 11.sp, fontWeight = FontWeight.Bold,
            color = if (read) p.read else muted,
            modifier = Modifier.semantics { contentDescription = if (read) "gelesen" else "zugestellt" },
        )
    }
}

/** A day separator: a small paper tab pinned in the middle of the timeline. */
@Composable
internal fun DayMark(label: String) {
    val p = Harbour.palette()
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            Modifier.background(p.card.copy(alpha = .92f), RoundedCornerShape(10.dp))
                .border(1.dp, p.line, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = p.inkSoft, letterSpacing = .4.sp,
        )
    }
}

/** One full-width action row in a message's action sheet. */
@Composable
private fun SheetAction(glyph: String, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp).semantics { role = Role.Button },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(glyph, fontSize = 19.sp, modifier = Modifier.width(36.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else .4f))
        Text(label, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .4f))
    }
}

/** The harbour paper behind a conversation, tinted by the active theme. */
@Composable
internal fun Modifier.chatWallpaper(): Modifier = this.harbourPaper(Harbour.palette())

@Composable
internal fun EpOpportunityCard(opportunity: EpOpportunity, onProposeEp: (EpOpportunity) -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(opportunity.question, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onProposeEp(opportunity) }) { Text("Punkte vorschlagen") }
        }
    } }
}

@Composable
internal fun EpProposalDialog(
    opportunity: EpOpportunity, onSubmit: (String, String?) -> Unit, onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(opportunity.letterTitle) }
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Punkte vorschlagen") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("An ${opportunity.friendName} · verknüpft mit „${opportunity.letterTitle}“", fontSize = 12.sp)
            Text("Punkte sind ein Dankeschön – sie zählen erst, wenn die andere Person annimmt.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatEpPoints(EP_PROPOSAL_POINTS), fontWeight = FontWeight.Bold)
            OutlinedTextField(title, { title = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("Titel (erforderlich)") })
            OutlinedTextField(description, { description = it.take(1000) }, Modifier.fillMaxWidth(), label = { Text("Beschreibung – optional") }, minLines = 2)
        } },
        confirmButton = { Button(
            onClick = { onSubmit(title.trim(), description.trim().ifBlank { null }) },
            enabled = title.isNotBlank(),
        ) { Text("Vorschlagen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Nicht jetzt") } },
    )
}

@Composable
internal fun RequestDialog(
    request: PendingRequest, onAccept: () -> Unit, onReject: () -> Unit, onLater: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(request.headline) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (request.detail.isNotBlank()) Text(request.detail)
            if (request.kind == RequestKind.EP) Text(
                "Erst deine Annahme zählt. Abgelehntes und Offenes zählen nie.", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onLater) { Text("Später") }
        } },
        confirmButton = { Button(onClick = onAccept) { Text("Annehmen") } },
        dismissButton = { OutlinedButton(onClick = onReject) { Text("Ablehnen") } },
    )
}

// ---------------------------------------------------------------------------
// Letter archive (reached from "Mehr"): received/sent, proof, export
// ---------------------------------------------------------------------------

