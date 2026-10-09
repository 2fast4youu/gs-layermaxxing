package at.gregor.layermaxxing

import org.junit.Assert.*
import org.junit.Test

class ChatSelectionTest {
    private val own = ApiClient.ChatMessage(1, 1, 2, "Eigene Nachricht", 100, null)
    private val incoming = own.copy(id = 2, senderId = 2, recipientId = 1)
    @Test fun mixedSelectionCannotDeleteOtherPersonsMessages() {
        assertFalse(ChatSelection.canDeleteAll(listOf(own, incoming), setOf(1, 2), 1))
        assertTrue(ChatSelection.canDeleteAll(listOf(own, incoming), setOf(1), 1))
        assertFalse(ChatSelection.canDeleteAll(listOf(own), setOf(1, 99), 1))
        assertFalse(ChatSelection.canDeleteAll(listOf(own), emptySet(), 1))
    }
    @Test fun removingLastSelectionExitsSelectionMode() {
        assertTrue(ChatSelection.toggle(setOf(1), 1).isEmpty())
    }
    @Test fun mediaExportKeepsAudioAndImageExtensionsDistinct() {
        assertEquals("Layermaxxing-1.jpg", ChatSelection.fileName(own.copy(kind = "image", attachmentMime = "image/jpeg")))
        assertEquals("Layermaxxing-1.m4a", ChatSelection.fileName(own.copy(kind = "voice", attachmentMime = "audio/mp4")))
        assertEquals("Layermaxxing-1.ogg", ChatSelection.fileName(own.copy(kind = "voice", attachmentMime = "audio/ogg")))
    }
}
