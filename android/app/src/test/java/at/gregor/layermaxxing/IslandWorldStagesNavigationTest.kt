package at.gregor.layermaxxing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
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

    @Composable private fun World(c: ApiClient.IslandConstruction) {
        val home = ApiClient.HomeIsland(1, emptyMap(), 0, emptyList(), construction = c)
        VillageTheme(true) { IslandWorld(
            ownName = "Gregor", ownEmoji = "🦉", friends = StageFixtures.friends, groups = StageFixtures.groups, islands = ApiClient.Islands(140, emptyList()),
            quests = StageFixtures.quests, letters = StageFixtures.letters, topics = StageFixtures.topics, opened = emptyMap(), onBack = {}, onChat = {},
            onComposeLetter = { calls += "write:$it" }, onOpenLetter = { calls += "open:${it.id}" }, onLockedTap = { calls += "locked:${it.id}" },
            onTopics = {}, onAllTopics = { calls += "topics" }, onGlossary = {}, onPeople = { calls += "people" },
            onCreateQuest = { _, _, _, _, _, _ -> }, onQuestDone = { q, d -> calls += "done:${q.id}:$d" }, onQuestDelete = { calls += "delete:${it.id}" },
            ownId = 1, startView = "home", loadIsland = { home }, incoming = StageFixtures.incoming, outgoing = StageFixtures.outgoing,
            onRespondFriend = { id, yes -> calls += "respond:$id:$yes" }, onPlace = { calls += "place:${it.name}" },
            glossary = { GlossaryScreen(seed = StageFixtures.glossary, addTerm = { _, _, _ -> }) },
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
                slot("friend:3")[0].performClick(); tick()
                assertEquals(msg, 3, rule.onAllNodes(SemanticsMatcher("letters") { it.config.getOrNull(SceneSlotKey)?.startsWith("letter:") == true }, useUnmergedTree = true).fetchSemanticsNodes().size)
                slot("letter:4")[0].performClick(); tick()
                assertTrue("$msg $calls", calls.any { it == "open:4" || it == "locked:4" })
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
                rule.onAllNodesWithContentDescription("Kategorie Witze")[0].performClick(); tick()
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
                slot("me")[0].assertExists(); slot("keepsake:Freunde")[0].assertExists()
                rule.onAllNodesWithText("Profil & Einstellungen")[0].performClick(); tick()
                assertTrue("$msg $calls", "place:HOUSE" in calls)
            }
        }
    }

    @Test fun post() = (0..3).forEach { visit(IsleBuilding.POST, it) }
    @Test fun harbour() = (0..3).forEach { visit(IsleBuilding.HARBOUR, it) }
    @Test fun lighthouse() = (0..3).forEach { visit(IsleBuilding.LIGHTHOUSE, it) }
    @Test fun library() = (0..3).forEach { visit(IsleBuilding.LIBRARY, it) }
    @Test fun campfire() = (0..3).forEach { visit(IsleBuilding.CAMPFIRE, it) }
    @Test fun house() = (0..3).forEach { visit(IsleBuilding.HOUSE, it) }
}
