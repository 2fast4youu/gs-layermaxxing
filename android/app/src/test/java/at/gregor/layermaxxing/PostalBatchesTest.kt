package at.gregor.layermaxxing

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class PostalBatchesTest {
    @Test fun everyIncomingIdReachableAcrossManyMonthsYearsAndCapacitySubpages() {
        val mail = (1..120).map { n -> PostPilotFixtures.letters.first().copy(id=n.toLong(), incoming=true,
            createdAt=Instant.parse(if (n <= 70) "2026-01-01T00:00:00Z" else "2025-12-31T23:59:59Z").epochSecond) }
        for (capacity in listOf(9,4,6)) {
            val batches = PostalBatches.build(mail.reversed() + mail.first().copy(id=999,incoming=false), capacity)
            val ids = batches.flatMap(PostalBatches::ids)
            assertEquals(120,ids.size); assertEquals(120,ids.distinct().size); assertFalse(999L in ids)
            assertEquals("I 2026",PostalBatches.label(batches.first())); assertEquals("XII 2025",PostalBatches.label(batches.last()))
            assertTrue(batches.all { PostalBatches.ids(it).size <= capacity })
            val gone = mail.filter { it.id != ids.first() }
            assertEquals(ids.drop(1).take(capacity-1),PostalBatches.records(batches.first(),gone).map { it.id })
        }
        assertTrue(PostalBatches.build(emptyList(),9).isEmpty())
    }
    @Test fun illustratedPaletteDeterministicVariedAndDateUsesActualEpochSecondsUtc() {
        val variants = (1L..120L).map(PostalStamp::variant)
        assertEquals((0..5).toSet(),variants.toSet())
        assertEquals(variants,(1L..120L).map(PostalStamp::variant))
        assertEquals("01.01.2026",PostalStamp.date(Instant.parse("2026-01-01T00:00:00Z").epochSecond))
        assertEquals("31.12.2025",PostalStamp.date(Instant.parse("2025-12-31T23:59:59Z").epochSecond))
        assertEquals("XII 2025",PostalBatches.label("2025-12|1"))
    }
}
