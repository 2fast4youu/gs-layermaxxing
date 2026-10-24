package at.gregor.layermaxxing

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Native Robolectric fixtures only; no server, login or real phone crash. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class CrashDiagnosticsUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val report = "SYNTHETIC TEST ONLY - NOT A REAL PHONE CRASH\n" +
        "java.lang.IllegalStateException\nat.gregor.layermaxxing.IslandWorldKt.IslandWorld:7\n" +
        "ISLAND ENTRY friends=3 messages=4 gridVersion=7\n"

    @Before fun reset() {
        CrashDiagnostics.install(rule.activity.applicationContext)
        CrashDiagnostics.clear()
        (rule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
            android.content.ClipData.newPlainText("synthetic", "unchanged sentinel"))
    }
    private fun clipboard() = (rule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .primaryClip?.getItemAt(0)?.text?.toString()

    @Test fun actualDialogSelectsCopiesConfirmsClearsAndCloses() {
        CrashDiagnostics.storage!!.save(report)
        val open = mutableStateOf(true)
        rule.setContent { LayermaxxingTheme("light") {
            if (open.value) CrashDiagnosticsDialog { open.value = false }
        } }
        rule.onNodeWithText(report, substring = true).assertExists()
        assertEquals("unchanged sentinel", clipboard())
        screenshot("synthetic-crash-preview")
        rule.onNodeWithText("Log kopieren").performClick()
        rule.onNodeWithText("Log kopiert").assertExists()
        assertTrue(clipboard()!!.contains(report))
        rule.onNodeWithText("Gespeichertes Log löschen").performClick()
        rule.onNodeWithText("Gespeichertes Log gelöscht").assertExists()
        rule.onNodeWithText("Kein Java-Absturz aufgezeichnet.", substring = true).assertExists()
        assertEquals("", CrashDiagnostics.storage!!.saved())
        rule.onNodeWithText("Schließen").performClick()
        rule.onNodeWithText("Fehlerbericht / Log kopieren").assertDoesNotExist()
    }

    @Test fun startupBlocksDependenciesAndDismissalPersistsUntilANewCrash() {
        CrashDiagnostics.storage!!.save(report)
        var dependenciesComposed = 0
        rule.setContent { LayermaxxingTheme("light") {
            CrashDiagnosticsStartup { dependenciesComposed++; Text("Dependency content") }
        } }
        rule.onNodeWithText("Absturzbericht vorhanden").assertExists()
        assertEquals(0, dependenciesComposed)
        assertEquals("unchanged sentinel", clipboard())
        rule.onNodeWithText("Später").performClick()
        rule.onNodeWithText("Dependency content").assertExists()
        assertTrue(dependenciesComposed > 0)
        assertFalse(CrashStorage(File(rule.activity.filesDir, "crash-diagnostics")).pending())
        assertEquals(report, CrashDiagnostics.storage!!.saved())
        CrashDiagnostics.storage!!.save(report + "new synthetic crash")
        assertTrue(CrashDiagnostics.pending())
    }

    @Test fun startupPromptCopiesOnlyOnDeliberateTapBeforeAnyDependency() {
        CrashDiagnostics.storage!!.save(report)
        var dependenciesComposed = false
        rule.setContent { LayermaxxingTheme("light") {
            CrashDiagnosticsStartup { dependenciesComposed = true; Text("Dependency content") }
        } }
        assertEquals("unchanged sentinel", clipboard())
        rule.onNodeWithText("Log kopieren").performClick()
        rule.onNodeWithText("Log kopiert").assertExists()
        assertFalse(dependenciesComposed)
        assertTrue(clipboard()!!.contains(report))
        assertTrue(CrashDiagnostics.pending())
    }

    @Test fun startupCanPreviewAndCopyBeforeAnyDependency() {
        CrashDiagnostics.storage!!.save(report)
        var dependenciesComposed = false
        rule.setContent { LayermaxxingTheme("light") {
            CrashDiagnosticsStartup { dependenciesComposed = true; Text("Dependency content") }
        } }
        rule.onNodeWithText("Fehlerbericht ansehen").performClick()
        assertFalse(dependenciesComposed)
        rule.onNodeWithText("Log kopieren").performClick()
        assertTrue(clipboard()!!.contains(report))
        rule.onNodeWithText("Schließen").performClick()
        rule.onNodeWithText("Dependency content").assertExists()
        assertFalse(CrashDiagnostics.pending())
        assertEquals(report, CrashDiagnostics.storage!!.saved())
    }

    @Test fun freshMehrEntryOpensCurrentDiagnosticsWithoutIslandOrAccount() {
        rule.setContent { LayermaxxingTheme("light") { MoreHubPreview(null, null) } }
        rule.onNodeWithText("Fehlerbericht / Log kopieren").performScrollTo().performClick()
        rule.onNodeWithText("Kein Java-Absturz aufgezeichnet.", substring = true).assertExists()
        rule.onNodeWithText("Log kopieren").performClick()
        assertTrue(clipboard()!!.contains("Aktueller Lauf"))
        assertTrue(clipboard()!!.contains("System-Prozessenden"))
    }

    @Test fun sharedIslandHostBoundaryRecordsBeforeChildAndDeduplicates() {
        val revision = mutableStateOf(0)
        rule.setContent {
            CrashDiagnosticsIslandEntry(3, 4, 7) {
                assertTrue(CrashDiagnostics.storage!!.events().contains("ISLAND ENTRY friends=3 messages=4 gridVersion=7"))
                Text("Synthetic child ${revision.value}")
            }
        }
        rule.onNodeWithText("Synthetic child 0").assertExists()
        val before = CrashDiagnostics.storage!!.events()
        rule.runOnIdle { revision.value++ }
        rule.onNodeWithText("Synthetic child 1").assertExists()
        assertEquals(before, CrashDiagnostics.storage!!.events())
    }

    private fun screenshot(name: String) {
        val path = System.getenv("CRASH_LOG_SHOTS") ?: return
        rule.waitForIdle()
        rule.runOnUiThread {
            val decor = rule.activity.window.decorView
            val views = (shadowOf(rule.activity.windowManager) as org.robolectric.shadows.ShadowWindowManagerImpl).getViews()
            val view = views.lastOrNull { it !== decor } ?: decor
            check(view.width > 0 && view.height > 0)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(android.graphics.Canvas(bitmap))
                val out = File(path); check(out.isDirectory || out.mkdirs())
                File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { bitmap.recycle() }
        }
    }
}
