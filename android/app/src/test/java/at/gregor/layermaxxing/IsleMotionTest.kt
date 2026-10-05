package at.gregor.layermaxxing

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IsleMotionTest {
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
