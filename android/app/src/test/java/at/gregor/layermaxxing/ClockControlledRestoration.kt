package at.gregor.layermaxxing

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.StateRestorationTester

/** Commit the tester's remove/recreate steps even while the animation clock is manual. */
internal fun clockControlledRestorationTester(rule: ComposeContentTestRule): StateRestorationTester =
    StateRestorationTester(object : ComposeContentTestRule by rule {
        override fun <T> runOnIdle(action: () -> T): T {
            val result = rule.runOnIdle(action)
            // Compose 1.7's tester toggles shouldEmitChildren in separate runOnIdle calls.
            // With autoAdvance=false, waitForIdle alone does not recompose between them.
            // Commit each toggle before the next one; never drain the running animation.
            if (!rule.mainClock.autoAdvance) rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            return result
        }
    })
