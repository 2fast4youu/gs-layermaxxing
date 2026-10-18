package at.gregor.layermaxxing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 7.9.12 on the rendered production views (Robolectric NATIVE graphics, JVM simulation, not a device): the data
 * objects really sit IN the stage's painted place, not on a card below it.
 *  - letter tree / mailbox stand / beach pole / jetty / notice board: every data object's centre lies on opaque
 *    pixels of the decoded set piece where it is drawn (pinned to the tree, hung on the stand, nailed to the post),
 *  - fire ring / meeting hut: the members sit in a ring AROUND the fire / the crate table, on every side of it,
 *  - the book lies on the crates (its lower edge on opaque crate pixels),
 *  - on every one of the 24 stages: no data object overlaps another one, the header or the golden action,
 *  - more friends than places: scenic pages, every friend reachable.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class StageSceneAnchorTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources
    private fun decode(id: Int): Bitmap = BitmapFactory.decodeResource(res, id, BitmapFactory.Options().apply { inScaled = false })

    private fun slots(prefix: String = ""): List<Pair<String, Rect>> = rule.onAllNodes(SemanticsMatcher("scene slot") { it.config.getOrNull(SceneSlotKey)?.startsWith(prefix) == true }, useUnmergedTree = true)
        .fetchSemanticsNodes().map { it.config[SceneSlotKey] to it.boundsInRoot }
    /** The unclipped bounds of a node (a set piece may reach beyond the screen edge). */
    private fun full(n: SemanticsNode) = Rect(n.positionInRoot, androidx.compose.ui.geometry.Size(n.size.width.toFloat(), n.size.height.toFloat()))
    private fun node(desc: String): SemanticsNode = rule.onAllNodesWithContentDescription(desc, substring = true, useUnmergedTree = true)[0].fetchSemanticsNode()

    private var current by mutableStateOf(IsleBuilding.POST to 0)
    private fun show(b: IsleBuilding, level: Int) {
        rule.runOnIdle { current = b to level; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        rule.waitForIdle(); repeat(20) { rule.mainClock.advanceTimeByFrame() }; rule.waitForIdle()
    }
    private fun start() {
        rule.mainClock.autoAdvance = false
        rule.setContent { androidx.compose.runtime.key(current) { StageFixtures.Place(current.first, current.second) } }
    }

    /** Alpha (0..255) of the set piece's picture at a point given in root px, the piece drawn FillBounds into [bounds]. */
    private fun alphaAt(bmp: Bitmap, bounds: Rect, x: Float, y: Float, radius: Float = .02f): Int {
        var best = 0
        for (dx in listOf(-radius, 0f, radius)) for (dy in listOf(-radius, 0f, radius)) {
            val u = ((x - bounds.left) / bounds.width + dx).coerceIn(0f, .999f); val v = ((y - bounds.top) / bounds.height + dy).coerceIn(0f, .999f)
            best = maxOf(best, bmp.getPixel((u * bmp.width).toInt(), (v * bmp.height).toInt()) ushr 24)
        }
        return best
    }

    @Test fun dataObjectsSitOnThePaintedSetPiece() {
        start()
        val cases = listOf(IsleBuilding.POST to 0, IsleBuilding.POST to 1, IsleBuilding.HARBOUR to 0, IsleBuilding.HARBOUR to 1, IsleBuilding.HARBOUR to 2)
        for ((b, level) in cases) {
            show(b, level)
            val scene = StageScenes.of(b, level)
            val piece = full(node("Bühnenbild: ${scene.caption.substringBefore(":")}"))
            val bmp = decode(scene.piece!!.res)
            val objects = slots(if (b == IsleBuilding.POST) "friend:" else "quest:")
            assertTrue("$b $level has objects", objects.isNotEmpty())
            for ((id, r) in objects) {
                assertTrue("$b $level $id inside the picture", piece.contains(r.center))
                assertTrue("$b $level $id on painted pixels at ${(r.center.x - piece.left) / piece.width},${(r.top + r.height * .3f - piece.top) / piece.height} piece $piece",
                    alphaAt(bmp, piece, r.center.x, r.top + r.height * .3f) >= 128)
            }
        }
        // the opened friend's letters hang on the same tree
        show(IsleBuilding.POST, 0)
        rule.onAllNodes(SemanticsMatcher("Anna") { it.config.getOrNull(SceneSlotKey) == "friend:3" })[0].performClick()
        rule.waitForIdle(); repeat(5) { rule.mainClock.advanceTimeByFrame() }
        val tree = full(node("Bühnenbild: Großer Briefbaum"))
        val bmp = decode(R.drawable.n_tree)
        val anna = slots("letter:")
        assertEquals(3, anna.size) // all three letters with Anna, none filtered
        anna.forEach { (id, r) -> assertTrue(id, alphaAt(bmp, tree, r.center.x, r.top + r.height * .3f) >= 128) }
    }

    @Test fun membersSitInARingAroundTheFireAndTheCrateTable() {
        start()
        for (level in 0..1) {
            show(IsleBuilding.CAMPFIRE, level)
            val centre = slots("group:").single().second.center
            val members = slots("member:1:")
            assertEquals("all four members of Bergfreunde", 4, members.size)
            val d = members.map { (_, r) -> (r.center - centre).getDistance() }
            assertTrue("$level ring ${d}", d.min() > 50f && d.max() / d.min() < 2.6f)
            // on every side of the fire: above, below, left and right of it
            val angles = members.map { (_, r) -> Math.toDegrees(kotlin.math.atan2((r.center.y - centre.y).toDouble(), (r.center.x - centre.x).toDouble())) }
            for (q in listOf(-90.0, 0.0, 90.0, 180.0)) assertTrue("$level side $q $angles", angles.any { a -> kotlin.math.abs(((a - q + 540) % 360) - 180) < 50 })
            // the fire / table itself is in the painted piece and is the quest action
            val piece = full(node("Bühnenbild:"))
            assertTrue(piece.contains(centre))
            // the other group is one page further, around the same fire
            rule.onAllNodesWithContentDescription("Nächste Seite: Gruppe")[0].performClick()
            rule.waitForIdle(); repeat(5) { rule.mainClock.advanceTimeByFrame() }
            assertEquals(3, slots("member:2:").size)
            assertTrue(slots("member:1:").isEmpty())
        }
    }

    @Test fun theBookLiesOnTheDriftwoodCrates() {
        start()
        show(IsleBuilding.LIBRARY, 0)
        val book = slots("book:").single().second
        val crates = full(node("Bühnenbild: Treibholzkisten"))
        val bmp = decode(R.drawable.n_crates)
        assertTrue("book lower edge on the crate", alphaAt(bmp, crates, book.center.x, book.bottom, .03f) >= 128)
        rule.onAllNodesWithText("Wort oder Erklärung suchen", useUnmergedTree = true)[0].assertExists()
    }

    @Test fun onNoneOfThe24StagesDoesADataObjectCoverAnotherOrTheControls() {
        start()
        val density = rule.activity.resources.displayMetrics.density
        for (b in IsleBuilding.entries) for (level in 0..3) {
            show(b, level)
            val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
            val objects = slots().filter { !it.first.startsWith("book:") }
            val msg = "$b $level"
            for (i in objects.indices) for (j in i + 1 until objects.size) {
                val a = objects[i].second.deflate(4 * density); val c = objects[j].second.deflate(4 * density)
                assertTrue("$msg ${objects[i].first} overlaps ${objects[j].first}", !a.overlaps(c))
            }
            val action = StageFixtures.action(b)?.let { rule.onAllNodesWithText(it)[0].fetchSemanticsNode().boundsInRoot }
            for ((id, r) in objects) {
                assertTrue("$msg $id below the header", r.top >= 100 * density)
                assertTrue("$msg $id inside the screen", r.left >= -2 * density && r.right <= root.right + 2 * density)
                action?.let { assertTrue("$msg $id over the action", !r.deflate(2 * density).overlaps(it)) }
            }
        }
    }
}
