package at.gregor.layermaxxing

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 7.9.12 harbour routing, the model (HarbourRoute): berths follow the CURRENT harbour (moved left / right / up /
 * down, every land and stage) and lie in the water right at its pier; islands without a harbour get a visible landing
 * stage; routes start and end exactly at the berths, run through water around every island, carry lanes without
 * moving their ends; boats arrive at the berth at full progress, waiting letters stay in the sender's roadstead.
 */
class HarbourRouteTest {
    /** 7.9.12 berth cases (rule 5, the harbour on the grid's coast), pinned; rule 6 berths: IslandGroundTest. */
    private fun island(land: Int, harbour: List<Int>?, stage: Int = 2) = ApiClient.IslandConstruction(land = land, gridVersion = PlacementRule.MASKS,
        buildings = if (harbour != null && stage > 0) mapOf("HARBOUR" to stage) else emptyMap(),
        starters = if (harbour != null && stage == 0) setOf("HARBOUR") else emptySet(),
        positions = harbour?.let { mapOf("HARBOUR" to it) } ?: emptyMap())

    @Test fun theBerthIsTheWaterRightInFrontOfTheCurrentHarbourWhereverItWasMoved() {
        for (land in 0..5) for (stage in 0..3) {
            val spots = IslandMasks.harbourSpots(stage, land)
            assertTrue("land $land stage $stage", spots.isNotEmpty())
            // left-most, right-most, highest (furthest up the screen) and lowest fitting spot, and an old north-coast harbour (v4 data)
            val north = IslandGrid.coastCells(land).minBy { it[1] }
            for (spot in listOf(spots.minBy { it[0] }, spots.maxBy { it[0] }, spots.minBy { it[1] }, spots.maxBy { it[1] }, north)) {
                val c = island(land, spot, stage)
                val (bx, by) = HarbourRoute.berth(c)
                // the renderer's harbour piece: its foot is the spot; the berth is one cell in front of it, inside its width
                val piece = IslandPlans.placedBuildings(0, c).getValue(IsleBuilding.HARBOUR)
                val step = IslandGrid.step(land)
                assertEquals("$land/$stage $spot x", piece.x, bx, 1e-4f)
                assertEquals("$land/$stage $spot y", piece.y + step, by, 1e-4f)
                assertTrue(kotlin.math.abs(bx - piece.x) <= piece.size / 2)
                // the berth's cell is water (never on the land), unless the harbour stands on an old inland spot
                if (spot in spots) assertFalse("$land/$stage $spot", IslandGrid.buildable(spot[0], spot[1] + 1, land))
            }
        }
        // moving the harbour moves the berth, at once (no cached point)
        val a = HarbourRoute.berth(island(5, listOf(7, 15))); val b = HarbourRoute.berth(island(5, listOf(13, 15)))
        assertTrue(b.first - a.first > .2f)
    }

    @Test fun withoutAHarbourTheVisibleLandingStageIsTheEnd() {
        for (land in 0..5) {
            for (c in listOf(island(land, null), ApiClient.IslandConstruction(supported = false, land = land), null)) {
                val (x, y) = HarbourRoute.berth(c)
                assertEquals(HarbourRoute.fallbackBerth(c?.land ?: 0), x to y)
                assertFalse(HarbourRoute.hasHarbour(c))
            }
            // the stage reaches from the southern shore out into the water. 7.9.13: from the painted beach of the cove
            // (zone B, the same on every land stage) into its open water (zone W)
            val root = HarbourRoute.fallbackRoot(land); val tip = HarbourRoute.fallbackBerth(land)
            assertEquals('B', IslandGround.zone(IslandGrid.unproject(root.first, root.second, land)))
            assertEquals('W', IslandGround.zone(IslandGrid.unproject(tip.first, tip.second, land)))
            assertFalse(IslandGrid.onLand(tip.first, tip.second, land))
            assertTrue(tip.second > root.second)
        }
    }

    private val hub = HarbourRoute.Island(Offset(500f, 500f), 120f)
    private fun inside(p: Offset, o: HarbourRoute.Island) = (p - o.centre).getDistance() < o.radius - .5f

    @Test fun routesStartAndEndAtTheBerthsAndGoAroundIslandsThroughWater() {
        val southBerth = Offset(500f, 590f) // a harbour on the south coast of the hub
        for (friend in listOf(Offset(500f, 120f), Offset(120f, 500f), Offset(880f, 500f), Offset(500f, 880f), Offset(150f, 150f), Offset(860f, 150f))) {
            val fi = HarbourRoute.Island(friend, 80f)
            // the friend's harbour on its own south coast too
            val friendBerth = friend + Offset(0f, 60f)
            val other = HarbourRoute.Island(Offset(820f, 820f), 70f)
            val all = listOf(hub, fi, other)
            val r = HarbourRoute.route(southBerth, hub, friendBerth, fi, all, 30f)!!
            assertEquals(southBerth, r.first()); assertEquals(friendBerth, r.last())
            // between leaving the own pier and arriving at the other one: never over an island
            val samples = (1..199).map { HarbourRoute.at(r, it / 200f) }
            val l = HarbourRoute.length(r)
            samples.forEachIndexed { k, p ->
                val run = l * (k + 1) / 200f
                val nearEnds = run < 60f || run > l - 60f
                for (o in all) if (!nearEnds) assertFalse("$friend sample $k $p in $o", inside(p, o))
            }
            // a friend to the north of the hub is reached around the island, not through it
            val open = samples.filterIndexed { k, _ -> l * (k + 1) / 200f in 60f..(l - 60f) }
            if (friend.y < 300f) assertTrue(open.none { inside(it, HarbourRoute.Island(hub.centre, hub.radius * .9f)) })
            // lanes: the same ends, a different middle
            val lane = HarbourRoute.lane(r, 40f)
            assertEquals(r.first(), lane.first()); assertEquals(r.last(), lane.last())
            assertNotEquals(HarbourRoute.at(r, .5f), HarbourRoute.at(lane, .5f))
        }
    }

    @Test fun progressArrivesAtTheBerthWaitingLettersStayInTheRoadstead() {
        val r = HarbourRoute.route(Offset(500f, 590f), hub, Offset(900f, 560f), HarbourRoute.Island(Offset(900f, 500f), 80f), listOf(hub), 30f)!!
        // outgoing: 0 = my berth, 1 = the friend's berth (not .86 of the way as 7.9.11)
        assertEquals(r.first(), HarbourRoute.at(r, HarbourRoute.boatT(false, 0f)))
        assertEquals(r.last(), HarbourRoute.at(r, HarbourRoute.boatT(false, 1f)))
        val half = HarbourRoute.at(r, HarbourRoute.boatT(false, .5f))
        val l = HarbourRoute.length(r)
        assertEquals(l / 2, HarbourRoute.length(HarbourRoute.part(r, 0f, .5f, 400)), l * .01f)
        assertTrue((half - r.first()).getDistance() > 50f && (half - r.last()).getDistance() > 50f)
        // incoming: swapped, 0 = the friend's berth, 1 = mine
        assertEquals(r.last(), HarbourRoute.at(r, HarbourRoute.boatT(true, 0f)))
        assertEquals(r.first(), HarbourRoute.at(r, HarbourRoute.boatT(true, 1f)))
        // waiting (approval / presence): in the sender's roadstead, never at the receiver's berth
        val waitOut = HarbourRoute.at(r, HarbourRoute.boatT(false, null)); val waitIn = HarbourRoute.at(r, HarbourRoute.boatT(true, null))
        assertTrue((waitOut - r.first()).getDistance() < (waitOut - r.last()).getDistance())
        assertTrue((waitIn - r.last()).getDistance() < (waitIn - r.first()).getDistance())
        assertNotEquals(r.last(), waitOut); assertNotEquals(r.first(), waitIn)
    }

    @Test fun theBerthIsInTheIslandBoxWhereverTheIslandIsDrawn() {
        // map px = the island box's top-left + berth fraction × its drawn size: the same for every zoom/pan of the map layer
        val c = island(3, listOf(12, 15))
        val f = HarbourRoute.berth(c)
        for ((tl, w) in listOf(Offset(0f, 0f) to 180f, Offset(300f, 200f) to 360f, Offset(-50f, 40f) to 104f)) {
            val p = HarbourRoute.onMap(tl, w, f)
            assertEquals(tl.x + f.first * w, p.x, 1e-3f); assertEquals(tl.y + f.second * w / IslandPlans.ASPECT, p.y, 1e-3f)
            for ((zoom, pan) in listOf(1f to Offset.Zero, 2.2f to Offset(-300f, 120f), .8f to Offset(40f, -60f))) {
                // the map layer scales and pans islands and routes together: the end stays on the berth on screen
                val screenBerth = Offset(p.x * zoom + pan.x, p.y * zoom + pan.y)
                val screenRouteEnd = HarbourRoute.route(p, HarbourRoute.Island(tl + Offset(w / 2, w / 2), w * .45f), Offset(900f, 900f), HarbourRoute.Island(Offset(900f, 860f), 50f), emptyList(), 20f)!!
                    .first().let { Offset(it.x * zoom + pan.x, it.y * zoom + pan.y) }
                assertEquals(screenBerth, screenRouteEnd)
            }
        }
    }

    // ---- 7.9.12 routing guarantees: every computed way and every lane is water, else nothing is drawn ----

    private fun berthOf(o: HarbourRoute.Island, dir: Offset) = o.centre + dir * (o.radius * .9f)

    /** Every point of [r] away from the two dock pieces lies outside every island; the dock pieces only touch their own. */
    private fun assertWater(r: List<Offset>, ia: HarbourRoute.Island, ib: HarbourRoute.Island, all: List<HarbourRoute.Island>, label: String) {
        val dockA = (r[1] - r[0]).getDistance(); val dockB = (r[r.size - 2] - r.last()).getDistance()
        val l = HarbourRoute.length(r)
        for (k in 0..400) {
            val p = HarbourRoute.at(r, k / 400f)
            val run = l * k / 400f
            for (o in all) {
                val own = (o == ia && run <= dockA + 1f) || (o == ib && run >= l - dockB - 1f)
                if (!own) assertFalse("$label sample $k $p in $o", inside(p, o))
            }
        }
        // the dock pieces never go deeper into their own island than the berth
        assertTrue(label, (HarbourRoute.at(r, 0f) - ia.centre).getDistance() <= (r[1] - ia.centre).getDistance())
    }

    @Test fun manyIslandsEveryRouteAndEveryLaneIsWater() {
        // a ring of 9 islands round the hub plus a second ring: deep routes that the old depth-7 detour cut short
        val home = HarbourRoute.Island(Offset(1000f, 1000f), 110f)
        val islands = (0 until 9).map { k -> val a = 2 * Math.PI * k / 9; HarbourRoute.Island(Offset(1000f + 330f * kotlin.math.cos(a).toFloat(), 1000f + 330f * kotlin.math.sin(a).toFloat()), 70f) } +
            (0 until 10).map { k -> val a = 2 * Math.PI * (k + .5) / 10; HarbourRoute.Island(Offset(1000f + 640f * kotlin.math.cos(a).toFloat(), 1000f + 640f * kotlin.math.sin(a).toFloat()), 80f) }
        val all = islands + home
        val bounds = androidx.compose.ui.geometry.Rect(0f, 0f, 2000f, 2000f)
        val a = berthOf(home, Offset(0f, 1f))
        var found = 0
        for (f in islands) {
            val b = berthOf(f, Offset(0f, 1f))
            val r = HarbourRoute.route(a, home, b, f, all, 24f, bounds) ?: continue
            found++
            assertEquals(a, r.first()); assertEquals(b, r.last())
            assertWater(r, home, f, all, "route to ${f.centre}")
            for (side in listOf(-92f, -46f, 46f, 92f, 140f)) {
                val lane = HarbourRoute.safeLane(r, side, home, f, all, 12f, bounds)
                assertEquals(a, lane.first()); assertEquals(b, lane.last())
                assertTrue(HarbourRoute.laneClear(lane, home, f, all, 12f, bounds))
                assertWater(lane, home, f, all, "lane $side to ${f.centre}")
            }
        }
        // the rings leave water gaps: every island is reachable by water
        assertEquals(islands.size, found)
    }

    @Test fun aNarrowCorridorIsUsedButAClosedOneGivesNoRoute() {
        val home = HarbourRoute.Island(Offset(200f, 500f), 80f)
        val friend = HarbourRoute.Island(Offset(800f, 500f), 80f)
        // a wall of islands between them with one gap at y = 500 just wide enough
        val gap = listOf(HarbourRoute.Island(Offset(500f, 380f), 90f), HarbourRoute.Island(Offset(500f, 620f), 90f)) +
            (1..4).flatMap { k -> listOf(HarbourRoute.Island(Offset(500f, 380f - 170f * k), 90f), HarbourRoute.Island(Offset(500f, 620f + 170f * k), 90f)) }
        val bounds = androidx.compose.ui.geometry.Rect(0f, 0f, 1000f, 1000f)
        val a = berthOf(home, Offset(1f, 0f)); val b = berthOf(friend, Offset(-1f, 0f))
        val all = gap + home + friend
        val r = HarbourRoute.route(a, home, b, friend, all, 12f, bounds)!!
        assertWater(r, home, friend, all, "corridor")
        // a lane wider than the corridor falls back to a lane that fits (or the route itself), never into the islands
        val lane = HarbourRoute.safeLane(r, 46f, home, friend, all, 6f, bounds)
        assertWater(lane, home, friend, all, "corridor lane")
        // close the gap: no way inside the map → no route at all (not a straight line through the wall)
        val closed = all + HarbourRoute.Island(Offset(500f, 500f), 60f)
        assertEquals(null, HarbourRoute.route(a, home, b, friend, closed, 12f, bounds))
    }

    @Test fun anExitInsideAnotherIslandGivesNoRouteAndTheDockIsNotAFreePass() {
        val home = HarbourRoute.Island(Offset(300f, 300f), 80f)
        val friend = HarbourRoute.Island(Offset(800f, 300f), 80f)
        val blocker = HarbourRoute.Island(Offset(300f, 420f), 50f) // right in front of the home pier
        val a = berthOf(home, Offset(0f, 1f)); val b = berthOf(friend, Offset(0f, 1f))
        assertEquals(null, HarbourRoute.route(a, home, b, friend, listOf(home, friend, blocker), 24f))
        // a lane that cuts across its own island (berth on the far side) is rejected by laneClear
        val r = HarbourRoute.route(a, home, b, friend, listOf(home, friend), 24f)!!
        val cut = listOf(a, home.centre, r[r.size - 2], b)
        assertFalse(HarbourRoute.laneClear(cut, home, friend, listOf(home, friend), 0f))
    }

    @Test fun aPierFacingTheMapEdgeTurnsTowardsTheOpenSeaInsteadOfLeavingTheMap() {
        // the 7.9.12 review case: Lena's harbour faces west, her island close to the left edge of the boat's sea
        val sea = androidx.compose.ui.geometry.Rect(126f, 63f, 1107f, 2610f)
        val home = HarbourRoute.Island(Offset(616.5f, 1386.3f), 224.1f)
        val lena = HarbourRoute.Island(Offset(296.2f, 1860.8f), 194.4f)
        val a = Offset(562.5f, 1462.4f); val b = Offset(216.7f, 1860.5f)
        val all = listOf(home, lena)
        val r = HarbourRoute.route(a, home, b, lena, all, 66f, sea)!!
        assertEquals(a, r.first()); assertEquals(b, r.last())
        // everything but the berths themselves lies in the sea a boat fits in
        r.subList(1, r.size - 1).forEach { assertTrue("$it", sea.contains(it)) }
        assertWater(r, home, lena, all, "lena")
        for (side in listOf(-92f, -46f, 46f, 92f)) HarbourRoute.safeLane(r, side, home, lena, all, 33f, sea).subList(1, r.size - 1).forEach { assertTrue("$side $it", sea.contains(it)) }
    }

    // 7.9.13 physical hull: two square islands, each berth 6 px off its coast (water for the 4 px keel, NOT for the
    // 12 px hull). The ship moors at hull water close to the berth and the whole sailed way keeps the hull clear.
    private val keel = 4f; private val hull = 12f
    private fun square(x: Float, y: Float, s: Float) = SeaChart.Coast(listOf(Offset(x, y), Offset(x + s, y), Offset(x + s, y + s), Offset(x, y + s)), emptyList())
    private val coasts = listOf(square(100f, 100f, 200f), square(500f, 100f, 200f))
    private val field = SeaChart.Field(coasts, androidx.compose.ui.geometry.Rect(0f, 0f, 800f, 500f), 7.2f)
    private val keelOnlyA = Offset(200f, 306f); private val keelOnlyB = Offset(600f, 306f)

    @Test fun aBerthWithWaterForTheKeelButNotTheHullMoorsAtHullWaterCloseBy() {
        assertTrue(SeaChart.clearance(keelOnlyA, coasts) in keel..hull)
        val m = SeaChart.moor(keelOnlyA, null, coasts, keel, 3 * hull, atSocket = true, hull = hull)!!
        assertFalse(m.repair)
        assertTrue("${m.point}", SeaChart.clearance(m.point, coasts) >= hull)
        // the short, visible mooring distance in front of the pier, never a far point at another side of the island
        assertTrue("${m.point}", (m.point - keelOnlyA).getDistance() <= 2 * hull)
    }

    @Test fun everyPieceOfARouteAndItsLanesKeepsTheWholeHullInWater() {
        val a = SeaChart.moor(keelOnlyA, null, coasts, keel, 3 * hull, atSocket = true, hull = hull)!!.point
        val b = SeaChart.moor(keelOnlyB, null, coasts, keel, 3 * hull, atSocket = true, hull = hull)!!.point
        val r = SeaChart.route(a, b, field, hull, keel)!!
        fun check(p: List<Offset>, what: String) = p.zipWithNext().forEachIndexed { k, (x, y) ->
            assertTrue("$what piece $k $x→$y hull over land", SeaChart.clearSegment(x, y, coasts, hull * .99f, null, keel)) }
        check(r, "route")
        for (side in listOf(-46f, 46f)) check(SeaChart.lane(r, side, field, hull, keel), "lane $side")
    }

    @Test fun aSocketWithoutHullWaterNearbyIsARepairCaseNotAFarHarbour() {
        // deep inland: hull water only far away (another side) -> the island's real landing stage, marked for repair
        val inland = Offset(200f, 200f); val cove = Offset(200f, 330f)
        val m = SeaChart.moor(inland, cove, coasts, keel, 3 * hull, atSocket = true, hull = hull)!!
        assertTrue(m.repair)
        assertTrue("${m.point}", SeaChart.clearance(m.point, coasts) >= hull && (m.point - cove).getDistance() <= 2 * hull)
    }
}
