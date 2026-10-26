package at.gregor.layermaxxing

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

/** UTC is the postal archive's calendar, independent of device zone and elapsed time.
 * Saved strings contain real IDs only; refreshed records supply all live state and API targets. */
internal object PostalBatches {
    val romans = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII")
    fun month(seconds: Long): YearMonth = YearMonth.from(Instant.ofEpochSecond(seconds).atZone(ZoneOffset.UTC))
    fun build(messages: List<ApiClient.Message>, capacity: Int): List<String> = messages
        .filter { it.incoming && it.groupId == null }.distinctBy { it.id }
        .sortedWith(compareByDescending<ApiClient.Message> { it.createdAt }.thenByDescending { it.id })
        .groupBy { month(it.createdAt) }.flatMap { (month, mail) ->
            mail.chunked(capacity).map { page -> "$month|${page.joinToString(",") { it.id.toString() }}" }
        }
    fun ids(batch: String): List<Long> = batch.substringAfter('|').split(',').mapNotNull(String::toLongOrNull)
    fun label(batch: String): String {
        val month = YearMonth.parse(batch.substringBefore('|'))
        return "${romans[month.monthValue - 1]} ${month.year}"
    }
    fun records(batch: String?, messages: List<ApiClient.Message>): List<ApiClient.Message> {
        val live = messages.filter { it.incoming && it.groupId == null }.associateBy { it.id }
        return batch?.let(::ids).orEmpty().mapNotNull(live::get)
    }
}
