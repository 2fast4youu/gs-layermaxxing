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
 * 7.9.12 grid_version 5: ONE geometry for renderer, drawn area, targets, green/✓ and server.
 *  - the app reads exactly the server's server/app/geometry/island_masks.json (packaged as a resource, not a copy),
 *  - every building is drawn with the sprite, accent, flag and size the mask was measured with,
 *  - the mask hugs the painted body (measured independently in SpriteBounds): no invisible padding, nothing missing,
 *  - an upgrade whose bigger picture does not fit is said before paying; old overlaps are listed, never moved.
 * The pixel proof on the decoded sprites (densest neighbours never share a pixel) is IslandMasksPixelTest.
 */
class IslandMasksTest {
    private val serverFile by lazy {
        listOf("../../server/app/geometry/island_masks.json", "server/app/geometry/island_masks.json", "../server/app/geometry/island_masks.json")
            .map(::File).first { it.exists() }
    }
    private val json by lazy { JSONObject(serverFile.readText()) }
    private val grid by lazy {
        JSONObject(listOf("../../server/app/island_grid.json", "server/app/island_grid.json", "../server/app/island_grid.json")
            .map(::File).first { it.exists() }.readText())
    }
    private fun drawable(name: String): Int = R.drawable::class.java.getField(name).getInt(null)

    /** 7.9.13: rule 6 (IslandGround) is current; these are the rule-5 cases, pinned to version 5 and kept as they were. */
    @Test fun theAppReadsTheServersMaskFileOfRule5() {
        assertEquals(IslandMasks.parse(serverFile.readText()), IslandMasks.parse(
            IslandMasks::class.java.classLoader!!.getResourceAsStream(IslandMasks.RESOURCE)!!.use { it.readBytes().toString(Charsets.UTF_8) }))
        assertEquals(5, IslandMasks.version())
        assertEquals(PlacementRule.GROUND, PlacementRule.CURRENT)
        // island_grid.json pins the build grid and the rules up to 5; version 6's contract is geometry/island_ground.json
        assertEquals(grid.getInt("version"), PlacementRule.MASKS)
        assertEquals("geometry/island_masks.json", grid.getString("masks"))
        for (b in IsleBuilding.entries) for (stage in 0..3) for (land in 0..5) assertNotNull(IslandMasks.building(b, stage, land))
        for (item in IsleDecor.labels.keys + SpecialDecor.items.map { it.key }) assertEquals("decor:$item", IslandMasks.decorKey(item))
        assertEquals("decor:?", IslandMasks.decorKey("gnome_legacy"))
        assertEquals("decor:?special", IslandMasks.decorKey("place_future"))
    }

    @Test fun everyBuildingIsDrawnWithTheSpriteAccentFlagAndSizeItsMaskWasMeasuredWith() {
        val pieces = json.getJSONObject("pieces")
        for (b in IsleBuilding.entries) for (stage in 0..3) {
            val entry = pieces.getJSONObject(IslandMasks.buildingKey(b, stage))
            val s = IslandStages.of(b, stage)
            assertEquals("$b $stage sprite", drawable(entry.getString("sprite")), s.res)
            assertEquals("$b $stage accent", entry.optString("accent").takeIf { !entry.isNull("accent") }?.let(::drawable), s.accent)
            assertEquals("$b $stage flag", stage == 3, entry.getBoolean("flag"))
            for (land in 0..5) {
                val c = if (stage == 0) ApiClient.IslandConstruction(gridVersion = PlacementRule.MASKS, land = land, starters = setOf(b.name))
                    else ApiClient.IslandConstruction(gridVersion = PlacementRule.MASKS, land = land, buildings = mapOf(b.name to stage))
                val piece = IslandPlans.placedBuildings(0, c).getValue(b)
                assertEquals("$b $stage land $land", entry.getJSONArray("lands").getJSONObject(land).getDouble("size").toFloat(), piece.size, 1e-6f)
                assertEquals(s.res, piece.res)
            }
        }
        // decoration: the same sizes on the island as in the masks
        for (land in 0..5) {
            val c = ApiClient.IslandConstruction(gridVersion = PlacementRule.MASKS, land = land, decor = (IsleDecor.labels.keys + "place_hut" + "gnome_legacy").mapIndexed { i, it -> ApiClient.IslandDecor(100 + i, it, listOf(10, 10)) })
            decorPieces(emptyMap(), ApiClient.HomeIsland(1, emptyMap(), 0, emptyList(), construction = c), IslandPlans.constructionScale(land)).forEach { (d, p) ->
                assertEquals("${d.item} land $land", IslandMasks.decor(d.item, land).size, p.size, 1e-6f)
            }
        }
    }

    /** Nothing missing: the mask's tiles span the painted body measured independently (SpriteBounds, 7.9.7). */
    @Test fun theMaskHugsThePaintedBodyOfEverySprite() {
        for (b in IsleBuilding.entries) for (stage in 0..3) for (land in 0..5) {
            val shape = IslandMasks.building(b, stage, land)
            val res = IslandStages.of(b, stage).res
            if (stage == 0 && IslandStages.of(b, 0).accent != null) continue // accent overlays widen the box; pixel test covers them
            val bounds = SpriteBounds.of(res)
            val step = IslandPlans.constructionScale(land) / IslandGrid.STEPS
            // the sprite box (width fractions; heights in width units too, y × 1/ASPECT per lattice row)
            val left = -shape.size / 2 + bounds[0] * shape.size; val right = -shape.size / 2 + bounds[2] * shape.size
            val top = -shape.size + bounds[1] * shape.size; val bottom = -shape.size + bounds[3] * shape.size
            val rowH = step / IslandPlans.ASPECT
            val xs = shape.mask.map { it[0] }; val ys = shape.mask.map { it[1] }
            val mL = (xs.min() - .5f) * step; val mR = (xs.max() + .5f) * step
            val mT = (ys.min() - .5f) * rowH; val mB = (ys.max() + .5f) * rowH
            val msg = "$b $stage land $land"
            // covers the body (3 % trimmed alpha bounds, so a small tolerance) ...
            val tol = .04f * shape.size
            assertTrue("$msg left", mL <= left + tol); assertTrue("$msg right", mR >= right - tol)
            assertTrue("$msg top", mT <= top + tol)
            // (no invisible clearance: every tile of the mask holds painted pixels, IslandMasksPixelTest)
            assertTrue("$msg bottom", mB >= bottom - rowH)
        }
    }

    @Test fun smallStartersAndBigFinalBuildingsAreNotTreatedAlike() {
        for (b in IsleBuilding.entries) {
            val starter = IslandMasks.building(b, 0, 5).mask.size
            val final = IslandMasks.building(b, 3, 5).mask.size
            assertTrue("$b $starter vs $final", starter <= final)
            assertTrue("$b final", final >= 6)
        }
        // never one cell for a wide picture (7.9.11's error) on any land
        for (b in IsleBuilding.entries) for (stage in 0..3) for (land in 0..5)
            assertTrue("$b $stage $land", IslandMasks.building(b, stage, land).mask.size >= 4)
    }

    @Test fun anUpgradeThatDoesNotFitIsSaidBeforePayingAndTheMoveFirstIsOffered() {
        // the letter tree on the western rim of land 0: the tree fits, the stage-1 box would stick out of the land
        val rim = IslandGrid.buildableCells(0).first {
            IslandMasks.landProblem("building:POST:0", 0, it, false) == null && IslandMasks.landProblem("building:POST:1", 0, it, false) != null
        }
        val c = ApiClient.IslandConstruction(gridVersion = PlacementRule.MASKS, land = 0, starters = setOf("POST"), positions = mapOf("POST" to rim))
        assertTrue(IslandLayout.valid(c.copy(positions = emptyMap()), IsleBuilding.POST, rim))
        val why = IslandLayout.upgradeProblem(c, IsleBuilding.POST)
        assertNotNull(why); assertTrue(why!!.contains("zuerst verschieben") && why.contains("Postkasten"))
        // moved to the middle, it fits
        assertNull(IslandLayout.upgradeProblem(c.copy(positions = mapOf("POST" to listOf(10, 11))), IsleBuilding.POST))
        // a neighbour inside the bigger picture blocks the upgrade too
        val lantern = IslandGrid.buildableCells(0).first { p ->
            val l = IslandMasks.cells("decor:lantern", 0, p)
            l.none { it in IslandMasks.cells("building:POST:0", 0, listOf(10, 11)) } && l.any { it in IslandMasks.cells("building:POST:1", 0, listOf(10, 11)) } &&
                IslandMasks.landProblem("decor:lantern", 0, p, false) == null
        }
        val near = ApiClient.IslandConstruction(gridVersion = PlacementRule.MASKS, land = 0, starters = setOf("POST"), positions = mapOf("POST" to listOf(10, 11)),
            decor = listOf(ApiClient.IslandDecor(100, "lantern", lantern)))
        assertTrue(IslandLayout.overlaps(near).isEmpty())
        assertNotNull(IslandLayout.upgradeProblem(near, IsleBuilding.POST))
        // fully built: nothing to say
        assertNull(IslandLayout.upgradeProblem(ApiClient.IslandConstruction(gridVersion = PlacementRule.MASKS, land = 5, buildings = mapOf("POST" to 3)), IsleBuilding.POST))
    }

    @Test fun aVersion4LayoutWithOverlapsIsListedNotMovedAndOffersOnlyRealSpots() {
        val old = ApiClient.IslandConstruction(land = 5, gridVersion = PlacementRule.COMPACT, buildings = mapOf("HOUSE" to 3, "POST" to 2),
            positions = mapOf("HOUSE" to listOf(10, 10), "POST" to listOf(11, 10)))
        // the historic rule accepted it (one cell each) ...
        assertTrue(IslandLayout.ruleValid(old, IsleBuilding.POST, listOf(11, 10)))
        // ... the pictures overlap: listed, kept where they are
        assertEquals(listOf(IslandTarget.Building(IsleBuilding.HOUSE) to IslandTarget.Building(IsleBuilding.POST)), IslandLayout.overlaps(old))
        assertEquals(listOf(11, 10), old.positions["POST"])
        for (v in listOf(PlacementRule.COMPACT, PlacementRule.MASKS)) {
            val c = old.copy(gridVersion = v)
            // its own (overlapping) spot is not offered again, every offered spot is really free
            val targets = IslandLayout.targets(c, IslandTarget.Building(IsleBuilding.POST))
            assertFalse(listOf(11, 10) in targets)
            val house = IslandLayout.cellsAt(c, IslandTarget.Building(IsleBuilding.HOUSE), listOf(10, 10))
            assertTrue(targets.isNotEmpty() && targets.all { p -> IslandLayout.cellsAt(c, IslandTarget.Building(IsleBuilding.POST), p).none { it in house } })
        }
    }

    @Test fun aDraggedPieceSnapsOnlyToSpotsWhereItsPictureFitsTheLand() {
        for (land in 0..5) for (b in IsleBuilding.entries) for (stage in listOf(0, 3)) {
            val c = if (stage == 0) ApiClient.IslandConstruction(gridVersion = PlacementRule.MASKS, land = land, starters = setOf(b.name)) else ApiClient.IslandConstruction(gridVersion = PlacementRule.MASKS, land = land, buildings = mapOf(b.name to stage))
            val t = IslandTarget.Building(b)
            val spots = IslandLayout.fitSpots(c, t)
            assertTrue("$b $stage $land", spots.isNotEmpty())
            spots.forEach { assertNull("$b $stage $land $it", IslandMasks.landProblem(IslandLayout.key(c, t), land, it, b == IsleBuilding.HARBOUR)) }
            if (b == IsleBuilding.HARBOUR) assertTrue(spots.all { IslandGrid.coast(it, land) && !IslandGrid.buildable(it[0], it[1] + 1, land) })
        }
    }

    /** The request's example: a 3×3 harbour, whose anchor alone is free, is not offered there. */
    @Test fun aThreeByThreeHarbourNeedsItsWholePierFree() {
        val land = 5
        val mask = IslandMasks.cells("building:HARBOUR:3", land, listOf(10, 15))
        assertTrue(mask.map { it[0] }.toSet().size >= 3 && mask.map { it[1] }.toSet().size >= 3)
        val behind = mask.filter { it != listOf(10, 15) && it[1] < 15 }.sortedWith(compareBy({ it[1] }, { it[0] })).first()
        val c = ApiClient.IslandConstruction(gridVersion = PlacementRule.MASKS, land = land, buildings = mapOf("HARBOUR" to 3), positions = mapOf("HARBOUR" to listOf(8, 15)),
            decor = listOf(ApiClient.IslandDecor(100, "lantern", behind)))
        assertFalse(listOf(10, 15) in IslandMasks.cells("decor:lantern", land, behind))
        assertFalse(listOf(10, 15) in IslandLayout.targets(c, IslandTarget.Building(IsleBuilding.HARBOUR)))
        assertEquals("Belegt – rot markierte Fläche", IslandLayout.problem(c, IslandTarget.Building(IsleBuilding.HARBOUR), listOf(10, 15)))
        // its own old area never blocks it: one coast spot further on overlaps the old pier and is offered
        val alone = ApiClient.IslandConstruction(gridVersion = PlacementRule.MASKS, land = land, buildings = mapOf("HARBOUR" to 3), positions = mapOf("HARBOUR" to listOf(10, 15)))
        assertTrue(IslandMasks.cells("building:HARBOUR:3", land, listOf(11, 15)).any { it in mask })
        assertTrue(listOf(11, 15) in IslandLayout.targets(alone, IslandTarget.Building(IsleBuilding.HARBOUR)))
    }
}
