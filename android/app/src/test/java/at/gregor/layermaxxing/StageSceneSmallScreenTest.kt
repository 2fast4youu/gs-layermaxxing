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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
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
    /** A node's own laid-out box (position + size), NOT cut by a clipping parent: what is cut off shows as outside. */
    private fun unclipped(n: androidx.compose.ui.semantics.SemanticsNode) = Rect(n.positionInRoot, androidx.compose.ui.geometry.Size(n.size.width.toFloat(), n.size.height.toFloat()))
    /** True when the text of [n] (or of its text children) does not fit: ellipsis, cut lines or overflow. */
    private fun overflows(n: androidx.compose.ui.semantics.SemanticsNode): Boolean {
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        n.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.any { r -> r.didOverflowHeight || (0 until r.lineCount).any { r.isLineEllipsized(it) } } || n.children.any { overflows(it) }
    }
    private fun layouts(n: androidx.compose.ui.semantics.SemanticsNode): String {
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        n.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.joinToString { "${it.size} lines=${it.lineCount} hOv=${it.didOverflowHeight} wOv=${it.didOverflowWidth} c=${it.layoutInput.constraints}" }
    }
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
                assertTrue("$msg $id $r on screen $root", r.left >= -2 * density && r.right <= root.right + 2 * density && r.bottom <= root.bottom)
                action?.let { assertTrue("$msg $id over the action", !r.deflate(2 * density).overlaps(it)) }
            }
            // 7.9.13 (renders: cut off on Anleger / Hafen): every quest note shows its whole "Abhaken"/"Erledigt" button
            if (b == IsleBuilding.HARBOUR) {
                val notes = slots("quest:").map { it.second }
                val buttons = listOf("Abhaken", "Erledigt").flatMap { rule.onAllNodesWithText(it, useUnmergedTree = true).fetchSemanticsNodes() }.map { it.boundsInRoot }
                assertEquals(msg, notes.size, buttons.size)
                for (btn in buttons) assertTrue("$msg button $btn not inside its note $notes", notes.any { n ->
                    btn.left >= n.left - 1 && btn.right <= n.right + 1 && btn.top >= n.top - 1 && btn.bottom <= n.bottom + 1 })
            }
            // 7.9.13 (renders Anleger / Hafen): title, partner, "Abhaken" and × lie whole INSIDE their note and apart from
            // each other (the partner line was under "Abhaken", the × over the edge); both buttons are 48 dp touch targets
            if (b == IsleBuilding.HARBOUR) {
                // measured exactly in the note's own (tilted) frame: no slack for the rotation needed
                val notes = rule.onAllNodesWithTag("quest-note", useUnmergedTree = true).fetchSemanticsNodes()
                assertEquals(msg, slots("quest:").size, notes.size)
                for (tag in listOf("quest-title", "quest-partner", "quest-done", "quest-delete"))
                    assertEquals("$msg $tag", notes.size, rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size)
                for (k in notes.indices) {
                    val frame = notes[k].layoutInfo.coordinates
                    val note = Rect(0f, 0f, frame.size.width.toFloat(), frame.size.height.toFloat())
                    val parts = listOf("quest-title", "quest-partner", "quest-done", "quest-delete").associateWith {
                        frame.localBoundingBoxOf(rule.onAllNodesWithTag(it, useUnmergedTree = true)[k].fetchSemanticsNode().layoutInfo.coordinates, clipBounds = false) }
                    for ((tag, r) in parts) assertTrue("$msg $tag $r not inside its note $note",
                        r.left >= note.left - 1 && r.right <= note.right + 1 && r.top >= note.top - 1 && r.bottom <= note.bottom + 1)
                    val list = parts.entries.toList()
                    for (i in list.indices) for (j in i + 1 until list.size)
                        assertTrue("$msg ${list[i].key} ${list[i].value} / ${list[j].key} ${list[j].value}", !list[i].value.deflate(1f).overlaps(list[j].value.deflate(1f)))
                    for (tag in listOf("quest-done", "quest-delete")) assertTrue("$msg $tag in $note: $parts", parts.getValue(tag).height >= 48 * density - 1 && parts.getValue(tag).width >= 48 * density - 1)
                    assertTrue("$msg Abhaken cut", !overflows(rule.onAllNodesWithTag("quest-done", useUnmergedTree = true)[k].fetchSemanticsNode()))
                }
            }
            // 7.9.13 (render Ausguck): the friend request card shows name, request text and both answers whole
            if (b == IsleBuilding.LIGHTHOUSE) {
                val card = unclipped(rule.onNodeWithTag("bottle-card", useUnmergedTree = true).fetchSemanticsNode())
                assertTrue("$msg card $card on screen", card.left >= 0f && card.right <= root.right && card.bottom <= root.bottom)
                val parts = listOf("bottle-name", "bottle-ask", "bottle-no", "bottle-yes").associateWith { rule.onNodeWithTag(it, useUnmergedTree = true).fetchSemanticsNode() }
                for ((tag, n) in parts) {
                    val r = unclipped(n)
                    assertTrue("$msg $tag $r not inside $card", r.left >= card.left - 1 && r.right <= card.right + 1 && r.top >= card.top - 1 && r.bottom <= card.bottom + 1)
                    assertTrue("$msg $tag text cut ${layouts(n)}", !overflows(n))
                }
                assertTrue(msg, rule.onNodeWithTag("bottle-ask", useUnmergedTree = true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString().contains("Freundschaftsanfrage"))
                for (tag in listOf("bottle-no", "bottle-yes")) assertTrue("$msg $tag", unclipped(parts.getValue(tag)).height >= 48 * density - 1)
                assertTrue(msg, !unclipped(parts.getValue("bottle-no")).overlaps(unclipped(parts.getValue("bottle-yes"))))
                // the taller card covers no friend or waiting signal
                for ((id, r) in objects) assertTrue("$msg $id $r under the request card $card", !r.deflate(2 * density).overlaps(card))
            }
            // the library's book: on screen, its search field reachable
            if (b == IsleBuilding.LIBRARY) {
                val book = slots("book:").single().second
                assertTrue(msg, book.height > 200 * density && book.bottom <= root.bottom + 2 * density)
                rule.onAllNodesWithText("Wort oder Erklärung suchen", useUnmergedTree = true)[0].assertExists()
                // 7.9.13 (renders Bücherstube / Bibliothek: "Sprüche" cut at the right): every category chip whole on the
                // book's page, text not cut, a full touch target, and apart from the others
                val chips = rule.onAllNodesWithTag("glossary-category", useUnmergedTree = true).fetchSemanticsNodes()
                assertEquals(msg, GlossaryCategories.all.size + 1, chips.size)
                val rects = chips.map { unclipped(it) }
                for ((k, r) in rects.withIndex()) {
                    assertTrue("$msg chip $k $r outside the book $book", r.left >= book.left - 1 && r.right <= book.right + 1 && r.right <= root.right)
                    assertTrue("$msg chip $k ${r.height}", r.height >= 48 * density - 1)
                    for (j in k + 1 until rects.size) assertTrue("$msg chips $k/$j", !r.deflate(1f).overlaps(rects[j].deflate(1f)))
                }
                rule.onAllNodesWithText("Sprüche", useUnmergedTree = true).fetchSemanticsNodes().forEach { assertTrue("$msg Sprüche cut", !overflows(it)) }
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
