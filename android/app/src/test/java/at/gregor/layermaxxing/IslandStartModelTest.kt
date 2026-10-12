package at.gregor.layermaxxing

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 7.9.9-start: free stage 0 in the client model (mirrors server test_v13_start.py) and glossary categories. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class IslandStartModelTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun stageZeroIsFreeOnAnEmptyIslandAndOnlyUpgradesCost() {
        val empty = ApiClient.IslandConstruction(mode = "normal", available = 0, ageDays = 0)
        for (b in IsleBuilding.entries) {
            assertTrue(IslandBuild.buildable(empty, b)); assertEquals("gratis", IslandBuild.price(empty, b))
        }
        assertEquals("gratis", IslandBuild.shopLine(empty))
        val starter = empty.copy(starters = setOf("HOUSE"), positions = mapOf("HOUSE" to listOf(10, 10)))
        assertTrue(starter.standing(IsleBuilding.HOUSE)); assertEquals(0, starter.level(IsleBuilding.HOUSE))
        assertFalse(IslandBuild.buildable(starter, IsleBuilding.HOUSE))
        assertEquals("steht · Start", IslandBuild.price(starter, IsleBuilding.HOUSE))
        // upgrading the hammock to stage 1 costs the first step
        assertEquals("25 P", IslandBuild.upgradeCost(starter, 0))
        assertEquals("Stufe 1: 25 Punkte · es fehlen 25", IslandBuild.upgradeLine(starter, 0))
        // everything stands: the cheapest next step is land again
        val all = empty.copy(starters = IsleBuilding.entries.map { it.name }.toSet())
        assertEquals("ab 40 P", IslandBuild.shopLine(all))
        // old ledgers keep their meaning: level 2 stands at stage 2
        val old = empty.copy(buildings = mapOf("POST" to 2))
        assertTrue(old.standing(IsleBuilding.POST)); assertEquals(2, old.level(IsleBuilding.POST))
    }

    @Test fun stageZeroPiecesStandOnTheIslandAndKeepOnlyTheSmallGap() {
        val c = ApiClient.IslandConstruction(land = 0, starters = setOf("HOUSE"), positions = mapOf("HOUSE" to listOf(10, 10)))
        val placed = IslandPlans.placedBuildings(0, c)
        assertEquals(setOf(IsleBuilding.HOUSE), placed.keys)
        assertEquals(IslandStages.of(IsleBuilding.HOUSE, 0).res, placed.getValue(IsleBuilding.HOUSE).res)
        val f = IslandPlans.constructionScale(0)
        val near = IslandGrid.buildableCells(0).first { p ->
            val d = Math.sqrt(((p[0] - 10) * (p[0] - 10) + (p[1] - 10) * (p[1] - 10)).toDouble())
            d >= IslandLayout.DECOR_BUILDING_GAP / f && d < IslandLayout.BUILDING_GAP / f
        }
        // a new (stage 0) piece may stand there, a big stage-1 building next to a big one may not
        assertTrue(IslandLayout.valid(c, IsleBuilding.POST, near))
        val big = c.copy(starters = emptySet(), buildings = mapOf("HOUSE" to 1))
        assertTrue(IslandLayout.blocked(big, near, big = true, ignoreBuilding = IsleBuilding.POST))
        // the preview of a new piece is its stage 0, never a stage-1 building
        val h = ApiClient.HomeIsland(1, emptyMap(), 0, emptyList(), construction = c)
        val preview = IslandLayout.preview(h, IslandDraft(IslandTarget.Building(IsleBuilding.POST), near)).construction
        assertTrue("POST" in preview.starters); assertEquals(0, preview.level(IsleBuilding.POST))
    }

    @Test fun glossaryCategoriesFilterAndDefault() {
        assertEquals(listOf("Begriffe", "Witze", "Sprüche", "Zitate"), GlossaryCategories.all)
        assertEquals("Begriffe", GlossaryCategories.normalize(null))
        assertEquals("Begriffe", GlossaryCategories.normalize("Rezepte"))
        val joke = ApiClient.GlossaryTerm(1, "Kalauer", "A", emptyList(), false, "Witze")
        val word = ApiClient.GlossaryTerm(2, "Layer", "A", emptyList(), false)
        assertEquals("Begriffe", word.category)
        assertEquals(listOf("Kalauer"), Dictionary.rows(listOf(joke, word), "", "Witze").map { it.term })
        assertTrue(Dictionary.rows(listOf(joke, word), "", "Begriffe").any { it.term == "Layer" })
        assertTrue(Dictionary.rows(listOf(joke, word), "", "Begriffe").none { it.term == "Kalauer" })
        assertTrue(Dictionary.rows(listOf(joke, word), "", "Zitate").isEmpty())
    }

    @Test fun theGlossaryHasCategoryTabsShowsEachCategoryAndNewWordsDefaultToBegriffe() {
        val saved = mutableListOf<Triple<String, String, String>>()
        val seed = listOf(
            ApiClient.GlossaryTerm(1, "Kalauer", "A", listOf(ApiClient.GlossaryExplanation(1, "Was ist gelb und kann schießen? Eine Banone.", "A", 0, 0, false)), false, "Witze"),
            ApiClient.GlossaryTerm(2, "Carpe diem", "A", listOf(ApiClient.GlossaryExplanation(2, "Nutze den Tag.", "A", 0, 0, false)), false, "Zitate"),
        )
        rule.setContent { VillageTheme(true) { GlossaryScreen(seed = seed, addTerm = { t, x, c -> saved += Triple(t, x, c) }) } }
        for (c in listOf("Alle") + GlossaryCategories.all) rule.onAllNodesWithContentDescription("Kategorie $c")[0].assertExists()
        // "Alle" starts with the built-in app terms, which are Begriffe
        rule.onAllNodesWithContentDescription("Kategorie von Insel: Begriffe")[0].assertExists()
        rule.onAllNodesWithContentDescription("Kategorie Witze")[0].performClick(); rule.waitForIdle()
        rule.onAllNodesWithContentDescription("Kategorie von Kalauer: Witze")[0].assertExists()
        rule.onAllNodesWithText("Carpe diem").assertCountEquals(0)
        rule.onAllNodesWithText("Insel").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Kategorie Zitate")[0].performClick(); rule.waitForIdle()
        rule.onAllNodesWithContentDescription("Kategorie von Carpe diem: Zitate")[0].assertExists()
        rule.onAllNodesWithText("Kalauer").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Kategorie Alle")[0].performClick(); rule.waitForIdle()
        // a new word: Begriffe is preselected, Sprüche can be chosen
        rule.onAllNodesWithText("Wort hinzufügen")[0].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); rule.waitForIdle()
        val begriffe = rule.onAllNodesWithContentDescription("Neue Kategorie Begriffe")[0].fetchSemanticsNode()
        assertEquals(true, begriffe.config.getOrNull(SemanticsProperties.Selected))
        rule.onAllNodesWithContentDescription("Neue Kategorie Sprüche")[0].performClick()
        rule.onAllNodesWithText("Wort")[0].performTextInput("Morgenstund")
        rule.onAllNodesWithText("Erklärung")[0].performTextInput("hat Gold im Mund")
        rule.onAllNodesWithText("Eintragen")[0].performClick(); rule.waitForIdle()
        assertEquals(listOf(Triple("Morgenstund", "hat Gold im Mund", "Sprüche")), saved)
        rule.onAllNodesWithContentDescription("Kategorie Sprüche")[0].performClick(); rule.waitForIdle()
        rule.onAllNodesWithContentDescription("Kategorie von Morgenstund: Sprüche")[0].assertExists()
    }
}
