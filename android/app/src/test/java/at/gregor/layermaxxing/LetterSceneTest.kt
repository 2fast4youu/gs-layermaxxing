package at.gregor.layermaxxing

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Briefbaum 22.10.2026, the native letter scene: the state machine and the camera as plain logic; then the production
 * composable with REAL taps (Robolectric, JVM – not a device, 360 × 640 dp, font 1.5): a tap beside the letter puts it
 * back, taps / scrolling on it never do; a read letter comes straight out, a letter marked unread is sealed again and
 * needs the extra tap (through the app's server marker); the same person's previous / newer letter with every gate kept;
 * the recipients are real buttons; a restored scene settles. TESTDATEN: PostPilotFixtures; "server" answers are played.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class LetterSceneTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val calls = mutableListOf<String>()
    private val opened = mutableStateMapOf<Long, OpenedMessage>()
    private val marked = mutableListOf<Long>()
    private var markFails = false
    private val density get() = rule.activity.resources.displayMetrics.density

    private fun frames(n: Int = 140) { rule.waitForIdle(); repeat(n) { rule.mainClock.advanceTimeByFrame() }; rule.waitForIdle() }
    private fun tag(t: String) = rule.onNodeWithTag(t, useUnmergedTree = true)
    private fun tap(t: String, n: Int = 140) { tag(t).performClick(); frames(n) }
    private fun exists(t: String) = rule.onAllNodesWithTag(t, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun shown(text: String) = rule.onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun phase(): ScenePhase = ScenePhase.entries.first { exists("tree-scene-${it.name}") }
    private fun back(n: Int = 220) { rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }; frames(n) }

    private fun start(letters: List<ApiClient.Message> = PostPilotFixtures.letters, withOpened: Map<Long, OpenedMessage> = emptyMap(), restore: StateRestorationTester? = null) {
        opened.putAll(withOpened)
        rule.mainClock.autoAdvance = false
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 1.5f)) {
                VillageTheme(true) {
                    val opener = rememberLetterOpener(opened, fetch = { m -> calls += "open:${m.id}"; OpenedMessage("Inhalt von Brief ${m.id} (TESTDATEN)", null, null, null) },
                        onDone = {}, onError = {})
                    val marker = rememberUnreadMarker(opened, transmit = { m ->
                        calls += "mark:${m.id}"; if (markFails) throw java.io.IOException("TESTDATEN: kein Netz"); marked += m.id
                    }, onDone = {}, onError = { calls += "mark-error" })
                    val writer = remember {
                        LetterWriter(PostPilotFixtures.pals, listOf(ApiClient.Group(70, "Waldrand-Runde", 1, PostPilotFixtures.pals.take(3))), emptyList(),
                            creativeActive = false, transmit = { listOf(900L) }, onSent = {}, draftState = mutableStateOf(null))
                    }
                    PostOffice(PostPilotFixtures.pals, letters, opened, level = 0, openRequests = opener.requests, writer = writer, unread = marker,
                        onOpen = { m -> opener.open(m) }, onLocked = { calls += "locked:${it.id}" }, onWrite = { calls += "write:$it" }, onClose = { calls += "close" })
                }
            }
        }
        if (restore != null) restore.setContent(content) else rule.setContent(content)
        frames()
    }

    // ---------------------------------------------------------------- the state machine (pure)

    @Test fun theSceneTakesEachEventOnlyInItsPhase() {
        val s = LetterScene()
        assertTrue(s.approach(1, read = false))
        assertFalse("a second letter tapped during the zoom", s.approach(2, read = false))
        assertFalse("the envelope is not tappable before the camera arrived", s.tapEnvelope(openedHere = false, busy = false))
        s.arrived(); assertEquals(ScenePhase.SEALED, s.phase)
        assertTrue("first tap asks the server", s.tapEnvelope(openedHere = false, busy = false))
        assertFalse("rapid second tap: no second request", s.tapEnvelope(openedHere = false, busy = false))
        assertEquals(ScenePhase.WAITING, s.phase)
        s.failed(); assertEquals(ScenePhase.SEALED, s.phase)
        // the app's request still runs: wait for it, never a second call
        assertFalse(s.tapEnvelope(openedHere = false, busy = true)); assertEquals(ScenePhase.WAITING, s.phase)
        s.answered(); assertEquals(ScenePhase.UNSEALING, s.phase)
        // back while the seal breaks: it is not cut off; once out, the letter goes back in by itself
        assertTrue(s.out()); assertEquals(ScenePhase.UNSEALING, s.phase)
        s.unsealed(); s.emerged(); assertEquals(ScenePhase.STOWING, s.phase)
        s.stowed(); assertEquals(ScenePhase.LEAVING, s.phase)
        assertNull(s.left()); assertEquals(ScenePhase.REST, s.phase); assertNull(s.letter)
        assertFalse(s.out())
        // a late answer after the view let go changes nothing
        s.answered(); s.emerged(); assertEquals(ScenePhase.REST, s.phase)
    }

    @Test fun aReadLetterComesStraightOutAndMarkedUnreadIsSealedAgain() {
        val s = LetterScene()
        s.approach(9, read = true); s.arrived()
        assertEquals("no seal, no extra tap", ScenePhase.EMERGING, s.phase)
        s.emerged(); assertEquals(ScenePhase.READING, s.phase)
        s.markedUnread(); assertEquals(ScenePhase.STOWING, s.phase); assertTrue(s.reseal)
        s.stowed(); assertEquals(ScenePhase.RESEALING, s.phase)
        s.resealed(); assertEquals(ScenePhase.SEALED, s.phase); assertFalse(s.reseal)
        // sealed again: the extra tap asks the server (the normal open call)
        assertTrue(s.tapEnvelope(openedHere = false, busy = false))
        // the same person's letter: this one goes in, the camera out, then in to the other
        val t = LetterScene(); t.approach(2, true); t.arrived(); t.emerged()
        t.goTo(1, read = false); assertEquals(ScenePhase.STOWING, t.phase)
        t.stowed(); assertEquals(ScenePhase.LEAVING, t.phase)
        assertEquals(1L to false, t.left())
        // back during the way out cancels the way to the other letter
        val u = LetterScene(); u.approach(2, true); u.arrived(); u.emerged(); u.goTo(1, false); u.out(); u.stowed()
        assertNull(u.left())
    }

    @Test fun theCameraLandsTheEnvelopeOnItsTargetAndKeepsThePictureOnScreen() {
        val w = 1080f; val h = 1920f
        val picture = Rect(-120f, 0f, w + 120f, h)
        val rest = Rect(300f, 700f, 420f, 700f + 120f / EnvelopeArt.ASPECT)
        val target = LetterCamera.target(w, h, 160f, 330f)
        assertEquals(EnvelopeArt.ASPECT, target.width / target.height, .01f)
        assertTrue(target.width >= w * .8f)
        assertTrue(LetterCamera.transform(rest, target, picture, w, h, 0f).contentEquals(floatArrayOf(1f, 0f, 0f)))
        val end = LetterCamera.apply(rest, LetterCamera.transform(rest, target, picture, w, h, 1f))
        assertEquals(target.left, end.left, 1f); assertEquals(target.top, end.top, 1f); assertEquals(target.width, end.width, 1f)
        // on the way the scale grows evenly (exponentially) and the picture always covers the screen
        var lastK = 1f
        for (i in 1..20) {
            val t = LetterCamera.transform(rest, target, picture, w, h, i / 20f)
            assertTrue(t[0] > lastK); lastK = t[0]
            val p = LetterCamera.apply(picture, t)
            assertTrue("$p at ${i / 20f}", p.left <= .5f && p.top <= .5f && p.right >= w - .5f && p.bottom >= h - .5f)
        }
    }

    @Test fun neighboursAreTheSamePersonsLettersInTime() {
        val l = PostPilotFixtures.letters
        // Mira (40): 14 (oldest), 3 (sent, travelling), 2 (read), 1 (newest, sealed)
        assertEquals(listOf(14L, 3, 2, 1), PeerLetters.of(l, 40).map { it.id })
        val (prev, next) = PeerLetters.neighbours(l, l.first { it.id == 2L })
        assertEquals(3L, prev!!.id); assertEquals(1L, next!!.id)
        assertNull(PeerLetters.neighbours(l, l.first { it.id == 14L }).first)
        assertNull(PeerLetters.neighbours(l, l.first { it.id == 1L }).second)
        for (m in l) PeerLetters.neighbours(l, m).toList().filterNotNull().forEach { assertEquals(m.peerId, it.peerId) }
        // group letters are not a person's thread
        val group = l.first { it.id == 9L }.copy(id = 99, groupId = 70)
        assertTrue(PeerLetters.of(l + group, group.peerId).none { it.id == 99L })
    }

    // ---------------------------------------------------------------- the production composable

    @Test fun aTapBesideTheLetterPutsItBackTapsAndScrollingOnItDoNot() {
        start(withOpened = mapOf(2L to OpenedMessage(LetterSceneFixtures.text(2), null, null, null)))
        NativePostTestDriver(rule).letter(2); frames(220)
        assertEquals(ScenePhase.READING, phase())
        // on the paper: a tap in its middle, a scroll – nothing closes
        tag("tree-lifted").performTouchInput { click(Offset(width * .5f, height * .4f)) }; frames()
        val page = tag("tree-page").fetchSemanticsNode()
        val before = page.config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("the long letter scrolls", page.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0f)
        tag("tree-page").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 600f) }; frames()
        assertTrue(tag("tree-page").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > before)
        assertEquals(ScenePhase.READING, phase())
        // the buttons at the paper's bottom stay whole on the paper and on screen while the text scrolls
        val paper = tag("tree-lifted").fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        for (b in listOf("tree-letter-prev", "tree-letter-next")) {
            val r = tag(b).fetchSemanticsNode().boundsInRoot
            assertTrue("$b $r on $paper", r.top >= paper.top && r.bottom <= paper.bottom + 1 && r.bottom <= root.bottom && r.height >= 48 * density - 1)
        }
        // beside it: back into the envelope (no new seal: it stays read), then the camera moves out
        rule.onRoot().performTouchInput { click(Offset(width * .5f, height - 4f)) }; rule.waitForIdle()
        repeat(4) { rule.mainClock.advanceTimeByFrame() }; rule.waitForIdle()
        assertEquals(ScenePhase.STOWING, phase())
        frames(220)
        assertEquals(ScenePhase.REST, phase())
        assertTrue(calls.none { it.startsWith("open:") || it.startsWith("mark:") })
        // the same letter again: straight out, no seal, no extra tap
        NativePostTestDriver(rule).letter(2); frames(220)
        assertEquals(ScenePhase.READING, phase()); assertFalse(exists("tree-envelope"))
        // system back = the same way out
        back(); assertEquals(ScenePhase.REST, phase())
    }

    @Test fun markedUnreadIsSealedAgainThroughTheServerAndNeedsTheExtraTap() {
        start(withOpened = mapOf(2L to OpenedMessage(LetterSceneFixtures.text(2), null, null, null)))
        NativePostTestDriver(rule).letter(2); frames(220)
        // a read-once letter is not offered (it is consumed); this one is
        tag("tree-page").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 9000f) }; frames()
        // the server fails first: said on the paper, the letter stays read and open
        markFails = true
        tap("tree-letter-unread")
        assertEquals(listOf("mark:2", "mark-error"), calls)
        assertTrue(exists("tree-letter-unread-failed") && phase() == ScenePhase.READING && opened[2L] != null)
        markFails = false
        tag("tree-letter-unread").performClick(); rule.waitForIdle()
        repeat(3) { rule.mainClock.advanceTimeByFrame() }; rule.waitForIdle()
        // the server keeps the mark; the app's copy of the text is dropped (it is sealed for real)
        assertEquals(listOf(2L), marked); assertNull(opened[2L])
        // back in, a fresh seal pressed on, the camera stays: the extra tap is needed
        val seen = mutableListOf<ScenePhase>()
        repeat(200) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle(); phase().let { if (seen.lastOrNull() != it) seen += it } }
        assertEquals(listOf(ScenePhase.STOWING, ScenePhase.RESEALING, ScenePhase.SEALED), seen.filter { it != ScenePhase.READING }.take(3))
        assertTrue(exists("tree-envelope") && !exists("tree-lifted"))
        assertEquals("Siegel lösen und Brief öffnen", tag("tree-envelope").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString())
        assertTrue(shown("ungelesen") || shown("Zum Öffnen"))
        assertTrue(calls.none { it.startsWith("open:") })
        // Out to the tree: sealed again in its unchanged chronological slot.
        back()
        assertTrue(rule.onAllNodesWithTag("tree-letter-2", useUnmergedTree = true).fetchSemanticsNodes().single()
            .config[SemanticsProperties.ContentDescription].joinToString().contains("versiegelt"))
        // opened again: the extra tap is the normal open call (the server checks the gates and lifts the mark)
        tap("tree-letter-2"); tap("tree-envelope", 260)
        assertEquals(listOf("open:2"), calls.filter { it.startsWith("open:") })
        assertEquals(ScenePhase.READING, phase())
        assertTrue(shown("Inhalt von Brief 2 (TESTDATEN)"))
    }

    @Test fun aReadOnceLetterOffersNoUnreadAndAnUnlockedButUnmarkedLetterIsNeverRefetched() {
        val once = PostPilotFixtures.letters.map { if (it.id == 9L) it.copy(oneTime = true) else it }
        start(letters = once, withOpened = mapOf(9L to OpenedMessage("Inhalt von Brief 9 (TESTDATEN)", null, null, null)))
        NativePostTestDriver(rule).letter(9); frames(220)
        assertTrue(exists("tree-letter-unread-none") && !exists("tree-letter-unread"))
        assertTrue(calls.isEmpty())
    }

    @Test fun theSamePersonsPreviousAndNewerLetterKeepEveryGate() {
        // A real incoming mutual-gated predecessor; outgoing 3 is outside the received archive.
        start(letters = PostPilotFixtures.letters.map { if (it.id == 4L) it.copy(peerId = 40, peerName = "Mira") else it }, withOpened = mapOf(2L to OpenedMessage(LetterSceneFixtures.text(2), null, null, null)))
        NativePostTestDriver(rule).letter(2); frames(220)
        // Previous: incoming letter 4, mutual-gated; locked view, no text, no move.
        assertTrue(tag("tree-letter-prev").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString().contains("versiegelt"))
        tap("tree-letter-prev")
        assertTrue(exists("post-arrival")); assertTrue(calls.isEmpty())
        // At font 1.5 the arrival action is below the dialog viewport. Drive the
        // owning scroll with frames before the real click; an offscreen click is not a request.
        NativePostTestDriver(rule).tap("post-arrival-action"); frames()
        assertEquals(listOf("locked:4"), calls); assertEquals(ScenePhase.READING, phase())
        // newer: letter 1 (arrived, sealed) – back in, out, in to letter 1: it stays sealed until its own tap
        tap("tree-letter-next", 400)
        assertEquals(ScenePhase.SEALED, phase())
        assertTrue(exists("tree-envelope") && !exists("tree-letter-text"))
        assertTrue(calls.none { it.startsWith("open:") })
        tap("tree-envelope", 260)
        assertEquals(ScenePhase.READING, phase()); assertTrue(shown("Inhalt von Brief 1 (TESTDATEN)"))
        // the newest letter: "neuer" is there but disabled; "früher" leads back to letter 2 (read: straight out)
        assertTrue(tag("tree-letter-next").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Disabled) != null)
        tap("tree-letter-prev", 400)
        assertEquals(ScenePhase.READING, phase())
        assertTrue(shown("Absatz 1"))
        assertEquals(listOf("locked:4", "open:1"), calls)
    }

    @Test fun theRecipientsAreRealButtons() {
        start()
        tap("tree-pen", 60)
        val names = PostPilotFixtures.pals.map { "tree-to-${it.id}" } + "tree-to-group-70"
        for (t in names) {
            val n = tag(t).fetchSemanticsNode()
            assertEquals(Role.Button, n.config.getOrNull(SemanticsProperties.Role))
            assertTrue("$t ${n.boundsInRoot}", n.boundsInRoot.height >= 48 * density - 1)
        }
        // two to a row: Mira and Jonte side by side
        val a = tag("tree-to-40").fetchSemanticsNode().boundsInRoot; val b = tag("tree-to-41").fetchSemanticsNode().boundsInRoot
        assertEquals(a.top, b.top, 1f); assertTrue(b.left > a.right)
        tap("tree-to-40", 60)
        assertTrue(exists("tree-write-text"))
        assertEquals(listOf<String>(), calls.filter { it.startsWith("write:") })
        tap("tree-write-to", 60)
        assertEquals(Role.Checkbox, tag("tree-to-40").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
        assertEquals(true, tag("tree-to-40").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
    }

    @Test fun aRestoredSceneSettlesWithItsLetter() {
        val restore = clockControlledRestorationTester(rule)
        start(withOpened = mapOf(9L to OpenedMessage("Inhalt von Brief 9 (TESTDATEN)", null, null, null)), restore = restore)
        tap("post-filter"); tap("post-mail"); NativePostTestDriver(rule).scrollTo("post-select-9"); tag("post-select-9").performClick(); rule.waitForIdle()
        repeat(60) { rule.mainClock.advanceTimeByFrame() }; rule.waitForIdle()
        // mid-way out of the envelope: restored, it is simply open
        restore.emulateSavedInstanceStateRestore(); frames()
        assertEquals(ScenePhase.READING, phase())
        assertTrue(shown("Inhalt von Brief 9 (TESTDATEN)"))
        back(); assertEquals(ScenePhase.REST, phase())
    }

}
