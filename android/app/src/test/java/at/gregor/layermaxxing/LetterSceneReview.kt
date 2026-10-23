package at.gregor.layermaxxing

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Briefbaum 22.10. (native scene): opt-in renders for Veit's review (skipped unless VILLAGE_SHOTS is set; filter
 * '*LetterSceneReview*'). Every state reached by REAL taps on the production composable; the frame series are captures
 * of the running native UI on the JVM test clock ("Testclock-Capture", Robolectric – no phone, no video in the app).
 * TESTDATEN: PostPilotFixtures; the "server" answers open / mark calls with invented results.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class LetterSceneReview {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val out = System.getenv("VILLAGE_SHOTS")?.let(::File)
    private val opened = mutableStateMapOf<Long, OpenedMessage>()
    private val draft = androidx.compose.runtime.mutableStateOf<LetterDraft?>(null)
    private fun frames(n: Int = 160) { repeat(n) { rule.mainClock.advanceTimeByFrame() }; org.robolectric.shadows.ShadowLooper.idleMainLooper() }
    private fun save(name: String) {
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp)); out!!.mkdirs()
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }; bmp.recycle()
    }
    private fun open(small: Boolean = false) {
        assumeTrue(out != null)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val c: @Composable () -> Unit = { VillageTheme(true) {
                val opener = rememberLetterOpener(opened, fetch = { m -> OpenedMessage(LetterSceneFixtures.text(m.id), null, null, null) }, onDone = {}, onError = {})
                val marker = rememberUnreadMarker(opened, transmit = { }, onDone = {}, onError = {})
                val writer = remember {
                    LetterWriter(PostPilotFixtures.pals, listOf(ApiClient.Group(70, "Waldrand-Runde", 1, PostPilotFixtures.pals.take(3))), emptyList(),
                        creativeActive = false, transmit = { listOf(900L) }, onSent = {}, draftState = draft)
                }
                PostOffice(PostPilotFixtures.pals, PostPilotFixtures.letters, opened, level = 0, openRequests = opener.requests, writer = writer, unread = marker,
                    onOpen = { m -> opener.open(m) }, onLocked = {}, onWrite = {}, onClose = {})
            } }
            if (small) { val d = LocalDensity.current; CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 1.5f)) { c() } } else c()
        }
        frames(60)
    }
    private fun tag(t: String) = rule.onNodeWithTag(t, useUnmergedTree = true)
    private fun tap(t: String, n: Int = 160) { tag(t).performClick(); frames(n) }
    private fun series(name: String, count: Int, step: Int) {
        for (k in 0 until count) { save("%s-%02d".format(name, k)); frames(step) }
    }

    @Test fun rest() { open(); save("nz-01-baum-ruhe") }
    @Test fun sealed() { open(); tap("tree-letter-1"); save("nz-02-kuvert-versiegelt-nah") }
    @Test fun zoomSeries() { open(); tag("tree-letter-1").performClick(); frames(1); series("nz-anim-zoom", 12, 4) }
    @Test fun openSeries() { open(); tap("tree-letter-1"); tag("tree-envelope").performClick(); frames(1); series("nz-anim-oeffnen", 30, 5) }
    @Test fun reading() { open(); tap("tree-letter-1"); tap("tree-envelope", 260); save("nz-03-brief-schwebt") }
    @Test fun stowSeries() { open(); tap("tree-letter-1"); tap("tree-envelope", 260); rule.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(width * .5f, height - 4f)) }; frames(1); series("nz-anim-zurueck", 26, 5) }
    @Test fun readDirectSeries() {
        opened[2] = OpenedMessage(LetterSceneFixtures.text(2), null, null, null)
        open(); tap("tree-time-back"); tag("tree-letter-2").performClick(); frames(1); series("nz-anim-gelesen-direkt", 22, 5)
    }
    @Test fun longLetterScrolled() {
        opened[2] = OpenedMessage(LetterSceneFixtures.text(2), null, null, null)
        open(); tap("tree-time-back"); tap("tree-letter-2", 260); save("nz-04-langer-brief-oben")
        tag("tree-page").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, 900f) }; frames(40)
        save("nz-05-langer-brief-gescrollt")
    }
    @Test fun markUnreadSeries() {
        opened[2] = OpenedMessage(LetterSceneFixtures.text(2), null, null, null)
        open(); tap("tree-time-back"); tap("tree-letter-2", 260)
        tag("tree-page").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, 5000f) }; frames(40)
        save("nz-06-ungelesen-knopf")
        tag("tree-letter-unread").performClick(); frames(1); series("nz-anim-wieder-versiegeln", 22, 5)
        save("nz-07-wieder-versiegelt")
    }
    @Test fun recipients() { open(); tap("tree-pen"); save("nz-08-empfaenger-knoepfe") }
    @Test fun recipientsChosen() { open(); tap("tree-pen"); tap("tree-to-40"); tap("tree-write-to"); tap("tree-to-41"); save("nz-09-empfaenger-gewaehlt") }

    @Config(qualifiers = "w360dp-h640dp-xhdpi") @Test fun restSmall() { open(true); save("nz-klein15-01-baum") }
    @Config(qualifiers = "w360dp-h640dp-xhdpi") @Test fun sealedSmall() { open(true); tap("tree-letter-1"); save("nz-klein15-02-kuvert") }
    @Config(qualifiers = "w360dp-h640dp-xhdpi") @Test fun readingSmall() { open(true); tap("tree-letter-1"); tap("tree-envelope", 260); save("nz-klein15-03-brief") }
    @Config(qualifiers = "w360dp-h640dp-xhdpi") @Test fun longSmall() {
        opened[2] = OpenedMessage(LetterSceneFixtures.text(2), null, null, null)
        open(true); tap("tree-time-back"); tap("tree-letter-2", 260); save("nz-klein15-04-langer-brief")
    }
    @Config(qualifiers = "w360dp-h640dp-xhdpi") @Test fun recipientsSmall() { open(true); tap("tree-pen"); save("nz-klein15-05-empfaenger") }
}

/** Invented letter texts for the renders and tests (TESTDATEN). */
internal object LetterSceneFixtures {
    fun text(id: Long): String = if (id == 2L) LONG else "Liebe Grüße vom Waldrand! Morgen bringe ich die Äpfel vorbei. (TESTDATEN, Brief $id)"
    private val LONG = "Hallo du,\n\n" + List(9) { i ->
        "Absatz ${i + 1}: Am Morgen lag Nebel über dem Waldrand, und die alte Linde stand ganz still. Ich habe an unseren Spaziergang gedacht und an die Briefe, die wir uns schreiben wollten, bevor der Winter kommt. (TESTDATEN)"
    }.joinToString("\n\n") + "\n\nHerzlich, Mira (TESTDATEN, Brief 2)"
}
