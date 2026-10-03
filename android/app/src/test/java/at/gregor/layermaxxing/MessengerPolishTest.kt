package at.gregor.layermaxxing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class MessengerPolishTest {
    private val zone = ZoneId.of("Europe/Vienna")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toEpochSecond()

    @Test fun replyRoundTrip() {
        val sent = ChatTools.replyText("Wann treffen wir uns?", "Um acht")
        assertEquals("Wann treffen wir uns?" to "Um acht", ChatTools.parseReply(sent))
    }

    @Test fun plainTextAndLooseQuotesStayUntouched() {
        assertEquals(null to "Hallo", ChatTools.parseReply("Hallo"))
        assertEquals(null to "> nur ein Zitat", ChatTools.parseReply("> nur ein Zitat"))
        assertNull(ChatTools.parseReply("> a\n\n   ").first)
    }

    @Test fun listTimeLikeWhatsApp() {
        val now = at(2026, 10, 3, 15, 0) // Saturday
        assertEquals("09:05", ChatTools.listTime(at(2026, 10, 3, 9, 5), now, zone))
        assertEquals("Gestern", ChatTools.listTime(at(2026, 10, 2, 23, 0), now, zone))
        assertEquals("Mi", ChatTools.listTime(at(2026, 9, 30, 12, 0), now, zone))
        assertEquals("20.09.26", ChatTools.listTime(at(2026, 9, 20, 12, 0), now, zone))
        assertEquals("", ChatTools.listTime(0, now, zone))
    }

    private fun conv(id: Long, name: String, text: String, unread: Int = 0, locked: Int = 0) = Conversation(
        id, name, "🙂", "#000000", 1, ConversationPreview(text, false, false), unread, 0, locked, 0, true, true, false,
    )

    @Test fun filtersCombineSearchAndChip() {
        val list = listOf(conv(1, "Anna", "Hallo", unread = 1), conv(2, "Bert", "Grillen?"), conv(3, "Cleo", "x", locked = 1))
        assertEquals(listOf(1L), ChatTools.filterConversations(list, "", ChatTools.ListFilter.UNREAD).map { it.friendId })
        assertEquals(listOf(3L), ChatTools.filterConversations(list, "", ChatTools.ListFilter.LETTERS).map { it.friendId })
        assertEquals(listOf(2L), ChatTools.filterConversations(list, "grill", ChatTools.ListFilter.ALL).map { it.friendId })
        assertTrue(ChatTools.filterConversations(list, "grill", ChatTools.ListFilter.UNREAD).isEmpty())
    }

    @Test fun calmVillageShowsOnlyActiveMarkersAtOverview() {
        val counts = mapOf(ValleyDestination.TOPICS to 2, ValleyDestination.GROUPS to 0)
        val calm = VillageCalm.visibleMarkers(counts, closeUp = false, selected = null)
        assertTrue(ValleyDestination.TOPICS in calm)
        assertTrue(ValleyDestination.CONVERSATIONS in calm)
        assertFalse(ValleyDestination.GROUPS in calm)
        assertTrue(ValleyDestination.GROUPS in VillageCalm.visibleMarkers(counts, closeUp = false, selected = ValleyDestination.GROUPS))
        assertEquals(ValleyDestination.entries.toSet(), VillageCalm.visibleMarkers(counts, closeUp = true, selected = null))
        val busy = ValleyDestination.entries.associateWith { it.ordinal + 1 }
        val shown = VillageCalm.visibleMarkers(busy, closeUp = false, selected = null)
        assertEquals(VillageCalm.MAX_ACTIVE + VillageCalm.ALWAYS.size, shown.size)
        assertTrue(ValleyDestination.entries.maxBy { busy.getValue(it) } in shown)
    }

    @Test fun dictionaryMergesBuiltInAndSharedAndSearchesExplanations() {
        val shared = listOf(
            ApiClient.GlossaryTerm(1, "Hüttenwart", "A", listOf(ApiClient.GlossaryExplanation(1, "kümmert sich ums Brennerhaus", "A", 1, 1, true)), true),
        )
        val all = Dictionary.rows(shared, "")
        assertEquals(AppGlossary.entries.size + 1, all.size)
        assertTrue(all.last().shared != null)
        assertEquals(listOf("Hüttenwart"), Dictionary.rows(shared, "brennerhaus").map { it.term })
        assertFalse(Dictionary.canSubmit(" ", "x"))
        assertFalse(Dictionary.canSubmit("x", " "))
        assertTrue(Dictionary.canSubmit("Wort", "Erklärung"))
    }
}
