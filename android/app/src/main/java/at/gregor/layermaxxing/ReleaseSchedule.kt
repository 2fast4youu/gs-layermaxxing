package at.gregor.layermaxxing

import java.time.LocalDateTime
import java.time.ZoneId

internal enum class TimeBasis { ABSOLUTE, RELATIVE }
internal data class ReleaseTimes(val releaseAt: Long? = null, val from: Long? = null, val to: Long? = null)
internal object ReleaseSchedule {
    const val MAX_SECONDS = 365L * 86400
    val choices = listOf(ComposeMode.MANUAL, ComposeMode.DATE_TIME, ComposeMode.RANDOM)
    fun duration(text: String, unit: DelayUnit): Long {
        val n = text.toLongOrNull()
        require(n != null && n > 0 && n <= MAX_SECONDS / unit.seconds) { "Dauer: positive ganze Zahl, höchstens 365 Tage." }
        return Math.multiplyExact(n, unit.seconds)
    }
    /** Reject DST gaps AND overlaps. Never silently pick one of two local instants. */
    fun absolute(value: LocalDateTime, zone: ZoneId): Long {
        val offsets = zone.rules.getValidOffsets(value)
        require(offsets.size == 1) { "Diese Ortszeit ist durch die Zeitumstellung ungültig oder doppeldeutig. Bitte eine eindeutige Uhrzeit wählen." }
        return value.toEpochSecond(offsets.single())
    }
    fun resolve(draft: LetterDraft, now: Long, zone: ZoneId, minimumDelay: Long = 0): ReleaseTimes {
        if (draft.mode == ComposeMode.MANUAL) return ReleaseTimes()
        require(draft.mode in choices || draft.mode == ComposeMode.DURATION) { "Bitte eine der drei Freigabearten wählen." }
        val relative = draft.timeBasis == TimeBasis.RELATIVE || draft.mode == ComposeMode.DURATION
        fun at(text: String, unit: DelayUnit, local: LocalDateTime): Long =
            if (relative) Math.addExact(now, duration(text, unit)) else absolute(local, zone)
        var start = if (draft.mode == ComposeMode.RANDOM) at(draft.randomStart, draft.randomStartUnit, draft.windowStart)
            else at(draft.amount, draft.unit, draft.dateTime)
        var end = if (draft.mode == ComposeMode.RANDOM) at(draft.randomEnd, draft.randomEndUnit, draft.windowEnd) else null
        require(start > now) { "Der Zeitpunkt muss in der Zukunft liegen." }
        end?.let { require(it - start >= 60) { "Das Zeitfenster muss mindestens eine Minute groß sein; das Ende muss nach dem Beginn liegen." } }
        if (minimumDelay > 0) {
            val minimum = Math.addExact(now, Math.addExact(minimumDelay, 5))
            if (relative && start < minimum) {
                val shift = minimum - start
                start = minimum; end = end?.let { Math.addExact(it, shift) }
            } else require(start >= minimum) { "Die gewählte Ortszeit unterschreitet die Mindestwartezeit dieser Freundschaft." }
        }
        val latest = end ?: start
        require(start - now <= MAX_SECONDS && latest - now <= MAX_SECONDS) { "Freigabe höchstens 365 Tage nach dem Senden." }
        return if (end == null) ReleaseTimes(releaseAt = start) else ReleaseTimes(from = start, to = end)
    }
}
