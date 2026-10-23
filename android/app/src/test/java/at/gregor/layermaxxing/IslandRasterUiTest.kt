package at.gregor.layermaxxing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
 * 7.9.11-raster on the real IslandWorld (Robolectric, JVM simulation, not a device): the grid draws exactly the
 * targets of the held piece under the server's declared rule ([GridTargetsKey] carries the drawn cells), the
 * preview and ✓ follow the same decision, unknown rules offer nothing, and refused writes are reported honestly
 * without automatic retries.
 * 7.9.12: the island is laid out so that no two pictures overlap (rule 5); every spot used below is taken from
 * [IslandLayout.targets] / the obstacles instead of fixed one-cell neighbours of the 7.9.11 compact rule.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class IslandRasterUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    /** The fake server: [refuse] answers the next write with an [ApiException] after applying [otherDevice] to the stored island. */
    private class Server(var state: ApiClient.IslandConstruction) {
        val requests = mutableListOf<ApiClient.IslandRequest>()
        val errors = mutableListOf<String>()
        var refuse: Int? = null
        var otherDevice: ((ApiClient.IslandConstruction) -> ApiClient.IslandConstruction)? = null
    }
    private val decor = listOf(ApiClient.IslandDecor(0, "flowers", listOf(15, 11), 0), ApiClient.IslandDecor(101, "bench", listOf(13, 13)),
        ApiClient.IslandDecor(201, "place_hut", listOf(10, 9), name = "Brennerhaus"))
    /**
     * Five functions at stage 1 and three decorations on the full land, no two pictures overlapping (checked); the
     * eastern meadow (no lighthouse yet) leaves the post several free spots.
     */
    private val spots = FitIsland.SPOTS - "LIGHTHOUSE"
    private fun grown(version: Int) = ApiClient.IslandConstruction(mode = "creative", available = 500, ageDays = 21,
        buildings = spots.keys.associateWith { 1 }, land = 5, decor = decor, positions = spots, gridVersion = version)
        .also { assertTrue(IslandLayout.overlaps(it.copy(gridVersion = PlacementRule.MASKS)).isEmpty()) }
    /** A free spot of the held post that is far from its current one (away from the ✓/✗ bubbles). */
    private fun postSpot(c: ApiClient.IslandConstruction) = awayFrom(IslandLayout.targets(c, IslandTarget.Building(IsleBuilding.POST)), c.positions.getValue("POST"))

    @Composable private fun Island(server: Server) {
        var shown by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(server.state) }
        val home = ApiClient.HomeIsland(1, emptyMap(), 500, emptyList(), construction = shown)
        VillageTheme(true) { IslandWorld(
            ownName = "Veit", ownEmoji = "🦊", friends = emptyList(), groups = emptyList(), islands = ApiClient.Islands(500, emptyList()),
            quests = emptyList(), letters = emptyList(), topics = emptyList(), opened = emptyMap(), onBack = {}, onChat = {}, onComposeLetter = {},
            onOpenLetter = {}, onLockedTap = {}, onTopics = {}, onAllTopics = {}, onGlossary = {}, onPeople = {}, onCreateQuest = { _, _, _, _, _, _ -> },
            onQuestDone = { _, _ -> }, onQuestDelete = {}, ownId = 1, startView = "home", creativeActive = true,
            onError = { server.errors += it },
            loadIsland = { shown = server.state; home.copy(construction = server.state) },
            changeConstruction = { c, req ->
                server.requests += req
                server.refuse?.let { code ->
                    server.refuse = null
                    server.otherDevice?.let { server.state = it(server.state) }
                    throw ApiException(code, if (code == 409) "Die Insel wurde inzwischen geändert. Bitte neu laden." else "Bauplatz ist bereits belegt")
                }
                check(c.revision == server.state.revision) { "409" }
                server.state = when (req.action) {
                    "move" -> c.copy(positions = c.positions + (req.building!! to req.position!!), revision = c.revision + 1)
                    else -> c
                }
                shown = server.state
                home.copy(construction = server.state)
            },
        ) }
    }

    private fun tick(ms: Long = 700) { rule.mainClock.advanceTimeBy(ms); rule.waitForIdle() }
    private fun start(server: Server) { rule.setContent { Island(server) }; tick(1200) }
    private fun lift(label: String) { rule.onAllNodesWithContentDescription(label).filter(hasClickAction())[0].performTouchInput { longClick() }; tick() }
    private fun groundTap(cell: List<Int>) = rule.onAllNodesWithContentDescription("Bauplatzraster")[0].performTouchInput {
        val (fx, fy) = IslandGrid.project(cell, 5); click(Offset(width * fx, height * fy))
    }
    private fun drawn(): Set<List<Int>> =
        rule.onAllNodesWithContentDescription("Baufeld mit Kästchen")[0].fetchSemanticsNode().config.getOrNull(GridTargetsKey).orEmpty().toSet()
    private val confirm get() = rule.onAllNodesWithContentDescription("Bestätigen")[0]
    private fun hint(text: String) = rule.onAllNodesWithContentDescription("Hier nicht möglich: $text")
    /** A target away from the ✓/✗ bubbles of the held piece (they hang below its foot). */
    private fun awayFrom(cells: Collection<List<Int>>, from: List<Int>) = cells.filter { it != from && (it[1] <= from[1] || kotlin.math.abs(it[0] - from[0]) >= 3) }
        .maxBy { kotlin.math.abs(it[0] - from[0]) + kotlin.math.abs(it[1] - from[1]) }

    @Test fun theHarbourGridIsExactlyTheFreeCoastCellsAndATargetConfirms() {
        val server = Server(grown(PlacementRule.MASKS)); start(server)
        lift("Hafen")
        val c = server.state
        val targets = IslandLayout.targets(c, IslandTarget.Building(IsleBuilding.HARBOUR)).toSet()
        assertEquals(targets, drawn())
        assertTrue(targets.isNotEmpty() && targets.all { IslandGrid.coast(it, 5) })
        // no inland cell, no water, no taken coast cell is drawn
        assertTrue(IslandGrid.buildableCells(5).filter { !IslandGrid.coast(it, 5) }.none { it in drawn() })
        val taken = IslandLayout.obstacles(c, IslandTarget.Building(IsleBuilding.HARBOUR)).flatMap { it.cells }.toSet()
        assertTrue(drawn().none { it in taken })
        // 7.9.12: only coast spots where the whole pier fits, with water in front
        assertTrue(targets.all { !IslandGrid.buildable(it[0], it[1] + 1, 5) })
        val spot = targets.filter { it != c.positions["HARBOUR"] }.maxBy { kotlin.math.abs(it[0] - 10) }
        groundTap(spot); tick()
        confirm.assertIsEnabled(); confirm.performClick(); tick(900)
        assertEquals(spot, server.state.positions["HARBOUR"])
    }

    @Test fun everyOtherPieceSeesItsFreeCellsAndAnOccupiedCellIsNeverDrawnOrConfirmed() {
        val server = Server(grown(PlacementRule.MASKS)); start(server)
        lift("Post")
        val c = server.state
        val targets = IslandLayout.targets(c, IslandTarget.Building(IsleBuilding.POST)).toSet()
        assertEquals(targets, drawn())
        // the house's and the hut's spots are taken, and so is every cell whose picture would reach into their areas
        assertFalse(listOf(8, 6) in drawn()); assertFalse(listOf(10, 9) in drawn())
        val house = IslandLayout.cellsAt(c, IslandTarget.Building(IsleBuilding.HOUSE), listOf(8, 6))
        assertTrue(drawn().none { p -> IslandLayout.cellsAt(c, IslandTarget.Building(IsleBuilding.POST), p).any { it in house } })
        // (the house's spot, beside the held post: away from the ✓/✗ bubbles below its foot)
        groundTap(listOf(8, 6)); tick()
        confirm.assertIsNotEnabled()
        hint("Belegt – rot markierte Fläche")[0].assertExists()
        groundTap(postSpot(c)); tick()
        confirm.assertIsEnabled()
    }

    @Test fun anOldVersion2ServerGetsItsOwnClearancesNotTheNewFreeCells() {
        val server = Server(grown(PlacementRule.CIRCLES)); start(server)
        lift("Post")
        val c = server.state
        val v2 = IslandLayout.targets(c, IslandTarget.Building(IsleBuilding.POST)).toSet()
        val v5 = IslandLayout.targets(c.copy(gridVersion = PlacementRule.MASKS), IslandTarget.Building(IsleBuilding.POST)).toSet()
        assertEquals(v2, drawn())
        // what the app draws for the old server: its own rule AND the graphic fit, never more than it accepts
        assertTrue(IslandLayout.ruleTargets(c, IslandTarget.Building(IsleBuilding.POST)).containsAll(v2))
        // cells the newest rule would offer but the running v2 server refuses: never drawn, never green
        val onlyNew = v5 - v2
        assertTrue(onlyNew.isNotEmpty())
        val spot = awayFrom(onlyNew, c.positions["POST"] ?: IslandLayout.defaultPosition(IsleBuilding.POST))
        groundTap(spot); tick()
        confirm.assertIsNotEnabled()
        assertTrue(server.requests.isEmpty())
    }

    @Test fun anUnknownRuleOffersNothingAndAsksForAnUpdate() {
        val server = Server(grown(8)); start(server)
        lift("Post")
        assertTrue(drawn().isEmpty())
        confirm.assertIsNotEnabled()
        hint("Der Server nutzt neuere Bauregeln (Version 8) – bitte die App aktualisieren")[0].assertExists()
        groundTap(listOf(9, 10)); tick()
        confirm.assertIsNotEnabled()
        assertTrue(server.requests.isEmpty())
    }

    @Test fun aConflictFromAnotherDeviceReloadsSaysSoAndIsNotRetried() {
        val server = Server(grown(PlacementRule.MASKS)); start(server)
        lift("Post")
        val spot = postSpot(server.state)
        groundTap(spot); tick()
        confirm.assertIsEnabled()
        // meanwhile another device puts a lantern exactly there
        server.refuse = 409
        server.otherDevice = { s -> s.copy(revision = s.revision + 1, decor = s.decor + ApiClient.IslandDecor(300, "lantern", spot)) }
        confirm.performClick(); tick(1500)
        assertEquals(1, server.requests.size)
        assertEquals(listOf(ISLAND_CONFLICT), server.errors)
        // reloaded: the spot is taken now, red, no longer drawn; the draft stays for a new choice
        assertFalse(spot in drawn())
        confirm.assertIsNotEnabled()
        hint("Belegt – rot markierte Fläche")[0].assertExists()
        tick(3000)
        assertEquals(1, server.requests.size)
    }

    /** The draft is over: no ✓/✗, no drawn targets. */
    private fun noDraft() {
        assertEquals(0, rule.onAllNodesWithContentDescription("Bestätigen").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithContentDescription("Baufeld mit Kästchen").fetchSemanticsNodes().size)
    }

    @Test fun aDecorationRemovedOnAnotherDeviceEndsTheDraftAfterTheConflictAndIsNeverPlacedAgain() {
        val server = Server(grown(PlacementRule.MASKS)); start(server)
        lift("Bank")
        val spot = awayFrom(drawn(), listOf(14, 13))
        groundTap(spot); tick()
        confirm.assertIsEnabled()
        server.refuse = 409
        server.otherDevice = { s -> s.copy(revision = s.revision + 1, decor = s.decor.filter { it.id != 101 }) }
        confirm.performClick(); tick(1500)
        // one write (a move of 101), then reloaded: the draft ends with a clear message, nothing is placed anew
        assertEquals(listOf("decor_move"), server.requests.map { it.action })
        assertEquals(listOf(IslandLayout.GONE), server.errors)
        noDraft()
        tick(3000)
        assertEquals(1, server.requests.size)
        assertTrue(server.state.decor.none { it.id == 101 || it.item == "bench" })
    }

    @Test fun aBuildingRemovedOnAnotherDeviceIsNeverRebuiltByTheMoveDraft() {
        val server = Server(grown(PlacementRule.MASKS)); start(server)
        lift("Post")
        groundTap(postSpot(server.state)); tick()
        confirm.assertIsEnabled()
        server.refuse = 409
        server.otherDevice = { s -> s.copy(revision = s.revision + 1, buildings = s.buildings - "POST") }
        confirm.performClick(); tick(1500)
        // the only write was the move; after the reload no "build" follows on its own or from the old draft
        assertEquals(listOf("move"), server.requests.map { it.action })
        assertEquals(listOf(IslandLayout.GONE), server.errors)
        noDraft()
        tick(3000)
        assertEquals(1, server.requests.size)
        assertFalse("POST" in server.state.buildings)
    }

    @Test fun theMinutePollEndsADraftWhosePieceWasRemovedWithoutAnyWrite() {
        val server = Server(grown(PlacementRule.MASKS)); start(server)
        lift("Bank")
        assertTrue(drawn().isNotEmpty())
        // removed on another device while the bench is held; the app only sees it with the next poll
        server.state = server.state.copy(revision = server.state.revision + 1, decor = server.state.decor.filter { it.id != 101 })
        tick(61_000)
        assertEquals(listOf(IslandLayout.GONE), server.errors)
        noDraft()
        assertTrue(server.requests.isEmpty())
    }

    @Test fun theMinutePollEndsALiftedBuildingDraftAndNothingIsBuilt() {
        val server = Server(grown(PlacementRule.MASKS)); start(server)
        lift("Post")
        groundTap(postSpot(server.state)); tick()
        server.state = server.state.copy(revision = server.state.revision + 1, buildings = server.state.buildings - "POST")
        tick(61_000)
        assertEquals(listOf(IslandLayout.GONE), server.errors)
        noDraft()
        assertTrue(server.requests.isEmpty())
    }

    @Test fun aSpotTheServerRefusesIsNotOfferedAgainForThisDraft() {
        val server = Server(grown(PlacementRule.MASKS)); start(server)
        lift("Post")
        val spot = postSpot(server.state)
        groundTap(spot); tick()
        server.refuse = 422
        confirm.performClick(); tick(1500)
        assertEquals(1, server.requests.size)
        assertEquals(1, server.errors.size)
        assertFalse(spot in drawn())
        confirm.assertIsNotEnabled()
        hint(IslandLayout.REJECTED)[0].assertExists()
        // another free cell is still offered and can be confirmed
        val other = awayFrom(drawn() - setOf(spot), spot)
        groundTap(other); tick()
        confirm.assertIsEnabled(); confirm.performClick(); tick(900)
        assertEquals(other, server.state.positions["POST"])
    }
}
