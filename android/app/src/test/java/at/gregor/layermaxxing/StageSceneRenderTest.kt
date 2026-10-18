package at.gregor.layermaxxing

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 7.9.11: all 24 building × stage views rendered as the production composables in a real Android window
 * (Robolectric NATIVE graphics, JVM simulation, not a device), with fixture data and every action. Checked on the
 * pixels, not only on semantics: the header sign reads (contrast of the title against its painted sign), the
 * stage's light is the configured one, and every stage of a building looks clearly different from its others.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class StageSceneRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun lum(c: Int): Double {
        fun ch(v: Int) = (v / 255.0).let { if (it <= .03928) it / 12.92 else Math.pow((it + .055) / 1.055, 2.4) }
        return .2126 * ch((c shr 16) and 255) + .7152 * ch((c shr 8) and 255) + .0722 * ch(c and 255)
    }
    /** The real Android window, drawn like the visual review does. */
    private fun window(): Bitmap {
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        return bmp
    }
    private fun crop(b: Bitmap, r: androidx.compose.ui.geometry.Rect): Bitmap {
        val l = r.left.toInt().coerceIn(0, b.width - 1); val t = r.top.toInt().coerceIn(0, b.height - 1)
        return Bitmap.createBitmap(b, l, t, r.width.toInt().coerceIn(1, b.width - l), r.height.toInt().coerceIn(1, b.height - t))
    }
    /** The screen as a small grid of average colours (robust against single pixels and antialiasing). */
    private fun grid(b: Bitmap, cols: Int = 24, rows: Int = 48): IntArray = IntArray(cols * rows) { k ->
        val cx = k % cols; val cy = k / cols
        var r = 0L; var g = 0L; var bl = 0L; var n = 0
        val x0 = cx * b.width / cols; val x1 = (cx + 1) * b.width / cols; val y0 = cy * b.height / rows; val y1 = (cy + 1) * b.height / rows
        for (y in y0 until y1 step 3) for (x in x0 until x1 step 3) { val p = b.getPixel(x, y); r += (p shr 16) and 255; g += (p shr 8) and 255; bl += p and 255; n++ }
        (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (bl / n).toInt()
    }
    /** Share of grid cells whose colour clearly differs. */
    private fun differs(a: IntArray, b: IntArray): Double = a.indices.count { i ->
        listOf(16, 8, 0).any { s -> kotlin.math.abs(((a[i] shr s) and 255) - ((b[i] shr s) and 255)) > 18 }
    }.toDouble() / a.size
    /**
     * Mean colour of the open sea between the header and the island ground (16.5–18.5 % of the height, middle of the
     * width: no hero, no boat prop there): the painted sea tile in the stage's light.
     */
    private fun seaBand(b: Bitmap): Triple<Int, Int, Int> {
        var r = 0L; var g = 0L; var bl = 0L; var n = 0
        for (y in (b.height * .165).toInt() until (b.height * .185).toInt() step 2) for (x in (b.width * .3).toInt() until (b.width * .7).toInt() step 2) {
            val p = b.getPixel(x, y); r += (p shr 16) and 255; g += (p shr 8) and 255; bl += p and 255; n++
        }
        return Triple((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
    }

    @Test fun allTwentyFourStagesRenderTheirOwnSceneWithAReadableHeaderAndTheRealData() {
        var current by mutableStateOf(IsleBuilding.POST to 0)
        rule.mainClock.autoAdvance = false
        rule.setContent { androidx.compose.runtime.key(current) { StageFixtures.Place(current.first, current.second) } }
        val grids = mutableMapOf<Pair<IsleBuilding, Int>, IntArray>()
        val sea = mutableMapOf<Pair<IsleBuilding, Int>, Triple<Int, Int, Int>>()
        for (b in IsleBuilding.entries) for (level in 0..3) {
            // The clock is paused (endless water/flame animations): the state write must happen on the UI thread and be
            // announced to the recomposer before the frames advance, otherwise the frames run without an invalidation
            // and the previous building stays on screen.
            rule.runOnIdle {
                current = b to level
                androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            }
            rule.waitForIdle()
            repeat(30) { rule.mainClock.advanceTimeByFrame() }
            rule.waitForIdle()
            val scene = StageScenes.of(b, level)
            val name = scene.stage.name
            val msg = "$b stage $level ($name)"
            // the header: the stage's name on the painted sign, its stage line, the stage scene and the action
            rule.onAllNodesWithContentDescription("Ort: $name")[0].assertExists()
            rule.onAllNodesWithContentDescription("Stufe: ${scene.stage.subtitle}")[0].assertExists()
            val stage = rule.onAllNodesWithContentDescription("Bühne: ${scene.caption}")[0].fetchSemanticsNode()
            assertEquals(msg, scene.signature, stage.config.getOrNull(SemanticsProperties.StateDescription))
            StageFixtures.action(b)?.let { rule.onAllNodesWithText(it)[0].assertExists() }
            // the real data is there on every stage
            when (b) {
                IsleBuilding.POST -> rule.onAllNodesWithText("Anna")[0].assertExists()
                IsleBuilding.HARBOUR -> rule.onAllNodesWithText("Gemeinsam auf den Gipfel")[0].assertExists()
                IsleBuilding.LIGHTHOUSE -> { rule.onAllNodesWithText("Jonas")[0].assertExists(); rule.onAllNodesWithText("Gerfried")[0].assertExists() }
                IsleBuilding.LIBRARY -> rule.onAllNodesWithContentDescription("Kategorie Witze")[0].assertExists()
                IsleBuilding.CAMPFIRE -> rule.onAllNodesWithText("Bergfreunde", substring = true)[0].assertExists()
                IsleBuilding.HOUSE -> rule.onAllNodesWithText("Gregor")[0].assertExists()
            }
            // header readability on the pixels: the cream title against its painted sign and shadow (WCAG AA 4.5:1)
            val whole = window()
            val title = crop(whole, rule.onAllNodesWithContentDescription("Ort: $name")[0].fetchSemanticsNode().boundsInWindow)
            val ls = (0 until title.height step 2).flatMap { y -> (0 until title.width step 2).map { x -> lum(title.getPixel(x, y)) } }.sorted()
            val hi = ls[(ls.size * .95).toInt().coerceAtMost(ls.size - 1)]; val lo = ls[(ls.size * .05).toInt()]
            assertTrue("$msg title contrast ${(hi + .05) / (lo + .05)}", (hi + .05) / (lo + .05) >= 4.5)
            // the compose root (the window may have a status bar inset above it)
            val shot = crop(whole, rule.onRoot().fetchSemanticsNode().boundsInWindow)
            grids[b to level] = grid(shot)
            sea[b to level] = seaBand(shot)
            whole.recycle()
        }
        for (b in IsleBuilding.entries) {
            // every stage of a building is a clearly different picture (not a renamed copy of the finished room)
            for (i in 0..3) for (j in i + 1..3) {
                val d = differs(grids.getValue(b to i), grids.getValue(b to j))
                assertTrue("$b stage $i vs $j differ only in ${"%.3f".format(d)} of the screen", d >= .12)
            }
            // the light on the open sea is the configured one: blue-green by day, warm in the evening, dark at night
            for (level in 0..2) {
                // (7.9.12: where the open book of a stage lies over that strip of sea, the sea is not visible there)
                if ((StageScenes.of(b, level).book?.top ?: 1f) < .19f) continue
                val (r, g, bl) = sea.getValue(b to level)
                val l = lum((0xFF shl 24) or (r shl 16) or (g shl 8) or bl)
                val m = "$b stage $level sea ($r,$g,$bl)"
                when (StageScenes.of(b, level).backdrop) {
                    StageScenes.Backdrop.DAY_BEACH -> assertTrue(m, r - bl <= -12 && l > .09)
                    StageScenes.Backdrop.EVENING_BEACH -> assertTrue(m, r - bl >= 5 && l > .09)
                    StageScenes.Backdrop.NIGHT_SEA -> assertTrue(m, l < .08)
                    StageScenes.Backdrop.INTERIOR -> Unit
                }
            }
        }
    }
}
