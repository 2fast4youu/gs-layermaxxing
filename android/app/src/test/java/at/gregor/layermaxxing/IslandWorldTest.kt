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
        assertEquals("⭐", Isle.questEmoji("??")); assertEquals("🥾", Isle.questEmoji("hike"))
    }
}
