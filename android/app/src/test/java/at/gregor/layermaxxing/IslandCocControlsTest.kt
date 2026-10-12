package at.gregor.layermaxxing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Clash-of-Clans style island controls (7.9.5), driven through the production IslandWorld:
 * check/cross bubbles at the held building, info bar + tap-on-ground, isometric grid in edit mode,
 * finger -> cell -> saved position. Real Compose UI on Robolectric, not a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class IslandCocControlsTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private class Saved { var state = ApiClient.IslandConstruction() }

    @Composable private fun Island(saved: Saved, creative: Boolean = true, grown: Boolean = false) {
        var state by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(ApiClient.IslandConstruction(
            mode = if (creative) "creative" else "normal", available = 500, ageDays = 21,
            buildings = if (grown) IsleBuilding.entries.associate { it.name to 3 } else emptyMap(), land = if (grown) 5 else 0)) }
        saved.state = state
        val items = IsleDecor.labels.keys.mapIndexed { i, k -> ApiClient.DecorItem(k, i * 30, true) }
        val home = ApiClient.HomeIsland(1, emptyMap(), 500, items, construction = state)
        VillageTheme(true) { IslandWorld(
            ownName = "Gregor", ownEmoji = "🦉", friends = emptyList(), groups = emptyList(),
            islands = ApiClient.Islands(500, emptyList()),
            quests = emptyList(), letters = emptyList(), topics = emptyList(), opened = emptyMap(), onBack = {}, onChat = {}, onComposeLetter = {},
            onOpenLetter = {}, onLockedTap = {}, onTopics = {}, onAllTopics = {}, onGlossary = {}, onPeople = {}, onCreateQuest = { _, _, _, _, _, _ -> },
            onQuestDone = { _, _ -> }, onQuestDelete = {}, ownId = 1, startView = "home", creativeActive = creative,
            loadIsland = { home.copy(construction = state) },
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

    private fun start(saved: Saved, creative: Boolean = true, grown: Boolean = false) {
        rule.setContent { Island(saved, creative, grown) }
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(900)
    }
    // Touch handlers write state outside a frame; apply it like the real main loop would, then run the clock.
    private fun tick(ms: Long = 600) { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); rule.mainClock.advanceTimeBy(ms) }
    private fun placePost() {
        rule.onAllNodesWithText("Bauen")[0].performClick(); tick()
        rule.onAllNodesWithContentDescription("Post platzieren")[0].performClick(); tick()
    }
    private val confirmNode get() = rule.onNode(hasText("Bestätigen") or hasContentDescription("Bestätigen"))
    private val cancelNode get() = rule.onNode(hasText("Abbrechen") or hasContentDescription("Abbrechen"))

    @Test fun checkAndCrossStayAtTheHeldBuilding() {
        start(Saved()); placePost()
        val sprite = rule.onAllNodesWithContentDescription("Post")[0].getBoundsInRoot()
        for (button in listOf(confirmNode, cancelNode)) {
            val b = button.getBoundsInRoot()
            val dx = ((b.left + b.right) / 2).value - ((sprite.left + sprite.right) / 2).value
            assertTrue("bubble beside the building, was $dx dp away", kotlin.math.abs(dx) <= sprite.width.value * 1.6f)
            assertTrue("bubble close to the building, was ${(b.top - sprite.bottom).value} dp below its foot", (b.top - sprite.bottom) <= 64.dp && (sprite.top - b.bottom) <= 64.dp)
        }
    }

    @Test fun invalidPositionGreysTheCheckButNotTheCross() {
        // 7.9.6: the sea can no longer be reached (the preview clamps to land, see IslandFlowTest);
        // an occupied cell is the impossible spot now.
        val saved = Saved(); start(saved); placePost()
        val a = IslandGrid.project(listOf(11, 7), 0)
        rule.onAllNodesWithContentDescription("Bauplatzraster")[0].performTouchInput { click(Offset(width * a.first, height * a.second)) }
        tick(); confirmNode.assertIsEnabled()
        confirmNode.performClick(); tick()
        assertEquals(listOf(11, 7), saved.state.positions["POST"])
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].performClick(); tick()
        rule.onAllNodesWithContentDescription("Mein Haus platzieren")[0].performClick(); tick()
        confirmNode.assertIsEnabled()
        // put onto the post
        rule.onAllNodesWithContentDescription("Bauplatzraster")[0].performTouchInput { click(Offset(width * a.first, height * a.second)) }
        tick()
        confirmNode.assertIsNotEnabled(); cancelNode.assertIsEnabled()
    }

    @Test fun tappingTheGroundPutsTheBuildingOnTheSnappedCellAndSavesExactlyThere() {
        val saved = Saved(); start(saved); placePost()
        val land = 0
        val target = IslandGrid.project(listOf(11, 7), land)
        rule.onAllNodesWithContentDescription("Bauplatzraster")[0].performTouchInput { click(Offset(width * (target.first + .004f), height * (target.second - .003f))) }
        tick()
        confirmNode.performClick(); tick()
        assertEquals(listOf(11, 7), saved.state.positions["POST"])
    }

    @Test fun dragCarriesTheBuildingByTheOffsetOfTheTouch() {
        val saved = Saved(); start(saved); placePost()
        val a = IslandGrid.project(listOf(11, 7), 0)
        rule.onAllNodesWithContentDescription("Bauplatzraster")[0].performTouchInput { click(Offset(width * a.first, height * a.second)) }
        tick()
        val b = IslandGrid.project(listOf(13, 8), 0)
        // grab slightly beside the building, release slightly beside the target cell: the same offset
        rule.onAllNodesWithContentDescription("Bauplatzraster")[0].performTouchInput {
            swipe(Offset(width * (a.first + .01f), height * (a.second + .01f)), Offset(width * (b.first + .01f), height * (b.second + .01f)), 400)
        }
        tick()
        confirmNode.performClick(); tick()
        assertEquals(listOf(13, 8), saved.state.positions["POST"])
    }

    @Test fun infoBarShowsTheActionsAndATapOnEmptyGroundClosesIt() {
        start(Saved(), grown = true)
        rule.onAllNodesWithContentDescription("Post").filter(hasClickAction())[0].performClick(); tick()
        for (label in listOf("Öffnen", "Info", "Verschieben", "Abreißen")) rule.onAllNodesWithText(label)[0].assertExists()
        // 7.9.6: the actions hang under the centred piece, so tap free ground at the upper left instead of the bottom middle
        rule.onRoot().performTouchInput { click(Offset(width * .08f, height * .3f)) }
        tick(800)
        rule.onAllNodesWithText("Verschieben").assertCountEquals(0)
    }

    @Test fun movingFromTheActionsShowsTheGridAndCancelHidesIt() {
        start(Saved(), grown = true)
        rule.onAllNodesWithContentDescription("Baufeld mit Kästchen").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Post").filter(hasClickAction())[0].performClick(); tick()
        rule.onAllNodesWithText("Verschieben")[0].performClick(); tick()
        rule.onAllNodesWithContentDescription("Baufeld mit Kästchen")[0].assertExists()
        rule.onAllNodesWithContentDescription("Bauplatzraster")[0].assertExists()
        confirmNode.assertExists(); cancelNode.assertExists()
        cancelNode.performClick(); tick()
        rule.onAllNodesWithContentDescription("Baufeld mit Kästchen").assertCountEquals(0)
    }

    /** 7.9.7 (D): the extra "Bearbeiten" button is gone; the right column is Menü + Bauen only. */
    @Test fun thereIsNoEditButtonAnyMore() {
        start(Saved(), grown = true)
        rule.onAllNodesWithText("Bearbeiten").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Bearbeiten").assertCountEquals(0)
        rule.onAllNodesWithText("Menü")[0].assertExists()
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].assertExists()
    }

    @Test fun buildButtonIsTheRoundShopButton() {
        start(Saved(), grown = true)
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].performClick(); tick()
        rule.onAllNodesWithContentDescription("Post platzieren").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Mein Haus auswählen")[0].assertExists()
    }

    @Test fun selectedBuildingStaysInSightAboveTheInfoBar() {
        start(Saved(), grown = true)
        // the harbour stands at the very bottom of the island: its actions (7.9.6: at the piece) must not hide it
        val tag = rule.onAllNodesWithContentDescription("Hafen").filter(hasClickAction())[0]
        tag.performClick(); tick(1200)
        val root = rule.onRoot().getBoundsInRoot()
        val bar = rule.onAllNodesWithContentDescription("Gebäudeaktionen")[0].getBoundsInRoot()
        val foot = rule.onAllNodesWithContentDescription("Hafen").filter(hasClickAction())[0].getBoundsInRoot()
        assertTrue("actions on screen: $bar in $root", bar.top >= root.top && bar.bottom <= root.bottom)
        assertTrue("foot $foot on screen", foot.top >= root.top && foot.bottom <= root.bottom)
        assertTrue("foot $foot is hidden by the actions $bar", foot.bottom <= bar.top || foot.top >= bar.bottom)
    }

    @Test fun aNewBuildingStartsInTheMiddleOfTheScreen() {
        start(Saved())
        placePost()
        val root = rule.onRoot().getBoundsInRoot()
        val sprite = rule.onAllNodesWithContentDescription("Post")[0].getBoundsInRoot()
        val dx = ((sprite.left + sprite.right) / 2).value - ((root.left + root.right) / 2).value
        assertTrue("preview sprite should stand mid-screen, was $dx dp off", kotlin.math.abs(dx) <= 30f)
    }
}