package at.gregor.layermaxxing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two promises of the Freundesfunke, pinned on the client side.
 *
 * The server is the authority for both, but the client model must not be able to
 * carry a sender or a time even by accident: these tests fail the moment such a
 * field is added to the receiving side.
 */
class SparkTest {

    /** Declared payload fields, without the `$stable` the Compose compiler adds. */
    private fun declaredFieldNames(type: Class<*>): List<String> =
        type.declaredFields.map { it.name.lowercase() }.filterNot { it.startsWith("\$") }

    @Test
    fun receivedSparkCarriesNeitherSenderNorTime() {
        val fields = declaredFieldNames(SparkItem::class.java)
        listOf("sender", "from", "author", "created", "time", "at").forEach { forbidden ->
            assertFalse(
                "a received spark must never carry a '$forbidden' field",
                fields.any { it.contains(forbidden) },
            )
        }
        assertEquals(setOf("id", "text", "opened"), fields.toSet())
    }

    @Test
    fun sentSparkLearnsExactlyOneBitAndNoTime() {
        val fields = declaredFieldNames(SparkSent::class.java)
        assertEquals(setOf("id", "recipientid", "recipientname", "opened"), fields.toSet())
        assertEquals("Unterwegs", Sparks.statusLabel(opened = false))
        assertEquals("Geöffnet", Sparks.statusLabel(opened = true))
    }

    @Test
    fun theCoverNamesNobody() {
        // The heading a recipient sees is fixed copy, so it cannot grow a name.
        assertEquals("Für dich ✨", Sparks.COVER)
        assertFalse(Sparks.COVER.contains("von", ignoreCase = true))
    }

    @Test
    fun openedIsAReadMarkAndNotALock() {
        // A spark carries its own key: "opened" is the one bit the sender learns,
        // never a release gate, so an unopened spark still holds a readable line
        // that the screen keeps covered until it is asked for.
        val unopened = SparkItem(1, text = "schön, dass es dich gibt", opened = false)
        assertEquals("Unterwegs", Sparks.statusLabel(unopened.opened))
        assertEquals(1, Sparks.unopenedCount(listOf(unopened)))
        assertEquals(0, Sparks.unopenedCount(listOf(unopened.copy(opened = true))))
    }

    @Test
    fun unopenedCountAndEntryVisibilityFollowTheInbox() {
        val inbox = listOf(
            SparkItem(1, "schön", opened = true),
            SparkItem(2, null, opened = false),
            SparkItem(3, null, opened = false),
        )
        assertEquals(2, Sparks.unopenedCount(inbox))
        assertEquals(0, Sparks.unopenedCount(emptyList()))
        // The entry stays out of the overview until a spark actually exists.
        assertFalse(Sparks.entryVisible(emptyList(), emptyList()))
        assertTrue(Sparks.entryVisible(inbox, emptyList()))
        assertTrue(Sparks.entryVisible(emptyList(), listOf(SparkSent(9, 2, "Bea", opened = false))))
    }

    @Test
    fun textIsClampedToOneShortLine() {
        val long = "x".repeat(Sparks.MAX_TEXT + 50)
        assertEquals(Sparks.MAX_TEXT, Sparks.clampText(long).length)
        assertEquals("kurz", Sparks.clampText("kurz"))
        assertEquals("", Sparks.clampText(""))
    }

    @Test
    fun theVisibleCopyNeverPromisesAReplyOrPoints() {
        listOf(SPARK_PROMISE, SPARK_INFO, SPARK_ABUSE_INFO).forEach { line ->
            assertFalse(line.contains("antworte", ignoreCase = true))
            assertFalse(line.contains("Streak", ignoreCase = true))
        }
        // The abuse surface is honest about where the sender is known: the server.
        assertTrue(SPARK_ABUSE_INFO.contains("Server"))
        // And it promises the client is never told.
        assertTrue(SPARK_INFO.contains("nie, von wem"))
    }
}
