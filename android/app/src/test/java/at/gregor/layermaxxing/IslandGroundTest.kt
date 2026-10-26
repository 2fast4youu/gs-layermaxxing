package at.gregor.layermaxxing

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 7.9.13 grid_version 6: the ground contract of server/app/geometry/island_ground.json, on the app's side.
 *  - the app reads exactly the server's file (packaged resource, not a copy) and its decision equals the contract
 *    re-derived here from the file alone (zones + rectangles), for every piece, land stage and cell;
 *  - roofs may cover another piece, a shared ground cell never; the drawing order is the ground line;
 *  - zones: the rowboat (harbour 0) on the beach, the piers (1..3) from the beach into open water, everything else meadow;
 *  - an upgrade's bigger ground is checked before paying; old overlapping layouts are listed, kept and repairable.
 * The server's side of the same contract is server/tests/test_v17_ground.py.
 */
class IslandGroundTest {
    private val serverFile by lazy {
        listOf("../../server/app/geometry/island_ground.json", "server/app/geometry/island_ground.json", "../server/app/geometry/island_ground.json")
            .map(::File).first { it.exists() }
    }
    private val json by lazy { JSONObject(serverFile.readText()) }
    private val harbour = IslandTarget.Building(IsleBuilding.HARBOUR)
    private fun v6(land: Int = 5, buildings: Map<String, Int> = emptyMap(), positions: Map<String, List<Int>> = emptyMap(),
        starters: Set<String> = emptySet(), decor: List<ApiClient.IslandDecor> = emptyList()) =
        ApiClient.IslandConstruction(land = land, gridVersion = PlacementRule.GROUND, buildings = buildings, positions = positions, starters = starters, decor = decor)
    private fun at(b: IsleBuilding, stage: Int, land: Int, p: List<Int>? = null) =
        if (stage == 0) v6(land, starters = setOf(b.name), positions = p?.let { mapOf(b.name to it) }.orEmpty())
        else v6(land, buildings = mapOf(b.name to stage), positions = p?.let { mapOf(b.name to it) }.orEmpty())

    // the contract, read independently of IslandGround from the server's file
    private fun zone(i: Int, j: Int): Char = if (i in 0..20 && j in 0..20) json.getJSONArray("zones").getString(j)[i] else 'W'
    private fun rect(key: String, land: Int, name: String): List<Int>? =
        json.getJSONObject("pieces").getJSONObject(key).getJSONArray("lands").getJSONObject(land).optJSONArray(name)?.let { a -> (0..3).map { a.getInt(it) } }
    private fun cells(r: List<Int>?, p: List<Int>) = r?.let { buildSet { for (x in r[0]..r[2]) for (y in r[1]..r[3]) add(listOf(p[0] + x, p[1] + y)) } }.orEmpty()
    private fun contractFits(key: String, land: Int, p: List<Int>, isHarbour: Boolean): Boolean {
        val ground = cells(rect(key, land, "ground"), p); val sea = cells(rect(key, land, "sea"), p)
        if (isHarbour) {
            if (ground.any { zone(it[0], it[1]) != 'B' }) return false
            val berth = json.getJSONArray("berth")
            return sea.isEmpty() || (sea.all { zone(it[0], it[1]) == 'W' } && zone(p[0] + berth.getInt(0), p[1] + berth.getInt(1)) == 'W')
        }
        return ground.all { zone(it[0], it[1]) == 'L' && IslandGrid.buildable(it[0], it[1], land) }
    }
    private val buildingKeys = IsleBuilding.entries.flatMap { b -> (0..3).map { b to IslandMasks.buildingKey(b, it) } }
    private val decorItems = IsleDecor.labels.keys + SpecialDecor.items.map { it.key }

    @Test fun theAppReadsTheServersGroundFileAndDecidesExactlyLikeTheContract() {
        val packaged = IslandGround::class.java.classLoader!!.getResourceAsStream(IslandGround.RESOURCE)!!.use { it.readBytes() }
        assertTrue("packaged island_ground.json differs from the server's", packaged.contentEquals(serverFile.readBytes()))
        assertEquals(6, json.getInt("version")); assertEquals(PlacementRule.GROUND, IslandGround.version())
        assertEquals(PlacementRule.EDGE, PlacementRule.CURRENT)
        assertEquals(json.getJSONObject("pieces").keys().asSequence().toSet(), IslandGround.keys())
        val keys = buildingKeys.map { (b, k) -> k to (b == IsleBuilding.HARBOUR) } + decorItems.map { IslandMasks.decorKey(it) to false }
        for ((key, isHarbour) in keys) for (land in 0..5) for (i in 0..20) for (j in 0..20) {
            val p = listOf(i, j)
            assertEquals("$key land $land $p", contractFits(key, land, p, isHarbour), IslandGround.landProblem(key, land, p, isHarbour) == null)
            assertEquals("$key ground $p", cells(rect(key, land, "ground"), p), IslandGround.ground(key, land, p))
        }
    }

    @Test fun everyBuildingStageAndLandHasRealTargetsInItsZoneAndTheGroundLineIsTheSpot() {
        for (land in 0..5) for (b in IsleBuilding.entries) for (stage in 0..3) {
            val c = at(b, stage, land)
            val key = IslandLayout.key(c, IslandTarget.Building(b))
            assertEquals(IslandMasks.buildingKey(b, stage), key)
            val targets = IslandLayout.targets(c, IslandTarget.Building(b))
            assertTrue("$b $stage land $land has no spot", targets.isNotEmpty())
            for (p in targets) {
                val ground = IslandGround.ground(key, land, p); val sea = IslandGround.sea(key, land, p)
                when {
                    b != IsleBuilding.HARBOUR -> { assertTrue("$b $p", ground.all { IslandGround.meadow(it[0], it[1], land) }); assertTrue(sea.isEmpty()) }
                    stage == 0 -> { assertTrue("rowboat $p", ground.all { IslandGround.zone(it) == 'B' }); assertTrue(sea.isEmpty()) }
                    else -> {
                        assertTrue("pier $stage root $p", ground.all { IslandGround.zone(it) == 'B' })
                        assertTrue("pier $stage head $p", sea.isNotEmpty() && sea.all { IslandGround.zone(it) == 'W' })
                        assertEquals('W', IslandGround.zone(IslandGround.berthCell(p, stage)))
                    }
                }
            }
            // the spot is the front row of the ground (a pier's head lies in front of it): drawing sorted by the spot is
            // sorting by the ground line
            val s = IslandGround.shape(key, land)
            assertEquals("$key front row", if (s.sea != null) -1 else 0, s.ground[3])
        }
        for (land in 0..5) for (item in decorItems)
            assertTrue("$item land $land", IslandLayout.fitSpots(v6(land), IslandTarget.Decor(item, 1)).all { p ->
                IslandGround.ground(IslandMasks.decorKey(item), land, p).all { IslandGround.meadow(it[0], it[1], land) } })
    }

    @Test fun theBeachIsOnlyForTheHarbourAndTheHarbourOnlyThere() {
        val beach = (0..20).flatMap { j -> (0..20).map { i -> listOf(i, j) } }.filter { IslandGround.zone(it) == 'B' }
        assertTrue(beach.isNotEmpty())
        val house = at(IsleBuilding.HOUSE, 1, 5)
        for (p in beach) assertFalse("house on the beach $p", IslandLayout.valid(house, IsleBuilding.HOUSE, p))
        assertEquals("Am Strand liegt nur der Hafen – Gebäude stehen auf der Wiese",
            IslandGround.landProblem("building:HOUSE:0", 5, beach.first { IslandGround.ground("building:HOUSE:0", 5, it).all { c -> IslandGround.zone(c) == 'B' } }, false))
        // a rowboat on the meadow is refused with the beach reason; a pier needs open water in front
        val boat = at(IsleBuilding.HARBOUR, 0, 5)
        assertEquals("Das Ruderboot liegt am Strand – nur auf dem Sand der Bucht", IslandLayout.problem(boat, harbour, listOf(10, 10)))
        for (stage in 1..3) for (p in IslandLayout.targets(at(IsleBuilding.HARBOUR, stage, 5), harbour))
            assertNull(IslandGround.landProblem(IslandMasks.buildingKey(IsleBuilding.HARBOUR, stage), 5, p, true))
        assertEquals("Der Steg wächst vom Strand ins Wasser – sein Fuß gehört auf den Strand",
            IslandLayout.problem(at(IsleBuilding.HARBOUR, 2, 5), harbour, listOf(10, 10)))
    }

    @Test fun roofsMayCoverAnotherPieceButNoGroundCellIsShared() {
        val land = 5; val house = listOf(10, 10)
        val houseGround = IslandGround.ground("building:HOUSE:3", land, house)
        val houseMask = IslandMasks.cells("building:HOUSE:3", land, house)
        val free = IslandGround.spots(land, false).filter { p ->
            IslandGround.landProblem("building:POST:3", land, p, false) == null && IslandGround.ground("building:POST:3", land, p).none { it in houseGround }
        }
        // a spot right beside the house whose painted picture (rule 5 mask) overlaps the house's: under rule 6 allowed
        val roof = free.first { p -> IslandMasks.cells("building:POST:3", land, p).any { it in houseMask } }
        val c = v6(land, buildings = mapOf("HOUSE" to 3, "POST" to 3), positions = mapOf("HOUSE" to house, "POST" to roof))
        assertTrue(IslandLayout.overlaps(c).isEmpty())
        assertTrue(IslandLayout.valid(c, IsleBuilding.POST, roof))
        assertTrue(roof in IslandLayout.targets(c, IslandTarget.Building(IsleBuilding.POST)))
        // the same layout under rule 5 is an overlap of the pictures (kept exactly for those servers)
        assertFalse(IslandLayout.overlaps(c.copy(gridVersion = PlacementRule.MASKS)).isEmpty())
        // one shared ground cell: refused, with the red reason
        val shared = IslandGround.spots(land, false).first { p ->
            IslandGround.landProblem("building:POST:3", land, p, false) == null && IslandGround.ground("building:POST:3", land, p).any { it in houseGround }
        }
        assertFalse(IslandLayout.valid(c, IsleBuilding.POST, shared))
        assertEquals("Belegt – rot markierte Fläche", IslandLayout.problem(c, IslandTarget.Building(IsleBuilding.POST), shared))
        assertTrue(IslandLayout.targets(c, IslandTarget.Building(IsleBuilding.POST)).all { p ->
            IslandGround.ground("building:POST:3", land, p).none { it in houseGround } })
        // the moved piece never blocks itself: its own spot stays a target
        assertTrue(IslandLayout.valid(c, IsleBuilding.HOUSE, house))
    }

    @Test fun anOldLayoutWithSharedGroundIsListedKeptAndRepairable() {
        val land = 5; val house = listOf(10, 10)
        val houseGround = IslandGround.ground("building:HOUSE:3", land, house)
        val shared = IslandGround.spots(land, false).first { p -> p != house && IslandGround.ground("building:POST:2", land, p).any { it in houseGround } }
        val c = v6(land, buildings = mapOf("HOUSE" to 3, "POST" to 2), positions = mapOf("HOUSE" to house, "POST" to shared))
        assertEquals(listOf(IslandTarget.Building(IsleBuilding.HOUSE) to IslandTarget.Building(IsleBuilding.POST)), IslandLayout.overlaps(c))
        assertEquals(shared, c.positions["POST"])
        // the old spot is not offered again; real targets exist and are free
        val targets = IslandLayout.targets(c, IslandTarget.Building(IsleBuilding.POST))
        assertFalse(shared in targets)
        assertTrue(targets.isNotEmpty() && targets.all { p -> IslandGround.ground("building:POST:2", land, p).none { it in houseGround } })
        // an old harbour inland (saved under rule 5) is not moved on its own, its draft starts on a real harbour spot
        val inland = v6(land, buildings = mapOf("HARBOUR" to 2), positions = mapOf("HARBOUR" to listOf(10, 10)))
        assertEquals(listOf(10, 10), inland.positions["HARBOUR"])
        assertFalse(IslandLayout.valid(inland, IsleBuilding.HARBOUR, listOf(10, 10)))
        val start = IslandLayout.startCell(inland, IsleBuilding.HARBOUR, viewCentre = null)
        assertTrue("$start", IslandLayout.valid(inland, IsleBuilding.HARBOUR, start))
    }

    @Test fun anUpgradeWhoseNextGroundDoesNotFitIsSaidBeforePayingAndStartsOnTheNextStagesSpot() {
        val land = 5
        // the rowboat on the beach: its pier (stage 1) needs its head in the water; where the boat lies it does not fit
        val boatSpot = IslandGround.harbourSpots(0, land).first { IslandGround.landProblem("building:HARBOUR:1", land, it, true) != null }
        val c = v6(land, starters = setOf("HARBOUR"), positions = mapOf("HARBOUR" to boatSpot))
        assertTrue(IslandLayout.valid(c, IsleBuilding.HARBOUR, boatSpot))
        val why = IslandLayout.upgradeProblem(c, IsleBuilding.HARBOUR)
        assertNotNull(why); assertTrue(why!!, why.contains("zuerst verschieben"))
        val next = IslandLayout.upgraded(c, IsleBuilding.HARBOUR)
        assertEquals(1, next.level(IsleBuilding.HARBOUR))
        val start = IslandLayout.upgradeStart(c, IsleBuilding.HARBOUR)
        assertTrue("$start", IslandLayout.valid(next, IsleBuilding.HARBOUR, start))
        assertTrue(IslandGround.ground("building:HARBOUR:1", land, start).all { IslandGround.zone(it) == 'B' })
        // the upgrade draft sees the island with the next stage; the stored island is unchanged until the server says yes
        val h = ApiClient.HomeIsland(1, emptyMap(), 0, emptyList(), construction = c)
        val draft = IslandDraft(harbour, start, upgrade = true)
        assertEquals(1, IslandLayout.editing(h, draft).construction.level(IsleBuilding.HARBOUR))
        assertEquals(0, h.construction.level(IsleBuilding.HARBOUR))
        assertNull(IslandLayout.reason(h, draft))
        // a building whose next ground fits where it stands: nothing to say; fully built: nothing either
        assertNull(IslandLayout.upgradeProblem(v6(land, buildings = mapOf("HOUSE" to 1), positions = mapOf("HOUSE" to listOf(10, 9))), IsleBuilding.HOUSE))
        assertNull(IslandLayout.upgradeProblem(v6(land, buildings = mapOf("HOUSE" to 3)), IsleBuilding.HOUSE))
    }

    @Test fun unknownRulesFailClosedAndOlderRulesKeepTheirOwnGeometry() {
        for (v in listOf(9, 99)) {
            val c = at(IsleBuilding.HOUSE, 1, 5).copy(gridVersion = v)
            assertTrue(IslandLayout.targets(c, IslandTarget.Building(IsleBuilding.HOUSE)).isEmpty())
            assertNotNull(PlacementRule.problem(v))
        }
        for (v in 2..5) assertTrue(PlacementRule.known(v))
        // under rules 2..5 the occupied cells are the picture's mask, under 6 the ground rectangle
        val c = at(IsleBuilding.HOUSE, 3, 5)
        val p = listOf(10, 10)
        for (v in listOf(PlacementRule.CIRCLES, PlacementRule.DIAMONDS, PlacementRule.COMPACT, PlacementRule.MASKS))
            assertEquals("v$v", IslandMasks.cells("building:HOUSE:3", 5, p), IslandLayout.cellsAt(c.copy(gridVersion = v), IslandTarget.Building(IsleBuilding.HOUSE), p))
        assertEquals(IslandGround.ground("building:HOUSE:3", 5, p), IslandLayout.cellsAt(c, IslandTarget.Building(IsleBuilding.HOUSE), p))
    }
}
