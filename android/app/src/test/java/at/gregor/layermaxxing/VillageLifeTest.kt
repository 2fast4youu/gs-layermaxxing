package at.gregor.layermaxxing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VillageLifeTest {
    @Test fun populationGrowsWithRealPlacesAndIsCapped() {
        assertEquals(4, VillageLife.population(VillageGrowth.START.size))
        assertTrue(VillageLife.population(ValleyDestination.entries.size) <= 7)
        assertEquals(2, VillageLife.population(0))
    }

    @Test fun walkersStayOnTheirTracedPath() {
        repeat(7) { i ->
            val route = VillageLife.routes[i % VillageLife.routes.size]
            val minX = route.minOf { it.x } - .001f; val maxX = route.maxOf { it.x } + .001f
            val minY = route.minOf { it.y } - .001f; val maxY = route.maxOf { it.y } + .001f
            var t = 0f
            while (t < 300f) {
                val (p, _) = VillageLife.walker(i, t)
                assertTrue("walker $i left its path at $t: $p", p.x in minX..maxX && p.y in minY..maxY)
                t += .7f
            }
        }
    }

    @Test fun pointAtCoversTheEnds() {
        val r = VillageLife.routes.first()
        assertEquals(r.first(), VillageLife.pointAt(r, 0f))
        assertEquals(r.last(), VillageLife.pointAt(r, 1f))
    }

    @Test fun daylightFollowsTheClock() {
        assertEquals(VillageLife.Daylight.MORNING, VillageLife.daylight(7))
        assertEquals(VillageLife.Daylight.DAY, VillageLife.daylight(13))
        assertEquals(VillageLife.Daylight.EVENING, VillageLife.daylight(19))
        assertEquals(VillageLife.Daylight.NIGHT, VillageLife.daylight(2))
    }

    @Test fun everyRouteAndPointLiesOnThePlate() {
        (VillageLife.routes.flatten() + VillageLife.chimneys.map { it.first } + VillageLife.water + VillageLife.lanterns + VillageLife.windows)
            .forEach { assertTrue(it.toString(), it.x in 0f..1f && it.y in 0f..1f) }
    }
}
