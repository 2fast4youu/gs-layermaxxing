package at.gregor.layermaxxing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatExtrasTest {
    private fun msg(id: Long = 1, sender: Long = 1, text: String = "Hallo", kind: String = "text", created: Long = 1_000, deleted: Boolean = false) =
        ApiClient.ChatMessage(id, sender, 2, text, created, null, kind = kind, deleted = deleted)

    @Test fun reactionsGroupAndToggle() {
        val r = listOf(ApiClient.Reaction(1, "a", "❤️"), ApiClient.Reaction(2, "b", "❤️"), ApiClient.Reaction(3, "c", "😂"))
        val chips = ChatExtras.reactionChips(r, ownUserId = 2)
        assertEquals(listOf("❤️", "😂"), chips.map { it.emoji })
        assertEquals(2, chips[0].count); assertTrue(chips[0].mine); assertFalse(chips[1].mine)
        assertEquals("❤️", ChatExtras.myReaction(r, 2))
        assertEquals("", ChatExtras.toggledReaction("❤️", "❤️"))
        assertEquals("😂", ChatExtras.toggledReaction("❤️", "😂"))
    }

    @Test fun presenceLinePrefersTypingThenOnline() {
        val p = ApiClient.ChatPresence(typing = true, online = true, lastSeenAt = 100, chatDayToday = false, chatDays = 0, chatPoints = 0)
        assertEquals("schreibt gerade …", ChatExtras.presenceLine("Anna", p, 200))
        assertEquals("ist gerade auf der Insel", ChatExtras.presenceLine("Anna", p.copy(typing = false), 200))
        assertEquals("zuletzt vor 2 Std. da", ChatExtras.presenceLine("Anna", p.copy(typing = false, online = false), 100 + 2 * 3600 + 5))
        assertEquals("zuletzt vor 1 Tag da", ChatExtras.presenceLine("Anna", p.copy(typing = false, online = false), 100 + 86400 + 5))
        assertNull(ChatExtras.presenceLine("Anna", null, 0))
    }

    @Test fun typingIsThrottled() {
        assertTrue(ChatExtras.shouldPingTyping(0, 5_000, "h"))
        assertFalse(ChatExtras.shouldPingTyping(4_000, 5_000, "h"))
        assertFalse(ChatExtras.shouldPingTyping(0, 5_000, "  "))
    }

    @Test fun editAndDeleteRules() {
        assertTrue(ChatExtras.canEdit(msg(), 1, 2_000))
        assertFalse(ChatExtras.canEdit(msg(), 2, 2_000))
        assertFalse(ChatExtras.canEdit(msg(), 1, 1_000 + 25 * 3600))
        assertFalse(ChatExtras.canEdit(msg(kind = "image"), 1, 2_000))
        assertTrue(ChatExtras.canDelete(msg(kind = "image"), 1))
        assertFalse(ChatExtras.canDelete(msg(deleted = true), 1))
    }

    @Test fun questDraftPicksIconAndShortTitle() {
        val d = ChatExtras.questDraft("> Wann?\n\nSamstag aufs Brennerhaus wandern?")
        assertEquals("hike", d.icon)
        assertEquals("Samstag aufs Brennerhaus wandern", d.title)
        assertEquals(20, d.points)
        assertEquals("grill", ChatExtras.questDraft("Grillen bei mir am Sonntag").icon)
        assertEquals("star", ChatExtras.questDraft("Treffen wir uns").icon)
        val long = ChatExtras.questDraft("a".repeat(80))
        assertTrue(long.title.length <= 60 && long.title.endsWith("…"))
        assertEquals("Gemeinsame Quest", ChatExtras.questDraft("???").title)
    }

    @Test fun glossaryHitsAreWholeWordsLongestFirst() {
        val terms = listOf(
            ApiClient.GlossaryTerm(1, "Hütte", "x", emptyList(), false),
            ApiClient.GlossaryTerm(2, "Brennerhaus Hütte", "x", emptyList(), false),
            ApiClient.GlossaryTerm(3, "GS", "x", emptyList(), false),
        )
        val hits = ChatExtras.glossaryHits("Treffen an der brennerhaus hütte, nicht GSX aber gs.", terms)
        assertEquals(listOf(2L, 3L), hits.map { it.termId })
        val text = "Treffen an der brennerhaus hütte, nicht GSX aber gs."
        assertEquals("brennerhaus hütte", text.substring(hits[0].start, hits[0].end))
        assertEquals("gs", text.substring(hits[1].start, hits[1].end))
        assertTrue(ChatExtras.glossaryHits("", terms).isEmpty())
    }

    @Test fun stickersAndPreviews() {
        val s = msg(text = ChatExtras.stickerText("palm"), kind = "sticker")
        assertEquals("palm", ChatExtras.stickerKey(s))
        assertNull(ChatExtras.stickerKey(msg(text = "sticker:unknown", kind = "sticker")))
        assertNull(ChatExtras.stickerKey(msg(text = "sticker:palm")))
        assertEquals("Sticker: Palme", ChatExtras.previewText(s))
        assertEquals("📷 Foto", ChatExtras.previewText(msg(text = "", kind = "image")))
        assertEquals("🍾 Flaschenpost", ChatExtras.previewText(msg(text = "", kind = "voice")))
        assertEquals("Nachricht gelöscht", ChatExtras.previewText(msg(deleted = true)))
    }

    @Test fun mediaHelpers() {
        assertEquals("0:07", ChatExtras.voiceLabel(7_400))
        assertEquals("1:00", ChatExtras.voiceLabel(60_000))
        assertEquals(1280 to 960, ChatExtras.scaledSize(4000, 3000))
        assertEquals(800 to 600, ChatExtras.scaledSize(800, 600))
        assertEquals(0 to 0, ChatExtras.scaledSize(0, 10))
    }
}
