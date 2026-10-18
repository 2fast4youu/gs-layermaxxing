package at.gregor.layermaxxing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 7.9.10 (render review of menu-04): the painted rivets of the upgrade sheet's brass plate covered the outer
 * letters of "Postkasten ausbauen". The title now stays inside the plate's usable middle ([UpgradeTitle.INSET]
 * per side), also for the longest stage names with a large font on a narrow phone (Robolectric, JVM simulation).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w320dp-h700dp-xxhdpi")
class IslandUpgradeTitleTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private var title by mutableStateOf("")
    private fun show(width: Dp, fontScale: Float) = rule.setContent {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
            VillageTheme(true) { Box(Modifier.width(width)) { UpgradeTitlePlate(title) } }
        }
    }

    /** Text within the plate's rivet-free middle, nothing cut off, at most two lines; returns the line count. */
    private fun assertInsideSafeArea(container: Dp): Int {
        rule.waitForIdle()
        val plate = rule.onNodeWithTag(UpgradeTitle.PLATE_TAG).getBoundsInRoot()
        val text = rule.onAllNodesWithText(title, useUnmergedTree = true)[0]
        val bounds = text.getBoundsInRoot()
        val inset = (plate.right - plate.left) * UpgradeTitle.INSET
        val slack = .5.dp
        assertTrue("$title: plate ${plate.right - plate.left} wider than $container", plate.right - plate.left <= container + slack)
        assertTrue("$title: left ${bounds.left} inside the rivet (${plate.left + inset})", bounds.left >= plate.left + inset - slack)
        assertTrue("$title: right ${bounds.right} inside the rivet (${plate.right - inset})", bounds.right <= plate.right - inset + slack)
        val layout = mutableListOf<TextLayoutResult>()
        text.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layout)
        assertFalse("$title cut off", layout.single().hasVisualOverflow)
        assertTrue("$title: ${layout.single().lineCount} lines", layout.single().lineCount in 1..2)
        return layout.single().lineCount
    }

    private val titles = IsleBuilding.entries.flatMap { b -> IslandStages.all(b).flatMap { s -> listOf("${s.name} ausbauen", "${s.name} bauen") } } + "Insel vergrößern"

    @Test fun postkastenTitleStaysClearOfTheRivetsOnOneLine() {
        title = "Postkasten ausbauen"
        show(280.dp, 1f)
        assertEquals(1, assertInsideSafeArea(280.dp))
    }

    @Test fun everyStageTitleFitsWithALargeFontOnANarrowPhone() {
        title = titles.first()
        show(280.dp, 1.6f)
        for (t in titles) { title = t; assertInsideSafeArea(280.dp) }
    }

    /**
     * Normal font on a wide sheet: every title inside the safe area, complete, at most two lines (a long name such
     * as "Wörter am Treibholz ausbauen" may wrap readably); short titles like "Postkasten ausbauen" stay on one line.
     */
    @Test fun normalFontOnAWideSheetShortTitlesOneLineLongOnesAtMostTwo() {
        val short = "Postkasten ausbauen"
        title = titles.first()
        show(371.dp, 1f)
        for (t in titles) {
            title = t
            val lines = assertInsideSafeArea(371.dp)
            if (t.length <= short.length) assertEquals(t, 1, lines)
        }
        assertTrue(short in titles && titles.any { it.length > short.length })
    }
}
