package at.gregor.layermaxxing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * „Hafenpost“ – the messenger's own material.
 *
 * The island world is painted; the messenger is where its letters land. Instead of
 * generic Material greys it uses warm harbour paper, sea-glass for my own words,
 * navy ink and one gold trim, so chat and island read as one place. Light and dark
 * follow the app theme but keep the same character.
 */
@Immutable
internal data class HarbourPalette(
    val dark: Boolean,
    val paper: Color,
    val paperDeep: Color,
    val card: Color,
    val ink: Color,
    val inkSoft: Color,
    val line: Color,
    val sea: Color,
    val seaDeep: Color,
    val mine: Color,
    val onMine: Color,
    val theirs: Color,
    val gold: Color,
    val wax: Color,
    val read: Color,
    val head: Brush,
    val onHead: Color,
)

internal object Harbour {
    private val light = HarbourPalette(
        dark = false,
        paper = Color(0xFFF7F0E1), paperDeep = Color(0xFFEFE3CA), card = Color(0xFFFFFBF2),
        ink = Color(0xFF22303F), inkSoft = Color(0xFF6E6555), line = Color(0x1F5A4A30),
        sea = Color(0xFF2F7F95), seaDeep = Color(0xFF1C4E66),
        mine = Color(0xFFD5ECE6), onMine = Color(0xFF173A40), theirs = Color(0xFFFFFCF5),
        gold = Color(0xFFC8962F), wax = Color(0xFFB3473C), read = Color(0xFF2F7F95),
        head = Brush.verticalGradient(listOf(Color(0xFF2F7F95), Color(0xFF1C4E66))), onHead = Color(0xFFFFF7E6),
    )
    private val night = HarbourPalette(
        dark = true,
        paper = Color(0xFF111B26), paperDeep = Color(0xFF0C141D), card = Color(0xFF1A2633),
        ink = Color(0xFFEDE6D6), inkSoft = Color(0xFFA9A08E), line = Color(0x24F3E5C6),
        sea = Color(0xFF6FB9C9), seaDeep = Color(0xFF3F8EA3),
        mine = Color(0xFF1E4A50), onMine = Color(0xFFE4F4EF), theirs = Color(0xFF1E2B39),
        gold = Color(0xFFE2B457), wax = Color(0xFFD06A5C), read = Color(0xFF8ED3E0),
        head = Brush.verticalGradient(listOf(Color(0xFF16273A), Color(0xFF111E2D))), onHead = Color(0xFFFFF4DE),
    )

    /** The palette for the current app theme (dark when the surface is dark). */
    @Composable
    fun palette(): HarbourPalette = if (MaterialTheme.colorScheme.surface.luminance() < .4f) night else light

    val Title = TextStyle(fontFamily = Kit.Display, fontSize = 22.sp, letterSpacing = .2.sp)
    val Name = TextStyle(fontFamily = Kit.Body, fontWeight = FontWeight.Bold, fontSize = 16.sp)
}

/**
 * The quiet harbour paper behind lists and threads: a warm wash, two faint chart
 * contours and a few hand-set wave strokes – no repeating dot grid.
 */
internal fun Modifier.harbourPaper(p: HarbourPalette): Modifier = this
    .background(Brush.verticalGradient(listOf(p.paper, p.paperDeep)))
    .drawBehind {
        val ink = p.sea.copy(alpha = if (p.dark) .05f else .04f)
        val stroke = Stroke(width = 1.2.dp.toPx())
        // Chart contours around an island somewhere off the bottom-right edge.
        val c = Offset(size.width * .92f, size.height * .96f)
        listOf(.28f, .40f, .54f).forEach { r ->
            drawCircle(ink, radius = size.minDimension * r, center = c, style = stroke)
        }
        // A handful of small painted wave marks, placed deterministically.
        val wave = p.sea.copy(alpha = if (p.dark) .07f else .06f)
        val marks = listOf(.78f to .16f, .12f to .58f, .66f to .84f)
        val w = 14.dp.toPx(); val h = 3.dp.toPx()
        marks.forEach { (fx, fy) ->
            val x = size.width * fx; val y = size.height * fy
            val path = Path().apply {
                moveTo(x, y)
                quadraticBezierTo(x + w * .25f, y - h, x + w * .5f, y)
                quadraticBezierTo(x + w * .75f, y + h, x + w, y)
            }
            drawPath(path, wave, style = Stroke(width = 1.6.dp.toPx()))
        }
    }

/**
 * A friend as a tiny island seen from above: sand rim, their colour as the land,
 * their animal in the middle. Optional gold ring marks something new.
 */
@Composable
internal fun IslandAvatar(emoji: String, color: Color, size: Dp, p: HarbourPalette, news: Boolean = false) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2
            // shallow water halo
            drawCircle(p.sea.copy(alpha = if (p.dark) .30f else .18f), radius = r)
            // sand
            drawCircle(Color(0xFFEBD6A6).copy(alpha = if (p.dark) .85f else 1f), radius = r * .84f)
            // land in the friend's colour, slightly off-centre for a painted feel
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = .55f).compositeOverWhite(), color.copy(alpha = .85f).compositeOverWhite()),
                    center = Offset(center.x - r * .12f, center.y - r * .14f), radius = r),
                radius = r * .70f, center = Offset(center.x, center.y + r * .02f),
            )
            if (news) drawCircle(p.gold, radius = r - 1.dp.toPx(), style = Stroke(width = 2.4.dp.toPx()))
        }
        Text(emoji, fontSize = (size.value * .44f).sp)
    }
}

private fun Color.compositeOverWhite(): Color {
    val a = alpha
    return Color(red * a + (1 - a), green * a + (1 - a), blue * a + (1 - a), 1f)
}

/** A small round wax seal with a count – used for unread chats and waiting letters. */
@Composable
internal fun WaxSeal(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.sizeIn(minWidth = 22.dp, minHeight = 22.dp)
            .background(Brush.radialGradient(listOf(color.copy(alpha = .85f), color)), CircleShape)
            .border(1.dp, Color.White.copy(alpha = .22f), CircleShape)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Kit.Body)
    }
}

/** A wax seal carrying a line icon instead of a glyph. */
@Composable
internal fun WaxSealIcon(icon: Int, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.sizeIn(minWidth = 22.dp, minHeight = 22.dp)
            .background(Brush.radialGradient(listOf(color.copy(alpha = .85f), color)), CircleShape)
            .border(1.dp, Color.White.copy(alpha = .22f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { AppIcon(icon, null, tint = Color.White, size = 17.dp) }
}

/** A folded paper boat – the send glyph of the harbour. Drawn, not an emoji. */
@Composable
internal fun PaperBoat(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val hull = Path().apply {
            moveTo(w * .06f, h * .58f); lineTo(w * .94f, h * .58f)
            lineTo(w * .76f, h * .84f); lineTo(w * .24f, h * .84f); close()
        }
        val sail = Path().apply {
            moveTo(w * .50f, h * .10f); lineTo(w * .78f, h * .52f); lineTo(w * .50f, h * .52f); close()
        }
        val sail2 = Path().apply {
            moveTo(w * .46f, h * .22f); lineTo(w * .46f, h * .52f); lineTo(w * .24f, h * .52f); close()
        }
        drawPath(hull, color)
        drawPath(sail, color)
        drawPath(sail2, color.copy(alpha = .78f))
    }
}
