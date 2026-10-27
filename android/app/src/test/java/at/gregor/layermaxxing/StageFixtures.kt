package at.gregor.layermaxxing

import androidx.compose.runtime.Composable

/**
 * Test fixtures for the 24 building × stage views (7.9.11): the same invented names and data the visual review
 * already uses (VillageVisualReview: Gerfried, Anna, Lena …, the glossary seed). No real chats or project data.
 */
internal object StageFixtures {
    val friends = listOf(
        ApiClient.UserSummary(2, "Gerfried", "friends", "🦊", "#2E7D32"),
        ApiClient.UserSummary(3, "Anna", "friends", "🐻", "#1565C0"),
        ApiClient.UserSummary(4, "Lena", "friends", "🦉", "#EF6C00"),
        ApiClient.UserSummary(5, "Ida", "friends", "🐺", "#6A1B9A"),
        ApiClient.UserSummary(6, "Luki", "friends", "🐰", "#C2185B"),
    )
    private val now = 1_790_000_000L
    private fun letter(id: Long, peer: Long, name: String, mode: String, incoming: Boolean) = ApiClient.Message(
        id, peer, name, incoming, "Brief $id", "", "none", now - 3 * 86_400, mode,
        if (mode == "timed") now + 2 * 86_400 else null,
        if (mode == "random") now - 86_400 else null, if (mode == "random") now + 5 * 86_400 else null,
        false, false, null, false, false,
        null, null, null, emptyList(), null, null, null,
    )
    val letters = listOf(letter(1, 3, "Anna", "timed", false), letter(4, 3, "Anna", "random", true), letter(5, 3, "Anna", "mutual", false),
        letter(2, 2, "Gerfried", "mutual", true), letter(6, 2, "Gerfried", "timed", false), letter(3, 4, "Lena", "random", false))
    private fun quest(id: Long, title: String, icon: String, pts: Int, target: String, tid: Long, type: String = "friend", done: Boolean = false) =
        ApiClient.Quest(id, title, "", icon, pts, 1, "Gregor", type, target, tid, 0, if (done) 1 else null, if (done) "Anna" else null, true)
    val quests = listOf(quest(1, "Gemeinsam auf den Gipfel", "hike", 30, "Anna", 3), quest(2, "Radtour am See", "bike", 20, "Lena", 4, done = true),
        quest(3, "Grillabend planen", "food", 10, "Bergfreunde", 1, type = "group"))
    val topics = listOf(ApiClient.Topic(1, "Urlaub im Sommer", "", "Gregor", "friend", "Anna", 3, 0, null, null, true))
    val groups = listOf(ApiClient.Group(1, "Bergfreunde", 1, friends.take(4)), ApiClient.Group(2, "WG", 1, friends.drop(2)))
    val incoming = listOf(ApiClient.IncomingRequest(9, 7, "Jonas", 0))
    val outgoing = listOf(ApiClient.OutgoingRequest(10, 8, "Sophie", 0))
    val glossary = listOf(
        ApiClient.GlossaryTerm(1, "Kalauer", "Gerfried", listOf(ApiClient.GlossaryExplanation(1, "Was ist gelb und kann schießen? Eine Banone.", "Gerfried", 0, 0, false)), false, "Witze"),
        ApiClient.GlossaryTerm(2, "Carpe diem", "Veit", listOf(ApiClient.GlossaryExplanation(2, "Nutze den Tag. (Horaz)", "Veit", 0, 0, false)), false, "Zitate"),
        ApiClient.GlossaryTerm(4, "Layermaxxing", "Gregor", listOf(ApiClient.GlossaryExplanation(4, "Ebenen stapeln, bis es passt.", "Gregor", 0, 0, false)), false),
    )

    /** Arrived letters as the island counts them (incoming, unlocked, unread): none in these fixtures. */
    val arrivals = letters.count { it.incoming && it.unlocked && it.readAt == null }

    /** The production view of [b] at [level], with every action wired (to nothing) and the fixture data. */
    @Composable
    fun Place(b: IsleBuilding, level: Int, postLetters: List<ApiClient.Message> = letters) = VillageTheme(true) {
        when (b) {
            IsleBuilding.POST -> PostOffice(friends, postLetters, emptyMap(), onOpen = {}, onLocked = {}, onWrite = {}, onClose = {}, level = level)
            IsleBuilding.HARBOUR -> HarbourBoard(quests, topics, onQuestDone = { _, _ -> }, onQuestDelete = {}, onNewQuest = {}, onAllTopics = {}, onClose = {}, level = level,
                arrivals = arrivals, onArrivals = {})
            IsleBuilding.LIGHTHOUSE -> LighthouseView(friends, friends.associate { it.id to 2 }, incoming, outgoing, onRespond = { _, _ -> }, onFriend = {}, onFind = {}, onClose = {}, level = level)
            IsleBuilding.LIBRARY -> LibraryBook(onClose = {}, glossary = { GlossaryScreen(seed = glossary, addTerm = { _, _, _ -> }) }, level = level)
            IsleBuilding.CAMPFIRE -> CommunityHall(groups, quests, onGroupQuest = {}, onManage = {}, onClose = {}, level = level, onPlans = {})
            IsleBuilding.HOUSE -> MyHouse("Gregor", "🦉", "#17A2A6", friends.size, 1, letters.size, 140, onSettings = {}, onEdit = {}, onClose = {}, level = level)
        }
    }

    /** The action every view keeps on every stage (the golden button). */
    /** stage-ui-redesign: a clean stage has no golden action – its painted things act (the letter tree's blank sheet). */
    fun action(b: IsleBuilding, level: Int): String? = if (StageScenes.of(b, level).clean) null else action(b)
    fun action(b: IsleBuilding): String? = when (b) {
        IsleBuilding.POST -> "Brief schreiben"
        IsleBuilding.HARBOUR -> "Quest anpinnen"
        IsleBuilding.LIGHTHOUSE -> "Freunde finden"
        IsleBuilding.LIBRARY -> null
        IsleBuilding.CAMPFIRE -> "Gruppen verwalten"
        IsleBuilding.HOUSE -> "Profil & Einstellungen"
    }
}
