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
class PostAllStagesProductionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val testName = TestName()
    private var readingText = "TESTDATA verified reading content"
    private var level = 0
    private var fontScale = 1f
    private var activeMail by mutableStateOf<List<ApiClient.Message>>(emptyList())
    private val calls = AtomicInteger()
    private val unreadCalls = AtomicInteger()
    private val cancelledTransport = AtomicInteger()
    private val opened = mutableStateMapOf<Long, OpenedMessage>()
    private var draft = mutableStateOf<LetterDraft?>(null)
    private var placeMounted by mutableStateOf(true)
    private var allowSends = false
    private val sentBodies = java.util.concurrent.CopyOnWriteArrayList<JSONObject>()
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
        peerName = people[(it - 1) % 20].name, title = "TESTDATA letter $it", incoming = true, unlocked = true, readAt = null,
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
        val aad = "TESTDATA canonical metadata"
        val encrypted = CryptoBox.encryptBytes(readingText.toByteArray(), key, aad.toByteArray())
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            check(chain.request().header("Authorization") == "Bearer synthetic-postal-only")
            if (chain.request().url.encodedPath.endsWith("/messages")) {
                check(allowSends)
                val buffer = okio.Buffer(); chain.request().body!!.writeTo(buffer)
                sentBodies += JSONObject(buffer.readUtf8())
                return@addInterceptor Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                    .message("Synthetic send").body("{\"ids\":[9001]}".toResponseBody()).build()
            }
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
                    val savedDraft = androidx.compose.runtime.saveable.rememberSaveable(saver = LetterDraft.StateSaver) { draft }
                    SideEffect { draft = savedDraft }
                    val writer = remember { LetterWriter(people, emptyList(), emptyList(), false,
                        transmit = { check(allowSends); api.send("synthetic-postal-only", it) }, onSent = {}, draftState = savedDraft) }
                    if (placeMounted) LetterTreePlace(people, activeMail, opened, onOpen = { if (!droppedCallback) actual.open(it) },
                        onLocked = {}, onWrite = {}, onClose = {}, requests = actual.requests, writer = writer, unread = marker, level = level)
                }
            }
        }
        if (restore == null) rule.setContent(content) else restore.setContent(content)
        frames(30)
    }
    private fun select() { tap("tree-letter-1"); until(ScenePhase.SEALED); assertEquals(0, calls.get()) }
    private fun capture(name: String) {
        val directory = (System.getenv("POST_BATCH84_SHOTS") ?: System.getenv("POST_ALL_STAGE_SHOTS"))?.takeIf { it.isNotBlank() } ?: return
        frames()
        val out = File(directory, "post$level-${fontScale}/${testName.methodName}").also { it.mkdirs() }
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { view.draw(Canvas(bitmap)) }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        if (exists("tree-letter-text")) {
            val metrics = File(out, "reading-layout.csv")
            if (!metrics.exists()) metrics.writeText("frame,tag,font_sp,line_height_sp,left,top,right,bottom,text_length,lines\n")
            for (tag in listOf("tree-letter-sender", "tree-letter-date", "tree-letter-text")) {
                if (!exists(tag)) continue
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                node(tag).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val layout = layouts.single(); val bounds = node(tag).fetchSemanticsNode().boundsInRoot
                metrics.appendText("$name,$tag,${layout.layoutInput.style.fontSize.value},${layout.layoutInput.style.lineHeight.value},${bounds.left},${bounds.top},${bounds.right},${bounds.bottom},${layout.layoutInput.text.length},${layout.lineCount}\n")
            }
        }
        if (phase() == ScenePhase.REST) File(out, "batch-frames.csv").appendText("$name,${rule.mainClock.currentTime},${page()},${ids().joinToString(";")}\n")
        val csv = File(out, "actual-frames.csv")
        if (!csv.exists()) csv.writeText("frame,clock_ms,phase,transport_calls,pointer_x_px:pointer_y_px,before_pointer,after_pointer,calls_after_pointer\n")
        csv.appendText("$name,${rule.mainClock.currentTime},${phase()},${calls.get()},$pointer\n")
        File(out, "metadata.txt").writeText("SYNTHETIC native Robolectric View.draw. Production LetterTreePlace / LetterOpener / ApiClient / AES-GCM with AAD; fake OkHttp interceptor only. No sockets/accounts. level=$level fontScale=$fontScale. Explicit per-frame Compose clock, Snapshot notifications and main looper. Actual root bounds=${rule.onRoot().fetchSemanticsNode().boundsInRoot}. PNG and CSV are test output, not phone/APK evidence.")
    }

    private fun stampPixels(id:Long): IntArray {
        val bounds=node("post-stamp-$id").fetchSemanticsNode().boundsInRoot
        val view=rule.activity.findViewById<android.view.View>(android.R.id.content)
        val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        rule.runOnIdle { view.draw(Canvas(image)) }
        val left=(bounds.left+bounds.width*.77f).toInt().coerceIn(0,image.width-1)
        val top=(bounds.top+bounds.height*.53f).toInt().coerceIn(0,image.height-1)
        val right=(bounds.left+bounds.width*.96f).toInt().coerceIn(left+1,image.width)
        val bottom=(bounds.top+bounds.height*.88f).toInt().coerceIn(top+1,image.height)
        val pixels=IntArray((right-left)*(bottom-top))
        image.getPixels(pixels,0,right-left,left,top,right-left,bottom-top)
        image.recycle()
        assertTrue("Actual native stamp region must contain drawn ink/paper detail",pixels.distinct().size>4)
        return pixels
    }

    @Test fun post0Normal411() = pilot(0)
    @Test fun post1Normal411() = pilot(1)
    @Test fun post2Normal411() = pilot(2)
    @Test fun post3Normal411() = pilot(3)
    @Test @Config(qualifiers = "w360dp-h640dp-hdpi") fun post0Compact360() = pilot(0, 1.5f)
    @Test @Config(qualifiers = "w360dp-h640dp-hdpi") fun post1Compact360() = pilot(1, 1.5f)
    @Test @Config(qualifiers = "w360dp-h640dp-hdpi") fun post2Compact360() = pilot(2, 1.5f)
    @Test @Config(qualifiers = "w360dp-h640dp-hdpi") fun post3Compact360() = pilot(3, 1.5f)
    @Test @Config(qualifiers = "w320dp-h640dp-hdpi") fun post3Compact320() = pilot(3, 1.5f)

    @Test fun receivedStablePost0() = stableReceived(0)
    @Test fun receivedStablePost1() = stableReceived(1)
    @Test fun receivedStablePost2() = stableReceived(2)
    @Test @Config(qualifiers = "w320dp-h640dp-hdpi") fun receivedStablePost3Compact320() = stableReceived(3, 1.5f)
    private fun stableReceived(stage: Int, scale: Float = 1f) {
        level = stage; fontScale = scale
        val restore = StateRestorationTester(rule)
        val sent = mail.take(8).map { it.copy(id=it.id+1000, incoming=false, createdAt=it.createdAt+999999) }
        start(mail + sent, restore=restore)
        val original = ids()
        assertEquals(listOf(9,4,6,6)[stage],original.size)
        assertTrue(original.all { it < 1000 })
        val stamp = node("post-stamp-${original.first()}").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription]
        rule.onNodeWithText("Schreiben").assertDoesNotExist()
        capture("received-anchored")
        val stampBefore=stampPixels(original.first())
        tap("tree-letter-${original.first()}"); until(ScenePhase.SEALED)
        release=true; at(.5f,.5f); until(ScenePhase.READING)
        NativePostTestDriver(rule).scrollTo("tree-letter-unread"); tap("tree-letter-unread")
        until(ScenePhase.SEALED)
        assertEquals(1,unreadCalls.get())
        back(); until(ScenePhase.REST); assertEquals(original,ids())
        rule.runOnIdle {
            opened[original.first()] = OpenedMessage("SYNTHETIC cached open",null,null,null)
            activeMail = (mail + sent).map { it.copy(readAt=2000000010L, markedUnread=it.id % 2L == 0L) } +
                mail.first().copy(id=9999,createdAt=2000005000L)
        }; frames(40)
        assertEquals(original,ids())
        assertEquals(stamp,node("post-stamp-${original.first()}").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription])
        assertArrayEquals("Native stamp pixels remain stable after read/unread/new mail refresh",stampBefore,stampPixels(original.first()))
        restore.emulateSavedInstanceStateRestore(); frames(40); assertEquals(original,ids())
        val survivor=original[1]
        val survivorBefore=node("tree-env-$survivor").fetchSemanticsNode().boundsInRoot
        rule.runOnIdle { activeMail = activeMail.filter { it.id != original.last() && it.id != original.first() } }; frames(40)
        assertEquals(original.drop(1).dropLast(1),ids())
        assertEquals(survivorBefore,node("tree-env-$survivor").fetchSemanticsNode().boundsInRoot)
        node("tree-letter-${original.last()}").assertDoesNotExist()
        // Accessibility detents invoke the same actual navigation as the knob.
        node("post-time-rail").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(1f) }; frames(40)
        assertEquals(9999L,ids().first()); assertFalse(ids().any { it in sent.map { m -> m.id } })
        tap("post-filter"); tap("post-status"); tap("tree-window-UNREAD"); frames(40)
        assertTrue(ids().all { id -> activeMail.first { it.id==id }.let { it.readAt==null || it.markedUnread } })
        rule.runOnIdle { activeMail = sent }; frames(40)
        assertTrue(ids().isEmpty())
        node("post-empty").assertTextContains("Noch keine empfangenen Briefe")
        assertEquals(1,calls.get()); assertEquals(1,unreadCalls.get()); assertTrue(sentBodies.isEmpty())
        tap(if (stage==0) "tree-wax-bucket" else "tree-pen"); frames(50)
        assertTrue(exists("tree-page"))
        capture("sent-only-compose")
    }

    @Test fun singleMonthDetentCanExplicitlyRefreshNewPages() {
        level=1; start(mail.take(3))
        val original=ids()
        rule.runOnIdle { activeMail=mail + mail.first().copy(id=9999,createdAt=2000005000L) }; frames(30)
        assertEquals(original,ids())
        node("post-time-rail").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(1f) }; frames(30)
        assertEquals(9999L,ids().first())
        assertEquals(4,ids().size)
        node("post-time-rail").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0f) }; frames(30)
        assertEquals(listOf(120L),ids())
    }

    @Test fun deletedSelectedIdCannotTriggerContentRequest() {
        level=1; start()
        tap("tree-letter-1"); until(ScenePhase.SEALED)
        rule.runOnIdle { activeMail=mail.filter { it.id!=1L } }; frames(50)
        assertEquals(ScenePhase.REST,phase())
        assertEquals(listOf(2L,3L,4L),ids())
        node("tree-letter-1").assertDoesNotExist(); assertEquals(0,calls.get())
    }

    @Test fun accountProfileAndFreshMountDoNotReusePhysicalIds() {
        level=0
        val store=SessionStore(rule.activity)
        val originalName=store.name; val originalProfile=store.serverProfile
        try {
            start()
            tap("tree-letter-1"); until(ScenePhase.SEALED)
            rule.runOnIdle { store.name="SYNTHETIC other account"; activeMail=mail.map { it.copy(id=it.id+4000) } }
            frames(50)
            assertEquals(ScenePhase.REST,phase()); assertTrue(ids().all { it>4000 })
            rule.runOnIdle {
                store.selectServerProfile(if(originalProfile==ServerProfile.GERFRIED) ServerProfile.GREGOR_TEST else ServerProfile.GERFRIED)
                activeMail=mail.map { it.copy(id=it.id+8000) }
            }; frames(50)
            assertTrue(ids().all { it>8000 })
            rule.runOnIdle { placeMounted=false }; frames(10)
            rule.runOnIdle { activeMail=mail.map { it.copy(id=it.id+12000) }; placeMounted=true }; frames(40)
            assertEquals(mail.take(9).map { it.id+12000 },ids())
        } finally { rule.runOnIdle { store.selectServerProfile(originalProfile); store.name=originalName } }
    }

    @Test fun monthPagesPost0() = monthPages(0)
    @Test fun monthPagesPost1() = monthPages(1)
    @Test fun monthPagesPost2() = monthPages(2)
    @Test @Config(qualifiers = "w320dp-h640dp-hdpi") fun monthPagesPost3Compact320() = monthPages(3,1.5f)
    private fun monthPages(stage:Int, scale:Float=1f) {
        level=stage; fontScale=scale
        val history=(0 until 8).flatMap { month -> (1..17).map { item ->
            val time=java.time.YearMonth.of(2026,3).minusMonths(month.toLong()).atDay(item).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond()
            mail.first().copy(id=(month*100+item).toLong(),createdAt=time)
        } }.reversed()
        val capacity=listOf(9,4,6,6)[stage]
        val expected=receivedMonthPages(history,capacity)
        val restore=StateRestorationTester(rule)
        start(history,restore=restore)
        val visited=mutableListOf<Long>()
        for(index in expected.indices) {
            node("post-time-rail").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) {
                it(1f-index.toFloat()/(expected.size-1))
            }; frames(3)
            assertEquals(expected[index],ids())
            assertTrue(node("post-time-rail").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].any {
                it.contains("UTC") && it.contains("Seite ${index + 1} von ${expected.size}")
            })
            visited+=ids()
        }
        assertEquals(history.size,visited.size); assertEquals(history.map { it.id }.toSet(),visited.toSet())
        restore.emulateSavedInstanceStateRestore(); frames(40)
        assertEquals(expected.last(),ids())
        capture("month-detents-year-boundary")
        assertEquals(0,calls.get())
    }

    @Test fun wholeBatch84Post0() = wholeBatch84(0)
    @Test fun wholeBatch84Post1() = wholeBatch84(1)
    @Test fun wholeBatch84Post2() = wholeBatch84(2)
    @Test fun wholeBatch84Post3() = wholeBatch84(3)
    @Test @Config(qualifiers = "w320dp-h640dp-hdpi") fun wholeBatch84Post0Compact320() = wholeBatch84(0, 1.5f)
    @Test @Config(qualifiers = "w320dp-h640dp-hdpi") fun wholeBatch84Post1Compact320() = wholeBatch84(1, 1.5f)
    @Test @Config(qualifiers = "w320dp-h640dp-hdpi") fun wholeBatch84Post2Compact320() = wholeBatch84(2, 1.5f)
    @Test @Config(qualifiers = "w320dp-h640dp-hdpi") fun wholeBatch84Post3Compact320() = wholeBatch84(3, 1.5f)
    @Test @Config(qualifiers = "w360dp-h640dp-hdpi") fun wholeBatch84Post0Compact360() = wholeBatch84(0, 1.5f)
    @Test @Config(qualifiers = "w360dp-h640dp-hdpi") fun wholeBatch84Post1Compact360() = wholeBatch84(1, 1.5f)
    @Test @Config(qualifiers = "w360dp-h640dp-hdpi") fun wholeBatch84Post2Compact360() = wholeBatch84(2, 1.5f)
    @Test @Config(qualifiers = "w360dp-h640dp-hdpi") fun wholeBatch84Post3Compact360() = wholeBatch84(3, 1.5f)

    /** Independent fixture/order oracle, real end taps, real handle down/moves/up, real mouse wheel.
     * Two January messages force the first full spread across the December/year boundary. */
    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    private fun wholeBatch84(stage: Int, scale: Float = 1f) {
        level = stage; fontScale = scale
        val capacity = listOf(9,4,6,6)[stage]
        val epoch = java.time.Instant.parse("2026-01-02T12:00:00Z").epochSecond
        val arrived = mail.take(3 * capacity + 1).mapIndexed { i, m -> m.copy(createdAt = epoch - i * 86400L) }
        val pending = listOf("timed", "mutual", "manual", "random", "presence").mapIndexed { i, mode ->
            arrived.first().copy(id = 500L + i, mode = mode, unlocked = false, releaseAt = epoch - 999999,
                recipientApproved = false, createdAt = epoch + 500 + i,
                randomFrom = if (mode == "random") epoch + 10000 else null,
                randomTo = if (mode == "random") epoch + 20000 else null)
        }
        val sent = arrived.first().copy(id = 600, incoming = false, createdAt = epoch + 1000)
        // The oracle deliberately does not call PostalBatches or the shared test helper.
        val expected = arrived.sortedWith(compareByDescending<ApiClient.Message> { it.createdAt }.thenByDescending { it.id })
            .map { it.id }.chunked(capacity)
        val restore = StateRestorationTester(rule)
        start((arrived + pending + sent).reversed(), restore = restore)
        var capturedFrame = 0
        fun faces(index: Int, interactive: Boolean = true) {
            assertEquals(expected[index], ids())
            val native = rule.onAllNodes(SemanticsMatcher("actual native envelopes") {
                it.config.getOrNull(PostalPageSlideKey) != null
            }, useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals(expected[index].size, native.size)
            expected[index].forEach { id ->
                assertTrue(node("tree-env-$id").fetchSemanticsNode().config.contains(StoragePoseKey))
                if (interactive) node("tree-letter-$id").assertExists() else node("tree-letter-$id").assertDoesNotExist()
            }
            (pending + sent).forEach {
                node("tree-env-${it.id}").assertDoesNotExist(); node("tree-letter-${it.id}").assertDoesNotExist()
                node("post-stamp-${it.id}").assertDoesNotExist()
            }
            // Inspect the actual displayed TextLayout, not just its full accessibility description.
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            node("post-time-label").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            val from = index * capacity + 1
            val to = index * capacity + expected[index].size
            val roman = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII")
            fun month(id: Long) = java.time.Instant.ofEpochSecond(arrived.single { it.id == id }.createdAt)
                .atZone(java.time.ZoneOffset.UTC).let { java.time.YearMonth.of(it.year, it.monthValue) }
            val endpoints = listOf(month(expected[index].first()), month(expected[index].last())).distinct()
            val months = endpoints.joinToString("–") { roman[it.monthValue - 1] }
            val fullMonths = endpoints.joinToString("–") { "${roman[it.monthValue - 1]} ${it.year}" }
            val description = node("post-time-label").fetchSemanticsNode().children.single()
                .config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single()
            assertEquals("$from–$to von ${arrived.size} · $fullMonths", description)
            val visible = if (stage == 0) "$from–$to/${arrived.size} · $months"
                else "$from–$to von ${arrived.size}"
            if (stage == 0) assertEquals(visible, layout.layoutInput.text.text)
            else assertTrue(layout.layoutInput.text.text.contains(visible))
            assertFalse("POST$stage actual wood ink must fit", layout.hasVisualOverflow)
            assertEquals(1, layout.lineCount)
            assertEquals(layout.layoutInput.text.length, layout.getLineEnd(0, visibleEnd = true))
            if (stage == 0) {
                val paintedText = rule.onNode(hasText(visible) and hasAnyAncestor(hasTestTag("post-time-label")), useUnmergedTree = true)
                paintedText.assertIsDisplayed()
                val ink = paintedText.fetchSemanticsNode().boundsInRoot
                val knob = node("post-time-knob").fetchSemanticsNode().boundsInRoot
                val wood = node("post-time-rail").fetchSemanticsNode().boundsInRoot
                assertTrue("combined ink stays on the registered wood", ink.left >= wood.left && ink.right <= wood.right && ink.top >= wood.top)
                assertTrue("actual range/Roman line clears the photographed brass", ink.bottom < knob.top)
                assertTrue("wood ink remains legible", layout.layoutInput.style.fontSize.value >= 8f)
                // Roman glyphs are part of the painted line and have real nonempty glyph bounds.
                val romanStart = visible.indexOf(" · ") + 3
                for (i in romanStart until visible.length) {
                    val glyph = layout.getBoundingBox(i)
                    assertTrue(glyph.width > 0f && glyph.height > 0f)
                    assertTrue(glyph.left >= 0f && glyph.right <= layout.size.width)
                }
            }
            capture("batch84-${capturedFrame++}-page-$index")
        }
        fun end(older: Boolean, jitter: Boolean = false) {
            val rail = node("post-time-rail").fetchSemanticsNode().boundsInRoot
            var target = Offset.Zero
            node("post-time-rail").performTouchInput {
                target = Offset(if (stage == 0) width * (if (older) 32f else 229f) / 261f
                    else width * (if (older) 14f else 596f) / 610f, height / 2f)
                if (jitter) { down(target); moveTo(target + Offset(1f, 1f), 16); up() } else click(target)
            }
            pointer = "${rail.left + target.x}:${rail.top + target.y},REST,REST,${calls.get()}"
            frames(30)
        }
        fun x(f: Float, width: Int) = if (stage == 0) width * (70.5f + 120f * f) / 261f
            else width * (28f + 554f * f) / 610f
        fun drag(index: Int, releasePointer: Boolean = true) {
            val rail = node("post-time-rail").fetchSemanticsNode().boundsInRoot
            val knob = node("post-time-knob").fetchSemanticsNode().boundsInRoot
            var target = Offset.Zero
            node("post-time-rail").performTouchInput {
                down(knob.center - rail.topLeft)
                target = Offset(x(1f - index.toFloat() / expected.lastIndex, width), knob.center.y - rail.top)
                moveTo(target, 240)
            }
            pointer = "${rail.left + target.x}:${rail.top + target.y},REST,REST,${calls.get()}"
            frames(3)
            faces(index, interactive = false)
            val moving = rule.onAllNodes(SemanticsMatcher("whole faces during held pointer") {
                it.config.getOrNull(PostalPageSlideKey) != null
            }, useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue(moving.all { it.config[PostalPageSlideKey] == 0f })
            if (releasePointer) { node("post-time-rail").performTouchInput { up() }; frames(30); faces(index) }
        }
        faces(0)
        val readout = node("post-time-label").fetchSemanticsNode().children.single()
        val readoutDescription = readout.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single()
        assertTrue(readoutDescription.contains("I 2026–XII 2025"))
        assertTrue(readoutDescription.contains("1–$capacity von ${arrived.size}"))
        val readoutLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        node("post-time-label").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(readoutLayouts) }
        assertTrue(readoutLayouts.single().layoutInput.text.text.contains(
            if (stage == 0) "1–$capacity/${arrived.size} · I–XII" else "1–$capacity von ${arrived.size}"))
        // A new arrival BETWEEN gestures must not rotate the next frozen whole spread.
        rule.runOnIdle { activeMail = activeMail + arrived.first().copy(id = 9998, createdAt = epoch + 9998) }
        frames(30); faces(0)
        node("tree-env-9998").assertDoesNotExist()
        val visited = ids().toMutableList()
        end(true); faces(1); visited += ids()
        end(true); faces(2); visited += ids()
        end(true); faces(3); visited += ids()
        assertEquals(arrived.map { it.id }, visited); assertEquals(visited.size, visited.toSet().size)
        end(false); faces(2); end(false); faces(1); end(false); faces(0)
        end(true, jitter = true); faces(1); end(false, jitter = true); faces(0)
        drag(1); drag(3); drag(0)
        node("post-time-rail").performMouseInput { moveTo(center); scroll(1f) }; frames(30); faces(1)
        node("post-time-rail").performMouseInput { moveTo(center); scroll(-1f) }; frames(30); faces(0)
        node("post-time-rail").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        node("post-time-rail").performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionLeft) }; frames(30); faces(1)
        node("post-time-rail").performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionRight) }; frames(30); faces(0)
        node("tree-time-back").performClick(); frames(30); faces(1)
        node("tree-time-back").performClick(); frames(30); faces(2)
        node("tree-time-now").performClick(); frames(30); faces(1)
        node("tree-time-now").performClick(); frames(30); faces(0)
        // A new eligible arrival during DOWN..UP must not rotate any held batch.
        drag(1, releasePointer = false)
        rule.runOnIdle {
            activeMail = activeMail + arrived.first().copy(id = 9999, createdAt = epoch + 9999)
            opened[arrived.first().id] = OpenedMessage("SYNTHETIC cached reading", null, null, null)
        }; frames(5); faces(1, interactive = false)
        val heldRail = node("post-time-rail").fetchSemanticsNode().boundsInRoot
        var heldTarget = Offset.Zero
        node("post-time-rail").performTouchInput {
            heldTarget = Offset(x(1f - 2f / expected.lastIndex, width), height / 2f)
            moveTo(heldTarget, 200)
        }
        pointer = "${heldRail.left + heldTarget.x}:${heldRail.top + heldTarget.y},REST,REST,${calls.get()}"
        frames(3); faces(2, interactive = false)
        node("post-time-rail").performTouchInput { up() }; frames(30); faces(2)
        restore.emulateSavedInstanceStateRestore(); frames(30); faces(2)
        // Live server revocation removes an anchored face immediately; its neighbour keeps its slot.
        val revoked = expected[2].first()
        val survivor = expected[2][1]
        val bounds = node("tree-env-$survivor").fetchSemanticsNode().boundsInRoot
        rule.runOnIdle { activeMail = activeMail.map { if (it.id == revoked) it.copy(unlocked = false) else it } }; frames(30)
        assertEquals(expected[2].drop(1), ids()); node("tree-env-$revoked").assertDoesNotExist()
        assertEquals(bounds, node("tree-env-$survivor").fetchSemanticsNode().boundsInRoot)
        // Ordinary back/forward navigation still uses the original partition after revocation.
        end(false); faces(1)
        end(true); assertEquals(expected[2].drop(1), ids())
        node("tree-env-$revoked").assertDoesNotExist()
        end(false); faces(1)
        // A real tap on the CURRENT photographed knob deliberately refreshes all chunks.
        val refreshRail = node("post-time-rail").fetchSemanticsNode().boundsInRoot
        val refreshKnob = node("post-time-knob").fetchSemanticsNode().boundsInRoot
        node("post-time-rail").performTouchInput { click(refreshKnob.center - refreshRail.topLeft) }
        frames(30)
        val refreshed = activeMail.filter { it.incoming && it.groupId == null && it.unlocked }
            .sortedWith(compareByDescending<ApiClient.Message> { it.createdAt }.thenByDescending { it.id }).map { it.id }.chunked(capacity)
        assertEquals(refreshed[1], ids())
        end(false)
        assertEquals(refreshed[0], ids())
        assertEquals(listOf(9999L, 9998L), ids().take(2))
        ids().forEach { node("tree-env-$it").assertExists(); node("tree-letter-$it").assertExists() }
        assertEquals(ids().size, rule.onAllNodes(SemanticsMatcher("refreshed native envelopes") {
            it.config.getOrNull(PostalPageSlideKey) != null
        }, useUnmergedTree = true).fetchSemanticsNodes().size)
        capture("batch84-explicit-knob-refresh")
        assertEquals(0, calls.get())
        // Pending metadata survives exclusively as a deliberate announcement, with the gate action intact.
        tap("post-status"); NativePostTestDriver(rule).scrollTo("tree-window-TRAVEL"); tap("tree-window-TRAVEL")
        assertTrue(ids().isEmpty()); node("post-empty").assertExists()
        tap("post-mail"); NativePostTestDriver(rule).scrollTo("post-select-500"); tap("post-select-500")
        node("post-arrival").assertTextContains("Freigabezeit:", substring = true)
        assertEquals(0, calls.get()); assertTrue(exists("post-arrival-action"))
    }

    @Test fun wholeBatch84ReadingPost0() = reading84(0)
    @Test fun wholeBatch84ReadingPost1() = reading84(1)
    @Test fun wholeBatch84ReadingPost2() = reading84(2)
    @Test fun wholeBatch84ReadingPost3() = reading84(3)
    @Test @Config(qualifiers = "w320dp-h640dp-hdpi") fun wholeBatch84ReadingPost0Compact() = reading84(0, 1.5f)
    @Test @Config(qualifiers = "w320dp-h640dp-hdpi") fun wholeBatch84ReadingPost1Compact() = reading84(1, 1.5f)
    @Test @Config(qualifiers = "w320dp-h640dp-hdpi") fun wholeBatch84ReadingPost2Compact() = reading84(2, 1.5f)
    @Test @Config(qualifiers = "w320dp-h640dp-hdpi") fun wholeBatch84ReadingPost3Compact() = reading84(3, 1.5f)
    @Test @Config(qualifiers = "w360dp-h640dp-hdpi") fun wholeBatch84ReadingPost0Compact360() = reading84(0, 1.5f)
    @Test @Config(qualifiers = "w360dp-h640dp-hdpi") fun wholeBatch84ReadingPost1Compact360() = reading84(1, 1.5f)
    @Test @Config(qualifiers = "w360dp-h640dp-hdpi") fun wholeBatch84ReadingPost2Compact360() = reading84(2, 1.5f)
    @Test @Config(qualifiers = "w360dp-h640dp-hdpi") fun wholeBatch84ReadingPost3Compact360() = reading84(3, 1.5f)
    @Test fun wholeBatch84PendingOnlyPost0() = pendingOnly84(0)
    @Test @Config(qualifiers = "w320dp-h640dp-hdpi") fun wholeBatch84PendingOnlyPost1() = pendingOnly84(1)
    @Test @Config(qualifiers = "w320dp-h640dp-hdpi") fun wholeBatch84PendingOnlyPost2() = pendingOnly84(2)
    @Test @Config(qualifiers = "w320dp-h640dp-hdpi") fun wholeBatch84PendingOnlyPost3() = pendingOnly84(3)
    private fun pendingOnly84(stage: Int) {
        level = stage; fontScale = 1.5f
        start(listOf("timed", "mutual", "manual").mapIndexed { i, mode ->
            mail[i].copy(unlocked = false, mode = mode, releaseAt = 1, readAt = 1)
        })
        assertTrue(ids().isEmpty())
        mail.take(3).forEach { m ->
            node("tree-env-${m.id}").assertDoesNotExist(); node("tree-letter-${m.id}").assertDoesNotExist()
            node("post-stamp-${m.id}").assertDoesNotExist()
        }
        node("post-empty").assertTextEquals("3 unterwegs")
        assertTrue(node("post-empty").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription]
            .any { it.contains("3 Ankündigungen unterwegs") && it.contains("Noch nicht angekommen") })
        val pendingLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        node("post-empty").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(pendingLayouts) }
        assertEquals(1, pendingLayouts.single().lineCount)
        capture("pending84-layout-diagnostic")
        val pendingLayout = pendingLayouts.single()
        assertFalse("pending stage=$stage size=${pendingLayout.size} constraints=${pendingLayout.layoutInput.constraints} " +
            "paragraph=${pendingLayout.multiParagraph.width}x${pendingLayout.multiParagraph.height} " +
            "width=${pendingLayout.didOverflowWidth} height=${pendingLayout.didOverflowHeight} style=${pendingLayout.layoutInput.style}",
            pendingLayout.hasVisualOverflow)
        node("post-time-rail").assertIsNotEnabled()
        assertEquals(ScenePhase.REST, phase())
        assertEquals(0, calls.get())
        capture("pending84-announcements-no-envelopes")
        tap("post-status")
        assertTrue(NativePostTestDriver(rule).description("tree-window-ALL").contains("0 Briefe"))
        assertTrue(NativePostTestDriver(rule).description("tree-window-UNREAD").contains("0 Briefe"))
        assertTrue(NativePostTestDriver(rule).description("tree-window-TRAVEL").contains("3 Ankündigungen"))
        back()
        tap(if (stage == 0) "tree-wax-bucket" else "tree-pen"); frames(50)
        assertTrue(exists("tree-page")); assertEquals(0, calls.get())
        capture("pending84-announcements-only-composer")
    }

    private fun reading84(stage: Int, scale: Float = 1f) {
        level = stage; fontScale = scale
        readingText = "TESTDATA actual decrypted first paragraph. The words on this paper are the message.\n\n" +
            (1..12).joinToString("\n\n") { "Paragraph $it: comfortable ink on original paper, with the complete authenticated content available by scrolling." }
        val restore = StateRestorationTester(rule)
        start(mail.take(1), restore = restore); select(); release = true; at(.5f, .5f); until(ScenePhase.READING)
        assertEquals(1, calls.get())
        node("tree-letter-text").assertTextEquals(readingText)
        fun layout(tag: String): androidx.compose.ui.text.TextLayoutResult {
            val result = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            node(tag).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(result) }
            return result.single()
        }
        val body = layout("tree-letter-text")
        assertTrue(body.layoutInput.style.fontSize.value > layout("tree-letter-sender").layoutInput.style.fontSize.value)
        assertTrue(body.layoutInput.style.fontSize.value > layout("tree-letter-date").layoutInput.style.fontSize.value)
        assertEquals(29f, body.layoutInput.style.lineHeight.value)
        assertEquals(readingText.length, body.getLineEnd(body.lineCount - 1, visibleEnd = true))
        assertFalse((0 until body.lineCount).any { body.isLineEllipsized(it) })
        val firstInk = node("tree-letter-text").fetchSemanticsNode().boundsInRoot
        val paper = node("tree-page").fetchSemanticsNode().boundsInRoot
        assertTrue("Actual reading starts on the first screen", firstInk.top >= paper.top && firstInk.top < paper.bottom - 29f)
        capture("reading84-first-authenticated-text")
        NativePostTestDriver(rule).scrollTo("tree-letter-unread")
        node("tree-letter-unread").assertIsDisplayed()
        node("tree-letter-prev").assertExists(); node("tree-letter-next").assertExists()
        capture("reading84-scrolled-actions")
        fun scrollOffset() = node("tree-page").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        val offset = scrollOffset()
        assertTrue(offset > 0f)
        rule.runOnIdle { activeMail = activeMail.map { it.copy(readAt = 2000000000L) } }; frames(10)
        assertEquals(offset, scrollOffset())
        restore.emulateSavedInstanceStateRestore(); until(ScenePhase.READING)
        assertEquals(offset, scrollOffset())
        tap("tree-letter-unread"); until(ScenePhase.SEALED); assertEquals(1, unreadCalls.get())
        at(.5f, .84f); until(ScenePhase.READING); assertEquals(2, calls.get())
        node("tree-letter-text").assertTextEquals(readingText)
    }

    @Test fun releaseComposerPointRelative411() = composerRelease(ComposeMode.DATE_TIME, TimeBasis.RELATIVE)
    @Test fun releaseComposerPointAbsolute411() = composerRelease(ComposeMode.DATE_TIME, TimeBasis.ABSOLUTE)
    @Test @Config(qualifiers = "w360dp-h640dp-hdpi") fun releaseComposerWindowRelative360() = composerRelease(ComposeMode.RANDOM, TimeBasis.RELATIVE, 1.5f)
    @Test @Config(qualifiers = "w320dp-h640dp-hdpi") fun releaseComposerWindowAbsolute320() = composerRelease(ComposeMode.RANDOM, TimeBasis.ABSOLUTE, 1.5f)
    @Test fun releaseComposerManual411() = composerRelease(ComposeMode.MANUAL, TimeBasis.RELATIVE)
    @Test @Config(qualifiers = "w320dp-h640dp-hdpi") fun releaseComposerInvalidWindowNeverTransmits() {
        fontScale=1.5f; allowSends=true; start(emptyList())
        tap("tree-wax-bucket"); frames(50)
        val d=NativePostTestDriver(rule)
        d.scrollTo("tree-to-101"); tap("tree-to-101"); frames(50)
        node("tree-write-text").performTextInput("SYNTHETIC validation body"); frames()
        d.scrollTo("tree-write-when"); tap("tree-write-when")
        d.scrollTo("tree-mode-RANDOM"); tap("tree-mode-RANDOM")
        for(value in listOf("", "0", "-1", "999999999999999999999999")) {
            d.scrollTo("release-duration-Beginn nach")
            node("release-duration-Beginn nach").performTextReplacement(value); frames()
            node("release-error").assertExists()
            d.scrollTo("tree-write-send"); tap("tree-write-send")
            assertTrue(sentBodies.isEmpty()); assertEquals(value,draft.value!!.randomStart)
        }
        d.scrollTo("release-duration-Beginn nach"); node("release-duration-Beginn nach").performTextReplacement("25"); frames()
        d.scrollTo("tree-write-send"); tap("tree-write-send")
        assertTrue(sentBodies.isEmpty()); node("tree-write-problem").assertExists()
        d.scrollTo("tree-mode-MANUAL"); tap("tree-mode-MANUAL")
        d.scrollTo("tree-mode-RANDOM"); tap("tree-mode-RANDOM")
        assertEquals("25",draft.value!!.randomStart)
        assertEquals("SYNTHETIC validation body",draft.value!!.text)
        d.scrollTo("release-error"); capture("composer-invalid-window")
    }

    private fun composerRelease(mode: ComposeMode, basis: TimeBasis, scale: Float = 1f) {
        level=0; fontScale=scale; allowSends=true
        val restore=StateRestorationTester(rule)
        start(emptyList(),restore=restore)
        tap("tree-wax-bucket"); frames(50)
        val d=NativePostTestDriver(rule)
        d.scrollTo("tree-to-101"); tap("tree-to-101"); frames(50)
        node("tree-write-text").performTextInput("SYNTHETIC retained composer body"); frames()
        d.scrollTo("tree-write-when"); tap("tree-write-when")
        assertEquals(3,rule.onAllNodes(SemanticsMatcher("release mode") {
            it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith("tree-mode-")==true
        },true).fetchSemanticsNodes().size)
        d.scrollTo("tree-mode-${mode.name}"); tap("tree-mode-${mode.name}")
        if (mode!=ComposeMode.MANUAL) {
            d.scrollTo("release-basis-${basis.name}"); tap("release-basis-${basis.name}")
            if (basis==TimeBasis.RELATIVE) {
                val input=if (mode==ComposeMode.RANDOM) "release-duration-Beginn nach" else "release-duration-Freigabe nach"
                d.scrollTo(input); node(input).performTextReplacement("0"); frames()
                node("release-error").assertExists()
                // Real send button refuses the invalid draft without transport.
                d.scrollTo("tree-write-send"); tap("tree-write-send"); assertTrue(sentBodies.isEmpty())
                d.scrollTo(input); node(input).performTextReplacement("2"); frames()
            }
            if (basis==TimeBasis.ABSOLUTE) {
                val target=if (mode==ComposeMode.RANDOM) "release-window-start" else "release-point-absolute"
                d.scrollTo(target)
                rule.onNode(hasText("Datum") and hasAnyAncestor(hasTestTag(target))).performClick()
                rule.runOnIdle {
                    val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog() as android.app.DatePickerDialog
                    val chosen=java.time.LocalDate.now().plusDays(3)
                    dialog.datePicker.updateDate(chosen.year,chosen.monthValue-1,chosen.dayOfMonth)
                    dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
                }; frames(10)
                d.scrollTo(target)
                rule.onNode(hasText("Uhrzeit") and hasAnyAncestor(hasTestTag(target))).performClick()
                rule.runOnIdle {
                    val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog() as android.app.TimePickerDialog
                    val timePicker=dialog.findViewById<android.widget.TimePicker>(rule.activity.resources.getIdentifier("timePicker","id","android"))
                    timePicker.hour=12; timePicker.minute=15
                    dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
                }; frames(10)
                if(mode==ComposeMode.RANDOM) {
                    d.scrollTo("release-window-end")
                    rule.onNode(hasText("Datum") and hasAnyAncestor(hasTestTag("release-window-end"))).performClick()
                    rule.runOnIdle {
                        val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog() as android.app.DatePickerDialog
                        val chosen=java.time.LocalDate.now().plusDays(4)
                        dialog.datePicker.updateDate(chosen.year,chosen.monthValue-1,chosen.dayOfMonth)
                        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
                    }; frames(10)
                }
            }
            d.scrollTo(if (mode==ComposeMode.RANDOM && basis==TimeBasis.ABSOLUTE) "release-window-end" else "release-summary")
            capture("composer-${mode.name}-${basis.name}")
        } else capture("composer-manual")
        val before=draft.value!!
        val text=before.text; val amount=before.amount; val start=before.randomStart
        restore.emulateSavedInstanceStateRestore(); frames(60)
        assertEquals(text,draft.value!!.text); assertEquals(amount,draft.value!!.amount)
        assertEquals(start,draft.value!!.randomStart); assertEquals(mode,draft.value!!.mode); assertEquals(basis,draft.value!!.timeBasis)
        val lower=java.time.Instant.now().epochSecond
        d.scrollTo("tree-write-send"); tap("tree-write-send")
        if (mode==ComposeMode.MANUAL) { d.scrollTo("tree-write-manual-yes"); tap("tree-write-manual-yes") }
        repeat(150) { if(sentBodies.isEmpty()) { frames(2); Thread.sleep(10) } }
        assertEquals(1,sentBodies.size)
        val json=sentBodies.single(); val upper=java.time.Instant.now().epochSecond
        assertEquals(mode.api,json.getString("mode")); assertTrue(json.has("signature"))
        assertEquals(listOf(101L), (0 until json.getJSONArray("recipient_ids").length()).map { json.getJSONArray("recipient_ids").getLong(it) })
        if(mode==ComposeMode.MANUAL) { assertFalse(json.has("release_at")); assertFalse(json.has("random_from")) }
        else if(mode==ComposeMode.DATE_TIME) {
            val at=json.getLong("release_at")
            if(basis==TimeBasis.RELATIVE) assertTrue(at in (lower+2*86400)..(upper+2*86400))
            else assertEquals(ReleaseSchedule.absolute(before.dateTime,java.time.ZoneId.systemDefault()),at)
        } else {
            assertFalse(json.has("release_at"))
            if(basis==TimeBasis.RELATIVE) {
                assertTrue(json.getLong("random_from") in (lower+7200)..(upper+7200))
                assertEquals(22*3600L,json.getLong("random_to")-json.getLong("random_from"))
            } else {
                assertEquals(ReleaseSchedule.absolute(before.windowStart,java.time.ZoneId.systemDefault()),json.getLong("random_from"))
                assertEquals(ReleaseSchedule.absolute(before.windowEnd,java.time.ZoneId.systemDefault()),json.getLong("random_to"))
            }
        }
        assertEquals(0,calls.get())
    }

    private fun ids() = node("tree-scene-REST").fetchSemanticsNode().config[PostalVisibleMailIdsKey]
    private fun page() = node("tree-scene-REST").fetchSemanticsNode().config[PostalTimePageKey]
    private fun pilot(stage: Int, scale: Float = 1f) {
        level = stage; fontScale = scale
        val restore = StateRestorationTester(rule)
        start(restore = restore)
        val d = NativePostTestDriver(rule)
        val capacity = listOf(9, 4, 6, 6)[level]
        val pages = (mail.size + capacity - 1) / capacity
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val density = rule.activity.resources.displayMetrics.density
        fun contained(tag: String) {
            val r = node(tag).fetchSemanticsNode().boundsInRoot
            assertTrue("POST$level $tag inside actual root: $r", r.left >= root.left - 1 && r.right <= root.right + 1 && r.top >= root.top && r.bottom <= root.bottom + 1)
        }
        fun actualFaces(expected: List<Long>, interactive: Boolean = true) {
            assertEquals(expected, ids()); assertEquals(expected.size, ids().distinct().size)
            val faces = rule.onAllNodes(SemanticsMatcher("real native faces") {
                it.config.getOrNull(PostalPageSlideKey) != null
            }, useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals(expected.size, faces.size)
            expected.forEachIndexed { index, id ->
                val env = node("tree-env-$id").fetchSemanticsNode()
                assertTrue("Native original art required", env.config.contains(StoragePoseKey))
                assertTrue("Landscape native envelope", env.size.width > env.size.height * 1.5f)
                contained("tree-env-$id")
                val name = mail.first { m -> m.id == id }.peerName
                // All first-page names must fit completely even at font1.5. The separate long-identity
                // canary has an unabridged, large close-view identity checked below, not microscopic ink.
                if (name.length <= 18) {
                    val ink = rule.onNode(hasTestTag("tree-ink") and hasText(name, substring = true), useUnmergedTree = true)
                    val inkLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    ink.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(inkLayouts) }
                    val layout = inkLayouts.single()
                    assertEquals("Only envelope names use the native handwriting face", LetterInkFont, layout.layoutInput.style.fontFamily)
                    assertEquals(androidx.compose.ui.text.font.FontWeight.Normal, layout.layoutInput.style.fontWeight)
                    assertEquals("Complete name without decorative UI arrows", name, layout.layoutInput.text.text)
                    val inkRoom = ink.fetchSemanticsNode().boundsInRoot
                    // Native TextLayoutResult rounds its intrinsic height down even when the actual
                    // ink node reserves the paragraph's ceiling + leading. Check the real draw room,
                    // every final character and lack of ellipsis, not that subpixel false positive.
                    assertFalse("POST$level full name=$name", layout.didOverflowWidth)
                    assertFalse("POST$level full name=$name, size=${layout.layoutInput.style.fontSize}, room=$inkRoom", layout.isLineEllipsized(0))
                    assertEquals(layout.layoutInput.text.length, layout.getLineEnd(0, visibleEnd = true))
                    assertTrue("POST$level whole glyph height for $name: ${layout.multiParagraph.height} in $inkRoom",
                        kotlin.math.ceil(layout.multiParagraph.height) <= inkRoom.height)
                }
                if (interactive) contained("tree-letter-$id")
                if (level > 0) {
                    assertEquals(PhysicalPostalStorage.slots(level)[index], env.config[StorageFrameKey].slot)
                    val hit = if (interactive) node("tree-letter-$id").fetchSemanticsNode().boundsInRoot else null
                    val registration = PostRegistration.of(level)
                    val sourceHit = PhysicalPostalStorage.hitBox(level, index, expected.size)
                    if (hit != null) assertTrue("Exposed, usable native target", hit.width > 0 && hit.height > 0)
                    assertTrue(PhysicalPostalStorage.visible(level, index, expected.size,
                        Offset((sourceHit.l + sourceHit.r) * 512f, (sourceHit.t + sourceHit.b) * 768f)))
                    assertEquals(capacity, registration.sheets.size)
                }
                if (interactive) {
                    val description = node("tree-letter-$id").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription]
                    assertTrue(description.any { it.contains(mail.first { m -> m.id == id }.peerName) })
                }
            }
        }
        capture("rest120")
        actualFaces(mail.take(capacity).map { it.id })
        for (tag in listOf("post-filter", "post-time-rail")) {
            contained(tag)
            val control = node(tag).fetchSemanticsNode()
            val r = control.boundsInRoot
            assertTrue("48dp control $tag", r.width >= 48*density-1 && r.height >= 48*density-1)
            assertTrue("48dp actual layout $tag", control.size.width >= 48*density-1 && control.size.height >= 48*density-1)
            assertEquals("Unclipped target width $tag", control.size.width.toFloat(), r.width, 1f)
            assertEquals("Unclipped target height $tag", control.size.height.toFloat(), r.height, 1f)
        }
        if (level == 3) {
            val filter = node("post-filter").fetchSemanticsNode().boundsInRoot
            val rail = node("post-time-rail").fetchSemanticsNode().boundsInRoot
            assertFalse("Full POST3 Filter and rail targets must be disjoint: $filter / $rail", filter.overlaps(rail))
            assertTrue("4dp between full POST3 targets: $filter / $rail", rail.top - filter.bottom >= 4*density-1)
            mail.take(capacity).forEach { m ->
                val env = node("tree-env-${m.id}").fetchSemanticsNode()
                val frame = env.config[StorageFrameKey]
                // SceneEnvelope projects its nominal landscape box into these physical corners.
                val scale = env.boundsInRoot.width / frame.slot.width
                val corners = frame.corners.map { p -> env.boundsInRoot.topLeft +
                    Offset(p.x-frame.slot.left, p.y-frame.slot.top-frame.dy) * scale }
                val envelope = androidx.compose.ui.geometry.Rect(corners.minOf { it.x }, corners.minOf { it.y },
                    corners.maxOf { it.x }, corners.maxOf { it.y })
                assertFalse("POST3 Filter clear of envelope ${m.id}: $filter / $envelope", filter.overlaps(envelope))
                assertFalse("POST3 rail clear of envelope ${m.id}: $rail / $envelope", rail.overlaps(envelope))
                val hit = node("tree-letter-${m.id}").fetchSemanticsNode().boundsInRoot
                assertFalse("POST3 Filter clear of letter target ${m.id}", filter.overlaps(hit))
                assertFalse("POST3 rail clear of letter target ${m.id}", rail.overlaps(hit))
            }
            println("POST3 actual root=$root density=$density filter=$filter rail=$rail " +
                "ink=${node("post-filter-ink").fetchSemanticsNode().boundsInRoot} " +
                "knob=${node("post-time-knob").fetchSemanticsNode().boundsInRoot}")
        }
        node("post-controls").assertDoesNotExist(); node("tree-belt").assertDoesNotExist(); node("tree-rope").assertDoesNotExist()
        capture("rest120")
        // Every actual exposed face is independently reachable through its real pointer target.
        for (id in mail.take(capacity).map { it.id }) {
            tap("tree-letter-$id"); until(ScenePhase.SEALED)
            assertTrue(exists("tree-env-$id")); assertEquals(0, calls.get())
            // Freeze owns the selected real ID; selection cannot silently swap a representative.
            assertEquals(id, node("tree-scene-SEALED").fetchSemanticsNode().config[PostalSelectedMailIdKey])
            back(); until(ScenePhase.REST); frames(30)
        }
        tap("post-filter"); capture("filteropen")
        assertEquals("e_post_0_lay_sheet", node("post-selector").fetchSemanticsNode().config[PostalMenuMaterialKey])
        tap("post-selector-close"); node("post-selector").assertDoesNotExist()
        tap("post-filter"); at(.995f,.08f); node("post-selector").assertDoesNotExist()
        tap("post-filter"); node("post-search").performTextInput("NO SUCH TESTDATA NAME"); frames()
        rule.onNodeWithText("0 Briefe durchsuchen", useUnmergedTree = true).assertExists()
        tap("post-filter-reset"); tap("post-person")
        assertEquals(21, d.people().size)
        tap("tree-strip-alle"); until(ScenePhase.REST)
        assertEquals(capacity,ids().size); tap("post-person")
        node("post-person-search").performTextInput("Mira"); frames(); assertEquals(2, d.people().size)
        tap("tree-strip-101"); until(ScenePhase.REST)
        assertEquals(mail.filter { it.peerId == 101L }.take(capacity).map { it.id }, ids())
        restore.emulateSavedInstanceStateRestore(); frames(40)
        assertEquals(mail.filter { it.peerId == 101L }.take(capacity).map { it.id }, ids())
        tap("post-filter"); tap("post-filter-reset"); tap("post-status")
        for (v in PostViews.View.entries) {
            node("tree-window-${v.name}").assertIsDisplayed()
            assertTrue(node("tree-window-${v.name}").fetchSemanticsNode().boundsInRoot.height >= 48*density-1)
        }
        tap("tree-window-TRAVEL"); until(ScenePhase.REST); assertTrue(ids().isEmpty())
        tap("post-filter"); tap("post-filter-reset"); back()
        tap("post-mail"); assertEquals(120, d.keys("letter:").size); back()
        d.letter(20); until(ScenePhase.SEALED)
        val identity = rule.onNodeWithText("von Alexandra Sommer vom Waldrand", useUnmergedTree = true)
        identity.assertExists()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        identity.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse("Full identity canary is readable", layouts.single().hasVisualOverflow)
        back(); until(ScenePhase.REST); frames(30)
        val rail = node("post-time-rail").fetchSemanticsNode().boundsInRoot
        val knob = node("post-time-knob").fetchSemanticsNode().boundsInRoot
        assertTrue(rail.contains(knob.center)); contained("post-time-knob")
        assertEquals("Exactly one actual time handle",1,rule.onAllNodesWithTag("post-time-knob",true).fetchSemanticsNodes().size)
        fun fractionX(fraction: Float, width: Int): Float = if (level == 0)
            width * (70.5f + 120f * fraction) / 261f else width * (28f + 554f * fraction) / 610f
        node("post-time-rail").performTouchInput {
            down(knob.center - rail.topLeft)
            moveTo(Offset(fractionX(0f,width),knob.center.y-rail.top),300)
        }; frames(3)
        assertEquals(pages - 1, page())
        // The rail owns the held gesture; mail targets are intentionally disabled until release.
        actualFaces(mail.drop((pages-1)*capacity).map { it.id }, interactive = false)
        val held = rule.onAllNodes(SemanticsMatcher("held native face") { it.config.getOrNull(PostalPageSlideKey) != null },true).fetchSemanticsNodes()
        assertTrue(held.all { it.config[PostalPageSlideKey] == 0f }); capture("dragheld")
        node("post-time-rail").performTouchInput { up() }; frames(30)
        assertEquals(pages - 1, page())
        actualFaces(mail.drop((pages-1)*capacity).map { it.id })
        restore.emulateSavedInstanceStateRestore(); frames(40); assertEquals(pages-1,page())
        val restoredRail = node("post-time-rail").fetchSemanticsNode().boundsInRoot
        val restoredKnob = node("post-time-knob").fetchSemanticsNode().boundsInRoot
        assertTrue(restoredRail.contains(restoredKnob.center))
        node("post-time-rail").performTouchInput {
            down(restoredKnob.center - restoredRail.topLeft)
            moveTo(Offset(fractionX(1f,width),restoredKnob.center.y-restoredRail.top),300); up()
        }; frames(40)
        assertEquals(0,page()); actualFaces(mail.take(capacity).map { it.id })
        select(); capture("selectedsealed"); release = true
        at(.5f,.5f); until(ScenePhase.READING); assertEquals(1,calls.get())
        node("tree-letter-text").assertTextContains("TESTDATA verified reading content",substring=true); capture("reading")
        back(); until(ScenePhase.REST)
        rule.runOnIdle { activeMail = emptyList(); Snapshot.sendApplyNotifications() }; frames(40)
        tap(if (level == 0) "tree-wax-bucket" else "tree-pen"); frames(50)
        d.scrollTo("tree-to-101"); tap("tree-to-101"); frames(50)
        node("tree-write-text").performTextInput("TESTDATA retained draft"); frames(); capture("compose0mail")
        back(); frames(60); tap("tree-pen"); frames(60)
        node("tree-write-text").assertTextContains("TESTDATA retained draft",substring=true)
        assertEquals(1,calls.get())
    }
}
