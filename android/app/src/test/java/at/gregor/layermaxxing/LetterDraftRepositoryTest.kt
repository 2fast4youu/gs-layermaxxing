package at.gregor.layermaxxing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LetterDraftRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun populated(mode: ComposeMode, basis: TimeBasis) = LetterDraft(101, "SYNTHETIC private body").apply {
        title = "SYNTHETIC title"; coverNote = "SYNTHETIC cover"; selected = setOf(101, 102)
        this.mode = mode; timeBasis = basis; oneTime = true
        amount = "009"; unit = DelayUnit.HOURS
        randomStart = "02"; randomEnd = "007"; randomStartUnit = DelayUnit.DAYS; randomEndUnit = DelayUnit.DAYS
        dateTime = LocalDateTime.of(2030, 7, 9, 13, 4)
        windowStart = LocalDateTime.of(2030, 8, 1, 10, 3); windowEnd = LocalDateTime.of(2030, 8, 2, 11, 5)
        attachment = Triple("synthetic.txt", "text/plain", byteArrayOf(1, 2, 3, 4))
    }
    private fun same(expected: LetterDraft, actual: LetterDraft) {
        assertEquals(expected.id, actual.id); assertEquals(expected.text, actual.text)
        assertEquals(expected.title, actual.title); assertEquals(expected.coverNote, actual.coverNote)
        assertEquals(expected.selected, actual.selected); assertEquals(expected.groupId, actual.groupId)
        assertEquals(expected.mode, actual.mode); assertEquals(expected.timeBasis, actual.timeBasis)
        assertEquals(expected.amount, actual.amount); assertEquals(expected.unit, actual.unit)
        assertEquals(expected.randomStart, actual.randomStart); assertEquals(expected.randomEnd, actual.randomEnd)
        assertEquals(expected.randomStartUnit, actual.randomStartUnit); assertEquals(expected.randomEndUnit, actual.randomEndUnit)
        assertEquals(expected.dateTime, actual.dateTime); assertEquals(expected.windowStart, actual.windowStart); assertEquals(expected.windowEnd, actual.windowEnd)
        assertEquals(expected.oneTime, actual.oneTime)
        assertEquals(expected.attachment!!.first, actual.attachment!!.first); assertEquals(expected.attachment!!.second, actual.attachment!!.second)
        assertArrayEquals(expected.attachment!!.third, actual.attachment!!.third)
    }
    @Test fun restartRestoresEveryModeAndBasisAndFullPrivateAttachment() {
        val dir = temporary.newFolder()
        val repository = LetterDraftRepository(dir)
        val expected = ReleaseSchedule.choices.flatMap { mode -> TimeBasis.entries.map { basis ->
            repository.bind(populated(mode, basis).also { if (mode == ComposeMode.MANUAL) it.groupId = 700 })
        } }
        val fresh = LetterDraftRepository(dir).list()
        assertEquals(6, fresh.size)
        expected.forEach { before -> same(before, fresh.first { it.draft.id == before.id }.draft) }
        assertFalse(dir.walk().filter { it.isFile }.any { "token" in it.readText() })
    }
    @Test fun editsPersistWithoutCloseAndNoEmptyOrDuplicateRecordsAndStableOrdering() {
        val dir = temporary.newFolder(); var time = 100L
        val repository = LetterDraftRepository(dir) { time }
        repository.bind(LetterDraft(null)); assertTrue(repository.list().isEmpty())
        val first = repository.bind(LetterDraft(101, "first")); time++
        val second = repository.bind(LetterDraft(102, "second")); time++
        first.text = "changed without closing"
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400))
        assertEquals("changed without closing", LetterDraftRepository(dir).list().first().draft.text)
        repeat(5) { first.flush() }
        val fresh = LetterDraftRepository(dir).list()
        assertEquals(listOf(first.id, second.id), fresh.map { it.draft.id })
        assertEquals("changed without closing", fresh.first().draft.text)
        assertEquals(2, dir.listFiles()!!.count { it.extension == "json" })
    }
    @Test fun accountAndServerIsolationSurviveLogoutAndTokenRotation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefix = temporary.root.name + java.util.UUID.randomUUID()
        val a = LetterDraftRepository.forAccount(context, "$prefix/server-a", "alice", 101)
        val draft = a.bind(populated(ComposeMode.RANDOM, TimeBasis.ABSOLUTE))
        assertTrue(LetterDraftRepository.forAccount(context, "$prefix/server-a", "bob", 102).list().isEmpty())
        assertTrue(LetterDraftRepository.forAccount(context, "$prefix/server-b", "alice", 101).list().isEmpty())
        SessionStore(context).clear()
        val reopened = LetterDraftRepository.forAccount(context, "$prefix/server-a", "alice")
        same(draft, reopened.list().single().draft)
        assertTrue(runCatching { reopened.bind(draft) }.isFailure)
        // A late edit remains bound to A, never written into Bob's repository.
        draft.text = "late account A edit"; assertTrue(draft.flush())
        assertTrue(LetterDraftRepository.forAccount(context, "$prefix/server-a", "bob", 102).list().isEmpty())
        assertEquals("late account A edit", reopened.list().single().draft.text)
    }
    @Test fun externalDiskRefreshReplacesCleanCachedViewWithOneCanonicalRecord() {
        val dir = temporary.newFolder()
        val writer = LetterDraftRepository(dir)
        val source = writer.bind(populated(ComposeMode.RANDOM, TimeBasis.ABSOLUTE))
        val reader = LetterDraftRepository(dir)
        val cached = reader.list().single().draft
        assertSame(cached, reader.list().single().draft)
        source.text = "external body"; source.title = "external title"
        source.attachment = Triple("new.txt", "text/plain", byteArrayOf(9, 8))
        assertTrue(source.flush())
        val refreshed = reader.list().single().draft
        assertNotSame(cached, refreshed); same(source, refreshed)
        assertSame(refreshed, reader.list().single().draft)
        assertTrue(refreshed.discard())
        assertTrue(cached.consumed); assertTrue(reader.list().isEmpty())
    }
    @Test fun cachedStaleUnchangedFlushNeverOverwritesNewerDiskBeforeOrAfterRefresh() {
        val dir = temporary.newFolder()
        val source = LetterDraftRepository(dir).bind(LetterDraft(101, "original"))
        val reader = LetterDraftRepository(dir)
        val stale = reader.list().single().draft
        source.text = "newer disk"; assertTrue(source.flush())
        val file = File(dir, "${source.id}.json")
        val bytes = file.readBytes()
        assertTrue(stale.flush()); assertArrayEquals(bytes, file.readBytes())
        val refreshed = reader.list().single().draft
        assertEquals("newer disk", refreshed.text)
        repeat(3) { assertTrue(stale.flush()); assertTrue(refreshed.flush()) }
        assertArrayEquals(bytes, file.readBytes())
        assertSame(refreshed, reader.list().single().draft)
    }
    @Test fun pendingLocalVsNewerDiskKeepsLocalEditsAndRejectsOverwrite() {
        val dir = temporary.newFolder()
        val source = LetterDraftRepository(dir).bind(LetterDraft(101, "original"))
        val reader = LetterDraftRepository(dir)
        val local = reader.list().single().draft
        local.text = "pending local body"
        assertSame(local, reader.list().single().draft)
        source.text = "external body"; assertTrue(source.flush())
        val file = File(dir, "${source.id}.json")
        val bytes = file.readBytes()
        repeat(3) {
            assertSame(local, reader.list().single().draft)
            assertEquals("pending local body", local.text)
            assertFalse(local.flush()); assertTrue(local.saveStatus!!.startsWith("Fehler:"))
            assertArrayEquals(bytes, file.readBytes())
        }
        assertEquals("external body", LetterDraftRepository(dir).list().single().draft.text)
    }
    @Test fun conflictsRetainedWithoutOverwriteFromOldViewOrUnbasedSavedState() {
        val dir = temporary.newFolder()
        val source = LetterDraftRepository(dir).bind(LetterDraft(101, "original"))
        val reader = LetterDraftRepository(dir)
        val stale = reader.list().single().draft
        source.text = "newer disk"; assertTrue(source.flush())
        val refreshed = reader.list().single().draft
        stale.text = "late stale view edit"
        val file = File(dir, "${source.id}.json")
        val bytes = file.readBytes()
        repeat(3) {
            assertFalse(stale.flush()); assertEquals("late stale view edit", stale.text)
            assertSame(refreshed, reader.list().single().draft)
            assertArrayEquals(bytes, file.readBytes())
        }
        val restored = LetterDraft(101, "stale saved state").apply { id = source.id }
        LetterDraftRepository(dir).bind(restored)
        assertFalse(restored.flush()); assertEquals("stale saved state", restored.text)
        assertArrayEquals(bytes, file.readBytes())
        // An unresolved old editor must not prevent the canonical view from saving new edits.
        refreshed.text = "current canonical edit"; assertTrue(refreshed.flush())
        assertFalse(stale.flush()); assertFalse(restored.flush())
        assertEquals("current canonical edit", LetterDraftRepository(dir).list().single().draft.text)
    }
    @Test fun failedStorageIsReadableRetainsMemoryAndCanRetry() {
        val dir = File(temporary.root, "blocked").apply { writeText("not a directory") }
        val repository = LetterDraftRepository(dir)
        val draft = repository.bind(LetterDraft(101, "must survive"))
        assertFalse(draft.flush()); assertTrue(draft.saveStatus!!.startsWith("Fehler:")); assertEquals("must survive", draft.text)
        assertTrue(dir.delete()); assertTrue(draft.flush())
        assertEquals("must survive", LetterDraftRepository(dir).list().single().draft.text)
    }
    @Test fun atomicBackupRecoveryAndCorruptRecordNeverSilentlyDeletesGoodDraft() {
        val dir = temporary.newFolder(); val repository = LetterDraftRepository(dir)
        val draft = repository.bind(populated(ComposeMode.DATE_TIME, TimeBasis.RELATIVE))
        val file = File(dir, "${draft.id}.json")
        assertTrue(file.renameTo(File(dir, "${draft.id}.json.bak")))
        same(draft, LetterDraftRepository(dir).list().single().draft)
        val corrupt = File(dir, "${java.util.UUID.randomUUID()}.json").apply { writeText("broken") }
        assertEquals(1, repository.list().size); assertNotNull(repository.error); assertTrue(corrupt.exists())
        // A stale Bundle with the same ID must not overwrite a corrupt private attachment.
        val stale = LetterDraft(101, "stale saved-state text").apply { id = corrupt.nameWithoutExtension }
        repository.bind(stale); assertFalse(stale.flush()); assertEquals("broken", corrupt.readText())
    }
    @Test fun attachmentLimitPreservesPreviousAtomicRecordAndExplicitDeleteCleansPrivateCopy() {
        val dir = temporary.newFolder(); val repository = LetterDraftRepository(dir)
        val draft = repository.bind(populated(ComposeMode.MANUAL, TimeBasis.RELATIVE))
        draft.attachment = Triple("too large", "application/octet-stream", ByteArray(2_000_001))
        assertFalse(draft.flush()); assertEquals(4, LetterDraftRepository(dir).list().single().draft.attachment!!.third.size)
        assertTrue(draft.discard()); assertTrue(repository.list().isEmpty())
        assertTrue(dir.listFiles()!!.isEmpty()); draft.text = "late editor"; assertTrue(repository.list().isEmpty())
    }
    @Test fun failureCancellationEmptyReceiptRetainAndSuccessRemovesOnlyMatchingDraft() = runBlocking {
        val dir = temporary.newFolder(); val repository = LetterDraftRepository(dir)
        val draft = repository.bind(populated(ComposeMode.MANUAL, TimeBasis.RELATIVE))
        val other = repository.bind(LetterDraft(102, "other"))
        draft.send(0, { throw java.io.IOException("offline") }, { _, _ -> fail("failure callback") })
        assertEquals(2, LetterDraftRepository(dir).list().size)
        try { draft.send(0, { throw CancellationException() }, { _, _ -> fail("cancel callback") }) } catch (_: CancellationException) { }
        assertFalse(draft.busy); assertEquals(2, repository.list().size)
        draft.send(0, { emptyList() }, { _, _ -> fail("missing receipt callback") })
        assertEquals(2, repository.list().size)
        var calls = 0; var receipts = 0
        draft.send(0, { calls++; listOf(900L) }, { id, _ -> assertEquals(900L, id); receipts++ })
        draft.send(0, { calls++; listOf(901L) }, { _, _ -> receipts++ })
        assertEquals(1, calls); assertEquals(1, receipts)
        assertEquals(listOf(other.id), LetterDraftRepository(dir).list().map { it.draft.id })
        draft.flush(); assertEquals(1, repository.list().size)
        assertFalse(File(dir, "${draft.id}.json").exists())
    }
    @Test fun deletingListInstancePreventsOldComposerFromResurrectingSameDraft() {
        val repository = LetterDraftRepository(temporary.newFolder())
        val current = repository.bind(LetterDraft(101, "body"))
        assertTrue(repository.list().single().draft.discard())
        current.text = "late edit"; assertTrue(current.flush()); assertTrue(repository.list().isEmpty())
    }
}
