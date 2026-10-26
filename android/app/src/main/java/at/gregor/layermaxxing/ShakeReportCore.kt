package at.gregor.layermaxxing

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.CRC32

/** Three separate peaks in 1.2s; all entry points share a 12s monotonic cooldown. */
internal class ShakeDetector {
    private val peaks = ArrayDeque<Long>()
    private var lastPeak = Long.MIN_VALUE
    private var lastCapture: Long? = null
    private var armed = true
    fun reset() { peaks.clear(); lastPeak = Long.MIN_VALUE; armed = true }
    fun claim(now: Long): Boolean {
        if (lastCapture?.let { now - it < 12_000 } == true) return false
        lastCapture = now; reset(); return true
    }
    fun sample(now: Long, g: Double, active: Boolean): Boolean {
        if (!active) { reset(); return false }
        while (peaks.isNotEmpty() && now - peaks.first() > 1200) peaks.removeFirst()
        if (!g.isFinite()) return false
        if (g < 1.6) armed = true
        if (!armed || g < 2.4 || (lastPeak != Long.MIN_VALUE && now - lastPeak < 160)) return false
        armed = false; lastPeak = now; peaks.addLast(now)
        return peaks.size >= 3 && claim(now)
    }
}

internal class BoundedOutput(private val output: OutputStream, private val max: Int) : OutputStream() {
    private var size = 0
    override fun write(b: Int) { check(size < max); output.write(b); size++ }
    override fun write(b: ByteArray, off: Int, len: Int) {
        check(len >= 0 && size.toLong() + len <= max); output.write(b, off, len); size += len
    }
    override fun flush() = output.flush()
    override fun close() = output.close()
}

internal object ShakePng {
    const val MAX_BYTES = 8 * 1024 * 1024
    const val MAX_META = 64 * 1024
    const val MAX_PIXELS = 2_000_000
    private val signature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
    private val prefix = "gs-shake-diagnostics\u0000\u0000\u0000\u0000\u0000".toByteArray(Charsets.UTF_8)
    private data class Chunk(val start: Int, val end: Int, val type: String, val data: ByteArray)
    private fun chunks(png: ByteArray): List<Chunk> {
        require(png.size in 45..MAX_BYTES && png.take(8).toByteArray().contentEquals(signature))
        val result = mutableListOf<Chunk>()
        var offset = 8
        while (offset < png.size) {
            require(result.size < 2048 && offset.toLong() + 12 <= png.size)
            val size = ByteBuffer.wrap(png, offset, 4).int
            require(size >= 0 && offset.toLong() + size + 12 <= png.size)
            val end = offset + size + 12
            val type = String(png, offset + 4, 4, Charsets.US_ASCII)
            val crc = CRC32().apply { update(png, offset + 4, size + 4) }.value
            require(crc == (ByteBuffer.wrap(png, offset + 8 + size, 4).int.toLong() and 0xffffffffL))
            result.add(Chunk(offset, end, type, png.copyOfRange(offset + 8, offset + 8 + size)))
            offset = end
        }
        require(result.first().type == "IHDR" && result.first().data.size == 13)
        val header = ByteBuffer.wrap(result.first().data)
        val w = header.int; val h = header.int
        require(w > 0 && h > 0 && w.toLong() * h <= MAX_PIXELS)
        require(result.count { it.type == "IHDR" } == 1 && result.any { it.type == "IDAT" })
        require(result.last().type == "IEND" && result.last().data.isEmpty() && result.count { it.type == "IEND" } == 1)
        return result
    }
    fun embed(png: ByteArray, diagnostics: String): ByteArray {
        val chunks = chunks(png)
        require(chunks.none { it.type in listOf("iTXt", "tEXt", "zTXt") })
        val text = diagnostics.toByteArray(Charsets.UTF_8)
        require(text.size <= MAX_META && diagnostics.startsWith("Shake report v1\n"))
        val payload = prefix + text
        val type = "iTXt".toByteArray(Charsets.US_ASCII)
        val output = ByteArrayOutputStream()
        DataOutputStream(BoundedOutput(output, MAX_BYTES)).use { out ->
            out.write(png, 0, chunks.last().start)
            out.writeInt(payload.size); out.write(type); out.write(payload)
            out.writeInt(CRC32().apply { update(type); update(payload) }.value.toInt())
            out.write(png, chunks.last().start, 12)
        }
        return output.toByteArray()
    }
    fun extract(png: ByteArray): String {
        val found = chunks(png).filter { it.type == "iTXt" }
        require(found.size == 1)
        val data = found.single().data
        require(data.size <= MAX_META + prefix.size && data.take(prefix.size).toByteArray().contentEquals(prefix))
        return Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(data, prefix.size, data.size - prefix.size)).toString()
    }
    fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var count = 0L
        while (true) {
            val n = input.read(buffer); if (n < 0) break
            count += n; require(count <= MAX_BYTES); digest.update(buffer, 0, n)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}

internal class ShakeReportStorage(private val directory: File) {
    @Synchronized fun save(png: ByteArray): File {
        require(png.size <= ShakePng.MAX_BYTES)
        check(directory.isDirectory || directory.mkdirs())
        val id = UUID.randomUUID().toString()
        val temporary = File(directory, "$id.tmp")
        val target = File(directory, "$id.png")
        try {
            temporary.outputStream().use { it.write(png); it.fd.sync() }
            check(temporary.renameTo(target))
            retain()
            return target
        } finally { temporary.delete() }
    }
    @Synchronized fun retain() {
        val files = directory.listFiles().orEmpty().filter { it.extension == "png" }.sortedWith(compareBy<File> { it.lastModified() }.thenBy { it.name }).toMutableList()
        var bytes = files.sumOf { it.length() }
        while (files.size > 5 || bytes > 30L * 1024 * 1024) {
            val first = files.removeAt(0); val size = first.length()
            check(first.delete()); bytes -= size
        }
    }
    @Synchronized fun clear(): Boolean = directory.listFiles().orEmpty().map { it.delete() }.all { it }
}
