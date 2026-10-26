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
        // Upper-stage envelopes clear their wooden lip before camera zoom begins.
        // Raw approach time is not the delayed physical-camera progress.
        val early = h.tag("tree-env-1").fetchSemanticsNode()
        val storage = early.config[StorageFrameKey]
        assertTrue("the envelope must lift out of its registered cavity", storage.dy < 0f)
        assertEquals(0f, storage.turn, 0f)
        assertEquals(0f, PostalStorageMotion.camera(h.sample().camera), 0f)
        assertEquals("no early zoom through the wooden lip", rest.width, early.boundsInRoot.width, 1f)
        assertEquals(0, h.calls)
        h.until(ScenePhase.SEALED)
        assertEquals(1f, h.sample().camera, 0f)
        assertTrue("the camera widens the envelope after clearance",
            h.tag("tree-env-1").fetchSemanticsNode().boundsInRoot.width > rest.width)
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
        // Exercise an incoming timed gate; outgoing letters stay out of the inbox.
        val receivedPeer = PostPilotFixtures.letters.filter { it.peerId == 40L }
            .map { if (it.id == 3L) it.copy(incoming = true) else it }
        h.start(letters = receivedPeer, level = level)
        h.touch("tree-letter-1"); h.until(ScenePhase.SEALED)
        h.touch("tree-envelope"); h.answer.complete(Unit); h.until(ScenePhase.READING)
        h.tag("tree-page").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 9000f) }; h.frames(35)
        h.tag("tree-letter-unread").performClick(); h.until(ScenePhase.RESEALING)
        assertEquals(1, h.marks)
        h.until(ScenePhase.SEALED)
        assertFalse(h.exists("tree-letter-text"))
        h.touch("tree-envelope"); h.until(ScenePhase.READING)
        // Marking unread removes cached plaintext: reopening must fetch 1 again.
        assertEquals(2, h.calls); assertEquals(listOf(1L, 1L), h.fetched)
        h.tag("tree-letter-prev").performClick(); h.frames(1); h.until(ScenePhase.READING)
        // The newest same-peer fixture is 1; earlier leads to the already-read 2,
        // whose content still needs its own authenticated fetch.
        // Another earlier letter (3) is locked: the footer must retain that gate.
        assertTrue(h.exists("tree-letter-text")); assertEquals(3, h.calls)
        assertEquals(listOf(1L, 1L, 2L), h.fetched)
        h.tag("tree-letter-prev").performClick(); h.frames(5)
        assertEquals(ScenePhase.READING, h.phase()); assertEquals(3, h.calls)
        assertEquals(listOf(1L, 1L, 2L), h.fetched)
        assertTrue(h.exists("post-arrival")); assertTrue(h.locked.isEmpty())
        NativePostTestDriver(rule).scrollTo("post-arrival-action")
        h.touch("post-arrival-action"); h.frames()
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
        // Original furniture stays registered; distinctness belongs to the traced
        // mail faces and their lift paths, not artificial movement of the wood.
        assertEquals(listOf(0f, 0f, 0f), registrations.map { it.holderTravel })
        assertEquals(3, (1..3).map { stage -> PhysicalPostalStorage.slots(stage).map { it.lift } }.distinct().size)
        val a = PostRegistration.of(level)
        for (b in a.sheets + a.people + a.windows + listOf(a.pen, a.padlock)) {
            assertTrue(b.l >= 0f && b.r <= 1f && b.t >= 0f && b.b <= 1f && b.l < b.r && b.t < b.b)
        }
        fun overlaps(b: LetterTree.Box, c: LetterTree.Box) =
            b.l < c.r && b.r > c.l && b.t < c.b && b.b > c.t
        for (i in a.sheets.indices) for (j in i + 1 until a.sheets.size) {
            assertFalse("POST$level physical faces $i/$j must be disjoint", overlaps(a.sheets[i], a.sheets[j]))
        }
        // Actual click regions must remain disjoint even where the source faces
        // overlap. Cover every occupancy, not only one fully populated page.
        for (occupied in 1..a.sheets.size) {
            val hits = (0 until occupied).map { PhysicalPostalStorage.hitBox(level, it, occupied) }
            for (i in hits.indices) {
                val b = hits[i]
                assertTrue(b.l >= 0f && b.r <= 1f && b.t >= 0f && b.b <= 1f && b.l < b.r && b.t < b.b)
                for (fx in listOf(.05f, .5f, .95f)) for (fy in listOf(.05f, .5f, .95f)) {
                    val point = androidx.compose.ui.geometry.Offset((b.l + (b.r - b.l) * fx) * 1024f,
                        (b.t + (b.b - b.t) * fy) * 1536f)
                    assertTrue("POST$level occupied=$occupied slot=$i must hit exposed mail",
                        PhysicalPostalStorage.visible(level, i, occupied, point))
                    for (j in 0 until occupied) if (j != i) {
                        assertFalse("POST$level slot=$i must never choose neighbour=$j",
                            PhysicalPostalStorage.visible(level, j, occupied, point))
                    }
                }
                for (j in i + 1 until hits.size) assertFalse("POST$level click regions $i/$j overlap", overlaps(b, hits[j]))
            }
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
