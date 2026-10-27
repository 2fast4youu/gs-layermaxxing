package at.gregor.layermaxxing

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

/** UTC is the postal archive's calendar, independent of device zone and elapsed time.
 * Saved strings contain real IDs only; refreshed records supply all live state and API targets. */
internal object PostalBatches {
    val romans = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII")
    fun month(seconds: Long): YearMonth = YearMonth.from(Instant.ofEpochSecond(seconds).atZone(ZoneOffset.UTC))
    fun eligible(m: ApiClient.Message) = m.incoming && m.groupId == null && !Conversations.letterState(m, false).locked
    fun build(messages: List<ApiClient.Message>, capacity: Int): List<String> = messages
        .filter(::eligible).distinctBy { it.id }
        .sortedWith(compareByDescending<ApiClient.Message> { it.createdAt }.thenByDescending { it.id })
        .chunked(capacity).map { page ->
            val newest = month(page.first().createdAt)
            val oldest = month(page.last().createdAt)
            "${if (newest == oldest) newest.toString() else "$newest/$oldest"}|${page.joinToString(",") { it.id.toString() }}"
        }
    fun ids(batch: String): List<Long> = batch.substringAfter('|').split(',').mapNotNull(String::toLongOrNull)
    fun label(batch: String): String {
        return batch.substringBefore('|').split('/').joinToString("–") {
            val month = YearMonth.parse(it)
            "${romans[month.monthValue - 1]} ${month.year}"
        }
    }
    /** Real endpoint months, in the saved batch's newest-to-oldest order. */
    fun compactMonths(batch: String): String = batch.substringBefore('|').split('/').joinToString("–") {
        romans[YearMonth.parse(it).monthValue - 1]
    }
    fun records(batch: String?, messages: List<ApiClient.Message>): List<ApiClient.Message> {
        val live = messages.filter(::eligible).associateBy { it.id }
        return batch?.let(::ids).orEmpty().mapNotNull(live::get)
    }
}
