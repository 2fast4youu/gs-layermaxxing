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
        val installed = Thread.getDefaultUncaughtExceptionHandler()
        assertTrue(installed is CrashUncaughtHandler)
        assertNotNull(CrashDiagnostics.storage)
        assertTrue(CrashDiagnostics.storage!!.events().contains("STARTUP"))
        CrashDiagnostics.install(app)
        assertSame(installed, Thread.getDefaultUncaughtExceptionHandler())
    }

    @Test @Config(sdk = [29]) fun olderAndroidReportsSystemExitHistoryUnavailable() {
        val text = CrashDiagnostics.historicalExits(ApplicationProvider.getApplicationContext())
        assertTrue(text.contains("erst ab Android 11 verfügbar"))
        assertTrue(text.contains("Nicht alle Systemabbrüche"))
    }
}
