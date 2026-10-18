package at.gregor.layermaxxing

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 7.9.7: one build grid for app and server. The cells drawn on the island ARE the set of valid spots for
 * every land stage 0–5 (server fixture server/app/island_grid.json), so nothing can be built in the water
 * or outside the current land any more.
 */
class IslandBuildGridTest {
    private val fixture: Map<Int, Set<List<Int>>> by lazy {
        val file = listOf("../../server/app/island_grid.json", "server/app/island_grid.json", "../server/app/island_grid.json")
            .map(::File).first { it.exists() }
        val cells = JSONObject(file.readText()).getJSONObject("cells")
        (0..5).associateWith { land ->
            cells.getJSONArray("$land").let { a -> (0 until a.length()).map { a.getJSONArray(it).let { c -> listOf(c.getInt(0), c.getInt(1)) } }.toSet() }
        }
    }
    private fun empty(land: Int) = ApiClient.IslandConstruction(land = land)
    private val lattice = (0..20).flatMap { i -> (0..20).map { j -> listOf(i, j) } }

    @Test fun gridCellsAreExactlyTheServerCellsOfEveryLandStage() {
        for (land in 0..5) assertEquals("land $land", fixture.getValue(land), IslandGrid.buildableCells(land).toSet())
        assertTrue(fixture.getValue(5).size > fixture.getValue(0).size)
    }

    @Test fun gridCellsAreTheValidPlacementSetOnAnEmptyIsland() {
        for (land in 0..5) {
            val cells = fixture.getValue(land)
            lattice.forEach { p ->
                // the spot rule itself: every grid cell (7.9.12: [IslandLayout.ruleValid] of the declared rule)
                assertEquals("building $p land $land", p in cells, IslandLayout.ruleValid(empty(land).copy(gridVersion = PlacementRule.COMPACT), IsleBuilding.POST, p))
                assertEquals("decor $p land $land", p in cells, IslandLayout.ruleValidDecor(empty(land).copy(gridVersion = PlacementRule.COMPACT), p))
                assertEquals("special $p land $land", p in cells, IslandLayout.ruleValidDecor(empty(land).copy(gridVersion = PlacementRule.COMPACT), p, special = true))
                // 7.9.12 (rule 5): a spot is offered only on the grid and only where the whole picture's ground is on the land
                val fit = IslandLayout.valid(empty(land), IsleBuilding.POST, p)
                assertEquals("fit $p land $land", p in cells && IslandMasks.landProblem("building:POST:0", land, p, false) == null, fit)
            }
        }
    }

    /** 7.9.6 bug: the client grid was the full painted ellipse (r .95) for every stage, incl. shore and water. */
    @Test fun waterAndCoastSpotsOfTheOldGridAreRefused() {
        for (land in 0..5) {
            for (p in listOf(listOf(2, 9), listOf(18, 9), listOf(10, 17), listOf(1, 10), listOf(10, 19))) {
                assertFalse("$p land $land", IslandLayout.valid(empty(land), IsleBuilding.POST, p))
                assertFalse("$p land $land", IslandLayout.validDecor(empty(land), p))
            }
        }
        // A spot of the big island is not land of the small one.
        val outer = fixture.getValue(5).first { it !in fixture.getValue(0) }
        assertFalse(IslandLayout.valid(empty(0), IsleBuilding.POST, outer))
        assertTrue(IslandLayout.ruleValid(empty(5).copy(gridVersion = PlacementRule.COMPACT), IsleBuilding.POST, outer))
    }

    @Test fun everyFingerIsClampedOntoTheGridOfTheCurrentLand() {
        for (land in 0..5) {
            val cells = fixture.getValue(land)
            for (fx in listOf(0f, .05f, .2f, .5f, .8f, .95f, 1f)) for (fy in listOf(0f, .1f, .5f, .9f, 1f)) {
                assertTrue("finger $fx,$fy land $land", IslandGrid.nearestLandCell(fx, fy, land) in cells)
            }
            lattice.forEach { p -> assertTrue(IslandGrid.clampToLand(p, land) in cells) }
            // On land the finger keeps its own cell.
            cells.forEach { c -> IslandGrid.project(c, land).let { (x, y) -> assertEquals(c, IslandGrid.nearestLandCell(x, y, land)) } }
        }
    }

    @Test fun defaultSpotsLieOnTheGridOfEveryStage() {
        for (land in 0..5) IsleBuilding.entries.forEach { b ->
            assertTrue("${b.name} land $land", IslandLayout.defaultPosition(b) in fixture.getValue(land))
        }
    }
}
