package at.gregor.layermaxxing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The approved letter-tree painting is its own post office. All nine nailed envelopes hold real mail;
 * older pages remain available through time on the upper wood/brass strap. Person, status and mail
 * selection hang as ink labels on the existing rope. The pen and right red wax bucket share the
 * native composer and account draft. Arrival information belongs to each travelling letter.
 * A sealed letter's first tap selects/zooms; the second central screen tap requests verified content.
 * Only the narrow outer band returns while sealed/waiting; reading and writing keep their paper input.
 */
internal object LetterTree {
    data class Box(val l: Float, val t: Float, val r: Float, val b: Float) {
        val region get() = StageScenes.Region(l, t, r, b)
    }
    /** The nine nailed sheets (picture fractions, measured on e_post_0_scene / post-0-detail), reading order. */
    val sheets = listOf(
        Box(.3375f, .300f, .4425f, .397f), Box(.530f, .300f, .635f, .397f),
        Box(.250f, .402f, .3475f, .500f), Box(.445f, .402f, .550f, .500f), Box(.645f, .402f, .745f, .500f),
        Box(.200f, .517f, .300f, .622f), Box(.3525f, .524f, .4675f, .630f), Box(.550f, .519f, .650f, .627f), Box(.7075f, .514f, .800f, .620f),
    )
    /** The tilt of every painted sheet (degrees), so the ink follows the paper. */
    val tilts = listOf(-1.5f, 1.5f, 1f, -1f, 1.5f, -1.5f, 1f, -1f, 1.5f)
    /** The four windows of the brass belt and the board around them. */
    val windows = listOf(Box(.370f, .212f, .4125f, .239f), Box(.4475f, .212f, .490f, .239f), Box(.5175f, .212f, .5575f, .239f), Box(.5875f, .212f, .6275f, .239f))
    val belt = Box(.355f, .196f, .645f, .266f)
    /** The rope with the five clothes pegs (peg x and the peg's lower end), its two knots. */
    val pegs = listOf(.240f, .350f, .490f, .635f, .765f)
    const val PEG_BOTTOM = .168f
    val rope = Box(.17f, .118f, .83f, .175f)
    val knotLeft = Box(.15f, .118f, .21f, .185f)
    val knotRight = Box(.80f, .118f, .86f, .185f)
    val padlock = Box(.7575f, .230f, .850f, .357f)
    /** Whole visible wax bucket, including its rim; distinct from the small seal on a letter. */
    val waxBucket = Box(.7575f, .262f, .870f, .367f)
    /** Paper to cut the hanging name strips from: the plain middle of the centre sheet. */
    val paper = Box(.465f, .425f, .530f, .485f)

    /** 1074: every sheet place holds a letter; writing is the fountain pen, older letters lie back in time. */
    const val PER_PAGE = 9
    fun pages(total: Int) = maxOf(1, (total + PER_PAGE - 1) / PER_PAGE)
    /** Where the fountain pen lies (at the tree's foot, on the root) and the time arrow (on the bark under the sheets). */
    /** The fountain pen (B5) hangs on its cord from the rope, left of the sheets, beside the belt's plain brass end. */
    val pen = Box(.181f, .128f, .219f, .372f)
    /** The time arrow boards (B6, 1.875:1) nailed to the bark under the sheets. */
    val arrowBack = Box(.215f, .640f, .335f, .694f)
    val arrowNow = Box(.655f, .640f, .775f, .694f)
    /** 22.10.: the room a letter at sheet [i] has: the distance to its nearest row neighbour's centre (picture widths). */
    private val rows = listOf(0..1, 2..4, 5..8)
    fun room(i: Int): Float {
        val row = rows.first { i in it }
        fun cx(k: Int) = (sheets[k].l + sheets[k].r) / 2
        return listOfNotNull((i - 1).takeIf { it in row }?.let { cx(i) - cx(it) }, (i + 1).takeIf { it in row }?.let { cx(it) - cx(i) }).minOrNull() ?: .2f
    }
}

/**
 * 1074 (Veit: "am Baum sollen zuerst alle versiegelten Briefe sein, dann alle die demnächst kommen und dann alle die schon
 * geöffnet wurden … wie so einen Pfeil … mit dem man in der Zeit zurückreist"): the order on the trunk, from what is
 * known – never from a guess:
 *  1. SEALED: arrived and not opened (the server lets them be opened now: unlocked, incoming, no read time, not opened
 *     here), newest first;
 *  2. TRAVEL: not openable yet (time, chance, both agree, presence …), the soonest known arrival first, letters without a
 *     known time after them (an unknown time is never taken as 0), then newest first;
 *  3. OPENED: opened letters and the own sent ones (delivered), newest first.
 * Ties end by the id (stable). Page 0 is "now"; the arrow goes back in time page by page.
 */
internal object TreeOrder {
    enum class Kind { SEALED, TRAVEL, OPENED }
    fun kind(m: ApiClient.Message, openedHere: Boolean): Kind {
        val s = Conversations.letterState(m, openedHere)
        return when {
            s.locked -> Kind.TRAVEL
            s == LetterState.READY -> Kind.SEALED
            else -> Kind.OPENED
        }
    }
    fun order(letters: List<ApiClient.Message>, opened: Map<Long, *>): List<ApiClient.Message> = letters.sortedWith(
        compareBy<ApiClient.Message> { kind(it, opened[it.id] != null).ordinal }
            .thenBy { if (kind(it, opened[it.id] != null) == Kind.TRAVEL) (it.releaseAt == null) else false }
            .thenBy { if (kind(it, opened[it.id] != null) == Kind.TRAVEL) (it.releaseAt ?: 0L) else 0L }
            .thenByDescending { it.createdAt }
            .thenByDescending { it.id })
    fun page(ordered: List<ApiClient.Message>, page: Int, perPage: Int = LetterTree.PER_PAGE): List<ApiClient.Message> =
        ordered.drop(page.coerceIn(0, maxOf(1, (ordered.size + perPage - 1) / perPage) - 1) * perPage).take(perPage)
}

/** NEW = "An wen?" on the paper, WRITE = the letter written on the paper (1074 end-to-end). A letter looked at is the [LetterScene]. */
internal enum class TreeFocus { REST, FILTER, BELT, ROPE, MAIL, NEW, WRITE }

/**
 * The painted tree pieces of 1074 (briefbaum-final/IMAGE-REQUESTS.json): the fountain pen and the time arrow. The
 * envelopes are the native layered [SceneEnvelope] since 22.10. (B1 / B2 portrait sprites and the B3 / B4 close-ups are
 * no longer drawn – see briefbaum-native-20261022/STATUS.md).
 */
internal object TreeArt {
    /**
     * The generated sprites are lit brighter than the painted trunk (measured: envelope paper mean RGB 216/181/135 against
     * the painted sheets' 126/111/89): on the tree they take the scene's evening light (per-channel scale, no recolour).
     */
    val sceneLight = androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
        .62f, 0f, 0f, 0f, 0f, 0f, .63f, 0f, 0f, 0f, 0f, 0f, .68f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
    private val ids = HashMap<String, Int>()
    @android.annotation.SuppressLint("DiscouragedApi")
    fun id(context: android.content.Context, name: String): Int =
        ids.getOrPut(name) { context.resources.getIdentifier(name, "drawable", context.packageName) }
    const val PEN = "e_post_0_pen"
    const val ARROW = "e_post_0_time_arrow"
}

// 1054 (Veit: "schrift lesbar … markantere schrift"): opaque dark iron-gall ink, a deep red, dark graphite
private val Ink = Color(0xFF1E1208)
private val RedInk = Color(0xFF7A160C)
private val Pencil = Color(0xFF2C2A28)
private val Scratch = Color(0xFFF3E2B8)

/** The scene's rest layout as the stage measured it (value equality: recorded during composition without a loop). */
private data class TreeLayout(val picture: androidx.compose.ui.geometry.Rect, val w: Float, val h: Float)

@Composable
internal fun LetterTreePlace(
    pals: List<ApiClient.UserSummary>,
    letters: List<ApiClient.Message>,
    opened: Map<Long, OpenedMessage>,
    onOpen: (ApiClient.Message) -> Unit,
    onLocked: (ApiClient.Message) -> Unit,
    onWrite: (Long) -> Unit,
    onClose: () -> Unit,
    startFocus: TreeFocus = TreeFocus.REST,
    /** The app's open request per letter ([LetterOpener]): running / failed; absent = none. */
    requests: Map<Long, OpenRequest> = emptyMap(),
    /** Writing on the tree's own paper with the app's draft and send ([LetterDraft]); null = the pen calls [onWrite]. */
    writer: LetterWriter? = null,
    /** Briefbaum 22.10.: "auf ungelesen setzen" (the recipient's own server-side marker); null = not offered. */
    unread: UnreadMarker? = null,
    level: Int = 0,
) {
    val context = LocalContext.current
    val session = remember(context) { SessionStore(context) }
    androidx.compose.runtime.key(session.serverProfile.key, session.name) {
        LetterTreeContent(pals, letters, opened, onOpen, onLocked, onWrite, onClose,
            startFocus, requests, writer, unread, level)
    }
}

@Composable
private fun LetterTreeContent(
    pals: List<ApiClient.UserSummary>,
    letters: List<ApiClient.Message>,
    opened: Map<Long, OpenedMessage>,
    onOpen: (ApiClient.Message) -> Unit,
    onLocked: (ApiClient.Message) -> Unit,
    onWrite: (Long) -> Unit,
    onClose: () -> Unit,
    startFocus: TreeFocus = TreeFocus.REST,
    /** The app's open request per letter ([LetterOpener]): running / failed; absent = none. */
    requests: Map<Long, OpenRequest> = emptyMap(),
    /** Writing on the tree's own paper with the app's draft and send ([LetterDraft]); null = the pen calls [onWrite]. */
    writer: LetterWriter? = null,
    /** Briefbaum 22.10.: "auf ungelesen setzen" (the recipient's own server-side marker); null = not offered. */
    unread: UnreadMarker? = null,
    level: Int = 0,
) {
    val anchors = remember(level) { PostRegistration.of(level) }
    val context = LocalContext.current
    val density = LocalDensity.current
    var view by rememberSaveable { mutableStateOf(PostViews.View.ALL) }
    var person by rememberSaveable { mutableStateOf<Long?>(null) }
    val postalPage = rememberSaveable { mutableStateOf(0) }
    var page by postalPage
    var query by rememberSaveable { mutableStateOf("") }
    var focus by rememberSaveable { mutableStateOf(startFocus) }
    var arrivalId by rememberSaveable { mutableStateOf<Long?>(null) }
    var peopleSearch by rememberSaveable { mutableStateOf("") }
    val scene = rememberSaveable(saver = LetterScene.Saver) { LetterScene() }
    // the letter just sent from the paper (the paper says so until one goes back)
    var sentNote by remember { mutableStateOf<String?>(null) }
    // letters already fetched for reading once in this visit (an opened letter whose text is not here yet)
    val fetched = remember { mutableSetOf<Long>() }
    // the page on the trunk keeps its letters while one is looked at (the order changes only back at the tree)
    var frozen by rememberSaveable { mutableStateOf<List<Long>?>(null) }
    // "auf ungelesen setzen" asked on the paper for this letter (the scene reseals it when the server said yes)
    var markAsked by remember { mutableStateOf<Long?>(null) }
    // Observe the system setting as well as reading it initially: disabling motion mid-flight
    // cancels this phase's animation and settles it without a delayed continuation.
    val resolver = context.contentResolver
    fun motionDisabled() = android.provider.Settings.Global.getFloat(resolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var still by remember(context) { mutableStateOf(motionDisabled()) }
    androidx.compose.runtime.DisposableEffect(resolver) {
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { still = motionDisabled() }
        }
        resolver.registerContentObserver(android.provider.Settings.Global.getUriFor(android.provider.Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        still = motionDisabled()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    fun ms(d: Int) = if (still) 0 else d
    val originalStage = StageScenes.of(IsleBuilding.POST, level)
    // Only clean derivatives of the approved originals may activate physical storage.
    // Rejected v2 geometry is deliberately not a fallback.
    val backArt = if (level == 0) R.drawable.e_post_0_time_back else TreeArt.id(context, "e_post_${level}_physical_back")
    val holderArt = if (level == 0) 0 else TreeArt.id(context, "e_post_${level}_physical_holder")
    val materialReady = backArt != 0 && holderArt != 0
    val stage = originalStage
    // the letters as the tree shows them: marked unread here counts at once (the server's list may lag behind)
    val received = letters.filter { it.incoming && it.groupId == null }
    val all = unread?.let { u -> received.map(u::effective) } ?: received
    val direct = PostViews.direct(all)
    val people = PostViews.people(pals, all)
    val q = query
    val counts = PostViews.counts(all, opened, person, q)
    val ordered = PostViews.shown(all, opened, view, person, q)
    val perPage = anchors.sheets.size
    // Read/unread, arrivals and cache refresh update records, never the physical ID snapshot.
    var batches by rememberSaveable(view, person, q, level) { mutableStateOf<List<String>?>(null) }
    if (batches == null && ordered.isNotEmpty()) batches = PostalBatches.build(ordered, perPage)
    val archive = batches.orEmpty()
    val pages = maxOf(1, archive.size)
    if (page >= pages) page = pages - 1
    val anchoredIds = frozen?.takeIf { !scene.atRest }
        ?: archive.getOrNull(page)?.let(PostalBatches::ids).orEmpty()
    // A deleted record leaves its physical slot empty until explicit navigation. Never
    // manufacture a placeholder message or shift its surviving neighbours into the hole.
    val slotMail = anchoredIds.map { id -> direct.firstOrNull { it.id == id } }
    val onPage = slotMail.filterNotNull()
    val occupiedSlots = slotMail.indexOfLast { it != null } + 1

    val faceIds = onPage.map { it.id }
    var timeScrubbing by remember { mutableStateOf(false) }
    var settledFaceIds by remember { mutableStateOf(faceIds) }
    val pageSlide = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(faceIds, still, timeScrubbing) {
        if (!timeScrubbing && scene.atRest && settledFaceIds != faceIds) {
            pageSlide.snapTo(if (still) 0f else 1f)
            if (!still) pageSlide.animateTo(0f, tween(220))
        } else pageSlide.snapTo(0f)
        settledFaceIds = faceIds
    }
    // A held time gesture selects whole pages continuously; translating their clipped faces
    // produces fragments. The immediate draw guard also covers the frame before effect cancellation.
    val visiblePageSlide = if (timeScrubbing) 0f else pageSlide.value
    val pageSettled = !timeScrubbing && settledFaceIds == faceIds && visiblePageSlide == 0f

    if (arrivalId != null && direct.none { it.id == arrivalId }) androidx.compose.runtime.SideEffect { arrivalId = null }
    val locked = direct.filter { Conversations.letterState(it, opened[it.id] != null).locked }.sortedBy { it.createdAt }
    val who = people.firstOrNull { it.id == person }
    val current = scene.letter?.let { id -> direct.firstOrNull { it.id == id && !Conversations.letterState(it, false).locked } }
    // a letter that is gone (account switch, the friendship's gate closed) ends the scene at once
    if (scene.letter != null && current == null) androidx.compose.runtime.SideEffect { scene.drop() }
    val slot = current?.let { c -> slotMail.indexOfFirst { it?.id == c.id } }?.takeIf { it >= 0 }

    // ---- the scene's animations: each phase runs its own, then reports its end (reduced motion: at once)
    val cam = remember { androidx.compose.animation.core.Animatable(if (scene.atRest) 0f else 1f) }
    val reading = scene.phase == ScenePhase.READING
    val unfold = remember { androidx.compose.animation.core.Animatable(if (reading) 1f else 0f) }
    val paperMotion = LetterMotion.paper(unfold.value)
    val flap = paperMotion.flap
    val rise = paperMotion.rise
    val lift = paperMotion.lift
    val crack = remember { androidx.compose.animation.core.Animatable(0f) }
    val press = remember { androidx.compose.animation.core.Animatable(1f) }
    val ease = androidx.compose.animation.core.FastOutSlowInEasing

    /** Takes [m] to the trunk (its person and all views when the current view does not show it) and moves in to it. */
    fun approach(m: ApiClient.Message, read: Boolean) {
        var ord = ordered
        if (ord.none { it.id == m.id } && archive.none { m.id in PostalBatches.ids(it) }) {
            view = PostViews.View.ALL; person = m.peerId; query = ""
            ord = PostViews.shown(all, opened, PostViews.View.ALL, m.peerId, "")
        }
        // Explicit picker/next navigation may select a letter outside the anchored archive.
        val nextArchive = if (archive.any { m.id in PostalBatches.ids(it) }) archive else PostalBatches.build(ord, perPage)
        batches = nextArchive
        page = nextArchive.indexOfFirst { m.id in PostalBatches.ids(it) }.coerceAtLeast(0)
        frozen = nextArchive.getOrNull(page)?.let(PostalBatches::ids)
        if (direct.none { it.id == m.id }) return
        scene.approach(m.id, read)
    }
    fun lockedTap(m: ApiClient.Message) { arrivalId = m.id }
    fun finishMotion() {
        scene.finishMotion()?.let { (id, _) ->
            direct.firstOrNull { it.id == id && !Conversations.letterState(it, opened[it.id] != null).locked }?.let { approach(it, TreeOrder.kind(it, opened[it.id] != null) == TreeOrder.Kind.OPENED) }
        }
    }
    fun backgroundTap() {
        if (arrivalId != null) { arrivalId = null; return }
        if (!scene.atRest) { finishMotion(); return }
        if (focus != TreeFocus.REST) { focus = TreeFocus.REST }
    }
    fun readable(m: ApiClient.Message) = TreeOrder.kind(m, opened[m.id] != null) == TreeOrder.Kind.OPENED

    androidx.compose.runtime.LaunchedEffect(scene.phase, scene.letter, still) {
        fun t(d: Int) = tween<Float>(ms(d), easing = ease)
        // (an animation only where the value really changes: no dead wait on a value already there)
        suspend fun androidx.compose.animation.core.Animatable<Float, *>.go(v: Float, spec: androidx.compose.animation.core.AnimationSpec<Float>) { if (still) snapTo(v) else if (value != v) animateTo(v, spec) }
        when (scene.phase) {
            ScenePhase.REST -> { cam.snapTo(0f); unfold.snapTo(0f); crack.snapTo(0f); press.snapTo(1f) }
            ScenePhase.APPROACH -> {
                unfold.snapTo(0f); crack.snapTo(0f); press.snapTo(1f)
                cam.go(1f, tween(LetterMotion.APPROACH_MS, easing = LetterMotion.cameraEase)); scene.arrived()
            }
            ScenePhase.SEALED -> { cam.snapTo(1f); unfold.snapTo(0f); crack.snapTo(0f); press.snapTo(1f) }
            ScenePhase.WAITING -> Unit
            // the seal: lifts, cracks, its halves fall away (a calm, own movement)
            ScenePhase.UNSEALING -> { crack.go(1f, tween(LetterMotion.SEAL_MS, easing = androidx.compose.animation.core.LinearEasing)); scene.unsealed() }
            // One continuous path: flap clears first, extraction and floating unfold gently overlap.
            ScenePhase.EMERGING -> {
                cam.snapTo(1f)
                unfold.go(1f, tween(LetterMotion.EMERGE_MS, easing = androidx.compose.animation.core.LinearEasing)); scene.emerged()
            }
            ScenePhase.READING -> { cam.snapTo(1f); unfold.snapTo(1f) }
            // back into the envelope: it folds, slides in, the flap closes (no new seal for a read letter)
            ScenePhase.STOWING -> { unfold.go(0f, tween(LetterMotion.STOW_MS, easing = androidx.compose.animation.core.LinearEasing)); scene.stowed() }
            // marked unread: a fresh seal is pressed on the closed flap
            ScenePhase.RESEALING -> { crack.snapTo(0f); press.snapTo(0f); press.go(1f, tween(ms(600), easing = androidx.compose.animation.core.LinearOutSlowInEasing)); scene.resealed() }
            ScenePhase.LEAVING -> {
                unfold.go(0f, t(240))
                cam.go(0f, tween(LetterMotion.LEAVE_MS, easing = LetterMotion.cameraEase))
                val next = scene.left()
                next?.let { (id, _) -> direct.firstOrNull { it.id == id && !Conversations.letterState(it, false).locked }?.let { approach(it, TreeOrder.kind(it, opened[it.id] != null) == TreeOrder.Kind.OPENED) } }
            }
        }
    }
    // the server's answer to the seal (only while the view waits for it; a late answer after back only changes the tree)
    val hasText = current?.let { opened[it.id] != null } == true
    val request = current?.let { requests[it.id] }
    var missingRequest by remember(scene.letter) { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(scene.phase, hasText, request) {
        if (scene.phase == ScenePhase.WAITING) {
            if (hasText) scene.answered()
            else if (request == OpenRequest.FAILED) scene.failed()
            else if (request == null) {
                // A reset, dropped callback or absent request must never strand this view.
                // Allow the initiating snapshot to arrive before classifying an absent request.
                kotlinx.coroutines.delay(250)
                if (current != null && opened[current.id] == null && requests[current.id] == null) {
                    missingRequest = true; scene.failed()
                }
            }
        }
    }
    // the server marked it unread: back in, sealed again
    val marking = current?.let { unread?.requests?.get(it.id) }
    androidx.compose.runtime.LaunchedEffect(marking, scene.phase) {
        if (markAsked != null && markAsked == current?.id && marking == MarkRequest.DONE && scene.phase == ScenePhase.READING) { markAsked = null; scene.markedUnread() }
    }
    // an opened letter whose text is not here: fetched once through the same open call (never a sealed / marked one)
    if (current != null && (scene.phase == ScenePhase.EMERGING || scene.phase == ScenePhase.READING) && opened[current.id] == null && current.id !in fetched &&
        requests[current.id] == null && readable(current) && !Conversations.letterState(current, false).locked)
        androidx.compose.runtime.SideEffect { if (fetched.add(current.id)) onOpen(current) }

    // writing: the paper rises from the pen and goes back to it
    var draftsOpen by remember { mutableStateOf(false) }
    var leavingWrite by remember { mutableStateOf(false) }
    val writing = focus == TreeFocus.NEW || focus == TreeFocus.WRITE
    var writeStarted by remember { mutableStateOf(still) }
    androidx.compose.runtime.LaunchedEffect(writing) { writeStarted = writing }
    val writeLift by androidx.compose.animation.core.animateFloatAsState(if (writing && writeStarted && !leavingWrite) 1f else 0f,
        tween(ms(520), easing = ease), label = "pen-paper", finishedListener = { v -> if (v == 0f && leavingWrite) { leavingWrite = false; focus = TreeFocus.REST } })
    if (leavingWrite && still) androidx.compose.runtime.SideEffect { leavingWrite = false; focus = TreeFocus.REST }

    // back is always possible: the view lets go of a seal waiting for the server (its request keeps running – an answer
    // after that only changes the letter's state on the tree, it never pulls the view forward); a draft on the paper stays
    fun back() {
        if (writing && writer?.draft?.flush() == false) return
        if (arrivalId != null) { arrivalId = null; return }
        if (!scene.atRest) { scene.out(); return }
        sentNote = null
        if (leavingWrite) { leavingWrite = false; focus = TreeFocus.REST; return }
        if (writing && !still) leavingWrite = true else focus = TreeFocus.REST
    }
    // the paper composer needs its draft (after a restore without one: "An wen?")
    if (focus == TreeFocus.WRITE && (writer == null || writer.draft == null && sentNote == null)) focus = TreeFocus.NEW
    androidx.activity.compose.BackHandler(enabled = arrivalId != null || focus != TreeFocus.REST || !scene.atRest) { back() }
    // On compact phones frame the whole registered POST0 mail spread and rope, not just
    // the trunk centre. The older .16..87 crop cut off the leftmost bottom envelope and
    // the Person selector. Change only the camera: painted art and mail registration stay fixed.
    val narrow = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 380
    val camera = when (focus) {
        TreeFocus.BELT -> null
        TreeFocus.ROPE -> null
        else -> if (narrow && level == 0) LetterTree.Box(.07f, .10f, .94f, .80f).region else null
    }
    val layout = remember { mutableStateOf<TreeLayout?>(null) }
    val idle = focus == TreeFocus.REST && scene.atRest
    // the letters may be tapped at rest and with the rope open (as before); never while one is looked at or written
    val lettersTappable = pageSettled && scene.atRest && (focus == TreeFocus.REST || focus == TreeFocus.ROPE)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().postalBackgroundTap { backgroundTap() }.semantics { contentDescription = "${anchors.label}: ${direct.size} Briefe" }) {
        val wPx = constraints.maxWidth.toFloat(); val hPx = constraints.maxHeight.toFloat()
        val target = with(density) { LetterCamera.target(wPx, hPx, 64.dp.toPx(), 132.dp.toPx()) }
        // while the letter floats, the camera lowers the envelope: its open top stays in view under the paper's lower edge
        // (Hermes pixel review: the letter floats OVER its envelope instead of covering it like a dialog)
        val low = target.translate(0f, hPx * (1f - READ_BELOW) + hPx * .012f - target.top)
        fun aim(l: Float) = androidx.compose.ui.geometry.Rect(target.left, target.top + (low.top - target.top) * l, target.right, target.bottom + (low.top - target.top) * l)
        val restEnvelope: androidx.compose.ui.geometry.Rect? = layout.value?.let { l -> slot?.let {
            with(density) { envelopeRect(l, it, anchors).px(this) }.translate(0f, if (level == 0) 0f else
                l.picture.height / 1536f * (PhysicalPostalStorage.frame(level, it, cam.value)?.dy ?: 0f))
        } }
        fun camAt(v: Float, l: Float = lift): FloatArray = layout.value?.let { lay -> restEnvelope?.let { LetterCamera.transform(it, aim(l), lay.picture, wPx, hPx, v) } } ?: floatArrayOf(1f, 0f, 0f)
        // ---- the scene under one camera: the painted tree, its letters, belt, rope – all moving together
        Box(Modifier.fillMaxSize().graphicsLayer {
            val c = camAt(if (level == 0) cam.value else PostalStorageMotion.camera(cam.value))
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f); scaleX = c[0]; scaleY = c[0]; translationX = c[1]; translationY = c[2]
        }) {
            FunctionStage(stage, camera, maxZoom = if (focus == TreeFocus.BELT) 2.0f else if (narrow && level == 0) 1f else 1.3f, header = 64.dp, reserve = 24.dp, pictureOverride = if (backArt != 0) backArt else null, preservePictureOverflow = level > 0) { g ->
                val l = TreeLayout(with(density) { androidx.compose.ui.geometry.Rect(g.picture.left.toPx(), g.picture.top.toPx(), (g.picture.left + g.picture.w).toPx(), (g.picture.top + g.picture.h).toPx()) }, wPx, hPx)
                if (layout.value != l) layout.value = l
                // Keep the registered furniture and real stored mail behind every selection slip.
                run {
                    // ---- the letters on the trunk, in the order of TreeOrder, one page of time
                    anchors.sheets.forEachIndexed { i, s ->
                        val m = slotMail.getOrNull(i) ?: return@forEachIndexed
                        val r = if (level == 0) g.box(s) else envelopeRect(g, i, anchors)
                        val st = Conversations.letterState(m, opened[m.id] != null)
                        val kind = TreeOrder.kind(m, opened[m.id] != null)
                        val v = PostViews.viewOf(m, opened[m.id] != null)
                        if (m.id != scene.letter) Box(Modifier.graphicsLayer { alpha = 1f - .6f * cam.value }) {
                            NailedLetter(g, i, r, m, kind, EnvelopeLook(seal = if (kind == TreeOrder.Kind.SEALED) 1f else 0f, waxMark = m.incoming), tilt = anchors.tilts[i] * .6f, anchors = anchors, progress = 0f, occupied = occupiedSlots, pageSlide = visiblePageSlide)
                        }
                        val say = "Brief " + (if (m.incoming) "von " else "an ") + m.peerName + ": " + when (kind) {
                            TreeOrder.Kind.SEALED -> "angekommen, noch versiegelt – zum Kuvert"
                            TreeOrder.Kind.TRAVEL -> PostViews.availability(m, st)
                            TreeOrder.Kind.OPENED -> "${v.label} – lesen"
                        }
                        val hit = if (level == 0) r else g.box(remember(level, i, occupiedSlots) {
                            PhysicalPostalStorage.hitBox(level, i, occupiedSlots)
                        })
                        if (lettersTappable) SheetZone(hit, say, "tree-letter-${m.id}", slot = "letter:${m.id}", exact = level > 0) {
                            when (kind) {
                                TreeOrder.Kind.SEALED -> approach(m, read = false)
                                TreeOrder.Kind.TRAVEL -> lockedTap(m)
                                TreeOrder.Kind.OPENED -> approach(m, read = true)
                            }
                        }
                    }
                    if (level > 0 && materialReady) PostHolder(g, anchors, 0f, holderArt)
                    // ---- the time arrow: back to older letters / forward to now; the fountain pen: a new letter
                    if (focus == TreeFocus.REST || writing) StagePen(g.box(anchors.pen), context, level, enabled = idle) { focus = if (writer?.draft != null) TreeFocus.WRITE else TreeFocus.NEW }
                    // Counts live only in the labelled phone controls / status picker.
                    // The former source-window ink painted detached numbers over the furniture.
                    if (idle) {
                        if (level == 0) {
                            val beginWrite = { focus = if (writer?.draft != null) TreeFocus.WRITE else TreeFocus.NEW }
                            // The painted pail can extend beyond the cropped image. Its full
                            // 48dp target must remain inside the viewport without moving the art.
                            val wax = g.box(LetterTree.waxBucket)
                            val waxW = maxOf(48.dp, wax.w).coerceAtMost(g.screen.w)
                            val waxH = maxOf(48.dp, wax.h).coerceAtMost(g.screen.h)
                            Zone(wax.copy(left = wax.left.coerceIn(0.dp, g.screen.w - waxW),
                                top = wax.top.coerceIn(0.dp, g.screen.h - waxH), w = waxW, h = waxH),
                                "Mit rotem Wachs einen Brief schreiben", "tree-wax-bucket", onClick = beginWrite)
                        } else Zone(g.box(anchors.padlock), if (locked.isEmpty()) "Kein Brief unterwegs" else "Briefe unterwegs: ${locked.size}, den ältesten zeigen", "tree-padlock",
                            enabled = locked.isNotEmpty()) { locked.firstOrNull()?.let(::lockedTap) }
                    }
                    if (idle) {
                        val tag = g.box(anchors.filterTag)
                        val clearTag = if (level == 0) tag.copy(top = minOf(tag.top, g.box(anchors.belt).top - 52.dp)) else tag
                        val registeredStrap = g.box(if (level == 0) anchors.belt else anchors.timeRail)
                        // Keep POST3 Filter clear of the counter letters. Move only the rail down
                        // on the apron, leaving 4dp after RopeFilterTag's entire expanded target.
                        val strap = if (level == 3) registeredStrap.copy(top = maxOf(registeredStrap.top,
                            clearTag.top + maxOf(48.dp, clearTag.h) + 4.dp)) else registeredStrap
                        PostalTimeRail(page, pages, true, modifier = Modifier.offset(strap.left, strap.top).size(strap.w, maxOf(48.dp, strap.h)),
                            summary = archive.getOrNull(page)?.let { "${PostalBatches.label(it)} · ${page + 1}/$pages" } ?: "Keine Post", detents = archive.map(PostalBatches::label), painted = level == 0,
                            furniture = level > 0, onScrubbing = { timeScrubbing = it }) { requested ->
                                // Only this explicit time action refreshes the physical archive.
                                val refreshed = PostalBatches.build(ordered, perPage)
                                batches = refreshed
                                page = requested.coerceIn(0, (refreshed.size - 1).coerceAtLeast(0))
                            }
                        RopeFilterTag(clearTag, person != null || view != PostViews.View.ALL || query.isNotBlank(), furniture = level > 0) { focus = TreeFocus.FILTER }
                    }
                }
                if (idle) {
                    if (onPage.isEmpty()) Box(g.box(anchors.sheets.first()).let { it.copy(w = g.picture.w * .42f) }.mod(), Alignment.Center) {
                        Text(if (direct.isEmpty()) "Noch keine empfangenen Briefe" else "Keine Briefe in dieser Auswahl", style = ink(13f, color = if (level == 0) Ink else Scratch), modifier = Modifier.testTag("post-empty"))
                    }
                }
                // ---- the letter looked at: the same envelope, in front of the bark that dims a little as the camera comes
                if (current != null && slot != null) {
                    val r = envelopeRect(g, slot, anchors, cam.value)
                    // the rest of the tree steps back as the camera comes (a soft depth: darker, the envelope stays lit)
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = cam.value }.background(Brush.radialGradient(
                        0f to Color.Transparent, .62f to Color(0x8C0E0904), 1f to Color(0xD90E0904),
                        center = with(density) { Offset((r.left + r.w / 2).toPx(), (r.top + r.h / 2).toPx()) },
                        radius = with(density) { (r.w * 1.05f).toPx() })))
                    val kind = TreeOrder.kind(current, opened[current.id] != null)
                    val needsSeal = when (scene.phase) {
                        ScenePhase.APPROACH, ScenePhase.SEALED -> kind == TreeOrder.Kind.SEALED
                        ScenePhase.WAITING, ScenePhase.UNSEALING, ScenePhase.RESEALING -> true
                        else -> false
                    }
                    val look = EnvelopeLook(flap = flap, seal = if (scene.phase == ScenePhase.RESEALING) press.value else if (needsSeal) 1f else 0f,
                        crack = if (scene.phase == ScenePhase.UNSEALING) LetterMotion.seal(crack.value) else 0f,
                        rise = if (lift > 0f) null else rise, waxMark = current.incoming && !needsSeal)
                    NailedLetter(g, slot, r, current, kind, look, tilt = anchors.tilts[slot] * .6f * (1f - cam.value), stateAlpha = 1f - cam.value, anchors = anchors, progress = cam.value, occupied = occupiedSlots)
                }
            }
        }
        // Selected sealed input is in SCREEN space. It never inherits the source envelope's camera.
        if (current != null && scene.phase in listOf(ScenePhase.SEALED, ScenePhase.WAITING)) {
            val kind = TreeOrder.kind(current, opened[current.id] != null)
            val busy = scene.phase == ScenePhase.WAITING || request == OpenRequest.RUNNING
            val openedHere = opened[current.id] != null || kind == TreeOrder.Kind.OPENED
            val label = if (busy) "Brief wird geöffnet" else if (openedHere) "Kuvert öffnen und Brief lesen" else "Siegel lösen und Brief öffnen"
            Box(Modifier.fillMaxSize().clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClickLabel = "Zurück zu ${anchors.label}") { scene.out() }
                .semantics { contentDescription = "Zurück zu ${anchors.label}"; role = Role.Button }.testTag("tree-outside"))
            Box(Modifier.fillMaxSize().padding(12.dp).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClickLabel = label) {
                if (!busy) {
                    missingRequest = false
                    val ask = scene.tapEnvelope(openedHere, request == OpenRequest.RUNNING)
                    if (ask && kind == TreeOrder.Kind.SEALED) {
                        try { onOpen(current) } catch (_: Exception) { missingRequest = true; scene.failed() }
                    }
                }
            }.semantics { role = Role.Button; contentDescription = label }.testTag("tree-envelope"))
        }
        // ---- under the envelope looked at: who, and what a tap does (screen space: a steady size while zooming)
        if (current != null && (scene.phase == ScenePhase.SEALED || scene.phase == ScenePhase.WAITING || scene.phase == ScenePhase.APPROACH || scene.phase == ScenePhase.RESEALING)) {
            val c = camAt(1f)
            val env = restEnvelope?.let { LetterCamera.apply(it, c) } ?: target
            val kind = TreeOrder.kind(current, opened[current.id] != null)
            val busy = scene.phase == ScenePhase.WAITING || request == OpenRequest.RUNNING
            val state = when {
                busy -> "wird geöffnet … · Am Rand zurück (Anfrage läuft höchstens 45 s)"
                request == OpenRequest.FAILED || missingRequest -> "Öffnen hat nicht geklappt – Mitte: erneut versuchen · Rand: zurück"
                kind == TreeOrder.Kind.OPENED -> "Geöffnet – aufs Kuvert tippen zum Lesen"
                scene.phase == ScenePhase.RESEALING || current.markedUnread -> "Wieder versiegelt (ungelesen) – aufs Kuvert tippen"
                else -> "Zum Öffnen aufs Kuvert tippen"
            }
            Box(Modifier.offset(y = with(density) { env.bottom.toDp() } + 12.dp).fillMaxWidth()
                .graphicsLayer { alpha = if (scene.phase == ScenePhase.APPROACH) ((cam.value - .6f) / .4f).coerceIn(0f, 1f) else 1f }, Alignment.TopCenter) {
                PaperSlip(Modifier.padding(horizontal = 28.dp)) {
                    Text((if (current.incoming) "von " else "an ") + current.peerName, style = ink(19f, bold = true),
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    // what the envelope itself says (title / public cover text) – nothing when it says nothing
                    current.title.ifBlank { current.coverNote }.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = ink(14f, color = RedInk), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                    Text(state, style = ink(13f), textAlign = TextAlign.Center, modifier = Modifier.testTag("tree-seal-state"))
                }
            }
        }
        // ---- the letter floating above its envelope
        if (current != null && lift > 0f) {
            val c = camAt(1f)
            val env = restEnvelope?.let { LetterCamera.apply(it, c) } ?: target
            val s = EnvelopeArt.sheet(rise)
            val from = with(density) { DpRect((env.left + s.left * env.width).toDp(), (env.top + s.top * env.height).toDp(), (s.width * env.width).toDp(), (s.height * env.height).toDp()) }
            FloatingPaper(from, lift, onOutside = { if (reading) scene.out() else finishMotion() }, outsideLabel = "Brief zurück ins Kuvert", below = READ_BELOW, dim = .16f,
                onPaperTap = if (reading) null else ({ finishMotion() }),
                footer = { PeerButtons(all, current, opened, ::lockedTap) { m -> scene.goTo(m.id, readable(m)) } }) {
                LetterOnPaper(current, opened, requests, unread, markAsked = markAsked == current.id, onOpen = onOpen,
                    onMark = { if (unread?.mark(current) == true) markAsked = current.id },
                    onlyPerson = person == current.peerId, onPerson = { only -> person = if (only) current.peerId else null; page = 0; batches = null; scene.out() })
            }
        }
        // ---- writing: the paper rises from the pen
        if (writing && writeLift > 0f) {
            val pen = layout.value?.let { l -> with(density) { penRect(l, anchors) } } ?: DpRect(0.dp, 0.dp, 40.dp, 40.dp)
            FloatingPaper(pen, writeLift, onOutside = { back() }, outsideLabel = if (focus == TreeFocus.WRITE) "Brief liegen lassen, zurück zu ${anchors.label}" else "Füllfeder zurücklegen") {
                if (writer?.repository != null) {
                    StampButton("Entwürfe", "drafts-open") {
                        if (writer.draft?.flush() != false) draftsOpen = !draftsOpen
                    }
                }
                when {
                    draftsOpen && writer?.repository != null -> DraftListPaper(writer.repository,
                        onResume = { writer.draft = it; draftsOpen = false; focus = TreeFocus.WRITE },
                        onNew = { if (writer.draft?.flush() != false) { writer.draft = null; draftsOpen = false; focus = TreeFocus.NEW } })
                    focus == TreeFocus.WRITE && writer != null -> {
                        val draft = writer.draft
                        if (draft == null) {
                            Text(sentNote ?: "", style = ink(20f, bold = true, color = Color(0xFF1F5A2C)), modifier = Modifier.testTag("tree-write-sent"))
                            Text("Der Brief ist versiegelt unterwegs. Gesendete Briefe bleiben im Chat-Verlauf; hier liegt nur empfangene Post.", style = ink(14f))
                            StampButton("zurück zu ${anchors.label}", "tree-write-done") { back() }
                        } else TreeLetterComposer(writer, draft, onRecipients = { focus = TreeFocus.NEW }, onPutAway = { back() },
                            onDiscard = { if (draft.discard()) { writer.draft = null; back() } }, onSent = { sentNote = "✓ Verschlüsselt gesendet"; writer.draft = null; writer.onSent() })
                    }
                    focus == TreeFocus.NEW && writer != null -> TreeRecipients(writer) { focus = TreeFocus.WRITE }
                    else -> {
                        Text("An wen?", style = ink(22f, bold = true))
                        if (pals.isEmpty()) Text("Noch keine Freunde – erst am Leuchtturm jemanden finden", style = ink(13f))
                        RecipientGrid(pals.map { Recipient(it.id, it.name, it.avatarEmoji, "tree-to-${it.id}", false) }) { r -> focus = TreeFocus.REST; onWrite(r.id) }
                    }
                }
            }
        }
        if (scene.atRest && focus in listOf(TreeFocus.FILTER, TreeFocus.ROPE, TreeFocus.BELT, TreeFocus.MAIL)) {
            PostalSelector(onDismiss = { focus = TreeFocus.REST }, clean = true,
                title = when (focus) { TreeFocus.FILTER -> "Filter"; TreeFocus.ROPE -> "Person"; TreeFocus.BELT -> "Status"; else -> "Briefe" }) {
                when (focus) {
                    TreeFocus.FILTER -> {
                        var expanded by remember { mutableStateOf<String?>(null) }
                        if (expanded != "status") PostalFilterRow("Person", "post-person", value = who?.name ?: "Alle Personen") {
                            expanded = if (expanded == "person") null else "person"
                        }
                        if (expanded == "person") {
                            PostalFilterSearch(peopleSearch, { peopleSearch = it }, "Person suchen", "post-person-search")
                            Column(Modifier.heightIn(max = 192.dp).verticalScroll(rememberScrollState())) {
                                PostalFilterRow("Alle Personen", "tree-strip-alle", chosen = person == null, slot = "person:alle") { person = null; page = 0; batches = null; focus = TreeFocus.REST }
                                val pc = PostViews.personCounts(all, opened, view, q)
                                people.filter { it.name.contains(peopleSearch, true) }.forEach { p ->
                                    PostalFilterRow(p.name, "tree-strip-${p.id}", value = "${pc[p.id] ?: 0} Briefe", chosen = p.id == person, slot = "person:${p.id}") {
                                        person = p.id; page = 0; batches = null; focus = TreeFocus.REST
                                    }
                                }
                            }
                        }
                        if (expanded != "person") PostalFilterRow("Status", "post-status", value = view.label) { expanded = if (expanded == "status") null else "status" }
                        if (expanded == "status") PostViews.View.entries.forEach { v ->
                            PostalFilterRow(v.label, "tree-window-${v.name}", value = "${counts[v] ?: 0} Briefe", chosen = view == v) {
                                view = v; page = 0; batches = null; focus = TreeFocus.REST
                            }
                        }
                        if (expanded == null) {
                            PostalFilterSearch(query, { query = it; page = 0; batches = null }, "Briefe suchen", "post-search")
                            PostalFilterRow("${ordered.size} Briefe durchsuchen", "post-mail") { focus = TreeFocus.MAIL }
                            if (person != null || view != PostViews.View.ALL || query.isNotBlank())
                                PostalFilterRow("Filter zurücksetzen", "post-filter-reset") { person = null; view = PostViews.View.ALL; query = ""; peopleSearch = ""; page = 0; batches = null; expanded = null }
                        }
                    }
                    TreeFocus.ROPE -> {
                        if (level > 0) Text("Person auswählen", style = ink(20f, bold = true))
                        PostalFilterSearch(peopleSearch, { peopleSearch = it }, "Person suchen", "post-person-search")
                        PostalPickerButton("Alle Personen", "tree-strip-alle", slot = "person:alle", clean = true) { person = null; page = 0; batches = null; focus = TreeFocus.REST }
                        val pc = PostViews.personCounts(all, opened, view, q)
                        people.filter { it.name.contains(peopleSearch, true) }.forEach { p ->
                            PostalPickerButton("${p.name} · ${pc[p.id] ?: 0}", "tree-strip-${p.id}", chosen = p.id == person, slot = "person:${p.id}", clean = true) { person = p.id; page = 0; batches = null; focus = TreeFocus.REST }
                        }
                    }
                    TreeFocus.BELT -> {
                        if (level > 0) Text("Briefstatus auswählen", style = ink(20f, bold = true))
                        PostViews.View.entries.forEach { v ->
                            PostalPickerButton("${v.label}: ${counts[v] ?: 0}", "tree-window-${v.name}", chosen = view == v, clean = true) { view = v; page = 0; batches = null; focus = TreeFocus.REST }
                        }
                    }
                    TreeFocus.MAIL -> {
                        if (level > 0) Text(if (level == 3) "Briefe in Ablagen und auf dem Tisch" else "Briefe auswählen", style = ink(20f, bold = true))
                        PostalFilterSearch(query, { query = it; page = 0; batches = null }, "Briefe suchen", "post-search")
                        if (ordered.isEmpty()) Text("Keine Briefe in dieser Auswahl", style = ink(16f))
                        ordered.forEachIndexed { index, m ->
                            if (level == 3 && index % 2 == 0) Text("Zeitseite ${index / perPage + 1} · Ablage & Tisch ${(index % perPage) / 2 + 1}: ${minOf(2, ordered.size - index)} Briefe", style = ink(16f, bold = true))
                            val tray = if (level == 3) "${if (index % 2 == 0) "Ablage" else "Tisch"} ${(index % perPage) / 2 + 1} · " else ""
                            PostalPickerButton("$tray${if (m.incoming) "von" else "an"} ${m.peerName}\n${PostViews.viewOf(m, opened[m.id] != null).label} · ${PostViews.availability(m, Conversations.letterState(m, opened[m.id] != null))}\n${m.title.ifBlank { m.coverNote }}", "post-select-${m.id}", slot = "letter:${m.id}", clean = true) {
                                focus = TreeFocus.REST
                                if (Conversations.letterState(m, opened[m.id] != null).locked) lockedTap(m) else approach(m, readable(m))
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
        arrivalId?.let { id -> direct.firstOrNull { it.id == id } }?.let { m ->
            PostalSelector(onDismiss = { arrivalId = null }) {
                Text("Brief ${if (m.incoming) "von" else "an"} ${m.peerName}", style = ink(21f, bold = true))
                Text(PostViews.arrival(m), style = ink(17f), modifier = Modifier.testTag("post-arrival"))
                Text("Das Siegel spielt einen Klang; mehrfaches Berühren kann Nebel zeigen. Der Brief bleibt versiegelt.", style = ink(15f))
                StampButton("Siegelklang hören", "post-arrival-action") { arrivalId = null; onLocked(m) }
                StampButton("Schließen", "post-arrival-close") { arrivalId = null }
            }
        }
        // the way back: a chalk arrow in the corner, no button disc
        if (scene.phase !in listOf(ScenePhase.SEALED, ScenePhase.WAITING)) Box(Modifier.statusBarsPadding().padding(start = 6.dp, top = 6.dp).size(48.dp)
            .clickable(onClickLabel = if (idle) "Zurück zur Insel" else "Zurück zu ${anchors.label}") { if (idle) onClose() else back() }
            .semantics { contentDescription = if (idle) "Zurück zur Insel" else "Zurück zu ${anchors.label}"; role = Role.Button }.testTag("tree-back"), Alignment.Center) {
            Text("‹", style = TextStyle(fontFamily = Kit.Display, fontSize = 34.sp, color = Color(0xE6F3E6C8), shadow = Shadow(Color(0x99000000), Offset(0f, 2f), 6f)))
        }
        val st = IslandStages.of(IsleBuilding.POST, level)
        Box(Modifier.size(1.dp).semantics { heading(); contentDescription = "Ort: ${st.name}" })
        Box(Modifier.size(1.dp).semantics { contentDescription = "Stufe: ${st.subtitle}" })
        Box(Modifier.size(1.dp).semantics {
            contentDescription = "Briefszene: ${scene.phase.name}"
            this[PostObjectMotionKey] = PostObjectSample(level, if (materialReady) anchors.holderTravel * cam.value else 0f, anchors.letterTravel * cam.value, materialReady)
            current?.let { this[PostalSelectedMailIdKey] = it.id }
            this[PostalTimePageKey] = page
            this[PostalVisibleMailIdsKey] = onPage.map { it.id }
            this[LetterMotionKey] = LetterMotionSample(cam.value, LetterMotion.seal(crack.value), flap, rise, lift)
        }.testTag("tree-scene-${scene.phase.name}"))
    }
}

/** Kept free under the floating letter (fraction of the screen's height): the envelope's open top shows there. */
private const val READ_BELOW = .19f

/**
 * The landscape envelope hanging at sheet [i] (dp, in the scene): as wide as the painted sheet allows between its
 * neighbours (1.6 × the sheet at most), 1.45 : 1, centred on the sheet just below its painted nail.
 */
private fun envelopeRect(g: StageGeometry, i: Int, anchors: PostRegistration = PostRegistration.of(0), progress: Float = 0f): DpRect {
    val frame = PhysicalPostalStorage.frame(anchors.level, i, progress)
    if (frame != null) return DpRect(g.picture.left + g.picture.w * (frame.slot.left / 1024f),
        g.picture.top + g.picture.h * ((frame.slot.top + frame.dy) / 1536f),
        g.picture.w * (frame.slot.width / 1024f), g.picture.w * (frame.slot.width / 1024f) / EnvelopeArt.ASPECT)
    return envelopeRect(g.box(anchors.sheets[i]), g.picture.w * anchors.room(i))
}
private fun envelopeRect(r: DpRect, room: Dp): DpRect {
    val w = minOf(r.w * 1.6f, room * .94f); val h = w / EnvelopeArt.ASPECT
    return DpRect(r.left + r.w / 2 - w / 2, r.top + r.h * .12f, w, h)
}
private fun envelopeRect(l: TreeLayout, i: Int, anchors: PostRegistration): DpRectPx {
    val s = anchors.sheets[i]; val p = l.picture
    PhysicalPostalStorage.slots(anchors.level).getOrNull(i)?.let { slot ->
        return DpRectPx(p.left + p.width * slot.left / 1024f,
            p.top + p.height * slot.top / 1536f, p.width * slot.width / 1024f,
            0f, p.width * slot.width / 1024f, physical = true)
    }
    return DpRectPx(p.left + p.width * s.l, p.top + p.height * s.t, p.width * (s.r - s.l), p.height * (s.b - s.t), p.width * anchors.room(i))
}
/** The same placement in px (for the camera, measured on the stage's rest layout). */
private class DpRectPx(val left: Float, val top: Float, val w: Float, val h: Float, val room: Float, val physical: Boolean = false) {
    fun px(@Suppress("UNUSED_PARAMETER") d: androidx.compose.ui.unit.Density): androidx.compose.ui.geometry.Rect {
        val ew = if (physical) w else minOf(w * 1.6f, room * .94f); val eh = ew / EnvelopeArt.ASPECT
        val x = left + w / 2 - ew / 2; val y = top + h * .12f
        return androidx.compose.ui.geometry.Rect(x, y, x + ew, y + eh)
    }
}
private fun androidx.compose.ui.unit.Density.penRect(l: TreeLayout, anchors: PostRegistration): DpRect {
    val b = anchors.pen; val p = l.picture
    return DpRect((p.left + p.width * b.l).toDp(), (p.top + p.height * b.t).toDp(), (p.width * (b.r - b.l)).toDp(), (p.height * (b.b - b.t)).toDp())
}

/**
 * A letter on the trunk: the native landscape envelope (sealed: wax on the flap's tip; opened: a faint stain) with the
 * name in ink on the flap and its state below the seal; a letter still travelling keeps the painted sheet ("unterwegs").
 */
@Composable
private fun NailedLetter(g: StageGeometry, i: Int, r: DpRect, m: ApiClient.Message, kind: TreeOrder.Kind, look: EnvelopeLook,
                         tilt: Float = LetterTree.tilts[i] * .6f, stateAlpha: Float = 1f,
                         anchors: PostRegistration = PostRegistration.of(0), progress: Float = 0f, occupied: Int = anchors.sheets.size, pageSlide: Float = 0f) {
    val (word, short) = when (kind) {
        TreeOrder.Kind.SEALED -> "angekommen" to "neu"
        TreeOrder.Kind.TRAVEL -> "unterwegs" to "bald"
        TreeOrder.Kind.OPENED -> if (m.incoming) "offen" to "offen" else "gesendet" to "raus"
    }
    val color = if (kind == TreeOrder.Kind.SEALED) RedInk else Ink
    if (kind == TreeOrder.Kind.TRAVEL) {
        // the painted sheet itself, its paper below the nail
        if (anchors.level > 0) SceneEnvelope(envelopeRect(g, i, anchors, progress), anchors.tilts[i], EnvelopeLook(seal = 1f), "tree-env-${m.id}",
            pose = PostalStorageMotion.pose(anchors.level, progress, i), storage = PhysicalPostalStorage.frame(anchors.level, i, progress, occupied), pageSlide = pageSlide) {
            StoredSender(m.peerName, envelopeRect(g, i, anchors, progress), 1f)
            PostalStampInk(m)
        }
        if (anchors.level == 0) Box(envelopeRect(g, i, anchors, progress).mod().graphicsLayer { rotationZ = tilt }) {
            PostalStampInk(m)
        }
        if (anchors.level == 0) Ink(r, anchors.tilts[i], top = .08f, bottom = .62f, gap = 0.dp) {
            // (the same name size as on the envelopes: no letter shouts louder than another)
            if (anchors.level == 0) InkFit(m.peerName, 12f, 8.5f, Ink, inkFont = LetterInkFont, tight = true, modifier = Modifier.weight(1f))
            InkFit(word, 10.5f, 8f, Ink, bold = true, fallback = short, tight = true, modifier = Modifier.weight(.8f))
        }
        return
    }
    val env = envelopeRect(g, i, anchors, progress)
    SceneEnvelope(env, tilt, look, "tree-env-${m.id}", pose = PostalStorageMotion.pose(anchors.level, progress, i), storage = PhysicalPostalStorage.frame(anchors.level, i, progress, occupied), pageSlide = pageSlide) {
        PostalStampInk(m, look.flap)
        // the name in ink on the closed flap, above the seal (the layers: seal .56–.84 of the height); it goes with the flap
        if (anchors.level > 0) StoredSender(m.peerName, env, (1f - look.flap * 5f).coerceIn(0f, 1f))
        else InkFit(m.peerName, 14f, 8.5f, color, inkFont = LetterInkFont, tight = true,
            modifier = Modifier.graphicsLayer { alpha = (1f - look.flap * 5f).coerceIn(0f, 1f) }
                .padding(top = env.h * .06f, start = env.w * .12f, end = env.w * .12f).height(env.h * .42f))
    }
    // the state in ink on the painted sheet the envelope hangs on, under the envelope (the delivered envelope is flat)
    // Keep the full lower paper strip for the state. A five-percent bottom reserve left
    // less than the minimum 8sp line height on compact phones at font scale 1.5.
    val top = env.bottom + 1.dp; val room = DpRect(r.left, top, r.w, r.bottom - r.h * .01f - top)
    if (anchors.level == 0 && room.h > 6.dp) Box(Modifier.graphicsLayer { alpha = stateAlpha }) {
        Ink(room, anchors.tilts[i], top = 0f, bottom = 1f, gap = 0.dp) {
            InkFit(word, 10.5f, 8f, color, bold = true, fallback = short, tight = true, modifier = Modifier.weight(1f))
        }
    }
}

/** Ink stays on the same projected face and under the same wood masks as its envelope.
 * Warm dark handwriting sits directly on the original painted paper. */
@Composable
private fun StoredSender(name: String, env: DpRect, alpha: Float) {
    Box(Modifier.padding(top = env.h * .04f, start = env.w * .08f, end = env.w * .08f)
        .height(env.h * .46f).graphicsLayer { this.alpha = alpha }) {
        InkFit(name, 22f, 8.5f, Ink, inkFont = LetterInkFont, tight = true, modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp))
    }
}

/** A slip cut from the letter paper (N3's plain middle, at the slip's own aspect), ink written on it. */
@Composable
private fun PaperSlip(modifier: Modifier = Modifier, inset: Dp = 16.dp, verticalInset: Dp = 9.dp, content: @Composable ColumnScope.() -> Unit) {
    val sheet = EnvelopeArt.of(LocalContext.current.resources).sheet
    Column(modifier.graphicsLayer { rotationZ = -.8f }.drawBehind {
        for (k in 1..3) drawRect(Color(0x22000000), topLeft = Offset(k * 1.5f, k * 2.5f), size = size)
        val sw = (sheet.width * .8f).toInt(); val sh = (sw * size.height / size.width).toInt().coerceIn(1, (sheet.height * .5f).toInt())
        drawImage(sheet, srcOffset = IntOffset((sheet.width * .1f).toInt(), (sheet.height * .36f).toInt()), srcSize = IntSize(sw, sh),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()), colorFilter = EnvelopeArt.sheetLight)
    }.padding(horizontal = inset, vertical = verticalInset), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
}

/**
 * The letter on its paper: who, the title, the text (the paper scrolls), what it is, and – for a letter to me that may be
 * read again – "auf ungelesen setzen" (the server keeps it; a read-once letter honestly cannot).
 */
@Composable
private fun ColumnScope.LetterOnPaper(m: ApiClient.Message, opened: Map<Long, OpenedMessage>, requests: Map<Long, OpenRequest>, unread: UnreadMarker?,
                                      markAsked: Boolean, onOpen: (ApiClient.Message) -> Unit, onMark: () -> Unit, onlyPerson: Boolean, onPerson: (Boolean) -> Unit) {
    // the text stays on the paper while it goes back into the envelope (a mark drops the app's copy)
    var kept by remember(m.id) { mutableStateOf<String?>(null) }
    opened[m.id]?.text?.let { kept = it }
    val text = opened[m.id]?.text ?: kept
    val st = Conversations.letterState(m, opened[m.id] != null)
    Text((if (m.incoming) "von " else "an ") + m.peerName, style = ink(21f, bold = true))
    Text(formatLetterDate(m.createdAt) + " · " + PostViews.availability(m, st), style = ink(12f))
    Text(Conversations.letterPreviewText(m), style = ink(16f, color = RedInk), maxLines = 2, overflow = TextOverflow.Ellipsis)
    val failed = text == null && requests[m.id] == OpenRequest.FAILED
    Text(text ?: if (failed) "Der Brief konnte nicht geholt werden." else "Der Brief wird geholt …",
        style = ink(16f, color = if (failed) RedInk else Ink).copy(fontWeight = FontWeight.SemiBold, lineHeight = 23.sp), modifier = Modifier.testTag("tree-letter-text"))
    if (failed) StampButton("noch einmal holen", "tree-letter-retry") { onOpen(m) }
    opened[m.id]?.name?.let { Text("Anhang: $it", style = ink(13f)) }
    androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
    if (unread != null && m.incoming) {
        val state = unread.requests[m.id]
        when {
            m.oneTime -> Text("Einmal-Brief: kann nicht wieder versiegelt werden.", style = ink(12f), modifier = Modifier.testTag("tree-letter-unread-none"))
            unread.offered(m, opened[m.id] != null) -> {
                StampButton(if (state == MarkRequest.RUNNING) "wird markiert …" else "als ungelesen markieren", "tree-letter-unread",
                    enabled = state != MarkRequest.RUNNING) { onMark() }
                if (state == MarkRequest.FAILED) Text("Markieren hat nicht geklappt.", style = ink(12f, color = RedInk), modifier = Modifier.testTag("tree-letter-unread-failed"))
            }
        }
    }
    if (onlyPerson) StampButton("wieder alle Personen", "tree-only-clear") { onPerson(false) }
    else StampButton("nur Briefe mit ${m.peerName}", "tree-only") { onPerson(true) }
}

private fun formatLetterDate(epochSeconds: Long): String = java.time.format.DateTimeFormatter.ofPattern("d. MMMM yyyy", java.util.Locale.GERMAN)
    .format(java.time.Instant.ofEpochSecond(epochSeconds).atZone(java.time.ZoneId.systemDefault()))

/**
 * Unten auf dem Brief: the same person's previous and newer letter (in time, both directions; never another person's).
 * At an end the button is there but disabled; a letter still behind its gate keeps it (the app's locked view, no text).
 */
@Composable
private fun RowScope.PeerButtons(letters: List<ApiClient.Message>, current: ApiClient.Message, opened: Map<Long, OpenedMessage>,
                                 onLocked: (ApiClient.Message) -> Unit, onGo: (ApiClient.Message) -> Unit) {
    val (prev, next) = PeerLetters.neighbours(letters, current)
    fun tap(m: ApiClient.Message) { if (TreeOrder.kind(m, opened[m.id] != null) == TreeOrder.Kind.TRAVEL) onLocked(m) else onGo(m) }
    fun say(m: ApiClient.Message?, what: String) = when {
        m == null -> "Kein $what Brief von ${current.peerName}"
        TreeOrder.kind(m, opened[m.id] != null) == TreeOrder.Kind.TRAVEL -> "$what Brief: ${PostViews.availability(m, Conversations.letterState(m, false))}"
        else -> "$what Brief " + (if (m.incoming) "von " else "an ") + m.peerName
    }
    PaperButton("‹ früher", say(prev, "Früherer"), "tree-letter-prev", enabled = prev != null, modifier = Modifier.weight(1f)) { prev?.let(::tap) }
    PaperButton("neuer ›", say(next, "Neuerer"), "tree-letter-next", enabled = next != null, modifier = Modifier.weight(1f)) { next?.let(::tap) }
}

/** A button on the paper: an ink-framed stamp, at least 48 dp high, the whole label always visible. */
@Composable
private fun PaperButton(text: String, label: String, tag: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
    Box(modifier.heightIn(min = 48.dp).graphicsLayer { alpha = if (enabled) 1f else .4f }.clip(shape).background(Color(0x1A1E1208), shape)
        .border(2.dp, Ink, shape).clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
        .semantics { role = Role.Button; contentDescription = label }.testTag(tag).padding(horizontal = 8.dp, vertical = 6.dp), Alignment.Center) {
        Text(text, style = ink(16f, bold = true), maxLines = 1, softWrap = false)
    }
}

/**
 * The time arrow on the bark (painted B6 when delivered, interim a chalk arrow): back to older letters, forward to now.
 */
@Composable
private fun TimeArrow(r: DpRect, back: Boolean, label: String, tag: String, context: android.content.Context, onClick: () -> Unit) {
    val art = TreeArt.id(context, TreeArt.ARROW)
    val w = maxOf(r.w, 48.dp); val h = maxOf(r.h, 48.dp)
    Box(Modifier.offset(x = r.left - (w - r.w) / 2, y = r.top - (h - r.h) / 2).size(w, h).clickable(onClickLabel = label, onClick = onClick)
        .semantics { contentDescription = label; role = Role.Button }.testTag(tag), Alignment.Center) {
        // the painted board (B6) points left: back in time; mirrored: forward to now
        if (art != 0) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(art), null,
            Modifier.size(r.w, r.h).graphicsLayer { scaleX = if (back) 1f else -1f }, colorFilter = TreeArt.sceneLight)
        // the word scratched light into the board's body (beside its arrow head), fitted to the board (pixel review 1074:
        // at a large font the word was bigger than the board and hid it)
        val word = if (back) "früher" else "jetzt"
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        fun st(sp: Float) = TextStyle(fontFamily = Kit.Display, fontSize = sp.sp, color = Scratch, shadow = Shadow(Color(0xFF120802), Offset(0f, 2f), 3f),
            lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.Both))
        val bodyW = r.w * .70f; val bodyH = r.h * .62f
        val sp = remember(word, r, density) {
            val bw = with(density) { bodyW.roundToPx() }; val bh = with(density) { bodyH.roundToPx() }
            generateSequence(15f) { it - .5f }.takeWhile { it >= 7f }.firstOrNull { val l = measurer.measure(word, st(it), maxLines = 1, softWrap = false); l.size.width <= bw && l.size.height <= bh } ?: 7f
        }
        Text(word, style = st(sp).copy(lineHeight = sp.sp), maxLines = 1, softWrap = false,
            modifier = Modifier.offset(x = r.w * (if (back) .1f else -.1f)).testTag("$tag-label"))
    }
}

/** The fountain pen lying on the root (B5 when delivered, interim the pen drawn in ink on the bark): a new letter. */
@Composable
private fun Pen(r: DpRect, context: android.content.Context, enabled: Boolean = true, onClick: () -> Unit) {
    val art = TreeArt.id(context, TreeArt.PEN)
    val h = maxOf(r.h, 48.dp); val w = maxOf(r.w, 48.dp)
    Box(Modifier.offset(x = r.left - (w - r.w) / 2, y = r.top - (h - r.h) / 2).size(w, h).let { if (enabled) it.clickable(onClickLabel = "Mit der Füllfeder einen neuen Brief schreiben", onClick = onClick)
        .semantics { contentDescription = "Füllfeder: neuen Brief schreiben"; role = Role.Button }.testTag("tree-pen") else it }, Alignment.Center) {
        if (art != 0) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(art), null, Modifier.size(r.w, r.h),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit, alignment = Alignment.TopCenter, colorFilter = TreeArt.sceneLight)
        else Canvas(Modifier.size(r.w, r.h)) {
            // interim: a dark green pen with a brass band and gold nib, lying diagonally on the root
            rotate(-14f) {
                val y = size.height / 2; val l = size.width * .08f; val rr = size.width * .82f; val t = size.height * .16f
                drawRoundRect(Color(0xFF1E3A2A), Offset(l, y - t), androidx.compose.ui.geometry.Size(rr - l, 2 * t), androidx.compose.ui.geometry.CornerRadius(t, t))
                drawRect(Color(0xFFB08A3E), Offset(rr - size.width * .14f, y - t), androidx.compose.ui.geometry.Size(size.width * .04f, 2 * t))
                val nib = androidx.compose.ui.graphics.Path().apply { moveTo(rr, y - t * .8f); lineTo(size.width * .98f, y); lineTo(rr, y + t * .8f); close() }
                drawPath(nib, Color(0xFFD9B66A))
                drawLine(Color(0x55000000), Offset(l + 2f, y + t + 3f), Offset(rr, y + t + 3f), 3f)
            }
        }
    }
}

private fun StageGeometry.box(b: LetterTree.Box): DpRect = DpRect(picture.x(b.l), picture.y(b.t), picture.w * (b.r - b.l), picture.h * (b.b - b.t))
private fun DpRect.mod(): Modifier = Modifier.offset(x = left, y = top).size(w, h)

/** Android's legible casual handwriting face; only envelope identities opt in.
 * Menus retain their packaged Lilita/Nunito fonts. No new envelope art or font download. */
internal val LetterInkFont: FontFamily by lazy {
    FontFamily(android.graphics.Typeface.create("casual", android.graphics.Typeface.NORMAL))
}

/** Ink on paper: names and headings in the chunky display face (Lilita), the rest in extra-bold Nunito – never hairline. */
private fun ink(sp: Float, bold: Boolean = false, color: Color = Ink) =
    TextStyle(fontFamily = if (bold) Kit.Display else Kit.Body, fontWeight = if (bold) FontWeight.Normal else FontWeight.ExtraBold,
        fontSize = sp.sp, lineHeight = (sp * 1.12f).sp, color = color)

/** Ink written on a painted sheet: the paper's text area (below its nail), following its tilt. */
@Composable
private fun Ink(r: DpRect, tilt: Float, tag: String? = null, top: Float = .2f, bottom: Float = .92f, gap: Dp = 1.dp, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.offset(x = r.left + r.w * .04f, y = r.top + r.h * top).size(r.w * .92f, r.h * (bottom - top)).rotate(tilt).let { if (tag != null) it.testTag(tag) else it },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically), content = content)
}

/** One line of ink, as large as the paper allows between [maxSp] and [minSp] (font scale included, never cut). */
@Composable
private fun InkFit(text: String, maxSp: Float, minSp: Float, color: Color, bold: Boolean = false, optional: Boolean = false, fallback: String? = null, tight: Boolean = false,
                   modifier: Modifier = Modifier, inkFont: FontFamily? = null) {
    val measurer = rememberTextMeasurer()
    val inheritedStyle = androidx.compose.material3.LocalTextStyle.current
    // The artwork has finite room: keep the minimum physical ink size, rather than multiplying
    // that floor by the accessibility scale and clipping names. Larger type still wins if it fits;
    // full accessible descriptions and the separate close-view identity retain the user's scale.
    val fontScale = LocalDensity.current.fontScale
    val floorSp = minSp.coerceAtMost(8.5f) / fontScale.coerceAtLeast(1f)
    fun style(size: Float) = inheritedStyle.merge(ink(size, bold, color)).let { base ->
        if (inkFont != null) base.copy(fontFamily = inkFont, fontWeight = FontWeight.Normal) else base
    }.copy(textAlign = TextAlign.Center, letterSpacing = 0.sp).let { if (tight) it.copy(lineHeight = (size * 1.02f).sp,
        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.Both)) else it }
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth(), Alignment.Center) {
        val px = constraints.maxWidth
        val ph = if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE
        // width AND height of the room this line has (pixel review 1074: a line taller than its room was cut)
        fun measured(t: String, size: Float) = measurer.measure(t, style(size), maxLines = 1, softWrap = false,
            constraints = androidx.compose.ui.unit.Constraints(maxWidth = px, maxHeight = ph))
        fun fit(t: String) = generateSequence(maxSp) { it - .5f }.takeWhile { it >= floorSp }.plus(sequenceOf(floorSp))
            .firstOrNull { measured(t, it).let { layout -> !layout.didOverflowWidth &&
                !layout.multiParagraph.didExceedMaxLines && kotlin.math.ceil(layout.multiParagraph.height) + 2 <= ph } }
        // the text, else its short [fallback], at the largest size that fits
        val (shown, sp) = remember(text, fallback, px, ph, maxSp, floorSp, fontScale, inkFont, inheritedStyle, bold, tight) {
            fit(text)?.let { text to it } ?: fallback?.let { f -> fit(f)?.let { f to it } } ?: ((fallback ?: text) to -1f)
        }
        // a second line that does not fit even at [minSp] is left out (the sheet's close look and TalkBack say it whole)
        if (sp < 0f && optional) return@BoxWithConstraints
        val size = if (sp < 0f) floorSp else sp
        // Native paragraph height can be fractional while Text's intrinsic height rounds down.
        // Reserve its ceiling explicitly so the final descender pixel is not clipped.
        val linePixels = (kotlin.math.ceil(measured(shown, size).multiParagraph.height).toInt() + 2).coerceAtMost(ph)
        val lineHeight = with(LocalDensity.current) { linePixels.toDp() }
        Text(shown, style = style(size), maxLines = 1, softWrap = false,
            overflow = if (sp < 0f) TextOverflow.Ellipsis else TextOverflow.Visible,
            modifier = Modifier.fillMaxWidth().height(lineHeight).testTag("tree-ink"))
    }
}

/** A label scratched into the dark wood (light, one line, as large as the room under its window allows). */
@Composable
private fun ScratchFit(text: String, on: Boolean) {
    val measurer = rememberTextMeasurer()
    fun st(sp: Float) = TextStyle(fontFamily = Kit.Display, fontSize = sp.sp, color = if (on) Color(0xFFFFE9A8) else Scratch, textAlign = TextAlign.Center,
        shadow = Shadow(Color(0xFF120802), Offset(0f, 2f), 3f), textDecoration = if (on) TextDecoration.Underline else null)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth(), Alignment.TopCenter) {
        val px = constraints.maxWidth
        val sp = remember(text, px) { generateSequence(14f) { it - .5f }.takeWhile { it >= 8f }.firstOrNull { measurer.measure(text, st(it), maxLines = 1, softWrap = false).size.width <= px } ?: 8f }
        Text(text, style = st(sp), maxLines = 1, softWrap = false)
    }
}

/** A drop of red sealing wax pressed on the lower corner of a sheet (a letter that cannot be opened yet). */
@Composable
private fun WaxDrop(r: DpRect) {
    val d = (r.w * .2f).coerceIn(9.dp, 24.dp)
    Canvas(Modifier.offset(x = r.right - d - r.w * .1f, y = r.bottom - d - r.h * .08f).size(d)) {
        // matte, dark like the painted wax on the padlock
        drawCircle(Brush.radialGradient(listOf(Color(0xFF9E2E22), Color(0xFF5E150F)), center = Offset(size.width * .42f, size.height * .4f), radius = size.width * .6f))
        drawCircle(Color(0x33FFE0C0), radius = size.width * .1f, center = Offset(size.width * .38f, size.height * .34f))
    }
}

/** A small paper label tied to the wax pail, not a floating toolbar or replacement painting. */
/** An invisible touch area on a painted thing (at least 48 dp), labelled for TalkBack. */
@Composable
private fun Zone(r: DpRect, label: String, tag: String, enabled: Boolean = true, slot: String? = null, chosen: Boolean? = null, onClick: () -> Unit) {
    val w = maxOf(r.w, 48.dp); val h = maxOf(r.h, 48.dp)
    Box(Modifier.offset(x = r.left - (w - r.w) / 2, y = r.top - (h - r.h) / 2).size(w, h)
        .clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
        .semantics { contentDescription = label; role = Role.Button; chosen?.let { selected = it }; slot?.let { set(SceneSlotKey, it) } }.testTag(tag))
}

@Composable
private fun SheetZone(r: DpRect, label: String, tag: String, slot: String? = null, exact: Boolean = false, onClick: () -> Unit) {
    if (!exact) Zone(r, label, tag, slot = slot, onClick = onClick)
    else Box(r.mod().pointerInput(onClick) { detectTapGestures { onClick() } }
        .semantics {
            contentDescription = label; role = Role.Button
            slot?.let { set(SceneSlotKey, it) }
            set(androidx.compose.ui.semantics.SemanticsActions.OnClick,
                androidx.compose.ui.semantics.AccessibilityAction(label) { onClick(); true })
        }.testTag(tag))
}

/** The zoomed sheet: the letter written on the paper, scrolling if the large font needs it. */
@Composable
private fun SheetPage(r: DpRect, tilt: Float, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.offset(x = r.left + r.w * .1f, y = r.top + r.h * .14f).size(r.w * .8f, r.h * .82f).rotate(tilt * .5f)
        .verticalScroll(rememberScrollState()).testTag("tree-page"), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
}

/** A link written in ink on the paper: underlined, a whole 48 dp line to tap. */
@Composable
private fun InkLink(text: String, tag: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = text, onClick = onClick)
        .semantics { role = Role.Button; contentDescription = text }.testTag(tag), Alignment.CenterStart) {
        Text("› $text", style = ink(16f, bold = true).copy(textDecoration = TextDecoration.Underline), maxLines = 2)
    }
}

/**
 * 1054: a button on the paper in the paper's own material – an ink stamp: a framed box pressed in dark ink (the main
 * step in red ink, the paper showing through the letters). At least 48 dp high.
 */
@Composable
private fun StampButton(text: String, tag: String, primary: Boolean = false, enabled: Boolean = true, chosen: Boolean = false, slot: String? = null, compact: Boolean = false, onClick: () -> Unit) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp)
    Box(Modifier.padding(vertical = 3.dp).heightIn(min = 48.dp).graphicsLayer { alpha = if (enabled) 1f else .5f }.clip(shape)
        .background(if (primary) RedInk.copy(alpha = .92f) else Color(0x1A1E1208), shape)
        .border(2.5.dp, if (primary) RedInk else Ink, shape)
        .clickable(enabled = enabled, onClickLabel = text, onClick = onClick).semantics { role = Role.Button; contentDescription = text; selected = chosen; slot?.let { set(SceneSlotKey, it) } }.testTag(tag)
        .padding(horizontal = if (compact) 7.dp else 14.dp, vertical = if (compact) 5.dp else 8.dp), Alignment.CenterStart) {
        val textStyle = ink(if (compact) 13f else 16f, bold = true, color = if (primary) Color(0xFFF6E9CC) else Ink)
        Text(text, style = if (compact) textStyle.copy(lineHeight = 16.sp) else textStyle,
            maxLines = if (compact) 2 else Int.MAX_VALUE, overflow = if (compact) TextOverflow.Ellipsis else TextOverflow.Clip)
    }
}

/**
 * 1054: a name tag clipped in clothes peg [k] (paper cut from the painted sheet): it hangs between the rope and the brass
 * belt only – ending above the belt's windows –, as wide as the room to the next peg; the name in the display face, the
 * count beside it. It covers neither the belt nor the sheets. (The rope is as wide as the trunk: looked at closer, its
 * outer pegs would leave the screen, so the camera stays.)
 * The touch area is at least 48 dp.
 */
@Composable
private fun PegTag(g: StageGeometry, k: Int, name: String, n: Int, chosen: Boolean, tag: String, label: String, slot: String?, onClick: () -> Unit) {
    val res = LocalContext.current.resources
    val bmp = remember { ScenePictures.of(res, R.drawable.e_post_0_scene) }
    val pegs = LetterTree.pegs
    val spacing = g.picture.w * minOf(if (k > 0) pegs[k] - pegs[k - 1] else 1f, if (k < pegs.lastIndex) pegs[k + 1] - pegs[k] else 1f)
    val w = spacing * .94f
    val top = g.picture.y(LetterTree.PEG_BOTTOM) - 4.dp
    val bottom = g.picture.y(LetterTree.windows[0].t) - 2.dp
    val h = (bottom - top).coerceAtLeast(20.dp)
    val x = g.picture.x(pegs[k]) - w / 2
    val p = LetterTree.paper
    val zh = maxOf(h, 48.dp)
    Box(Modifier.offset(x = x, y = top + h / 2 - zh / 2).size(w, zh)
        .clickable(onClickLabel = label, onClick = onClick)
        .semantics { contentDescription = label; role = Role.Button; selected = chosen; slot?.let { set(SceneSlotKey, it) } }.testTag(tag), Alignment.Center) {
        Box(Modifier.size(w, h).testTag("$tag-paper"), Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Color(0x55000000), topLeft = Offset(2.dp.toPx(), 3.dp.toPx()), size = size)
                drawImage(bmp, srcOffset = IntOffset((p.l * bmp.width).toInt(), (p.t * bmp.height).toInt()),
                    srcSize = IntSize(((p.r - p.l) * bmp.width).toInt(), ((p.b - p.t) * bmp.height / 3).toInt()), dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                if (chosen) drawRect(RedInk, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            }
            // the name has priority: the count is written beside it only when both fit at a readable size (TalkBack says it)
            val measurer = rememberTextMeasurer()
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 2.dp), Alignment.Center) {
                val px = constraints.maxWidth
                val both = remember(name, n, px) { measurer.measure("$name $n", ink(10f, bold = true), maxLines = 1, softWrap = false).size.width <= px }
                InkFit(if (both) "$name $n" else name, 15f, 8f, if (chosen) RedInk else Ink, bold = true)
            }
        }
    }
}

/**
 * The chosen person at rest: a small slip of the same paper clipped on the middle peg, lying across between the rope and
 * the belt (it covers neither); a tap lets the filter go.
 */
@Composable
private fun NameTag(g: StageGeometry, pegX: Float, name: String, label: String, onClick: () -> Unit) {
    val res = LocalContext.current.resources
    val bmp = remember { ScenePictures.of(res, R.drawable.e_post_0_scene) }
    val measurer = rememberTextMeasurer()
    val st = ink(12f, bold = true, color = RedInk)
    val tw = with(LocalDensity.current) { measurer.measure("$name ×", st, maxLines = 1).size.width.toDp() }
    val w = tw + 16.dp; val h = (g.picture.y(.195f) - g.picture.y(LetterTree.PEG_BOTTOM)).coerceAtLeast(22.dp)
    val p = LetterTree.paper
    // the touch area is 48 dp high around the slip
    val top = g.picture.y(LetterTree.PEG_BOTTOM) - 3.dp
    Box(Modifier.offset(x = g.picture.x(pegX) - w / 2, y = top + h / 2 - 24.dp).size(w, 48.dp)
        .clickable(onClickLabel = label, onClick = onClick).semantics { contentDescription = label; role = Role.Button; selected = true }.testTag("tree-strip-chosen"), Alignment.Center) {
        Canvas(Modifier.size(w, h).rotate(-2f)) {
            drawRect(Color(0x40000000), topLeft = Offset(2.dp.toPx(), 3.dp.toPx()), size = size)
            drawImage(bmp, srcOffset = IntOffset((p.l * bmp.width).toInt(), (p.t * bmp.height).toInt()),
                srcSize = IntSize(((p.r - p.l) * bmp.width).toInt(), ((p.b - p.t) * bmp.height / 3).toInt()), dstSize = IntSize(size.width.toInt(), size.height.toInt()))
        }
        Text("$name ×", style = st, maxLines = 1, softWrap = false)
    }
}

/** A text box as wide as [w] before it is rotated (so a rotated name may use the strip's length). */
private fun Modifier.requiredWidthOf(w: Dp): Modifier =
    this.wrapContentSize(Alignment.Center, unbounded = true).requiredSize(w, 24.dp)

/**
 * 1074 end-to-end, "An wen?" on the paper – 22.10.: real buttons (Veit: "mehr Knöpfe … statt einfach nur Personen mit
 * Links"): every friend (and group) as a stamp with a wax disc and the name. Without a draft a tap starts the letter to
 * that person; with one the buttons tick the recipients (as the sheet's chooser does).
 */
@Composable
private fun TreeRecipients(writer: LetterWriter, onWrite: () -> Unit) {
    val draft = writer.draft
    Text("An wen?", style = ink(22f, bold = true))
    if (writer.friends.isEmpty() && writer.groups.isEmpty()) Text("Noch keine Freunde – erst am Leuchtturm jemanden finden", style = ink(13f))
    RecipientGrid(writer.friends.map { f -> Recipient(f.id, f.name, f.avatarEmoji, "tree-to-${f.id}", draft != null && draft.groupId == null && f.id in draft.selected) },
        checkable = draft != null) { r ->
        if (draft == null) { writer.draft = LetterDraft(r.id); onWrite() }
        else { draft.groupId = null; draft.selected = if (r.chosen) draft.selected - r.id else draft.selected + r.id }
    }
    if (writer.groups.isNotEmpty()) {
        Text("oder eine Gruppe", style = ink(14f))
        RecipientGrid(writer.groups.map { gr -> Recipient(gr.id, "${gr.name} (${gr.members.size})", "👥", "tree-to-group-${gr.id}", draft?.groupId == gr.id) },
            checkable = draft != null, columns = 1) { r ->
            if (draft == null) { writer.draft = LetterDraft(null).also { it.groupId = r.id }; onWrite() }
            else { draft.groupId = if (r.chosen) null else r.id; if (draft.groupId != null) draft.selected = emptySet() }
        }
    }
    if (draft != null) StampButton("weiter schreiben", "tree-to-done", primary = true, onClick = onWrite)
}

private data class Recipient(val id: Long, val name: String, val emoji: String, val tag: String, val chosen: Boolean)

/** The recipients as buttons, two to a row (groups: one, their names are long), each at least 56 dp high. */
@Composable
private fun RecipientGrid(items: List<Recipient>, checkable: Boolean = false, columns: Int = 2, onTap: (Recipient) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(columns).forEach { row ->
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { r -> RecipientButton(r, checkable, Modifier.weight(1f)) { onTap(r) } }
                if (row.size < columns) androidx.compose.foundation.layout.Spacer(Modifier.weight((columns - row.size).toFloat()))
            }
        }
    }
}

@Composable
private fun RecipientButton(r: Recipient, checkable: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp)
    val fontScale = LocalDensity.current.fontScale
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.heightIn(min = 56.dp).clip(shape)
        .background(if (r.chosen) Color(0x307A160C) else Color(0x24FFF6E0), shape)
        .border(if (r.chosen) 2.dp else 1.dp, if (r.chosen) RedInk else Ink.copy(alpha = .65f), shape)
        .clickable(onClickLabel = r.name, onClick = onClick)
        .semantics(mergeDescendants = true) {
            role = if (checkable) Role.Checkbox else Role.Button
            selected = r.chosen
            contentDescription = r.name
            if (checkable) {
                toggleableState = if (r.chosen) ToggleableState.On else ToggleableState.Off
                stateDescription = if (r.chosen) "Gewählt" else "Nicht gewählt"
            }
        }
        .testTag(r.tag).padding(horizontal = 8.dp, vertical = 7.dp)) {
        // Keep the two-column sheet; at large fonts give the name the full stamp width.
        val stacked = maxWidth < 160.dp && fontScale > 1.2f
        val seal: @Composable () -> Unit = {
            Box(Modifier.size(if (stacked) 24.dp else 28.dp), Alignment.Center) {
                Box(Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFF943528), Color(0xFF60251B)))), Alignment.Center) {
                    Text(r.emoji, style = TextStyle(fontSize = 15.sp))
                }
                // The person's sign stays recognisable when chosen; the ink tick also works without colour.
                if (r.chosen) Text("✓", modifier = Modifier.align(Alignment.BottomEnd).offset(x = 5.dp, y = 3.dp),
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink))
            }
        }
        val name: @Composable (Modifier) -> Unit = { labelModifier ->
            Text(r.name, modifier = labelModifier.testTag("${r.tag}-label"), style = ink(16f, bold = true),
                textAlign = if (stacked) TextAlign.Center else TextAlign.Start)
        }
        if (stacked) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            seal(); name(Modifier.fillMaxWidth())
        } else androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            seal(); name(Modifier.weight(1f))
        }
    }
}

/** A choice written in ink: a whole 48 dp line, ticked (✓, red) when chosen. */
@Composable
private fun InkChoice(text: String, on: Boolean, tag: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = text, onClick = onClick)
        .semantics { role = Role.Checkbox; selected = on; contentDescription = text }.testTag(tag), Alignment.CenterStart) {
        Text((if (on) "✓ " else "› ") + text, style = ink(16f, bold = true, color = if (on) RedInk else Ink).copy(textDecoration = TextDecoration.Underline), maxLines = 2)
    }
}

/**
 * 1074 end-to-end: the letter written on the tree's own paper (R0 sheet close-up) – the app's whole composer, drawn in
 * ink: recipients, the sealed text, when it may open (all release modes with their inputs and the friendship's minimum
 * delay), the creative presets, the attachment, title / public envelope text / read-once, the honest note on the key,
 * why it cannot go yet, the manual confirmation, sending and its error. Draft and send are [LetterDraft]'s (the same as
 * the messenger's sheet); going back keeps the draft, "verwerfen" asks once more.
 */
@Composable
private fun TreeLetterComposer(writer: LetterWriter, draft: LetterDraft, onRecipients: () -> Unit, onPutAway: () -> Unit, onDiscard: () -> Unit, onSent: () -> Unit) {
    DraftPersistence(draft)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val pick = rememberAttachmentPicker(draft)
    var whenOpen by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var tried by remember { mutableStateOf(false) }
    var confirmManual by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val minimumDelay = draft.minimumDelay(writer.settings)
    fun send() = scope.launch { draft.send(minimumDelay, writer.transmit) { _, _ -> onSent() } }
    val to = when {
        draft.groupId != null -> "Gruppe " + writer.groups.firstOrNull { it.id == draft.groupId }?.name.orEmpty()
        draft.selected.isEmpty() -> "noch niemand"
        else -> writer.friends.filter { it.id in draft.selected }.joinToString { it.name }
    }
    draft.saveStatus?.let { Text(it, style = ink(13f), modifier = Modifier.testTag("draft-save-status")) }
    // to …, changed on the paper's own "An wen?"
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = "Empfänger ändern") { keyboard?.hide(); onRecipients() }
        .semantics { role = Role.Button; contentDescription = "An: $to – ändern" }.testTag("tree-write-to"), Alignment.CenterStart) {
        Text("An $to", style = ink(24f, bold = true), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    // the sealed text, written in ink on ruled paper
    val inkStyle = ink(18f).copy(fontWeight = FontWeight.Bold)
    androidx.compose.foundation.text.BasicTextField(draft.text, { draft.text = it },
        Modifier.fillMaxWidth().heightIn(min = 132.dp).semantics { contentDescription = "Brieftext" }.testTag("tree-write-text")
            .drawBehind {
                val step = (18 * 1.12f).sp.toPx(); var y = step + 3.dp.toPx()
                while (y < size.height) { drawLine(Color(0x332C1A0A), Offset(0f, y), Offset(size.width, y), 1.dp.toPx()); y += step }
            },
        textStyle = inkStyle, cursorBrush = androidx.compose.ui.graphics.SolidColor(Ink),
        decorationBox = { field -> Box { if (draft.text.isEmpty()) Text("Dein versiegelter Text …", style = inkStyle.copy(color = Ink.copy(alpha = .45f))); field() } })
    // when it may open
    StampButton(draft.releaseSummary(), "tree-write-when") { keyboard?.hide(); whenOpen = !whenOpen }
    if (whenOpen) Column(Modifier.fillMaxWidth().testTag("tree-write-modes"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ReleaseSchedule.choices.forEach { choice ->
            InkChoice(choice.label, draft.mode == choice, "tree-mode-${choice.name}") { draft.mode = choice }
            if (draft.mode == choice) Text(modeDescription(choice), style = ink(12f))
        }
        PaperFields {
            ReleaseFields(draft, minimumDelay)
        }
        if (minimumDelay > 0) Text("Diese Freundschaft verlangt mindestens ${formatRemaining(minimumDelay)} bis zur Freigabe. Relative Zeiten werden bei Bedarf gemeinsam angehoben; absolute Zeiten müssen die Mindestwartezeit einhalten.", style = ink(12f))
    }
    StampButton(draft.attachment?.let { "Anhang: ${it.first}" } ?: "Anhang beilegen", "tree-write-attach") { keyboard?.hide(); pick() }
    draft.attachment?.let { Text("${it.third.size / 1024} KB · verschlüsselt", style = ink(12f)) }
    StampButton(if (details) "Details schließen" else "Details", "tree-write-details") { details = !details }
    if (details) PaperFields {
        androidx.compose.material3.OutlinedTextField(draft.title, { draft.title = it.take(100) }, Modifier.fillMaxWidth().testTag("tree-write-title"),
            label = { Text("Brieftitel – optional") }, singleLine = true)
        androidx.compose.material3.OutlinedTextField(draft.coverNote, { draft.coverNote = it.take(1000) }, Modifier.fillMaxWidth().testTag("tree-write-cover"),
            label = { Text("Öffentlicher Umschlagtext") }, supportingText = { Text("Offen und unverschlüsselt sichtbar") }, minLines = 2)
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Switch(draft.oneTime, { draft.oneTime = it }, Modifier.testTag("tree-write-onetime"))
            Text("  Nur einmal lesbar", style = ink(14f))
        }
    }
    // honest: the server holds the key until the release
    Text("Der Server hält den Schlüssel bis zur Freigabe.", style = ink(12f))
    InkLink(if (more) "weniger" else "mehr zum Siegel", "tree-write-more") { more = !more }
    if (more) Text(CRYPTO_HONESTY, style = ink(12f))
    // why it cannot go yet (after a try), and the send's own answer
    val problem = draft.problem()
    if (tried && problem != null) Text(problem, style = ink(14f, color = RedInk), modifier = Modifier.testTag("tree-write-problem"))
    draft.notice?.let { Text(it, style = ink(14f, color = if (it.startsWith("✓")) Color(0xFF1F5A2C) else RedInk), modifier = Modifier.testTag("tree-write-notice")) }
    if (confirmManual) {
        Text("Manuell senden? Sie bleibt gesperrt, bis du sie später ausdrücklich freigibst.", style = ink(14f, color = RedInk))
        StampButton("ja, versiegelt senden", "tree-write-manual-yes", primary = true, enabled = !draft.busy) { confirmManual = false; send() }
        StampButton("nein", "tree-write-manual-no") { confirmManual = false }
    } else StampButton(if (draft.busy) "wird versiegelt …" else "versiegelt senden", "tree-write-send", primary = true, enabled = !draft.busy) {
        keyboard?.hide(); tried = true
        if (draft.canSend()) { if (draft.mode == ComposeMode.MANUAL) confirmManual = true else send() }
    }
    StampButton("Entwurf liegen lassen", "tree-write-later") { keyboard?.hide(); onPutAway() }
    if (!confirmDiscard) InkLink("Brief verwerfen", "tree-write-discard") { confirmDiscard = true }
    else {
        Text("Den Entwurf wirklich verwerfen?", style = ink(14f, color = RedInk))
        StampButton("ja, verwerfen", "tree-write-discard-yes") { onDiscard() }
        StampButton("nein, behalten", "tree-write-discard-no") { confirmDiscard = false }
    }
}

/** The app's number / date inputs on the paper: ink colours, the paper showing through. */
@Composable
private fun PaperFields(content: @Composable () -> Unit) {
    val base = androidx.compose.material3.MaterialTheme.colorScheme
    androidx.compose.material3.MaterialTheme(colorScheme = base.copy(surface = Color.Transparent, onSurface = Ink, onSurfaceVariant = Ink.copy(alpha = .75f),
        primary = RedInk, outline = Ink.copy(alpha = .6f), background = Color.Transparent)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

/** Upper post stages use their own painted pen, stamp places and brass, without importing tree props. */
@Composable
private fun StagePen(r: DpRect, context: android.content.Context, level: Int, enabled: Boolean, onClick: () -> Unit) {
    if (level == 0) Pen(r, context, enabled, onClick)
    else {
        Zone(r, "Am Schreibzeug einen Brief schreiben", "tree-pen", enabled, onClick = onClick)
    }
}

@Composable
private fun ObjectArrow(r: DpRect, text: String) {
    Box(r.mod(), Alignment.Center) { Text(text, style = ink(25f, color = Scratch)) }
}

@Composable
private fun StageTimeArrow(r: DpRect, level: Int, back: Boolean, label: String, tag: String, context: android.content.Context, onClick: () -> Unit) {
    if (level == 0) TimeArrow(r, back, label, tag, context, onClick)
    else {
        ObjectArrow(r, if (back) "‹" else "›")
        Zone(r, label, tag, onClick = onClick)
    }
}

@Composable
private fun StagePerson(g: StageGeometry, anchors: PostRegistration, k: Int, name: String, n: Int, chosen: Boolean, tag: String, label: String, slot: String?, onClick: () -> Unit) {
    if (anchors.level == 0) {
        PegTag(g, k, name, n, chosen, tag, label, slot, onClick)
        return
    }
    val r = g.box(anchors.people[k])
    Box(r.mod(), Alignment.Center) {
        InkFit("$name · $n", 15f, 8f, if (chosen) RedInk else Ink, bold = true)
    }
    Zone(r, label, tag, slot = slot, chosen = chosen, onClick = onClick)
}

@Composable
private fun StageNameTag(g: StageGeometry, anchors: PostRegistration, name: String, label: String, onClick: () -> Unit) {
    if (anchors.level == 0) NameTag(g, anchors.pegs[2], name, label, onClick)
    else {
        val r = g.box(anchors.people[1])
        Box(r.mod(), Alignment.Center) { InkFit("$name ×", 14f, 8f, RedInk, bold = true) }
        Zone(r, label, "tree-strip-chosen", onClick = onClick)
    }
}

@Composable
private fun PostalPickerButton(text: String, tag: String, chosen: Boolean = false, slot: String? = null,
                               clean: Boolean, onClick: () -> Unit) {
    if (clean) PostalFilterRow(text, tag, chosen = chosen, slot = slot, onClick = onClick)
    else StampButton(text, tag, chosen = chosen, slot = slot, onClick = onClick)
}

private fun timeSummary(page: Int, pages: Int, mail: List<ApiClient.Message>): String {
    val date = mail.firstOrNull()?.let {
        java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy").format(java.time.Instant.ofEpochSecond(it.createdAt).atZone(java.time.ZoneId.systemDefault()))
    } ?: "leer"
    return "${page + 1}/$pages · $date"
}

/** A quiet label in the scene's own paper and ink; no floating white Material card. */
@Composable
private fun RopeFilterTag(r: DpRect, active: Boolean, furniture: Boolean = false, action: () -> Unit) {
    Box(Modifier.offset(r.left, r.top).size(maxOf(96.dp, r.w), maxOf(48.dp, r.h))
        .clickable(onClickLabel = "Person, Status und Briefe filtern", onClick = action)
        .semantics { contentDescription = "Filter"; role = Role.Button; stateDescription = if (active) "Auswahl aktiv" else "Alle Briefe" }
        .testTag("post-filter"), Alignment.TopCenter) {
        Box(Modifier.fillMaxWidth().heightIn(min = 40.dp)
            .testTag("post-filter-surface")
            .then(if (furniture) Modifier else Modifier.postalPaperSurface(strip = true))
            .padding(horizontal = 8.dp, vertical = 8.dp), Alignment.Center) {
            Text(if (active) "Filter • ▾" else "Filter ▾", fontFamily = if (furniture) Kit.Display else Kit.Body,
                fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.sp, fontWeight = FontWeight.SemiBold,
                color = if (furniture) Color(0xFFFFEBC5) else Color(0xFF382616), maxLines = 1,
                style = TextStyle(shadow = if (furniture) androidx.compose.ui.graphics.Shadow(Color(0xFF27180D), Offset(1f, 1f), 1f) else null),
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().testTag("post-filter-ink"))
        }
    }
}

/** Shared real repository list, rendered on the same paper as the production composer. */
@Composable
internal fun DraftListPaper(repository: LetterDraftRepository, onResume: (LetterDraft) -> Unit, onNew: () -> Unit) {
    val revision = repository.revision
    var refresh by remember { mutableStateOf(0) }
    var deleting by remember { mutableStateOf<LetterDraft?>(null) }
    val entries = remember(repository, revision, refresh) { runCatching { repository.list() } }
    Text("Entwürfe", style = ink(24f, bold = true), modifier = Modifier.testTag("drafts-list"))
    Text("Nur auf diesem Gerät · für dieses Konto", style = ink(12f))
    StampButton("Neuer Brief", "drafts-new", primary = true, onClick = onNew)
    entries.exceptionOrNull()?.let { Text("Fehler: Entwürfe können nicht gelesen werden. Bitte erneut versuchen.", style = ink(14f, color = RedInk)) }
    repository.error?.let { Text(it, style = ink(14f, color = RedInk), modifier = Modifier.testTag("drafts-error")) }
    StampButton("Erneut laden", "drafts-refresh") { refresh++ }
    if (entries.getOrNull()?.isEmpty() == true && repository.error == null) Text("Noch keine Entwürfe", style = ink(16f), modifier = Modifier.testTag("drafts-empty"))
    entries.getOrDefault(emptyList()).forEach { entry ->
        val draft = entry.draft
        Column(Modifier.fillMaxWidth().testTag("draft-row-${draft.id}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StampButton("Fortsetzen · " + draft.title.ifBlank { draft.text.take(70).ifBlank { "Begonnener Brief" } }, "draft-resume-${draft.id}") { onResume(draft) }
            if (draft.title.isNotBlank()) Text(draft.text.take(120), style = ink(14f), maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(java.time.Instant.ofEpochMilli(entry.edited).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")), style = ink(12f))
            Text(draft.releaseSummary(), style = ink(12f))
            draft.attachment?.let { Text("Anhang: ${it.first}", style = ink(12f)) }
            StampButton("Löschen", "draft-delete-${draft.id}") { deleting = draft }
        }
    }
    deleting?.let { draft ->
        androidx.compose.material3.AlertDialog(onDismissRequest = { deleting = null },
            title = { Text("Entwurf löschen?", fontFamily = Kit.Display) },
            text = { Text("Brief und privater Anhang werden auf diesem Gerät gelöscht.", fontFamily = Kit.Display) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                if (draft.discard()) { deleting = null; refresh++ } else refresh++
            }, modifier = Modifier.heightIn(min = 48.dp).testTag("draft-delete-confirm")) { Text("Löschen") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { deleting = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Behalten") } })
    }
}

@Composable
internal fun DraftsPage(repository: LetterDraftRepository, onResume: (LetterDraft) -> Unit, onNew: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFFF3E6C8)).verticalScroll(rememberScrollState()).padding(16.dp)) {
        PaperSlip { DraftListPaper(repository, onResume, onNew) }
    }
}
