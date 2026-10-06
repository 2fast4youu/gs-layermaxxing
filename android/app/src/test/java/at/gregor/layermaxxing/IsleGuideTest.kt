package at.gregor.layermaxxing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IsleGuideTest {
    @Test fun friendRequestsComeFirst() {
        val step = IsleGuide.nextStep(incoming = 2, readyLetters = 3, openQuests = 1, friends = 4)!!
        assertEquals(IsleBuilding.LIGHTHOUSE, step.building)
        assertEquals("2 Freundschaftsanfragen", step.text)
    }

    @Test fun lettersBeforeQuests() {
        val step = IsleGuide.nextStep(incoming = 0, readyLetters = 1, openQuests = 5, friends = 4)!!
        assertEquals(IsleBuilding.POST, step.building)
        assertEquals("Ein Brief ist angekommen", step.text)
    }

    @Test fun questsLast() {
        assertEquals(IsleBuilding.HARBOUR, IsleGuide.nextStep(0, 0, 1, 4)!!.building)
    }

    @Test fun nothingToDoShowsNothing() {
        assertNull(IsleGuide.nextStep(0, 0, 0, 4))
        assertNull(IsleGuide.nextStep(0, 0, 0, 0))
    }
}
