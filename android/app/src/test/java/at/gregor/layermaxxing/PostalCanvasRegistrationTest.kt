package at.gregor.layermaxxing

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A full-canvas foreground must never be squeezed to the narrower phone viewport. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-hdpi")
class PostalCanvasRegistrationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun sameCanvas(level: Int, fontScale: Float = 1f) {
        val h = CodexMotionHarness(rule); h.start(level = level, fontScale = fontScale)
        val measured = h.tag("post-holder-canvas").fetchSemanticsNode().config[PostHolderCanvasKey]
        assertTrue("expected source registration must be measured", measured.expected.width > 0 && measured.expected.height > 0)
        assertEquals("POST$level foreground was squeezed or resized independently from its background", measured.expected, measured.actual)
    }
    @Test fun post1FullCanvasMatchesBackground() = sameCanvas(1)
    @Test fun post2FullCanvasMatchesBackground() = sameCanvas(2)
    @Test fun post3FullCanvasMatchesBackground() = sameCanvas(3)
    @Test @Config(qualifiers = "w360dp-h640dp-hdpi")
    fun post1SmallFont15CanvasMatchesBackground() = sameCanvas(1, 1.5f)
    @Test @Config(qualifiers = "w360dp-h640dp-hdpi")
    fun post2SmallFont15CanvasMatchesBackground() = sameCanvas(2, 1.5f)
    @Test @Config(qualifiers = "w360dp-h640dp-hdpi")
    fun post3SmallFont15CanvasMatchesBackground() = sameCanvas(3, 1.5f)
}
