package at.gregor.layermaxxing

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 7.9.12 on the real archipelago map of IslandWorld (Robolectric, JVM simulation, not a device): every route (and the
 * lane of every boat) starts at MY island's berth and ends at the friend's berth, computed from the current
 * constructions; a friend without a harbour ends at the visible landing stage; a harbour moved on another device is
 * the new end after the next poll, without a restart; a boat at full progress lies at the berth, a waiting letter in
 * the roadstead of its sender, an arrived (opened) letter is no boat any more.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class IslandMapRouteUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private companion object { const val MAX_TURN_DEG = 30.0 }
    private val now = System.currentTimeMillis() / 1000

    private var homeVersion = PlacementRule.CURRENT
    private var homeStage = 2
    private fun construction(harbour: List<Int>?, land: Int = 5, version: Int = PlacementRule.CURRENT, stage: Int = 2) = ApiClient.IslandConstruction(land = land,
        gridVersion = version,
        buildings = mapOf("HOUSE" to 1) + (if (harbour != null && stage > 0) mapOf("HARBOUR" to stage) else emptyMap()),
        starters = if (harbour != null && stage == 0) setOf("HARBOUR") else emptySet(),
        positions = mapOf("HOUSE" to listOf(8, 6)) + (harbour?.let { mapOf("HARBOUR" to it) } ?: emptyMap()))
    private var homeHarbour = listOf(8, 15)
    private var friends = listOf(ApiClient.UserSummary(2, "Gerfried", "friends", "🦊", "#2E7D32"), ApiClient.UserSummary(3, "Anna", "friends", "🐻", "#1565C0"))
    private var infos = listOf(
        ApiClient.IslandInfo(2, 10, 0, 10, 2, 40, ApiClient.HomeIsland(2, emptyMap(), 10, emptyList(), construction = construction(IslandGround.harbourSpots(2, 3).first(), land = 3))),
        // Anna's island has no harbour (old data): her routes end at the visible landing stage
        ApiClient.IslandInfo(3, 10, 0, 10, 1, 40, ApiClient.HomeIsland(3, emptyMap(), 10, emptyList(), construction = construction(null, land = 1))),
    )
    private var letters = listOf(
        // to Gerfried: sent two days ago, arrives in two days (half way)
        letter(11, 2, "Gerfried", incoming = false, mode = "timed", createdAt = now - 2 * 86_400, releaseAt = now + 2 * 86_400),
        // from Gerfried: its trip is over (full progress) but not opened yet: at MY berth
        letter(12, 2, "Gerfried", incoming = true, mode = "timed", createdAt = now - 4 * 86_400, releaseAt = now - 60),
        // to Anna: waits for both approvals: in my roadstead
        letter(13, 3, "Anna", incoming = false, mode = "mutual", createdAt = now - 86_400),
        // opened: not at sea any more
        letter(14, 3, "Anna", incoming = true, mode = "timed", unlocked = true, createdAt = now - 9 * 86_400, releaseAt = now - 86_400),
    )

    @Composable private fun World() {
        VillageTheme(true) { IslandWorld(
            ownName = "Gregor", ownEmoji = "🦉", friends = friends, groups = emptyList(), islands = ApiClient.Islands(10, infos),
            quests = emptyList(), letters = letters, topics = emptyList(), opened = emptyMap(), onBack = {}, onChat = {}, onComposeLetter = {},
            onOpenLetter = {}, onLockedTap = {}, onTopics = {}, onAllTopics = {}, onGlossary = {}, onPeople = {}, onCreateQuest = { _, _, _, _, _, _ -> },
            onQuestDone = { _, _ -> }, onQuestDelete = {}, ownId = 1, startView = "map",
            loadIsland = { ApiClient.HomeIsland(1, emptyMap(), 0, emptyList(), construction = construction(homeHarbour, version = homeVersion, stage = homeStage)) },
        ) }
    }
    private fun tick(ms: Long = 800) { rule.mainClock.advanceTimeBy(ms); rule.waitForIdle() }
    private fun node() = rule.onAllNodes(SemanticsMatcher("map routes") { it.config.getOrNull(MapRoutesKey) != null })[0].fetchSemanticsNode().config
    private fun berths() = node()[MapBerthsKey].toMap()
    private fun routes() = node()[MapRoutesKey]
    private fun close(a: Offset, b: Offset, tol: Float = .5f) = (a - b).getDistance() <= tol
    /** My hub's box as IslandWorld draws it (180 dp wide around the map centre). */
    private fun hub(): Pair<Offset, Float> {
        val area = node()[MapAreaKey]; val px = 180f * rule.activity.resources.displayMetrics.density
        return Offset(area.width / 2 - px / 2, area.height / 2 - px * .42f) to px
    }
    /** Where my harbour at [spot] puts the berth on the map: from its construction, moored at the painted coast. */
    private fun rawBerth(spot: List<Int>) = hub().let { (tl, px) ->
        HarbourRoute.onMap(tl, px, HarbourRoute.berth(construction(spot, version = homeVersion, stage = homeStage))) }
    /**
     * A coast berth moors at the nearest water (7.9.12, kept); 7.9.13: a legacy one at most three cells short of it, and
     * the ship lies where its whole 12 dp hull has water, at most two hulls further (SeaChart.moor).
     */
    private fun moored(raw: Offset) = hub().let { (tl, px) ->
        SeaChart.mooring(raw, listOf(SeaChart.coastOf(tl, px, 5)), 12f * density, HarbourRoute.LEGACY_REACH_CELLS * IslandGrid.step(5) * px + SeaChart.MOOR_HULLS * 12f * density)
    }
    private fun expectedBerth(spot: List<Int>) = moored(rawBerth(spot)) ?: error("berth of $spot is not at the coast")
    private fun repairs() = node()[MapRepairKey]
    /** No kink anywhere on the drawn (= sailed) line, the dock exits included: every turn <= [MAX_TURN_DEG]. */
    private fun assertCurved(r: MapRoute) {
        val p = r.path
        for (k in 1 until p.size - 1) {
            val a = p[k] - p[k - 1]; val c = p[k + 1] - p[k]
            if (a.getDistance() < .5f || c.getDistance() < .5f) continue
            val deg = Math.toDegrees(kotlin.math.acos(((a.x * c.x + a.y * c.y) / (a.getDistance() * c.getDistance())).coerceIn(-1f, 1f).toDouble()))
            assertTrue("${r.friendId}/${r.letterId} turn of $deg° at $k of ${p.size}", deg <= MAX_TURN_DEG)
        }
    }
    private val density get() = rule.activity.resources.displayMetrics.density

    @Test fun everyRouteAndBoatRunsFromMyHarbourToTheFriendsHarbour() {
        rule.setContent { World() }
        tick(1500)
        val b = berths()
        for (r in routes()) {
            assertTrue("${r.friendId} ${r.letterId} starts at my berth", close(r.path.first(), b.getValue(0L)))
            assertTrue("${r.friendId} ${r.letterId} ends at the friend's berth", close(r.path.last(), b.getValue(r.friendId)))
        }
        val boats = routes().filter { it.letterId != null }.associateBy { it.letterId }
        // the opened letter is no boat; the others are on their lanes
        assertEquals(setOf(11L, 12L, 13L), boats.keys)
        val half = boats.getValue(11L)
        assertTrue(close(half.boat!!, HarbourRoute.at(half.path, .5f), 6f))
        // full progress: at my berth (incoming), not 86 % of the way
        assertTrue(close(boats.getValue(12L).boat!!, b.getValue(0L), 1f))
        // waiting: in my roadstead, far from Anna's landing stage
        val wait = boats.getValue(13L)
        assertTrue((wait.boat!! - b.getValue(0L)).getDistance() < (wait.boat - b.getValue(3L)).getDistance())
        assertTrue(!close(wait.boat, b.getValue(3L), 20f))
        // Anna's island has no harbour: her end is the visible landing stage below her island (in the water)
        val annaRoute = routes().first { it.friendId == 3L && it.letterId == null }
        assertTrue(close(annaRoute.path.last(), b.getValue(3L)))
    }

    /**
     * 7.9.13: the "Meine Insel" plate is never covered by a boat (before, the boats at my south berth sat on it). The
     * boats keep their real size and place; the plate moves to the side of the island away from the berth.
     */
    @Test fun noBoatCoversMyIslandsNamePlate() {
        // the map of the 7.9.13 renders: my harbour on the south coast, boats 11/12/13 at sea / at my berth / waiting
        rule.setContent { World() }
        tick(1500)
        val plate = rule.onNodeWithTag("HubPlate").fetchSemanticsNode().boundsInRoot
        val boats = rule.onAllNodes(SemanticsMatcher("boat") { n ->
            n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.any { it.contains(" – Brief ") } == true
        }).fetchSemanticsNodes()
        assertEquals(3, boats.size)
        boats.forEach { b -> assertTrue("boat ${b.boundsInRoot} over the plate $plate", !b.boundsInRoot.overlaps(plate)) }
    }

    /** Version 5 (7.9.12 coast spots, kept exactly): the pier moved 4 cells east is the new end, 4 cells east. */
    @Test fun aHarbourMovedMeanwhileIsTheNewEndAfterTheNextPollWithoutARestart() {
        homeVersion = PlacementRule.MASKS
        rule.setContent { World() }
        tick(1500)
        val before = berths().getValue(0L)
        homeHarbour = listOf(12, 15) // moved 4 cells to the east (e.g. on another device)
        tick(61_000)
        val after = berths().getValue(0L)
        val density = rule.activity.resources.displayMetrics.density
        // the pier's own berth moved exactly 4 cells east; a berth on the painted coast moors at the nearest open water
        assertEquals(4 * IslandGrid.step(5) * 180f * density, rawBerth(homeHarbour).x - rawBerth(listOf(8, 15)).x, 1f)
        assertTrue("$before", close(before, expectedBerth(listOf(8, 15)), 1f))
        assertTrue("$after", close(after, expectedBerth(homeHarbour), 1f))
        assertTrue(after.x - before.x > 2 * IslandGrid.step(5) * 180f * density)
        routes().forEach { assertTrue(close(it.path.first(), after)) }
    }
    /**
     * The real case of the 7.9.12 review map: with Lena (west of the hub, two boats on her route) a boat sat half
     * outside the map's left edge. Every boat's drawn hull (84 × 42 dp box, its badge and "+N" inside) lies completely
     * inside the map area; routes still run berth to berth.
     */
    @Test fun withLenaEveryBoatHullLiesInsideTheMap() {
        friends = friends + ApiClient.UserSummary(4, "Lena", "friends", "🦉", "#EF6C00")
        infos = infos + ApiClient.IslandInfo(4, 10, 0, 10, 3, 120, ApiClient.HomeIsland(4, emptyMap(), 10, emptyList(), construction = construction(listOf(6, 14), land = 4)))
        letters = letters.filter { it.id != 14L } + listOf(
            letter(15, 4, "Lena", incoming = true, mode = "timed", createdAt = now - 86_400, releaseAt = now + 3 * 86_400),
            letter(16, 4, "Lena", incoming = false, mode = "timed", createdAt = now - 3 * 86_400, releaseAt = now + 86_400),
        )
        rule.setContent { World() }
        tick(1500)
        val area = node()[MapAreaKey]
        val boats = routes().filter { it.letterId != null }
        assertTrue(boats.any { it.friendId == 4L })
        for (b in boats) {
            val hull = b.hull!!
            assertTrue("boat ${b.letterId} hull $hull outside $area",
                hull.left >= area.left - .5f && hull.top >= area.top - .5f && hull.right <= area.right + .5f && hull.bottom <= area.bottom + .5f)
            assertTrue(close(b.boat!!, hull.center, 1f))
        }
        val b = berths()
        for (r in routes()) { assertTrue(close(r.path.first(), b.getValue(0L))); assertTrue(close(r.path.last(), b.getValue(r.friendId))); assertCurved(r) }
    }

    /**
     * 7.9.13 review: Lena's steamer sat on her own "Lena · Vertraut" plate (the end island was exempt and so was its
     * plate). With the measured plates: every boat (11/12/13 and Lena's 15/16) is on the map and its drawn box covers
     * no friend's name plate and no "Warteliegeplatz" plate; every plate stays whole inside the map.
     */
    @Test fun noBoatCoversAFriendsNamePlateOrAWaitingPlate() {
        friends = friends + ApiClient.UserSummary(4, "Lena", "friends", "🦉", "#EF6C00")
        infos = infos + ApiClient.IslandInfo(4, 10, 0, 10, 3, 120, ApiClient.HomeIsland(4, emptyMap(), 10, emptyList(), construction = construction(listOf(6, 14), land = 4)))
        letters = letters.filter { it.id != 14L } + listOf(
            letter(15, 4, "Lena", incoming = true, mode = "timed", createdAt = now - 86_400, releaseAt = now + 3 * 86_400),
            letter(16, 4, "Lena", incoming = false, mode = "timed", createdAt = now - 3 * 86_400, releaseAt = now + 86_400),
        )
        homeHarbour = IslandGround.harbourSpots(2, 5).single()
        rule.setContent { World() }
        tick(1500); tick(1500)
        assertEquals(setOf(11L, 12L, 13L, 15L, 16L), routes().mapNotNull { it.letterId }.toSet())
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val plates = listOf(2L, 3L, 4L).map { rule.onNodeWithTag("IslandPlate-$it", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }
        val waiting = rule.onAllNodes(SemanticsMatcher("waiting plate") { n ->
            n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.any { it.startsWith("Warteliegeplatz in der Bucht") } == true
        }).fetchSemanticsNodes().map { it.boundsInRoot }
        val boats = rule.onAllNodes(SemanticsMatcher("boat") { n ->
            n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.any { it.contains(" – Brief ") } == true
        }).fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(5, boats.size)
        plates.forEach { p -> assertTrue("plate $p outside $root", p.left >= root.left && p.top >= root.top && p.right <= root.right && p.bottom <= root.bottom) }
        for (b in boats) {
            assertTrue("boat $b outside $root", b.left >= root.left - .5f && b.right <= root.right + .5f && b.top >= root.top - .5f && b.bottom <= root.bottom + .5f)
            (plates + waiting).forEach { p -> assertTrue("boat $b over plate $p (plates $plates waiting $waiting boats $boats)", !b.overlaps(p)) }
        }
        for (r in routes()) assertCurved(r)
    }

    /**
     * 7.9.13 rule 6: the rowboat moved on the beach (another device) moors after the next poll at the water line in front
     * of its NEW spot ([IslandGround.berthCell]); every route starts there.
     */
    @Test fun aRowboatMovedOnTheBeachIsTheNewEndUnderTheGroundRule() {
        homeVersion = PlacementRule.GROUND; homeStage = 0
        val spots = IslandGround.harbourSpots(0, 5)
        val from = spots.minBy { it[0] * 100 + it[1] }; val to = spots.maxBy { it[0] * 100 + it[1] }
        assertTrue("$from $to", from != to)
        homeHarbour = from
        rule.setContent { World() }
        tick(1500)
        val before = berths().getValue(0L)
        homeHarbour = to
        tick(61_000)
        val after = berths().getValue(0L)
        assertTrue("$before", close(before, expectedBerth(from), 1f))
        assertTrue("$after", close(after, expectedBerth(to), 1f))
        assertTrue(!close(before, after, 5f))
        assertTrue(routes().isNotEmpty())
        routes().forEach { assertTrue(close(it.path.first(), after)) }
    }

    /**
     * 7.9.13 Phase-2 risk: an old pier (stage 2, v5 data) standing in the middle of the land is NOT declared moored at
     * some far coast point. The island is marked for repair (visibly, with a reason) and its boats use the real landing
     * stage in the cove; routes and boats stay (nothing hidden).
     */
    @Test fun anInlandHarbourIsMarkedForRepairAndBoatsUseTheCoveStage() {
        homeVersion = PlacementRule.MASKS; homeHarbour = listOf(10, 9)
        rule.setContent { World() }
        tick(1500)
        assertEquals("${berths()}", setOf(0L), repairs())
        val (tl, px) = hub()
        val coast = listOf(SeaChart.coastOf(tl, px, 5))
        val far = SeaChart.mooring(rawBerth(homeHarbour), coast, 4f * density)!!
        val cove = moored(HarbourRoute.onMap(tl, px, HarbourRoute.fallbackBerth(5)))!!
        val b = berths().getValue(0L)
        assertTrue("the cove stage, $b vs $cove", close(b, cove, 1f))
        assertTrue("not the far coast point the old mooring chose", !close(b, far, 5f) || close(far, cove, 5f))
        assertTrue("the repair need is said", rule.onAllNodes(SemanticsMatcher("repair") { n ->
            n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.any { it.startsWith("Reparatur nötig") } == true
        }).fetchSemanticsNodes().isNotEmpty())
        assertTrue("boats are not hidden", routes().any { it.letterId != null })
        // the cove stage is visibly named a waiting berth (in the repair note), and the boat bound for my island does
        // not claim my harbour
        assertTrue(rule.onAllNodes(SemanticsMatcher("waiting berth") { n ->
            n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.any { it.startsWith("Reparatur nötig") && it.contains("Warteliegeplatz in der Bucht") } == true
        }).fetchSemanticsNodes().size == 1)
        val inbound = rule.onAllNodes(SemanticsMatcher("boat from Gerfried") { n ->
            n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.any { it.contains("Brief von Gerfried") } == true
        }).fetchSemanticsNodes()
        assertTrue(inbound.isNotEmpty())
        inbound.forEach { n -> assertTrue(n.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single().contains("Ziel: Warteliegeplatz")) }
        routes().forEach { assertTrue(close(it.path.first(), b)); assertCurved(it) }
        // A friend's correct coast harbour is no repair case.
        assertTrue(2L !in repairs())
    }

    /** v6: a pier on a spot that is no socket of its stage (here Lena's 7.9.12 spot) is a repair case, still with routes. */
    @Test fun underTheGroundRuleAPierOffItsSocketIsARepairCaseNotHidden() {
        friends = friends + ApiClient.UserSummary(4, "Lena", "friends", "🦉", "#EF6C00")
        infos = infos + ApiClient.IslandInfo(4, 10, 0, 10, 3, 120, ApiClient.HomeIsland(4, emptyMap(), 10, emptyList(), construction = construction(listOf(6, 14), land = 4)))
        homeHarbour = IslandGround.harbourSpots(2, 5).first()
        assertEquals(false, HarbourRoute.atSocket(construction(listOf(6, 14), land = 4)))
        rule.setContent { World() }
        tick(1500)
        assertEquals(setOf(4L), repairs())
        assertTrue("Lena still has a water way", routes().any { it.friendId == 4L })
        // her cove stage is visibly named a waiting berth, not passed off as her harbour
        assertTrue(rule.onAllNodes(SemanticsMatcher("Lena's waiting berth") { n ->
            n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.any { it == "Warteliegeplatz in der Bucht – kein Hafen (Insel Lena)" } == true
        }).fetchSemanticsNodes().size == 1)
    }

    /** Harbours on the coast (v5 and v6) are never repair cases. */
    @Test fun coastHarboursNeedNoRepair() {
        homeHarbour = IslandGround.harbourSpots(2, 5).first()
        rule.setContent { World() }
        tick(1500)
        assertTrue("${repairs()} ${berths()}", repairs().isEmpty())
    }

    /**
     * 7.9.13 v6 explicit: every drawn route and boat lane is checked water: the open middle keeps the whole hull clear of
     * every painted coast and rock, the dock pieces included (physical hull, no keel-only zone); the lines are curves (no sharp corners in the
     * middle); every boat's centre lies in water on its own lane and its hull box inside the map.
     */
    @Test fun underTheGroundRuleEveryRouteIsCurvedCheckedWaterAndNoBoatClips() {
        homeVersion = PlacementRule.GROUND; homeStage = 2
        homeHarbour = IslandGround.harbourSpots(2, 5).first()
        rule.setContent { World() }
        tick(1500)
        assertTrue("${repairs()}", repairs().isEmpty())
        val coasts = node()[MapCoastsKey]; val area = node()[MapAreaKey]
        val keel = 4f * density; val hull = 12f * density
        val all = routes()
        assertTrue(all.any { it.letterId != null })
        for (r in all) {
            val p = r.path
            // 7.9.13 physical hull: EVERY piece, the dock pieces at both berths included, keeps the whole hull in water
            for (k in 0 until p.size - 1) assertTrue("${r.friendId}/${r.letterId} piece $k hull over land or rock",
                SeaChart.clearSegment(p[k], p[k + 1], coasts, hull * .99f, null, keel))
            // curves: no kink anywhere, the dock exits included (7.9.13 before the fix: up to ~77° at the dock exit of
            // Gerfried's route, the old check skipped those two corners). Every turn between neighbouring pieces <= 30°.
            assertCurved(r)
            r.boat?.let { boat ->
                assertTrue("boat's hull in water", SeaChart.clearance(boat, coasts) >= hull * .99f)
                val h = r.hull!!
                assertTrue("hull inside the map", h.left >= area.left - .5f && h.right <= area.right + .5f && h.top >= area.top - .5f && h.bottom <= area.bottom + .5f)
            }
        }
    }

    /**
     * 7.9.13 review: the historical spot (8, 15) is no stage-2 socket of the v6 ground rule, so a v6 construction with it
     * is honestly a repair case (the render banner was right); the same pier on the real v6 socket is none.
     */
    @Test fun theHistoricalSpotIsARepairCaseUnderV6AndTheRealSocketIsNot() {
        homeVersion = PlacementRule.GROUND; homeStage = 2
        val socket = IslandGround.harbourSpots(2, 5).single()
        println("v6 stage-2 socket on land 5: $socket")
        assertTrue(listOf(8, 15) != socket)
        homeHarbour = listOf(8, 15)
        rule.setContent { World() }
        tick(1500)
        assertEquals(setOf(0L), repairs())
        homeHarbour = socket
        tick(61_000)
        assertTrue("${repairs()}", 0L !in repairs())
    }

    /**
     * v6 pier (stage 2; it has a single socket per land stage, IslandGround.harbourSpots): moved off it on another device,
     * it is a repair case after the next poll (boats at the cove stage), moved back it is the end again, no restart.
     */
    @Test fun aPierMovedOffItsSocketBecomesARepairCaseAfterTheNextPoll() {
        homeVersion = PlacementRule.GROUND; homeStage = 2
        val socket = IslandGround.harbourSpots(2, 5).single()
        homeHarbour = socket
        rule.setContent { World() }
        tick(1500)
        assertTrue(0L !in repairs())
        val atSocket = berths().getValue(0L)
        assertTrue(close(atSocket, expectedBerth(socket), 1f))
        homeHarbour = listOf(socket[0] - 4, socket[1] - 3)
        tick(61_000)
        assertTrue(0L in repairs())
        // the boats use the cove stage (its berth lies next to this single pier socket), never the moved pier's inland berth
        val (tl, px) = hub()
        val cove = moored(HarbourRoute.onMap(tl, px, HarbourRoute.fallbackBerth(5)))!!
        assertTrue(close(berths().getValue(0L), cove, 1f))
        assertTrue(SeaChart.clearance(rawBerth(homeHarbour), listOf(SeaChart.coastOf(tl, px, 5))) < 0f)
        routes().forEach { assertTrue(close(it.path.first(), berths().getValue(0L))) }
        homeHarbour = socket
        tick(61_000)
        assertTrue(0L !in repairs())
        assertTrue(close(berths().getValue(0L), atSocket, 1f))
    }
}
