package at.gregor.layermaxxing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 7.9.14 on the rendered production views (Robolectric NATIVE graphics, JVM simulation, not a device): the data sits
 * ON the surfaces of the painted place it was painted for (411 × 891 dp, where the whole surfaces are on screen):
 *  - post: the brass slider on the brass band / slider, the person filter on the stamps / cubbies / under the band, the
 *    letters in the drawer / on the trunk / in the tray;
 *  - house: my picture on the wooden disc / in the frame; the counters on the slips;
 *  - library: the opened book stands on the painted book's lower edge; the register on its ribbons / drawers / banners;
 *  - groups: the members around the fire / table on every side;
 *  - harbour: the notes on the notice surface; friends: out on the sea of the picture;
 *  - on every one of the 24 stages: no data object overlaps another one, the header or the golden action.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class StageSceneAnchorTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val density get() = rule.activity.resources.displayMetrics.density

    private fun slots(prefix: String = ""): List<Pair<String, Rect>> = rule.onAllNodes(SemanticsMatcher("scene slot") { it.config.getOrNull(SceneSlotKey)?.startsWith(prefix) == true }, useUnmergedTree = true)
        .fetchSemanticsNodes().map { it.config[SceneSlotKey] to it.boundsInRoot }
    private fun node(desc: String) = rule.onAllNodesWithContentDescription(desc, substring = true, useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot

    private var current by mutableStateOf(IsleBuilding.POST to 0)
    private fun show(b: IsleBuilding, level: Int) {
        rule.runOnIdle { current = b to level; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        rule.waitForIdle(); repeat(20) { rule.mainClock.advanceTimeByFrame() }; rule.waitForIdle()
    }
    private fun start(postLetters: List<ApiClient.Message> = StageFixtures.letters) {
        rule.mainClock.autoAdvance = false
        rule.setContent { androidx.compose.runtime.key(current) { StageFixtures.Place(current.first, current.second, postLetters = postLetters) } }
    }

    /** The painted surface on screen (px), mapped like FunctionStage maps the picture (Crop). */
    private fun surface(b: IsleBuilding, level: Int, s: StageScenes.Surface): Rect {
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val r = StageScenes.of(b, level).surface(s)!!
        val aspect = 900f / 1350f
        val byHeight = root.width / root.height < aspect
        val iw = if (byHeight) root.height * aspect else root.width
        val ih = if (byHeight) root.height else root.width / aspect
        // the same pan as FunctionStage (every surface on screen)
        val l = if (byHeight) StageScenes.panLeft(StageScenes.of(b, level), root.width, iw, 8 * density) else (root.width - iw) / 2; val t = (root.height - ih) / 2
        return Rect(l + iw * r.left, t + ih * r.top, l + iw * r.right, t + ih * r.bottom)
    }
    private fun exact(desc: String) = rule.onAllNodesWithContentDescription(desc, substring = false, useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot
    private fun Rect.near(o: Rect, slack: Float) = Rect(o.left - slack, o.top - slack, o.right + slack, o.bottom + slack).contains(center)

    @Test fun thePostsObjectsAreItsControlsAndTheLettersLieInTheDrawer() {
        // ARRIVED test-only copies keep this holder-geometry test populated; the shared records stay pending.
        val postLetters = StageFixtures.letters.map { if (it.incoming) it.copy(unlocked = true) else it }
        start(postLetters = postLetters)
        val d = NativePostTestDriver(rule)
        for (level in 1..3) {
            show(IsleBuilding.POST, level)
            val anchors = PostRegistration.of(level)
            val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
            val aspect = 900f / 1350f
            val iw = maxOf(root.width, root.height * aspect)
            val ih = iw / aspect
            val left = StageScenes.panLeft(StageScenes.of(IsleBuilding.POST, level), root.width, iw, 8 * density)
            val top = (root.height - ih) / 2
            fun mapped(box: LetterTree.Box) = Rect(left + iw * box.l, top + ih * box.t, left + iw * box.r, top + ih * box.b)
            for ((tag, at) in listOf("post-filter" to anchors.filterTag, "post-time-rail" to anchors.timeRail, "tree-pen" to anchors.pen, "tree-padlock" to anchors.padlock)) {
                val r = d.tag(tag).fetchSemanticsNode().boundsInRoot
                assertTrue("$level $tag on its measured painted object", r.center.near(mapped(at), density))
                assertTrue("$level $tag touch area", r.width >= 48 * density - 1 && r.height >= 48 * density - 1)
            }
            val shown = d.slots("letter:")
            // Every registered face owns one independently selectable real letter.
            val liveReceived = postLetters.filter { it.incoming && it.groupId == null && it.unlocked }
            val byId = liveReceived.associateBy { it.id }
            val page = receivedMonthPages(liveReceived, anchors.sheets.size).firstOrNull().orEmpty().map { byId.getValue(it) }
            val fronts = page
            assertTrue("POST$level actual arrived faces for holder geometry", fronts.isNotEmpty())
            assertEquals("POST$level occupied physical faces", fronts.size, shown.size)
            assertEquals("POST$level exact front letters in holder order",
                fronts.map { "letter:${it.id}" }, shown.map { it.config[SceneSlotKey] })
            shown.forEachIndexed { i, node ->
                val r = node.boundsInRoot
                assertTrue("$level letter $i on its actual holder", r.center.near(mapped(anchors.sheets[i]), density))
                assertTrue("$level letter on screen", r.left >= -1 && r.right <= root.right + 1 && r.top >= 64 * density)
            }
            assertEquals(liveReceived.map { "letter:${it.id}" }.toSet(), d.letters())
            assertTrue(d.keys("person:").isEmpty())
            d.tap("post-person")
            assertTrue(d.keys("person:").isNotEmpty())
            d.back()
            assertTrue(d.keys("person:").isEmpty())
        }
    }

    private fun androidx.compose.ui.geometry.Offset.near(o: Rect, slack: Float) = Rect(o.left - slack, o.top - slack, o.right + slack, o.bottom + slack).contains(this)
    private fun abs(f: Float) = kotlin.math.abs(f)

    @Test fun myPictureIsOnTheDiscOrInTheFrame() {
        start()
        for (level in 0..3) {
            show(IsleBuilding.HOUSE, level)
            val me = slots("me").single().second
            val at = surface(IsleBuilding.HOUSE, level, StageScenes.Surface.AVATAR)
            // centred on the painted disc / frame (the picture is panned so the frame is on screen, no exception)
            assertTrue("$level me ${me.center} centred on $at", kotlin.math.abs(me.center.x - at.center.x) <= 4 * density && kotlin.math.abs(me.center.y - at.center.y) <= 4 * density)
            assertTrue("$level me $me inside $at", at.inflate(2 * density).contains(me.topLeft) && at.inflate(2 * density).contains(me.bottomRight) || me.width >= at.width * .9f)
            assertEquals("$level three slips", 3, slots("keepsake:").size)
        }
    }

    @Test fun theOpenedBookStandsOnThePaintedBookAndTheRegisterIsThere() {
        start()
        for (level in 0..3) {
            show(IsleBuilding.LIBRARY, level)
            val book = slots("book:").single().second
            val painted = surface(IsleBuilding.LIBRARY, level, StageScenes.Surface.BOOK)
            assertTrue("$level book bottom ${book.bottom} on the painted book $painted", book.bottom in painted.top..painted.bottom + 2 * density)
            assertEquals("$level Alle + four categories", 5, rule.onAllNodes(SemanticsMatcher("register") { n ->
                n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.any { it.startsWith("Register ") } == true
            }, useUnmergedTree = true).fetchSemanticsNodes().size)
            rule.onAllNodesWithText("Wort oder Erklärung suchen", useUnmergedTree = true)[0].assertExists()
        }
    }

    @Test fun membersSitAroundTheFireOrTableAndTheOtherGroupIsOneTapAway() {
        start()
        for (level in 0..3) {
            show(IsleBuilding.CAMPFIRE, level)
            val centre = slots("group:").single().second.center
            val members = slots("member:1:")
            assertEquals("$level all four members of Bergfreunde", 4, members.size)
            val angles = members.map { (_, r) -> Math.toDegrees(kotlin.math.atan2((r.center.y - centre.y).toDouble(), (r.center.x - centre.x).toDouble())) }
            for (q in listOf(-90.0, 0.0, 90.0, 180.0)) assertTrue("$level side $q $angles", angles.any { a -> kotlin.math.abs(((a - q + 540) % 360) - 180) < 50 })
            // the other group: its pennant is on the place, one tap brings it to the fire / table
            rule.onAllNodes(SemanticsMatcher("WG") { it.config.getOrNull(SceneSlotKey) == "pennant:2" }, useUnmergedTree = true)[0].performClick()
            rule.waitForIdle(); repeat(5) { rule.mainClock.advanceTimeByFrame() }
            assertEquals(3, slots("member:2:").size)
            assertTrue(slots("member:1:").isEmpty())
        }
    }

    @Test fun questNotesHangOnTheBoardAndFriendsAreOutOnTheSea() {
        start()
        for (level in 0..3) {
            show(IsleBuilding.HARBOUR, level)
            val board = surface(IsleBuilding.HARBOUR, level, StageScenes.Surface.BOARD)
            val notes = slots("quest:")
            assertTrue("$level", notes.isNotEmpty())
            notes.forEach { (id, r) -> assertTrue("$level $id ${r.center} on $board", r.center.y >= board.top - 24 * density) }
            show(IsleBuilding.LIGHTHOUSE, level)
            val sea = surface(IsleBuilding.LIGHTHOUSE, level, StageScenes.Surface.SEA)
            val friends = slots("friend:")
            assertTrue("$level", friends.isNotEmpty())
            friends.forEach { (id, r) -> assertTrue("$level $id ${r.center} on the sea $sea", r.center.y in sea.top - 8 * density..sea.bottom + 60 * density) }
        }
    }

    @Test fun onNoneOfThe24StagesDoesADataObjectCoverAnotherOrTheControls() {
        start()
        for (b in IsleBuilding.entries) for (level in 0..3) {
            show(b, level)
            val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
            val objects = slots().filter { !it.first.startsWith("book:") }
            val msg = "$b $level"
            for (i in objects.indices) for (j in i + 1 until objects.size) {
                val a = objects[i].second.deflate(4 * density); val c = objects[j].second.deflate(4 * density)
                assertTrue("$msg ${objects[i].first} ${objects[i].second} overlaps ${objects[j].first} ${objects[j].second}", !a.overlaps(c))
            }
            val action = StageFixtures.action(b, level)?.let { rule.onAllNodesWithText(it)[0].fetchSemanticsNode().boundsInRoot }
            for ((id, r) in objects) {
                assertTrue("$msg $id below the header", r.top >= 100 * density)
                assertTrue("$msg $id inside the screen", r.left >= -2 * density && r.right <= root.right + 2 * density)
                action?.let { assertTrue("$msg $id over the action", !r.deflate(2 * density).overlaps(it)) }
            }
        }
    }
}
