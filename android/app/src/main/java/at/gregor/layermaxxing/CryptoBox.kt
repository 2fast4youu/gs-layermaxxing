package at.gregor.layermaxxing

import java.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoBox {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128
    private val random = SecureRandom()

    data class Encrypted(val ciphertext: String, val nonce: String, val key: String)
    data class EncryptedBytes(val ciphertext: String, val nonce: String)

    fun encrypt(plaintext: String): Encrypted {
        val generator = KeyGenerator.getInstance("AES")
        generator.init(256)
        val key = generator.generateKey().encoded
        val encrypted = encryptBytes(plaintext.toByteArray(Charsets.UTF_8), encode(key))
        return Encrypted(encrypted.ciphertext, encrypted.nonce, encode(key))
    }

    fun encryptBytes(plaintext: ByteArray, key: String, aad: ByteArray = byteArrayOf()): EncryptedBytes {
        val nonce = ByteArray(12).also(random::nextBytes)
        return encryptBytes(plaintext, key, aad, nonce)
    }

    fun encryptBytes(plaintext: ByteArray, key: String, aad: ByteArray, nonce: ByteArray): EncryptedBytes {
        require(nonce.size == 12) { "AES-GCM benötigt eine 12-Byte-Nonce" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(decode(key), "AES"), GCMParameterSpec(TAG_BITS, nonce))
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        return EncryptedBytes(encode(cipher.doFinal(plaintext)), encode(nonce))
    }

    fun decrypt(ciphertext: String, nonce: String, key: String): String =
        decryptBytes(ciphertext, nonce, key).toString(Charsets.UTF_8)

    fun decryptBytes(ciphertext: String, nonce: String, key: String, aad: ByteArray = byteArrayOf()): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(decode(key), "AES"), GCMParameterSpec(TAG_BITS, decode(nonce)))
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        return cipher.doFinal(decode(ciphertext))
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)
}
