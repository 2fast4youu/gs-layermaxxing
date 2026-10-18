package at.gregor.layermaxxing

import androidx.compose.ui.unit.dp
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 7.9.10: ONE occupancy rule for preview, ✓ and server. A piece occupies the grid cells of its footprint
 * (|di| + |dj| <= rings, pinned in server/app/island_grid.json like the grid itself); two pieces never share
 * a cell and there is no other clearance. Only the harbour has the extra coast rule.
 */
class IslandPlacementTest {
    private val json by lazy {
        JSONObject(listOf("../../server/app/island_grid.json", "server/app/island_grid.json", "../server/app/island_grid.json")
            .map(::File).first { it.exists() }.readText())
    }
    private fun cellsOf(key: String, land: Int): Set<List<Int>> = json.getJSONObject(key).getJSONArray("$land").let { a ->
        (0 until a.length()).map { a.getJSONArray(it).let { c -> listOf(c.getInt(0), c.getInt(1)) } }.toSet()
    }
    private fun rings(key: String) = json.getJSONObject("footprints").getJSONArray(key).let { a -> (0 until a.length()).map { a.getInt(it) } }
    private fun empty(land: Int) = ApiClient.IslandConstruction(land = land)
    private fun l1(p: List<Int>, q: List<Int>) = kotlin.math.abs(p[0] - q[0]) + kotlin.math.abs(p[1] - q[1])

    @Test fun coastCellsAndFootprintsAreTheServerFixture() {
        for (land in 0..5) assertEquals("coast $land", cellsOf("coast", land), IslandGrid.coastCells(land).toSet())
        assertEquals(rings("big"), (0..5).map { IslandFootprint.BIG[it] })
        assertEquals(rings("starter"), (0..5).map { IslandFootprint.STARTER[it] })
        assertEquals(rings("decor"), (0..5).map { IslandFootprint.DECOR[it] })
        assertEquals(listOf(1, 5, 13), (0..2).map { IslandFootprint.cells(listOf(10, 10), it).size })
        // the spot (the sprite's foot) is the front tip, the diamond lies behind it — the server's footprint_cells
        assertEquals(setOf(listOf(10, 10), listOf(9, 9), listOf(10, 9), listOf(11, 9), listOf(10, 8)), IslandFootprint.cells(listOf(10, 10), 1))
    }

    @Test fun harbourStandsOnlyOnCoastCellsEveryOtherBuildingOnEveryGridCell() {
        for (land in 0..5) {
            val cells = cellsOf("cells", land); val coast = cellsOf("coast", land)
            for (i in 0..20) for (j in 0..20) {
                val p = listOf(i, j)
                assertEquals("harbour $p land $land", p in coast, IslandLayout.valid(empty(land), IsleBuilding.HARBOUR, p))
                assertEquals("post $p land $land", p in cells, IslandLayout.valid(empty(land), IsleBuilding.POST, p))
            }
        }
        val h = ApiClient.HomeIsland(1, emptyMap(), 0, emptyList(), construction = empty(5))
        val harbour = IslandTarget.Building(IsleBuilding.HARBOUR)
        assertEquals("Hafen nur an der Küste", IslandLayout.reason(h, IslandDraft(harbour, listOf(10, 10))))
        assertEquals("Außerhalb der Insel", IslandLayout.reason(h, IslandDraft(harbour, listOf(1, 10))))
        assertNull(IslandLayout.reason(h, IslandDraft(harbour, listOf(10, 15))))
    }

    @Test fun everyCellWhoseFootprintIsFreeIsUsableNoHiddenGap() {
        for (land in 0..5) {
            val house = listOf(10, 10)
            val c = ApiClient.IslandConstruction(land = land, buildings = mapOf("HOUSE" to 1), positions = mapOf("HOUSE" to house))
            val taken = IslandFootprint.cells(house, IslandFootprint.BIG[land])
            for (p in IslandGrid.buildableCells(land)) {
                val free = IslandFootprint.cells(p, IslandFootprint.DECOR[land]).none { it in taken }
                assertEquals("decor $p land $land", free, IslandLayout.validDecor(c, p))
            }
            // Right beside the footprint is free: touching (a cell next to it), not overlapping.
            val touching = IslandGrid.buildableCells(land).filter { p ->
                val mine = IslandFootprint.cells(p, IslandFootprint.DECOR[land])
                mine.none { it in taken } && mine.any { (x, y) -> listOf(listOf(x + 1, y), listOf(x - 1, y), listOf(x, y + 1), listOf(x, y - 1)).any { it in taken } }
            }
            assertTrue("land $land", touching.isNotEmpty() && touching.all { IslandLayout.validDecor(c, it) })
            assertTrue("land $land", taken.filter { IslandGrid.buildable(it, land) }.none { IslandLayout.validDecor(c, it) })
        }
    }

    @Test fun theMovedPiecesOwnOldSpotNeverBlocksForeignOverlapAlways() {
        val c = ApiClient.IslandConstruction(land = 5, buildings = mapOf("POST" to 2, "HOUSE" to 1),
            positions = mapOf("POST" to listOf(10, 10), "HOUSE" to listOf(10, 6)), decor = listOf(ApiClient.IslandDecor(100, "bench", listOf(14, 10))))
        // one step: overlaps only its own old footprint
        assertTrue(IslandLayout.valid(c, IsleBuilding.POST, listOf(10, 11)))
        assertTrue(IslandLayout.validDecor(c, listOf(14, 11), ignoreId = 100))
        // onto the house or the bench: refused, with the reason the hint shows
        assertFalse(IslandLayout.valid(c, IsleBuilding.POST, listOf(10, 8)))
        assertFalse(IslandLayout.valid(c, IsleBuilding.POST, listOf(14, 11)))
        val h = ApiClient.HomeIsland(1, emptyMap(), 0, emptyList(), construction = c)
        assertEquals("Belegt – rot markierte Fläche", IslandLayout.reason(h, IslandDraft(IslandTarget.Building(IsleBuilding.POST), listOf(10, 8))))
    }

    @Test fun obstaclesAreEveryOtherPieceWithItsFootprintNeverTheHeldOne() {
        val c = ApiClient.IslandConstruction(land = 5, buildings = IsleBuilding.entries.associate { it.name to 2 },
            decor = listOf(ApiClient.IslandDecor(100, "bench", listOf(14, 12)), ApiClient.IslandDecor(201, "place_hut", listOf(10, 10), name = "Hütte")))
        val held = IslandTarget.Building(IsleBuilding.POST)
        val obstacles = IslandLayout.obstacles(c, held)
        assertEquals(IsleBuilding.entries.size - 1 + 2, obstacles.size)
        assertFalse(obstacles.any { it.target == held })
        val hut = obstacles.single { it.target == IslandTarget.Decor("place_hut", 201) }
        assertEquals(IslandFootprint.cells(listOf(10, 10), IslandFootprint.BIG[5]), hut.cells)
        // a held decoration is not its own obstacle either
        assertFalse(IslandLayout.obstacles(c, IslandTarget.Decor("bench", 100)).any { it.target == IslandTarget.Decor("bench", 100) })
        // the held piece's footprint: a new building is its stage 0, a standing one keeps its stage
        assertEquals(IslandFootprint.BIG[5], IslandLayout.rings(c, held))
        assertEquals(IslandFootprint.STARTER[0], IslandLayout.rings(empty(0), held))
    }

    /** The harbour's snap: every finger (inland, shore, water) gives a coast cell; every other piece keeps the land snap. */
    @Test fun harbourSnapGivesACoastCellForEveryFingerOtherPiecesTheLand() {
        for (land in 0..5) {
            val coast = IslandGrid.coastCells(land).toSet()
            for (a in 0..40) for (b in 0..40) {
                val fx = a / 40f; val fy = b / 40f
                assertTrue("finger $fx,$fy land $land", IslandGrid.snapCell(fx, fy, land, coastOnly = true) in coast)
                assertEquals(IslandGrid.nearestLandCell(fx, fy, land), IslandGrid.snapCell(fx, fy, land, coastOnly = false))
            }
            // A finger right on a coast cell keeps it; a finger on an inland cell goes to the rim, never stays inland.
            coast.forEach { c -> IslandGrid.project(c, land).let { (x, y) -> assertEquals(c, IslandGrid.snapCell(x, y, land, true)) } }
            IslandGrid.buildableCells(land).filter { it !in coast }.forEach { c ->
                IslandGrid.project(c, land).let { (x, y) -> assertTrue("$c land $land", IslandGrid.snapCell(x, y, land, true) in coast) }
            }
        }
    }

    /** An old inland harbour starts its first lift on the coast; only the local draft moves, the inland spot stays refused. */
    @Test fun legacyInlandHarbourStartsOnTheCoastAndATakenCoastCellIsNotSkipped() {
        val inland = listOf(10, 10)
        val c = ApiClient.IslandConstruction(land = 5, buildings = mapOf("HARBOUR" to 2), positions = mapOf("HARBOUR" to inland))
        assertFalse(IslandGrid.coast(inland, 5))
        val start = IslandLayout.startCell(c, IsleBuilding.HARBOUR, viewCentre = null)
        assertTrue(IslandGrid.coast(start, 5))
        assertEquals(IslandGrid.coastCellNear(inland, 5), start)
        assertTrue(IslandLayout.valid(c, IsleBuilding.HARBOUR, start))
        // nothing is saved by lifting; a manipulated inland spot is still refused by the model (and the server)
        assertEquals(inland, c.positions["HARBOUR"])
        assertFalse(IslandLayout.valid(c, IsleBuilding.HARBOUR, inland))
        // the nearest coast cell is taken: the draft stays there (red, ✓ disabled) instead of jumping to another cell
        val taken = c.copy(decor = listOf(ApiClient.IslandDecor(100, "flowers", start)))
        assertEquals(start, IslandLayout.startCell(taken, IsleBuilding.HARBOUR, viewCentre = null))
        assertFalse(IslandLayout.valid(taken, IsleBuilding.HARBOUR, start))
        // every other building keeps its stored spot
        val post = ApiClient.IslandConstruction(land = 5, buildings = mapOf("POST" to 1), positions = mapOf("POST" to inland))
        assertEquals(inland, IslandLayout.startCell(post, IsleBuilding.POST, viewCentre = null))
    }

    /**
     * 7.9.9's ring search stopped after 10 rings: from the west coast [3,9] the only free coast cell [17,9] (14 cells
     * away) was never found, the new harbour started on a red cell. Now the whole grid is searched.
     */
    @Test fun freeCellSearchesTheWholeIslandAndNeverPicksATakenSpot() {
        val target = listOf(17, 9); val from = listOf(3, 9)
        assertTrue(kotlin.math.abs(target[0] - from[0]) > 10)
        val keep = IslandFootprint.cells(target, IslandFootprint.STARTER[5])
        val others = IslandGrid.coastCells(5).filter { it !in keep }
        val c = ApiClient.IslandConstruction(land = 5, decor = others.mapIndexed { n, p -> ApiClient.IslandDecor(100 + n, "flowers", p) })
        assertEquals(listOf(target), IslandGrid.coastCells(5).filter { IslandLayout.valid(c, IsleBuilding.HARBOUR, it) })
        assertEquals(target, IslandLayout.freeCell(from) { IslandLayout.valid(c, IsleBuilding.HARBOUR, it) })
        assertEquals(target, IslandLayout.startCell(c, IsleBuilding.HARBOUR, viewCentre = from))
        // the last free cell taken too: the start stays where it was (red, refused), never a taken spot "found"
        val full = c.copy(decor = c.decor + ApiClient.IslandDecor(999, "flowers", target))
        assertEquals(from, IslandLayout.freeCell(from) { IslandLayout.valid(full, IsleBuilding.HARBOUR, it) })
        val start = IslandLayout.startCell(full, IsleBuilding.HARBOUR, viewCentre = from)
        assertTrue(IslandGrid.coast(start, 5))
        assertFalse(IslandLayout.valid(full, IsleBuilding.HARBOUR, start))
    }

    /** The upgrade comparison's painted stage: >= 160 dp art on a narrow phone, bounded on a wide one. */
    @Test fun upgradePreviewArtIsLargeAndFollowsTheWidth() {
        for (w in 100..480 step 10) {
            val h = UpgradePreview.height(w.dp)
            assertTrue("$w dp", h * UpgradePreview.ART >= 160.dp && h <= UpgradePreview.MAX)
        }
        assertTrue(UpgradePreview.height(170.dp) > UpgradePreview.height(150.dp))
    }

    /** No oversized padding: the footprint is about as wide as the drawn sprite, on every land and stage. */
    @Test fun footprintWidthFollowsTheSprite() {
        for (land in 0..5) for (tier in 0..3) {
            val c = if (tier == 0) ApiClient.IslandConstruction(land = land, starters = IsleBuilding.entries.map { it.name }.toSet())
                else ApiClient.IslandConstruction(land = land, buildings = IsleBuilding.entries.associate { it.name to tier })
            IslandPlans.placedBuildings(0, c).forEach { (b, p) ->
                val print = (2 * IslandLayout.buildingRings(c, b) + 1) * IslandGrid.step(land)
                assertTrue("$b tier $tier land $land: ${print / p.size}", print / p.size in .6f..1.7f)
            }
        }
    }
}
