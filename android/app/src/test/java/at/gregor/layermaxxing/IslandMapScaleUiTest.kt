package at.gregor.layermaxxing

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Production IslandWorld, synthetic mail, native JVM renderer; no account, DB or handset. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class IslandMapScaleUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun zeroFriendsWith120Letters() = exercise(0)
    @Test fun oneFriendWith120Letters() = exercise(1)
    @Test fun twelveFriendsWith120Letters() = exercise(12)
    @Test fun twentyFriendsWith120Letters() = exercise(20)
    @Test fun fiftyFriendsWith120Letters() = exercise(50)

    private fun construction(land: Int) = ApiClient.IslandConstruction(
        land = land, gridVersion = PlacementRule.GROUND, buildings = mapOf("HOUSE" to 1, "HARBOUR" to 2),
        positions = mapOf("HOUSE" to listOf(8, 6), "HARBOUR" to IslandGround.harbourSpots(2, land).let { it[it.size / 2] }))

    private fun exercise(n: Int) {
        // Native capture and sheet scrolling use explicit frames, not an unbounded auto-advance loop.
        rule.mainClock.autoAdvance = false
        val now = System.currentTimeMillis() / 1000
        val friends = (0 until n).map { ApiClient.UserSummary(100L + it, "Freund %02d".format(it), "friends", "🌊", "#2E7D32") }
        val infos = friends.mapIndexed { i, f -> ApiClient.IslandInfo(f.id, 10, 0, 100, 2, 300,
            ApiClient.HomeIsland(f.id, emptyMap(), 10, emptyList(), construction = construction(i % 6))) }
        // Exact seed shape: 100 incoming + 20 outgoing. Dense timed/manual/mutual/random and arrived mail.
        val letters = (0 until 120).map { k ->
            val peer = friends.getOrNull(if (n == 0) 0 else k % n)
            val mode = listOf("timed", "manual", "mutual", "random")[(k % n.coerceAtLeast(1) + k / n.coerceAtLeast(1)) % 4]
            ApiClient.Message(1000L + k, peer?.id ?: 999, peer?.name ?: "Anderer Kontakt", k < 100,
                "Brief $k", "", "none", now - 172800, mode, now + 86400,
                if (mode == "random") now else null, if (mode == "random") now + 345600 else null,
                (k / n.coerceAtLeast(1)) % 5 == 0, false, null, false, false, null, null, null, emptyList(), null, null, null)
        }
        val data = mutableStateOf(ApiClient.Islands(10, infos))
        val active = mutableStateOf(true)
        var chosen: Long? = null
        rule.setContent {
            val holder = rememberSaveableStateHolder()
            if (active.value) holder.SaveableStateProvider("isles_synthetic_owner") {
                VillageTheme(true) { IslandWorld(
                    ownName = "Ich", ownEmoji = "🦉", friends = friends, groups = emptyList(), islands = data.value,
                    quests = emptyList(), letters = letters, topics = emptyList(), opened = emptyMap(),
                    onBack = {}, onChat = { chosen = it }, onComposeLetter = {}, onOpenLetter = {}, onLockedTap = {},
                    onTopics = {}, onAllTopics = {}, onGlossary = {}, onPeople = {}, onCreateQuest = { _, _, _, _, _, _ -> },
                    onQuestDone = { _, _ -> }, onQuestDelete = {}, ownId = 1,
                    loadIsland = { ApiClient.HomeIsland(1, emptyMap(), 0, emptyList(), construction = construction(5)) },
                ) }
            }
        }
        fun settle() {
            repeat(100) {
                androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
                rule.mainClock.advanceTimeByFrame()
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
            }
            rule.waitForIdle()
        }
        fun config() = rule.onAllNodes(SemanticsMatcher("map page") { it.config.getOrNull(MapPageFriendsKey) != null })[0].fetchSemanticsNode().config
        settle()
        if (n == 20) System.getenv("ISLAND_CRASH_SHOTS")?.let { folder ->
            val output = java.io.File(folder).apply { mkdirs() }
            val view = rule.activity.window.decorView
            val image = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            rule.runOnIdle { view.draw(android.graphics.Canvas(image)) }
            java.io.File(output, "islands-20-friends-120-letters.png").outputStream().use { stream ->
                image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            }
            image.recycle()
        }
        val seen = mutableListOf<Long>()
        repeat(IslandMapPages.count(n)) { page ->
            val c = config()
            val ids = c[MapPageFriendsKey]
            assertEquals(friends.drop(page * 6).take(6).map { it.id }, ids)
            assertTrue(c[MapCoastsKey].size <= 7)
            assertEquals(ids.toSet(), c[MapBerthsKey].map { it.first }.filter { it != 0L }.toSet())
            c[MapRoutesKey].forEach { route ->
                assertTrue(route.friendId in ids)
                route.letterId?.let { id ->
                    val letter = letters.single { it.id == id }
                    assertEquals(route.friendId, letter.peerId)
                    assertFalse(letter.unlocked)
                }
            }
            val routes = c[MapRoutesKey]
            val routedPeers = routes.filter { it.letterId == null }.map { it.friendId }.toSet()
            val plus = rule.onAllNodes(SemanticsMatcher("waiting boats") { node ->
                node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.any { it.text.startsWith("+") } == true
            }).fetchSemanticsNodes().sumOf { node ->
                node.config[androidx.compose.ui.semantics.SemanticsProperties.Text].first().text.drop(1).toInt()
            }
            assertEquals("every locked letter on a navigable page route is a boat or counted in +N",
                letters.count { !it.unlocked && it.peerId in routedPeers }, routes.count { it.letterId != null } + plus)
            seen += ids
            val probes = SeaChart.probes
            rule.runOnIdle { data.value = data.value.copy(friends = infos.map { it.copy() }) }
            rule.mainClock.advanceTimeBy(61000); rule.waitForIdle()
            assertEquals("no route planning on unchanged poll/minute tick", probes, SeaChart.probes)
            if (page + 1 < IslandMapPages.count(n)) { rule.onNodeWithTag("IslandMapNext").performClick(); settle() }
        }
        assertEquals(friends.map { it.id }, seen)
        // Same SaveableStateHolder pattern as LayerHome's tab: last page survives leaving and reentering.
        val last = config()[MapPageFriendsKey]
        rule.runOnIdle { active.value = false }; settle()
        rule.onNodeWithTag("IslandMap").assertDoesNotExist()
        rule.runOnIdle { active.value = true }; settle()
        assertEquals(last, config()[MapPageFriendsKey])
        if (n > 0) {
            // The lazy all-friends navigator reaches a peer outside page one. Its entire mail is passed to FriendSheet.
            val peer = friends.last()
            if (n > IslandMapPages.SIZE) {
                rule.onNodeWithTag("IslandMapPrevious").performClick(); settle()
                assertFalse(peer.id in config()[MapPageFriendsKey])
            }
            rule.onNodeWithTag("IslandMapAllFriends").performClick(); settle()
            val peerNode = rule.onNodeWithTag("IslandMapFriend-${peer.id}")
            var swipes = 0
            while (runCatching { peerNode.assertIsDisplayed() }.isFailure && swipes < 20) {
                rule.onNodeWithTag("IslandMapFriendList").performTouchInput { swipeUp() }
                settle()
                swipes++
            }
            peerNode.assertIsDisplayed()
            peerNode.performClick(); settle()
            val sheet = rule.onAllNodes(SemanticsMatcher("peer mail") { it.config.getOrNull(MapPeerLettersKey) != null })[0].fetchSemanticsNode()
            assertEquals(letters.filter { it.peerId == peer.id }.map { it.id }, sheet.config[MapPeerLettersKey])
            rule.onNodeWithText("Chat", useUnmergedTree = true).performClick(); settle()
            assertEquals(peer.id, chosen)
            assertTrue(peer.id in config()[MapPageFriendsKey])
        }
    }
}
