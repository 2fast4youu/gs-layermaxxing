package at.gregor.layermaxxing

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
 * 7.9.12: a small phone (360 × 640 dp) with a large system font (1.5×), Robolectric (JVM, not a device): on all 24
 * function stages the data objects stay on screen, below the header, apart from each other and off the golden action;
 * many friends turn into scenic pages and every one of them stays reachable (nothing is filtered away).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class StageSceneSmallScreenTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private var current by mutableStateOf(IsleBuilding.POST to 0)
    private fun slots(prefix: String = ""): List<Pair<String, Rect>> = rule.onAllNodes(SemanticsMatcher("scene slot") { it.config.getOrNull(SceneSlotKey)?.startsWith(prefix) == true }, useUnmergedTree = true)
        .fetchSemanticsNodes().map { it.config[SceneSlotKey] to it.boundsInRoot }
    private fun frames() { rule.waitForIdle(); repeat(20) { rule.mainClock.advanceTimeByFrame() }; rule.waitForIdle() }

    @Test fun allTwentyFourStagesStayUsableOnASmallPhoneWithALargeFont() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 1.5f)) {
                androidx.compose.runtime.key(current) { StageFixtures.Place(current.first, current.second) }
            }
        }
        val density = rule.activity.resources.displayMetrics.density
        for (b in IsleBuilding.entries) for (level in 0..3) {
            rule.runOnIdle { current = b to level; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
            frames()
            val msg = "$b $level"
            val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
            val objects = slots().filter { !it.first.startsWith("book:") }
            assertTrue(msg, objects.isNotEmpty() || b == IsleBuilding.LIBRARY)
            for (i in objects.indices) for (j in i + 1 until objects.size)
                assertTrue("$msg ${objects[i].first} / ${objects[j].first}", !objects[i].second.deflate(4 * density).overlaps(objects[j].second.deflate(4 * density)))
            val action = StageFixtures.action(b)?.let { rule.onAllNodesWithText(it)[0].fetchSemanticsNode().boundsInRoot }
            for ((id, r) in objects) {
                assertTrue("$msg $id below the header ${r.top / density}", r.top >= 100 * density)
                assertTrue("$msg $id on screen", r.left >= -2 * density && r.right <= root.right + 2 * density && r.bottom <= root.bottom)
                action?.let { assertTrue("$msg $id over the action", !r.deflate(2 * density).overlaps(it)) }
            }
            // the library's book: on screen, its search field reachable
            if (b == IsleBuilding.LIBRARY) {
                val book = slots("book:").single().second
                assertTrue(msg, book.height > 200 * density && book.bottom <= root.bottom + 2 * density)
                rule.onAllNodesWithText("Wort oder Erklärung suchen", useUnmergedTree = true)[0].assertExists()
            }
        }
    }

    @Test fun manyFriendsBecomeScenicPagesOnTheLetterTreeAndEveryoneIsReachable() {
        val many = (0 until 13).map { ApiClient.UserSummary(100L + it, "Freund $it", "friends", "🐻", "#1565C0") }
        rule.mainClock.autoAdvance = false
        rule.setContent { VillageTheme(true) { PostOffice(many, emptyList(), emptyMap(), {}, {}, {}, {}, level = 0) } }
        frames()
        val perPage = StageScenes.of(IsleBuilding.POST, 0).slots.size
        val seen = mutableSetOf<String>()
        var pages = 0
        while (true) {
            val here = slots("friend:").map { it.first }
            assertTrue(here.size in 1..perPage)
            seen += here; pages++
            val next = rule.onAllNodesWithContentDescription("Nächste Seite: Freunde")
            if (next.fetchSemanticsNodes().isEmpty() || pages > 5) break
            val enabled = next.fetchSemanticsNodes()[0].config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Disabled) == null
            if (!enabled) break
            next[0].performClick(); frames()
        }
        assertEquals(many.map { "friend:${it.id}" }.toSet(), seen)
        assertEquals((many.size + perPage - 1) / perPage, pages)
    }
}
