package at.gregor.layermaxxing

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 7.9.14 on the real archipelago map (Robolectric, JVM simulation, not a device; synthetic friends and letters only):
 * a full map of 12 v6 islands with 3 letter boats each opens with every island, route and boat, the painted terrain
 * under all 13 islands and the boats are decoded once (shared), not once per island or boat.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class IslandMapPerfUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val now = System.currentTimeMillis() / 1000

    private fun construction(land: Int, stage: Int) = ApiClient.IslandConstruction(land = land, gridVersion = PlacementRule.GROUND,
        buildings = mapOf("HOUSE" to 1) + (if (stage > 0) mapOf("HARBOUR" to stage) else emptyMap()),
        starters = if (stage == 0) setOf("HARBOUR") else emptySet(),
        positions = mapOf("HOUSE" to listOf(8, 6), "HARBOUR" to IslandGround.harbourSpots(stage, land).let { it[it.size / 2] }))
    private val friends = (2L..13L).map { ApiClient.UserSummary(it, "Insel $it", "friends", "🌊", "#2E7D32") }
    private val infos = friends.mapIndexed { i, f ->
        ApiClient.IslandInfo(f.id, 10, 0, 40 + i * 20, 1 + i % 4, null, ApiClient.HomeIsland(f.id, emptyMap(), 10, emptyList(), construction = construction(i % 6, i % 4)))
    }
    private val modes = listOf("timed", "mutual", "random")
    private val letters = friends.flatMap { f -> (0 until 3).map { k ->
        val id = f.id * 10 + k
        ApiClient.Message(id, f.id, f.name, k == 1, "Brief $id", "", "none", now - 2 * 86_400, modes[k], now + (k + 1) * 86_400L,
            if (modes[k] == "random") now else null, if (modes[k] == "random") now + 4 * 86_400 else null, false, false, null, false, false, null, null, null, emptyList(), null, null, null)
    } }

    private val islands = androidx.compose.runtime.mutableStateOf(ApiClient.Islands(10, infos))

    @Composable private fun World() {
        VillageTheme(true) { IslandWorld(
            ownName = "Ich", ownEmoji = "🦉", friends = friends, groups = emptyList(), islands = islands.value,
            quests = emptyList(), letters = letters, topics = emptyList(), opened = emptyMap(), onBack = {}, onChat = {}, onComposeLetter = {},
            onOpenLetter = {}, onLockedTap = {}, onTopics = {}, onAllTopics = {}, onGlossary = {}, onPeople = {}, onCreateQuest = { _, _, _, _, _, _ -> },
            onQuestDone = { _, _ -> }, onQuestDelete = {}, ownId = 1, startView = "map",
            loadIsland = { ApiClient.HomeIsland(1, emptyMap(), 0, emptyList(), construction = construction(5, 2)) },
        ) }
    }

    @Test fun aFullMapOpensWithEveryBoatAndDecodesItsPicturesOnce() {
        val before = IslandBitmaps.decodes
        val t0 = System.nanoTime()
        rule.setContent { World() }
        rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        println("PERF ui: full map (12 islands, 36 letters) composed in ${(System.nanoTime() - t0) / 1_000_000} ms (Robolectric)")
        val routes = rule.onAllNodes(SemanticsMatcher("map routes") { it.config.getOrNull(MapRoutesKey) != null })[0].fetchSemanticsNode().config[MapRoutesKey]
        // every friend with a water way has its route; no boat is hidden: each letter at sea is a boat or counted in "+N"
        val boats = routes.filter { it.letterId != null }
        val friendsWithRoute = routes.filter { it.letterId == null }.map { it.friendId }.toSet()
        val atSea = letters.count { it.peerId in friendsWithRoute }
        val plus = rule.onAllNodes(SemanticsMatcher("+N") { n ->
            n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.any { it.text.startsWith("+") } == true
        }).fetchSemanticsNodes().sumOf { n -> n.config[androidx.compose.ui.semantics.SemanticsProperties.Text].first().text.drop(1).toInt() }
        assertTrue("routes for ${friendsWithRoute.size} friends", friendsWithRoute.size >= 10)
        assertEquals("boats ${boats.size} + waiting $plus", atSea, boats.size + plus)
        // the terrain under 13 islands and three boat types: at most one decode each
        assertTrue(IslandBitmaps.cached(R.drawable.terrain_home))
        assertTrue("decodes ${IslandBitmaps.decodes - before}", IslandBitmaps.decodes - before <= 4)
    }

    /**
     * The water ways depend only on islands and berths: the minute tick of the boats and a poll answer with the same
     * islands (new objects, same content) never plan a route or a lane again (no exact clearance probe at all).
     */
    @Test fun theMinuteTickAndAnUnchangedPollNeverPlanRoutesAgain() {
        rule.setContent { World() }
        rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        val settled = SeaChart.probes
        islands.value = ApiClient.Islands(10, infos.map { it.copy(island = it.island?.copy(construction = it.island!!.construction.copy())) })
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(61_000); rule.waitForIdle()
        assertEquals("exact probes after the tick and the poll", settled, SeaChart.probes)
    }
}
