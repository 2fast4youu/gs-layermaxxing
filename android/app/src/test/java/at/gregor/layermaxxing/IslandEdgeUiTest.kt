package at.gregor.layermaxxing

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Production IslandWorld + actual touch/confirm/cancel/upgrade handlers. Synthetic API state and an
 * independently checked injected writer, no phone, backend, network, account, or real persistence. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class IslandEdgeUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val lighthouse = IslandTarget.Building(IsleBuilding.LIGHTHOUSE)
    private class Server(var home: ApiClient.HomeIsland) {
        val requests = mutableListOf<ApiClient.IslandRequest>()
        val errors = mutableListOf<String>()
        // Independent server7 placement assertion: no IslandLayout/IslandEdge production decision here.
        fun apply(c: ApiClient.IslandConstruction, req: ApiClient.IslandRequest): ApiClient.HomeIsland {
            check(c.revision == home.construction.revision)
            check(req.building == "LIGHTHOUSE")
            val old = c.positions["LIGHTHOUSE"] ?: IslandLayout.defaultPosition(IsleBuilding.LIGHTHOUSE)
            val p = req.position ?: old
            val currentStage = c.buildings["LIGHTHOUSE"] ?: 0
            val stage = if (req.action == "build") 0 else if (req.action == "upgrade") currentStage + 1 else currentStage
            val key = "building:LIGHTHOUSE:$stage"
            check(Grid7Contract.fits(key, c.land, p))
            val sameUpgrade = req.action == "upgrade" && c.standing(IsleBuilding.LIGHTHOUSE) && p == old
            if (!sameUpgrade) check(p in Grid7Contract.spots(stage, c.land))
            val mine = Grid7Contract.rect(key, c.land, p)
            check(c.decor.none { d -> Grid7Contract.rect(IslandMasks.decorKey(d.item), c.land, d.position).any { it in mine } })
            requests += req
            home = home.copy(construction = c.copy(
                buildings = if (req.action == "upgrade") c.buildings + ("LIGHTHOUSE" to stage) else c.buildings,
                starters = if (req.action == "build") c.starters + "LIGHTHOUSE" else if (req.action == "upgrade") c.starters - "LIGHTHOUSE" else c.starters,
                positions = c.positions + ("LIGHTHOUSE" to p), revision = c.revision + 1))
            return home
        }
    }
    @Composable private fun World(server: Server) {
        VillageTheme(true) { IslandWorld(
            ownName = "Grid7 synthetic", ownEmoji = "🌴", friends = emptyList(), groups = emptyList(),
            islands = ApiClient.Islands(500, emptyList()), quests = emptyList(), letters = emptyList(), topics = emptyList(), opened = emptyMap(),
            onBack = {}, onChat = {}, onComposeLetter = {}, onOpenLetter = {}, onLockedTap = {}, onTopics = {}, onAllTopics = {}, onGlossary = {}, onPeople = {},
            onCreateQuest = { _, _, _, _, _, _ -> }, onQuestDone = { _, _ -> }, onQuestDelete = {}, ownId = 1, startView = "home", creativeActive = true,
            onError = { server.errors += it }, loadIsland = { server.home },
            changeConstruction = { c, req -> server.apply(c, req) },
        ) }
    }
    private fun tick(ms: Long = 800) { rule.mainClock.advanceTimeBy(ms); rule.waitForIdle() }
    private fun start(h: ApiClient.HomeIsland): Server {
        val server = Server(h)
        rule.setContent { World(server) }; tick(1200)
        return server
    }
    private fun lift() { rule.onAllNodesWithContentDescription("Freunde").filter(hasClickAction())[0].performTouchInput { longClick() }; tick() }
    private val confirm get() = rule.onAllNodesWithContentDescription("Bestätigen")[0]
    private val cancel get() = rule.onAllNodesWithContentDescription("Abbrechen")[0]
    private fun tap(p: List<Int>) {
        rule.onAllNodesWithContentDescription("Bauplatzraster")[0].performTouchInput {
            val (x, y) = IslandGrid.project(p, 5); click(Offset(width*x, height*y))
        }; tick()
    }
    private fun drawn() = rule.onAllNodesWithContentDescription("Baufeld mit Kästchen")[0].fetchSemanticsNode().config.getOrNull(GridTargetsKey).orEmpty().toSet()
    private fun noUpdateWarning() {
        rule.onAllNodesWithContentDescription("Hier nicht möglich: Der Server nutzt neuere Bauregeln (Version 7) – bitte die App aktualisieren").assertCountEquals(0)
    }
    private fun expected(c: ApiClient.IslandConstruction, stage: Int): Set<List<Int>> = Grid7Contract.spots(stage, 5).filter { p ->
        val mine = Grid7Contract.rect("building:LIGHTHOUSE:$stage", 5, p)
        c.decor.none { d -> Grid7Contract.rect(IslandMasks.decorKey(d.item), 5, d.position).any { it in mine } }
    }.toSet()
    private fun shot(name: String) {
        val out = System.getenv("GRID7_SHOTS")?.let(::File) ?: return
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap)); out.mkdirs()
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun moveScenario(evidence: Boolean = false) {
        val original = Grid7Contract.home()
        val occupied = Grid7Contract.spots(1, 5).first { it[1] <= 5 }
        val home = original.copy(construction = original.construction.copy(decor = listOf(ApiClient.IslandDecor(100, "bench", occupied))))
        val server = start(home)
        if (evidence) shot("01-grid7-saved-inland-before")
        lift(); noUpdateWarning()
        assertEquals(expected(home.construction, 1), drawn()); assertTrue(drawn().isNotEmpty())
        assertFalse(listOf(10,10) in drawn()); assertFalse(occupied in drawn())
        confirm.assertIsNotEnabled() // Saved inland stays exactly there when lifted; even move-to-self needs coast.
        assertTrue(server.requests.isEmpty()); assertEquals(home, server.home)
        if (evidence) shot("02-grid7-inland-move-refused")
        tap(occupied); confirm.assertIsNotEnabled()
        assertTrue(server.requests.isEmpty()); assertEquals(home, server.home)
        if (evidence) shot("03-grid7-occupied-coast-refused")
        cancel.performClick(); tick()
        assertTrue(server.requests.isEmpty()); assertEquals(home, server.home)
        lift()
        // Inland tap snaps to a coastal ground fit, not to an inland rock; choose away from the held bubbles.
        val inland = listOf(10,6)
        val (x,y) = IslandGrid.project(inland, 5)
        val snap = IslandGrid.nearestOf(Grid7Contract.spots(1, 5).toList(), x, y, 5)
        assertTrue(snap in expected(home.construction, 1))
        tap(inland); confirm.assertIsEnabled()
        assertTrue(server.requests.isEmpty()); assertEquals(home, server.home)
        if (evidence) shot("04-grid7-free-coast-before-confirm")
        confirm.performClick(); tick()
        assertEquals(1, server.requests.size)
        assertEquals("move", server.requests.single().action); assertEquals(snap, server.requests.single().position)
        assertEquals(snap, server.home.construction.positions["LIGHTHOUSE"])
        assertTrue(server.errors.isEmpty())
        rule.onAllNodesWithContentDescription("Baufeld mit Kästchen").assertCountEquals(0)
        if (evidence) {
            shot("05-grid7-confirmed-injected-state")
            File(System.getenv("GRID7_SHOTS")!!, "GRID7-EVIDENCE.txt").writeText(
                "Synthetic grid_version=7 parsed by production API parser; production IslandWorld on Robolectric.\n" +
                "Before: ${home.construction.positions}; after: ${server.home.construction.positions}\n" +
                "Targets: ${expected(home.construction, 1)}\nRequests: ${server.requests}\n" +
                "Inland lift, occupied coast, cancel and pre-confirm: zero injected writes. Confirm: exactly one move.\n" +
                "No phone/backend E2E, no network or real persistence. Frames generated only when this entrypoint runs.\n")
        }
    }
    @Test fun rule7RealWorldMoveTargetsSnappingOccupiedCoastCancelAndConfirm() = moveScenario()

    @Test fun rule7RealWorldBuildOffersNonemptyCoastTargetsAndWritesOnlyAfterConfirm() {
        val h = Grid7Contract.home().let { it.copy(construction = it.construction.copy(buildings = emptyMap(), positions = emptyMap())) }
        val server = start(h)
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].performClick(); tick()
        rule.onAllNodesWithContentDescription("Freunde platzieren")[0].performClick(); tick()
        noUpdateWarning(); assertEquals(Grid7Contract.spots(0, 5), drawn()); confirm.assertIsEnabled()
        assertEquals(h, server.home); assertTrue(server.requests.isEmpty())
        confirm.performClick(); tick()
        assertEquals("build", server.requests.single().action)
        assertTrue(server.requests.single().position!! in Grid7Contract.spots(0, 5))
        assertTrue(server.errors.isEmpty())
    }

    @Test fun rule7RealWorldSamePositionInlandUpgradeRemainsAllowedWithoutMoving() {
        val h = Grid7Contract.home(); val server = start(h)
        rule.onAllNodesWithContentDescription("Freunde").filter(hasClickAction())[0].performClick(); tick()
        rule.onAllNodesWithText("Ausbauen")[0].performClick(); tick()
        rule.onAllNodesWithText("Auf Stufe 2 ausbauen")[0].assertIsEnabled()
        assertTrue(server.requests.isEmpty()); assertEquals(h, server.home)
        rule.onAllNodesWithText("Auf Stufe 2 ausbauen")[0].performClick(); tick()
        assertEquals("upgrade", server.requests.single().action)
        assertEquals(listOf(10,10), server.home.construction.positions["LIGHTHOUSE"])
        assertEquals(2, server.home.construction.buildings["LIGHTHOUSE"])
        assertTrue(server.errors.isEmpty())
    }

    @Test fun rule7RealWorldUpgradeRelocationUsesNextStageCoastTargetsAndAtomicUpgrade() {
        val h = Grid7Contract.home()
        // Stage 1 on full land occupies one cell; stage 2 grows behind it. A bench just behind blocks only the upgrade.
        val here = h.construction.positions.getValue("LIGHTHOUSE")
        val nextGround = Grid7Contract.rect("building:LIGHTHOUSE:2", 5, here)
        val oldGround = Grid7Contract.rect("building:LIGHTHOUSE:1", 5, here)
        val blocker = (nextGround - oldGround).first { p -> Grid7Contract.rect(IslandMasks.decorKey("bench"), 5, p).none { it in oldGround } }
        val c = h.construction.copy(decor = listOf(ApiClient.IslandDecor(100, "bench", blocker)))
        val server = start(h.copy(construction = c))
        rule.onAllNodesWithContentDescription("Freunde").filter(hasClickAction())[0].performClick(); tick()
        rule.onAllNodesWithText("Ausbauen")[0].performClick(); tick()
        rule.onAllNodesWithText("Auf Stufe 2 ausbauen")[0].assertIsNotEnabled()
        rule.onAllNodesWithText("Platz für die nächste Stufe wählen")[0].performClick(); tick()
        assertEquals(expected(c, 2), drawn()); assertFalse(here in drawn()); noUpdateWarning()
        assertEquals(1, server.home.construction.buildings["LIGHTHOUSE"]); assertTrue(server.requests.isEmpty())
        confirm.assertIsEnabled(); confirm.performClick(); tick()
        assertEquals("upgrade", server.requests.single().action)
        assertTrue(server.requests.single().position!! in Grid7Contract.spots(2, 5))
        assertEquals(2, server.home.construction.buildings["LIGHTHOUSE"])
        assertTrue(server.errors.isEmpty())
    }

    /** Fresh opt-in evidence entrypoint; absent GRID7_SHOTS is skipped, and produces no fabricated frames. */
    @Test fun renderSyntheticGrid7ProductionUiEvidence() {
        assumeTrue("Set GRID7_SHOTS to a fresh output directory", !System.getenv("GRID7_SHOTS").isNullOrBlank())
        moveScenario(evidence = true)
    }
}
