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

    private fun conv(id: Long, name: String, emoji: String, color: String, ago: Long, text: String, fromMe: Boolean, unread: Int = 0, ready: Int = 0, sealed: Boolean = false) = Conversation(
        friendId = id, friendName = name, avatarEmoji = emoji, displayColor = color,
        lastActivityAt = java.time.Instant.now().epochSecond - ago,
        preview = ConversationPreview(text, sealed, fromMe), unreadChats = unread, readyLetters = ready,
        lockedLetters = 0, awaitingMe = 0, chatsEnabled = true, lettersEnabled = true, epEnabled = false,
    )

    @Test fun chatList() = shot("05-chats") {
        LayermaxxingTheme("light") {
            androidx.compose.material3.Surface {
                ChatsScreen(
                    conversations = listOf(
                        conv(2, "Gerfried", "🦊", "#2E7D32", 300, "Passt, dann Samstag am Brennerhaus 👍", false, unread = 2),
                        conv(3, "Anna", "🐻", "#1565C0", 4_000, "Brief für dich", false, ready = 1, sealed = true),
                        conv(4, "Lukas", "🐺", "#6A1B9A", 90_000, "> Wann?\n\nUm acht passt", true),
                        conv(5, "Mara", "🦉", "#EF6C00", 400_000, "Danke dir!", false),
                    ),
                    requests = emptyList(), sparkInbox = emptyList(), sparkSent = emptyList(),
                    onOpen = {}, onRespond = { _, _ -> }, onGoPeople = {}, onOpenSparks = {}, onSendSpark = {}, sparkEnabled = true,
                    onGroups = {}, onTopicsHub = {}, onGlossary = {}, onValley = {}, groupCount = 2, topicCount = 3, showValley = true,
                )
            }
        }
    }

    @Test fun login() = shot("13-login") {
        LayermaxxingTheme("light") {
            AuthScreen(api = ApiClient(), profile = ServerProfile.GREGOR_TEST, onProfile = {}, onAuthenticated = {})
        }
    }

    @Test fun emptyChats() = shot("12-empty-chats") {
        LayermaxxingTheme("light") {
            androidx.compose.material3.Surface {
                ChatsScreen(
                    conversations = emptyList(), requests = emptyList(), sparkInbox = emptyList(), sparkSent = emptyList(),
                    onOpen = {}, onRespond = { _, _ -> }, onGoPeople = {}, onOpenSparks = {}, onSendSpark = {}, sparkEnabled = false,
                    onGroups = {}, onTopicsHub = {}, onGlossary = {}, onValley = {}, groupCount = 0, topicCount = 0, showValley = true,
                )
            }
        }
    }

    @Test fun chatThread() = shot("06-thread") {
        val now = java.time.Instant.now().epochSecond
        LayermaxxingTheme("light") {
            androidx.compose.material3.Surface {
                androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxSize()) {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f).fillMaxSize().chatWallpaper()) {
                        androidx.compose.foundation.layout.Column(
                            androidx.compose.ui.Modifier.padding(10.dp),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                        ) {
                            ThreadChatBubble(ApiClient.ChatMessage(1, 2, 1, "Servus! Kommst du am Samstag mit aufs Brennerhaus?", now - 600, null), false, {}, {})
                            ThreadChatBubble(ApiClient.ChatMessage(2, 1, 2, "Ja klar, ich bring die Schlüssel mit", now - 500, now - 400), true, {}, {})
                            ThreadChatBubble(ApiClient.ChatMessage(3, 2, 1, "> Ja klar, ich bring die Schlüssel mit\n\nPerfekt, dann um 9 beim Parkplatz", now - 300, null), false, {}, {})
                            ThreadChatBubble(ApiClient.ChatMessage(4, 1, 2, "👍", now - 60, null), true, {}, {})
                        }
                    }
                    Composer(ComposerPlan(true, true, "Nachricht", 0, null), "", {}, {}, {}, {})
                }
            }
        }
    }

    @Test fun glossary() = shot("07-glossary") {
        VillageTheme(true) {
            VillageRoom(true, icon = R.drawable.ic_glossary, title = "Wörterbuch", onClose = {}) {
                GlossaryScreen()
            }
        }
    }

    private val topicsSample = listOf(
        ApiClient.Topic(1, "Grillabend planen", "", "Gregor", "friend", "Gerfried", 2, 1, null, null, true),
        ApiClient.Topic(2, "Hüttenschlüssel", "", "Gregor", "personal", "Nur für mich", null, 1, null, null, true),
    )

    @Test fun topicsRoom() = shot("08-topics-room") {
        VillageTheme(true) {
            VillageRoom(true, icon = R.drawable.ic_topics, title = "Schwarzes Brett", onClose = {}) {
                TopicsHub(topicsSample, friends, listOf(ApiClient.Group(9, "Hüttenteam", 1, friends)), { _, _ -> })
            }
        }
    }

    @Test fun groupsRoom() = shot("09-groups-room") {
        VillageTheme(true) {
            VillageRoom(true, icon = R.drawable.ic_groups, title = "Gruppenplatz", onClose = {}) {
                GroupsHub(listOf(ApiClient.Group(9, "Hüttenteam", 1, friends)), friends, topicsSample, emptyList(), "", ApiClient(), {}, { _, _ -> })
            }
        }
    }

    @Test fun newPlayerVillage() = shot("10-new-player") {
        VillageTheme(true) {
            CastlesScreen(
                ownUserId = 1, friends = emptyList(), settings = emptyList(), ep = null, letters = emptyList(),
                builds = emptyMap(), creativeActive = false, onBuild = { _, _, _ -> }, onBack = {}, onPeople = {},
                onComposeLetter = {}, letterAccess = { LetterAction(true, "Brief", null) }, opened = emptyMap(), act = {}, token = "",
                api = ApiClient(), onOpenLetter = {}, onLockedTap = {}, onProof = {}, epLinkedLetterIds = emptySet(),
                dismissedEpLetters = emptyMap(), onProposeEp = {}, seenEarned = { 0 }, onSeenEarned = { _, _ -> },
                topics = emptyList(), onTopics = {}, onChat = {}, onGroups = {}, onGlossary = {},
                onDestination = {}, activities = emptyList(),
                hud = VillageHudInfo("Gregor", "🦉", "Ebenen-Neuling", 0, 0, "Messenger", unlocked = VillageGrowth.START),
            )
        }
    }

    @Test fun growingVillage() = shot("11-growing") {
        VillageTheme(true) {
            CastlesScreen(
                ownUserId = 1, friends = friends.take(1), settings = emptyList(), ep = null, letters = emptyList(),
                builds = emptyMap(), creativeActive = false, onBuild = { _, _, _ -> }, onBack = {}, onPeople = {},
                onComposeLetter = {}, letterAccess = { LetterAction(true, "Brief", null) }, opened = emptyMap(), act = {}, token = "",
                api = ApiClient(), onOpenLetter = {}, onLockedTap = {}, onProof = {}, epLinkedLetterIds = emptySet(),
                dismissedEpLetters = emptyMap(), onProposeEp = {}, seenEarned = { 0 }, onSeenEarned = { _, _ -> },
                topics = emptyList(), onTopics = {}, onChat = {}, onGroups = {}, onGlossary = {},
                onDestination = {}, activities = listOf(VillageActivity(ValleyDestination.CONVERSATIONS, 2, "Neue Nachrichten")),
                hud = VillageHudInfo(
                    "Gregor", "🦉", "Ebenen-Neuling", 0, 0, "Messenger",
                    unlocked = VillageGrowth.START + ValleyDestination.TOPICS + ValleyDestination.SPARKS,
                    fresh = setOf(ValleyDestination.SPARKS),
                ),
            )
        }
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
                R.drawable.ic_chat, null, "Treffpunkt", ValleyDestination.CONVERSATIONS.detail, "3 × Neue Nachrichten",
                "Betreten", {}, {}, androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(10.dp),
                secondary = "Chat" to {},
            )
        }
    }

    @Test fun busyVillage() = shot("02-busy") {
        Village(friends, VillageActivityModel.collect(3, 2, 1, 1, 1, 1, 1, 0))
    }
}
