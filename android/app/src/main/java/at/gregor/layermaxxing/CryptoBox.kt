package at.gregor.layermaxxing

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoBox {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    data class Encrypted(val ciphertext: String, val nonce: String, val key: String)
    data class EncryptedBytes(val ciphertext: String, val nonce: String)

    fun encrypt(plaintext: String): Encrypted {
        val generator = KeyGenerator.getInstance("AES")
        generator.init(256)
        val key = generator.generateKey().encoded
        val encrypted = encryptBytes(plaintext.toByteArray(Charsets.UTF_8), encode(key))
        return Encrypted(encrypted.ciphertext, encrypted.nonce, encode(key))
    }

    fun encryptBytes(plaintext: ByteArray, key: String): EncryptedBytes {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(decode(key), "AES"))
        return EncryptedBytes(encode(cipher.doFinal(plaintext)), encode(cipher.iv))
    }

    fun decrypt(ciphertext: String, nonce: String, key: String): String =
        decryptBytes(ciphertext, nonce, key).toString(Charsets.UTF_8)

    fun decryptBytes(ciphertext: String, nonce: String, key: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(decode(key), "AES"), GCMParameterSpec(TAG_BITS, decode(nonce)))
        return cipher.doFinal(decode(ciphertext))
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)
}
