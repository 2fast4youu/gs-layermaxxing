package at.gregor.layermaxxing

import androidx.compose.ui.test.performClick

import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.filter

import androidx.compose.ui.test.doubleClick

import androidx.compose.ui.test.pinch

import androidx.compose.ui.test.swipe

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

    private fun shot(name: String, gesture: (() -> Unit)? = null, content: @Composable () -> Unit) {
        assumeTrue(out != null)
        rule.setContent(content)
        rule.mainClock.autoAdvance = false
        repeat(30) { rule.mainClock.advanceTimeByFrame() }
        if (gesture != null) { gesture(); repeat(40) { rule.mainClock.advanceTimeByFrame() } }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
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
                    ThreadHeader(friends[0], false, 2, 3, true, {}, {}, {}, {}, {}, {}, {}, {}, {})
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
                            LetterActivityRow(
                                ApiClient.Message(9, 2, "Gerfried", true, "Für Samstag", "", "ok", now - 3600, "timed", now - 10, null, null, true, false, null, true, true, null, null, null, emptyList(), null, null, null),
                                LetterState.READY, now, outgoing = false, pulsing = false, onOpenRoom = {},
                            )
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
        rule.onAllNodesWithContentDescription("Post").filter(androidx.compose.ui.test.hasClickAction())[0].performClick()
        repeat(30) { rule.mainClock.advanceTimeByFrame() }
    }) { Isles(start = "home") }
    @Test fun islandVisit() = shot("23-besuch") { Isles(start = "visit:2") }
    @Test fun buildPlace() = shot("25-bauen") {
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(Isle.SeaBottom).padding(top = 260.dp).then(androidx.compose.ui.Modifier.background(Isle.Card))) {
            VillageTheme(true) { BuildPlaceContent(null, {}, null, {}) }
        }
    }
    @Test fun placeSheet() = shot("26-ort") {
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(Isle.SeaBottom).padding(top = 300.dp).then(androidx.compose.ui.Modifier.background(Isle.Card))) {
            VillageTheme(true) { PlaceSheetContent(ApiClient.LifePlace("desk", "Coden am Berg"), true, "am Coden", { _, _ -> }, {}) }
        }
    }
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
    @Test fun islandsMapDebug() = shot("26-inseln-debug") {
        androidx.compose.runtime.CompositionLocalProvider(LocalHitboxDebug provides true) { Isles() }
    }
    @Test fun islandsEmpty() = shot("21-inseln-leer") { Isles(false) }
}
