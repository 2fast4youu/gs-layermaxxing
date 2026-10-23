package at.gregor.layermaxxing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.performSemanticsAction

import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.filter

import androidx.compose.ui.test.doubleClick

import androidx.compose.ui.test.pinch

import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.longClick

import androidx.compose.ui.test.performTouchInput

import androidx.compose.foundation.layout.height

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
import androidx.compose.foundation.background
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

    private fun shot(name: String, gesture: (() -> Unit)? = null, captureDialogs: Boolean = false, content: @Composable () -> Unit) {
        assumeTrue(out != null)
        rule.setContent(content)
        rule.mainClock.autoAdvance = false
        repeat(30) { rule.mainClock.advanceTimeByFrame() }
        if (gesture != null) { gesture(); repeat(40) { rule.mainClock.advanceTimeByFrame() } }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        view.draw(canvas)
        if (captureDialogs) {
            // ModalBottomSheet owns a second real Android window; the activity decor alone omits it.
            val globalClass = Class.forName("android.view.WindowManagerGlobal")
            val global = globalClass.getMethod("getInstance").invoke(null)
            val viewsField = globalClass.getDeclaredField("mViews").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val windows = viewsField.get(global) as List<android.view.View>
            windows.filter { it !== view && it.visibility == android.view.View.VISIBLE }.forEach { window ->
                val location = IntArray(2); window.getLocationOnScreen(location)
                canvas.save(); canvas.translate(location[0].toFloat(), location[1].toFloat())
                window.draw(canvas); canvas.restore()
            }
        }
        out!!.mkdirs()
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val friends = listOf(
        ApiClient.UserSummary(2, "Gerfried", "friends", "🦊", "#2E7D32"),
        ApiClient.UserSummary(3, "Anna", "friends", "🐻", "#1565C0"),
    )

    private fun conv(id: Long, name: String, emoji: String, color: String, ago: Long, text: String, fromMe: Boolean, unread: Int = 0, ready: Int = 0, sealed: Boolean = false) = Conversation(
        friendId = id, friendName = name, avatarEmoji = emoji, displayColor = color,
        lastActivityAt = java.time.Instant.now().epochSecond - ago,
        preview = ConversationPreview(text, sealed, fromMe), unreadChats = unread, readyLetters = ready,
        lockedLetters = 0, awaitingMe = 0, chatsEnabled = true, lettersEnabled = true, epEnabled = false,
    )

    @Test fun actualCalmThread() = shot("53-actual-calm-thread", gesture = {
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Samstag um neun?").fetchSemanticsNodes().isNotEmpty() }
    }) {
        LayermaxxingTheme("light") { androidx.compose.material3.Surface(androidx.compose.ui.Modifier.fillMaxSize()) { ChatReviewThread() } }
    }
    @Test fun calmFriendCard() = shot("51-calm-friend-card") {
        LayermaxxingTheme("light") { androidx.compose.material3.Surface(androidx.compose.ui.Modifier.fillMaxSize()) {
            FriendSheet(friends[0], ApiClient.IslandInfo(2, 100, 4, 104, 3, 200), "Bester Freund", {}, emptyList(), emptyMap(),
                listOf(ApiClient.Quest(1, "Gipfelrunde", "", "hike", 30, 1, "Gregor", "friend", "Gerfried", 2, java.time.Instant.now().epochSecond, null, null, true)),
                0, {}, {}, {}, {}, {}, null, {}, { _, _ -> }, extra = { androidx.compose.material3.Text("Freundschaftsregeln") })
        } }
    }
    @Test fun calmAttachmentMenu() = shot("52-calm-plus-menu") {
        LayermaxxingTheme("light") { androidx.compose.material3.Surface(androidx.compose.ui.Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(20.dp)) {
                androidx.compose.material3.Text("Anhänge und Aktionen")
                ChatAttachmentMenu(ComposerPlan(true, true, "Nachricht", 0, null), ComposerExtras(false, false, "", {}, {}, {}, {}, {}), {}, {}, {})
            }
        } }
    }
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

    @Test fun moreMenu() = shot("14-more") {
        LayermaxxingTheme("light") {
            androidx.compose.material3.Surface {
                MoreHubPreview(
                    ApiClient.Status(1, "Gregor", false, "🦉", "#315B8A", true),
                    ApiClient.EpOverview(emptyList(), emptyList(), emptyList(), 3, 5, "x"),
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
                    ThreadHeader(friends[0], false, 2, 3, false, {}, {}, {}, {}, {}, {}, {}, {}, {})
                    ChatActionHint("1 Brief bereit · 1 Quest offen", {})
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f).fillMaxSize().chatWallpaper()) {
                        androidx.compose.foundation.layout.Column(
                            androidx.compose.ui.Modifier.padding(10.dp),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                        ) {
                            DayMark("HEUTE")
                            ThreadChatBubble(ApiClient.ChatMessage(1, 2, 1, "Servus! Kommst du am Samstag mit aufs Brennerhaus?", now - 600, null), false, {}, {})
                            ThreadChatBubble(ApiClient.ChatMessage(2, 1, 2, "Ja klar, ich bring die Schlüssel mit", now - 500, now - 400), true, {}, {})
                            ThreadChatBubble(ApiClient.ChatMessage(3, 2, 1, "> Ja klar, ich bring die Schlüssel mit\n\nPerfekt, dann um 9 beim Parkplatz", now - 300, null), false, {}, {})
                            ThreadChatBubble(ApiClient.ChatMessage(4, 1, 2, "👍", now - 60, null), true, {}, {})
                            ThreadChatBubble(ApiClient.ChatMessage(5, 2, 1, "Hast du den Brief schon gesehen? Den hab ich dir letzte Woche vom Leuchtturm geschickt 🙂", now - 30, null), false, {}, {})
                        }
                    }
                    Composer(ComposerPlan(true, true, "Nachricht", 0, null), "", {}, {}, {}, {})
                }
            }
        }
    }

    @Test fun chatExtras() = shot("13-chat-extras") {
        val now = java.time.Instant.now().epochSecond
        val heart = listOf(ApiClient.Reaction(2, "Gerfried", "❤️"), ApiClient.Reaction(1, "Gregor", "❤️"))
        val terms = listOf(ApiClient.GlossaryTerm(1, "Brennerhaus", "Gregor", emptyList(), false))
        LayermaxxingTheme("light") {
            androidx.compose.material3.Surface {
                androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxSize()) {
                    ThreadHeader(friends[0], false, 0, 0, false, {}, {}, {}, {}, {}, {}, {}, {}, {},
                        presenceLine = "schreibt gerade …", online = true, chatDays = 12)
                    PinnedStrip(listOf(ApiClient.ChatMessage(7, 2, 1, "Schlüssel liegt unter dem roten Blumentopf", now - 9000, null, pinnedAt = now)), Harbour.palette()) {}
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f).fillMaxSize().chatWallpaper()) {
                        androidx.compose.foundation.layout.Column(
                            androidx.compose.ui.Modifier.padding(10.dp),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                        ) {
                            ThreadChatBubble(ApiClient.ChatMessage(1, 2, 1, "Samstag aufs Brennerhaus wandern?", now - 600, null, reactions = heart), false, {}, {}, ownUserId = 1, glossary = terms)
                            ThreadChatBubble(ApiClient.ChatMessage(2, 1, 2, "Bin dabei, ich mach eine Quest draus", now - 500, now - 400, editedAt = now - 450), true, {}, {}, ownUserId = 1)
                            ThreadChatBubble(ApiClient.ChatMessage(3, 2, 1, ChatExtras.stickerText("palm"), now - 300, null, kind = "sticker"), false, {}, {}, ownUserId = 1)
                            ThreadChatBubble(ApiClient.ChatMessage(4, 1, 2, "0:07", now - 200, now - 100, kind = "voice", hasAttachment = true), true, {}, {}, ownUserId = 1,
                                media = { m, tint -> VoiceNote(m.text, { ByteArray(0) }, tint, Harbour.palette()) })
                            ThreadChatBubble(ApiClient.ChatMessage(5, 2, 1, "", now - 90, null, deleted = true), false, {}, {}, ownUserId = 1)
                            TypingBubble("Gerfried")
                        }
                    }
                    StickerTray(Harbour.palette()) {}
                    Composer(ComposerPlan(true, true, "Nachricht", 0, null), "", {}, {}, {}, {},
                        extras = ComposerExtras(true, false, "0:00", {}, {}, {}, {}))
                }
            }
        }
    }

    @Test fun chatActions() = shot("14-chat-actions") {
        LayermaxxingTheme("light") {
            androidx.compose.material3.Surface {
                androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxSize().padding(top = 40.dp)) {
                    ReactionBar("❤️", Harbour.palette()) {}
                    androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(20.dp))
                    androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.padding(12.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        ChatExtras.STICKERS.take(4).forEach { HarbourSticker(it, 80.dp) }
                    }
                    androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.padding(12.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        ChatExtras.STICKERS.drop(4).forEach { HarbourSticker(it, 80.dp) }
                    }
                    Composer(ComposerPlan(true, true, "Nachricht", 0, null), "", {}, {}, {}, {},
                        extras = ComposerExtras(false, true, "0:12", {}, {}, {}, {}))
                }
            }
        }
    }

    @Test fun glossary() = shot("07-glossary") {
        VillageTheme(true) {
            VillageRoom(true, icon = R.drawable.ico_glossary, title = "Wörterbuch", onClose = {}) {
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
            VillageRoom(true, icon = R.drawable.ico_topics, title = "Schwarzes Brett", onClose = {}) {
                TopicsHub(topicsSample, friends, listOf(ApiClient.Group(9, "Hüttenteam", 1, friends)), { _, _ -> })
            }
        }
    }

    @Test fun groupsRoom() = shot("09-groups-room") {
        VillageTheme(true) {
            VillageRoom(true, icon = R.drawable.ico_groups, title = "Gruppenplatz", onClose = {}) {
                GroupsHub(listOf(ApiClient.Group(9, "Hüttenteam", 1, friends)), friends, topicsSample, emptyList(), "", ApiClient(), {}, { _, _ -> })
            }
        }
    }

                            @Test fun buildingRoom() = shot("03-room") {
        VillageTheme(true) {
            VillageRoom(true, icon = R.drawable.ico_topics, title = "Schwarzes Brett", onClose = {}) {
                androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) {
                    androidx.compose.material3.Text("Offene Themen", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    androidx.compose.material3.OutlinedTextField("", {}, label = { androidx.compose.material3.Text("Neues Thema") })
                    androidx.compose.material3.Button(onClick = {}) { androidx.compose.material3.Text("Anlegen") }
                    androidx.compose.material3.Card { androidx.compose.material3.Text("Grillabend planen", androidx.compose.ui.Modifier.padding(12.dp)) }
                }
            }
        }
    }

        private fun quest(id: Long, title: String, icon: String, pts: Int, target: String, tid: Long, type: String = "friend", done: Boolean = false) =
        ApiClient.Quest(id, title, "", icon, pts, 1, "Gregor", type, target, tid, 0, if (done) 1 else null, if (done) "Anna" else null, true)

    private val nowSec = System.currentTimeMillis() / 1000
    private fun letter(id: Long, peer: Long, name: String, mode: String, incoming: Boolean) = ApiClient.Message(
        id, peer, name, incoming, "Brief $id", "", "none", nowSec - 3 * 86_400, mode,
        if (mode == "timed") nowSec + 2 * 86_400 else null,
        if (mode == "random") nowSec - 86_400 else null, if (mode == "random") nowSec + 5 * 86_400 else null,
        false, false, null, false, false,
        null, null, null, emptyList(), null, null, null,
    )

    @Composable
    private fun Isles(withFriends: Boolean = true, start: String = "map", placeKey: String? = null) {
        val pals = if (!withFriends) emptyList() else listOf(
            ApiClient.UserSummary(2, "Gerfried", "friends", "🦊", "#2E7D32"),
            ApiClient.UserSummary(3, "Anna", "friends", "🐻", "#1565C0"),
            ApiClient.UserSummary(4, "Lena", "friends", "🦉", "#EF6C00"),
            ApiClient.UserSummary(5, "Ida", "friends", "🐺", "#6A1B9A"),
            ApiClient.UserSummary(6, "Luki", "friends", "🐰", "#C2185B"),
        )
        IslandWorld(
            ownName = "Gregor", ownEmoji = "🦉", friends = pals,
            groups = if (!withFriends) emptyList() else listOf(
                ApiClient.Group(1, "Bergfreunde", 1, pals.take(4)), ApiClient.Group(2, "WG", 1, pals.drop(2)),
            ),
            islands = ApiClient.Islands(140, listOf(
                ApiClient.IslandInfo(2, 320, 2, 326, 4, null), ApiClient.IslandInfo(3, 130, 0, 130, 3, 300),
                ApiClient.IslandInfo(4, 50, 0, 50, 2, 120), ApiClient.IslandInfo(5, 0, 0, 0, 1, 40),
                ApiClient.IslandInfo(6, 10, 0, 10, 1, 40),
            )),
            quests = listOf(quest(1, "Gemeinsam auf den Gipfel", "hike", 30, "Anna", 3), quest(2, "Radtour am See", "bike", 20, "Lena", 4, done = true)),
            letters = if (withFriends) listOf(letter(1, 3, "Anna", "timed", false), letter(4, 3, "Anna", "random", true), letter(5, 3, "Anna", "mutual", false), letter(2, 2, "Gerfried", "mutual", true), letter(6, 2, "Gerfried", "timed", false), letter(3, 4, "Lena", "random", false)) else emptyList(),
            topics = emptyList(), opened = emptyMap(), onBack = {}, onChat = {}, onComposeLetter = {}, onOpenLetter = {},
            onLockedTap = {}, onTopics = {}, onAllTopics = {}, onGlossary = {}, onPeople = {},
            onCreateQuest = { _, _, _, _, _, _ -> }, onQuestDone = { _, _ -> }, onQuestDelete = {},
            ownId = 1, startView = start, startPlace = placeKey,
            labels = mapOf(2L to "Allerbester Freund", 3L to "Verlobte", 4L to "Schwester", 5L to "Schwesterherz", 6L to "Süßer"),
            incoming = if (withFriends) listOf(ApiClient.IncomingRequest(9, 7, "Jonas", 0)) else emptyList(),
            outgoing = if (withFriends) listOf(ApiClient.OutgoingRequest(10, 8, "Sophie", 0)) else emptyList(),
            glossary = { GlossaryScreen() },
            loadIsland = { id ->
                val items = IsleDecor.labels.keys.mapIndexed { i, k -> ApiClient.DecorItem(k, i * 20, i < 6) }
                val unlocks = listOf(0, 0, 0, 40, 80, 120, 200, 300, 450)
                if (id == 1L) ApiClient.HomeIsland(1, mapOf(0 to "flowers", 3 to "bench"), 140, items,
                    places = mapOf(
                        0 to ApiClient.LifePlace("bude", "Meine Bude"), 1 to ApiClient.LifePlace("hut", "Brennerhaus"),
                        2 to ApiClient.LifePlace("uni", "JKU"), 3 to ApiClient.LifePlace("desk", "Coden am Berg"),
                    ),
                    plots = 5, plotUnlocks = unlocks, here = ApiClient.Here(3, "am Coden"))
                else ApiClient.HomeIsland(id, mapOf(0 to "windmill", 2 to "fountain"), 400, items,
                    places = mapOf(0 to ApiClient.LifePlace("home", ""), 1 to ApiClient.LifePlace("club", "Turnverein"),
                        2 to ApiClient.LifePlace("station", ""), 4 to ApiClient.LifePlace("city", "Linz")),
                    plots = 7, plotUnlocks = unlocks)
            },
        )
    }
    /** Gerfried-Testauftrag: Gruppenraum (Gemeindehaus/CAMPFIRE) im Inselmodus mit 11 Mitgliedern
     * an einem Tisch (RoundTable nimmt nur members.take(8) – hier sichtbar machen, ob das reicht). */
    @Test fun campfireLinzerTaskforce() = shot("30-gruppenraum-linzer-taskforce") {
        val crew = listOf(
            ApiClient.UserSummary(2, "Veit", "friends", "🦊", "#2E7D32"),
            ApiClient.UserSummary(3, "Anna", "friends", "🐻", "#1565C0"),
            ApiClient.UserSummary(4, "Lena", "friends", "🦉", "#EF6C00"),
            ApiClient.UserSummary(5, "Ida", "friends", "🐺", "#6A1B9A"),
            ApiClient.UserSummary(6, "Luki", "friends", "🐰", "#C2185B"),
            ApiClient.UserSummary(7, "Moritz", "friends", "🐨", "#00897B"),
            ApiClient.UserSummary(8, "Sophie", "friends", "🦋", "#D81B60"),
            ApiClient.UserSummary(9, "Jonas", "friends", "🐢", "#5D4037"),
            ApiClient.UserSummary(10, "Clara", "friends", "🐧", "#3949AB"),
            ApiClient.UserSummary(11, "Paul", "friends", "🦁", "#F57F17"),
            ApiClient.UserSummary(12, "Mia", "friends", "🦄", "#8E24AA"),
        )
        IslandWorld(
            ownName = "Gregor", ownEmoji = "🦉", friends = crew,
            groups = listOf(ApiClient.Group(1, "Linzer Taskforce", 1, crew)),
            islands = ApiClient.Islands(0, emptyList()),
            quests = emptyList(), letters = emptyList(), topics = emptyList(), opened = emptyMap(),
            onBack = {}, onChat = {}, onComposeLetter = {}, onOpenLetter = {},
            onLockedTap = {}, onTopics = {}, onAllTopics = {}, onGlossary = {}, onPeople = {},
            onCreateQuest = { _, _, _, _, _, _ -> }, onQuestDone = { _, _ -> }, onQuestDelete = {},
            ownId = 1, startView = "map", startPlace = IsleBuilding.CAMPFIRE.name,
            glossary = { GlossaryScreen() },
            loadIsland = { id -> ApiClient.HomeIsland(id, emptyMap(), 0, emptyList()) },
        )
    }
    /** Insel-Ausbau Scheibe 1: frische Insel (10 Punkte) = überall Baumstamm-Ruinen. */
    @Test fun islandRuinState() = shot("31-insel-ruinen") {
        VillageTheme(true) {
            HubIsland(
                decor = emptyMap(), labels = true, seed = 1L, animate = false,
                life = ApiClient.HomeIsland(1, emptyMap(), 10, emptyList()),
            )
        }
    }
    /** Volle Insel (400 Punkte) = überall ausgebaute Gebäude (Regel-Fall). */
    @Test fun islandBuiltState() = shot("32-insel-ausgebaut") {
        VillageTheme(true) {
            HubIsland(
                decor = emptyMap(), labels = true, seed = 1L, animate = false,
                life = ApiClient.HomeIsland(1, emptyMap(), 400, emptyList()),
            )
        }
    }
    @Test fun boatSheet() = shot("25-schiff") {
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF4FC3C7))) {
            androidx.compose.foundation.layout.Box(
                androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                    .background(androidx.compose.ui.graphics.Color(0xFF123E4A), androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .padding(top = 24.dp),
            ) { IsleTypography { BoatSheetContent(letter(1, 3, "Anna", "timed", false), "Anna", nowSec) } }
        }
    }
    @Test fun islandHome() = shot("22-meine-insel") { Isles(start = "home") }
    /** Pinch-zoom into the own island: island stays below the title, signs keep their size. */
    @Test fun islandHomeZoomed() = shot("27-insel-zoom", gesture = {
        rule.onRoot().performTouchInput {
            val c = center
            pinch(c - androidx.compose.ui.geometry.Offset(40f, 0f), c - androidx.compose.ui.geometry.Offset(260f, -60f),
                c + androidx.compose.ui.geometry.Offset(40f, 0f), c + androidx.compose.ui.geometry.Offset(260f, -60f))
        }
    }) { Isles(start = "home") }
    @Test fun islandHomeDoubleTap() = shot("28-insel-doppeltipp", gesture = {
        rule.onRoot().performTouchInput { doubleClick(androidx.compose.ui.geometry.Offset(width * .3f, height * .62f)) }
    }) { Isles(start = "home") }
    /** Gerfried-Fund: Nach Doppeltipp-Zoom nach oben wischen — die Insel darf nicht
     * aus dem Bild geschnitten werden, nur Stille-Wasser über ihr darf sichtbar sein. */
    @Test fun islandPanUp() = shot("33-insel-wischen-hoch", gesture = {
        rule.onRoot().performTouchInput { doubleClick(androidx.compose.ui.geometry.Offset(width * .5f, height * .6f)) }
        repeat(40) { rule.mainClock.advanceTimeByFrame() }
        rule.onRoot().performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width * .5f, height * .7f),
                  androidx.compose.ui.geometry.Offset(width * .5f, height * .25f), 400L)
        }
        repeat(20) { rule.mainClock.advanceTimeByFrame() }
    }) { Isles(start = "home") }
    @Test fun islandHomeSingleTap() = shot("29-insel-einzeltipp", gesture = {
        rule.onAllNodesWithContentDescription("Posteingang").filter(androidx.compose.ui.test.hasClickAction())[0].performClick()
        repeat(30) { rule.mainClock.advanceTimeByFrame() }
    }) { Isles(start = "home") }
    @Test fun islandVisit() = shot("23-besuch") { Isles(start = "visit:2") }
    // 25-bauen / 26-ort (life place plots) are gone since 7.9.7: places are special decoration in the build bar.
    @Test fun decorPicker() = shot("24-deko-wahl") {
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(Isle.SeaBottom).padding(top = 300.dp).then(androidx.compose.ui.Modifier.background(Isle.Card))) {
            VillageTheme(true) { DecorPickerContent("palm", IsleDecor.labels.keys.mapIndexed { i, k -> ApiClient.DecorItem(k, i * 20, i < 6) }, 140, {}, {}) }
        }
    }

    @Test fun islandsMap() = shot("20-inseln") { Isles() }
    @Test fun islandPlacePost() = shot("30-post") { Isles(start = "home", placeKey = "POST") }
    @Test fun islandPlaceHarbour() = shot("31-hafen") { Isles(start = "home", placeKey = "HARBOUR") }
    @Test fun islandPlaceLighthouse() = shot("32-leuchtturm") { Isles(start = "home", placeKey = "LIGHTHOUSE") }
    @Test fun islandPlaceLibrary() = shot("33-bibliothek") { Isles(start = "home", placeKey = "LIBRARY") }
    @Test fun islandPlaceHall() = shot("34-gemeindehaus") { Isles(start = "home", placeKey = "CAMPFIRE") }
    @Test fun islandPlaceHouse() = shot("35-haus") { Isles(start = "home", placeKey = "HOUSE") }
    @Composable private fun EvolutionIsland(creative: Boolean = false, grown: Boolean = false, start: String = "home", startingPoints: Int = 500, rejectFirst: Boolean = false,
                                            nav: Boolean = false, withLife: Boolean = false, level: Int = 3, eligible: Boolean = false, starters: Boolean = false,
                                            version: Int = PlacementRule.CURRENT, legacyDecor: Boolean = false) {
        var creativeOn by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(creative) }
        var state by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(ApiClient.IslandConstruction(
            mode=if(creative) "creative" else "normal", available=startingPoints, ageDays=if(startingPoints==0) 0 else 21,
            buildings=if(grown) IsleBuilding.entries.associate { it.name to level } else emptyMap(),land=if(grown) 5 else 0,
            starters=if(starters) IsleBuilding.entries.map { it.name }.toSet() else emptySet(),
            decor=(if(withLife) listOf(ApiClient.IslandDecor(0,"flowers",listOf(8,8),0),ApiClient.IslandDecor(100,"palm",listOf(13,9)),ApiClient.IslandDecor(101,"bench",listOf(14,13)),ApiClient.IslandDecor(201,"place_hut",listOf(10,10),name="Brennerhaus")) else emptyList()) +
                (if(legacyDecor) listOf(ApiClient.IslandDecor(102,"gnome_legacy",listOf(8,11))) else emptyList()),
            gridVersion=version)) }
        var firstAttempt by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
        val items=IsleDecor.labels.keys.mapIndexed { i, k -> ApiClient.DecorItem(k, i * 30, i * 30 <= startingPoints) }
        val home=ApiClient.HomeIsland(1,emptyMap(),startingPoints,items,construction=state,
            places=if(withLife) mapOf(1 to ApiClient.LifePlace("hut","Brennerhaus")) else emptyMap(), plots=if(withLife) 5 else 0)
        val friendHome=ApiClient.HomeIsland(2,emptyMap(),500,emptyList(),construction=ApiClient.IslandConstruction(buildings=mapOf("POST" to 2,"LIGHTHOUSE" to 3),land=3))
        VillageTheme(true) { IslandWorld(
            ownName="Gregor",ownEmoji="🦉",friends=friends,groups=emptyList(),
            islands=ApiClient.Islands(startingPoints,listOf(ApiClient.IslandInfo(2,0,0,0,1,40,friendHome))),
            quests=emptyList(),letters=emptyList(),topics=emptyList(),opened=emptyMap(),onBack={},onChat={},onComposeLetter={},onOpenLetter={},
            onLockedTap={},onTopics={},onAllTopics={},onGlossary={},onPeople={},onCreateQuest={_,_,_,_,_,_->},onQuestDone={_,_->},onQuestDelete={},
            ownId=1, startView=start, creativeActive=creativeOn, creativeEligible=eligible,
            onCreativeSwitch={ on -> creativeOn=on; state=state.copy(mode=if(on) "creative" else "normal") },
            mainNavigation=if(nav) ({ LayermaxxingTheme("light") { MainNavigationBar(listOf(MainTab.CHATS, MainTab.CASTLES, MainTab.MORE), MainTab.CASTLES, 2) {} } }) else null,
            loadIsland={id->if(id==1L) home.copy(construction=state) else friendHome},
            changeConstruction={c,req->
                check(c.revision==state.revision) { "409 stale revision" }
                if(rejectFirst && firstAttempt) { firstAttempt=false;state=state.copy(revision=state.revision+1);throw IllegalStateException("409 Insel geändert") }
                val b=req.building
                state=when(req.action) {
                    "upgrade"->c.copy(buildings=c.buildings+(b!! to ((c.buildings[b]?:0)+1)),positions=req.position?.let { c.positions+(b to it) } ?: c.positions,revision=c.revision+1)
                    "build"->c.copy(starters=c.starters+b!!,positions=c.positions+(b to req.position!!),revision=c.revision+1)
                    "move"->c.copy(positions=c.positions+(b!! to req.position!!),revision=c.revision+1)
                    "expand"->c.copy(land=c.land+1,revision=c.revision+1)
                    "downgrade"->c.copy(buildings=c.buildings+(b!! to ((c.buildings[b]?:0)-1)),revision=c.revision+1)
                    "demolish"->c.copy(buildings=c.buildings-b!!,positions=c.positions-b,revision=c.revision+1)
                    "decor_place"->c.copy(decor=c.decor+ApiClient.IslandDecor((c.decor.maxOfOrNull { it.id } ?: 99)+1,req.item!!,req.position!!),revision=c.revision+1)
                    "decor_move"->c.copy(decor=c.decor.map { if(it.id==req.decorId) it.copy(position=req.position!!) else it },revision=c.revision+1)
                    "decor_remove"->c.copy(decor=c.decor.filter { it.id!=req.decorId },revision=c.revision+1)
                    else->c
                }.copy(mode=state.mode); home.copy(construction=state)
            }
        ) }
    }
    @Test fun islandNewSandStart() = shot("61-sandhaufen-start") { EvolutionIsland(startingPoints=0) }
    @Test fun islandEarnedGrowth() = shot("62-insel-meisterbau") { EvolutionIsland(grown=true) }
    @Test fun islandBuildingPreviewInteraction() = shot("63-bauen-auswahl",captureDialogs=true,gesture={
        rule.onAllNodesWithText("Bauen")[0].performClick()
        rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithContentDescription("Post platzieren")[0].assertExists()
    }) { EvolutionIsland() }
    @Test fun islandPlacementPreview() = shot("64-platzieren-raster",gesture={
        rule.onAllNodesWithText("Bauen")[0].performClick()
        rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithContentDescription("Post platzieren")[0].performScrollTo(); settle()
        rule.onAllNodesWithContentDescription("Post platzieren")[0].performClick()
        rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithContentDescription("Bestätigen")[0].assertExists()
    }) { EvolutionIsland(creative=true) }
    @Test fun islandCreativeBuildInteraction() = shot("65-kreativ-gebaut",gesture={
        rule.onAllNodesWithText("Bauen")[0].performClick()
        rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithContentDescription("Post platzieren")[0].performScrollTo(); settle()
        rule.onAllNodesWithContentDescription("Post platzieren")[0].performClick()
        rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithContentDescription("Bauplatzraster")[0].performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width*.316f,height*.504f),androidx.compose.ui.geometry.Offset(width*.5f,height*.55f),600)
        }
        rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithContentDescription("Bestätigen")[0].performClick()
        rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithContentDescription("Post")[0].assertExists()
    }) { EvolutionIsland(creative=true) }
    @Test fun islandMoveInteraction() = shot("68-gebaeude-verschieben",gesture={
        rule.onAllNodesWithContentDescription("Post")[0].performClick()
        rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithContentDescription("Post").filter(androidx.compose.ui.test.hasClickAction())[0].performTouchInput { longClick() }
        rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithContentDescription("Bauplatzraster")[0].performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width*.12f,height*.47f),androidx.compose.ui.geometry.Offset(width*.2f,height*.55f),600)
        }
        rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithContentDescription("Bestätigen")[0].performClick();rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithContentDescription("Bauplatzraster").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Post")[0].assertExists()
    }) { EvolutionIsland(creative=true,grown=true) }
    @Test fun islandInvalidPlacement() = shot("69-ungueltiger-bauplatz",gesture={
        rule.onAllNodesWithText("Bauen")[0].performClick();rule.mainClock.advanceTimeBy(600)
        // 7.9.6: the sea is out of reach (the preview clamps to land); an occupied cell is the invalid spot
        rule.onAllNodesWithContentDescription("Post platzieren")[0].performScrollTo(); settle()
        rule.onAllNodesWithContentDescription("Post platzieren")[0].performClick();rule.mainClock.advanceTimeBy(600)
        groundTap(listOf(11, 7), 0); settle()
        rule.onAllNodesWithContentDescription("Bestätigen")[0].performClick(); settle()
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].performClick(); settle()
        rule.onAllNodesWithContentDescription("Freunde platzieren")[0].performScrollTo(); settle()
        rule.onAllNodesWithContentDescription("Freunde platzieren")[0].performClick(); settle()
        groundTap(listOf(11, 7), 0); settle()
        rule.onAllNodesWithContentDescription("Bestätigen")[0].assertIsNotEnabled()
    }) { EvolutionIsland(creative=true) }
    @Test fun islandCancelPlacementKeepsStarterEmpty() = shot("70-platzieren-abgebrochen",gesture={
        rule.onAllNodesWithText("Bauen")[0].performClick();rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithContentDescription("Post platzieren")[0].performScrollTo(); settle()
        rule.onAllNodesWithContentDescription("Post platzieren")[0].performClick();rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithContentDescription("Abbrechen")[0].performClick();rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithContentDescription("Post").assertCountEquals(0)
    }) { EvolutionIsland(creative=true) }
    @Test fun islandExpansionPreviewInteraction() = shot("71-land-vorschau",captureDialogs=true,gesture={
        rule.onAllNodesWithText("Bauen")[0].performClick();rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithText("Land")[0].performClick();rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithContentDescription("Land erweitern")[0].performClick();rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithText("Insel vergrößern")[0].assertExists()
    }) { EvolutionIsland() }
    @Test fun islandSelectionToolbar() = shot("72-gebaeude-kontext",gesture={
        rule.onAllNodesWithContentDescription("Post")[0].performClick();rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithText("Verschieben").assertCountEquals(0)
        rule.onAllNodesWithText("Info")[0].assertExists()
    }) { EvolutionIsland(grown=true) }
    @Test fun islandUpgradePreviewAfterSelection() = shot("73-ausbau-vorschau",captureDialogs=true,gesture={
        rule.onAllNodesWithContentDescription("Post")[0].performClick();rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithText("Info")[0].performClick();rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithText("Vollständig ausgebaut")[0].assertExists()
    }) { EvolutionIsland(grown=true) }
    @Test fun islandPlacementBackCancelsWithoutTrappingNavigation() = shot("74-zurueck-bricht-ab",gesture={
        rule.onAllNodesWithText("Bauen")[0].performClick();rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithContentDescription("Post platzieren")[0].performScrollTo(); settle()
        rule.onAllNodesWithContentDescription("Post platzieren")[0].performClick();rule.mainClock.advanceTimeBy(600)
        // 7.9.6: while a piece is held only ✓/✗ and the hint are shown; Android back cancels like ✗
        // and brings the build sheet back, a second back closes it
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; settle()
        rule.onAllNodesWithText("Meine Insel")[0].assertExists()
        rule.onAllNodesWithContentDescription("Post").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Bauleiste")[0].assertExists()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; settle()
        rule.onAllNodesWithText("Bauen")[0].assertExists()
    }) { EvolutionIsland(creative=true) }
    @Test fun islandConflictReloadRetainsDraftAndAllowsRetry() = shot("75-konflikt-erholung",gesture={
        rule.onAllNodesWithText("Bauen")[0].performClick();rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithContentDescription("Post platzieren")[0].performScrollTo(); settle()
        rule.onAllNodesWithContentDescription("Post platzieren")[0].performClick();rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithContentDescription("Bestätigen")[0].performClick();rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithContentDescription("Bauplatzraster")[0].assertExists()
        rule.onAllNodesWithContentDescription("Bestätigen")[0].performClick();rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithContentDescription("Bauplatzraster").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Post")[0].assertExists()
    }) { EvolutionIsland(creative=true,rejectFirst=true) }
    @Test fun islandActualFriendOverview() = shot("66-freunde-echter-ausbau") { EvolutionIsland(start="map") }
    @Test fun islandActualFriendVisit() = shot("67-freund-besuch") { EvolutionIsland(start="visit:2") }

    @Test fun islandsMapDebug() = shot("26-inseln-debug") {
        androidx.compose.runtime.CompositionLocalProvider(LocalHitboxDebug provides true) { Isles() }
    }
    @Test fun islandsEmpty() = shot("21-inseln-leer") { Isles(false) }

    // 7.9.4 Kreativbau: real island in creative mode, base-builder shelf, long-press edit, demolish.
    @Test fun kbStarterIsland() = shot("80-startinsel") { EvolutionIsland(startingPoints=0, nav=true) }
    @Test fun kbBuildShelfWithArtwork() = shot("81-bauleiste-bilder", gesture={
        rule.onAllNodesWithText("Bauen")[0].performClick(); rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithContentDescription("Post platzieren")[0].assertExists()
        rule.onAllNodesWithContentDescription("Leuchtturm platzieren").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Freunde platzieren")[0].assertExists()
    }) { EvolutionIsland(nav=true) }
    @Test fun kbPlacementPreview() = shot("82-platzierung", gesture={
        rule.onAllNodesWithText("Bauen")[0].performClick(); rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithContentDescription("Freunde platzieren")[0].performScrollTo(); settle()
        rule.onAllNodesWithContentDescription("Freunde platzieren")[0].performClick(); rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithContentDescription("Bauplatzraster")[0].performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width*.5f,height*.55f),androidx.compose.ui.geometry.Offset(width*.477f,height*.642f),500)
        }
        rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithContentDescription("Bestätigen")[0].assertExists()
    }) { EvolutionIsland(nav=true, withLife=true) }
    @Test fun kbLongPressEdit() = shot("83-langes-druecken", gesture={
        rule.onAllNodesWithContentDescription("Post").filter(androidx.compose.ui.test.hasClickAction())[0].performTouchInput { longClick() }
        rule.mainClock.advanceTimeBy(800)
        rule.onAllNodesWithContentDescription("Bauplatzraster")[0].assertExists()
        rule.onAllNodesWithContentDescription("Bauplatzraster")[0].performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width*.2f,height*.5f),androidx.compose.ui.geometry.Offset(width*.3f,height*.62f),500)
        }
        rule.mainClock.advanceTimeBy(600)
    }) { EvolutionIsland(grown=true, nav=true) }
    @Test fun kbDemolishDialog() = shot("84-abreissen-dialog", captureDialogs=true, gesture={
        rule.onAllNodesWithContentDescription("Post").filter(androidx.compose.ui.test.hasClickAction())[0].performClick(); rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithText("Abreißen")[0].performClick(); rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithText("Post abreißen?")[0].assertExists()
    }) { EvolutionIsland(grown=true, nav=true) }
    @Test fun kbDemolishDone() = shot("85-abgerissen", gesture={
        rule.onAllNodesWithContentDescription("Post").filter(androidx.compose.ui.test.hasClickAction())[0].performClick(); rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithText("Abreißen")[0].performClick(); rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithText("Abreißen")[1].performClick(); rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithContentDescription("Post").filter(androidx.compose.ui.test.hasClickAction()).assertCountEquals(0)
    }) { EvolutionIsland(grown=true, nav=true) }
    @Test fun kbCreativeIslandStaysTappable() = shot("86-kreativ-antippbar", gesture={
        rule.onAllNodesWithContentDescription("Wörterbuch").filter(androidx.compose.ui.test.hasClickAction())[0].performClick(); rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithText("Abreißen")[0].assertExists()
        rule.onAllNodesWithText("Verschieben").assertCountEquals(0)
    }) { EvolutionIsland(creative=true, grown=true, nav=true, withLife=true) }
    @Test fun kbMyIslandHeaderWithUnchangedNavigation() = shot("87-meine-insel-navigation", gesture={
        rule.onAllNodesWithText("Meine Insel")[0].assertExists()
        rule.onAllNodesWithText("Übersicht")[0].assertExists()
        rule.onAllNodesWithText("Chats")[0].assertExists(); rule.onAllNodesWithText("Inseln")[0].assertExists(); rule.onAllNodesWithText("Mehr")[0].assertExists()
    }) { EvolutionIsland(nav=true, withLife=true, startingPoints=140) }
    @Test fun kbIslandMenu() = shot("88-inselmenue", captureDialogs=true, gesture={
        rule.onAllNodesWithText("Menü")[0].performClick(); rule.mainClock.advanceTimeBy(800)
        rule.onAllNodesWithText("Inselmenü")[0].assertExists()
    }) { EvolutionIsland(grown=true, nav=true) }
    @Test fun kbDecorShelf() = shot("89-deko-bauleiste", gesture={
        rule.onAllNodesWithText("Bauen")[0].performClick(); rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithText("Deko")[0].performClick(); rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithContentDescription("Laterne platzieren")[0].assertExists()
    }) { EvolutionIsland(nav=true, withLife=true, startingPoints=140) }
    @Test fun kbDecorPlacedFreely() = shot("90-deko-frei-platziert", gesture={
        rule.onAllNodesWithText("Bauen")[0].performClick(); rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithText("Deko")[0].performClick(); rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithContentDescription("Laterne platzieren")[0].performScrollTo(); settle()
        rule.onAllNodesWithContentDescription("Laterne platzieren")[0].performClick(); rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithContentDescription("Bauplatzraster")[0].assertExists()
        rule.onAllNodesWithContentDescription("Bestätigen")[0].performClick(); rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithContentDescription("Bauplatzraster").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Laterne").filter(androidx.compose.ui.test.hasClickAction())[0].assertExists()
    }) { EvolutionIsland(nav=true, withLife=true, startingPoints=140) }
    @Test fun kbDecorLongPressMove() = shot("91-deko-verschieben", gesture={
        rule.onAllNodesWithContentDescription("Palme").filter(androidx.compose.ui.test.hasClickAction())[0].performTouchInput { longClick() }
        rule.mainClock.advanceTimeBy(800)
        rule.onAllNodesWithContentDescription("Bauplatzraster")[0].assertExists()
    }) { EvolutionIsland(nav=true, withLife=true, startingPoints=140) }
    @Test fun kbOverviewIsClearlyDifferent() = shot("92-uebersicht", gesture={
        rule.onAllNodesWithText("Übersicht · alle Inseln")[0].assertExists()
    }) { EvolutionIsland(start="map", nav=true, withLife=true) }

    // 7.9.5 Clash-of-Clans controls: shop button, shelf, bubbles at the piece, info bar, edit mode, isometric grid.
    private fun groundTap(cell: List<Int>, land: Int) = rule.onAllNodesWithContentDescription("Bauplatzraster")[0].performTouchInput {
        val (fx, fy) = IslandGrid.project(cell, land); click(androidx.compose.ui.geometry.Offset(width * fx, height * fy))
    }
    private fun settle(ms: Long = 600) { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); rule.mainClock.advanceTimeBy(ms) }
    @Test fun cocShopButton() = shot("coc-01-shop-button") { EvolutionIsland(grown=true, nav=true, withLife=true, startingPoints=140) }
    @Test fun cocShopShelf() = shot("coc-02-shop-leiste", gesture={
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].performClick(); settle()
        rule.onAllNodesWithContentDescription("Mein Haus auswählen")[0].assertExists()
    }) { EvolutionIsland(grown=true, nav=true, withLife=true, startingPoints=140) }
    @Test fun cocPlacementNewBuilding() = shot("coc-03-platzierung", gesture={
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].performClick(); settle()
        rule.onAllNodesWithContentDescription("Freunde platzieren")[0].performScrollTo(); settle()
        rule.onAllNodesWithContentDescription("Freunde platzieren")[0].performClick(); settle()
        groundTap(listOf(13, 7), 0); settle()
        rule.onAllNodesWithContentDescription("Bestätigen")[0].assertExists(); rule.onAllNodesWithContentDescription("Abbrechen")[0].assertExists()
    }) { EvolutionIsland(creative=true, nav=true) }
    @Test fun cocMoveBuildingLifted() = shot("coc-04-verschieben", gesture={
        rule.onAllNodesWithContentDescription("Post").filter(androidx.compose.ui.test.hasClickAction())[0].performClick(); settle()
        rule.onAllNodesWithContentDescription("Post").filter(androidx.compose.ui.test.hasClickAction())[0].performTouchInput { longClick() }; settle()
        groundTap(listOf(6, 11), 5); settle()
        rule.onAllNodesWithContentDescription("Bestätigen")[0].assertExists()
    }) { EvolutionIsland(creative=true, grown=true, nav=true, withLife=true) }
    @Test fun cocInfoBar() = shot("coc-05-info-leiste", gesture={
        rule.onAllNodesWithContentDescription("Wörterbuch").filter(androidx.compose.ui.test.hasClickAction())[0].performClick(); settle()
        rule.onAllNodesWithText("Ausbauen")[0].assertExists(); rule.onAllNodesWithText("Abreißen")[0].assertExists()
    }) { EvolutionIsland(grown=true, level=1, nav=true, withLife=true, startingPoints=300) }
    @Test fun cocDecorPlacement() = shot("coc-06-deko", gesture={
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].performClick(); settle()
        rule.onAllNodesWithText("Deko")[0].performClick(); settle()
        rule.onAllNodesWithContentDescription("Laterne platzieren")[0].performScrollTo(); settle()
        rule.onAllNodesWithContentDescription("Laterne platzieren")[0].performClick(); settle()
        rule.onAllNodesWithContentDescription("Bestätigen")[0].assertExists()
    }) { EvolutionIsland(grown=true, nav=true, withLife=true, startingPoints=200) }
    @Test fun cocEditMode() = shot("coc-07-bearbeiten", gesture={
        rule.onAllNodesWithText("Bearbeiten").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Post").filter(androidx.compose.ui.test.hasClickAction())[0].performClick(); settle()
        rule.onAllNodesWithContentDescription("Post").filter(androidx.compose.ui.test.hasClickAction())[0].performTouchInput { longClick() }; settle()
        rule.onAllNodesWithContentDescription("Baufeld mit Kästchen")[0].assertExists()
    }) { EvolutionIsland(grown=true, nav=true, withLife=true, startingPoints=140) }
    @Test fun cocGridInvalidSpot() = shot("coc-08-raster-ungueltig", gesture={
        // 7.9.6: the sea is out of reach, an occupied cell is the impossible spot
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].performClick(); settle()
        rule.onAllNodesWithContentDescription("Post platzieren")[0].performScrollTo(); settle()
        rule.onAllNodesWithContentDescription("Post platzieren")[0].performClick(); settle()
        groundTap(listOf(11, 7), 0); settle()
        rule.onAllNodesWithContentDescription("Bestätigen")[0].performClick(); settle()
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].performClick(); settle()
        rule.onAllNodesWithContentDescription("Freunde platzieren")[0].performScrollTo(); settle()
        rule.onAllNodesWithContentDescription("Freunde platzieren")[0].performClick(); settle()
        groundTap(listOf(11, 7), 0); settle()
        rule.onAllNodesWithContentDescription("Bestätigen")[0].assertIsNotEnabled()
    }) { EvolutionIsland(creative=true, nav=true) }
    @Test fun cocGridSmallLandNearSpot() = shot("coc-09-raster-nahe-gebaeude", gesture={
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].performClick(); settle()
        rule.onAllNodesWithContentDescription("Post platzieren")[0].performScrollTo(); settle()
        rule.onAllNodesWithContentDescription("Post platzieren")[0].performClick(); settle()
        groundTap(listOf(11, 8), 5); settle()
    }) { EvolutionIsland(creative=true, grown=true, level=0, nav=true, withLife=true) }
    @Test fun cocOverview() = shot("coc-10-uebersicht") { EvolutionIsland(start="map", nav=true, withLife=true) }
    @Test fun cocVisit() = shot("coc-11-besuch") { EvolutionIsland(start="visit:2", nav=true) }

    // 7.9.6 "flow": one context at a time, placement only on land, long press moves in the same touch.
    private fun centralTag(): Pair<IsleBuilding, androidx.compose.ui.unit.DpRect> {
        val root = rule.onRoot().getBoundsInRoot()
        val mx = (root.left + root.right) / 2; val my = (root.top + root.bottom) / 2
        return IsleBuilding.entries.map { it to rule.onAllNodesWithContentDescription(it.label).filter(androidx.compose.ui.test.hasClickAction())[0].getBoundsInRoot() }
            .minBy { (_, l) -> kotlin.math.hypot(((l.left + l.right) / 2 - mx).value, ((l.top + l.bottom) / 2 - my).value) }
    }
    @Test fun flowRest() = shot("flow-01-ruhezustand") { EvolutionIsland(grown=true, nav=true, withLife=true, startingPoints=140) }
    @Test fun flowSelected() = shot("flow-02-gebaeude-angetippt", gesture={
        rule.onAllNodesWithContentDescription("Wörterbuch").filter(androidx.compose.ui.test.hasClickAction())[0].performClick(); settle(900)
        rule.onAllNodesWithContentDescription("Gebäudeaktionen")[0].assertExists()
    }) { EvolutionIsland(grown=true, level=1, nav=true, withLife=true, startingPoints=300) }
    @Test fun flowBuildSheet() = shot("flow-03-bauleiste", gesture={
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].performClick(); settle()
        rule.onAllNodesWithContentDescription("Bauleiste")[0].assertExists()
    }) { EvolutionIsland(nav=true, withLife=true, startingPoints=140) }
    @Test fun flowPlacementFingerInTheWater() = shot("flow-04-platzierung-finger-im-wasser", gesture={
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].performClick(); settle()
        rule.onAllNodesWithContentDescription("Post platzieren")[0].performScrollTo(); settle()
        rule.onAllNodesWithContentDescription("Post platzieren")[0].performClick(); settle()
        val a = IslandGrid.project(listOf(11, 7), 0)
        groundTap(listOf(11, 7), 0); settle()
        // drag far out into the sea (top left): the preview stops on the nearest shore cell
        rule.onAllNodesWithContentDescription("Bauplatzraster")[0].performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width * a.first, height * a.second), androidx.compose.ui.geometry.Offset(width * .06f, height * .12f), 500)
        }
        settle(300)
        rule.onAllNodesWithContentDescription("Bestätigen")[0].assertIsEnabled()
    }) { EvolutionIsland(creative=true, nav=true) }
    @Test fun flowLongPressMove() = shot("flow-05-verschieben-longpress", gesture={
        val (b, tag) = centralTag()
        val c = ApiClient.IslandConstruction(buildings = IsleBuilding.entries.associate { it.name to 3 }, land = 5)
        val from = IslandLayout.defaultPosition(b)
        val target = listOf(2, 3, -2, -3).flatMap { dy -> listOf(0, 1, -1).map { dx -> listOf(from[0] + dx, from[1] + dy) } }.first { IslandLayout.valid(c, b, it) }
        val ground = rule.onAllNodesWithContentDescription("Boden")[0].getBoundsInRoot()
        val p0 = IslandGrid.project(from, 5); val p1 = IslandGrid.project(target, 5)
        rule.onRoot().performTouchInput {
            val down = androidx.compose.ui.geometry.Offset(((tag.left + tag.right) / 2).toPx(), (tag.top - 22.dp).toPx())
            val delta = androidx.compose.ui.geometry.Offset(((ground.right - ground.left) * (p1.first - p0.first)).toPx(), ((ground.bottom - ground.top) * (p1.second - p0.second)).toPx())
            down(down); advanceEventTime(LONG_PRESS_MS + 150)
            // still held: the piece is lifted and follows the same finger to a free cell
            for (k in 1..10) { moveTo(down + delta * (k / 10f)); advanceEventTime(30) }
        }
        settle(400)
        rule.onAllNodesWithContentDescription("Bauplatzraster")[0].assertExists()
        rule.onAllNodesWithContentDescription("Bestätigen")[0].assertIsEnabled()
    }) { EvolutionIsland(grown=true, nav=true, startingPoints=140) }
    @Test fun flowEditMode() = shot("flow-06-bearbeiten", gesture={
        rule.onAllNodesWithText("Bearbeiten").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Post").filter(androidx.compose.ui.test.hasClickAction())[0].performClick(); settle()
        rule.onAllNodesWithContentDescription("Post").filter(androidx.compose.ui.test.hasClickAction())[0].performTouchInput { longClick() }; settle()
        rule.onAllNodesWithContentDescription("Bauplatzraster")[0].assertExists()
    }) { EvolutionIsland(grown=true, nav=true, withLife=true, startingPoints=140) }
    @Test fun flowCreativeMenu() = shot("flow-07-kreativ-menue", captureDialogs=true, gesture={
        rule.onAllNodesWithText("Menü")[0].performClick(); settle(900)
        rule.onAllNodesWithText("Kreativmodus: Aus")[0].assertExists()
    }) { EvolutionIsland(grown=true, nav=true, withLife=true, startingPoints=140, eligible=true) }
    @Test fun flowCreativeSwitchedOn() = shot("flow-08-kreativ-an", gesture={
        rule.onAllNodesWithText("Menü")[0].performClick(); settle(900)
        rule.onAllNodesWithContentDescription("Kreativmodus")[0].performClick(); settle(900)
        rule.onAllNodesWithText("Kreativmodus · alles gratis")[0].assertExists()
    }) { EvolutionIsland(grown=true, nav=true, withLife=true, startingPoints=140, eligible=true) }
    // 7.9.7: production screen captures, with real navigation/selection before capture.
    @Test fun ausbauStarter() = shot("ausbau-01-start") { EvolutionIsland(startingPoints=0, nav=true) }
    @Test fun ausbauBuilt() = shot("ausbau-02-ausgebaut") { EvolutionIsland(grown=true, nav=true, level=2, withLife=true) }
    @Test fun ausbauMenuStarter() = shot("ausbau-03-menue-start", captureDialogs=true, gesture={
        rule.onAllNodesWithText("Menü")[0].performClick(); settle(900)
        rule.onAllNodesWithText("Briefbaum")[0].assertExists()
        rule.onAllNodesWithText("Aussichtspalme")[0].assertExists()
    }) { EvolutionIsland(startingPoints=0, nav=true) }
    @Test fun ausbauMenuBuilt() = shot("ausbau-04-menue-stufe2", captureDialogs=true, gesture={
        rule.onAllNodesWithText("Menü")[0].performClick(); settle(900)
        rule.onAllNodesWithText("Briefstube")[0].assertExists()
        rule.onAllNodesWithText("Signalstation")[0].assertExists()
    }) { EvolutionIsland(grown=true, level=2, nav=true, eligible=true) }
    @Test fun ausbauPreview() = shot("ausbau-05-grosse-vorschau", captureDialogs=true, gesture={
        rule.onAllNodesWithContentDescription("Post").filter(androidx.compose.ui.test.hasClickAction())[0].performClick(); settle()
        rule.onAllNodesWithText("Ausbauen")[0].performClick(); settle(900)
        rule.onAllNodesWithContentDescription("Ausbauvorschau: Briefstube")[0].assertExists()
        rule.onAllNodesWithText("Verschieben").assertCountEquals(0)
    }) { EvolutionIsland(grown=true, level=1, nav=true) }
    @Test fun ausbauSpecialShelf() = shot("ausbau-06-spezialdeko", gesture={
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].performClick(); settle()
        rule.onAllNodesWithContentDescription("Reiter Besondere Deko")[0].performClick(); settle(1400)
        rule.onAllNodesWithText("Besondere Deko 1/3")[0].assertExists()
    }) { EvolutionIsland(grown=true, level=1, nav=true, withLife=true) }
    @Test fun ausbauSmallLandGrid() = shot("ausbau-07-kleines-landraster", gesture={
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].performClick(); settle()
        rule.onAllNodesWithContentDescription("Post platzieren")[0].performScrollTo(); settle()
        rule.onAllNodesWithContentDescription("Post platzieren")[0].performClick(); settle()
    }) { EvolutionIsland(creative=true, nav=true) }
    @Test fun ausbauLargeLandGrid() = shot("ausbau-08-grosses-landraster", gesture={
        rule.onAllNodesWithContentDescription("Post").filter(androidx.compose.ui.test.hasClickAction())[0].performClick(); settle()
        rule.onAllNodesWithContentDescription("Post").filter(androidx.compose.ui.test.hasClickAction())[0].performTouchInput { longClick() }; settle()
    }) { EvolutionIsland(grown=true, level=2, nav=true) }
    @Test fun ausbauOverview() = shot("ausbau-09-uebersicht", captureDialogs=true, gesture={
        // 7.9.9: the overview is the painted back plate, no longer a pill in the menu
        rule.onAllNodesWithContentDescription("Zurück zur Übersicht")[0].performClick(); settle(900)
        rule.onAllNodesWithText("Übersicht · alle Inseln")[0].assertExists()
    }) { EvolutionIsland(grown=true, nav=true, withLife=true) }
    @Test fun ausbauVisit() = shot("ausbau-10-besuch") { EvolutionIsland(start="visit:2", nav=true) }
    // 7.9.9-start: Veit's phone feedback on 7.9.8, production composables after real interactions (JVM renders).
    private fun noClick(label: String) = rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("click $label") {
        it.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick)?.label == label }).assertCountEquals(0)
    @Test fun start69Menu() = shot("start69-01-inselmenue-ohne-pills", captureDialogs=true, gesture={
        rule.onAllNodesWithText("Menü")[0].performClick(); settle(900)
        rule.onAllNodesWithText("Inselmenü")[0].assertExists()
        for (l in listOf("Übersicht", "Bauen", "Einstellungen")) noClick(l)
    }) { EvolutionIsland(grown=true, level=1, nav=true, eligible=true) }
    @Test fun start69BackPlate() = shot("start69-02-meine-insel-zurueck", gesture={
        rule.onAllNodesWithContentDescription("Zurück zur Übersicht")[0].assertExists()
    }) { EvolutionIsland(grown=true, level=1, nav=true, withLife=true) }
    @Test fun start69EmptyShelf() = shot("start69-03-leere-insel-bauleiste", gesture={
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].performClick(); settle()
        rule.onAllNodesWithText("Nachtlager")[0].assertExists()
        rule.onAllNodesWithContentDescription("Abschnitt Gebäude").assertCountEquals(0)
    }) { EvolutionIsland(startingPoints=0, nav=true) }
    @Test fun start69EmptyShelfGrey() = shot("start69-04-leere-insel-bauleiste-grau", gesture={
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].performClick(); settle()
        rule.onAllNodesWithContentDescription("Reiter Besondere Deko")[0].performClick(); settle(1400)
    }) { EvolutionIsland(startingPoints=0, nav=true) }
    @Test fun start69HammockBuilt() = shot("start69-05-haengematte-gratis-gebaut", gesture={
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].performClick(); settle()
        rule.onAllNodesWithContentDescription("Mein Haus platzieren")[0].performClick(); settle()
        rule.onAllNodesWithContentDescription("Bestätigen")[0].performClick(); settle(900)
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].performClick(); settle()
        rule.onAllNodesWithContentDescription("Post platzieren")[0].performClick(); settle()
        rule.onAllNodesWithContentDescription("Bestätigen")[0].performClick(); settle(900)
        rule.onAllNodesWithContentDescription("Mein Haus").filter(androidx.compose.ui.test.hasClickAction())[0].assertExists()
    }) { EvolutionIsland(startingPoints=0, nav=true) }
    @Test fun start69Actions() = shot("start69-06-gebaeude-aktionen", gesture={
        rule.onAllNodesWithContentDescription("Post").filter(androidx.compose.ui.test.hasClickAction())[0].performClick(); settle()
        rule.onAllNodesWithText("Verschieben").assertCountEquals(0)
        rule.onAllNodesWithText("Ausbauen")[0].assertExists()
    }) { EvolutionIsland(grown=true, level=1, nav=true, withLife=true) }
    private fun openPlace(label: String) { rule.onAllNodesWithText("Menü")[0].performClick(); settle(900)
        rule.onAllNodesWithContentDescription("$label öffnen")[0].performClick(); settle(1200) }
    @Test fun start69LighthouseStage0() = shot("start69-07-leuchtturm-stufe0", gesture={
        openPlace("Freunde"); rule.onAllNodesWithContentDescription("Ort: Aussichtspalme")[0].assertExists()
    }) { EvolutionIsland(startingPoints=0, nav=true) }
    @Test fun start69LighthouseStage2() = shot("start69-08-leuchtturm-stufe2", gesture={
        openPlace("Freunde"); rule.onAllNodesWithContentDescription("Ort: Signalstation")[0].assertExists()
    }) { EvolutionIsland(grown=true, level=2, nav=true) }
    @Test fun start69PostStage0() = shot("start69-09-post-stufe0", gesture={
        openPlace("Post"); rule.onAllNodesWithContentDescription("Ort: Briefbaum")[0].assertExists()
    }) { EvolutionIsland(startingPoints=0, nav=true) }
    private val glossarySeed = listOf(
        ApiClient.GlossaryTerm(1, "Kalauer", "Gerfried", listOf(ApiClient.GlossaryExplanation(1, "Was ist gelb und kann schießen? Eine Banone.", "Gerfried", 0, 0, false)), false, "Witze"),
        ApiClient.GlossaryTerm(2, "Carpe diem", "Veit", listOf(ApiClient.GlossaryExplanation(2, "Nutze den Tag. (Horaz)", "Veit", 0, 0, false)), false, "Zitate"),
        ApiClient.GlossaryTerm(3, "Morgenstund", "Veit", listOf(ApiClient.GlossaryExplanation(3, "… hat Gold im Mund.", "Veit", 0, 0, true)), true, "Sprüche"),
        ApiClient.GlossaryTerm(4, "Layermaxxing", "Gregor", listOf(ApiClient.GlossaryExplanation(4, "Ebenen stapeln, bis es passt.", "Gregor", 0, 0, false)), false),
    )
    @Test fun start69Glossary() = shot("start69-10-woerterbuch-kategorien") {
        VillageTheme(true) { LibraryBook(onClose = {}, glossary = { GlossaryScreen(seed = glossarySeed, addTerm = { _, _, _ -> }) }, level = 0) }
    }
    @Test fun start69GlossaryFilter() = shot("start69-11-woerterbuch-filter-witze", gesture={
        rule.onAllNodesWithContentDescription("Register Witze")[0].performClick(); settle()
    }) { VillageTheme(true) { LibraryBook(onClose = {}, glossary = { GlossaryScreen(seed = glossarySeed, addTerm = { _, _, _ -> }) }, level = 2) } }
    @Test fun start69GlossaryNewWord() = shot("start69-12-woerterbuch-neues-wort", captureDialogs=true, gesture={
        rule.onAllNodesWithContentDescription("Feder – Wort hinzufügen")[0].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); settle()
    }) { VillageTheme(true) { LibraryBook(onClose = {}, glossary = { GlossaryScreen(seed = glossarySeed, addTerm = { _, _, _ -> }) }, level = 3) } }

    // 7.9.10 island placement + building menus (Veit): production IslandWorld after real interactions on fixtures.
    // JVM/Robolectric renders of real Android windows, not device recordings. Written to VILLAGE_SHOTS.
    private val lifeDecor = listOf(ApiClient.IslandDecor(0,"flowers",listOf(8,8),0),ApiClient.IslandDecor(100,"palm",listOf(13,9)),ApiClient.IslandDecor(101,"bench",listOf(14,13)),ApiClient.IslandDecor(201,"place_hut",listOf(10,10),name="Brennerhaus"))
    private fun grownFixture(level: Int = 3) = ApiClient.IslandConstruction(buildings=IsleBuilding.entries.associate { it.name to level }, land=5, decor=lifeDecor)
    /** Free cells for [b] on the grown fixture, nearest to the middle first; [coast] filters the harbour's rim. */
    private fun freeCells(b: IsleBuilding, coast: Boolean?, level: Int = 3): List<List<Int>> { val c = grownFixture(level)
        return IslandGrid.buildableCells(5).filter { (coast == null || IslandGrid.coast(it, 5) == coast) && it != IslandLayout.defaultPosition(b) && !IslandLayout.blocked(c, it, big = true, ignoreBuilding = b) }
            .sortedBy { kotlin.math.abs(it[0] - 10) + kotlin.math.abs(it[1] - 11) } }
    private fun liftPiece(label: String) { rule.onAllNodesWithContentDescription(label).filter(androidx.compose.ui.test.hasClickAction())[0].performTouchInput { longClick() }; settle() }
    private fun gridState() = rule.onAllNodesWithContentDescription("Baufeld mit Kästchen")[0].fetchSemanticsNode().config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.StateDescription)
    @Test fun placementRestNoMarks() = shot("placement-01-ruhezustand-keine-roten-markierungen", gesture={
        rule.onAllNodesWithContentDescription("Baufeld mit Kästchen").assertCountEquals(0)
    }) { EvolutionIsland(creative=true, grown=true, nav=true, withLife=true) }
    @Test fun placementMoveBuildingObstaclesRed() = shot("placement-02-post-verschieben-hindernisse-rot-nachbarfeld-frei", gesture={
        liftPiece("Post")
        // right beside the post's own spot (4,9): overlaps only its own old footprint, every other piece is outlined red.
        // Beside it, not in front: the ✓/✗ bubbles hang half a cell below the held foot, so a tap on (5,10) hit ✓
        // (confirming the unmoved post and ending the placement) instead of the grid.
        val beside = listOf(5, 9)
        check(IslandLayout.valid(grownFixture(), IsleBuilding.POST, beside) && beside[1] <= IslandLayout.defaultPosition(IsleBuilding.POST)[1]) { "$beside" }
        groundTap(beside, 5); settle()
        check(gridState() == "9 belegte Flächen rot markiert") { gridState().toString() }
        rule.onAllNodesWithContentDescription("Bestätigen")[0].assertIsEnabled()
    }) { EvolutionIsland(creative=true, grown=true, nav=true, withLife=true) }
    @Test fun placementMoveBuildingNextToHut() = shot("placement-03-post-direkt-neben-fremder-flaeche-gueltig", gesture={
        liftPiece("Post")
        // the closest free cell left of the special decoration "Brennerhaus" (10,10): touching, no hidden gap
        val cell = listOf(10 - IslandFootprint.V4.big[5] - IslandFootprint.V4.big[5] - 1, 10)
        check(IslandLayout.valid(grownFixture(), IsleBuilding.POST, cell)) { "$cell" }
        groundTap(cell, 5); settle()
        rule.onAllNodesWithContentDescription("Bestätigen")[0].assertIsEnabled()
    }) { EvolutionIsland(creative=true, grown=true, nav=true, withLife=true) }
    @Test fun placementMoveBuildingOntoForeignFootprint() = shot("placement-04-post-auf-fremder-flaeche-ungueltig", gesture={
        liftPiece("Post")
        // 7.9.11 (version 4): the hut holds exactly its own cell (10,10)
        groundTap(listOf(10, 10), 5); settle()
        rule.onAllNodesWithContentDescription("Bestätigen")[0].assertIsNotEnabled()
        rule.onAllNodesWithContentDescription("Hier nicht möglich: Belegt – rot markierte Fläche")[0].assertExists()
    }) { EvolutionIsland(creative=true, grown=true, nav=true, withLife=true) }
    @Test fun placementHarbourCoastValid() = shot("placement-05-hafen-am-rand-gueltig", gesture={
        liftPiece("Hafen")
        val shore = freeCells(IsleBuilding.HARBOUR, coast = true).first { it[1] < 12 }
        groundTap(shore, 5); settle()
        check(gridState() == "9 belegte Flächen rot markiert · Küstenfelder markiert") { gridState().toString() }
        rule.onAllNodesWithContentDescription("Bestätigen")[0].assertIsEnabled()
    }) { EvolutionIsland(creative=true, grown=true, nav=true, withLife=true) }
    @Test fun placementHarbourInlandTapSnapsToCoast() = shot("placement-06-hafen-innen-getippt-rand-snap", gesture={
        liftPiece("Hafen")
        // 7.9.10: a tap inland (7,11) puts the held harbour on the nearest coast cell (5,13); it never stands inland.
        val inland = listOf(7, 11); val shore = IslandGrid.coastCellNear(inland, 5)
        check(!IslandGrid.coast(inland, 5) && IslandLayout.valid(grownFixture(), IsleBuilding.HARBOUR, shore)) { "$shore" }
        groundTap(inland, 5); settle()
        rule.onAllNodesWithContentDescription("Bestätigen")[0].assertIsEnabled()
        rule.onAllNodesWithContentDescription("Hier nicht möglich: Hafen nur an der Küste").assertCountEquals(0)
    }) { EvolutionIsland(creative=true, grown=true, nav=true, withLife=true) }
    @Test fun placementDecorObstacles() = shot("placement-07-deko-verschieben-hindernisse-rot", gesture={
        liftPiece("Bank")
        groundTap(listOf(15, 11), 5); settle()
        rule.onAllNodesWithContentDescription("Bestätigen")[0].assertIsEnabled()
    }) { EvolutionIsland(creative=true, grown=true, nav=true, withLife=true) }
    @Test fun menuActionsStarter() = shot("menu-01-aktionen-start-haengematte", gesture={
        rule.onAllNodesWithContentDescription("Mein Haus").filter(androidx.compose.ui.test.hasClickAction())[0].performClick(); settle()
        rule.onAllNodesWithContentDescription("Start, noch keine Ausbaustufe")[0].assertExists()
        rule.onAllNodesWithContentDescription("Preis: 25 P")[0].assertExists()
    }) { EvolutionIsland(starters=true, nav=true, startingPoints=40) }
    @Test fun menuActionsStage1() = shot("menu-02-aktionen-post-stufe1", gesture={
        rule.onAllNodesWithContentDescription("Post").filter(androidx.compose.ui.test.hasClickAction())[0].performClick(); settle()
        rule.onAllNodesWithContentDescription("Stufe 1 von 3")[0].assertExists()
        for (l in listOf("Öffnen", "Ausbauen", "Abreißen")) rule.onAllNodesWithText(l)[0].assertExists()
    }) { EvolutionIsland(grown=true, level=1, nav=true, withLife=true, startingPoints=140) }
    @Test fun menuActionsMaster() = shot("menu-03-aktionen-hafen-meisterbau", gesture={
        rule.onAllNodesWithContentDescription("Hafen").filter(androidx.compose.ui.test.hasClickAction())[0].performClick(); settle()
        rule.onAllNodesWithText("Info")[0].assertExists()
    }) { EvolutionIsland(grown=true, level=3, nav=true, withLife=true) }
    @Test fun menuUpgradeSheet() = shot("menu-04-ausbau-post-stufe1-zu-2", captureDialogs=true, gesture={
        rule.onAllNodesWithContentDescription("Post").filter(androidx.compose.ui.test.hasClickAction())[0].performClick(); settle()
        rule.onAllNodesWithText("Ausbauen")[0].performClick(); settle(900)
        rule.onAllNodesWithText("Auf Stufe 2 ausbauen")[0].assertIsEnabled()
    }) { EvolutionIsland(grown=true, level=1, nav=true, startingPoints=140) }
    @Test fun menuUpgradeSheetMissingPoints() = shot("menu-05-ausbau-leuchtturm-punkte-fehlen", captureDialogs=true, gesture={
        rule.onAllNodesWithContentDescription("Freunde").filter(androidx.compose.ui.test.hasClickAction())[0].performClick(); settle()
        rule.onAllNodesWithText("Ausbauen")[0].performClick(); settle(900)
        rule.onAllNodesWithText("Auf Stufe 3 ausbauen")[0].assertIsNotEnabled()
    }) { EvolutionIsland(grown=true, level=2, nav=true, startingPoints=30) }
    @Test fun menuUpgradeSheetFull() = shot("menu-06-ausbau-bibliothek-voll", captureDialogs=true, gesture={
        rule.onAllNodesWithContentDescription("Wörterbuch").filter(androidx.compose.ui.test.hasClickAction())[0].performClick(); settle()
        rule.onAllNodesWithText("Info")[0].performClick(); settle(900)
        rule.onAllNodesWithText("Vollständig ausgebaut")[0].assertExists()
    }) { EvolutionIsland(grown=true, level=3, nav=true) }
    @Test fun menuUpgradeSheetStarter() = shot("menu-07-ausbau-schlafplatz-start-zu-1", captureDialogs=true, gesture={
        rule.onAllNodesWithContentDescription("Mein Haus").filter(androidx.compose.ui.test.hasClickAction())[0].performClick(); settle()
        rule.onAllNodesWithText("Ausbauen")[0].performClick(); settle(900)
        rule.onAllNodesWithText("Gebäude bauen")[0].assertIsEnabled()
    }) { EvolutionIsland(starters=true, nav=true, startingPoints=40) }
    @Test fun menuIslandMenuStages() = shot("menu-08-inselmenue-stufen", captureDialogs=true, gesture={
        rule.onAllNodesWithText("Menü")[0].performClick(); settle(900)
        rule.onAllNodesWithContentDescription("Stufe 2 von 3")[0].assertExists()
    }) { EvolutionIsland(grown=true, level=2, nav=true, eligible=true) }
    @Test fun menuPlaceHeaderStage() = shot("menu-09-ort-kopf-leuchtfeuer-stufe2", gesture={
        openPlace("Freunde"); rule.onAllNodesWithContentDescription("Ort: Signalstation")[0].assertExists()
    }) { EvolutionIsland(grown=true, level=2, nav=true) }
    @Test fun menuLandSheet() = shot("menu-10-land-vergroessern", captureDialogs=true, gesture={
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].performClick(); settle()
        rule.onAllNodesWithContentDescription("Reiter Land")[0].performClick(); settle(1400)
        rule.onAllNodesWithContentDescription("Land erweitern")[0].performClick(); settle(900)
        rule.onAllNodesWithText("Insel vergrößern")[0].assertExists()
    }) { EvolutionIsland(startingPoints=140, nav=true, starters=true) }

    // 7.9.11-raster: the grid shows only the held piece's real targets under the server's declared rule.
    private fun drawnTargets() = rule.onAllNodesWithContentDescription("Baufeld mit Kästchen")[0].fetchSemanticsNode().config.getOrNull(GridTargetsKey).orEmpty()
    @Test fun rasterHarbourOnlyFreeCoast() = shot("raster-01-hafen-nur-freie-kuestenfelder", gesture={
        liftPiece("Hafen")
        val c = grownFixture(); val want = IslandLayout.targets(c, IslandTarget.Building(IsleBuilding.HARBOUR))
        check(drawnTargets().toSet() == want.toSet() && want.all { IslandGrid.coast(it, 5) }) { "${drawnTargets()}" }
    }) { EvolutionIsland(creative=true, grown=true, nav=true, withLife=true) }
    @Test fun rasterPostOnlyFreeTargets() = shot("raster-02-post-nur-freie-zielfelder", gesture={
        liftPiece("Post")
        check(drawnTargets().toSet() == IslandLayout.targets(grownFixture(), IslandTarget.Building(IsleBuilding.POST)).toSet())
    }) { EvolutionIsland(creative=true, grown=true, nav=true, withLife=true) }
    @Test fun rasterCompactNeighbour() = shot("raster-03-kompakt-direkt-neben-dem-haus", gesture={
        liftPiece("Post")
        groundTap(listOf(11, 6), 5); settle()
        rule.onAllNodesWithContentDescription("Bestätigen")[0].assertIsEnabled()
    }) { EvolutionIsland(creative=true, grown=true, nav=true, withLife=true) }
    @Test fun rasterOldServerVersion2() = shot("raster-04-server-version2-alte-abstaende", gesture={
        liftPiece("Post")
        check(drawnTargets().toSet() == IslandLayout.targets(grownFixture().copy(gridVersion = PlacementRule.CIRCLES), IslandTarget.Building(IsleBuilding.POST)).toSet())
    }) { EvolutionIsland(creative=true, grown=true, nav=true, withLife=true, version=PlacementRule.CIRCLES) }
    @Test fun rasterUnknownVersion() = shot("raster-05-unbekannte-bauregel-update", gesture={
        liftPiece("Post")
        check(drawnTargets().isEmpty())
        rule.onAllNodesWithContentDescription("Bestätigen")[0].assertIsNotEnabled()
    }) { EvolutionIsland(creative=true, grown=true, nav=true, withLife=true, version=7) }
    @Test fun rasterLegacyDecorPlaceholder() = shot("raster-06-unbekannte-deko-platzhalter") {
        EvolutionIsland(creative=true, grown=true, nav=true, withLife=true, legacyDecor=true)
    }
    @Test fun rasterLegacyDecorBlocks() = shot("raster-07-unbekannte-deko-blockiert", gesture={
        liftPiece("Post")
        check(listOf(8, 11) !in drawnTargets())
    }) { EvolutionIsland(creative=true, grown=true, nav=true, withLife=true, legacyDecor=true) }
    @Test fun rasterSmallLand() = shot("raster-08-kleine-insel-land0-post", gesture={
        rule.onAllNodesWithContentDescription("Bauen – Bauleiste öffnen")[0].performClick(); settle()
        rule.onAllNodesWithContentDescription("Post platzieren")[0].performScrollTo(); settle()
        rule.onAllNodesWithContentDescription("Post platzieren")[0].performClick(); settle()
        check(drawnTargets().isNotEmpty())
    }) { EvolutionIsland(creative=true, nav=true) }
}
