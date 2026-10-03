package at.gregor.layermaxxing

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp

/**
 * Renders the village to PNG files for a human/visual review. Skipped unless
 * VILLAGE_SHOTS points to an output directory, so the normal gate stays fast.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class VillageVisualReview {
    @get:Rule val rule = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val out = System.getenv("VILLAGE_SHOTS")?.let(::File)

    private fun shot(name: String, content: @Composable () -> Unit) {
        assumeTrue(out != null)
        rule.setContent(content)
        rule.mainClock.autoAdvance = false
        repeat(30) { rule.mainClock.advanceTimeByFrame() }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        out!!.mkdirs()
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val friends = listOf(
        ApiClient.UserSummary(2, "Gerfried", "friend", "🦊", "#2E7D32"),
        ApiClient.UserSummary(3, "Anna", "friend", "🐻", "#1565C0"),
    )

    @Composable
    private fun Village(friends: List<ApiClient.UserSummary>, activities: List<VillageActivity>) = VillageTheme(true) {
        CastlesScreen(
            ownUserId = 1, friends = friends, settings = emptyList(), ep = null, letters = emptyList(),
            builds = emptyMap(), creativeActive = false, onBuild = { _, _, _ -> }, onBack = {}, onPeople = {},
            onComposeLetter = {}, letterAccess = { LetterAction(true, "Brief", null) }, opened = emptyMap(), act = {}, token = "",
            api = ApiClient(), onOpenLetter = {}, onLockedTap = {}, onProof = {}, epLinkedLetterIds = emptySet(),
            dismissedEpLetters = emptyMap(), onProposeEp = {}, seenEarned = { 0 }, onSeenEarned = { _, _ -> },
            topics = emptyList(), onTopics = {}, onChat = {}, onGroups = {}, onGlossary = {},
            onDestination = {}, activities = activities,
            hud = VillageHudInfo("Gregor", "🦉", "Ebenen-Wanderer", 12, 7, "Messenger"),
        )
    }

    @Test fun emptyVillage() = shot("01-empty") { Village(emptyList(), emptyList()) }

    @Test fun buildingRoom() = shot("03-room") {
        VillageTheme(true) {
            VillageRoom(true, icon = R.drawable.ic_topics, title = "Schwarzes Brett", onClose = {}) {
                androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) {
                    androidx.compose.material3.Text("Offene Themen", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    androidx.compose.material3.OutlinedTextField("", {}, label = { androidx.compose.material3.Text("Neues Thema") })
                    androidx.compose.material3.Button(onClick = {}) { androidx.compose.material3.Text("Anlegen") }
                    androidx.compose.material3.Card { androidx.compose.material3.Text("Grillabend planen", androidx.compose.ui.Modifier.padding(12.dp)) }
                }
            }
        }
    }

    @Test fun buildingCard() = shot("04-card") {
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(R.drawable.village_plate), null,
                androidx.compose.ui.Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            BuildingCard(
                R.drawable.ic_chat, null, "Treffpunkt", ValleyDestination.CONVERSATIONS.detail, "3 neu · Neue Nachrichten",
                "Betreten", {}, {}, androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(10.dp),
                secondary = "Chat" to {},
            )
        }
    }

    @Test fun busyVillage() = shot("02-busy") {
        Village(friends, VillageActivityModel.collect(3, 2, 1, 1, 1, 1, 1, 0))
    }
}
