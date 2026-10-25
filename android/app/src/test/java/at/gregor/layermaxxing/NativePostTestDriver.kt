package at.gregor.layermaxxing

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Assert.*

/** Test driver for the actual object gestures. Every page is reached through production controls. */
internal class NativePostTestDriver(val rule: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>) {
    fun tag(t: String) = rule.onNodeWithTag(t, useUnmergedTree = true)
    fun exists(t: String) = rule.onAllNodesWithTag(t, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    fun frames(n: Int = 40) { repeat(n) { rule.mainClock.advanceTimeByFrame() }; rule.waitForIdle() }
    /** Advance animated ScrollBy ourselves: performScrollTo loops without frames when autoAdvance=false. */
    fun scrollTo(t: String) {
        repeat(3) {
            val matches = rule.onAllNodesWithTag(t, useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals("unique target $t (bounded lookup; no error-tree dump)", 1, matches.size)
            val node = matches.single()
            val owner = generateSequence(node.parent) { it.parent }
                .firstOrNull { it.config.contains(SemanticsActions.ScrollBy) } ?: return
            val viewport = owner.boundsInRoot
            val center = node.positionInRoot.y + node.size.height / 2f
            if (center >= viewport.top && center <= viewport.bottom && node.boundsInRoot.height > 0f) return
            val action = owner.config[SemanticsActions.ScrollBy].action
            assertNotNull("ScrollBy action for $t", action)
            rule.runOnIdle { action!!.invoke(0f, center - viewport.center.y) }
            frames()
        }
        val node = rule.onAllNodesWithTag(t, useUnmergedTree = true).fetchSemanticsNodes().single()
        val owner = generateSequence(node.parent) { it.parent }
            .first { it.config.contains(SemanticsActions.ScrollBy) }
        val center = node.positionInRoot.y + node.size.height / 2f
        assertTrue("$t remains outside scroll viewport ${owner.boundsInRoot}: ${node.positionInRoot}/${node.size}",
            center >= owner.boundsInRoot.top && center <= owner.boundsInRoot.bottom && node.boundsInRoot.height > 0f)
    }
    fun tap(t: String) {
        if (t in listOf("post-person", "post-status", "post-mail") && !exists(t) && exists("post-filter")) {
            tag("post-filter").performClick(); frames()
        }
        scrollTo(t); tag(t).performClick(); frames() }
    fun description(t: String) = tag(t).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()
    fun slots(prefix: String) = rule.onAllNodes(SemanticsMatcher(prefix) { it.config.getOrNull(SceneSlotKey)?.startsWith(prefix) == true }, useUnmergedTree = true).fetchSemanticsNodes()
    fun keys(prefix: String) = slots(prefix).map { it.config[SceneSlotKey] }.toSet()
    fun back() {
        // Dispatcher callbacks write Compose state outside a test gesture. With the clock
        // frozen, publish that snapshot before advancing frames or the old picker survives.
        rule.runOnIdle {
            rule.activity.onBackPressedDispatcher.onBackPressed()
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        frames(200)
    }
    fun letters(): Set<String> {
        tap("post-mail")
        val actual = keys("letter:")
        back()
        return actual
    }
    fun people(): Set<String> = keys("person:")
    fun person(id: Long?) {
        val name = "tree-strip-${id ?: "alle"}"
        scrollTo(name)
        tap(name)
    }
    fun letter(id: Long) {
        tap("post-mail")
        val name = "post-select-$id"
        scrollTo(name)
        tap(name)
        frames(40)
    }
}
