package at.gregor.layermaxxing

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ShakeReportUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @Test fun manualProductionDialogCapturesOnePrivatePngWithDiagnostics() {
        rule.activity.getSharedPreferences("shake-reports", Context.MODE_PRIVATE).edit().clear().commit()
        val directory = java.io.File(rule.activity.filesDir, "shake-reports")
        directory.deleteRecursively()
        CrashDiagnostics.install(rule.activity.applicationContext)
        val session = SessionStore(rule.activity)
        rule.runOnUiThread {
            session.selectServerProfile(ServerProfile.GERFRIED)
            session.name = "synthetic"; session.token = "AUTH_NOT_IN_METADATA_CANARY"
            org.robolectric.Shadows.shadowOf(rule.activity.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).setIsInteractive(true)
            org.robolectric.Shadows.shadowOf(rule.activity.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).apply {
                setKeyguardLocked(false); setIsDeviceLocked(false)
            }
        }
        val reports = ShakeReports(rule.activity)
        val open = androidx.compose.runtime.mutableStateOf(true)
        try {
            rule.runOnUiThread {
                reports.resume()
                reports.bind(session.token, true, session.serverProfile, "test", ApiClient())
                reports.enableCapture(true)
            }
            rule.setContent { LayermaxxingTheme("light") {
                CompositionLocalProvider(LocalShakeReports provides reports) {
                    androidx.compose.material3.Text("Synthetic app visible content")
                    if (open.value) CrashDiagnosticsDialog { open.value = false }
                }
            } }
            rule.onNodeWithText("App jetzt aufnehmen").performScrollTo().performClick()
            // Each nested postOnAnimation requires its own display frame after dismissal.
            repeat(4) {
                rule.mainClock.advanceTimeByFrame()
                rule.runOnUiThread {
                    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                        .idleFor(java.time.Duration.ofMillis(32))
                }
            }
            var attempts = 0
            while (!reports.status.startsWith("PNG lokal gespeichert") && attempts++ < 500) {
                rule.mainClock.advanceTimeByFrame()
                rule.runOnUiThread { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16)) }
                Thread.sleep(10)
            }
            assertTrue("Actual capture status: ${reports.status}", reports.status.startsWith("PNG lokal gespeichert"))
            val files = directory.listFiles().orEmpty().filter { it.extension == "png" }
            assertEquals(1, files.size)
            val metadata = ShakePng.extract(files.single().readBytes())
            assertTrue(metadata.contains("Shake report v1"))
            assertFalse(metadata.contains("AUTH_NOT_IN_METADATA_CANARY"))
            assertFalse(metadata.contains("Synthetic app visible content"))
            rule.onNodeWithText("Fehlerbericht / Log kopieren").assertDoesNotExist()
        } finally { reports.destroy(); directory.deleteRecursively() }
    }

    @Test fun secureWindowRefusesManualCaptureWithoutWritingPixels() {
        rule.activity.getSharedPreferences("shake-reports", Context.MODE_PRIVATE).edit().clear().commit()
        val directory = java.io.File(rule.activity.filesDir, "shake-reports")
        directory.deleteRecursively()
        val session = SessionStore(rule.activity)
        session.name = "synthetic"; session.token = "synthetic-token"
        val reports = ShakeReports(rule.activity)
        try {
            rule.runOnUiThread {
                reports.bind(session.token, true, session.serverProfile, "test", ApiClient())
                reports.resume(); reports.enableCapture(true)
                rule.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                reports.capture()
            }
            rule.waitUntil(5000) { reports.status.startsWith("Aufnahme nicht möglich") }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            rule.activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            reports.destroy(); directory.deleteRecursively()
        }
    }

    @Test fun productionControlsDefaultOffRequireSeparateExplicitUploadConsent() {
        rule.activity.getSharedPreferences("shake-reports", Context.MODE_PRIVATE).edit().clear().commit()
        val reports = ShakeReports(rule.activity)
        try {
            rule.setContent { LayermaxxingTheme("light") {
                CompositionLocalProvider(LocalShakeReports provides reports) { ShakeReportSettings {} }
            } }
            assertFalse(reports.enabled); assertFalse(reports.autoUpload)
            rule.onAllNodes(isToggleable())[0].assertIsOff().performClick()
            assertTrue(reports.enabled)
            rule.onAllNodes(isToggleable())[1].performClick()
            rule.onNodeWithText("Auto-Upload ausdrücklich erlauben?").assertExists()
            assertFalse(reports.autoUpload)
            rule.onNodeWithText("Abbrechen").performClick()
            assertFalse(reports.autoUpload)
            rule.onAllNodes(isToggleable())[1].performClick()
            rule.onNodeWithText("Upload erlauben").performClick()
            assertTrue(reports.autoUpload)
            rule.onAllNodes(isToggleable())[0].performClick()
            assertFalse(reports.enabled); assertFalse(reports.autoUpload)
            rule.onNodeWithText("App jetzt aufnehmen").assertIsNotEnabled()
        } finally { reports.destroy() }
    }
}
