package at.gregor.layermaxxing

import androidx.compose.ui.platform.testTag

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
import androidx.compose.ui.layout.onGloballyPositioned
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
    /** The creative switch is offered in the island menu only where the server allows it (test server + entitlement). */
    creativeEligible: Boolean = false,
    onCreativeSwitch: (Boolean) -> Unit = {},
    changeConstruction: suspend (ApiClient.IslandConstruction, ApiClient.IslandRequest) -> ApiClient.HomeIsland? = { _, _ -> null },
    /** The app's own bottom navigation, drawn unchanged below the island's second row. */
    mainNavigation: (@Composable () -> Unit)? = null,
    onSettings: () -> Unit = { onPlace(IsleBuilding.HOUSE) },
    /** 7.9.13: server + account of [ownId]; with it the last real island is kept for navigation and rotation. */
    islandScope: String? = null,
) {
    val pals = remember(friends) { IsleLayout.friendsOnMap(friends) }
    val infoById = remember(islands) { islands?.friends?.associateBy { it.friendId }.orEmpty() }
    var selectedFriend by rememberSaveable { mutableStateOf<Long?>(null) }
    var newQuestFor by remember { mutableStateOf<Pair<Long?, Long?>?>(null) }
    val openQuests = quests.count { it.completedAt == null }
    // "map" = archipelago, "home" = my island up close, "visit:<id>" = a friend's island.
    var view by rememberSaveable { mutableStateOf(startView) }
    // A building's own place, drawn natively on top of the island (null = none open).
    var place by rememberSaveable { mutableStateOf(startPlace) }
    val owner = ownId?.let { id -> islandScope?.let { IslandOwner(it, id) } }
    // 7.9.13: the last island the server really sent for exactly this server/account/user shows at once.
    var home by remember { mutableStateOf(IslandCache.get(owner)) }
    var load by remember { mutableStateOf(IslandLoad(loading = ownId != null)) }
    val retry = remember { kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED) }
    var visited by remember { mutableStateOf<ApiClient.HomeIsland?>(null) }
    var upgradePick by remember { mutableStateOf<IsleBuilding?>(null) }
    var functionMenu by remember { mutableStateOf(false) }
    var buildBar by remember { mutableStateOf(false) }
    var placement by remember { mutableStateOf<IslandDraft?>(null) }
    var selection by remember { mutableStateOf<IslandTarget?>(null) }
    var demolishTarget by remember { mutableStateOf<IslandTarget?>(null) }
    var landPick by remember { mutableStateOf(false) }
    var constructionBusy by remember { mutableStateOf(false) }
    // The held piece came from the build sheet: cancelling it brings the sheet back.
    var shopDraft by remember { mutableStateOf(false) }
    var creativeHint by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val mode = if (creativeActive) "creative" else "normal"
    val currentOwner by androidx.compose.runtime.rememberUpdatedState(ownId)
    // Creative mode builds on the same real island. Late answers are only checked against the
    // owner: 7.9.3 also compared the ledger mode, so a server answering "normal" (or without a
    // construction block) was discarded forever and the island went dead (CreativeIslandRegressionTest).
    fun currentResponse(response: ApiClient.HomeIsland) = response.userId == currentOwner
    val island = home?.let { it.copy(construction = it.construction.copy(mode = mode)) }
    LaunchedEffect(owner, ownId) {
        if (home?.userId != ownId || home !== IslandCache.get(owner)) {
            // Another owner (or the same user id on another server): never the previous island, only its own cached one.
            home = IslandCache.get(owner)
            load = IslandLoad(loading = ownId != null)
            placement = null; selection = null; buildBar = false; functionMenu = false; upgradePick = null; landPick = false; demolishTarget = null
        }
    }
    // 7.9.11: a fresh island (60 s poll or reload after a refused write) ends a draft whose existing piece was removed
    // meanwhile: said once, never retried, never rebuilt.
    fun adopt(fresh: ApiClient.HomeIsland) {
        home = fresh
        IslandCache.put(owner, fresh)
        val d = placement
        if (d != null && IslandLayout.gone(fresh.construction, d)) { placement = null; shopDraft = false; onError(IslandLayout.GONE) }
    }
    // 7.9.13: one loader at a time, each attempt bounded; a failure keeps what is shown, retries soon
    // (2/5/10/20 s, then the calm 60 s refresh) and says so once in the header, never as a toast flood.
    LaunchedEffect(owner, ownId, islands?.score, creativeActive) {
        val id = ownId ?: return@LaunchedEffect
        while (true) {
            var wait = IslandSync.REFRESH_MS
            if (constructionBusy) wait = IslandSync.RETRY_MS.first()
            else {
                load = load.copy(loading = true)
                IslandSync.attempt { loadIsland(id) }
                    .onSuccess { loaded ->
                        if (!currentResponse(loaded) || currentOwner != id) return@onSuccess
                        if (!constructionBusy && (home == null || loaded.construction.revision >= home!!.construction.revision)) adopt(loaded)
                        load = IslandLoad(confirmed = true)
                    }.onFailure { e ->
                        load = load.copy(loading = false, error = IslandSync.message(e), failures = load.failures + 1)
                        wait = IslandSync.retryDelay(load.failures)
                    }
            }
            // A tap on "Erneut versuchen" or the app coming back to the front ends the pause early.
            kotlinx.coroutines.withTimeoutOrNull(wait) { retry.receive() }
        }
    }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    val latestLoad by androidx.compose.runtime.rememberUpdatedState(load)
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        // Back in front after a failure: try again at once instead of waiting out the pause.
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && latestLoad.error != null) retry.trySend(Unit)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val visitId = view.removePrefix("visit:").toLongOrNull()
    LaunchedEffect(visitId) { visited = null; visitId?.let { id -> visited = runCatching { loadIsland(id) }.getOrNull() } }
    BackHandler(enabled = view != "map" || place != null || placement != null || buildBar || selection != null) {
        when { constructionBusy -> Unit; placement != null -> { placement = null; if (shopDraft) buildBar = true; shopDraft = false }; selection != null -> selection = null; buildBar -> buildBar = false
            place != null -> place = null; else -> view = "map" }
    }
    val homeBadges = mapOf(
        IsleBuilding.HARBOUR to openQuests,
        IsleBuilding.POST to letters.count { it.incoming && it.unlocked && it.readAt == null && opened[it.id] == null },
        IsleBuilding.LIGHTHOUSE to incoming.size,
    )
    fun construct(request: ApiClient.IslandRequest, after: () -> Unit = {}) {
        val original = island ?: return
        if (constructionBusy) return
        if (!load.confirmed) { onError(IslandSync.NOT_CONFIRMED); return }
        if (!original.construction.supported) { onError("Dieser Server kann Inselbauten noch nicht speichern."); return }
        // 7.9.11: a placement is only written under a rule the app knows (never guessed as the newest).
        if (request.position != null) PlacementRule.problem(original.construction.gridVersion)?.let { onError(it); return }
        constructionBusy = true
        scope.launch {
            try {
                val response = changeConstruction(original.construction, request)
                if (response != null && currentResponse(response)) { home = response; IslandCache.put(owner, response); after() }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (currentResponse(original)) {
                    val code = (e as? ApiException)?.code
                    // Invalid or stale writes never save: reload (the targets are recomputed from the fresh island),
                    // keep the local draft for a new choice, unless its piece is gone now (adopt ends it and says so).
                    val before = placement
                    ownId?.let { id -> IslandSync.attempt { loadIsland(id) }.getOrNull()?.let { fresh -> if (currentResponse(fresh)) adopt(fresh) } }
                    // 409 = the island changed meanwhile (another device): said honestly, never retried on its own.
                    if (before == null || placement != null) onError(if (code == 409) ISLAND_CONFLICT else ApiErrors.friendly(e.message))
                    // 422 on a spot the app showed as free: never offer that spot as green again for this draft.
                    val spot = request.position
                    if (code == 422 && spot != null) placement = placement?.let { d -> if (d.position == spot) d.copy(rejected = d.rejected + listOf(spot)) else d }
                }
            } finally { constructionBusy = false }
        }
    }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    // A new piece appears in the middle of what the player sees, not at a fixed corner of the island.
    var cameraRef by remember { mutableStateOf<IslandCamera?>(null) }
    fun screenCentreCell(land: Int): List<Int>? = cameraRef?.viewCenter()?.let { IslandGrid.unproject(it.x, it.y, land) }
    fun startBuilding(b: IsleBuilding, lifted: Boolean, fromShop: Boolean = false) {
        val h = island ?: return
        if (!load.confirmed) { onError(IslandSync.NOT_CONFIRMED); return }
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        selection = null; buildBar = false; upgradePick = null; landPick = false; shopDraft = fromShop
        // A new piece starts in the middle of what the player sees, but never in the water; the harbour on the coast.
        placement = IslandDraft(IslandTarget.Building(b), IslandLayout.startCell(h.construction, b, screenCentreCell(h.construction.land)), lifted)
    }
    /** 7.9.13: the next stage does not fit where it stands: hold that stage and choose its spot; ✓ = one atomic upgrade. */
    fun startUpgradePlacement(b: IsleBuilding) {
        val h = island ?: return
        if (!load.confirmed) { onError(IslandSync.NOT_CONFIRMED); return }
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        selection = null; buildBar = false; upgradePick = null; landPick = false; shopDraft = false
        placement = IslandDraft(IslandTarget.Building(b), IslandLayout.upgradeStart(h.construction, b), lifted = true, upgrade = true)
    }
    fun startDecor(item: String, existing: ApiClient.IslandDecor?, fromShop: Boolean = false) {
        val h = island ?: return
        if (!load.confirmed) { onError(IslandSync.NOT_CONFIRMED); return }
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        selection = null; buildBar = false; shopDraft = fromShop
        val start = existing?.position ?: IslandGrid.clampToLand(screenCentreCell(h.construction.land) ?: listOf(10, 12), h.construction.land)
        val pos = existing?.position ?: IslandLayout.freeCell(start) { IslandLayout.validDecor(h.construction, it, special = SpecialDecor.isSpecial(item), item = item) }
        placement = IslandDraft(IslandTarget.Decor(item, existing?.id), pos, existing != null)
    }
    fun confirmDraft() {
        val d = placement ?: return
        val h = island ?: return
        // A moved piece that is not on the island any more is never written: no "build" instead of "move".
        if (IslandLayout.gone(h.construction, d)) { placement = null; shopDraft = false; onError(IslandLayout.GONE); return }
        val request = when (val t = d.target) {
            // 7.9.9: a new function is built for free at stage 0 ("build"); a standing one only moves.
            is IslandTarget.Building -> ApiClient.IslandRequest(if (d.upgrade) "upgrade" else if (h.construction.standing(t.building)) "move" else "build", t.building.name, position = d.position)
            is IslandTarget.Decor -> if (t.id == null) ApiClient.IslandRequest("decor_place", item = t.item, position = d.position)
                else ApiClient.IslandRequest("decor_move", decorId = t.id, position = d.position)
        }
        construct(request) { placement = null; shopDraft = false }
    }
    fun cancelDraft() { if (!constructionBusy) { placement = null; if (shopDraft) buildBar = true; shopDraft = false } }
    val context = androidx.compose.ui.platform.LocalContext.current
    fun switchCreative(on: Boolean) {
        onCreativeSwitch(on)
        // The first time it is switched on here, say once in one line what it does.
        val prefs = context.getSharedPreferences("island_hints", android.content.Context.MODE_PRIVATE)
        if (on && !prefs.getBoolean("creative_hint_seen", false)) { prefs.edit().putBoolean("creative_hint_seen", true).apply(); creativeHint = true }
    }
    LaunchedEffect(creativeHint) { if (creativeHint) { kotlinx.coroutines.delay(7000); creativeHint = false } }
    // Placement hint: shown when a piece is picked up, gone after a moment.
    var placementHint by remember { mutableStateOf(false) }
    LaunchedEffect(placement?.target) {
        placementHint = placement != null
        if (placement != null) { kotlinx.coroutines.delay(2600); placementHint = false }
    }

    IsleTypography {
    val sea = rememberSeaBrush()
    Box(Modifier.fillMaxSize().background(sea)) {
        AnimatedIslandSea(Modifier.fillMaxSize())
        when {
            view == "home" -> {
                val land = island?.construction?.land ?: 0
                // Own island opens zoomed in; zooming out stops well before overview size.
                val initialZoom = (.82f / IslandPlans.constructionScale(land)).coerceIn(1.15f, 1.85f)
                CloseIsland(
                    title = "Meine Insel", subtitle = "",
                    placementActive = placement != null,
                    zoom = IslandZoom(initialZoom, (initialZoom * .82f).coerceAtLeast(1f), 3f),
                    // Only the app navigation sits below the island now; the side buttons float over the sea.
                    bottomReserve = if (mainNavigation != null) 84.dp else 24.dp,
                    focus = selection?.takeIf { placement == null }?.let { t ->
                        when (t) {
                            is IslandTarget.Building -> IslandPlans.placedBuildings(0, island?.construction).get(t.building)?.let { CameraFocus(it.x, it.y, it.size) }
                            is IslandTarget.Decor -> island?.construction?.decor?.firstOrNull { it.id == t.id }?.let { d -> IslandGrid.project(d.position, land).let { (x, y) -> CameraFocus(x, y, IsleDecor.share(d.item)) } }
                        }
                    },
                    onCamera = { cameraRef = it },
                    onEmptyTap = { if (placement == null) { selection = null; buildBar = false } },
                    header = {
                        // Header and placement hint share one fixed box: nothing below them ever jumps.
                        val headerAlpha by androidx.compose.animation.core.animateFloatAsState(if (placement != null) 0f else 1f,
                            tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "header")
                        Box(contentAlignment = Alignment.Center) {
                            Column(Modifier.graphicsLayer { alpha = headerAlpha }, horizontalAlignment = Alignment.CenterHorizontally) {
                            val loadLine = IslandSync.status(island != null, ownId != null, load)
                            MyIslandHeader(
                                "Meine Insel",
                                island?.construction?.let { c ->
                                    // 7.9.12: a layout saved under an older rule may have pieces standing inside each other:
                                    // kept as it is (never moved on its own), said here so the owner can move them apart.
                                    // 7.9.13 (rule 6): only real ground collisions count; a roof over a neighbour is wanted.
                                    val overlaps = remember(c) { IslandLayout.overlaps(c).size }
                                    "Landstufe ${c.land} · ${c.available} Punkte zum Bauen" +
                                        (if (overlaps > 0) " · $overlaps× ${if (IslandLayout.ground(c)) "teilen sich Teile Boden" else "stehen Teile ineinander"} – lang drücken und verschieben" else "") +
                                        (if (island != null && loadLine != null) " · $loadLine" else "")
                                } ?: loadLine.orEmpty(),
                                creativeActive,
                            )
                            // 7.9.13: a failed load says so once here and offers a retry; the island stays as it is.
                            if (load.error != null && ownId != null) IslandRetryChip(island != null, load.error!!) { retry.trySend(Unit) }
                            }
                            // While the spot is impossible the hint says why and stays (e.g. "Hafen nur an der Küste").
                            val reason = placement?.let { d -> island?.let { IslandLayout.reason(IslandLayout.editing(it, d), d) } }
                            val harbour = (placement?.target as? IslandTarget.Building)?.building == IsleBuilding.HARBOUR
                            androidx.compose.animation.AnimatedVisibility(placement != null && (placementHint || reason != null),
                                enter = androidx.compose.animation.fadeIn(tween(200)) + androidx.compose.animation.slideInVertically(tween(240, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { -it / 2 },
                                exit = androidx.compose.animation.fadeOut(tween(260))) {
                                PlacementHint(reason ?: if (harbour) (if (island?.construction?.let(IslandLayout::ground) == true) "Hafen in die Bucht · Strand und Wasserkante" else "Hafen an die Küste · blaue Randfelder")
                                    else "Ziehen oder tippen · Grün = frei, Rot = belegt",
                                    warning = reason != null)
                            }
                        }
                    },
                ) {
                    val sel = selection
                    val h = island
                    val selectionBar: (@Composable () -> Unit)? = if (h == null || placement != null || place != null) null else when (sel) {
                        is IslandTarget.Building -> {
                            val b = sel.building; val level = h.construction.level(b)
                            ({
                                // 7.9.9: no "Verschieben" here any more: moving is the long press on the piece.
                                PieceActionBar(IslandStages.of(b, level).name, IslandStages.stageLabel(level), listOf(
                                    SelectionAction("Öffnen", IslandStages.of(b, level).icon, tone = Color(0xFF3F7C78)) { selection = null; place = b.name },
                                    SelectionAction(if (level < 3) "Ausbauen" else "Info", if (level < 3) R.drawable.ico_growth else R.drawable.ico_info,
                                        detail = IslandBuild.upgradeCost(h.construction, level), tone = Color(0xFFE2A93C), dark = true) { selection = null; upgradePick = b },
                                    SelectionAction("Abreißen", R.drawable.ico_delete, tone = IslandBuild.No) { demolishTarget = sel },
                                ), level = level)
                            })
                        }
                        is IslandTarget.Decor -> {
                            val d = h.construction.decor.firstOrNull { it.id == sel.id }
                            ({
                                PieceActionBar(d?.let(IsleDecor::name) ?: IsleDecor.labels[sel.item] ?: sel.item, if (SpecialDecor.isSpecial(sel.item)) "Besondere Deko" else "Deko", listOf(
                                    SelectionAction("Entfernen", R.drawable.ico_delete, tone = IslandBuild.No) { demolishTarget = sel },
                                ))
                            })
                        }
                        null -> null
                    }
                    HubIsland(
                        decor = island?.decor.orEmpty(), modifier = Modifier.fillMaxWidth(), seed = ownId ?: 1L,
                        badges = homeBadges,
                        onBuilding = { b -> selection = IslandTarget.Building(b); buildBar = false },
                        onBuildingLongPress = { b -> startBuilding(b, true) },
                        onDecor = { d ->
                            if (island?.construction?.supported == true) { selection = IslandTarget.Decor(d.item, d.id); buildBar = false }
                            else onError("Dieser Server kann Deko noch nicht frei platzieren.")
                        },
                        onDecorLongPress = { d -> startDecor(d.item, d) },
                        // 7.9.13: an upgrade draft sees (and draws) the held building at its next stage
                        life = island?.let { IslandLayout.preview(IslandLayout.editing(it, placement), placement) },
                        placementValid = placement?.let { d -> island?.let { IslandLayout.valid(IslandLayout.editing(it, d), d) } } ?: true,
                        // 7.9.10: every other piece is outlined in quiet red while one is held; never at rest.
                        obstacles = remember(island?.construction, placement?.target, placement?.upgrade) {
                            placement?.let { d -> island?.let { IslandLayout.obstacles(IslandLayout.editing(it, d).construction, d.target) } }.orEmpty()
                        },
                        // 7.9.11: only the cells where the held piece may really stand are drawn (same rule as ✓).
                        placementTargets = remember(island?.construction, placement?.target, placement?.rejected, placement?.upgrade) {
                            placement?.let { d -> island?.let { IslandLayout.targets(IslandLayout.editing(it, d).construction, d) } }.orEmpty()
                        },
                        showFigure = placement == null,
                        placement = placement, onPosition = { p -> if (!constructionBusy) placement = placement?.copy(position = p) },
                        placementBusy = constructionBusy, onConfirm = { confirmDraft() }, onCancel = { cancelDraft() },
                        selected = selection,
                        // A tap on empty ground closes whatever is open (actions or the build sheet).
                        onGround = if (selection != null || buildBar) ({ selection = null; buildBar = false }) else null,
                        selectionBar = selectionBar,
                        onFigure = { functionMenu = true },
                    )
                }
            }
            visitId != null -> {
                val friend = pals.firstOrNull { it.id == visitId }
                CloseIsland(title = "Insel von ${friend?.name ?: "…"}", subtitle = "Du bist zu Besuch", bottomReserve = 150.dp) {
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
        val step = if (place != null || view != "map") null
            else IsleGuide.nextStep(incoming = incoming.size, readyLetters = ready, openQuests = openQuests, friends = pals.size)
        // While a piece is held, every other control steps aside (only the hint at the top and ✓/✗ at the piece remain).
        androidx.compose.animation.AnimatedVisibility(placement == null,
            enter = androidx.compose.animation.fadeIn(tween(220)), exit = androidx.compose.animation.fadeOut(tween(180))) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (view == "home") {
                // 7.9.9: a clearly visible painted plate back to the overview (Android back does the same).
                OverviewBackPlate({ if (!constructionBusy) { selection = null; buildBar = false; place = null; view = "map" } })
                Spacer(Modifier.width(8.dp))
            } else {
                Pill(Modifier.clip(RoundedCornerShape(50)).clickable(onClickLabel = "Zurück") {
                    if (!constructionBusy) { if (view != "map") view = "map" else onBack() }
                }.semantics { contentDescription = "Zurück" }) {
                    AppIcon(R.drawable.ico_back, null, tint = Color(0xFFFFF8E6), size = 22.dp)
                }
                Spacer(Modifier.width(8.dp))
            }
            // On the map the HUD's middle is the "what now?" hint; without one, it says who I am.
            if (step != null) NextStepCard(step, Modifier.weight(1f)) { place = step.building.name }
            else {
                if (view == "map") Pill { Text("$ownEmoji  $ownName", color = Color(0xFFFFF8E6), maxLines = 1) }
                Spacer(Modifier.weight(1f))
            }
            // Points and letters are always one tap away; at zero they only fade back.
            Pill(Modifier.padding(start = 6.dp).then(Modifier.clip(RoundedCornerShape(50)).clickable(onClickLabel = "Punkte ansehen") { if(placement==null && !constructionBusy) onPoints() }.semantics { contentDescription = "$qp Punkte" })) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(R.drawable.ico_points, null, tint = Color(0xFFE6D3A3).copy(alpha = if (qp > 0) 1f else .6f), size = 18.dp)
                    Text(" $qp", color = Color(0xFFFFF8E6).copy(alpha = if (qp > 0) 1f else .6f))
                }
            }
            Spacer(Modifier.width(6.dp))
            Pill(Modifier.clip(RoundedCornerShape(50)).clickable(onClickLabel = "Briefe öffnen") { if(placement==null && !constructionBusy) place = IsleBuilding.POST.name }.semantics { contentDescription = if (ready > 0) "$ready neue Briefe" else "Posteingang" }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(R.drawable.ico_letter, null, tint = Color(0xFFFFF8E6).copy(alpha = if (ready > 0) 1f else .6f), size = 18.dp)
                    if (ready > 0) Text(" $ready", color = Color(0xFFFFF8E6))
                }
            }
        }
        }
        // The overview is labelled as such, so it never reads like the own island.
        if (view == "map" && place == null) WorldText("Übersicht · alle Inseln", 13.sp, display = false, fill = Color(0xFFE6D3A3),
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 68.dp))
        if (pals.isEmpty() && view == "map") {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp).padding(top = 260.dp)
                    .shadow(6.dp, RoundedCornerShape(22.dp)).background(Isle.Card, RoundedCornerShape(22.dp)).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Noch ruhig auf dem Meer", fontWeight = FontWeight.Bold, color = Isle.Ink, fontSize = 17.sp)
                Text("Sobald du Freunde hast, taucht für jeden eine eigene Insel auf.", color = Isle.Muted, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                IsleButton("Freunde finden", onPeople)
            }
        }
        // Bottom: context (placement / selection / build shelf), the island's own second row,
        // and below it the app's unchanged main navigation.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
            if (view == "home") {
                // One context at a time above the navigation, in one shared container (no stacking, no jumps):
                // the narrow side buttons at rest, the slim build sheet while shopping, nothing while a piece is held.
                val ease = androidx.compose.animation.core.FastOutSlowInEasing
                val h = island
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    androidx.compose.animation.AnimatedVisibility(h != null && place == null && placement == null && !buildBar,
                        modifier = Modifier.align(Alignment.BottomEnd),
                        enter = androidx.compose.animation.fadeIn(tween(220, easing = ease)) + androidx.compose.animation.slideInVertically(tween(240, easing = ease)) { it / 4 },
                        exit = androidx.compose.animation.fadeOut(tween(160, easing = ease)) + androidx.compose.animation.slideOutVertically(tween(180, easing = ease)) { it / 4 },
                    ) {
                        IslandSideActions(
                            shopLine = h?.let { IslandBuild.shopLine(it.construction) } ?: "",
                            onBuild = { place = null; selection = null; buildBar = true },
                            onMenu = { functionMenu = true },
                            modifier = Modifier.padding(end = 10.dp, bottom = 4.dp),
                        )
                    }
                    androidx.compose.animation.AnimatedVisibility(h != null && place == null && placement == null && buildBar,
                        enter = androidx.compose.animation.slideInVertically(tween(260, easing = ease)) { it } + androidx.compose.animation.fadeIn(tween(200, easing = ease)),
                        exit = androidx.compose.animation.slideOutVertically(tween(200, easing = ease)) { it } + androidx.compose.animation.fadeOut(tween(160, easing = ease)),
                    ) {
                        if (h != null) IslandBuildBar(h,
                            onBuilding = { b ->
                                // Standing: select it (its actions show the upgrade); otherwise its stage 0 is free.
                                if (h.construction.standing(b)) { buildBar = false; selection = IslandTarget.Building(b) }
                                else startBuilding(b, false, fromShop = true)
                            },
                            onDecor = { item ->
                                val c = h.construction
                                when {
                                    !c.supported -> onError("Dieser Server kann Deko noch nicht frei platzieren.")
                                    SpecialDecor.isSpecial(item) && SpecialDecor.full(c) -> onError("Höchstens ${c.specialLimit} besondere Deko pro Insel")
                                    SpecialDecor.isSpecial(item) && !SpecialDecor.affordable(c, item) ->
                                        onError("Für ${SpecialDecor.label(item)} fehlen ${SpecialDecor.cost(c, item) - c.available} Punkte")
                                    SpecialDecor.isSpecial(item) -> startDecor(item, null, fromShop = true)
                                    IslandBuild.decorFull(h) -> onError("Für mehr Deko zuerst die Insel erweitern")
                                    !IslandBuild.decorUnlocked(h, item) -> onError("${IsleDecor.labels[item] ?: item} wird mit Punkten freigeschaltet")
                                    else -> startDecor(item, null, fromShop = true)
                                }
                            },
                            onLand = { buildBar = false; landPick = true },
                            onClose = { buildBar = false })
                    }
                }
            } else IslandActionRow {
                when {
                    visitId != null -> {
                        IslandRowItem(R.drawable.ico_map, "Übersicht") { view = "map" }
                        IslandRowItem(R.drawable.ico_island, "Meine Insel") { view = "home" }
                    }
                    else -> {
                        IslandRowItem(R.drawable.ico_menu, "Menü") { functionMenu = true }
                    }
                }
            }
            if (mainNavigation != null) mainNavigation() else Spacer(Modifier.navigationBarsPadding().height(8.dp))
        }
        androidx.compose.animation.AnimatedVisibility(creativeHint && view == "home" && placement == null,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 160.dp),
            enter = androidx.compose.animation.fadeIn(tween(220)) + androidx.compose.animation.slideInVertically(tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { -it / 3 },
            exit = androidx.compose.animation.fadeOut(tween(200))) {
            CreativeHintCard(onDismiss = { creativeHint = false })
        }
        val openPlace = place?.let { key -> IsleBuilding.entries.firstOrNull { it.name == key } }
        androidx.compose.animation.AnimatedVisibility(
            visible = openPlace != null,
            enter = androidx.compose.animation.slideInVertically { it / 3 } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { it / 3 } + androidx.compose.animation.fadeOut(),
        ) {
            val close = { place = null }
            // 7.9.9: every place shows the stage it really has (stage 0 when it does not stand yet).
            fun stageOf(b: IsleBuilding) = island?.construction?.level(b) ?: 0
            when (openPlace ?: IsleBuilding.HOUSE) {
                IsleBuilding.POST -> PostOffice(
                    pals = pals, letters = letters, opened = opened,
                    onOpen = onOpenLetter, onLocked = onLockedTap, onWrite = onComposeLetter, onClose = close, level = stageOf(IsleBuilding.POST),
                )
                IsleBuilding.HARBOUR -> HarbourBoard(
                    quests = quests, topics = topics, onQuestDone = onQuestDone, onQuestDelete = onQuestDelete,
                    onNewQuest = { newQuestFor = null to null }, onAllTopics = onAllTopics, onClose = close, level = stageOf(IsleBuilding.HARBOUR),
                    arrivals = homeBadges[IsleBuilding.POST] ?: 0, onArrivals = { place = IsleBuilding.POST.name },
                )
                IsleBuilding.LIGHTHOUSE -> LighthouseView(
                    pals = pals, levels = infoById.mapValues { it.value.level }, incoming = incoming, outgoing = outgoing,
                    onRespond = onRespondFriend, onFriend = { selectedFriend = it }, onFind = onPeople, onClose = close, level = stageOf(IsleBuilding.LIGHTHOUSE),
                )
                IsleBuilding.LIBRARY -> LibraryBook(onClose = close, glossary = glossary, level = stageOf(IsleBuilding.LIBRARY))
                IsleBuilding.CAMPFIRE -> CommunityHall(
                    groups = groups, quests = quests, onGroupQuest = { newQuestFor = null to it },
                    onManage = { onPlace(IsleBuilding.CAMPFIRE) }, onClose = close, level = stageOf(IsleBuilding.CAMPFIRE),
                    onPlans = { place = IsleBuilding.HARBOUR.name },
                )
                IsleBuilding.HOUSE -> MyHouse(
                    name = ownName, emoji = ownEmoji, color = ownColor, friends = pals.size,
                    questsDone = quests.count { it.completedAt != null },
                    letters = letters.size, qp = islands?.qp ?: 0,
                    onSettings = { onPlace(IsleBuilding.HOUSE) },
                    onEdit = { place = null; view = "home"; selection = null; buildBar = true }, onClose = close, level = stageOf(IsleBuilding.HOUSE),
                )
            }
        }
    }

    if (functionMenu) ModalBottomSheet(onDismissRequest = { functionMenu = false }, containerColor = Isle.Card,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        IslandMenuContent(island,
            onOpen = { b -> functionMenu = false; view = "home"; place = b.name },
            onSettings = { functionMenu = false; onSettings() },
            onOverview = { functionMenu = false; place = null; selection = null; buildBar = false; view = if (view == "map") "home" else "map" },
            overviewLabel = if (view == "map") "Meine Insel" else "Übersicht",
            onBuild = { functionMenu = false; place = null; view = "home"; selection = null; buildBar = true },
            creativeEligible = creativeEligible, creative = creativeActive,
            // The switch acts at once and the menu closes, so the chip and the "gratis" prices are seen right away.
            onCreative = { on -> functionMenu = false; switchCreative(on) })
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
    if ((upgradePick != null || landPick) && island != null) {
        val h = island
        val building = upgradePick
        ModalBottomSheet(onDismissRequest = { if (!constructionBusy) { upgradePick = null; landPick = false } },
            containerColor = Isle.Card, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            IslandUpgradeContent(building, h, constructionBusy,
                onAction = { action ->
                    if (action == "upgrade" && building != null && !h.construction.standing(building)) startBuilding(building, false)
                    // 7.9.12: the bigger stage does not fit where it stands: never moved on its own. 7.9.13: the owner holds
                    // the next stage and picks its spot (e.g. the pier at the water), written as one upgrade.
                    else if (action == "move" && building != null) startUpgradePlacement(building)
                    else construct(ApiClient.IslandRequest(action, building?.name))
                }, onDismiss = { if (!constructionBusy) { upgradePick = null; landPick = false } })
        }
    }
    demolishTarget?.let { target ->
        val h = island
        if (h == null) demolishTarget = null else when (target) {
            is IslandTarget.Building -> DemolishDialog(target.building.label, creativeActive, "Das Gebäude",
                onConfirm = { demolishTarget = null; selection = null; construct(ApiClient.IslandRequest("demolish", target.building.name)) },
                onDismiss = { demolishTarget = null })
            is IslandTarget.Decor -> DemolishDialog(h.construction.decor.firstOrNull { it.id == target.id }?.let(IsleDecor::name) ?: IsleDecor.labels[target.item] ?: target.item, creativeActive, "Die Deko",
                onConfirm = { demolishTarget = null; selection = null; construct(ApiClient.IslandRequest("decor_remove", decorId = target.id)) },
                onDismiss = { demolishTarget = null })
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
        // 7.9.12: every island's berth from its CURRENT construction (HarbourRoute), in map px. The hub box is 180 dp wide
        // at (w/2 − 90 dp, h/2 − 75.6 dp), a friend's box ownerWidth wide at (x − w/2, y − w/2): exactly where they are drawn.
        val hubPx = with(density) { 180.dp.toPx() }
        val hubTopLeft = Offset(center.x - hubPx / 2, center.y - hubPx * .42f)
        val homeConstruction = homeLife?.construction
        val friendBox = ordered.mapIndexed { i, f ->
            val fw = with(density) { Isle.ownerWidth(infoById[f.id]?.island).toPx() }
            Offset(points[i].x - fw / 2, points[i].y - fw * .5f) to fw
        }
        // 7.9.13: every island's coast as it is painted (outline + rocks in the water), at its land stage and drawn box;
        // a boat is its physical hull ([hull]); it lies and sails only where the whole hull has water, [keel] is the
        // sampling step (badges and plaques are no hull).
        val hull = with(density) { 12.dp.toPx() }; val keel = with(density) { 4.dp.toPx() }
        val homeLand = (homeConstruction?.land ?: 0).coerceIn(0, 5)
        val friendLands = ordered.map { f -> (infoById[f.id]?.island?.construction?.land ?: 0).coerceIn(0, 5) }
        val coasts = remember(friendBox, friendLands, hubTopLeft, hubPx, homeLand) {
            friendBox.mapIndexed { i, (tl, fw) -> SeaChart.coastOf(tl, fw, friendLands[i]) } + SeaChart.coastOf(hubTopLeft, hubPx, homeLand)
        }
        // the berth from the CURRENT construction; a v6 harbour on a real socket of its stage, or a legacy one at most three grid cells short of the water it moors at the nearest
        // water. An old harbour standing further inland is honestly marked for repair and its boats use the island's real
        // landing stage in the cove (SeaChart.moor), never a far coast point passed off as its harbour.
        val rawHome = HarbourRoute.onMap(hubTopLeft, hubPx, HarbourRoute.berth(homeConstruction))
        val rawFriends = ordered.mapIndexed { i, f -> HarbourRoute.onMap(friendBox[i].first, friendBox[i].second, HarbourRoute.berth(infoById[f.id]?.island?.construction)) }
        fun moorOf(raw: Offset, tl: Offset, px: Float, land: Int, c: ApiClient.IslandConstruction?) =
            SeaChart.moor(raw, HarbourRoute.onMap(tl, px, HarbourRoute.fallbackBerth(land)), coasts, keel, HarbourRoute.LEGACY_REACH_CELLS * IslandGrid.step(land) * px,
                HarbourRoute.atSocket(c), hull) ?: SeaChart.Mooring(raw, true)
        val friendConstructions = ordered.map { infoById[it.id]?.island?.construction }
        val homeMoor = remember(rawHome, coasts, keel, homeConstruction) { moorOf(rawHome, hubTopLeft, hubPx, homeLand, homeConstruction) }
        val friendMoors = remember(rawFriends, coasts, keel, friendConstructions) {
            rawFriends.mapIndexed { i, r -> moorOf(r, friendBox[i].first, friendBox[i].second, friendLands[i], friendConstructions[i]) }
        }
        val homeBerth = homeMoor.point
        val friendBerths = friendMoors.map { it.point }
        val repairs = (if (homeMoor.repair && HarbourRoute.hasHarbour(homeConstruction)) setOf(0L) else emptySet()) +
            ordered.filterIndexed { i, f -> friendMoors[i].repair && HarbourRoute.hasHarbour(infoById[f.id]?.island?.construction) }.map { it.id }
        AnimatedIslandSea(Modifier.fillMaxSize(), oversized = true)
        // the map's own coordinates (same layer as islands and boats): name plates are measured in them
        var mapCoords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
        val plateBase = remember { androidx.compose.runtime.mutableStateMapOf<Long, androidx.compose.ui.geometry.Rect>() }
        Spacer(Modifier.fillMaxSize().onGloballyPositioned { mapCoords = it })
        // One lane per boat: each lane is the route shifted sideways in its open-water middle, so boats sail side by
        // side instead of stacking and still end at the berths.
        val laneGap = with(density) { 46.dp.toPx() }
        // Obstacles a boat (with its time tag) must not sit on: every island incl. its name plaque, the screen edge and other boats.
        val boatR = with(density) { 30.dp.toPx() }
        val islands = ordered.mapIndexed { i, f ->
            val wPx = with(density) { Isle.ownerWidth(infoById[f.id]?.island).toPx() }
            Offset(points[i].x, points[i].y + wPx * .1f) to wPx * .45f
        } + (Offset(center.x, center.y + hubShorePx * .2f) to hubShorePx * .9f)
        val edge = with(density) { 40.dp.toPx() }
        val mapW = with(density) { w.toPx() }; val mapH = with(density) { h.toPx() }
        // Water ways only, inside the map. null = no water way (islands closing every gap): nothing is drawn for that
        // friend rather than a line through land; the letters stay in the mail lists.
        // A boat is drawn as a box hullW × hullH around its point (hull, badge, "+N"): the whole box stays on the map
        // (verified by IslandMapRouteUiTest).
        val hullHalfW = with(density) { 42.dp.toPx() }; val hullHalfH = with(density) { 21.dp.toPx() }
        fun hullOf(p: Offset) = androidx.compose.ui.geometry.Rect(p.x - hullHalfW, p.y - hullHalfH, p.x + hullHalfW, p.y + hullHalfH)
        val seaBounds = androidx.compose.ui.geometry.Rect(0f, 0f, mapW, mapH)
        // routes and lanes run where a boat's whole box fits: the sea inset by half a hull on every side (not just its centre point)
        val boatSea = androidx.compose.ui.geometry.Rect(hullHalfW, hullHalfH, mapW - hullHalfW, mapH - hullHalfH)
        // 7.9.13: the water ways depend only on where islands and berths are, never on the boats' progress or the
        // clock: computed once per geometry, not again on every minute tick or refresh (that stalled the first frames).
        // They follow the painted coasts and rocks (SeaChart), smoothed only where the smooth curve is checked water.
        val field = remember(coasts, boatSea, hull) { SeaChart.Field(coasts, boatSea, maxOf(hull * .6f, 4f)) }
        val routes = remember(homeBerth, friendBerths, field) {
            ordered.indices.map { i -> SeaChart.route(homeBerth, friendBerths[i], field, hull, keel) }
        }
        // a boat's lane is geometry too: checked once per route and shift, never per tick
        val laneCache = remember(routes, field) { mutableMapOf<Pair<Int, Float>, List<Offset>>() }
        val placed = mutableListOf<Offset>()
        // 7.9.13: the "Warteliegeplatz" plates (same rect as drawn below) are never exempt, not even at a route's end.
        val waitPlates = with(density) {
            ordered.indices.filter { ordered[it].id in repairs }.map { val b = friendBerths[it]
                ordered[it].id to androidx.compose.ui.geometry.Rect(b.x - 46.dp.toPx(), b.y - 30.dp.toPx(), b.x + 46.dp.toPx(), b.y - 12.dp.toPx()) }
        }
        val stagePlates = waitPlates.map { it.second }
        // 7.9.13: every friend's name plate as measured (unshifted, map px): never exempt either, not even at the boat's own end.
        val namePlates = ordered.mapNotNull { f -> plateBase[f.id]?.let { f.id to it } }
        fun clashes(p: Offset, ends: Set<Int>): Float {
            var c = 0f
            val h = hullOf(p)
            namePlates.forEach { (_, r) -> if (h.overlaps(r)) c += boatR * 20 }
            stagePlates.forEach { r -> if (p.x > r.left - boatR && p.x < r.right + boatR && p.y > r.top - boatR && p.y < r.bottom + boatR) c += boatR * 20 }
            islands.forEachIndexed { k, (o, r) -> if (k !in ends) { val d = (p - o).getDistance(); if (d < r + boatR) c += r + boatR - d } }
            placed.forEach { o -> if (kotlin.math.abs(p.x - o.x) < boatR * 3f && kotlin.math.abs(p.y - o.y) < boatR * 2.8f) c += boatR * 20 }
            if (p.x < edge * 1.2f || p.x > mapW - edge * 1.2f) c += boatR * 4
            if (p.y < edge * 3 || p.y > mapH - edge * 3) c += boatR * 4
            return c
        }
        val voyages = ordered.flatMapIndexed { i, friend ->
            val route = routes[i] ?: return@flatMapIndexed emptyList()
            val all = transit.filter { it.peerId == friend.id }.sortedBy { it.createdAt }
            // Close neighbours have little open water: fewer lanes there; the rest wait in the outermost lane as "+N".
            val mine = all.take(IsleMotion.lanesThatFit(HarbourRoute.length(route) * .5f, laneGap))
            val extra = all.size - mine.size
            val lanes = IsleMotion.laneOffsets(mine.size)
            mine.mapIndexed { k, letter ->
                val share = IsleMotion.tripShare(letter, nowSec)
                val t = HarbourRoute.boatT(letter.incoming, share)
                // a boat at its berth belongs to that island: the two end islands never count as clashing
                val ends = setOf(i, islands.lastIndex)
                fun voyage(side: Float) = Voyage(friend, letter, laneCache.getOrPut(i to side) { SeaChart.lane(route, side, field, hull, keel) }, t, share, if (k == mine.lastIndex) extra else 0)
                val candidates = IsleMotion.laneCandidates(lanes[k]).map { it * laneGap }.filter { kotlin.math.abs(it) <= laneGap * 2.2f }.ifEmpty { listOf(lanes[k] * laneGap) }
                val best = candidates.map { voyage(it) }.let { list -> list.firstOrNull { clashes(it.boat, ends) == 0f } ?: list.minBy { clashes(it.boat, ends) } }
                placed += best.boat
                best
            }
        }
        // A plate a boat still covers (no free lane) moves, the boats keep their real course: the nearest free spot by
        // its island/berth that no hull and no other plate covers and that stays whole on the map. Waiting plates first
        // (they belong right at the berth), then the name plates around them.
        val hulls = voyages.map { hullOf(it.boat) }
        val taken = mutableListOf<androidx.compose.ui.geometry.Rect>()
        val gap = with(density) { 6.dp.toPx() }; val touch = with(density) { 3.dp.toPx() }
        fun settle(r: androidx.compose.ui.geometry.Rect, others: List<androidx.compose.ui.geometry.Rect>, up: Boolean): Offset {
            fun free(s: Offset) = r.translate(s).let { q ->
                q.left >= 0f && q.top >= 0f && q.right <= mapW && q.bottom <= mapH &&
                    // plates may touch each other (no real cover), never a hull
                    hulls.none { it.overlaps(q) } && q.deflate(touch).let { t -> taken.none { it.overlaps(t) } && others.none { it.overlaps(t) } }
            }
            val dx = r.width * .5f + hullHalfW + gap; val dy = (hullHalfH * 2 + gap) * if (up) -1 else 1
            val options = listOf(Offset.Zero, Offset(-dx, 0f), Offset(dx, 0f), Offset(0f, dy), Offset(-dx, dy), Offset(dx, dy), Offset(0f, dy * 2),
                Offset(-dx, dy * 2), Offset(dx, dy * 2))
            val s = options.firstOrNull(::free) ?: options.minBy { s -> hulls.count { it.overlaps(r.translate(s)) } }
            taken += r.translate(s)
            return s
        }
        val waitShift = waitPlates.associate { (id, r) -> id to settle(r, namePlates.map { it.second }, up = true) }
        val plateShift = namePlates.associate { (id, r) -> id to settle(r, namePlates.filter { it.first != id }.map { it.second }, up = false) }
        val busy = voyages.map { it.friend.id }.toSet()
        val jettyW = with(density) { 7.dp.toPx() }
        Canvas(Modifier.fillMaxSize().semantics {
            set(MapRoutesKey, ordered.mapIndexedNotNull { i, f -> routes[i]?.let { MapRoute(f.id, null, it, null) } } + voyages.map { v -> MapRoute(v.friend.id, v.letter.id, v.path, v.boat, hullOf(v.boat)) })
            set(MapAreaKey, seaBounds)
            set(MapBerthsKey, listOf(0L to homeBerth) + ordered.mapIndexed { i, f -> f.id to friendBerths[i] })
            set(MapRepairKey, repairs)
            set(MapCoastsKey, coasts)
        }) {
            // 7.9.13: a rowboat on the beach: the short walk down to where the boats moor, drawn as a sandy dotted path.
            fun walk(from: Pair<Float, Float>?, tl: Offset, w: Float, to: Offset) = from?.let {
                drawLine(Color(0xCCE6D3A3), HarbourRoute.onMap(tl, w, it), to, 2.2f, StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(2f, 5f), 0f))
            }
            walk(HarbourRoute.beachWalk(homeConstruction), hubTopLeft, hubPx, homeBerth)
            ordered.forEachIndexed { i, f -> walk(HarbourRoute.beachWalk(infoById[f.id]?.island?.construction), friendBox[i].first, friendBox[i].second, friendBerths[i]) }
            // A visible landing stage wherever an island has no harbour yet (old data): routes never end in nothing.
            if (!HarbourRoute.hasHarbour(homeConstruction) || 0L in repairs) {
                val land = (homeConstruction?.land ?: 0).coerceIn(0, 5)
                drawIslandJetty(homeBerth, HarbourRoute.onMap(hubTopLeft, hubPx, HarbourRoute.fallbackRoot(land)), jettyW)
            }
            ordered.forEachIndexed { i, f ->
                val c = infoById[f.id]?.island?.construction
                if (!HarbourRoute.hasHarbour(c) || f.id in repairs) drawIslandJetty(friendBerths[i], HarbourRoute.onMap(friendBox[i].first, friendBox[i].second,
                    HarbourRoute.fallbackRoot((c?.land ?: 0).coerceIn(0, 5))), jettyW)
            }
            fun line(points: List<Offset>) = androidx.compose.ui.graphics.Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) } }
            val round = androidx.compose.ui.graphics.StrokeJoin.Round
            // 7.9.13: calm sea lanes: a quiet dotted line where no boat sails, no progress "highways".
            routes.forEachIndexed { i, r ->
                if (r == null || ordered[i].id in busy) return@forEachIndexed
                drawPath(line(r), Color.White.copy(alpha = .30f), style = Stroke(2.5f, cap = StrokeCap.Round, join = round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 15f), 0f)))
            }
            // Each boat: the water ahead faintly dotted, a short soft wake behind it (the real share decides where it is).
            voyages.forEach { v ->
                val dots = PathEffect.dashPathEffect(floatArrayOf(3f, 13f), 0f)
                val ahead = if (v.letter.incoming) HarbourRoute.part(v.path, 0f, v.t) else HarbourRoute.part(v.path, v.t, 1f)
                drawPath(line(ahead), Color.White.copy(alpha = .32f), style = Stroke(2.5f, cap = StrokeCap.Round, pathEffect = dots))
                if (v.share != null) {
                    // the wake: the last stretch behind the boat, fading out (sailed = sender's berth → boat)
                    val len = HarbourRoute.length(v.path).coerceAtLeast(1f)
                    val wake = (hull * 5f / len).coerceAtMost(.25f)
                    val (w0, w1) = if (v.letter.incoming) v.t to (v.t + wake).coerceAtMost(1f) else (v.t - wake).coerceAtLeast(0f) to v.t
                    for (k in 0 until 4) {
                        val a = w0 + (w1 - w0) * k / 4f; val b = w0 + (w1 - w0) * (k + 1) / 4f
                        val nearBoat = if (v.letter.incoming) 1f - k / 4f else (k + 1) / 4f
                        drawPath(line(HarbourRoute.part(v.path, a, b, 6)), Color.White.copy(alpha = .10f + .32f * nearBoat),
                            style = Stroke(1.5f + 2.5f * nearBoat, cap = StrokeCap.Round, join = round))
                    }
                } else drawPath(line(if (v.letter.incoming) HarbourRoute.part(v.path, v.t, 1f) else HarbourRoute.part(v.path, 0f, v.t)),
                    Color.White.copy(alpha = .32f), style = Stroke(2.5f, cap = StrokeCap.Round, pathEffect = dots))
                drawCircle(Color(0xB3E6D3A3), 4f, if (v.letter.incoming) v.path.first() else v.path.last())
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
                plateShift = plateShift[friend.id] ?: Offset.Zero,
                onPlate = { c -> mapCoords?.takeIf { it.isAttached && c.isAttached }?.localBoundingBoxOf(c)?.let { r ->
                    if (plateBase[friend.id] != r) plateBase[friend.id] = r } },
            ) { onFriend(friend.id) }
        }
        val hubW = 180.dp
        val plateAbove = homeBerth.y > hubTopLeft.y + hubPx * .5f
        Column(
            Modifier.offset(x = w * .5f - hubW / 2, y = h * .5f - hubW * .42f).width(hubW)
                .worldTap("Meine Insel öffnen", onHome),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The overview shows the same life buildings as the close-up, just without name tags.
            HubIsland(homeDecor, Modifier.fillMaxWidth(), labels = false, seed = ownSeed, life = homeLife, lifeTags = false)
            if (!plateAbove) HubPlate(ownName, Modifier.offset(y = (-8).dp))
        }
        // 7.9.13: boats moor at my berth; a berth on the south coast would put them right over the name plate, so the
        // plate then sits above the island (the side away from the berth). The island and its coast never move.
        if (plateAbove) Box(Modifier.offset(x = w * .5f - hubW / 2, y = h * .5f - hubW * .42f - 14.dp).width(hubW), Alignment.Center) { HubPlate(ownName) }
        // 7.9.13: honest repair need instead of a far coast point (the boats use the cove stage meanwhile); in the open
        // sea above the menu, never over islands, routes or boats.
        if (0L in repairs) Text("Dein Hafen steht im Inland – Boote warten in der Bucht. Hafen an die Küste verschieben", color = Color(0xFFFFE3B0), fontSize = 11.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 3,
            modifier = Modifier.offset(x = w * .5f - 130.dp, y = h - 168.dp).width(260.dp)
                .background(Color(0xE0432A1B), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 3.dp)
                .semantics { contentDescription = "Reparatur nötig: Hafen steht im Inland, Boote warten am Warteliegeplatz in der Bucht, bitte Hafen an die Küste verschieben" })
        // 7.9.13: where a harbour stands inland, boats only reach the landing stage in the cove: said there, visibly,
        // so the stage is never passed off as the harbour. Above the berth (on the beach): below it sit the islands' name
        // plates and the boats.
        // (My own island: its boats lie right at that berth; the repair note below says it instead.)
        ordered.mapIndexed { i, f -> f.id to friendBerths[i] }.filter { it.first in repairs }.forEach { (id, b) ->
            Text("Warteliegeplatz", color = Color(0xFFFFE3B0), fontSize = 10.sp, maxLines = 1,
                modifier = Modifier.offset { val s = waitShift[id] ?: Offset.Zero
                    IntOffset((b.x - 46.dp.toPx() + s.x).roundToInt(), (b.y - 30.dp.toPx() + s.y).roundToInt()) }.width(92.dp)
                    .background(Color(0xD9432A1B), RoundedCornerShape(8.dp)).padding(horizontal = 4.dp, vertical = 1.dp)
                    .semantics { contentDescription = "Warteliegeplatz in der Bucht – kein Hafen (Insel ${ordered.firstOrNull { it.id == id }?.name ?: ""})" },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
                .semantics {
                    // the end of this trip: my berth (incoming) or the friend's; a repair island has only a waiting berth
                    val waiting = (if (v.letter.incoming) 0L else v.friend.id) in repairs
                    contentDescription = "${boat.label} – Brief ${if (v.letter.incoming) "von" else "an"} ${v.friend.name}, ${IsleMotion.seaTime(IsleMotion.arrivalSec(v.letter), nowSec, waiting)}" +
                        if (waiting) ", Ziel: Warteliegeplatz in der Bucht, nicht der Hafen" else ""
                }
            Column(
                Modifier.offset { IntOffset((p.x - 42.dp.toPx()).roundToInt(), (p.y - 21.dp.toPx()).roundToInt()) }.width(84.dp).then(tapped),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { hull() }
        }
        boatSheet?.let { letter ->
            BoatSheet(letter, ordered.firstOrNull { it.id == letter.peerId }?.name ?: "…", nowSec, (if (letter.incoming) 0L else letter.peerId) in repairs) { boatSheet = null }
        }
    }
}

/** The name plate of my island on the map ("<name>  Meine Insel"). */
@Composable private fun HubPlate(ownName: String, modifier: Modifier = Modifier) {
    Row(
        modifier.testTag("HubPlate").background(Color(0xD9243A3F), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WorldText(ownName, 14.sp, plaque = false)
        WorldText("  Meine Insel", 10.sp, fill = Color(0xFFE6D3A3), display = false, plaque = false)
    }
}

/** One boat on its own lane: [path] = home berth → friend berth, [t] where it is now along it, [share] null = waiting in the roadstead. */
private class Voyage(
    val friend: ApiClient.UserSummary, val letter: ApiClient.Message,
    val path: List<Offset>, val t: Float, val share: Float?,
    /** Further boats on this route that did not get a lane of their own. */
    val more: Int = 0,
) {
    val boat: Offset get() = HarbourRoute.at(path, t)
}

/** 7.9.12: a route on the archipelago map in map px (for tests and a11y): idle routes have no letter and no boat. */
internal data class MapRoute(val friendId: Long, val letterId: Long?, val path: List<Offset>, val boat: Offset?,
    /** The boat's drawn box (hull, badge, "+N"), map px. */
    val hull: androidx.compose.ui.geometry.Rect? = null)
/** The map's drawing area, map px. */
internal val MapAreaKey = androidx.compose.ui.semantics.SemanticsPropertyKey<androidx.compose.ui.geometry.Rect>("MapArea")
internal val MapRoutesKey = androidx.compose.ui.semantics.SemanticsPropertyKey<List<MapRoute>>("MapRoutes")
/** 7.9.12: every island's berth on the map (0 = my island). */
/** 7.9.13: islands (0 = mine) whose harbour stands inland and must be moved to the coast; their boats use the cove stage. */
internal val MapRepairKey = androidx.compose.ui.semantics.SemanticsPropertyKey<Set<Long>>("MapRepair")
/** 7.9.13: the painted coasts (outline + rocks) the routes were checked against, for the water tests. */
internal val MapCoastsKey = androidx.compose.ui.semantics.SemanticsPropertyKey<List<SeaChart.Coast>>("MapCoasts")
internal val MapBerthsKey = androidx.compose.ui.semantics.SemanticsPropertyKey<List<Pair<Long, Offset>>>("MapBerths")

internal data class IslandZoom(val initial: Float = 1f, val min: Float = 1f, val max: Float = 2.6f)

/** A tapped piece the camera keeps in sight with its actions: foot (island-box fraction) and sprite size (fraction of the width). */
internal data class CameraFocus(val x: Float, val y: Float, val size: Float)

/**
 * How far the camera pans so a selected piece is centred together with its actions (px, relative to the
 * viewport centre): the sprite above the foot and the name + action pill below it end up in the middle.
 * Taller than the view: its top stays in sight.
 */
internal object FocusPan {
    fun shift(foot: Offset, spriteHeight: Float, below: Float, viewH: Float, margin: Float): Offset {
        val top = foot.y - spriteHeight; val bottom = foot.y + below
        val dy = if (bottom - top <= viewH - 2 * margin) -(top + bottom) / 2f else (-viewH / 2f + margin) - top
        return Offset(-foot.x, dy)
    }
}

/** Close-up of one island: calm sea, title card, the island filling the width. */
@Composable
private fun CloseIsland(
    title: String, subtitle: String, placementActive: Boolean = false,
    zoom: IslandZoom = IslandZoom(), bottomReserve: Dp = 96.dp,
    /** A selected piece the camera keeps in sight together with its actions (pans only as far as needed). */
    focus: CameraFocus? = null,
    onCamera: (IslandCamera) -> Unit = {},
    header: (@Composable () -> Unit)? = null,
    /** A clean tap that nothing on the island took (the open sea around it): closes what is open. */
    onEmptyTap: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val emptyTap by androidx.compose.runtime.rememberUpdatedState(onEmptyTap)
    // Pinch, drag and double-tap zoom happen in a viewport below the title, so the zoomed
    // island never slides over the title or out of reach of the fingers. The own island
    // starts zoomed in and cannot be zoomed out to overview size (see [IslandZoom]).
    var z by remember(zoom.initial) { mutableFloatStateOf(zoom.initial) }
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
    // While a piece is selected near the shore, the camera may pan a little past the usual limit so it and its actions stay in sight.
    var extra by remember { mutableStateOf(Offset.Zero) }
    fun clamp(o: Offset, zz: Float): Offset {
        val mx = ((isle.width * zz - view.width) / 2f).coerceAtLeast(0f) + extra.x
        val my = ((isle.height * zz - view.height) / 2f).coerceAtLeast(0f) + extra.y
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }
    // Camera moves are short animated pans (FastOutSlowIn); a finger on the island takes over at once.
    var cameraMove by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun animateTo(tz: Float, to: Offset) {
        val sz = z; val so = off
        cameraMove?.cancel()
        cameraMove = scope.launch {
            androidx.compose.animation.core.animate(0f, 1f, animationSpec = tween(240, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { f, _ ->
                z = sz + (tz - sz) * f
                off = Offset(so.x + (to.x - so.x) * f, so.y + (to.y - so.y) * f)
            }
        }
    }
    BackHandler(enabled = kotlin.math.abs(z - zoom.initial) > .05f && !placementActive) { animateTo(zoom.initial, Offset.Zero) }
    val coverDensity = androidx.compose.ui.platform.LocalDensity.current.density
    LaunchedEffect(focus) {
        if (focus != null && view.width > 1f && isle.width > 1f) {
            val d = coverDensity
            val hubH = isle.width / IslandPlans.ASPECT
            val c = Offset(isle.width / 2f, isle.height / 2f)
            val p = Offset(focus.x * isle.width, (isle.height - hubH) / 2f + focus.y * hubH)
            val foot = off + (p - c) * z
            // the actions keep their screen size: name sign + one row of round buttons under the foot
            val shift = FocusPan.shift(foot, spriteHeight = focus.size * isle.width * z + 8f * d, below = 130f * d,
                viewH = view.height, margin = 10f * d)
            // already (almost) centred: do not nudge the island
            if (shift.getDistance() > 6f * d) {
                val target = off + shift
                val mx = ((isle.width * z - view.width) / 2f).coerceAtLeast(0f); val my = ((isle.height * z - view.height) / 2f).coerceAtLeast(0f)
                extra = Offset((kotlin.math.abs(target.x) - mx).coerceAtLeast(0f), (kotlin.math.abs(target.y) - my).coerceAtLeast(0f))
                animateTo(z, target)
            }
        } else if (focus == null && extra != Offset.Zero) {
            extra = Offset.Zero; animateTo(z, clamp(off, z))
        }
    }
    // Held piece near the screen edge: the camera scrolls on its own and the piece stays under the finger.
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    var edge by remember { mutableStateOf(Offset.Zero) }
    val camera = remember {
        object : IslandCamera {
            override val offset get() = off
            override val zoom get() = z
            private fun centre() = Offset(isle.width / 2f, isle.height / 2f)
            // The island box (full width, ASPECT tall) sits centred in the camera box, which is as tall as the
            // viewport. Fingers and fractions are in island-box coordinates, so they are shifted by its top
            // (7.9.5 mixed both: edge scrolling fired mid-screen and a new piece could start in the sea).
            private fun hubSize() = androidx.compose.ui.geometry.Size(isle.width, isle.width / IslandPlans.ASPECT)
            private fun shift() = Offset((isle.width - hubSize().width) / 2f, (isle.height - hubSize().height) / 2f)
            override fun viewCenter(): Offset {
                val p = centre() - off / z - shift()
                return Offset(p.x / hubSize().width, p.y / hubSize().height)
            }
            override fun visibleIn(hub: androidx.compose.ui.geometry.Size): androidx.compose.ui.geometry.Rect {
                val c = centre()
                val dx = (isle.width - hub.width) / 2f; val dy = (isle.height - hub.height) / 2f
                return androidx.compose.ui.geometry.Rect(
                    c.x + (-view.width / 2f - off.x) / z - dx, c.y + (-view.height / 2f - off.y) / z - dy,
                    c.x + (view.width / 2f - off.x) / z - dx, c.y + (view.height / 2f - off.y) / z - dy,
                )
            }
            override fun track(finger: Offset?) {
                edge = if (finger == null) Offset.Zero
                else EdgeScroll.velocity(off + (finger + shift() - centre()) * z, view, 56f * density, 720f * density)
            }
            override fun fingerNow(finger: Offset, offsetThen: Offset, zoomThen: Float): Offset =
                centre() - shift() + (offsetThen + (finger + shift() - centre()) * zoomThen - off) / z
        }
    }
    androidx.compose.runtime.SideEffect { onCamera(camera) }
    LaunchedEffect(edge != Offset.Zero) {
        var last = androidx.compose.runtime.withFrameNanos { it }
        while (edge != Offset.Zero) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val dt = ((now - last) / 1e9f).coerceAtMost(.05f); last = now
            off = clamp(off + edge * dt, z)
        }
    }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(top = 64.dp, bottom = bottomReserve),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Title cards draw above the island (zIndex), so a zoomed island can slide under them
        // instead of being cut off at a hard edge.
        if (header != null) Box(Modifier.zIndex(1f)) { header() } else {
            WorldText(title, 20.sp, modifier = Modifier.zIndex(1f).padding(top = 2.dp))
            Spacer(Modifier.height(3.dp))
            WorldText(subtitle, 11.sp, fill = Color(0xFFE6D3A3), display = false, modifier = Modifier.zIndex(1f))
        }
        Spacer(Modifier.height(8.dp))
        // No clipToBounds: the island lies on the same full-screen sea, so panning/zooming
        // never shows a straight cut between the moving island and the static water.
        Box(
            Modifier.fillMaxWidth().weight(1f)
                .onSizeChanged { view = androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(placementActive) {
                    if(placementActive) return@pointerInput
                    detectTransformGestures { centroid, pan, zoomBy, _ ->
                        cameraMove?.cancel()
                        val nz = (z * zoomBy).coerceIn(zoom.min, zoom.max)
                        val c = Offset(view.width / 2f, view.height / 2f)
                        val r = nz / z
                        val o = Offset(off.x + (centroid.x - c.x - off.x) * (1f - r), off.y + (centroid.y - c.y - off.y) * (1f - r)) + pan
                        z = nz; off = clamp(o, nz)
                    }
                }
                // Double-tap is watched in the Initial pass, so it also works on top of buildings:
                // their own tap is held back briefly (see LocalIslandTapGate) and cancelled here.
                .pointerInput(placementActive) {
                    if(placementActive) return@pointerInput
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
                            val near = (zoom.initial * 1.6f).coerceAtMost(zoom.max)
                            val tz = if (z > zoom.initial * 1.25f) zoom.initial else near
                            val c = Offset(view.width / 2f, view.height / 2f)
                            animateTo(tz, if (tz == zoom.initial) Offset.Zero else clamp(Offset((c.x - at.x) * (tz - 1f), (c.y - at.y) * (tz - 1f)), tz))
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
                            // Pieces, plots and the ground take their taps in the Main pass (and consume them);
                            // a tap nobody took landed on the open sea.
                            if (!multi && !moved && up != null && emptyTap != null) {
                                val after = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Final)
                                if (after.changes.none { it.isConsumed }) emptyTap?.invoke()
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    .onSizeChanged { isle = androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) }
                    .graphicsLayer { scaleX = z; scaleY = z; translationX = off.x; translationY = off.y },
                // The sea fills the whole viewport; the island itself sits in its middle.
                contentAlignment = Alignment.Center,
            ) {
                AnimatedIslandSea(Modifier.fillMaxSize(), oversized = true)
                // Signs read the zoom lazily and stay the same size on screen.
                androidx.compose.runtime.CompositionLocalProvider(LocalIslandZoom provides { z }, LocalIslandTapGate provides tapGate, LocalIslandCamera provides camera) { content() }
            }
        }
    }
}

@Composable
private fun IslandSprite(level: Int, seed: Long, width: Dp, cx: Dp, cy: Dp, name: String, sub: String, color: Color, life: ApiClient.HomeIsland? = null,
                         plateShift: Offset = Offset.Zero, onPlate: (androidx.compose.ui.layout.LayoutCoordinates) -> Unit = {}, onClick: () -> Unit) {
    Column(
        Modifier.offset(x = cx - width / 2, y = cy - width * .5f).width(width)
            .worldTap("$name öffnen", onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HubIsland(life?.decor.orEmpty(), Modifier.fillMaxWidth(), labels = false, seed = seed, life = life, lifeTags = false)
        // Name painted on the sea right under the island, nickname as a small line beneath. Measured where it belongs
        // (before the shift) so moving it away from a boat never feeds back into the measurement.
        Column(
            Modifier.offset(y = 2.dp).onGloballyPositioned(onPlate)
                .offset { IntOffset(plateShift.x.roundToInt(), plateShift.y.roundToInt()) }
                .testTag("IslandPlate-$seed").widthIn(max = 152.dp)
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
internal fun rememberIsleReducedMotion(): Boolean {
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
internal fun BoatSheet(letter: ApiClient.Message, friendName: String, nowSec: Long, waitingBerth: Boolean = false, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF243A3F), sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        BoatSheetContent(letter, friendName, nowSec, waitingBerth)
    }
}

@Composable
internal fun BoatSheetContent(letter: ApiClient.Message, friendName: String, nowSec: Long, waitingBerth: Boolean = false) {
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
                WorldText(IsleMotion.seaTime(arrival, nowSec, waitingBerth), 15.sp, display = false)
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

/** 7.9.13: small retry plate under the island header; with a shown island it stays quiet. */
@Composable
internal fun IslandRetryChip(hasIsland: Boolean, error: String, onRetry: () -> Unit) {
    Row(
        Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50)).background(Color(0xE6FFF3DC))
            .clickable(onClickLabel = "Insel erneut laden", onClick = onRetry)
            .semantics { contentDescription = "Insel erneut laden" }
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (hasIsland) "Letzter Stand · Erneut versuchen" else "$error · Erneut versuchen",
            color = Color(0xFF3B2410), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2)
    }
}
