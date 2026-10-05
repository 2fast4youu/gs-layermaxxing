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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    val SeaTop = Color(0xFF8FE3E0)
    val SeaBottom = Color(0xFF4FC3C7)
    val Teal = Color(0xFF17A2A6)
    val TealDark = Color(0xFF0E7F84)
    val Ink = Color(0xFF1F3B4D)
    val Muted = Color(0xFF6B8592)
    val Card = Color(0xFFFFFFFF)
    val Sand = Color(0xFFFFF6E5)
    val Star = Color(0xFFF2B33D)

    val levelNames = listOf("Neu", "Freunde", "Vertraut", "Beste Freunde")
    fun levelName(level: Int) = levelNames[(level - 1).coerceIn(0, 3)]

    /** Relative size on the map: closer, bigger friendships read at a glance. */
    fun islandWidth(level: Int): Dp = when (level.coerceIn(1, 4)) { 1 -> 104.dp; 2 -> 118.dp; 3 -> 134.dp; else -> 156.dp }

    val questIcons = linkedMapOf(
        "hike" to "🥾", "grill" to "🍖", "bike" to "🚲", "food" to "🍝", "game" to "🎲",
        "travel" to "🧳", "sport" to "⚽", "music" to "🎵", "help" to "🤝", "star" to "⭐",
    )
    fun questEmoji(icon: String) = questIcons[icon] ?: "⭐"
}

/** One boat type per release mechanism, so a letter's rule is readable on the sea. */
internal enum class LetterBoat(val res: Int, val badge: String, val label: String) {
    STEAMER(R.drawable.boat_steam, "🕰", "Dampfer · kommt zu einer Zeit"),
    SAILBOAT(R.drawable.boat_sail, "✋", "Segelboot · wartet auf Zustimmung"),
    ROWBOAT(R.drawable.boat_row, "🎲", "Ruderboot · kommt zufällig"),
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
            val rx = if (ring == 0) .36 else .45
            val ry = if (ring == 0) .31 else .40
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
) {
    val pals = remember(friends) { IsleLayout.friendsOnMap(friends) }
    val infoById = remember(islands) { islands?.friends?.associateBy { it.friendId }.orEmpty() }
    var selectedFriend by rememberSaveable { mutableStateOf<Long?>(null) }
    var newQuestFor by remember { mutableStateOf<Pair<Long?, Long?>?>(null) }
    val openQuests = quests.count { it.completedAt == null }
    // "map" = archipelago, "home" = my island up close, "visit:<id>" = a friend's island.
    var view by rememberSaveable { mutableStateOf(startView) }
    var editing by rememberSaveable { mutableStateOf(false) }
    // A building's own place, drawn natively on top of the island (null = none open).
    var place by rememberSaveable { mutableStateOf(startPlace) }
    var home by remember { mutableStateOf<ApiClient.HomeIsland?>(null) }
    var visited by remember { mutableStateOf<ApiClient.HomeIsland?>(null) }
    var slotPick by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(ownId, islands?.score) { ownId?.let { id -> home = runCatching { loadIsland(id) }.getOrNull() ?: home } }
    val visitId = view.removePrefix("visit:").toLongOrNull()
    LaunchedEffect(visitId) { visited = null; visitId?.let { id -> visited = runCatching { loadIsland(id) }.getOrNull() } }
    BackHandler(enabled = view != "map" || editing || place != null) {
        when { place != null -> place = null; editing -> editing = false; else -> view = "map" }
    }
    val homeBadges = mapOf(
        IsleBuilding.HARBOUR to openQuests,
        IsleBuilding.POST to letters.count { it.incoming && it.unlocked && it.readAt == null && opened[it.id] == null },
    )

    IsleTypography {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Isle.SeaTop, Isle.SeaBottom)))) {
        when {
            view == "home" -> CloseIsland(
                title = if (editing) "Insel bearbeiten" else "Meine Insel",
                subtitle = if (editing) "Tippe auf einen freien Platz" else "Tippe auf ein Gebäude",
            ) {
                HubIsland(
                    decor = home?.decor.orEmpty(), modifier = Modifier.fillMaxWidth(), seed = ownId ?: 1L,
                    badges = homeBadges,
                    onBuilding = { b -> place = b.name },
                    onSlot = if (editing) ({ slotPick = it }) else null,
                )
            }
            visitId != null -> {
                val friend = pals.firstOrNull { it.id == visitId }
                CloseIsland(title = "Insel von ${friend?.name ?: "…"}", subtitle = "Du bist zu Besuch") {
                    HubIsland(decor = visited?.decor.orEmpty(), modifier = Modifier.fillMaxWidth(), labels = false, seed = visitId)
                }
            }
            else -> IslandMap(
                ownName = ownName, pals = pals, infoById = infoById, letters = letters, homeDecor = home?.decor.orEmpty(), labels = labels,
                onHome = { view = "home" }, onFriend = { selectedFriend = it }, ownSeed = ownId ?: 1L,
            )
        }
        // Calm HUD: who I am, my quest points, letters waiting, nothing else.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(Modifier.clickable { if (editing) editing = false else if (view != "map") view = "map" else onBack() }) { Text("‹", fontSize = 20.sp, color = Isle.Ink, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(8.dp))
            Pill { Text("$ownEmoji  $ownName", color = Isle.Ink, fontWeight = FontWeight.Bold, maxLines = 1) }
            Spacer(Modifier.weight(1f))
            Pill(Modifier.semantics { contentDescription = "Quest-Punkte" }) {
                Text("⭐ ${islands?.qp ?: 0}", color = Isle.Ink, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(6.dp))
            val ready = letters.count { it.incoming && it.unlocked && it.readAt == null && opened[it.id] == null }
            Pill { Text("✉ $ready", color = Isle.Ink, fontWeight = FontWeight.Bold) }
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
        // Floating bar: harbour (quests + topics), glossary, people.
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp)
                .shadow(8.dp, RoundedCornerShape(28.dp)).background(Isle.Card, RoundedCornerShape(28.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when {
                editing -> BarItem("✓", "Fertig") { editing = false }
                view == "home" -> {
                    BarItem("🗺", "Karte") { view = "map" }
                    BarItem("✎", "Bearbeiten") { editing = true }
                    BarItem("⚓", "Hafen", badge = openQuests) { place = IsleBuilding.HARBOUR.name }
                }
                visitId != null -> {
                    BarItem("🗺", "Karte") { view = "map" }
                    BarItem("💬", "Chat") { onChat(visitId) }
                    BarItem("✉", "Brief") { onComposeLetter(visitId) }
                }
                else -> {
                    BarItem("🏝", "Meine Insel") { view = "home" }
                    BarItem("⚓", "Hafen", badge = openQuests) { place = IsleBuilding.HARBOUR.name }
                    BarItem("🗼", "Freunde", badge = incoming.size) { place = IsleBuilding.LIGHTHOUSE.name }
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
                    runCatching { saveDecor(next) }.onFailure { home = before; onError(ApiErrors.friendly(it.message)) }
                }
            },
            onDismiss = { slotPick = null },
        )
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
    labels: Map<Long, String> = emptyMap(),
    onHome: () -> Unit,
    onFriend: (Long) -> Unit,
    ownSeed: Long = 1L,
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(androidx.compose.ui.geometry.Size(1f, 1f)) }
    val reduced = rememberIsleReducedMotion()
    val waves = rememberInfiniteTransition(label = "sea")
    // One slow 60 s cycle drives everything; nothing restarts visibly.
    val t by waves.animateFloat(0f, 1f, infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val time = if (reduced) .3f else t
    val seconds = time * 60f
    val nowSec = remember(letters) { System.currentTimeMillis() / 1000 }
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
        val sidePx = with(density) { 16.dp.toPx() }
        val points = spots.map { (x, y) -> Offset(with(density) { (w * x).toPx() }, with(density) { (h * y).toPx() }) }
        // Sea sparkles and dotted routes.
        Canvas(Modifier.fillMaxSize()) {
            for (i in 0 until 26) {
                val sx = ((i * 0.381f) % 1f) * size.width + sin(seconds / 9f + i) * 10f
                val sy = ((i * 0.617f) % 1f) * size.height
                val a = (sin(seconds / 3f + i * 1.7f) * .5f + .5f) * .28f
                drawLine(Color.White.copy(alpha = a), Offset(sx, sy), Offset(sx + 14f, sy), 3f, StrokeCap.Round)
            }
            points.forEach { p ->
                drawLine(
                    Color.White.copy(alpha = .75f), center, p, 4f, StrokeCap.Round,
                    PathEffect.dashPathEffect(floatArrayOf(4f, 16f), -seconds * 6f),
                )
            }
        }
        // Home island.
        ordered.forEachIndexed { i, friend ->
            val info = infoById[friend.id]
            val level = info?.level ?: 1
            val (x, y) = spots[i]
            IslandSprite(
                level, friend.id, Isle.islandWidth(level), w * x, h * y,
                friend.name, FriendLabels.display(labels[friend.id], level), profileColor(friend.displayColor),
            ) { onFriend(friend.id) }
        }
        // Boats in transit: one per locked letter (max 2 per route keeps the sea calm).
        ordered.forEachIndexed { i, friend ->
            transit.filter { it.peerId == friend.id }.take(2).forEachIndexed { k, letter ->
                val boat = LetterBoat.forMode(letter.mode)
                val progress = IsleMotion.boatProgress(letter.createdAt, letter.releaseAt, nowSec) + IsleMotion.sway(seconds, i * 3 + k)
                val f = if (letter.incoming) 1f - progress else progress
                val dx = points[i].x - center.x; val dy = points[i].y - center.y
                val len = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
                val side = (if (k == 0) 1f else -1f) * sidePx
                val p = Offset(center.x + dx * f - dy / len * side, center.y + dy * f + dx / len * side)
                val bob = sin(seconds * 1.2f + i) * 1.5f
                Box(
                    Modifier.offset { IntOffset((p.x - 22.dp.toPx()).roundToInt(), (p.y - 22.dp.toPx() + bob).roundToInt()) }
                        .semantics { contentDescription = "${boat.label} – Brief ${if (letter.incoming) "von" else "an"} ${friend.name}" },
                ) {
                    Image(painterResource(boat.res), null, Modifier.size(44.dp))
                    Text(
                        boat.badge, fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.TopEnd).background(Isle.Card, CircleShape).padding(horizontal = 3.dp),
                    )
                }
            }
        }
        val hubW = 200.dp
        Column(
            Modifier.offset(x = w * .5f - hubW / 2, y = h * .5f - hubW * .42f).width(hubW)
                .clickable(onClickLabel = "Meine Insel öffnen", onClick = onHome),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HubIsland(homeDecor, Modifier.fillMaxWidth(), labels = false, seed = ownSeed)
            Row(
                Modifier.offset(y = (-6).dp).shadow(3.dp, RoundedCornerShape(50)).background(Isle.Teal, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) { Text("$ownName · Meine Insel", fontSize = 13.sp, fontFamily = Kit.Display, color = Color.White, maxLines = 1) }
        }
    }
}

/** Close-up of one island: calm sea, title card, the island filling the width. */
@Composable
private fun CloseIsland(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(top = 64.dp, bottom = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, fontSize = 24.sp, fontFamily = Kit.Display, color = Isle.Ink)
        Text(subtitle, fontSize = 14.sp, fontFamily = Kit.Body, fontWeight = FontWeight.Bold, color = Isle.Ink.copy(alpha = .75f))
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) { content() }
    }
}

@Composable
private fun IslandSprite(level: Int, seed: Long, width: Dp, cx: Dp, cy: Dp, name: String, sub: String, color: Color, onClick: () -> Unit) {
    Column(
        Modifier.offset(x = cx - width / 2, y = cy - width * .5f).width(width)
            .clickable(onClickLabel = "$name öffnen", onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DynamicIsland(remember(level, seed) { IslandPlans.friend(level, seed) }, Modifier.fillMaxWidth())
        Row(
            Modifier.offset(y = (-6).dp).shadow(3.dp, RoundedCornerShape(50)).background(Isle.Card, RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(4.dp))
            Column {
                Text(name, fontSize = 13.sp, fontFamily = Kit.Display, color = Isle.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, fontSize = 10.sp, fontFamily = Kit.Display, color = Isle.TealDark, maxLines = 1)
            }
        }
    }
}

@Composable
private fun FriendSheet(
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
    onVisit: () -> Unit,
    onNewQuest: () -> Unit,
    onQuestDone: (ApiClient.Quest, Boolean) -> Unit,
) {
    val level = info?.level ?: 1
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
                    Text(
                        if (label == null) "✎ Wie nennst du ${friend.name}?" else "✎ Namen ändern",
                        color = Isle.TealDark, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { naming = true }.padding(vertical = 4.dp),
                    )
                }
                DynamicIsland(remember(level, friend.id) { IslandPlans.friend(level, friend.id) }, Modifier.width(84.dp), animate = false)
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { IsleLayout.progress(info) }, color = Isle.Teal, trackColor = Isle.Sand,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
            )
            Text(
                info?.nextAt?.let { "Noch ${it - info.score} Punkte bis „${Isle.levelName(level + 1)}“ · am schnellsten mit gemeinsamen Quests" }
                    ?: "Eure Insel ist voll ausgebaut ✨",
                color = Isle.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = onVisit, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("🏝 Insel von ${friend.name} besuchen") }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onChat, Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Chat") }
                Button(
                    onClick = onLetter, Modifier.weight(1f).heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Isle.Teal),
                ) { Text("Brief schreiben") }
            }
            SectionTitle("Quests mit ${friend.name}", action = "+ Neue" to onNewQuest)
            if (quests.isEmpty()) Text("Noch keine gemeinsame Quest – plant etwas Echtes zusammen.", color = Isle.Muted, fontSize = 13.sp)
        }
        items(quests.take(4), key = { "q${it.id}" }) { QuestRow(it, onQuestDone, null) }
        item {
            val received = letters.filter { it.incoming }.sortedByDescending { it.createdAt }
            val sent = letters.filter { !it.incoming }.sortedByDescending { it.createdAt }
            SectionTitle("Briefe")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LetterColumn("Empfangen", received, opened, onOpenLetter, onLockedTap, Modifier.weight(1f))
                LetterColumn("Gesendet", sent, opened, onOpenLetter, onLockedTap, Modifier.weight(1f))
            }
            TextButton(onClick = onTopics, modifier = Modifier.padding(top = 6.dp).heightIn(min = 48.dp)) {
                Text("📌 Themen mit ${friend.name}${if (topicCount > 0) " · $topicCount offen" else ""}", color = Isle.TealDark)
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
                Text(if (state.locked) boat.badge else if (state == LetterState.READY) "📬" else "✉", fontSize = 15.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    Conversations.letterPreviewText(m), fontSize = 12.sp, color = Isle.Ink, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (state.locked) Text("🔒", fontSize = 11.sp)
            }
        }
        if (letters.size > 4) Text("+${letters.size - 4} weitere", fontSize = 11.sp, color = Isle.Muted)
    }
}

@Composable
private fun QuestRow(q: ApiClient.Quest, onDone: (ApiClient.Quest, Boolean) -> Unit, onDelete: ((ApiClient.Quest) -> Unit)?) {
    val done = q.completedAt != null
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).background(if (done) Isle.Sand else Color(0xFFF2FAFA), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(Isle.Card, CircleShape), Alignment.Center) { Text(Isle.questEmoji(q.icon), fontSize = 20.sp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(q.title, fontWeight = FontWeight.Bold, color = Isle.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                (if (q.targetType == "group") "👥 " else "mit ") + q.targetName +
                    (if (done) " · erledigt${q.completedByName?.let { " von $it" } ?: ""}" else ""),
                fontSize = 12.sp, color = Isle.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text("⭐${q.points}", fontWeight = FontWeight.Bold, color = Isle.Star, fontSize = 13.sp)
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.size(40.dp).clip(CircleShape)
                .background(if (done) Isle.Teal else Isle.Card)
                .border(2.dp, Isle.Teal, CircleShape)
                .clickable(onClickLabel = if (done) "Wieder öffnen" else "Als erledigt markieren") { onDone(q, !done) },
            Alignment.Center,
        ) { Text(if (done) "✓" else "", color = Color.White, fontWeight = FontWeight.Bold) }
        if (onDelete != null && q.canDelete) TextButton(onClick = { onDelete(q) }, Modifier.heightIn(min = 40.dp)) {
            Text("✕", color = Isle.Muted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewQuestSheet(
    pals: List<ApiClient.UserSummary>,
    groups: List<ApiClient.Group>,
    presetPeer: Long?,
    presetGroup: Long?,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, Int, Long?, Long?) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf("") }
    var icon by rememberSaveable { mutableStateOf("hike") }
    var points by rememberSaveable { mutableIntStateOf(20) }
    var peer by rememberSaveable { mutableStateOf(presetPeer ?: if (presetGroup == null) pals.firstOrNull()?.id else null) }
    var group by rememberSaveable { mutableStateOf(presetGroup) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Isle.Card, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
            item {
                Text("Neue Quest", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Isle.Ink)
                Text("Etwas, das ihr wirklich gemeinsam macht.", color = Isle.Muted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(title, { title = it.take(80) }, label = { Text("Was habt ihr vor?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(details, { details = it.take(500) }, label = { Text("Details (optional)") }, modifier = Modifier.fillMaxWidth())
                SectionTitle("Symbol")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Isle.questIcons.entries.take(5).forEach { (k, e) -> IconChoice(e, icon == k) { icon = k } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Isle.questIcons.entries.drop(5).forEach { (k, e) -> IconChoice(e, icon == k) { icon = k } }
                }
                SectionTitle("Mit wem?")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        pals.take(6).forEach { f ->
                            FilterChip(peer == f.id, { peer = f.id; group = null }, label = { Text("${f.avatarEmoji} ${f.name}", maxLines = 1) })
                        }
                    }
                    if (groups.isNotEmpty()) Column(Modifier.weight(1f)) {
                        groups.take(6).forEach { g ->
                            FilterChip(group == g.id, { group = g.id; peer = null }, label = { Text("👥 ${g.name}", maxLines = 1) })
                        }
                    }
                }
                SectionTitle("Belohnung")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(10 to "klein", 20 to "normal", 30 to "groß", 50 to "episch").forEach { (p, l) ->
                        FilterChip(points == p, { points = p }, label = { Text("⭐$p $l") })
                    }
                }
                Spacer(Modifier.height(14.dp))
                IsleButton(
                    "Quest ans Brett hängen",
                    { onCreate(title, details, icon, points, peer, group) },
                    Modifier.fillMaxWidth(), enabled = title.isNotBlank() && (peer != null || group != null),
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun IconChoice(emoji: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(if (selected) Isle.Teal.copy(alpha = .18f) else Isle.Sand)
            .border(if (selected) 2.dp else 0.dp, Isle.Teal, CircleShape).clickable(onClick = onClick),
        Alignment.Center,
    ) { Text(emoji, fontSize = 22.sp) }
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
        modifier.heightIn(min = 40.dp).shadow(4.dp, RoundedCornerShape(50)).background(Isle.Card, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        Alignment.Center,
    ) { content() }
}

@Composable
private fun BarItem(icon: String, label: String, badge: Int = 0, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick).heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Text(icon, fontSize = 22.sp)
            if (badge > 0) Text(
                "$badge", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-2).dp).background(Isle.Teal, CircleShape).padding(horizontal = 4.dp),
            )
        }
        Text(label, fontSize = 12.sp, fontFamily = Kit.Display, color = Isle.Ink)
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
private fun LabelDialog(name: String, current: String?, onDismiss: () -> Unit, onSave: (String?) -> Unit) {
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
