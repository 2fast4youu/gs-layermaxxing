package at.gregor.layermaxxing

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody

/** Production paper, gestures, list and repository. Synthetic data only; never a socket or account. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-hdpi")
class LetterDraftProductionUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val temporary = TemporaryFolder()
    @get:Rule val testName = TestName()
    private val driver get() = NativePostTestDriver(rule)
    private lateinit var root: File
    private var account by mutableStateOf("a")
    private var mounted by mutableStateOf(true)
    private var stage = 0
    private var scale = 1f
    private lateinit var active: LetterWriter
    private lateinit var repository: LetterDraftRepository
    private val people = PostPilotFixtures.pals.take(1).map { it.copy(id = 101L, name = "SYNTHETIC Mira") }
    @Volatile private var sendFails = true
    private val sends = java.util.concurrent.atomic.AtomicInteger()
    private val receiptResponses = java.util.concurrent.atomic.AtomicInteger()
    private val receiptCallbacks = java.util.concurrent.atomic.AtomicInteger()
    private val parsedReceipts = java.util.concurrent.CopyOnWriteArrayList<List<Long>>()
    private val sendCount get() = sends.get()
    private val bodies = java.util.concurrent.CopyOnWriteArrayList<org.json.JSONObject>()

    @Composable private fun Host() {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
            VillageTheme(true) {
                key(account) {
                    val repo = remember { LetterDraftRepository(File(root, account)) }
                    val state = remember { mutableStateOf<LetterDraft?>(null) }
                    val api = remember { ApiClient("https://drafts.invalid/", OkHttpClient.Builder().addInterceptor { chain ->
                        check(chain.request().header("Authorization") == "Bearer synthetic-drafts-only")
                        check(chain.request().method == "POST" && chain.request().url.encodedPath == "/api/messages")
                        val buffer = okio.Buffer(); chain.request().body!!.writeTo(buffer)
                        bodies += org.json.JSONObject(buffer.readUtf8()); sends.incrementAndGet()
                        if (sendFails) throw java.io.IOException("SYNTHETIC offline")
                        receiptResponses.incrementAndGet()
                        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                            .message("Synthetic receipt").body("""{"ids":[800]}""".toResponseBody()).build()
                    }.build()) }
                    val writer = remember { LetterWriter(people, emptyList(), emptyList(), false,
                        transmit = { api.send("synthetic-drafts-only", it).also { ids -> parsedReceipts += ids } },
                        onSent = { receiptCallbacks.incrementAndGet() }, draftState = state, repository = repo) }
                    SideEffect { active = writer; repository = repo }
                    if (mounted) LetterTreePlace(people, emptyList(), emptyMap(), onOpen = {}, onLocked = {},
                        onWrite = {}, onClose = {}, writer = writer, level = stage)
                }
            }
        }
    }
    private fun frames(n: Int = 60) {
        repeat(n) { rule.runOnIdle { Snapshot.sendApplyNotifications() }; rule.mainClock.advanceTimeByFrame(); ShadowLooper.idleMainLooper() }
        rule.waitForIdle()
    }
    private fun start(blocked: Boolean = false) {
        root = temporary.newFolder()
        if (blocked) File(root, "a").writeText("blocked storage")
        rule.mainClock.autoAdvance = false
        rule.setContent { Host() }; frames()
    }
    private fun tag(name: String) = rule.onNodeWithTag(name, useUnmergedTree = true)
    private fun tap(name: String) { driver.scrollTo(name); tag(name).performTouchInput { click(center) }; frames() }
    private fun back() { rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }; frames(100) }
    private fun openWax() = tap(if (stage == 0) "tree-wax-bucket" else "tree-pen")
    private fun begin(body: String) {
        openWax(); tap("tree-to-101")
        tag("tree-write-text").performTextInput(body); frames()
    }
    private fun list() { tap("drafts-open"); tag("drafts-list").assertExists() }
    private fun capture(frame: String) {
        val base = System.getenv("LETTER_DRAFT_SHOTS")?.takeIf { it.isNotBlank() } ?: return
        frames()
        val out = File(base, "${testName.methodName}/post$stage-font$scale").also { check(it.mkdirs() || it.isDirectory) }
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { view.draw(Canvas(bitmap)) }
        File(out, "$frame.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }; bitmap.recycle()
        val csv = File(out, "actual-frames.csv")
        if (!csv.exists()) csv.writeText("frame,clock_ms,transport_calls\n")
        csv.appendText("$frame,${rule.mainClock.currentTime},$sendCount\n")
        File(out, "metadata.txt").writeText("SYNTHETIC native Robolectric production LetterTreePlace/DraftListPaper/LetterDraftRepository. Actual decorView.draw, no fake images, no sockets, no real accounts. Font=$scale stage=$stage. Root pixel review required; not phone evidence.")
    }
    private fun freshActivity() {
        // Recreate the actual Activity, drop all writer/remember/Bundle draft state and reopen
        // through the real list. Only the private files supply restored letter contents.
        rule.activityRule.scenario.recreate()
        rule.activityRule.scenario.onActivity { activity -> activity.setContent { Host() } }
        frames(100)
        rule.runOnIdle { assertNull(active.draft) }
    }
    @Test fun physicalBackFreshActivityListRestoresRecipientBodyTitleCoverOneTimeAttachment() {
        start(); begin("SYNTHETIC durable body")
        tap("tree-write-details")
        driver.scrollTo("tree-write-title"); tag("tree-write-title").performTextInput("SYNTHETIC title")
        driver.scrollTo("tree-write-cover"); tag("tree-write-cover").performTextInput("SYNTHETIC cover")
        tap("tree-write-onetime")
        // Picker bytes are exercised by the real bounded private repository, not a saved Bundle.
        rule.runOnIdle { active.draft!!.attachment = Triple("fixture.txt", "text/plain", byteArrayOf(7, 8, 9)) }
        val id = rule.runOnIdle { active.draft!!.id }
        back(); freshActivity(); openWax(); list(); capture("fresh-list")
        tap("draft-resume-$id")
        tag("tree-write-text").assertTextContains("SYNTHETIC durable body")
        rule.runOnIdle {
            val draft = active.draft!!
            assertEquals(setOf(101L), draft.selected); assertEquals("SYNTHETIC title", draft.title)
            assertEquals("SYNTHETIC cover", draft.coverNote); assertTrue(draft.oneTime)
            assertArrayEquals(byteArrayOf(7, 8, 9), draft.attachment!!.third)
        }
        capture("resumed-body"); tap("tree-write-details"); capture("resumed-details")
    }
    @Test @Config(qualifiers = "w320dp-h640dp-hdpi") fun outsideAndMultipleDraftsCompactFont15ExplicitDeleteAndAccountIsolation() {
        scale = 1.5f; stage = 3; start(); begin("SYNTHETIC first")
        val first = rule.runOnIdle { active.draft!!.id }
        // Physical root pointer lands in the production FloatingPaper outside target.
        rule.onRoot().performTouchInput { click(Offset(2f, 2f)) }; frames(100)
        tag("tree-write-text").assertDoesNotExist()
        openWax(); list(); tap("drafts-new"); tap("tree-to-101")
        tag("tree-write-text").performTextInput("SYNTHETIC second"); frames()
        val second = rule.runOnIdle { active.draft!!.id }; assertNotEquals(first, second)
        back(); freshActivity(); openWax(); list()
        tag("draft-resume-$first").assertExists(); tag("draft-resume-$second").assertExists(); capture("multiple-drafts")
        tap("draft-delete-$first"); capture("delete-confirmation")
        rule.onNodeWithText("Behalten").performClick(); frames()
        assertEquals(2, rule.runOnIdle { repository.list().size })
        tap("draft-delete-$first"); tap("draft-delete-confirm")
        tag("draft-resume-$first").assertDoesNotExist(); tag("draft-resume-$second").assertExists()
        rule.runOnIdle { account = "b" }; frames(); openWax(); list(); tag("drafts-empty").assertExists(); capture("isolated-empty")
        rule.runOnIdle { account = "a" }; frames(); openWax(); list(); tag("draft-resume-$second").assertExists()
    }
    @Test fun productionBackAndOutsideRefuseSilentCloseOnStorageFailureThenRetry() {
        start(blocked = true); begin("SYNTHETIC retained on failure")
        back(); tag("tree-write-text").assertTextContains("SYNTHETIC retained on failure")
        tag("draft-save-status").assertTextContains("Fehler:", substring = true)
        rule.onRoot().performTouchInput { click(Offset(2f, 2f)) }; frames()
        tag("tree-write-text").assertExists(); capture("storage-failure-stays-open")
        rule.runOnIdle { assertTrue(File(root, "a").delete()) }
        back(); assertEquals("SYNTHETIC retained on failure", LetterDraftRepository(File(root, "a")).list().single().draft.text)
    }
    private fun awaitSend(sendingDraft: LetterDraft) {
        repeat(200) {
            frames(2)
            // A consumed draft is hidden by the writer before atomic cleanup finishes.
            // Wait for the retained operation itself, not its nullable UI selection.
            if (rule.runOnIdle { !sendingDraft.busy }) return
            Thread.sleep(5)
        }
        fail("Production send did not finish")
    }
    @Test fun realComposerSendFailureRetainsSuccessfulReceiptRemovesOnlyItsDraft() {
        start(); begin("SYNTHETIC sent body")
        val sendingDraft = rule.runOnIdle { active.draft!! }
        val id = sendingDraft.id
        val otherDraft = LetterDraft(101, "SYNTHETIC another draft")
        rule.runOnIdle { repository.bind(otherDraft) }
        tap("tree-write-send"); awaitSend(sendingDraft)
        assertEquals(1, sendCount); assertEquals(2, rule.runOnIdle { repository.list().size })
        tag("tree-write-notice").assertTextEquals("[LM-NET-IO] Verbindung zum Server fehlgeschlagen.")
        tag("tree-write-notice").assertTextEquals(ApiErrors.fromNetworkFailure(java.io.IOException("SYNTHETIC offline")).message!!)
        rule.runOnIdle {
            assertFalse(active.draft!!.notice!!.contains("offline"))
            assertFalse(active.draft!!.notice!!.contains("SYNTHETIC"))
            assertFalse(active.draft!!.notice!!.contains("IOException"))
        }
        assertEquals(0, receiptResponses.get()); assertEquals(0, receiptCallbacks.get())
        assertFalse(rule.runOnIdle { sendingDraft.consumed })
        capture("send-failure")
        rule.runOnIdle { sendFails = false }
        tap("tree-write-send"); awaitSend(sendingDraft)
        assertEquals(2, sendCount)
        assertEquals(2, bodies.size); assertEquals("timed", bodies.last().getString("mode"))
        assertEquals(101L, bodies.last().getJSONArray("recipient_ids").getLong(0))
        // Keep the original disk-removal assertion and deadline. All data in this host is synthetic.
        val receiptView = driver.exists("tree-write-sent")
        val diagnostic = rule.runOnIdle {
            "SYNTHETIC send cleanup: notice=${sendingDraft.notice} saveStatus=${sendingDraft.saveStatus} " +
                "busy=${sendingDraft.busy} consumed=${sendingDraft.consumed} " +
                "receiptResponses=${receiptResponses.get()} parsedReceipts=$parsedReceipts receiptCallbacks=${receiptCallbacks.get()} " +
                "receiptView=$receiptView " +
                "diskJson=${File(root, "a/$id.json").exists()} diskBackup=${File(root, "a/$id.json.bak").exists()} " +
                "diskNew=${File(root, "a/$id.json.new").exists()} diskSent=${File(root, "a/$id.sent").exists()} " +
                "diskSentNew=${File(root, "a/$id.sent.new").exists()} repositoryError=${repository.error}"
        }
        val remaining = LetterDraftRepository(File(root, "a")).list()
        assertFalse(diagnostic, remaining.any { it.draft.id == id })
        assertEquals(diagnostic, 1, remaining.size)
        assertEquals(otherDraft.id, remaining.single().draft.id)
        assertEquals("SYNTHETIC another draft", remaining.single().draft.text)
        assertEquals(diagnostic, 1, receiptResponses.get()); assertEquals(diagnostic, 1, receiptCallbacks.get())
        assertEquals(listOf(listOf(800L)), parsedReceipts.toList())
        tag("tree-write-sent").assertExists()
        assertTrue(diagnostic, File(root, "a/$id.sent").isFile)
        capture("send-success")
    }
    @Test fun relativeWindowRawFieldsPersistAcrossNavigationUnmountAndFreshActivity() = scheduleRestart(ComposeMode.RANDOM, TimeBasis.RELATIVE)
    @Test fun relativePointRawFieldsPersistAcrossFreshActivity() = scheduleRestart(ComposeMode.DATE_TIME, TimeBasis.RELATIVE)
    @Test fun absolutePointPersistsAcrossFreshActivity() = scheduleRestart(ComposeMode.DATE_TIME, TimeBasis.ABSOLUTE)
    @Test @Config(qualifiers = "w320dp-h640dp-hdpi") fun absoluteWindowPersistsAcrossFreshActivityFont15() {
        scale = 1.5f; scheduleRestart(ComposeMode.RANDOM, TimeBasis.ABSOLUTE)
    }
    @Test fun manualChoicePersistsAcrossFreshActivity() = scheduleRestart(ComposeMode.MANUAL, TimeBasis.RELATIVE)
    private fun scheduleRestart(mode: ComposeMode, basis: TimeBasis) {
        start(); begin("SYNTHETIC full schedule")
        tap("tree-write-when"); tap("tree-mode-${mode.name}")
        if (mode != ComposeMode.MANUAL) {
            tap("release-basis-${basis.name}")
            if (basis == TimeBasis.RELATIVE) {
                val input = if (mode == ComposeMode.RANDOM) "Beginn nach" else "Freigabe nach"
                driver.scrollTo("release-duration-$input"); tag("release-duration-$input").performTextReplacement("007")
                if (mode == ComposeMode.RANDOM) {
                    driver.scrollTo("release-duration-Ende nach"); tag("release-duration-Ende nach").performTextReplacement("031")
                }
            } else {
                val targets = if (mode == ComposeMode.RANDOM) listOf("release-window-start", "release-window-end") else listOf("release-point-absolute")
                targets.forEachIndexed { index, target ->
                    driver.scrollTo(target)
                    rule.onNode(hasText("Datum") and hasAnyAncestor(hasTestTag(target))).performClick()
                    rule.runOnIdle {
                        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as android.app.DatePickerDialog
                        val chosen = java.time.LocalDate.now().plusDays(4L + index)
                        dialog.datePicker.updateDate(chosen.year, chosen.monthValue - 1, chosen.dayOfMonth)
                        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
                    }; frames()
                    driver.scrollTo(target)
                    rule.onNode(hasText("Uhrzeit") and hasAnyAncestor(hasTestTag(target))).performClick()
                    rule.runOnIdle {
                        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as android.app.TimePickerDialog
                        val picker = dialog.findViewById<android.widget.TimePicker>(rule.activity.resources.getIdentifier("timePicker", "id", "android"))
                        picker.hour = 13 + index; picker.minute = 17
                        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
                    }; frames()
                }
            }
        }
        val before = rule.runOnIdle { active.draft!! }
        capture("schedule-before")
        // Navigation unmount without close uses the same production state and persistent fields.
        rule.runOnIdle { mounted = false }; frames(); rule.runOnIdle { mounted = true }; frames()
        freshActivity(); openWax(); list(); tap("draft-resume-${before.id}")
        rule.runOnIdle {
            val after = active.draft!!
            assertEquals(before.text, after.text); assertEquals(before.selected, after.selected)
            assertEquals(mode, after.mode); assertEquals(basis, after.timeBasis)
            assertEquals(before.amount, after.amount); assertEquals(before.unit, after.unit)
            assertEquals(before.randomStart, after.randomStart); assertEquals(before.randomEnd, after.randomEnd)
            assertEquals(before.randomStartUnit, after.randomStartUnit); assertEquals(before.randomEndUnit, after.randomEndUnit)
            assertEquals(before.dateTime, after.dateTime); assertEquals(before.windowStart, after.windowStart); assertEquals(before.windowEnd, after.windowEnd)
        }
        tap("tree-write-when"); capture("schedule-restored")
    }
}
