package at.gregor.layermaxxing

import android.graphics.Bitmap
import androidx.activity.compose.setContent

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule

import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Explicit synthetic coverage for the formerly-missing reachable clickpath surfaces. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ClickpathVisualReview {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val out = System.getenv("CLICKPATH_SHOTS")?.let(::File)
    private val friend = ApiClient.UserSummary(2, "Gerfried", "friends", "🦊", "#2E7D32")
    private val pals = listOf(friend, ApiClient.UserSummary(3, "Anna", "friends", "🐻", "#1565C0"))
    private val now = java.time.Instant.now().epochSecond
    private val msg = ApiClient.Message(id = 7, peerId = 2, peerName = "Gerfried", incoming = true, title = "Für Samstag", coverNote = "Brennerhaus", proofStatus = "ok", createdAt = now - 3600, mode = "timed", releaseAt = now + 3600, randomFrom = null, randomTo = null, unlocked = true, oneTime = false, readAt = null, senderApproved = false, recipientApproved = false, attachmentName = null, attachmentMime = null, groupId = null, reactions = emptyList(), ciphertext = null, nonce = null, encryptionKey = null)
    private val topic = ApiClient.Topic(1, "Grillabend planen", "Samstag am See", "Gregor", "friend", "Gerfried", 2, now - 8000, null, null, true)
    private val settings = ApiClient.FriendshipSettings(2, "Gerfried", true, true, true, 3600, null, null)

    /**
     * Compose Dialog/Modal surfaces live in their own window. Drawing only the
     * activity decorView therefore misses them; for dialog captures use the last
     * Robolectric WindowManager view, which is the actual popup/dialog window.
     */
    private fun shot(name: String, dialog: Boolean = false, content: @Composable () -> Unit) {
        assumeTrue(out != null)
        rule.mainClock.autoAdvance = false
        if (dialog) rule.activity.setContent { content() } else rule.setContent(content)
        if (!dialog) rule.waitForIdle()
        repeat(80) { rule.mainClock.advanceTimeByFrame() }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val activityDecor = rule.activity.window.decorView
        val view = if (dialog) {
            (shadowOf(rule.activity.windowManager) as org.robolectric.shadows.ShadowWindowManagerImpl)
                .getViews().lastOrNull { it !== activityDecor } ?: activityDecor
        } else activityDecor
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        out!!.mkdirs()
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Composable
    private fun light(content: @Composable () -> Unit) {
        LayermaxxingTheme("light") { Surface { content() } }
    }
    private val api = ApiClient()
    private val act: ((suspend () -> Unit) -> Unit) = {}

    @Test fun topicsScreen() = shot("36-topics-screen") { light { TopicsScreen(listOf(topic), friend, "synthetic", api, act) } }
    @Test fun epScreen() = shot("37-ep-screen") { light { EpScreen("synthetic", api, listOf(settings), ApiClient.EpOverview(emptyList(), emptyList(), emptyList(), 4, 7, "Wegweiser"), EpPrompt(2, 7), {}, act) } }
    @Test fun letterRoom() = shot("38-letter-room") { light { LetterRoom("Gerfried", listOf(msg), emptyMap(), LetterAction(true, "Brief schreiben", null), "synthetic", api, act, false, emptyMap(), null, {}, {}, {}, {}, {}, {}, {}, {}) } }
    @Test fun sparkRoom() = shot("39-spark-room") { light { SparkRoom(listOf(SparkItem(1, "Ein kleiner Gruß aus dem Tal.", true), SparkItem(2, null, false)), listOf(SparkSent(3, 2, "Gerfried", false)), "synthetic", api, act, {}, {}) } }
    @Test fun threadInfoSheet() = shot("40-thread-info-sheet") { light { ThreadInfoSheet(friend, settings, "Noch keine Punkte vorgeschlagen.", {}) } }
    @Test fun questDialog() = shot("41-quest-dialog", dialog = true) { light { ChatQuestDialog("Gerfried", ChatExtras.QuestDraft("Samstagswanderung", "Gemeinsam zum Brennerhaus", "hike", 20), {}, {}) } }
    @Test fun glossaryTermDialog() = shot("42-glossary-term-dialog", dialog = true) { light { GlossaryTermDialog(ApiClient.GlossaryTerm(1, "Brennerhaus", "Gregor", emptyList(), true), {}) } }
    @Test fun friendSheet() = shot("43-friend-sheet") { light { FriendSheet(friend, ApiClient.IslandInfo(2, 320, 2, 326, 4, null), "Allerbester Freund", {}, listOf(msg), emptyMap(), listOf(ApiClient.Quest(1, "Gipfelrunde", "", "hike", 30, 1, "Gregor", "friend", "Gerfried", 2, now, null, null, true)), 2, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }) } }
    @Test fun newQuestSheet() = shot("44-new-quest-sheet", dialog = true) { light { NewQuestSheet(pals, listOf(ApiClient.Group(9, "Hüttenteam", 1, pals)), 2, null, {}, { _, _, _, _, _, _ -> }) } }
    @Test fun labelDialog() = shot("45-label-dialog", dialog = true) { light { LabelDialog("Gerfried", "Allerbester Freund", {}, {}) } }
    @Test fun recoveryDialog() = shot("46-recovery-dialog", dialog = true) { light { RecoveryDialog("SYNTHETIC-RECOVERY-7XQ2", {}) } }
    @Test fun proofDialog() = shot("47-proof-dialog", dialog = true) { light { ProofDialog(ApiClient.ProofDetails(7, "Für Samstag", true, "timed", null, "{}"), {}) } }
    @Test fun epProposalDialog() = shot("48-ep-proposal-dialog", dialog = true) { light { EpProposalDialog(EpOpportunity(2, "Gerfried", 7, "Für Samstag", EpRole.I_OPENED_THEIR_LETTER), { _, _ -> }, {}) } }
    @Test fun requestDialog() = shot("49-request-dialog", dialog = true) { light { RequestDialog(PendingRequest(RequestKind.EP, 4, 2, "Gerfried", "Punkte-Anfrage von Gerfried", "Eine Anerkennung für deinen Brief."), {}, {}, {}) } }
}
