package at.gregor.layermaxxing

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 7.9.13 E: the 24 stage stories of docs/insel-kueste-storyplan-7.9.13.md as painted things with the building's real
 * mechanics (Robolectric on a 360 × 640 dp phone, JVM, not a device). Every tap below is a real click on the drawn
 * thing and must reach the real handler with the real data; empty data says why and does nothing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class StageStoryTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val density get() = rule.activity.resources.displayMetrics.density
    private fun frames() { rule.waitForIdle(); repeat(20) { rule.mainClock.advanceTimeByFrame() }; rule.waitForIdle() }
    private fun things(): List<Pair<String, Rect>> = rule.onAllNodes(SemanticsMatcher("story") { it.config.getOrNull(SceneSlotKey)?.startsWith("story:") == true }, useUnmergedTree = true)
        .fetchSemanticsNodes().map { it.config[SceneSlotKey] to it.boundsInRoot }
    private fun thing(key: String) = rule.onAllNodes(SemanticsMatcher(key) { it.config.getOrNull(SceneSlotKey) == "story:$key" }, useUnmergedTree = true)
    private fun described(key: String) = thing(key).fetchSemanticsNodes().single().config
    private fun tap(key: String) { thing(key)[0].performClick(); frames() }

    // ---- the table ----

    @Test fun twentyFourChaptersEachWithThingsThatCarryTheAnchorOfTheStageBefore() {
        assertEquals(24, StageStory.all.size)
        for (b in IsleBuilding.entries) for (l in 0..3) {
            val c = StageStory.of(b, l)
            assertTrue("$b $l core", c.core.isNotBlank())
            assertEquals("$b $l two things (left and right of the action)", 2, c.items.size)
            assertEquals("$b $l", 2, c.items.map { it.key }.toSet().size)
            if (l > 0) assertTrue("$b $l keeps an anchor of stage ${l - 1}", c.items.any { i -> StageStory.of(b, l - 1).items.any { it.key == i.key } })
        }
        // the things do what the building does
        val acts = mapOf(
            IsleBuilding.HOUSE to setOf(StageStory.Act.PROFILE, StageStory.Act.EDIT),
            IsleBuilding.POST to setOf(StageStory.Act.UNREAD, StageStory.Act.LOCKED),
            IsleBuilding.LIGHTHOUSE to setOf(StageStory.Act.FIND, StageStory.Act.REQUESTS, StageStory.Act.FRIENDS),
            IsleBuilding.LIBRARY to setOf(StageStory.Act.SEARCH, StageStory.Act.ADD_WORD, StageStory.Act.CATEGORIES),
            IsleBuilding.CAMPFIRE to setOf(StageStory.Act.GROUP_QUEST, StageStory.Act.GROUP_PLANS),
            IsleBuilding.HARBOUR to setOf(StageStory.Act.TOPICS, StageStory.Act.ARRIVALS, StageStory.Act.OPEN_QUESTS),
        )
        for ((b, ok) in acts) for (l in 0..3) StageStory.of(b, l).items.forEach { assertTrue("$b $l ${it.act}", it.act in ok) }
        // the fire ring's story has no house or hammock
        for (l in 0..3) StageStory.of(IsleBuilding.CAMPFIRE, l).items.forEach { assertTrue(it.res != R.drawable.decor_hammock && it.res != R.drawable.b_house) }
    }

    @Test fun ordinaryStartersAreNotOnTheBeachOnlyTheHarbourStarterIsAtTheWater() {
        // 7.9.14: the painted places themselves: the night camp, the letter tree, the stump and the campfire are inland
        // (forest, meadow); the palm stands at the island's edge looking out to sea; the landing starts on the shore
        for (b in IsleBuilding.entries) {
            val start = StageScenes.of(b, 0)
            when (b) {
                IsleBuilding.HARBOUR -> assertTrue(start.caption, start.caption.contains("Steg") && start.caption.contains("Ufer"))
                IsleBuilding.LIGHTHOUSE -> assertTrue(start.caption, start.caption.contains("Rand") && start.caption.contains("Meer"))
                else -> assertTrue("$b ${start.caption}", "Strand" !in start.caption && "Sand" !in start.caption && "Meer" !in start.caption)
            }
        }
    }

    // ---- on a small phone with a large font: there, tappable, apart, off the action and the book ----

    @Test fun onAllTwentyFourStagesTheThingsAreBigEnoughOnScreenAndCoverNothing() {
        var current by mutableStateOf(IsleBuilding.POST to 0)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 1.5f)) {
                androidx.compose.runtime.key(current) { StageFixtures.Place(current.first, current.second) }
            }
        }
        for (b in IsleBuilding.entries) for (level in 0..3) {
            rule.runOnIdle { current = b to level; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
            frames()
            val msg = "$b $level"
            val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
            val drawn = things()
            // a clean stage has no story buttons: its painted things act (letter tree: padlock = sealed, sheets = letters)
            assertEquals(msg, if (StageScenes.of(b, level).clean) emptySet() else StageStory.of(b, level).items.map { "story:${it.key}" }.toSet(), drawn.map { it.first }.toSet())
            for ((id, r) in drawn) {
                assertTrue("$msg $id ≥ 48 dp", r.width >= 48 * density - 1 && r.height >= 48 * density - 1)
                assertTrue("$msg $id on screen", r.left >= 0f && r.right <= root.right + 1 && r.bottom <= root.bottom + 1 && r.top >= 0f)
                val node = described(id.removePrefix("story:"))
                assertTrue("$msg $id described", node.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()?.isNotBlank() == true)
            }
            for (i in drawn.indices) for (j in i + 1 until drawn.size) assertTrue(msg, !drawn[i].second.overlaps(drawn[j].second))
            StageFixtures.action(b, level)?.let { a ->
                val action = rule.onAllNodesWithText(a)[0].fetchSemanticsNode().boundsInRoot
                drawn.forEach { (id, r) -> assertTrue("$msg $id over the action", !r.overlaps(action)) }
            }
            // no thing lies on a data object or on the book's pages
            val objects = rule.onAllNodes(SemanticsMatcher("slot") { n -> n.config.getOrNull(SceneSlotKey)?.let { !it.startsWith("story:") } == true }, useUnmergedTree = true)
                .fetchSemanticsNodes().map { it.config[SceneSlotKey] to it.boundsInRoot }
            for ((id, r) in drawn) for ((o, ob) in objects) assertTrue("$msg $id covers $o", !r.deflate(2 * density).overlaps(ob.deflate(2 * density)))
        }
    }

    // ---- the mechanics: real taps, real handlers, real data ----

    private val now = System.currentTimeMillis() / 1000
    private fun letter(id: Long, peer: Long, mode: String, incoming: Boolean, unlocked: Boolean, createdAt: Long) = ApiClient.Message(
        id, peer, "P$peer", incoming, "Brief $id", "", "none", createdAt, mode, if (mode == "timed") now + 86_400 else null,
        null, null, unlocked, false, null, false, false, null, null, null, emptyList(), null, null, null,
    )

    private var current by mutableStateOf(0)

    @Test fun thePostsFlapOpensTheOldestUnreadLetterAndTheSealTheOldestLockedOne() {
        val opened = mutableListOf<Long>(); val locked = mutableListOf<Long>()
        val letters = listOf(letter(7, 3, "manual", true, true, now - 50), letter(5, 2, "manual", true, true, now - 90),
            letter(9, 2, "timed", false, false, now - 30), letter(8, 3, "timed", true, false, now - 60))
        for (level in 0..3) {
            opened.clear(); locked.clear()
            rule.runOnIdle { current = level; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
            if (level == 0) rule.setContent { androidx.compose.runtime.key(current) { VillageTheme(true) { PostOffice(StageFixtures.friends, letters, emptyMap(), onOpen = { opened += it.id }, onLocked = { locked += it.id }, onWrite = {}, onClose = {}, level = current) } } }
            frames()
            val post = NativePostTestDriver(rule)
            assertEquals(letters.map { "letter:${it.id}" }.toSet(), post.letters())
            if (post.exists("tree-time-now")) post.tap("tree-time-now")
                // All post stages use their own physical anchors and the shared seal protocol.
                // the letter tree (clean stage): the unread letter is opened from its sheet, the padlock shows the oldest sealed one
                val t = { tag: String -> rule.onNode(androidx.compose.ui.test.hasTestTag(tag), useUnmergedTree = true).performClick(); frames() }
                // 1074: an arrived letter hangs sealed – its envelope, then its seal opens it through the real call
                // (22.10. native scene: the camera moves to the envelope – 720 ms – before it takes a tap)
                t("tree-letter-5"); frames(); frames(); t("tree-envelope"); assertEquals("$level", listOf(5L), opened)
                // (the camera moves back out first: 640 ms)
                rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }; frames(); frames(); frames()
                t("tree-padlock"); assertEquals("$level", listOf(8L), locked)

        }
    }



    @Test fun anEmptyPostSaysWhyAndTapsDoNothing() {
        var calls = 0
        rule.setContent { VillageTheme(true) { PostOffice(StageFixtures.friends, emptyList(), emptyMap(), onOpen = { calls++ }, onLocked = { calls++ }, onWrite = {}, onClose = {}, level = 1) } }
        frames()
        val post = NativePostTestDriver(rule)
        assertTrue(post.keys("letter:").isEmpty())
        assertTrue(post.exists("post-empty"))
        assertEquals("Kein Brief unterwegs", post.description("tree-padlock"))
        post.tag("tree-padlock").performClick(); frames()
        assertEquals(0, calls)
    }

    @Test fun theSpyglassSearchesAndTheBottleShowsTheRealRequests() {
        var finds = 0
        rule.setContent { VillageTheme(true) { LighthouseView(StageFixtures.friends, emptyMap(), StageFixtures.incoming, emptyList(), onRespond = { _, _ -> }, onFriend = {}, onFind = { finds++ }, onClose = {}, level = 0) } }
        frames()
        tap("spyglass"); assertEquals(1, finds)
        assertTrue(described("signal")[SemanticsProperties.ContentDescription].single().endsWith("· 1"))
        rule.onAllNodesWithText("Jonas", substring = true)[0].assertExists()
    }

    @Test fun theLibrarysThingsWorkTheGlossaryInTheBook() {
        var seen: GlossaryCommands? = null
        rule.setContent { VillageTheme(true) { LibraryBook(onClose = {}, glossary = {
            seen = LocalGlossaryCommands.current
            GlossaryScreen(seed = StageFixtures.glossary, addTerm = { _, _, _ -> })
        }, level = 2) } }
        frames()
        tap("books")
        assertTrue("next category: Begriffe", described("books")[SemanticsProperties.ContentDescription].single().contains("jetzt: Begriffe"))
        rule.onAllNodesWithContentDescription("Register Begriffe")[0].assertIsSelected()
        // the quill reaches the glossary in the book (its add dialog is the existing one; its focused field never lets
        // Robolectric idle, so the command is checked, not the dialog's pixels)
        assertEquals(0, seen!!.add)
        thing("quill")[0].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        assertEquals(1, seen!!.add)
        assertTrue(seen!!.canAdd)
    }

    @Test fun theMagnifierFocusesTheBooksSearch() {
        rule.setContent { VillageTheme(true) { LibraryBook(onClose = {}, glossary = { GlossaryScreen(seed = StageFixtures.glossary, addTerm = { _, _, _ -> }) }, level = 0) } }
        frames()
        tap("lens")
        rule.onAllNodes(SemanticsMatcher("search field") { n -> n.config.getOrNull(SemanticsProperties.EditableText) != null }, useUnmergedTree = true)[0].assertIsFocused()
    }

    @Test fun theFireMakesAPlanForTheGroupSittingThereAndTheNoticeShowsTheRealPlans() {
        val planned = mutableListOf<Long>(); var plans = 0
        rule.setContent { VillageTheme(true) { CommunityHall(StageFixtures.groups, StageFixtures.quests, onGroupQuest = { planned += it }, onManage = {}, onClose = {}, level = 0, onPlans = { plans++ }) } }
        frames()
        tap("fire"); assertEquals(listOf(1L), planned)
        assertTrue(described("notice")[SemanticsProperties.ContentDescription].single().endsWith("· 1"))
        tap("notice"); assertEquals(1, plans)
    }

    @Test fun withoutGroupsTheFireSaysWhy() {
        rule.setContent { VillageTheme(true) { CommunityHall(emptyList(), emptyList(), onGroupQuest = {}, onManage = {}, onClose = {}, level = 1, onPlans = {}) } }
        frames()
        assertEquals("Noch keine Gruppe – lege eine an", described("fire").getOrNull(SemanticsProperties.StateDescription))
    }

    @Test fun theHarboursPennantOpensTheTopicsAndTheBoatTheArrivedLetters() {
        var topics = 0; var arrivals = 0
        rule.setContent { VillageTheme(true) { HarbourBoard(StageFixtures.quests, StageFixtures.topics, { _, _ -> }, {}, onNewQuest = {}, onAllTopics = { topics++ }, onClose = {}, level = 0, arrivals = 2, onArrivals = { arrivals++ }) } }
        frames()
        assertTrue(described("pennant")[SemanticsProperties.ContentDescription].single().endsWith("· 1"))
        tap("pennant"); assertEquals(1, topics)
        assertTrue(described("arrival")[SemanticsProperties.ContentDescription].single().endsWith("· 2"))
        tap("arrival"); assertEquals(1, arrivals)
    }

    @Test fun theBollardsCountTheOpenQuestsAndShowThem() {
        rule.setContent { VillageTheme(true) { HarbourBoard(StageFixtures.quests, StageFixtures.topics, { _, _ -> }, {}, onNewQuest = {}, onAllTopics = {}, onClose = {}, level = 2) } }
        frames()
        assertTrue(described("bollard")[SemanticsProperties.ContentDescription].single().endsWith("· 2"))
        rule.onAllNodesWithText("Erledigt", substring = true)[0].performClick(); frames()
        assertTrue(rule.onAllNodesWithText("Gemeinsam auf den Gipfel").fetchSemanticsNodes().isEmpty())
        tap("bollard")
        rule.onAllNodesWithText("Gemeinsam auf den Gipfel")[0].assertExists()
    }

    @Test fun theNameBoardOpensTheProfileTheToolsTheEditorAndTheFlagCarriesTheProfileColour() {
        var settings = 0; var edits = 0
        rule.setContent { VillageTheme(true) { MyHouse("Gregor", "🦉", "#17A2A6", 5, 1, 6, 140, onSettings = { settings++ }, onEdit = { edits++ }, onClose = {}, level = 3) } }
        frames()
        tap("name"); assertEquals(1, settings)
        tap("tools"); assertEquals(1, edits)
        assertTrue(described("name")[SemanticsProperties.ContentDescription].single().startsWith("Fahne in deiner Profilfarbe"))
        assertNull("the old duplicate edit button is gone", rule.onAllNodesWithText("Insel bearbeiten").fetchSemanticsNodes().firstOrNull())
    }
}
