package at.gregor.layermaxxing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CryptoBoxTest {
    @Test
    fun roundTripPreservesUnicodeText() {
        val text = "Geheim bis später 🔐 Grüße!"
        val encrypted = CryptoBox.encrypt(text)
        assertNotEquals(text, encrypted.ciphertext)
        assertEquals(text, CryptoBox.decrypt(encrypted.ciphertext, encrypted.nonce, encrypted.key))
    }

    @Test(expected = Exception::class)
    fun wrongKeyCannotDecrypt() {
        val first = CryptoBox.encrypt("Nachricht")
        val second = CryptoBox.encrypt("andere")
        CryptoBox.decrypt(first.ciphertext, first.nonce, second.key)
    }

    @Test
    fun attachmentRoundTripUsesMessageKey() {
        val message = CryptoBox.encrypt("Text")
        val bytes = byteArrayOf(0, 1, 2, 127, -1)
        val encrypted = CryptoBox.encryptBytes(bytes, message.key)
        assertArrayEquals(bytes, CryptoBox.decryptBytes(encrypted.ciphertext, encrypted.nonce, message.key))
    }
}
