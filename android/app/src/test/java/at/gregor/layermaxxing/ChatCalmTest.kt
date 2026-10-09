package at.gregor.layermaxxing

import org.junit.Assert.*
import org.junit.Test

class ChatCalmTest {
    @Test fun inTransitAndEmptyStateDoNotCreateHint() {
        assertNull(ChatCalm.hint(listOf(SealBadge(LetterBucket.IN_TRANSIT, 4, 1)), 0, false, 0))
    }
    @Test fun approvalIsNotMislabelledAsReady() {
        assertEquals("1 Briefe warten auf dich", ChatCalm.hint(listOf(SealBadge(LetterBucket.WAITING_ON_YOU, 1, 1)), 0, false, 0))
    }
    @Test fun allActionableItemsShareOneHint() {
        val hint = ChatCalm.hint(emptyList(), 2, true, 3)!!
        assertEquals("2 Anfragen · Regelvorschlag wartet · 3 Quests offen", hint)
    }
}
