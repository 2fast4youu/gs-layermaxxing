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

    @Test fun freshIslandHasPrimitiveBasicsInsteadOfRuins() {
        val fresh = IslandPlans.home(1L, 500, ApiClient.IslandConstruction())
        assertTrue(fresh.pieces.all { it.tier == 0 })
        assertEquals(IsleBuilding.entries.toSet(), fresh.pieces.mapNotNull { it.primitive }.toSet())
        assertTrue(fresh.pieces.none { it.res == R.drawable.lm_post_ruin })
        val built = IslandPlans.home(1L, 0, ApiClient.IslandConstruction(buildings=mapOf("POST" to 3), land=4))
        assertEquals(3, built.pieces.first { it.name == "Post" }.tier)
        assertTrue(built.landScale > fresh.landScale)
    }

    /** The starter must reuse the painted asset family, not fall back to Canvas icons. */
    @Test fun starterUsesPaintedNaturalPlacesInsteadOfBuildings() {
        assertEquals(0f, IslandPlans.meadow(IslandPlans.constructionScale(0)))
        val pieces = IslandPlans.placedBuildings(0, ApiClient.IslandConstruction())
        val expected = mapOf(
            IsleBuilding.HOUSE to R.drawable.decor_hammock,
            IsleBuilding.POST to R.drawable.decor_palm,
            IsleBuilding.LIGHTHOUSE to R.drawable.decor_palm,
            IsleBuilding.LIBRARY to R.drawable.n_crates,
            IsleBuilding.CAMPFIRE to R.drawable.decor_campfire,
            IsleBuilding.HARBOUR to R.drawable.n_rowboat,
        )
        expected.forEach { (building, paintedAsset) -> assertEquals(building.name, paintedAsset, pieces.getValue(building).res) }
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
}
