package at.gregor.layermaxxing

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Synthetic only. No crash is sent to Android's real uncaught handler. */
class CrashDiagnosticsCoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val meta = CrashMetadata("synthetic", 77, "test", 34, "Synthetic model")
    private val memory = CrashMemory(-1, Long.MAX_VALUE, 200, 100, 30)
    private fun report(error: Throwable) = CrashFormatter.report(error, meta, memory, "STARTUP", 0)

    @Test fun processRestartRetainsPreviousIslandBreadcrumbsWithoutAJavaCrash() {
        val dir = folder.newFolder()
        val first = CrashStorage(dir)
        first.start(); first.tab(CrashTab.CASTLES)
        first.island(CrashIslandView.MAP, 20, 120, 7)
        val beforeKill = first.events()
        val next = CrashStorage(dir)
        next.start()
        assertEquals(beforeKill, next.previousEvents())
        assertTrue(next.previousEvents().contains("ISLAND MAP friends=20 messages=120 gridVersion=7"))
        assertFalse(next.events().contains("ISLAND MAP"))
        assertEquals("", next.saved())
        assertFalse(next.pending())
        assertTrue(next.clear())
        assertEquals("", CrashStorage(dir).previousEvents())
    }

    @Test fun privateMessagesCausesPathsAndThreadDataAreNeverFormatted() {
        val secrets = listOf("Bearer SECRET_TOKEN", "https://secret.invalid/account/peer-891", "password=hunter", "alice@example.invalid", "ciphertext-letter-811", "recovery-code-981")
        val error = IllegalStateException(secrets.joinToString(" "), IllegalArgumentException(secrets.reversed().joinToString(" ")))
        error.stackTrace = arrayOf(
            StackTraceElement("at.gregor.layermaxxing.IslandWorldKt", "IslandWorld", "/private/secret-file-991.kt", 7),
            StackTraceElement("at.gregor.layermaxxing.Bad\nBearer SECRET_TOKEN", "https://secret.invalid", "secret-file", 99),
            StackTraceElement("secret.peer891", "alice@example.invalid", "secret-file", 11),
        )
        val value = report(error)
        secrets.forEach { assertFalse(it, value.contains(it)) }
        assertFalse(value.contains("secret-file"))
        assertFalse(value.contains("secret.peer891"))
        assertTrue(value.contains("java.lang.IllegalStateException"))
        assertTrue(value.contains("java.lang.IllegalArgumentException"))
        assertTrue(value.contains("at.gregor.layermaxxing.IslandWorldKt.IslandWorld:7"))
        assertTrue(value.contains("redacted.redacted"))
    }

    @Test fun neverCallsThrowableTextAndBoundsFramesCausesAndOutput() {
        val poison = object : RuntimeException() {
            override val message: String get() = error("must not read message")
            override fun toString(): String = error("must not stringify throwable")
        }
        var tail: Throwable = poison
        repeat(30) { tail = IllegalStateException("SECRET".repeat(10000), tail) }
        tail.stackTrace = Array(4000) { StackTraceElement("at.gregor.layermaxxing.IslandWorldKt", "render", "secret", it) }
        val value = report(tail)
        assertTrue(value.length <= CrashFormatter.MAX_REPORT)
        assertEquals(CrashFormatter.MAX_CAUSES, Regex("Cause [0-9]+:").findAll(value).count())
        assertEquals(CrashFormatter.MAX_FRAMES, Regex("IslandWorldKt.render").findAll(value).count())
        assertTrue(value.contains("Cause limit/cycle"))
        assertFalse(value.contains("SECRET"))
        report(poison)
        val a = RuntimeException(); val b = RuntimeException(a); a.initCause(b)
        assertTrue(report(a).contains("Cause limit/cycle"))
    }

    @Test fun storagePersistsRotatesDismissesAndClearsWithoutDeletingOnDismiss() {
        val dir = folder.newFolder()
        val first = CrashStorage(dir)
        first.start(); first.tab(CrashTab.CASTLES)
        first.island(CrashIslandView.ENTRY, 1000000, -100, 10000)
        assertTrue(first.events().contains("friends=100000 messages=0 gridVersion=999"))
        first.save("synthetic crash one")
        assertTrue(CrashStorage(dir).pending())
        CrashStorage(dir).dismiss()
        val restart = CrashStorage(dir)
        assertFalse(restart.pending()); assertEquals("synthetic crash one", restart.saved())
        restart.start(); assertEquals("synthetic crash one", restart.saved())
        restart.save("synthetic crash two"); assertTrue(restart.pending())
        assertEquals("synthetic crash two", first.saved())
        assertFalse(dir.listFiles()!!.any { it.name.endsWith(".tmp") })
        restart.clear()
        assertEquals("", first.saved()); assertFalse(first.pending()); assertEquals("", restart.events())
    }

    @Test fun failedAtomicWriteKeepsPreviousReport() {
        val dir = folder.newFolder(); val store = CrashStorage(dir)
        store.save("previous synthetic crash")
        assertTrue(File(dir, "last.txt.tmp").mkdir())
        try { store.save("replacement"); fail("expected injected filesystem failure") } catch (_: java.io.IOException) { }
        assertEquals("previous synthetic crash", store.saved())
    }

    @Test fun eventsAreDeduplicatedBoundedAndDiskWritesStop() {
        val dir = folder.newFolder(); val store = CrashStorage(dir); store.start()
        repeat(1000) { store.tab(CrashTab.MORE) }
        assertEquals(2, store.events().lines().size)
        repeat(300) { store.tab(if (it % 2 == 0) CrashTab.CHATS else CrashTab.MORE) }
        assertEquals(32, store.events().lines().size)
        val persisted = File(dir, "run.txt").readText()
        repeat(100) { store.island(CrashIslandView.MAP, it, 0, 7) }
        assertEquals(persisted, File(dir, "run.txt").readText())
        store.save("x".repeat(CrashFormatter.MAX_REPORT * 2))
        assertEquals(CrashFormatter.MAX_REPORT.toLong(), File(dir, "last.txt").length())
        assertTrue(File(dir, "run.txt").length() < 8192)
        store.save("🦊".repeat(CrashFormatter.MAX_REPORT))
        assertTrue(File(dir, "last.txt").length() <= CrashFormatter.MAX_REPORT)
    }

    @Test fun failingWriterStillDelegatesOriginalThrowableAndThread() {
        val error = IllegalStateException("synthetic")
        val thread = Thread.currentThread()
        var delegated = 0; var terminated = 0
        val handler = CrashUncaughtHandler(Thread.UncaughtExceptionHandler { t, e ->
            assertSame(thread, t); assertSame(error, e); delegated++
        }, { throw IllegalStateException("writer failed") }, { terminated++ })
        handler.uncaughtException(thread, error)
        assertEquals(1, delegated); assertEquals(1, terminated)
    }

    @Test fun recursiveWriterAndPriorHandlerCannotRecurseForever() {
        var writes = 0; var delegated = 0; var terminated = 0
        val error = RuntimeException("synthetic")
        lateinit var handler: CrashUncaughtHandler
        handler = CrashUncaughtHandler(Thread.UncaughtExceptionHandler { t, e ->
            delegated++; handler.uncaughtException(t, e)
        }, { writes++; handler.uncaughtException(Thread.currentThread(), it) }, { terminated++ })
        handler.uncaughtException(Thread.currentThread(), error)
        assertEquals(1, writes); assertEquals(1, delegated); assertTrue(terminated in 1..3)
    }

    @Test fun absentOrThrowingPriorStillTerminates() {
        var terminated = 0
        CrashUncaughtHandler(null, {}, { terminated++ }).uncaughtException(Thread.currentThread(), RuntimeException())
        try {
            CrashUncaughtHandler(Thread.UncaughtExceptionHandler { _, _ -> throw IllegalStateException() }, {}, { terminated++ })
                .uncaughtException(Thread.currentThread(), RuntimeException())
        } catch (_: IllegalStateException) { /* original prior handler semantics */ }
        assertEquals(2, terminated)
    }
}
