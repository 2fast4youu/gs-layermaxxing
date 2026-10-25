package at.gregor.layermaxxing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
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
                            summary: String = "", painted: Boolean = false, furniture: Boolean = false,
                            onScrubbing: (Boolean) -> Unit = {}, onPage: (Int) -> Unit) {
    val last = (pages - 1).coerceAtLeast(0)
    var dragging by remember { mutableStateOf(false) }
    var finger by remember { mutableStateOf(1f) }
    val value = if (dragging) finger else PostalStorageMotion.fractionAt(page, pages)
    val active = enabled && last > 0
    fun choose(f: Float) {
        if (active && f.isFinite()) {
            onPage(PostalStorageMotion.pageAt(f, pages))
        }
    }
    val currentChoose = rememberUpdatedState<(Float) -> Unit> { choose(it) }
    val currentScrubbing = rememberUpdatedState(onScrubbing)
    // Measured on the immutable 900x1350 scene: belt x319.5..580.5, slot x390..510/y343.
    // Pointer and moving photographed brass share the exact same source-art transform.
    fun fractionAtX(x: Float, width: Int): Float = if (painted)
        ((x / width * 261f - 70.5f) / 120f).coerceIn(0f, 1f)
        else if (furniture) ((x / width * 610f - 28f) / 554f).coerceIn(0f, 1f)
        else (x / width).coerceIn(0f, 1f)
    val ink = Color(0xFF27180C)
    Column(modifier.graphicsLayer { alpha = if (active) 1f else .6f }) {
        if (!painted && !furniture) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("← früher", color = ink, fontFamily = Kit.Display, fontSize = 11.sp, lineHeight = 14.sp)
            Text("◷", color = ink, fontFamily = Kit.Display, fontSize = 13.sp, lineHeight = 16.sp)
            Text("jetzt →", color = ink, fontFamily = Kit.Display, fontSize = 11.sp, lineHeight = 14.sp)
        }
        if (!painted && !furniture) Text(if (summary.isNotBlank()) summary else if (active) "Zeit ziehen · ${page + 1}/${last + 1}" else "Keine älteren Seiten", color = ink,
            fontFamily = Kit.Display, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().testTag("post-time-label"))
        BoxWithConstraints(if (painted || furniture) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(48.dp)) {
            if (furniture) {
                val scale = maxWidth / 610f
                val density = LocalDensity.current
                Image(painterResource(R.drawable.post_time_track), contentDescription = null,
                    modifier = Modifier.offset(y = scale * 24f).fillMaxWidth().height(scale * 56f))
                Image(painterResource(R.drawable.post_time_handle), contentDescription = null,
                    modifier = Modifier.offset {
                        with(density) { IntOffset((scale * (28f + 554f * value - 24.5f)).roundToPx(),
                            (scale * 27f).roundToPx()) }
                    }.size(scale * 49f).testTag("post-time-knob"))
                Box(Modifier.align(Alignment.TopEnd).offset(y = (-22).dp).fillMaxWidth(.64f), Alignment.Center) {
                    Text(summary, color = Color(0xFFFFEBC5), fontFamily = Kit.Display, fontSize = 12.sp, lineHeight = 15.sp,
                        style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0xFF27180D), Offset(1f, 1f), 1f)),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp).testTag("post-time-label"))
                }
            }
            if (painted) {
                val scale = maxWidth / 261f
                val density = LocalDensity.current
                Image(painterResource(R.drawable.e_post_0_time_knob), contentDescription = null,
                    modifier = Modifier.offset {
                        with(density) { IntOffset((scale * (70.5f + 120f * value - 15f)).roundToPx(),
                            (scale * (78.4f - 15f)).roundToPx()) }
                    }.size(scale * 30f).testTag("post-time-knob"))
            }
            if (painted) {
                // Ink sits inside the upper wood, above the actual slot and photographed knob.
                Text(summary, color = Color(0xFFFFEBC5), fontFamily = Kit.Display, fontSize = 12.sp, lineHeight = 14.sp,
                    maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = maxWidth / 261f * 23f)
                        .testTag("post-time-label"))
            }
            Canvas(Modifier.fillMaxSize().then(if (painted || furniture) Modifier else Modifier.padding(horizontal = 22.dp)).testTag("post-time-rail")
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
                            dragging = true
                            currentScrubbing.value(true)
                            try {
                                finger = fractionAtX(down.position.x, size.width)
                                currentChoose.value(finger)
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val pointer = event.changes.firstOrNull { it.id == down.id }
                                    if (pointer != null) {
                                        pointer.consume()
                                        finger = fractionAtX(pointer.position.x, size.width)
                                        currentChoose.value(finger)
                                    }
                                } while (event.changes.any { it.id == down.id && it.pressed })
                            } finally {
                                dragging = false
                                currentScrubbing.value(false)
                            }
                        }
                    }
                }) {
                if (painted) {
                    // These are direction marks engraved at the ends, never a competing cursor
                    // or pointer owner. The whole wooden band, including its real knob, drags.
                    val scale = size.width / 261f
                    val y = 78.4f * scale
                    val dx = 3.dp.toPx(); val dy = 5.dp.toPx()
                    for ((sourceX, direction) in listOf(32f to -1f, 229f to 1f)) {
                        val x = sourceX * scale
                        val tone = Color(0xFFFFEBC5).copy(alpha = if ((direction < 0 && page < last) || (direction > 0 && page > 0)) .9f else .3f)
                        drawLine(tone, Offset(x - direction * dx, y - dy), Offset(x + direction * dx, y), 1.5.dp.toPx())
                        drawLine(tone, Offset(x + direction * dx, y), Offset(x - direction * dx, y + dy), 1.5.dp.toPx())
                    }
                    return@Canvas // One photographed moving knob, no floating second cursor.
                }
                if (furniture) return@Canvas // The scene brass supplies track and exactly one moving handle.
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
            if (active && page < last) Text(if (painted || furniture) "" else "‹", fontSize = 25.sp, color = ink,
                modifier = Modifier.align(Alignment.CenterStart).size(48.dp).testTag("tree-time-back")
                    .then(if (painted || furniture) Modifier.semantics { role = Role.Button; onClick("Ältere Briefe") { onPage((page + 1).coerceAtMost(last)); true } }
                        else Modifier.clickable(onClickLabel = "Ältere Briefe") { onPage((page + 1).coerceAtMost(last)) }))
            if (active && page > 0) Text(if (painted || furniture) "" else "›", fontSize = 25.sp, color = ink,
                modifier = Modifier.align(Alignment.CenterEnd).size(48.dp).testTag("tree-time-now")
                    .then(if (painted || furniture) Modifier.semantics { role = Role.Button; onClick("Zurück zur Gegenwart") { onPage(0); true } }
                        else Modifier.clickable(onClickLabel = "Zurück zur Gegenwart") { onPage(0) }))
        }
    }
}
