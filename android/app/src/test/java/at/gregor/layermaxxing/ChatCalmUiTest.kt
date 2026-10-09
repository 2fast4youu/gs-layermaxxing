package at.gregor.layermaxxing

import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ChatCalmUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val friend = ApiClient.UserSummary(2, "Gerfried", "friends", "🦊", "#2E7D32")
    private val quest = ApiClient.Quest(1, "Gipfelrunde", "", "hike", 30, 1, "Gregor", "friend", "Gerfried", 2, 100, null, null, true)

    private val letter = ApiClient.Message(id = 7, peerId = 2, peerName = "Gerfried", incoming = true, title = "Nur im Briefraum", coverNote = "", proofStatus = "ok", createdAt = 100, mode = "timed", releaseAt = 101, randomFrom = null, randomTo = null, unlocked = true, oneTime = false, readAt = null, senderApproved = false, recipientApproved = false, attachmentName = null, attachmentMime = null, groupId = null, reactions = emptyList(), ciphertext = null, nonce = null, encryptionKey = null)

    @Test fun letterNeverAppearsInTimelineAndHeaderOpensSharedCard() {
        rule.setContent { LayermaxxingTheme("light") { Surface(Modifier.fillMaxSize()) {
            ThreadScreen(friend, ApiClient.FriendshipSettings(2, "Gerfried", true, false, true, 3600, null, null),
                1, "synthetic", ApiClient("http://127.0.0.1:1/"), listOf(letter), emptyMap(), emptySet(), emptyMap(),
                emptyList(), { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, emptyList(), "", false)
        } } }
        rule.onNodeWithText("Nur im Briefraum", substring = true).assertDoesNotExist()
        rule.onNodeWithContentDescription("Infos zu Gerfried").performClick()
        rule.onNodeWithText("Briefe · 1", substring = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        rule.onNodeWithText("Nur im Briefraum", substring = true).assertExists().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        rule.onNodeWithText("Alle Briefe und Punkte öffnen").assertDoesNotExist()
        rule.onAllNodesWithText("Nur im Briefraum", substring = true).assertCountEquals(2) // Room row plus focused detail.
    }
    @Test fun friendCardHidesEmptySectionsAndKeepsQuestCreation() {
        rule.setContent { LayermaxxingTheme("light") { Surface(Modifier.fillMaxSize()) {
            FriendSheet(friend, null, null, {}, emptyList(), emptyMap(), emptyList(), 0, {}, {}, {}, {}, {}, null, {}, { _, _ -> })
        } } }
        rule.onNodeWithText("Neue Quest").assertExists()
        rule.onNodeWithText("Briefe", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Noch keine gemeinsame Quest", substring = true).assertDoesNotExist()
    }
    @Test fun openQuestsStayReachableAndCompletedQuestsStartCollapsed() {
        var done = false
        rule.setContent { LayermaxxingTheme("light") { Surface(Modifier.fillMaxSize()) {
            FriendSheet(friend, null, null, {}, emptyList(), emptyMap(), listOf(quest, quest.copy(id = 2, title = "Erledigte Runde", completedAt = 110)), 0, {}, {}, {}, {}, {}, null, {}, { _, completed -> done = completed })
        } } }
        rule.onNodeWithText("Gipfelrunde").assertExists()
        rule.onNodeWithText("Erledigte Runde").assertDoesNotExist()
        rule.onNodeWithContentDescription("Als erledigt markieren").performClick()
        assertEquals(true, done)
    }
    @Test fun attachmentMenuRespectsFeatureGatesAndSeparatesCallbacks() {
        var gallery = 0; var letters = 0; var quests = 0; var closed = 0
        rule.setContent { LayermaxxingTheme("light") {
            androidx.compose.foundation.layout.Column { ChatAttachmentMenu(ComposerPlan(false, true, "Nachricht", 0, null),
                ComposerExtras(false, false, "", {}, { gallery++ }, {}, {}, {}),
                { letters++ }, { quests++ }, { closed++ }) }
        } }
        rule.onNodeWithText("Brief schreiben").assertDoesNotExist()
        rule.onNodeWithText("Galerie").performClick()
        assertEquals(1, gallery); assertEquals(1, closed); assertEquals(0, letters)
        rule.onNodeWithText("Neue Quest").performClick()
        assertEquals(1, quests)
    }
}
