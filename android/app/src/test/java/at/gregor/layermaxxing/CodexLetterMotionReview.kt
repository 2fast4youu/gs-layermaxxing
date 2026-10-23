package at.gregor.layermaxxing

import android.graphics.Bitmap
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Opt-in continuous production UI capture. One PostOffice composition, real coordinate taps,
 * existing raster assets, synthetic server response only in CodexMotionHarness.
 * No encoding here: Hermes can encode the PNGs using the measured clock timestamps.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-hdpi")
class CodexLetterMotionReview {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun continuousNativeLetterRoundTrip() = recordCodexLetterRoundTrip(rule)
}

/** Shared capture path: both review entry points exercise the same production round trip. */
internal fun recordCodexLetterRoundTrip(
    rule: androidx.compose.ui.test.junit4.AndroidComposeTestRule<androidx.test.ext.junit.rules.ActivityScenarioRule<ComponentActivity>, ComponentActivity>,
    coreContract: Boolean = false,
    level: Int = 0,
    fontScale: Float = 1f,
    small: Boolean = false,
) {
    val destination = System.getenv("VILLAGE_SHOTS")?.takeIf { it.isNotBlank() }?.let(::File)
    assumeTrue("opt-in: set VILLAGE_SHOTS", destination != null)
    val out = requireNotNull(destination)
    out.mkdirs()
    require(out.isDirectory) { "VILLAGE_SHOTS is not a directory" }
    val framePrefix = if (coreContract) "post-$level-${if (small) "small15-" else ""}frame" else "motion-frame"
    fun still(legacy: String, standard: String) = if (coreContract) standard.replace("post-0-", "post-$level-${if (small) "small15-" else ""}") else legacy
    // A stale longer clip must never be silently mixed into this capture.
    require(out.listFiles().orEmpty().none { it.name.matches(Regex("$framePrefix-\\d{4}\\.png")) }) {
        "Use a fresh VILLAGE_SHOTS directory for the motion sequence"
    }
    val resolver = rule.activity.contentResolver
    val oldScale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    val h = CodexMotionHarness(rule)
    val csv = StringBuilder("frame,file,clock_ms,phase,camera,crack,flap,rise,lift,event,target_ms,interval_ms,overrun_ms,storage_pitch,storage_yaw,storage_roll,storage_clearance,storage_turn,storage_light,storage_dy\n")
    var index = 0
    var previousTime: Long? = null
    val captureIntervalMs = 48L
    val intervals = mutableListOf<Long>()
    var lateCaptures = 0
    var maxOverrunMs = 0L
    var completed = false
    var objectMaterialReady: Boolean? = null
    var pendingEvent = "initial-rest"
    val phases = linkedSetOf<ScenePhase>()
    fun save(name: String): Long {
        // Draw the running decor, as in LetterSceneReview; never instantiate a staged still scene.
        val view = rule.activity.window.decorView
        check(view.width > 0 && view.height > 0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        var drawnAt = 0L
        try {
            rule.runOnIdle {
                view.draw(android.graphics.Canvas(bitmap))
                drawnAt = rule.mainClock.currentTime
            }
            File(out, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
        return drawnAt
    }
    fun capture(targetTime: Long? = null) {
        // Keep the existing bound: nominal motion totals 5280 ms, plus the fixed holds
        // and bounded phase transitions. No runtime evidence justifies raising it.
        check(index < 140) { "140-frame safety bound reached in ${h.phase()}" }
        val phase = h.phase()
        val m = h.sample()
        val pose = if (h.exists("tree-env-1")) h.tag("tree-env-1").fetchSemanticsNode().config[StoragePoseKey] else null
        val storage = if (level > 0 && h.exists("tree-env-1")) h.tag("tree-env-1").fetchSemanticsNode().config[StorageFrameKey] else null
        val sampledAt = rule.mainClock.currentTime
        phases += phase
        val file = "$framePrefix-%04d.png".format(java.util.Locale.ROOT, index)
        val time = save(file)
        val delta = previousTime?.let { time - it }
        val overrun = targetTime?.let { (time - it).coerceAtLeast(0L) }
        delta?.let { intervals += it }
        if (overrun != null && overrun > 0L) {
            lateCaptures++
            maxOverrunMs = maxOf(maxOverrunMs, overrun)
        }
        csv.append(listOf(index, file, time, phase.name, m.camera, m.crack, m.flap, m.rise, m.lift,
            pendingEvent, targetTime ?: "", delta ?: "", overrun ?: "", pose?.pitch ?: "", pose?.yaw ?: "", pose?.roll ?: "",
            storage?.clearance ?: "", storage?.turn ?: "", storage?.light ?: "", storage?.dy ?: "").joinToString(",")).append('\n')
        pendingEvent = ""
        previousTime = time
        index++
        assertTrue("positive real capture timestamp", time > 0L)
        delta?.let { assertTrue("positive measured clock step", it > 0L) }
        targetTime?.let { assertTrue("capture must reach its real clock target", time >= it) }
        assertEquals("phase sample and native draw must share the real clock timestamp", sampledAt, time)
    }
    fun step() {
        val target = requireNotNull(previousTime) + captureIntervalMs
        // Touch input/idling may already have consumed real frames. Settle first, then
        // re-read the clock after each frame instead of unconditionally adding three.
        h.frames(0)
        repeat(3) {
            if (rule.mainClock.currentTime < target) {
                val before = rule.mainClock.currentTime
                h.frames(1)
                assertTrue("native frame must advance the real clock", rule.mainClock.currentTime > before)
            }
        }
        // An overrun cannot be undone: capture the actual frame and report the gap.
        capture(target)
    }
    fun toPhase(target: ScenePhase, maxCaptures: Int) {
        repeat(maxCaptures) { if (h.phase() == target) return; step() }
        assertEquals("bounded capture loop", target, h.phase())
    }
    try {
        h.start(letters = PostPilotFixtures.letters, level = level, fontScale = fontScale)
        val startingAnchor = h.tag("tree-env-1").fetchSemanticsNode().boundsInRoot
        if (level > 0) {
            val objects = h.tag("tree-scene-REST").fetchSemanticsNode().config[PostObjectMotionKey]
            assertEquals(level, objects.level); assertEquals(0f, objects.holder, 0f); assertEquals(0f, objects.letter, 0f)
            objectMaterialReady = objects.materialReady
        }
        capture()
        repeat(2) { step() }
        save(still("motion-still-tree-closed.png", "post-0-rest.png"))
        h.touch("tree-letter-1"); pendingEvent = "tap-tree-letter-1"
        toPhase(ScenePhase.SEALED, 24)
        assertTrue(h.exists("tree-envelope"))
        assertFalse(h.exists("tree-lifted") || h.exists("tree-letter-text"))
        assertEquals(0, h.calls)
        assertEquals(1f, h.sample().camera, 0f)
        if (!coreContract) save("motion-still-envelope-sealed.png")
        repeat(6) { step() } // short closed-envelope hold, part of the continuous clock
        h.touch("tree-envelope"); pendingEvent = "tap-envelope-server-held"
        step()
        assertEquals(ScenePhase.WAITING, h.phase())
        assertEquals(1, h.calls)
        assertEquals(0f, h.sample().crack, 0f)
        assertFalse(h.exists("tree-letter-text"))
        // Release an actual suspended production opener, rather than assigning UI scene state.
        h.answer.complete(Unit); pendingEvent = "synthetic-server-answer"
        toPhase(ScenePhase.READING, 56)
        assertTrue(h.exists("tree-lifted") && h.exists("tree-letter-text"))
        assertFalse(h.exists("tree-envelope"))
        assertEquals(1f, h.sample().lift, 0f)
        save(still("motion-still-letter-open.png", "post-0-active.png"))
        repeat(6) { step() }
        h.outside(); pendingEvent = "tap-outside"
        step()
        assertEquals(ScenePhase.STOWING, h.phase())
        toPhase(ScenePhase.LEAVING, 30)
        assertEquals(0f, h.sample().rise, 0f)
        assertEquals(0f, h.sample().flap, 0f)
        assertFalse(h.exists("tree-lifted"))
        toPhase(ScenePhase.REST, 22)
        repeat(3) { step() }
        assertEquals(0f, h.sample().camera, 0f)
        assertFalse(h.exists("tree-letter-text"))
        assertEquals(1, h.calls)
        if (level > 0) {
            assertRestBounds("next unread letter occupies the returned registered slot", startingAnchor,
                h.tag("tree-env-6").fetchSemanticsNode().boundsInRoot)
            val objects = h.tag("tree-scene-REST").fetchSemanticsNode().config[PostObjectMotionKey]
            assertEquals(level, objects.level); assertEquals(0f, objects.holder, 0f); assertEquals(0f, objects.letter, 0f)
        }
        if (!coreContract) save("motion-still-tree-returned.png")
        assertTrue("at least twelve actual native frames", index >= 12)
        assertEquals(1, h.compositions)
        assertEquals(0, h.disposals)
        assertEquals(0, h.marks)
        assertTrue("every native phase captured: $phases", phases.containsAll(listOf(
            ScenePhase.REST, ScenePhase.APPROACH, ScenePhase.SEALED, ScenePhase.WAITING,
            ScenePhase.UNSEALING, ScenePhase.EMERGING, ScenePhase.READING, ScenePhase.STOWING, ScenePhase.LEAVING,
        )))
        completed = true
    } finally {
        try {
            // Preserve real timing and completion status even if an assertion fails.
            File(out, if (coreContract) "post-$level-${if (small) "small15-" else ""}motion.csv" else "motion-frames.csv").writeText(csv.toString())
            File(out, if (coreContract) "post-$level-${if (small) "small15-" else ""}metadata.txt" else "STEP-METADATA.txt").writeText("""
            Source: one running production PostOffice; Robolectric NATIVE, SDK 34.
            Level: $level; small: $small; fontScale: $fontScale. Qualifiers are declared on the calling test. Existing production raster layers only.
            Painted moving-holder layer pair available: $objectMaterialReady (POST0 has no upper holder).
            Clock: Compose mainClock, autoAdvance=false. Target: previous real capture + $captureIntervalMs ms.
            Advance only missing native frames (at most 3); input/idling frame time counts toward the target.
            Full roundtrip and all assertions completed: $completed.
            Capture safety bound: 140 frames; phase bounds unchanged.
            Frames saved: $index; numbered from $framePrefix-0000.png when present.
            Measured capture intervals: min=${intervals.minOrNull()} ms, max=${intervals.maxOrNull()} ms.
            All measured intervals equal target: ${intervals.isNotEmpty() && intervals.all { it == captureIntervalMs }}.
            Late captures: $lateCaptures; maximum target overrun: $maxOverrunMs ms.
            Overruns remain real variable intervals; never encode them as a constant framerate.
            First clock timestamp: ${csv.lines().getOrNull(1)?.split(',')?.getOrNull(2)} ms; last: $previousTime ms.
            Actual mainClock.currentTime at each native draw: ${if (coreContract) "post-$level-${if (small) "small15-" else ""}motion.csv" else "motion-frames.csv"}.
            CSV target_ms, interval_ms and overrun_ms are empty for the initial frame.
            Holds: 6 capture intervals sealed, 6 reading, 2 initial rest, 3 returned rest.
            Taps and response release occur between adjacent captures; events are recorded on the next frame.
            Native nominal durations at scale 1: camera in ${LetterMotion.APPROACH_MS} ms,
            seal ${LetterMotion.SEAL_MS} ms, emerging ${LetterMotion.EMERGE_MS} ms,
            stowing ${LetterMotion.STOW_MS} ms, camera out ${LetterMotion.LEAVE_MS} ms.
            Phase/recomposition boundaries may take extra clock frames: use CSV timestamps for encoding.
            No assumed playback framerate; no media implementation or encoder was run by this renderer.
            Stills draw the same live composition with no explicit clock step; any idling time counts toward the next target.
            Test data: PostPilotFixtures / LetterSceneFixtures; server answer synthetic and test-only.
        """.trimIndent() + "\n")
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, oldScale)
        }
    }
}
