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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
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
            val action = StageFixtures.action(b, level)?.let { rule.onAllNodesWithText(it)[0].fetchSemanticsNode().boundsInRoot }
            // 7.9.14 (pixel review 360 dp / 1.5×): the whole controls of the place – the post's four views with their
            // counts, the person filter, the letters' area, the quest board, the register, the group pennants, the
            // members – lie whole between the header and the bottom row (story things + golden action), never under it
            val bottomRow = rule.onAllNodes(SemanticsMatcher("story") { it.config.getOrNull(SceneSlotKey)?.startsWith("story:") == true }, useUnmergedTree = true)
                .fetchSemanticsNodes().map { it.boundsInRoot } + listOfNotNull(action)
            val rowTop = bottomRow.minOfOrNull { it.top } ?: root.bottom
            val controls = listOf("post-people", "post-letters", "harbour-board", "library-register").flatMap { tag ->
                rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().map { tag to unclipped(it) } } +
                PostViews.View.entries.flatMap { v -> rule.onAllNodesWithTag("post-view-${v.name}", useUnmergedTree = true).fetchSemanticsNodes().map { "view ${v.label}" to unclipped(it) } } +
                slots("pennant:").map { it } + slots("member:").map { it } + slots("sign:").map { it }
            for ((id, r) in controls) {
                assertTrue("$msg $id $r reaches under the bottom row (top ${rowTop / density} dp)", r.bottom <= rowTop + 1)
                assertTrue("$msg $id $r under the header", r.top >= 100 * density - 1)
                assertTrue("$msg $id $r off screen", r.left >= -1 && r.right <= root.right + 1)
            }
            // the post's views show their label and count whole
            // 1011: the post's views appear when its brass slider is touched: label and count whole, over the bottom row
            if (b == IsleBuilding.POST && StageScenes.of(b, level).clean) {
                // the letter tree: its brass belt touched, the four windows with their scratched labels on screen, labels whole
                rule.onNodeWithTag("tree-belt", useUnmergedTree = true).performClick(); frames()
                for (v in PostViews.View.entries) {
                    rule.onNodeWithTag("tree-window-${v.name}", useUnmergedTree = true).performScrollTo(); frames()
                    val n = rule.onNodeWithTag("tree-window-${v.name}", useUnmergedTree = true).fetchSemanticsNode()
                    val r = unclipped(n)
                    assertTrue("$msg ${v.label} $r", r.width >= 48 * density - 1 && r.height >= 48 * density - 1 && r.left >= -1 && r.right <= root.right + 1 && r.bottom <= root.bottom)
                }
                rule.onAllNodesWithText("Ungelesen", useUnmergedTree = true).fetchSemanticsNodes().forEach { assertTrue("$msg Ungelesen cut", !overflows(it)) }
                rule.activity.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; frames()
            } else if (b == IsleBuilding.POST) {
                rule.onNodeWithTag("post-hotspot-VIEW", useUnmergedTree = true).performClick(); frames()
                for (v in PostViews.View.entries) {
                    val n = rule.onNodeWithTag("post-view-${v.name}", useUnmergedTree = true).fetchSemanticsNode()
                    assertTrue("$msg ${v.label} cut", !overflows(n))
                    assertTrue("$msg ${v.label} under the bottom row", unclipped(n).bottom <= rowTop + 1 && unclipped(n).top >= 100 * density - 1)
                }
                rule.onNodeWithTag("post-back", useUnmergedTree = true).performClick(); frames()
            }
            // group pennants never lie on a member (their name included)
            for ((p, pr) in slots("pennant:")) for ((m, mr) in slots("member:")) assertTrue("$msg $p $pr covers $m $mr", !pr.deflate(1f).overlaps(mr.deflate(1f)))
            // every quest's title, partner, points and button lie ON its paper
            if (b == IsleBuilding.HARBOUR) {
                val papers = rule.onAllNodesWithTag("quest-paper", useUnmergedTree = true).fetchSemanticsNodes()
                val notes = rule.onAllNodesWithTag("quest-note", useUnmergedTree = true).fetchSemanticsNodes()
                assertEquals(msg, notes.size, papers.size)
                for (k in notes.indices) {
                    val frame = notes[k].layoutInfo.coordinates
                    val paper = frame.localBoundingBoxOf(papers[k].layoutInfo.coordinates, clipBounds = false)
                    for (tag in listOf("quest-title", "quest-partner", "quest-done")) {
                        val r = frame.localBoundingBoxOf(rule.onAllNodesWithTag(tag, useUnmergedTree = true)[k].fetchSemanticsNode().layoutInfo.coordinates, clipBounds = false)
                        assertTrue("$msg $tag $r not on its paper $paper", r.left >= paper.left - 1 && r.right <= paper.right + 1 && r.top >= paper.top - 1 && r.bottom <= paper.bottom + 1)
                    }
                }
            }
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
                // 7.9.14: the categories are the place's register (ribbons / card drawers / banners): Alle + four, every one
                // whole on screen with its label not cut, a full touch target, apart from the others and off the book
                val marks = rule.onAllNodesWithTag("library-register", useUnmergedTree = true).fetchSemanticsNodes()
                assertEquals(msg, GlossaryCategories.all.size + 1, marks.size)
                val rects = marks.map { unclipped(it) }
                for ((k, r) in rects.withIndex()) {
                    assertTrue("$msg mark $k $r on screen", r.left >= -1 && r.right <= root.right + 1 && r.bottom <= root.bottom && r.top >= 100 * density)
                    assertTrue("$msg mark $k ${r.height}", r.height >= 48 * density - 1 && r.width >= 48 * density - 1)
                    assertTrue("$msg mark $k on the book", !r.deflate(1f).overlaps(book.deflate(1f)))
                    for (j in k + 1 until rects.size) assertTrue("$msg marks $k/$j", !r.deflate(1f).overlaps(rects[j].deflate(1f)))
                }
                rule.onAllNodesWithTag("library-register-label", useUnmergedTree = true).fetchSemanticsNodes().forEach { n ->
                    val label = unclipped(n); val mark = rects.first { it.contains(label.center) }
                    assertTrue("$msg label ${n.config[androidx.compose.ui.semantics.SemanticsProperties.Text]} cut: $label in $mark", label.left >= mark.left - 1 && label.right <= mark.right + 1)
                    val res = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    n.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult)?.action?.invoke(res)
                    // the label's whole text width fits its mark (measured, not only the box it was given)
                    assertTrue("$msg label cut ${layouts(n)} in $mark", !overflows(n) && res.all { it.multiParagraph.maxIntrinsicWidth <= mark.width })
                }
            }
        }
    }

    @Test fun manyPeopleAndLettersStayReachableOnTheLetterTree() {
        // 13 friends with a letter each (more than the screen shows): every person can be reached by scrolling the
        // person filter, every letter by scrolling the trunk; the search appears and finds one
        val now = 1_790_000_000L
        val many = (0 until 13).map { ApiClient.UserSummary(100L + it, "Freund $it", "friends", "🐻", "#1565C0") }
        val letters = many.map { f -> ApiClient.Message(f.id * 10, f.id, f.name, true, "Gruß ${f.name}", "", "none", now - f.id, "manual", null, null, null,
            true, false, null, false, false, null, null, null, emptyList(), null, null, null) }
        rule.mainClock.autoAdvance = false
        rule.setContent { VillageTheme(true) { PostOffice(many, letters, emptyMap(), {}, {}, {}, {}, level = 1) } }
        frames()
        val d = NativePostTestDriver(rule)
        d.tap("tree-rope")
        assertEquals((listOf("person:alle") + many.map { "person:${it.id}" }).toSet(), d.people())
        d.back()
        assertEquals(letters.map { "letter:${it.id}" }.toSet(), d.letters())
        if (d.exists("tree-time-now")) d.tap("tree-time-now")
        d.tap("post-mail")
        d.tag("post-search").performTextInput("Freund 12")
        d.frames()
        d.back()
        assertEquals(setOf("letter:1120"), d.keys("letter:"))
        assertTrue(d.description("tree-belt").contains("Alle, 1 Briefe"))
    }
}
