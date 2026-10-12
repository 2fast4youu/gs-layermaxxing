package at.gregor.layermaxxing

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The player's figure on the own island. Until 7.9.6 the island also had building plots for real life
 * places ("+ Bauplatz"); since 7.9.7 those places are special decoration ([SpecialDecor]) placed freely
 * from the build bar, and the server migrated the old ones with their names.
 */
internal object LifeFigure {
    /** The figure stands on the plaza in the middle of the land (box fractions before land scaling). */
    const val X = .50f
    const val Y = .52f
}

/** Flat, quiet name tag (overview/visit labels). The own island's signs are [IslandNameSign]. */
@Composable
internal fun PlaceTag(text: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Text(
        text, maxLines = 1, fontFamily = Kit.Body, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, lineHeight = 12.sp,
        letterSpacing = .2.sp, color = Color(0xFFF6EFE2),
        modifier = modifier.clip(RoundedCornerShape(6.dp))
            .background(if (accent) Color(0xCC2F5D50) else Color(0xB3263A40))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** My figure, drawn in island-box coordinates (only on the own island). */
@Composable
internal fun BoxWithConstraintsScope.FigureLayer(onFigure: (() -> Unit)?, landScale: Float = 1f) {
    val w = maxWidth; val h = maxHeight
    val gate = LocalIslandTapGate.current
    val (fx, fy) = IslandPlans.onLand(LifeFigure.X, LifeFigure.Y, landScale)
    val fh = w * .085f * IslandPlans.pieceScale(landScale)
    Image(
        painterResource(R.drawable.char_me), "Du",
        Modifier.offset(x = w * fx - fh * .2f, y = h * fy - fh).height(fh).width(fh * .4f)
            .then(if (onFigure != null) Modifier.worldTap("Du – Profil öffnen") { gate { onFigure() } } else Modifier),
    )
}
