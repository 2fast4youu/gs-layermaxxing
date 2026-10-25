package at.gregor.layermaxxing

import androidx.compose.ui.test.onAllNodesWithTag

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextInput
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
 * POST stage 0, the letter tree (1074): opt-in renders for Veit's review (skipped unless VILLAGE_SHOTS is set; filter
 * '*LetterTreeReview*'). Every state reached by REAL taps; the seal opening is captured frame by frame from the running
 * composable on the test clock (JVM, synthetic – not a phone video). TESTDATEN: PostPilotFixtures; the "server" of the
 * render answers an open call with an invented text.
 * 1074 end-to-end: the tree runs with the app's own open request ([rememberLetterOpener]) and the app's draft / send
 * ([LetterWriter]); the "send" of a render is recorded only (nothing leaves the JVM). The keyboard (IME) is not drawn
 * by Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class LetterTreeReview {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val out = System.getenv("VILLAGE_SHOTS")?.let(::File)
    private val opened = mutableStateMapOf<Long, OpenedMessage>()
    private val slow = HashMap<Long, kotlinx.coroutines.CompletableDeferred<Unit>>()
    private val sent = mutableListOf<ApiClient.SendRequest>()
    private val draft = androidx.compose.runtime.mutableStateOf<LetterDraft?>(null)
    private fun frames(n: Int = 45) { repeat(n) { rule.mainClock.advanceTimeByFrame() }; org.robolectric.shadows.ShadowLooper.idleMainLooper() }
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
                val opener = rememberLetterOpener(opened, fetch = { m ->
                    slow[m.id]?.await()
                    OpenedMessage("Liebe Grüße vom Waldrand! Morgen bringe ich die Äpfel vorbei. (TESTDATEN, Brief ${m.id})", null, null, null)
                }, onDone = {}, onError = {})
                val writer = androidx.compose.runtime.remember {
                    LetterWriter(PostPilotFixtures.pals, listOf(ApiClient.Group(70, "Waldrand-Runde", 1, PostPilotFixtures.pals.take(3))),
                        listOf(ApiClient.FriendshipSettings(40, "Mira", true, true, true, 7200, null, null)), creativeActive = false,
                        transmit = { sent += it; listOf(900L) }, onSent = {}, draftState = draft)
                }
                PostOffice(PostPilotFixtures.pals, PostPilotFixtures.letters, opened, level = 0, openRequests = opener.requests, writer = writer,
                    onOpen = { m -> opener.open(m) }, onLocked = {}, onWrite = {}, onClose = {})
            } }
            if (small) { val d = LocalDensity.current; CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 1.5f)) { c() } } else c()
        }
        frames()
    }
    private fun tap(tag: String, n: Int = 90) {
        if (tag in listOf("post-person", "post-status", "post-mail") && rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
            rule.onNodeWithTag("post-filter", useUnmergedTree = true).performClick(); frames(n)
        }
        scrollTo(tag); rule.onNodeWithTag(tag, useUnmergedTree = true).performClick(); frames(n) }
    private fun exists(tag: String) = rule.onAllNodes(androidx.compose.ui.test.hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    /** The paper scrolls like under a finger until [tag] stands in its middle (animated: run on the test clock). */
    private fun scrollTo(tag: String, top: Boolean = false) {
        if (!exists("tree-page") || !exists(tag)) return
        val page = rule.onNodeWithTag("tree-page", useUnmergedTree = true).fetchSemanticsNode()
        val node = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        if (generateSequence(node.parent) { it.parent }.none { it.id == page.id }) return
        val y = node.positionInRoot.y + if (top) 0f else node.size.height / 2f
        val to = if (top) page.boundsInRoot.top + 8f else page.boundsInRoot.center.y
        rule.onNodeWithTag("tree-page", useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, y - to) }
        frames(40)
    }
    private fun type(text: String) { rule.onNodeWithTag("tree-write-text", useUnmergedTree = true).performTextInput(text); frames(10) }
    private fun write() { tap("tree-pen"); tap("tree-to-40"); type("Liebe Mira, am Samstag am Waldrand? Ich bringe Brot mit. (TESTDATEN)") }
    private fun sendAndWait() {
        tap("tree-write-send")
        repeat(200) { frames(2); if (exists("tree-write-sent")) return; Thread.sleep(25) }
    }

    @Test fun rest() { open(); save("bf-1-ruhe-jetzt") }
    @Test fun sealedClose() { open(); tap("tree-letter-1"); save("bf-2-umschlag-versiegelt-nah") }
    @Test fun sealOpened() { open(); tap("tree-letter-1"); tap("tree-envelope", 260); save("bf-3-siegel-geoeffnet-brieftext") }
    @Test fun history() { open(); tap("tree-time-back"); save("bf-4-zurueck-in-der-zeit") }
    @Test fun openedDirect() { opened[9] = OpenedMessage("Danke für das Rezept! (TESTDATEN, Brief 9)", null, null, null); open(); tap("tree-time-back"); tap("tree-letter-9"); save("bf-5-geoeffnet-direkt") }
    @Test fun pen() { open(); tap("tree-pen"); save("bf-6-fuellfeder-an-wen") }
    // ---- 1074 end-to-end: the letter written on the tree's paper (the app's draft and send)
    @Test fun written() { open(); write(); save("bf-8-brief-geschrieben") }
    @Test fun options() { open(); write(); tap("tree-write-when"); scrollTo("tree-write-when", top = true); save("bf-9-sendoptionen") }
    @Test fun details() { open(); write(); tap("tree-write-details"); scrollTo("tree-write-details", top = true); save("bf-9b-details-anhang") }
    @Test fun invalid() { open(); tap("tree-pen"); tap("tree-to-40"); tap("tree-write-send"); scrollTo("tree-write-problem"); save("bf-10-validierung-leer") }
    @Test fun invalidDuration() {
        open(); write(); tap("tree-write-when")
        rule.onNode(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("3")), useUnmergedTree = true)
            .performTextReplacement("0"); frames(10)
        tap("tree-write-send"); scrollTo("tree-write-problem"); save("bf-10b-validierung-dauer")
    }
    @Test fun draftKept() {
        open(); write(); rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }; frames(60); save("bf-11-zurueck-zum-baum-mit-entwurf")
        tap("tree-pen"); save("bf-12-entwurf-wieder-da")
    }
    @Test fun recipients() { open(); write(); tap("tree-write-to"); tap("tree-to-41"); save("bf-12b-empfaenger-auf-papier") }
    @Test fun sentDone() { open(); write(); sendAndWait(); save("bf-13-versiegelt-gesendet") }
    @Test fun sealWaiting() {
        // the open request answers late: the seal waits; back to the tree and to the envelope again – still one request
        slow[1] = kotlinx.coroutines.CompletableDeferred(); open(); tap("tree-letter-1", 90); tap("tree-envelope"); save("bf-14-siegel-wartet")
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle(); frames(60); rule.waitForIdle(); frames(10); tap("tree-letter-1")
        slow.getValue(1).complete(Unit); frames(60); save("bf-15-antwort-nach-zurueck-geoeffnet-ohne-sprung")
    }
    @Test fun penAnimation() {
        // the paper grows out of the pen, captured every 2 frames from the running composable (test clock)
        open(); rule.onNodeWithTag("tree-pen", useUnmergedTree = true).performClick()
        for (k in 0 until 14) { save("bf-anim-feder-%02d".format(k)); repeat(2) { rule.mainClock.advanceTimeByFrame() }; org.robolectric.shadows.ShadowLooper.idleMainLooper() }
    }
    @Test fun rope() { open(); tap("post-person"); save("bf-7-leine-offen") }
    @Test fun zoomAnimation() {
        // stepping up to the sealed envelope, captured every 2 frames from the running composable (test clock)
        open(); rule.onNodeWithTag("tree-letter-1", useUnmergedTree = true).performClick()
        for (k in 0 until 14) { save("bf-anim-zoom-%02d".format(k)); repeat(2) { rule.mainClock.advanceTimeByFrame() }; org.robolectric.shadows.ShadowLooper.idleMainLooper() }
    }
    @Test fun backAnimation() {
        open(); tap("tree-letter-1")
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        for (k in 0 until 14) { save("bf-anim-zurueck-%02d".format(k)); repeat(2) { rule.mainClock.advanceTimeByFrame() }; org.robolectric.shadows.ShadowLooper.idleMainLooper() }
    }
    @Test fun sealAnimation() {
        // the opening captured from the running composable, every 2 frames (test clock): the seal breaks, then the text
        open(); tap("tree-letter-1")
        rule.onNodeWithTag("tree-envelope", useUnmergedTree = true).performClick()
        for (k in 0 until 16) { save("bf-anim-siegel-%02d".format(k)); repeat(3) { rule.mainClock.advanceTimeByFrame() }; org.robolectric.shadows.ShadowLooper.idleMainLooper() }
    }
    @Config(qualifiers = "w360dp-h640dp-xhdpi") @Test fun restSmall() { open(true); save("bf-klein15-1-ruhe") }
    @Config(qualifiers = "w360dp-h640dp-xhdpi") @Test fun sealedSmall() { open(true); tap("tree-letter-1"); save("bf-klein15-2-umschlag") }
    @Config(qualifiers = "w360dp-h640dp-xhdpi") @Test fun letterSmall() { open(true); tap("tree-letter-1"); tap("tree-envelope", 260); save("bf-klein15-3-brieftext") }
    @Config(qualifiers = "w360dp-h640dp-xhdpi") @Test fun ropeSmall() { open(true); tap("post-person"); save("bf-klein15-4-leine") }
    @Config(qualifiers = "w360dp-h640dp-xhdpi") @Test fun penSmall() { open(true); tap("tree-pen"); save("bf-klein15-5-an-wen") }
    @Config(qualifiers = "w360dp-h640dp-xhdpi") @Test fun writtenSmall() { open(true); write(); save("bf-klein15-6-brief-geschrieben") }
    @Config(qualifiers = "w360dp-h640dp-xhdpi") @Test fun optionsSmall() { open(true); write(); tap("tree-write-when"); scrollTo("tree-write-when", top = true); save("bf-klein15-7-sendoptionen") }
    @Config(qualifiers = "w360dp-h640dp-xhdpi") @Test fun invalidSmall() { open(true); tap("tree-pen"); tap("tree-to-40"); tap("tree-write-send"); scrollTo("tree-write-problem"); save("bf-klein15-8-validierung") }
    @Config(qualifiers = "w360dp-h640dp-xhdpi") @Test fun draftKeptSmall() {
        open(true); write(); rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }; frames(60); save("bf-klein15-9-zurueck-mit-entwurf")
        tap("tree-pen"); save("bf-klein15-10-entwurf-wieder-da")
    }
    @Config(qualifiers = "w360dp-h640dp-xhdpi") @Test fun sendSmall() { open(true); write(); scrollTo("tree-write-send"); save("bf-klein15-11-senden-stempel"); sendAndWait(); save("bf-klein15-12-gesendet") }
}
