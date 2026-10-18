package at.gregor.layermaxxing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * 7.9.12, the proof on the painted pixels (Robolectric NATIVE graphics decodes the real WebP sprites; JVM, not a
 * device). Every piece is composited like [PaintedIslandPlace] draws it (Fit, bottom centre, accent, flag) at the size
 * the renderer uses, on a virtual island [W] px wide, and its opaque pixels (alpha >= 128) are mapped to the lattice
 * tiles of the land:
 *  - every opaque pixel lies in a tile of the piece's mask (no painted part outside its area),
 *  - for every pair of building stages, the densest neighbours the rule allows (in 8 directions) share NO opaque pixel,
 *  - Gegenprobe: the 7.9.11 one-cell neighbours (house stage 3 and post office stage 2 one cell apart) do share pixels.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class IslandMasksPixelTest {
    private val W = 1600f
    private val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources
    private val decoded = HashMap<Int, Bitmap>()
    private fun bitmap(id: Int) = decoded.getOrPut(id) { BitmapFactory.decodeResource(res, id, BitmapFactory.Options().apply { inScaled = false }) }

    /** Opaque pixels of a piece in island px, relative to its foot: x in [-box/2, box/2), y in [-box, 0). */
    private class Body(val box: Int, val opaque: BooleanArray) {
        val pixels: IntArray by lazy { opaque.indices.filter { opaque[it] }.toIntArray() }
        fun at(x: Int, y: Int): Boolean { val i = x + box / 2; val j = y + box; return i in 0 until box && j in 0 until box && opaque[j * box + i] }
    }
    private val bodies = HashMap<Triple<String, Int, Float>, Body>()

    private fun fit(b: Bitmap, side: Float): RectF {
        val k = minOf(side / b.width, side / b.height); return RectF(0f, 0f, b.width * k, b.height * k)
    }
    private fun body(key: String, land: Int, w: Float = W): Body = bodies.getOrPut(Triple(key, land, w)) {
        val shape = IslandMasks.shape(key, land)
        val box = (shape.size * w).roundToInt()
        val bmp = Bitmap.createBitmap(box, box, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp); val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        fun draw(id: Int, side: Float, left: (RectF) -> Float, top: (RectF) -> Float) {
            val b = bitmap(id); val r = fit(b, side); r.offset(left(r), top(r))
            canvas.drawBitmap(b, Rect(0, 0, b.width, b.height), r, paint)
        }
        val parts = key.split(":")
        val (sprite, accent, flag, library) = if (parts[0] == "building") {
            val stage = IslandStages.of(IsleBuilding.valueOf(parts[1]), parts[2].toInt())
            listOf(stage.res, stage.accent, if (stage.level == 3) R.drawable.decor_flag else null, stage.building == IsleBuilding.LIBRARY)
        } else listOf(IsleDecor.shown(parts[1].let { if (it == "?" ) "gnome" else if (it == "?special") "place_x" else it }), null, null, false)
        val s = box.toFloat()
        draw(sprite as Int, s, { (s - it.width()) / 2 }, { s - it.height() })
        (accent as Int?)?.let { a ->
            val side = s * .38f
            if (library as Boolean) draw(a, side, { (s - side) / 2 + (side - it.width()) / 2 }, { (side - it.height()) / 2 })
            else draw(a, side, { s - side + (side - it.width()) / 2 }, { (s - side) / 2 + (side - it.height()) / 2 })
        }
        (flag as Int?)?.let { f -> val side = s * .30f; draw(f, side, { s - side + (side - it.width()) / 2 }, { (side - it.height()) / 2 }) }
        val px = IntArray(box * box); bmp.getPixels(px, 0, box, 0, 0, box, box)
        Body(box, BooleanArray(box * box) { (px[it] ushr 24) >= 128 })
    }
    private fun cellW(land: Int, w: Float = W) = IslandPlans.constructionScale(land) / 20f * w
    private fun cellH(land: Int, w: Float = W) = cellW(land, w) / IslandPlans.ASPECT

    @Test fun everyOpaquePixelLiesInsideThePiecesMask() {
        val keys = IsleBuilding.entries.flatMap { b -> (0..3).map { IslandMasks.buildingKey(b, it) } } +
            (IsleDecor.labels.keys + SpecialDecor.items.map { it.key }).map { IslandMasks.decorKey(it) }
        for (key in keys) for (land in listOf(0, 2, 5)) {
            // a finer raster here (an island 3200 px wide), so thin painted details (poles, flags) keep their pixels
            val b = body(key, land, 2 * W)
            val mask = IslandMasks.shape(key, land).mask.toHashSet()
            val cw = cellW(land, 2 * W); val ch = cellH(land, 2 * W)
            var outside = 0
            for (k in b.pixels) {
                val x = k % b.box - b.box / 2 + .5f; val y = k / b.box - b.box + .5f
                val u = x / cw; val v = y / ch
                // a pixel within 3 % of a tile border may belong to either tile (resampling differs by a sub-pixel)
                val near = listOf(-.03f, .03f).flatMap { du -> listOf(-.03f, .03f).map { dv -> listOf((u + du).roundToInt(), (v + dv).roundToInt()) } }
                if (near.none { it in mask }) outside++
            }
            // stray paint specks below the generator's 24 px (at 720 px) threshold may remain: at most a few pixels
            assertTrue("$key land $land: $outside of ${b.pixels.size} opaque pixels outside the mask", outside <= 6)
            // no invisible clearance: every tile of the mask (except the foot's own spot) holds painted pixels, at most a
            // sliver (8 % of a tile) away (the generator resamples with Lanczos at 720 px, Android bilinear)
            val painted = HashSet<List<Int>>()
            for (k in b.pixels) {
                val u = (k % b.box - b.box / 2 + .5f) / cw; val v = (k / b.box - b.box + .5f) / ch
                for (du in listOf(-.08f, .08f)) for (dv in listOf(-.08f, .08f)) painted += listOf((u + du).roundToInt(), (v + dv).roundToInt())
            }
            val empty = mask - painted - setOf(listOf(0, 0))
            // (a swaying tree or palm also covers the tiles of its two sway extremes)
            val sway = key in setOf("building:POST:0", "building:LIGHTHOUSE:0", "decor:palm", "decor:flag")
            assertTrue("$key land $land: empty tiles $empty", if (sway) empty.size <= 4 else empty.isEmpty())
        }
    }

    private fun overlap(a: Body, b: Body, dx: Int, dy: Int): Int {
        var n = 0
        for (k in a.pixels) { val x = k % a.box - a.box / 2; val y = k / a.box - a.box; if (b.at(x - dx, y - dy)) n++ }
        return n
    }

    @Test fun theDensestNeighboursTheRuleAllowsNeverShareAPaintedPixel() {
        val directions = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, -1 to 1, 1 to -1, -1 to -1)
        var checked = 0
        for (land in listOf(0, 5)) {
            val cw = cellW(land); val ch = cellH(land)
            for (b1 in IsleBuilding.entries) for (s1 in 0..3) for (b2 in IsleBuilding.entries) for (s2 in 0..3) {
                if (b1 == b2) continue
                val k1 = IslandMasks.buildingKey(b1, s1); val k2 = IslandMasks.buildingKey(b2, s2)
                val m1 = IslandMasks.cells(k1, land, listOf(0, 0))
                for ((ux, uy) in directions) {
                    // walk outward until the masks are disjoint: the first spot the rule accepts in this direction
                    val n = (1..20).first { n -> IslandMasks.cells(k2, land, listOf(n * ux, n * uy)).none { it in m1 } }
                    val px = (n * ux * cw).roundToInt(); val py = (n * uy * ch).roundToInt()
                    val o = overlap(body(k1, land), body(k2, land), px, py)
                    assertEquals("$k1 and $k2 at ${n * ux},${n * uy} on land $land", 0, o)
                    checked++
                }
            }
        }
        assertTrue(checked > 7000)
    }

    @Test fun the7911OneCellNeighboursReallyStandInsideEachOther() {
        val land = 5
        val house = body("building:HOUSE:3", land); val post = body("building:POST:2", land)
        for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
            val o = overlap(house, post, (dx * cellW(land)).roundToInt(), (dy * cellH(land)).roundToInt())
            assertTrue("one cell $dx,$dy: $o shared pixels", o > 200)
            // and rule 5 refuses exactly these spots
            val c = ApiClient.IslandConstruction(land = land, buildings = mapOf("HOUSE" to 3, "POST" to 2), positions = mapOf("HOUSE" to listOf(10, 10)))
            assertTrue(!IslandLayout.valid(c, IsleBuilding.POST, listOf(10 + dx, 10 + dy)))
        }
    }
}
