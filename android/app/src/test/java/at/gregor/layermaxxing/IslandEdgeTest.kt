package at.gregor.layermaxxing

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.ArrayDeque

/** Independent oracle: committed terrain/rectangles + ellipse + border flood fill, never production edge/fit helpers. */
internal object Grid7Contract {
    fun file(name: String): File = listOf("../../server/app/geometry/$name", "server/app/geometry/$name", "../server/app/geometry/$name")
        .map(::File).first { it.exists() }
    val ground by lazy { JSONObject(file("island_ground.json").readText()) }
    val fixture by lazy { JSONObject(file("island_edge.json").readText()) }
    val lattice = (0..20).flatMap { y -> (0..20).map { x -> listOf(x, y) } }
    fun pairs(a: JSONArray): Set<List<Int>> = (0 until a.length()).map { a.getJSONArray(it).let { listOf(it.getInt(0), it.getInt(1)) } }.toSet()
    fun zone(p: List<Int>): Char = if (p[0] in 0..20 && p[1] in 0..20) ground.getJSONArray("zones").getString(p[1])[p[0]] else 'W'
    private val meadows by lazy { List(6) { computeMeadow(it) } }
    fun meadow(land: Int): Set<List<Int>> = meadows[land]
    private fun computeMeadow(land: Int): Set<List<Int>> {
        val r = 1.0 - .035 / listOf(.46, .58, .70, .82, .92, 1.0)[land]
        return lattice.filter { p ->
            val u = (p[0] / 20.0 - .5) / .37; val v = (p[1] / 20.0 - .47) / .33
            zone(p) == 'L' && u*u + v*v <= r*r
        }.toSet()
    }
    fun neighbours(p: List<Int>) = listOf(listOf(p[0]+1,p[1]), listOf(p[0]-1,p[1]), listOf(p[0],p[1]+1), listOf(p[0],p[1]-1))
    private val edges by lazy { List(6) { coast(meadow(it)) } }
    fun coast(meadow: Set<List<Int>>): Set<List<Int>> {
        val outside = mutableSetOf<List<Int>>(); val queue = ArrayDeque<List<Int>>()
        queue.add(listOf(-1, -1))
        while (queue.isNotEmpty()) {
            val p = queue.removeFirst()
            if (p[0] !in -1..21 || p[1] !in -1..21 || p in meadow || !outside.add(p)) continue
            neighbours(p).forEach { queue.add(it) }
        }
        return meadow.filter { p -> neighbours(p).any { it in outside } }.toSet()
    }
    fun edge(land: Int) = edges[land]
    fun rect(key: String, land: Int, p: List<Int>, part: String = "ground"): Set<List<Int>> {
        val r = ground.getJSONObject("pieces").getJSONObject(key).getJSONArray("lands").getJSONObject(land).optJSONArray(part) ?: return emptySet()
        return buildSet { for (x in r.getInt(0)..r.getInt(2)) for (y in r.getInt(1)..r.getInt(3)) add(listOf(p[0]+x,p[1]+y)) }
    }
    fun fits(key: String, land: Int, p: List<Int>): Boolean {
        val g = rect(key, land, p)
        if (!key.startsWith("building:HARBOUR:")) return meadow(land).containsAll(g)
        val sea = rect(key, land, p, "sea"); val berth = ground.getJSONArray("berth")
        return g.all { zone(it) == 'B' } && (sea.isEmpty() || sea.all { zone(it) == 'W' } && zone(listOf(p[0]+berth.getInt(0),p[1]+berth.getInt(1))) == 'W')
    }
    fun spots(stage: Int, land: Int): Set<List<Int>> = lattice.filter { p ->
        fits("building:LIGHTHOUSE:$stage", land, p) && rect("building:LIGHTHOUSE:$stage", land, p).any { it in edge(land) }
    }.toSet()
    // Explicitly synthetic, live-shaped API payload. Version 7 reflects the public metadata reported in TASK.md.
    fun homeJson(stage: Int = 1, spot: List<Int> = listOf(10, 10), version: String = "7"): JSONObject = JSONObject("""
        {"user_id":1,"decor":{},"score":500,"items":[],"construction":{
        "mode":"creative","land":5,"revision":17,"available":500,"age_days":30,"grid_version":$version,
        "buildings":{"LIGHTHOUSE":$stage},"starters":[],"positions":{"LIGHTHOUSE":[${spot[0]},${spot[1]}]},"decor":[]}}
    """)
    fun home(stage: Int = 1, spot: List<Int> = listOf(10, 10), version: String = "7"): ApiClient.HomeIsland =
        ApiClient.parseHome(homeJson(stage, spot, version))
}

class IslandEdgeTest {
    private val lighthouse = IslandTarget.Building(IsleBuilding.LIGHTHOUSE)
    private fun at(stage: Int, land: Int) = ApiClient.IslandConstruction(land = land, gridVersion = 7,
        buildings = if (stage > 0) mapOf("LIGHTHOUSE" to stage) else emptyMap(), starters = if (stage == 0) setOf("LIGHTHOUSE") else emptySet())

    @Test fun packagedFixtureAndFloodFilledOpenSeaCoastMatchExactlyIncludingInlandPockets() {
        val bytes = IslandEdge::class.java.classLoader!!.getResourceAsStream(IslandEdge.RESOURCE)!!.use { it.readBytes() }
        assertTrue(bytes.contentEquals(Grid7Contract.file(IslandEdge.RESOURCE).readBytes()))
        assertEquals(7, Grid7Contract.fixture.getInt("version")); assertEquals(6, Grid7Contract.ground.getInt("version"))
        for (land in 0..5) {
            val edge = Grid7Contract.edge(land); val meadow = Grid7Contract.meadow(land)
            assertEquals(Grid7Contract.pairs(Grid7Contract.fixture.getJSONObject("edge").getJSONArray("$land")), edge)
            assertEquals(edge, IslandEdge.cells(land))
            for (p in meadow - edge) if (Grid7Contract.neighbours(p).any { it !in meadow }) {
                assertFalse(p in IslandEdge.cells(land))
            }
        }
        // Explicit enclosed rock pocket: unlike a naive four-neighbour rim it must create no coast.
        val rock = listOf(10, 10)
        val meadow = (3..17).flatMap { y -> (3..17).map { x -> listOf(x,y) } }.toSet() - setOf(rock)
        val coast = Grid7Contract.coast(meadow)
        assertTrue(Grid7Contract.neighbours(rock).all { it in meadow && it !in coast })
        assertTrue(listOf(3,10) in coast)
    }

    @Test fun everyCandidateAcrossSixLandsAndFourStagesMatchesIndependentRule7AndPinnedRule6() {
        for (land in 0..5) for (stage in 0..3) {
            val c = at(stage, land); val ref = Grid7Contract.spots(stage, land)
            assertTrue("land $land stage $stage", ref.isNotEmpty())
            assertEquals(ref, Grid7Contract.pairs(Grid7Contract.fixture.getJSONObject("lighthouse_spots").getJSONObject("$land").getJSONArray("$stage")))
            assertEquals(ref, IslandLayout.ruleTargets(c, lighthouse).toSet())
            assertEquals(ref, IslandLayout.targets(c, lighthouse).toSet())
            assertEquals(ref, IslandLayout.fitSpots(c, lighthouse).toSet())
            val h = Grid7Contract.home().copy(construction = c)
            for (p in Grid7Contract.lattice) {
                assertEquals("v7 $land/$stage $p", p in ref, IslandLayout.reason(h, IslandDraft(lighthouse, p)) == null)
                assertEquals("v6 $land/$stage $p", Grid7Contract.fits("building:LIGHTHOUSE:$stage", land, p), IslandLayout.ruleValid(c.copy(gridVersion = 6), IsleBuilding.LIGHTHOUSE, p))
            }
        }
    }

    @Test fun occupiedCandidatesAcrossAllLandsAndStagesStillMatchIndependentRectangles() {
        for (land in 0..5) for (stage in 0..3) {
            val bench = Grid7Contract.spots(stage, land).first()
            val c = at(stage, land).copy(buildings = at(stage, land).buildings + ("HOUSE" to 1),
                positions = mapOf("HOUSE" to listOf(10,10)), decor = listOf(ApiClient.IslandDecor(100, "bench", bench)))
            val blocked = Grid7Contract.rect("building:HOUSE:1", land, listOf(10,10)) +
                Grid7Contract.rect(IslandMasks.decorKey("bench"), land, bench)
            val expected = Grid7Contract.spots(stage, land).filter { p ->
                Grid7Contract.rect("building:LIGHTHOUSE:$stage", land, p).none { it in blocked }
            }.toSet()
            assertEquals("occupied $land/$stage", expected, IslandLayout.ruleTargets(c, lighthouse).toSet())
            assertEquals(expected, IslandLayout.targets(c, lighthouse).toSet())
            assertTrue(expected.isNotEmpty()); assertFalse(bench in expected)
        }
    }

    @Test fun onlyLighthouseChangesOtherBuildingsHarbourAndDecorKeepGround6() {
        val items = IsleDecor.labels.keys + SpecialDecor.items.map { it.key }
        for (land in 0..5) {
            for (b in IsleBuilding.entries.filter { it != IsleBuilding.LIGHTHOUSE }) for (stage in 0..3) {
                val c = ApiClient.IslandConstruction(land = land, gridVersion = 7, buildings = if (stage > 0) mapOf(b.name to stage) else emptyMap(), starters = if (stage == 0) setOf(b.name) else emptySet())
                val t = IslandTarget.Building(b)
                val expected = Grid7Contract.lattice.filter { Grid7Contract.fits("building:${b.name}:$stage", land, it) }.toSet()
                assertEquals("$b/$stage/$land", expected, IslandLayout.ruleTargets(c, t).toSet())
                assertEquals(IslandLayout.targets(c.copy(gridVersion = 6), t), IslandLayout.targets(c, t))
            }
            for (item in items) {
                val c = at(1, land).copy(buildings = emptyMap())
                val t = IslandTarget.Decor(item)
                assertEquals(IslandLayout.ruleTargets(c.copy(gridVersion = 6), t), IslandLayout.ruleTargets(c, t))
                assertEquals(Grid7Contract.lattice.filter { Grid7Contract.fits(IslandMasks.decorKey(item), land, it) }.toSet(), IslandLayout.ruleTargets(c, t).toSet())
            }
        }
    }

    @Test fun savedInlandUpgradeIsNarrowActionAwareAndNeverRelocatesOrMutatesTheLedger() {
        val inland = Grid7Contract.lattice.first { Grid7Contract.fits("building:LIGHTHOUSE:2", 5, it) && it !in Grid7Contract.spots(2, 5) }
        val h = Grid7Contract.home(1, inland); val c = h.construction
        val upgrade = IslandDraft(lighthouse, inland, lifted = true, upgrade = true)
        assertNull(IslandLayout.upgradeProblem(c, IsleBuilding.LIGHTHOUSE))
        assertEquals(inland, IslandLayout.upgradeStart(c, IsleBuilding.LIGHTHOUSE))
        assertEquals(inland, IslandLayout.startCell(c, IsleBuilding.LIGHTHOUSE, null))
        assertNull(IslandLayout.reason(h, upgrade))
        assertTrue(inland in IslandLayout.targets(c, upgrade)); assertTrue(inland in IslandLayout.fitSpots(c, upgrade))
        assertFalse(inland in IslandLayout.targets(c, lighthouse))
        assertEquals(IslandEdge.ERROR, IslandLayout.reason(h, upgrade.copy(upgrade = false)))
        assertNull(IslandLayout.requestProblem(c, ApiClient.IslandRequest("upgrade", "LIGHTHOUSE")))
        assertNull(IslandLayout.requestProblem(c, ApiClient.IslandRequest("upgrade", "LIGHTHOUSE", position = inland)))
        assertEquals(IslandEdge.ERROR, IslandLayout.requestProblem(c, ApiClient.IslandRequest("move", "LIGHTHOUSE", position = inland)))
        val otherInside = Grid7Contract.lattice.first { it != inland && Grid7Contract.fits("building:LIGHTHOUSE:2", 5, it) && it !in Grid7Contract.spots(2, 5) }
        assertEquals(IslandEdge.ERROR, IslandLayout.reason(h, upgrade.copy(position = otherInside)))
        val edge = Grid7Contract.spots(2, 5).first()
        assertNull(IslandLayout.reason(h, upgrade.copy(position = edge)))
        assertNull(IslandLayout.requestProblem(c, ApiClient.IslandRequest("upgrade", "LIGHTHOUSE", position = edge)))
        val preview = IslandLayout.preview(IslandLayout.editing(h, upgrade.copy(position = edge)), upgrade.copy(position = edge))
        assertEquals(edge, preview.construction.positions["LIGHTHOUSE"])
        assertEquals(inland, c.positions["LIGHTHOUSE"]); assertEquals(1, c.level(IsleBuilding.LIGHTHOUSE)); assertEquals(17, c.revision)
        // Ground and occupancy are still mandatory even for an in-place upgrade.
        val blocked = c.copy(decor = listOf(ApiClient.IslandDecor(100, "bench", inland)))
        assertNotNull(IslandLayout.upgradeProblem(blocked, IsleBuilding.LIGHTHOUSE))
        assertNotNull(IslandLayout.reason(h.copy(construction = blocked), upgrade))
        assertFalse(inland in IslandLayout.targets(blocked, upgrade))
        val gone = c.copy(buildings = emptyMap())
        assertEquals(IslandLayout.GONE, IslandLayout.reason(h.copy(construction = gone), upgrade))
        assertEquals(IslandLayout.REJECTED, IslandLayout.reason(h, upgrade.copy(rejected = setOf(inland))))
    }

    @Test fun liveShapedVersion7ParsesAndOffersBuildMoveTargetsWhileUnknownMalformedAndMissingFailClosed() {
        val h = Grid7Contract.home(); val c = h.construction
        assertEquals(7, c.gridVersion); assertNull(PlacementRule.problem(c.gridVersion))
        assertEquals(7, PlacementRule.read(JSONObject("""{"grid_version":7,"features":["build_grid_v7"]}""")))
        assertTrue(IslandLayout.targets(c, lighthouse).isNotEmpty())
        val empty = c.copy(buildings = emptyMap(), positions = emptyMap())
        val build = IslandLayout.startCell(empty, IsleBuilding.LIGHTHOUSE, listOf(10, 10))
        assertNull(IslandLayout.requestProblem(empty, ApiClient.IslandRequest("build", "LIGHTHOUSE", position = build)))
        // Direct paid construction (upgrade of a not-yet-standing piece) uses stage 1, without grandfathering.
        val edge1 = Grid7Contract.spots(1, 5).first()
        assertNull(IslandLayout.requestProblem(empty, ApiClient.IslandRequest("upgrade", "LIGHTHOUSE", position = edge1)))
        assertNotNull(IslandLayout.requestProblem(empty, ApiClient.IslandRequest("upgrade", "LIGHTHOUSE", position = listOf(10,10))))
        val occupied = c.copy(decor = listOf(ApiClient.IslandDecor(100, "bench", edge1)))
        assertFalse(edge1 in IslandLayout.targets(occupied, lighthouse))
        assertTrue(IslandLayout.targets(occupied, lighthouse).isNotEmpty())
        for (raw in listOf("8", "99", "\"7\"", "7.0", "7.1", "true", "{}", "[]", "2147483648", "null")) {
            val bad = Grid7Contract.home(version = raw).construction
            assertNotNull(raw, PlacementRule.problem(bad.gridVersion))
            assertTrue(IslandLayout.targets(bad, lighthouse).isEmpty())
            assertNotNull(IslandLayout.requestProblem(bad, ApiClient.IslandRequest("move", "LIGHTHOUSE", position = edge1)))
        }
        val missing = ApiClient.parseHome(JSONObject("""{"user_id":1,"decor":{},"score":0,"items":[],"construction":{}}""")).construction
        assertEquals(PlacementRule.LEGACY, missing.gridVersion)
        assertTrue(IslandLayout.targets(missing, lighthouse).isEmpty())
    }
}
