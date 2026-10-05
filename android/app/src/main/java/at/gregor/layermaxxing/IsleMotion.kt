package at.gregor.layermaxxing

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle

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
 * Text painted straight into the world: chunky game font with a dark outline and a soft
 * drop shadow – no white card on top of the landscape.
 */
@Composable
internal fun WorldText(
    text: String,
    size: androidx.compose.ui.unit.TextUnit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    fill: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFFFFFBF0),
    outline: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFF16424F),
    display: Boolean = true,
) {
    val family = if (display) Kit.Display else Kit.Body
    val weight = if (display) null else androidx.compose.ui.text.font.FontWeight.ExtraBold
    val stroke = with(androidx.compose.ui.platform.LocalDensity.current) { (size.toPx() * .13f).coerceAtLeast(2.5f) }
    androidx.compose.foundation.layout.Box(modifier) {
        androidx.compose.material3.Text(
            text, fontSize = size, fontFamily = family, fontWeight = weight, color = outline, maxLines = 1,
            style = TextStyle(
                drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, join = androidx.compose.ui.graphics.StrokeJoin.Round),
                shadow = androidx.compose.ui.graphics.Shadow(androidx.compose.ui.graphics.Color(0x55000000), androidx.compose.ui.geometry.Offset.Zero, stroke * 1.6f),
            ),
        )
        androidx.compose.material3.Text(text, fontSize = size, fontFamily = family, fontWeight = weight, color = fill, maxLines = 1)
    }
}
