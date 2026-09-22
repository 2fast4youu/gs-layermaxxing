package at.gregor.layermaxxing

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.random.Random

/** Short effects belong in SoundPool; it is released with the composition lifecycle. */
internal class LockedLetterSoundPlayer(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val resources = intArrayOf(
        R.raw.seal_rip_1,
        R.raw.seal_rip_2,
        R.raw.seal_rip_3,
        R.raw.seal_rip_4,
        R.raw.seal_rip_5,
        R.raw.seal_rip_6,
    )
    private val sounds: List<Int>
    private val loaded = mutableSetOf<Int>()
    private val streams = mutableSetOf<Int>()
    private val picker: NonRepeatingSoundPicker

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loaded += sampleId
        }
        sounds = resources.map { pool.load(context.applicationContext, it, 1) }
        picker = NonRepeatingSoundPicker(sounds.size) { Random.nextInt(it) }
    }

    fun playRandom() {
        val soundId = sounds[picker.next()]
        if (soundId !in loaded) return
        pool.play(soundId, 1f, 1f, 1, 0, 1f).takeIf { it != 0 }?.let(streams::add)
    }

    fun stopAll() {
        streams.forEach(pool::stop)
        streams.clear()
    }

    fun release() {
        stopAll()
        pool.release()
        loaded.clear()
    }
}

@Composable
internal fun rememberLockedLetterSoundPlayer(context: Context): LockedLetterSoundPlayer {
    val player = remember(context.applicationContext) { LockedLetterSoundPlayer(context.applicationContext) }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return player
}

/** The four quiet, diegetic sounds of the valley. Kept distinct from the whip gag. */
internal enum class FiefSound { BUILD, COIN, TICK, PAPER }

/**
 * The valley's own SoundPool: short, low-volume, one-shot samples.
 *
 * Nothing here loops, and every sample is played well below full volume so the
 * place stays calm. It follows the same lifecycle-released pattern as the locked
 * letter player and is muted entirely when the system removes animations.
 */
internal class FiefSoundPlayer(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    // Each sample stays quiet; the build thud is the loudest and still gentle.
    private val spec = mapOf(
        FiefSound.BUILD to (R.raw.fief_build to 0.5f),
        FiefSound.COIN to (R.raw.fief_coin to 0.45f),
        FiefSound.TICK to (R.raw.fief_tick to 0.35f),
        FiefSound.PAPER to (R.raw.fief_paper to 0.4f),
    )
    private val sounds: Map<FiefSound, Int>
    private val volumes: Map<Int, Float>
    private val loaded = mutableSetOf<Int>()
    private val streams = mutableSetOf<Int>()

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) loaded += sampleId }
        sounds = spec.mapValues { (_, v) -> pool.load(context.applicationContext, v.first, 1) }
        volumes = sounds.entries.associate { (kind, id) -> id to spec.getValue(kind).second }
    }

    fun play(kind: FiefSound) {
        val soundId = sounds[kind] ?: return
        if (soundId !in loaded) return
        val volume = volumes[soundId] ?: 1f
        pool.play(soundId, volume, volume, 0, 0, 1f).takeIf { it != 0 }?.let(streams::add)
    }

    fun release() {
        streams.forEach(pool::stop)
        streams.clear()
        pool.release()
        loaded.clear()
    }
}

@Composable
internal fun rememberFiefSoundPlayer(context: Context): FiefSoundPlayer {
    val player = remember(context.applicationContext) { FiefSoundPlayer(context.applicationContext) }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

/** Null means silence: the valley provides a real player only when sound is allowed. */
internal val LocalFiefSounds = staticCompositionLocalOf<FiefSoundPlayer?> { null }
