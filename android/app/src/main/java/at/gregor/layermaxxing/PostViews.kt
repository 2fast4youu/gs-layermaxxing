package at.gregor.layermaxxing

/**
 * 7.9.14: what the post shows, on every stage the same rule (the tree, the mailbox drawer, the letter room, the counter):
 *  - four labelled views with real counts: Alle, Ungelesen, Gelesen, Gesendet;
 *  - the READING state is the own one and nothing else: an incoming letter is "Gelesen" when the server has my read time
 *    for it or it was opened here, otherwise "Ungelesen"; a sent letter is "Gesendet". Whether a letter is still
 *    travelling, sealed or waiting for a gate is its AVAILABILITY ([availability]) and never changes its view: a sealed
 *    letter to me stays visible under Ungelesen (without its content), nothing disappears;
 *  - a person filter that combines with every view and is kept when the view changes; people are every friend plus
 *    everyone a letter was exchanged with (an old friend's letters do not vanish);
 *  - a search over name and cover (title / cover note, what the envelope shows; never the sealed content).
 * Only direct letters (group letters live with their group).
 */
internal object PostViews {
    enum class View(val label: String) { ALL("Alle"), UNREAD("Ungelesen"), READ("Gelesen"), SENT("Gesendet") }
    data class Person(val id: Long, val name: String, val emoji: String, val color: String?)

    /** Above this many direct letters the post offers its search. */
    const val SEARCH_FROM = 8

    fun direct(letters: List<ApiClient.Message>) = letters.filter { it.groupId == null }

    /** The view a letter belongs to besides [View.ALL]. */
    fun viewOf(m: ApiClient.Message, openedHere: Boolean): View = when {
        !m.incoming -> View.SENT
        // (marked unread again by me: unread until it is opened again – its read time stays the server's history)
        m.readAt != null && !m.markedUnread || openedHere -> View.READ
        else -> View.UNREAD
    }

    fun inView(m: ApiClient.Message, view: View, opened: Map<Long, *>): Boolean =
        view == View.ALL || viewOf(m, opened[m.id] != null) == view

    fun matches(m: ApiClient.Message, query: String): Boolean {
        val q = query.trim()
        return q.isEmpty() || m.peerName.contains(q, ignoreCase = true) || m.title.contains(q, ignoreCase = true) ||
            m.coverNote.contains(q, ignoreCase = true)
    }

    /** The letters shown: view × person × search, newest first. */
    fun shown(letters: List<ApiClient.Message>, opened: Map<Long, *>, view: View, person: Long?, query: String): List<ApiClient.Message> =
        direct(letters).filter { inView(it, view, opened) && (person == null || it.peerId == person) && matches(it, query) }
            .sortedWith(compareByDescending<ApiClient.Message> { it.createdAt }.thenByDescending { it.id })

    /** The count on every view's label: with the chosen person and search (what that view would show). */
    fun counts(letters: List<ApiClient.Message>, opened: Map<Long, *>, person: Long?, query: String): Map<View, Int> =
        View.entries.associateWith { shown(letters, opened, it, person, query).size }

    /** Every friend, then everyone else a letter was exchanged with (sorted by name). */
    fun people(pals: List<ApiClient.UserSummary>, letters: List<ApiClient.Message>): List<Person> {
        val known = pals.map { Person(it.id, it.name, it.avatarEmoji, it.displayColor) }
        val ids = known.map { it.id }.toSet()
        val others = direct(letters).filter { it.peerId !in ids }.distinctBy { it.peerId }
            .map { Person(it.peerId, it.peerName, "✉", null) }.sortedBy { it.name.lowercase() }
        return known + others
    }

    /** The count beside every person: in the chosen view and search. */
    fun personCounts(letters: List<ApiClient.Message>, opened: Map<Long, *>, view: View, query: String): Map<Long, Int> =
        shown(letters, opened, view, null, query).groupingBy { it.peerId }.eachCount()

    /**
     * Where the letter is, independent of its reading state: travelling (timed / random), sealed until a gate opens
     * (both approve, manual, presence), arrived, delivered or – only when the server reports the read time – read by
     * the recipient. Never a made-up read receipt.
     */
    fun availability(m: ApiClient.Message, state: LetterState, now: Long = System.currentTimeMillis() / 1000): String = when (state) {
        LetterState.LOCKED_TIMED -> "unterwegs" + (m.releaseAt?.let { at -> if (at > now) " · noch ${formatRemaining(at - now)}" else "" } ?: "")
        LetterState.LOCKED_RANDOM -> "unterwegs · kommt zufällig an"
        LetterState.LOCKED_MANUAL -> "versiegelt · wird von Hand freigegeben"
        LetterState.LOCKED_MUTUAL_WAITING_ME -> "versiegelt · wartet auf deine Freigabe"
        LetterState.LOCKED_MUTUAL_WAITING_PEER -> "versiegelt · wartet auf ${m.peerName}"
        LetterState.LOCKED_PRESENCE -> "versiegelt · öffnet beim Treffen"
        LetterState.READY -> "angekommen"
        LetterState.OPENED -> "angekommen"
        LetterState.DELIVERED -> "zugestellt"
        LetterState.READ -> "von ${m.peerName} gelesen"
    }
}
