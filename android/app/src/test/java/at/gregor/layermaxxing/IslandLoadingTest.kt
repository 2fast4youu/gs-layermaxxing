package at.gregor.layermaxxing

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * Field bug 7.9.13: the own island hung on "Insel wird geladen…" for ever.
 *
 * Causes: (1) the user id came only from the status request, which shared one all-or-nothing refresh with 17 other
 * requests, so one failing endpoint left ownId null and no island was ever asked for; (2) a failed or missing island
 * answer waited 60 s before the next try, with no error, timeout or retry in sight; (3) nothing was kept between
 * visits, so every navigation started from the spinner again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class IslandLoadingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @After fun clean() = IslandCache.clear()

    private fun island(user: Long, land: Int, revision: Int = 1) =
        ApiClient.HomeIsland(user, emptyMap(), 40, emptyList(), construction = ApiClient.IslandConstruction(available = 40, land = land, revision = revision))

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

    private fun shown(text: String) = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun world(
        ownId: Long?, scope: String?, load: suspend (Long) -> ApiClient.HomeIsland?, errors: MutableList<String> = mutableListOf(),
        write: suspend (ApiClient.IslandConstruction, ApiClient.IslandRequest) -> ApiClient.HomeIsland? = { _, _ -> null },
    ) {
        rule.setContent {
            VillageTheme(true) {
                IslandWorld(
                    ownName = "Veit", ownEmoji = "🦊", friends = emptyList(), groups = emptyList(),
                    islands = ApiClient.Islands(40, emptyList()), quests = emptyList(), letters = emptyList(),
                    topics = emptyList(), opened = emptyMap(), onBack = {}, onChat = {}, onComposeLetter = {},
                    onOpenLetter = {}, onLockedTap = {}, onTopics = {}, onAllTopics = {}, onGlossary = {}, onPeople = {},
                    onCreateQuest = { _, _, _, _, _, _ -> }, onQuestDone = { _, _ -> }, onQuestDelete = {},
                    ownId = ownId, startView = "home", loadIsland = load, onError = { errors += it }, islandScope = scope,
                    changeConstruction = write,
                )
            }
        }
    }

    // ---- pure loading rules ----

    @Test fun anAnswerThatNeverComesEndsInATimeoutNotASpinner() = runBlocking {
        val result = IslandSync.attempt(timeoutMs = 50) { awaitCancellation() }
        assertTrue(result.exceptionOrNull() is IslandTimeout)
        assertTrue(IslandSync.message(result.exceptionOrNull()!!).contains("Zeitüberschreitung"))
    }

    @Test fun aMissingIslandIsAFailureNeverAnEmptyIsland() = runBlocking {
        val result = IslandSync.attempt { null }
        assertTrue(result.isFailure)
        assertEquals("Der Server hat keine Insel geliefert.", IslandSync.message(result.exceptionOrNull()!!))
    }

    @Test fun cancellationOfTheCallerIsPassedOn() {
        var passedOn = false
        runBlocking {
            try { IslandSync.attempt { throw CancellationException("screen left") } } catch (e: CancellationException) { passedOn = true }
        }
        assertTrue("CancellationException must not be swallowed", passedOn)
    }

    @Test fun retriesAreShortAndBoundedThenCalm() {
        assertEquals(2_000L, IslandSync.retryDelay(1))
        assertEquals(20_000L, IslandSync.retryDelay(4))
        assertEquals(IslandSync.REFRESH_MS, IslandSync.retryDelay(9))
        assertTrue("the first retry is never the old 60 s pause", IslandSync.retryDelay(1) < 60_000L)
    }

    @Test fun theSameUserIdOnTwoServersNeverSharesAnIsland() {
        val gregor = IslandOwner(IslandCache.scope("gregor_test", "https://a.invalid/", "veit"), 1)
        val gerfried = IslandOwner(IslandCache.scope("gerfried", "https://b.invalid/", "veit"), 1)
        IslandCache.put(gregor, island(1, land = 4))
        assertNull(IslandCache.get(gerfried))
        // An answer for another user is never filed under this owner.
        IslandCache.put(gerfried, island(2, land = 2))
        assertNull(IslandCache.get(gerfried))
        IslandCache.forget(gregor.scope)
        assertNull("logout forgets the account's island", IslandCache.get(gregor))
    }

    // ---- the island screen ----

    @Test fun aFailedFirstLoadSaysSoOffersRetryAndRecovers() {
        val calls = AtomicInteger()
        rule.mainClock.autoAdvance = false
        world(1, "s|u|veit", { id ->
            if (calls.incrementAndGet() == 1) throw ApiException(0, "[LM-NET-ROUTE] Server nicht erreichbar.")
            island(id, land = 3)
        })
        assertTrue("a retry appears after the failure", awaitAny { rule.onAllNodesWithContentDescription("Insel erneut laden").fetchSemanticsNodes().isNotEmpty() })
        assertTrue("an honest empty state with the error", shown(IslandSync.NOT_LOADED) && shown("Offline"))
        assertFalse("no endless 'wird geladen' after a failure", shown("Insel wird geladen…"))
        assertFalse("no island data invented", shown("Landstufe"))
        rule.onAllNodesWithContentDescription("Insel erneut laden")[0].performClick()
        assertTrue("retry loads the real island", awaitAny { shown("Landstufe 3") })
        assertEquals(2, calls.get())
    }

    @Test fun theLastRealIslandShowsAtOnceWhileTheServerDoesNotAnswer() {
        val scope = "s|u|veit"
        IslandCache.put(IslandOwner(scope, 1), island(1, land = 4))
        val errors = mutableListOf<String>()
        rule.mainClock.autoAdvance = false
        world(1, scope, { awaitCancellation() }, errors)
        assertTrue("cached island visible immediately", awaitAny { shown("Landstufe 4") })
        assertFalse(shown(IslandSync.NOT_LOADED))
        assertTrue("background refresh is a small hint only", awaitAny { shown("Aktualisiert") })
        assertTrue("no error toasts while waiting", errors.isEmpty())
    }

    @Test fun anotherServersUserOneNeverSeesThisIsland() {
        IslandCache.put(IslandOwner("gregor|veit", 1), island(1, land = 5))
        rule.mainClock.autoAdvance = false
        world(1, "gerfried|veit", { awaitCancellation() })
        assertTrue(awaitAny { shown(IslandSync.NOT_LOADED) })
        assertFalse("the other server's island must not appear", shown("Landstufe 5"))
    }

    @Test fun aLateAnswerForThePreviousOwnerIsDropped() {
        var owner by mutableStateOf<Long?>(1)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            VillageTheme(true) {
                IslandWorld(
                    ownName = "Veit", ownEmoji = "🦊", friends = emptyList(), groups = emptyList(),
                    islands = ApiClient.Islands(40, emptyList()), quests = emptyList(), letters = emptyList(),
                    topics = emptyList(), opened = emptyMap(), onBack = {}, onChat = {}, onComposeLetter = {},
                    onOpenLetter = {}, onLockedTap = {}, onTopics = {}, onAllTopics = {}, onGlossary = {}, onPeople = {},
                    onCreateQuest = { _, _, _, _, _, _ -> }, onQuestDone = { _, _ -> }, onQuestDelete = {},
                    ownId = owner, startView = "home", islandScope = "s|u|${owner}",
                    // user 1's server answers slowly with user 1; user 2 gets theirs at once
                    loadIsland = { id -> if (id == 1L) { kotlinx.coroutines.delay(300); island(1, land = 5) } else island(2, land = 2) },
                )
            }
        }
        owner = 2
        assertTrue(awaitAny { shown("Landstufe 2") })
        repeat(30) { rule.mainClock.advanceTimeByFrame(); Thread.sleep(15) }
        assertFalse("user 1's late island must never replace user 2's", shown("Landstufe 5"))
    }

    @Test fun withoutAConfirmedStateNothingCanBeBuilt() {
        val scope = "s|u|veit"
        IslandCache.put(IslandOwner(scope, 1), island(1, land = 3))
        val errors = mutableListOf<String>()
        val writes = AtomicInteger()
        rule.mainClock.autoAdvance = false
        world(1, scope, { awaitCancellation() }, errors, write = { _, _ -> writes.incrementAndGet(); null })
        assertTrue(awaitAny { rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen").fetchSemanticsNodes().isNotEmpty() })
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].performClick()
        // Deterministic: the house card must be there (no house stands on this cached island) and is really tapped.
        assertTrue("the build sheet offers the house", awaitAny { rule.onAllNodesWithContentDescription("Mein Haus platzieren").fetchSemanticsNodes().isNotEmpty() })
        rule.onAllNodesWithContentDescription("Mein Haus platzieren")[0].performClick()
        assertTrue("a write before the fresh state is refused with a reason", awaitAny { IslandSync.NOT_CONFIRMED in errors })
        assertEquals("refused exactly once", 1, errors.count { it == IslandSync.NOT_CONFIRMED })
        assertTrue("no placement grid opened", rule.onAllNodesWithContentDescription("Bauplatzraster").fetchSemanticsNodes().isEmpty())
        assertEquals("nothing was sent to the server", 0, writes.get())
    }

    // ---- 7.9.13 A: the last real island survives a process restart ----

    private class MemoryDisk : IslandDisk {
        val map = linkedMapOf<String, String>()
        override fun read(key: String) = map[key]
        override fun write(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
        override fun keys(): Set<String> = map.keys.toSet()
    }

    private fun richIsland() = ApiClient.HomeIsland(
        7, mapOf(2 to "flowers"), 55, listOf(ApiClient.DecorItem("flowers", 10, true)),
        places = mapOf(1 to ApiClient.LifePlace("work", "Werkstatt")), plots = 4, plotUnlocks = listOf(0, 20),
        here = ApiClient.Here(null, "am Strand"),
        construction = ApiClient.IslandConstruction(
            mode = "normal", buildings = mapOf("HOUSE" to 2, "HARBOUR" to 1), land = 3, spent = 120, revision = 9, available = 33, ageDays = 12,
            positions = mapOf("HOUSE" to listOf(9, 10), "HARBOUR" to listOf(3, 15)),
            decor = listOf(ApiClient.IslandDecor(4, "lantern", listOf(11, 12), null, ""), ApiClient.IslandDecor(5, "summit", listOf(8, 8), 2, "Coden am Berg")),
            starters = setOf("POST", "LIBRARY"), gridVersion = 6,
        ),
    )

    @Test fun theStoredIslandIsExactlyTheServerAnswerBack() {
        val owner = IslandOwner("gerfried|https://x.invalid/test-gerfried/|veit", 7)
        val island = richIsland()
        assertEquals(island, IslandCodec.decode(IslandCodec.encode(owner, island), owner))
        // A server without construction block stays exactly that (not supported, legacy rule), never a newer rule.
        val bare = ApiClient.HomeIsland(7, emptyMap(), 0, emptyList(), construction = ApiClient.IslandConstruction(supported = false, gridVersion = PlacementRule.LEGACY))
        assertEquals(bare, IslandCodec.decode(IslandCodec.encode(owner, bare), owner))
        val legacy = island.copy(construction = island.construction.copy(gridVersion = PlacementRule.LEGACY))
        assertEquals(PlacementRule.LEGACY, IslandCodec.decode(IslandCodec.encode(owner, legacy), owner)!!.construction.gridVersion)
        // Another scope, another user, a broken or older entry are never read as this owner's island.
        assertNull(IslandCodec.decode(IslandCodec.encode(owner, island), owner.copy(scope = "gerfried|https://x.invalid/|veit")))
        assertNull(IslandCodec.decode(IslandCodec.encode(owner, island), owner.copy(userId = 8)))
        assertNull(IslandCodec.decode("{not json", owner))
        assertNull(IslandCodec.decode(IslandCodec.encode(owner, island).replace("\"v\":1", "\"v\":0"), owner))
    }

    @Test fun afterAProcessRestartTheLastRealIslandShowsAtOnce() {
        val disk = MemoryDisk()
        IslandCache.disk = disk
        try {
            val scope = "gerfried|https://x.invalid/test-gerfried/|veit"
            IslandCache.put(IslandOwner(scope, 1), island(1, land = 4, revision = 3))
            IslandCache.dropMemory() // the process died; only the device storage is left
            val errors = mutableListOf<String>()
            rule.mainClock.autoAdvance = false
            world(1, scope, { awaitCancellation() }, errors)
            assertTrue("the stored island is visible before any answer", awaitAny { shown("Landstufe 4") })
            assertFalse(shown(IslandSync.NOT_LOADED))
            assertTrue(errors.isEmpty())
            // The same user id under another base path is another server: nothing from here.
            IslandCache.dropMemory()
            assertNull(IslandCache.get(IslandOwner("gerfried|https://x.invalid/|veit", 1)))
        } finally { IslandCache.clear(); IslandCache.disk = null }
    }

    @Test fun logoutAndRemovingAnAccountDeleteTheStoredIslandAndNoTokenIsStored() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        try {
            val store = SessionStore(context)
            store.name = "veit"; store.token = "secret-token-123"
            val mine = IslandOwner(store.islandScope(), 1)
            val other = IslandOwner(store.islandScope("lena"), 2)
            IslandCache.put(mine, island(1, land = 4))
            IslandCache.put(other, island(2, land = 2))
            val files = context.getSharedPreferences("layermaxxing_islands", android.content.Context.MODE_PRIVATE)
            assertTrue("one entry per owner", files.all.size == 2)
            assertFalse("no token in the island storage", files.all.values.any { it.toString().contains("secret-token-123") })
            // Restart: a fresh store on the same device reads it back.
            IslandCache.dropMemory()
            val restarted = SessionStore(context)
            assertEquals(4, IslandCache.get(IslandOwner(restarted.islandScope(), 1))!!.construction.land)
            restarted.removeAccount("lena")
            IslandCache.dropMemory()
            assertNull("a removed account's island is gone", IslandCache.get(other))
            assertEquals(4, IslandCache.get(mine)!!.construction.land)
            restarted.clear()
            IslandCache.dropMemory()
            assertNull("logout deletes the own island", IslandCache.get(mine))
            assertTrue(files.all.isEmpty())
        } finally { IslandCache.clear(); IslandCache.disk = null }
    }
}
