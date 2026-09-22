package at.gregor.layermaxxing

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Instant
import kotlin.math.max
import kotlin.math.sin

/**
 * "Das Lehen": a second, spatial view on exactly one friendship.
 *
 * It owns no messenger rule of its own. The board opens the one letter composer
 * of the app, the letters are the letters of the conversation, and every number
 * is derived from accepted EP plus the device-local build ledger. Switching the
 * experiment off removes this screen and nothing else.
 *
 * Everything the screens show comes from [FiefScenes]; this file only renders.
 */
@Composable
internal fun CastlesScreen(
    ownUserId: Long?,
    friends: List<ApiClient.UserSummary>,
    settings: List<ApiClient.FriendshipSettings>,
    ep: ApiClient.EpOverview?,
    letters: List<ApiClient.Message>,
    builds: Map<Long, Set<BuildStep>>,
    creativeActive: Boolean,
    onBuild: (Long, BuildStep, Int) -> Unit,
    onBack: () -> Unit,
    onPeople: () -> Unit,
    onComposeLetter: (Long) -> Unit,
    letterAccess: (Long) -> LetterAction,
    opened: Map<Long, OpenedMessage>,
    act: ((suspend () -> Unit) -> Unit),
    token: String,
    api: ApiClient,
    onOpenLetter: (ApiClient.Message) -> Unit,
    onLockedTap: (ApiClient.Message) -> Unit,
    onProof: (ApiClient.Message) -> Unit,
    epLinkedLetterIds: Set<Long>,
    dismissedEpLetters: Map<Long, Boolean>,
    onProposeEp: (EpOpportunity) -> Unit,
    seenEarned: (Long) -> Int,
    onSeenEarned: (Long, Int) -> Unit,
) {
    // Sound follows the same "removed animations" switch as the idle motion: with
    // animations off the valley falls silent instead of ticking and thudding.
    val muted = rememberReducedMotion()
    val fiefSounds = rememberFiefSoundPlayer(LocalContext.current)
    CompositionLocalProvider(LocalFiefSounds provides fiefSounds.takeUnless { muted }) {
        CastlesScreenContent(
            ownUserId, friends, settings, ep, letters, builds, creativeActive, onBuild, onBack, onPeople,
            onComposeLetter, letterAccess, opened, act, token, api, onOpenLetter, onLockedTap,
            onProof, epLinkedLetterIds, dismissedEpLetters, onProposeEp, seenEarned, onSeenEarned,
        )
    }
}

@Composable
private fun CastlesScreenContent(
    ownUserId: Long?,
    friends: List<ApiClient.UserSummary>,
    settings: List<ApiClient.FriendshipSettings>,
    ep: ApiClient.EpOverview?,
    letters: List<ApiClient.Message>,
    builds: Map<Long, Set<BuildStep>>,
    creativeActive: Boolean,
    onBuild: (Long, BuildStep, Int) -> Unit,
    onBack: () -> Unit,
    onPeople: () -> Unit,
    onComposeLetter: (Long) -> Unit,
    letterAccess: (Long) -> LetterAction,
    opened: Map<Long, OpenedMessage>,
    act: ((suspend () -> Unit) -> Unit),
    token: String,
    api: ApiClient,
    onOpenLetter: (ApiClient.Message) -> Unit,
    onLockedTap: (ApiClient.Message) -> Unit,
    onProof: (ApiClient.Message) -> Unit,
    epLinkedLetterIds: Set<Long>,
    dismissedEpLetters: Map<Long, Boolean>,
    onProposeEp: (EpOpportunity) -> Unit,
    seenEarned: (Long) -> Int,
    onSeenEarned: (Long, Int) -> Unit,
) {
    val lockedByFriend = letters.filter { !it.unlocked }.groupingBy { it.peerId }.eachCount()
    val vales = Castles.vales(
        friends, settings, ep?.history.orEmpty(), lockedByFriend, builds, ownUserId, creativeActive,
    )
    var selectedId by remember { mutableStateOf<Long?>(null) }
    val vale = vales.firstOrNull { it.friendId == selectedId } ?: vales.firstOrNull()

    var place by remember { mutableStateOf(CastlePlace.VALE) }
    var sheet by remember { mutableStateOf<FiefSheet?>(null) }
    // The step just built, so the scene it lands in can settle it with a dust puff.
    var justBuilt by remember { mutableStateOf<BuildStep?>(null) }
    // Tapping a courier on the road must land on exactly its letter, not on "the
    // post office in general" — otherwise the road would be decoration again.
    var focusLetterId by remember { mutableStateOf<Long?>(null) }
    // Couriers stand where their own release rule puts them, so the road only
    // needs a slow tick; a letter's journey is measured in hours, not frames.
    var roadNow by remember { mutableStateOf(Instant.now().epochSecond) }
    LaunchedEffect(Unit) { while (true) { roadNow = Instant.now().epochSecond; delay(15_000) } }

    fun stepBack() {
        if (sheet != null) { sheet = null; return }
        when (val target = Castles.back(place)) {
            null -> onBack()
            else -> place = target
        }
    }

    BackHandler { stepBack() }

    LaunchedEffect(vale?.friendId) {
        // Switching friendship leaves no sheet, no camera and no place behind.
        place = CastlePlace.VALE
        sheet = null
        justBuilt = null
    }

    if (vale == null) {
        EmptyValley(onBack = onBack, onPeople = onPeople)
        return
    }
    val built = vale.built
    val friendLetters = letters.filter { it.peerId == vale.friendId }
    val action = letterAccess(vale.friendId)
    val post = Conversations.postStatus(friendLetters, opened.keys)

    Crossfade(targetState = place, animationSpec = tween(240), label = "fief-place") { current ->
        when (current) {
            CastlePlace.VALE -> ValleyScreen(
                vale = vale,
                built = built,
                deliveries = FiefScenes.deliveryStops(friendLetters, vale.friendName, roadNow),
                post = post,
                justBuilt = justBuilt,
                onRevealed = { justBuilt = null },
                onBack = ::stepBack,
                onEnterCourtyard = { place = CastlePlace.COURTYARD },
                onVisitFriend = { place = CastlePlace.FRIEND },
                onSignpost = { sheet = FiefSheet.Signpost },
                onDelivery = { letterId -> focusLetterId = letterId; place = CastlePlace.BOARD },
            )
            CastlePlace.FRIEND -> FriendCourtScreen(
                vale = vale,
                letters = friendLetters,
                onBack = ::stepBack,
                onPlaque = { sheet = FiefSheet.Plaque },
            )
            CastlePlace.COURTYARD -> CourtyardScreen(
                vale = vale,
                built = built,
                lettersEnabled = action.enabled,
                post = post,
                justBuilt = justBuilt,
                onRevealed = { justBuilt = null },
                onBack = ::stepBack,
                onBoard = { place = CastlePlace.BOARD },
                onTreasury = { place = CastlePlace.TREASURY },
                onBuildSite = { place = CastlePlace.BUILD_SITE },
            )
            // The composer is the app's single letter sheet, opened on top of the
            // board; dismissing it is exactly the COMPOSER -> BOARD step of the chain.
            CastlePlace.BOARD, CastlePlace.COMPOSER -> BoardScreen(
                vale = vale,
                letters = friendLetters,
                action = action,
                post = post,
                opened = opened,
                token = token,
                api = api,
                act = act,
                focusLetterId = focusLetterId,
                onFocusConsumed = { focusLetterId = null },
                onBack = ::stepBack,
                onCompose = { onComposeLetter(vale.friendId) },
                onOpenLetter = onOpenLetter,
                onLockedTap = onLockedTap,
                onProof = onProof,
                epLinkedLetterIds = epLinkedLetterIds,
                dismissedEpLetters = dismissedEpLetters,
                onProposeEp = onProposeEp,
                epEnabled = vale.epEnabled,
            )
            CastlePlace.TREASURY -> TreasuryScreen(
                vale = vale,
                ownUserId = ownUserId,
                ep = ep,
                lastSeenEarned = seenEarned(vale.friendId),
                onSeen = { onSeenEarned(vale.friendId, vale.myEarnedEp) },
                onBack = ::stepBack,
            )
            CastlePlace.BUILD_SITE -> BuildScreen(
                vale = vale,
                built = built,
                onBack = ::stepBack,
                onBuild = { step -> onBuild(vale.friendId, step, vale.myEarnedEp); justBuilt = step },
            )
        }
    }

    when (sheet) {
        FiefSheet.Plaque -> FiefSheet(onDismiss = { sheet = null }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(vale.avatarEmoji, fontSize = 28.sp, modifier = Modifier.padding(end = 10.dp))
                Text(vale.friendName, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            Text(
                "Abgeleitet aus den Ebenen-Punkten, die ${vale.friendName} von dir angenommen hat.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FiefSheet.Signpost -> FiefSheet(onDismiss = { sheet = null }) {
            vales.forEach { option ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickableRow { selectedId = option.friendId; sheet = null }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(option.avatarEmoji, fontSize = 22.sp, modifier = Modifier.width(38.dp))
                    Text(option.friendName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    if (option.friendId == vale.friendId) Text("✓", color = MaterialTheme.colorScheme.primary)
                }
            }
            TextButton(onClick = { sheet = FiefSheet.About }) { Text("Über dieses Tal") }
        }
        FiefSheet.About -> FiefSheet(onDismiss = { sheet = null }) {
            Text("Über dieses Tal", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(FIEF_ABOUT, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        null -> Unit
    }
}

private enum class FiefSheet { Plaque, Signpost, About }

internal const val FIEF_ABOUT =
    "Der Ausbau ist kosmetisch, entsteht nur auf diesem Gerät und wird nie an den Server gesendet. " +
        "Der Hof der Gegenseite ist aus den Ebenen-Punkten abgeleitet, die sie von dir angenommen hat; " +
        "was dort lokal gebaut wurde, weiß nur ihr Gerät. Ebenen-Punkte und Freundschaftsregeln gelten " +
        "beidseitig: nur angenommene Vorschläge zählen, Abgelehntes und Offenes nie."

// ---------------------------------------------------------------------------
// The scene renderer
// ---------------------------------------------------------------------------

/** Plate, sprite layers, camera and touch targets of one scene. */
@Composable
private fun SceneMap(
    scene: Scene,
    modifier: Modifier = Modifier,
    initialFocus: MapPoint? = null,
    initialZoom: Float = 1f,
    focusToken: Int = 0,
    onSprite: (SceneSprite) -> Unit,
    onPinchOut: (() -> Unit)? = null,
    overlay: @Composable (PlateScope.() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val sounds = LocalFiefSounds.current
    val reducedMotion = rememberReducedMotion()
    var viewport by remember(scene.id) { mutableStateOf(MapSize(0f, 0f)) }
    var zoom by remember(scene.id) { mutableStateOf(initialZoom) }
    var pan by remember(scene.id) { mutableStateOf(MapPoint(0f, 0f)) }
    var pressed by remember { mutableStateOf<String?>(null) }
    var settled by remember(scene.id) { mutableStateOf(false) }
    val minTouchPx = with(density) { 48.dp.toPx() }

    LaunchedEffect(viewport, scene.id) {
        if (viewport.width > 0f && !settled) {
            initialFocus?.let { pan = FiefMap.focusPan(it, viewport, scene.image, zoom) }
            settled = true
        }
        pan = FiefMap.clampPan(viewport, scene.image, zoom, pan)
    }

    LaunchedEffect(focusToken) {
        if (focusToken > 0 && viewport.width > 0f && initialFocus != null) {
            zoom = initialZoom
            pan = FiefMap.focusPan(initialFocus, viewport, scene.image, initialZoom)
        }
    }

    // One clock for the whole scene: no sprite owns its own transition.
    val clock = if (reducedMotion) 0f else {
        val transition = rememberInfiniteTransition(label = "fief-clock")
        val value by transition.animateFloat(
            initialValue = 0f, targetValue = 12f,
            animationSpec = infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Restart),
            label = "fief-clock-value",
        )
        value
    }

    // Once per scene a quiet light walks over the usable objects. It replaces
    // every tutorial chip: nothing is explained, the eye simply gets shown.
    var glintId by remember(scene.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(scene.id, reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        delay(500)
        Fief.glintOrder(scene).forEach { id ->
            glintId = id
            delay(600)
        }
        glintId = null
    }

    Box(
        modifier.fillMaxSize()
            .background(FIEF_BACKDROP)
            .clipToBounds()
            .onSizeChanged { viewport = MapSize(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(scene.id, viewport) {
                detectTransformGestures { centroid, gesturePan, gestureZoom, _ ->
                    val nextZoom = (zoom * gestureZoom).coerceIn(FiefMap.MIN_ZOOM, scene.maxZoom)
                    val ratio = nextZoom / zoom
                    val centerX = viewport.width / 2f
                    val centerY = viewport.height / 2f
                    val focused = MapPoint(
                        pan.x + gesturePan.x + (centroid.x - centerX - pan.x) * (1f - ratio),
                        pan.y + gesturePan.y + (centroid.y - centerY - pan.y) * (1f - ratio),
                    )
                    // Pinching below the minimum on a nested scene steps one level out.
                    if (onPinchOut != null && gestureZoom < 1f && zoom <= FiefMap.MIN_ZOOM + .001f) onPinchOut()
                    zoom = nextZoom
                    pan = FiefMap.clampPan(viewport, scene.image, nextZoom, focused)
                }
            }
            .pointerInput(scene.id, viewport, zoom, pan) {
                detectTapGestures(onDoubleTap = { offset ->
                    val hit = FiefMap.hitTest(
                        MapPoint(offset.x, offset.y), viewport, scene.image, zoom, pan, scene.sprites, minTouchPx,
                    )
                    if (hit != null) {
                        onSprite(hit)
                    } else {
                        // Smart zoom towards the tapped spot, and back out again.
                        val target = if (zoom > 1.2f) 1f else minOf(1.8f, scene.maxZoom)
                        val ratio = target / zoom
                        val focused = MapPoint(
                            pan.x + (offset.x - viewport.width / 2f - pan.x) * (1f - ratio),
                            pan.y + (offset.y - viewport.height / 2f - pan.y) * (1f - ratio),
                        )
                        zoom = target
                        pan = FiefMap.clampPan(viewport, scene.image, target, focused)
                    }
                })
            },
    ) {
        val fitted = FiefMap.fittedSize(viewport, scene.image)
        if (fitted.width <= 0f) return@Box
        val plateWidth = with(density) { fitted.width.toDp() }
        val plateHeight = with(density) { fitted.height.toDp() }
        val scope = PlateScope(plateWidth, plateHeight)

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(plateWidth, plateHeight).graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = pan.x
                    translationY = pan.y
                    transformOrigin = TransformOrigin.Center
                },
            ) {
                Image(
                    painter = painterResource(drawableFor(scene.plate)),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
                scene.sprites.forEach { sprite ->
                    if (sprite.asset == FiefAssets.PATH_OVERLAY) {
                        PathOverlay(plateWidth, plateHeight)
                    } else {
                        SpriteImage(
                            sprite = sprite,
                            scope = scope,
                            clock = clock,
                            pressed = pressed == sprite.id,
                            glowing = glintId == sprite.id,
                        )
                    }
                }
                overlay?.invoke(scope)
            }
        }

        // The touch targets live outside the camera layer, so a sprite keeps a
        // comfortable 48 dp target however far the valley is zoomed out.
        scene.sprites.filter { it.interactive }.forEach { sprite ->
            val center = FiefMap.screenPoint(sprite.hitPoint, viewport, scene.image, zoom, pan)
            val diameter = with(density) {
                max(minTouchPx, sprite.hitRadius * 2f * fitted.width * zoom).toDp()
            }
            Box(
                Modifier
                    .offset(
                        x = with(density) { center.x.toDp() } - diameter / 2,
                        y = with(density) { center.y.toDp() } - diameter / 2,
                    )
                    .size(diameter)
                    .semantics { contentDescription = sprite.label; role = Role.Button }
                    .pointerInput(sprite.id) {
                        detectTapGestures(
                            onPress = {
                                pressed = sprite.id
                                tryAwaitRelease()
                                pressed = null
                            },
                            onTap = {
                                sounds?.play(FiefSound.TICK)
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSprite(sprite)
                            },
                        )
                    },
            )
        }
    }
}

/** Plate-relative placement for sprites and for the close-up scenes. */
private class PlateScope(val width: Dp, val height: Dp) {
    fun Modifier.at(point: MapPoint, spriteWidth: Dp, spriteHeight: Dp, pivotY: Float = 1f): Modifier =
        offset(width * point.x - spriteWidth / 2, height * point.y - spriteHeight * pivotY)
}

@Composable
private fun SpriteImage(
    sprite: SceneSprite,
    scope: PlateScope,
    clock: Float,
    pressed: Boolean,
    glowing: Boolean,
) {
    val painter = painterResource(drawableFor(sprite.asset))
    val intrinsic = painter.intrinsicSize
    val aspect = if (intrinsic.width > 0f) intrinsic.height / intrinsic.width else 1f
    val spriteWidth = scope.width * sprite.widthFraction
    val spriteHeight = spriteWidth * aspect
    val density = LocalDensity.current
    val motion = sprite.motion
    val phase = if (motion == null) 0f else
        sin(2.0 * Math.PI * (clock / motion.periodSec + motion.phase)).toFloat()
    val scale by animateFloatAsState(if (pressed) .98f else 1f, tween(120), label = "sprite-press")
    val glow by animateFloatAsState(if (glowing) 1f else 0f, tween(500), label = "sprite-glint")

    with(scope) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier
                .at(sprite.anchor, spriteWidth, spriteHeight, sprite.pivotY)
                .size(spriteWidth, spriteHeight)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    if (motion != null) {
                        val travel = with(density) { motion.ampPx.toDp().toPx() }
                        when (motion.kind) {
                            MotionKind.SWAY -> rotationZ = phase * .6f
                            MotionKind.FLOAT -> translationY = phase * travel
                            MotionKind.GLINT -> alpha = .92f + .08f * (phase + 1f) / 2f
                        }
                    }
                    if (glow > 0f) alpha = 1f
                },
            contentScale = ContentScale.Fit,
        )
    }
}

/** The upgraded road: drawn along the same spline the courier walks. */
@Composable
private fun PathOverlay(plateWidth: Dp, plateHeight: Dp) {
    Canvas(Modifier.size(plateWidth, plateHeight)) {
        val points = FiefScenes.road.map { androidx.compose.ui.geometry.Offset(it.x * size.width, it.y * size.height) }
        points.zipWithNext().forEach { (a, b) ->
            // Gravel and a few planks on the existing track, not a new road.
            drawLine(Color(0x99CBBB92), a, b, strokeWidth = size.width * .013f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(Color(0x66806B45), a, b, strokeWidth = size.width * .005f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

// ---------------------------------------------------------------------------
// E1 valley, E2 courtyard
// ---------------------------------------------------------------------------

@Composable
private fun ValleyScreen(
    vale: Vale,
    built: Set<BuildStep>,
    deliveries: List<FiefScenes.DeliveryStop>,
    post: PostStatus,
    justBuilt: BuildStep?,
    onRevealed: () -> Unit,
    onBack: () -> Unit,
    onEnterCourtyard: () -> Unit,
    onVisitFriend: () -> Unit,
    onSignpost: () -> Unit,
    onDelivery: (Long) -> Unit,
) {
    val scene = remember(vale, built) { FiefScenes.valleyScene(vale, built) }
    var homeToken by remember(vale.friendId) { mutableStateOf(0) }
    val revealAnchor = justBuilt?.let(FiefScenes::valleyRevealAnchor)
    Box(Modifier.fillMaxSize()) {
        SceneMap(
            scene = scene,
            initialFocus = FiefScenes.OWN_CLEARING,
            initialZoom = 1.35f,
            focusToken = homeToken,
            onSprite = { sprite ->
                when (sprite.id) {
                    "home" -> onEnterCourtyard()
                    "friend" -> onVisitFriend()
                    "signpost" -> onSignpost()
                }
            },
            overlay = {
                // Every figure on the road is exactly one existing sealed letter,
                // standing where its own release rule puts it. No letter, no motion.
                deliveries.forEach { stop ->
                    CourierOnRoad(stop, width, height) { onDelivery(stop.letterId) }
                }
                // The post office lives in my yard, so its sign hangs at my hut:
                // one glance answers "are there letters, and do any wait on me".
                PlaceSign(
                    headline = "Deine Poststelle", post = post,
                    anchor = MapPoint(FiefScenes.OWN_CLEARING.x, .585f), scope = this,
                )
                // The other side gets its own sign, so the two ends of the road are
                // named places instead of two anonymous huts.
                PlaceSign(
                    headline = vale.friendName, post = null,
                    anchor = MapPoint(FiefScenes.FRIEND_CLEARING.x, .148f), scope = this,
                    subline = "besuchen",
                )
                revealAnchor?.let { DustReveal(it, onRevealed) }
            },
        )
        FiefPlaceBar("Zurück zu Chats", onBack, "Tal mit ${vale.friendName}", vale.creative)
        WoodToken("⌂", "Eigenen Hof zentrieren", Modifier.align(Alignment.BottomEnd)) { homeToken += 1 }
    }
}

/**
 * The read-only visit to the other side's court.
 *
 * It shows only what is honestly derivable here: how far their place has grown
 * from the EP they accepted from me, and my own letters that are with them. The
 * scene has no interactive sprite at all — a visit looks, it never acts.
 */
@Composable
private fun FriendCourtScreen(
    vale: Vale,
    letters: List<ApiClient.Message>,
    onBack: () -> Unit,
    onPlaque: () -> Unit,
) {
    val scene = remember(vale) { FiefScenes.friendCourtScene(vale) }
    val visit = remember(letters) { FiefScenes.visitStatus(letters) }
    Box(Modifier.fillMaxSize()) {
        SceneMap(
            scene = scene,
            onPinchOut = onBack,
            onSprite = {},
            overlay = {
                PlaceSign(
                    headline = "${vale.friendName}s Hof", post = visit,
                    anchor = MapPoint(.225f, .555f), scope = this,
                    countLabel = "von dir",
                )
                WoodSign(
                    "Nur Geteiltes",
                    "Was ${vale.friendName} hier selbst gebaut hat, weiß nur ihr Gerät. " +
                        "Dieser Hof wächst aus den Ebenen-Punkten, die sie von dir angenommen hat.",
                    MapPoint(.50f, .845f), this,
                )
            },
        )
        FiefPlaceBar("Zurück ins Tal", onBack, "Besuch bei ${vale.friendName}", vale.creative)
        WoodToken("ⓘ", "Woraus dieser Hof abgeleitet ist", Modifier.align(Alignment.BottomEnd), onClick = onPlaque)
    }
}

@Composable
private fun CourtyardScreen(
    vale: Vale,
    built: Set<BuildStep>,
    lettersEnabled: Boolean,
    post: PostStatus,
    justBuilt: BuildStep?,
    onRevealed: () -> Unit,
    onBack: () -> Unit,
    onBoard: () -> Unit,
    onTreasury: () -> Unit,
    onBuildSite: () -> Unit,
) {
    val scene = remember(vale, built, lettersEnabled) {
        FiefScenes.courtyardScene(vale, built, vale.epEnabled, lettersEnabled)
    }
    val revealAnchor = justBuilt?.let(FiefScenes::courtyardRevealAnchor)
    Box(Modifier.fillMaxSize()) {
        SceneMap(
            scene = scene,
            onPinchOut = onBack,
            onSprite = { sprite ->
                when (sprite.id) {
                    "cy-board" -> onBoard()
                    "cy-chest" -> onTreasury()
                    "cy-build" -> onBuildSite()
                }
            },
            overlay = {
                PlaceSign(
                    headline = "Poststelle", post = post,
                    anchor = MapPoint(.225f, .432f), scope = this,
                )
                revealAnchor?.let { DustReveal(it, onRevealed) }
            },
        )
        FiefPlaceBar("Zurück ins Tal", onBack, "Dein Hof", vale.creative)
    }
}

/**
 * The carved sign that names a place on the map and counts its post.
 *
 * It lives inside the camera layer, so it scales and pans like something nailed
 * into the world — anchored to its place, never a floating circle. Every real
 * destination carries one, so "what is this and how much lies there" is answered
 * without tapping anything.
 */
@Composable
private fun PlaceSign(
    headline: String,
    post: PostStatus?,
    anchor: MapPoint,
    scope: PlateScope,
    subline: String? = null,
    countLabel: String = "hier",
) {
    val description = buildString {
        append(headline)
        post?.let {
            append(": ${it.total} Briefe $countLabel")
            if (it.urgent > 0) append(", ${it.urgent} davon bereit oder warten auf dich")
        }
        subline?.let { append(", $it") }
    }
    with(scope) {
        Column(
            Modifier.at(anchor, 84.dp, 26.dp, pivotY = .5f)
                .semantics { contentDescription = description },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(color = Color(0xEE4A3F2E), contentColor = Color(0xFFF2E7D0), shape = RoundedCornerShape(6.dp)) {
                Text(
                    headline, Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 2.dp)) {
                if (post != null && post.total > 0) Surface(
                    color = Color(0xE6E7DCC0), contentColor = Color(0xFF3A3222), shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        "✉ ${post.total}", Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    )
                }
                if (post != null && post.urgent > 0) Surface(
                    color = Color(0xF0D9A441), contentColor = Color(0xFF32270F), shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        "✦ ${post.urgent}", Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    )
                }
                subline?.let {
                    Surface(
                        color = Color(0xE6E7DCC0), contentColor = Color(0xFF3A3222), shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            it, Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * One sealed letter on the road, as the courier its release mode deserves.
 *
 * There is no random walker any more: a figure exists only while a real letter
 * is still sealed, it stands at the position its own gate implies, and tapping
 * it opens exactly that letter. The glyph is the mode — rider, pigeon, owl,
 * herald, courtyard messenger — so the delivery kind is readable without a word.
 */
@Composable
private fun CourierOnRoad(
    stop: FiefScenes.DeliveryStop,
    plateWidth: Dp,
    plateHeight: Dp,
    onClick: () -> Unit,
) {
    val point = FiefScenes.roadPoint(stop.roadPosition)
    val glyph = when (stop.courier) {
        Courier.RIDER -> "🐎"
        Courier.PIGEON -> "🕊"
        Courier.OWL -> "🦉"
        Courier.HERALD -> "🎺"
        Courier.COURTYARD -> "🏮"
    }
    Box(
        Modifier
            .offset(plateWidth * point.x - 15.dp, plateHeight * point.y - 15.dp)
            .size(30.dp)
            .semantics { contentDescription = "${stop.label}. ${stop.courier.note}."; role = Role.Button }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = Color(0xEE4A3F2E),
            contentColor = Color(0xFFF2E7D0),
            shadowElevation = 2.dp,
        ) {
            Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                Text(glyph, fontSize = 14.sp)
            }
        }
    }
}

/**
 * A short, bounded dust puff where a newly built sprite settles.
 *
 * It plays once for ~800 ms and then reports back so the trigger clears. With
 * animations removed it draws nothing and reports immediately.
 */
@Composable
private fun PlateScope.DustReveal(anchor: MapPoint, onFinished: () -> Unit) {
    if (rememberReducedMotion()) {
        LaunchedEffect(anchor) { onFinished() }
        return
    }
    val progress = remember(anchor) { Animatable(0f) }
    LaunchedEffect(anchor) {
        progress.animateTo(1f, tween(800, easing = LinearEasing))
        onFinished()
    }
    val boxSize = width * .40f
    Canvas(Modifier.at(anchor, boxSize, boxSize, pivotY = .5f).size(boxSize)) {
        val t = progress.value
        val radius = size.minDimension * (.14f + .26f * t)
        val fade = (1f - t) * .5f
        drawCircle(Color(0xFFCFC3A4).copy(alpha = fade), radius = radius, center = center)
        drawCircle(Color(0xFFEAE1C8).copy(alpha = fade * .6f), radius = radius * .6f, center = center)
    }
}

// ---------------------------------------------------------------------------
// E3a: the board
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardScreen(
    vale: Vale,
    letters: List<ApiClient.Message>,
    action: LetterAction,
    post: PostStatus,
    opened: Map<Long, OpenedMessage>,
    token: String,
    api: ApiClient,
    act: ((suspend () -> Unit) -> Unit),
    focusLetterId: Long?,
    onFocusConsumed: () -> Unit,
    onBack: () -> Unit,
    onCompose: () -> Unit,
    onOpenLetter: (ApiClient.Message) -> Unit,
    onLockedTap: (ApiClient.Message) -> Unit,
    onProof: (ApiClient.Message) -> Unit,
    epLinkedLetterIds: Set<Long>,
    dismissedEpLetters: Map<Long, Boolean>,
    onProposeEp: (EpOpportunity) -> Unit,
    epEnabled: Boolean,
) {
    var now by remember { mutableStateOf(Instant.now().epochSecond) }
    LaunchedEffect(Unit) { while (true) { now = Instant.now().epochSecond; delay(1000) } }
    val opportunities = EpOpportunities.forFriend(
        vale.friendId, vale.friendName, letters, epEnabled, epLinkedLetterIds, dismissedEpLetters.keys,
    ).associateBy { it.letterId }
    val pages = remember(letters, opened.keys, now / 60, opportunities.keys) {
        FiefScenes.boardPages(letters, opened.keys, now, opportunities.keys)
    }
    // Page 0 holds what asks for attention; turning pages walks into older post.
    var page by remember(vale.friendId) { mutableStateOf(0) }
    val current = page.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val slots = pages.getOrElse(current) { emptyList() }
    var openLetterId by remember { mutableStateOf<Long?>(null) }
    var archiveOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val sounds = LocalFiefSounds.current

    fun turnTo(target: Int) {
        val next = target.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        if (next != current) {
            page = next
            sounds?.play(FiefSound.PAPER)
        }
    }

    // Arriving from a courier on the road: turn to the page that letter hangs on
    // and open it, so the figure outside and the envelope here are the same thing.
    LaunchedEffect(focusLetterId, pages.size) {
        val target = focusLetterId ?: return@LaunchedEffect
        val index = pages.indexOfFirst { slots -> slots.any { it.letterId == target } }
        if (index >= 0) {
            page = index
            openLetterId = target
        }
        onFocusConsumed()
    }

    Closeup(
        FiefAssets.BOARD_PLATE, onBack, "Zurück zum Hof",
        title = "Poststelle",
        creative = vale.creative,
        plateGesture = Modifier.pointerInput(pages.size, current) {
            val threshold = 48.dp.toPx()
            var travelled = 0f
            detectHorizontalDragGestures(
                onDragStart = { travelled = 0f },
                onDragEnd = {
                    if (travelled <= -threshold) turnTo(current + 1)
                    if (travelled >= threshold) turnTo(current - 1)
                },
            ) { _, dragAmount -> travelled += dragAmount }
        },
        barTrailing = {
            if (post.total > 0) {
                val description = buildString {
                    append("${post.total} Briefe an dieser Tafel")
                    if (post.urgent > 0) append(", ${post.urgent} bereit oder warten auf dich")
                }
                Row(
                    Modifier.semantics { contentDescription = description },
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    WoodPlate("✉ ${post.total}")
                    if (post.urgent > 0) Surface(
                        shape = RoundedCornerShape(9.dp), color = Color(0xF0D9A441), contentColor = Color(0xFF32270F),
                    ) {
                        Text(
                            "✦ ${post.urgent}", Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        },
    ) {
        PaperLabel("Erhalten", MapPoint(.295f, .215f), this)
        PaperLabel("Gesendet", MapPoint(.705f, .215f), this)
        slots.forEach { slot ->
            val message = letters.firstOrNull { it.id == slot.letterId } ?: return@forEach
            val locked = Conversations.letterState(message, message.id in opened.keys).locked
            Envelope(
                slot = slot,
                scope = this,
                onTap = {
                    sounds?.play(FiefSound.PAPER)
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (locked && message.incoming) onLockedTap(message) else openLetterId = message.id
                },
                onLongPress = { onProof(message) },
            )
        }
        if (pages.size > 1) PaperLabel(
            "Seite ${current + 1} / ${pages.size}", MapPoint(.50f, .715f), this,
            description = "Tafelseite ${current + 1} von ${pages.size}. Wischen oder blättern zeigt weitere Briefe.",
        )
        if (action.enabled) PlateSprite(
            asset = FiefAssets.QUILL, point = MapPoint(.80f, .845f), widthFraction = .22f, scope = this,
            description = "Feder: Brief an ${vale.friendName} schreiben", sound = FiefSound.PAPER, onClick = onCompose,
        )
        PlateSprite(
            asset = FiefAssets.PARCHMENT, point = MapPoint(.22f, .855f), widthFraction = .17f, scope = this,
            description = "Briefverzeichnis dieser Freundschaft", sound = FiefSound.PAPER, onClick = { archiveOpen = true },
        )
        PaperLabel("Schreiben", MapPoint(.80f, .885f), this, visible = action.enabled)
        PaperLabel("Verzeichnis", MapPoint(.22f, .895f), this)
        if (!action.enabled) action.reason?.let { reason ->
            WoodSign("Briefe aus", reason, MapPoint(.50f, .78f), this)
        }
    }
    if (pages.size > 1) Box(Modifier.fillMaxSize()) {
        if (current > 0) WoodToken(
            "‹", "Neuere Briefe, Seite $current von ${pages.size}",
            Modifier.align(Alignment.CenterStart),
        ) { turnTo(current - 1) }
        if (current < pages.size - 1) WoodToken(
            "›", "Ältere Briefe, Seite ${current + 2} von ${pages.size}",
            Modifier.align(Alignment.CenterEnd),
        ) { turnTo(current + 1) }
    }

    openLetterId?.let { id ->
        val message = letters.firstOrNull { it.id == id }
        if (message == null) openLetterId = null else FiefSheet(onDismiss = { openLetterId = null }) {
            LetterCard(
                message = message, opened = opened[message.id], now = now, token = token, api = api, act = act,
                onOpenLetter = onOpenLetter, onLockedTap = onLockedTap, onProof = onProof,
                creativeActive = vale.creative,
            )
            opportunities[message.id]?.let { opportunity ->
                EpOpportunityCard(opportunity) { onProposeEp(it); openLetterId = null }
            }
        }
    }

    if (archiveOpen) ModalBottomSheet(onDismissRequest = { archiveOpen = false }) {
        LetterStack(
            letters = letters, opened = opened, now = now, token = token, api = api, act = act,
            onOpenLetter = onOpenLetter, onLockedTap = onLockedTap, onProof = onProof,
            opportunities = opportunities, onProposeEp = onProposeEp,
            title = "Briefe mit ${vale.friendName}", creativeActive = vale.creative,
            modifier = Modifier.fillMaxWidth().height(520.dp),
        )
    }
}

@Composable
private fun Envelope(slot: BoardSlot, scope: PlateScope, onTap: () -> Unit, onLongPress: () -> Unit) {
    val painter = painterResource(drawableFor(slot.envelope))
    val intrinsic = painter.intrinsicSize
    val aspect = if (intrinsic.width > 0f) intrinsic.height / intrinsic.width else .7f
    val envelopeWidth = scope.width * .17f
    val envelopeHeight = envelopeWidth * aspect
    val reducedMotion = rememberReducedMotion()
    val shimmer = if (reducedMotion || slot.envelope != FiefAssets.ENV_READY) 1f else {
        val transition = rememberInfiniteTransition(label = "ready-${slot.letterId}")
        val value by transition.animateFloat(
            initialValue = .9f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(4_000, easing = LinearEasing), RepeatMode.Reverse),
            label = "ready-shimmer",
        )
        value
    }
    with(scope) {
        Box(
            Modifier.at(slot.anchor, envelopeWidth, envelopeHeight, pivotY = .1f)
                .size(width = envelopeWidth, height = envelopeHeight + 22.dp),
        ) {
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.size(envelopeWidth, envelopeHeight)
                    .graphicsLayer { alpha = shimmer }
                    .semantics { contentDescription = slot.label; role = Role.Button }
                    .pointerInput(slot.letterId) {
                        detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() })
                    },
                contentScale = ContentScale.Fit,
            )
            Row(
                Modifier.align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                slot.tag?.let { tag ->
                    Surface(color = Color(0xE8E7DCC0), contentColor = Color(0xFF3A3222), shape = RoundedCornerShape(6.dp)) {
                        Text(tag, Modifier.padding(horizontal = 5.dp, vertical = 1.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (slot.coinTag) Image(
                    painter = painterResource(drawableFor(FiefAssets.COIN)),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// E3b: the chest
// ---------------------------------------------------------------------------

@Composable
private fun TreasuryScreen(
    vale: Vale,
    ownUserId: Long?,
    ep: ApiClient.EpOverview?,
    lastSeenEarned: Int,
    onSeen: () -> Unit,
    onBack: () -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    val sounds = LocalFiefSounds.current
    val haptics = LocalHapticFeedback.current
    val dropped = FiefScenes.coinDropCount(vale.myEarnedEp, lastSeenEarned)
    var shown by remember(vale.friendId) { mutableStateOf(if (reducedMotion) vale.balance else vale.balance - dropped) }
    var chronicleOpen by remember { mutableStateOf(false) }
    LaunchedEffect(vale.friendId, vale.myEarnedEp) {
        // The one moment the chest celebrates: new coins fall in, one by one,
        // each with a light coin sound and a gentle tick.
        while (shown < vale.balance) {
            delay(250)
            shown += 1
            sounds?.play(FiefSound.COIN)
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        shown = vale.balance
        onSeen()
    }
    val coins = FiefScenes.treasuryCoins(shown.coerceAtLeast(0))

    Closeup(FiefAssets.TREASURY_PLATE, onBack, "Zurück zum Hof", title = "Schatzkammer", creative = vale.creative) {
        coins.forEach { point ->
            Image(
                painter = painterResource(drawableFor(FiefAssets.COIN)),
                contentDescription = null,
                modifier = with(this) {
                    Modifier.at(point, width * .05f, width * .05f, pivotY = .5f).size(width * .05f)
                },
            )
        }
        if (!vale.epEnabled) Image(
            painter = painterResource(drawableFor(FiefAssets.CHAIN_LOCK)),
            contentDescription = "Ebenen-Punkte sind in dieser Freundschaft aus",
            modifier = with(this) {
                Modifier.at(MapPoint(.57f, .57f), width * .60f, width * .60f, pivotY = .5f).size(width * .60f)
            },
        )
        PlateSprite(
            asset = null, point = MapPoint(.225f, .745f), widthFraction = .30f, scope = this,
            description = "Kontobuch: Chronik der angenommenen Ebenen-Punkte",
            onClick = { chronicleOpen = true },
        )
        Surface(
            modifier = with(this) {
                Modifier.at(MapPoint(.865f, .625f), 34.dp, 34.dp, pivotY = .5f).size(34.dp)
            },
            shape = RoundedCornerShape(6.dp),
            color = Color(0xE8C2A667),
            contentColor = Color(0xFF2E2413),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("${vale.balance}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }

    if (chronicleOpen) {
        val chronicle = ep?.history.orEmpty()
            .filter { it.beneficiaryId == ownUserId && it.proposerId == vale.friendId }
            .sortedByDescending { it.createdAt }
        FiefSheet(onDismiss = { chronicleOpen = false }) {
            if (chronicle.isEmpty()) Text(
                "Noch keine angenommenen Ebenen-Punkte.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ) else LazyColumn(
                Modifier.fillMaxWidth().height(380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(chronicle, key = { "chron-${it.id}" }) { proposal ->
                    Column {
                        Text("${formatEpPoints(proposal.points)} · ${proposal.title}", fontWeight = FontWeight.SemiBold)
                        Text(
                            formatDate(proposal.createdAt), fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        proposal.description?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// E3c: the build site
// ---------------------------------------------------------------------------

@Composable
private fun BuildScreen(
    vale: Vale,
    built: Set<BuildStep>,
    onBack: () -> Unit,
    onBuild: (BuildStep) -> Unit,
) {
    val plans = remember(built, vale.myEarnedEp, vale.epEnabled, vale.creative) {
        FiefScenes.buildPlans(built, vale.myEarnedEp, vale.epEnabled, vale.creative)
    }
    val sounds = LocalFiefSounds.current
    val haptics = LocalHapticFeedback.current
    var confirm by remember { mutableStateOf<BuildStep?>(null) }
    Closeup(FiefAssets.BUILD_PLATE, onBack, "Zurück zum Hof", title = "Baustelle", creative = vale.creative) {
        plans.forEach { plan -> Plan(plan, this) { if (plan.affordable) confirm = plan.step } }
    }
    confirm?.let { step ->
        FiefSheet(onDismiss = { confirm = null }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(drawableForPlan(step)),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    contentScale = ContentScale.Fit,
                )
                Column(Modifier.padding(start = 12.dp)) {
                    Text(step.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (vale.creative) Text("frei · Kreativmodus", fontSize = 13.sp, color = Color(0xFF8A671B))
                    else Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(step.cost) {
                            Image(
                                painter = painterResource(drawableFor(FiefAssets.COIN)),
                                contentDescription = null, modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
            // The one honest line: what visibly changes, and where. Without it a
            // plan is a decoration purchase, which is exactly what it must not be.
            Text(step.effect, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(
                modifier = Modifier.fillMaxWidth()
                    .clickableRow {
                        sounds?.play(FiefSound.BUILD)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBuild(step); confirm = null; onBack()
                    }
                    .semantics { role = Role.Button },
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF6E5A43),
                contentColor = Color(0xFFF3EAD6),
            ) {
                Text(
                    "Bauen", Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Plan(plan: PlanSlot, scope: PlateScope, onClick: () -> Unit) {
    val alpha = if (plan.affordable) 1f else .45f
    with(scope) {
        Column(
            Modifier.at(plan.anchor, width * .46f, width * .46f, pivotY = .5f)
                .size(width = width * .46f, height = width * .46f)
                .graphicsLayer { this.alpha = alpha }
                .semantics {
                    contentDescription = if (plan.free) "Bauplan ${plan.step.title}, frei im Kreativmodus"
                    else "Bauplan ${plan.step.title}, ${plan.coins} Münzen"
                    role = Role.Button
                }
                .pointerInput(plan.step) { detectTapGestures(onTap = { onClick() }) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(drawableForPlan(plan.step)),
                contentDescription = null,
                modifier = Modifier.size(width * .26f),
                contentScale = ContentScale.Fit,
            )
            // A plan on the wall carries its name, so nothing on this board is an
            // unlabelled sprite somebody has to buy to find out what it was.
            Surface(
                shape = RoundedCornerShape(5.dp), color = Color(0xE8E7DCC0), contentColor = Color(0xFF3A3222),
                modifier = Modifier.padding(top = 5.dp),
            ) {
                Text(
                    plan.step.title, Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                )
            }
            if (plan.free) Surface(
                shape = RoundedCornerShape(6.dp), color = Color(0xF0D9A441), contentColor = Color(0xFF32270F),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Text(
                    "frei", Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                )
            } else Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 6.dp)) {
                repeat(plan.coins) {
                    Image(
                        painter = painterResource(drawableFor(FiefAssets.COIN)),
                        contentDescription = null,
                        modifier = Modifier.size(width * .05f),
                    )
                }
            }
            if (plan.locked) Image(
                painter = painterResource(drawableFor(FiefAssets.CHAIN_LOCK)),
                contentDescription = "Ebenen-Punkte sind in dieser Freundschaft aus",
                modifier = Modifier.size(width * .18f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------

private val FIEF_BACKDROP = Color(0xFF232A24)
private val FIEF_PARCHMENT = Color(0xFFEFE3C8)
private val FIEF_PARCHMENT_INK = Color(0xFF3B3122)

/**
 * The place anchor of every scene: back token, carved place name, and — only
 * while creative mode is on — its small amber mark. One glance says where you
 * are and how to get out; no scene needs an explaining paragraph.
 */
@Composable
private fun BoxScope.FiefPlaceBar(
    backDescription: String,
    onBack: () -> Unit,
    title: String,
    creative: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    // The scene runs edge-to-edge underneath; the bar reserves its own status-bar
    // inset instead of letting a strip of app background sit above the map.
    Row(
        Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WoodToken("←", backDescription, onClick = onBack)
        WoodPlate(title)
        if (creative) {
            Spacer(Modifier.width(6.dp))
            CreativeTag()
        }
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

/** The carved name plate of a place. A label, never a button. */
@Composable
private fun WoodPlate(text: String) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = Color(0xE64A3F2E),
        contentColor = Color(0xFFF2E7D0),
        shadowElevation = 3.dp,
    ) {
        Text(
            text, Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        )
    }
}

/** The one visible mark of creative mode inside the valley. */
@Composable
private fun CreativeTag() {
    Surface(shape = RoundedCornerShape(9.dp), color = Color(0xF0D9A441), contentColor = Color(0xFF32270F)) {
        Text(
            "⚗ Kreativ", Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 11.sp, fontWeight = FontWeight.Bold,
        )
    }
}

/** A close-up: the plate fills the screen, nothing pans, one place bar. */
@Composable
private fun Closeup(
    plate: String,
    onBack: () -> Unit,
    backDescription: String,
    title: String,
    creative: Boolean = false,
    plateGesture: Modifier = Modifier,
    barTrailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable PlateScope.() -> Unit,
) {
    val density = LocalDensity.current
    var viewport by remember { mutableStateOf(MapSize(0f, 0f)) }
    Box(
        Modifier.fillMaxSize().background(FIEF_BACKDROP).clipToBounds()
            .onSizeChanged { viewport = MapSize(it.width.toFloat(), it.height.toFloat()) },
    ) {
        val fitted = FiefMap.fittedSize(viewport, FiefScenes.CLOSEUP_IMAGE)
        if (fitted.width > 0f) {
            val plateWidth = with(density) { fitted.width.toDp() }
            val plateHeight = with(density) { fitted.height.toDp() }
            Box(Modifier.fillMaxSize().then(plateGesture), contentAlignment = Alignment.Center) {
                Box(Modifier.size(plateWidth, plateHeight)) {
                    Image(
                        painter = painterResource(drawableFor(plate)),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds,
                    )
                    PlateScope(plateWidth, plateHeight).content()
                }
            }
        }
        FiefPlaceBar(backDescription, onBack, title, creative, barTrailing)
    }
}

/** One tappable thing on a close-up plate, optionally without its own image. */
@Composable
private fun PlateSprite(
    asset: String?,
    point: MapPoint,
    widthFraction: Float,
    scope: PlateScope,
    description: String,
    sound: FiefSound = FiefSound.TICK,
    onClick: () -> Unit,
) {
    val spriteWidth = scope.width * widthFraction
    val painter = asset?.let { painterResource(drawableFor(it)) }
    val aspect = painter?.intrinsicSize?.let { if (it.width > 0f) it.height / it.width else 1f } ?: .6f
    val spriteHeight = spriteWidth * aspect
    val haptics = LocalHapticFeedback.current
    val sounds = LocalFiefSounds.current
    with(scope) {
        Box(
            Modifier.at(point, spriteWidth, spriteHeight, pivotY = .5f)
                .size(width = maxOf(spriteWidth, 48.dp), height = maxOf(spriteHeight, 48.dp))
                .semantics { contentDescription = description; role = Role.Button }
                .pointerInput(description) {
                    detectTapGestures(onTap = {
                        sounds?.play(sound)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            if (painter != null) Image(
                painter = painter, contentDescription = null,
                modifier = Modifier.size(spriteWidth, spriteHeight), contentScale = ContentScale.Fit,
            )
        }
    }
}

/**
 * A one-word paper tag pinned to a close-up plate. Labels a thing where it
 * stands instead of explaining it somewhere else; never a button.
 */
@Composable
private fun PaperLabel(
    text: String,
    point: MapPoint,
    scope: PlateScope,
    visible: Boolean = true,
    description: String? = null,
) {
    if (!visible) return
    with(scope) {
        Surface(
            modifier = Modifier.at(point, 74.dp, 20.dp, pivotY = .5f)
                .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
            shape = RoundedCornerShape(6.dp),
            color = Color(0xD8E7DCC0),
            contentColor = Color(0xFF3A3222),
        ) {
            Text(
                text, Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** The two allowed words in the valley: an honest "off" sign with its reason. */
@Composable
private fun WoodSign(label: String, reason: String, point: MapPoint, scope: PlateScope) {
    var open by remember { mutableStateOf(false) }
    with(scope) {
        Surface(
            modifier = Modifier.at(point, 110.dp, 44.dp, pivotY = .5f)
                .size(width = 110.dp, height = 44.dp)
                .semantics { role = Role.Button }
                .pointerInput(label) { detectTapGestures(onTap = { open = true }) },
            shape = RoundedCornerShape(8.dp),
            color = Color(0xE6463B2C),
            contentColor = Color(0xFFF0E6D2),
        ) {
            Box(contentAlignment = Alignment.Center) { Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
    }
    if (open) FiefSheet(onDismiss = { open = false }) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(reason, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Back and home: two drawn wooden tokens, no text, no numbers. */
@Composable
private fun WoodToken(glyph: String, description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val sounds = LocalFiefSounds.current
    Surface(
        modifier = modifier.navigationBarsPadding().padding(10.dp).size(48.dp)
            .semantics { contentDescription = description; role = Role.Button }
            .pointerInput(description) { detectTapGestures(onTap = { sounds?.play(FiefSound.TICK); onClick() }) },
        shape = CircleShape,
        color = Color(0xE64A3F2E),
        contentColor = Color(0xFFF2E7D0),
        shadowElevation = 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center) { Text(glyph, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FiefSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    // A sheet of the valley is parchment on wood, not a grey system card: it rises
    // out of the same world the map is painted in.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = FIEF_PARCHMENT,
        contentColor = FIEF_PARCHMENT_INK,
        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { content() }
    }
}

@Composable
private fun EmptyValley(onBack: () -> Unit, onPeople: () -> Unit) {
    Box(Modifier.fillMaxSize().background(FIEF_BACKDROP)) {
        Image(
            painter = painterResource(drawableFor(FiefAssets.VALLEY_PLATE)),
            contentDescription = "Stilles, leeres Tal",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Surface(
            modifier = Modifier.align(Alignment.Center).padding(24.dp)
                .semantics { role = Role.Button }
                .pointerInput(Unit) { detectTapGestures(onTap = { onPeople() }) },
            shape = RoundedCornerShape(10.dp),
            color = Color(0xE6463B2C),
            contentColor = Color(0xFFF0E6D2),
        ) {
            Text(
                "Noch kein Tal", Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                fontWeight = FontWeight.Bold,
            )
        }
        WoodToken("←", "Zurück zu Chats", Modifier.align(Alignment.TopStart), onBack)
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) }

/** Zero animator scale means "remove animations": no idle, no glint, hard cuts. */
@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/** The one place base names become resources. */
private fun drawableFor(asset: String): Int = when (asset) {
    FiefAssets.VALLEY_PLATE -> R.drawable.fief_valley_base
    FiefAssets.COURTYARD_PLATE -> R.drawable.fief_courtyard_base
    FiefAssets.BOARD_PLATE -> R.drawable.fief_board_closeup
    FiefAssets.TREASURY_PLATE -> R.drawable.fief_treasury_closeup
    FiefAssets.BUILD_PLATE -> R.drawable.fief_build_closeup
    FiefAssets.HUT_STAGE0 -> R.drawable.fief_hut_stage0
    FiefAssets.HUT_STAGE1 -> R.drawable.fief_hut_stage1
    FiefAssets.HUT_STAGE2 -> R.drawable.fief_hut_stage2
    FiefAssets.HUT_STAGE3 -> R.drawable.fief_hut_stage3
    FiefAssets.WELL -> R.drawable.fief_cy_well
    FiefAssets.YARD_RING -> R.drawable.fief_yard_ring
    FiefAssets.BARN -> R.drawable.fief_barn_small
    FiefAssets.TOWER -> R.drawable.fief_tower_small
    FiefAssets.JETTY -> R.drawable.fief_jetty
    FiefAssets.FIELD -> R.drawable.fief_field
    FiefAssets.ORCHARD -> R.drawable.fief_orchard
    FiefAssets.BRIDGE -> R.drawable.fief_bridge
    FiefAssets.SIGNPOST -> R.drawable.fief_signpost
    FiefAssets.BOARD -> R.drawable.fief_cy_board
    FiefAssets.CHEST -> R.drawable.fief_cy_chest
    FiefAssets.BUILD_CORNER -> R.drawable.fief_cy_buildcorner
    FiefAssets.ENV_SEALED -> R.drawable.fief_env_sealed
    FiefAssets.ENV_READY -> R.drawable.fief_env_ready
    FiefAssets.ENV_OPEN -> R.drawable.fief_env_open
    FiefAssets.ENV_OUT -> R.drawable.fief_env_out
    FiefAssets.QUILL -> R.drawable.fief_quill
    FiefAssets.COIN -> R.drawable.fief_coin
    FiefAssets.CHAIN_LOCK -> R.drawable.fief_chain_lock
    else -> R.drawable.fief_parchment_card
}

/** A plan shows the real sprite of its step; the drawn path gets the signpost. */
private fun drawableForPlan(step: BuildStep): Int =
    if (step.sprite == FiefAssets.PATH_OVERLAY) R.drawable.fief_signpost else drawableFor(step.sprite)
