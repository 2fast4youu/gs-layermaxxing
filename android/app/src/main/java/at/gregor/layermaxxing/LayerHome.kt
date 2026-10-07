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

private enum class ComposeMode(val label: String, val api: String) {
    DURATION("Nach einer Dauer", "timed"), DATE_TIME("Zu einem Zeitpunkt", "timed"), MANUAL("Von mir freigeben", "manual"),
    MUTUAL("Wenn beide zustimmen", "mutual"), PRESENCE("Wenn beide online sind", "presence"), RANDOM("Zufällig", "random")
}
private enum class DelayUnit(val label: String, val seconds: Long) {
    MINUTES("Minuten", 60), HOURS("Stunden", 3600), DAYS("Tage", 86400)
}
internal data class OpenedMessage(val text: String, val attachment: ByteArray?, val name: String?, val mime: String?)
data class EpPrompt(val friendId: Long, val letterId: Long?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayerHome(
    token: String, api: ApiClient, store: SessionStore, initialRecoveryCode: String?,
    onRecoveryCodeSeen: () -> Unit, onTheme: (String) -> Unit, onLogout: () -> Unit,
    accounts: List<SavedAccount>, onAddAccount: () -> Unit, onSwitchAccount: (SavedAccount) -> Unit,
    serverProfile: ServerProfile, onServerProfile: (ServerProfile) -> Unit,
) {
    val villageStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var appMode by remember(token) { mutableStateOf(store.appMode) }
    var tab by remember(token) { mutableStateOf(if (store.startsInVillage) MainTab.CASTLES else store.appMode.startTab) }
    var openThreadFriend by remember { mutableStateOf<Long?>(null) }
    // "Leute" is no longer a tab. Finding, adding and managing people is a place
    // you enter from the chat overview and leave again — the overview itself is
    // the people list, so this screen is for the rare cases only.
    var peopleOpen by remember { mutableStateOf(false) }
    var hub by remember { mutableStateOf<String?>(null) }
    var topicScope by remember { mutableStateOf<TopicScope?>(null) }
    var topicName by remember { mutableStateOf("") }
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
    var quests by remember { mutableStateOf<List<ApiClient.Quest>>(emptyList()) }
    var islands by remember { mutableStateOf<ApiClient.Islands?>(null) }
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
    // Background refresh failures never shout: they only flip this quiet chip.
    var offline by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var recoveryCode by remember { mutableStateOf(initialRecoveryCode) }
    var castleExperiment by remember { mutableStateOf(store.castleExperiment) }
    var castleBuilds by remember { mutableStateOf(store.fiefBuilds()) }
    var villageUnlocked by remember(token) { mutableStateOf(store.villageUnlocked() ?: VillageGrowth.START) }
    var villageFresh by remember(token) { mutableStateOf(store.villageFresh()) }
    // The device remembers only the switch; role and entitlement arrive fresh
    // with every status refresh, so production or a revoked entitlement wins.
    var creativeSwitch by remember { mutableStateOf(store.creativeMode) }
    // Exactly one letter composer exists in the whole app. The thread, the letter
    // section and the castle post office all open this one, never a second copy.
    var composeLetterFriend by remember { mutableStateOf<Long?>(null) }
    // A chat line turned into a letter arrives here as the letter's first draft.
    var composeLetterText by remember { mutableStateOf("") }
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
                    // Older servers lack the island endpoints: the sea then simply starts calm.
                    async { quests = runCatching { api.quests(token) }.getOrDefault(quests) },
                    async { islands = runCatching { api.islands(token) }.getOrDefault(islands) },
                )
                jobs.awaitAll()
            }
            offline = false
        }.onFailure {
            if (ApiErrors.isOffline(it.message)) offline = true
            else if (status == null) error = ApiErrors.friendly(it.message)
        }
        busy = false
    }
    fun act(block: suspend () -> Unit) {
        scope.launch { runCatching { block(); refresh() }.onFailure { error = ApiErrors.friendly(it.message) } }
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
        hub = null; topicScope = null
        sparkInbox = emptyList(); sparkSent = emptyList()
        castleExperiment = store.castleExperiment
        appMode = store.appMode; tab = if (store.startsInVillage) MainTab.CASTLES else appMode.startTab
        creativeSwitch = store.creativeMode
        fogVisible = false; lockedTapTracker.reset(); lockedLetterSounds.stopAll()
        busy = true; error = null; offline = false
        // Offline? retry sooner, so the app comes back by itself.
        while (true) { refresh(); delay(if (offline) 8_000 else 30_000) }
    }
    if (recoveryCode != null) RecoveryDialog(recoveryCode!!) { recoveryCode = null; onRecoveryCodeSeen() }
    proof?.let { ProofDialog(it, onDismiss = { proof = null }) }

    val conversations = Conversations.overview(friends, friendshipSettings, chatThreads, messages, outbox, status?.userId)
    val requestInbox = RequestInbox.collect(incoming, friendshipSettings, ep)
    val currentDialog = RequestInbox.dialog(requestInbox, dismissedRequests.keys, snoozed = false)
    val tabs = appMode.tabs()
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
    BackHandler(enabled = tab != appMode.startTab) { tab = appMode.startTab; openThreadFriend = null }
    BackHandler(enabled = openThreadFriend != null) { openThreadFriend = null }
    BackHandler(enabled = peopleOpen) { peopleOpen = false }
    BackHandler(enabled = sparksOpen) { sparksOpen = false }
    BackHandler(enabled = hub != null) { hub = null }
    BackHandler(enabled = topicScope != null) { topicScope = null }

    // An open thread, the people place and the valley are full-screen places: the
    // global bar and the bottom navigation step aside instead of stacking a second
    // head on top.
    val villageActivities = VillageActivityModel.collect(
        conversations.sumOf { it.unreadChats }, topics.count { it.completedAt == null }, groups.size,
        Sparks.unopenedCount(sparkInbox), Conversations.postStatus(messages + outbox, opened.keys).urgent,
        requestInbox.count { it.kind == RequestKind.EP },
        requestInbox.count { it.kind == RequestKind.FRIEND }, requestInbox.count { it.kind == RequestKind.SETTINGS },
    )
    val growthFacts = GrowthFacts(
        friends = friends.size,
        chatsWithActivity = chatThreads.count { it.lastMessageAt > 0 },
        letters = (messages + outbox).size,
        topics = topics.size,
        groups = groups.size,
        sparks = sparkInbox.size + sparkSent.size,
        epEnabledFriendships = friendshipSettings.count { it.epEnabled },
        epReceived = ep?.received ?: 0,
    )
    // Measure growth only once real data has arrived, so an empty first frame
    // never "locks" a grown village and never fakes a celebration.
    LaunchedEffect(token, busy, growthFacts, villageActivities) {
        if (busy || status == null) return@LaunchedEffect
        val remembered = store.villageUnlocked()
        val now = VillageGrowth.unlocked(growthFacts, remembered.orEmpty(), villageActivities.associate { it.destination to it.count })
        if (remembered == null) {
            store.setVillageUnlocked(now); villageUnlocked = now
        } else if (now != remembered) {
            val added = now - remembered
            store.setVillageUnlocked(now); villageUnlocked = now
            if (added.isNotEmpty()) { villageFresh = villageFresh + added; store.setVillageFresh(villageFresh) }
        }
    }
    // Personal friend names live on the device, shared by the island and the neutral friend page.
    val labelAccount = "${store.serverProfile.key}_${store.name}"
    var friendLabels by remember(labelAccount) { mutableStateOf(FriendLabels.load(context, labelAccount)) }
    fun saveFriendLabel(id: Long, label: String?) {
        friendLabels = friendLabels.toMutableMap().apply { if (label == null) remove(id) else put(id, label) }
        FriendLabels.save(context, labelAccount, friendLabels)
    }
    val fullScreenPlace = tab == MainTab.CASTLES || openThreadFriend != null || peopleOpen || sparksOpen || hub != null || topicScope != null
    // The valley is a painted world, so it runs under the system bars instead of
    // sitting in a window of app background: a letterboxed plate inside inset
    // padding is exactly what produced the pale strips above and below the map.
    // Its own chrome reserves the insets it needs.
    val edgeToEdgePlace = tab == MainTab.CASTLES && hub == null && topicScope == null && openThreadFriend == null && !peopleOpen && !sparksOpen
    val worldStyle = tab == MainTab.CASTLES
    val worldRoom = worldStyle && !edgeToEdgePlace
    VillageTheme(worldStyle) {
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            // One chrome row, not a landscape of bars. The tab name is already on
            // the bottom navigation and the build number belongs in "Mehr", so the
            // only things left here are: who am I, and refresh.
            if (!fullScreenPlace && !worldStyle) AppChrome(
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
            if (!fullScreenPlace && !worldStyle) NavigationBar {
                tabs.forEach { item -> NavigationBarItem(
                    selected = tab == item, onClick = { tab = item; openThreadFriend = null }, icon = {
                        val count = when (item) {
                            MainTab.CHATS -> requestInbox.size + conversations.count { it.hasNews } +
                                Sparks.unopenedCount(sparkInbox)
                            else -> 0
                        }
                        androidx.compose.material3.BadgedBox(badge = { if (count > 0) androidx.compose.material3.Badge { Text(if (count > 9) "9+" else "$count") } }) {
                            AppIcon(AppIcons.tab(item), null, size = 24.dp)
                        }
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
            if (tab == MainTab.CASTLES) { villageStateHolder.SaveableStateProvider("isles_${store.serverProfile.key}_${store.name}") {
                IslandWorld(
                    labels = friendLabels,
                    onLabel = { id, label -> saveFriendLabel(id, label) },
                    ownName = status?.name ?: store.name, ownEmoji = status?.avatarEmoji ?: "🙂",
                    friends = friends, groups = groups, islands = islands, quests = quests,
                    letters = messages + outbox, topics = topics, opened = opened,
                    onBack = { tab = MainTab.CHATS },
                    onChat = { id -> openThreadFriend = id },
                    onComposeLetter = { composeLetterFriend = it },
                    onOpenLetter = ::openLetter, onLockedTap = ::lockedLetterTap,
                    onTopics = { id -> topicScope = TopicScope.friend(id); topicName = friends.firstOrNull { it.id == id }?.name ?: "Themen" },
                    onAllTopics = { hub = "topics" },
                    onGlossary = { hub = "glossary" },
                    onPeople = { peopleOpen = true },
                    onPoints = { hub = "ep" },
                    onCreateQuest = { title, details, icon, points, peer, group ->
                        act { api.createQuest(token, title, details, icon, points, peer, group) }
                    },
                    onQuestDone = { quest, done -> act { api.setQuestCompleted(token, quest.id, done) } },
                    onQuestDelete = { quest -> act { api.deleteQuest(token, quest.id) } },
                    ownId = status?.userId,
                    incoming = incoming, outgoing = outgoing,
                    onRespondFriend = { id, accept -> act { api.respondFriend(token, id, accept) } },
                    ownColor = status?.displayColor ?: "#17A2A6",
                    glossary = { GlossaryScreen(api, token) },
                    loadIsland = { id -> api.island(token, id) },
                    saveDecor = { decor -> api.setDecor(token, decor) },
                    savePlaces = { places -> api.setPlaces(token, places) },
                    saveHere = { plot, status -> api.setHere(token, plot, status) },
                    chatsBadge = requestInbox.size + conversations.count { it.hasNews } + Sparks.unopenedCount(sparkInbox),
                    onError = { error = it },
                    onPlace = { place ->
                        when (place) {
                            IsleBuilding.HOUSE -> hub = "settings"
                            IsleBuilding.LIGHTHOUSE -> peopleOpen = true
                            IsleBuilding.POST -> hub = "archive"
                            IsleBuilding.LIBRARY -> hub = "glossary"
                            IsleBuilding.CAMPFIRE -> hub = "groups"
                            IsleBuilding.HARBOUR -> hub = "topics"
                        }
                    },
                )
            } }
            val roomPlace = when {
                hub == "conversations" || openThreadFriend != null -> ValleyDestination.CONVERSATIONS
                hub == "archive" -> ValleyDestination.ARCHIVE
                hub == "ep" -> ValleyDestination.EP
                hub == "settings" || hub == "accounts" -> ValleyDestination.SETTINGS
                topicScope != null || hub == "topics" -> ValleyDestination.TOPICS
                hub == "glossary" -> ValleyDestination.GLOSSARY
                hub == "groups" -> ValleyDestination.GROUPS
                sparksOpen -> ValleyDestination.SPARKS
                peopleOpen -> ValleyDestination.PEOPLE
                else -> ValleyDestination.SETTINGS
            }
            val roomTitle = when {
                hub == "settings" -> "Mein Haus"
                hub == "archive" -> "Post"
                hub == "groups" -> "Lagerfeuer · Gruppen"
                peopleOpen -> "Leuchtturm · Freunde"
                hub == "topics" -> "Hafen · Themen"
                openThreadFriend != null -> friends.firstOrNull { it.id == openThreadFriend }?.name ?: roomPlace.title
                topicScope != null -> topicName
                hub == "accounts" -> "Konten"
                else -> roomPlace.title
            }
            VillageRoom(worldRoom, mapBehind = tab == MainTab.CASTLES, icon = roomPlace.iconRes(), title = roomTitle, onClose = { hub = null; topicScope = null; openThreadFriend = null; peopleOpen = false; sparksOpen = false; tab = MainTab.CASTLES }) {
            Box(Modifier.fillMaxSize()) {
            val threadFriend = openThreadFriend?.let { id -> friends.firstOrNull { it.id == id } }
            when {
                // The meeting point IS the messenger: same list, same search, same chips.
                hub == "conversations" -> SubScreen("Treffpunkt", onBack = { hub = null }, backLabel = "Inseln") {
                    ChatsScreen(
                        conversations = conversations, requests = requestInbox,
                        sparkInbox = sparkInbox, sparkSent = sparkSent,
                        onOpen = { id -> hub = null; openThreadFriend = id }, onRespond = ::respondRequest,
                        onGoPeople = { hub = null; peopleOpen = true },
                        onOpenSparks = { hub = null; sparksOpen = true },
                        onSendSpark = { sparkCompose = true },
                        sparkEnabled = friends.isNotEmpty(),
                        onGroups = { hub = "groups" }, onTopicsHub = { hub = "topics" },
                        onGlossary = { hub = "glossary" }, onValley = { hub = null },
                        groupCount = groups.size, topicCount = topics.count { it.completedAt == null }, showValley = false,
                    refreshing = refreshing, onRefresh = { scope.launch { refreshing = true; refresh(); refreshing = false } },
                    )
                }
                hub == "archive" -> SubScreen("Briefe", onBack = { hub = null }, backLabel = "Inseln") {
                    InboxScreen(messages, outbox, opened, token, api, ::act, ::openLetter, ::lockedLetterTap, { message -> scope.launch { runCatching { proof = api.proof(token, message.id) }.onFailure { error = it.message } } })
                }
                hub == "ep" -> SubScreen("Punkte", onBack = { hub = null }, backLabel = "Inseln") {
                    EpScreen(token, api, friendshipSettings, ep, draft = null, onDraftConsumed = {}, act = ::act)
                }
                hub == "accounts" -> SubScreen("Konten", onBack = { hub = "settings" }, backLabel = "Gemeindehaus") {
                    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                        items(accounts, key = { it.token }) { account -> TextButton(onClick = { onSwitchAccount(account) }) { Text(account.name) } }
                        item { Button(onClick = onAddAccount) { Text("Konto hinzufügen") } }
                    }
                }
                topicScope != null -> SubScreen(topicName, onBack = { topicScope = null }, backLabel = "Zurück") {
                    TopicsScreen(topics, ApiClient.UserSummary(topicScope!!.id ?: 0, topicName), token, api, ::act, scope = topicScope!!)
                }
                hub == "glossary" -> SubScreen("Wörterbuch", onBack = { hub = null }, backLabel = "Zurück") { GlossaryScreen(api, token) }
                hub == "topics" -> SubScreen("Themen", onBack = { hub = null }, backLabel = "Zurück") {
                    TopicsHub(topics, friends, groups) { s, name -> topicScope = s; topicName = name }
                }
                hub == "groups" -> SubScreen("Gruppen", onBack = { hub = null }, backLabel = "Zurück") {
                    GroupsHub(groups, friends, topics, friendshipSettings, token, api, ::act, { s, name -> topicScope = s; topicName = name }, quests = quests)
                }
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
                    onLetterFromChat = { id, body -> composeLetterText = body; composeLetterFriend = id },
                    onOpenLetter = ::openLetter,
                    onLockedTap = ::lockedLetterTap,
                    onProof = { message -> scope.launch { runCatching { proof = api.proof(token, message.id) }.onFailure { error = it.message } } },
                    onProposeEp = { epProposal = it },
                    topics = topics,
                    epLine = epLineFor(ep, status?.userId, threadFriend),
                    creativeActive = creativeActive,
                    quests = quests, label = friendLabels[threadFriend.id],
                    onLabel = { label -> saveFriendLabel(threadFriend.id, label) },
                    onQuestDone = { quest, done -> act { api.setQuestCompleted(token, quest.id, done) } },
                )
                sparksOpen -> SparkRoom(
                    inbox = sparkInbox, sent = sparkSent, token = token, api = api, act = ::act,
                    onBack = { sparksOpen = false }, onCompose = { sparkCompose = true },
                )
                peopleOpen -> SubScreen("Leute", onBack = { peopleOpen = false }, backLabel = "Chats") {
                    FriendsScreen(token, api, users, friends, incoming, outgoing, groups,
                        friendshipSettings, onOpenThread = { peopleOpen = false; openThreadFriend = it }, act = ::act,
                        onGroups = { peopleOpen = false; hub = "groups" })
                }
                tab == MainTab.CHATS -> ChatsScreen(
                    conversations = conversations, requests = requestInbox,
                    sparkInbox = sparkInbox, sparkSent = sparkSent,
                    onOpen = { openThreadFriend = it }, onRespond = ::respondRequest,
                    onGoPeople = { peopleOpen = true },
                    onOpenSparks = { sparksOpen = true },
                    onSendSpark = { sparkCompose = true },
                    sparkEnabled = friends.isNotEmpty(),
                    onGroups = { hub = "groups" }, onTopicsHub = { hub = "topics" },
                    onGlossary = { hub = "glossary" }, onValley = { store.setCastleExperiment(true); castleExperiment = true; tab = MainTab.CASTLES },
                    groupCount = groups.size, topicCount = topics.count { it.completedAt == null }, showValley = appMode.showsValley,
                    refreshing = refreshing, onRefresh = { scope.launch { refreshing = true; refresh(); refreshing = false } },
                )
                tab == MainTab.MORE || hub == "settings" -> MoreScreen(
                    token, api, store, status, sessions, blocked, messages, outbox, ep,
                    friendshipSettings, opened, onTheme, onLogout, serverProfile, onServerProfile,
                    onExit = if (hub == "settings") ({ hub = null }) else null,
                    onAccounts = { hub = "accounts" },
                    castleExperiment = castleExperiment,
                    onCastleExperiment = { store.setCastleExperiment(it); castleExperiment = it; appMode = store.appMode; tab = appMode.startTab; hub = null; topicScope = null },
                    creativeEligible = creativeEligible,
                    creativeSwitch = creativeSwitch,
                    onCreativeSwitch = { store.setCreativeMode(it); creativeSwitch = it },
                    onRecovery = { recoveryCode = it }, onProof = { message -> scope.launch { runCatching { proof = api.proof(token, message.id) }.onFailure { error = it.message } } },
                    onOpenLetter = ::openLetter, onLockedTap = ::lockedLetterTap, act = ::act,
                )
                tab == MainTab.CASTLES -> Unit
            }
            // First load: one calm spinner. Later refreshes never cover the screen.
            if (busy && status == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
            if (offline) OfflineChip(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 6.dp))
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
            error?.let { message ->
                LaunchedEffect(message) { delay(6_000); if (error == message) error = null }
                NoticeToast(ApiErrors.friendly(message), onDismiss = { error = null }, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 96.dp))
            }
            } }
        }
    }
    composeLetterFriend?.let { friendId ->
        ModalBottomSheet(
            onDismissRequest = { composeLetterFriend = null; composeLetterText = "" },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = null,
        ) {
            SendScreen(
                token, api, friends, groups, friendshipSettings, initialRecipient = friendId,
                creativeActive = creativeActive, initialText = composeLetterText,
                onClose = { composeLetterFriend = null; composeLetterText = "" },
                onSent = { _, _ -> composeLetterFriend = null; composeLetterText = ""; act {} },
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
                AppIcon(R.drawable.ico_expand, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 16.dp, modifier = Modifier.padding(start = 4.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenu(false) }) {
                accounts.forEach { account ->
                    DropdownMenuItem(
                        text = { Text(account.name) }, trailingIcon = { if (account.name == name) AppIcon(R.drawable.ico_check, null, size = 18.dp) },
                        onClick = { onMenu(false); if (account.name != name) onSwitchAccount(account) },
                    )
                }
                if (accounts.isNotEmpty()) HorizontalDivider()
                DropdownMenuItem(text = { Text("Konto hinzufügen") }, leadingIcon = { AppIcon(R.drawable.ico_add, null, size = 18.dp) }, onClick = { onMenu(false); onAddAccount() })
            }
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(50)).clickable(onClick = onRefresh)
                .semantics { contentDescription = "Aktualisieren"; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) { AppIcon(R.drawable.ico_refresh, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 23.dp) }
    }
}

// ---------------------------------------------------------------------------
// Chats: the conversation overview
// ---------------------------------------------------------------------------

@Composable
internal fun InboxScreen(
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
        exportError?.let { item { Text(ApiErrors.friendly(it), color = MaterialTheme.colorScheme.error, fontSize = 12.sp) } }
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
                    if (message.groupId != null) AppIcon(R.drawable.ico_group, null, modifier = Modifier.padding(start = 6.dp), size = 15.dp)
                    if (message.oneTime) Text("1×", Modifier.padding(start = 6.dp))
                }
                Text("Von ${message.peerName} · ${formatDate(message.createdAt)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (message.coverNote.isNotBlank()) Text("„${message.coverNote}“", fontStyle = FontStyle.Italic)
                if (message.proofStatus == "legacy") Text("Legacy – ohne kryptografischen Nachweis", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                when {
                    opened != null -> {
                        Text(opened.text, fontSize = 17.sp)
                        if (opened.attachment != null) OutlinedButton(onClick = { save.launch(safeFileName(opened.name ?: "Anhang")) }) { AppIcon(R.drawable.ico_attach, null, size = 18.dp, modifier = Modifier.padding(end = 6.dp)); Text("${opened.name ?: "Anhang"} speichern") }
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
                    else -> Row(verticalAlignment = Alignment.CenterVertically) { AppIcon(R.drawable.ico_lock, null, size = 18.dp, modifier = Modifier.padding(end = 6.dp)); Text("Noch ${formatRemaining(max(0, (message.releaseAt ?: now) - now))}") }
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
                if (message.groupId != null) AppIcon(R.drawable.ico_group, null, modifier = Modifier.padding(start = 6.dp), size = 15.dp)
                if (message.oneTime) Text("1×", Modifier.padding(start = 6.dp))
            }
            Text("An ${message.peerName} · ${formatDate(message.createdAt)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (message.coverNote.isNotBlank()) Text("„${message.coverNote}“", fontStyle = FontStyle.Italic)
            if (message.proofStatus == "legacy") Text("Legacy – ohne kryptografischen Nachweis", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            when {
                message.unlocked -> Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(if (message.readAt != null) R.drawable.ico_check_double else R.drawable.ico_check, null, tint = Color(0xFF19703B), size = 18.dp, modifier = Modifier.padding(end = 6.dp))
                    Text(if (message.readAt != null) "Gelesen" else "Freigegeben", color = Color(0xFF19703B))
                }
                message.mode == "manual" -> Button(onClick = { confirmRelease = true }) { Text("Jetzt freigeben") }
                message.mode == "mutual" && !message.senderApproved -> Button(onClick = onApprove) { Text("Meine Freigabe bestätigen") }
                message.mode == "mutual" -> Text("Wartet auf Zustimmung von ${message.peerName}")
                message.mode == "presence" -> Text("Wartet, bis ihr beide online seid")
                message.mode == "random" -> Text("Zufällige Freigabe bis ${message.randomTo?.let(::formatDate)}")
                else -> Row(verticalAlignment = Alignment.CenterVertically) { AppIcon(R.drawable.ico_lock, null, size = 18.dp, modifier = Modifier.padding(end = 6.dp)); Text("Automatisch in ${formatRemaining(max(0, (message.releaseAt ?: now) - now))}") }
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
    onClose: () -> Unit, onSent: (Long?, Long?) -> Unit, initialText: String = "",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }; var coverNote by remember { mutableStateOf("") }; var text by remember(initialText) { mutableStateOf(initialText) }
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
                        if (friend.id in selected) AppIcon(R.drawable.ico_check, null, tint = MaterialTheme.colorScheme.primary, size = 21.dp)
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
                            AppIcon(R.drawable.ico_group, null, modifier = Modifier.width(38.dp), size = 24.dp)
                            Text("${group.name} (${group.members.size})", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            if (groupId == group.id) AppIcon(R.drawable.ico_check, null, tint = MaterialTheme.colorScheme.primary, size = 21.dp)
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
                        AppIcon(modeIcon(choice), null, modifier = Modifier.padding(end = 18.dp), tint = MaterialTheme.colorScheme.primary, size = 24.dp)
                        Column(Modifier.weight(1f)) {
                            Text(choice.label, fontWeight = FontWeight.SemiBold)
                            Text(modeDescription(choice), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (mode == choice) AppIcon(R.drawable.ico_check, null, tint = MaterialTheme.colorScheme.primary, size = 22.dp)
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
        groupId != null -> "Gruppe: " + groups.firstOrNull { it.id == groupId }?.name.orEmpty()
        selected.isEmpty() -> "Empfänger wählen"
        else -> friends.filter { it.id in selected }.joinToString { "${it.avatarEmoji} ${it.name}" }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { AppIcon(R.drawable.ico_back, null, size = 22.dp) }
            Column(Modifier.weight(1f)) {
                Text("An", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(recipientLabel, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            if (friends.size > 1 || groups.isNotEmpty()) TextButton(onClick = { keyboard?.hide(); recipientMenu = true }) { AppIcon(R.drawable.ico_add, null, size = 20.dp) }
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
                ComposerChip(label) {
                    val chosen = DelayUnit.entries.lastOrNull { seconds >= it.seconds && seconds % it.seconds == 0L }
                        ?: DelayUnit.MINUTES
                    mode = ComposeMode.DURATION; unit = chosen; amount = (seconds / chosen.seconds).toString()
                }
            }
            ComposerChip(attachment?.first ?: "Anhang", icon = R.drawable.ico_attach) { keyboard?.hide(); picker.launch("*/*") }
            ComposerChip("Details", icon = if (detailsOpen) R.drawable.ico_collapse else R.drawable.ico_expand) { detailsOpen = !detailsOpen }
            if (oneTime) ComposerChip("1×")
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Der Server hält den Schlüssel bis zur Freigabe.",
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
        ) { Text(if (busy) "Wird versiegelt …" else "Versiegelt senden", fontWeight = FontWeight.Bold) }
    }
}

internal const val CRYPTO_HONESTY =
    "AES-256-GCM schützt Vertraulichkeit und Integrität in Speicherung und Übertragung. " +
        "Der Server hält den AES-Schlüssel bis zur Freigabe und kann technisch auf gesperrte Inhalte zugreifen. " +
        "Zeit, Zufall, manuell, gegenseitig und Präsenz unterscheiden nur das serverseitige Freigabe-Gate. " +
        "Präsenz ist serverbestätigte Aktivität, keine ausdrückliche Zustimmung. Einmal-Lesen ist App-/Server-Regel " +
        "und verhindert keine Screenshots oder Caches."

@Composable
private fun ComposerChip(text: String, highlighted: Boolean = false, icon: Int? = null, onClick: (() -> Unit)? = null) {
    Surface(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        shape = RoundedCornerShape(50),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) AppIcon(icon, null, size = 15.dp, modifier = Modifier.padding(end = 5.dp))
            Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** Short, honest summary of the release gate, including the agreed minimum delay. */
private fun sealChipLabel(
    mode: ComposeMode, amount: String, unit: DelayUnit, dateTime: LocalDateTime, minimumDelay: Long,
): String {
    val base = when (mode) {
        ComposeMode.DURATION -> "${amount.ifBlank { "0" }} ${unit.label}"
        ComposeMode.DATE_TIME -> dateTime.format(DateTimeFormatter.ofPattern("dd.MM. HH:mm"))
        ComposeMode.MANUAL -> "Von mir freigeben"
        ComposeMode.MUTUAL -> "Beide stimmen zu"
        ComposeMode.PRESENCE -> "Beide online"
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
    act: ((suspend () -> Unit) -> Unit), onGroups: () -> Unit = {},
) {
    val scope = rememberCoroutineScope(); var search by remember { mutableStateOf("") }; var result by remember { mutableStateOf<List<ApiClient.UserSummary>>(emptyList()) }
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
        if (friends.isEmpty()) item { EmptyHint(R.drawable.ico_people, "Noch keine Freunde", "Suche oben nach einem Benutzernamen und schick eine Anfrage – sobald sie annimmt, taucht ihre Insel auf.") }
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
        // Groups are created in exactly one place – the groups hub; here they are only listed.
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Gruppen")
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onGroups) { Text(if (groups.isEmpty()) "+ Gruppe" else "Alle Gruppen") }
        } }
        if (groups.isEmpty()) item { EmptyHint(R.drawable.ico_groups, "Noch keine Gruppe", "Mit „+ Gruppe“ holst du mehrere Freunde an einen Tisch.") }
        else items(groups, key = { "group-${it.id}" }) { group -> InfoCard("${group.name}: ${group.members.joinToString { it.name }}") }
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
                ) { AppIcon(R.drawable.ico_menu, null, size = 24.dp) }
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
                ToggleSetting("Punkte", draft.epEnabled) { draft = draft.copy(epEnabled = it) }
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
internal fun SubScreen(
    title: String,
    onBack: () -> Unit,
    backLabel: String = "Mehr",
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (LocalInVillageRoom.current) {
            // Inside a building the room header already names the place; only a
            // wooden step back remains, and none at all when it would just leave.
            if (backLabel != "Inseln") Row(Modifier.fillMaxWidth().padding(start = 10.dp, top = 8.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                NamePlank("‹ $backLabel", Modifier.heightIn(min = 32.dp).clickable(onClick = onBack), size = 13.sp)
                Spacer(Modifier.width(10.dp))
                Text(title, Modifier.weight(1f), fontWeight = FontWeight.Black, color = Kit.Ink, maxLines = 1)
            }
        } else Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { AppIcon(R.drawable.ico_back, null, size = 18.dp, modifier = Modifier.padding(end = 6.dp)); Text(backLabel) }
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold)
        }
        Box(Modifier.weight(1f)) { content() }
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
            AppIcon(R.drawable.ico_chevron, null, size = 24.dp)
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
internal fun RecoveryDialog(code: String, onDismiss: () -> Unit) {
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
internal fun ProofDialog(proof: ApiClient.ProofDetails, onDismiss: () -> Unit) {
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
internal fun ConfirmDialog(title: String, text: String, confirm: () -> Unit, dismiss: () -> Unit) = AlertDialog(
    onDismissRequest = dismiss, title = { Text(title) }, text = { Text(text) },
    confirmButton = { Button(onClick = confirm) { Text("Bestätigen") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Abbrechen") } },
)

@Composable
internal fun ActionCard(text: String, actions: @Composable RowScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Row(horizontalArrangement = Arrangement.spacedBy(3.dp), content = actions)
    } }
}

@Composable
internal fun SectionTitle(text: String) = Text(text, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
@Composable
internal fun EmptyHint(icon: Int, title: String, hint: String) =
    Column(Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AppIcon(icon, null, tint = MaterialTheme.colorScheme.primary, size = 36.dp)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, fontSize = 14.sp)
    }
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
private fun modeIcon(mode: ComposeMode): Int = when (mode) {
    ComposeMode.DURATION -> R.drawable.ico_timer; ComposeMode.DATE_TIME -> R.drawable.ico_date; ComposeMode.MANUAL -> R.drawable.ico_manual
    ComposeMode.MUTUAL -> R.drawable.ico_check_double; ComposeMode.PRESENCE -> R.drawable.ico_presence; ComposeMode.RANDOM -> R.drawable.ico_random
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


/** Quiet "no connection" chip: the app keeps working with what it has. */
@Composable
private fun OfflineChip(modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color(0xE61C2A3E)).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF2C14E)))
        Spacer(Modifier.width(8.dp))
        Text("Offline – verbinde neu …", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** One toast for every failed action: readable sentence, auto-hides, tap to close. */
@Composable
private fun NoticeToast(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.inverseSurface).clickable(onClick = onDismiss)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(R.drawable.ico_warning, null, tint = Color(0xFFF2C14E), size = 18.dp)
        Spacer(Modifier.width(10.dp))
        Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.inverseOnSurface, fontSize = 14.sp, maxLines = 3)
    }
}
