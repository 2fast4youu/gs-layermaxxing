package at.gregor.layermaxxing

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

class PostalStorageProfileTest {
    @Test fun treePoseIsExactlyUnchanged() {
        for (i in 0..100) assertEquals(StoragePose(), PostalStorageMotion.pose(0, i / 100f))
    }
    @Test fun everyStoragePoseIsBoundedReversibleAndFacesReaderAtTheEnd() {
        for (level in 1..3) {
            var previous = PostalStorageMotion.pose(level, 0f)
            assertTrue(previous.pitch < 0f)
            for (i in 0..100) {
                val p = i / 100f; val pose = PostalStorageMotion.pose(level, p)
                assertTrue(pose.pitch.isFinite() && pose.yaw.isFinite() && pose.roll.isFinite())
                assertTrue(pose.pitch >= previous.pitch - .0001f)
                assertTrue(kotlin.math.abs(pose.yaw) <= 9f && kotlin.math.abs(pose.roll) <= 4.001f)
                assertEquals(pose, PostalStorageMotion.pose(level, p))
                previous = pose
            }
            val end = PostalStorageMotion.pose(level, 1f)
            assertEquals(0f, end.pitch, .0001f); assertEquals(0f, end.yaw, .0001f); assertEquals(0f, end.roll, .0001f)
            assertEquals(PostalStorageMotion.pose(level, 0f), PostalStorageMotion.pose(level, -1f))
            assertEquals(PostalStorageMotion.pose(level, 1f), PostalStorageMotion.pose(level, 2f))
        }
    }
    @Test fun lipClearsBeforeRotationStarts() {
        for (level in 1..3) assertEquals(PostalStorageMotion.pose(level, 0f).pitch, PostalStorageMotion.pose(level, .17f).pitch, 0f)
    }
    @Test fun timelineMapsEveryActualPageInBothDirectionsAndHandlesEmptySinglePages() {
        for (pages in 1..25) for (page in 0 until pages) {
            assertEquals(page, PostalStorageMotion.pageAt(PostalStorageMotion.fractionAt(page, pages), pages))
        }
        assertEquals(0, PostalStorageMotion.pageAt(1f, 8))
        assertEquals(7, PostalStorageMotion.pageAt(0f, 8))
        assertEquals(0, PostalStorageMotion.pageAt(.4f, 0)); assertEquals(1f, PostalStorageMotion.fractionAt(0, 0), 0f)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsNonFiniteMotion() { PostalStorageMotion.pose(1, Float.NaN) }
    @Test(expected = IllegalArgumentException::class) fun rejectsNonFiniteTimeline() { PostalStorageMotion.pageAt(Float.NaN, 3) }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-hdpi")
class PostalStorageInteractionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun rotation(level: Int) {
        val h = CodexMotionHarness(rule); h.start(level = level)
        fun pose() = h.tag("tree-env-1").fetchSemanticsNode().config[StoragePoseKey]
        val rest = pose(); assertTrue(rest.pitch < 0f)
        h.touch("tree-letter-1"); h.frames(30)
        val mid = pose(); assertTrue(mid.pitch >= rest.pitch && mid.pitch <= 0f)
        h.until(ScenePhase.SEALED); assertEquals(0f, pose().pitch, .0001f)
        assertEquals("rotation must not fetch a sealed letter", 0, h.calls)
        h.back(); h.until(ScenePhase.REST)
        assertEquals(rest.pitch, pose().pitch, .0001f)
        assertEquals(rest.yaw, pose().yaw, .0001f)
        assertEquals(1, h.compositions); assertEquals(0, h.disposals)
    }
    @Test fun drawerLetterRotatesNativelyAndReturns() = rotation(1)
    @Test fun pigeonholeLetterRotatesNativelyAndReturns() = rotation(2)
    @Test fun counterTrayLetterRotatesNativelyAndReturns() = rotation(3)
    @Test fun actualTimeRailPagesRealLettersAndReturnsToNow() {
        val h = CodexMotionHarness(rule); h.start(letters = PostPilotFixtures.letters, level = 1)
        assertTrue(h.exists("tree-letter-1"))
        fun setTime(fraction: Float) {
            val action = requireNotNull(h.tag("post-time-rail").fetchSemanticsNode().config[SemanticsActions.SetProgress].action)
            rule.runOnIdle {
                assertTrue(action(fraction))
                // A direct accessibility callback is not a dispatched Android input frame.
                // Flush its snapshot write before advancing the explicitly paused test clock.
                androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            }; h.frames(20)
        }
        setTime(0f)
        assertEquals("the actual rail must move to the oldest page", 0f,
            h.tag("post-time-rail").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo].current, 0f)
        assertFalse("oldest page must not retain the newest letter; " + h.tag("post-time-rail").fetchSemanticsNode().config.toString(), h.exists("tree-letter-1"))
        setTime(1f)
        assertTrue(h.exists("tree-letter-1")); assertEquals(0, h.calls)
        h.tag("post-time-rail").performTouchInput { swipeLeft(durationMillis = 350) }
        rule.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }; h.frames(20)
        assertFalse("dragging the actual runner must show older letters", h.exists("tree-letter-1"))
        h.tag("post-time-rail").performTouchInput { swipeRight(durationMillis = 350) }
        rule.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }; h.frames(20)
        assertTrue("dragging back must return to now", h.exists("tree-letter-1"))
    }
    @Test fun runnerReceivesRealPointerPositionsBeforeSnapshotFlush() {
        val requests = mutableListOf<Int>() // Plain list: snapshot/recomposition cannot fabricate pointer callbacks.
        val currentPage = androidx.compose.runtime.mutableStateOf(0)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            VillageTheme(true) {
                PostalTimeRail(currentPage.value, 5, true) { page -> requests += page; currentPage.value = page }
            }
        }
        hFrames()
        rule.onNodeWithTag("post-time-rail").performTouchInput { swipeLeft(durationMillis = 350) }
        rule.runOnIdle {
            assertTrue("drag dispatch must produce intermediate positions", requests.size > 3)
            assertEquals(0, requests.first())
            assertEquals(4, requests.last())
            assertTrue(requests.zipWithNext().all { (a,b) -> b >= a })
        }
        // Apply the real callback's page change so the obsolete left end-button no longer covers
        // the start of the return drag. Freezing page=0 would test a non-updating host, not the app.
        rule.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        hFrames()
        rule.runOnIdle { requests.clear() }
        rule.onNodeWithTag("post-time-rail").performTouchInput { swipeRight(durationMillis = 350) }
        rule.runOnIdle { assertEquals(4, requests.first()); assertEquals(0, requests.last()) }
    }
    private fun hFrames() { repeat(3) { rule.mainClock.advanceTimeByFrame() }; rule.waitForIdle() }

    @Test fun singlePageRailDoesNotInventOlderLetters() {
        var changes = 0
        rule.setContent { VillageTheme(true) { PostalTimeRail(0, 1, true) { changes++ } } }
        rule.onNodeWithTag("post-time-rail").assertIsNotEnabled()
        rule.onNodeWithTag("post-time-rail").performSemanticsAction(SemanticsActions.SetProgress) { assertFalse(it(.2f)) }
        rule.onNodeWithTag("post-time-rail").performTouchInput { click(center) }
        assertEquals(0, changes)
    }
    @Test fun oneActualMonthDetentRefreshesTheSamePageWithoutInventingHistory() {
        val requests = mutableListOf<Int>()
        rule.setContent { VillageTheme(true) { PostalTimeRail(0, 1, true, detents = listOf("IX 2026")) { requests += it } } }
        rule.onNodeWithTag("post-time-rail").assertIsEnabled()
        rule.onNodeWithTag("post-time-rail").performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(.2f)) }
        assertEquals(listOf(0), requests)
        rule.onNodeWithTag("post-time-rail").performTouchInput { click(center) }
        assertTrue(requests.size > 1)
        assertTrue(requests.all { it == 0 })
    }
}
