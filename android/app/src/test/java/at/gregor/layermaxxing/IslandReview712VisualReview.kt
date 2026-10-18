package at.gregor.layermaxxing

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performTouchInput
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 7.9.12 opt-in renders for the human review (skipped unless VILLAGE_SHOTS is an output directory; filter
 * '*IslandReview712VisualReview*'): the archipelago routes between the real harbours, the own island with close
 * neighbours under the graphic masks, the harbour's and the post's targets while held, an old version-4 island whose
 * pictures overlap. Real Android windows drawn on Robolectric (JVM), not device screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class IslandReview712VisualReview {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val out = System.getenv("VILLAGE_SHOTS")?.let(::File)
    private val now = System.currentTimeMillis() / 1000

    private fun save(name: String) {
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        out!!.mkdirs()
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun tick(ms: Long = 900) { rule.mainClock.advanceTimeBy(ms); rule.waitForIdle() }

    private fun construction(harbour: List<Int>?, land: Int = 5, level: Int = 2, spots: Map<String, List<Int>> = mapOf("HOUSE" to listOf(8, 6))) =
        ApiClient.IslandConstruction(land = land, mode = "creative", available = 500, ageDays = 30,
            buildings = spots.keys.associateWith { level } + (if (harbour != null) mapOf("HARBOUR" to level) else emptyMap()),
            positions = spots + (harbour?.let { mapOf("HARBOUR" to it) } ?: emptyMap()))

    @Composable private fun World(home: ApiClient.IslandConstruction, view: String) {
        val friends = listOf(ApiClient.UserSummary(2, "Gerfried", "friends", "🦊", "#2E7D32"), ApiClient.UserSummary(3, "Anna", "friends", "🐻", "#1565C0"),
            ApiClient.UserSummary(4, "Lena", "friends", "🦉", "#EF6C00"))
        val infos = listOf(
            ApiClient.IslandInfo(2, 10, 0, 10, 2, 40, ApiClient.HomeIsland(2, emptyMap(), 10, emptyList(), construction = construction(listOf(12, 15), land = 3))),
            ApiClient.IslandInfo(3, 10, 0, 10, 1, 40, ApiClient.HomeIsland(3, emptyMap(), 10, emptyList(), construction = construction(null, land = 1))),
            ApiClient.IslandInfo(4, 10, 0, 10, 3, 120, ApiClient.HomeIsland(4, emptyMap(), 10, emptyList(), construction = construction(listOf(6, 14), land = 4))),
        )
        val letters = listOf(
            letter(11, 2, "Gerfried", incoming = false, mode = "timed", createdAt = now - 2 * 86_400, releaseAt = now + 2 * 86_400),
            letter(12, 2, "Gerfried", incoming = true, mode = "timed", createdAt = now - 4 * 86_400, releaseAt = now - 60),
            letter(15, 4, "Lena", incoming = true, mode = "timed", createdAt = now - 86_400, releaseAt = now + 3 * 86_400),
            letter(16, 4, "Lena", incoming = false, mode = "timed", createdAt = now - 3 * 86_400, releaseAt = now + 86_400),
            letter(13, 3, "Anna", incoming = false, mode = "mutual", createdAt = now - 86_400),
        )
        VillageTheme(true) { IslandWorld(
            ownName = "Gregor", ownEmoji = "🦉", friends = friends, groups = emptyList(), islands = ApiClient.Islands(10, infos),
            quests = emptyList(), letters = letters, topics = emptyList(), opened = emptyMap(), onBack = {}, onChat = {}, onComposeLetter = {},
            onOpenLetter = {}, onLockedTap = {}, onTopics = {}, onAllTopics = {}, onGlossary = {}, onPeople = {}, onCreateQuest = { _, _, _, _, _, _ -> },
            onQuestDone = { _, _ -> }, onQuestDelete = {}, ownId = 1, startView = view, creativeActive = true,
            loadIsland = { ApiClient.HomeIsland(1, emptyMap(), 0, emptyList(), construction = home) },
        ) }
    }

    @Test fun mapRoutesBetweenTheRealHarbours() {
        assumeTrue(out != null)
        rule.setContent { World(construction(listOf(8, 15)), "map") }
        tick(1500); save("karte-routen-hafen-sued")
    }

    @Test fun mapRoutesWithAMovedHarbour() {
        assumeTrue(out != null)
        // an old north-coast harbour (version-4 data): routes go around the island to it
        rule.setContent { World(construction(listOf(10, 4)), "map") }
        tick(1500); save("karte-routen-hafen-nord-altdaten")
    }

    @Test fun closeNeighboursUnderTheMasks() {
        assumeTrue(out != null)
        rule.setContent { World(construction(FitIsland.SPOTS.getValue("HARBOUR"), level = 3, spots = FitIsland.SPOTS - "HARBOUR"), "home") }
        tick(1500); save("insel-nahe-gebaeude-stufe3")
    }

    @Test fun harbourTargetsWhileHeld() {
        assumeTrue(out != null)
        rule.setContent { World(construction(FitIsland.SPOTS.getValue("HARBOUR"), level = 2, spots = FitIsland.SPOTS - "HARBOUR" - "LIGHTHOUSE"), "home") }
        tick(1500)
        rule.onAllNodesWithContentDescription("Hafen").filter(hasClickAction())[0].performTouchInput { longClick() }
        tick(); save("insel-hafen-ziele")
    }

    @Test fun postTargetsWhileHeld() {
        assumeTrue(out != null)
        rule.setContent { World(construction(FitIsland.SPOTS.getValue("HARBOUR"), level = 1, spots = FitIsland.SPOTS - "HARBOUR" - "LIGHTHOUSE"), "home") }
        tick(1500)
        rule.onAllNodesWithContentDescription("Post").filter(hasClickAction())[0].performTouchInput { longClick() }
        tick(); save("insel-post-ziele-masken")
    }

    @Test fun anOldVersion4IslandWithOverlaps() {
        assumeTrue(out != null)
        val old = ApiClient.IslandConstruction(land = 5, mode = "creative", available = 500, ageDays = 30, gridVersion = 4,
            buildings = mapOf("HOUSE" to 3, "POST" to 2, "LIBRARY" to 1, "HARBOUR" to 1),
            positions = mapOf("HOUSE" to listOf(10, 10), "POST" to listOf(11, 10), "LIBRARY" to listOf(10, 11), "HARBOUR" to listOf(10, 15)))
        rule.setContent { World(old, "home") }
        tick(1500); save("insel-v4-ueberlappung-hinweis")
        rule.onAllNodesWithContentDescription("Post").filter(hasClickAction())[0].performTouchInput { longClick() }
        tick(); save("insel-v4-ueberlappung-post-verschieben")
    }
}
