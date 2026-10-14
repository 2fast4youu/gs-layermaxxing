package at.gregor.layermaxxing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** One isometric projection for grid, preview, snapping and saved buildings (7.9.5). */
class IslandGridTest {
    private val aspect = IslandPlans.ASPECT

    /** Screen vector (island box height = 1) between two cells. */
    private fun screen(e: IslandGrid.Edge, land: Int): Pair<Float, Float> =
        (IslandGrid.x(e.i2, land) - IslandGrid.x(e.i1, land)) * aspect to (IslandGrid.y(e.j2, land) - IslandGrid.y(e.j1, land))

    @Test fun gridLinesAreDiagonalNotAxisParallel() {
        for (land in 0..5) assertTrue(IslandGrid.edges(land).size > 150)
        for (land in 0..5) IslandGrid.edges(land).forEach { e ->
            val (dx, dy) = screen(e, land)
            assertTrue("no vertical line", abs(dx) > 1e-4f)
            assertTrue("no horizontal line", abs(dy) > 1e-4f)
            // Every line has the island's diamond slope (about .82 = height/width of one grid diamond).
            assertEquals(IslandGrid.DIAMOND_ASPECT, abs(dy / dx), 1e-3f)
        }
    }

    @Test fun gridLinesRunInBothDiagonalDirections() {
        val down = IslandGrid.edges(5).count { it.j2 > it.j1 }; val up = IslandGrid.edges(5).count { it.j2 < it.j1 }
        assertTrue(down > 100 && up > 100)
    }

    @Test fun gridOnlyCoversLand() {
        for (land in 0..5) IslandGrid.edges(land).forEach { e ->
            assertTrue(IslandGrid.buildable(e.i1, e.j1, land))
            assertTrue(IslandGrid.buildable(e.i2, e.j2, land))
        }
        assertFalse(IslandGrid.edges(5).any { it.i1 == 0 && it.j1 == 0 })
    }

    @Test fun projectionIsTheOneTheIslandRendersWith() {
        for (land in 0..5) {
            val c = ApiClient.IslandConstruction(land = land, buildings = IsleBuilding.entries.associate { it.name to 1 },
                positions = IsleBuilding.entries.mapIndexed { n, b -> b.name to listOf(3 + n * 3, 5 + n * 2) }.toMap())
            val placed = IslandPlans.placedBuildings(0, c)
            IsleBuilding.entries.forEach { b ->
                val cell = c.positions.getValue(b.name)
                val (x, y) = IslandGrid.project(cell, land)
                assertEquals("${b.name} x", placed.getValue(b).x, x, 1e-6f)
                assertEquals("${b.name} y", placed.getValue(b).y, y, 1e-6f)
            }
            val d = ApiClient.IslandDecor(100, "palm", listOf(13, 9))
            val piece = decorPieces(emptyMap(), ApiClient.HomeIsland(1, emptyMap(), 0, emptyList(), construction = c.copy(decor = listOf(d), supported = true)),
                IslandPlans.constructionScale(land)).single().second
            assertEquals(IslandGrid.project(d.position, land).first, piece.x, 1e-6f)
            assertEquals(IslandGrid.project(d.position, land).second, piece.y, 1e-6f)
        }
    }

    @Test fun fingerToCellRoundTripsOnEveryCell() {
        for (land in 0..5) for (i in 0..20) for (j in 0..20) {
            val cell = listOf(i, j)
            val (fx, fy) = IslandGrid.project(cell, land)
            assertEquals("exact finger", cell, IslandGrid.unproject(fx, fy, land))
            // A finger a little off the cell centre still snaps to that cell.
            val h = IslandGrid.step(land) * .45f
            assertEquals(cell, IslandGrid.unproject(fx + h, fy - h, land).let { if (i in 1..19 && j in 1..19) it else cell })
        }
    }

    @Test fun previewSpriteStandsOnTheTouchedCellAndSavesThere() {
        val land = 3
        val c = ApiClient.IslandConstruction(land = land)
        val h = ApiClient.HomeIsland(1, emptyMap(), 0, emptyList(), construction = c)
        val finger = IslandGrid.project(listOf(7, 12), land).let { (x, y) -> x + .004f to y - .003f }
        val cell = IslandGrid.unproject(finger.first, finger.second, land)
        assertEquals(listOf(7, 12), cell)
        val preview = IslandLayout.preview(h, IslandDraft(IslandTarget.Building(IsleBuilding.POST), cell))
        val sprite = IslandPlans.placedBuildings(0, preview.construction).getValue(IsleBuilding.POST)
        val (cx, cy) = IslandGrid.project(cell, land)
        assertEquals(cx, sprite.x, 1e-6f); assertEquals(cy, sprite.y, 1e-6f)
        // the cell the app would send is exactly the cell that was drawn
        assertEquals(cell, preview.construction.positions["POST"])
        // footprint centre = cell centre
        val fp = IslandGrid.footprint(cell, land, 2)
        assertEquals(cx, (fp[1].first + fp[3].first) / 2f, 1e-6f); assertEquals(cy, (fp[0].second + fp[2].second) / 2f, 1e-6f)
        assertEquals(cx, fp[0].first, 1e-6f); assertEquals(cy, fp[1].second, 1e-6f)
    }

    @Test fun footprintCornersAreGridCellsAndItsEdgesLieOnTheMesh() {
        for (land in 0..5) for (r in 1..3) {
            val cell = listOf(10, 10)
            val fp = IslandGrid.footprint(cell, land, r)
            assertEquals(IslandGrid.project(listOf(10, 10 - r), land).second, fp[0].second, 1e-6f)
            assertEquals(IslandGrid.project(listOf(10 + r, 10), land).first, fp[1].first, 1e-6f)
            assertEquals(IslandGrid.project(listOf(10, 10 + r), land).second, fp[2].second, 1e-6f)
            assertEquals(IslandGrid.project(listOf(10 - r, 10), land).first, fp[3].first, 1e-6f)
            // top -> right edge has the mesh slope
            val dx = (fp[1].first - fp[0].first) * aspect; val dy = fp[1].second - fp[0].second
            assertEquals(IslandGrid.DIAMOND_ASPECT, abs(dy / dx), 1e-3f)
        }
    }

    @Test fun footprintGrowsWithTheSpriteAndShrinksWithLand() {
        assertTrue(IslandGrid.radiusFor(.12f, 0) >= IslandGrid.radiusFor(.12f, 5))
        assertTrue(IslandGrid.radiusFor(.14f, 3) >= IslandGrid.radiusFor(.08f, 3))
        for (land in 0..5) assertTrue(IslandGrid.radiusFor(.01f, land) >= 1)
    }

    @Test fun bubblesFlipAboveWhenThereIsNoRoomBelow() {
        assertFalse(PlacementBubbles.flipAbove(.60f, .10f))
        assertTrue(PlacementBubbles.flipAbove(.92f, .10f))
    }

    @Test fun cameraScrollsOnlyAtTheScreenEdgeAndTowardsIt() {
        val view = Size(400f, 600f)
        assertEquals(Offset.Zero, EdgeScroll.velocity(Offset.Zero, view, 48f, 600f))
        assertEquals(Offset.Zero, EdgeScroll.velocity(Offset(100f, -200f), view, 48f, 600f))
        val right = EdgeScroll.velocity(Offset(199f, 0f), view, 48f, 600f)
        assertTrue("content moves left so the right side comes into view", right.x < 0f && right.y == 0f)
        val top = EdgeScroll.velocity(Offset(0f, -299f), view, 48f, 600f)
        assertTrue(top.y > 0f && top.x == 0f)
        // the closer to the edge the faster
        assertTrue(abs(EdgeScroll.velocity(Offset(199f, 0f), view, 48f, 600f).x) > abs(EdgeScroll.velocity(Offset(170f, 0f), view, 48f, 600f).x))
    }

    @Test fun upgradeLineStatesNextStagePriceAndWhatIsMissing() {
        val n = ApiClient.IslandConstruction(available = 500, ageDays = 21)
        assertEquals("Stufe 2: 60 Punkte", IslandBuild.upgradeLine(n, 1))
        assertEquals("60 P", IslandBuild.upgradeCost(n, 1))
        assertEquals("Stufe 2: 60 Punkte · es fehlen 20", IslandBuild.upgradeLine(n.copy(available = 40), 1))
        assertEquals("Stufe 3: 100 Punkte · ab Tag 7", IslandBuild.upgradeLine(n.copy(ageDays = 3), 2))
        val c = n.copy(mode = "creative")
        assertEquals("Nächste Stufe 2: gratis", IslandBuild.upgradeLine(c, 1)); assertEquals("gratis", IslandBuild.upgradeCost(c, 1))
        assertEquals(null, IslandBuild.upgradeCost(n, 3)); assertTrue(IslandBuild.upgradeLine(n, 3).contains("Meisterbau"))
    }
}
