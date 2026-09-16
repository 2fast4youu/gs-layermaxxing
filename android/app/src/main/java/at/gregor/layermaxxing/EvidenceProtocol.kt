package at.gregor.layermaxxing

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.text.Normalizer
import java.util.Base64
import javax.crypto.KeyGenerator

data class LetterMetadata(
    val title: String,
    val coverNote: String,
    val recipientIds: List<Long>,
    val groupId: Long?,
    val mode: String,
    val releaseAt: Long?,
    val randomFrom: Long?,
    val randomTo: Long?,
    val oneTime: Boolean,
)

data class LetterEvidence(
    val protocolVersion: Int,
    val canonicalMetadata: String,
    val commitmentSalt: String,
    val plaintextSha256: String,
    val commitment: String,
    val publicSigningKey: String,
    val publicKeyFingerprint: String,
    val signature: String,
) {
    val hasPrivateSigningKey: Boolean get() = false
}

data class SealedAttachment(
    val name: String,
    val mime: String,
    val ciphertext: String,
    val nonce: String,
)

data class SealedLetter(
    val ciphertext: String,
    val nonce: String,
    val releaseKey: String,
    val evidence: LetterEvidence,
    val attachment: SealedAttachment? = null,
) {
    fun toVerificationEvidence() = VerificationEvidence(
        protocolVersion = evidence.protocolVersion,
        canonicalMetadata = evidence.canonicalMetadata,
        commitmentSalt = evidence.commitmentSalt,
        plaintextSha256 = evidence.plaintextSha256,
        commitment = evidence.commitment,
        publicSigningKey = evidence.publicSigningKey,
        publicKeyFingerprint = evidence.publicKeyFingerprint,
        signature = evidence.signature,
        ciphertext = ciphertext,
        nonce = nonce,
        releaseKey = releaseKey,
        releaseKeyFingerprint = EvidenceProtocol.sha256Hex(Base64.getDecoder().decode(releaseKey)),
        attachment = attachment,
    )
}

data class VerificationEvidence(
    val protocolVersion: Int,
    val canonicalMetadata: String,
    val commitmentSalt: String,
    val plaintextSha256: String,
    val commitment: String,
    val publicSigningKey: String,
    val publicKeyFingerprint: String,
    val signature: String,
    val ciphertext: String,
    val nonce: String,
    val releaseKey: String?,
    val releaseKeyFingerprint: String?,
    val attachment: SealedAttachment? = null,
)

data class LocalVerification(
    val valid: Boolean,
    val status: String,
    val plaintext: String? = null,
    val attachment: ByteArray? = null,
)

object EvidenceProtocol {
    private val random = SecureRandom()
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()
    private val commitPrefix = "GS-LM-COMMIT-V1".toByteArray()
    private val sealPrefix = "GS-LM-SEAL-V1".toByteArray()
    private val attachmentMarker = "\nGS-LM-ATTACHMENT-V1\n".toByteArray()

    fun canonicalMetadata(metadata: LetterMetadata): String = buildString {
        append("{\"cover_note\":").append(jsonString(metadata.coverNote))
        append(",\"group_id\":").append(metadata.groupId ?: "null")
        append(",\"message_class\":\"letter\"")
        append(",\"mode\":").append(jsonString(metadata.mode))
        append(",\"one_time\":").append(metadata.oneTime)
        append(",\"random_from\":").append(metadata.randomFrom ?: "null")
        append(",\"random_to\":").append(metadata.randomTo ?: "null")
        append(",\"recipient_ids\":[").append(metadata.recipientIds.distinct().sorted().joinToString(",")).append("]")
        append(",\"release_at\":").append(metadata.releaseAt ?: "null")
        append(",\"title\":").append(jsonString(metadata.title)).append("}")
    }

    fun commitment(plaintext: ByteArray, metadata: ByteArray, salt: ByteArray): ByteArray =
        sha256(commitPrefix + salt + metadata + plaintext)

    fun seal(
        plaintext: String,
        metadata: LetterMetadata,
        attachmentName: String? = null,
        attachmentMime: String? = null,
        attachmentBytes: ByteArray? = null,
    ): SealedLetter {
        require(listOf(attachmentName, attachmentMime, attachmentBytes).all { it == null } ||
            listOf(attachmentName, attachmentMime, attachmentBytes).all { it != null }) { "Anhangsdaten sind unvollständig" }
        val canonical = canonicalMetadata(metadata)
        val canonicalBytes = canonical.toByteArray(Charsets.UTF_8)
        val normalized = Normalizer.normalize(plaintext, Normalizer.Form.NFC).toByteArray(Charsets.UTF_8)
        val salt = ByteArray(32).also(random::nextBytes)
        val commitment = commitment(normalized, canonicalBytes, salt)
        val generator = KeyGenerator.getInstance("AES").apply { init(256) }
        val key = generator.generateKey().encoded
        val keyB64 = encoder.encodeToString(key)
        val nonce = ByteArray(12).also(random::nextBytes)
        val encrypted = CryptoBox.encryptBytes(normalized, keyB64, canonicalBytes, nonce)

        val signingGenerator = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"), random)
        }
        val signingPair = signingGenerator.generateKeyPair()
        val sealDigest = sha256(
            sealPrefix + canonicalBytes + commitment + decoder.decode(encrypted.ciphertext) + nonce
        )
        val signature = Signature.getInstance("NONEwithECDSA").run {
            initSign(signingPair.private, random)
            update(sealDigest)
            sign()
        }
        val publicKey = signingPair.public.encoded
        val evidence = LetterEvidence(
            protocolVersion = 1,
            canonicalMetadata = canonical,
            commitmentSalt = encoder.encodeToString(salt),
            plaintextSha256 = encoder.encodeToString(sha256(normalized)),
            commitment = encoder.encodeToString(commitment),
            publicSigningKey = encoder.encodeToString(publicKey),
            publicKeyFingerprint = sha256Hex(publicKey),
            signature = encoder.encodeToString(signature),
        )
        val attachment = if (attachmentBytes != null && attachmentName != null && attachmentMime != null) {
            var attachmentNonce: ByteArray
            do attachmentNonce = ByteArray(12).also(random::nextBytes) while (attachmentNonce.contentEquals(nonce))
            val result = CryptoBox.encryptBytes(
                attachmentBytes, keyB64, attachmentAad(canonicalBytes, attachmentName, attachmentMime), attachmentNonce,
            )
            SealedAttachment(attachmentName, attachmentMime, result.ciphertext, result.nonce)
        } else null
        return SealedLetter(encrypted.ciphertext, encrypted.nonce, keyB64, evidence, attachment)
    }

    fun verify(evidence: VerificationEvidence): LocalVerification = try {
        require(evidence.protocolVersion == 1)
        val metadata = evidence.canonicalMetadata.toByteArray(Charsets.UTF_8)
        val commitment = decoder.decode(evidence.commitment)
        val ciphertext = decoder.decode(evidence.ciphertext)
        val nonce = decoder.decode(evidence.nonce)
        require(nonce.size == 12)
        val publicKeyBytes = decoder.decode(evidence.publicSigningKey)
        require(sha256Hex(publicKeyBytes) == evidence.publicKeyFingerprint)
        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKeyBytes))
        val sealDigest = sha256(sealPrefix + metadata + commitment + ciphertext + nonce)
        require(Signature.getInstance("NONEwithECDSA").run {
            initVerify(publicKey); update(sealDigest); verify(decoder.decode(evidence.signature))
        })
        val key = evidence.releaseKey ?: return LocalVerification(true, "locked")
        val keyBytes = decoder.decode(key)
        require(keyBytes.size == 32)
        require(sha256Hex(keyBytes) == evidence.releaseKeyFingerprint)
        val clear = CryptoBox.decryptBytes(evidence.ciphertext, evidence.nonce, key, metadata)
        require(MessageDigest.isEqual(sha256(clear), decoder.decode(evidence.plaintextSha256)))
        require(MessageDigest.isEqual(
            commitment(clear, metadata, decoder.decode(evidence.commitmentSalt)), commitment,
        ))
        val plaintext = clear.toString(Charsets.UTF_8)
        require(Normalizer.normalize(plaintext, Normalizer.Form.NFC) == plaintext)
        val attachmentBytes = evidence.attachment?.let {
            require(it.nonce != evidence.nonce)
            CryptoBox.decryptBytes(
                it.ciphertext, it.nonce, key,
                attachmentAad(metadata, it.name, it.mime),
            )
        }
        LocalVerification(true, "verified", plaintext, attachmentBytes)
    } catch (error: Exception) {
        LocalVerification(false, error.message ?: "verification failed")
    }

    internal fun sha256Hex(bytes: ByteArray): String = sha256(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
    fun attachmentAad(metadata: String, name: String, mime: String): ByteArray =
        attachmentAad(metadata.toByteArray(Charsets.UTF_8), name, mime)
    private fun attachmentAad(metadata: ByteArray, name: String, mime: String) =
        metadata + attachmentMarker + name.toByteArray(Charsets.UTF_8) + byteArrayOf('\n'.code.toByte()) + mime.toByteArray(Charsets.UTF_8)

    internal fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
            }
        }
        append('"')
    }
}
