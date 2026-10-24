package at.gregor.layermaxxing

import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/** No free-form event API: callers can supply only fixed labels and bounded counters. */
internal enum class CrashTab { CHATS, MORE, CASTLES }
internal enum class CrashIslandView { ENTRY, MAP, HOME, VISIT, PLACE }
internal data class CrashMemory(val used: Long, val total: Long, val max: Long, val native: Long, val pssKb: Long) {
    fun text(): String = listOf(used, total, max, native, pssKb).joinToString(" ") { it.coerceIn(0, 1L shl 50).toString() }
}
internal data class CrashMetadata(val version: String, val code: Int, val build: String, val api: Int, val model: String)

internal object CrashFormatter {
    const val MAX_REPORT = 48 * 1024
    const val MAX_CAUSES = 6
    const val MAX_FRAMES = 32
    private val technical = Regex("[A-Za-z_][A-Za-z0-9_.$<>-]{0,95}")
    private fun token(value: String) = if (technical.matches(value)) value else "redacted"
    private fun text(value: String) = value.take(80).map { if ((it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9') || it in " ._-+") it else '_' }.joinToString("")
    fun header(meta: CrashMetadata, memory: CrashMemory, now: Long): String =
        "UTC ${Instant.ofEpochMilli(now)}\nApp ${text(meta.version)} code=${meta.code} build=${text(meta.build)}\n" +
            "Android API=${meta.api} model=${text(meta.model)}\nMemory bytes: JVM_used total max native; process_PSS_KB\n${memory.text()}\n"

    fun report(error: Throwable, meta: CrashMetadata, memory: CrashMemory, events: String, now: Long): String {
        val out = StringBuilder(8192).append(header(meta, memory, now)).append("Java crash (messages omitted)\n")
        var cause: Throwable? = error
        val seen = java.util.IdentityHashMap<Throwable, Boolean>()
        var depth = 0
        while (cause != null && depth < MAX_CAUSES && seen.put(cause, true) == null) {
            val current = cause
            out.append("Cause ").append(depth++).append(": ").append(token(current.javaClass.name)).append('\n')
            // Files and thread names are deliberately absent. Throwable text is never evaluated.
            current.stackTrace.take(MAX_FRAMES).forEach { frame ->
                val allowed = listOf("at.gregor.layermaxxing.", "android.", "androidx.", "java.", "javax.", "kotlin.", "kotlinx.", "dalvik.", "com.android.").any { frame.className.startsWith(it) }
                if (allowed) out.append(" at ").append(token(frame.className)).append('.').append(token(frame.methodName))
                    .append(':').append(frame.lineNumber.coerceIn(-1, 999999)).append('\n')
            }
            cause = current.cause
        }
        if (cause != null) out.append("Cause limit/cycle\n")
        out.append("Safe run events\n").append(events.take(8192))
        return out.toString().take(MAX_REPORT)
    }
}

/** Private, bounded snapshots. Atomic rename leaves the previous report intact on a failed write. */
internal class CrashStorage(private val directory: File) {
    private val crash get() = File(directory, "last.txt")
    private val run get() = File(directory, "run.txt")
    private val previousRun get() = File(directory, "previous-run.txt")
    private val dismissed get() = File(directory, "dismissed.txt")
    private val events = ArrayDeque<String>()
    private var writes = 0
    private var lastTab: CrashTab? = null
    private var lastIsland: String? = null
    @Synchronized private fun atomic(name: String, value: String) {
        check(directory.isDirectory || directory.mkdirs())
        val temp = File(directory, "$name.tmp")
        try {
            temp.outputStream().use { output ->
                val bytes = value.take(CrashFormatter.MAX_REPORT).toByteArray(Charsets.UTF_8)
                output.write(bytes, 0, minOf(bytes.size, CrashFormatter.MAX_REPORT))
                output.fd.sync()
            }
            check(temp.renameTo(File(directory, name)))
        } finally { temp.delete() }
    }
    private fun read(file: File): String = try {
        file.inputStream().use { input ->
            val bytes = ByteArray(CrashFormatter.MAX_REPORT)
            var size = 0
            while (size < bytes.size) {
                val count = input.read(bytes, size, bytes.size - size)
                if (count <= 0) break
                size += count
            }
            String(bytes, 0, size, Charsets.UTF_8)
        }
    } catch (_: Throwable) { "" }
    @Synchronized fun start() {
        // OS/native/low-memory termination may bypass the Java handler. Preserve the last
        // allowlisted breadcrumb file before STARTUP overwrites the current run.
        val prior = read(run)
        if (prior.isNotEmpty()) try { atomic("previous-run.txt", prior) } catch (_: Throwable) { }
        events.clear(); writes = 0; lastTab = null; lastIsland = null; event("STARTUP")
    }
    private fun event(label: String) {
        if (events.size == 32) events.removeFirst()
        events.addLast("${Instant.now()} $label")
        // Cap synchronous writes per process, including rapidly alternating tabs; memory remains current.
        if (writes < 128) {
            writes++
            try { atomic("run.txt", events.joinToString("\n")) } catch (_: Throwable) { /* diagnostics must not break navigation */ }
        }
    }
    @Synchronized fun tab(tab: CrashTab) { if (lastTab != tab) { lastTab = tab; event("TAB ${tab.name}") } }
    @Synchronized fun island(view: CrashIslandView, friends: Int, messages: Int, gridVersion: Int) {
        val label = "ISLAND ${view.name} friends=${friends.coerceIn(0, 100000)} messages=${messages.coerceIn(0, 100000)} gridVersion=${gridVersion.coerceIn(0, 999)}"
        if (lastIsland != label) { lastIsland = label; event(label) }
    }
    @Synchronized fun events(): String = if (events.isEmpty()) read(run) else events.joinToString("\n")
    @Synchronized fun previousEvents(): String = read(previousRun)
    @Synchronized fun save(report: String) { atomic("last.txt", report) }
    @Synchronized fun saved(): String = read(crash)
    // Full bounded report equality avoids timestamp collisions, without storing another full report.
    @Synchronized fun crashId(): String? = saved().takeIf { it.isNotEmpty() }?.let {
        java.security.MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) }
    }
    @Synchronized fun pending(): Boolean = crashId()?.let { it != read(dismissed) } ?: false
    @Synchronized fun dismiss() { crashId()?.let { atomic("dismissed.txt", it) } }
    @Synchronized fun clear(): Boolean {
        var success = true
        (listOf(crash, run, previousRun, dismissed) + directory.listFiles().orEmpty().filter { it.name.endsWith(".tmp") }).forEach {
            if (it.exists() && !it.delete()) success = false
        }
        events.clear(); lastTab = null; lastIsland = null
        return success
    }
}

/** Injection keeps failure/recursion tests from terminating the test runner. */
internal class CrashUncaughtHandler(
    private val previous: Thread.UncaughtExceptionHandler?,
    private val write: (Throwable) -> Unit,
    private val terminate: () -> Unit,
) : Thread.UncaughtExceptionHandler {
    private val recording = AtomicBoolean(false)
    private val forwarding = AtomicBoolean(false)
    override fun uncaughtException(thread: Thread, error: Throwable) {
        try {
            if (recording.compareAndSet(false, true)) try { write(error) } catch (_: Throwable) { /* preserve original crash */ }
        } finally {
            if (forwarding.compareAndSet(false, true)) {
                try { previous?.uncaughtException(thread, error) } finally { terminate() }
            } else {
                terminate()
            }
        }
    }
}
