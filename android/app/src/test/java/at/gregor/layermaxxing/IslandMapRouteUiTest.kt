package at.gregor.layermaxxing

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
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
 * 7.9.12 on the real archipelago map of IslandWorld (Robolectric, JVM simulation, not a device): every route (and the
 * lane of every boat) starts at MY island's berth and ends at the friend's berth, computed from the current
 * constructions; a friend without a harbour ends at the visible landing stage; a harbour moved on another device is
 * the new end after the next poll, without a restart; a boat at full progress lies at the berth, a waiting letter in
 * the roadstead of its sender, an arrived (opened) letter is no boat any more.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class IslandMapRouteUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val now = System.currentTimeMillis() / 1000

    private fun construction(harbour: List<Int>?, land: Int = 5) = ApiClient.IslandConstruction(land = land,
        buildings = mapOf("HOUSE" to 1) + (if (harbour != null) mapOf("HARBOUR" to 2) else emptyMap()),
        positions = mapOf("HOUSE" to listOf(8, 6)) + (harbour?.let { mapOf("HARBOUR" to it) } ?: emptyMap()))
    private var homeHarbour = listOf(8, 15)
    private var friends = listOf(ApiClient.UserSummary(2, "Gerfried", "friends", "🦊", "#2E7D32"), ApiClient.UserSummary(3, "Anna", "friends", "🐻", "#1565C0"))
    private var infos = listOf(
        ApiClient.IslandInfo(2, 10, 0, 10, 2, 40, ApiClient.HomeIsland(2, emptyMap(), 10, emptyList(), construction = construction(listOf(12, 15), land = 3))),
        // Anna's island has no harbour (old data): her routes end at the visible landing stage
        ApiClient.IslandInfo(3, 10, 0, 10, 1, 40, ApiClient.HomeIsland(3, emptyMap(), 10, emptyList(), construction = construction(null, land = 1))),
    )
    private var letters = listOf(
        // to Gerfried: sent two days ago, arrives in two days (half way)
        letter(11, 2, "Gerfried", incoming = false, mode = "timed", createdAt = now - 2 * 86_400, releaseAt = now + 2 * 86_400),
        // from Gerfried: its trip is over (full progress) but not opened yet: at MY berth
        letter(12, 2, "Gerfried", incoming = true, mode = "timed", createdAt = now - 4 * 86_400, releaseAt = now - 60),
        // to Anna: waits for both approvals: in my roadstead
        letter(13, 3, "Anna", incoming = false, mode = "mutual", createdAt = now - 86_400),
        // opened: not at sea any more
        letter(14, 3, "Anna", incoming = true, mode = "timed", unlocked = true, createdAt = now - 9 * 86_400, releaseAt = now - 86_400),
    )

    @Composable private fun World() {
        VillageTheme(true) { IslandWorld(
            ownName = "Gregor", ownEmoji = "🦉", friends = friends, groups = emptyList(), islands = ApiClient.Islands(10, infos),
            quests = emptyList(), letters = letters, topics = emptyList(), opened = emptyMap(), onBack = {}, onChat = {}, onComposeLetter = {},
            onOpenLetter = {}, onLockedTap = {}, onTopics = {}, onAllTopics = {}, onGlossary = {}, onPeople = {}, onCreateQuest = { _, _, _, _, _, _ -> },
            onQuestDone = { _, _ -> }, onQuestDelete = {}, ownId = 1, startView = "map",
            loadIsland = { ApiClient.HomeIsland(1, emptyMap(), 0, emptyList(), construction = construction(homeHarbour)) },
        ) }
    }
    private fun tick(ms: Long = 800) { rule.mainClock.advanceTimeBy(ms); rule.waitForIdle() }
    private fun node() = rule.onAllNodes(SemanticsMatcher("map routes") { it.config.getOrNull(MapRoutesKey) != null })[0].fetchSemanticsNode().config
    private fun berths() = node()[MapBerthsKey].toMap()
    private fun routes() = node()[MapRoutesKey]
    private fun close(a: Offset, b: Offset, tol: Float = .5f) = (a - b).getDistance() <= tol

    @Test fun everyRouteAndBoatRunsFromMyHarbourToTheFriendsHarbour() {
        rule.setContent { World() }
        tick(1500)
        val b = berths()
        for (r in routes()) {
            assertTrue("${r.friendId} ${r.letterId} starts at my berth", close(r.path.first(), b.getValue(0L)))
            assertTrue("${r.friendId} ${r.letterId} ends at the friend's berth", close(r.path.last(), b.getValue(r.friendId)))
        }
        val boats = routes().filter { it.letterId != null }.associateBy { it.letterId }
        // the opened letter is no boat; the others are on their lanes
        assertEquals(setOf(11L, 12L, 13L), boats.keys)
        val half = boats.getValue(11L)
        assertTrue(close(half.boat!!, HarbourRoute.at(half.path, .5f), 6f))
        // full progress: at my berth (incoming), not 86 % of the way
        assertTrue(close(boats.getValue(12L).boat!!, b.getValue(0L), 1f))
        // waiting: in my roadstead, far from Anna's landing stage
        val wait = boats.getValue(13L)
        assertTrue((wait.boat!! - b.getValue(0L)).getDistance() < (wait.boat - b.getValue(3L)).getDistance())
        assertTrue(!close(wait.boat, b.getValue(3L), 20f))
        // Anna's island has no harbour: her end is the visible landing stage below her island (in the water)
        val annaRoute = routes().first { it.friendId == 3L && it.letterId == null }
        assertTrue(close(annaRoute.path.last(), b.getValue(3L)))
    }

    @Test fun aHarbourMovedMeanwhileIsTheNewEndAfterTheNextPollWithoutARestart() {
        rule.setContent { World() }
        tick(1500)
        val before = berths().getValue(0L)
        homeHarbour = listOf(12, 15) // moved 4 cells to the east (e.g. on another device)
        tick(61_000)
        val after = berths().getValue(0L)
        val density = rule.activity.resources.displayMetrics.density
        val expected = 4 * IslandGrid.step(5) * 180f * density
        assertEquals(expected, after.x - before.x, 1f)
        assertEquals(before.y, after.y, .5f)
        routes().forEach { assertTrue(close(it.path.first(), after)) }
    }
    /**
     * The real case of the 7.9.12 review map: with Lena (west of the hub, two boats on her route) a boat sat half
     * outside the map's left edge. Every boat's drawn hull (84 × 42 dp box, its badge and "+N" inside) lies completely
     * inside the map area; routes still run berth to berth.
     */
    @Test fun withLenaEveryBoatHullLiesInsideTheMap() {
        friends = friends + ApiClient.UserSummary(4, "Lena", "friends", "🦉", "#EF6C00")
        infos = infos + ApiClient.IslandInfo(4, 10, 0, 10, 3, 120, ApiClient.HomeIsland(4, emptyMap(), 10, emptyList(), construction = construction(listOf(6, 14), land = 4)))
        letters = letters.filter { it.id != 14L } + listOf(
            letter(15, 4, "Lena", incoming = true, mode = "timed", createdAt = now - 86_400, releaseAt = now + 3 * 86_400),
            letter(16, 4, "Lena", incoming = false, mode = "timed", createdAt = now - 3 * 86_400, releaseAt = now + 86_400),
        )
        rule.setContent { World() }
        tick(1500)
        val area = node()[MapAreaKey]
        val boats = routes().filter { it.letterId != null }
        assertTrue(boats.any { it.friendId == 4L })
        for (b in boats) {
            val hull = b.hull!!
            assertTrue("boat ${b.letterId} hull $hull outside $area",
                hull.left >= area.left - .5f && hull.top >= area.top - .5f && hull.right <= area.right + .5f && hull.bottom <= area.bottom + .5f)
            assertTrue(close(b.boat!!, hull.center, 1f))
        }
        val b = berths()
        for (r in routes()) { assertTrue(close(r.path.first(), b.getValue(0L))); assertTrue(close(r.path.last(), b.getValue(r.friendId))) }
    }
}
