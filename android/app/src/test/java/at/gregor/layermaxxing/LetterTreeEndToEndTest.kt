package at.gregor.layermaxxing

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CompletableDeferred
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
 * 1074 end-to-end on the letter tree (Robolectric, JVM – not a device), the production pieces wired as the app wires
 * them: [rememberLetterOpener] (the app's one open request per letter) and [LetterWriter] / [LetterDraft] (the app's
 * draft and its sealed send). Played here are only the server's answers: an open that answers late, fails or answers;
 * a send that is recorded (never sent anywhere) or fails. TESTDATEN: PostPilotFixtures, no account, no token.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
open class LetterTreeEndToEndTest {
    protected open val postLevel: Int = 0
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val calls = mutableListOf<String>()
    private val opened = mutableStateMapOf<Long, OpenedMessage>()
    /** Open answers held back by the "server" until the test lets them go (a slow network). */
    private val slow = HashMap<Long, CompletableDeferred<Unit>>()
    private var openFails = false
    private val sent = mutableListOf<ApiClient.SendRequest>()
    private var sendFails = false
    private val draft = mutableStateOf<LetterDraft?>(null)
    private val groups = listOf(ApiClient.Group(70, "Waldrand-Runde", 1, PostPilotFixtures.pals.take(3)))
    // Mira's friendship asks for at least two hours until a letter may open
    private val settings = listOf(ApiClient.FriendshipSettings(40, "Mira", true, true, true, 7200, null, null))

    private fun frames(n: Int = 140) { rule.waitForIdle(); repeat(n) { rule.mainClock.advanceTimeByFrame() }; rule.waitForIdle() }
    private fun tag(t: String) = rule.onNodeWithTag(t, useUnmergedTree = true)
    /** A tap like a finger's: what lies further down the paper is scrolled into view first (the paper scrolls). */
    private fun tap(t: String) {
        if (postLevel > 0 && t.startsWith("tree-letter-") && !exists(t)) {
            NativePostTestDriver(rule).letter(t.removePrefix("tree-letter-").toLong())
            return
        }
        scrollTo(t); tag(t).performClick(); frames() }
    /** Scrolls the paper (tree-page) so that [t] stands in its middle, as a finger would. */
    private fun scrollTo(t: String) {
        if (!exists("tree-page")) return
        val page = tag("tree-page").fetchSemanticsNode()
        val node = tag(t).fetchSemanticsNode()
        if (generateSequence(node.parent) { it.parent }.none { it.id == page.id }) return
        val y = node.positionInRoot.y + node.size.height / 2f
        val mid = page.boundsInRoot.center.y
        if (kotlin.math.abs(y - mid) > page.boundsInRoot.height / 2f - node.size.height / 2f) {
            // (the scroll is animated: let it run on the test clock)
            tag("tree-page").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, y - mid) }
            frames(40)
        }
    }
    private fun exists(t: String) = rule.onAllNodesWithTag(t, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun shown(text: String) = rule.onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun back() { rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }; frames(200) }
    private fun onTrunk(): List<Long> = rule.onAllNodes(SemanticsMatcher("letter") { it.config.getOrNull(SceneSlotKey)?.startsWith("letter:") == true }, useUnmergedTree = true)
        .fetchSemanticsNodes().sortedWith(compareBy({ it.boundsInRoot.top.toInt() / 40 }, { it.boundsInRoot.left })).map { it.config[SceneSlotKey].removePrefix("letter:").toLong() }
    /** Sealing runs off the main thread (EvidenceProtocol on Dispatchers.Default): wait for it in real time. */
    private fun until(what: () -> Boolean) { repeat(200) { frames(2); if (what()) return; Thread.sleep(25) }; assertTrue("timed out", what()) }

    private fun start(withOpened: Map<Long, OpenedMessage> = emptyMap(), fontScale: Float = 1.5f) {
        opened.putAll(withOpened)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = fontScale)) {
                VillageTheme(true) {
                    val opener = rememberLetterOpener(opened, fetch = { m ->
                        calls += "open:${m.id}"
                        slow[m.id]?.await()
                        if (openFails) throw java.io.IOException("TESTDATEN: kein Netz")
                        OpenedMessage("Inhalt von Brief ${m.id} (TESTDATEN)", null, null, null)
                    }, onDone = { calls += "refresh" }, onError = { calls += "error" })
                    val writer = remember {
                        LetterWriter(PostPilotFixtures.pals, groups, settings, creativeActive = false,
                            transmit = { request -> sent += request; if (sendFails) throw java.io.IOException("TESTDATEN: Server nicht erreichbar"); listOf(900L) },
                            onSent = { calls += "sent" }, draftState = draft)
                    }
                    PostOffice(PostPilotFixtures.pals, PostPilotFixtures.letters, opened, level = postLevel, openRequests = opener.requests, writer = writer,
                        onOpen = { m -> calls += "tap:${m.id}"; opener.open(m) }, onLocked = { calls += "locked:${it.id}" },
                        onWrite = { calls += "sheet:$it" }, onClose = { calls += "close" })
                }
            }
        }
        frames()
    }

    // ---------------------------------------------------------------- opening: one request per letter, the view lets go

    @Test fun sealBackSealBeforeTheAnswerAsksTheServerOnceAndTheAnswerDoesNotPullTheViewForward() {
        slow[1] = CompletableDeferred()
        start()
        tap("tree-letter-1")
        // a double tap on the envelope: one request
        tag("tree-envelope").performClick(); tag("tree-envelope").performClick(); frames()
        assertEquals(listOf("tap:1", "open:1"), calls)
        assertTrue(shown("wird geöffnet"))
        // back to the tree while the server still works; then the same envelope again and its seal again
        back()
        assertFalse(exists("tree-lifted"))
        tap("tree-letter-1")
        assertTrue(shown("wird geöffnet"))
        assertEquals("Brief wird geöffnet", tag("tree-envelope").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString())
        tag("tree-envelope").performClick(); tag("tree-envelope").performClick(); frames()
        assertEquals("still the one request", 1, calls.count { it == "open:1" })
        // back to the tree again; the answer arrives: the letter is opened on the tree, the view stays where it is
        back()
        slow.getValue(1).complete(Unit); frames(60)
        assertTrue(opened[1L] != null)
        assertFalse("no step forward to the letter", exists("tree-lifted") || exists("tree-letter-text"))
        assertTrue("letter 1 moved to the opened ones", 1L !in onTrunk().take(3))
        assertEquals(1, calls.count { it == "open:1" })
    }

    @Test fun anAnswerWhileTheEnvelopeIsLookedAtAgainShowsItOpenWithoutJumpingToTheText() {
        slow[6] = CompletableDeferred()
        start()
        tap("tree-letter-6"); tap("tree-envelope")
        back()
        tap("tree-letter-6")
        slow.getValue(6).complete(Unit); frames(60)
        // the read state is shown on the envelope (no seal any more), the text only on request: a tap on the envelope
        assertTrue(exists("tree-envelope") && !exists("tree-lifted") && !exists("tree-letter-text"))
        assertTrue(shown("Geöffnet"))
        assertEquals("Kuvert öffnen und Brief lesen", tag("tree-envelope").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString())
        tap("tree-envelope"); frames(120)
        assertTrue(exists("tree-letter-text") && shown("Inhalt von Brief 6 (TESTDATEN)"))
        assertEquals(1, calls.count { it == "open:6" })
    }

    @Test fun aFailedOpenIsSaidAndMayBeTriedAgainNeverTwiceAtOnce() {
        openFails = true
        start(withOpened = emptyMap())
        tap("tree-letter-1"); tap("tree-envelope")
        assertTrue(shown("Öffnen hat nicht geklappt") && exists("tree-envelope") && !exists("tree-letter-text"))
        assertEquals(listOf("tap:1", "open:1", "error"), calls)
        // retry with the network back: a second (not a parallel) request opens it
        openFails = false; slow[1] = CompletableDeferred()
        tag("tree-envelope").performClick(); tag("tree-envelope").performClick(); frames()
        assertEquals(2, calls.count { it == "open:1" })
        slow.getValue(1).complete(Unit); frames(200)
        assertTrue(exists("tree-letter-text") && shown("Inhalt von Brief 1 (TESTDATEN)"))
        // an opened letter whose text is not here: fetched once; a failure is said with a way to try again
        back()
        openFails = true
        tap("tree-time-back"); tap("tree-letter-13"); frames(120)
        assertTrue(shown("Der Brief konnte nicht geholt werden."))
        assertEquals(1, calls.count { it == "open:13" })
        openFails = false
        tap("tree-letter-retry")
        assertTrue(shown("Inhalt von Brief 13 (TESTDATEN)"))
        assertEquals(2, calls.count { it == "open:13" })
    }

    @Test fun travellingLettersStayBehindTheirGateAndNeverAskToOpen() {
        start()
        tap("tree-letter-3"); assertTrue(exists("post-arrival")); tap("post-arrival-action")
        tap("tree-letter-8"); assertTrue(exists("post-arrival")); tap("post-arrival-action")
        assertEquals(listOf("locked:3", "locked:8"), calls)
        assertFalse(exists("tree-envelope") || exists("tree-lifted"))
        tap("tree-padlock"); assertTrue(exists("post-arrival")); tap("post-arrival-action")
        assertTrue(calls.last().startsWith("locked:") && calls.none { it.startsWith("open:") })
    }

    @Test fun reducedMotionLetsGoAtOnceAndOpensAtOnce() {
        android.provider.Settings.Global.putFloat(rule.activity.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            slow[1] = CompletableDeferred()
            start()
            tap("tree-letter-1"); tap("tree-envelope")
            rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }; frames(3)
            assertFalse(exists("tree-lifted"))
            slow.getValue(1).complete(Unit); frames(4)
            assertFalse(exists("tree-lifted"))
            // a seal answered at once: the text after a few frames (no 760 ms crack, no 720 ms camera, no unfolding)
            tag("tree-letter-6").performClick(); frames(3)
            tag("tree-envelope").performClick(); frames(10)
            assertTrue(exists("tree-letter-text") && shown("Inhalt von Brief 6 (TESTDATEN)"))
        } finally {
            android.provider.Settings.Global.putFloat(rule.activity.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }
    }

    // ---------------------------------------------------------------- writing: on the tree's paper, the app's draft and send

    private fun field(value: String) = rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(value)), useUnmergedTree = true)

    @Test fun thePenWritesTheWholeLetterOnTheTreesPaperWithTheAppsSealAndSend() {
        start()
        tap("tree-pen")
        assertTrue(shown("An wen?"))
        tap("tree-to-40")
        // the letter is written on the painted paper (no sheet, no other screen): the field lies on the paper
        val paper = tag("tree-lifted").fetchSemanticsNode().boundsInRoot
        val text = tag("tree-write-text").fetchSemanticsNode().boundsInRoot
        assertTrue("$text on $paper", text.left >= paper.left - 1 && text.right <= paper.right + 1 && text.top >= paper.top - 1)
        assertTrue(calls.none { it.startsWith("sheet:") })
        assertTrue(shown("An Mira"))
        // validation: an empty letter does not go; it says why
        tap("tree-write-send")
        assertTrue(dump(), shown("Der Brief ist noch leer.")); assertTrue(sent.isEmpty())
        tag("tree-write-text").performTextInput("Liebe Mira, bis Samstag am Waldrand! (TESTDATEN)"); frames()
        // when it may open: a duration of 0 does not go; the friendship's minimum is said
        tap("tree-write-when")
        assertTrue(exists("tree-write-modes"))
        assertTrue(shown("Diese Freundschaft verlangt mindestens"))
        field("3").performTextReplacement("0"); frames()
        tap("tree-write-send")
        assertTrue(shown("Die Dauer bis zum Öffnen muss größer als 0 sein.")); assertTrue(sent.isEmpty())
        field("0").performTextReplacement("2"); frames()
        // details: title, public envelope text, read once
        tap("tree-write-details")
        tag("tree-write-title").performTextInput("Samstag (TESTDATEN)"); frames()
        tap("tree-write-onetime")
        // the honest note on the key
        tap("tree-write-more")
        assertTrue(shown("Der Server hält den AES-Schlüssel bis zur Freigabe"))
        // manual release: asked once more, then sealed and sent through the app's send
        tap("tree-mode-MANUAL")
        tap("tree-write-send")
        assertTrue(shown("Manuell senden?")); assertTrue(sent.isEmpty())
        tap("tree-write-manual-yes")
        until { sent.isNotEmpty() && exists("tree-write-sent") }
        val r = sent.single()
        assertEquals(listOf(40L), r.recipientIds); assertNull(r.groupId)
        assertEquals("manual", r.mode); assertNull(r.releaseAt)
        assertEquals("Samstag (TESTDATEN)", r.title); assertTrue(r.oneTime)
        // sealed: no plain text leaves the app, the evidence (commitment + signature) goes with it
        assertFalse(r.encrypted.ciphertext.contains("Waldrand"))
        assertTrue(r.evidence != null && r.evidence!!.signature.isNotBlank())
        assertTrue(shown("✓ Verschlüsselt gesendet")); assertTrue("sent" in calls)
        assertNull(draft.value)
        tap("tree-write-done")
        assertFalse(exists("tree-lifted"))
    }

    @Test fun goingBackKeepsTheDraftAndTheRecipientsChangeOnThePaper() {
        start()
        tap("tree-pen"); tap("tree-to-40")
        tag("tree-write-text").performTextInput("Entwurf für Mira (TESTDATEN)"); frames()
        // system back: to the tree, the draft stays
        back()
        assertFalse(exists("tree-lifted"))
        tap("tree-pen")
        assertTrue("straight back to the letter", exists("tree-write-text"))
        field("Entwurf für Mira (TESTDATEN)").assertExistsSafely()
        // to whom, changed on the paper: Jonte too
        tap("tree-write-to")
        assertTrue(shown("An wen?"))
        // the recipients are real buttons: Mira ticked (a checkbox with the draft), Jonte not yet
        assertEquals(true, tag("tree-to-40").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        assertEquals(androidx.compose.ui.semantics.Role.Checkbox, tag("tree-to-40").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
        assertEquals(false, tag("tree-to-41").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        tap("tree-to-41"); tap("tree-to-done")
        assertTrue(shown("An Mira, Jonte"))
        // or a group instead
        tap("tree-write-to"); tap("tree-to-group-70"); tap("tree-to-done")
        assertTrue(shown("An Gruppe Waldrand-Runde"))
        // the "put back" stamp keeps it as well
        tap("tree-write-later")
        assertFalse(exists("tree-lifted")); assertTrue(draft.value?.text == "Entwurf für Mira (TESTDATEN)")
        // discarding asks once more, then the pen starts a new letter
        tap("tree-pen"); tap("tree-write-discard")
        assertTrue(shown("wirklich verwerfen"))
        tap("tree-write-discard-yes")
        assertNull(draft.value)
        tap("tree-pen")
        assertTrue(shown("An wen?") && !exists("tree-write-text"))
        assertTrue(calls.none { it.startsWith("sheet:") })
    }

    @Test fun aFailedSendIsSaidAndTheDraftStaysForASecondTry() {
        sendFails = true
        start()
        tap("tree-pen"); tap("tree-to-41")
        tag("tree-write-text").performTextInput("Hallo Jonte (TESTDATEN)"); frames()
        tap("tree-write-send")
        until { sent.size == 1 && exists("tree-write-notice") }
        assertTrue(shown("TESTDATEN: Server nicht erreichbar"))
        field("Hallo Jonte (TESTDATEN)").assertExistsSafely()
        assertTrue(calls.none { it == "sent" })
        sendFails = false
        tap("tree-write-send")
        until { sent.size == 2 && exists("tree-write-sent") }
        assertEquals(listOf(41L), sent.last().recipientIds)
        assertEquals("timed", sent.last().mode)
    }

    private fun dump() = rule.onNode(androidx.compose.ui.test.isRoot(), useUnmergedTree = true).printToString(maxDepth = 60)
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsSafely() { fetchSemanticsNode() }
}
