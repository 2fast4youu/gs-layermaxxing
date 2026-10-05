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
                .background(androidx.compose.ui.graphics.Color(0xD9123E4A), shape)
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
