package at.gregor.layermaxxing

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** VILLAGE_SHOTS opt-in. Each test records one uninterrupted production composition with real taps.
 * The recorder asserts all phases, request dedup, completed reverse, and one living composition.
 * Normal and small variants both retain their own honest, bounded continuous PNG/CSV evidence.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-hdpi")
class NativeRolloutPostReview {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun post1() = recordCodexLetterRoundTrip(rule, coreContract = true, level = 1)
    @Test fun post2() = recordCodexLetterRoundTrip(rule, coreContract = true, level = 2)
    @Test fun post3() = recordCodexLetterRoundTrip(rule, coreContract = true, level = 3)
    @Test @Config(qualifiers = "w360dp-h640dp-hdpi")
    fun post1Small15() = recordCodexLetterRoundTrip(rule, coreContract = true, level = 1, fontScale = 1.5f, small = true)
    @Test @Config(qualifiers = "w360dp-h640dp-hdpi")
    fun post2Small15() = recordCodexLetterRoundTrip(rule, coreContract = true, level = 2, fontScale = 1.5f, small = true)
    @Test @Config(qualifiers = "w360dp-h640dp-hdpi")
    fun post3Small15() = recordCodexLetterRoundTrip(rule, coreContract = true, level = 3, fontScale = 1.5f, small = true)
}
