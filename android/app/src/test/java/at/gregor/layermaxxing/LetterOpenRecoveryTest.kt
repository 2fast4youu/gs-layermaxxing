package at.gregor.layermaxxing

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Lifecycle regressions against the production coordinator. No network, accounts or database. */
class LetterOpenRecoveryTest {
    private val letter get() = PostPilotFixtures.letters.first().copy(unlocked = true, readAt = null)
    private val text = OpenedMessage("Synthetic authenticated result", null, null, null)

    @Test fun noAnswerHasFiniteFailureAndExplicitRetry() = runBlocking {
        val opened = mutableMapOf<Long, OpenedMessage>()
        var calls = 0; var errors = 0; var success = false
        val opener = LetterOpener(this, opened, { { calls++; if (!success) awaitCancellation(); text } },
            { {} }, { { errors++ } }, timeoutMillis = 30)
        assertTrue(opener.open(letter)); assertFalse(opener.open(letter))
        delay(80)
        assertEquals(OpenRequest.FAILED, opener.requests[letter.id]); assertTrue(opened.isEmpty())
        assertEquals(1, calls); assertEquals(1, errors)
        success = true; assertTrue(opener.open(letter)); yield()
        assertEquals(text, opened[letter.id]); assertNull(opener.requests[letter.id]); assertEquals(2, calls)
    }

    @Test fun resetDiscardsEvenNonCancellableOldAnswerAndDoesNotClobberNewAttempt() = runBlocking {
        val opened = mutableMapOf<Long, OpenedMessage>(); var calls = 0; var done = 0
        val old = CompletableDeferred<Unit>(); val fresh = CompletableDeferred<Unit>()
        val opener = LetterOpener(this, opened, { {
            val n = ++calls
            if (n == 1) withContext(NonCancellable) { old.await() } else fresh.await()
            text.copy(text = "Synthetic attempt $n")
        } }, { { done++ } }, { {} })
        opener.open(letter); yield(); opener.reset(); assertTrue(opener.requests.isEmpty())
        opener.open(letter); yield(); old.complete(Unit); yield()
        assertTrue(opened.isEmpty()); assertEquals(OpenRequest.RUNNING, opener.requests[letter.id])
        fresh.complete(Unit); yield()
        assertEquals("Synthetic attempt 2", opened[letter.id]?.text); assertEquals(1, done)
    }

    @Test fun cancelAndThrownFetchFactoryAreRecoverableAndCallbackFailureCannotEraseSuccess() = runBlocking {
        val opened = mutableMapOf<Long, OpenedMessage>(); var mode = 0
        val opener = LetterOpener(this, opened, {
            if (mode == 1) throw IllegalStateException("synthetic factory failure");
            { if (mode == 0) awaitCancellation(); text }
        }, { { throw IllegalStateException("synthetic refresh failure") } }, { {} })
        opener.open(letter); yield(); opener.cancel(letter.id)
        assertEquals(OpenRequest.FAILED, opener.requests[letter.id]); assertTrue(opened.isEmpty())
        mode = 1; opener.open(letter); yield(); assertEquals(OpenRequest.FAILED, opener.requests[letter.id])
        mode = 2; opener.open(letter); yield()
        assertEquals(text, opened[letter.id]); assertNull(opener.requests[letter.id])
    }

    @Test fun alreadyCancelledScopeNeverLeavesRunning() = runBlocking {
        val cancelled = CoroutineScope(Job().also { it.cancel() } + Dispatchers.Unconfined)
        val opener = LetterOpener(cancelled, mutableMapOf(), { { text } }, { {} }, { {} })
        opener.open(letter)
        assertEquals(OpenRequest.FAILED, opener.requests[letter.id])
    }
}
