package at.gregor.layermaxxing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 7.9.12 on the real IslandWorld (Robolectric, JVM simulation, not a device): for each of the 6 buildings at each of
 * its 4 stages, tapping the building on the own island and "Öffnen" shows exactly THAT building's stage scene (no other
 * building's place), with the real data objects in it, and one of its data actions reaches the app's callback.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class IslandWorldStagesNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val calls = mutableListOf<String>()

    private fun construction(b: IsleBuilding, level: Int) = ApiClient.IslandConstruction(mode = "normal", available = 0, ageDays = 30, land = 5,
        buildings = if (level > 0) mapOf(b.name to level) else emptyMap(), starters = if (level == 0) setOf(b.name) else emptySet(),
        positions = mapOf(b.name to FitIsland.SPOTS.getValue(b.name)))

    private var writer: LetterWriter? = null
    private var requests: Map<Long, OpenRequest> = emptyMap()
    private var extraLetters: List<ApiClient.Message> = emptyList()
    @Composable private fun World(c: ApiClient.IslandConstruction) {
        val home = ApiClient.HomeIsland(1, emptyMap(), 0, emptyList(), construction = c)
        VillageTheme(true) { IslandWorld(
            ownName = "Gregor", ownEmoji = "🦉", friends = StageFixtures.friends, groups = StageFixtures.groups, islands = ApiClient.Islands(140, emptyList()),
            quests = StageFixtures.quests, letters = StageFixtures.letters + extraLetters, topics = StageFixtures.topics, opened = emptyMap(), onBack = {}, onChat = {},
            onComposeLetter = { calls += "write:$it" }, onOpenLetter = { calls += "open:${it.id}" }, onLockedTap = { calls += "locked:${it.id}" },
            onTopics = {}, onAllTopics = { calls += "topics" }, onGlossary = {}, onPeople = { calls += "people" },
            onCreateQuest = { _, _, _, _, _, _ -> }, onQuestDone = { q, d -> calls += "done:${q.id}:$d" }, onQuestDelete = { calls += "delete:${it.id}" },
            ownId = 1, startView = "home", loadIsland = { home }, incoming = StageFixtures.incoming, outgoing = StageFixtures.outgoing,
            onRespondFriend = { id, yes -> calls += "respond:$id:$yes" }, onPlace = { calls += "place:${it.name}" },
            glossary = { GlossaryScreen(seed = StageFixtures.glossary, addTerm = { _, _, _ -> }) },
            openRequests = requests, letterWriter = writer,
        ) }
    }
    private fun tick(ms: Long = 800) { rule.mainClock.advanceTimeBy(ms); rule.waitForIdle() }
    private fun slot(id: String) = rule.onAllNodes(SemanticsMatcher("slot $id") { it.config.getOrNull(SceneSlotKey) == id }, useUnmergedTree = true)

    private var current by androidx.compose.runtime.mutableStateOf<ApiClient.IslandConstruction?>(null)
    private fun visit(b: IsleBuilding, level: Int) {
        calls.clear()
        if (current == null) { current = construction(b, level); rule.setContent { current?.let { c -> androidx.compose.runtime.key(c) { World(c) } } } }
        else rule.runOnIdle { current = construction(b, level) }
        tick(1500)
        // tap the building on the island, then "Öffnen" in its compact actions
        rule.onAllNodesWithContentDescription(b.label).filter(hasClickAction())[0].performClick(); tick()
        rule.onAllNodesWithText("Öffnen")[0].performClick(); tick(1200)
        val scene = StageScenes.of(b, level)
        val msg = "$b $level"
        rule.onAllNodesWithContentDescription("Bühne: ${scene.caption}")[0].assertExists()
        rule.onAllNodesWithContentDescription("Ort: ${scene.stage.name}")[0].assertExists()
        // never another building's (or another stage's) place
        for (o in StageScenes.all) if (o != scene) assertEquals("$msg shows ${o.building} ${o.level}", 0,
            rule.onAllNodesWithContentDescription("Bühne: ${o.caption}").fetchSemanticsNodes().size)
        when (b) {
            IsleBuilding.POST -> {
                // Current production control: POST0 Filter → Person; POST1..3 Person.
                // Count all Anna letters via the real picker, not only the current time page.
                val d = NativePostTestDriver(rule)
                d.tap("post-person")
                d.person(3)
                // Only Anna's received letter belongs in the physical inbox; her
                // two outgoing records remain chat/history data, never inbox tiles.
                assertEquals(msg, setOf("letter:4"), d.letters())
                assertTrue("$msg no eager callback $calls", calls.isEmpty())
                d.letter(4)
                assertTrue("$msg arrival preview", d.exists("post-arrival"))
                assertTrue("$msg preview must not dispatch $calls", calls.isEmpty())
                d.tap("post-arrival-action")
                assertEquals("$msg exactly one locked callback", listOf("locked:4"), calls)
            }
            IsleBuilding.HARBOUR -> {
                slot("quest:1")[0].assertExists()
                rule.onAllNodesWithText("Abhaken")[0].performClick(); tick()
                assertTrue("$msg $calls", calls.any { it.startsWith("done:") })
            }
            IsleBuilding.LIGHTHOUSE -> {
                slot("friend:3")[0].assertExists(); slot("waiting:10")[0].assertExists()
                rule.onAllNodesWithText("Annehmen")[0].performClick(); tick()
                assertTrue("$msg $calls", "respond:9:true" in calls)
            }
            IsleBuilding.LIBRARY -> {
                slot("book:${scene.holder}")[0].assertExists()
                // 7.9.14: the place's register (ribbon / drawer / banner) picks the category
                rule.onAllNodesWithContentDescription("Register Witze")[0].performClick(); tick()
                rule.onAllNodesWithText("Kalauer", useUnmergedTree = true)[0].assertExists()
            }
            IsleBuilding.CAMPFIRE -> {
                assertTrue(msg, rule.onAllNodes(SemanticsMatcher("members") { it.config.getOrNull(SceneSlotKey)?.startsWith("member:1:") == true }, useUnmergedTree = true)
                    .fetchSemanticsNodes().size == 4 || level >= 2)
                slot("group:1")[0].assertExists()
                rule.onAllNodesWithText("Gruppen verwalten")[0].performClick(); tick()
                assertTrue("$msg $calls", "place:CAMPFIRE" in calls)
            }
            IsleBuilding.HOUSE -> {
                slot("me")[0].assertExists(); slot("keepsake:friends")[0].assertExists()
                rule.onAllNodesWithText("Profil & Einstellungen")[0].performClick(); tick()
                assertTrue("$msg $calls", "place:HOUSE" in calls)
            }
        }
    }

    @Test fun post() = (0..3).forEach { visit(IsleBuilding.POST, it) }

    /**
     * 1074 end-to-end wiring: the island hands the app's [LetterWriter] and open requests through to the letter tree
     * (as LayerHome does): the pen writes on the tree's paper – never the messenger's sheet (onComposeLetter) – and a
     * letter whose open request runs shows its seal waiting. TESTDATEN; the send is recorded, never sent.
     */
    @Test fun theIslandHandsTheWriterAndTheOpenRequestsToTheLetterTree() {
        val sent = mutableListOf<ApiClient.SendRequest>()
        writer = LetterWriter(StageFixtures.friends, StageFixtures.groups, emptyList(), creativeActive = false,
            transmit = { sent += it; listOf(1L) }, onSent = {}, draftState = androidx.compose.runtime.mutableStateOf(null))
        // an arrived, still sealed letter from Lena (TESTDATEN; not Anna, whose three letters the visit counts) whose open request is running in the app
        val arrived = StageFixtures.letters.first { it.id == 4L }.copy(id = 40, peerId = 4, peerName = "Lena", mode = "manual", randomFrom = null, randomTo = null, unlocked = true)
        extraLetters = listOf(arrived)
        assertEquals(LetterState.READY, Conversations.letterState(arrived, false))
        requests = mapOf(arrived.id to OpenRequest.RUNNING)
        visit(IsleBuilding.POST, 0)
        val tag = { t: String -> rule.onAllNodes(androidx.compose.ui.test.hasTestTag(t), useUnmergedTree = true) }
        // (the visit above filtered the tree to Anna; all people again) the arrived letter's seal waits for its running request
        val d = NativePostTestDriver(rule)
        d.tap("post-person"); d.person(null)
        calls.clear()
        d.letter(arrived.id); tick(1200)
        assertTrue(rule.onAllNodesWithText("wird geöffnet", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        tag("tree-envelope")[0].performClick(); tick()
        assertTrue("$calls", calls.none { it.startsWith("open:") })
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }; tick(1200)
        // the pen: "An wen?" and the letter on the paper
        tag("tree-pen")[0].performClick(); tick(1200)
        val friend = StageFixtures.friends.first()
        tag("tree-to-${friend.id}")[0].performClick(); tick(1200)
        assertTrue(tag("tree-write-text").fetchSemanticsNodes().isNotEmpty())
        assertTrue("$calls", calls.none { it.startsWith("write:") })
        assertTrue(writer!!.draft?.selected == setOf(friend.id))
        assertTrue(sent.isEmpty())
    }
    @Test fun harbour() = (0..3).forEach { visit(IsleBuilding.HARBOUR, it) }
    @Test fun lighthouse() = (0..3).forEach { visit(IsleBuilding.LIGHTHOUSE, it) }
    @Test fun library() = (0..3).forEach { visit(IsleBuilding.LIBRARY, it) }
    @Test fun campfire() = (0..3).forEach { visit(IsleBuilding.CAMPFIRE, it) }
    @Test fun house() = (0..3).forEach { visit(IsleBuilding.HOUSE, it) }
}
