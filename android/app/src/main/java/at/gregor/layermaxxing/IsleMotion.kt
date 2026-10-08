package at.gregor.layermaxxing

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.res.imageResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

/** Calm, deterministic motion for the archipelago (no loops that jump back to the start). */
internal object IsleMotion {
    const val MIN_ZOOM = .8f
    const val MAX_ZOOM = 2.4f

    /**
     * Where along its route (0 = home, 1 = friend) a letter boat is right now.
     * Timed letters travel with real time from sending to release; letters that wait
     * for an approval or a random moment lie at anchor in the middle of the route.
     * Never wraps around, so a boat never "respawns".
     */
    fun boatProgress(createdAtSec: Long, releaseAtSec: Long?, nowSec: Long): Float {
        val span = (releaseAtSec ?: 0L) - createdAtSec
        val raw = if (releaseAtSec == null || span <= 0) .5f else ((nowSec - createdAtSec).toFloat() / span).coerceIn(0f, 1f)
        return .18f + raw * .64f
    }

    /** When a letter's boat docks: fixed time, the latest moment of a random window, or unknown (waits for approval). */
    fun arrivalSec(m: ApiClient.Message): Long? = when (m.mode) {
        "random" -> m.randomTo ?: m.releaseAt
        "manual", "mutual", "presence" -> null
        else -> m.releaseAt
    }

    /** Share of the trip already done, 0..1 (null = waiting at anchor). */
    fun tripShare(m: ApiClient.Message, nowSec: Long): Float? {
        val end = arrivalSec(m) ?: return null
        val span = end - m.createdAt
        if (span <= 0) return 1f
        return ((nowSec - m.createdAt).toFloat() / span).coerceIn(0f, 1f)
    }

    /** At most this many parallel lanes per route; more boats share the outer lanes. */
    const val MAX_LANES = 4

    /**
     * Sideways offset of each boat's own lane, in lane units, centred on the route:
     * 1 boat → [0], 2 → [-.5, .5], 3 → [-1, 0, 1] … so boats sail side by side, never stacked.
     */
    fun laneOffsets(count: Int, oneSided: Boolean = false): List<Float> {
        val n = count.coerceIn(0, MAX_LANES)
        // One-sided (short routes): every lane bows out to the same side, first lane already clear of the straight line.
        return if (oneSided) List(n) { it + 1f } else List(n) { it - (n - 1) / 2f }
    }

    /** How many side-by-side lanes a route of [open] px of open water can carry (1..MAX_LANES). */
    fun lanesThatFit(open: Float, laneGap: Float): Int =
        (((open + laneGap * 2f) / (laneGap * 1.4f)).toInt()).coerceIn(1, MAX_LANES)

    /**
     * Lanes to try for one boat, in lane units: its own lane first, then bowing further out on
     * the same side (both sides for the centre lane), so a boat dodges shores and other boats
     * without ever swapping sides with its neighbours.
     */
    fun laneCandidates(own: Float): List<Float> {
        val steps = listOf(0f, .5f, 1f, 1.5f, 2f)
        return if (own == 0f) steps.flatMap { if (it == 0f) listOf(0f) else listOf(it, -it) }
        else { val dir = if (own < 0f) -1f else 1f; steps.map { own + dir * it } }
    }

    /**
     * Remaining time in sailor speak, for the label on a route.
     * null arrival = waits at anchor (approval / presence).
     */
    fun seaTime(arrivalSec: Long?, nowSec: Long): String {
        if (arrivalSec == null) return "vor Anker · wartet auf Wind"
        val left = arrivalSec - nowSec
        if (left <= 0) return "Land in Sicht!"
        val min = left / 60
        val h = left / 3600
        val d = left / 86_400
        return when {
            min < 60 -> "noch ${min.coerceAtLeast(1)} Min · gleich im Hafen"
            h < 24 -> "noch $h ${if (h == 1L) "Stunde" else "Stunden"} auf See"
            d < 7 -> "noch $d ${if (d == 1L) "Tag" else "Tage"} auf See"
            else -> "noch ${d / 7} ${if (d / 7 == 1L) "Woche" else "Wochen"} auf hoher See"
        }
    }

    /** Line icon that goes with [seaTime]: anchor (waiting), bell (arrived), hourglass (today), calendar (days+). */
    fun seaIcon(arrivalSec: Long?, nowSec: Long): Int {
        if (arrivalSec == null) return R.drawable.ico_harbour
        val left = arrivalSec - nowSec
        return when {
            left <= 0 -> R.drawable.ico_bell
            left < 86_400 -> R.drawable.ico_timer
            else -> R.drawable.ico_date
        }
    }

    /** Short form for the label right under a boat on the map. */
    fun seaTimeShort(arrivalSec: Long?, nowSec: Long): String {
        if (arrivalSec == null) return "Anker"
        val left = arrivalSec - nowSec
        if (left <= 0) return "Land in Sicht"
        val min = left / 60; val h = left / 3600; val d = left / 86_400
        return when {
            min < 60 -> "${min.coerceAtLeast(1)} Min"
            h < 24 -> "$h Std"
            d < 7 -> "$d ${if (d == 1L) "Tag" else "Tage"}"
            else -> "${d / 7} Wo"
        }
    }

    /** Gentle anchor sway (±1 %) so a waiting boat looks afloat, not frozen. */
    fun sway(seconds: Float, seed: Int): Float = kotlin.math.sin(seconds / 4f + seed) * .01f

    /** Clamp pan so the content can never be dragged completely off screen. */
    fun clampPan(pan: Offset, zoom: Float, width: Float, height: Float): Offset {
        val lx = width * (zoom - 1f).coerceAtLeast(0f) / 2f + width * .25f
        val ly = height * (zoom - 1f).coerceAtLeast(0f) / 2f + height * .2f
        return Offset(pan.x.coerceIn(-lx, lx), pan.y.coerceIn(-ly, ly))
    }

    /** Zoom around the fingers' centroid instead of the screen centre. */
    fun zoomAround(pan: Offset, zoom: Float, factor: Float, centroid: Offset, width: Float, height: Float): Pair<Offset, Float> {
        val next = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val ratio = next / zoom
        val c = Offset(width / 2f, height / 2f)
        val p = Offset(
            pan.x + (centroid.x - c.x - pan.x) * (1f - ratio),
            pan.y + (centroid.y - c.y - pan.y) * (1f - ratio),
        )
        return p to next
    }
}

/** Island screens use the same game type as the island art: Lilita for titles, Nunito for text. */
@Composable
internal fun IsleTypography(content: @Composable () -> Unit) {
    val base = MaterialTheme.typography
    fun b(s: TextStyle) = s.copy(fontFamily = Kit.Body)
    fun d(s: TextStyle) = s.copy(fontFamily = Kit.Display)
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = Typography(
            displayLarge = d(base.displayLarge), displayMedium = d(base.displayMedium), displaySmall = d(base.displaySmall),
            headlineLarge = d(base.headlineLarge), headlineMedium = d(base.headlineMedium), headlineSmall = d(base.headlineSmall),
            titleLarge = d(base.titleLarge), titleMedium = b(base.titleMedium), titleSmall = b(base.titleSmall),
            bodyLarge = b(base.bodyLarge), bodyMedium = b(base.bodyMedium), bodySmall = b(base.bodySmall),
            labelLarge = b(base.labelLarge), labelMedium = b(base.labelMedium), labelSmall = b(base.labelSmall),
        ),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalTextStyle provides androidx.compose.material3.LocalTextStyle.current.copy(fontFamily = Kit.Body),
            content = content,
        )
    }
}


/**
 * Clean world label: flat text on a small dark-teal plaque. No outline, no shadow,
 * no white card – reads like a painted harbour sign and stays crisp at any zoom.
 */
@Composable
internal fun WorldText(
    text: String,
    size: androidx.compose.ui.unit.TextUnit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    fill: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFFFFF8E6),
    display: Boolean = true,
    plaque: Boolean = true,
) {
    val family = if (display) Kit.Display else Kit.Body
    val weight = if (display) null else androidx.compose.ui.text.font.FontWeight.ExtraBold
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
    androidx.compose.material3.Text(
        text, fontSize = size, fontFamily = family, fontWeight = weight, color = fill, maxLines = 1,
        style = TextStyle(shadow = null),
        modifier = modifier.then(
            if (plaque) androidx.compose.ui.Modifier
                .background(androidx.compose.ui.graphics.Color(0xD9243A3F), shape)
                .padding(horizontal = 8.dp, vertical = 1.dp)
            else androidx.compose.ui.Modifier,
        ),
    )
}

/** Hand-painted sea, tiled so it continues endlessly and moves with the map. */
@Composable
internal fun rememberSeaBrush(): androidx.compose.ui.graphics.ShaderBrush {
    val img = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.sea_tile)
    return androidx.compose.runtime.remember(img) {
        androidx.compose.ui.graphics.ShaderBrush(
            androidx.compose.ui.graphics.ImageShader(img, androidx.compose.ui.graphics.TileMode.Repeated, androidx.compose.ui.graphics.TileMode.Repeated),
        )
    }
}
