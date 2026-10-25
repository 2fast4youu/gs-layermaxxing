package at.gregor.layermaxxing

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.IOException
import androidx.compose.ui.semantics.getOrNull
import org.junit.rules.TestName
import java.util.concurrent.atomic.AtomicInteger

/** Only the HTTP transport is fake. ApiClient authorization/parsing, authenticated crypto, the
 * account opener and LetterTreePlace are the same production path as LayerHome. Never uses a socket. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-hdpi")
class Post0RedesignPilotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val testName = TestName()
    private val level = 0
    private var fontScale = 1f
    private var activeMail by mutableStateOf<List<ApiClient.Message>>(emptyList())
    private val calls = AtomicInteger()
    private val unreadCalls = AtomicInteger()
    private val cancelledTransport = AtomicInteger()
    private val opened = mutableStateMapOf<Long, OpenedMessage>()
    private val draft = mutableStateOf<LetterDraft?>(null)
    private lateinit var opener: LetterOpener
    @Volatile private var release = false
    @Volatile private var status = 200
    @Volatile private var brokenCrypto = false
    @Volatile private var offline = false
    private var droppedCallback = false
    private var pointer = "none"
    private val syntheticNames = listOf("Mira Fuchs", "Jonas Berg", "Hannah Lind", "Noah Stein", "Emilia Wald", "Paul Bach", "Lina Wolf", "Felix Blum", "Clara Hahn",
        "Theo Brand", "Ida Sturm", "Leon Herbst", "Ella Braun", "Finn Moos", "Nora Kern", "Luis Stern", "Emma Licht", "Ben Tal", "Mia Eber", "Alexandra Sommer vom Waldrand")
    private val people = syntheticNames.mapIndexed { i, name -> PostPilotFixtures.pals.first().copy(id = 101L + i, name = name) }
    private val mail = (1..120).map { PostPilotFixtures.letters.first().copy(id = it.toLong(), peerId = people[(it - 1) % 20].id,
        peerName = people[(it - 1) % 20].name, incoming = true, unlocked = true, readAt = null,
        markedUnread = false, oneTime = false, createdAt = 2000000000L - it * 3600L) }
    private fun node(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true)
    private fun exists(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun phase() = ScenePhase.entries.first { exists("tree-scene-${it.name}") }
    private fun frames(n: Int = 3) {
        repeat(n) {
            rule.runOnIdle { Snapshot.sendApplyNotifications() }
            rule.mainClock.advanceTimeByFrame()
            ShadowLooper.idleMainLooper()
        }
        rule.waitForIdle()
    }
    private fun until(target: ScenePhase) {
        repeat(240) { frames(2); if (phase() == target) return; Thread.sleep(5) }
        assertEquals(target, phase())
    }
    private fun tap(tag: String) {
        if (tag in listOf("post-person", "post-status", "post-mail") && !exists(tag) && exists("post-filter")) {
            node("post-filter").performTouchInput { click(center) }; frames()
        }
        node(tag).performTouchInput { click(center) }; frames() }
    private fun at(x: Float, y: Float) {
        val before = phase()
        var actual = Offset.Zero
        rule.onRoot().performTouchInput { actual = Offset(width * x, height * y); click(actual) }
        frames()
        pointer = "${actual.x}:${actual.y},$before,${phase()},${calls.get()}"
        println("PostalPhoneRepair pointer=$pointer")
    }
    private fun awaitCalls(expected: Int) {
        repeat(100) { if (calls.get() >= expected) return; frames(); Thread.sleep(5) }
        assertEquals(expected, calls.get())
    }
    private fun failed(expectedCalls: Int = 1) {
        awaitCalls(expectedCalls)
        repeat(100) { if (opener.requests[1] != OpenRequest.FAILED) { frames(); Thread.sleep(5) } }
        until(ScenePhase.SEALED)
        assertEquals(OpenRequest.FAILED, opener.requests[1]); assertNull(opened[1])
    }
    private fun back() { rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }; frames() }
    private fun start(letters: List<ApiClient.Message> = mail, timeout: Long = 45000, restore: StateRestorationTester? = null) {
        activeMail = letters
        val key = CryptoBox.encrypt("synthetic key material").key
        val aad = "synthetic canonical metadata"
        val encrypted = CryptoBox.encryptBytes("SYNTHETIC verified reading content".toByteArray(), key, aad.toByteArray())
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            check(chain.request().header("Authorization") == "Bearer synthetic-postal-only")
            if (chain.request().url.encodedPath.endsWith("/unread")) {
                unreadCalls.incrementAndGet()
                return@addInterceptor Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                    .message("Synthetic unread response").body("{}".toResponseBody()).build()
            }
            calls.incrementAndGet()
            check(chain.request().url.encodedPath.endsWith("/content"))
            val end = System.nanoTime() + 10_000_000_000L
            while (!release && !chain.call().isCanceled() && System.nanoTime() < end) Thread.sleep(5)
            if (chain.call().isCanceled()) { cancelledTransport.incrementAndGet(); throw IOException("synthetic cancellation") }
            if (!release || offline) throw IOException("synthetic offline")
            val json = JSONObject().put("ciphertext", if (brokenCrypto) "invalid" else encrypted.ciphertext)
                .put("nonce", encrypted.nonce).put("encryption_key", key).put("canonical_metadata", aad)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status)
                .message("Synthetic response").body(json.toString().toResponseBody()).build()
        }.build()
        val api = ApiClient("https://postal.invalid/", transport)
        rule.mainClock.autoAdvance = false
        val content: @Composable () -> Unit = {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                VillageTheme(true) {
                    val actual = rememberLetterOpener(opened, { openLetterContent(api, "synthetic-postal-only", it) }, {}, {}, timeout)
                    SideEffect { opener = actual }
                    val marker = rememberUnreadMarker(opened, { api.markUnread("synthetic-postal-only", it.id) }, {}, {})
                    val writer = remember { LetterWriter(people, emptyList(), emptyList(), false,
                        transmit = { error("No sends authorized in this test") }, onSent = {}, draftState = draft) }
                    LetterTreePlace(people, activeMail, opened, onOpen = { if (!droppedCallback) actual.open(it) },
                        onLocked = {}, onWrite = {}, onClose = {}, requests = actual.requests, writer = writer, unread = marker, level = level)
                }
            }
        }
        if (restore == null) rule.setContent(content) else restore.setContent(content)
        frames(30)
    }
    private fun select() { tap("tree-letter-1"); until(ScenePhase.SEALED); assertEquals(0, calls.get()) }
    private fun capture(name: String) {
        val directory = System.getenv("POST0_REDESIGN_SHOTS")?.takeIf { it.isNotBlank() } ?: return
        frames()
        val out = File(directory, "post0-redesign-${fontScale}/${testName.methodName}").also { it.mkdirs() }
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { view.draw(Canvas(bitmap)) }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val csv = File(out, "actual-frames.csv")
        if (!csv.exists()) csv.writeText("frame,clock_ms,phase,transport_calls,pointer_x_px:pointer_y_px,before_pointer,after_pointer,calls_after_pointer\n")
        csv.appendText("$name,${rule.mainClock.currentTime},${phase()},${calls.get()},$pointer\n")
        File(out, "metadata.txt").writeText("SYNTHETIC native Robolectric View.draw. Production LetterTreePlace / LetterOpener / ApiClient / AES-GCM with AAD; fake OkHttp interceptor only. No sockets/accounts. level=$level fontScale=$fontScale. Explicit per-frame Compose clock, Snapshot notifications and main looper. Actual root bounds=${rule.onRoot().fetchSemanticsNode().boundsInRoot}. PNG and CSV are test output, not phone/APK evidence.")
    }

    @Test fun normal411ProductionPilot() = pilot()

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-hdpi")
    fun compact360LargeFontProductionPilot() { fontScale = 1.5f; pilot() }

    private fun pilot() {
        start(); capture("rest120")
        val d = NativePostTestDriver(rule)
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val px = rule.activity.resources.displayMetrics.density
        fun contained(tag: String) {
            val r = node(tag).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag inside actual root: $r", r.left >= root.left && r.right <= root.right && r.top >= root.top && r.bottom <= root.bottom)
        }
        val entry = node("post-filter").fetchSemanticsNode().boundsInRoot
        assertTrue(entry.width >= 48 * px - 1 && entry.height >= 48 * px - 1)
        for (t in listOf("post-person", "post-status", "post-mail", "post-controls")) node(t).assertDoesNotExist()
        contained("post-filter"); contained("post-time-label"); contained("tree-wax-write-label")
        assertEquals("e_post_0_lay_sheet", node("post-filter-surface").fetchSemanticsNode().config[PostalMenuMaterialKey])
        for (tag in listOf("post-filter-ink", "post-time-label", "tree-wax-writing-cue")) {
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            node(tag).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse("Readable control $tag: ${layouts.single()}", layouts.single().hasVisualOverflow)
        }
        for (m in mail.take(9)) {
            contained("tree-env-${m.id}")
            val envelope = node("tree-env-${m.id}").fetchSemanticsNode()
            assertTrue("Keep the existing native SceneEnvelope, not a replacement paper card", envelope.config.contains(StoragePoseKey))
            assertTrue("Keep the existing landscape envelope proportions", envelope.boundsInRoot.width > envelope.boundsInRoot.height * 1.5f)
            node("post-identity-${m.id}").assertDoesNotExist()
            assertTrue(node("tree-letter-${m.id}").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].any { it.contains(m.peerName) })
            assertFalse("Writing cue must not cover a mail face", node("tree-wax-writing-cue").fetchSemanticsNode().boundsInRoot
                .overlaps(node("tree-env-${m.id}").fetchSemanticsNode().boundsInRoot))
        }
        capture("rest120")
        tap("post-filter"); capture("filteropen")
        assertEquals("e_post_0_lay_sheet", node("post-selector").fetchSemanticsNode().config[PostalMenuMaterialKey])
        contained("post-selector-close")
        tap("post-selector-close"); node("post-selector").assertDoesNotExist(); tap("post-filter")
        for (t in listOf("post-person", "post-status", "post-mail")) node(t).assertExists()
        node("post-search").performTextInput("Hannah"); frames(); capture("filter-search")
        rule.onNodeWithText("${mail.count { it.peerName.contains("Hannah") }} Briefe durchsuchen", useUnmergedTree = true).assertExists()
        tap("post-filter-reset")
        assertEquals("", node("post-search").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
        rule.onNodeWithText("${mail.size} Briefe durchsuchen", useUnmergedTree = true).assertExists()
        node("post-filter-reset").assertDoesNotExist()
        tap("post-person"); assertEquals(21, d.people().size); capture("filter-person")
        node("post-person-search").performTextInput("Mira"); frames()
        assertEquals(2, d.people().size)
        tap("tree-strip-101"); until(ScenePhase.REST)
        assertEquals(mail.filter { it.peerId == 101L }.map { it.id }, node("tree-scene-REST").fetchSemanticsNode().config[PostalVisibleMailIdsKey])
        tap("post-filter"); tap("post-filter-reset"); back()
        tap("post-status")
        PostViews.View.entries.forEach {
            node("tree-window-${it.name}").assertIsDisplayed()
            assertTrue("Each status row stays readable in the viewport", node("tree-window-${it.name}").fetchSemanticsNode().boundsInRoot.height >= 48 * px - 1)
        }
        capture("filter-status")
        val chosenView = PostViews.View.entries.first { it != PostViews.View.ALL }
        tap("tree-window-${chosenView.name}"); until(ScenePhase.REST)
        tap("post-filter"); rule.onNodeWithText(chosenView.label, useUnmergedTree = true).assertExists()
        tap("post-filter-reset"); back()
        tap("post-mail"); assertEquals(120, d.keys("letter:").size); back()
        d.letter(20); until(ScenePhase.SEALED)
        rule.onNodeWithText("von Alexandra Sommer vom Waldrand", useUnmergedTree = true).assertExists()
        assertEquals(0, calls.get()); at(.995f, .5f); until(ScenePhase.REST)
        val touchRail = node("post-time-rail").fetchSemanticsNode().boundsInRoot
        val paintedKnob = node("post-time-knob").fetchSemanticsNode().boundsInRoot
        assertTrue("The actual painted knob must belong to its drag target", touchRail.contains(paintedKnob.center))
        node("post-time-rail").performTouchInput {
            down(paintedKnob.center - touchRail.topLeft)
            moveTo(Offset(width * .27f, height / 2f), 300)
        }
        frames(3)
        assertEquals(13, node("tree-scene-REST").fetchSemanticsNode().config[PostalTimePageKey])
        val faces = rule.onAllNodes(SemanticsMatcher("held face") { it.config.getOrNull(PostalPageSlideKey) != null }, true).fetchSemanticsNodes()
        assertEquals(3, faces.size); assertTrue(faces.all { it.config[PostalPageSlideKey] == 0f })
        assertTrue("Time navigation must keep the original native envelopes", faces.all { it.config.contains(StoragePoseKey) })
        contained("post-time-knob"); capture("dragheld")
        node("post-time-rail").performTouchInput { moveTo(Offset(width * .73f, height / 2f), 300); up() }; frames(40)
        assertEquals(0, node("tree-scene-REST").fetchSemanticsNode().config[PostalTimePageKey])
        assertEquals(0, calls.get())
        val rail = node("post-time-rail").fetchSemanticsNode().boundsInRoot
        val knob = node("post-time-knob").fetchSemanticsNode().boundsInRoot
        val scale = rail.width / 261f
        assertEquals(rail.left + 190.5f * scale, knob.center.x, 1.5f)
        assertEquals(rail.top + 78.4f * scale, knob.center.y, 1.5f)
        select(); capture("selectedsealed")
        release = true
        at(.5f, .5f); until(ScenePhase.READING)
        assertEquals(1, calls.get())
        node("tree-letter-text").assertTextContains("SYNTHETIC verified reading content", substring = true)
        capture("reading")
        at(.5f, .995f); until(ScenePhase.REST)
        rule.runOnIdle { activeMail = emptyList(); Snapshot.sendApplyNotifications() }; frames(40)
        capture("rest0mail")
        // The same production writer survives closing and reopening through bucket and pen.
        tap("tree-wax-bucket"); frames(50); tap("tree-to-101"); frames(50)
        node("tree-write-text").performTextInput("SYNTHETIC retained draft"); frames(); capture("composer0mail")
        back(); frames(60); tap("tree-pen"); frames(60)
        node("tree-write-text").assertTextContains("SYNTHETIC retained draft", substring = true)
        assertEquals(1, calls.get())
    }
}
