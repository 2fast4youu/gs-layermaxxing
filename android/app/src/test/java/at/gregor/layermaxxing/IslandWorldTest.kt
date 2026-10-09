package at.gregor.layermaxxing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandWorldTest {
    @Test fun everyReleaseModeGetsABoat() {
        assertEquals(LetterBoat.STEAMER, LetterBoat.forMode("timed"))
        assertEquals(LetterBoat.ROWBOAT, LetterBoat.forMode("random"))
        listOf("manual", "mutual", "presence").forEach { assertEquals(LetterBoat.SAILBOAT, LetterBoat.forMode(it)) }
        assertEquals(LetterBoat.STEAMER, LetterBoat.forMode("unknown"))
    }

    @Test fun islandsStayOnTheMapAndDoNotOverlapTheHarbour() {
        for (n in 1..12) {
            val p = IsleLayout.positions(n)
            assertEquals(n, p.size)
            p.forEach { (x, y) ->
                assertTrue("x=$x", x in .02f..0.98f); assertTrue("y=$y", y in .08f..0.92f)
                val d = Math.hypot((x - .5).toDouble(), (y - .5).toDouble())
                assertTrue("too close to home: $d", d > .2)
            }
            assertEquals(n, p.map { (x, y) -> (x * 100).toInt() to (y * 100).toInt() }.toSet().size)
        }
        assertTrue(IsleLayout.positions(0).isEmpty())
    }

    @Test fun progressFollowsServerThresholds() {
        assertEquals(0f, IsleLayout.progress(null))
        assertEquals(.5f, IsleLayout.progress(ApiClient.IslandInfo(1, 20, 0, 20, 1, 40)), .001f)
        assertEquals(.25f, IsleLayout.progress(ApiClient.IslandInfo(1, 60, 0, 60, 2, 120)), .001f)
        assertEquals(1f, IsleLayout.progress(ApiClient.IslandInfo(1, 400, 0, 400, 4, null)))
    }

    @Test fun levelNamesAndUnknownIconsAreSafe() {
        assertEquals("Neu", Isle.levelName(0)); assertEquals("Beste Freunde", Isle.levelName(9))
        assertEquals(R.drawable.ico_q_star, AppIcons.questIcon("??")); assertEquals(R.drawable.ico_q_hike, AppIcons.questIcon("hike"))
    }

    @Test fun serverFriendsAppearOnMap() {
        val f = listOf(
            ApiClient.UserSummary(2, "Gerfried", "friends", "🦉", "#336699"),
            ApiClient.UserSummary(3, "Fremd", "none", "🙂", "#336699"),
        )
        assertEquals(listOf(2L), IsleLayout.friendsOnMap(f).map { it.id })
    }

    @Test fun friendIslandGrowsWithLevel() {
        val counts = (1..4).map { IslandPlans.friend(it, 7L).pieces.size }
        assertTrue(counts.zipWithNext().all { (a, b) -> b > a })
    }

    @Test fun homeLawnsAndBuildingsStayOnLand() {
        IslandPlans.homeSlots.forEach { (x, y) -> assertTrue(IslandPlans.inside(x, y)) }
        IslandPlans.homeBuildings.values.forEach { assertTrue(IslandPlans.inside(it.x, it.y, radius = .95f)) }
    }

    /** Insel-Ausbau: a fresh island is a small bare sand bank and grows step by step. */
    @Test fun freshIslandIsSmallSandBankAndGrows() {
        val sizes = listOf(0, 25, 60, 100, 160, 240, 999).map { IslandPlans.landScale(it) }
        assertTrue(sizes.first() <= .35f)
        assertEquals(1f, sizes.last())
        assertTrue(sizes.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(0f, IslandPlans.meadow(IslandPlans.landScale(0)))
        assertEquals(1f, IslandPlans.meadow(1f))
        // No trees on the bare sand bank, all of them on the full island.
        val trees = setOf(R.drawable.n_pine, R.drawable.n_tree, R.drawable.n_cypress)
        assertTrue(IslandPlans.home(1L, 0).pieces.none { it.res in trees })
        assertEquals(4, IslandPlans.home(1L, 999).pieces.count { it.res in trees })
    }

    @Test fun everythingStaysOnTheGrowingLand() {
        listOf(0, 25, 60, 100, 160, 240).forEach { score ->
            val s = IslandPlans.landScale(score)
            // Buildings (named pieces) and plots; rim trees stand on the painted coast by design.
            IslandPlans.home(1L, score).pieces.filter { it.name != null }
                .forEach { assertTrue("$score ${it.name}", IslandPlans.inside(it.x, it.y, s, .95f)) }
            LifePlaces.plots.forEach { (px, py) ->
                val (x, y) = IslandPlans.onLand(px, py, s)
                assertTrue("$score plot", IslandPlans.inside(x, y, s, .95f))
            }
        }
    }

    /** Signs/touch targets read placedBuildings: on a small island they sit with the sprites, not in the sea. */
    @Test fun signsFollowBuildingsOntoSmallIsland() {
        val placed = IslandPlans.placedBuildings(0)
        val sprites = IslandPlans.home(1L, 0).pieces.filter { it.name != null }
        placed.values.forEach { p -> assertTrue(sprites.any { it.x == p.x && it.y == p.y }) }
        placed.values.forEach { assertTrue(IslandPlans.inside(it.x, it.y, IslandPlans.landScale(0), .95f)) }
    }

    @Test fun freshIslandHasNoBuildingsOrStandIns() {
        val fresh = IslandPlans.home(1L, 500, ApiClient.IslandConstruction())
        assertTrue("A starter island must really be empty", fresh.pieces.isEmpty())
        assertTrue(IslandPlans.placedBuildings(500, ApiClient.IslandConstruction()).isEmpty())
        val built = IslandPlans.home(1L, 0, ApiClient.IslandConstruction(buildings=mapOf("POST" to 3), land=4))
        assertEquals(1, built.pieces.count { it.name != null })
        assertEquals(3, built.pieces.first { it.name == "Post" }.tier)
    }

    @Test fun firstBuildAndExpansionHaveDistinctPaintedArtwork() {
        IsleBuilding.entries.forEach { building ->
            org.junit.Assert.assertNotEquals(building.name, IslandArtwork.resource(building, 0), IslandArtwork.resource(building, 1))
            org.junit.Assert.assertNotEquals(building.name, IslandArtwork.resource(building, 1), IslandArtwork.resource(building, 2))
        }
    }

    @Test fun normalPreviewRequiresPointsAndTimeButCreativeIsFree() {
        val earned = ApiClient.IslandConstruction(available=100,ageDays=1)
        assertTrue(IslandEvolution.allowed(earned,60,0))
        assertTrue(!IslandEvolution.allowed(earned,60,2))
        assertTrue(!IslandEvolution.allowed(earned,120,0))
        assertTrue(IslandEvolution.allowed(earned.copy(mode="creative",available=0),320,21))
    }
    @Test fun movedBuildingsRenderAtPersistedGridPositionWithoutChangingProgress() {
        val c=ApiClient.IslandConstruction(buildings=mapOf("POST" to 2),land=3,spent=85,positions=mapOf("POST" to listOf(14,11)))
        val piece=IslandPlans.placedBuildings(0,c).getValue(IsleBuilding.POST)
        val expected=IslandPlans.onLand(.7f,.55f,IslandPlans.constructionScale(3))
        assertEquals(expected.first,piece.x,.0001f);assertEquals(expected.second,piece.y,.0001f)
        assertEquals(2,piece.tier);assertEquals(1,IslandPlans.placedBuildings(0,c).size)
        assertEquals(85,c.spent)
    }

    @Test fun placementBlocksSeaBuildingsAndOccupiedDecorOrLifePlaces() {
        val c=ApiClient.IslandConstruction(buildings=mapOf("POST" to 1),positions=mapOf("POST" to listOf(10,11)))
        assertTrue(!IslandLayout.valid(c,IsleBuilding.HOUSE,listOf(0,0)))
        assertTrue(!IslandLayout.valid(c,IsleBuilding.HOUSE,listOf(11,11)))
        assertTrue(IslandLayout.valid(c,IsleBuilding.POST,listOf(10,11)))
        val h=ApiClient.HomeIsland(1,mapOf(0 to "flowers"),0,emptyList(),places=mapOf(0 to ApiClient.LifePlace("home","")),construction=c)
        assertTrue(!IslandLayout.valid(c,IsleBuilding.POST,listOf(8,9),IslandLayout.obstacles(h)))
        assertTrue(!IslandLayout.valid(c,IsleBuilding.POST,listOf(10,6),IslandLayout.obstacles(h)))
        assertTrue(IslandLayout.obstacles(h.copy(construction=c.copy(mode="creative"))).isEmpty())
    }
    @Test fun buildingFootprintsScaleWithLandSoAdjacentGridCellsDoNotOverlap() {
        for(land in 0..5) for(tier in 1..3) {
            val c=ApiClient.IslandConstruction(buildings=IsleBuilding.entries.associate { it.name to tier },land=land)
            IslandPlans.placedBuildings(0,c).values.forEach { p ->
                assertTrue(p.size <= IslandLayout.separation(c)/20f*IslandPlans.constructionScale(land))
            }
        }
    }
}
