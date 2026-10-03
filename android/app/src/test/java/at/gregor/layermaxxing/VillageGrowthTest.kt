package at.gregor.layermaxxing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class VillageGrowthTest {
    @Test fun newPlayerStartsSmall() {
        val u = VillageGrowth.unlocked(GrowthFacts(), emptySet(), emptyMap())
        assertEquals(VillageGrowth.START, u)
        assertEquals(1, VillageGrowth.level(u))
        assertEquals("Finde deinen ersten Freund", VillageGrowth.nextStep(u)?.task)
    }

    @Test fun settingsAndPeopleAreNeverLocked() {
        assertTrue(ValleyDestination.SETTINGS in VillageGrowth.START)
        assertTrue(ValleyDestination.PEOPLE in VillageGrowth.START)
    }

    @Test fun realUseGrowsTheVillage() {
        val u = VillageGrowth.unlocked(GrowthFacts(friends = 2, chatsWithActivity = 1), emptySet(), emptyMap())
        assertTrue(ValleyDestination.TOPICS in u)
        assertTrue(ValleyDestination.SPARKS in u)
        assertTrue(ValleyDestination.GROUPS in u)
        assertFalse(ValleyDestination.ARCHIVE in u)
    }

    @Test fun newsAlwaysOpensItsPlace() {
        val u = VillageGrowth.unlocked(GrowthFacts(), emptySet(), mapOf(ValleyDestination.ARCHIVE to 1))
        assertTrue(ValleyDestination.ARCHIVE in u)
    }

    @Test fun grownPlacesNeverShrinkBack() {
        val u = VillageGrowth.unlocked(GrowthFacts(), setOf(ValleyDestination.GROUPS), emptyMap())
        assertTrue(ValleyDestination.GROUPS in u)
    }

    @Test fun fullVillageHasNoNextStep() {
        val all = ValleyDestination.entries.toSet()
        assertNull(VillageGrowth.nextStep(all))
        assertEquals(VillageGrowth.MAX_LEVEL, VillageGrowth.level(all))
    }

    @Test fun everyLockedPlaceHasAGuide() {
        ValleyDestination.entries.filter { it !in VillageGrowth.START }.forEach { d ->
            assertTrue("$d needs a growth step", VillageGrowth.PATH.any { it.destination == d })
        }
    }

    @Test fun encodingRoundTrips() {
        val set = setOf(ValleyDestination.TOPICS, ValleyDestination.EP)
        assertEquals(set, VillageGrowth.decode(VillageGrowth.encode(set)))
        assertEquals(emptySet<ValleyDestination>(), VillageGrowth.decode("garbage,"))
    }
}
