package at.gregor.layermaxxing

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.SemanticsMatcher
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
 * pixels, not only on semantics: the header sign reads (contrast of the title against its painted sign), 7.9.14 the
 * stage's painted place fills the screen as mapped, and every stage of a building looks clearly different from its others.
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
    /** Mean colour of the window's left 2 % between 30 % and 70 % of the height (no UI there). */
    private fun edgeStrip(b: Bitmap): IntArray {
        var r = 0L; var g = 0L; var bl = 0L; var n = 0
        for (y in (b.height * .3).toInt() until (b.height * .7).toInt() step 2) for (x in 0 until (b.width * .02).toInt().coerceAtLeast(2)) {
            val p = b.getPixel(x, y); r += (p shr 16) and 255; g += (p shr 8) and 255; bl += p and 255; n++
        }
        return intArrayOf((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
    }
    /** The same strip taken from the decoded scene picture as FunctionStage maps it onto a [w] × [h] window (Crop). */
    private fun pictureEdge(scene: StageScenes.Scene, w: Int, h: Int): IntArray {
        val id = scene.picture
        val res = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val pic = android.graphics.BitmapFactory.decodeResource(res, id, android.graphics.BitmapFactory.Options().apply { inScaled = false })
        val aspect = pic.width.toFloat() / pic.height
        val byHeight = w.toFloat() / h < aspect
        val iw = if (byHeight) h * aspect else w.toFloat(); val ih = if (byHeight) h.toFloat() else w / aspect
        val l = if (byHeight) StageScenes.panLeft(scene, w.toFloat(), iw, 8 * res.displayMetrics.density) else (w - iw) / 2; val t = (h - ih) / 2
        var r = 0L; var g = 0L; var bl = 0L; var n = 0
        for (y in (h * .3).toInt() until (h * .7).toInt() step 2) for (x in 0 until (w * .02).toInt().coerceAtLeast(2)) {
            val px = (((x - l) / iw) * pic.width).toInt().coerceIn(0, pic.width - 1); val py = (((y - t) / ih) * pic.height).toInt().coerceIn(0, pic.height - 1)
            val p = pic.getPixel(px, py); r += (p shr 16) and 255; g += (p shr 8) and 255; bl += p and 255; n++
        }
        pic.recycle()
        return intArrayOf((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
    }

    @Test fun allTwentyFourStagesRenderTheirOwnSceneWithAReadableHeaderAndTheRealData() {
        var current by mutableStateOf(IsleBuilding.POST to 0)
        rule.mainClock.autoAdvance = false
        rule.setContent { androidx.compose.runtime.key(current) { StageFixtures.Place(current.first, current.second) } }
        val grids = mutableMapOf<Pair<IsleBuilding, Int>, IntArray>()
        val edges = mutableMapOf<Pair<IsleBuilding, Int>, Pair<IntArray, IntArray>>()
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
            StageFixtures.action(b, level)?.let { rule.onAllNodesWithText(it)[0].assertExists() }
            // the real data is there on every stage
            when (b) {
                // 1011: at rest the post tells its real numbers on its plate (the letters appear at the touched drawer)
                // (the letter tree, a clean stage: the six letters are written on its sheets)
                IsleBuilding.POST -> {
                    val post = NativePostTestDriver(rule)
                    assertEquals(msg, minOf(6, PostRegistration.of(level).sheets.size), post.keys("letter:").size)
                    assertEquals(msg, StageFixtures.letters.map { "letter:${it.id}" }.toSet(), post.letters())
                    if (post.exists("tree-time-now")) post.tap("tree-time-now")
                }
                IsleBuilding.HARBOUR -> rule.onAllNodesWithText("Gemeinsam auf den Gipfel")[0].assertExists()
                IsleBuilding.LIGHTHOUSE -> { rule.onAllNodesWithText("Jonas")[0].assertExists(); rule.onAllNodesWithText("Gerfried")[0].assertExists() }
                IsleBuilding.LIBRARY -> rule.onAllNodesWithContentDescription("Register Witze")[0].assertExists()
                IsleBuilding.CAMPFIRE -> rule.onAllNodesWithText("Bergfreunde", substring = true)[0].assertExists()
                IsleBuilding.HOUSE -> rule.onAllNodesWithText("Gregor")[0].assertExists()
            }
            // header readability on the pixels: the cream title against its painted sign and shadow (WCAG AA 4.5:1)
            val whole = window()
            // (a clean stage draws no sign: its name is only read out; nothing to measure)
            if (!scene.clean) {
                val title = crop(whole, rule.onAllNodesWithContentDescription("Ort: $name")[0].fetchSemanticsNode().boundsInWindow)
                val ls = (0 until title.height step 2).flatMap { y -> (0 until title.width step 2).map { x -> lum(title.getPixel(x, y)) } }.sorted()
                val hi = ls[(ls.size * .95).toInt().coerceAtMost(ls.size - 1)]; val lo = ls[(ls.size * .05).toInt()]
                assertTrue("$msg title contrast ${(hi + .05) / (lo + .05)}", (hi + .05) / (lo + .05) >= 4.5)
            }
            // the compose root (the window may have a status bar inset above it)
            val shot = crop(whole, rule.onRoot().fetchSemanticsNode().boundsInWindow)
            grids[b to level] = grid(shot)
            edges[b to level] = edgeStrip(shot) to pictureEdge(scene, shot.width, shot.height)
            whole.recycle()
        }
        for (b in IsleBuilding.entries) {
            // every stage of a building is a clearly different picture (not a renamed copy of the finished room)
            for (i in 0..3) for (j in i + 1..3) {
                val d = differs(grids.getValue(b to i), grids.getValue(b to j))
                assertTrue("$b stage $i vs $j differ only in ${"%.3f".format(d)} of the screen", d >= .12)
            }
            // 7.9.14: the painted place itself fills the screen as mapped (Crop): along the left edge (the 8 dp margin
            // no surface uses, between the header and the action row) the window shows the scene picture's own colours
            for (level in 0..3) {
                val (shot, pic) = edges.getValue(b to level)
                assertTrue("$b stage $level edge differs from its picture by $shot vs $pic", listOf(0, 1, 2).all { kotlin.math.abs(shot[it] - pic[it]) <= 28 })
            }
        }
    }
}
