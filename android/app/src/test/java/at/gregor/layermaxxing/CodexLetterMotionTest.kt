package at.gregor.layermaxxing

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.rules.ActivityScenarioRule
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** One physical pixel allows native layout rounding, for every edge, not a zoom tolerance. */
internal fun assertRestBounds(
    message: String,
    expected: androidx.compose.ui.geometry.Rect,
    actual: androidx.compose.ui.geometry.Rect,
) {
    assertEquals("$message: left", expected.left, actual.left, 1f)
    assertEquals("$message: top", expected.top, actual.top, 1f)
    assertEquals("$message: right", expected.right, actual.right, 1f)
    assertEquals("$message: bottom", expected.bottom, actual.bottom, 1f)
    assertEquals("$message: width", expected.width, actual.width, 1f)
    assertEquals("$message: height", expected.height, actual.height, 1f)
}

/** Only the server response is synthetic. PostOffice, opener, unread marker, assets and gates are production. */
internal class CodexMotionHarness(
    val rule: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>,
) {
    val opened = mutableStateMapOf<Long, OpenedMessage>()
    val answer = CompletableDeferred<Unit>()
    var calls = 0
    val locked = mutableListOf<Long>()
    var marks = 0
    var compositions = 0
    var disposals = 0
    fun tag(name: String) = rule.onNodeWithTag(name, useUnmergedTree = true)
    fun exists(name: String) = rule.onAllNodesWithTag(name, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    fun phase() = ScenePhase.entries.first { exists("tree-scene-${it.name}") }
    fun sample() = tag("tree-scene-${phase().name}").fetchSemanticsNode().config[LetterMotionKey]
    fun frames(n: Int = 3) {
        repeat(n) { rule.mainClock.advanceTimeByFrame() }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        rule.waitForIdle()
    }
    fun until(target: ScenePhase, limit: Int = 320) {
        repeat(limit) { if (phase() == target) return; frames(1) }
        assertEquals("bounded phase wait", target, phase())
    }
    fun touch(name: String) { tag(name).performTouchInput { click(center) }; rule.waitForIdle() }
    fun flushPointerState() {
        rule.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
    }
    /** Real selector input, including tall rows at 360dp / font 1.5. ScrollTo's repeated
     * semantics action cannot advance this paused clock and can loop without making progress. */
    fun scrollSelectorTo(name: String) {
        val minimum = 48f * rule.activity.resources.displayMetrics.density
        while (true) {
            val node = tag(name).fetchSemanticsNode()
            val visible = node.boundsInRoot
            if (!visible.isEmpty && visible.height >= minOf(minimum, node.size.height.toFloat()) - 1f) return
            val range = tag("post-selector-scroll").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange]
            val before = range.value()
            val viewport = tag("post-selector-scroll").fetchSemanticsNode().boundsInRoot
            val upward = node.positionInRoot.y + node.size.height / 2f >= viewport.center.y
            fun distanceToVisible(n: androidx.compose.ui.semantics.SemanticsNode): Float {
                val needed = minOf(minimum, n.size.height.toFloat())
                val top = n.positionInRoot.y
                return maxOf(0f, viewport.top + needed - (top + n.size.height), top + needed - viewport.bottom)
            }
            val distanceBefore = distanceToVisible(node)
            tag("post-selector-scroll").performTouchInput {
                // Short, slow drags avoid flinging over small choices between tall mail rows.
                if (upward) swipeUp(startY = height * .7f, endY = height * .4f, durationMillis = 600)
                else swipeDown(startY = height * .3f, endY = height * .6f, durationMillis = 600)
            }
            flushPointerState()
            frames(20)
            val after = tag("post-selector-scroll").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
            assertTrue("Selector must scroll toward $name: $before -> $after", if (upward) after > before else after < before)
            val distanceAfter = distanceToVisible(tag(name).fetchSemanticsNode())
            assertTrue("Target must get closer, preventing oscillating scroll loops: $distanceBefore -> $distanceAfter", distanceAfter < distanceBefore)
        }
    }
    fun timePage() = tag("tree-scene-${phase().name}").fetchSemanticsNode().config[PostalTimePageKey]
    fun visibleMailIds() = tag("tree-scene-${phase().name}").fetchSemanticsNode().config[PostalVisibleMailIdsKey]
    fun outside() { rule.onRoot().performTouchInput { click(Offset(width * .5f, height - 4f)) }; rule.waitForIdle() }
    fun back() { rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle() }
    fun start(
        letters: List<ApiClient.Message> = PostPilotFixtures.letters.filter { it.id == 1L || it.id == 2L },
        restore: StateRestorationTester? = null,
        fontScale: Float = 1f,
        level: Int = 0,
    ) {
        rule.mainClock.autoAdvance = false
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            DisposableEffect(Unit) {
                compositions++
                onDispose { disposals++ }
            }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                VillageTheme(true) {
                    val opener = rememberLetterOpener(opened, fetch = { m ->
                        calls++; answer.await()
                        OpenedMessage(LetterSceneFixtures.text(m.id), null, null, null)
                    }, onDone = {}, onError = {})
                    val marker = rememberUnreadMarker(opened, transmit = { marks++ }, onDone = {}, onError = {})
                    PostOffice(PostPilotFixtures.pals, letters, opened, level = level,
                        openRequests = opener.requests, unread = marker,
                        onOpen = { opener.open(it) }, onLocked = { locked += it.id }, onWrite = {}, onClose = {})
                }
            }
        }
        if (restore == null) rule.setContent(content) else restore.setContent(content)
        frames()
        assertEquals(ScenePhase.REST, phase())
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-hdpi")
class CodexLetterMotionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var h: CodexMotionHarness
    private var oldScale = 1f
    private fun scale(value: Float) {
        rule.runOnIdle { Settings.Global.putFloat(rule.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, value) }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        rule.waitForIdle()
    }
    @Before fun prepare() {
        oldScale = Settings.Global.getFloat(rule.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        scale(1f)
        h = CodexMotionHarness(rule)
    }
    @After fun restoreScale() { scale(oldScale) }

    @Test fun profilesAreBoundedMonotoneReversibleAndFlapAlwaysClearsThePaper() {
        assertEquals(LetterMotion.Paper(0f, 0f, 0f), LetterMotion.paper(-1f))
        assertEquals(LetterMotion.Paper(1f, 1f, 1f), LetterMotion.paper(2f))
        var previous = LetterMotion.paper(0f)
        var previousSeal = 0f
        for (i in 0..1000) {
            val t = i / 1000f
            val p = LetterMotion.paper(t)
            for (v in listOf(p.flap, p.rise, p.lift, LetterMotion.seal(t))) assertTrue(v in 0f..1f)
            assertTrue(p.flap >= previous.flap && p.rise >= previous.rise && p.lift >= previous.lift)
            assertTrue(LetterMotion.seal(t) >= previousSeal)
            if (p.rise > 0f) assertTrue("paper cannot cross the closed flap at $t", p.flap > .5f)
            if (p.lift > 0f) assertTrue(p.rise > .9f)
            previous = p; previousSeal = LetterMotion.seal(t)
        }
        val overlap = LetterMotion.paper(.66f)
        assertTrue(overlap.rise in .9f..<1f && overlap.lift in 0f..<1f && overlap.lift > 0f)
        assertEquals(.3f, LetterMotion.seal(.5f), .0001f)
        assertEquals(0f, LetterMotion.seal(-1f), 0f)
        assertEquals(1f, LetterMotion.seal(2f), 0f)
        // The identical sampled path is used in descending order for stowing.
        val path = (0..100).map { LetterMotion.paper(it / 100f) }
        assertEquals(path.reversed(), (100 downTo 0).map { LetterMotion.paper(it / 100f) })
    }

    @Test fun productionClockZoomSealOverlapReadingAndOutsideReturn() {
        h.start()
        val initial = h.tag("tree-env-1").fetchSemanticsNode().boundsInRoot
        h.touch("tree-letter-1"); h.frames(12)
        assertEquals(ScenePhase.APPROACH, h.phase())
        assertTrue(h.sample().camera in 0f..<1f && h.sample().camera > 0f)
        assertTrue(h.tag("tree-env-1").fetchSemanticsNode().boundsInRoot.width > initial.width)
        h.until(ScenePhase.SEALED)
        assertEquals(1f, h.sample().camera, 0f)
        assertEquals(0, h.calls)
        h.touch("tree-envelope"); h.frames(80)
        assertEquals(ScenePhase.WAITING, h.phase())
        assertEquals(0f, h.sample().crack, 0f)
        assertFalse(h.exists("tree-lifted") || h.exists("tree-letter-text"))
        h.answer.complete(Unit); h.until(ScenePhase.UNSEALING)
        h.frames(20)
        assertTrue(h.sample().crack > 0f && h.sample().crack < .3f)
        assertEquals(0f, h.sample().flap, 0f)
        assertFalse(h.exists("tree-letter-text"))
        h.until(ScenePhase.EMERGING)
        val samples = mutableListOf<LetterMotionSample>()
        repeat(95) {
            samples += h.sample(); h.frames(1)
        }
        assertTrue(samples.any { it.flap > 0f && it.rise == 0f })
        assertTrue(samples.any { it.flap < 1f && it.rise > 0f })
        assertTrue(samples.any { it.rise < 1f && it.lift > 0f })
        samples.filter { it.rise > 0f }.forEach { assertTrue(it.flap > .5f) }
        assertEquals(ScenePhase.READING, h.phase())
        assertTrue(h.exists("tree-letter-text"))
        assertEquals(LetterMotionSample(1f, 1f, 1f, 1f, 1f), h.sample())
        h.outside(); h.frames(12)
        assertEquals(ScenePhase.STOWING, h.phase())
        assertTrue(h.sample().lift in 0f..<1f)
        assertEquals(1f, h.sample().flap, 0f)
        h.until(ScenePhase.LEAVING)
        assertEquals(0f, h.sample().rise, 0f); assertEquals(0f, h.sample().flap, 0f)
        h.frames(12); assertTrue(h.sample().camera in 0f..<1f)
        h.until(ScenePhase.REST); h.frames()
        assertEquals(0f, h.sample().camera, 0f)
        assertEquals(initial.width, h.tag("tree-env-1").fetchSemanticsNode().boundsInRoot.width, 1f)
        assertEquals(1, h.calls)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-hdpi")
    fun openingReprioritizesTheTrunkAndReadReentryReturnsToTheCurrentAnchor() {
        h.start(letters = PostPilotFixtures.letters, fontScale = 1.5f)
        val firstEnvelope = h.tag("tree-env-1").fetchSemanticsNode().boundsInRoot
        val nextAnchor = h.tag("tree-letter-12").fetchSemanticsNode().boundsInRoot
        h.touch("tree-letter-1"); h.until(ScenePhase.SEALED)
        assertEquals(0, h.calls)
        h.touch("tree-envelope"); h.frames()
        assertEquals(ScenePhase.WAITING, h.phase())
        assertEquals(1, h.calls)
        h.answer.complete(Unit); h.until(ScenePhase.READING)
        assertTrue(h.exists("tree-letter-text"))
        assertNotNull(h.opened[1L])
        h.back(); h.until(ScenePhase.REST)
        // REST begins the real reorder slide; stale hit targets stay disabled until it settles.
        repeat(30) { if (!h.exists("tree-letter-1")) h.frames(1) }
        h.tag("tree-letter-1").assertExists()
        assertEquals(0f, h.sample().camera, 0f)
        assertFalse(h.exists("tree-letter-text") || h.exists("tree-lifted"))
        // Initial order: 1,6,11,14,3,8,4,7,12. Now: 6,11,14,3,8,4,7,12,1.
        // Compare physical slots, since letter 1 no longer hangs at its original slot.
        assertRestBounds("opened letter at slot 8", nextAnchor,
            h.tag("tree-letter-1").fetchSemanticsNode().boundsInRoot)
        assertRestBounds("unopened letter at slot 0", firstEnvelope,
            h.tag("tree-env-6").fetchSemanticsNode().boundsInRoot)
        val currentEnvelope = h.tag("tree-env-1").fetchSemanticsNode().boundsInRoot
        // The reprioritized last-row envelope can lie behind the resting controls. Use the
        // production readable picker, not an injected touch at an obscured semantics rectangle.
        h.touch("post-mail"); h.frames(); h.scrollSelectorTo("post-select-1"); h.touch("post-select-1")
        h.until(ScenePhase.READING)
        assertTrue(h.exists("tree-letter-text"))
        assertFalse(h.exists("tree-envelope"))
        h.back(); h.until(ScenePhase.REST)
        assertEquals(0f, h.sample().camera, 0f)
        assertFalse(h.exists("tree-letter-text") || h.exists("tree-lifted"))
        assertRestBounds("read letter at current anchor", currentEnvelope,
            h.tag("tree-env-1").fetchSemanticsNode().boundsInRoot)
        assertEquals(1, h.calls)
        assertEquals(0, h.marks)
        assertEquals(1, h.compositions)
        assertEquals(0, h.disposals)
    }

    @Test fun delayedServerRapidTapsBackAndReentryNeverDuplicateOrAutoForward() {
        h.start(); h.touch("tree-letter-1"); h.until(ScenePhase.SEALED)
        repeat(3) { h.tag("tree-envelope").performClick() }; h.frames()
        assertEquals(1, h.calls); assertEquals(ScenePhase.WAITING, h.phase())
        h.back(); h.until(ScenePhase.REST)
        h.touch("tree-letter-1"); h.until(ScenePhase.SEALED)
        repeat(3) { h.tag("tree-envelope").performClick() }; h.frames()
        assertEquals(1, h.calls)
        h.back(); h.until(ScenePhase.REST)
        h.answer.complete(Unit); h.frames(170)
        assertEquals(ScenePhase.REST, h.phase())
        assertFalse(h.exists("tree-letter-text") || h.exists("tree-lifted"))
        h.touch("tree-letter-1"); h.until(ScenePhase.EMERGING)
        assertEquals(0f, h.sample().crack, 0f)
        h.back(); h.until(ScenePhase.REST)
        assertEquals(1, h.calls)
    }

    @Test fun readDirectUnreadResealsAndNeedsExtraTap() {
        h.opened[2] = OpenedMessage(LetterSceneFixtures.text(2), null, null, null)
        h.start(); h.touch("tree-letter-2"); h.until(ScenePhase.EMERGING)
        assertEquals(0f, h.sample().crack, 0f)
        h.until(ScenePhase.READING); assertEquals(0, h.calls)
        h.tag("tree-page").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 9000f) }; h.frames(30)
        h.tag("tree-letter-unread").performClick(); h.until(ScenePhase.RESEALING)
        assertEquals(0f, h.sample().rise, 0f)
        h.until(ScenePhase.SEALED)
        assertEquals(1, h.marks); assertEquals(0, h.calls); assertNull(h.opened[2])
        h.frames(20); assertEquals(ScenePhase.SEALED, h.phase())
        h.answer.complete(Unit); h.touch("tree-envelope"); h.until(ScenePhase.UNSEALING)
        h.until(ScenePhase.READING); assertEquals(1, h.calls)
    }

    @Test fun reduceMotionInitiallyAndDuringTransitionSettlesWithoutDelayedWork() {
        scale(0f); h.start()
        h.touch("tree-letter-1"); h.frames(3)
        assertEquals(ScenePhase.SEALED, h.phase())
        h.touch("tree-envelope"); h.frames(3)
        assertEquals(ScenePhase.WAITING, h.phase()); assertEquals(0f, h.sample().crack, 0f)
        h.answer.complete(Unit); h.frames(6)
        assertEquals(ScenePhase.READING, h.phase())
        h.outside(); h.frames(6); assertEquals(ScenePhase.REST, h.phase())
        scale(1f); h.touch("tree-letter-1"); h.frames(12)
        assertEquals(ScenePhase.APPROACH, h.phase())
        scale(0f); h.frames(6)
        assertEquals(ScenePhase.READING, h.phase())
        h.back(); h.frames(6); assertEquals(ScenePhase.REST, h.phase())
        h.frames(180); assertEquals(ScenePhase.REST, h.phase()); assertEquals(1, h.calls)
    }

    @Test fun reduceMotionDuringSealAndStowingFinishesWithoutWaitingForOldTweens() {
        h.answer.complete(Unit); h.start()
        h.touch("tree-letter-1"); h.until(ScenePhase.SEALED)
        h.touch("tree-envelope"); h.until(ScenePhase.UNSEALING); h.frames(12)
        scale(0f); h.frames(6)
        assertEquals(ScenePhase.READING, h.phase())
        scale(1f); h.outside(); h.frames(12)
        assertEquals(ScenePhase.STOWING, h.phase())
        scale(0f); h.frames(6)
        assertEquals(ScenePhase.REST, h.phase())
        h.frames(180); assertEquals(ScenePhase.REST, h.phase()); assertEquals(1, h.calls)
    }

    @Test fun lifecyclePauseResumeDuringSealThenBackCompletesOneRoundTrip() {
        h.answer.complete(Unit); h.start()
        h.touch("tree-letter-1"); h.until(ScenePhase.SEALED)
        h.touch("tree-envelope"); h.until(ScenePhase.UNSEALING); h.frames(12)
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        h.back(); h.until(ScenePhase.REST)
        h.frames(170)
        assertEquals(ScenePhase.REST, h.phase())
        assertEquals(1, h.calls)
        assertFalse(h.exists("tree-letter-text") || h.exists("tree-lifted"))
    }

    @Test fun restorationDuringExtractionSettlesAndBackCancelsPendingPeerNavigation() {
        val restore = clockControlledRestorationTester(rule)
        h.opened[2] = OpenedMessage(LetterSceneFixtures.text(2), null, null, null)
        h.start(restore = restore); h.touch("tree-letter-2"); h.until(ScenePhase.EMERGING); h.frames(35)
        assertEquals(ScenePhase.EMERGING, h.phase())
        assertTrue("save during extraction", h.sample().rise > 0f && h.sample().rise < 1f)
        val compositions = h.compositions
        val disposals = h.disposals
        restore.emulateSavedInstanceStateRestore(); h.frames(6)
        assertEquals("restore must dispose the old composition", disposals + 1, h.disposals)
        assertEquals("restore must create a new composition", compositions + 1, h.compositions)
        assertEquals(ScenePhase.READING, h.phase())
        assertEquals(1f, h.sample().camera, 0f)
        assertEquals(1f, h.sample().lift, 0f)
        assertTrue(h.exists("tree-letter-text"))
        h.tag("tree-letter-next").performClick(); h.frames(6)
        assertEquals(ScenePhase.STOWING, h.phase())
        h.back(); h.until(ScenePhase.REST); h.frames(170)
        assertEquals(ScenePhase.REST, h.phase()); assertEquals(0, h.calls)
    }
}
