package at.gregor.layermaxxing

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test

/** The server is polled only while the app is on screen; back on screen the next poll runs at once. */
class PollingTest {
    private fun lifecycle(state: Lifecycle.State) = LifecycleRegistry.createUnsafe(object : LifecycleOwner {
        override val lifecycle: Lifecycle get() = throw UnsupportedOperationException()
    }).apply { currentState = state }

    @Test fun onScreenThePauseIsTheInterval() = runBlocking {
        val l = lifecycle(Lifecycle.State.RESUMED)
        val start = System.nanoTime()
        l.pollPause(150)
        assertTrue((System.nanoTime() - start) / 1_000_000 >= 140)
    }

    @Test fun offScreenNothingIsPolledUntilTheAppIsBack() = runBlocking {
        val l = lifecycle(Lifecycle.State.CREATED)
        val pause = async(Dispatchers.Default) { l.pollPause(50) }
        // far longer than the interval: still waiting, the app is not on screen
        assertNull(withTimeoutOrNull(400) { pause.await() })
        l.currentState = Lifecycle.State.STARTED
        assertNotNull(withTimeoutOrNull(1_000) { pause.await() })
    }

    @Test fun backOnScreenTheNextPollDoesNotWaitForTheRestOfTheInterval() = runBlocking {
        val l = lifecycle(Lifecycle.State.RESUMED)
        val pause = async(Dispatchers.Default) { l.pollPause(60_000) }
        delay(100)
        l.currentState = Lifecycle.State.CREATED
        delay(100)
        assertFalse(pause.isCompleted)
        l.currentState = Lifecycle.State.RESUMED
        assertNotNull(withTimeoutOrNull(1_000) { pause.await() })
    }
}
