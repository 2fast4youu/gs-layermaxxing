package at.gregor.layermaxxing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The village is alive: people walk the painted paths, chimneys smoke, the
 * river glints, birds cross the forest and the light follows the real clock.
 *
 * Everything here is ambience bound to real state, never fake activity:
 * the population grows with the places the player actually unlocked, smoke
 * only rises from lived-in houses, and friends walk in their own colour.
 * Coordinates are normalised to the village plate (1024 x 1536).
 */
internal object VillageLife {

    /** Walkways traced from the plate's cobblestone (pixel-mask + review). */
    val routes: List<List<MapPoint>> = listOf(
        // north road: tavern → notice board → gate
        listOf(MapPoint(.605f, .203f), MapPoint(.695f, .221f), MapPoint(.820f, .297f), MapPoint(.863f, .315f), MapPoint(.891f, .315f)),
        // central crossing below the tavern
        listOf(MapPoint(.375f, .310f), MapPoint(.461f, .349f), MapPoint(.480f, .349f), MapPoint(.539f, .383f), MapPoint(.574f, .383f), MapPoint(.605f, .404f), MapPoint(.637f, .404f)),
        // shrine promenade
        listOf(MapPoint(.715f, .594f), MapPoint(.691f, .578f), MapPoint(.621f, .578f), MapPoint(.574f, .560f), MapPoint(.516f, .562f), MapPoint(.473f, .591f)),
        // south: farm lane → treasury
        listOf(MapPoint(.543f, .698f), MapPoint(.648f, .763f), MapPoint(.645f, .779f), MapPoint(.578f, .823f), MapPoint(.578f, .862f), MapPoint(.656f, .914f)),
        // town-hall forecourt
        listOf(MapPoint(.418f, .224f), MapPoint(.391f, .221f), MapPoint(.363f, .240f), MapPoint(.332f, .250f), MapPoint(.285f, .253f)),
        // post office lane
        listOf(MapPoint(.438f, .872f), MapPoint(.449f, .883f), MapPoint(.449f, .896f), MapPoint(.469f, .909f), MapPoint(.469f, .927f), MapPoint(.566f, .987f)),
    )

    /** Chimney tops and which place has to be lived in for smoke to rise. */
    val chimneys: List<Pair<MapPoint, ValleyDestination?>> = listOf(
        MapPoint(.562f, .190f) to ValleyDestination.CONVERSATIONS, // tavern
        MapPoint(.171f, .422f) to ValleyDestination.GLOSSARY,      // library
        MapPoint(.325f, .585f) to null,                            // own cottage (always lived in)
        MapPoint(.784f, .611f) to null,                            // farm house
    )
    val water = listOf(MapPoint(.029f, .677f), MapPoint(.070f, .720f), MapPoint(.107f, .768f), MapPoint(.150f, .860f), MapPoint(.195f, .938f))
    val lanterns = listOf(MapPoint(.124f, .778f), MapPoint(.269f, .752f), MapPoint(.776f, .221f), MapPoint(.407f, .482f))
    val crystal = MapPoint(.740f, .485f)
    val windows = listOf(MapPoint(.508f, .260f), MapPoint(.306f, .690f), MapPoint(.798f, .690f), MapPoint(.174f, .182f), MapPoint(.250f, .360f), MapPoint(.420f, .840f))

    /** More places in use → more people in the streets (2 … 7). */
    fun population(unlocked: Int): Int = (unlocked + 1).coerceIn(2, 7)

    enum class Daylight { MORNING, DAY, EVENING, NIGHT }

    fun daylight(hour: Int): Daylight = when (hour) {
        in 6..8 -> Daylight.MORNING
        in 9..16 -> Daylight.DAY
        in 17..20 -> Daylight.EVENING
        else -> Daylight.NIGHT
    }

    fun length(route: List<MapPoint>): Float =
        route.zipWithNext().sumOf { (a, b) -> hypot(a.x - b.x, (a.y - b.y) * 1.5f).toDouble() }.toFloat()

    /** Point at fraction [f] (0..1) of the route's arc length; y is scaled to plate aspect. */
    fun pointAt(route: List<MapPoint>, f: Float): MapPoint {
        val total = length(route)
        if (total <= 0f) return route.first()
        var left = f.coerceIn(0f, 1f) * total
        for ((a, b) in route.zipWithNext()) {
            val seg = hypot(a.x - b.x, (a.y - b.y) * 1.5f)
            if (left <= seg) {
                val t = if (seg == 0f) 0f else left / seg
                return MapPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            }
            left -= seg
        }
        return route.last()
    }

    /**
     * Walker [index] at time [seconds]: walks there and back with a short
     * pause at each end. Returns the position and whether it faces left.
     */
    fun walker(index: Int, seconds: Float): Pair<MapPoint, Boolean> {
        val route = routes[index % routes.size]
        val speed = .022f + (index % 3) * .004f          // plate widths per second
        val walk = length(route) / speed
        val pause = 2.2f + index % 2
        val cycle = 2 * (walk + pause)
        val t = ((seconds + index * 7.3f) % cycle + cycle) % cycle
        val (f, back) = when {
            t < walk -> t / walk to false
            t < walk + pause -> 1f to false
            t < 2 * walk + pause -> 1f - (t - walk - pause) / walk to true
            else -> 0f to true
        }
        val p = pointAt(route, f)
        val ahead = pointAt(route, (f + if (back) -.01f else .01f).coerceIn(0f, 1f))
        return p to (ahead.x < p.x)
    }
}

/** Test/preview seam: pin the village clock to an hour. */
internal val LocalVillageHour = compositionLocalOf<Int?> { null }

private val Tunics = listOf(Color(0xFF3F6FB0), Color(0xFFB0473F), Color(0xFF4F8A4A), Color(0xFF8A5BB0), Color(0xFFC9973B), Color(0xFF2F7F86), Color(0xFF7A4E2D))

/**
 * Draws on the camera layer, so everything zooms and pans with the painting.
 * One frame clock drives the whole village; reduced motion freezes it.
 */
@Composable
internal fun VillageLifeLayer(
    unlocked: Set<ValleyDestination>,
    friendColors: List<Color>,
    reducedMotion: Boolean,
    tap: Pair<MapPoint, Long>?,
) {
    var seconds by remember { mutableStateOf(3f) }
    if (!reducedMotion) LaunchedEffect(Unit) {
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) withInfiniteAnimationFrameMillis { seconds = 3f + (it - start) / 1000f }
    }
    val pinned = LocalVillageHour.current
    var hour by remember { mutableStateOf(pinned ?: java.time.LocalTime.now().hour) }
    if (pinned == null) LaunchedEffect(Unit) {
        while (true) { delay(60_000); hour = java.time.LocalTime.now().hour }
    }
    val light = VillageLife.daylight(hour)
    val people = VillageLife.population(unlocked.size)
    val night = light == VillageLife.Daylight.NIGHT
    val evening = light == VillageLife.Daylight.EVENING

    Canvas(Modifier.fillMaxSize()) {
        val s = seconds
        // --- light of the hour: a wash over the painting --------------------------
        when (light) {
            VillageLife.Daylight.NIGHT -> {
                drawRect(Color(0xFF0A1633), alpha = .52f)
                drawRect(Brush.verticalGradient(listOf(Color(0x660A1633), Color.Transparent)))
            }
            VillageLife.Daylight.MORNING -> drawRect(Brush.verticalGradient(listOf(Color(0x33FFD6E0), Color(0x14FFFFFF), Color.Transparent)))
            VillageLife.Daylight.DAY -> drawRect(Color(0x10FFFFFF))
            VillageLife.Daylight.EVENING -> {
                drawRect(Color(0xFF3A2250), alpha = .16f)
                drawRect(Brush.verticalGradient(listOf(Color(0x33FF9A4D), Color.Transparent, Color(0x223A1E5A))))
            }
        }

        // --- river glints ------------------------------------------------------
        VillageLife.water.forEachIndexed { i, p ->
            val phase = (sin(s * 1.6f + i * 1.9f) + 1f) / 2f
            val c = plate(p)
            val w = size.width * (.018f + .01f * phase)
            drawLine(Color.White.copy(alpha = .15f + .45f * phase), Offset(c.x - w, c.y), Offset(c.x + w, c.y), strokeWidth = size.width * .0035f, cap = StrokeCap.Round)
            val c2 = Offset(c.x + size.width * .014f, c.y + size.width * .012f)
            drawLine(Color.White.copy(alpha = .1f + .35f * (1f - phase)), Offset(c2.x - w * .6f, c2.y), Offset(c2.x + w * .6f, c2.y), strokeWidth = size.width * .0028f, cap = StrokeCap.Round)
        }

        // --- warm windows, lanterns and the crystal ----------------------------
        val glow = if (night) 1f else if (evening) .55f else 0f
        if (glow > 0f) {
            VillageLife.windows.forEachIndexed { i, p ->
                val flicker = .85f + .15f * sin(s * 3f + i * 2.1f)
                val r = size.width * (.035f + .02f * glow)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFC66B).copy(alpha = .7f * glow * flicker), Color(0x00FFC66B)), center = plate(p), radius = r), r, plate(p))
            }
            VillageLife.lanterns.forEachIndexed { i, p ->
                val flicker = .8f + .2f * sin(s * 4.3f + i)
                val r = size.width * (.05f + .03f * glow)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD27A).copy(alpha = .75f * glow * flicker), Color(0x00FFD27A)), center = plate(p), radius = r), r, plate(p))
                // the pool of light it throws on the path below
                drawOval(Brush.radialGradient(listOf(Color(0xFFFFC66B).copy(alpha = .35f * glow), Color(0x00FFC66B)), center = Offset(plate(p).x, plate(p).y + r * .9f), radius = r * 1.3f),
                    Offset(plate(p).x - r * 1.3f, plate(p).y + r * .45f), Size(r * 2.6f, r * .9f))
            }
        }
        val pulse = (sin(s * 1.3f) + 1f) / 2f
        val cr = size.width * (.045f + .015f * pulse)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF7FF3FF).copy(alpha = .25f + .25f * pulse + .2f * glow), Color.Transparent), center = plate(VillageLife.crystal), radius = cr), cr, plate(VillageLife.crystal))

        // --- people on the paths (sorted back-to-front) -----------------------
        val walkers = (0 until people).map { i -> Triple(i, VillageLife.walker(i, s), friendColors.getOrNull(i) ?: Tunics[i % Tunics.size]) }
        walkers.sortedBy { it.second.first.y }.forEach { (i, pos, color) ->
            drawVillager(plate(pos.first), pos.second, color, s + i * .37f, still = reducedMotion)
        }

        // --- chimney smoke -----------------------------------------------------
        VillageLife.chimneys.forEach { (p, needs) ->
            if (needs == null || needs in unlocked) drawSmoke(plate(p), s)
        }

        // --- birds over the forest --------------------------------------------
        if (!night) repeat(2) { b ->
            val period = 26f + b * 9f
            val f = ((s + b * 11f) % period) / period
            val x = size.width * (-.1f + 1.2f * f)
            val y = size.height * (.045f + .03f * b) + sin(f * 2 * PI.toFloat() * 2) * size.width * .01f
            repeat(3) { k -> drawBird(Offset(x - k * size.width * .028f, y + (k % 2) * size.width * .015f), s * 7f + k + b) }
        }

        // --- the player's touch on the ground ----------------------------------
        tap?.let { (p, at) ->
            val age = (android.os.SystemClock.uptimeMillis() - at) / 1000f
            if (age in 0f..0.9f) {
                val k = age / .9f
                drawCircle(Color.White.copy(alpha = .55f * (1f - k)), size.width * (.01f + .05f * k), plate(p), style = Stroke(size.width * .004f))
            }
        }
    }
}

private fun DrawScope.plate(p: MapPoint) = Offset(p.x * size.width, p.y * size.height)

/** A tiny painted villager: shadow, legs, tunic, head, a bob while walking. */
private fun DrawScope.drawVillager(at: Offset, left: Boolean, tunic: Color, t: Float, still: Boolean) {
    val u = size.width * .0072f                         // one "pixel" of the figure
    val step = if (still) 0f else sin(t * 9f)
    val bob = abs(step) * u * .5f
    val dir = if (left) -1f else 1f
    // shadow
    drawOval(Color(0x55000000), Offset(at.x - u * 1.8f, at.y - u * .5f), Size(u * 3.6f, u * 1.1f))
    // legs
    val hip = Offset(at.x, at.y - u * 2.4f - bob)
    drawLine(Color(0xFF3B2A20), hip, Offset(at.x + u * .9f * step, at.y - u * .2f), strokeWidth = u * .75f, cap = StrokeCap.Round)
    drawLine(Color(0xFF3B2A20), hip, Offset(at.x - u * .9f * step, at.y - u * .2f), strokeWidth = u * .75f, cap = StrokeCap.Round)
    // tunic (a soft rounded body)
    val body = Path().apply {
        moveTo(at.x - u * 1.25f, hip.y + u * .3f)
        lineTo(at.x + u * 1.25f, hip.y + u * .3f)
        quadraticTo(at.x + u * 1.15f, hip.y - u * 2.6f, at.x, hip.y - u * 2.9f)
        quadraticTo(at.x - u * 1.15f, hip.y - u * 2.6f, at.x - u * 1.25f, hip.y + u * .3f)
        close()
    }
    drawPath(body, tunic)
    drawPath(body, Brush.horizontalGradient(listOf(Color(0x33FFFFFF), Color(0x33000000)), startX = at.x - u, endX = at.x + u))
    // arm swing
    drawLine(tunic.copy(alpha = 1f), Offset(at.x + dir * u * .6f, hip.y - u * 2f), Offset(at.x + dir * u * (.6f - .7f * step), hip.y - u * .7f), strokeWidth = u * .6f, cap = StrokeCap.Round)
    // head + hair
    val head = Offset(at.x, hip.y - u * 3.7f)
    drawCircle(Color(0xFFF1C9A0), u * 1.05f, head)
    drawArc(Color(0xFF5A3A22), 180f, 180f, true, Offset(head.x - u * 1.05f, head.y - u * 1.1f), Size(u * 2.1f, u * 1.6f))
    // a rim of light from the evening sun
    drawCircle(Color(0x40FFE2A8), u * 1.05f, Offset(head.x - u * .3f, head.y - u * .3f), style = Stroke(u * .3f))
}

private fun DrawScope.drawSmoke(top: Offset, t: Float) {
    val u = size.width
    repeat(5) { i ->
        val life = ((t * .32f + i / 5f) % 1f)
        val p = Offset(top.x + sin(life * 5f + i) * u * .008f + life * u * .02f, top.y - life * u * .085f)
        val r = u * (.007f + .016f * life)
        drawCircle(Color(0xFFE8E4DC).copy(alpha = .42f * (1f - life)), r, p)
    }
}

private fun DrawScope.drawBird(at: Offset, flap: Float) {
    val u = size.width * .009f
    val wing = sin(flap) * u * .7f
    val path = Path().apply {
        moveTo(at.x - u, at.y - wing)
        quadraticTo(at.x - u * .4f, at.y - u * .4f, at.x, at.y)
        quadraticTo(at.x + u * .4f, at.y - u * .4f, at.x + u, at.y - wing)
    }
    drawPath(path, Color(0xCC1E2430), style = Stroke(u * .28f, cap = StrokeCap.Round))
}
