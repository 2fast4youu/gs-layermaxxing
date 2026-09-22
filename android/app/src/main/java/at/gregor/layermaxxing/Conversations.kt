package at.gregor.layermaxxing

/**
 * Pure conversation model shared by the chat list and the per-person thread.
 *
 * Everything in this file is deliberately free of Android, Compose and org.json
 * types so it stays testable on the plain JVM: `org.json` is only available as a
 * throwing stub in unit tests, and Compose state cannot be exercised there at all.
 * The screens map these values to UI; the rules live here.
 */

/** How a sealed letter presents itself to the person currently looking at it. */
enum class LetterState {
    LOCKED_TIMED,
    LOCKED_RANDOM,
    LOCKED_MANUAL,
    LOCKED_MUTUAL_WAITING_ME,
    LOCKED_MUTUAL_WAITING_PEER,
    LOCKED_PRESENCE,
    READY,
    OPENED,
    DELIVERED,
    READ;

    val locked: Boolean get() = name.startsWith("LOCKED")
}

/** One item in a person thread. Letters and instant messages share the timeline. */
sealed class ThreadEntry {
    abstract val id: Long
    abstract val timestamp: Long
    abstract val outgoing: Boolean

    /** An ordinary, immediately released message. */
    data class Chat(val message: ApiClient.ChatMessage, override val outgoing: Boolean) : ThreadEntry() {
        override val id: Long get() = message.id
        override val timestamp: Long get() = message.createdAt
    }

    /** A sealed letter, shown as a distinct card inside the same timeline. */
    data class Letter(val message: ApiClient.Message, val state: LetterState) : ThreadEntry() {
        override val id: Long get() = message.id
        override val timestamp: Long get() = message.createdAt
        override val outgoing: Boolean get() = !message.incoming
    }

    val key: String get() = if (this is Chat) "chat-$id" else "letter-$id"
}

/** The single line under a name in the conversation list. */
data class ConversationPreview(val text: String, val sealed: Boolean, val fromMe: Boolean)

/** Metadata-only letter group. No ciphertext, key or opened body is copied into a preview. */
data class LetterGroup(val bucket: LetterBucket, val messages: List<ApiClient.Message>)

/**
 * What the post-office sign of one friendship announces: how many letters hang
 * there at all, and how many of them ask for a look right now.
 */
data class PostStatus(val total: Int, val ready: Int, val awaitingMe: Int) {
    /** Released-but-unopened plus waiting-for-my-approval: the amber number. */
    val urgent: Int get() = ready + awaitingMe
}

data class Conversation(
    val friendId: Long,
    val friendName: String,
    val avatarEmoji: String,
    val displayColor: String,
    /** 0 means the two have never exchanged anything yet. */
    val lastActivityAt: Long,
    val preview: ConversationPreview,
    val unreadChats: Int,
    /** Incoming letters that are released but not opened yet. */
    val readyLetters: Int,
    /** Incoming letters still behind their release gate. */
    val lockedLetters: Int,
    /** Letters whose mutual release is waiting for exactly my approval. */
    val awaitingMe: Int,
    val chatsEnabled: Boolean,
    val lettersEnabled: Boolean,
    val epEnabled: Boolean,
) {
    val hasNews: Boolean get() = unreadChats > 0 || readyLetters > 0 || awaitingMe > 0
    val silenced: Boolean get() = !chatsEnabled && !lettersEnabled
}

/** Coarse German duration label, used for countdowns and for agreed minimum delays. */
fun formatRemaining(seconds: Long): String {
    val d = seconds / 86400
    val h = seconds % 86400 / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return when {
        d > 0 -> "$d T $h Std"
        h > 0 -> "$h Std $m Min"
        m > 0 -> "$m Min $s Sek"
        else -> "$s Sek"
    }
}

/**
 * Whether "write a letter" may be offered, and what to say when it may not.
 *
 * This is the *only* rule in the app that gates the letter composer, and it
 * depends on nothing but the bilaterally agreed friendship rules. No map, no
 * castle, no building level and no EP balance is an input here — which is what
 * keeps the messenger complete while the castle experiment is switched off.
 */
data class LetterAction(val enabled: Boolean, val label: String, val reason: String?)

object LetterAccess {
    const val LABEL = "✦ Brief"
    const val LABEL_LONG = "✦ Brief schreiben"

    fun forSettings(settings: ApiClient.FriendshipSettings?): LetterAction = when {
        settings == null -> LetterAction(
            enabled = false, label = LABEL,
            reason = "Diese Freundschaft ist nicht mehr aktiv. Briefe sind erst nach einer bestätigten Freundschaft möglich.",
        )
        !settings.lettersEnabled -> LetterAction(
            enabled = false, label = LABEL,
            reason = "Briefe sind in dieser Freundschaft aus. Ihr könnt das in den Freundschaftsregeln gemeinsam ändern.",
        )
        else -> LetterAction(enabled = true, label = LABEL, reason = null)
    }
}

/**
 * One jump badge of the seal band above the timeline.
 *
 * Only buckets that actually hold letters ever become a badge, so the band never
 * prints a zero and disappears completely while no letter is in play.
 */
data class SealBadge(val bucket: LetterBucket, val count: Int, val oldestLetterId: Long)

/** The timeline as rendered: entries plus the day marks between them. */
sealed class TimelineItem {
    data class DayMark(val label: String, val dayStart: Long) : TimelineItem()
    data class Entry(val entry: ThreadEntry) : TimelineItem()

    val key: String
        get() = when (this) {
            is DayMark -> "day-$dayStart"
            is Entry -> entry.key
        }
}

/**
 * What the one composer row offers right now.
 *
 * Both genres are gated by the bilateral friendship rules alone; a disabled genre
 * never leaves a dead button behind, only an honest reason and the way to the rules.
 */
data class ComposerPlan(
    val sealVisible: Boolean,
    val inputEnabled: Boolean,
    val placeholder: String,
    val topicBadge: Int,
    val blockedReason: String?,
)

/** The one composer rule, kept out of Compose so it can be pinned by a JVM test. */
fun composerPlan(action: LetterAction, chatsEnabled: Boolean, openTopics: Int): ComposerPlan = ComposerPlan(
    sealVisible = action.enabled,
    inputEnabled = chatsEnabled,
    placeholder = if (chatsEnabled) "Nachricht" else "Chat ist in dieser Freundschaft aus",
    topicBadge = openTopics.coerceAtLeast(0),
    blockedReason = if (!chatsEnabled && !action.enabled) action.reason ?: Conversations.SILENCED else null,
)

object Conversations {
    const val NO_MESSAGES = "Noch keine Nachrichten"
    const val EMPTY_THREAD = "Noch nichts geschrieben."
    const val SILENCED = "Briefe und Chats sind in dieser Freundschaft aus."
    const val SEALED_FALLBACK = "Versiegelter Brief"
    const val CHAT_FALLBACK = "Neue Nachricht"

    /** Public envelope metadata only; ciphertext and decrypted body are never preview inputs. */
    fun letterPreviewText(message: ApiClient.Message): String =
        message.title.ifBlank { message.coverNote }.ifBlank { SEALED_FALLBACK }

    fun topicsWith(friendId: Long, friendName: String, topics: List<ApiClient.Topic>): List<ApiClient.Topic> =
        topics.filter {
            it.targetType == "friend" &&
                (it.targetId == friendId || it.targetId == null && it.targetName == friendName)
        }

    fun letterState(message: ApiClient.Message, openedLocally: Boolean): LetterState = when {
        message.unlocked && (openedLocally || message.incoming && message.readAt != null) -> LetterState.OPENED
        message.unlocked && message.incoming -> LetterState.READY
        message.unlocked -> if (message.readAt != null) LetterState.READ else LetterState.DELIVERED
        message.mode == "manual" -> LetterState.LOCKED_MANUAL
        message.mode == "mutual" ->
            if (if (message.incoming) message.recipientApproved else message.senderApproved)
                LetterState.LOCKED_MUTUAL_WAITING_PEER else LetterState.LOCKED_MUTUAL_WAITING_ME
        message.mode == "presence" -> LetterState.LOCKED_PRESENCE
        message.mode == "random" -> LetterState.LOCKED_RANDOM
        else -> LetterState.LOCKED_TIMED
    }

    /** Every letter exchanged with one person, both directions, newest last. */
    fun lettersWith(
        friendId: Long,
        incoming: List<ApiClient.Message>,
        outbox: List<ApiClient.Message>,
    ): List<ApiClient.Message> = (incoming + outbox)
        .filter { it.peerId == friendId }
        .sortedWith(compareBy({ it.createdAt }, { it.id }))

    fun letterBucket(message: ApiClient.Message, openedLocally: Boolean): LetterBucket {
        val state = letterState(message, openedLocally)
        return when {
            state == LetterState.READY -> LetterBucket.READY
            state == LetterState.OPENED || state == LetterState.DELIVERED || state == LetterState.READ ->
                LetterBucket.OPENED_HISTORY
            state == LetterState.LOCKED_MUTUAL_WAITING_ME || (!message.incoming && state == LetterState.LOCKED_MANUAL) ->
                LetterBucket.WAITING_ON_YOU
            else -> LetterBucket.IN_TRANSIT
        }
    }

    fun groupedLetters(
        letters: List<ApiClient.Message>,
        openedLetterIds: Set<Long>,
    ): List<LetterGroup> = LetterBucket.entries.mapNotNull { bucket ->
        letters.filter { letterBucket(it, letterIdOpened(it.id, openedLetterIds)) == bucket }
            .takeIf { it.isNotEmpty() }
            ?.let { LetterGroup(bucket, it.sortedWith(compareByDescending<ApiClient.Message> { message -> message.createdAt }.thenByDescending { message -> message.id })) }
    }

    private fun letterIdOpened(id: Long, openedLetterIds: Set<Long>): Boolean = id in openedLetterIds

    /**
     * The numbers on the post-office sign, derived from the same buckets the
     * board hangs its envelopes by — sign and board can never disagree.
     */
    fun postStatus(letters: List<ApiClient.Message>, openedLetterIds: Set<Long>): PostStatus {
        var ready = 0
        var awaiting = 0
        letters.forEach { message ->
            when (letterBucket(message, message.id in openedLetterIds)) {
                LetterBucket.READY -> ready += 1
                LetterBucket.WAITING_ON_YOU -> awaiting += 1
                else -> Unit
            }
        }
        return PostStatus(letters.size, ready, awaiting)
    }

    /**
     * Merges instant messages and letters into one timeline.
     *
     * Letters and instant messages come from the same server table and therefore
     * share one id sequence, so (createdAt, id) is a total order without collisions.
     */
    fun threadEntries(
        chatMessages: List<ApiClient.ChatMessage>,
        letters: List<ApiClient.Message>,
        ownUserId: Long?,
        openedLetterIds: Set<Long>,
    ): List<ThreadEntry> {
        val entries = ArrayList<ThreadEntry>(chatMessages.size + letters.size)
        chatMessages.forEach { entries.add(ThreadEntry.Chat(it, it.senderId == ownUserId)) }
        letters.forEach { entries.add(ThreadEntry.Letter(it, letterState(it, it.id in openedLetterIds))) }
        return entries.sortedWith(compareBy({ it.timestamp }, { it.id }))
    }

    /**
     * The jump badges above the timeline, in a fixed order.
     *
     * Waiting on me first, then a released letter, then what is still on the road.
     * The opened history is never a badge: it is not something to act on.
     */
    fun sealBand(groups: List<LetterGroup>): List<SealBadge> {
        val order = listOf(LetterBucket.WAITING_ON_YOU, LetterBucket.READY, LetterBucket.IN_TRANSIT)
        return order.mapNotNull { bucket ->
            val group = groups.firstOrNull { it.bucket == bucket } ?: return@mapNotNull null
            // Groups arrive newest first, so the oldest letter of a bucket is the last one.
            val oldest = group.messages.lastOrNull() ?: return@mapNotNull null
            SealBadge(bucket, group.messages.size, oldest.id)
        }
    }

    /** Where a letter sits in the timeline, for the jump from a seal badge. */
    fun timelineIndexOf(items: List<TimelineItem>, letterId: Long): Int? = items
        .indexOfFirst { it is TimelineItem.Entry && it.entry is ThreadEntry.Letter && it.entry.id == letterId }
        .takeIf { it >= 0 }

    /**
     * Inserts exactly one day mark in front of each day of the timeline.
     *
     * The order of the entries is preserved untouched; the marks are derived, so
     * the timeline stays one totally ordered flow.
     */
    fun withDayMarks(
        entries: List<ThreadEntry>,
        nowEpochSeconds: Long,
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ): List<TimelineItem> {
        val today = java.time.Instant.ofEpochSecond(nowEpochSeconds).atZone(zone).toLocalDate()
        val result = ArrayList<TimelineItem>(entries.size + 4)
        var lastDay: java.time.LocalDate? = null
        entries.forEach { entry ->
            val day = java.time.Instant.ofEpochSecond(entry.timestamp).atZone(zone).toLocalDate()
            if (day != lastDay) {
                val label = when (day) {
                    today -> "Heute"
                    today.minusDays(1) -> "Gestern"
                    else -> day.format(
                        java.time.format.DateTimeFormatter.ofPattern("EEE, dd.MM.", java.util.Locale.GERMAN),
                    )
                }
                result += TimelineItem.DayMark(label, day.atStartOfDay(zone).toEpochSecond())
                lastDay = day
            }
            result += TimelineItem.Entry(entry)
        }
        return result
    }

    /**
     * Builds the conversation list.
     *
     * The friend list is the base, not `/api/chats`: that endpoint only knows
     * people who already sent an instant message, so a friend reached only by
     * letter — or not yet at all — would be missing from a messenger's home screen.
     */
    fun overview(
        friends: List<ApiClient.UserSummary>,
        settings: List<ApiClient.FriendshipSettings>,
        threads: List<ApiClient.ChatThread>,
        incoming: List<ApiClient.Message>,
        outbox: List<ApiClient.Message>,
        ownUserId: Long?,
    ): List<Conversation> = friends.map { friend ->
        val rules = settings.firstOrNull { it.friendId == friend.id }
        val thread = threads.firstOrNull { it.friendId == friend.id }
        val letters = lettersWith(friend.id, incoming, outbox)
        val lastLetter = letters.lastOrNull()
        val chatAt = thread?.lastMessageAt ?: 0L
        val letterAt = lastLetter?.createdAt ?: 0L
        Conversation(
            friendId = friend.id,
            friendName = friend.name,
            avatarEmoji = friend.avatarEmoji,
            displayColor = friend.displayColor,
            lastActivityAt = maxOf(chatAt, letterAt),
            preview = preview(thread, lastLetter, ownUserId),
            unreadChats = thread?.unreadCount ?: 0,
            // Server truth, not local state: a released, unread letter is the one
            // thing the list must surface even right after a fresh login.
            readyLetters = letters.count { it.incoming && it.unlocked && it.readAt == null },
            lockedLetters = letters.count { it.incoming && !it.unlocked },
            awaitingMe = letters.count {
                letterState(it, openedLocally = false) == LetterState.LOCKED_MUTUAL_WAITING_ME
            },
            // An absent settings row is not an enabled feature.
            chatsEnabled = rules?.chatsEnabled == true,
            lettersEnabled = rules?.lettersEnabled == true,
            epEnabled = rules?.epEnabled == true,
        )
    }.sortedWith(
        compareByDescending<Conversation> { it.lastActivityAt }.thenBy { it.friendName.lowercase() },
    )

    private fun preview(
        thread: ApiClient.ChatThread?,
        lastLetter: ApiClient.Message?,
        ownUserId: Long?,
    ): ConversationPreview {
        val chatAt = thread?.lastMessageAt ?: 0L
        val letterAt = lastLetter?.createdAt ?: 0L
        return when {
            thread != null && chatAt >= letterAt -> ConversationPreview(
                // A null text means the stored message could not be decrypted here.
                // Say so neutrally instead of inventing a preview line.
                text = thread.lastText ?: CHAT_FALLBACK,
                sealed = false,
                fromMe = ownUserId != null && thread.lastSenderId == ownUserId,
            )
            lastLetter != null -> ConversationPreview(
                // Only the openly readable parts of a letter may appear here.
                // The sealed body stays sealed, also in a preview.
                text = letterPreviewText(lastLetter),
                sealed = true,
                fromMe = !lastLetter.incoming,
            )
            else -> ConversationPreview(NO_MESSAGES, sealed = false, fromMe = false)
        }
    }
}
