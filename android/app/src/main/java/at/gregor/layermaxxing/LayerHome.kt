package at.gregor.layermaxxing

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

private enum class ComposeMode(val label: String, val api: String) {
    DURATION("Nach einer Dauer", "timed"), DATE_TIME("Zu einem Zeitpunkt", "timed"), MANUAL("Von mir freigeben", "manual"),
    MUTUAL("Wenn beide zustimmen", "mutual"), PRESENCE("Wenn beide online sind", "presence"), RANDOM("Zufällig", "random")
}
private enum class DelayUnit(val label: String, val seconds: Long) {
    MINUTES("Minuten", 60), HOURS("Stunden", 3600), DAYS("Tage", 86400)
}
internal data class OpenedMessage(val text: String, val attachment: ByteArray?, val name: String?, val mime: String?)
data class EpPrompt(val friendId: Long, val letterId: Long?)

/** Where the "Mehr" hub can dive into an existing full screen without a new tab. */
private enum class MoreDest { LETTERS, EP }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayerHome(
    token: String, api: ApiClient, store: SessionStore, initialRecoveryCode: String?,
    onRecoveryCodeSeen: () -> Unit, onTheme: (String) -> Unit, onLogout: () -> Unit,
    accounts: List<SavedAccount>, onAddAccount: () -> Unit, onSwitchAccount: (SavedAccount) -> Unit,
    serverProfile: ServerProfile, onServerProfile: (ServerProfile) -> Unit,
) {
    var tab by remember { mutableStateOf(MainTab.CHATS) }
    var openThreadFriend by remember { mutableStateOf<Long?>(null) }
    // "Leute" is no longer a tab. Finding, adding and managing people is a place
    // you enter from the chat overview and leave again — the overview itself is
    // the people list, so this screen is for the rare cases only.
    var peopleOpen by remember { mutableStateOf(false) }
    var sparkInbox by remember { mutableStateOf<List<SparkItem>>(emptyList()) }
    var sparkSent by remember { mutableStateOf<List<SparkSent>>(emptyList()) }
    var sparksOpen by remember { mutableStateOf(false) }
    var sparkCompose by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<ApiClient.Status?>(null) }
    var users by remember { mutableStateOf<List<ApiClient.UserSummary>>(emptyList()) }
    var friends by remember { mutableStateOf<List<ApiClient.UserSummary>>(emptyList()) }
    var blocked by remember { mutableStateOf<List<ApiClient.UserSummary>>(emptyList()) }
    var incoming by remember { mutableStateOf<List<ApiClient.IncomingRequest>>(emptyList()) }
    var outgoing by remember { mutableStateOf<List<ApiClient.OutgoingRequest>>(emptyList()) }
    var groups by remember { mutableStateOf<List<ApiClient.Group>>(emptyList()) }
    var topics by remember { mutableStateOf<List<ApiClient.Topic>>(emptyList()) }
    var messages by remember { mutableStateOf<List<ApiClient.Message>>(emptyList()) }
    var outbox by remember { mutableStateOf<List<ApiClient.Message>>(emptyList()) }
    var sessions by remember { mutableStateOf<List<ApiClient.Session>>(emptyList()) }
    var friendshipSettings by remember { mutableStateOf<List<ApiClient.FriendshipSettings>>(emptyList()) }
    var ep by remember { mutableStateOf<ApiClient.EpOverview?>(null) }
    var chatThreads by remember { mutableStateOf<List<ApiClient.ChatThread>>(emptyList()) }
    var proof by remember { mutableStateOf<ApiClient.ProofDetails?>(null) }
    var epProposal by remember { mutableStateOf<EpOpportunity?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(true) }
    var recoveryCode by remember { mutableStateOf(initialRecoveryCode) }
    var castleExperiment by remember { mutableStateOf(store.castleExperiment) }
    var castleBuilds by remember { mutableStateOf(store.fiefBuilds()) }
    // The device remembers only the switch; role and entitlement arrive fresh
    // with every status refresh, so production or a revoked entitlement wins.
    var creativeSwitch by remember { mutableStateOf(store.creativeMode) }
    // Exactly one letter composer exists in the whole app. The thread, the letter
    // section and the castle post office all open this one, never a second copy.
    var composeLetterFriend by remember { mutableStateOf<Long?>(null) }
    var fogVisible by remember { mutableStateOf(false) }
    val opened = remember { mutableStateMapOf<Long, OpenedMessage>() }
    // Session-scoped dismissals: a snoozed request dialog or an "not now" EP card
    // must not bounce back on the next 30-second refresh.
    val dismissedRequests = remember { mutableStateMapOf<String, Boolean>() }
    val dismissedEpLetters = remember { mutableStateMapOf<Long, Boolean>() }
    val scope = rememberCoroutineScope()
    var accountMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val lockedLetterSounds = rememberLockedLetterSoundPlayer(context)
    val lockedTapTracker = remember { LockedLetterTapTracker() }

    fun lockedLetterTap(message: ApiClient.Message) {
        if (fogVisible) return
        when (lockedTapTracker.register(message.id, SystemClock.elapsedRealtime())) {
            LockedTapResult.PLAY_SOUND -> lockedLetterSounds.playRandom()
            LockedTapResult.SHOW_FOG -> {
                lockedLetterSounds.stopAll()
                fogVisible = true
            }
        }
    }

    suspend fun refresh() {
        runCatching {
            coroutineScope {
                val jobs = listOf(
                    async { status = api.status(token) }, async { users = api.users(token) },
                    async { friends = api.friends(token) }, async { blocked = api.blocked(token) },
                    async { incoming = api.incomingRequests(token) }, async { outgoing = api.outgoingRequests(token) },
                    async { groups = api.groups(token) }, async { topics = api.topics(token) }, async { messages = api.messages(token) },
                    async { outbox = api.outbox(token) }, async { sessions = api.sessions(token) },
                    async { friendshipSettings = api.friendshipSettings(token) },
                    async { ep = api.ep(token) }, async { chatThreads = api.chatThreads(token) },
                    async { sparkInbox = api.sparks(token) }, async { sparkSent = api.sparkOutbox(token) },
                )
                jobs.awaitAll()
            }
            error = null
        }.onFailure { error = it.message ?: "Aktualisierung fehlgeschlagen" }
        busy = false
    }
    fun act(block: suspend () -> Unit) {
        scope.launch { runCatching { block(); refresh() }.onFailure { error = it.message ?: "Vorgang fehlgeschlagen" } }
    }
    fun openLetter(message: ApiClient.Message) = act {
        val content = api.content(token, message.id)
        val aad = content.canonicalMetadata?.toByteArray() ?: byteArrayOf()
        val text = CryptoBox.decryptBytes(content.ciphertext, content.nonce, content.encryptionKey, aad).toString(Charsets.UTF_8)
        val bytes = if (content.attachmentCiphertext != null && content.attachmentNonce != null)
            CryptoBox.decryptBytes(
                content.attachmentCiphertext, content.attachmentNonce, content.encryptionKey,
                if (content.canonicalMetadata != null && content.attachmentName != null && content.attachmentMime != null)
                    EvidenceProtocol.attachmentAad(content.canonicalMetadata, content.attachmentName, content.attachmentMime)
                else byteArrayOf(),
            ) else null
        opened[message.id] = OpenedMessage(text, bytes, content.attachmentName, content.attachmentMime)
    }

    LaunchedEffect(token) {
        // Switching accounts must not leak the previous account's transient state.
        opened.clear(); epProposal = null; openThreadFriend = null; proof = null; composeLetterFriend = null
        dismissedRequests.clear(); dismissedEpLetters.clear()
        peopleOpen = false; sparksOpen = false; sparkCompose = false
        sparkInbox = emptyList(); sparkSent = emptyList()
        castleExperiment = store.castleExperiment
        creativeSwitch = store.creativeMode
        fogVisible = false; lockedTapTracker.reset(); lockedLetterSounds.stopAll()
        busy = true; error = null
        while (true) { refresh(); delay(30_000) }
    }
    if (recoveryCode != null) RecoveryDialog(recoveryCode!!) { recoveryCode = null; onRecoveryCodeSeen() }
    proof?.let { ProofDialog(it, onDismiss = { proof = null }) }

    val conversations = Conversations.overview(friends, friendshipSettings, chatThreads, messages, outbox, status?.userId)
    val requestInbox = RequestInbox.collect(incoming, friendshipSettings, ep)
    val currentDialog = RequestInbox.dialog(requestInbox, dismissedRequests.keys, snoozed = false)
    val tabs = Navigation.tabs(castleExperiment)
    val creativeEligible = CreativeMode.eligible(status?.serverRole, status?.creativeEntitled == true)
    val creativeActive = creativeEligible && creativeSwitch
    // The valley reads from the ledger of the current mode: creative builds live
    // in their own ledger and vanish from view the moment the mode goes off.
    LaunchedEffect(creativeActive, token) { castleBuilds = store.fiefBuilds(creativeActive) }

    fun respondRequest(request: PendingRequest, accept: Boolean) = act {
        when (request.kind) {
            RequestKind.SETTINGS -> api.respondFriendshipSettings(token, request.id, accept)
            RequestKind.EP -> api.respondEp(token, request.id, accept)
            RequestKind.FRIEND -> api.respondFriend(token, request.id, accept)
        }
    }

    // Back walks the same chain the visible arrows walk. Screens rendered below
    // register their own handlers, and a handler composed later wins, so a thread
    // section or a castle place is unwound before the tab and before the app closes.
    BackHandler(enabled = tab != MainTab.CHATS) { tab = MainTab.CHATS; openThreadFriend = null }
    BackHandler(enabled = openThreadFriend != null) { openThreadFriend = null }
    BackHandler(enabled = peopleOpen) { peopleOpen = false }
    BackHandler(enabled = sparksOpen) { sparksOpen = false }

    // An open thread, the people place and the valley are full-screen places: the
    // global bar and the bottom navigation step aside instead of stacking a second
    // head on top.
    val fullScreenPlace = tab == MainTab.CASTLES || openThreadFriend != null || peopleOpen || sparksOpen
    // The valley is a painted world, so it runs under the system bars instead of
    // sitting in a window of app background: a letterboxed plate inside inset
    // padding is exactly what produced the pale strips above and below the map.
    // Its own chrome reserves the insets it needs.
    val edgeToEdgePlace = tab == MainTab.CASTLES
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            // One chrome row, not a landscape of bars. The tab name is already on
            // the bottom navigation and the build number belongs in "Mehr", so the
            // only things left here are: who am I, and refresh.
            if (!fullScreenPlace) AppChrome(
                name = status?.name ?: store.name,
                avatarEmoji = status?.avatarEmoji ?: "👤",
                color = status?.displayColor?.let(::profileColor) ?: MaterialTheme.colorScheme.primary,
                accounts = accounts,
                menuOpen = accountMenu,
                onMenu = { accountMenu = it },
                onSwitchAccount = onSwitchAccount,
                onAddAccount = onAddAccount,
                onRefresh = { scope.launch { refresh() } },
            )
        },
        bottomBar = {
            if (!fullScreenPlace) NavigationBar {
                tabs.forEach { item -> NavigationBarItem(
                    selected = tab == item, onClick = { tab = item; openThreadFriend = null }, icon = {
                        val count = when (item) {
                            MainTab.CHATS -> requestInbox.size + conversations.count { it.hasNews } +
                                Sparks.unopenedCount(sparkInbox)
                            else -> 0
                        }
                        Text(if (count > 0) "${item.icon}${if (count > 9) "9+" else count}" else item.icon, fontSize = 19.sp)
                    },
                    label = { Text(item.label) },
                ) }
            }
        },
    ) { padding ->
        // Consuming the scaffold insets here lets imePadding() below add exactly the
        // keyboard height and not the navigation bar a second time.
        Box(
            if (edgeToEdgePlace) Modifier.fillMaxSize()
            else Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
        ) {
            val threadFriend = openThreadFriend?.let { id -> friends.firstOrNull { it.id == id } }
            when {
                threadFriend != null -> ThreadScreen(
                    friend = threadFriend,
                    settings = friendshipSettings.firstOrNull { it.friendId == threadFriend.id },
                    ownUserId = status?.userId, token = token, api = api,
                    letters = Conversations.lettersWith(threadFriend.id, messages, outbox),
                    opened = opened,
                    epLinkedLetterIds = EpOpportunities.linkedLetterIds(ep),
                    dismissedEpLetters = dismissedEpLetters,
                    requests = RequestInbox.forFriend(requestInbox, threadFriend.id),
                    onRespond = ::respondRequest,
                    act = ::act, onBack = { openThreadFriend = null },
                    onComposeLetter = { composeLetterFriend = it },
                    onOpenLetter = ::openLetter,
                    onLockedTap = ::lockedLetterTap,
                    onProof = { message -> scope.launch { runCatching { proof = api.proof(token, message.id) }.onFailure { error = it.message } } },
                    onProposeEp = { epProposal = it },
                    topics = topics,
                    epLine = epLineFor(ep, status?.userId, threadFriend),
                    creativeActive = creativeActive,
                )
                sparksOpen -> SparkRoom(
                    inbox = sparkInbox, sent = sparkSent, token = token, api = api, act = ::act,
                    onBack = { sparksOpen = false }, onCompose = { sparkCompose = true },
                )
                peopleOpen -> SubScreen("Leute", onBack = { peopleOpen = false }, backLabel = "Chats") {
                    FriendsScreen(token, api, users, friends, incoming, outgoing, groups,
                        friendshipSettings, onOpenThread = { peopleOpen = false; openThreadFriend = it }, act = ::act)
                }
                tab == MainTab.CHATS -> ChatsScreen(
                    conversations = conversations, requests = requestInbox,
                    sparkInbox = sparkInbox, sparkSent = sparkSent,
                    onOpen = { openThreadFriend = it }, onRespond = ::respondRequest,
                    onGoPeople = { peopleOpen = true },
                    onOpenSparks = { sparksOpen = true },
                    onSendSpark = { sparkCompose = true },
                    sparkEnabled = friends.isNotEmpty(),
                )
                tab == MainTab.MORE -> MoreScreen(
                    token, api, store, status, sessions, blocked, messages, outbox, ep,
                    friendshipSettings, opened, onTheme, onLogout, serverProfile, onServerProfile,
                    castleExperiment = castleExperiment,
                    onCastleExperiment = { store.setCastleExperiment(it); castleExperiment = it },
                    creativeEligible = creativeEligible,
                    creativeSwitch = creativeSwitch,
                    onCreativeSwitch = { store.setCreativeMode(it); creativeSwitch = it },
                    onRecovery = { recoveryCode = it }, onProof = { message -> scope.launch { runCatching { proof = api.proof(token, message.id) }.onFailure { error = it.message } } },
                    onOpenLetter = ::openLetter, onLockedTap = ::lockedLetterTap, act = ::act,
                )
                tab == MainTab.CASTLES -> CastlesScreen(
                    ownUserId = status?.userId, friends = friends, settings = friendshipSettings,
                    ep = ep, letters = messages + outbox, builds = castleBuilds,
                    creativeActive = creativeActive,
                    onBuild = { friendId, step, earnedEp ->
                        if (store.buildFiefStep(friendId, step, earnedEp, creativeActive)) {
                            castleBuilds = store.fiefBuilds(creativeActive)
                        }
                    },
                    onBack = { tab = MainTab.CHATS },
                    onPeople = { tab = MainTab.CHATS; peopleOpen = true },
                    onComposeLetter = { composeLetterFriend = it },
                    letterAccess = { friendId ->
                        LetterAccess.forSettings(friendshipSettings.firstOrNull { it.friendId == friendId })
                    },
                    opened = opened, act = ::act, token = token, api = api,
                    onOpenLetter = ::openLetter, onLockedTap = ::lockedLetterTap,
                    onProof = { message -> scope.launch { runCatching { proof = api.proof(token, message.id) }.onFailure { error = it.message } } },
                    epLinkedLetterIds = EpOpportunities.linkedLetterIds(ep),
                    dismissedEpLetters = dismissedEpLetters,
                    onProposeEp = { epProposal = it },
                    seenEarned = { friendId -> store.fiefSeenEarned(friendId) },
                    onSeenEarned = { friendId, earned -> store.setFiefSeenEarned(friendId, earned) },
                )
            }
            if (busy) CircularProgressIndicator(Modifier.align(Alignment.Center))
            currentDialog?.let { request -> RequestDialog(
                request,
                onAccept = { respondRequest(request, true); dismissedRequests[request.key] = true },
                onReject = { respondRequest(request, false); dismissedRequests[request.key] = true },
                onLater = { dismissedRequests[request.key] = true },
            ) }
            epProposal?.let { opportunity -> EpProposalDialog(
                opportunity,
                onSubmit = { title, description -> act {
                    api.proposeEp(
                        token, opportunity.friendId, EP_PROPOSAL_POINTS, title, description, opportunity.letterId,
                    )
                }; epProposal = null },
                onDismiss = { dismissedEpLetters[opportunity.letterId] = true; epProposal = null },
            ) }
            error?.let { message -> Card(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) { Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                TextButton(onClick = { error = null }) { Text("Schließen") }
            } } }
        }
    }
    composeLetterFriend?.let { friendId ->
        ModalBottomSheet(
            onDismissRequest = { composeLetterFriend = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = null,
        ) {
            SendScreen(
                token, api, friends, groups, friendshipSettings, initialRecipient = friendId,
                creativeActive = creativeActive,
                onClose = { composeLetterFriend = null },
                onSent = { _, _ -> composeLetterFriend = null; act {} },
            )
        }
    }
    if (sparkCompose) ModalBottomSheet(
        onDismissRequest = { sparkCompose = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        SparkComposer(
            friends = friends,
            settings = friendshipSettings,
            onClose = { sparkCompose = false },
            onSend = { friendId, message ->
                sparkCompose = false
                act { api.sendSpark(token, friendId, message) }
            },
        )
    }
    if (fogVisible) FogOverlay {
        fogVisible = false
        lockedTapTracker.reset()
    }
    }
}

// ---------------------------------------------------------------------------
// The one chrome row under the test banner
// ---------------------------------------------------------------------------

/**
 * Who am I, and refresh. That is the whole bar.
 *
 * The tab name is already spelled out on the bottom navigation, the build number
 * lives in "Mehr" and the test-server warning is its own line above — repeating
 * any of them here is what turned the head of this app into a stack of strips.
 * Switching account is the identity chip itself, so it needs no second control.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppChrome(
    name: String,
    avatarEmoji: String,
    color: Color,
    accounts: List<SavedAccount>,
    menuOpen: Boolean,
    onMenu: (Boolean) -> Unit,
    onSwitchAccount: (SavedAccount) -> Unit,
    onAddAccount: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Row(
                Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp))
                    .clickable { onMenu(true) }
                    .padding(start = 6.dp, end = 12.dp)
                    .semantics { contentDescription = "Angemeldet als $name. Konto wechseln"; role = Role.Button },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = .18f), modifier = Modifier.size(34.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(avatarEmoji, fontSize = 18.sp) }
                }
                Text(
                    name.ifBlank { "Konto" }, Modifier.padding(start = 9.dp),
                    fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1,
                )
                Text(" ⌄", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenu(false) }) {
                accounts.forEach { account ->
                    DropdownMenuItem(
                        text = { Text(if (account.name == name) "${account.name} ✓" else account.name) },
                        onClick = { onMenu(false); if (account.name != name) onSwitchAccount(account) },
                    )
                }
                if (accounts.isNotEmpty()) HorizontalDivider()
                DropdownMenuItem(text = { Text("＋ Konto hinzufügen") }, onClick = { onMenu(false); onAddAccount() })
            }
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(50)).clickable(onClick = onRefresh)
                .semantics { contentDescription = "Aktualisieren"; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) { Text("⟳", fontSize = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

// ---------------------------------------------------------------------------
// Chats: the conversation overview
// ---------------------------------------------------------------------------

/**
 * The chat overview: friends and live conversations are the list itself.
 *
 * Two round actions sit above it — find a person, and send a Freundesfunke.
 * Requests that wait for an answer appear contextually at the top; rare people
 * management stays behind the finding action.
 */
@Composable
private fun ChatsScreen(
    conversations: List<Conversation>, requests: List<PendingRequest>,
    sparkInbox: List<SparkItem>, sparkSent: List<SparkSent>,
    onOpen: (Long) -> Unit, onRespond: (PendingRequest, Boolean) -> Unit, onGoPeople: () -> Unit,
    onOpenSparks: () -> Unit, onSendSpark: () -> Unit, sparkEnabled: Boolean,
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (requests.isNotEmpty()) {
                item { SectionTitle("Wartet auf deine Antwort") }
                items(requests, key = { it.key }) { request -> RequestCard(request, onRespond) }
            }
            if (Sparks.entryVisible(sparkInbox, sparkSent)) item { SparkEntryRow(sparkInbox, sparkSent, onOpenSparks) }
            item { SectionTitle("Gespräche") }
            if (conversations.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Noch keine Gespräche.", fontWeight = FontWeight.Bold)
                    Text(
                        "Such jemanden über ＋, dann erscheint hier euer Gespräch.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(conversations, key = { "conv-${it.friendId}" }) { conversation -> ConversationRow(conversation, onOpen) }
            // Room so the last row is never trapped under the round actions.
            item { Spacer(Modifier.height(148.dp)) }
        }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (sparkEnabled) RoundAction(
                glyph = "✨",
                description = "Freundesfunke senden",
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = onSendSpark,
            )
            RoundAction(
                glyph = "＋",
                description = "Person finden oder Freundschaftsanfrage senden",
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = onGoPeople,
            )
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
) {
    Surface(
        modifier = Modifier.size(56.dp)
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
private fun ConversationRow(conversation: Conversation, onOpen: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp)).clickable { onOpen(conversation.friendId) }
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = profileColor(conversation.displayColor).copy(alpha = .18f),
            modifier = Modifier.size(46.dp),
        ) { Box(contentAlignment = Alignment.Center) { Text(conversation.avatarEmoji, fontSize = 24.sp) } }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                conversation.friendName, fontSize = 16.sp,
                fontWeight = if (conversation.hasNews) FontWeight.Bold else FontWeight.SemiBold,
            )
            val preview = conversation.preview
            Text(
                (if (preview.fromMe) "Du: " else "") + (if (preview.sealed) "✦ " else "") + preview.text,
                fontSize = 13.sp, maxLines = 1,
                fontStyle = if (preview.sealed) FontStyle.Italic else FontStyle.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (conversation.silenced) Text(
                "Briefe und Chat sind in dieser Freundschaft aus",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.error,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (conversation.unreadChats > 0) Chip("${conversation.unreadChats}", MaterialTheme.colorScheme.primary)
            if (conversation.readyLetters > 0) Chip("✦ ${conversation.readyLetters}", MaterialTheme.colorScheme.tertiary)
            if (conversation.awaitingMe > 0) Chip("✓ ${conversation.awaitingMe}", MaterialTheme.colorScheme.secondary)
            if (conversation.lockedLetters > 0) Text(
                "🔒 ${conversation.lockedLetters}", fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadScreen(
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
    var chat by remember(friend.id) { mutableStateOf<List<ApiClient.ChatMessage>>(emptyList()) }
    var text by remember(friend.id) { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var threadError by remember(friend.id) { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(Instant.now().epochSecond) }
    // The Briefraum is a place of this conversation, not a sheet over it: letters
    // are the significant genre and deserve a room, one tap from here.
    var roomOpen by remember(friend.id) { mutableStateOf(false) }
    var roomFocusLetterId by remember(friend.id) { mutableStateOf<Long?>(null) }
    var infoOpen by remember(friend.id) { mutableStateOf(false) }
    var topicsOpen by remember(friend.id) { mutableStateOf(false) }
    // The bilateral rules are a sheet of this conversation, not a trip to
    // another tab: proposing, answering and withdrawing happen in place.
    var rulesOpen by remember(friend.id) { mutableStateOf(false) }
    var confirmRemove by remember(friend.id) { mutableStateOf(false) }
    var confirmBlock by remember(friend.id) { mutableStateOf(false) }
    var pulseLetterId by remember(friend.id) { mutableStateOf<Long?>(null) }
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
    LaunchedEffect(friend.id, chatsEnabled) { while (true) { reloadChat(); delay(5_000) } }
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
    val items = Conversations.withDayMarks(entries, now)

    var jumped by remember(friend.id) { mutableStateOf(false) }
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
        if (items.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(Conversations.EMPTY_THREAD, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.key }) { item ->
                when (item) {
                    is TimelineItem.DayMark -> Text(
                        item.label, Modifier.fillMaxWidth().padding(top = 8.dp),
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    is TimelineItem.Entry -> when (val entry = item.entry) {
                        is ThreadEntry.Chat -> ThreadChatBubble(entry.message, entry.outgoing)
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
            item { Spacer(Modifier.height(4.dp)) }
        }
        threadError?.let {
            Text(it, Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        Composer(
            plan = plan,
            text = text,
            onText = { text = it },
            onCompose = { onComposeLetter(friend.id) },
            onRules = { rulesOpen = true },
            onSend = {
                scope.launch {
                    val sent = text.trim()
                    if (sent.isNotEmpty()) runCatching { api.sendChat(token, friend.id, sent) }
                        .onSuccess { text = ""; reloadChat() }.onFailure { threadError = it.message }
                }
            },
        )
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
private fun ThreadHeader(
    friend: ApiClient.UserSummary,
    menu: Boolean,
    topicsBadge: Int,
    lettersBadge: Int,
    lettersUrgent: Boolean,
    onMenu: (Boolean) -> Unit,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    onRules: () -> Unit,
    onTopics: () -> Unit,
    onLetterRoom: () -> Unit,
    onRemove: () -> Unit,
    onBlock: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(50)).clickable(onClick = onBack)
                .semantics { contentDescription = "Zurück zu Chats"; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) { Text("←", fontSize = 22.sp) }
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable(onClick = onInfo)
                .semantics { contentDescription = "Infos zu ${friend.name}"; role = Role.Button }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = profileColor(friend.displayColor).copy(alpha = .18f),
                modifier = Modifier.size(40.dp),
            ) { Box(contentAlignment = Alignment.Center) { Text(friend.avatarEmoji, fontSize = 21.sp) } }
            Text(
                friend.name, Modifier.padding(start = 10.dp), fontWeight = FontWeight.Bold,
                fontSize = 18.sp, maxLines = 1,
            )
        }
        // The door to the letters. It is always here, so the Briefraum is exactly
        // one tap away from the conversation, whatever the timeline shows.
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(50)).clickable(onClick = onLetterRoom)
                .semantics {
                    contentDescription = if (lettersBadge > 0)
                        "Briefraum, $lettersBadge Briefe" else "Briefraum"
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (lettersBadge > 0) "✉$lettersBadge" else "✉",
                fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = if (lettersUrgent) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(50)).clickable(onClick = onTopics)
                .semantics {
                    contentDescription = if (topicsBadge > 0)
                        "Notizen, $topicsBadge offene Stichworte" else "Notizen"
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (topicsBadge > 0) "#$topicsBadge" else "#",
                fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Box {
            Box(
                Modifier.size(48.dp).clickable { onMenu(true) }
                    .semantics { contentDescription = "Weitere Aktionen"; role = Role.Button },
                contentAlignment = Alignment.Center,
            ) { Text("⋮", fontSize = 22.sp) }
            DropdownMenu(menu, { onMenu(false) }) {
                DropdownMenuItem(text = { Text("Freundschaftsregeln") }, onClick = { onMenu(false); onRules() })
                DropdownMenuItem(text = { Text("Info & Nachweise") }, onClick = { onMenu(false); onInfo() })
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
private fun Composer(
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
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (plan.sealVisible) Surface(
                modifier = Modifier.height(52.dp).clickable(onClick = onCompose)
                    .semantics { contentDescription = LetterAccess.LABEL_LONG; role = Role.Button },
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text("✦ Brief", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = onText,
                modifier = Modifier.weight(1f),
                maxLines = 4,
                enabled = plan.inputEnabled,
                placeholder = { Text(plan.placeholder, fontSize = 14.sp) },
                shape = RoundedCornerShape(26.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                // Messenger convention: sending keeps the keyboard and the focus.
                keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) onSend() }),
            )
            if (plan.inputEnabled) {
                val ready = text.isNotBlank()
                Surface(
                    modifier = Modifier.size(52.dp)
                        .clickable(enabled = ready, onClick = onSend)
                        .semantics { contentDescription = "Senden"; role = Role.Button },
                    shape = RoundedCornerShape(50),
                    color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (ready) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                ) { Box(contentAlignment = Alignment.Center) { Text("➤", fontSize = 19.sp) } }
            } else TextButton(onClick = onRules, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Regeln", fontSize = 13.sp)
            }
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
            InfoRow("Ebenen-Punkte", settings.epEnabled)
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
private fun SealMark(message: ApiClient.Message, opened: Boolean, modifier: Modifier = Modifier) {
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

@Composable
private fun ThreadChatBubble(message: ApiClient.ChatMessage, outgoing: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start) {
        Card(
            colors = CardDefaults.cardColors(containerColor = if (outgoing)
                MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(18.dp),
        ) { Column(Modifier.padding(11.dp)) {
            Text(message.text)
            Text(if (outgoing && message.readAt != null) "✓ gelesen" else socialDate(message.createdAt), fontSize = 10.sp)
        } }
    }
}

@Composable
internal fun EpOpportunityCard(opportunity: EpOpportunity, onProposeEp: (EpOpportunity) -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(opportunity.question, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onProposeEp(opportunity) }) { Text("EP vorschlagen") }
        }
    } }
}

@Composable
private fun EpProposalDialog(
    opportunity: EpOpportunity, onSubmit: (String, String?) -> Unit, onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(opportunity.letterTitle) }
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ebenen-Punkte vorschlagen") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("An ${opportunity.friendName} · verknüpft mit „${opportunity.letterTitle}“", fontSize = 12.sp)
            Text("Ebenen-Punkte (wie Experience Points, nur auf mehreren Ebenen)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun RequestDialog(
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

@Composable
private fun InboxScreen(
    messages: List<ApiClient.Message>, outbox: List<ApiClient.Message>, opened: Map<Long, OpenedMessage>,
    token: String, api: ApiClient, act: ((suspend () -> Unit) -> Unit),
    onOpenLetter: (ApiClient.Message) -> Unit, onLockedTap: (ApiClient.Message) -> Unit,
    onProof: (ApiClient.Message) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingExport by rememberSaveable { mutableStateOf<String?>(null) }
    var exportError by remember { mutableStateOf<String?>(null) }
    val exportPending = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val content = pendingExport
        if (uri != null && content != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                ?: error("Exportdatei konnte nicht geschrieben werden")
        }.onFailure { exportError = it.message ?: "Export fehlgeschlagen" }
        pendingExport = null
    }
    var showSent by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(Instant.now().epochSecond) }
    LaunchedEffect(Unit) { while (true) { now = Instant.now().epochSecond; delay(1000) } }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            OutlinedButton(onClick = { scope.launch {
                runCatching { api.pendingProofExport(token) }.onSuccess {
                    exportError = null; pendingExport = it; exportPending.launch("offene-briefe.gsverify.json")
                }.onFailure { exportError = it.message ?: "Export fehlgeschlagen" }
            } }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Öffentliche Prüfdateien exportieren") }
        }
        exportError?.let { item { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!showSent) Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("Empfangen (${messages.size})") }
                else OutlinedButton(onClick = { showSent = false }, modifier = Modifier.weight(1f)) { Text("Empfangen (${messages.size})") }
                if (showSent) Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("Gesendet (${outbox.size})") }
                else OutlinedButton(onClick = { showSent = true }, modifier = Modifier.weight(1f)) { Text("Gesendet (${outbox.size})") }
            }
        }
        val shown = if (showSent) outbox else messages
        if (shown.isEmpty()) item { InfoCard(if (showSent) "Noch nichts gesendet." else "Noch keine Briefe empfangen.") }
        if (!showSent) {
            shown.groupBy { it.peerName }.forEach { (name, peerMessages) ->
                item { SectionTitle("$name · ${peerMessages.size}") }
                items(peerMessages, key = { "in-${it.id}" }) { message ->
                    IncomingMessageCard(message, opened[message.id], now,
                        onOpen = { onOpenLetter(message) },
                        onApprove = { act { api.approve(token, message.id) } },
                        onReact = { emoji -> act { api.react(token, message.id, emoji) } },
                        onProof = { onProof(message) },
                        onLockedTap = { onLockedTap(message) },
                    )
                }
            }
        } else items(shown, key = { "out-${it.id}" }) { message ->
            OutboxMessageCard(message, now,
                onRelease = { act { api.release(token, message.id) } },
                onApprove = { act { api.approve(token, message.id) } },
                onRetract = { act { api.retract(token, message.id) } },
                onProof = { onProof(message) },
                onLockedTap = { onLockedTap(message) },
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun IncomingMessageCard(
    message: ApiClient.Message, opened: OpenedMessage?, now: Long,
    onOpen: () -> Unit, onApprove: () -> Unit, onReact: (String) -> Unit, onProof: () -> Unit,
    onLockedTap: () -> Unit, pulsing: Boolean = false, modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null && opened?.attachment != null) context.contentResolver.openOutputStream(uri)?.use { it.write(opened.attachment) }
    }
    val locked = !message.unlocked && opened == null
    Box(modifier.padding(top = 10.dp)) {
        Card(
            Modifier.fillMaxWidth().combinedClickable(
                onClick = { if (locked) onLockedTap() }, onLongClick = onProof,
            ),
            shape = RoundedCornerShape(18.dp),
            border = if (pulsing) BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary) else null,
            colors = CardDefaults.cardColors(
                containerColor = if (message.unlocked) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(Modifier.padding(start = 15.dp, end = 15.dp, top = 18.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message.title.ifBlank { "Versiegelter Brief" }, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    if (message.groupId != null) Text("👥", Modifier.padding(start = 6.dp), fontSize = 14.sp)
                    if (message.oneTime) Text("1×", Modifier.padding(start = 6.dp))
                }
                Text("Von ${message.peerName} · ${formatDate(message.createdAt)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (message.coverNote.isNotBlank()) Text("„${message.coverNote}“", fontStyle = FontStyle.Italic)
                if (message.proofStatus == "legacy") Text("Legacy – ohne kryptografischen Nachweis", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                when {
                    opened != null -> {
                        Text(opened.text, fontSize = 17.sp)
                        if (opened.attachment != null) OutlinedButton(onClick = { save.launch(safeFileName(opened.name ?: "Anhang")) }) { Text("📎 ${opened.name ?: "Anhang"} speichern") }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("❤️", "👍", "😂", "😮").forEach { emoji -> TextButton(onClick = { onReact(emoji) }) { Text(emoji) } } }
                    }
                    message.unlocked -> Button(onClick = onOpen) { Text(if (message.oneTime) "Einmalig öffnen" else "Brief öffnen") }
                    message.mode == "manual" -> Text("Wartet auf Freigabe durch ${message.peerName}")
                    message.mode == "mutual" -> {
                        Text("Beide müssen zustimmen")
                        if (!message.recipientApproved) Button(onClick = onApprove) { Text("Meine Freigabe bestätigen") }
                    }
                    message.mode == "presence" -> Text("Öffnet, sobald ihr beide online seid")
                    message.mode == "random" -> Text("Öffnet zufällig bis ${message.randomTo?.let(::formatDate) ?: "später"}")
                    else -> Text("🔒 Noch ${formatRemaining(max(0, (message.releaseAt ?: now) - now))}")
                }
                if (message.reactions.isNotEmpty()) Text(message.reactions.joinToString("  ") { "${it.emoji} ${it.name}" }, fontSize = 13.sp)
            }
        }
        SealMark(message, opened != null, Modifier.align(Alignment.TopStart).offset(x = 14.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun OutboxMessageCard(
    message: ApiClient.Message, now: Long, onRelease: () -> Unit, onApprove: () -> Unit,
    onRetract: () -> Unit, onProof: () -> Unit, onLockedTap: () -> Unit,
    pulsing: Boolean = false, modifier: Modifier = Modifier,
) {
    var confirmRelease by remember { mutableStateOf(false) }
    var confirmRetract by remember { mutableStateOf(false) }
    if (confirmRelease) ConfirmDialog("Jetzt wirklich freigeben?", "Der Empfänger kann die Nachricht danach lesen.", { confirmRelease = false; onRelease() }) { confirmRelease = false }
    if (confirmRetract) ConfirmDialog("Nachricht zurückziehen?", "Sie wird für beide Seiten gelöscht.", { confirmRetract = false; onRetract() }) { confirmRetract = false }
    Box(modifier.padding(top = 10.dp)) {
        Card(
            Modifier.fillMaxWidth().combinedClickable(
                onClick = { if (!message.unlocked) onLockedTap() },
                onLongClick = onProof,
            ),
            shape = RoundedCornerShape(18.dp),
            border = if (pulsing) BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary) else null,
        ) { Column(Modifier.padding(start = 15.dp, end = 15.dp, top = 18.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(message.title.ifBlank { "An ${message.peerName}" }, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                if (message.groupId != null) Text("👥", Modifier.padding(start = 6.dp), fontSize = 14.sp)
                if (message.oneTime) Text("1×", Modifier.padding(start = 6.dp))
            }
            Text("An ${message.peerName} · ${formatDate(message.createdAt)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (message.coverNote.isNotBlank()) Text("„${message.coverNote}“", fontStyle = FontStyle.Italic)
            if (message.proofStatus == "legacy") Text("Legacy – ohne kryptografischen Nachweis", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            when {
                message.unlocked -> Text(if (message.readAt != null) "✓✓ Gelesen" else "✓ Freigegeben", color = Color(0xFF19703B))
                message.mode == "manual" -> Button(onClick = { confirmRelease = true }) { Text("Jetzt freigeben") }
                message.mode == "mutual" && !message.senderApproved -> Button(onClick = onApprove) { Text("Meine Freigabe bestätigen") }
                message.mode == "mutual" -> Text("Wartet auf Zustimmung von ${message.peerName}")
                message.mode == "presence" -> Text("Wartet, bis ihr beide online seid")
                message.mode == "random" -> Text("Zufällige Freigabe bis ${message.randomTo?.let(::formatDate)}")
                else -> Text("🔒 Automatisch in ${formatRemaining(max(0, (message.releaseAt ?: now) - now))}")
            }
            if (!message.unlocked) TextButton(onClick = { confirmRetract = true }) { Text("Zurückziehen") }
            if (message.reactions.isNotEmpty()) Text(message.reactions.joinToString("  ") { "${it.emoji} ${it.name}" })
        } }
        SealMark(message, opened = message.unlocked, modifier = Modifier.align(Alignment.TopStart).offset(x = 14.dp))
    }
}

// ---------------------------------------------------------------------------
// Letter composer (opened as a sheet from the thread)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SendScreen(
    token: String, api: ApiClient, friends: List<ApiClient.UserSummary>, groups: List<ApiClient.Group>,
    settings: List<ApiClient.FriendshipSettings>,
    initialRecipient: Long?, creativeActive: Boolean = false,
    onClose: () -> Unit, onSent: (Long?, Long?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }; var coverNote by remember { mutableStateOf("") }; var text by remember { mutableStateOf("") }
    var selected by remember(initialRecipient) { mutableStateOf(initialRecipient?.let(::setOf) ?: emptySet()) }; var groupId by remember { mutableStateOf<Long?>(null) }
    var mode by remember { mutableStateOf(ComposeMode.DURATION) }; var amount by remember { mutableStateOf("3") }
    var modeMenu by remember { mutableStateOf(false) }
    var recipientMenu by remember { mutableStateOf(false) }
    var unit by remember { mutableStateOf(DelayUnit.DAYS) }; var dateTime by remember { mutableStateOf(LocalDateTime.now().plusDays(1).withSecond(0).withNano(0)) }
    var randomStart by remember { mutableStateOf("1") }; var randomEnd by remember { mutableStateOf("24") }
    var randomStartUnit by remember { mutableStateOf(DelayUnit.HOURS) }; var randomEndUnit by remember { mutableStateOf(DelayUnit.HOURS) }
    var oneTime by remember { mutableStateOf(false) }
    var attachment by remember { mutableStateOf<Triple<String, String, ByteArray>?>(null) }; var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }; var confirmManual by remember { mutableStateOf(false) }
    var detailsOpen by remember { mutableStateOf(false) }; var infoSheet by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val bodyFocus = remember { FocusRequester() }
    // The sealed text field owns the screen, so it takes the focus as the sheet
    // settles; requesting earlier would hit an unattached FocusRequester.
    LaunchedEffect(Unit) { delay(250); runCatching { bodyFocus.requestFocus() } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                require(bytes.size <= 2_000_000) { "Der Anhang darf höchstens 2 MB groß sein." }
                var name = "Anhang"; context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (cursor.moveToFirst() && index >= 0) name = cursor.getString(index)
                }
                Triple(name, context.contentResolver.getType(uri) ?: "application/octet-stream", bytes)
            } }.onSuccess { attachment = it }.onFailure { notice = it.message }
        }
    }
    val targetValid = groupId != null || selected.isNotEmpty()
    val minimumDelay = if (groupId == null) selected.mapNotNull { id ->
        settings.firstOrNull { it.friendId == id }?.minLetterDelaySeconds
    }.maxOrNull() ?: 0L else 0L
    val now = Instant.now().epochSecond
    val scheduleValid = when (mode) {
        ComposeMode.DURATION -> (amount.toLongOrNull() ?: 0) > 0
        ComposeMode.DATE_TIME -> dateTime.atZone(ZoneId.systemDefault()).toEpochSecond() > now
        ComposeMode.RANDOM -> {
            val from = (randomStart.toLongOrNull() ?: 0) * randomStartUnit.seconds
            val to = (randomEnd.toLongOrNull() ?: 0) * randomEndUnit.seconds
            from >= 60 && to - from >= 60
        }
        else -> true
    }
    suspend fun send() {
        busy = true; notice = null
        runCatching {
            val current = Instant.now().epochSecond
            val minimum = current + minimumDelay + 5
            val release = when (mode) {
                ComposeMode.DURATION -> maxOf(current + (amount.toLongOrNull() ?: 0) * unit.seconds, minimum)
                ComposeMode.DATE_TIME -> dateTime.atZone(ZoneId.systemDefault()).toEpochSecond()
                else -> null
            }
            val randomFrom = if (mode == ComposeMode.RANDOM)
                maxOf(current + (randomStart.toLongOrNull() ?: 1) * randomStartUnit.seconds, minimum) else null
            val randomTo = if (mode == ComposeMode.RANDOM) {
                val requested = current + (randomEnd.toLongOrNull() ?: 24) * randomEndUnit.seconds
                if (randomFrom != null) maxOf(requested, randomFrom + 60) else requested
            } else null
            val metadata = LetterMetadata(
                title.trim(), coverNote.trim(), if (groupId == null) selected.toList() else emptyList(),
                groupId, mode.api, release, randomFrom, randomTo, oneTime,
            )
            val sealed = withContext(Dispatchers.Default) {
                EvidenceProtocol.seal(text, metadata, attachment?.first, attachment?.second, attachment?.third)
            }
            val encrypted = CryptoBox.Encrypted(sealed.ciphertext, sealed.nonce, sealed.releaseKey)
            val encryptedAttachment = sealed.attachment?.let {
                ApiClient.EncryptedAttachment(it.name, it.mime, it.ciphertext, it.nonce)
            }
            api.send(token, ApiClient.SendRequest(
                recipientIds = if (groupId == null) selected.toList() else emptyList(), groupId = groupId,
                encrypted = encrypted, title = title.trim(), mode = mode.api, releaseAt = release,
                randomFrom = randomFrom, randomTo = randomTo,
                oneTime = oneTime, attachment = encryptedAttachment, coverNote = coverNote.trim(), evidence = sealed.evidence,
            ))
        }.onSuccess { ids ->
            val likelyFriend = if (groupId == null) selected.firstOrNull() else null
            text = ""; title = ""; coverNote = ""; attachment = null; notice = "✓ Verschlüsselt gesendet"
            onSent(ids.firstOrNull(), likelyFriend)
        }
            .onFailure { notice = it.message ?: "Senden fehlgeschlagen" }
        busy = false
    }
    if (confirmManual) ConfirmDialog("Manuelle Nachricht senden?", "Sie bleibt gesperrt, bis du sie später ausdrücklich freigibst.",
        { confirmManual = false; scope.launch { send() } }) { confirmManual = false }

    // Every chooser is a chip sheet without a text field, so picking never opens
    // the keyboard; the keyboard is hidden explicitly before a sheet appears.
    if (recipientMenu) ModalBottomSheet(onDismissRequest = { recipientMenu = false }) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("An wen?", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            friends.forEach { friend ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        groupId = null
                        selected = if (friend.id in selected) selected - friend.id else selected + friend.id
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = if (friend.id in selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(friend.avatarEmoji, fontSize = 22.sp, modifier = Modifier.width(38.dp))
                        Text(friend.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        if (friend.id in selected) Text("✓", fontSize = 19.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (groups.isNotEmpty()) {
                HorizontalDivider()
                Text("Oder eine Gruppe", fontWeight = FontWeight.SemiBold)
                groups.forEach { group ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            groupId = if (groupId == group.id) null else group.id
                            if (groupId != null) selected = emptySet()
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (groupId == group.id) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("👥", fontSize = 22.sp, modifier = Modifier.width(38.dp))
                            Text("${group.name} (${group.members.size})", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            if (groupId == group.id) Text("✓", fontSize = 19.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Button(onClick = { recipientMenu = false }, Modifier.fillMaxWidth()) { Text("Fertig") }
        }
    }

    if (infoSheet) ModalBottomSheet(onDismissRequest = { infoSheet = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Was das Siegel leistet", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(CRYPTO_HONESTY)
        }
    }

    if (modeMenu) ModalBottomSheet(onDismissRequest = { modeMenu = false }) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Wann öffnen?", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Wähle, wann die Nachricht sichtbar werden darf.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            ComposeMode.entries.forEach { choice ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { mode = choice },
                    shape = RoundedCornerShape(18.dp),
                    color = if (mode == choice) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(modeIcon(choice), fontSize = 22.sp, modifier = Modifier.width(42.dp), color = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(choice.label, fontWeight = FontWeight.SemiBold)
                            Text(modeDescription(choice), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (mode == choice) Text("✓", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            // The chosen mode's own inputs live right below it, not on the main sheet.
            when (mode) {
                ComposeMode.DURATION -> NumberAndUnit(amount, { amount = it }, unit, { unit = it })
                ComposeMode.DATE_TIME -> DateTimeChooser(dateTime) { dateTime = it }
                ComposeMode.RANDOM -> {
                    Text("Zufällig innerhalb dieses Fensters:")
                    NumberAndUnit(randomStart, { randomStart = it }, randomStartUnit, { randomStartUnit = it }, "Frühestens")
                    NumberAndUnit(randomEnd, { randomEnd = it }, randomEndUnit, { randomEndUnit = it }, "Spätestens")
                    Text("Das Fenster muss mindestens 1 Minute groß sein. Den tatsächlichen Zeitpunkt wählt der Server.", fontSize = 12.sp)
                }
                else -> Unit
            }
            if (minimumDelay > 0) Text(
                "Diese Freundschaft verlangt mindestens ${formatRemaining(minimumDelay)} bis zur Freigabe; kürzere Zeiten werden automatisch angehoben.",
                fontSize = 12.sp,
            )
            Button(onClick = { modeMenu = false }, Modifier.fillMaxWidth()) { Text("Übernehmen") }
        }
    }

    val recipientLabel = when {
        groupId != null -> "👥 " + groups.firstOrNull { it.id == groupId }?.name.orEmpty()
        selected.isEmpty() -> "Empfänger wählen"
        else -> friends.filter { it.id in selected }.joinToString { "${it.avatarEmoji} ${it.name}" }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("←", fontSize = 20.sp) }
            Column(Modifier.weight(1f)) {
                Text("An", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(recipientLabel, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            if (friends.size > 1 || groups.isNotEmpty()) TextButton(onClick = { keyboard?.hide(); recipientMenu = true }) { Text("＋") }
        }
        HorizontalDivider()

        // One large field owns the screen; everything else is a chip below it.
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)
                .focusRequester(bodyFocus),
            placeholder = { Text("Dein versiegelter Text …") },
            label = null,
            shape = RoundedCornerShape(18.dp),
        )

        if (detailsOpen) Column(
            Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(title, { title = it.take(100) }, Modifier.fillMaxWidth(),
                label = { Text("Brieftitel – optional") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
            OutlinedTextField(coverNote, { coverNote = it.take(1000) }, Modifier.fillMaxWidth(),
                label = { Text("Öffentlicher Umschlagtext") },
                supportingText = { Text("Offen und unverschlüsselt sichtbar") }, minLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(oneTime, { oneTime = it }); Spacer(Modifier.width(8.dp)); Text("Nur einmal lesbar")
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ComposerChip(sealChipLabel(mode, amount, unit, dateTime, minimumDelay), highlighted = true) {
                keyboard?.hide(); modeMenu = true
            }
            // Creative testing: one tap per release preset. The letter itself takes
            // the ordinary path — same friendship gate, same crypto, same server —
            // and every preset stays fast-forwardable, so the whole journey of a
            // test letter can be walked without the other side pressing anything.
            if (creativeActive) CreativeMode.LETTER_PRESETS.forEach { (label, _, seconds) ->
                ComposerChip("⚗ $label") {
                    val chosen = DelayUnit.entries.lastOrNull { seconds >= it.seconds && seconds % it.seconds == 0L }
                        ?: DelayUnit.MINUTES
                    mode = ComposeMode.DURATION; unit = chosen; amount = (seconds / chosen.seconds).toString()
                }
            }
            ComposerChip(attachment?.let { "📎 ${it.first}" } ?: "📎") { keyboard?.hide(); picker.launch("*/*") }
            ComposerChip(if (detailsOpen) "－ Details" else "＋ Details") { detailsOpen = !detailsOpen }
            if (oneTime) ComposerChip("1×")
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "🔏 Der Server hält den Schlüssel bis zur Freigabe.",
                Modifier.weight(1f), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { keyboard?.hide(); infoSheet = true }) { Text("Mehr", fontSize = 12.sp) }
        }
        attachment?.let { Text("${it.third.size / 1024} KB · verschlüsselt", Modifier.padding(horizontal = 14.dp), fontSize = 11.sp) }
        notice?.let { Text(it, Modifier.padding(horizontal = 14.dp), color = if (it.startsWith("✓")) Color(0xFF19703B) else MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        Button(
            onClick = { keyboard?.hide(); if (mode == ComposeMode.MANUAL) confirmManual = true else scope.launch { send() } },
            enabled = targetValid && text.isNotBlank() && scheduleValid && !busy,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).height(52.dp),
        ) { Text(if (busy) "Wird versiegelt …" else "✦ Versiegelt senden", fontWeight = FontWeight.Bold) }
    }
}

internal const val CRYPTO_HONESTY =
    "AES-256-GCM schützt Vertraulichkeit und Integrität in Speicherung und Übertragung. " +
        "Der Server hält den AES-Schlüssel bis zur Freigabe und kann technisch auf gesperrte Inhalte zugreifen. " +
        "Zeit, Zufall, manuell, gegenseitig und Präsenz unterscheiden nur das serverseitige Freigabe-Gate. " +
        "Präsenz ist serverbestätigte Aktivität, keine ausdrückliche Zustimmung. Einmal-Lesen ist App-/Server-Regel " +
        "und verhindert keine Screenshots oder Caches."

@Composable
private fun ComposerChip(text: String, highlighted: Boolean = false, onClick: (() -> Unit)? = null) {
    Surface(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        shape = RoundedCornerShape(50),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(text, Modifier.padding(horizontal = 13.dp, vertical = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Short, honest summary of the release gate, including the agreed minimum delay. */
private fun sealChipLabel(
    mode: ComposeMode, amount: String, unit: DelayUnit, dateTime: LocalDateTime, minimumDelay: Long,
): String {
    val base = when (mode) {
        ComposeMode.DURATION -> "◷ ${amount.ifBlank { "0" }} ${unit.label}"
        ComposeMode.DATE_TIME -> "▣ " + dateTime.format(DateTimeFormatter.ofPattern("dd.MM. HH:mm"))
        ComposeMode.MANUAL -> "☝ Von mir freigeben"
        ComposeMode.MUTUAL -> "✓✓ Beide stimmen zu"
        ComposeMode.PRESENCE -> "● Beide online"
        ComposeMode.RANDOM -> "? Zufällig"
    }
    return if (minimumDelay > 0) "$base · mind. ${formatRemaining(minimumDelay)}" else base
}

// ---------------------------------------------------------------------------
// People: friends, requests, groups, search
// ---------------------------------------------------------------------------

/**
 * People, ordered by how often something is actually done here: answer an open
 * request, open a friend, find and ask a person. Rules, removing and blocking
 * live one level down behind each row's ⋮ — present, but never a dashboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendsScreen(
    token: String, api: ApiClient, users: List<ApiClient.UserSummary>, friends: List<ApiClient.UserSummary>,
    incoming: List<ApiClient.IncomingRequest>, outgoing: List<ApiClient.OutgoingRequest>, groups: List<ApiClient.Group>,
    settings: List<ApiClient.FriendshipSettings>, onOpenThread: (Long) -> Unit,
    act: ((suspend () -> Unit) -> Unit),
) {
    val scope = rememberCoroutineScope(); var search by remember { mutableStateOf("") }; var result by remember { mutableStateOf<List<ApiClient.UserSummary>>(emptyList()) }
    var groupName by remember { mutableStateOf("") }; var groupMembers by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var creatingGroup by remember { mutableStateOf(false) }
    var rulesFriend by remember { mutableStateOf<ApiClient.UserSummary?>(null) }
    var confirmRemove by remember { mutableStateOf<ApiClient.UserSummary?>(null) }
    var confirmBlock by remember { mutableStateOf<ApiClient.UserSummary?>(null) }

    confirmRemove?.let { friend ->
        ConfirmDialog(
            "${friend.name} als Freund entfernen?",
            "Briefe und Chats dieser Freundschaft sind danach nicht mehr zugänglich.",
            { confirmRemove = null; act { api.removeFriend(token, friend.id) } },
        ) { confirmRemove = null }
    }
    confirmBlock?.let { friend ->
        ConfirmDialog(
            "${friend.name} blockieren?",
            "Die Freundschaft endet und ${friend.name} kann dich nicht mehr erreichen.",
            { confirmBlock = null; act { api.block(token, friend.id) } },
        ) { confirmBlock = null }
    }
    rulesFriend?.let { friend ->
        ModalBottomSheet(onDismissRequest = { rulesFriend = null }) {
            FriendshipRulesEditor(
                friendName = friend.name,
                settings = settings.firstOrNull { it.friendId == friend.id },
                token = token, api = api, act = act,
                onDone = { rulesFriend = null },
            )
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        if (incoming.isNotEmpty()) { item { SectionTitle("Wartet auf dich") }; items(incoming, key = { "req-${it.id}" }) { request ->
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) { Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${request.senderName} möchte mit dir befreundet sein", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { act { api.respondFriend(token, request.id, true) } }, modifier = Modifier.height(48.dp)) { Text("Bestätigen") }
                    OutlinedButton(onClick = { act { api.respondFriend(token, request.id, false) } }, modifier = Modifier.height(48.dp)) { Text("Ablehnen") }
                }
            } }
        } }
        item { SectionTitle("Freunde") }
        if (friends.isEmpty()) item { InfoCard("Noch keine Freunde.") }
        else items(friends, key = { "friend-${it.id}" }) { friend ->
            val rules = settings.firstOrNull { it.friendId == friend.id }
            FriendRow(
                friend = friend,
                silenced = rules != null && !rules.lettersEnabled && !rules.chatsEnabled,
                pendingProposal = rules?.incomingProposal != null,
                onOpen = { onOpenThread(friend.id) },
                onRules = { rulesFriend = friend },
                onRemove = { confirmRemove = friend },
                onBlock = { confirmBlock = friend },
            )
        }
        item { SectionTitle("Person finden") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(search, { search = it }, Modifier.weight(1f), label = { Text("Exakter Benutzername") }, singleLine = true)
            Button(
                onClick = { scope.launch { result = runCatching { api.searchUser(token, search.trim()) }.getOrDefault(emptyList()) } },
                enabled = search.isNotBlank(),
                modifier = Modifier.height(48.dp),
            ) { Text("Suchen") }
        } }
        val discover = if (result.isNotEmpty()) result else users
        items(discover.filter { it.relationship == "none" }, key = { "discover-${it.id}" }) { user ->
            ActionCard("${user.avatarEmoji} ${user.name}") { Button(onClick = { act { api.requestFriend(token, user.id) } }) { Text("Anfragen") } }
        }
        if (outgoing.isNotEmpty()) { item { SectionTitle("Angefragt") }; items(outgoing, key = { "outreq-${it.id}" }) { request ->
            ActionCard("${request.recipientName}") { TextButton(onClick = { act { api.withdrawFriendRequest(token, request.id) } }) { Text("Zurückziehen") } }
        } }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Gruppen")
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { creatingGroup = !creatingGroup }) { Text(if (creatingGroup) "Schließen" else "+ Neue Gruppe") }
        } }
        if (groups.isEmpty()) item { InfoCard("Noch keine Gruppe.") }
        else items(groups, key = { "group-${it.id}" }) { group -> InfoCard("👥 ${group.name}: ${group.members.joinToString { it.name }}") }
        if (friends.isNotEmpty() && creatingGroup) item {
            Card { Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Neue Gruppe", fontWeight = FontWeight.Bold)
                OutlinedTextField(groupName, { groupName = it }, Modifier.fillMaxWidth(), label = { Text("Gruppenname") })
                friends.forEach { friend -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(friend.id in groupMembers, { checked -> groupMembers = if (checked) groupMembers + friend.id else groupMembers - friend.id }); Text(friend.name)
                } }
                Button(onClick = { act { api.createGroup(token, groupName.trim(), groupMembers.toList()); groupName = ""; groupMembers = emptySet() } },
                    enabled = groupName.isNotBlank() && groupMembers.isNotEmpty()) { Text("Gruppe erstellen") }
            } }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

/** One friend, one warm row: the row opens the conversation, ⋮ manages it. */
@Composable
private fun FriendRow(
    friend: ApiClient.UserSummary,
    silenced: Boolean,
    pendingProposal: Boolean,
    onOpen: () -> Unit,
    onRules: () -> Unit,
    onRemove: () -> Unit,
    onBlock: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = profileColor(friend.displayColor).copy(alpha = .18f),
                modifier = Modifier.size(44.dp),
            ) { Box(contentAlignment = Alignment.Center) { Text(friend.avatarEmoji, fontSize = 22.sp) } }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(friend.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1)
                if (silenced) Text(
                    "Briefe und Chat sind aus", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                ) else if (pendingProposal) Text(
                    "Regelvorschlag wartet auf dich", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Box {
                Box(
                    Modifier.size(48.dp).clickable { menu = true }
                        .semantics { contentDescription = "Verwaltung für ${friend.name}"; role = Role.Button },
                    contentAlignment = Alignment.Center,
                ) { Text("⋮", fontSize = 22.sp) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Freundschaftsregeln") }, onClick = { menu = false; onRules() })
                    DropdownMenuItem(text = { Text("Freund entfernen") }, onClick = { menu = false; onRemove() })
                    DropdownMenuItem(
                        text = { Text("Blockieren", color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onBlock() },
                    )
                }
            }
        }
    }
}

/**
 * The one editor for the bilateral friendship rules, shared by the thread sheet
 * and the people tab. There is deliberately no direct-save path: everything
 * leaves as a proposal, an open proposal is answerable or withdrawable here.
 */
@Composable
internal fun FriendshipRulesEditor(
    friendName: String,
    settings: ApiClient.FriendshipSettings?,
    token: String,
    api: ApiClient,
    act: ((suspend () -> Unit) -> Unit),
    onDone: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Freundschaftsregeln", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        when {
            settings == null -> Text(
                "Diese Freundschaft ist nicht mehr aktiv.",
                color = MaterialTheme.colorScheme.error,
            )
            settings.incomingProposal != null -> {
                val proposal = settings.incomingProposal
                Text("$friendName schlägt vor:", fontWeight = FontWeight.SemiBold)
                Text(
                    settingsSummary(proposal.lettersEnabled, proposal.chatsEnabled, proposal.epEnabled, proposal.minLetterDelaySeconds),
                    fontSize = 13.sp,
                )
                Text(
                    "Bisher: " + settingsSummary(settings.lettersEnabled, settings.chatsEnabled, settings.epEnabled, settings.minLetterDelaySeconds),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { act { api.respondFriendshipSettings(token, proposal.id, true) }; onDone() },
                        modifier = Modifier.height(48.dp),
                    ) { Text("Annehmen") }
                    OutlinedButton(
                        onClick = { act { api.respondFriendshipSettings(token, proposal.id, false) }; onDone() },
                        modifier = Modifier.height(48.dp),
                    ) { Text("Ablehnen") }
                }
            }
            settings.outgoingProposal != null -> {
                val proposal = settings.outgoingProposal
                Text("Dein Vorschlag wartet auf $friendName:", fontWeight = FontWeight.SemiBold)
                Text(
                    settingsSummary(proposal.lettersEnabled, proposal.chatsEnabled, proposal.epEnabled, proposal.minLetterDelaySeconds),
                    fontSize = 13.sp,
                )
                OutlinedButton(
                    onClick = { act { api.withdrawFriendshipSettings(token, proposal.id) }; onDone() },
                    modifier = Modifier.height(48.dp),
                ) { Text("Zurückziehen") }
            }
            else -> {
                var draft by remember(settings) { mutableStateOf(settings) }
                ToggleSetting("Briefe", draft.lettersEnabled) { draft = draft.copy(lettersEnabled = it) }
                ToggleSetting("Chats", draft.chatsEnabled) { draft = draft.copy(chatsEnabled = it) }
                ToggleSetting("Ebenen-Punkte", draft.epEnabled) { draft = draft.copy(epEnabled = it) }
                OutlinedTextField(
                    draft.minLetterDelaySeconds.toString(),
                    { entered -> draft = draft.copy(minLetterDelaySeconds = entered.filter(Char::isDigit).toLongOrNull() ?: 0) },
                    Modifier.fillMaxWidth(), label = { Text("Mindest-Briefverzögerung in Sekunden") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Button(
                    onClick = { act { api.proposeFriendshipSettings(token, draft) }; onDone() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Als Vorschlag senden") }
                Text(
                    "Gilt erst, wenn $friendName zustimmt.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// More: settings hub with everything grouped and reachable
// ---------------------------------------------------------------------------

@Composable
private fun MoreScreen(
    token: String, api: ApiClient, store: SessionStore, status: ApiClient.Status?, sessions: List<ApiClient.Session>,
    blocked: List<ApiClient.UserSummary>, messages: List<ApiClient.Message>, outbox: List<ApiClient.Message>,
    ep: ApiClient.EpOverview?, settings: List<ApiClient.FriendshipSettings>, opened: Map<Long, OpenedMessage>,
    onTheme: (String) -> Unit, onLogout: () -> Unit,
    serverProfile: ServerProfile, onServerProfile: (ServerProfile) -> Unit,
    castleExperiment: Boolean, onCastleExperiment: (Boolean) -> Unit,
    creativeEligible: Boolean, creativeSwitch: Boolean, onCreativeSwitch: (Boolean) -> Unit,
    onRecovery: (String) -> Unit, onProof: (ApiClient.Message) -> Unit, onOpenLetter: (ApiClient.Message) -> Unit,
    onLockedTap: (ApiClient.Message) -> Unit, act: ((suspend () -> Unit) -> Unit),
) {
    var dest by remember { mutableStateOf<MoreDest?>(null) }
    BackHandler(enabled = dest != null) { dest = null }
    when (dest) {
        MoreDest.LETTERS -> SubScreen("Briefe & Prüfdateien", onBack = { dest = null }) {
            InboxScreen(messages, outbox, opened, token, api, act, onOpenLetter, onLockedTap, onProof)
        }
        MoreDest.EP -> SubScreen("Ebenen-Punkte", onBack = { dest = null }) {
            EpScreen(token, api, settings, ep, draft = null, onDraftConsumed = {}, act = act)
        }
        null -> MoreHub(token, api, store, status, sessions, blocked, ep, onTheme, onLogout,
            serverProfile, onServerProfile, castleExperiment, onCastleExperiment,
            creativeEligible, creativeSwitch, onCreativeSwitch, onRecovery,
            onDest = { dest = it }, act = act)
    }
}

@Composable
private fun SubScreen(
    title: String,
    onBack: () -> Unit,
    backLabel: String = "Mehr",
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("← $backLabel") }
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold)
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun MoreHub(
    token: String, api: ApiClient, store: SessionStore, status: ApiClient.Status?, sessions: List<ApiClient.Session>,
    blocked: List<ApiClient.UserSummary>, ep: ApiClient.EpOverview?,
    onTheme: (String) -> Unit, onLogout: () -> Unit,
    serverProfile: ServerProfile, onServerProfile: (ServerProfile) -> Unit,
    castleExperiment: Boolean, onCastleExperiment: (Boolean) -> Unit,
    creativeEligible: Boolean, creativeSwitch: Boolean, onCreativeSwitch: (Boolean) -> Unit,
    onRecovery: (String) -> Unit, onDest: (MoreDest) -> Unit, act: ((suspend () -> Unit) -> Unit),
) {
    val context = LocalContext.current
    val notificationGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
    var name by remember(status?.name) { mutableStateOf(status?.name.orEmpty()) }; var emoji by remember(status?.avatarEmoji) { mutableStateOf(status?.avatarEmoji ?: "👤") }
    var color by remember(status?.displayColor) { mutableStateOf(status?.displayColor ?: "#6750A4") }; var discoverable by remember(status?.discoverable) { mutableStateOf(status?.discoverable ?: true) }
    var oldPassword by remember { mutableStateOf("") }; var newPassword by remember { mutableStateOf("") }; var migrationPassword by remember { mutableStateOf("") }
    var biometric by remember { mutableStateOf(store.biometricEnabled) }
    var advanced by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionTitle("Postfach & Verlauf") }
        item { NavCard("✉  Briefe & Prüfdateien", "Empfangene und gesendete Briefe, Nachweis-Export") { onDest(MoreDest.LETTERS) } }
        item { NavCard("★  Ebenen-Punkte", "Verlauf, Summen und Level · Gegeben ${ep?.given ?: 0} · Erhalten ${ep?.received ?: 0}") { onDest(MoreDest.EP) } }

        item { SectionTitle("Profil") }
        item { Card { Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (status?.needsPassword == true) {
                Text("Bestehendes Profil absichern", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                OutlinedTextField(migrationPassword, { migrationPassword = it }, Modifier.fillMaxWidth(), label = { Text("Neues Passwort") }, visualTransformation = PasswordVisualTransformation())
                Button(onClick = { act { onRecovery(api.setPassword(token, migrationPassword)) } }, enabled = migrationPassword.length >= 8) { Text("Passwort setzen") }
                HorizontalDivider()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(emoji, { emoji = it.take(8) }, Modifier.width(90.dp), label = { Text("Avatar") })
                OutlinedTextField(name, { name = it }, Modifier.weight(1f), label = { Text("Benutzername") })
            }
            Text("Profilfarbe", fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("#195C3A" to "Grün", "#315B8A" to "Blau", "#8A671B" to "Gold", "#8A3B3B" to "Rot").forEach { (value, label) ->
                    if (color.equals(value, true)) Button(onClick = {}) { Text("● $label") }
                    else OutlinedButton(onClick = { color = value }) { Text("● $label", color = profileColor(value)) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(discoverable, { discoverable = it }); Spacer(Modifier.width(8.dp)); Text("In Nutzerliste sichtbar") }
            Button(onClick = { act { api.updateProfile(token, name, emoji, color, discoverable); store.name = name } },
                enabled = name.isNotBlank() && Regex("^#[0-9A-Fa-f]{6}$").matches(color)) { Text("Profil speichern") }
        } } }

        item { SectionTitle("Darstellung und Schutz") }
        item { Card { Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Theme")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("system" to "System", "light" to "Hell", "dark" to "Dunkel", "antique" to "Altertümlich").forEach { (key, label) ->
                    if (store.theme == key) Button(onClick = {}) { Text(label) } else OutlinedButton(onClick = { onTheme(key) }) { Text(label) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(biometric, { biometric = it; store.biometricEnabled = it }); Spacer(Modifier.width(8.dp)); Text("Beim Start biometrisch sperren") }
            if (!notificationGranted && Build.VERSION.SDK_INT >= 33) {
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        })
                    }
                }) { Text("Benachrichtigungen in Android erlauben") }
            } else Text("✓ Benachrichtigungen erlaubt", fontSize = 12.sp, color = Color(0xFF19703B))
        } } }

        // The calm info surface. The chrome above only carries the short warning
        // strip, so the full sentence and the build number belong here.
        item { SectionTitle("Server") }
        item { Card { Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ServerProfileSelector(serverProfile, onServerProfile)
            if (!serverProfile.isConfigured()) Text("Nicht konfiguriert", color = MaterialTheme.colorScheme.error)
            if (status?.serverRole == "test") Text(
                ServerProfilePolicy.TEST_WARNING_DETAIL,
                fontSize = 12.sp, color = MaterialTheme.colorScheme.error,
            )
            Text(
                "App ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } } }

        item { OutlinedButton(onClick = { advanced = !advanced }, Modifier.fillMaxWidth().height(50.dp)) {
            Text(if (advanced) "Weniger anzeigen" else "Konto, Geräte und Blockierungen")
        } }
        if (advanced) {
            item { SectionTitle("Konto & Sicherheit") }
            item { Card { Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(oldPassword, { oldPassword = it }, Modifier.fillMaxWidth(), label = { Text("Altes Passwort") }, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(newPassword, { newPassword = it }, Modifier.fillMaxWidth(), label = { Text("Neues Passwort") }, visualTransformation = PasswordVisualTransformation())
                Button(onClick = { act { api.changePassword(token, oldPassword, newPassword); oldPassword = ""; newPassword = "" } }, enabled = oldPassword.isNotBlank() && newPassword.length >= 8) { Text("Passwort ändern") }
                OutlinedButton(onClick = { act { onRecovery(api.newRecoveryCode(token)) } }) { Text("Neuen Wiederherstellungscode erzeugen") }
            } } }
            item { SectionTitle("Angemeldete Geräte") }
            items(sessions, key = { "session-${it.id}" }) { session -> ActionCard("${session.deviceName}\nZuletzt ${formatDate(session.lastSeenAt)}${if (session.current) " · dieses Gerät" else ""}") {
                if (!session.current) TextButton(onClick = { act { api.revokeSession(token, session.id) } }) { Text("Abmelden") }
            } }
            if (blocked.isNotEmpty()) { item { SectionTitle("Blockierte Nutzer") }; items(blocked, key = { "blocked-${it.id}" }) { user ->
                ActionCard(user.name) { TextButton(onClick = { act { api.unblock(token, user.id) } }) { Text("Freigeben") } }
            } }

            item { SectionTitle("Erweiterte Einstellungen") }
            item { Card { Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Rückschrittlichen Modus aktivieren", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Switch(castleExperiment, onCastleExperiment)
            } } }
            if (creativeEligible) item { Card { Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Kreativmodus (Testserver)", fontWeight = FontWeight.SemiBold)
                    Text("Frei bauen zum Testen; Briefe und EP laufen echt.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(creativeSwitch, onCreativeSwitch)
            } } }
        }
        item { OutlinedButton(onClick = onLogout, Modifier.fillMaxWidth()) { Text("Auf diesem Gerät abmelden") } }
        item { Text("Neue Nachrichten werden regelmäßig im Hintergrund geprüft.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun NavCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", fontSize = 22.sp)
        }
    }
}

// ---------------------------------------------------------------------------
// Shared small pieces
// ---------------------------------------------------------------------------

@Composable
private fun NumberAndUnit(
    amount: String, onAmount: (String) -> Unit, unit: DelayUnit, onUnit: (DelayUnit) -> Unit,
    label: String = "Nach",
) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(amount, { onAmount(it.filter(Char::isDigit).take(4)) }, Modifier.width(140.dp), label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Box { OutlinedButton(onClick = { menu = true }) { Text(unit.label) }; DropdownMenu(menu, { menu = false }) {
            DelayUnit.entries.forEach { choice -> DropdownMenuItem({ Text(choice.label) }, { onUnit(choice); menu = false }) }
        } }
    }
}

@Composable
private fun DateTimeChooser(value: LocalDateTime, onChange: (LocalDateTime) -> Unit) {
    val context = LocalContext.current
    Text("Gewählt: ${value.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}", fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { DatePickerDialog(context, { _, y, m, d -> onChange(value.withYear(y).withMonth(m + 1).withDayOfMonth(d)) }, value.year, value.monthValue - 1, value.dayOfMonth).show() }) { Text("Datum") }
        OutlinedButton(onClick = { TimePickerDialog(context, { _, h, min -> onChange(value.withHour(h).withMinute(min)) }, value.hour, value.minute, true).show() }) { Text("Uhrzeit") }
    }
}

@Composable
private fun RecoveryDialog(code: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Sicherheitscode sichern") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Notiere diesen Code. Er wird nur einmal angezeigt:")
            SelectionContainer { Text(code, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
            Text("Ein neuer Code macht den vorherigen ungültig.")
        } },
        confirmButton = { Button(onClick = onDismiss) { Text("Ich habe ihn notiert") } },
        dismissButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(code)); copied = true }) { Text(if (copied) "✓ Kopiert" else "Code kopieren") } },
    )
}

@Composable
private fun ProofDialog(proof: ApiClient.ProofDetails, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) context.contentResolver.openOutputStream(uri)?.use { it.write(proof.rawExport.toByteArray()) }
    }
    val evidence = proof.evidence
    val verification = remember(evidence) { evidence?.let(EvidenceProtocol::verify) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kryptografischer Nachweis") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (proof.legacy || evidence == null) {
                Text("Legacy – ohne kryptografischen Nachweis", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            } else {
                Text("Protokollversion ${evidence.protocolVersion}")
                Text("Versiegelungs-Hash", fontWeight = FontWeight.Bold)
                SelectionContainer { Text(evidence.commitment, fontSize = 12.sp) }
                Text("Klartext-Hash (bei kurzen Nachrichten möglicherweise erratbar)", fontWeight = FontWeight.Bold)
                SelectionContainer { Text(evidence.plaintextSha256, fontSize = 12.sp) }
                Text("Fingerabdruck des öffentlichen Prüfschlüssels", fontWeight = FontWeight.Bold)
                SelectionContainer { Text(evidence.publicKeyFingerprint, fontSize = 12.sp) }
                Text("Öffentlicher Prüfschlüssel (kopierbar)", fontWeight = FontWeight.Bold)
                SelectionContainer { Text(evidence.publicSigningKey, fontSize = 11.sp) }
                Text("Signatur", fontWeight = FontWeight.Bold)
                SelectionContainer { Text(evidence.signature, fontSize = 11.sp) }
                Text("Freigaberegel/Zeitstempel: ${proof.releaseRule}")
                if (evidence.releaseKey != null) {
                    Text("Fingerabdruck des offengelegten AES-Freigabeschlüssels", fontWeight = FontWeight.Bold)
                    SelectionContainer { Text(evidence.releaseKeyFingerprint.orEmpty(), fontSize = 12.sp) }
                    Text(
                        if (verification?.valid == true && verification.status == "verified") "✓ Lokale Prüfung erfolgreich"
                        else "⚠ Lokale Prüfung fehlgeschlagen: ${verification?.status}",
                        color = if (verification?.valid == true) Color(0xFF19703B) else MaterialTheme.colorScheme.error,
                    )
                } else Text("Der AES-Freigabeschlüssel ist noch nicht offengelegt.")
            }
            OutlinedButton(onClick = { export.launch("brief-${proof.messageId}.gsverify.json") }) {
                Text("Öffentliche Prüfdatei exportieren")
            }
        } },
        confirmButton = { Button(onClick = onDismiss) { Text("Schließen") } },
    )
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: () -> Unit, dismiss: () -> Unit) = AlertDialog(
    onDismissRequest = dismiss, title = { Text(title) }, text = { Text(text) },
    confirmButton = { Button(onClick = confirm) { Text("Bestätigen") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Abbrechen") } },
)

@Composable
private fun ActionCard(text: String, actions: @Composable RowScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Row(horizontalArrangement = Arrangement.spacedBy(3.dp), content = actions)
    } }
}

@Composable
internal fun SectionTitle(text: String) = Text(text, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
@Composable
internal fun InfoCard(text: String) = Card(Modifier.fillMaxWidth()) { Text(text, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable
private fun ToggleSetting(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) =
    Row(verticalAlignment = Alignment.CenterVertically) { Switch(checked, onChecked); Spacer(Modifier.width(8.dp)); Text(label) }
internal fun formatDate(epoch: Long): String = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(epoch))
internal fun profileColor(value: String): Color = runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(Color(0xFF5B3FD6))
private fun safeFileName(name: String): String =
    name.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[\\p{Cntrl}]"), "").trim()
        .trimStart('.').take(120).ifBlank { "Anhang" }

@Composable
private fun FogOverlay(onFinished: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(1_200))
        delay(2_000)
        alpha.animateTo(0f, tween(1_800))
        onFinished()
    }
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { this.alpha = alpha.value }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            }
            .semantics {
                contentDescription = "Nebel über dem versiegelten Brief"
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0xE6C9D0CB))
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFF5F6F1), Color(0x00F5F6F1)),
                    center = center.copy(x = size.width * .25f, y = size.height * .35f),
                    radius = size.minDimension * .58f,
                ),
                radius = size.minDimension * .58f,
                center = center.copy(x = size.width * .25f, y = size.height * .35f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFDDE4DF), Color(0x00DDE4DF)),
                    center = center.copy(x = size.width * .80f, y = size.height * .67f),
                    radius = size.minDimension * .72f,
                ),
                radius = size.minDimension * .72f,
                center = center.copy(x = size.width * .80f, y = size.height * .67f),
            )
        }
        Surface(
            color = Color(0xD92B312E),
            contentColor = Color.White,
            shape = RoundedCornerShape(22.dp),
            shadowElevation = 12.dp,
            modifier = Modifier.padding(26.dp),
        ) {
            Text(
                "Was willst du eigentlich, du Knecht? Lass mich doch einfach verschlossen. Du kannst jetzt eh nichts machen.",
                Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
private fun modeIcon(mode: ComposeMode): String = when (mode) {
    ComposeMode.DURATION -> "◷"; ComposeMode.DATE_TIME -> "▣"; ComposeMode.MANUAL -> "☝"
    ComposeMode.MUTUAL -> "✓✓"; ComposeMode.PRESENCE -> "●"; ComposeMode.RANDOM -> "?"
}
private fun modeDescription(mode: ComposeMode): String = when (mode) {
    ComposeMode.DURATION -> "Öffnet nach einer Anzahl Stunden oder Tage."
    ComposeMode.DATE_TIME -> "Öffnet an einem bestimmten Datum und zu einer Uhrzeit."
    ComposeMode.MANUAL -> "Bleibt gesperrt, bis du sie selbst freigibst."
    ComposeMode.MUTUAL -> "Öffnet erst, nachdem beide zugestimmt haben."
    ComposeMode.PRESENCE -> "Öffnet bei serverbestätigter gleichzeitiger Aktivität; das ist keine ausdrückliche Zustimmung."
    ComposeMode.RANDOM -> "Der Server wählt den Freigabezeitpunkt innerhalb deines Zeitfensters."
}

/** The EP standing of one friendship, for the separate info surface. */
private fun epLineFor(ep: ApiClient.EpOverview?, ownUserId: Long?, friend: ApiClient.UserSummary): String {
    val history = ep?.history.orEmpty()
    val received = history.filter { it.beneficiaryId == ownUserId && it.proposerId == friend.id }.sumOf { it.points }
    val given = history.filter { it.proposerId == ownUserId && it.beneficiaryId == friend.id }.sumOf { it.points }
    return "Von ${friend.name} angenommen: ${formatEpPoints(received)} · an ${friend.name} gegeben: ${formatEpPoints(given)}"
}
