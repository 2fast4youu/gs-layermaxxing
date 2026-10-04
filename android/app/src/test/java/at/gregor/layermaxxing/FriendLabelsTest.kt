package at.gregor.layermaxxing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FriendLabelsTest {
    @Test fun roundTripAndClean() {
        val m = mapOf(2L to "Allerbester Freund", 3L to "Verlobte")
        assertEquals(m, FriendLabels.parse(FriendLabels.encode(m)))
        assertNull(FriendLabels.clean("   "))
        assertEquals("Ida Schwesterherz", FriendLabels.clean("  Ida   Schwesterherz "))
        assertEquals(28, FriendLabels.clean("x".repeat(60))!!.length)
        assertEquals(emptyMap<Long, String>(), FriendLabels.parse("kaputt"))
    }
    @Test fun labelWinsOverLevel() {
        assertEquals("Verlobte", FriendLabels.display("Verlobte", 3))
        assertEquals("Vertraut", FriendLabels.display(null, 3))
    }
}
