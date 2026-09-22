package at.gregor.layermaxxing

/** Pure tap/rand logic; Android audio and animation remain thin lifecycle-aware views. */
enum class LockedTapResult { PLAY_SOUND, SHOW_FOG }

class LockedLetterTapTracker(
    private val threshold: Int = 5,
    private val windowMs: Long = 4_000,
) {
    private var letterId: Long? = null
    private val taps = ArrayDeque<Long>()

    fun register(id: Long, elapsedMs: Long): LockedTapResult {
        if (letterId != id) {
            reset()
            letterId = id
        }
        while (taps.isNotEmpty() && elapsedMs - taps.first() > windowMs) taps.removeFirst()
        taps.addLast(elapsedMs)
        if (taps.size >= threshold) {
            reset()
            return LockedTapResult.SHOW_FOG
        }
        return LockedTapResult.PLAY_SOUND
    }

    fun reset() {
        letterId = null
        taps.clear()
    }
}

/**
 * Uniformly maps a draw from size-1 choices around the previous index.
 * This guarantees no immediate repetition without retry loops.
 */
class NonRepeatingSoundPicker(
    private val size: Int,
    private val nextInt: (Int) -> Int,
) {
    private var previous = -1

    fun next(): Int {
        require(size > 0)
        if (size == 1) return 0
        val candidate = if (previous < 0) {
            nextInt(size).coerceIn(0, size - 1)
        } else {
            val draw = nextInt(size - 1).coerceIn(0, size - 2)
            if (draw >= previous) draw + 1 else draw
        }
        previous = candidate
        return candidate
    }
}

enum class LetterBucket(val label: String) {
    WAITING_ON_YOU("Wartet auf dich"),
    IN_TRANSIT("Unterwegs"),
    READY("Bereit"),
    OPENED_HISTORY("Geöffnet-Verlauf"),
}
