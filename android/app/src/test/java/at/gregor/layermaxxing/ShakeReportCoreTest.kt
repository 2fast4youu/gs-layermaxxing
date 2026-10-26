package at.gregor.layermaxxing

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ShakeReportCoreTest {
    @Test fun pureMetadataReaderChecksCrcBoundsAndRoundtrip() {
        val png = shakeFixturePng()
        assertEquals("Shake report v1\nApp fixture code=83\n", ShakePng.extract(png))
        val corrupt = png.clone().also { it[29] = (it[29].toInt() xor 1).toByte() }
        assertThrows(IllegalArgumentException::class.java) { ShakePng.extract(corrupt) }
        val hugeChunk = png.clone().also { java.nio.ByteBuffer.wrap(it, 8, 4).putInt(Int.MAX_VALUE) }
        assertThrows(IllegalArgumentException::class.java) { ShakePng.extract(hugeChunk) }
    }

    @Test fun peaksGapWindowCooldownAndDisable() {
        val d = ShakeDetector()
        assertFalse(d.sample(0, 3.0, true))
        assertFalse(d.sample(50, 3.0, true))
        assertFalse(d.sample(100, 1.0, true))
        assertFalse(d.sample(200, 3.0, true))
        assertFalse(d.sample(300, 1.0, true))
        assertTrue(d.sample(400, 3.0, true))
        assertFalse(d.claim(12000))
        assertTrue(d.claim(12400))
        d.reset()
        assertFalse(d.sample(25000, 3.0, true))
        assertFalse(d.sample(26000, 1.0, true))
        assertFalse(d.sample(27000, 3.0, true)) // outside window
        assertFalse(d.sample(27200, 3.0, false)) // pause/disable discards peaks
        assertFalse(d.sample(27400, 3.0, true))
        assertFalse(d.sample(27500, 1.0, true))
        assertFalse(d.sample(27600, 3.0, true))
        assertFalse(d.sample(27700, 1.0, true))
        assertTrue(d.sample(27800, 3.0, true))
    }

    @Test fun retentionCountBytesDeleteAndOutputBound() {
        val directory = Files.createTempDirectory("shake-storage").toFile()
        try {
            val store = ShakeReportStorage(directory)
            repeat(6) { store.save(ByteArray(1024)) }
            assertEquals(5, directory.listFiles()!!.size)
            directory.listFiles()!!.forEach { it.delete() }
            repeat(5) { store.save(ByteArray(ShakePng.MAX_BYTES)) }
            assertEquals(3, directory.listFiles()!!.size)
            assertTrue(store.clear()); assertTrue(directory.listFiles()!!.isEmpty())
            val out = BoundedOutput(java.io.ByteArrayOutputStream(), 1)
            out.write(0)
            assertThrows(IllegalStateException::class.java) { out.write(1) }
        } finally { directory.deleteRecursively() }
    }

    @Test fun redactionCanariesNeverEnterDiagnosticText() {
        val secret = "TOKEN_password_key_chat_BODY_CANARY"
        val error = IllegalStateException(secret)
        val report = CrashFormatter.report(error, CrashMetadata("7.9.14-shake-debug1", 83, "debug", 34, "test"),
            CrashMemory(1, 2, 3, 4, 5), "TAB MORE\nISLAND MAP gridVersion=8", 0)
        assertFalse(report.contains(secret))
        assertTrue(report.contains("code=83")); assertTrue(report.contains("gridVersion=8"))
    }
}

/** A real, CRC-valid RGBA8 PNG shared by transport tests without Android graphics. */
internal fun shakeFixturePng(): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val stream = java.io.DataOutputStream(output)
    stream.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
    fun chunk(type: String, bytes: ByteArray) {
        val tag = type.toByteArray(Charsets.US_ASCII)
        stream.writeInt(bytes.size); stream.write(tag); stream.write(bytes)
        stream.writeInt(java.util.zip.CRC32().apply { update(tag); update(bytes) }.value.toInt())
    }
    chunk("IHDR", java.nio.ByteBuffer.allocate(13).putInt(1).putInt(1).put(8).put(6).put(0).put(0).put(0).array())
    val pixels = java.io.ByteArrayOutputStream()
    java.util.zip.DeflaterOutputStream(pixels).use { it.write(byteArrayOf(0, 127, 0, 0, 255.toByte())) }
    chunk("IDAT", pixels.toByteArray()); chunk("IEND", byteArrayOf())
    return ShakePng.embed(output.toByteArray(), "Shake report v1\nApp fixture code=83\n")
}
