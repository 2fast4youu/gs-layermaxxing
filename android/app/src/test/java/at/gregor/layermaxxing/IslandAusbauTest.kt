package at.gregor.layermaxxing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 7.9.7 "Ausbau" on the production composables (Robolectric, a JVM simulation, not a device):
 * special decoration instead of building plots (max 3), one continuous build strip.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class IslandAusbauTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val items = IsleDecor.labels.keys.map { ApiClient.DecorItem(it, 0, true) }
    private fun special(n: Int) = listOf(listOf(10, 9), listOf(5, 9), listOf(15, 9), listOf(10, 14)).take(n).mapIndexed { i, p ->
        ApiClient.IslandDecor(200 + i, SpecialDecor.items[i].key, p, name = "Ort ${i + 1}")
    }
    private class Sent { val requests = mutableListOf<ApiClient.IslandRequest>(); val errors = mutableListOf<String>() }

    @Composable private fun World(c: ApiClient.IslandConstruction, sent: Sent, start: String = "home") {
        var state by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(c) }
        VillageTheme(true) { IslandWorld(
            ownName = "Veit", ownEmoji = "🦊", friends = emptyList(), groups = emptyList(), islands = ApiClient.Islands(500, emptyList()),
            quests = emptyList(), letters = emptyList(), topics = emptyList(), opened = emptyMap(), onBack = {}, onChat = {}, onComposeLetter = {},
            onOpenLetter = {}, onLockedTap = {}, onTopics = {}, onAllTopics = {}, onGlossary = {}, onPeople = {}, onCreateQuest = { _, _, _, _, _, _ -> },
            onQuestDone = { _, _ -> }, onQuestDelete = {}, ownId = 1, startView = start, creativeActive = c.mode == "creative",
            onError = { sent.errors += it },
            loadIsland = { ApiClient.HomeIsland(1, emptyMap(), 500, items, construction = state) },
            changeConstruction = { cc, req ->
                sent.requests += req
                state = when (req.action) {
                    "upgrade" -> cc.copy(buildings = cc.buildings + (req.building!! to ((cc.buildings[req.building] ?: 0) + 1)))
                    "build" -> cc.copy(starters = cc.starters + req.building!!, positions = cc.positions + (req.building to req.position!!))
                    else -> cc.copy(decor = cc.decor + ApiClient.IslandDecor(300 + sent.requests.size, req.item!!, req.position!!))
                }.copy(revision = cc.revision + 1)
                ApiClient.HomeIsland(1, emptyMap(), 500, items, construction = state)
            },
        ) }
    }
    private fun tick(ms: Long = 700) { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); rule.mainClock.advanceTimeBy(ms) }
    private fun start(c: ApiClient.IslandConstruction, sent: Sent = Sent(), view: String = "home"): Sent {
        rule.setContent { World(c, sent, view) }
        rule.mainClock.autoAdvance = false
        tick(900)
        return sent
    }
    private fun openShopAt(section: String) {
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].performClick(); tick()
        rule.onAllNodesWithContentDescription("Reiter $section")[0].performClick(); tick(1200)
    }
    private fun selected(tab: String) = rule.onAllNodesWithContentDescription("Reiter $tab")
        .filter(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).fetchSemanticsNodes().isNotEmpty()

    // A) special decoration ---------------------------------------------------------------------

    @Test fun specialDecorIsPlacedFreelyFromItsOwnShelfSectionAndCountsTwoOfThree() {
        val sent = start(ApiClient.IslandConstruction(mode = "normal", available = 500, ageDays = 30, land = 5, decor = special(2)))
        // No plots any more: no "+ Bauplatz" signs, no life place sheet.
        rule.onAllNodesWithText("+ Bauplatz").assertCountEquals(0)
        openShopAt("Besondere Deko")
        rule.onAllNodesWithText("Besondere Deko 2/3")[0].assertExists()
        rule.onAllNodesWithContentDescription("Besondere Deko Hütte platzieren")[0].performClick(); tick()
        rule.onAllNodesWithContentDescription("Bauplatzraster")[0].assertExists()
        rule.onAllNodesWithContentDescription("Bestätigen")[0].performClick(); tick(900)
        val req = sent.requests.single()
        assertEquals("decor_place", req.action); assertEquals("place_hut", req.item)
        assertTrue("on the grid of land 5: ${req.position}", IslandGrid.buildable(req.position!!, 5))
    }

    @Test fun atThreeSpecialPiecesTheSectionIsFullAndNothingCanBePlaced() {
        val sent = start(ApiClient.IslandConstruction(mode = "creative", available = 0, ageDays = 30, land = 5, decor = special(3)))
        openShopAt("Besondere Deko")
        rule.onAllNodesWithText("Besondere Deko 3/3")[0].assertExists()
        val card = rule.onAllNodesWithContentDescription("Besondere Deko Hütte platzieren")[0]
        card.assertExists()
        // 7.9.9: greyed with the short reason "3/3"
        assertEquals("nicht verfügbar: 3/3", card.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        card.performClick(); tick()
        rule.onAllNodesWithContentDescription("Bauplatzraster").assertCountEquals(0)
        assertTrue(sent.errors.any { "3" in it }); assertTrue(sent.requests.isEmpty())
    }

    @Test fun oldPlacesStayAsNamedSpecialPiecesEvenWhenMoreThanThree() {
        start(ApiClient.IslandConstruction(mode = "normal", available = 500, ageDays = 30, land = 5, decor = special(4)))
        for (i in 1..4) assertTrue("Ort $i shown", rule.onAllNodesWithContentDescription("Ort $i").fetchSemanticsNodes().isNotEmpty())
        openShopAt("Besondere Deko")
        rule.onAllNodesWithText("Besondere Deko 4/3")[0].assertExists()
    }

    // H) one continuous build strip ---------------------------------------------------------------

    @Test fun theBuildStripIsOneContinuousShelfAndTheTabsFollowTheScroll() {
        start(ApiClient.IslandConstruction(mode = "creative", available = 500, ageDays = 30, land = 2))
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].performClick(); tick()
        assertTrue(selected("Gebäude"))
        // Every section heading lives in the same strip.
        val strip = rule.onAllNodesWithContentDescription("Bauleiste Streifen")[0]
        val counts = listOf(IsleBuilding.entries.size, BuildShelf.decorKeys(ApiClient.HomeIsland(1, emptyMap(), 500, items)).size, SpecialDecor.items.size, 1)
        val starts = BuildShelf.starts(counts)
        fun scrollToSection(section: Int) {
            val range = strip.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]
            val dp = starts[section] * BuildShelf.ITEM_DP  // 7.9.9: cards only, no heading items
            val px = with(rule.density) { dp.dp.toPx() }
            strip.performSemanticsAction(SemanticsActions.ScrollBy) { it(px - range.value(), 0f) }; tick()
        }
        scrollToSection(BuildShelf.DECOR)
        assertTrue("tab follows the scroll into Deko", selected("Deko"))
        scrollToSection(BuildShelf.SPECIAL)
        assertTrue("tab follows the scroll into Besondere Deko", selected("Besondere Deko"))
        // A tap on a tab scrolls there animated (no hard switch of the content).
        rule.onAllNodesWithContentDescription("Reiter Land")[0].performClick(); tick(1500)
        assertTrue(selected("Land"))
        rule.onAllNodesWithContentDescription("Land erweitern")[0].assertExists()
        rule.onAllNodesWithContentDescription("Reiter Gebäude")[0].performClick(); tick(1500)
        assertTrue(selected("Gebäude"))
        rule.onAllNodesWithContentDescription("Post platzieren")[0].assertExists()
    }

    @Test fun sectionStartsAreCardsOnlyWithoutHeadings() {
        // 7.9.9: no heading items in the strip any more; sections start right at their first card.
        assertEquals(listOf(0, 6, 16, 24), BuildShelf.starts(listOf(6, 10, 8, 1)))
        assertEquals(0, BuildShelf.sectionAt(5, listOf(0, 6, 16, 24)))
        assertEquals(1, BuildShelf.sectionAt(6, listOf(0, 6, 16, 24)))
        assertEquals(3, BuildShelf.sectionAt(24, listOf(0, 6, 16, 24)))
    }

    @Test fun theBuildButtonIsTealAndBrassNotYellow() {
        start(ApiClient.IslandConstruction(mode = "normal", available = 500, ageDays = 30, land = 2))
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].assertExists()
        // 7.9.9: missing functions are built for free at stage 0
        rule.onAllNodesWithContentDescription("Preis: gratis")[0].assertExists()
    }
    @Test fun overviewIsTheBackPlateAndItReallyNavigates() {
        // 7.9.9: the overview left the menu; it is the painted back plate at the top left.
        start(ApiClient.IslandConstruction(land = 2))
        rule.onAllNodesWithContentDescription("Zurück").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Zurück zur Übersicht")[0].performClick(); tick(900)
        rule.onAllNodesWithText("Übersicht · 0 Freunde")[0].assertExists()
        rule.onAllNodesWithContentDescription("Ansicht: Meine Insel").assertCountEquals(0)
        rule.onAllNodesWithText("Meine Insel").assertCountEquals(0)
        rule.onAllNodesWithText("Hafen").assertCountEquals(0)
        rule.onAllNodesWithText("Menü")[0].performClick(); tick(900)
        rule.onAllNodesWithText("Meine Insel")[0].performClick(); tick(900)
        rule.onAllNodesWithText("Übersicht · 0 Freunde").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].assertExists()
    }

    @Test fun starterMenuShowsNaturalStagesAndTheirActualFunctions() {
        start(ApiClient.IslandConstruction())
        rule.onAllNodesWithText("Menü")[0].performClick(); tick(900)
        for (b in IsleBuilding.entries) {
            val stage = IslandStages.of(b, 0)
            rule.onAllNodesWithText(stage.name)[0].assertExists()
            rule.onAllNodesWithText(stage.subtitle)[0].assertExists()
            rule.onAllNodesWithContentDescription("${stage.name} – ${stage.function}")[0].assertExists()
        }
        rule.onAllNodesWithContentDescription("Post öffnen")[0].performClick(); tick()
        rule.onAllNodesWithText("Inselmenü").assertCountEquals(0)
        // 7.9.9: the place shows the stage it has (stage 0 = Briefbaum), not the finished Postamt
        rule.onAllNodesWithContentDescription("Ort: Briefbaum")[0].assertExists()
        rule.onAllNodesWithContentDescription("Ort: Postamt").assertCountEquals(0)
    }

    @Test fun upgradePreviewHasLargePaintedBeforeAfterWithoutOpenOrMoveAndUpdatesTheStage() {
        // 7.9.12: a spot where the bigger stage-2 picture fits too (the default spot is at the western rim)
        val sent = start(ApiClient.IslandConstruction(mode = "normal", available = 500, ageDays = 30,
            buildings = mapOf("POST" to 1), land = 5, positions = mapOf("POST" to listOf(12, 6))))
        rule.onAllNodesWithText("Postkasten")[0].assertExists()
        rule.onAllNodesWithContentDescription("Post").filter(hasClickAction())[0].performClick(); tick()
        rule.onAllNodesWithText("Ausbauen")[0].performClick(); tick(900)
        rule.onAllNodesWithText("Öffnen").assertCountEquals(0)
        rule.onAllNodesWithText("Verschieben").assertCountEquals(0)
        for (name in listOf("Postkasten", "Briefstube")) {
            val preview = rule.onAllNodesWithContentDescription("Ausbauvorschau: $name")[0]
            assertTrue("large painted preview", preview.getBoundsInRoot().height >= 160.dp)
        }
        rule.onAllNodesWithText("Auf Stufe 2 ausbauen")[0].performClick(); tick(900)
        assertEquals("upgrade", sent.requests.single().action)
        rule.onAllNodesWithText("Schließen")[0].performClick(); tick()
        rule.onAllNodesWithText("Briefstube")[0].assertExists()
        rule.onAllNodesWithText("Menü")[0].performClick(); tick(900)
        rule.onAllNodesWithText("Briefe · Stufe 2")[0].assertExists()
    }

    @Test fun transparentSpriteMarginIsEmptyGroundButTheVisibleTowerIsTappable() {
        start(ApiClient.IslandConstruction(buildings = mapOf("LIGHTHOUSE" to 1), land = 5))
        val piece = IslandPlans.placedBuildings(0, ApiClient.IslandConstruction(buildings = mapOf("LIGHTHOUSE" to 1), land = 5)).getValue(IsleBuilding.LIGHTHOUSE)
        val ground = rule.onAllNodesWithContentDescription("Inselboden")[0]
        ground.performTouchInput {
            val box = piece.size * width
            // 7.9.14: transparent left margin of the lookout's picture: 4 % of the box left of its measured visible bounds
            val b = SpriteBounds.of(piece.res)
            org.junit.Assert.assertTrue("the picture has a transparent margin to tap into: ${b.toList()}", b[0] > .06f)
            click(Offset(piece.x * width - box * .5f + box * (b[0] - .04f), piece.y * height - box * .55f))
        }; tick(900)
        rule.onAllNodesWithContentDescription("Gebäudeaktionen").assertCountEquals(0)
        ground.performTouchInput {
            val box = piece.size * width
            click(Offset(piece.x * width, piece.y * height - box * .55f))
        }; tick(900)
        rule.onAllNodesWithContentDescription("Gebäudeaktionen")[0].assertExists()
    }

    @Test fun postAndFriendsAlwaysHaveDifferentIconsAndEveryStageHasItsOwnName() {
        for (level in 0..3) org.junit.Assert.assertNotEquals(IslandStages.of(IsleBuilding.POST, level).icon, IslandStages.of(IsleBuilding.LIGHTHOUSE, level).icon)
        for (b in IsleBuilding.entries) assertEquals(4, IslandStages.all(b).map { it.name }.toSet().size)
    }

}
