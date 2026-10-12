package at.gregor.layermaxxing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 7.9.6 "flow": one context at a time on the own island, placement only on land, long press moves in
 * the same touch, the Bauen button is always there, the creative mode is one clear chip.
 * Real IslandWorld + app navigation on Robolectric (JVM simulation, not a device).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class IslandFlowTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private class Saved { var state = ApiClient.IslandConstruction() }

    private fun construction(creative: Boolean, buildings: Map<String, Int>, land: Int) = ApiClient.IslandConstruction(
        mode = if (creative) "creative" else "normal", available = 500, ageDays = 21, buildings = buildings, land = land)
    private val grown = IsleBuilding.entries.associate { it.name to 3 }

    @Composable private fun Island(saved: Saved, creative: Boolean, buildings: Map<String, Int>, land: Int, eligible: Boolean = false) {
        var state by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(construction(creative, buildings, land)) }
        var creativeOn by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(creative) }
        saved.state = state
        val items = IsleDecor.labels.keys.mapIndexed { i, k -> ApiClient.DecorItem(k, i * 30, true) }
        val home = ApiClient.HomeIsland(1, emptyMap(), 500, items, construction = state)
        VillageTheme(true) { IslandWorld(
            ownName = "Veit", ownEmoji = "🦊", friends = emptyList(), groups = emptyList(),
            islands = ApiClient.Islands(500, emptyList()),
            quests = emptyList(), letters = emptyList(), topics = emptyList(), opened = emptyMap(), onBack = {}, onChat = {}, onComposeLetter = {},
            onOpenLetter = {}, onLockedTap = {}, onTopics = {}, onAllTopics = {}, onGlossary = {}, onPeople = {}, onCreateQuest = { _, _, _, _, _, _ -> },
            onQuestDone = { _, _ -> }, onQuestDelete = {}, ownId = 1, startView = "home", creativeActive = creativeOn,
            creativeEligible = eligible,
            // like LayerHome: the switch is stored and the server mode follows
            onCreativeSwitch = { on -> creativeOn = on; state = state.copy(mode = if (on) "creative" else "normal") },
            loadIsland = { home.copy(construction = state) },
            mainNavigation = { LayermaxxingTheme("light") { MainNavigationBar(listOf(MainTab.CHATS, MainTab.CASTLES, MainTab.MORE), MainTab.CASTLES, 0) {} } },
            changeConstruction = { c, req ->
                check(c.revision == state.revision) { "409" }
                val b = req.building
                state = when (req.action) {
                    "upgrade" -> c.copy(buildings = c.buildings + (b!! to ((c.buildings[b] ?: 0) + 1)), positions = req.position?.let { c.positions + (b to it) } ?: c.positions, revision = c.revision + 1)
                    "move" -> c.copy(positions = c.positions + (b!! to req.position!!), revision = c.revision + 1)
                    else -> c
                }.copy(mode = state.mode)
                home.copy(construction = state)
            },
        ) }
    }

    private fun start(saved: Saved = Saved(), creative: Boolean = true, buildings: Map<String, Int> = emptyMap(), land: Int = 0, eligible: Boolean = false): Saved {
        rule.setContent { Island(saved, creative, buildings, land, eligible) }
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(900)
        return saved
    }
    private fun tick(ms: Long = 600) { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); rule.mainClock.advanceTimeBy(ms) }
    private fun openShop() { rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].performClick(); tick() }
    private fun placePost() { openShop(); rule.onAllNodesWithContentDescription("Post platzieren")[0].performClick(); tick() }
    private val confirmNode get() = rule.onNode(hasText("Bestätigen") or hasContentDescription("Bestätigen"))
    private val raster get() = rule.onAllNodesWithContentDescription("Bauplatzraster")[0]
    private fun tapPost() { rule.onAllNodesWithContentDescription("Post").filter(hasClickAction())[0].performClick(); tick(800) }
    private val root get() = rule.onRoot().getBoundsInRoot()
    /** Empty ground/sea at the upper left of the island view, away from every piece and from the actions. */
    private fun tapEmptyGround() = rule.onRoot().performTouchInput { click(Offset(width * .08f, height * .3f)) }

    // 1) Placement only on the grid -------------------------------------------------------------

    @Test fun dragIntoTheWaterKeepsThePreviewOnTheNearestLandCell() {
        val saved = start(); placePost()
        val a = IslandGrid.project(listOf(11, 7), 0)
        raster.performTouchInput { click(Offset(width * a.first, height * a.second)) }; tick()
        raster.performTouchInput { swipe(Offset(width * a.first, height * a.second), Offset(width * .01f, height * .01f), 400) }; tick()
        confirmNode.assertIsEnabled()
        confirmNode.performClick(); tick()
        val p = saved.state.positions["POST"]
        assertTrue("saved at $p, must be a land cell", p != null && IslandGrid.buildable(p, saved.state.land))
        assertTrue("moved towards the finger, not left in the middle: $p", p!![0] < 11)
        // it stands on the shore: at least one neighbour cell is water
        assertTrue("on the shore: $p", listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1).any { (dx, dy) -> !IslandGrid.buildable(p[0] + dx, p[1] + dy, saved.state.land) })
    }

    @Test fun aTapInTheWaterDoesNotMoveThePreview() {
        val saved = start(); placePost()
        val a = IslandGrid.project(listOf(11, 7), 0)
        raster.performTouchInput { click(Offset(width * a.first, height * a.second)) }; tick()
        raster.performTouchInput { click(Offset(width * .03f, height * .95f)) }; tick()
        confirmNode.assertIsEnabled()
        confirmNode.performClick(); tick()
        assertEquals(listOf(11, 7), saved.state.positions["POST"])
    }

    // 4) Long press = move in the same touch -------------------------------------------------------

    @Test fun longPressLiftsTheBuildingAndTheSameFingerMovesIt() {
        val saved = start(buildings = grown, land = 5)
        val c = construction(true, grown, 5)
        // the building nearest the middle of the screen (away from the edge, where the camera would start scrolling)
        val midY = (root.top + root.bottom) / 2; val midX = (root.left + root.right) / 2
        fun label(b: IsleBuilding) = rule.onAllNodesWithContentDescription(b.label).filter(hasClickAction())[0].getBoundsInRoot()
        val b = IsleBuilding.entries.minBy { val l = label(it); kotlin.math.hypot(((l.left + l.right) / 2 - midX).value, ((l.top + l.bottom) / 2 - midY).value) }
        val from = IslandLayout.defaultPosition(b)
        val target = listOf(2, 3, -2, -3).flatMap { dy -> listOf(0, 1, -1).map { dx -> listOf(from[0] + dx, from[1] + dy) } }
            .first { IslandLayout.valid(c, b, it) }
        val ground = rule.onAllNodesWithContentDescription("Boden")[0].getBoundsInRoot()
        val p0 = IslandGrid.project(from, 5); val p1 = IslandGrid.project(target, 5)
        val tag = label(b)
        rule.onRoot().performTouchInput {
            // on the sprite, just above its name sign
            val down = Offset(((tag.left + tag.right) / 2).toPx(), (tag.top - 22.dp).toPx())
            val delta = Offset((ground.width * (p1.first - p0.first)).toPx(), (ground.height * (p1.second - p0.second)).toPx())
            down(down)
            advanceEventTime(LONG_PRESS_MS + 150)
            // the same finger, never lifted, carries it
            for (k in 1..12) { moveTo(down + delta * (k / 12f)); advanceEventTime(30) }
            up()
        }
        tick()
        raster.assertExists()
        confirmNode.assertIsEnabled()
        confirmNode.performClick(); tick()
        assertEquals("${b.label} moved by the same finger", target, saved.state.positions[b.name])
    }

    @Test fun aShortTapStillOpensTheActionsAndDoesNotLift() {
        start(buildings = grown, land = 5)
        val tag = rule.onAllNodesWithContentDescription("Post").filter(hasClickAction())[0].getBoundsInRoot()
        rule.onRoot().performTouchInput { click(Offset(((tag.left + tag.right) / 2).toPx(), (tag.top - 22.dp).toPx())) }
        tick(800)
        rule.onAllNodesWithContentDescription("Gebäudeaktionen")[0].assertExists()
        rule.onAllNodesWithContentDescription("Bauplatzraster").assertCountEquals(0)
    }

    // 2) + 3) One context at a time, nothing stacked ---------------------------------------------

    @Test fun restStateShowsOnlyANarrowEdgeGroupAboveTheNavigation() {
        start(buildings = grown, land = 5)
        val r = root
        for (label in listOf("Bauen", "Bearbeiten", "Menü")) {
            val b = rule.onAllNodesWithText(label)[0].getBoundsInRoot()
            assertTrue("$label sits in the edge group on the right, was at ${(b.left + b.right) / 2}", ((b.left + b.right) / 2).value >= r.width.value * .75f)
        }
        rule.onAllNodesWithText("Chats")[0].assertExists(); rule.onAllNodesWithText("Inseln")[0].assertExists(); rule.onAllNodesWithText("Mehr")[0].assertExists()
    }

    @Test fun tappingABuildingShowsCompactActionsAtTheBuildingAndNoBottomCard() {
        start(buildings = grown, land = 5)
        tapPost()
        rule.onAllNodesWithContentDescription("Info-Leiste").assertCountEquals(0)
        val bar = rule.onAllNodesWithContentDescription("Gebäudeaktionen")[0].getBoundsInRoot()
        val post = rule.onAllNodesWithContentDescription("Post").filter(hasClickAction())[0].getBoundsInRoot()
        val gap = maxOf(bar.top - post.bottom, post.top - bar.bottom)
        assertTrue("actions hang at the building (gap $gap)", gap.value <= 140f)
        assertTrue("actions stay compact (${bar.height})", bar.height.value <= root.height.value * .22f)
        for (label in listOf("Öffnen", "Info", "Verschieben", "Abreißen")) rule.onAllNodesWithText(label)[0].assertExists()
        // a tap on empty ground closes it again
        tapEmptyGround()
        tick(900)
        rule.onAllNodesWithContentDescription("Gebäudeaktionen").assertCountEquals(0)
    }

    @Test fun aTapOnTheOpenSeaBesideTheIslandClosesTheActions() {
        start(buildings = grown, land = 5)
        tapPost()
        rule.onAllNodesWithContentDescription("Gebäudeaktionen")[0].assertExists()
        // above the island box, below the header: nothing on the island takes this tap
        rule.onRoot().performTouchInput { click(Offset(width * .5f, height * .22f)) }
        tick(900)
        rule.onAllNodesWithContentDescription("Gebäudeaktionen").assertCountEquals(0)
    }

    @Test fun buildSheetIsSlimAndNeverShownTogetherWithTheActions() {
        start(buildings = grown, land = 5)
        tapPost()
        openShop()
        rule.onAllNodesWithContentDescription("Gebäudeaktionen").assertCountEquals(0)
        val sheet = rule.onAllNodesWithContentDescription("Bauleiste")[0].getBoundsInRoot()
        assertTrue("sheet covers ${sheet.height} of ${root.height}", sheet.height.value <= root.height.value * .28f)
        // nothing else stacked above the navigation while the sheet is open
        rule.onAllNodesWithText("Bearbeiten").assertCountEquals(0)
        rule.onAllNodesWithText("Menü").assertCountEquals(0)
        // tapping a building on the island swaps the sheet for its actions
        tapPost()
        rule.onAllNodesWithContentDescription("Bauleiste").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Gebäudeaktionen")[0].assertExists()
    }

    @Test fun theBuildButtonIsAlwaysThere() {
        start(buildings = grown, land = 5)
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].assertExists()
        tapPost()
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].assertExists()
        tapEmptyGround(); tick(900)
        rule.onAllNodesWithText("Bearbeiten")[0].performClick(); tick()
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].assertExists()
    }

    @Test fun theBuildButtonShowsTheCostInNormalMode() {
        start(creative = false)
        rule.onAllNodesWithText("ab 25 P")[0].assertExists()
    }

    @Test fun placementHidesTheOtherChromeAndTheHintFadesOut() {
        start(); placePost()
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Bauleiste").assertCountEquals(0)
        rule.onAllNodesWithText("Ziehen oder tippen · Grün = frei, Rot = belegt")[0].assertExists()
        tick(4000)
        rule.onAllNodesWithText("Ziehen oder tippen · Grün = frei, Rot = belegt").assertCountEquals(0)
        confirmNode.assertExists()
    }

    // 6) Creative mode: one clear chip ---------------------------------------------------------------

    @Test fun creativeModeIsOneChipAndTheShopButtonSaysGratis() {
        start(creative = true)
        rule.onAllNodesWithText("Kreativmodus · alles gratis").assertCountEquals(1)
        rule.onAllNodesWithText("KREATIV").assertCountEquals(0)
        rule.onAllNodesWithText("gratis")[0].assertExists()
    }

    @Test fun theIslandMenuHasAClearCreativeSwitchThatActsAtOnceAndExplainsItselfOnce() {
        start(creative = false, eligible = true)
        rule.onAllNodesWithText("Kreativmodus · alles gratis").assertCountEquals(0)
        rule.onAllNodesWithText("Menü")[0].performClick(); tick(800)
        rule.onAllNodesWithText("Kreativmodus: Aus")[0].assertExists()
        rule.onAllNodesWithText("Frei bauen, ausbauen, abreißen – kostenlos")[0].assertExists()
        rule.onAllNodesWithContentDescription("Kreativmodus")[0].performClick(); tick(800)
        // visible at once: the one chip, "gratis" on the Bauen button, a one-time note
        rule.onAllNodesWithText("Kreativmodus · alles gratis").assertCountEquals(1)
        rule.onAllNodesWithText("gratis")[0].assertExists()
        rule.onAllNodesWithContentDescription("Hinweis Kreativmodus")[0].assertExists()
        // nothing disappeared
        for (label in listOf("Bauen", "Bearbeiten", "Menü", "Chats", "Inseln", "Mehr")) rule.onAllNodesWithText(label)[0].assertExists()
        tick(8000)
        rule.onAllNodesWithContentDescription("Hinweis Kreativmodus").assertCountEquals(0)
        // off and on again: the note is not repeated
        rule.onAllNodesWithText("Menü")[0].performClick(); tick(800)
        rule.onAllNodesWithText("Kreativmodus: Ein")[0].assertExists()
        rule.onAllNodesWithContentDescription("Kreativmodus")[0].performClick(); tick(800)
        rule.onAllNodesWithText("Kreativmodus · alles gratis").assertCountEquals(0)
        rule.onAllNodesWithText("Menü")[0].performClick(); tick(800)
        rule.onAllNodesWithContentDescription("Kreativmodus")[0].performClick(); tick(800)
        rule.onAllNodesWithContentDescription("Hinweis Kreativmodus").assertCountEquals(0)
    }

    @Test fun withoutPermissionTheMenuHasNoCreativeSwitch() {
        start(creative = false, eligible = false)
        rule.onAllNodesWithText("Menü")[0].performClick(); tick(800)
        rule.onAllNodesWithText("Kreativmodus: Aus").assertCountEquals(0)
    }

    // Labels on a small island are readable in full ("Mein Haus" was cut off) ----------------------

    @Test fun labelsOnASmallIslandAreNotCutOff() {
        start(creative = false, buildings = IsleBuilding.entries.associate { it.name to 1 }, land = 0)
        for (name in listOf("Mein Haus", "Wörterbuch", "Gruppen")) {
            val node = rule.onAllNodesWithText(name)[0].fetchSemanticsNode()
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
            val t = layouts.single()
            // (the semantics layout result is laid out at the max width, so didOverflowWidth is meaningless here:
            // check that the one line holds the whole name and fits the drawn size)
            val fits = t.lineCount == 1 && t.getLineEnd(0, visibleEnd = true) == name.length && t.getLineRight(0) <= t.size.width + .5f && !t.didOverflowHeight
            assertTrue("$name is cut off: size=${t.size} constraints=${t.layoutInput.constraints} lines=${t.lineCount} end=${t.getLineEnd(0, true)} right=${t.getLineRight(0)}", fits)
        }
    }
}
