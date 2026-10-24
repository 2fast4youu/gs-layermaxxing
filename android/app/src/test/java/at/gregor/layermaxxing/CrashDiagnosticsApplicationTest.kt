package at.gregor.layermaxxing

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrashDiagnosticsApplicationTest {
    @Test fun manifestApplicationInstallsOnceBeforeAnyActivity() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertTrue(app is LayermaxxingApplication)
        // These prove Application.onCreate initialized diagnostics before any Activity.
        assertNotNull(CrashDiagnostics.storage)
        assertTrue(CrashDiagnostics.storage!!.events().contains("STARTUP"))
        // Thread's default slot is JVM-global, unlike Robolectric's per-SDK classloader.
        // Rebind if another sandbox restored its handler after Application.onCreate.
        CrashDiagnostics.install(app)
        val installed = Thread.getDefaultUncaughtExceptionHandler()
        assertTrue(installed is CrashUncaughtHandler)
        CrashDiagnostics.install(app)
        assertSame(installed, Thread.getDefaultUncaughtExceptionHandler())
    }

    @Test fun replacingTheGlobalHandlerDoesNotLeaveInstallWithAStaleReference() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val before = Thread.getDefaultUncaughtExceptionHandler()
        val replacement = Thread.UncaughtExceptionHandler { _, _ -> }
        try {
            Thread.setDefaultUncaughtExceptionHandler(replacement)
            CrashDiagnostics.install(app)
            val rebound = Thread.getDefaultUncaughtExceptionHandler()
            assertTrue(rebound is CrashUncaughtHandler)
            assertNotSame(replacement, rebound)
            CrashDiagnostics.install(app)
            assertSame(rebound, Thread.getDefaultUncaughtExceptionHandler())
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before)
        }
    }

    @Test @Config(sdk = [29]) fun olderAndroidReportsSystemExitHistoryUnavailable() {
        val text = CrashDiagnostics.historicalExits(ApplicationProvider.getApplicationContext())
        assertTrue(text.contains("erst ab Android 11 verfügbar"))
        assertTrue(text.contains("Nicht alle Systemabbrüche"))
    }
}
