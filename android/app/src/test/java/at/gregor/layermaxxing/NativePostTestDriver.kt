package at.gregor.layermaxxing

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Assert.*

/** Test driver for the actual object gestures. Every page is reached through production controls. */
internal class NativePostTestDriver(val rule: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>) {
    fun tag(t: String) = rule.onNodeWithTag(t, useUnmergedTree = true)
    fun exists(t: String) = rule.onAllNodesWithTag(t, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    fun frames(n: Int = 40) { repeat(n) { rule.mainClock.advanceTimeByFrame() }; rule.waitForIdle() }
    fun tap(t: String) { tag(t).performClick(); frames() }
    fun description(t: String) = tag(t).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()
    fun slots(prefix: String) = rule.onAllNodes(SemanticsMatcher(prefix) { it.config.getOrNull(SceneSlotKey)?.startsWith(prefix) == true }, useUnmergedTree = true).fetchSemanticsNodes()
    fun keys(prefix: String) = slots(prefix).map { it.config[SceneSlotKey] }.toSet()
    fun back() { rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }; frames(200) }
    fun letters(): Set<String> {
        if (exists("tree-time-now")) tap("tree-time-now")
        val seen = mutableSetOf<String>()
        repeat(40) {
            seen += keys("letter:")
            if (!exists("tree-time-back")) return seen
            tap("tree-time-back")
        }
        error("letter page bound exceeded")
    }
    fun people(): Set<String> {
        val seen = mutableSetOf<String>()
        val initial = keys("person:")
        repeat(40) {
            seen += keys("person:")
            if (!exists("tree-knot-right")) return seen
            tap("tree-knot-right")
            if (keys("person:") == initial) return seen
        }
        error("person page bound exceeded")
    }
    fun person(id: Long?) {
        val name = "tree-strip-${id ?: "alle"}"
        repeat(40) {
            if (exists(name)) { tap(name); return }
            assertTrue("more person pages", exists("tree-knot-right")); tap("tree-knot-right")
        }
        error("person not reachable")
    }
    fun letter(id: Long) {
        val name = "tree-letter-$id"
        if (!exists(name) && exists("tree-time-now")) tap("tree-time-now")
        repeat(40) {
            if (exists(name)) { tap(name); frames(40); return }
            assertTrue("letter $id reachable through paging", exists("tree-time-back")); tap("tree-time-back")
        }
        error("letter not reachable")
    }
}
