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
    val WoodDark = Color(0xFF3E2413)
    val Wood = Color(0xFF6E4122)
    val WoodLight = Color(0xFF9C6534)
    val Gold = Color(0xFFF3C24A)
    val GoldDark = Color(0xFFB27A1C)
    val Cream = Color(0xFFFFF4D8)
    val Parchment = Color(0xFFF6E7C3)
    val ParchmentDark = Color(0xFFE6CF9C)
    val Ink = Color(0xFF3B2614)
    val Red = Color(0xFFD9372B)
    val Green = Color(0xFF5DB235)
    val GreenDark = Color(0xFF2F6E17)
    val HudGlass = Color(0xB3261A10)
    val woodBrush = Brush.verticalGradient(listOf(WoodLight, Wood, WoodDark))
    val goldBrush = Brush.verticalGradient(listOf(Color(0xFFFFE08A), Gold, GoldDark))
    val greenBrush = Brush.verticalGradient(listOf(Color(0xFF9BE05E), Green, GreenDark))
    val parchmentBrush = Brush.verticalGradient(listOf(Color(0xFFFFF3D2), Parchment, ParchmentDark))
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

/** Bold cream lettering with a dark drop, readable on any part of the painting. */
@Composable
internal fun GameText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 14.sp,
    color: Color = Kit.Cream,
    weight: FontWeight = FontWeight.ExtraBold,
    maxLines: Int = 1,
    align: TextAlign? = null,
) {
    Text(
        text, modifier,
        style = TextStyle(
            color = color, fontSize = size, fontWeight = weight,
            shadow = Shadow(Color(0xCC1A0E05), Offset(0f, 3f), 4f),
        ),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = align,
    )
}

/** Red counter bubble with a white rim: the universal "something happened here". */
@Composable
internal fun CountBadge(count: Int, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    if (count <= 0) return
    Box(
        modifier
            .defaultMinSize(minWidth = size, minHeight = size)
            .shadow(3.dp, CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFFFF6A55), Kit.Red, Color(0xFF9E1F16))), RoundedCornerShape(50))
            .border(2.dp, Color.White, RoundedCornerShape(50))
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            style = TextStyle(color = Color.White, fontSize = (size.value * .55f).sp, fontWeight = FontWeight.Black),
        )
    }
}

/** A small carved name plank. */
@Composable
internal fun NamePlank(text: String, modifier: Modifier = Modifier, size: TextUnit = 12.sp) {
    Box(
        modifier
            .shadow(4.dp, RoundedCornerShape(8.dp))
            .background(Kit.woodBrush, RoundedCornerShape(8.dp))
            .border(1.5.dp, Kit.Gold.copy(alpha = .85f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) { GameText(text, size = size) }
}

/**
 * The floating icon above a building. It counter-scales against the camera so it
 * keeps a constant on-screen size like a game marker, bobs gently, and carries
 * the building's live counter.
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
    val pop by animateFloatAsState(if (selected) 1.2f else 1f, tween(220), label = "marker-pop")
    val unit = 1f / scope.zoom
    val box = 58.dp * unit
    val active = count > 0 || selected || showName
    val iconSize = (if (active) 40.dp else 26.dp) * unit
    // Buildings right under the HUD carry their marker below the roof line.
    val below = anchor.y < .2f
    val pinAt = MapPoint(anchor.x, if (below) anchor.y + .028f else anchor.y - .012f)
    with(scope) {
        Column(
            Modifier.at(pinAt, box, box + 12.dp * unit, pivotY = if (below) 0f else 1f).width(box)
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
                // Round gold-rimmed token: the same frame for every building marker.
                Box(
                    Modifier.size(iconSize + 10.dp * unit).graphicsLayer { alpha = if (active) 1f else .88f }
                        .shadow(4.dp * unit, CircleShape)
                        .background(Kit.goldBrush, CircleShape).padding(2.dp * unit)
                        .background(Brush.radialGradient(listOf(Color(0xFFFFF6DC), Color(0xFFE9CC8E))), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(painterResource(icon), null, Modifier.size(iconSize))
                }
                if (count > 0) CountBadge(count, Modifier.align(Alignment.TopEnd), size = 19.dp * unit)
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

/** The little gold pin that nails a marker to its roof. */
@Composable
private fun MarkerPin(up: Boolean, unit: Float) {
    Canvas(Modifier.size(14.dp * unit, 12.dp * unit)) {
        val path = androidx.compose.ui.graphics.Path().apply {
            if (up) { moveTo(size.width / 2, 0f); lineTo(size.width, size.height); lineTo(0f, size.height) }
            else { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height) }
            close()
        }
        drawPath(path, Kit.GoldDark)
        drawPath(path, Kit.WoodDark, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
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
    Box(Modifier.matchParentSize().background(Brush.radialGradient(listOf(Color.Transparent, Color(0x55000000)), radius = 1400f)))
    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(130.dp).background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent))))
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)))))
}

/** Player identity plate, top-left: avatar medallion, name and EP level. */
@Composable
internal fun PlayerPlate(emoji: String, name: String, level: String, modifier: Modifier = Modifier) {
    Box(modifier.height(44.dp)) {
        Column(
            Modifier.padding(start = 30.dp, top = 3.dp)
                .background(Kit.HudGlass, RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                .border(1.dp, Kit.Gold.copy(alpha = .5f), RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                .padding(start = 26.dp, end = 12.dp, top = 2.dp, bottom = 3.dp),
        ) {
            GameText(name, size = 13.sp, modifier = Modifier.widthIn(max = 110.dp))
            GameText(level, size = 10.sp, color = Kit.Gold, weight = FontWeight.Bold, modifier = Modifier.widthIn(max = 110.dp))
        }
        Box(
            Modifier.size(44.dp).shadow(5.dp, CircleShape).background(Kit.goldBrush, CircleShape)
                .padding(3.dp).background(Kit.woodBrush, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(emoji, fontSize = 20.sp) }
    }
}

/** A resource counter bar with its icon overlapping the left end (letters, EP …). */
@Composable
internal fun ResourcePill(icon: Int, value: String, description: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Box(
        modifier.height(34.dp).semantics { contentDescription = "$description: $value" }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.padding(start = 14.dp).height(24.dp).widthIn(min = 52.dp)
                .background(Kit.HudGlass, RoundedCornerShape(13.dp))
                .border(1.dp, Kit.Gold.copy(alpha = .5f), RoundedCornerShape(13.dp))
                .padding(start = 20.dp, end = 9.dp),
            contentAlignment = Alignment.CenterEnd,
        ) { GameText(value, size = 13.sp) }
        Image(painterResource(icon), null, Modifier.size(32.dp))
    }
}

/** Big round game button with a painted icon, gold rim, label and badge. */
@Composable
internal fun RoundHudButton(
    icon: Int?,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Int = 0,
    size: Dp = 62.dp,
    emoji: String? = null,
) {
    val sounds = LocalFiefSounds.current
    Column(modifier.width(size + 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size + 6.dp)) {
            Box(
                Modifier.align(Alignment.Center).size(size).shadow(6.dp, CircleShape)
                    .background(Kit.goldBrush, CircleShape).padding(3.dp)
                    .background(Brush.radialGradient(listOf(Color(0xFF9C6A3C), Kit.WoodDark)), CircleShape)
                    .clip(CircleShape)
                    .semantics { contentDescription = label; role = Role.Button }
                    .clickable { sounds?.play(FiefSound.TICK); onClick() },
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) Image(painterResource(icon), null, Modifier.size(size * .74f))
                else Text(emoji ?: "?", fontSize = (size.value * .42f).sp)
            }
            CountBadge(badge, Modifier.align(Alignment.TopEnd))
        }
        GameText(label, size = 11.sp, align = TextAlign.Center)
    }
}

/** Glossy primary game button (green) or secondary (gold). */
@Composable
internal fun GameButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true) {
    val sounds = LocalFiefSounds.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.heightIn(min = 48.dp).shadow(4.dp, shape)
            .background(Kit.WoodDark, shape).padding(bottom = 3.dp)
            .background(if (primary) Kit.greenBrush else Kit.goldBrush, shape)
            .border(1.5.dp, Color(0x66FFFFFF), shape)
            .clip(shape)
            .semantics { role = Role.Button }
            .clickable { sounds?.play(FiefSound.TICK); onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { GameText(text, size = 16.sp, color = Color.White) }
}

/**
 * Bottom card for the selected building, like a builder game's selection bar:
 * what it is, what is new there, one clear way in.
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
    val shape = RoundedCornerShape(20.dp)
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(top = 18.dp).fillMaxWidth().shadow(10.dp, shape)
                .background(Kit.woodBrush, shape).padding(4.dp)
                .background(Kit.parchmentBrush, RoundedCornerShape(16.dp))
                .border(1.dp, Kit.GoldDark, RoundedCornerShape(16.dp))
                .padding(start = 84.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Kit.Ink, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, color = Kit.Ink.copy(alpha = .75f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (news != null) Text(news, color = Kit.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.width(118.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GameButton(action, onAction, Modifier.fillMaxWidth())
                secondary?.let { (label, run) -> GameButton(label, run, Modifier.fillMaxWidth(), primary = false) }
            }
        }
        Box(
            Modifier.padding(start = 10.dp).size(76.dp).shadow(6.dp, CircleShape)
                .background(Kit.goldBrush, CircleShape).padding(3.dp)
                .background(Brush.radialGradient(listOf(Color(0xFFFFF2CF), Kit.ParchmentDark)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) Image(painterResource(icon), null, Modifier.size(58.dp))
            else Text(emoji ?: "", fontSize = 34.sp)
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 4.dp).size(30.dp).shadow(3.dp, CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFFFF6A55), Kit.Red)), CircleShape)
                .border(2.dp, Color.White, CircleShape).clip(CircleShape)
                .semantics { contentDescription = "Schließen"; role = Role.Button }
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) { Text("✕", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp) }
    }
}

/** Header strip for in-world building rooms: icon medallion, carved title, back and close. */
@Composable
internal fun RoomHeader(icon: Int, title: String, onBack: (() -> Unit)?, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        // Breadcrumb pill above the frame: says plainly "you left the village, here's the way back".
        Row(
            Modifier.padding(bottom = 6.dp).heightIn(min = 40.dp).shadow(4.dp, RoundedCornerShape(20.dp))
                .background(Color(0xE6261A10), RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onClose).padding(start = 10.dp, end = 14.dp)
                .semantics { contentDescription = "Zurück ins Dorf"; role = Role.Button },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("‹", color = Kit.Gold, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(6.dp))
            Image(painterResource(R.drawable.ic_home), null, Modifier.size(22.dp))
            Text(" Dorf", color = Kit.Cream, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("  ›  ", color = Kit.Gold.copy(alpha = .7f), fontSize = 14.sp)
            Text(title, color = Kit.Gold, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
        Row(
            Modifier.fillMaxWidth().height(56.dp)
                .background(Kit.woodBrush, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .padding(start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).shadow(4.dp, CircleShape)
                    .background(Kit.goldBrush, CircleShape).padding(2.dp)
                    .background(Brush.radialGradient(listOf(Color(0xFFFFF2CF), Kit.ParchmentDark)), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Image(painterResource(icon), null, Modifier.size(34.dp)) }
            GameText(title, size = 20.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
            if (onBack != null) SmallRound("‹", "Zurück", Brush.verticalGradient(listOf(Kit.WoodLight, Kit.WoodDark)), onBack)
            Spacer(Modifier.width(6.dp))
            SmallRound("✕", "Zurück ins Dorf", Brush.verticalGradient(listOf(Color(0xFFFF6A55), Kit.Red)), onClose)
        }
    }
}

@Composable
private fun SmallRound(glyph: String, description: String, brush: Brush, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).shadow(3.dp, CircleShape).background(brush, CircleShape)
            .border(2.dp, Kit.Cream, CircleShape).clip(CircleShape)
            .semantics { contentDescription = description; role = Role.Button }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp) }
}


/**
 * Soft fog over a building the player has not grown into yet: the painting
 * stays recognisable but quiet, so the village reads as "more to come", not as
 * a wall of icons.
 */
@Composable
internal fun FogPatch(anchor: MapPoint, scope: PlateScope) {
    val w = scope.width * .40f
    val unit = 1f / scope.zoom
    with(scope) {
        Box(Modifier.at(MapPoint(anchor.x, anchor.y + .01f), w, w * .85f, pivotY = .5f).size(w, w * .85f)) {
            Canvas(Modifier.fillMaxSize()) {
                // Several overlapping soft puffs instead of one disc: reads as mist, not a sticker.
                val puffs = listOf(Offset(.5f, .5f) to .50f, Offset(.32f, .56f) to .34f, Offset(.68f, .54f) to .34f,
                    Offset(.5f, .34f) to .32f, Offset(.42f, .70f) to .28f, Offset(.60f, .70f) to .28f)
                puffs.forEach { (c, r) ->
                    val center = Offset(size.width * c.x, size.height * c.y)
                    drawCircle(
                        Brush.radialGradient(listOf(Color(0xE6F1F3EC), Color(0x99E9EDE4), Color(0x00E9EDE4)), center = center, radius = size.minDimension * r),
                        radius = size.minDimension * r, center = center,
                    )
                }
            }
            Box(
                Modifier.align(Alignment.Center).size(30.dp * unit).shadow(3.dp * unit, CircleShape)
                    .background(Kit.goldBrush, CircleShape).padding(2.dp * unit)
                    .background(Brush.verticalGradient(listOf(Color(0xFF8B8F96), Color(0xFF5A5E66))), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("🔒", fontSize = 13.sp * unit) }
        }
    }
}

/** Gentle pulsing ring around a freshly built place. */
@Composable
internal fun FreshRing(anchor: MapPoint, scope: PlateScope, reducedMotion: Boolean) {
    val t = rememberInfiniteTransition(label = "fresh")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "fresh-p")
    val phase = if (reducedMotion) .5f else p
    val w = scope.width * .26f
    with(scope) {
        Canvas(Modifier.at(MapPoint(anchor.x, anchor.y + .01f), w, w * .6f, pivotY = .5f).size(w, w * .6f)) {
            drawOval(
                Kit.Gold.copy(alpha = (1f - phase) * .9f),
                topLeft = Offset(size.width * (.5f - .5f * (.6f + .4f * phase)), size.height * (.5f - .5f * (.6f + .4f * phase))),
                size = Size(size.width * (.6f + .4f * phase), size.height * (.6f + .4f * phase)),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx() / scope.zoom),
            )
        }
    }
}

/** "New building!" banner under the HUD: one tap flies to it. */
@Composable
internal fun FreshBanner(destination: ValleyDestination, onOpen: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.shadow(8.dp, RoundedCornerShape(18.dp)).background(Kit.woodBrush, RoundedCornerShape(18.dp))
            .padding(3.dp).background(Kit.parchmentBrush, RoundedCornerShape(15.dp))
            .clickable(onClick = onOpen).padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
            .semantics { contentDescription = "Neues Gebäude: ${destination.title}"; role = Role.Button },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(destination.iconRes()), null, Modifier.size(28.dp))
        Text(
            "Neu: ${destination.title}", Modifier.padding(horizontal = 8.dp).widthIn(max = 200.dp),
            fontSize = 13.sp, fontWeight = FontWeight.Black, color = Kit.Ink, maxLines = 1,
        )
        Box(
            Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onDismiss)
                .semantics { contentDescription = "Schließen"; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) { Text("✕", color = Kit.Ink, fontWeight = FontWeight.Bold) }
    }
}


/**
 * The single "what next" of the village: level, one task, one button. It is the
 * whole tutorial — no wall of text, no list of everything the app can do.
 */
@Composable
internal fun GuidePlank(step: GrowthStep, level: Int, maxLevel: Int, onGo: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.widthIn(max = 360.dp).fillMaxWidth().shadow(6.dp, RoundedCornerShape(16.dp))
            .background(Kit.woodBrush, RoundedCornerShape(16.dp)).padding(3.dp)
            .background(Kit.parchmentBrush, RoundedCornerShape(13.dp))
            .clickable(onClick = onGo).padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
            .semantics { contentDescription = "Nächster Schritt: ${step.task}"; role = Role.Button },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).background(Kit.goldBrush, CircleShape), contentAlignment = Alignment.Center) {
            Text("$level", fontWeight = FontWeight.Black, color = Kit.Ink, fontSize = 15.sp)
        }
        Column(Modifier.weight(1f).padding(horizontal = 9.dp)) {
            Text("Dorfstufe $level von $maxLevel · nächster Schritt", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Kit.GoldDark)
            Text(step.task, fontSize = 14.sp, fontWeight = FontWeight.Black, color = Kit.Ink, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        GameButton("Los", onGo)
    }
}
