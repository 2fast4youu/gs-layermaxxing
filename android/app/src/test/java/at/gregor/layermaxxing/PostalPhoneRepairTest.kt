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
open class PostalPhoneRepairTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val testName = TestName()
    protected open val level = 0
    protected open val fontScale = 1f
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
    private val people = (1..20).map { PostPilotFixtures.pals.first().copy(id = 100L + it, name = "Person $it") }
    private val mail = (1..120).map { PostPilotFixtures.letters.first().copy(id = it.toLong(), peerId = people[(it - 1) % 20].id,
        peerName = people[(it - 1) % 20].name, incoming = true, unlocked = true, readAt = null,
        markedUnread = false, oneTime = false, createdAt = 2000000000L - it * 3600L) }
    private fun node(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true)
    private fun exists(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    // Source-art knob travel, also exercised by PostAllStagesProductionTest. The rail's
    // padded width is not the handle's travel; keep this oracle independent of production input.
    private fun timeRailX(fraction: Float, width: Int): Float = if (level == 0)
        width * (70.5f + 120f * fraction) / 261f else width * (28f + 554f * fraction) / 610f
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
                    LetterTreePlace(people, letters, opened, onOpen = { if (!droppedCallback) actual.open(it) },
                        onLocked = {}, onWrite = {}, onClose = {}, requests = actual.requests, writer = writer, unread = marker, level = level)
                }
            }
        }
        if (restore == null) rule.setContent(content) else restore.setContent(content)
        frames(30)
    }
    private fun select() { tap("tree-letter-1"); until(ScenePhase.SEALED); assertEquals(0, calls.get()) }
    private fun capture(name: String) {
        val directory = System.getenv("POSTAL_REPAIR_SHOTS")?.takeIf { it.isNotBlank() } ?: return
        frames()
        val out = File(directory, "postal-repair-$level-${fontScale}/${testName.methodName}").also { it.mkdirs() }
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

    @Test fun realSecondTapTransportDecryptAndReadingWithBroadScreenInput() {
        start(); capture("120-rest"); select(); capture("sealed")
        // Bottom interior was outside the transformed tiny envelope on APK78.
        at(.5f, .84f); until(ScenePhase.WAITING); awaitCalls(1); capture("waiting")
        at(.10f, .55f); at(.90f, .55f); at(.5f, .5f)
        assertEquals(ScenePhase.WAITING, phase()); assertEquals(1, calls.get())
        release = true; until(ScenePhase.READING)
        node("tree-letter-text").assertTextContains("SYNTHETIC verified reading content", substring = true)
        assertEquals(1, calls.get()); capture("verified-success")
        // Actual outside-paper close still works after sealed input is gone.
        at(.5f, .995f); until(ScenePhase.REST)
    }

    @Test fun everyInteriorOpensButThinLeftRightAndBottomEdgesReturn() {
        start()
        val interior = listOf(.5f to .5f, .5f to .86f, .08f to .55f, .92f to .55f, .5f to .04f)
        for ((index, position) in interior.withIndex()) {
            val (x, y) = position
            tap("tree-letter-1"); until(ScenePhase.SEALED)
            at(x, y); until(ScenePhase.WAITING); awaitCalls(index + 1)
            at(.995f, .5f); until(ScenePhase.REST)
            rule.runOnIdle { opener.cancel(1) }; frames()
        }
        assertEquals(interior.size, calls.get())
        for ((x, y) in listOf(.005f to .5f, .995f to .5f, .5f to .995f, .5f to .005f)) {
            tap("tree-letter-1"); until(ScenePhase.SEALED); at(x, y); until(ScenePhase.REST)
        }
        assertEquals(interior.size, calls.get())
    }

    @Test fun badAuthenticatedContentNeverUnsealsAndRetryActuallyFetches() {
        brokenCrypto = true; release = true
        start(); select(); at(.5f, .5f)
        failed(); assertFalse(exists("tree-letter-text"))
        capture("decrypt-error")
        brokenCrypto = false; at(.5f, .5f); until(ScenePhase.READING)
        assertEquals(2, calls.get()); assertEquals("SYNTHETIC verified reading content", opened[1]?.text)
    }

    @Test fun deniedServerAuthorizationKeepsSealAndExplicitRetryUsesEndpoint() {
        status = 403; release = true; start(); select(); at(.5f, .5f); failed()
        assertFalse(exists("tree-letter-text")); capture("server-denied")
        status = 200; at(.5f, .5f); until(ScenePhase.READING); assertEquals(2, calls.get())
    }

    @Test fun offlineFailureCanRetryWithoutLocalUnsealing() {
        offline = true; release = true; start(); select(); at(.5f, .5f); failed()
        assertFalse(exists("tree-letter-text")); capture("offline-error")
        offline = false; at(.5f, .5f); until(ScenePhase.READING); assertEquals(2, calls.get())
    }

    @Test fun markedUnreadStillNeedsSecondTapAndOpenedContentIsNotFetchedTwice() {
        release = true
        start(listOf(mail.first().copy(markedUnread = true, readAt = 1999999000L)))
        select(); at(.5f, .5f); until(ScenePhase.READING); assertEquals(1, calls.get())
        back(); until(ScenePhase.REST)
        tap("tree-letter-1"); until(ScenePhase.READING); assertEquals(1, calls.get())
    }

    @Test fun oneTimeContentIsKeptAfterLeavingWithoutAnotherTransportRequest() {
        release = true; start(listOf(mail.first().copy(oneTime = true)))
        select(); at(.5f, .5f); until(ScenePhase.READING)
        back(); until(ScenePhase.REST); tap("tree-letter-1"); until(ScenePhase.READING)
        node("tree-letter-text").assertTextContains("SYNTHETIC verified reading content", substring = true)
        node("tree-letter-unread-none").assertExists(); node("tree-letter-unread").assertDoesNotExist()
        assertEquals(1, calls.get())
    }

    @Test fun actualUnreadEndpointResealsThenRequiresAnotherExplicitOpen() {
        release = true; start(listOf(mail.first()))
        select(); at(.5f, .5f); until(ScenePhase.READING)
        var visible = false
        for (step in 0..15) {
            val action = node("tree-letter-unread").fetchSemanticsNode()
            val minimum = 48 * rule.activity.resources.displayMetrics.density
            if (action.boundsInRoot.height >= minOf(minimum, action.size.height.toFloat()) - 1) { visible = true; break }
            node("tree-page").performTouchInput { swipeUp(durationMillis = 500) }; frames(20)
        }
        assertTrue("Unread action must be reachable by bounded native paper scrolling", visible)
        tap("tree-letter-unread"); until(ScenePhase.SEALED)
        assertEquals(1, unreadCalls.get()); assertNull(opened[1]); assertEquals(1, calls.get())
        at(.5f, .84f); until(ScenePhase.READING); assertEquals(2, calls.get())
    }

    @Test fun travellingLetterOnlyShowsArrivalInformationAndNeverFetches() {
        start(listOf(mail.first().copy(unlocked = false, mode = "timed", releaseAt = 2100000000)))
        tap("tree-letter-1"); node("post-arrival").assertTextContains("Freigabezeit:", substring = true)
        assertEquals(ScenePhase.REST, phase()); assertEquals(0, calls.get()); assertTrue(opened.isEmpty())
    }

    @Test fun absentCallbackAndResetDuringWaitReturnToRetryableSealedState() {
        droppedCallback = true; start(); select(); at(.5f, .5f)
        ShadowLooper.idleMainLooper(300, java.util.concurrent.TimeUnit.MILLISECONDS); frames()
        until(ScenePhase.SEALED); assertEquals(0, calls.get()); assertTrue(opened.isEmpty())
        droppedCallback = false; at(.5f, .5f); until(ScenePhase.WAITING)
        rule.runOnIdle { opener.reset() }
        ShadowLooper.idleMainLooper(300, java.util.concurrent.TimeUnit.MILLISECONDS); frames()
        until(ScenePhase.SEALED); assertTrue(opened.isEmpty()); assertTrue(opener.requests.isEmpty())
    }

    @Test fun realTransportTimeoutIsFiniteWithoutOpeningOrRepeatedNetwork() {
        start(timeout = 1000); select(); at(.5f, .5f); until(ScenePhase.WAITING); awaitCalls(1)
        ShadowLooper.idleMainLooper(1100, java.util.concurrent.TimeUnit.MILLISECONDS); frames()
        until(ScenePhase.SEALED)
        assertEquals(OpenRequest.FAILED, opener.requests[1]); assertNull(opened[1]); assertEquals(1, calls.get())
        repeat(100) { if (cancelledTransport.get() == 0) { frames(); Thread.sleep(5) } }
        assertEquals("Deadline must cancel the real ApiClient call", 1, cancelledTransport.get())
        frames(100); assertEquals(1, calls.get()); capture("timeout-retry")
    }

    @Test fun leavingAndReenteringKeepOneInflightAndLateAnswerDoesNotPullViewForward() {
        start(); select(); at(.5f, .5f); until(ScenePhase.WAITING); awaitCalls(1)
        back(); until(ScenePhase.REST)
        tap("tree-letter-1"); until(ScenePhase.SEALED); at(.5f, .84f)
        assertEquals(1, calls.get()); back(); until(ScenePhase.REST)
        release = true
        repeat(100) { if (opened[1] == null) { frames(); Thread.sleep(5) } }
        assertNotNull(opened[1]); assertEquals(ScenePhase.REST, phase()); assertEquals(1, calls.get())
    }

    @Test fun all120LettersAndTwentyPeopleRemainReachableByRealDrag() {
        start(); val ids = mutableSetOf<Long>()
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val rail = node("post-time-rail").fetchSemanticsNode().boundsInRoot
        val density = rule.activity.resources.displayMetrics.density
        assertTrue(rail.height >= 48 * density - 1)
        if (level == 3) {
            val filterNode = node("post-filter").fetchSemanticsNode()
            val filter = filterNode.boundsInRoot
            assertTrue("Full 48dp Filter layout", filterNode.size.width >= 48*density-1 && filterNode.size.height >= 48*density-1)
            assertTrue("Full 48dp rail layout", node("post-time-rail").fetchSemanticsNode().size.height >= 48*density-1)
            assertFalse("Full Filter and rail targets must be disjoint: $filter / $rail", filter.overlaps(rail))
            assertTrue("4dp between full POST3 targets", rail.top-filter.bottom >= 4*density-1)
            assertTrue("Full Filter inside root", root.contains(filter.topLeft) && filter.right <= root.right && filter.bottom <= root.bottom)
            assertTrue("Full rail inside root", root.contains(rail.topLeft) && rail.right <= root.right && rail.bottom <= root.bottom)
        }
        if (level == 0) {
            assertTrue("Rail must be on upper wood strap: $rail", rail.bottom < root.center.y)
            assertEquals(9, node("tree-scene-REST").fetchSemanticsNode().config[PostalVisibleMailIdsKey].size)
            val patches = rule.onAllNodesWithTag("tree-ink", useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue(patches.all { it.boundsInRoot.top >= rail.bottom - 1 })
        }
        val ordered = TreeOrder.order(mail, opened)
        val perPage = PostRegistration.of(level).sheets.size
        val pages = (mail.size + perPage - 1) / perPage
        if (level == 0) assertEquals(14, pages)
        for (page in 0 until pages) {
            val fraction = 1f - page.toFloat() / (pages - 1)
            val knob = node("post-time-knob").fetchSemanticsNode().boundsInRoot
            assertTrue("Physical knob must be on the rail: $knob in $rail", rail.contains(knob.center))
            val start = knob.center - rail.topLeft
            node("post-time-rail").performTouchInput {
                down(start)
                moveTo(Offset(timeRailX(fraction, width), start.y), 300)
                up()
            }; frames(20)
            val scene = node("tree-scene-REST").fetchSemanticsNode().config
            assertEquals("POST$level physical knob must reach page $page", page, scene[PostalTimePageKey])
            val faces = scene[PostalVisibleMailIdsKey]
            assertEquals(PostalMailGroups.faces(TreeOrder.page(ordered, page, perPage), level).map { it.id }, faces)
            // POST3 exposes all tray members through the real mail selector.
            ids += TreeOrder.page(ordered, page, perPage).map { it.id }
        }
        assertEquals(mail.map { it.id }.toSet(), ids); assertEquals(0, calls.get()); capture("time-oldest")
        tap("post-person"); node("post-person-search").performTextInput("Person 20"); frames()
        node("tree-strip-120").assertExists(); back()
        tap("post-mail")
        assertEquals(120, rule.onAllNodes(SemanticsMatcher("real selection") {
            it.config.getOrNull(SceneSlotKey)?.startsWith("letter:") == true
        }, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun dragOwnsGestureAndPagePersistsAfterReleaseFilterAndRotation() {
        val restore = StateRestorationTester(rule)
        start(restore = restore)
        node("post-time-rail").performTouchInput {
            down(center)
            moveTo(Offset(width * .35f, height / 2f), 300)
        }; frames(3)
        val during = node("tree-scene-REST").fetchSemanticsNode().config[PostalTimePageKey]
        assertTrue(during > 0)
        val heldFaces = rule.onAllNodes(SemanticsMatcher("registered held mail face") {
            it.config.getOrNull(PostalPageSlideKey) != null
        }, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(node("tree-scene-REST").fetchSemanticsNode().config[PostalVisibleMailIdsKey].size, heldFaces.size)
        assertTrue("Held scrub must render complete current faces, not clipped page transitions",
            heldFaces.all { it.config[PostalPageSlideKey] == 0f })
        val rootBounds = rule.onRoot().fetchSemanticsNode().boundsInRoot
        println("Held face viewport=$rootBounds faces=${heldFaces.map { "${it.size}:${it.boundsInRoot}" }}")
        assertTrue("Held scrub must not shrink any registered face bounds (scene cropping is reviewed separately)",
            heldFaces.all { it.size.width > 0 && it.size.height > 0 &&
                it.boundsInRoot.width >= it.size.width - 1f && it.boundsInRoot.height >= it.size.height - 1f })
        if (level == 0) {
            val cue = node("tree-wax-write-label").fetchSemanticsNode().boundsInRoot
            assertTrue("Writing label must not cover any of the nine mail faces: $cue",
                heldFaces.none { it.boundsInRoot.overlaps(cue) })
            assertTrue("POST0 held mail faces must fit the actual phone viewport: ${heldFaces.map { it.boundsInRoot }}",
                heldFaces.all { it.boundsInRoot.left >= rootBounds.left && it.boundsInRoot.right <= rootBounds.right &&
                    it.boundsInRoot.top >= rootBounds.top && it.boundsInRoot.bottom <= rootBounds.bottom })
            for (tag in listOf("post-filter")) {
                val control = node(tag).fetchSemanticsNode().boundsInRoot
                assertTrue("POST0 $tag must fit viewport: $control", control.left >= rootBounds.left && control.right <= rootBounds.right)
            }
        }
        val rail = node("post-time-rail").fetchSemanticsNode().boundsInRoot
        pointer = "${rail.left + rail.width * .35f}:${rail.center.y},REST,REST,${calls.get()}"
        println("PostalPhoneRepair timePointer=$pointer actualPage=$during")
        capture("time-drag-held")
        node("post-time-rail").performTouchInput { up() }; frames(30)
        assertEquals(during, node("tree-scene-REST").fetchSemanticsNode().config[PostalTimePageKey])
        restore.emulateSavedInstanceStateRestore(); frames(30)
        assertEquals(during, node("tree-scene-REST").fetchSemanticsNode().config[PostalTimePageKey])
        tap("post-person"); node("post-person-search").performTextInput("Person 20"); frames()
        tap("tree-strip-120"); frames(20)
        val selected = node("tree-scene-REST").fetchSemanticsNode().config[PostalVisibleMailIdsKey]
        assertTrue(selected.isNotEmpty()); assertTrue(selected.all { it % 20L == 0L })
        val page = node("tree-scene-REST").fetchSemanticsNode().config[PostalTimePageKey]
        restore.emulateSavedInstanceStateRestore(); frames(30)
        assertEquals(page, node("tree-scene-REST").fetchSemanticsNode().config[PostalTimePageKey])
        assertEquals(selected, node("tree-scene-REST").fetchSemanticsNode().config[PostalVisibleMailIdsKey])
        assertEquals(0, calls.get()); assertEquals(ScenePhase.REST, phase())
        capture("restored-filter-time")
    }

    @Test fun paintedKnobMovesOnTheRegisteredSlotWithoutASecondCursor() {
        org.junit.Assume.assumeTrue(level == 0)
        start()
        val rail = node("post-time-rail").fetchSemanticsNode().boundsInRoot
        // Independent original-art measurement: belt x319.5..580.5, slot x390..510,y343.
        val scale = rail.width / 261f
        fun expectedX(f: Float) = rail.left + (390f - 319.5f + 120f * f) * scale
        fun assertKnob(f: Float) {
            val knob = node("post-time-knob").fetchSemanticsNode().boundsInRoot
            assertEquals(expectedX(f), knob.center.x, 1.5f)
            assertEquals(rail.top + (343f - 264.6f) * scale, knob.center.y, 1.5f)
            assertEquals(30f * scale, knob.width, 1.5f)
        }
        assertKnob(1f); capture("painted-knob-now")
        node("post-time-rail").performTouchInput {
            down(Offset((390f - 319.5f) * scale, height / 2f))
        }; frames(3)
        assertKnob(0f)
        assertEquals(13, node("tree-scene-REST").fetchSemanticsNode().config[PostalTimePageKey])
        capture("painted-knob-oldest-held")
        node("post-time-rail").performTouchInput {
            moveTo(Offset((450f - 319.5f) * scale, height / 2f), 200)
        }; frames(3)
        assertKnob(.5f); capture("painted-knob-middle-held")
        node("post-time-rail").performTouchInput { up() }; frames(30)
        val page = node("tree-scene-REST").fetchSemanticsNode().config[PostalTimePageKey]
        assertTrue(page in 6..7)
        assertKnob(PostalStorageMotion.fractionAt(page, 14))
        assertEquals(0, calls.get())
    }

    @Test fun cleanFilterHasReadableSurfaceAndRealPickerTargets() {
        org.junit.Assume.assumeTrue(level == 0)
        start()
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val rail = node("post-time-rail").fetchSemanticsNode().boundsInRoot
        val density = rule.activity.resources.displayMetrics.density
        node("post-person").assertDoesNotExist(); node("post-status").assertDoesNotExist(); node("post-mail").assertDoesNotExist()
        val target = node("post-filter").fetchSemanticsNode().boundsInRoot
        assertTrue(target.width >= 48 * density - 1 && target.height >= 48 * density - 1)
        val surface = node("post-filter-surface").fetchSemanticsNode().boundsInRoot
        assertTrue(surface.left >= root.left && surface.right <= root.right && surface.bottom <= rail.top + 1)
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        node("post-filter-ink").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.size); assertFalse(layouts.single().hasVisualOverflow)
        capture("single-rope-entry")
        tap("post-person"); node("post-person-search").assertExists(); back(); frames(30)
        tap("post-status"); node("tree-window-ALL").assertExists(); back(); frames(30)
        tap("post-mail"); node("post-select-1").assertExists()
        assertEquals(0, calls.get())
    }

    @Test fun waxBucketWithZeroLettersChoosesRecipientResumesDraftAndCancels() {
        org.junit.Assume.assumeTrue(level == 0)
        start(emptyList()); capture("zero-rest")
        val bucket = node("tree-wax-bucket").fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(bucket.center.x > root.center.x)
        node("tree-wax-writing-cue").assertTextContains("Schreiben")
        val cue = node("tree-wax-writing-cue").fetchSemanticsNode().boundsInRoot
        assertTrue("Writing cue must fit the phone viewport: $cue", cue.left >= root.left && cue.right <= root.right && cue.top >= root.top && cue.bottom <= root.bottom)
        // The label is clamped into the viewport; its tie still joins the bucket even if its centre shifts left.
        assertTrue("Writing cue stays attached below the wax bucket", cue.right >= bucket.left && cue.left <= bucket.right && cue.top >= bucket.center.y && cue.top <= bucket.bottom + 12f)
        tap("tree-wax-bucket"); frames(50); node("tree-to-101").assertExists(); capture("compose-chooser")
        tap("tree-to-101"); frames(50)
        node("tree-write-text").performTextInput("SYNTHETIC preserved draft"); frames(); capture("compose-draft")
        back(); until(ScenePhase.REST); frames(50)
        tap("tree-wax-write-label"); frames(50)
        node("tree-write-text").assertTextContains("SYNTHETIC preserved draft", substring = true)
        back(); frames(50); tap("tree-pen"); frames(50)
        node("tree-write-text").assertTextContains("SYNTHETIC preserved draft", substring = true)
        assertEquals(0, calls.get()); assertNotNull(draft.value)
    }
}
class PostalPhoneRepairPost1Test : PostalPhoneRepairTest() { override val level = 1 }
class PostalPhoneRepairPost2Test : PostalPhoneRepairTest() { override val level = 2 }
class PostalPhoneRepairPost3Test : PostalPhoneRepairTest() { override val level = 3 }
@Config(sdk = [34], qualifiers = "w320dp-h640dp-hdpi")
class PostalPhoneRepairCompactTest : PostalPhoneRepairTest() { override val fontScale = 1.5f }
@Config(sdk = [34], qualifiers = "w320dp-h640dp-hdpi")
class PostalPhoneRepairCompact1Test : PostalPhoneRepairTest() { override val fontScale = 1.5f; override val level = 1 }
@Config(sdk = [34], qualifiers = "w320dp-h640dp-hdpi")
class PostalPhoneRepairCompact2Test : PostalPhoneRepairTest() { override val fontScale = 1.5f; override val level = 2 }
@Config(sdk = [34], qualifiers = "w320dp-h640dp-hdpi")
class PostalPhoneRepairCompact3Test : PostalPhoneRepairTest() { override val fontScale = 1.5f; override val level = 3 }
