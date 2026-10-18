package at.gregor.layermaxxing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 7.9.10 on the real IslandWorld (Robolectric, JVM simulation, not a device): while a piece is held every
 * other piece is outlined red, never at rest; the harbour only confirms on the coast; the building menus
 * keep their actions, stage and price in the new painted style.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class IslandPlacementUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private class Saved { var state = ApiClient.IslandConstruction() }
    private val decor = listOf(ApiClient.IslandDecor(0, "flowers", listOf(8, 8), 0), ApiClient.IslandDecor(101, "bench", listOf(14, 13)),
        ApiClient.IslandDecor(201, "place_hut", listOf(10, 10), name = "Brennerhaus"))

    @Composable private fun Island(saved: Saved, start: ApiClient.IslandConstruction) {
        var state by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(start) }
        saved.state = state
        val home = ApiClient.HomeIsland(1, emptyMap(), 500, emptyList(), construction = state)
        VillageTheme(true) { IslandWorld(
            ownName = "Veit", ownEmoji = "🦊", friends = emptyList(), groups = emptyList(), islands = ApiClient.Islands(500, emptyList()),
            quests = emptyList(), letters = emptyList(), topics = emptyList(), opened = emptyMap(), onBack = {}, onChat = {}, onComposeLetter = {},
            onOpenLetter = {}, onLockedTap = {}, onTopics = {}, onAllTopics = {}, onGlossary = {}, onPeople = {}, onCreateQuest = { _, _, _, _, _, _ -> },
            onQuestDone = { _, _ -> }, onQuestDelete = {}, ownId = 1, startView = "home", creativeActive = state.mode == "creative",
            loadIsland = { home.copy(construction = state) },
            changeConstruction = { c, req ->
                // Like the server: an impossible spot is refused (the app never sends one).
                if (req.action == "move") check(IslandLayout.valid(c, IsleBuilding.valueOf(req.building!!), req.position!!)) { "422" }
                state = when (req.action) {
                    "move" -> c.copy(positions = c.positions + (req.building!! to req.position!!), revision = c.revision + 1)
                    "upgrade" -> c.copy(buildings = c.buildings + (req.building!! to ((c.buildings[req.building] ?: 0) + 1)), revision = c.revision + 1)
                    else -> c
                }
                home.copy(construction = state)
            },
        ) }
    }

    private fun tick(ms: Long = 700) { rule.mainClock.advanceTimeBy(ms); rule.waitForIdle() }
    private fun start(c: ApiClient.IslandConstruction): Saved {
        val saved = Saved()
        rule.setContent { Island(saved, c) }
        tick(1200)
        return saved
    }
    /** [version] = the server's grid_version: the 7.9.10 footprint tests pin 3, the default is the current rule. */
    private fun grown(level: Int = 2, available: Int = 500, mode: String = "creative", version: Int = PlacementRule.CURRENT) = ApiClient.IslandConstruction(mode = mode, available = available, ageDays = 21,
        buildings = IsleBuilding.entries.associate { it.name to level }, land = 5, decor = FitIsland.DECOR, positions = FitIsland.SPOTS, gridVersion = version)
    private fun underlayState(): String? = rule.onAllNodesWithContentDescription("Baufeld mit Kästchen")[0].fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
    private fun lift(label: String) { rule.onAllNodesWithContentDescription(label).filter(hasClickAction())[0].performTouchInput { longClick() }; tick() }
    private fun groundTap(cell: List<Int>) = rule.onAllNodesWithContentDescription("Bauplatzraster")[0].performTouchInput {
        val (fx, fy) = IslandGrid.project(cell, 5); click(Offset(width * fx, height * fy))
    }
    private val confirm get() = rule.onAllNodesWithContentDescription("Bestätigen")[0]

    @Test fun atRestNoRedMarkingsWhileMovingEveryOtherPieceIsMarked() {
        val c = grown()
        val saved = start(c)
        rule.onAllNodesWithContentDescription("Baufeld mit Kästchen").assertCountEquals(0)
        lift("Post")
        // 5 other buildings + 3 decoration pieces; the held post is never its own obstacle.
        assertEquals("8 belegte Flächen rot markiert", underlayState())
        assertEquals(8, IslandLayout.obstacles(c, IslandTarget.Building(IsleBuilding.POST)).size)
        // The nearest free spot (its picture overlaps only its own old area): possible.
        val own = c.positions.getValue("POST")
        val step = IslandLayout.targets(c, IslandTarget.Building(IsleBuilding.POST)).filter { it != own && it[1] <= own[1] }
            .minBy { kotlin.math.abs(it[0] - own[0]) + kotlin.math.abs(it[1] - own[1]) }
        assertTrue(IslandLayout.valid(c, IsleBuilding.POST, step))
        assertTrue(IslandLayout.cellsAt(c, IslandTarget.Building(IsleBuilding.POST), step).any { it in IslandLayout.cellsAt(c, IslandTarget.Building(IsleBuilding.POST), own) })
        groundTap(step); tick()
        confirm.assertIsEnabled(); confirm.performClick(); tick(900)
        assertEquals(step, saved.state.positions["POST"])
        // Back at rest: the grid and its red markings are gone.
        rule.onAllNodesWithContentDescription("Baufeld mit Kästchen").assertCountEquals(0)
    }

    @Test fun aForeignFootprintTurnsThePreviewRedAndSaysWhy() {
        val c = grown(version = PlacementRule.DIAMONDS)
        start(c)
        lift("Post")
        // version 3: the house footprint (8,6 with one ring on land 5)
        groundTap(listOf(8, 7)); tick()
        confirm.assertIsNotEnabled()
        rule.onAllNodesWithContentDescription("Hier nicht möglich: Belegt – rot markierte Fläche")[0].assertExists()
    }

    @Test fun theHarbourSnapsToTheCoastATakenCoastCellIsRedAndAFreeOneConfirms() {
        // 7.9.12 (rule 5): the held harbour snaps to the coast spots where its whole pier fits (water in front)
        val c = grown()
        val saved = start(c)
        lift("Hafen")
        assertEquals("8 belegte Flächen rot markiert · Küstenfelder markiert", underlayState())
        val spots = IslandLayout.harbourSpots(c)
        val own = c.positions.getValue("HARBOUR")
        fun snap(cell: List<Int>) = IslandGrid.project(cell, 5).let { (x, y) -> IslandGrid.nearestOf(spots, x, y, 5) }
        val inland = IslandGrid.buildableCells(5).filter { !IslandGrid.coast(it, 5) && it[1] < 13 }
        // a tap inland whose nearest pier spot is taken by another picture: red there, ✓ disabled, no jump elsewhere
        val blocked = inland.first { val s = snap(it); s != own && !IslandLayout.valid(c, IsleBuilding.HARBOUR, s) }
        groundTap(blocked); tick()
        confirm.assertIsNotEnabled()
        rule.onAllNodesWithContentDescription("Hier nicht möglich: Belegt – rot markierte Fläche")[0].assertExists()
        rule.onAllNodesWithContentDescription("Hier nicht möglich: Hafen nur an der Küste").assertCountEquals(0)
        // another inland tap snaps to a free pier spot and confirms
        val free = inland.first { val s = snap(it); s != own && IslandLayout.valid(c, IsleBuilding.HARBOUR, s) }
        val shore = snap(free)
        groundTap(free); tick()
        confirm.assertIsEnabled(); confirm.performClick(); tick(900)
        assertEquals(shore, saved.state.positions["HARBOUR"])
        assertTrue(IslandGrid.coast(shore, 5) && !IslandGrid.buildable(shore[0], shore[1] + 1, 5))
        // the model (and the server) still refuse a manipulated inland spot
        assertEquals("Hafen nur an der Küste", IslandLayout.reason(ApiClient.HomeIsland(1, emptyMap(), 0, emptyList(), construction = c), IslandDraft(IslandTarget.Building(IsleBuilding.HARBOUR), listOf(8, 11))))
    }

    @Test fun anOldInlandHarbourIsLiftedOntoTheCoastWithoutSavingAnything() {
        val inland = listOf(10, 12)
        val c = grown().copy(positions = FitIsland.SPOTS + ("HARBOUR" to inland))
        check(!IslandGrid.coast(inland, 5))
        val saved = start(c)
        lift("Hafen")
        val shore = IslandLayout.harbourCellNear(c, inland)
        check(IslandLayout.valid(c, IsleBuilding.HARBOUR, shore)) { "$shore" }
        // only the local draft moved: ✓ is possible, nothing was written yet
        confirm.assertIsEnabled()
        rule.onAllNodesWithContentDescription("Hier nicht möglich: Hafen nur an der Küste").assertCountEquals(0)
        assertEquals(inland, saved.state.positions["HARBOUR"])
        confirm.performClick(); tick(900)
        assertEquals(shore, saved.state.positions["HARBOUR"])
    }

    /** 7.9.9/7.9.10 cut the one-line top hint off at the right edge with a large system font on a narrow phone. */
    @Test fun theTopHintWrapsInsteadOfBeingCutOffWithALargeFontOnANarrowPhone() {
        val text = "Ziehen oder tippen · Grün = frei, Rot = belegt"
        rule.setContent {
            val d = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, fontScale = 1.6f)) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.width(320.dp)) { PlacementHint(text, warning = true) }
            }
        }
        rule.waitForIdle()
        val layout = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        rule.onAllNodesWithText(text, useUnmergedTree = true)[0].fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!.invoke(layout)
        assertFalse("hint text cut off", layout.single().hasVisualOverflow)
        assertTrue(layout.single().lineCount > 1)
        val bounds = rule.onAllNodesWithContentDescription("Hier nicht möglich: $text")[0].getBoundsInRoot()
        assertTrue("hint ${bounds.right - bounds.left} wider than its 320 dp box", bounds.right - bounds.left <= 320.dp)
    }

    @Test fun tappedBuildingShowsStagePriceAndTheThreeActions() {
        start(grown(level = 2, mode = "normal", available = 140))
        rule.onAllNodesWithContentDescription("Post").filter(hasClickAction())[0].performClick(); tick()
        rule.onAllNodesWithContentDescription("Gebäudeaktionen")[0].assertExists()
        rule.onAllNodesWithText("Postfächer")[0].assertExists()
        rule.onAllNodesWithContentDescription("Stufe 2 von 3")[0].assertExists()
        rule.onAllNodesWithContentDescription("Preis: 100 P")[0].assertExists()
        for (label in listOf("Öffnen", "Ausbauen", "Abreißen")) rule.onAllNodesWithText(label)[0].assertExists()
        rule.onAllNodesWithText("Verschieben").assertCountEquals(0)
    }

    @Test fun upgradeSheetShowsNowAndNextCostsAndKeepsItsButtons() {
        start(grown(level = 2, mode = "normal", available = 140))
        rule.onAllNodesWithContentDescription("Post").filter(hasClickAction())[0].performClick(); tick()
        rule.onAllNodesWithText("Ausbauen")[0].performClick(); tick(900)
        rule.onAllNodesWithText("Postfächer ausbauen")[0].assertExists()
        rule.onAllNodesWithContentDescription("Ausbauvorschau: Postfächer")[0].assertExists()
        rule.onAllNodesWithContentDescription("Ausbauvorschau: Postamt")[0].assertExists()
        rule.onAllNodesWithText("Auf Stufe 3 ausbauen")[0].assertIsEnabled()
        rule.onAllNodesWithText("Schließen")[0].assertExists()
    }

    @Test fun upgradeSheetExplainsMissingPointsAndDisablesTheButton() {
        start(grown(level = 2, mode = "normal", available = 30))
        rule.onAllNodesWithContentDescription("Post").filter(hasClickAction())[0].performClick(); tick()
        rule.onAllNodesWithText("Ausbauen")[0].performClick(); tick(900)
        rule.onAllNodesWithText("Es fehlen 70 Punkte · Punkte kommen aus bestätigten Quests")[0].assertExists()
        rule.onAllNodesWithText("Auf Stufe 3 ausbauen")[0].assertIsNotEnabled()
    }

    @Test fun maxStageSaysFullyBuilt() {
        start(grown(level = 3, mode = "normal"))
        rule.onAllNodesWithContentDescription("Post").filter(hasClickAction())[0].performClick(); tick()
        rule.onAllNodesWithText("Info")[0].performClick(); tick(900)
        rule.onAllNodesWithText("Vollständig ausgebaut")[0].assertExists()
        assertNull(rule.onAllNodesWithText("Auf Stufe 4 ausbauen").fetchSemanticsNodes().firstOrNull())
    }
}
