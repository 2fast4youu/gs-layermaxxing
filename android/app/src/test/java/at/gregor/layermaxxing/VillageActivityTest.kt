package at.gregor.layermaxxing

import org.junit.Assert.*
import org.junit.Test

class VillageActivityTest {
    @Test fun emptyWorldHasNoInventedActivity() {
        assertTrue(VillageActivityModel.collect(0, 0, 0, 0, 0, 0).isEmpty())
    }
    @Test fun signalsPreserveRealCountsAndDestinations() {
        val signals = VillageActivityModel.collect(3, 2, 1, 4, 5, 6)
        assertEquals(6, signals.size)
        assertEquals(3, signals.first { it.destination == ValleyDestination.CONVERSATIONS }.count)
        assertEquals(5, signals.first { it.destination == ValleyDestination.ARCHIVE }.count)
        assertTrue(signals.all { it.count > 0 })
    }
    @Test fun invalidNegativeCountsAreNotShownAsActivity() {
        assertTrue(VillageActivityModel.collect(-1, -1, -1, -1, -1, -1).isEmpty())
    }
    @Test fun couriersFollowVillageRouteAndClampEndpoints() {
        assertEquals(VillageScenes.HOME, VillageActivityModel.courierPoint(-1f))
        assertEquals(VillageScenes.FRIEND, VillageActivityModel.courierPoint(2f))
        assertEquals(MapPoint(.51f, .72f), VillageActivityModel.courierPoint(.5f))
        val before = VillageActivityModel.courierPoint(.49f)
        val after = VillageActivityModel.courierPoint(.51f)
        assertTrue(kotlin.math.abs(before.x - after.x) < .02f)
    }
}
