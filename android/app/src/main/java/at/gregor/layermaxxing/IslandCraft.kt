package at.gregor.layermaxxing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.sqrt

/** Small roadside signs, native text on wood, no drop shadow or floating card. */
@Composable
internal fun IslandSign(label: String) {
    Box(Modifier.padding(horizontal = 3.dp).height(29.dp), contentAlignment = Alignment.TopCenter) {
        Canvas(Modifier.matchParentSize()) {
            val wood = Color(0xFF795637)
            val pale = Color(0xFFD8BC86)
            val plankH = size.height * .72f
            for (x in listOf(.22f, .78f)) {
                drawRect(wood, Offset(size.width * x, plankH - 1), Size(2.dp.toPx(), size.height - plankH))
            }
            drawRoundRect(wood, size = Size(size.width, plankH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            drawRoundRect(pale, Offset(1.dp.toPx(), 1.dp.toPx()), Size(size.width - 2.dp.toPx(), plankH - 2.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
            drawLine(Color(0x33795637), Offset(5.dp.toPx(), plankH - 3.dp.toPx()), Offset(size.width - 5.dp.toPx(), plankH - 3.dp.toPx()), 1f)
        }
        Text(label, fontFamily = Kit.Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = .2.sp, color = Color(0xFF3C2C20), maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

internal object IslandHarbours {
    /** Elliptical shoreline intersection, works equally in all directions. */
    fun shore(center: Offset, target: Offset, width: Float): Offset {
        val d = target - center
        if (d.getDistance() < 1f) return center + Offset(0f, width * .31f)
        val rx = width * .43f
        val ry = width * .31f
        val k = 1f / sqrt(d.x * d.x / (rx * rx) + d.y * d.y / (ry * ry))
        return center + d * k
    }
}

/** A still wooden jetty between coast and water; the route starts at its outer tip. */
internal fun DrawScope.drawIslandJetty(tip: Offset, inland: Offset, width: Float) {
    val direction = (tip - inland).let { it / it.getDistance().coerceAtLeast(1f) }
    val side = Offset(-direction.y, direction.x)
    val length = width * 1.9f
    val start = tip - direction * length
    drawLine(Color(0xFF6F4E35), start, tip, width + 2f)
    drawLine(Color(0xFFC9A773), start, tip, width)
    for (i in 0..6) {
        val p = start + direction * (length * i / 6f)
        drawLine(Color(0x88795637), p - side * width * .46f, p + side * width * .46f, 1.2f)
    }
    for (p in listOf(start, tip - direction * width * .25f)) {
        drawCircle(Color(0xFF63462E), width * .15f, p + side * width * .56f)
        drawCircle(Color(0xFF63462E), width * .15f, p - side * width * .56f)
    }
}
