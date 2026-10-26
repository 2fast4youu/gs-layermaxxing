package at.gregor.layermaxxing

import java.time.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReleaseScheduleTest {
    private val now = Instant.parse("2026-01-01T12:00:00Z").epochSecond
    private val zone = ZoneId.of("Europe/Berlin")
    private fun draft() = LetterDraft(7, "SYNTHETIC body")
    private fun invalid(d: LetterDraft) = assertThrows(IllegalArgumentException::class.java) { ReleaseSchedule.resolve(d, now, zone) }
    @Test fun absoluteRelativeAndCommonSendAnchorMatchSignedPayload() = runBlocking {
        val d = draft()
        d.amount = "90"; d.unit = DelayUnit.MINUTES
        assertEquals(now + 5400, d.seal(0, now, zone).releaseAt)
        assertEquals(now + 600 + 5400, d.seal(0, now + 600, zone).releaseAt)
        d.mode = ComposeMode.RANDOM
        d.randomStart = "2"; d.randomStartUnit = DelayUnit.HOURS
        d.randomEnd = "1"; d.randomEndUnit = DelayUnit.DAYS
        val window = d.seal(0, now, zone)
        assertEquals("random", window.mode); assertNull(window.releaseAt)
        assertEquals(now + 7200, window.randomFrom); assertEquals(now + 86400, window.randomTo)
        val canonical = org.json.JSONObject(window.evidence!!.canonicalMetadata)
        assertEquals(window.randomFrom, canonical.getLong("random_from"))
        assertEquals(window.randomTo, canonical.getLong("random_to"))
        d.timeBasis = TimeBasis.ABSOLUTE
        d.windowStart = LocalDateTime.of(2026,1,2,10,0)
        d.windowEnd = LocalDateTime.of(2026,1,2,11,0)
        val abs = d.seal(0, now, zone)
        assertEquals(Instant.parse("2026-01-02T09:00:00Z").epochSecond, abs.randomFrom)
        assertEquals(3600, abs.randomTo!! - abs.randomFrom!!)
        d.mode = ComposeMode.DATE_TIME; d.dateTime = d.windowStart
        assertEquals(abs.randomFrom, d.seal(0, now, zone).releaseAt)
        d.mode = ComposeMode.MANUAL
        val manual = d.seal(0, now, zone)
        assertEquals("manual", manual.mode); assertNull(manual.releaseAt); assertNull(manual.randomFrom); assertNull(manual.randomTo)
    }
    @Test fun invalidMissingPastInvertedOverflowAndDstNeverNormalizeSilently() {
        for (text in listOf("", "0", "-1", "abc", "9223372036854775807", "366")) {
            val d = draft(); d.amount = text; d.unit = DelayUnit.DAYS; invalid(d)
        }
        val d = draft(); d.mode = ComposeMode.RANDOM; d.randomStart = "2"; d.randomEnd = "1"; invalid(d)
        d.randomStart = "1"; d.randomEnd = "1"; invalid(d)
        d.timeBasis = TimeBasis.ABSOLUTE
        d.windowStart = LocalDateTime.of(2025,12,31,12,0); invalid(d)
        for (value in listOf(LocalDateTime.of(2026,3,29,2,30), LocalDateTime.of(2026,10,25,2,30))) {
            assertThrows(IllegalArgumentException::class.java) { ReleaseSchedule.absolute(value, zone) }
        }
        assertEquals(Instant.parse("2026-03-29T01:30:00Z").epochSecond,
            ReleaseSchedule.absolute(LocalDateTime.of(2026,3,29,3,30), zone))
    }
    @Test fun minimumDelayMovesRelativeWindowTogetherAndAbsoluteFailsClearly() {
        val d = draft(); d.mode = ComposeMode.RANDOM; d.randomStart = "1"; d.randomEnd = "2"
        val t = ReleaseSchedule.resolve(d, now, zone, 86400)
        assertEquals(now + 86405, t.from); assertEquals(3600, t.to!! - t.from!!)
        d.timeBasis = TimeBasis.ABSOLUTE
        d.windowStart = LocalDateTime.of(2026,1,1,14,0); d.windowEnd = d.windowStart.plusHours(2)
        assertThrows(IllegalArgumentException::class.java) { ReleaseSchedule.resolve(d, now, zone, 86400) }
    }
    @Test fun invalidSendNeverCallsTransportAndConcurrentSendOnlyOnce() = runBlocking {
        val d = draft(); d.amount = ""; var calls = 0
        d.send(0, { calls++; listOf(1) }, { _, _ -> })
        assertEquals(0,calls); assertNotNull(d.notice)
        d.mode = ComposeMode.MANUAL
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val finish = kotlinx.coroutines.CompletableDeferred<Unit>()
        val job = kotlinx.coroutines.CoroutineScope(coroutineContext).launchSend(d, started, finish) { calls++ }
        started.await()
        d.send(0, { calls++; listOf(1) }, { _, _ -> })
        finish.complete(Unit); job.join(); assertEquals(1,calls)
    }
    private fun kotlinx.coroutines.CoroutineScope.launchSend(d: LetterDraft, started: kotlinx.coroutines.CompletableDeferred<Unit>, finish: kotlinx.coroutines.CompletableDeferred<Unit>, count: () -> Unit) =
        launch { d.send(0, { count(); started.complete(Unit); finish.await(); listOf(1) }, { _, _ -> }) }
}
