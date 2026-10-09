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

/**
 * Inselwelt: the player's island in the middle, every friend an island of its
 * own that grows with the real friendship (mostly through completed quests,
 * rarely through EP). Letters travel as boats on the sea routes between them.
 * Everything here is purely visual – no island level unlocks rights or features.
 */
internal object Isle {
    // Muted "Nordic coast": slate-teal sea, spruce accents.
    val SeaTop = Color(0xFF7FA2A3)
    val SeaBottom = Color(0xFF5C8487)
    val Teal = Color(0xFF3F7C78)
    val TealDark = Color(0xFF2F5D5A)
    val Ink = Color(0xFF1F3B4D)
    val Muted = Color(0xFF6B8592)
    val Card = Color(0xFFFBF3E2) // harbour paper, never plain white
    val Sand = Color(0xFFFFF6E5)
    val Star = Color(0xFFC9A55C)

    val levelNames = listOf("Neu", "Freunde", "Vertraut", "Beste Freunde")
    fun ownerWidth(island: ApiClient.HomeIsland?): Dp = (104 + (island?.construction?.land ?: 0).coerceIn(0, 5) * 10).dp
    fun levelName(level: Int) = levelNames[(level - 1).coerceIn(0, 3)]

    /** Relative size on the map: closer, bigger friendships read at a glance. */
    fun islandWidth(level: Int): Dp = when (level.coerceIn(1, 4)) { 1 -> 104.dp; 2 -> 118.dp; 3 -> 134.dp; else -> 156.dp }

}

/** One boat type per release mechanism, so a letter's rule is readable on the sea. */
internal enum class LetterBoat(val res: Int, val badge: Int, val label: String) {
    STEAMER(R.drawable.boat_steam, R.drawable.ico_timer, "Dampfer · kommt zu einer Zeit"),
    SAILBOAT(R.drawable.boat_sail, R.drawable.ico_manual, "Segelboot · wartet auf Zustimmung"),
    ROWBOAT(R.drawable.boat_row, R.drawable.ico_random, "Ruderboot · kommt zufällig"),
    ;

    companion object {
        fun forMode(mode: String): LetterBoat = when (mode) {
            "random" -> ROWBOAT
            "manual", "mutual", "presence" -> SAILBOAT
            else -> STEAMER
        }
    }
}

/** Pure placement maths, kept testable: friends sit on rings around the home island. */
internal object IsleLayout {
    /** The server marks accepted friends as "friends"; everyone in that list gets an island. */
    fun friendsOnMap(friends: List<ApiClient.UserSummary>) = friends.filter { it.relationship == "friends" || it.relationship == "friend" }

    /** Returns map positions in units of the map width (0..1) / height (0..1). */
    fun positions(count: Int): List<Pair<Float, Float>> {
        if (count == 0) return emptyList()
        val inner = minOf(count, 6)
        return List(count) { i ->
            val ring = if (i < inner) 0 else 1
            val n = if (ring == 0) inner else count - inner
            val k = if (ring == 0) i else i - inner
            val angle = -PI / 2 + 2 * PI * k / n + if (ring == 1) PI / n else 0.0
            val rx = if (ring == 0) .30 else .43
            val ry = if (ring == 0) .36 else .41
            (.5 + rx * cos(angle)).toFloat() to (.5 + ry * sin(angle)).toFloat()
        }
    }

    fun progress(info: ApiClient.IslandInfo?): Float {
        info ?: return 0f
        val next = info.nextAt ?: return 1f
        val prev = ISLAND_THRESHOLDS.getOrElse(info.level - 1) { 0 }
        return ((info.score - prev).toFloat() / (next - prev).coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    val ISLAND_THRESHOLDS = listOf(0, 40, 120, 300)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IslandWorld(
    ownName: String,
    ownEmoji: String,
    friends: List<ApiClient.UserSummary>,
    groups: List<ApiClient.Group>,
    islands: ApiClient.Islands?,
    quests: List<ApiClient.Quest>,
    letters: List<ApiClient.Message>,
    topics: List<ApiClient.Topic>,
    opened: Map<Long, OpenedMessage>,
    onBack: () -> Unit,
    onChat: (Long) -> Unit,
    onComposeLetter: (Long) -> Unit,
    onOpenLetter: (ApiClient.Message) -> Unit,
    onLockedTap: (ApiClient.Message) -> Unit,
    onTopics: (Long) -> Unit,
    onAllTopics: () -> Unit,
    onGlossary: () -> Unit,
    onPeople: () -> Unit,
    onCreateQuest: (title: String, details: String, icon: String, points: Int, peerId: Long?, groupId: Long?) -> Unit,
    onQuestDone: (ApiClient.Quest, Boolean) -> Unit,
    onQuestDelete: (ApiClient.Quest) -> Unit,
    ownId: Long? = null,
    loadIsland: suspend (Long) -> ApiClient.HomeIsland? = { null },
    saveDecor: suspend (Map<Int, String>) -> Unit = {},
    onPlace: (IsleBuilding) -> Unit = {},
    onError: (String) -> Unit = {},
    startView: String = "map",
    incoming: List<ApiClient.IncomingRequest> = emptyList(),
    outgoing: List<ApiClient.OutgoingRequest> = emptyList(),
    onRespondFriend: (Long, Boolean) -> Unit = { _, _ -> },
    ownColor: String = "#17A2A6",
    startPlace: String? = null,
    glossary: @Composable () -> Unit = {},
    labels: Map<Long, String> = emptyMap(),
    onLabel: (Long, String?) -> Unit = { _, _ -> },
    onPoints: () -> Unit = {},
    savePlaces: suspend (Map<Int, ApiClient.LifePlace>) -> Unit = {},
    saveHere: suspend (Int?, String) -> Unit = { _, _ -> },
    /** Unread count of the messenger, shown on the bar's way out to the chats. */
    chatsBadge: Int = 0,
    creativeActive: Boolean = false,
    changeConstruction: suspend (ApiClient.IslandConstruction, String, String?) -> ApiClient.HomeIsland? = { _, _, _ -> null },
) {
    val pals = remember(friends) { IsleLayout.friendsOnMap(friends) }
    val infoById = remember(islands) { islands?.friends?.associateBy { it.friendId }.orEmpty() }
    var selectedFriend by rememberSaveable { mutableStateOf<Long?>(null) }
    var newQuestFor by remember { mutableStateOf<Pair<Long?, Long?>?>(null) }
    val openQuests = quests.count { it.completedAt == null }
    // "map" = archipelago, "home" = my island up close, "visit:<id>" = a friend's island.
    var view by rememberSaveable { mutableStateOf(startView) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var decorEditing by remember { mutableStateOf(false) }
    // A building's own place, drawn natively on top of the island (null = none open).
    var place by rememberSaveable { mutableStateOf(startPlace) }
    var home by remember { mutableStateOf<ApiClient.HomeIsland?>(null) }
    var visited by remember { mutableStateOf<ApiClient.HomeIsland?>(null) }
    var slotPick by remember { mutableStateOf<Int?>(null) }
    var plotPick by remember { mutableStateOf<Int?>(null) }
    var upgradePick by remember { mutableStateOf<IsleBuilding?>(null) }
    var landPick by remember { mutableStateOf(false) }
    var constructionBusy by remember { mutableStateOf(false) }
    var buildPick by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val currentIslandIdentity by androidx.compose.runtime.rememberUpdatedState(ownId to if (creativeActive) "creative" else "normal")
    fun currentResponse(response: ApiClient.HomeIsland) = response.userId == currentIslandIdentity.first && response.construction.mode == currentIslandIdentity.second
    LaunchedEffect(ownId, islands?.score, creativeActive) {
        val mode = if (creativeActive) "creative" else "normal"
        if (home?.userId != ownId || home?.construction?.mode != mode) {
            home = null
            slotPick = null; plotPick = null; buildPick = null; upgradePick = null; landPick = false; decorEditing = false
        }
        ownId?.let { id ->
            while (true) {
                if (!constructionBusy) runCatching { loadIsland(id) }
                    .onSuccess { loaded ->
                        if (!constructionBusy && loaded != null && currentResponse(loaded) && (home == null || loaded.construction.revision >= home!!.construction.revision)) home = loaded
                    }.onFailure { onError(ApiErrors.friendly(it.message)) }
                kotlinx.coroutines.delay(60_000)
            }
        }
    }
    val visitId = view.removePrefix("visit:").toLongOrNull()
    LaunchedEffect(visitId) { visited = null; visitId?.let { id -> visited = runCatching { loadIsland(id) }.getOrNull() } }
    BackHandler(enabled = view != "map" || editing || place != null) {
        when { place != null -> place = null; editing -> { editing = false; decorEditing = false }; else -> view = "map" }
    }
    val homeBadges = mapOf(
        IsleBuilding.HARBOUR to openQuests,
        IsleBuilding.POST to letters.count { it.incoming && it.unlocked && it.readAt == null && opened[it.id] == null },
        IsleBuilding.LIGHTHOUSE to incoming.size,
    )

    IsleTypography {
    val sea = rememberSeaBrush()
    Box(Modifier.fillMaxSize().background(sea)) {
        AnimatedIslandSea(Modifier.fillMaxSize())
        when {
            view == "home" -> CloseIsland(
                title = if (editing) "Insel bearbeiten" else "Meine Insel",
                subtitle = when {
                        editing -> "Tippe auf einen Ort für Ausbau & Vorschau"
                        else -> if (creativeActive) "Kreativinsel · frei ausbauen und zurückbauen" else home?.construction?.let { "Landstufe ${it.land} · ${it.available} Punkte zum Bauen" } ?: "Insel wird geladen…"
                    },
            ) {
                HubIsland(
                    decor = home?.decor.orEmpty(), modifier = Modifier.fillMaxWidth(), seed = ownId ?: 1L,
                    badges = homeBadges,
                    onBuilding = { b -> upgradePick = b },
                    onSlot = if (decorEditing) ({ slotPick = it }) else null,
                    life = home, showFigure = true, editingPlots = editing,
                    onPlot = { i -> if (home?.places?.containsKey(i) == true) plotPick = i else buildPick = i },
                    onFigure = { upgradePick = IsleBuilding.HOUSE },
                )
            }
            visitId != null -> {
                val friend = pals.firstOrNull { it.id == visitId }
                CloseIsland(title = "Insel von ${friend?.name ?: "…"}", subtitle = "Du bist zu Besuch") {
                    HubIsland(decor = visited?.decor.orEmpty(), modifier = Modifier.fillMaxWidth(), labels = false, seed = visitId, life = visited)
                }
            }
            else -> IslandMap(
                ownName = ownName, pals = pals, infoById = infoById, letters = letters, homeDecor = home?.decor.orEmpty(), homeLife = home, labels = labels,
                onHome = { view = "home" }, onFriend = { selectedFriend = it }, ownSeed = ownId ?: 1L,
            )
        }
        // Calm HUD: back, where I am, and counters only when they mean something. Each counter is a shortcut.
        val ready = letters.count { it.incoming && it.unlocked && it.readAt == null && opened[it.id] == null }
        val qp = islands?.qp ?: 0
        val step = if (editing || place != null || view != "map") null
            else IsleGuide.nextStep(incoming = incoming.size, readyLetters = ready, openQuests = openQuests, friends = pals.size)
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(Modifier.clip(RoundedCornerShape(50)).clickable(onClickLabel = "Zurück") { if (editing) editing = false else if (view != "map") view = "map" else onBack() }) { AppIcon(R.drawable.ico_back, null, tint = Color(0xFFFFF8E6), size = 22.dp) }
            Spacer(Modifier.width(8.dp))
            // On the map the HUD's middle is the "what now?" hint; without one, it says who I am.
            if (step != null) NextStepCard(step, Modifier.weight(1f)) { place = step.building.name }
            else {
                if (view == "map") Pill { Text("$ownEmoji  $ownName", color = Color(0xFFFFF8E6), maxLines = 1) }
                Spacer(Modifier.weight(1f))
            }
            // Points and letters are always one tap away; at zero they only fade back.
            Pill(Modifier.padding(start = 6.dp).then(Modifier.clip(RoundedCornerShape(50)).clickable(onClickLabel = "Punkte ansehen") { onPoints() }.semantics { contentDescription = "$qp Punkte" })) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(R.drawable.ico_points, null, tint = Color(0xFFE6D3A3).copy(alpha = if (qp > 0) 1f else .6f), size = 18.dp)
                    Text(" $qp", color = Color(0xFFFFF8E6).copy(alpha = if (qp > 0) 1f else .6f))
                }
            }
            Spacer(Modifier.width(6.dp))
            Pill(Modifier.clip(RoundedCornerShape(50)).clickable(onClickLabel = "Briefe öffnen") { place = IsleBuilding.POST.name }.semantics { contentDescription = if (ready > 0) "$ready neue Briefe" else "Post" }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(R.drawable.ico_letter, null, tint = Color(0xFFFFF8E6).copy(alpha = if (ready > 0) 1f else .6f), size = 18.dp)
                    if (ready > 0) Text(" $ready", color = Color(0xFFFFF8E6))
                }
            }
        }
        if (pals.isEmpty() && view == "map") {
            Column(
                Modifier.align(Alignment.BottomCenter).padding(24.dp).padding(bottom = 76.dp)
                    .shadow(6.dp, RoundedCornerShape(22.dp)).background(Isle.Card, RoundedCornerShape(22.dp)).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Noch ruhig auf dem Meer", fontWeight = FontWeight.Bold, color = Isle.Ink, fontSize = 17.sp)
                Text("Sobald du Freunde hast, taucht für jeden eine eigene Insel auf.", color = Isle.Muted, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                IsleButton("Freunde finden", onPeople)
            }
        }
        // One fixed bar everywhere: map · my island · harbour. Context actions float above it.
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (visitId != null) {
                var writeMenu by remember(visitId) { mutableStateOf(false) }
                Box {
                    GoldAction("Schreiben", R.drawable.ico_edit) { writeMenu = true }
                    androidx.compose.material3.DropdownMenu(writeMenu, { writeMenu = false }) {
                        androidx.compose.material3.DropdownMenuItem({ Text("Chat – sofort") }, { writeMenu = false; onChat(visitId) }, leadingIcon = { AppIcon(R.drawable.ico_chat, null, size = 20.dp) })
                        androidx.compose.material3.DropdownMenuItem({ Text("Brief – kommt per Boot") }, { writeMenu = false; onComposeLetter(visitId) }, leadingIcon = { AppIcon(R.drawable.ico_letter, null, size = 20.dp) })
                    }
                }
            }
            if (view == "home" && place == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoldAction("Land erweitern", R.drawable.ico_add) { landPick = true }
                GoldAction(if (editing) "Fertig" else "Bearbeiten", R.drawable.ico_edit) { editing = !editing; if (!editing) decorEditing = false }
            }
            if (editing && !creativeActive) GoldAction(if (decorEditing) "Deko fertig" else "Deko", R.drawable.ico_edit) { decorEditing = !decorEditing }
            Row(
                Modifier.background(Color(0xD9243A3F), RoundedCornerShape(30.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (editing) BarItem("done", "Fertig") { editing = false; decorEditing = false }
                else {
                    BarItem("chats", "Chats", badge = chatsBadge) { place = null; onBack() }
                    // Long-press on the map is the shortcut out to the messenger's main menu.
                    BarItem("map", "Karte", selected = view == "map" && place == null, onLongClick = onBack) { place = null; view = "map" }
                    BarItem("home", "Meine Insel", selected = view == "home" && place == null, badge = incoming.size) { place = null; view = "home" }
                    BarItem("harbour", "Hafen", selected = place == IsleBuilding.HARBOUR.name, badge = openQuests) { place = IsleBuilding.HARBOUR.name }

                }
            }
        }
        val openPlace = place?.let { key -> IsleBuilding.entries.firstOrNull { it.name == key } }
        androidx.compose.animation.AnimatedVisibility(
            visible = openPlace != null,
            enter = androidx.compose.animation.slideInVertically { it / 3 } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { it / 3 } + androidx.compose.animation.fadeOut(),
        ) {
            val close = { place = null }
            when (openPlace ?: IsleBuilding.HOUSE) {
                IsleBuilding.POST -> PostOffice(
                    pals = pals, letters = letters, opened = opened,
                    onOpen = onOpenLetter, onLocked = onLockedTap, onWrite = onComposeLetter, onClose = close,
                )
                IsleBuilding.HARBOUR -> HarbourBoard(
                    quests = quests, topics = topics, onQuestDone = onQuestDone, onQuestDelete = onQuestDelete,
                    onNewQuest = { newQuestFor = null to null }, onAllTopics = onAllTopics, onClose = close,
                )
                IsleBuilding.LIGHTHOUSE -> LighthouseView(
                    pals = pals, levels = infoById.mapValues { it.value.level }, incoming = incoming, outgoing = outgoing,
                    onRespond = onRespondFriend, onFriend = { selectedFriend = it }, onFind = onPeople, onClose = close,
                )
                IsleBuilding.LIBRARY -> LibraryBook(onClose = close, glossary = glossary)
                IsleBuilding.CAMPFIRE -> CommunityHall(
                    groups = groups, quests = quests, onGroupQuest = { newQuestFor = null to it },
                    onManage = { onPlace(IsleBuilding.CAMPFIRE) }, onClose = close,
                )
                IsleBuilding.HOUSE -> MyHouse(
                    name = ownName, emoji = ownEmoji, color = ownColor, friends = pals.size,
                    questsDone = quests.count { it.completedAt != null },
                    letters = letters.size, qp = islands?.qp ?: 0,
                    onSettings = { onPlace(IsleBuilding.HOUSE) },
                    onEdit = { place = null; view = "home"; editing = true }, onClose = close,
                )
            }
        }
    }

    selectedFriend?.let { id ->
        val friend = pals.firstOrNull { it.id == id }
        if (friend == null) selectedFriend = null else ModalBottomSheet(
            onDismissRequest = { selectedFriend = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Isle.Card,
        ) {
            FriendSheet(
                friend = friend, info = infoById[id], label = labels[id], onLabel = { onLabel(id, it) },
                letters = letters.filter { it.peerId == id && it.groupId == null }, opened = opened,
                quests = quests.filter { it.targetType == "friend" && it.targetId == id },
                topicCount = Conversations.topicsWith(id, friend.name, topics).count { it.completedAt == null },
                onChat = { selectedFriend = null; onChat(id) },
                onLetter = { selectedFriend = null; onComposeLetter(id) },
                onOpenLetter = { selectedFriend = null; onOpenLetter(it) },
                onLockedTap = onLockedTap,
                onTopics = { selectedFriend = null; onTopics(id) },
                onVisit = { selectedFriend = null; view = "visit:$id" },
                onNewQuest = { newQuestFor = id to null },
                onQuestDone = onQuestDone,
            )
        }
    }
    slotPick?.let { slot ->
        val island = home
        if (island == null) slotPick = null else DecorPicker(
            current = island.decor[slot], items = island.items, score = island.score,
            onPick = { item ->
                val before = island
                val next = island.decor.toMutableMap().apply { if (item == null) remove(slot) else put(slot, item) }
                home = island.copy(decor = next); slotPick = null
                scope.launch {
                    runCatching { saveDecor(next) }.onFailure { if (currentResponse(before)) { home = before; onError(ApiErrors.friendly(it.message)) } }
                }
            },
            onDismiss = { slotPick = null },
        )
    }
    if ((upgradePick != null || landPick) && home != null) {
        val island = home!!
        val building = upgradePick
        ModalBottomSheet(onDismissRequest = { if (!constructionBusy) { upgradePick = null; landPick = false } },
            containerColor = Isle.Card, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            IslandUpgradeContent(building, island, constructionBusy,
                onOpen = { upgradePick = null; landPick = false; place = building?.name },
                onAction = { action ->
                    if (!constructionBusy) {
                        constructionBusy = true
                        scope.launch {
                            try {
                                runCatching { changeConstruction(island.construction, action, building?.name) }
                                    .onSuccess { if (it != null && currentResponse(it)) home = it }
                                    .onFailure {
                                        if (it is kotlinx.coroutines.CancellationException) throw it
                                        if (currentResponse(island)) {
                                            onError(ApiErrors.friendly(it.message))
                                            ownId?.let { id -> runCatching { loadIsland(id) }.getOrNull()?.let { fresh -> if (currentResponse(fresh)) home = fresh } }
                                        }
                                    }
                            } finally { constructionBusy = false }
                        }
                    }
                }, onDismiss = { if (!constructionBusy) { upgradePick = null; landPick = false } })
        }
    }
    fun commitPlaces(next: Map<Int, ApiClient.LifePlace>) {
        val before = home ?: return
        val here = before.here?.takeIf { it.plot == null || next.containsKey(it.plot) } ?: before.here?.let { ApiClient.Here(null, "") }
        home = before.copy(places = next, here = here)
        scope.launch { runCatching { savePlaces(next) }.onFailure { if (currentResponse(before)) { home = before; onError(ApiErrors.friendly(it.message)) } } }
    }
    buildPick?.let { plot ->
        val island = home
        if (island == null) buildPick = null else androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { buildPick = null }, containerColor = Isle.Card,
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            val current = island.places[plot]
            BuildPlaceContent(
                current = current,
                onBuild = { p -> buildPick = null; commitPlaces(island.places + (plot to p)) },
                onDemolish = if (current != null) ({ buildPick = null; commitPlaces(island.places - plot) }) else null,
                onDismiss = { buildPick = null },
            )
        }
    }
    plotPick?.let { plot ->
        val island = home
        val p = island?.places?.get(plot)
        if (island == null || p == null) plotPick = null else androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { plotPick = null }, containerColor = Isle.Card,
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            val imHere = island.here?.plot == plot
            PlaceSheetContent(
                place = p, imHere = imHere, status = island.here?.status.orEmpty(),
                onHere = { stay, status ->
                    plotPick = null
                    val before = island
                    val target = if (stay) plot else null
                    home = island.copy(here = ApiClient.Here(target, if (stay) status else ""))
                    scope.launch { runCatching { saveHere(target, if (stay) status else "") }.onFailure { if (currentResponse(before)) { home = before; onError(ApiErrors.friendly(it.message)) } } }
                },
                onRebuild = { plotPick = null; buildPick = plot },
            )
        }
    }
    newQuestFor?.let { (peer, group) ->
        NewQuestSheet(
            pals = pals, groups = groups, presetPeer = peer, presetGroup = group,
            onDismiss = { newQuestFor = null },
            onCreate = { t, d, icon, pts, p, g -> newQuestFor = null; onCreateQuest(t, d, icon, pts, p, g) },
        )
    }
    }
}

@Composable
private fun IslandMap(
    ownName: String,
    pals: List<ApiClient.UserSummary>,
    infoById: Map<Long, ApiClient.IslandInfo>,
    letters: List<ApiClient.Message>,
    homeDecor: Map<Int, String>,
    homeLife: ApiClient.HomeIsland? = null,
    labels: Map<Long, String> = emptyMap(),
    onHome: () -> Unit,
    onFriend: (Long) -> Unit,
    ownSeed: Long = 1L,
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(androidx.compose.ui.geometry.Size(1f, 1f)) }
    val reduced = rememberIsleReducedMotion()
    // The sea map is still: no per-frame loops. Boats only move when the real clock moves.
    // Real clock, refreshed once a minute: boats creep forward like an hour hand, never loop.
    var nowSec by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(60_000); nowSec = System.currentTimeMillis() / 1000 } }
    var boatSheet by remember { mutableStateOf<ApiClient.Message?>(null) }
    // Friends with the strongest friendship sit first on the inner ring.
    val ordered = remember(pals, infoById) { pals.sortedWith(compareByDescending<ApiClient.UserSummary> { infoById[it.id]?.score ?: 0 }.thenBy { it.name }) }
    val spots = remember(ordered.size) { IsleLayout.positions(ordered.size) }
    val transit = remember(letters) { letters.filter { !it.unlocked && it.groupId == null } }
    val scope = rememberCoroutineScope()
    var glide by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun animateTo(targetPan: Offset, targetZoom: Float) {
        glide?.cancel()
        glide = scope.launch {
            val sp = pan; val sz = zoom
            androidx.compose.animation.core.animate(0f, 1f, animationSpec = tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { f, _ ->
                zoom = sz + (targetZoom - sz) * f
                pan = IsleMotion.clampPan(Offset(sp.x + (targetPan.x - sp.x) * f, sp.y + (targetPan.y - sp.y) * f), zoom, viewport.width, viewport.height)
            }
        }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .onSizeChanged { viewport = androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) }
            // Momentum: after a swipe the sea keeps gliding and eases out.
            .pointerInput(Unit) {
                val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    glide?.cancel()
                    tracker.resetTracking()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var multi = false
                    do {
                        val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        if (ev.changes.count { it.pressed } > 1) multi = true
                        ev.changes.firstOrNull { it.id == down.id }?.let { tracker.addPosition(it.uptimeMillis, it.position) }
                    } while (ev.changes.any { it.pressed })
                    if (multi || reduced) return@awaitEachGesture
                    val v = tracker.calculateVelocity()
                    if (kotlin.math.hypot(v.x, v.y) < 300f) return@awaitEachGesture
                    glide = scope.launch {
                        var vx = v.x; var vy = v.y
                        var last = androidx.compose.runtime.withFrameMillis { it }
                        while (kotlin.math.hypot(vx, vy) > 25f) {
                            androidx.compose.runtime.withFrameMillis { now ->
                                val dt = ((now - last).coerceIn(1L, 32L)) / 1000f
                                last = now
                                pan = IsleMotion.clampPan(Offset(pan.x + vx * dt, pan.y + vy * dt), zoom, viewport.width, viewport.height)
                                val k = kotlin.math.exp(-3.4f * dt)
                                vx *= k; vy *= k
                            }
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, p, z, _ ->
                    val (zp, nz) = IsleMotion.zoomAround(pan, zoom, z, centroid, viewport.width, viewport.height)
                    zoom = nz
                    pan = IsleMotion.clampPan(zp + p, nz, viewport.width, viewport.height)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { at ->
                    if (zoom > 1.3f) animateTo(Offset.Zero, 1f)
                    else {
                        val (zp, nz) = IsleMotion.zoomAround(pan, zoom, 1.8f / zoom, at, viewport.width, viewport.height)
                        animateTo(zp, nz)
                    }
                })
            }
            .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y },
    ) {
        val w = maxWidth; val h = maxHeight
        val density = LocalDensity.current
        val center = Offset(with(density) { (w * .5f).toPx() }, with(density) { (h * .5f).toPx() })
        val hubShorePx = with(density) { 83.dp.toPx() }
        val points = spots.map { (x, y) -> Offset(with(density) { (w * x).toPx() }, with(density) { (h * y).toPx() }) }
        val docks = ordered.mapIndexed { i, f ->
            val fw = with(density) { Isle.ownerWidth(infoById[f.id]?.island).toPx() }
            val fc = points[i] - Offset(0f, fw * .09f)
            IslandHarbours.shore(center, fc, with(density) { 180.dp.toPx() }) to IslandHarbours.shore(fc, center, fw)
        }
        AnimatedIslandSea(Modifier.fillMaxSize(), oversized = true)
        // Painted sea (moves with the map), sparkles and dotted routes.
        val sea = rememberSeaBrush()
        // One lane per boat on a route: boats sail side by side instead of stacking.
        // One lane per boat: each lane is a gentle arc that bows out to its own side, so boats sail
        // side by side instead of stacking; the boat rides the arc's open-water middle.
        val laneGap = with(density) { 78.dp.toPx() }
        // Obstacles a boat (with its time tag) must not sit on: every island incl. its name plaque, the screen edge and other boats.
        val boatR = with(density) { 30.dp.toPx() }
        val islands = ordered.mapIndexed { i, f ->
            val wPx = with(density) { Isle.ownerWidth(infoById[f.id]?.island).toPx() }
            Offset(points[i].x, points[i].y + wPx * .1f) to wPx * .45f
        } + (Offset(center.x, center.y + hubShorePx * .2f) to hubShorePx * .9f)
        val edge = with(density) { 40.dp.toPx() }
        val mapW = with(density) { w.toPx() }; val mapH = with(density) { h.toPx() }
        val placed = mutableListOf<Offset>()
        fun clashes(p: Offset): Float {
            var c = 0f
            islands.forEach { (o, r) -> val d = (p - o).getDistance(); if (d < r + boatR) c += r + boatR - d }
            placed.forEach { o -> if (kotlin.math.abs(p.x - o.x) < boatR * 3f && kotlin.math.abs(p.y - o.y) < boatR * 2.8f) c += boatR * 20 }
            if (p.x < edge * 1.2f || p.x > mapW - edge * 1.2f) c += boatR * 4
            if (p.y < edge * 3 || p.y > mapH - edge * 3) c += boatR * 4
            return c
        }
        val voyages = ordered.flatMapIndexed { i, friend ->
            val all = transit.filter { it.peerId == friend.id }.sortedBy { it.createdAt }
            val dx = points[i].x - center.x; val dy = points[i].y - center.y
            val len = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
            val ux = dx / len; val uy = dy / len
            // Sail on open water only: from my island's shore to the friend's shore.
            val startD = hubShorePx.coerceAtMost(len * .45f)
            val endD = (len - with(density) { (Isle.ownerWidth(infoById[friend.id]?.island) * .42f).toPx() }).coerceAtLeast(startD + 1f)
            // Close neighbours have little open water: fewer lanes there; the rest wait in the outermost lane as "+N".
            val mine = all.take(IsleMotion.lanesThatFit(endD - startD, laneGap))
            val extra = all.size - mine.size
            val lanes = IsleMotion.laneOffsets(mine.size)
            mine.mapIndexed { k, letter ->
                val (homeDock, friendDock) = docks[i]
                fun at(d: Float, side: Float): Offset {
                    val u = ((d - startD) / (endD - startD)).coerceIn(0f, 1f)
                    return homeDock + (friendDock - homeDock) * u + Offset(-uy * side, ux * side)
                }
                val share = IsleMotion.tripShare(letter, nowSec)
                // Keep the hull off the shores: the trip maps onto the arc's open-water middle.
                val t = .14f + (share ?: .5f) * .72f
                fun voyage(bow: Float): Voyage {
                    val a = at(startD, 0f); val b = at(endD, 0f)
                    val (from, to) = if (letter.incoming) b to a else a to b
                    val mid = at((startD + endD) / 2f, bow)
                    // Quadratic arc through `mid`: control point = 2·mid − (from+to)/2.
                    val ctrl = Offset(2 * mid.x - (from.x + to.x) / 2f, 2 * mid.y - (from.y + to.y) / 2f)
                    return Voyage(friend, letter, from, ctrl, to, t, share, if (k == mine.lastIndex) extra else 0)
                }
                // Own lane first; if that water is taken (island, plaque, other boat), bow the lane further out.
                // Never bow further out than the route is long, so a lane always visibly links its two islands.
                val candidates = IsleMotion.laneCandidates(lanes[k]).map { it * laneGap }.filter { kotlin.math.abs(it) <= laneGap * 1.6f }.ifEmpty { listOf(lanes[k] * laneGap) }
                fun cost(v: Voyage) = clashes(v.boat)
                val best = candidates.map { voyage(it) }.let { all -> all.firstOrNull { cost(it) == 0f } ?: all.minBy { cost(it) } }
                placed += best.boat
                best
            }
        }
        val busy = voyages.map { it.friend.id }.toSet()
        Canvas(Modifier.fillMaxSize()) {

            points.forEachIndexed { i, p ->
                if (ordered[i].id in busy) return@forEachIndexed
                drawLine(
                    Color.White.copy(alpha = .45f), docks[i].first, docks[i].second, 3f, StrokeCap.Round,
                    PathEffect.dashPathEffect(floatArrayOf(4f, 16f), 0f),
                )
            }
            // Each lane is a progress bar: sailed water bright and solid, the rest of the way faint and dotted.
            voyages.forEach { v ->
                val dots = PathEffect.dashPathEffect(floatArrayOf(4f, 14f), 0f)
                drawPath(v.path(v.t, 1f), Color.White.copy(alpha = .5f), style = Stroke(4f, cap = StrokeCap.Round, pathEffect = dots))
                if (v.share != null) {
                    drawPath(v.path(0f, v.t), Color(0x66243A3F), style = Stroke(11f, cap = StrokeCap.Round))
                    drawPath(v.path(0f, v.t), Color(0xFFE6D3A3), style = Stroke(6f, cap = StrokeCap.Round))
                } else drawPath(v.path(0f, v.t), Color.White.copy(alpha = .5f), style = Stroke(4f, cap = StrokeCap.Round, pathEffect = dots))
                drawCircle(Color(0xFFE6D3A3), 6f, v.to)
            }
        }
        // Home island.
        ordered.forEachIndexed { i, friend ->
            val info = infoById[friend.id]
            val level = info?.level ?: 1
            val (x, y) = spots[i]
            IslandSprite(
                level, friend.id, Isle.ownerWidth(info?.island), w * x, h * y,
                friend.name, FriendLabels.display(labels[friend.id], level), profileColor(friend.displayColor), info?.island,
            ) { onFriend(friend.id) }
        }
        val hubW = 180.dp
        Column(
            Modifier.offset(x = w * .5f - hubW / 2, y = h * .5f - hubW * .42f).width(hubW)
                .worldTap("Meine Insel öffnen", onHome),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The overview shows the same life buildings as the close-up, just without name tags.
            HubIsland(homeDecor, Modifier.fillMaxWidth(), labels = false, seed = ownSeed, life = homeLife, lifeTags = false)
            Row(
                Modifier.offset(y = (-8).dp).background(Color(0xD9243A3F), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WorldText(ownName, 14.sp, plaque = false)
                WorldText("  Meine Insel", 10.sp, fill = Color(0xFFE6D3A3), display = false, plaque = false)
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            docks.forEachIndexed { i, (homeDock, friendDock) ->
                drawIslandJetty(homeDock, center, 8.dp.toPx())
                drawIslandJetty(friendDock, points[i], 7.dp.toPx())
            }
        }
        // Boats in transit, still and individually tappable.
        voyages.forEach { v ->
            val boat = LetterBoat.forMode(v.letter.mode)
            val p = v.boat
            val hull: @Composable () -> Unit = {
                Box(Modifier.size(42.dp), Alignment.Center) {
                    Image(painterResource(boat.res), null, Modifier.size(38.dp))
                    Box(Modifier.align(Alignment.TopEnd).size(16.dp).background(Color(0xCC243A3F), CircleShape), Alignment.Center) {
                        AppIcon(boat.badge, null, tint = Color(0xFFFFF8E6), size = 11.dp)
                    }
                    if (v.more > 0) Text(
                        "+${v.more}", fontSize = 10.sp, color = Color(0xFF243A3F), fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.BottomStart).background(Color(0xFFE6D3A3), CircleShape).padding(horizontal = 4.dp),
                    )
                }
            }
            val tapped = Modifier
                .worldTap("Schiff ansehen") { boatSheet = v.letter }
                .semantics { contentDescription = "${boat.label} – Brief ${if (v.letter.incoming) "von" else "an"} ${v.friend.name}, ${IsleMotion.seaTime(IsleMotion.arrivalSec(v.letter), nowSec)}" }
            Column(
                Modifier.offset { IntOffset((p.x - 42.dp.toPx()).roundToInt(), (p.y - 21.dp.toPx()).roundToInt()) }.width(84.dp).then(tapped),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { hull() }
        }
        boatSheet?.let { letter ->
            BoatSheet(letter, ordered.firstOrNull { it.id == letter.peerId }?.name ?: "…", nowSec) { boatSheet = null }
        }
    }
}

/** One boat on its own lane: from/to are the shores, [boat] where it is now, [share] null = at anchor. */
private class Voyage(
    val friend: ApiClient.UserSummary, val letter: ApiClient.Message,
    val from: Offset, val ctrl: Offset, val to: Offset, val t: Float, val share: Float?,
    /** Further boats on this route that did not get a lane of their own. */
    val more: Int = 0,
) {
    fun point(t: Float): Offset {
        val u = 1f - t
        return Offset(u * u * from.x + 2 * u * t * ctrl.x + t * t * to.x, u * u * from.y + 2 * u * t * ctrl.y + t * t * to.y)
    }
    val boat: Offset get() = point(t)
    /** The arc between t0 and t1 as a polyline (smooth enough at 24 steps). */
    fun path(t0: Float, t1: Float) = androidx.compose.ui.graphics.Path().apply {
        val p0 = point(t0); moveTo(p0.x, p0.y)
        for (k in 1..24) { val p = point(t0 + (t1 - t0) * k / 24f); lineTo(p.x, p.y) }
    }
}

/** Close-up of one island: calm sea, title card, the island filling the width. */
@Composable
private fun CloseIsland(title: String, subtitle: String, content: @Composable () -> Unit) {
    // Pinch (1×–2.6×), drag and double-tap zoom happen in a clipped viewport below the title,
    // so the zoomed island never slides over the title or out of reach of the fingers.
    var z by remember { mutableFloatStateOf(1f) }
    var off by remember { mutableStateOf(Offset.Zero) }
    var view by remember { mutableStateOf(androidx.compose.ui.geometry.Size(1f, 1f)) }
    var isle by remember { mutableStateOf(androidx.compose.ui.geometry.Size(1f, 1f)) }
    val scope = rememberCoroutineScope()
    var pendingTap by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val tapGate: (() -> Unit) -> Unit = remember {
        { action ->
            pendingTap?.cancel()
            pendingTap = scope.launch { kotlinx.coroutines.delay(260); pendingTap = null; action() }
        }
    }
    fun clamp(o: Offset, zz: Float): Offset {
        val mx = ((isle.width * zz - view.width) / 2f).coerceAtLeast(0f)
        val my = ((isle.height * zz - view.height) / 2f).coerceAtLeast(0f)
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }
    fun animateTo(tz: Float, to: Offset) {
        val sz = z; val so = off
        scope.launch {
            androidx.compose.animation.core.animate(0f, 1f, animationSpec = tween(280, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { f, _ ->
                z = sz + (tz - sz) * f
                off = Offset(so.x + (to.x - so.x) * f, so.y + (to.y - so.y) * f)
            }
        }
    }
    BackHandler(enabled = z > 1.05f) { animateTo(1f, Offset.Zero) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(top = 64.dp, bottom = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Title cards draw above the island (zIndex), so a zoomed island can slide under them
        // instead of being cut off at a hard edge.
        WorldText(title, 20.sp, modifier = Modifier.zIndex(1f).padding(top = 2.dp))
        Spacer(Modifier.height(3.dp))
        WorldText(subtitle, 11.sp, fill = Color(0xFFE6D3A3), display = false, modifier = Modifier.zIndex(1f))
        Spacer(Modifier.height(8.dp))
        // No clipToBounds: the island lies on the same full-screen sea, so panning/zooming
        // never shows a straight cut between the moving island and the static water.
        Box(
            Modifier.fillMaxWidth().weight(1f)
                .onSizeChanged { view = androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoomBy, _ ->
                        val nz = (z * zoomBy).coerceIn(1f, 2.6f)
                        val c = Offset(view.width / 2f, view.height / 2f)
                        val r = nz / z
                        val o = Offset(off.x + (centroid.x - c.x - off.x) * (1f - r), off.y + (centroid.y - c.y - off.y) * (1f - r)) + pan
                        z = nz; off = clamp(o, nz)
                    }
                }
                // Double-tap is watched in the Initial pass, so it also works on top of buildings:
                // their own tap is held back briefly (see LocalIslandTapGate) and cancelled here.
                .pointerInput(Unit) {
                    var lastUp = 0L
                    var lastPos = Offset.Zero
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        val quick = lastUp > 0 && down.uptimeMillis - lastUp < viewConfiguration.doubleTapTimeoutMillis &&
                            (down.position - lastPos).getDistance() < viewConfiguration.touchSlop * 4
                        if (quick) {
                            pendingTap?.cancel(); pendingTap = null
                            do {
                                val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                e.changes.forEach { it.consume() }
                            } while (e.changes.any { it.pressed })
                            lastUp = 0L
                            val at = down.position
                            val tz = if (z > 1.3f) 1f else 2f
                            val c = Offset(view.width / 2f, view.height / 2f)
                            animateTo(tz, if (tz == 1f) Offset.Zero else clamp(Offset((c.x - at.x) * (tz - 1f), (c.y - at.y) * (tz - 1f)), tz))
                        } else {
                            var multi = false
                            var up: androidx.compose.ui.input.pointer.PointerInputChange? = null
                            while (true) {
                                val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                if (e.changes.size > 1) multi = true
                                if (e.changes.none { it.pressed }) { up = e.changes.firstOrNull(); break }
                            }
                            val moved = up == null || (up.position - down.position).getDistance() > viewConfiguration.touchSlop
                            if (!multi && !moved && up != null) { lastUp = up.uptimeMillis; lastPos = up.position } else lastUp = 0L
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    .onSizeChanged { isle = androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) }
                    .graphicsLayer { scaleX = z; scaleY = z; translationX = off.x; translationY = off.y },
            ) {
                AnimatedIslandSea(Modifier.fillMaxSize(), oversized = true)
                // Signs read the zoom lazily and stay the same size on screen.
                androidx.compose.runtime.CompositionLocalProvider(LocalIslandZoom provides { z }, LocalIslandTapGate provides tapGate) { content() }
            }
        }
    }
}

@Composable
private fun IslandSprite(level: Int, seed: Long, width: Dp, cx: Dp, cy: Dp, name: String, sub: String, color: Color, life: ApiClient.HomeIsland? = null, onClick: () -> Unit) {
    Column(
        Modifier.offset(x = cx - width / 2, y = cy - width * .5f).width(width)
            .worldTap("$name öffnen", onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HubIsland(life?.decor.orEmpty(), Modifier.fillMaxWidth(), labels = false, seed = seed, life = life, lifeTags = false)
        // Name painted on the sea right under the island, nickname as a small line beneath.
        Column(
            Modifier.offset(y = 2.dp).widthIn(max = 152.dp)
                .background(Color(0xD9243A3F), RoundedCornerShape(9.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(color, CircleShape))
                Spacer(Modifier.width(5.dp))
                WorldText(name, 12.sp, plaque = false)
            }
            if (sub.isNotBlank()) Text(sub, fontFamily = Kit.Body, fontSize = 10.sp, color = Color(0xFFE6D3A3),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
internal fun QuestRow(q: ApiClient.Quest, onDone: (ApiClient.Quest, Boolean) -> Unit, onDelete: ((ApiClient.Quest) -> Unit)?) {
    val done = q.completedAt != null
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).background(if (done) Isle.Sand else Color(0xFFF2FAFA), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(Isle.Card, CircleShape), Alignment.Center) { AppIcon(AppIcons.questIcon(q.icon), null, tint = Isle.TealDark, size = 22.dp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(q.title, fontWeight = FontWeight.Bold, color = Isle.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                (if (q.targetType == "group") "Gruppe " else "mit ") + q.targetName +
                    (if (done) " · erledigt${q.completedByName?.let { " von $it" } ?: ""}" else ""),
                fontSize = 12.sp, color = Isle.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(R.drawable.ico_points, null, tint = Isle.Star, size = 14.dp); Text(" ${q.points}", fontWeight = FontWeight.Bold, color = Isle.Star, fontSize = 13.sp)
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.size(40.dp).clip(CircleShape)
                .background(if (done) Isle.Teal else Isle.Card)
                .border(2.dp, Isle.Teal, CircleShape)
                .clickable(onClickLabel = if (done) "Wieder öffnen" else "Als erledigt markieren") { onDone(q, !done) }
                .semantics { contentDescription = if (done) "Wieder öffnen" else "Als erledigt markieren" },
            Alignment.Center,
        ) { if (done) AppIcon(R.drawable.ico_check, null, tint = Color.White, size = 16.dp) }
        if (onDelete != null && q.canDelete) TextButton(onClick = { onDelete(q) }, Modifier.heightIn(min = 40.dp)) {
            AppIcon(R.drawable.ico_close, null, tint = Isle.Muted, size = 20.dp)
        }
    }
}

/**
 * The one quest form of the app. The island, the harbour, the campfire, the
 * friend page and a chat message all open this same dialog – only the presets differ.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun NewQuestSheet(
    pals: List<ApiClient.UserSummary>,
    groups: List<ApiClient.Group>,
    presetPeer: Long?,
    presetGroup: Long?,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, Int, Long?, Long?) -> Unit,
    draft: ChatExtras.QuestDraft? = null,
) {
    var title by rememberSaveable { mutableStateOf(draft?.title.orEmpty()) }
    var details by rememberSaveable { mutableStateOf(draft?.details.orEmpty()) }
    var icon by rememberSaveable { mutableStateOf(draft?.icon ?: "hike") }
    var points by rememberSaveable { mutableIntStateOf(draft?.points ?: 20) }
    var peer by rememberSaveable { mutableStateOf(presetPeer ?: if (presetGroup == null) pals.firstOrNull()?.id else null) }
    var group by rememberSaveable { mutableStateOf(presetGroup) }
    val fixed = presetPeer != null && pals.size <= 1 && groups.isEmpty()
    val p = Harbour.palette()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        title = {
            Text(
                pals.firstOrNull { it.id == presetPeer }?.takeIf { fixed }?.let { "Quest mit ${it.name}" } ?: "Neue Quest",
                style = Harbour.Title.copy(fontSize = 20.sp), color = p.ink,
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Etwas, das ihr wirklich gemeinsam macht. Erledigt lässt es eure Insel wachsen.", color = p.inkSoft, fontSize = 13.sp)
                OutlinedTextField(title, { title = it.take(80) }, label = { Text("Was habt ihr vor?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(details, { details = it.take(500) }, label = { Text("Details (optional)") }, modifier = Modifier.fillMaxWidth())
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppIcons.quest.forEach { (k, res) -> IconChoice(res, AppIcons.questLabels[k].orEmpty(), icon == k) { icon = k } }
                }
                if (!fixed) {
                    Text("Mit wem?", fontWeight = FontWeight.Bold, color = p.ink, fontSize = 14.sp)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pals.take(8).forEach { f ->
                            FilterChip(peer == f.id, { peer = f.id; group = null }, label = { Text("${f.avatarEmoji} ${f.name}", maxLines = 1) })
                        }
                        groups.take(6).forEach { g ->
                            FilterChip(group == g.id, { group = g.id; peer = null }, label = { Text(g.name, maxLines = 1) }, leadingIcon = { AppIcon(R.drawable.ico_groups, null, size = 16.dp) })
                        }
                    }
                }
                Text("Belohnung", fontWeight = FontWeight.Bold, color = p.ink, fontSize = 14.sp)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(10 to "klein", 20 to "normal", 30 to "groß", 50 to "episch").forEach { (pts, l) ->
                        FilterChip(points == pts, { points = pts }, label = { Text("$pts $l") }, leadingIcon = { AppIcon(R.drawable.ico_points, null, size = 16.dp) })
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = { onCreate(title.trim(), details.trim(), icon, points, peer, group) },
                enabled = title.isNotBlank() && (peer != null || group != null),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = p.seaDeep, contentColor = Color.White),
            ) { Text("Quest anlegen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun IconChoice(icon: Int, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(if (selected) Isle.Teal.copy(alpha = .18f) else Isle.Sand)
            .border(if (selected) 2.dp else 0.dp, Isle.Teal, CircleShape).clickable(onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label; if (selected) this.selected = true },
        Alignment.Center,
    ) { AppIcon(icon, null, tint = if (selected) Isle.TealDark else Isle.Muted, size = 22.dp) }
}

@Composable
private fun SectionTitle(text: String, action: Pair<String, () -> Unit>? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontWeight = FontWeight.Bold, color = Isle.Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        action?.let { (label, onClick) -> TextButton(onClick = onClick, Modifier.heightIn(min = 40.dp)) { Text(label, color = Isle.TealDark) } }
    }
}

@Composable
private fun IsleButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 50.dp),
        shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Isle.Teal),
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable
private fun Pill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).background(Color(0xD9243A3F), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalTextStyle provides androidx.compose.material3.LocalTextStyle.current.copy(
                fontFamily = Kit.Display, color = Color(0xFFFFF8E6),
            ),
        ) { content() }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun BarItem(icon: String, label: String, badge: Int = 0, selected: Boolean = false, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(22.dp))
            .background(if (selected) Color(0x33E6D3A3) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = if (onLongClick != null) "Zum Hauptmenü" else null)
            .heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 4.dp)
            .semantics { if (selected) this.selected = true },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            val res = AppIcons.bar[icon]
            if (res != null) AppIcon(res, null, tint = if (selected) Color(0xFFE6D3A3) else Color(0xFFFFF8E6), size = 26.dp, modifier = Modifier.padding(4.dp))
            else Text(icon, fontSize = 24.sp, color = Color.White)
            if (badge > 0) Text(
                "$badge", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-2).dp).background(Isle.Teal, CircleShape).padding(horizontal = 4.dp),
            )
        }
        WorldText(label, 12.sp, plaque = false)
    }
}


@Composable
private fun rememberIsleReducedMotion(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(context) {
        runCatching {
            android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}


/** Pick or type a personal name for a friend; empty = back to the level name. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun LabelDialog(name: String, current: String?, onDismiss: () -> Unit, onSave: (String?) -> Unit) {
    var text by rememberSaveable { mutableStateOf(current ?: "") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wie nennst du $name?") },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    text, { text = it.take(28) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("z. B. Allerbester Freund") },
                )
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FriendLabels.suggestions.forEach { s ->
                        androidx.compose.material3.FilterChip(selected = text == s, onClick = { text = s }, label = { Text(s) })
                    }
                }
                Text("Nur du siehst diesen Namen.", color = Isle.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(FriendLabels.clean(text)) }) { Text("Speichern") } },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onSave(null) }) { Text("Entfernen") }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}


/** Tap on a boat: what it carries, where it goes and on which real day it docks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoatSheet(letter: ApiClient.Message, friendName: String, nowSec: Long, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF243A3F), sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        BoatSheetContent(letter, friendName, nowSec)
    }
}

@Composable
internal fun BoatSheetContent(letter: ApiClient.Message, friendName: String, nowSec: Long) {
    val boat = LetterBoat.forMode(letter.mode)
    val fmt = remember { java.text.SimpleDateFormat("EEEE, d. MMMM 'um' HH:mm", java.util.Locale.GERMAN) }
    fun date(sec: Long) = fmt.format(java.util.Date(sec * 1000))
    val share = IsleMotion.tripShare(letter, nowSec)
    val arrival = IsleMotion.arrivalSec(letter)
    run {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 28.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(boat.res), null, Modifier.size(72.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    WorldText(boat.label.substringBefore(" ·"), 22.sp, plaque = false)
                    WorldText(if (letter.incoming) "Brief von $friendName" else "Brief an $friendName", 13.sp, fill = Color(0xFFE6D3A3), display = false, plaque = false)
                }
            }
            Spacer(Modifier.height(18.dp))
            // Route: home harbour → friend, with the boat where it really is.
            Box(Modifier.fillMaxWidth().height(28.dp)) {
                Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(4.dp).background(Color(0x33FFFFFF), RoundedCornerShape(50)))
                Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(share ?: .5f).height(4.dp).background(Color(0xFFE6D3A3), RoundedCornerShape(50)))
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    AppIcon(R.drawable.ico_boat, null, modifier = Modifier.offset(x = maxWidth * (share ?: .5f) - 10.dp), tint = Color(0xFFFFF8E6), size = 20.dp)
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Text(if (letter.incoming) friendName else "Meine Insel", color = Color(0xB3FFF8E6), fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(if (letter.incoming) "Meine Insel" else friendName, color = Color(0xB3FFF8E6), fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(IsleMotion.seaIcon(arrival, nowSec), null, tint = Color(0xFFE6D3A3), size = 18.dp, modifier = Modifier.padding(end = 6.dp))
                WorldText(IsleMotion.seaTime(arrival, nowSec), 15.sp, display = false)
            }
            Spacer(Modifier.height(12.dp))
            InfoLine("Abgelegt", date(letter.createdAt))
            when {
                letter.mode == "random" && letter.randomFrom != null && letter.randomTo != null ->
                    InfoLine("Legt an", "irgendwann zwischen ${date(letter.randomFrom)} und ${date(letter.randomTo)}")
                arrival == null -> InfoLine("Legt an", if (letter.incoming) "sobald du zustimmst" else "sobald $friendName zustimmt – liegt bis dahin vor Anker")
                else -> InfoLine("Legt an", date(arrival))
            }
            if (share != null) InfoLine("Strecke", "${(share * 100).roundToInt()} % geschafft")
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = Color(0xB3FFF8E6), fontSize = 14.sp, modifier = Modifier.width(84.dp))
        Text(value, color = Color(0xFFFFF8E6), fontSize = 14.sp, fontFamily = Kit.Body, fontWeight = FontWeight.Bold)
    }
}


/** "What now?" for the island: one calm suggestion, highest priority first. Pure and testable. */
internal object IsleGuide {
    data class Step(val text: String, val building: IsleBuilding)

    fun nextStep(incoming: Int, readyLetters: Int, openQuests: Int, friends: Int): Step? = when {
        incoming == 1 -> Step("Neue Freundschaftsanfrage", IsleBuilding.LIGHTHOUSE)
        incoming > 1 -> Step("$incoming Freundschaftsanfragen", IsleBuilding.LIGHTHOUSE)
        readyLetters == 1 -> Step("Ein Brief ist angekommen", IsleBuilding.POST)
        readyLetters > 1 -> Step("$readyLetters Briefe sind angekommen", IsleBuilding.POST)
        openQuests == 1 -> Step("Eine Quest ist offen", IsleBuilding.HARBOUR)
        openQuests > 1 -> Step("$openQuests Quests sind offen", IsleBuilding.HARBOUR)
        friends == 0 -> null // the empty-sea card already says what to do
        else -> null
    }
}

@Composable
private fun NextStepCard(step: IsleGuide.Step, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(24.dp))
            .background(Color(0xE6FFF8E6)).border(1.dp, Color(0x66B9852E), RoundedCornerShape(18.dp))
            .clickable(onClickLabel = "Öffnen", onClick = onClick).heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(AppIcons.building(step.building), null, tint = Isle.TealDark, size = 20.dp)
        Spacer(Modifier.width(8.dp))
        Text(step.text, Modifier.weight(1f, fill = false), color = Isle.Ink, fontSize = 13.sp, fontFamily = Kit.Body, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 16.sp)
        Spacer(Modifier.width(6.dp))
        AppIcon(R.drawable.ico_chevron, null, tint = Isle.TealDark, size = 22.dp)
    }
}

@Composable
private fun GoldAction(text: String, icon: Int? = null, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFC9A55C)).border(1.dp, Color(0xFF7E6236), RoundedCornerShape(50))
            .clickable(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 20.dp, vertical = 10.dp),
        Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) AppIcon(icon, null, tint = Color(0xFF3B2410), size = 18.dp, modifier = Modifier.padding(end = 8.dp))
            Text(text, color = Color(0xFF3B2410), fontFamily = Kit.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}
