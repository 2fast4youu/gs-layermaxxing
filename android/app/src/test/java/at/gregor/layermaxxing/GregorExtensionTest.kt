package at.gregor.layermaxxing

import org.junit.Assert.*
import org.junit.Test

class GregorExtensionTest {
    @Test fun topicScopesNeverMixPeopleAndGroups() {
        val all = listOf(topic(1, 7), topic(2, 7, "group"), topic(3, null, "personal"), topic(4, 8))
        assertEquals(listOf(1L), TopicScope.friend(7).filter(all).map { it.id })
        assertEquals(listOf(2L), TopicScope.group(7).filter(all).map { it.id })
        assertEquals(listOf(3L), TopicScope.personal().filter(all).map { it.id })
    }
    @Test fun glossarySearchFindsMeaningAndIsCaseInsensitive() {
        assertTrue(AppGlossary.search("EP").any { it.term == "Ebenen-Punkte (EP)" })
        assertTrue(AppGlossary.search("server").isNotEmpty())
        assertTrue(AppGlossary.search("xyz-no-match").isEmpty())
        assertEquals(AppGlossary.entries.size, AppGlossary.search(" ").size)
    }
    @Test fun searchDoesNotRevealSealedBodies() {
        val sealed = ThreadEntry.Letter(letter(1, 2, title = "Umschlag", ciphertext = "secret"), LetterState.LOCKED_TIMED)
        val instant = ThreadEntry.Chat(chatMsg(2, 1, 2, "Treffen morgen", 10), true)
        assertEquals(listOf(instant), ChatTools.filterEntries(listOf(sealed, instant), "MORGEN"))
        assertTrue(ChatTools.filterEntries(listOf(sealed), "secret").isEmpty())
    }
    @Test fun quotedReplyPreservesBodyAndLimitsQuoteLength() {
        assertEquals("> Hallo\n\nAntwort", ChatTools.replyText("Hallo", "Antwort"))
        assertEquals("Antwort", ChatTools.replyText(null, "Antwort"))
        assertTrue(ChatTools.replyText("x".repeat(1000), "A").length < 260)
    }
}
