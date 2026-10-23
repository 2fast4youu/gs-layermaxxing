package at.gregor.layermaxxing

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 7.9.14 opt-in renders for the review of the style E integration (skipped unless VILLAGE_SHOTS is an output directory;
 * filter '*Atelier714VisualReview*'): the own island with all six buildings on their v6 grounds (stage 0 and the
 * finished stages), the archipelago with routes, the groups place with 0 / 1 / 5 groups and the post with many letters,
 * on a small phone with a 1.5× font. ALL DATA IS SYNTHETIC (invented names, no account, no server). Real Android
 * windows drawn on Robolectric (JVM), not device screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class Atelier714VisualReview {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val out = System.getenv("VILLAGE_SHOTS")?.let(::File)
    private val now = System.currentTimeMillis() / 1000

    private fun save(name: String) {
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        out!!.mkdirs()
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }
    private fun frames(n: Int = 40) { repeat(n) { rule.mainClock.advanceTimeByFrame() }; org.robolectric.shadows.ShadowLooper.idleMainLooper() }

    /** All six buildings at [stage] on a v6 land 5, each on a free spot the app's own rule accepts (synthetic ledger). */
    private fun island(stage: Int): ApiClient.IslandConstruction {
        var c = ApiClient.IslandConstruction(land = 5, mode = "creative", available = 500, ageDays = 30, gridVersion = PlacementRule.GROUND)
        for (b in listOf(IsleBuilding.HARBOUR, IsleBuilding.HOUSE, IsleBuilding.LIGHTHOUSE, IsleBuilding.POST, IsleBuilding.CAMPFIRE, IsleBuilding.LIBRARY)) {
            val next = if (stage == 0) c.copy(starters = c.starters + b.name) else c.copy(buildings = c.buildings + (b.name to stage))
            val spot = if (b == IsleBuilding.HARBOUR) IslandGround.harbourSpots(stage, 5).first()
                else IslandLayout.freeCell(IslandLayout.defaultPosition(b)) { IslandLayout.valid(next.copy(positions = next.positions - b.name), b, it) }
            c = next.copy(positions = next.positions + (b.name to spot))
        }
        return c
    }

    @Composable private fun World(home: ApiClient.IslandConstruction, view: String) {
        val friends = listOf(ApiClient.UserSummary(2, "Gerfried", "friends", "🦊", "#2E7D32"), ApiClient.UserSummary(3, "Anna", "friends", "🐻", "#1565C0"),
            ApiClient.UserSummary(4, "Lena", "friends", "🦉", "#EF6C00"))
        val infos = listOf(
            ApiClient.IslandInfo(2, 10, 0, 10, 2, 40, ApiClient.HomeIsland(2, emptyMap(), 10, emptyList(), construction = island(2))),
            ApiClient.IslandInfo(3, 10, 0, 10, 1, 40, ApiClient.HomeIsland(3, emptyMap(), 10, emptyList(), construction = island(1))),
            ApiClient.IslandInfo(4, 10, 0, 10, 3, 120, ApiClient.HomeIsland(4, emptyMap(), 10, emptyList(), construction = island(3))),
        )
        val letters = listOf(letter(11, 2, "Gerfried", false, "timed", now + 2 * 86_400), letter(12, 3, "Anna", true, "timed", now + 86_400),
            letter(13, 4, "Lena", false, "timed", now + 3 * 86_400))
        VillageTheme(true) { IslandWorld(
            ownName = "Testperson", ownEmoji = "🦉", friends = friends, groups = emptyList(), islands = ApiClient.Islands(10, infos),
            quests = emptyList(), letters = letters, topics = emptyList(), opened = emptyMap(), onBack = {}, onChat = {}, onComposeLetter = {},
            onOpenLetter = {}, onLockedTap = {}, onTopics = {}, onAllTopics = {}, onGlossary = {}, onPeople = {}, onCreateQuest = { _, _, _, _, _, _ -> },
            onQuestDone = { _, _ -> }, onQuestDelete = {}, ownId = 1, startView = view, creativeActive = true,
            loadIsland = { ApiClient.HomeIsland(1, emptyMap(), 0, emptyList(), construction = home) },
        ) }
    }

    private fun letter(id: Long, peer: Long, name: String, incoming: Boolean, mode: String, releaseAt: Long?, readAt: Long? = null, unlocked: Boolean = false) = ApiClient.Message(
        id, peer, name, incoming, "Brief $id", "", "none", now - id * 3600, mode, releaseAt, null, null, unlocked, false, readAt,
        false, false, null, null, null, emptyList(), null, null, null)

    private fun shot(name: String, small: Boolean = false, content: @Composable () -> Unit) {
        assumeTrue(out != null)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            if (small) {
                val d = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 1.5f)) { content() }
            } else content()
        }
        frames(); rule.mainClock.advanceTimeBy(1500); frames()
        save(name)
    }

    @Test fun islandAllStarters() = shot("insel-alle-start") { World(island(0), "home") }
    @Test fun islandAllStage1() = shot("insel-alle-stufe1") { World(island(1), "home") }
    @Test fun islandAllStage2() = shot("insel-alle-stufe2") { World(island(2), "home") }
    @Test fun islandAllStage3() = shot("insel-alle-stufe3") { World(island(3), "home") }
    @Test fun archipelagoRoutes() = shot("karte-routen-stil-e") { World(island(3), "map") }

    private val members = (0 until 7).map { ApiClient.UserSummary(20L + it, listOf("Mira", "Jonte", "Ilka", "Bosse", "Fenja", "Okko", "Tale")[it], "friends", "🦊", "#2E7D32") }
    private fun groups(n: Int) = (0 until n).map { ApiClient.Group(100L + it, listOf("Bergfreunde", "WG", "Chor", "Laufgruppe", "Kochrunde")[it], 1, members.take(3 + it)) }
    private fun campfire(n: Int, level: Int) = shot("gruppen-$n-stufe$level-klein-font15", small = true) {
        VillageTheme(true) { CommunityHall(groups(n), emptyList(), onGroupQuest = {}, onManage = {}, onClose = {}, level = level, onPlans = {}) }
    }
    @Config(qualifiers = "w360dp-h640dp-xhdpi") @Test fun groups0() = campfire(0, 0)
    @Config(qualifiers = "w360dp-h640dp-xhdpi") @Test fun groups0Glasshouse() = campfire(0, 3)
    @Config(qualifiers = "w360dp-h640dp-xhdpi") @Test fun groups1() = campfire(1, 1)
    @Config(qualifiers = "w360dp-h640dp-xhdpi") @Test fun groups5() = campfire(5, 2)

    private val pals = (0 until 8).map { ApiClient.UserSummary(40L + it, listOf("Mira", "Jonte", "Ilka", "Bosse", "Fenja", "Okko", "Tale", "Rune")[it], "friends", "🦉", "#1565C0") }
    private fun manyLetters() = (0 until 24).map { i ->
        val p = pals[i % pals.size]
        when (i % 4) {
            0 -> letter(500L + i, p.id, p.name, true, "manual", null, unlocked = true)                       // arrived, unread
            1 -> letter(500L + i, p.id, p.name, true, "manual", null, readAt = now - 600, unlocked = true)   // read
            2 -> letter(500L + i, p.id, p.name, false, "timed", now + 86_400)                                // sent, travelling
            else -> letter(500L + i, p.id, p.name, true, "mutual", null)                                     // to me, sealed
        }
    }
    private fun post(level: Int, small: Boolean) = shot("post-24-briefe-stufe$level" + if (small) "-klein-font15" else "", small) {
        VillageTheme(true) { PostOffice(pals, manyLetters(), emptyMap(), {}, {}, {}, {}, level = level) }
    }
    @Config(qualifiers = "w360dp-h640dp-xhdpi") @Test fun postMany0() = post(0, true)
    @Config(qualifiers = "w360dp-h640dp-xhdpi") @Test fun postMany1() = post(1, true)
    @Config(qualifiers = "w360dp-h640dp-xhdpi") @Test fun postMany2() = post(2, true)
    @Config(qualifiers = "w360dp-h640dp-xhdpi") @Test fun postMany3() = post(3, true)
    @Test fun postMany3Large() = post(3, false)
}
