package at.gregor.layermaxxing

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
 * 1011 clean pilot, opt-in renders for Veit's design review (skipped unless VILLAGE_SHOTS is an output directory;
 * filter '*Post1011PilotReview*'): the post at rest on its four stages and the close-ups reached by REAL taps on the
 * painted objects (drawer, stamp sheet, brass slider, a stamp). TESTDATEN: invented people and letters (PostPilotFixtures),
 * no account, no server. Real Android windows drawn on Robolectric (JVM), not device screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class Post1011PilotReview {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val out = System.getenv("VILLAGE_SHOTS")?.let(::File)

    private fun frames(n: Int = 100) { repeat(n) { rule.mainClock.advanceTimeByFrame() }; org.robolectric.shadows.ShadowLooper.idleMainLooper() }
    private fun save(name: String) {
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        out!!.mkdirs()
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }
    private fun open(level: Int, small: Boolean = false) {
        assumeTrue(out != null)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val content: @Composable () -> Unit = { VillageTheme(true) { PostPilotFixtures.Post(level) } }
            if (small) {
                val d = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 1.5f)) { content() }
            } else content()
        }
        frames()
    }
    private fun tap(tag: String) { rule.onNodeWithTag(tag, useUnmergedTree = true).performClick(); frames() }
    private fun tapSlot(key: String) {
        rule.onNode(SemanticsMatcher(key) { it.config.getOrNull(SceneSlotKey) == key }, useUnmergedTree = true).performClick(); frames()
    }

    @Test fun rest0() { open(0); save("p1011-post0-ruhe") }
    @Test fun rest1() { open(1); save("p1011-post1-ruhe") }
    @Test fun rest2() { open(2); save("p1011-post2-ruhe") }
    @Test fun rest3() { open(3); save("p1011-post3-ruhe") }
    @Test fun drawer1() { open(1); tap("tree-letter-1"); save("p1011-post1-schub-offen") }
    @Test fun stamps1() { open(1); tap("tree-rope"); save("p1011-post1-markenbogen") }
    @Test fun slider1() { open(1); tap("tree-belt"); save("p1011-post1-messingschieber") }
    @Test fun stampThenDrawer1() {
        // tap the stamp sheet, then Mira's stamp: the drawer opens with Mira's letters; then the slider: Ungelesen
        open(1); tap("tree-rope"); tapSlot("person:40"); save("p1011-post1-mira-im-schub")
        tap("tree-belt"); rule.onNodeWithTag("tree-window-UNREAD", useUnmergedTree = true).performClick(); frames()
        save("p1011-post1-mira-ungelesen")
    }
    @Test fun drawer3() { open(3); tap("tree-letter-1"); save("p1011-post3-ablage-offen") }
    @Test fun drawer0() { open(0); tap("tree-letter-1"); save("p1011-post0-stamm-nah") }
    @Config(qualifiers = "w360dp-h640dp-xhdpi") @Test fun rest1Small() { open(1, small = true); save("p1011-post1-ruhe-klein-font15") }
    @Config(qualifiers = "w360dp-h640dp-xhdpi") @Test fun drawer1Small() { open(1, small = true); tap("tree-letter-1"); save("p1011-post1-schub-offen-klein-font15") }
    @Config(qualifiers = "w360dp-h640dp-xhdpi") @Test fun stamps1Small() { open(1, small = true); tap("tree-rope"); save("p1011-post1-markenbogen-klein-font15") }
    @Config(qualifiers = "w360dp-h640dp-xhdpi") @Test fun slider2Small() { open(2, small = true); tap("tree-belt"); save("p1011-post2-messingschieber-klein-font15") }
}

/**
 * TESTDATEN for the 1011 post pilot: eight invented people, fourteen letters in every state the post knows (arrived and
 * unread, read, sent and travelling, sent and read by the recipient, sealed until both agree, timed, random). Never live
 * API answers.
 */
internal object PostPilotFixtures {
    private val now = 1_790_000_000L
    val pals = listOf("Mira", "Jonte", "Ilka", "Bosse", "Fenja", "Okko", "Tale", "Rune").mapIndexed { i, n ->
        ApiClient.UserSummary(40L + i, n, "friends", listOf("🦊", "🐻", "🦉", "🐺", "🐰", "🦔", "🐢", "🦡")[i], "#2E7D32")
    }
    private fun m(id: Long, peer: Int, incoming: Boolean, mode: String, unlocked: Boolean, readAt: Long? = null, release: Long? = null) = ApiClient.Message(
        id, pals[peer].id, pals[peer].name, incoming, "Brief $id", "", "none", now - id * 3600, mode, release, null, null, unlocked, false, readAt,
        false, false, null, null, null, emptyList(), null, null, null)
    val letters = listOf(
        m(1, 0, true, "manual", true), m(2, 0, true, "manual", true, readAt = now - 60), m(3, 0, false, "timed", false, release = now + 86_400),
        m(4, 1, true, "mutual", false), m(5, 1, false, "manual", true, readAt = now - 30), m(6, 2, true, "manual", true),
        m(7, 2, false, "random", false), m(8, 3, true, "timed", false, release = now + 3 * 86_400), m(9, 4, true, "manual", true, readAt = now - 90),
        m(10, 5, false, "manual", true), m(11, 6, true, "manual", true), m(12, 7, false, "mutual", false), m(13, 3, true, "manual", true, readAt = now - 10),
        m(14, 0, true, "manual", true),
    )
    @Composable fun Post(level: Int, onOpen: (ApiClient.Message) -> Unit = {}, onLocked: (ApiClient.Message) -> Unit = {}, startFocus: PostFocus = PostFocus.REST) =
        PostOffice(pals, letters, emptyMap(), onOpen, onLocked, onWrite = {}, onClose = {}, level = level, startFocus = startFocus)
}
