package at.gregor.layermaxxing

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.createAndroidComposeRule

import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The former list pilot's complete reachability contract, now through real paged scene objects.
 * Counts, people × status, gates, callbacks and retained filters remain assertions; no legacy UI is kept for tests.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-hdpi")
class PostCleanPilotTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private var level by mutableStateOf(1)
    @Test fun compactStageControlsKeep48DpInsideTheClickableInk() = checkCompactControls(false)
    @Test fun compactStageControlPickersOpenByPhysicalTap() = checkCompactControls(true)
    private fun checkCompactControls(tapPickers: Boolean) {
        val d = NativePostTestDriver(rule)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                androidx.compose.runtime.key(level) {
                    VillageTheme(true) { PostPilotFixtures.Post(level) }
                }
            }
        }
        for (l in 1..3) {
            rule.runOnIdle { level = l; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            d.frames(40)
            val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
            val px = rule.activity.resources.displayMetrics.density
            for (name in listOf("post-person", "post-status", "post-mail", "post-time-rail")) {
                val r = d.tag(name).fetchSemanticsNode().boundsInRoot
                assertTrue("$l $name actual clickable size $r", r.width >= 48 * px - 1 && r.height >= 48 * px - 1)
                assertTrue("$l $name contained $r", r.left >= 0 && r.right <= root.right && r.top >= 64 * px && r.bottom <= root.bottom)
            }
            System.getenv("POSTAL_REPAIR_SHOTS")?.let { out ->
                val view = rule.activity.window.decorView
                val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
                rule.runOnIdle { view.draw(android.graphics.Canvas(bitmap)) }
                val file = java.io.File(out, "compact-stage-$l.png"); file.parentFile.mkdirs()
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            }
            if (tapPickers) {
                d.tag("post-person").performTouchInput { click(center) }; d.frames()
                assertTrue("$l person picker opens", d.exists("post-person-search")); d.tag("post-selector-close").performTouchInput { click(center) }; d.frames()
                d.tag("post-status").performTouchInput { click(center) }; d.frames()
                assertTrue("$l status picker opens", d.exists("tree-window-UNREAD")); d.tag("post-selector-close").performTouchInput { click(center) }; d.frames()
                d.tag("post-mail").performTouchInput { click(center) }; d.frames()
                assertTrue("$l mail picker opens", d.exists("post-search")); d.tag("post-selector-close").performTouchInput { click(center) }; d.frames()
            }
        }
    }

    @Test fun oneBackDismissesEveryCompactPickerWithoutLeavingOrChangingTheStage() {
        val d = NativePostTestDriver(rule)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                androidx.compose.runtime.key(level) { VillageTheme(true) { PostPilotFixtures.Post(level) } }
            }
        }
        for (l in 1..3) {
            rule.runOnIdle { level = l; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            d.frames()
            val person = d.description("post-person")
            val status = d.description("post-status")
            val rail = d.description("post-time-rail")
            for (picker in listOf("post-mail", "post-person", "post-status")) {
                d.tap(picker)
                assertTrue("POST$l $picker opened", d.exists("post-selector"))
                d.back() // Exactly one dispatcher action; no retry or fallback close button.
                assertFalse("POST$l $picker dismissed by one Back", d.exists("post-selector"))
                assertTrue("POST$l remains at rest", d.exists("tree-scene-REST"))
                assertFalse(rule.activity.isFinishing)
                assertEquals(person, d.description("post-person"))
                assertEquals(status, d.description("post-status"))
                assertEquals(rail, d.description("post-time-rail"))
            }
        }
    }

    @Test fun onEveryPostStageTheRestIsCleanAndEveryFunctionIsOneObjectAway() {
        val d = NativePostTestDriver(rule)
        val opened = mutableListOf<Long>(); val locked = mutableListOf<Long>()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                androidx.compose.runtime.key(level) {
                    VillageTheme(true) { PostPilotFixtures.Post(level, onOpen = { opened += it.id }, onLocked = { locked += it.id }) }
                }
            }
        }
        for (l in 1..3) {
            rule.runOnIdle { level = l; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            d.frames()
            opened.clear(); locked.clear()
            val letters = PostPilotFixtures.letters
            val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
            val px = rule.activity.resources.displayMetrics.density
            assertFalse("no generic modal or function wall", d.exists("post-letters") || d.exists("post-people") || d.exists("tree-page"))
            assertTrue(d.keys("person:").isEmpty())
            for (name in listOf("post-person", "post-status", "post-mail", "post-time-rail")) {
                val r = d.tag(name).fetchSemanticsNode().boundsInRoot
                assertTrue("$l $name $r", r.width >= 48 * px - 1 && r.height >= 48 * px - 1)
                assertTrue("$l $name visible", r.left >= -1 && r.right <= root.right + 1 && r.top >= 64 * px - 1 && r.bottom <= root.bottom + 1)
                assertTrue(d.description(name).isNotBlank())
            }
            assertEquals(letters.map { "letter:${it.id}" }.toSet(), d.letters())
            if (d.exists("tree-time-now")) d.tap("tree-time-now")
            d.tap("post-person")
            assertEquals((listOf("person:alle") + PostPilotFixtures.pals.map { "person:${it.id}" }).toSet(), d.people())
            d.person(40)
            val mira = PostViews.shown(letters, emptyMap<Long, Any>(), PostViews.View.ALL, 40, "")
            assertEquals(mira.map { "letter:${it.id}" }.toSet(), d.letters())
            if (d.exists("tree-time-now")) d.tap("tree-time-now")
            d.tap("post-status")
            val counts = PostViews.counts(letters, emptyMap<Long, Any>(), 40, "")
            for (v in PostViews.View.entries) {
                d.scrollTo("tree-window-${v.name}")
                assertEquals("${v.label}: ${counts[v]}", d.description("tree-window-${v.name}"))
                val r = d.tag("tree-window-${v.name}").fetchSemanticsNode().boundsInRoot
                assertTrue("$l ${v.label} visible $r", r.left >= -1 && r.right <= root.right + 1 && r.top >= 64 * px - 1 && r.bottom <= root.bottom + 1)
            }
            d.scrollTo("tree-window-UNREAD")
            d.tap("tree-window-UNREAD")
            val both = PostViews.shown(letters, emptyMap<Long, Any>(), PostViews.View.UNREAD, 40, "")
            assertTrue(both.isNotEmpty())
            assertEquals(both.map { "letter:${it.id}" }.toSet(), d.letters())
            assertTrue(d.description("post-person").contains("Mira"))
            val first = both.first()
            d.letter(first.id)
            val state = Conversations.letterState(first, false)
            if (!state.locked) {
                assertTrue("extra full envelope tap", d.exists("tree-envelope")); assertTrue(opened.isEmpty())
                d.tap("tree-envelope")
            }
            if (state.locked) { assertTrue(d.exists("post-arrival")); d.tap("post-arrival-action") }
            assertEquals(if (state.locked) listOf(first.id) else emptyList<Long>(), locked)
            assertEquals(if (state.locked) emptyList<Long>() else listOf(first.id), opened)
            d.back()
            assertFalse(d.exists("tree-letter-text") || d.exists("tree-lifted"))
            d.tap("post-person"); assertTrue(d.keys("person:").isNotEmpty()); d.back()
            assertTrue(d.keys("person:").isEmpty())
            d.tap("post-status")
            assertEquals("Ungelesen: ${counts[PostViews.View.UNREAD]}", d.description("tree-window-UNREAD"))
            d.back()
            assertTrue(d.description("post-person").contains("Mira"))
        }
    }
}
