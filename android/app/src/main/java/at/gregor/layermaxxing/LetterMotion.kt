package at.gregor.layermaxxing

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.semantics.SemanticsPropertyKey

/** A single reversible path; material sizes and the scene's state/request gates stay elsewhere. */
internal object LetterMotion {
    const val APPROACH_MS = 880
    const val SEAL_MS = 1000
    const val EMERGE_MS = 1400
    const val STOW_MS = 1200
    const val LEAVE_MS = 800
    val cameraEase = CubicBezierEasing(.38f, 0f, .22f, 1f)

    data class Paper(val flap: Float, val rise: Float, val lift: Float)

    private fun segment(t: Float, start: Float, end: Float): Float {
        val x = ((t - start) / (end - start)).coerceIn(0f, 1f)
        return (x * x * (3f - 2f * x)).coerceIn(0f, 1f)
    }

    fun paper(progress: Float) = Paper(
        flap = segment(progress, 0f, .45f),
        // At .26 the flap is already > .5 (past 90°); it remains clear on the return.
        rise = segment(progress, .26f, .70f),
        lift = segment(progress, .62f, 1f),
    )

    /** First half: wax gently lifts; second half: the existing raster halves separate and fall. */
    fun seal(progress: Float): Float = if (progress <= .5f)
        .3f * segment(progress, 0f, .5f)
    else .3f + .7f * segment(progress, .5f, 1f)
}

/** Read-only scene diagnostics, published with the phase semantics; never a control or request bypass. */
internal data class LetterMotionSample(val camera: Float, val crack: Float, val flap: Float, val rise: Float, val lift: Float)
internal val LetterMotionKey = SemanticsPropertyKey<LetterMotionSample>("Letter motion")
