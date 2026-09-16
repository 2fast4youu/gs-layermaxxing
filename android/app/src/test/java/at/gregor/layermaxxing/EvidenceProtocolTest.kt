package at.gregor.layermaxxing

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceProtocolTest {
    private val metadata = LetterMetadata(
        title = "Versiegelt", coverNote = "Offen lesbar", recipientIds = listOf(9, 2),
        groupId = null, mode = "timed", releaseAt = 1_900_000_060,
        randomFrom = null, randomTo = null, oneTime = false,
    )

    @Test
    fun canonicalMetadataIsStableAndSorted() {
        assertEquals(
            "{\"cover_note\":\"Offen lesbar\",\"group_id\":null,\"message_class\":\"letter\",\"mode\":\"timed\",\"one_time\":false,\"random_from\":null,\"random_to\":null,\"recipient_ids\":[2,9],\"release_at\":1900000060,\"title\":\"Versiegelt\"}",
            EvidenceProtocol.canonicalMetadata(metadata),
        )
    }

    @Test
    fun hashesCommitmentExplicitNoncesAadAndSignatureRoundTrip() {
        val decomposed = "Gru\u0308ße 🔐"
        val sealed = EvidenceProtocol.seal(decomposed, metadata, "bild.bin", "application/octet-stream", byteArrayOf(0, 1, 2))
        assertEquals(12, Base64.getDecoder().decode(sealed.nonce).size)
        assertEquals(12, Base64.getDecoder().decode(sealed.attachment!!.nonce).size)
        assertNotEquals(sealed.nonce, sealed.attachment.nonce)
        assertEquals(32, Base64.getDecoder().decode(sealed.releaseKey).size)
        assertFalse(sealed.evidence.hasPrivateSigningKey)

        val verification = EvidenceProtocol.verify(sealed.toVerificationEvidence())
        assertTrue(verification.valid)
        assertEquals(Normalizer.normalize(decomposed, Normalizer.Form.NFC), verification.plaintext)
        assertArrayEquals(byteArrayOf(0, 1, 2), verification.attachment)

        val clear = Normalizer.normalize(decomposed, Normalizer.Form.NFC).toByteArray()
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(clear), Base64.getDecoder().decode(sealed.evidence.plaintextSha256))
        val wrongMetadata = sealed.toVerificationEvidence().copy(canonicalMetadata = sealed.evidence.canonicalMetadata + " ")
        assertFalse(EvidenceProtocol.verify(wrongMetadata).valid)
    }

    @Test
    fun attachmentCiphertextCannotBeOpenedWithBodyAad() {
        val sealed = EvidenceProtocol.seal("Text", metadata, "a.bin", "application/octet-stream", byteArrayOf(5, 6))
        val attachment = sealed.attachment!!
        val failed = runCatching {
            CryptoBox.decryptBytes(
                attachment.ciphertext, attachment.nonce, sealed.releaseKey,
                sealed.evidence.canonicalMetadata.toByteArray(),
            )
        }
        assertTrue(failed.isFailure)
    }

    @Test
    fun matchesFrozenPythonCrossImplementationVector() {
        val frozen = LetterMetadata(
            title = "Über Nacht: Prüfbericht / Test",
            coverNote = "Grüße heute – Kaffee Nr. 3",
            recipientIds = listOf(7, 3), groupId = null, mode = "random",
            releaseAt = null, randomFrom = 1_900_000_060, randomTo = 1_900_003_700, oneTime = false,
        )
        val canonical = EvidenceProtocol.canonicalMetadata(frozen)
        val salt = ByteArray(32) { it.toByte() }
        val plaintext = ("Geheim: Grüße!" + '\n' + "Zweite Zeile 🔐").toByteArray(Charsets.UTF_8)
        fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        assertEquals(
            "c61fe71428a39b0d9b9dcced61047b36afac40c2cf539ec0a0886d4c3ec440a6",
            hex(MessageDigest.getInstance("SHA-256").digest(plaintext)),
        )
        assertEquals(
            "3ed425b7c5b48f1245df2b04b670dd5773642f1602568ba4d848291bef7ffefc",
            hex(EvidenceProtocol.commitment(plaintext, canonical.toByteArray(Charsets.UTF_8), salt)),
        )
        assertEquals(
            "79ca93a972b3be230c304aa07522cc82f96e9be924c5b45bb6cc2c2882221683",
            hex(MessageDigest.getInstance("SHA-256").digest(
                EvidenceProtocol.attachmentAad(canonical, "Foto mit Zeilen.png", "image/png"),
            )),
        )
    }
}
