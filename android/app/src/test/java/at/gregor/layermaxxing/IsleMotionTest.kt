package at.gregor.layermaxxing

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IsleMotionTest {
    @Test fun boatsGetSeparateLanesSideBySide() {
        assertEquals(listOf(0f), IsleMotion.laneOffsets(1))
        assertEquals(listOf(-.5f, .5f), IsleMotion.laneOffsets(2))
        assertEquals(listOf(-1f, 0f, 1f), IsleMotion.laneOffsets(3))
        assertEquals(IsleMotion.MAX_LANES, IsleMotion.laneOffsets(9).size)
        assertTrue(IsleMotion.laneOffsets(0).isEmpty())
        val lanes = IsleMotion.laneOffsets(4)
        assertEquals("all lanes distinct", lanes.size, lanes.toSet().size)
        assertEquals(listOf(1f, 2f, 3f), IsleMotion.laneOffsets(3, oneSided = true))
        // Dodging never crosses to the other side of the route.
        assertTrue(IsleMotion.laneCandidates(-.5f).all { it <= -.5f })
        assertTrue(IsleMotion.laneCandidates(.5f).all { it >= .5f })
        assertEquals(0f, IsleMotion.laneCandidates(0f).first())
        // Close neighbours carry fewer lanes, long routes up to the maximum.
        assertEquals(2, IsleMotion.lanesThatFit(100f, 78f))
        assertEquals(IsleMotion.MAX_LANES, IsleMotion.lanesThatFit(800f, 78f))
        assertEquals(1, IsleMotion.lanesThatFit(0f, 200f))
    }

    @Test fun seaTimeSpeaksSailor() {
        assertEquals("⚓ vor Anker · wartet auf Wind", IsleMotion.seaTime(null, 0L))
        assertEquals("⚓ Anker", IsleMotion.seaTimeShort(null, 0L))
        assertEquals("🌊 2 Tage", IsleMotion.seaTimeShort(2 * 86_400L + 5, 0L))
        assertEquals("🔔 Land in Sicht!", IsleMotion.seaTime(100L, 100L))
        assertTrue(IsleMotion.seaTime(1_800L, 0L).contains("30 Min"))
        assertTrue(IsleMotion.seaTime(5 * 3600L, 0L).contains("5 Stunden"))
        assertTrue(IsleMotion.seaTime(3 * 86_400L, 0L).contains("3 Tage"))
        assertTrue(IsleMotion.seaTime(15 * 86_400L, 0L).contains("2 Wochen"))
    }

    @Test fun timedBoatMovesForwardAndNeverWraps() {
        val created = 1_000L; val release = 2_000L
        val samples = (0..12).map { IsleMotion.boatProgress(created, release, created + it * 100L) }
        samples.zipWithNext().forEach { (a, b) -> assertTrue("must not jump back", b >= a) }
        assertEquals(.18f, samples.first(), 1e-4f)
        assertEquals(.82f, samples.last(), 1e-4f) // stays at the friend's harbour after release time
    }

    @Test fun waitingBoatLiesAtAnchorMidRoute() {
        assertEquals(.5f, IsleMotion.boatProgress(1_000L, null, 99_999L), 1e-4f)
        assertEquals(.5f, IsleMotion.boatProgress(1_000L, 500L, 2_000L), 1e-4f)
    }

    @Test fun swayIsTiny() {
        (0..100).forEach { assertTrue(kotlin.math.abs(IsleMotion.sway(it * .7f, 3)) <= .0101f) }
    }

    @Test fun zoomIsClampedAndPanBounded() {
        val (_, z) = IsleMotion.zoomAround(Offset.Zero, 2f, 10f, Offset(100f, 100f), 1000f, 2000f)
        assertEquals(IsleMotion.MAX_ZOOM, z, 1e-4f)
        val p = IsleMotion.clampPan(Offset(99_999f, -99_999f), 1f, 1000f, 2000f)
        assertEquals(250f, p.x, 1e-3f); assertEquals(-400f, p.y, 1e-3f)
    }

    @Test fun zoomKeepsCentroidStill() {
        // Zooming around the screen centre must not move the pan.
        val (p, _) = IsleMotion.zoomAround(Offset.Zero, 1f, 1.5f, Offset(500f, 1000f), 1000f, 2000f)
        assertEquals(0f, p.x, 1e-3f); assertEquals(0f, p.y, 1e-3f)
    }

    private fun msg(mode: String, created: Long, release: Long?, rFrom: Long? = null, rTo: Long? = null) = ApiClient.Message(
        1, 2, "A", false, "t", "", "none", created, mode, release, rFrom, rTo, false, false, null, false, false,
        null, null, null, emptyList(), null, null, null,
    )

    @Test fun boatArrivesExactlyOnTheRealDay() {
        val m = msg("timed", 0L, 4 * 86_400L)
        assertEquals(0f, IsleMotion.tripShare(m, 0L)!!, 1e-4f)
        assertEquals(.5f, IsleMotion.tripShare(m, 2 * 86_400L)!!, 1e-4f)
        assertEquals(1f, IsleMotion.tripShare(m, 4 * 86_400L)!!, 1e-4f)
        // One minute moves the boat by a tiny fraction only – it creeps, it does not race.
        assertTrue(IsleMotion.tripShare(m, 60L)!! < .001f)
    }

    @Test fun approvalBoatsWaitAtAnchorAndRandomUsesWindowEnd() {
        assertEquals(null, IsleMotion.tripShare(msg("mutual", 0L, null), 999L))
        assertEquals(500L, IsleMotion.arrivalSec(msg("random", 0L, null, 100L, 500L)))
    }
}
