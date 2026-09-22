package at.gregor.layermaxxing

/**
 * Freundesfunke: a short, anonymous, one-way positive note.
 *
 * Pure model, so the two promises of the feature are pinned by JVM tests:
 * the recipient side never carries a sender or a time, and the sender side
 * learns exactly one bit per spark — underway or opened. No reply, no
 * reaction, no EP, no streak.
 */

/**
 * One received spark.
 *
 * A spark has no release gate — the key ships with it — so [opened] is a read
 * mark, not a lock: it is the single bit the sender is allowed to learn, and
 * the screen keeps the line covered until the recipient asks for it. [text] is
 * null only when the stored line could not be decrypted on this device.
 */
data class SparkItem(val id: Long, val text: String?, val opened: Boolean)

/** One sent spark: recipient plus the single status bit, nothing else. */
data class SparkSent(val id: Long, val recipientId: Long, val recipientName: String, val opened: Boolean)

object Sparks {
    /** One kind line, not a channel. The server enforces its own cap on top. */
    const val MAX_TEXT = 120

    const val COVER = "Für dich ✨"

    fun statusLabel(opened: Boolean): String = if (opened) "Geöffnet" else "Unterwegs"

    fun unopenedCount(inbox: List<SparkItem>): Int = inbox.count { !it.opened }

    /** Whether the quiet spark entry appears in the chat overview at all. */
    fun entryVisible(inbox: List<SparkItem>, sent: List<SparkSent>): Boolean =
        inbox.isNotEmpty() || sent.isNotEmpty()

    fun clampText(value: String): String = value.take(MAX_TEXT)
}
