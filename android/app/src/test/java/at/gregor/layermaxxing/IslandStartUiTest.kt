package at.gregor.layermaxxing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 7.9.9-start, Veit's phone feedback on 7.9.8 (production IslandWorld on Robolectric, a JVM simulation):
 * no bottom pills in the island menu, a painted back plate to the overview, stage 0 free on an empty
 * island, grey shelf cards with a reason and no section names in the strip, no "Verschieben" in the
 * tapped building's actions and every place showing its current stage.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class IslandStartUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private class Sent { val requests = mutableListOf<ApiClient.IslandRequest>() }
    private val items = IsleDecor.labels.keys.mapIndexed { i, k -> ApiClient.DecorItem(k, 40 + i * 30, false) }

    @Composable private fun World(c: ApiClient.IslandConstruction, sent: Sent, view: String, eligible: Boolean) {
        var state by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(c) }
        val home = ApiClient.HomeIsland(1, emptyMap(), 20, items, construction = state)
        VillageTheme(true) { IslandWorld(
            ownName = "Veit", ownEmoji = "🦊", friends = emptyList(), groups = emptyList(), islands = ApiClient.Islands(20, emptyList()),
            quests = emptyList(), letters = emptyList(), topics = emptyList(), opened = emptyMap(), onBack = {}, onChat = {}, onComposeLetter = {},
            onOpenLetter = {}, onLockedTap = {}, onTopics = {}, onAllTopics = {}, onGlossary = {}, onPeople = {}, onCreateQuest = { _, _, _, _, _, _ -> },
            onQuestDone = { _, _ -> }, onQuestDelete = {}, ownId = 1, startView = view, creativeActive = c.mode == "creative",
            creativeEligible = eligible,
            loadIsland = { home.copy(construction = state) },
            mainNavigation = { LayermaxxingTheme("light") { MainNavigationBar(listOf(MainTab.CHATS, MainTab.CASTLES, MainTab.MORE), MainTab.CASTLES, 0) {} } },
            changeConstruction = { cc, req ->
                sent.requests += req
                state = cc.copy(positions = req.position?.let { p -> req.building?.let { cc.positions + (it to p) } } ?: cc.positions, revision = cc.revision + 1)
                home.copy(construction = state)
            },
        ) }
    }
    private fun tick(ms: Long = 700) { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); rule.mainClock.advanceTimeBy(ms) }
    private fun start(c: ApiClient.IslandConstruction, view: String = "home", eligible: Boolean = false, sent: Sent = Sent()): Sent {
        rule.setContent { World(c, sent, view, eligible) }
        rule.mainClock.autoAdvance = false
        tick(900)
        return sent
    }
    private fun clickLabel(label: String) = SemanticsMatcher("click label $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }
    private val empty = ApiClient.IslandConstruction(mode = "normal", available = 0, ageDays = 0, land = 0)
    private fun built(level: Int) = ApiClient.IslandConstruction(mode = "normal", available = 500, ageDays = 30, land = 5,
        buildings = IsleBuilding.entries.associate { it.name to level })

    // 1) island menu without the bottom pills -------------------------------------------------------------
    @Test fun theIslandMenuHasNoOverviewBuildOrSettingsPillsButKeepsTheCreativeSwitch() {
        start(built(1), eligible = true)
        rule.onAllNodesWithText("Menü")[0].performClick(); tick(900)
        rule.onAllNodesWithText("Inselmenü")[0].assertExists()
        rule.onAllNodesWithText("Kreativmodus: Aus")[0].assertExists()
        for (label in listOf("Übersicht", "Bauen", "Einstellungen")) rule.onAllNodes(clickLabel(label)).assertCountEquals(0)
        // every function is still reachable from the menu
        for (b in IsleBuilding.entries) rule.onAllNodesWithContentDescription("${b.label} öffnen")[0].assertExists()
    }

    // 2) painted back plate to the overview ----------------------------------------------------------------
    @Test fun myIslandHasAPaintedBackPlateAtTheTopLeftThatOpensTheOverview() {
        start(built(1))
        val plate = rule.onAllNodesWithContentDescription("Zurück zur Übersicht")[0]
        val b = plate.getBoundsInRoot(); val root = rule.onRoot().getBoundsInRoot()
        assertTrue("at least 48 dp tall: ${b.height}", b.height >= 48.dp)
        assertTrue("top left: $b", b.left.value < root.right.value * .3f && b.top.value < root.bottom.value * .15f)
        rule.onAllNodesWithText("Übersicht")[0].assertExists()
        plate.performClick(); tick(900)
        rule.onAllNodesWithText("Übersicht · 0 Freunde")[0].assertExists()
        rule.onAllNodesWithContentDescription("Ansicht: Meine Insel").assertCountEquals(0)
    }

    @Test fun androidBackOnMyIslandAlsoOpensTheOverview() {
        start(built(1))
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        tick(900)
        rule.onAllNodesWithText("Übersicht · 0 Freunde")[0].assertExists()
    }

    // 3) + 4) empty island: stage 0 free, grey reasons, no section names in the strip --------------------
    @Test fun onAnEmptyIslandEveryStageZeroIsFreeAndPlacingItSendsAFreeBuild() {
        val sent = start(empty)
        rule.onAllNodesWithContentDescription("Preis: gratis")[0].assertExists()
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].performClick(); tick()
        for (b in IsleBuilding.entries) {
            val card = rule.onAllNodesWithContentDescription("${b.label} platzieren")[0]
            assertEquals("baubar", card.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
            rule.onAllNodesWithText(IslandStages.of(b, 0).name)[0].assertExists()
        }
        rule.onAllNodesWithText("Nachtlager")[0].assertExists()
        rule.onAllNodesWithContentDescription("Mein Haus platzieren")[0].performClick(); tick()
        rule.onAllNodesWithContentDescription("Bestätigen")[0].performClick(); tick(900)
        assertEquals("build", sent.requests.single().action)
        assertEquals("HOUSE", sent.requests.single().building)
    }

    @Test fun cardsThatCannotBeBuiltAreGreyWithAShortReason() {
        start(empty)
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].performClick(); tick()
        val land = rule.onAllNodesWithContentDescription("Land erweitern")[0].fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertEquals("nicht verfügbar: ab Tag 1", land)
        val firstDecor = BuildShelf.decorKeys(ApiClient.HomeIsland(1, emptyMap(), 20, items)).first()
        val decor = rule.onAllNodesWithContentDescription("${IsleDecor.labels[firstDecor] ?: firstDecor} platzieren")[0]
            .fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertTrue(decor, decor.startsWith("nicht verfügbar: fehlen ") && decor.endsWith(" P"))
        val special = rule.onAllNodesWithContentDescription("Besondere Deko ${SpecialDecor.items[0].label} platzieren")[0]
            .fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertTrue(special, special.startsWith("nicht verfügbar: fehlen "))
    }

    @Test fun theStripHasNoSectionNamesButTheTabsStay() {
        start(built(1))
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].performClick(); tick()
        for (t in BuildShelf.titles) {
            rule.onAllNodesWithContentDescription("Reiter $t")[0].assertExists()
            rule.onAllNodesWithContentDescription("Abschnitt $t").assertCountEquals(0)
        }
        val inStrip = hasAnyAncestor(hasContentDescription("Bauleiste Streifen"))
        for (word in listOf("Gebäude", "Deko", "Besondere", "Land")) rule.onAllNodes(inStrip and hasText(word)).assertCountEquals(0)
        // a fully built function is grey in the shelf: it cannot be built again
        val post = rule.onAllNodesWithContentDescription("Post auswählen")[0].fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertEquals("nicht verfügbar: steht · Stufe 1", post)
    }

    // 5) tapped building: Öffnen, Ausbauen, Abreißen; no Verschieben --------------------------------------
    @Test fun aTappedBuildingOffersOpenUpgradeDemolishButNoMove() {
        start(built(1))
        rule.onAllNodesWithContentDescription("Post").filter(hasClickAction())[0].performClick(); tick(900)
        rule.onAllNodesWithContentDescription("Gebäudeaktionen")[0].assertExists()
        for (label in listOf("Öffnen", "Ausbauen", "Abreißen")) rule.onAllNodesWithText(label)[0].assertExists()
        rule.onAllNodesWithText("Verschieben").assertCountEquals(0)
        rule.onAllNodesWithText("60 P")[0].assertExists()
    }

    // 6) every place shows its current stage --------------------------------------------------------------
    private fun openFromMenu(b: IsleBuilding) {
        rule.onAllNodesWithText("Menü")[0].performClick(); tick(900)
        rule.onAllNodesWithContentDescription("${b.label} öffnen")[0].performClick(); tick(1200)
    }

    @Test fun everyPlaceAtStageZeroShowsItsStageZeroNameAndPicture() {
        start(empty)
        for (b in IsleBuilding.entries) {
            openFromMenu(b)
            val stage = IslandStages.of(b, 0)
            rule.onAllNodesWithContentDescription("Ort: ${stage.name}")[0].assertExists()
            // (a clean stage – the letter tree – draws no header picture: the place itself is the picture)
            if (!StageScenes.of(b, 0).clean) rule.onAllNodesWithContentDescription("Kopfbild: ${stage.name}")[0].assertExists()
            rule.onAllNodesWithContentDescription("Stufe: ${stage.subtitle}")[0].assertExists()
            rule.onAllNodesWithContentDescription("Ort: ${IslandStages.of(b, 3).name}").assertCountEquals(0)
            rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; tick(900)
        }
        // the lighthouse at stage 0 is the look-out palm, not the finished lighthouse
        openFromMenu(IsleBuilding.LIGHTHOUSE)
        rule.onAllNodesWithText("Aussichtspalme")[0].assertExists()
        rule.onAllNodesWithText("Leuchtturm").assertCountEquals(0)
    }

    @Test fun everyPlaceAtStageTwoShowsItsStageTwoNameAndPicture() {
        start(built(2))
        for (b in IsleBuilding.entries) {
            openFromMenu(b)
            val stage = IslandStages.of(b, 2)
            rule.onAllNodesWithContentDescription("Ort: ${stage.name}")[0].assertExists()
            // (a clean stage – every post stage since 7.9.14 – draws no header picture: the place itself is the picture)
            if (!StageScenes.of(b, 2).clean) rule.onAllNodesWithContentDescription("Kopfbild: ${stage.name}")[0].assertExists()
            else rule.onAllNodesWithContentDescription("Kopfbild: ${stage.name}").assertCountEquals(0)
            rule.onAllNodesWithContentDescription("Ort: ${IslandStages.of(b, 0).name}").assertCountEquals(0)
            rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; tick(900)
        }
    }
}
