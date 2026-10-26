package at.gregor.layermaxxing

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Production IslandWorld and physical Compose pointer dispatch, native Robolectric rendering.
 * Synthetic declared-8 ledger; independent historic-stage0 fixture oracle and injected writer.
 * No semantic click, production upgrade helper, backend, network, account or device claim.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class IslandConstantGroundUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun ground(b: IsleBuilding, p: List<Int>) =
        Grid7Contract.rect("building:${b.name}:0", 5, p) + Grid7Contract.rect("building:${b.name}:0", 5, p, "sea")

    private fun fixture(b: IsleBuilding, creative: Boolean = true, version: Int = 8): ApiClient.HomeIsland {
        // Explicit rule 8, never CURRENT/default or the old foundation/cleanup fixture.
        val p = if (b == IsleBuilding.POST) listOf(8, 10) else
            Grid7Contract.spots(0, 5).sortedWith(compareBy({ it[1] }, { kotlin.math.abs(it[0] - 10) }, { it[0] })).first()
        val mine = ground(b, p)
        val border = (mine.flatMap { Grid7Contract.neighbours(it) }.toSet() - mine).sortedWith(compareBy({ it[1] }, { it[0] }))
        val c = ApiClient.IslandConstruction(
            gridVersion = version, mode = if (creative) "creative" else "normal", land = 5,
            spent = 73, available = 900, ageDays = 30, revision = 37,
            buildCosts = listOf(31, 67, 109), buildings = mapOf("HOUSE" to 2), starters = setOf(b.name),
            positions = mapOf(b.name to p, "HOUSE" to listOf(14, 12)),
            decor = border.mapIndexed { i, cell -> ApiClient.IslandDecor(700 + i, "lantern", cell, name = "Neighbour $i") })
        assertTrue(Grid7Contract.fits("building:${b.name}:0", 5, p))
        assertTrue(c.decor.all { d -> Grid7Contract.rect("decor:lantern", 5, d.position).none { it in mine } })
        assertTrue(ground(IsleBuilding.HOUSE, c.positions.getValue("HOUSE")).none { it in mine })
        return ApiClient.HomeIsland(1, emptyMap(), 1000, emptyList(), construction = c)
    }

    private class Writer(var home: ApiClient.HomeIsland, val chosen: IsleBuilding) {
        // Record every attempted call, even one rejected below: a swallowed assertion cannot hide a write.
        val calls = mutableListOf<ApiClient.IslandRequest>()
        val errors = mutableListOf<String>()
        fun apply(c: ApiClient.IslandConstruction, req: ApiClient.IslandRequest): ApiClient.HomeIsland {
            calls += req
            check(c == home.construction)
            check(c.gridVersion == 8)
            check(req.building == chosen.name && req.item == null && req.decorId == null)
            val old = c.positions.getValue(chosen.name)
            val p = req.position ?: old
            val level = c.buildings[chosen.name] ?: 0
            val next = when (req.action) {
                "upgrade" -> {
                    check(p == old && level in 0..2)
                    check(chosen.name in c.starters || chosen.name in c.buildings)
                    val cost = if (c.mode == "creative") 0 else listOf(31, 67, 109)[level]
                    check(c.available >= cost)
                    c.copy(buildings = c.buildings + (chosen.name to level + 1), starters = c.starters - chosen.name,
                        spent = c.spent + cost, available = c.available - cost, revision = c.revision + 1)
                }
                "move" -> {
                    // Historic fixture, NOT IslandLayout/IslandGround/IslandEdge decisions.
                    val key = "building:${chosen.name}:0"
                    check(Grid7Contract.fits(key, 5, p))
                    val mine = Grid7Contract.rect(key, 5, p)
                    if (chosen == IsleBuilding.LIGHTHOUSE) check(mine.any { it in Grid7Contract.edge(5) })
                    check(c.decor.none { d -> Grid7Contract.rect("decor:${d.item}", 5, d.position).any { it in mine } })
                    check(c.positions.filterKeys { it != chosen.name }.none { (name, spot) ->
                        Grid7Contract.rect("building:$name:0", 5, spot).any { it in mine }
                    })
                    c.copy(positions = c.positions + (chosen.name to p), revision = c.revision + 1)
                }
                else -> error("Unexpected request $req")
            }
            home = home.copy(construction = next)
            return home
        }
    }

    @Composable private fun World(writer: Writer) {
        VillageTheme(true) { IslandWorld(
            ownName = "Constant ground synthetic", ownEmoji = "🌴", friends = emptyList(), groups = emptyList(),
            islands = ApiClient.Islands(1000, emptyList()), quests = emptyList(), letters = emptyList(), topics = emptyList(), opened = emptyMap(),
            onBack = {}, onChat = {}, onComposeLetter = {}, onOpenLetter = {}, onLockedTap = {}, onTopics = {}, onAllTopics = {},
            onGlossary = {}, onPeople = {}, onCreateQuest = { _, _, _, _, _, _ -> }, onQuestDone = { _, _ -> }, onQuestDelete = {},
            ownId = 1, startView = "home", creativeActive = writer.home.construction.mode == "creative",
            onError = { writer.errors += it }, loadIsland = { writer.home }, changeConstruction = { c, req -> writer.apply(c, req) },
        ) }
    }
    private fun tick() { rule.mainClock.advanceTimeBy(1200); rule.waitForIdle() }
    private fun start(h: ApiClient.HomeIsland, b: IsleBuilding): Writer {
        val writer = Writer(h, b)
        rule.setContent { World(writer) }; tick()
        return writer
    }
    private fun sign(b: IsleBuilding): SemanticsNodeInteraction {
        val nodes = rule.onAllNodesWithContentDescription(b.label).filter(hasClickAction())
        nodes.assertCountEquals(1)
        return nodes[0].assertIsDisplayed()
    }
    private fun finger(node: SemanticsNodeInteraction) { node.assertIsDisplayed().performTouchInput { click() }; tick() }
    private fun text(label: String) = rule.onNodeWithText(label)
    private fun select(b: IsleBuilding, stage: Int) {
        // Semantics locates the painted sign; click() sends down/up through production pointer routing.
        finger(sign(b))
        rule.onNode(hasText(IslandStages.of(b, stage).name) and hasAnyAncestor(hasContentDescription("Gebäudeaktionen")))
            .assertIsDisplayed()
        rule.onNode(hasContentDescription(if (stage == 0) "Start, noch keine Ausbaustufe" else "Stufe $stage von 3") and
            hasAnyAncestor(hasContentDescription("Gebäudeaktionen"))).assertExists()
    }
    private fun sheet(b: IsleBuilding, stage: Int) {
        select(b, stage)
        finger(text("Ausbauen"))
        text("${IslandStages.of(b, stage).name} ausbauen").assertIsDisplayed()
    }
    private fun button(stage: Int) = text(if (stage == 0) "Gebäude bauen" else "Auf Stufe ${stage + 1} ausbauen")
    private fun unchanged(writer: Writer, original: ApiClient.HomeIsland) {
        assertEquals(emptyList<ApiClient.IslandRequest>(), writer.calls)
        assertEquals(original, writer.home)
        assertTrue(writer.errors.toString(), writer.errors.isEmpty())
    }
    private fun upgrades(b: IsleBuilding, creative: Boolean) {
        val h = fixture(b, creative); val c = h.construction; val writer = start(h, b)
        assertEquals(8, c.gridVersion)
        shot("${b.name}-${c.mode}-stage0")
        for (stage in 0..2) {
            val before = writer.home
            sheet(b, stage)
            button(stage).assertIsEnabled()
            assertEquals(stage, writer.calls.size); assertEquals(before, writer.home)
            // Close the sheet physically, with no implicit commit, then select the named sign again.
            finger(text("Schließen"))
            assertEquals(stage, writer.calls.size); assertEquals(before, writer.home)
            sheet(b, stage)
            assertEquals(stage, writer.calls.size); assertEquals(before, writer.home)
            finger(button(stage).assertIsEnabled())
            assertEquals(stage + 1, writer.calls.size)
            assertEquals(ApiClient.IslandRequest("upgrade", b.name), writer.calls.last())
            val paid = if (creative) 0 else listOf(31, 98, 207)[stage]
            assertEquals(c.copy(buildings = c.buildings + (b.name to stage + 1), starters = c.starters - b.name,
                spent = 73 + paid, available = 900 - paid, revision = 38 + stage), writer.home.construction)
            assertEquals(c.positions, writer.home.construction.positions)
            assertEquals(c.decor, writer.home.construction.decor)
            assertTrue(writer.errors.toString(), writer.errors.isEmpty())
            shot("${b.name}-${c.mode}-stage${stage + 1}")
        }
        select(b, 3)
        finger(text("Info"))
        text("Vollständig ausgebaut").assertIsDisplayed()
        assertEquals(3, writer.calls.size)
    }
    @Test fun postPhysicalSign0To3CreativePreservesSpentAndNeighbours() = upgrades(IsleBuilding.POST, true)
    @Test fun postPhysicalSign0To3NormalSpendsExactlyOnce() = upgrades(IsleBuilding.POST, false)
    @Test fun lighthousePhysicalSign0To3CreativePreservesSpentAndNeighbours() = upgrades(IsleBuilding.LIGHTHOUSE, true)
    @Test fun lighthousePhysicalSign0To3NormalSpendsExactlyOnce() = upgrades(IsleBuilding.LIGHTHOUSE, false)

    private fun lift(b: IsleBuilding, original: ApiClient.IslandConstruction) {
        sign(b).performTouchInput { longClick() }; tick()
        val mask = rule.onNodeWithContentDescription("Baufeld mit Kästchen").fetchSemanticsNode().config.getOrNull(GridHeldMaskKey)
        if (original.gridVersion == 8) {
            assertEquals("Long press must lift the named building, not its neighbour", ground(b, original.positions.getValue(b.name)), mask)
        }
    }
    private fun tapGround(p: List<Int>) {
        rule.onNodeWithContentDescription("Bauplatzraster").performTouchInput {
            val (x, y) = IslandGrid.project(p, 5); click(Offset(width * x, height * y))
        }; tick()
    }
    private fun moves(b: IsleBuilding) {
        val h = fixture(b); val c = h.construction; val writer = start(h, b)
        val confirm = rule.onNodeWithContentDescription("Bestätigen")
        lift(b, c); unchanged(writer, h)
        val expectedTargets = Grid7Contract.lattice.filter { p ->
            Grid7Contract.fits("building:${b.name}:0", 5, p) &&
                (b != IsleBuilding.LIGHTHOUSE || p in Grid7Contract.spots(0, 5)) &&
                c.decor.none { it.position in ground(b, p) } &&
                c.positions.filterKeys { it != b.name }.values.none { it in ground(b, p) }
        }.toSet()
        assertTrue(expectedTargets.isNotEmpty())
        assertEquals(expectedTargets, rule.onNodeWithContentDescription("Baufeld mit Kästchen")
            .fetchSemanticsNode().config.getOrNull(GridTargetsKey)?.toSet())
        // The front lantern occupies an otherwise valid terrain/coast anchor (not an off-map tap).
        val blocked = c.decor.map { it.position }.first { p -> Grid7Contract.fits("building:${b.name}:0", 5, p) &&
            (b != IsleBuilding.LIGHTHOUSE || p in Grid7Contract.spots(0, 5)) }
        tapGround(blocked); confirm.assertIsNotEnabled(); unchanged(writer, h)
        finger(confirm); unchanged(writer, h)
        finger(rule.onNodeWithContentDescription("Abbrechen")); unchanged(writer, h)
        lift(b, c)
        val free = expectedTargets.filter { p -> p[1] <= 12 && p != c.positions[b.name] }
            .maxBy { p -> kotlin.math.abs(p[0] - c.positions.getValue(b.name)[0]) + kotlin.math.abs(p[1] - c.positions.getValue(b.name)[1]) }
        tapGround(free); confirm.assertIsEnabled(); unchanged(writer, h)
        shot("${b.name}-move-before-confirm")
        finger(confirm)
        assertEquals(listOf(ApiClient.IslandRequest("move", b.name, position = free)), writer.calls)
        assertEquals(c.copy(positions = c.positions + (b.name to free), revision = c.revision + 1), writer.home.construction)
        assertTrue(writer.errors.toString(), writer.errors.isEmpty())
    }
    @Test fun postPhysicalInvalidMoveCancelAndPreconfirmNeverWrite() = moves(IsleBuilding.POST)
    @Test fun lighthousePhysicalInvalidMoveCancelAndPreconfirmNeverWrite() = moves(IsleBuilding.LIGHTHOUSE)

    private fun unknown(b: IsleBuilding) {
        val h = fixture(b, version = 9); val writer = start(h, b)
        sheet(b, 0)
        button(0).assertIsNotEnabled()
        finger(button(0)); unchanged(writer, h)
        finger(text("Schließen")); unchanged(writer, h)
        lift(b, h.construction)
        rule.onNodeWithContentDescription("Bestätigen").assertIsNotEnabled()
        val targets = rule.onNodeWithContentDescription("Baufeld mit Kästchen").fetchSemanticsNode().config.getOrNull(GridTargetsKey)
        assertTrue(targets != null && targets.isEmpty())
        finger(rule.onNodeWithContentDescription("Bestätigen")); unchanged(writer, h)
        finger(rule.onNodeWithContentDescription("Abbrechen")); unchanged(writer, h)
    }
    @Test fun unknown9PostPhysicalUpgradeAndMoveAreDisabled() = unknown(IsleBuilding.POST)
    @Test fun unknown9LighthousePhysicalUpgradeAndMoveAreDisabled() = unknown(IsleBuilding.LIGHTHOUSE)

    /** Optional real decorView.draw output. No extra skipped test and no fabricated OS frames. */
    private fun shot(name: String) {
        val path = System.getenv("CONSTANT_GROUND_UI_SHOTS") ?: return
        val scratch = File(checkNotNull(System.getenv("TMPDIR"))).canonicalFile
        val out = File(path).canonicalFile
        check(out.toPath().startsWith(scratch.toPath()) && out != scratch) { "Captures must be beneath TMPDIR" }
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            check(view.width > 0 && view.height > 0)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap)); check(out.isDirectory || out.mkdirs())
                File(out, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
        }
    }
}
