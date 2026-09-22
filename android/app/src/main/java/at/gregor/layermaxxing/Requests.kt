package at.gregor.layermaxxing

/**
 * Delivery rules for the two-sided requests of this app.
 *
 * Friendship rules and Ebenen-Punkte are only ever agreed bilaterally on the
 * server. The client's job is to make a request that waits for *my* answer
 * impossible to miss instead of parking it on a sub page. Like [Conversations]
 * this file stays free of Android, Compose and org.json types so the rules are
 * unit-testable on the plain JVM.
 */

enum class RequestKind { SETTINGS, EP, FRIEND }

internal const val EP_PROPOSAL_POINTS = 1

internal fun formatEpPoints(points: Int): String =
    if (points == 1) "1 Ebenen-Punkt" else "$points Ebenen-Punkte"

data class PendingRequest(
    val kind: RequestKind,
    val id: Long,
    val friendId: Long,
    val friendName: String,
    val headline: String,
    val detail: String,
) {
    /** Stable across refreshes, so a dismissed dialog stays dismissed. */
    val key: String get() = "${kind.name}-$id"
}

/** Why a letter currently offers to become an EP proposal. */
enum class EpRole { I_OPENED_THEIR_LETTER, THEY_READ_MY_LETTER }

data class EpOpportunity(
    val friendId: Long,
    val friendName: String,
    val letterId: Long,
    val letterTitle: String,
    val role: EpRole,
) {
    val question: String
        get() = when (role) {
            EpRole.I_OPENED_THEIR_LETTER -> "Du hast diesen Brief geöffnet. Hat er zu Ebenen-Punkten geführt?"
            EpRole.THEY_READ_MY_LETTER -> "$friendName hat diesen Brief gelesen. Hat er zu Ebenen-Punkten geführt?"
        }
}

fun settingsSummary(letters: Boolean, chats: Boolean, ep: Boolean, delaySeconds: Long): String =
    "Briefe ${if (letters) "an" else "aus"} · Chats ${if (chats) "an" else "aus"} · " +
        "EP ${if (ep) "an" else "aus"} · Mindestdauer ${formatRemaining(delaySeconds)}"

object RequestInbox {
    /**
     * Collects everything that waits for my answer, most binding first.
     *
     * Friendship-rule proposals outrank EP because accepting one can switch EP
     * off entirely, and an EP proposal outranks a new friend request because it
     * belongs to a person I already agreed to.
     */
    fun collect(
        friendRequests: List<ApiClient.IncomingRequest>,
        settings: List<ApiClient.FriendshipSettings>,
        ep: ApiClient.EpOverview?,
    ): List<PendingRequest> {
        val result = mutableListOf<PendingRequest>()
        settings.mapNotNull { it.incomingProposal }.sortedBy { it.id }.forEach { proposal ->
            result += PendingRequest(
                kind = RequestKind.SETTINGS, id = proposal.id, friendId = proposal.friendId,
                friendName = proposal.friendName,
                headline = "${proposal.friendName} schlägt neue Freundschaftsregeln vor",
                detail = settingsSummary(
                    proposal.lettersEnabled, proposal.chatsEnabled,
                    proposal.epEnabled, proposal.minLetterDelaySeconds,
                ),
            )
        }
        // An EP proposal from someone who is no longer an active, unblocked friend
        // can no longer be answered meaningfully; the server rejects the response.
        val activeFriends = settings.map { it.friendId }.toSet()
        ep?.incoming.orEmpty().filter { it.proposerId in activeFriends }.sortedBy { it.id }.forEach { proposal ->
            result += PendingRequest(
                kind = RequestKind.EP, id = proposal.id, friendId = proposal.proposerId,
                friendName = proposal.proposerName,
                headline = "${proposal.proposerName} schlägt dir ${formatEpPoints(proposal.points)} vor",
                detail = listOfNotNull(proposal.title, proposal.description?.takeIf { it.isNotBlank() })
                    .joinToString(" — "),
            )
        }
        friendRequests.sortedBy { it.id }.forEach { request ->
            result += PendingRequest(
                kind = RequestKind.FRIEND, id = request.id, friendId = request.senderId,
                friendName = request.senderName,
                headline = "${request.senderName} möchte mit dir befreundet sein",
                detail = "Danach gelten die Standardregeln: Briefe an, Chats an, EP aus.",
            )
        }
        return result
    }

    /**
     * The open requests of exactly one friendship, for the conversation itself.
     *
     * A proposal from someone else never appears in a foreign thread, and the
     * binding order of [collect] is preserved.
     */
    fun forFriend(requests: List<PendingRequest>, friendId: Long): List<PendingRequest> =
        requests.filter { it.friendId == friendId }

    /**
     * The one request that is worth interrupting for.
     *
     * Nothing limits how many EP proposals a friend may open, so a single
     * "Später" snoozes the dialog for the rest of the session rather than
     * marching the user through an unbounded queue. The cards stay either way.
     */
    fun dialog(requests: List<PendingRequest>, dismissed: Set<String>, snoozed: Boolean): PendingRequest? =
        if (snoozed) null else requests.firstOrNull { it.key !in dismissed }
}

object EpOpportunities {
    /** Letters that already carry an EP proposal, pending or accepted. */
    fun linkedLetterIds(ep: ApiClient.EpOverview?): Set<Long> = buildSet {
        if (ep == null) return@buildSet
        (ep.incoming + ep.outgoing + ep.history).forEach { proposal ->
            proposal.letterId?.let(::add)
        }
    }

    /**
     * Offers an EP proposal to *both* sides out of the same event.
     *
     * `readAt` is set when a letter's content is fetched, so it reads as "I
     * opened their letter" on an incoming letter and as "they opened mine" on an
     * outgoing one. Note this is a client convention: the server also ships the
     * key for an already released, non-one-time letter inside the list response,
     * so `readAt` only stays truthful while every client opens letters through
     * `GET /api/messages/{id}/content` — which this app does.
     */
    fun forFriend(
        friendId: Long,
        friendName: String,
        letters: List<ApiClient.Message>,
        epEnabled: Boolean,
        linkedLetterIds: Set<Long>,
        dismissedLetterIds: Set<Long>,
    ): List<EpOpportunity> {
        if (!epEnabled) return emptyList()
        return letters.filter {
            it.peerId == friendId && it.readAt != null &&
                it.id !in linkedLetterIds && it.id !in dismissedLetterIds
        }.map { letter ->
            EpOpportunity(
                friendId = friendId, friendName = friendName, letterId = letter.id,
                letterTitle = letter.title.ifBlank { Conversations.SEALED_FALLBACK },
                role = if (letter.incoming) EpRole.I_OPENED_THEIR_LETTER else EpRole.THEY_READ_MY_LETTER,
            )
        }
    }
}
