package at.gregor.layermaxxing

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** All inherited tests run on all three stages: actual clock motion, rest anchors, peer gates, unread and removal. */
abstract class PostUpperMotionContract {
    protected abstract val level: Int
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun normal(block: (CodexMotionHarness) -> Unit) {
        val resolver = rule.activity.contentResolver
        val before = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        try { block(CodexMotionHarness(rule)) } finally { Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, before) }
    }
    @Test fun measuredObjectAnchorRoundTripAndReadLetterDirectReturn() = normal { h ->
        h.start(letters = PostPilotFixtures.letters.filter { it.id == 1L }, level = level)
        val rest = h.tag("tree-env-1").fetchSemanticsNode().boundsInRoot
        h.touch("tree-letter-1"); h.frames(12)
        assertEquals(ScenePhase.APPROACH, h.phase())
        assertTrue(h.sample().camera > 0f && h.sample().camera < 1f)
        assertTrue(h.tag("tree-env-1").fetchSemanticsNode().boundsInRoot.width > rest.width)
        h.until(ScenePhase.SEALED)
        assertEquals(0, h.calls)
        repeat(3) { h.tag("tree-envelope").performClick() }; h.frames()
        assertEquals(1, h.calls); assertEquals(ScenePhase.WAITING, h.phase())
        assertFalse(h.exists("tree-letter-text"))
        h.answer.complete(Unit); h.until(ScenePhase.READING)
        assertEquals(1f, h.sample().lift, 0f)
        h.back(); h.until(ScenePhase.STOWING)
        h.until(ScenePhase.LEAVING)
        assertEquals(0f, h.sample().flap, 0f); assertEquals(0f, h.sample().rise, 0f)
        h.until(ScenePhase.REST)
        assertRestBounds("POST$level returns to its own original anchor", rest, h.tag("tree-env-1").fetchSemanticsNode().boundsInRoot)
        assertEquals(0f, h.sample().camera, 0f)
        h.touch("tree-letter-1"); h.until(ScenePhase.READING)
        assertFalse(h.exists("tree-envelope")); assertEquals(1, h.calls)
        h.back(); h.until(ScenePhase.REST)
        assertRestBounds("read letter returns to its own object", rest, h.tag("tree-env-1").fetchSemanticsNode().boundsInRoot)
        assertEquals(1, h.compositions); assertEquals(0, h.disposals)
    }
    @Test fun peerNavigationKeepsGateAndUnreadResealsPersonally() = normal { h ->
        h.start(letters = PostPilotFixtures.letters.filter { it.peerId == 40L }, level = level)
        h.touch("tree-letter-1"); h.until(ScenePhase.SEALED)
        h.touch("tree-envelope"); h.answer.complete(Unit); h.until(ScenePhase.READING)
        h.tag("tree-page").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 9000f) }; h.frames(35)
        h.tag("tree-letter-unread").performClick(); h.until(ScenePhase.RESEALING)
        assertEquals(1, h.marks)
        h.until(ScenePhase.SEALED)
        assertFalse(h.exists("tree-letter-text"))
        h.touch("tree-envelope"); h.until(ScenePhase.READING)
        assertEquals(1, h.calls)
        h.tag("tree-letter-prev").performClick(); h.frames(1); h.until(ScenePhase.READING)
        // The newest same-peer fixture is 1; earlier leads to the already-read 2.
        // Another earlier letter (3) is locked: the footer must retain that gate.
        assertTrue(h.exists("tree-letter-text")); assertEquals(2, h.calls)
        h.tag("tree-letter-prev").performClick(); h.frames(5)
        assertEquals(ScenePhase.READING, h.phase()); assertEquals(2, h.calls)
        assertTrue(h.exists("post-arrival")); assertTrue(h.locked.isEmpty())
        h.tag("post-arrival-action").performScrollTo(); h.touch("post-arrival-action"); h.frames()
        assertEquals(listOf(3L), h.locked)
        h.back(); h.until(ScenePhase.REST)
    }
    @Test fun removingCurrentLetterDropsVisibleContentAndEmptyStateHasNoPhantomLetters() = normal { h ->
        val letters = mutableStateListOf(PostPilotFixtures.letters.first())
        h.start(letters = letters, level = level)
        h.touch("tree-letter-1"); h.until(ScenePhase.SEALED)
        h.touch("tree-envelope"); h.answer.complete(Unit); h.until(ScenePhase.READING)
        rule.runOnIdle { letters.clear(); h.opened.clear() }; h.frames(5)
        assertEquals(ScenePhase.REST, h.phase())
        assertFalse(h.exists("tree-letter-text") || h.exists("tree-lifted") || h.exists("tree-env-1"))
        assertTrue(h.exists("post-empty")); assertEquals(0f, h.sample().camera, 0f)
        h.tag("tree-padlock").performClick(); h.frames()
        assertEquals(1, h.calls)
    }
    @Test fun registrationsAreDifferentBoundedAndContainNoOverlappingLetterSlots() {
        val registrations = (1..3).map(PostRegistration::of)
        assertEquals(3, registrations.map { it.sheets }.distinct().size)
        assertEquals(3, registrations.map { it.holderTravel }.distinct().size)
        val a = PostRegistration.of(level)
        for (b in a.sheets + a.people + a.windows + listOf(a.pen, a.padlock)) {
            assertTrue(b.l >= 0f && b.r <= 1f && b.t >= 0f && b.b <= 1f && b.l < b.r && b.t < b.b)
        }
        for (i in a.sheets.indices) for (j in i + 1 until a.sheets.size) {
            val b = a.sheets[i]; val c = a.sheets[j]
            assertFalse(b.l < c.r && b.r > c.l && b.t < c.b && b.b > c.t)
        }
    }
}
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-hdpi")
class Post1MotionTest : PostUpperMotionContract() { override val level = 1 }
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-hdpi")
class Post2MotionTest : PostUpperMotionContract() { override val level = 2 }
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-hdpi")
class Post3MotionTest : PostUpperMotionContract() { override val level = 3 }
