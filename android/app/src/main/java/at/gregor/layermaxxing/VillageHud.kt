package at.gregor.layermaxxing

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// Game UI kit for the village: one material language (oak, gold, parchment,
// glossy buttons) shared by HUD, markers, building cards and building rooms.
// ---------------------------------------------------------------------------

internal object Kit {
    // Premium-cozy kit: navy glass for HUD chrome (taken from the blue roofs),
    // warm ivory for content, antique gold only for progress/reward/selection.
    val Navy = Color(0xFF1C2A3E)
    val NavyLight = Color(0xFF2F4A6E)
    val Glass = Color(0xD91A2638)
    val Ivory = Color(0xFFFFF9EC)
    val IvoryDeep = Color(0xFFF3E5C6)
    val Trim = Color(0xFFD7A54A)
    val Ink = Color(0xFF2A2219)
    val InkSoft = Color(0xFF6B5B48)
    val Red = Color(0xFFE5483A)
    val Green = Color(0xFF55B33B)
    val GreenLedge = Color(0xFF2E7A1E)
    val Blue = Color(0xFF3C77C2)
    val BlueLedge = Color(0xFF1F4A85)
    // Legacy names kept for callers outside the HUD.
    val WoodDark = Navy
    val Wood = NavyLight
    val WoodLight = Color(0xFF46638C)
    val Gold = Color(0xFFF2C14E)
    val GoldDark = Color(0xFFB5832A)
    val Cream = Color(0xFFFFFBF2)
    val Parchment = Ivory
    val ParchmentDark = IvoryDeep
    val HudGlass = Glass
    val woodBrush = Brush.verticalGradient(listOf(NavyLight, Navy))
    val goldBrush = Brush.verticalGradient(listOf(Color(0xFFFFE18C), Gold, GoldDark))
    val greenBrush = Brush.verticalGradient(listOf(Color(0xFF6CC64F), Green))
    val parchmentBrush = Brush.verticalGradient(listOf(Ivory, IvoryDeep))

    val Display = FontFamily(Font(R.font.lilita_one))
    val Body = FontFamily(
        Font(R.font.nunito_semibold, FontWeight.Normal),
        Font(R.font.nunito_semibold, FontWeight.Medium),
        Font(R.font.nunito_semibold, FontWeight.SemiBold),
        Font(R.font.nunito_extrabold, FontWeight.Bold),
        Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
        Font(R.font.nunito_extrabold, FontWeight.Black),
    )
}

/**
 * Keeps the overview calm: at normal zoom only buildings with something new
 * (plus the selected one) wear a marker. Idle buildings stay tappable as painted
 * houses and show their marker once the player zooms in.
 */
internal object VillageCalm {
    fun visibleMarkers(counts: Map<ValleyDestination, Int>, closeUp: Boolean, selected: ValleyDestination?): Set<ValleyDestination> =
        if (closeUp) ValleyDestination.entries.toSet()
        else {
            // Even on a busy day the overview carries at most MAX_ACTIVE news markers:
            // the biggest piles of news win, the rest appear on zoom-in.
            val busiest = ValleyDestination.entries.filter { (counts[it] ?: 0) > 0 && it !in ALWAYS }
                .sortedByDescending { counts[it] ?: 0 }.take(MAX_ACTIVE)
            (ALWAYS + busiest + listOfNotNull(selected)).toSet()
        }

    const val MAX_ACTIVE = 3

    /** The meeting point is the messenger's door, so it is always marked. */
    val ALWAYS = setOf(ValleyDestination.CONVERSATIONS)
}

/** What the village HUD shows about the player; all values come from existing data. */
internal data class VillageHudInfo(
    val name: String = "Du",
    val emoji: String = "🙂",
    val level: String = "Ebenen-Neuling",
    val ep: Int = 0,
    val letters: Int = 0,
    /** Label of the way back to the messenger, or null when the game is the whole app. */
    val exitLabel: String? = null,
    /** Places already built; the rest lie under fog until the player grows into them. */
    val unlocked: Set<ValleyDestination> = ValleyDestination.entries.toSet(),
    /** Places that appeared since the player last looked: celebrated once. */
    val fresh: Set<ValleyDestination> = emptySet(),
    val onFreshSeen: () -> Unit = {},
    /** Real friends walk the village in their own profile colour. */
    val friendColors: List<Color> = emptyList(),
)

internal fun ValleyDestination.iconRes(): Int = when (this) {
    ValleyDestination.CONVERSATIONS -> R.drawable.ic_chat
    ValleyDestination.TOPICS -> R.drawable.ic_topics
    ValleyDestination.GROUPS -> R.drawable.ic_groups
    ValleyDestination.PEOPLE -> R.drawable.ic_people
    ValleyDestination.GLOSSARY -> R.drawable.ic_glossary
    ValleyDestination.SPARKS -> R.drawable.ic_sparks
    ValleyDestination.ARCHIVE -> R.drawable.ic_letter
    ValleyDestination.EP -> R.drawable.ic_ep
    ValleyDestination.SETTINGS -> R.drawable.ic_settings
}

/** Label without its legacy emoji prefix: the painted icon carries the meaning now. */
internal val ValleyDestination.title: String get() = label.substringAfter(' ')

/** Display lettering with a crisp short drop: the game's voice on top of the painting. */
@Composable
internal fun GameText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 14.sp,
    color: Color = Kit.Cream,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = 1,
    align: TextAlign? = null,
) {
    Text(
        text, modifier,
        style = TextStyle(
            color = color, fontSize = size, fontFamily = Kit.Display, fontWeight = weight,
            shadow = Shadow(Color(0x8C0B1220), Offset(0f, 2.5f), 1.5f), letterSpacing = .2.sp,
        ),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = align,
    )
}

/** Red counter with a white rim: the universal "something happened here". */
@Composable
internal fun CountBadge(count: Int, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    if (count <= 0) return
    Box(
        modifier
            .defaultMinSize(minWidth = size, minHeight = size)
            .shadow(2.dp, RoundedCornerShape(50))
            .background(Kit.Red, RoundedCornerShape(50))
            .border(1.5.dp, Color.White, RoundedCornerShape(50))
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            style = TextStyle(color = Color.White, fontSize = (size.value * .58f).sp, fontFamily = Kit.Display),
        )
    }
}

/** A light glass name tag. */
@Composable
internal fun NamePlank(text: String, modifier: Modifier = Modifier, size: TextUnit = 12.sp) {
    Box(
        modifier
            .shadow(3.dp, RoundedCornerShape(10.dp))
            .background(Kit.Glass, RoundedCornerShape(10.dp))
            .padding(horizontal = 9.dp, vertical = 2.dp),
    ) { GameText(text, size = size) }
}

/**
 * The floating bubble above a building. It counter-scales against the camera so
 * it keeps a constant on-screen size, bobs gently, and carries the live counter.
 * Idle bubbles are small and quiet; only real news or selection makes them big.
 */
@Composable
internal fun BuildingMarker(
    icon: Int,
    name: String,
    count: Int,
    anchor: MapPoint,
    scope: PlateScope,
    selected: Boolean,
    showName: Boolean,
    reducedMotion: Boolean,
    phase: Float = 0f,
) {
    val bob = if (reducedMotion) 0f else {
        val t = rememberInfiniteTransition(label = "marker-bob")
        val v by t.animateFloat(
            -1f, 1f, infiniteRepeatable(tween(1300 + (phase * 400).toInt(), easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "marker-bob-v",
        )
        v
    }
    val pop by animateFloatAsState(if (selected) 1.15f else 1f, tween(220), label = "marker-pop")
    val unit = 1f / scope.zoom
    val box = 54.dp * unit
    val active = count > 0 || selected || showName
    val iconSize = (if (active) 34.dp else 24.dp) * unit
    val below = anchor.y < .2f
    val pinAt = MapPoint(anchor.x, if (below) anchor.y + .028f else anchor.y - .014f)
    with(scope) {
        if (selected) GroundGlow(anchor, scope, Color(0xFFFFF1C4))
        Column(
            Modifier.at(pinAt, box, box + 10.dp * unit, pivotY = if (below) 0f else 1f).width(box)
                .graphicsLayer {
                    translationY = bob * 3.dp.toPx() * unit
                    scaleX = pop; scaleY = pop
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, if (below) 0f else 1f)
                }
                .semantics { contentDescription = if (count > 0) "$name, $count neu" else name },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (below) MarkerPin(up = true, unit = unit)
            Box(Modifier.size(box), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(iconSize + 12.dp * unit).graphicsLayer { alpha = if (active) 1f else .9f }
                        .shadow(5.dp * unit, CircleShape, ambientColor = Kit.Navy, spotColor = Kit.Navy)
                        .background(Brush.radialGradient(listOf(Color.White, Kit.Ivory, Kit.IvoryDeep)), CircleShape)
                        .border(2.dp * unit, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(painterResource(icon), null, Modifier.size(iconSize))
                }
                if (count > 0) CountBadge(count, Modifier.align(Alignment.TopEnd), size = 18.dp * unit)
            }
            if (!below) MarkerPin(up = false, unit = unit)
        }
        if (showName || selected) {
            val namePoint = if (below) MapPoint(anchor.x, anchor.y - .012f) else MapPoint(anchor.x, anchor.y + .004f)
            NamePlank(
                name,
                Modifier.at(namePoint, 120.dp * unit, 20.dp * unit, pivotY = if (below) 1f else 0f)
                    .width(120.dp * unit).wrapContentWidth(),
                size = 11.sp * unit,
            )
        }
    }
}

/** Soft light pooled on the ground under a building: selection without a sticker ring. */
@Composable
internal fun GroundGlow(anchor: MapPoint, scope: PlateScope, color: Color, strength: Float = .55f) {
    val w = scope.width * .30f
    with(scope) {
        Canvas(Modifier.at(MapPoint(anchor.x, anchor.y + .012f), w, w * .5f, pivotY = .5f).size(w, w * .5f)) {
            drawOval(
                Brush.radialGradient(
                    listOf(color.copy(alpha = strength), color.copy(alpha = strength * .35f), Color.Transparent),
                    center = center, radius = size.width / 2,
                ),
                topLeft = Offset.Zero, size = size,
            )
        }
    }
}

/** The little tail that points a bubble at its roof. */
@Composable
private fun MarkerPin(up: Boolean, unit: Float) {
    Canvas(Modifier.size(12.dp * unit, 8.dp * unit)) {
        val path = androidx.compose.ui.graphics.Path().apply {
            if (up) { moveTo(size.width / 2, 0f); lineTo(size.width, size.height); lineTo(0f, size.height) }
            else { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height) }
            close()
        }
        drawPath(path, Color.White)
    }
}

/** A courier walking the village road with a letter under his arm. */
@Composable
internal fun CourierWalker(point: MapPoint, scope: PlateScope, label: String, reducedMotion: Boolean, onClick: () -> Unit) {
    val hop = if (reducedMotion) 0f else {
        val t = rememberInfiniteTransition(label = "courier")
        val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse), label = "courier-hop")
        v
    }
    val unit = 1f / scope.zoom
    val s = 44.dp * unit
    with(scope) {
        Box(
            Modifier.at(point, s, s, pivotY = 1f).size(s)
                .graphicsLayer { translationY = -hop * 4.dp.toPx() * unit; rotationZ = (hop - .5f) * 6f }
                .semantics { contentDescription = label; role = Role.Button }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        ) {
            Image(painterResource(R.drawable.ic_courier), null, Modifier.fillMaxSize())
            Image(painterResource(R.drawable.ic_letter), null, Modifier.size(s * .42f).align(Alignment.TopEnd))
        }
    }
}

/**
 * A short in-world reaction when a building's counter rises: the icon lifts out
 * of the roof with "+N" and fades. Nothing is invented: it fires only on a real
 * increase of the counters that already drive the badges.
 */
@Composable
internal fun ActivityBursts(activities: List<VillageActivity>, scope: PlateScope, reducedMotion: Boolean) {
    val previous = remember { mutableStateMapOf<ValleyDestination, Int>() }
    var bursts by remember { mutableStateOf(listOf<Pair<Long, VillageActivity>>()) }
    var seeded by remember { mutableStateOf(false) }
    LaunchedEffect(activities) {
        val now = System.nanoTime()
        val fresh = activities.mapNotNull { a ->
            val before = previous[a.destination] ?: 0
            if (seeded && a.count > before) now + a.destination.ordinal to a.copy(count = a.count - before) else null
        }
        ValleyDestination.entries.forEach { d -> previous[d] = activities.firstOrNull { it.destination == d }?.count ?: 0 }
        seeded = true
        if (fresh.isNotEmpty() && !reducedMotion) bursts = bursts + fresh
    }
    bursts.forEach { (id, a) ->
        key(id) {
            val progress = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                progress.animateTo(1f, tween(1800, easing = FastOutSlowInEasing))
                bursts = bursts.filterNot { it.first == id }
            }
            val unit = 1f / scope.zoom
            with(scope) {
                Row(
                    Modifier.at(MapPoint(a.destination.anchor.x, a.destination.anchor.y - .10f), 80.dp * unit, 30.dp * unit, pivotY = 1f)
                        .graphicsLayer {
                            translationY = -progress.value * 46.dp.toPx() * unit
                            alpha = (1f - progress.value * progress.value)
                            val s = .7f + .5f * minOf(1f, progress.value * 4f)
                            scaleX = s; scaleY = s
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(painterResource(a.destination.iconRes()), null, Modifier.size(26.dp * unit))
                    GameText("+${a.count}", size = 16.sp * unit, color = Kit.Gold)
                }
            }
        }
    }
}

/** Slow soft cloud shadows sliding over the village: the world breathes. */
@Composable
internal fun CloudShadows(reducedMotion: Boolean, modifier: Modifier = Modifier) {
    if (reducedMotion) return
    val t = rememberInfiniteTransition(label = "clouds")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart), label = "clouds-x")
    Canvas(modifier.fillMaxSize()) {
        listOf(Triple(.15f, .22f, .55f), Triple(.62f, .58f, .42f), Triple(.35f, .86f, .5f)).forEachIndexed { i, (x0, y, w) ->
            val x = ((x0 + drift * (1f + i * .25f)) % 1.4f - .2f) * size.width
            val cw = size.width * w
            drawOval(
                Brush.radialGradient(listOf(Color(0x24000000), Color.Transparent), center = Offset(x + cw / 2, y * size.height), radius = cw / 2),
                topLeft = Offset(x, y * size.height - cw * .22f), size = Size(cw, cw * .44f),
            )
        }
    }
}

/** Screen-fixed vignette + HUD scrims: depth for the painting, legibility for the HUD. */
@Composable
internal fun BoxScope.WorldVignette() {
    Box(Modifier.matchParentSize().background(Brush.radialGradient(listOf(Color.Transparent, Color.Transparent, Color(0x4D0B1424)), radius = 1500f)))
    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(110.dp).background(Brush.verticalGradient(listOf(Color(0x730B1424), Color.Transparent))))
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(170.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x8C0B1424)))))
}

/** Player identity, top-left: avatar disc and a slim glass name tag. */
@Composable
internal fun PlayerPlate(emoji: String, name: String, level: String, modifier: Modifier = Modifier) {
    Box(modifier.height(46.dp)) {
        Column(
            Modifier.padding(start = 30.dp, top = 5.dp)
                .background(Kit.Glass, RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp))
                .padding(start = 24.dp, end = 14.dp, top = 3.dp, bottom = 4.dp),
        ) {
            GameText(name, size = 14.sp, modifier = Modifier.widthIn(max = 120.dp))
            Text(level, style = TextStyle(color = Kit.Gold, fontSize = 10.sp, fontFamily = Kit.Body, fontWeight = FontWeight.Bold), maxLines = 1,
                modifier = Modifier.widthIn(max = 120.dp))
        }
        Box(
            Modifier.size(46.dp).shadow(4.dp, CircleShape).background(Color.White, CircleShape)
                .padding(2.dp).background(Brush.radialGradient(listOf(Color(0xFF5B86BD), Kit.Navy)), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(emoji, fontSize = 22.sp) }
    }
}

/** A resource counter: glass bar with its icon overlapping the left end. */
@Composable
internal fun ResourcePill(icon: Int, value: String, description: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Box(
        modifier.height(34.dp).semantics { contentDescription = "$description: $value" }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.padding(start = 14.dp).height(26.dp).widthIn(min = 54.dp)
                .background(Kit.Glass, RoundedCornerShape(13.dp))
                .padding(start = 22.dp, end = 10.dp),
            contentAlignment = Alignment.CenterEnd,
        ) { GameText(value, size = 15.sp) }
        Image(painterResource(icon), null, Modifier.size(32.dp))
    }
}

/** Round glass HUD button with a painted icon: quieter than the call to action. */
@Composable
internal fun RoundHudButton(
    icon: Int?,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Int = 0,
    size: Dp = 58.dp,
    emoji: String? = null,
) {
    val sounds = LocalFiefSounds.current
    Column(modifier.width(size + 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size + 6.dp)) {
            Box(
                Modifier.align(Alignment.Center).size(size)
                    .shadow(6.dp, CircleShape, ambientColor = Kit.Navy, spotColor = Kit.Navy)
                    .background(Color.White.copy(alpha = .92f), CircleShape).padding(2.dp)
                    .background(Brush.radialGradient(listOf(Color(0xFF3E5F8C), Kit.Navy)), CircleShape)
                    .clip(CircleShape)
                    .semantics { contentDescription = label; role = Role.Button }
                    .clickable { sounds?.play(FiefSound.TICK); onClick() },
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) Image(painterResource(icon), null, Modifier.size(size * .72f))
                else Text(emoji ?: "?", fontSize = (size.value * .42f).sp)
            }
            CountBadge(badge, Modifier.align(Alignment.TopEnd))
        }
        GameText(label, size = 12.sp, align = TextAlign.Center)
    }
}

/** Flat game button with a solid ledge: green = do it, blue = secondary. */
@Composable
internal fun GameButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true) {
    val sounds = LocalFiefSounds.current
    val shape = RoundedCornerShape(14.dp)
    val face = if (primary) Kit.Green else Kit.Blue
    val ledge = if (primary) Kit.GreenLedge else Kit.BlueLedge
    Box(
        modifier.heightIn(min = 46.dp).shadow(3.dp, shape)
            .background(ledge, shape).padding(bottom = 3.5.dp)
            .background(Brush.verticalGradient(listOf(face.copy(red = minOf(1f, face.red + .08f), green = minOf(1f, face.green + .08f), blue = minOf(1f, face.blue + .08f)), face)), shape)
            .clip(shape)
            .semantics { role = Role.Button }
            .clickable { sounds?.play(FiefSound.TICK); onClick() }
            .padding(horizontal = 18.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        GameText(text, size = 17.sp, color = Color.White)
    }
}

/**
 * Bottom card for the selected building: what it is, what is new there, one
 * clear way in. Ivory with a single fine gold line, like a printed game card.
 */
@Composable
internal fun BuildingCard(
    icon: Int?,
    emoji: String?,
    title: String,
    detail: String,
    news: String?,
    action: String,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: Pair<String, () -> Unit>? = null,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(top = 14.dp).fillMaxWidth()
                .shadow(16.dp, shape, ambientColor = Kit.Navy, spotColor = Kit.Navy)
                .background(Kit.parchmentBrush, shape)
                .padding(3.dp).border(1.dp, Kit.Trim.copy(alpha = .7f), RoundedCornerShape(21.dp))
                .padding(start = 76.dp, end = 12.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = TextStyle(color = Kit.Ink, fontSize = 20.sp, fontFamily = Kit.Display), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, style = TextStyle(color = Kit.InkSoft, fontSize = 12.5.sp, fontFamily = Kit.Body, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (news != null) Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(Kit.Red, CircleShape))
                    Text(" $news", style = TextStyle(color = Kit.Red, fontSize = 12.5.sp, fontFamily = Kit.Body, fontWeight = FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.width(112.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GameButton(action, onAction, Modifier.fillMaxWidth())
                secondary?.let { (label, run) -> GameButton(label, run, Modifier.fillMaxWidth(), primary = false) }
            }
        }
        Box(
            Modifier.padding(start = 12.dp).size(64.dp)
                .shadow(8.dp, CircleShape, ambientColor = Kit.Navy, spotColor = Kit.Navy)
                .background(Color.White, CircleShape).padding(2.5.dp)
                .background(Brush.radialGradient(listOf(Color.White, Kit.Ivory, Kit.IvoryDeep)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) Image(painterResource(icon), null, Modifier.size(50.dp))
            else Text(emoji ?: "", fontSize = 30.sp)
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 22.dp, end = 10.dp).size(28.dp).clip(CircleShape)
                .background(Kit.Navy.copy(alpha = .08f), CircleShape)
                .semantics { contentDescription = "Schließen"; role = Role.Button }
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) { Text("✕", color = Kit.InkSoft, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
    }
}

/** Header for in-world building rooms: breadcrumb back to the village, then a navy title bar. */
@Composable
internal fun RoomHeader(icon: Int, title: String, onBack: (() -> Unit)?, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(bottom = 8.dp).heightIn(min = 40.dp)
                .background(Kit.Glass, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onClose).padding(start = 12.dp, end = 16.dp)
                .semantics { contentDescription = "Zurück ins Dorf"; role = Role.Button },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GameText("‹", size = 22.sp, color = Kit.Gold)
            Spacer(Modifier.width(8.dp))
            GameText("Dorf", size = 15.sp)
            GameText("  ›  ", size = 15.sp, color = Color.White.copy(alpha = .5f))
            GameText(title, size = 15.sp, color = Kit.Gold)
        }
        Row(
            Modifier.fillMaxWidth().height(60.dp)
                .background(Brush.verticalGradient(listOf(Kit.NavyLight, Kit.Navy)), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(start = 10.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).background(Color.White, CircleShape).padding(2.dp)
                    .background(Brush.radialGradient(listOf(Color.White, Kit.Ivory, Kit.IvoryDeep)), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Image(painterResource(icon), null, Modifier.size(34.dp)) }
            GameText(title, size = 22.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
            if (onBack != null) SmallRound("‹", "Zurück", onBack)
            Spacer(Modifier.width(6.dp))
            SmallRound("✕", "Zurück ins Dorf", onClose)
        }
    }
}

@Composable
private fun SmallRound(glyph: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f), CircleShape)
            .semantics { contentDescription = description; role = Role.Button }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { GameText(glyph, size = 18.sp) }
}


/**
 * A place the player has not grown into yet: the painting is drained of colour
 * and veiled in cool mist, with one small lock chip. It reads as "not yet",
 * while the building itself stays recognisable.
 */
@Composable
internal fun FogPatch(anchor: MapPoint, scope: PlateScope) {
    val w = scope.width * .34f
    val unit = 1f / scope.zoom
    with(scope) {
        Box(Modifier.at(MapPoint(anchor.x, anchor.y - .02f), w, w * .95f, pivotY = .5f).size(w, w * .95f)) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2, size.height / 2)
                val r = size.minDimension * .5f
                // 1) take most (not all) colour out of the painting underneath
                drawCircle(
                    Brush.radialGradient(listOf(Color.Gray.copy(alpha = .7f), Color.Gray.copy(alpha = .45f), Color.Transparent), center = c, radius = r),
                    radius = r, center = c, blendMode = androidx.compose.ui.graphics.BlendMode.Saturation,
                )
                // 2) cool dusk shade instead of a white wash: "not yet lit"
                drawCircle(
                    Brush.radialGradient(listOf(Color(0x5C1E2E48), Color(0x331E2E48), Color.Transparent), center = c, radius = r),
                    radius = r, center = c,
                )
                // 3) a thin ground mist only around the base, so it reads as mist, not a mask
                listOf(Offset(.36f, .74f) to .26f, Offset(.62f, .76f) to .28f, Offset(.5f, .82f) to .22f).forEach { (o, rr) ->
                    val cc = Offset(size.width * o.x, size.height * o.y)
                    drawOval(
                        Brush.radialGradient(listOf(Color(0x66D9E3EE), Color(0x1FD9E3EE), Color.Transparent), center = cc, radius = size.minDimension * rr),
                        topLeft = Offset(cc.x - size.minDimension * rr, cc.y - size.minDimension * rr * .5f),
                        size = Size(size.minDimension * rr * 2, size.minDimension * rr),
                    )
                }
            }
            LockChip(Modifier.align(Alignment.Center), unit)
        }
    }
}

/** Small drawn padlock on a glass chip (no emoji). */
@Composable
private fun LockChip(modifier: Modifier, unit: Float) {
    Box(
        modifier.size(24.dp * unit).shadow(2.dp * unit, CircleShape).background(Kit.Glass, CircleShape)
            .border(1.5.dp * unit, Color.White.copy(alpha = .85f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(11.dp * unit, 13.dp * unit)) {
            val bodyTop = size.height * .45f
            drawArc(
                Color.White, 180f, 180f, false,
                topLeft = Offset(size.width * .18f, size.height * .05f),
                size = Size(size.width * .64f, size.height * .75f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.width * .16f),
            )
            drawRoundRect(
                Color.White, topLeft = Offset(0f, bodyTop), size = Size(size.width, size.height - bodyTop),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * .18f),
            )
            drawCircle(Kit.Navy, radius = size.width * .1f, center = Offset(size.width / 2, bodyTop + (size.height - bodyTop) * .45f))
        }
    }
}

/** A freshly built place breathes warm light on the ground (no outline ring). */
@Composable
internal fun FreshRing(anchor: MapPoint, scope: PlateScope, reducedMotion: Boolean) {
    val t = rememberInfiniteTransition(label = "fresh")
    val p by t.animateFloat(.35f, .8f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "fresh-p")
    GroundGlow(anchor, scope, Color(0xFFFFD36B), strength = if (reducedMotion) .6f else p)
}

/** "New building" toast under the HUD: one tap flies to it. */
@Composable
internal fun FreshBanner(destination: ValleyDestination, onOpen: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.shadow(8.dp, RoundedCornerShape(22.dp), ambientColor = Kit.Navy, spotColor = Kit.Navy)
            .background(Kit.Glass, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onOpen).padding(start = 6.dp, end = 2.dp, top = 4.dp, bottom = 4.dp)
            .semantics { contentDescription = "Neues Gebäude: ${destination.title}"; role = Role.Button },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
            Image(painterResource(destination.iconRes()), null, Modifier.size(26.dp))
        }
        GameText("Neu: ", size = 14.sp, color = Kit.Gold, modifier = Modifier.padding(start = 8.dp))
        GameText(destination.title, size = 14.sp, modifier = Modifier.widthIn(max = 170.dp))
        Box(
            Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onDismiss)
                .semantics { contentDescription = "Schließen"; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) { GameText("✕", size = 13.sp, color = Color.White.copy(alpha = .7f)) }
    }
}

/**
 * The single "what next" of the village: level chip, one task, one button.
 */
@Composable
internal fun GuidePlank(step: GrowthStep, level: Int, maxLevel: Int, onGo: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.widthIn(max = 360.dp).fillMaxWidth()
            .shadow(10.dp, shape, ambientColor = Kit.Navy, spotColor = Kit.Navy)
            .background(Kit.parchmentBrush, shape).clip(shape)
            .clickable(onClick = onGo).padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
            .semantics { contentDescription = "Nächster Schritt: ${step.task}"; role = Role.Button },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Level as a progress ring: gold arc = how much of the village stands.
        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val st = 3.5.dp.toPx()
                drawCircle(Kit.Navy, radius = size.minDimension / 2)
                drawArc(Color.White.copy(alpha = .18f), 0f, 360f, false, topLeft = Offset(st, st), size = Size(size.width - 2 * st, size.height - 2 * st),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(st))
                drawArc(Kit.Gold, -90f, 360f * level / maxLevel, false, topLeft = Offset(st, st), size = Size(size.width - 2 * st, size.height - 2 * st),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(st, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
            GameText("$level", size = 16.sp)
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text("Nächster Schritt", style = TextStyle(color = Kit.InkSoft, fontSize = 11.sp, fontFamily = Kit.Body, fontWeight = FontWeight.Bold))
            Text(step.task, style = TextStyle(color = Kit.Ink, fontSize = 16.sp, fontFamily = Kit.Display), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        GameButton("Los", onGo)
    }
}
