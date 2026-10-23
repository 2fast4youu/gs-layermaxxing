package at.gregor.layermaxxing

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performScrollTo
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
        tap("post-mail")
        val actual = keys("letter:")
        back()
        return actual
    }
    fun people(): Set<String> = keys("person:")
    fun person(id: Long?) {
        val name = "tree-strip-${id ?: "alle"}"
        tag(name).performScrollTo()
        tap(name)
    }
    fun letter(id: Long) {
        tap("post-mail")
        val name = "post-select-$id"
        tag(name).performScrollTo()
        tap(name)
        frames(40)
    }
}
