package at.gregor.layermaxxing

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass

/** Reversible storage-to-view pose: clear the wooden lip before turning toward the reader. */
internal data class StoragePose(val pitch: Float = 0f, val yaw: Float = 0f, val roll: Float = 0f)
internal val StoragePoseKey = SemanticsPropertyKey<StoragePose>("Envelope storage rotation")
internal object PostalStorageMotion {
    private fun smooth(p: Float): Float { val x = p.coerceIn(0f, 1f); return x * x * (3f - 2f * x) }
    fun clearance(p: Float) = smooth(p / .42f)
    fun turn(p: Float) = smooth((p - .48f) / .52f)
    fun roomLight(p: Float) = smooth((p - .42f) / .40f)
    fun camera(p: Float) = smooth((p - .48f) / .52f)
    fun pose(level: Int, progress: Float, slot: Int = 0): StoragePose {
        require(progress.isFinite())
        if (level == 0) return StoragePose()
        val p = progress.coerceIn(0f, 1f)
        val turn = turn(p)
        val restPitch = when (level) { 1 -> -56f; 2 -> -16f; else -> -42f }
        val restYaw = when (level) { 1 -> -7f; 2 -> if (slot % 2 == 0) 9f else -9f; else -> 6f }
        val arc = 0f
        return StoragePose(restPitch * (1f - turn), restYaw * (1f - turn), arc)
    }
    fun pageAt(fraction: Float, pages: Int): Int {
        require(fraction.isFinite())
        val last = (pages - 1).coerceAtLeast(0)
        return last - (fraction.coerceIn(0f, 1f) * last).roundToInt()
    }
    fun fractionAt(page: Int, pages: Int): Float {
        val last = (pages - 1).coerceAtLeast(0)
        return if (last == 0) 1f else 1f - page.coerceIn(0, last).toFloat() / last
    }
}

/** A native brass runner, drawn on the furniture rather than a Material slider panel. Uses actual paging. */
@Composable
internal fun PostalTimeRail(page: Int, pages: Int, enabled: Boolean, modifier: Modifier = Modifier,
                            summary: String = "", onPage: (Int) -> Unit) {
    val last = (pages - 1).coerceAtLeast(0)
    val value = PostalStorageMotion.fractionAt(page, pages)
    val active = enabled && last > 0
    fun choose(f: Float) {
        if (active && f.isFinite()) {
            onPage(PostalStorageMotion.pageAt(f, pages))
        }
    }
    val currentChoose = rememberUpdatedState<(Float) -> Unit> { choose(it) }
    val ink = Color(0xFF27180C)
    Column(modifier.background(Color(0xF2E2CCA0)).graphicsLayer { alpha = if (active) 1f else .6f }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("← früher", color = ink, fontFamily = Kit.Display, fontSize = 11.sp, lineHeight = 14.sp)
            Text("◷", color = ink, fontFamily = Kit.Display, fontSize = 13.sp, lineHeight = 16.sp)
            Text("jetzt →", color = ink, fontFamily = Kit.Display, fontSize = 11.sp, lineHeight = 14.sp)
        }
        Text(if (summary.isNotBlank()) summary else if (active) "Zeit ziehen · ${page + 1}/${last + 1}" else "Keine älteren Seiten", color = ink,
            fontFamily = Kit.Display, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().testTag("post-time-label"))
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            Canvas(Modifier.fillMaxSize().padding(horizontal = 22.dp).testTag("post-time-rail")
                .semantics {
                    contentDescription = "Briefzeit ziehen: links ältere Briefe, rechts Gegenwart. Seite ${page + 1} von ${last + 1}"
                    stateDescription = if (active) "Zeitreise verfügbar" else "Keine weitere Zeitseite verfügbar"
                    progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f, (pages - 2).coerceAtLeast(0))
                    if (!active) disabled()
                    setProgress { if (active && it.isFinite()) { choose(it); true } else false }
                }
                .pointerInput(active, pages) {
                    // One owner from down to up. Capturing the old page callback in a long-lived
                    // pointer coroutine, or a competing tap detector, loses real runner swipes.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (active) {
                            down.consume()
                            currentChoose.value(down.position.x / size.width)
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val pointer = event.changes.firstOrNull { it.id == down.id }
                                if (pointer != null) {
                                    pointer.consume()
                                    currentChoose.value(pointer.position.x / size.width)
                                }
                            } while (event.changes.any { it.id == down.id && it.pressed })
                        }
                    }
                }) {
                val y = size.height * .50f
                drawLine(Color(0xF018100A), Offset(0f, y + 1.dp.toPx()), Offset(size.width, y + 1.dp.toPx()), 9.dp.toPx())
                drawLine(Color(0xFFCBA55A), Offset(0f, y), Offset(size.width, y), 3.dp.toPx())
                drawLine(Color(0xFFE4C276), Offset(0f, y - 1.dp.toPx()), Offset(size.width, y - 1.dp.toPx()), 1.dp.toPx())
                for (i in 0..last.coerceAtMost(24)) {
                    val count = last.coerceIn(1, 24); val x = size.width * i / count
                    drawLine(Color(0xAA412C17), Offset(x, y - 5.dp.toPx()), Offset(x, y + 5.dp.toPx()), 1.dp.toPx())
                }
                val x = value * size.width
                drawCircle(Color(0x66301B0E), 17.dp.toPx(), Offset(x + 1.dp.toPx(), y + 2.dp.toPx()))
                drawCircle(Brush.radialGradient(listOf(Color(0xFFF2D78B), Color(0xFF96703C)), center = Offset(x, y), radius = 17.dp.toPx()),
                    16.dp.toPx(), Offset(x, y))
                for (dx in listOf(-5f, 0f, 5f)) drawLine(Color(0xFF54391F), Offset(x + dx.dp.toPx(), y - 7.dp.toPx()), Offset(x + dx.dp.toPx(), y + 7.dp.toPx()), 2.dp.toPx())
            }
            if (active && page < last) Text("‹", fontSize = 25.sp, color = ink,
                modifier = Modifier.align(Alignment.CenterStart).size(48.dp).testTag("tree-time-back")
                    .clickable(onClickLabel = "Ältere Briefe") { onPage((page + 1).coerceAtMost(last)) })
            if (active && page > 0) Text("›", fontSize = 25.sp, color = ink,
                modifier = Modifier.align(Alignment.CenterEnd).size(48.dp).testTag("tree-time-now")
                    .clickable(onClickLabel = "Zurück zur Gegenwart") { onPage(0) })
        }
    }
}
