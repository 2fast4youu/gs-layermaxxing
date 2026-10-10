package at.gregor.layermaxxing

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Field bug 7.9.3: after switching creative mode on, the own island went dead -
 * no Bauen/Menü, no tappable place, everything looked deleted.
 *
 * Cause: the island loader asked for `?mode=creative`, but a server that answers
 * without a `construction` block (Gregor's test server has no island_construction
 * yet) or with another ledger mode came back as `mode = "normal"`. IslandWorld
 * discarded every response whose mode differed from the local switch, so `home`
 * stayed null forever: the empty fallback island has no touch targets and the
 * action row only appears with a loaded island.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class CreativeIslandRegressionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    /** Exactly what a server without island construction returns for GET /api/island/{id}?mode=creative. */
    private fun legacyServer(): ApiClient {
        val island = JSONObject()
            .put("user_id", 1).put("decor", JSONObject()).put("score", 40)
            .put("items", org.json.JSONArray())
            .put("places", JSONObject().put("0", JSONObject().put("kind", "home").put("name", "Altbau")))
            .put("plots", 3).put("plot_unlocks", org.json.JSONArray(listOf(0, 0, 0, 40)))
            .put("here", JSONObject.NULL)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(island.toString().toResponseBody("application/json".toMediaType())).build()
        }.build()
        return ApiClient("https://fixtures.invalid/", client)
    }

    private fun awaitAny(timeoutMs: Long = 8_000, found: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            rule.mainClock.advanceTimeByFrame()
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if (found()) return true
            Thread.sleep(25)
        }
        return false
    }

    @Test fun creativeModeKeepsTheRealIslandLoadedAndTappable() {
        val api = legacyServer()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            VillageTheme(true) {
                IslandWorld(
                    ownName = "Veit", ownEmoji = "🦊", friends = emptyList(), groups = emptyList(),
                    islands = ApiClient.Islands(40, emptyList()), quests = emptyList(), letters = emptyList(),
                    topics = emptyList(), opened = emptyMap(), onBack = {}, onChat = {}, onComposeLetter = {},
                    onOpenLetter = {}, onLockedTap = {}, onTopics = {}, onAllTopics = {}, onGlossary = {}, onPeople = {},
                    onCreateQuest = { _, _, _, _, _, _ -> }, onQuestDone = { _, _ -> }, onQuestDelete = {},
                    ownId = 1, startView = "home", creativeActive = true,
                    loadIsland = { id -> api.island("token", id, "creative") },
                )
            }
        }
        assertTrue("Build action must appear once the own island is loaded in creative mode",
            awaitAny { rule.onAllNodesWithText("Bauen").fetchSemanticsNodes().isNotEmpty() })
        // The real island (with its existing place) is shown, not an empty creative copy.
        assertTrue("The existing place must stay visible and tappable in creative mode",
            awaitAny { rule.onAllNodesWithContentDescription("Altbau").filter(hasClickAction()).fetchSemanticsNodes().isNotEmpty() })
    }

    /** Decoration is built from the shelf and placed freely: no fixed "+" slots, the server gets a position. */
    @Test fun decorationIsPlacedFreelyFromTheBuildShelf() {
        val requests = mutableListOf<ApiClient.IslandRequest>()
        var state = ApiClient.IslandConstruction(available = 200, ageDays = 21, land = 3,
            decor = listOf(ApiClient.IslandDecor(0, "flowers", listOf(8, 9), 0)))
        val items = IsleDecor.labels.keys.map { ApiClient.DecorItem(it, 0, true) }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            VillageTheme(true) {
                IslandWorld(
                    ownName = "Veit", ownEmoji = "🦊", friends = emptyList(), groups = emptyList(),
                    islands = ApiClient.Islands(200, emptyList()), quests = emptyList(), letters = emptyList(),
                    topics = emptyList(), opened = emptyMap(), onBack = {}, onChat = {}, onComposeLetter = {},
                    onOpenLetter = {}, onLockedTap = {}, onTopics = {}, onAllTopics = {}, onGlossary = {}, onPeople = {},
                    onCreateQuest = { _, _, _, _, _, _ -> }, onQuestDone = { _, _ -> }, onQuestDelete = {},
                    ownId = 1, startView = "home",
                    loadIsland = { ApiClient.HomeIsland(1, mapOf(0 to "flowers"), 200, items, construction = state) },
                    changeConstruction = { c, req ->
                        requests += req
                        state = c.copy(revision = c.revision + 1, decor = c.decor + ApiClient.IslandDecor(100, req.item!!, req.position!!))
                        ApiClient.HomeIsland(1, emptyMap(), 200, items, construction = state)
                    },
                )
            }
        }
        assertTrue(awaitAny { rule.onAllNodesWithText("Bauen").fetchSemanticsNodes().isNotEmpty() })
        // The migrated slot decoration is an ordinary tappable object now.
        assertTrue(awaitAny { rule.onAllNodesWithContentDescription("Blumen").filter(hasClickAction()).fetchSemanticsNodes().isNotEmpty() })
        rule.onAllNodesWithText("Bearbeiten")[0].performClick()
        repeat(20) { rule.mainClock.advanceTimeByFrame() }
        assertTrue("fixed decoration slots are gone", rule.onAllNodesWithContentDescription("Deko setzen").fetchSemanticsNodes().isEmpty())
        rule.onAllNodesWithText("Fertig")[0].performClick()
        rule.onAllNodesWithText("Bauen")[0].performClick()
        repeat(20) { rule.mainClock.advanceTimeByFrame() }
        rule.onAllNodesWithText("Deko")[0].performClick()
        repeat(20) { rule.mainClock.advanceTimeByFrame() }
        rule.onAllNodesWithContentDescription("Laterne platzieren")[0].performClick()
        repeat(20) { rule.mainClock.advanceTimeByFrame() }
        assertTrue(rule.onAllNodesWithContentDescription("Bauplatzraster").fetchSemanticsNodes().isNotEmpty())
        rule.onAllNodesWithText("Bestätigen")[0].performClick()
        assertTrue(awaitAny { requests.isNotEmpty() })
        val sent = requests.single()
        org.junit.Assert.assertEquals("decor_place", sent.action)
        org.junit.Assert.assertEquals("lantern", sent.item)
        assertTrue("a free position on the island", sent.position != null && IslandLayout.validDecor(state.copy(decor = state.decor.filter { it.id != 100 }), sent.position!!))
        assertTrue(awaitAny { rule.onAllNodesWithContentDescription("Laterne").filter(hasClickAction()).fetchSemanticsNodes().isNotEmpty() })
    }
}
