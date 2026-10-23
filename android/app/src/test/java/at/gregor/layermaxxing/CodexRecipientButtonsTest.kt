package at.gregor.layermaxxing

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real production scene and finger taps; fixtures only, no network or account. Shared by the opt-in renderer. */
internal class CodexRecipientScenario(
    val rule: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>,
    groups: List<ApiClient.Group> = listOf(ApiClient.Group(70, "Waldrand-Runde", 1, PostPilotFixtures.pals.take(3))),
    private val level: Int = 0,
) {
    val draft = mutableStateOf<LetterDraft?>(null)
    val callbacks = mutableListOf<Long>()
    var sends = 0
    private val writer = LetterWriter(PostPilotFixtures.pals,
        groups, emptyList(),
        creativeActive = false, transmit = { sends++; listOf(900L) }, onSent = {}, draftState = draft)
    fun frames() {
        rule.waitForIdle()
        repeat(160) { rule.mainClock.advanceTimeByFrame() }
        rule.waitForIdle()
    }
    fun start(fontScale: Float = 1f, withWriter: Boolean = true) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                VillageTheme(true) {
                    PostOffice(PostPilotFixtures.pals, PostPilotFixtures.letters, emptyMap(), level = level,
                        writer = if (withWriter) writer else null,
                        onOpen = {}, onLocked = {}, onWrite = { callbacks += it }, onClose = {})
                }
            }
        }
        frames()
        tap("tree-pen", scroll = false)
    }
    fun tag(t: String) = rule.onNodeWithTag(t, useUnmergedTree = true)
    fun reveal(t: String) {
        // Compose 1.7 performScrollTo loops until root-space bounds fit, without advancing
        // our manual clock. ScrollBy is animated, and this sheet is also rotated: retrying
        // unchanged/transformed bounds can fill Robolectric's retained ShadowTrace queue.
        // Use the same visible-sheet/target-center geometry as LetterTreeEndToEndTest,
        // and commit each scroll before checking again. Keep failures bounded; never
        // clear traces or bypass the UI.
        repeat(12) {
            val page = tag("tree-page").fetchSemanticsNode()
            val target = tag(t).fetchSemanticsNode()
            val (top, height, viewport) = rule.runOnIdle {
                // boundsInRoot on an offscreen target is clipped; position + size retains
                // its real extent, while the page bounds give the visible scroll viewport.
                Triple(target.positionInRoot.y, target.size.height.toFloat(), page.boundsInRoot)
            }
            assertTrue("$t fits sheet height: $height in $viewport", height <= viewport.height + 1f)
            if (top >= viewport.top - 1f && top + height <= viewport.bottom + 1f) {
                return
            }
            val dy = top + height / 2f - viewport.center.y
            tag("tree-page").performSemanticsAction(SemanticsActions.ScrollBy) { scroll ->
                assertTrue("$t sheet accepts scroll $dy", scroll(0f, dy))
            }
            frames()
        }
        fail("$t did not become fully visible on the sheet after 12 clock-driven scrolls")
    }
    fun tap(t: String, scroll: Boolean = true) {
        if (scroll) reveal(t)
        tag(t).performTouchInput { click() }
        frames()
    }
    fun selected(t: String, chosen: Boolean) {
        val c = tag(t).fetchSemanticsNode().config
        assertEquals(Role.Checkbox, c.getOrNull(SemanticsProperties.Role))
        assertEquals(chosen, c.getOrNull(SemanticsProperties.Selected))
        assertEquals(if (chosen) ToggleableState.On else ToggleableState.Off, c.getOrNull(SemanticsProperties.ToggleableState))
        assertEquals(if (chosen) "Gewählt" else "Nicht gewählt", c.getOrNull(SemanticsProperties.StateDescription))
        assertNotNull(c.getOrNull(SemanticsActions.OnClick))
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
open class CodexRecipientButtonsTest {
    protected open val postLevel: Int = 0
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val scene by lazy { CodexRecipientScenario(rule, level = postLevel) }

    @Test fun noGroupsOffersPeopleWithoutInventingAGroup() {
        val emptyGroups = CodexRecipientScenario(rule, level = postLevel, groups = emptyList())
        emptyGroups.start()
        assertTrue(rule.onAllNodesWithTag("tree-to-group-70", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        emptyGroups.tap("tree-to-40")
        rule.runOnIdle {
            assertEquals(setOf(40L), emptyGroups.draft.value!!.selected)
            assertNull(emptyGroups.draft.value!!.groupId)
            assertEquals(0, emptyGroups.sends)
        }
    }

    @Test fun manyGroupsRemainReachableAndReplaceRatherThanAccumulate() = checkManyGroups(1f)

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun manyGroupsRemainReachableAndReplaceRatherThanAccumulateSmall15() = checkManyGroups(1.5f)

    private fun checkManyGroups(fontScale: Float) {
        val groups = (70L..77L).map { ApiClient.Group(it, "Testgruppe $it am Waldrand", 1, PostPilotFixtures.pals.take(3)) }
        val manyGroups = CodexRecipientScenario(rule, groups, level = postLevel)
        manyGroups.start(fontScale)
        // Exercise every group, including scrolls beyond the initially visible sheet.
        groups.forEach { manyGroups.reveal("tree-to-group-${it.id}") }
        manyGroups.tap("tree-to-group-70")
        manyGroups.tap("tree-write-to")
        manyGroups.selected("tree-to-group-70", true)
        manyGroups.tap("tree-to-group-77")
        manyGroups.selected("tree-to-group-77", true)
        manyGroups.reveal("tree-to-group-70")
        manyGroups.selected("tree-to-group-70", false)
        rule.runOnIdle {
            assertEquals(77L, manyGroups.draft.value!!.groupId)
            assertTrue(manyGroups.draft.value!!.selected.isEmpty())
            assertEquals(0, manyGroups.sends)
            assertTrue(manyGroups.callbacks.isEmpty())
        }
    }

    @Test fun peopleAndGroupsToggleOnTheSameDraftWithoutSending() {
        scene.start()
        scene.tap("tree-to-40")
        val draft = scene.draft.value!!
        rule.runOnIdle { draft.text = "Bleibt erhalten"; draft.title = "Entwurf" }
        scene.tap("tree-write-to")
        scene.selected("tree-to-40", true); scene.selected("tree-to-41", false)
        scene.tap("tree-to-41")
        scene.selected("tree-to-41", true)
        rule.runOnIdle { assertEquals(setOf(40L, 41L), draft.selected) }
        scene.tap("tree-to-40"); scene.selected("tree-to-40", false)
        scene.tap("tree-to-group-70"); scene.selected("tree-to-group-70", true)
        scene.selected("tree-to-41", false)
        rule.runOnIdle { assertEquals(70L, draft.groupId); assertTrue(draft.selected.isEmpty()) }
        scene.tap("tree-to-group-70"); scene.selected("tree-to-group-70", false)
        rule.runOnIdle { assertNull(draft.groupId) }
        scene.tap("tree-to-group-70"); scene.tap("tree-to-41")
        scene.selected("tree-to-41", true); scene.selected("tree-to-group-70", false)
        scene.tap("tree-to-done")
        rule.runOnIdle {
            assertSame(draft, scene.draft.value)
            assertEquals(setOf(41L), draft.selected); assertNull(draft.groupId)
            assertEquals("Bleibt erhalten", draft.text); assertEquals("Entwurf", draft.title)
            assertEquals(0, scene.sends); assertTrue(scene.callbacks.isEmpty())
        }
    }

    @Test fun aGroupCanStartANewDraft() {
        scene.start(); scene.tap("tree-to-group-70")
        rule.runOnIdle {
            assertEquals(70L, scene.draft.value!!.groupId)
            assertTrue(scene.draft.value!!.selected.isEmpty())
            assertTrue(scene.callbacks.isEmpty()); assertEquals(0, scene.sends)
        }
        scene.tap("tree-write-to"); scene.selected("tree-to-group-70", true)
    }

    @Test fun withoutWriterTheExistingCallbackReceivesThePersonIdOnce() {
        scene.start(withWriter = false)
        assertEquals(Role.Button, scene.tag("tree-to-41").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
        scene.tap("tree-to-41")
        rule.runOnIdle { assertEquals(listOf(41L), scene.callbacks); assertNull(scene.draft.value) }
    }

    private fun checkLayout(fontScale: Float) {
        scene.start(fontScale)
        val density = rule.activity.resources.displayMetrics.density
        val tags = PostPilotFixtures.pals.map { "tree-to-${it.id}" } + "tree-to-group-70"
        for (t in tags) {
            scene.reveal(t)
            val bounds = scene.tag(t).fetchSemanticsNode().boundsInRoot
            val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
            assertTrue("$t target $bounds", bounds.width >= 48 * density - 1 && bounds.height >= 48 * density - 1)
            assertTrue("$t on screen $bounds", bounds.left >= root.left - 1 && bounds.right <= root.right + 1 &&
                bounds.top >= root.top - 1 && bounds.bottom <= root.bottom + 1)
            val label = scene.tag("$t-label").fetchSemanticsNode()
            val layouts = mutableListOf<TextLayoutResult>()
            assertTrue(label.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts))
            assertTrue("$t has text layout", layouts.isNotEmpty())
            for (l in layouts) {
                assertFalse("$t text overflow", l.hasVisualOverflow)
                assertTrue("$t text in button", label.boundsInRoot.left >= bounds.left - 1 &&
                    label.boundsInRoot.right <= bounds.right + 1 && label.boundsInRoot.top >= bounds.top - 1 &&
                    label.boundsInRoot.bottom <= bounds.bottom + 1)
            }
        }
        scene.reveal("tree-to-40")
        val a = scene.tag("tree-to-40").fetchSemanticsNode().boundsInRoot
        val b = scene.tag("tree-to-41").fetchSemanticsNode().boundsInRoot
        assertEquals(a.top, b.top, 1f); assertTrue("separate finger targets", a.right < b.left)
    }

    @Test fun normalButtonsFitAndHaveWholeNames() = checkLayout(1f)
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    @Test fun smallButtonsFitAtFontScale15AndHaveWholeNames() = checkLayout(1.5f)
}
