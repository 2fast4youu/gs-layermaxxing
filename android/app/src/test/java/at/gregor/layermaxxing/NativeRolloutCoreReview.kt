package at.gregor.layermaxxing

import android.graphics.Bitmap
import android.graphics.Canvas
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

/** approved-core POST0. Opt in with VILLAGE_SHOTS and --tests '*NativeRolloutCoreReview'.
 * Test-only synthetic replies; production PostOffice, native art, gestures and motion throughout.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-hdpi")
class NativeRolloutCoreReview {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun post0NormalAndContinuousRoundTrip() = recordCodexLetterRoundTrip(rule, coreContract = true)

    @Test
    @Config(qualifiers = "w360dp-h640dp-hdpi")
    fun post0Small15RestActiveAndBack() {
        val destination = System.getenv("VILLAGE_SHOTS")?.takeIf { it.isNotBlank() }
        assumeTrue("opt-in: set VILLAGE_SHOTS", destination != null)
        val out = File(requireNotNull(destination))
        out.mkdirs()
        require(out.isDirectory)
        val resolver = rule.activity.contentResolver
        val previousScale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val h = CodexMotionHarness(rule)
        fun save(name: String) {
            val view = rule.activity.window.decorView
            check(view.width > 0 && view.height > 0)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                rule.runOnIdle { view.draw(Canvas(bitmap)) }
                File(out, name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
        }
        try {
            h.start(letters = PostPilotFixtures.letters, fontScale = 1.5f)
            val firstEnvelope = h.tag("tree-env-1").fetchSemanticsNode().boundsInRoot
            // With all fourteen fixture letters, opening 1 moves it from slot 0 to
            // slot 8 (occupied by 12 before opening); unopened 6 takes slot 0.
            val nextAnchor = h.tag("tree-letter-12").fetchSemanticsNode().boundsInRoot
            save("post-0-small15-rest.png")
            h.touch("tree-letter-1"); h.until(ScenePhase.SEALED)
            assertEquals(0, h.calls)
            assertFalse(h.exists("tree-letter-text"))
            h.touch("tree-envelope"); h.frames()
            assertEquals(ScenePhase.WAITING, h.phase())
            assertEquals(1, h.calls)
            h.answer.complete(Unit); h.until(ScenePhase.READING)
            assertTrue(h.exists("tree-letter-text"))
            assertEquals(1f, h.sample().lift, 0f)
            save("post-0-small15-active.png")
            h.back(); h.until(ScenePhase.REST)
            assertFalse(h.exists("tree-letter-text") || h.exists("tree-lifted"))
            assertEquals(0f, h.sample().camera, 0f)
            assertRestBounds("opened letter at its current slot", nextAnchor,
                h.tag("tree-letter-1").fetchSemanticsNode().boundsInRoot)
            assertRestBounds("unopened letter now occupying slot 0", firstEnvelope,
                h.tag("tree-env-6").fetchSemanticsNode().boundsInRoot)
            val currentEnvelope = h.tag("tree-env-1").fetchSemanticsNode().boundsInRoot
            // A read letter leaves its current anchor directly, without a second
            // fetch or seal tap. On return its full bounds must match that anchor.
            h.touch("tree-letter-1"); h.until(ScenePhase.READING)
            assertTrue(h.exists("tree-letter-text"))
            assertFalse(h.exists("tree-envelope"))
            h.back(); h.until(ScenePhase.REST)
            assertEquals(0f, h.sample().camera, 0f)
            assertFalse(h.exists("tree-letter-text") || h.exists("tree-lifted"))
            assertRestBounds("read letter returned to current anchor", currentEnvelope,
                h.tag("tree-env-1").fetchSemanticsNode().boundsInRoot)
            assertEquals(1, h.calls)
            assertEquals(0, h.marks)
            assertEquals(1, h.compositions)
            assertEquals(0, h.disposals)
        } finally { Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, previousScale) }
    }
}
