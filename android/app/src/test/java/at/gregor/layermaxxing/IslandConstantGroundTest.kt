package at.gregor.layermaxxing

import org.junit.Assert.*
import org.junit.Test

/** Independent oracle: historic stage-0 fixture, never the new generated ground or production fit helpers. */
class IslandConstantGroundTest {
    private fun construction(b: IsleBuilding, land: Int, stage: Int, p: List<Int>) = ApiClient.IslandConstruction(
        gridVersion = 8, land = land, buildings = if (stage == 0) emptyMap() else mapOf(b.name to stage),
        starters = if (stage == 0) setOf(b.name) else emptySet(), positions = mapOf(b.name to p))
    private fun oracle(b: IsleBuilding, land: Int, p: List<Int>) =
        Grid7Contract.rect("building:${b.name}:0", land, p) + Grid7Contract.rect("building:${b.name}:0", land, p, "sea")

    @Test fun everyBuildingLandStageAndCandidateRetainsTheOriginalStageZeroCellsAndFit() {
        for (b in IsleBuilding.entries) for (land in 0..5) for (stage in 0..3) {
            val t = IslandTarget.Building(b)
            val c = construction(b, land, stage, listOf(10, 10))
            for (p in Grid7Contract.lattice) {
                assertEquals("$b/$land/$stage/$p", oracle(b, land, p), IslandLayout.cellsAt(c, t, p))
                val fits = Grid7Contract.fits("building:${b.name}:0", land, p) &&
                    (b != IsleBuilding.LIGHTHOUSE || oracle(b, land, p).any { it in Grid7Contract.edge(land) })
                assertEquals("$b/$land/$stage/$p", fits, IslandLayout.problem(c, t, p) == null)
            }
        }
    }

    @Test fun everyUpgradeKeepsTightNeighboursAndLegacySavedGroundWithoutClearance() {
        for (b in IsleBuilding.entries) for (land in 0..5) {
            val p = listOf(10, 10)
            val occupied = oracle(b, land, p)
            val border = occupied.flatMap { Grid7Contract.neighbours(it) }.toSet() - occupied
            var c = construction(b, land, 0, p).copy(decor = border.mapIndexed { i, cell -> ApiClient.IslandDecor(100+i, "lantern", cell) })
            for (stage in 1..3) {
                assertNull(IslandLayout.upgradeProblem(c, b))
                assertNull(IslandLayout.requestProblem(c, ApiClient.IslandRequest("upgrade", b.name)))
                assertNull(IslandLayout.requestProblem(c, ApiClient.IslandRequest("upgrade", b.name, position = p)))
                c = IslandLayout.upgraded(c, b)
                assertEquals(stage, c.level(b))
                assertEquals(p, c.positions[b.name])
                assertEquals(occupied, IslandLayout.cellsAt(c, IslandTarget.Building(b), p))
                assertFalse(IslandLayout.overlaps(c).any { it.first == IslandTarget.Building(b) || it.second == IslandTarget.Building(b) })
                assertEquals(border, c.decor.map { it.position }.toSet())
            }
        }
    }

    @Test fun sharedResourceIsByteExactAndV7DefaultsAndUnknownRulesRemainSafe() {
        val name = "island_constant_ground.json"
        val bytes = IslandGround::class.java.classLoader!!.getResourceAsStream(name)!!.use { it.readBytes() }
        assertTrue(bytes.contentEquals(Grid7Contract.file(name).readBytes()))
        assertEquals(7, PlacementRule.CURRENT)
        assertEquals(7, ApiClient.IslandConstruction().gridVersion)
        assertTrue(PlacementRule.known(8))
        for (version in listOf(9, 99, PlacementRule.INVALID, PlacementRule.LEGACY)) {
            val c = construction(IsleBuilding.POST, 5, 0, listOf(10,10)).copy(gridVersion = version)
            assertNotNull(PlacementRule.problem(version))
            for (action in listOf("upgrade", "demolish", "expand", "activate")) {
                assertNotNull(IslandLayout.requestProblem(c, ApiClient.IslandRequest(action, "POST")))
            }
            assertTrue(IslandLayout.targets(c, IslandTarget.Building(IsleBuilding.POST)).isEmpty())
        }
        for (land in 0..5) for (stage in 0..3) {
            val c = construction(IsleBuilding.POST, land, stage, listOf(10,10)).copy(gridVersion = 7)
            assertEquals(Grid7Contract.rect("building:POST:$stage", land, listOf(10,10)),
                IslandLayout.cellsAt(c, IslandTarget.Building(IsleBuilding.POST), listOf(10,10)))
        }
    }

    @Test fun harbourSocketAndWaterBerthDoNotChangeWithUpgrade() {
        for (land in 0..5) {
            val p = Grid7Contract.lattice.first { Grid7Contract.fits("building:HARBOUR:0", land, it) }
            val c = construction(IsleBuilding.HARBOUR, land, 0, p)
            val berth = HarbourRoute.berth(c)
            for (stage in 0..3) {
                val next = construction(IsleBuilding.HARBOUR, land, stage, p)
                assertEquals(true, HarbourRoute.atSocket(next))
                assertEquals(berth, HarbourRoute.berth(next))
                assertEquals(IslandGround.harbourSpots(0, land, 8), IslandGround.harbourSpots(stage, land, 8))
            }
        }
    }
}
