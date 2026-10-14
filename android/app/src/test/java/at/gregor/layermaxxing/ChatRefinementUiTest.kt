package at.gregor.layermaxxing

import android.content.Intent
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Protocol
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject

/** Local encrypted fixtures go through the real ApiClient and ThreadScreen; no server/token needed. */
internal fun chatReviewApi(): ApiClient {
    val now = java.time.Instant.now().epochSecond
    fun message(id: Int, sender: Int, text: String, kind: String = "text"): JSONObject {
        val sealed = CryptoBox.encrypt(text)
        return JSONObject().put("id", id).put("sender_id", sender).put("recipient_id", if (sender == 1) 2 else 1)
            .put("ciphertext", sealed.ciphertext).put("nonce", sealed.nonce).put("encryption_key", sealed.key)
            .put("created_at", now - (4 - id) * 60).put("kind", kind).put("has_attachment", kind == "image")
            .put("attachment_mime", if (kind == "image") "image/jpeg" else JSONObject.NULL)
    }
    val messages = JSONArray().put(message(1, 1, "Samstag um neun?"))
        .put(message(2, 2, "Ja, ich freue mich!"))
        .put(message(3, 2, "", "image"))
    val photo = android.graphics.Bitmap.createBitmap(240, 160, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(75, 140, 165)) }
    val canvas = android.graphics.Canvas(photo)
    canvas.drawCircle(170f, 42f, 25f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(244, 195, 85) })
    val bytes = java.io.ByteArrayOutputStream().also { photo.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
    val envelope = CryptoBox.encrypt("")
    val attachment = CryptoBox.encryptBytes(bytes, envelope.key)
    val attachmentJson = JSONObject().put("ciphertext", attachment.ciphertext).put("nonce", attachment.nonce).put("encryption_key", envelope.key).toString()
    val client = OkHttpClient.Builder().addInterceptor { chain ->
        val payload = when {
            chain.request().url.encodedPath.endsWith("/messages") -> messages.toString()
            chain.request().url.encodedPath.endsWith("/attachment") -> attachmentJson
            chain.request().url.encodedPath.endsWith("/glossary") -> "[]"
            else -> "{}"
        }
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(payload.toResponseBody("application/json".toMediaType())).build()
    }.build()
    return ApiClient("https://fixtures.invalid/", client)
}

@androidx.compose.runtime.Composable
internal fun ChatReviewThread() {
    val friend = ApiClient.UserSummary(2, "Gerfried", "friends", "🦊", "#2E7D32")
    val api = androidx.compose.runtime.remember { chatReviewApi() }
    ThreadScreen(friend, ApiClient.FriendshipSettings(2, "Gerfried", true, true, true, 3600, null, null),
        1, "synthetic", api, emptyList(), emptyMap(), emptySet(), emptyMap(), emptyList(), { _, _ -> },
        {}, {}, {}, {}, {}, {}, {}, emptyList(), "", false)
}

@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ChatRefinementUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private fun thread() {
        rule.setContent { LayermaxxingTheme("light") { Surface(Modifier.fillMaxSize()) { ChatReviewThread() } } }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Samstag um neun?").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun selectionCombinesMessagesAndNeverOffersDeletingIncomingMessages() {
        thread()
        rule.onNodeWithText("Samstag um neun?").performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        rule.onNodeWithText("Als Brief verschicken").assertDoesNotExist()
        rule.onNodeWithText("Auswählen").performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.onNodeWithText("1 ausgewählt").assertExists()
        rule.onNodeWithContentDescription("Auswahl löschen").assertExists()
        rule.onNodeWithText("Ja, ich freue mich!").performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.onNodeWithText("2 ausgewählt").assertExists()
        rule.onNodeWithContentDescription("Auswahl löschen").assertDoesNotExist()
    }
    @Test fun savingPhotoStartsAndroidDocumentPickerWithImageFileName() {
        thread()
        // The image placeholder has no caption; use the real bubble action semantics.
        // Image bubble is the last non-deleted message with a long-click action.
        rule.onNodeWithContentDescription("Foto – Nachrichtenaktionen")
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        rule.onNodeWithText("Speichern unter …").performSemanticsAction(SemanticsActions.OnClick) { it() }
        val intent = shadowOf(rule.activity).nextStartedActivityForResult.intent
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("Layermaxxing-3.jpg", intent.getStringExtra(Intent.EXTRA_TITLE))
    }
    @Test fun glossaryGesturesRespectChangedSelectionMode() {
        val selection = androidx.compose.runtime.mutableStateOf(false)
        var terms = 0; var actions = 0; var reaction: String? = null
        rule.setContent { LayermaxxingTheme("light") { Surface(Modifier.fillMaxSize()) {
            ThreadChatBubble(ApiClient.ChatMessage(1, 2, 1, "Brennerhaus", 100, null), false, { actions++ }, null,
                glossary = listOf(ApiClient.GlossaryTerm(1, "Brennerhaus", "Gregor", emptyList(), true)),
                onTerm = { terms++ }, onReact = { reaction = it }, selectionMode = selection.value)
        } } }
        rule.onNodeWithText("Brennerhaus", useUnmergedTree = true).performTouchInput { click() }
        rule.mainClock.advanceTimeBy(400); rule.waitForIdle()
        assertEquals(1, terms)
        rule.runOnIdle { selection.value = true }
        rule.onNodeWithText("Brennerhaus", useUnmergedTree = true).performTouchInput { click() }
        rule.mainClock.advanceTimeBy(400); rule.waitForIdle()
        assertEquals(1, terms); assertEquals(1, actions)
        rule.onNodeWithText("Brennerhaus", useUnmergedTree = true).performTouchInput { doubleClick() }
        rule.mainClock.advanceTimeBy(400); rule.waitForIdle()
        assertNull(reaction); assertEquals(2, actions)
    }
    @Test fun emojiCategoriesUseOnePickerAndReturnEmojiWithoutSending() {
        var selected: String? = null
        rule.setContent { LayermaxxingTheme("light") { EmojiPicker("Emoji", {}, { selected = it }) } }
        rule.onNodeWithText("Herzen").performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.onNodeWithContentDescription("Emoji ❤️").performSemanticsAction(SemanticsActions.OnClick) { it() }
        assertEquals("❤️", selected)
    }
}
