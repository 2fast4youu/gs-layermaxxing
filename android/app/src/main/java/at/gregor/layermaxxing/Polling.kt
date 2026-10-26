package at.gregor.layermaxxing

import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The pause between two polls of the server. While the app is on screen it waits [ms]; once the app leaves the screen
 * (another app, screen off) nothing is polled at all – no background traffic, battery or server load – and the next
 * poll runs the moment the app is back, so what it shows is fresh at once.
 */
internal suspend fun Lifecycle.pollPause(ms: Long) {
    withTimeoutOrNull(ms) { currentStateFlow.first { !it.isAtLeast(Lifecycle.State.STARTED) } } ?: return
    awaitOnScreen()
}

/** Returns at once while the app is on screen, otherwise as soon as it is back. */
internal suspend fun Lifecycle.awaitOnScreen() {
    if (!currentState.isAtLeast(Lifecycle.State.STARTED)) currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
}
