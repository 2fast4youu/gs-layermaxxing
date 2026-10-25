package at.gregor.layermaxxing

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

class PostalArrivalModelTest {
    private val m = PostPilotFixtures.letters.first()
    @Test fun futureAndElapsedTimesDoNotClaimUnlock() {
        val timed = m.copy(unlocked = false, mode = "timed", releaseAt = 2000000000)
        assertTrue(PostViews.arrival(timed, 1999996400).contains("Noch"))
        assertTrue(PostViews.arrival(timed, 2000000001).contains("noch nicht bestätigt"))
        assertTrue(PostViews.arrival(timed, 1999996400).contains("Freigabezeit:"))
    }
    @Test fun manualUnknownAndRandomAreTruthful() {
        assertTrue(PostViews.arrival(m.copy(unlocked = false, mode = "manual", releaseAt = null)).contains("von Hand"))
        assertTrue(PostViews.arrival(m.copy(unlocked = false, mode = "timed", releaseAt = null)).contains("unbekannt"))
        assertTrue(PostViews.arrival(m.copy(unlocked = false, mode = "random", randomFrom = 1000, randomTo = 2000)).contains("Zeitfenster:"))
    }
    @Test fun trayGroupsUseEveryRealLetterIncludingSelectedAndZeroOne() {
        for (n in 0..9) {
            val mail = (1..n).map { m.copy(id = it.toLong()) }
            val faces = PostalMailGroups.faces(mail, 3)
            assertEquals(n, faces.size)
            assertEquals(mail.map { it.id }, faces.map { it.id })
            mail.forEach { selected ->
                val selection = PostalMailGroups.faces(mail, 3, selected.id)
                assertTrue(selection.any { it.id == selected.id })
                assertTrue(selection.all { it in mail })
            }
        }
    }
}

/** Input goes through actual production composables with a paused clock, never assigned scene state. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-hdpi")
open class PostalPhoneFeedbackTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    protected open val level = 0
    protected open val fontScale = 1f
    private fun blankTap() {
        rule.onRoot().performTouchInput { click(Offset(width - 3f, 40f)) }
    }
    @Test fun actualBackgroundTapFinishesApproachAndReturn() {
        val h = CodexMotionHarness(rule); h.start(level = level, fontScale = fontScale)
        h.touch("tree-letter-1"); h.frames(5)
        assertEquals(ScenePhase.APPROACH, h.phase())
        blankTap(); h.frames()
        assertEquals(ScenePhase.SEALED, h.phase()); assertEquals(0, h.calls)
        h.back(); h.frames(4); blankTap(); h.frames()
        assertEquals(ScenePhase.REST, h.phase()); assertEquals(0, h.calls)
    }
    @Test fun waitingCannotBeSkippedAndLateAnswerDoesNotReenter() {
        val h = CodexMotionHarness(rule); h.start(level = level, fontScale = fontScale)
        h.touch("tree-letter-1"); h.until(ScenePhase.SEALED)
        h.touch("tree-envelope"); h.frames()
        assertEquals(ScenePhase.WAITING, h.phase()); assertEquals(1, h.calls)
        h.touch("tree-envelope"); h.frames()
        assertEquals(ScenePhase.WAITING, h.phase()); assertEquals(1, h.calls)
        h.back(); h.frames(3); blankTap(); h.frames()
        assertEquals(ScenePhase.REST, h.phase())
        rule.runOnIdle { h.answer.complete(Unit) }; h.frames(160)
        assertEquals(ScenePhase.REST, h.phase()); assertEquals(1, h.calls)
    }
    @Test fun skipUnsealAndEmergeThenPaperTapStaysAndOutsideReturns() {
        val h = CodexMotionHarness(rule); h.start(level = level, fontScale = fontScale)
        h.touch("tree-letter-1"); h.until(ScenePhase.SEALED)
        h.touch("tree-envelope"); h.frames()
        rule.runOnIdle { h.answer.complete(Unit) }; h.frames()
        h.until(ScenePhase.UNSEALING); blankTap(); h.frames()
        assertEquals(ScenePhase.READING, h.phase()); assertEquals(1, h.calls)
        h.tag("tree-lifted").performTouchInput { click(Offset(width - 6f, 10f)) }; h.frames()
        assertEquals(ScenePhase.READING, h.phase())
        h.outside(); h.until(ScenePhase.REST)
        assertTrue(h.exists("post-filter")); assertFalse(h.exists("tree-lifted"))
    }
    @Test fun menusDismissOnBackdropAndKeepRealNamedSelection() {
        val h = CodexMotionHarness(rule); h.start(letters = PostPilotFixtures.letters, level = level, fontScale = fontScale)
        h.touch("post-person"); h.frames()
        h.tag("post-selector-close").assertIsDisplayed()
        val slip = h.tag("post-selector").fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Picker fits phone: $slip in $root", root.contains(slip.topLeft) && root.contains(slip.bottomRight))
        h.touch("post-selector-close"); h.frames()
        assertFalse(h.exists("post-selector"))
        h.touch("post-person"); h.frames()
        h.tag("post-person-search").performTextInput("Mira"); h.frames()
        h.scrollSelectorTo("tree-strip-40"); h.touch("tree-strip-40"); h.frames()
        h.touch("post-filter")
        h.tag("post-person").assertContentDescriptionContains("Mira", substring = true)
        h.touch("post-status"); h.frames()
        h.scrollSelectorTo("tree-window-TRAVEL"); h.touch("tree-window-TRAVEL"); h.frames()
        h.touch("post-filter")
        h.tag("post-status").assertContentDescriptionContains("Unterwegs", substring = true)
        h.touch("post-status"); h.frames()
        for (v in PostViews.View.entries) h.tag("tree-count-${v.name}").assertDoesNotExist()
        blankTap(); h.frames()
        assertFalse(h.exists("post-selector")); assertEquals(0, h.calls)
    }
    @Test fun lockedSelectionShowsReleaseDetailsWithoutOpening() {
        val locked = PostPilotFixtures.letters.first().copy(unlocked = false, mode = "timed", releaseAt = 2000000000)
        val h = CodexMotionHarness(rule); h.start(letters = listOf(locked), level = level, fontScale = fontScale)
        h.touch("tree-letter-${locked.id}"); h.frames()
        h.tag("post-arrival").assertTextContains("Freigabezeit:", substring = true)
        assertEquals(0, h.calls); assertEquals(ScenePhase.REST, h.phase())
        h.scrollSelectorTo("post-arrival-action"); h.touch("post-arrival-action"); h.frames()
        assertEquals(listOf(locked.id), h.locked); assertEquals(0, h.calls)
    }
    @Test fun singleMailHasNoInventedHistory() {
        val h = CodexMotionHarness(rule); h.start(letters = PostPilotFixtures.letters.take(1), level = level, fontScale = fontScale)
        h.tag("post-time-rail").assertIsNotEnabled()
        h.touch("post-mail"); h.frames()
        h.tag("post-select-1").assertExists()
        assertEquals(1, rule.onAllNodes(SemanticsMatcher("selection") { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith("post-select-") == true }, useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, h.calls)
    }
    @Test fun extractionAndStowingAcceptRealSkipTaps() {
        val h = CodexMotionHarness(rule)
        h.opened[2] = OpenedMessage(LetterSceneFixtures.text(2), null, null, null)
        h.start(level = level, fontScale = fontScale)
        h.touch("post-mail"); h.frames(); h.scrollSelectorTo("post-select-2"); h.touch("post-select-2")
        h.until(ScenePhase.EMERGING); h.frames(10)
        blankTap(); h.frames()
        assertEquals(ScenePhase.READING, h.phase()); assertEquals(0, h.calls)
        h.outside(); h.frames(5)
        assertEquals(ScenePhase.STOWING, h.phase())
        blankTap(); h.frames()
        assertEquals(ScenePhase.REST, h.phase()); assertEquals(0, h.calls)
    }
    @Test fun skipResealingKeepsTheConfirmedUnreadSeal() {
        val h = CodexMotionHarness(rule)
        h.opened[2] = OpenedMessage(LetterSceneFixtures.text(2), null, null, null)
        h.start(level = level, fontScale = fontScale)
        h.touch("post-mail"); h.frames(); h.scrollSelectorTo("post-select-2"); h.touch("post-select-2")
        h.until(ScenePhase.READING)
        // Real drags, with observable progress and no unbounded performScrollTo loop under
        // the paused clock. Long letters can require several gestures before the footer appears.
        while (true) {
            val unread = h.tag("tree-letter-unread").fetchSemanticsNode()
            val needed = minOf(48f * rule.activity.resources.displayMetrics.density, unread.size.height.toFloat())
            if (!unread.boundsInRoot.isEmpty && unread.boundsInRoot.height >= needed - 1f) break
            val range = h.tag("tree-page").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange]
            val before = range.value()
            h.tag("tree-page").performTouchInput {
                down(Offset(width * .5f, height * .78f))
                moveTo(Offset(width * .5f, height * .20f), 600)
                // Stop before lifting: the following physical tap is not a fling-cancel gesture.
                advanceEventTime(150)
                up()
            }
            rule.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
            h.frames(20)
            val after = h.tag("tree-page").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
            assertTrue("Paper must make real scroll progress toward unread action: $before -> $after", after > before)
        }
        h.tag("tree-letter-unread").assertIsDisplayed()
        println("Unread before physical tap: phase=${h.phase()} marks=${h.marks} rect=${h.tag("tree-letter-unread").fetchSemanticsNode().boundsInRoot}")
        h.touch("tree-letter-unread")
        println("Unread after physical tap: phase=${h.phase()} marks=${h.marks}")
        h.until(ScenePhase.RESEALING); h.frames(5); blankTap(); h.frames()
        assertEquals(ScenePhase.SEALED, h.phase()); assertEquals(1, h.marks); assertEquals(0, h.calls)
    }
    @Test fun backCancelsQueuedPeerBeforeSkippingTheReturn() {
        val h = CodexMotionHarness(rule)
        h.opened[2] = OpenedMessage(LetterSceneFixtures.text(2), null, null, null)
        h.start(level = level, fontScale = fontScale)
        h.touch("post-mail"); h.frames(); h.scrollSelectorTo("post-select-2"); h.touch("post-select-2")
        h.until(ScenePhase.READING); h.touch("tree-letter-next"); h.frames(5)
        assertEquals(ScenePhase.STOWING, h.phase())
        h.back(); blankTap(); h.frames()
        assertEquals(ScenePhase.REST, h.phase()); assertEquals(0, h.calls)
        h.frames(100); assertEquals(ScenePhase.REST, h.phase())
    }
    @Test fun realTimeDragSlidesMailAndRemovesStaleTapTargets() {
        val h = CodexMotionHarness(rule); h.start(letters = PostPilotFixtures.letters, level = level, fontScale = fontScale)
        // Match the physical sprite travel used by PostAllStagesProductionTest, not the
        // generic swipe inset (which can stop short of the present-day knob position).
        fun fractionX(fraction: Float, width: Int): Float = if (level == 0)
            width * (70.5f + 120f * fraction) / 261f else width * (28f + 554f * fraction) / 610f
        fun dragKnob(fraction: Float) {
            val rail = h.tag("post-time-rail").fetchSemanticsNode().boundsInRoot
            val knob = h.tag("post-time-knob").fetchSemanticsNode().boundsInRoot
            assertTrue("Physical knob must be on the rail: $knob in $rail", rail.contains(knob.center))
            val start = knob.center - rail.topLeft
            h.tag("post-time-rail").performTouchInput {
                down(start)
                moveTo(Offset(fractionX(fraction, width), start.y), 350)
                up()
            }
        }
        fun page() = h.tag("tree-scene-REST").fetchSemanticsNode().config[PostalTimePageKey]
        val capacity = listOf(9, 4, 6, 6)[level]
        val lastPage = (PostPilotFixtures.letters.size - 1) / capacity
        assertEquals(0, page())
        assertTrue(h.exists("tree-letter-1"))
        dragKnob(0f)
        rule.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        h.frames(3)
        assertFalse(h.exists("tree-letter-1"))
        val envelopes = rule.onAllNodes(SemanticsMatcher("mail motion") { it.config.getOrNull(PostalPageSlideKey) != null }, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(envelopes.isNotEmpty())
        assertTrue("a real mail slide, not a number-only change", envelopes.any { it.config[PostalPageSlideKey] > 0f })
        assertEquals(0, h.calls)
        h.frames(20)
        assertEquals("Left knob endpoint must reach oldest page", lastPage, page())
        val settled = rule.onAllNodes(SemanticsMatcher("mail motion") { it.config.getOrNull(PostalPageSlideKey) != null }, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(settled.all { it.config[PostalPageSlideKey] == 0f })
        dragKnob(1f)
        rule.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        h.frames(20)
        assertEquals("Right knob endpoint must return to first page", 0, page())
        assertTrue(h.exists("tree-letter-1")); assertEquals(0, h.calls)
    }
    @Test fun emptyMailHasHonestDisabledTime() {
        val h = CodexMotionHarness(rule); h.start(letters = emptyList(), level = level, fontScale = fontScale)
        h.tag("post-time-rail").assertIsNotEnabled()
        h.touch("post-mail"); h.frames()
        rule.onNodeWithText("Keine Briefe in dieser Auswahl").assertExists()
        assertEquals(0, h.calls)
    }
}
class PostalPhoneFeedbackPost1Test : PostalPhoneFeedbackTest() { override val level = 1 }
class PostalPhoneFeedbackPost2Test : PostalPhoneFeedbackTest() { override val level = 2 }
class PostalPhoneFeedbackPost3Test : PostalPhoneFeedbackTest() {
    override val level = 3
    @Test fun allNineRealLettersCanBeSelectedFromTheirOwnSlotAcrossPages() {
        val letters = (1..9).map { index -> PostPilotFixtures.letters.first().copy(id = index.toLong(), readAt = null, unlocked = true) }
        val h = CodexMotionHarness(rule); h.start(letters = letters, level = 3)
        for (m in letters) {
            h.touch("post-mail"); h.frames()
            h.scrollSelectorTo("post-select-${m.id}"); h.touch("post-select-${m.id}"); h.until(ScenePhase.SEALED)
            assertTrue(h.exists("tree-env-${m.id}")); assertEquals(0, h.calls)
            val sourceSlot = TreeOrder.order(letters, emptyMap<Long, OpenedMessage>()).indexOfFirst { it.id == m.id } % PostRegistration.of(3).sheets.size
            val source = h.tag("tree-env-${m.id}").fetchSemanticsNode().config[StorageFrameKey]
            assertEquals("Selected real member must rise from its own source tray", PhysicalPostalStorage.slots(3)[sourceSlot], source.slot)
            h.back(); h.until(ScenePhase.REST); h.frames(20)
        }
    }
}

@Config(sdk = [34], qualifiers = "w360dp-h640dp-hdpi")
class PostalPhoneFeedbackSmall0Test : PostalPhoneFeedbackTest() {
    override val level = 0
    override val fontScale = 1.5f
    @Test fun restingMailInkIsNotHiddenBehindControls() {
        val h = CodexMotionHarness(rule)
        h.start(letters = PostPilotFixtures.letters, level = level, fontScale = fontScale)
        val controls = h.tag("post-filter").fetchSemanticsNode().boundsInRoot
        val ink = rule.onAllNodesWithTag("tree-ink", useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(ink.isNotEmpty())
        ink.forEach { node ->
            val bounds = node.boundsInRoot
            assertTrue("Mail ink $bounds must remain below rope controls $controls", bounds.top >= controls.bottom - 1f)
        }
    }
}
@Config(sdk = [34], qualifiers = "w360dp-h640dp-hdpi")
class PostalPhoneFeedbackSmall1Test : PostalPhoneFeedbackTest() { override val level = 1; override val fontScale = 1.5f }
@Config(sdk = [34], qualifiers = "w360dp-h640dp-hdpi")
class PostalPhoneFeedbackSmall2Test : PostalPhoneFeedbackTest() { override val level = 2; override val fontScale = 1.5f }
@Config(sdk = [34], qualifiers = "w360dp-h640dp-hdpi")
class PostalPhoneFeedbackSmall3Test : PostalPhoneFeedbackTest() { override val level = 3; override val fontScale = 1.5f }
