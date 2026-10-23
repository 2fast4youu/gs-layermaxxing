package at.gregor.layermaxxing

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * POST stage 0, the letter tree (1074, Veit's final changes; 22.10. native scene): order sealed → travelling → opened,
 * the time arrow, the sealed envelope opened only through the real open call (exactly once, the seal breaks only when
 * the letter came), opened letters straight out of their envelope, travelling letters stay closed, the pen writes, back
 * keeps page and filter.
 * Real taps on a 360 × 640 dp phone with a 1.5× font (Robolectric, JVM – not a device). TESTDATEN: PostPilotFixtures.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class LetterTreeTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val density get() = rule.activity.resources.displayMetrics.density
    private val calls = mutableListOf<String>()
    private val letters = PostPilotFixtures.letters
    private val opened = mutableStateMapOf<Long, OpenedMessage>()
    /** What the server does on an open call in this test: answer (the letter's content) or fail (no network). */
    private var serverAnswers = true
    private fun frames(n: Int = 140) { rule.waitForIdle(); repeat(n) { rule.mainClock.advanceTimeByFrame() }; rule.waitForIdle() }
    private fun tag(t: String) = rule.onNodeWithTag(t, useUnmergedTree = true)
    private fun tap(t: String) { tag(t).performClick(); frames() }
    private fun exists(t: String) = rule.onAllNodesWithTag(t, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun desc(t: String) = tag(t).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()
    private fun onTrunk(): List<Long> = rule.onAllNodes(SemanticsMatcher("letter") { it.config.getOrNull(SceneSlotKey)?.startsWith("letter:") == true }, useUnmergedTree = true)
        .fetchSemanticsNodes().sortedWith(compareBy({ it.boundsInRoot.top.toInt() / 40 }, { it.boundsInRoot.left })).map { it.config[SceneSlotKey].removePrefix("letter:").toLong() }
    private fun text(id: Long) = rule.onAllNodesWithText("Brief $id", substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun start(withOpened: Map<Long, OpenedMessage> = emptyMap()) {
        opened.putAll(withOpened)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 1.5f)) {
                VillageTheme(true) {
                    // 1074 end-to-end: the app's real open request (LetterOpener: one per letter, failures kept for a retry);
                    // only the server's answer is played here
                    val opener = rememberLetterOpener(opened, fetch = { m ->
                        calls += "open:${m.id}"
                        if (!serverAnswers) throw java.io.IOException("TESTDATEN: kein Netz")
                        OpenedMessage("Inhalt von Brief ${m.id} (TESTDATEN)", null, null, null)
                    }, onDone = {}, onError = {})
                    PostOffice(PostPilotFixtures.pals, letters, opened, level = 0, openRequests = opener.requests,
                        onOpen = { m -> opener.open(m) },
                        onLocked = { calls += "locked:${it.id}" }, onWrite = { calls += "write:$it" }, onClose = { calls += "close" })
                }
            }
        }
        frames()
    }

    @Test fun theOrderIsSealedThenTravellingThenOpenedFromKnownDataOnly() {
        // sealed (arrived, unopened) newest first; travelling: known arrival soonest first, unknown arrival after (never as
        // 0); opened / sent newest first; ties by id
        val order = TreeOrder.order(letters, emptyMap<Long, Any>()).map { it.id }
        assertEquals(listOf(1L, 6, 11, 14, 3, 8, 4, 7, 12, 2, 5, 9, 10, 13), order)
        assertEquals(order, TreeOrder.order(letters.reversed(), emptyMap<Long, Any>()).map { it.id })  // stable for any input order
        // a letter opened here moves from the sealed to the opened group
        assertEquals(TreeOrder.Kind.OPENED, TreeOrder.kind(letters.first { it.id == 1L }, true))
        // a travelling letter is never SEALED (the red seal is no time / friendship gate)
        assertTrue(letters.filter { Conversations.letterState(it, false).locked }.all { TreeOrder.kind(it, false) == TreeOrder.Kind.TRAVEL })
        // two pages: now and one step back in time, every letter on one of them
        assertEquals(2, LetterTree.pages(letters.size))
        assertEquals(order.take(9), TreeOrder.page(TreeOrder.order(letters, emptyMap<Long, Any>()), 0).map { it.id })
        assertEquals(order.drop(9), TreeOrder.page(TreeOrder.order(letters, emptyMap<Long, Any>()), 1).map { it.id })
    }

    @Test fun theSealOpensOnlyThroughTheServerAndExactlyOnce() {
        start()
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        // no app chrome on the picture (no header, golden action, story buttons)
        assertTrue(rule.onAllNodesWithText("Brief schreiben").fetchSemanticsNodes().isEmpty())
        assertEquals(listOf(1L, 6, 11, 14, 3, 8, 4, 7, 12), onTrunk())
        // tap the sealed letter: the camera moves to its envelope, still sealed; no text yet, no call yet
        tap("tree-letter-1")
        assertTrue(exists("tree-envelope") && !exists("tree-letter-text") && calls.isEmpty())
        assertTrue(tag("tree-envelope").fetchSemanticsNode().boundsInRoot.let { it.width >= 48 * density - 1 && it.height >= 48 * density - 1 && it.right <= root.right + 1 })
        // the server fails this time (no network): one call for a double tap, the seal holds, nothing is shown as opened,
        // the caption says so and the envelope may be tapped again
        serverAnswers = false
        tag("tree-envelope").performClick(); tag("tree-envelope").performClick(); frames()
        assertEquals(listOf("open:1"), calls)
        assertTrue(exists("tree-envelope") && !exists("tree-letter-text"))
        assertTrue(rule.onAllNodesWithText("Öffnen hat nicht geklappt", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        // the server answers: the seal breaks, the letter comes out and its text is on the paper
        serverAnswers = true
        tap("tree-envelope")
        assertEquals(listOf("open:1", "open:1"), calls)
        frames(200)
        assertTrue(exists("tree-letter-text") && !exists("tree-envelope"))
        assertTrue(rule.onAllNodesWithText("Inhalt von Brief 1 (TESTDATEN)").fetchSemanticsNodes().isNotEmpty())
        // back: into the envelope, out to the tree; letter 1 is now opened and has moved to the opened letters
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }; frames(200)
        assertTrue(!exists("tree-lifted") && exists("tree-scene-REST"))
        assertTrue(1L !in onTrunk().take(3))
    }

    @Test fun openedLettersGoStraightToTheirTextTravellingOnesStayClosed() {
        start(mapOf(9L to OpenedMessage("Inhalt von Brief 9 (TESTDATEN)", null, null, null)))
        // travelling (sealed by time / both agree): the existing locked view, no close-up, no open call
        tap("tree-letter-3")
        assertTrue(exists("post-arrival")); assertTrue(calls.isEmpty())
        CodexMotionHarness(rule).scrollSelectorTo("post-arrival-action")
        tap("post-arrival-action")
        assertEquals(listOf("locked:3"), calls); assertTrue(!exists("tree-lifted"))
        // an opened letter on the older page: straight to its text, no seal
        calls.clear()
        // the arrow's word is whole on screen at 360 dp / font 1.5
        assertTrue(tag("post-time-label").fetchSemanticsNode().boundsInRoot.width > 0f)
        tap("tree-time-back")
        assertEquals(listOf(2L, 5, 9, 10, 13), onTrunk())
        tap("tree-letter-9"); frames(120)
        // straight out of its envelope: no seal, no extra tap on the envelope
        assertTrue(!exists("tree-envelope") && exists("tree-letter-text") && exists("tree-scene-READING"))
        assertTrue(rule.onAllNodesWithText("Inhalt von Brief 9 (TESTDATEN)").fetchSemanticsNodes().isNotEmpty())
        assertTrue(calls.isEmpty())   // its text was here: no second fetch
        // an opened letter whose text is not here: fetched once through the same open call, then shown
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }; frames(200)
        tap("tree-letter-13"); frames(120)
        assertEquals(listOf("open:13"), calls)
        frames(); assertTrue(rule.onAllNodesWithText("Inhalt von Brief 13 (TESTDATEN)").fetchSemanticsNodes().isNotEmpty())
        // back keeps the older page (and the time arrow forward to now is there)
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }; frames(200)
        assertEquals(listOf(2L, 5, 9, 10, 13), onTrunk())
        tap("tree-time-now")
        assertEquals(listOf(1L, 6, 11, 14, 3, 8, 4, 7, 12), onTrunk())
    }

    @Test fun filtersCombineWithTheOrderAndTheTimePages() {
        start()
        tap("tree-rope"); tap("tree-strip-40")                 // Mira: 1, 2, 3, 14
        assertEquals(listOf(1L, 14, 3, 2), onTrunk())
        assertTrue(!exists("tree-time-back"))
        tap("tree-belt"); tap("tree-window-UNREAD")            // Mira × Ungelesen: the arrived ones (1, 14)
        assertEquals(PostViews.shown(letters, emptyMap<Long, Any>(), PostViews.View.UNREAD, 40, "").map { it.id }.toSet(), onTrunk().toSet())
        assertEquals(listOf(1L, 14), onTrunk())
        tap("tree-belt")
        val counts = PostViews.counts(letters, emptyMap<Long, Any>(), 40, "")
        for (v in PostViews.View.entries) assertEquals("${v.label}: ${counts[v]}", desc("tree-window-${v.name}"))
    }

    @Test fun theFountainPenWritesANewLetter() {
        start()
        val pen = tag("tree-pen").fetchSemanticsNode().boundsInRoot
        assertTrue("$pen", pen.width >= 48 * density - 1 && pen.height >= 48 * density - 1)
        assertTrue(!exists("tree-sheet-new"))
        tap("tree-pen")
        assertTrue(exists("tree-lifted"))
        tap("tree-to-41")
        assertEquals(listOf("write:41"), calls)
    }

    @Test fun namedPersonSelectionIsReadableScrollableAndDismissible() {
        start(); tap("post-person")
        assertTrue(exists("post-person-search"))
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val selector = tag("post-selector").fetchSemanticsNode().boundsInRoot
        assertTrue(selector.left >= root.left && selector.right <= root.right)
        assertTrue(selector.height <= root.height)
        tag("tree-strip-40").performScrollTo(); tap("tree-strip-40")
        assertTrue(desc("post-person").contains("Mira"))
        assertTrue(!exists("post-selector"))
    }

    @Test fun reducedMotionStepsCloserAtOnce() {
        android.provider.Settings.Global.putFloat(rule.activity.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            start()
            tag("tree-letter-1").performClick(); rule.waitForIdle(); rule.mainClock.advanceTimeByFrame(); rule.mainClock.advanceTimeByFrame(); rule.waitForIdle()
            assertTrue(exists("tree-envelope"))
        } finally {
            android.provider.Settings.Global.putFloat(rule.activity.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }
    }

    @Test fun theEnvelopesAreNativeLandscapeEnvelopesAndATapAnywhereOnItOpensIt() {
        // 22.10.: every arrived / opened letter hangs as the native layered envelope, landscape 1.45 : 1 (never the old
        // portrait sprite); travelling letters keep the painted sheet
        start()
        assertTrue(exists("tree-env-1") && exists("tree-env-6") && !exists("tree-env-3"))
        for (id in listOf(1L, 6L)) {
            val n = tag("tree-env-$id").fetchSemanticsNode()
            assertTrue("env $id ${n.size}", kotlin.math.abs(n.size.width.toFloat() / n.size.height - EnvelopeArt.ASPECT) < .03f)
        }
        tap("tree-letter-1")
        // looked at closely: wide on the screen, still landscape, horizontally centred
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val env = tag("tree-env-1").fetchSemanticsNode().boundsInRoot
        assertTrue("$env", env.width >= root.width * .78f && env.width <= root.width && kotlin.math.abs(env.center.x - root.center.x) < 6 * density)
        assertTrue("$env", kotlin.math.abs(env.width / env.height - EnvelopeArt.ASPECT) < .05f)
        // the touch area is the whole envelope; a tap near its lower left corner (far from the seal) opens it
        val zone = tag("tree-envelope").fetchSemanticsNode().boundsInRoot
        assertTrue("$zone ~ $env", kotlin.math.abs(zone.width - env.width) < 3 * density && kotlin.math.abs(zone.left - env.left) < 3 * density)
        tag("tree-envelope").performTouchInput { click(androidx.compose.ui.geometry.Offset(width * .08f, height * .9f)) }
        // the opening on the test clock: the seal lets go, then the letter comes out (each its own phase), then it floats
        val seen = mutableListOf<String>()
        var first: androidx.compose.ui.geometry.Rect? = null
        repeat(160) {
            rule.mainClock.advanceTimeByFrame(); rule.waitForIdle()
            ScenePhase.entries.firstOrNull { exists("tree-scene-${it.name}") }?.let { if (seen.lastOrNull() != it.name) seen += it.name }
            if (first == null && exists("tree-lifted")) first = tag("tree-lifted").fetchSemanticsNode().boundsInRoot
        }
        assertEquals(listOf("WAITING", "UNSEALING", "EMERGING", "READING"), seen.dropWhile { it == "SEALED" }.take(4))
        val paper = tag("tree-lifted").fetchSemanticsNode().boundsInRoot
        assertTrue("grows from the folded letter $first -> $paper", first!!.width < paper.width * .9f)
        // the letter: large, natural paper proportions (height / width ≈ 1.41 unless the screen is shorter), on screen
        assertTrue("$paper", paper.width >= root.width * .85f && paper.left >= 0f && paper.right <= root.right && paper.bottom <= root.bottom + 1)
        assertTrue("$paper", paper.height / paper.width <= EnvelopeArt.PAPER + .02f && paper.height / paper.width > 1.2f)
        assertEquals(listOf("open:1"), calls)
    }

    @Test fun theCameraMovesTheWholeSceneIntoTheEnvelopeAndBackOut() {
        start()
        val rest1 = tag("tree-env-1").fetchSemanticsNode().boundsInRoot
        val rest6 = tag("tree-env-6").fetchSemanticsNode().boundsInRoot
        val rest11 = tag("tree-env-11").fetchSemanticsNode().boundsInRoot
        tag("tree-letter-1").performClick(); rule.waitForIdle()
        repeat(14) { rule.mainClock.advanceTimeByFrame() }; rule.waitForIdle()
        // mid-way: the camera is between the tree and the envelope
        val mid = tag("tree-env-1").fetchSemanticsNode().boundsInRoot
        assertTrue("$rest1 < $mid", mid.width > rest1.width * 1.2f)
        frames()
        val full = tag("tree-env-1").fetchSemanticsNode().boundsInRoot
        assertTrue("$mid < $full", full.width > mid.width * 1.2f)
        // ONE camera: both neighbouring envelopes grow by the same factor.
        val k = full.width / rest1.width
        val full6 = tag("tree-env-6").fetchSemanticsNode().boundsInRoot
        val full11 = tag("tree-env-11").fetchSemanticsNode().boundsInRoot
        assertEquals(k, full6.width / rest6.width, .03f * k)
        assertEquals(k, full11.width / rest11.width, .05f * k)
        // a tap beside the envelope: the camera moves back out (nothing opened)
        rule.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(width * .5f, height * .95f)) }; rule.waitForIdle()
        repeat(14) { rule.mainClock.advanceTimeByFrame() }; rule.waitForIdle()
        val back = tag("tree-env-1").fetchSemanticsNode().boundsInRoot
        assertTrue("shrinks $back", back.width < full.width * .9f && back.width > rest1.width)
        frames()
        assertTrue(exists("tree-scene-REST") && exists("tree-letter-1"))
        assertEquals(rest1.width, tag("tree-env-1").fetchSemanticsNode().boundsInRoot.width, 1f)
        assertTrue(calls.isEmpty())
    }

    /** Every text of the tree's ink / numbers: its laid-out text fits its own box (no cut, no clipped descender). */
    private fun wholeTexts(tag: String): List<androidx.compose.ui.semantics.SemanticsNode> {
        val nodes = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        for (n in nodes) {
            val res = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            n.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult)?.action?.invoke(res)
            for (l in res) assertTrue("${n.config[SemanticsProperties.Text]} cut: w ${l.multiParagraph.maxIntrinsicWidth}/${l.size.width} h ${l.multiParagraph.height}/${n.size.height} overflowW=${l.didOverflowWidth} overflowH=${l.didOverflowHeight} maxW=${l.layoutInput.constraints.maxWidth} ellipsis=${(0 until l.lineCount).map { l.isLineEllipsized(it) }}",
                (0 until l.lineCount).all { l.getLineRight(it) - l.getLineLeft(it) <= l.size.width + 1 } && l.size.width <= l.layoutInput.constraints.maxWidth && l.multiParagraph.height <= n.size.height + 1 && !l.didOverflowHeight && (0 until l.lineCount).none { l.isLineEllipsized(it) })
        }
        return nodes
    }

    @Test fun pixelReview1074NamesStatesCountsAndTheArrowAreWholeOnASmallPhoneWithALargeFont() {
        start()
        // (2) every letter on the trunk shows its name AND its state word (long or short form), both whole
        val ink = wholeTexts("tree-ink")
        val shown = onTrunk()
        assertEquals("name + state on each of ${shown.size} letters", shown.size * 2, ink.size)
        // all ink lines lie on their envelope / sheet: inside the letter's touch area (= the painted thing)
        // (the painted thing: the envelope sprite where one hangs, else the sheet)
        val things = rule.onAllNodes(SemanticsMatcher("e") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("tree-env-") == true }, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot } +
            rule.onAllNodes(SemanticsMatcher("l") { it.config.getOrNull(SceneSlotKey)?.startsWith("letter:") == true }, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
        for (n in ink) { val r = n.boundsInRoot; assertTrue("$r on a letter", things.any { l -> l.inflate(2 * density).contains(r.topLeft) && l.inflate(2 * density).contains(r.bottomRight) }) }
        // pixel review 2: on an envelope the ink lies on its PAPER – the drawn sprite (tree-env = the fitted picture, not
        // its room) above the curled lower corners (the sprite's full-width paper ends at .95 of its height, alpha measured)
        val envs = things.take(rule.onAllNodes(SemanticsMatcher("e") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("tree-env-") == true }, useUnmergedTree = true).fetchSemanticsNodes().size)
        assertTrue("envelopes drawn", envs.isNotEmpty())
        for (n in ink) {
            val r = n.boundsInRoot
            val e = envs.firstOrNull { it.contains(r.center) } ?: continue
            assertTrue("$r on the paper of $e", r.bottom <= e.top + e.height * .95f + 1 * density && r.left >= e.left && r.right <= e.right)
        }
        // names have a readable size: no name drawn more than a third smaller than the largest one
        val names = ink.filterIndexed { k, _ -> k % 2 == 0 }.map { it.size.height }
        assertTrue("$names", names.min() >= names.max() * .66f)
        // (4) counts belong to labelled controls, never detached ink over the furniture.
        assertTrue(wholeTexts("post-status").isNotEmpty())
        tap("post-status")
        for (v in PostViews.View.entries) {
            wholeTexts("tree-window-${v.name}").single()
            val expectedCount = PostViews.counts(letters, opened, null, "")[v] ?: 0
            assertTrue("Named real count for $v", desc("tree-window-${v.name}").contains("${v.label}: $expectedCount"))
            assertTrue("No duplicate wood count for $v", !exists("tree-count-${v.name}"))
        }
        tap("post-selector-close")
        // (1) the time arrow: its painted board is there with its word, a 48 dp target, on screen
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val arrow = tag("tree-time-back").fetchSemanticsNode().boundsInRoot
        assertTrue("$arrow", arrow.width >= 48 * density - 1 && arrow.height >= 48 * density - 1 && arrow.left >= 0f && arrow.right <= root.right)
        val rail = tag("post-time-rail").fetchSemanticsNode().boundsInRoot
        assertTrue("runner touch height $rail", rail.height >= 48 * density - 1)
        assertTrue(wholeTexts("post-time-label").isNotEmpty())
        // (3) the older page: "offen" / "gesendet" whole on the opened envelopes
        tap("tree-time-back")
        assertEquals(10, wholeTexts("tree-ink").size)
    }
}
