package at.gregor.layermaxxing

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * Pure rules behind the chat extras: reactions, presence lines, chat → quest,
 * glossary underlines and the painted sticker set. Kept free of Compose state so
 * every rule is unit-tested.
 */
internal object ChatExtras {
    /** The quick reactions of the long-press bar, in order. */
    val QUICK_REACTIONS = listOf("❤️", "😂", "👍", "😮", "😢", "🌴")

    data class ReactionChip(val emoji: String, val count: Int, val mine: Boolean)

    /** Groups reactions by emoji; most used first, mine marked. */
    fun reactionChips(reactions: List<ApiClient.Reaction>, ownUserId: Long?): List<ReactionChip> =
        reactions.groupBy { it.emoji }
            .map { (emoji, list) -> ReactionChip(emoji, list.size, list.any { it.userId == ownUserId }) }
            .sortedWith(compareByDescending<ReactionChip> { it.count }.thenBy { QUICK_REACTIONS.indexOf(it.emoji).let { i -> if (i < 0) 99 else i } })

    /** My current reaction on a line, if any. */
    fun myReaction(reactions: List<ApiClient.Reaction>, ownUserId: Long?): String? =
        reactions.firstOrNull { it.userId == ownUserId }?.emoji

    /** Tapping the emoji I already chose removes it; anything else replaces it. */
    fun toggledReaction(current: String?, tapped: String): String = if (current == tapped) "" else tapped

    /** The subtitle under a friend's name in the thread head. */
    fun presenceLine(name: String, presence: ApiClient.ChatPresence?, now: Long): String? {
        presence ?: return null
        if (presence.typing) return "schreibt gerade …"
        if (presence.online) return "ist gerade auf der Insel"
        val seen = presence.lastSeenAt ?: return null
        val ago = (now - seen).coerceAtLeast(0)
        return when {
            ago < 3600 -> "zuletzt vor ${(ago / 60).coerceAtLeast(1)} Min. da"
            ago < 86400 -> "zuletzt vor ${ago / 3600} Std. da"
            ago < 7 * 86400 -> "zuletzt vor ${ago / 86400} ${if (ago / 86400 == 1L) "Tag" else "Tagen"} da"
            else -> "länger nicht da"
        }
    }

    /** Only send a typing ping every few seconds, never per keystroke. */
    const val TYPING_PING_MS = 4_000L
    fun shouldPingTyping(lastPingMs: Long, nowMs: Long, text: String): Boolean =
        text.isNotBlank() && nowMs - lastPingMs >= TYPING_PING_MS

    /** Edits are allowed on own text lines for 24 hours, like the server. */
    fun canEdit(message: ApiClient.ChatMessage, ownUserId: Long?, now: Long): Boolean =
        message.senderId == ownUserId && !message.deleted && message.kind == "text" && now - message.createdAt <= 24 * 3600

    fun canDelete(message: ApiClient.ChatMessage, ownUserId: Long?): Boolean =
        message.senderId == ownUserId && !message.deleted

    // ---------------------------------------------------------------- chat → quest

    data class QuestDraft(val title: String, val details: String, val icon: String, val points: Int)

    private val questIcons = listOf(
        "grill" to listOf("grill", "bbq", "würstel", "steak"),
        "hike" to listOf("wander", "berg", "hütte", "brennerhaus", "gipfel", "tour"),
        "bike" to listOf("rad", "bike", "fahrrad", "mtb"),
        "food" to listOf("essen", "pizza", "kochen", "frühstück", "kaffee", "brunch", "mittag", "abendessen"),
        "game" to listOf("spiel", "zocken", "kicker", "brettspiel", "karten"),
        "travel" to listOf("urlaub", "reise", "trip", "fahren nach", "ausflug", "zug"),
        "sport" to listOf("fußball", "laufen", "training", "gym", "schwimmen", "klettern", "ski"),
        "music" to listOf("konzert", "musik", "festival", "band", "gitarre"),
        "help" to listOf("hilf", "umzug", "reparier", "helfen"),
    )

    /** Turns a chat line into a quest suggestion the user can still edit. */
    fun questDraft(text: String): QuestDraft {
        val body = ChatTools.parseReply(text).second.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        val lower = body.lowercase()
        val icon = questIcons.firstOrNull { (_, words) -> words.any { it in lower } }?.first ?: "star"
        val cleaned = body.trimEnd('?', '!', '.', ' ').ifBlank { "Gemeinsame Quest" }
        val title = if (cleaned.length <= 60) cleaned else cleaned.take(57).trimEnd() + "…"
        return QuestDraft(title, if (body.length > title.length) body.take(500) else "", icon, 20)
    }

    // ---------------------------------------------------------------- glossary

    data class TermHit(val start: Int, val end: Int, val termId: Long)

    /**
     * Finds glossary words in a line: whole words, case-insensitive, longest term
     * first, never overlapping.
     */
    fun glossaryHits(text: String, terms: List<ApiClient.GlossaryTerm>): List<TermHit> {
        if (text.isBlank() || terms.isEmpty()) return emptyList()
        val hits = mutableListOf<TermHit>()
        val taken = BooleanArray(text.length)
        terms.filter { it.term.trim().length >= 2 }.sortedByDescending { it.term.length }.forEach { term ->
            val needle = Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(term.term.trim()) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
            needle.findAll(text).forEach { m ->
                val r = m.range
                if ((r.first..r.last).none { taken[it] }) {
                    (r.first..r.last).forEach { taken[it] = true }
                    hits += TermHit(r.first, r.last + 1, term.id)
                }
            }
        }
        return hits.sortedBy { it.start }
    }

    fun underlined(text: String, hits: List<TermHit>, color: Color): AnnotatedString = buildAnnotatedString {
        var at = 0
        hits.forEach { h ->
            append(text.substring(at, h.start))
            withStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = color)) { append(text.substring(h.start, h.end)) }
            at = h.end
        }
        append(text.substring(at))
    }

    // ---------------------------------------------------------------- stickers

    /** Painted harbour stickers. The line carries only the key, the app paints it. */
    val STICKERS = listOf("sun", "heart", "wave", "palm", "boat", "star", "shell", "lighthouse")
    const val STICKER_PREFIX = "sticker:"

    fun stickerText(key: String): String = STICKER_PREFIX + key
    fun stickerKey(message: ApiClient.ChatMessage): String? =
        message.text.takeIf { message.kind == "sticker" && it.startsWith(STICKER_PREFIX) }
            ?.removePrefix(STICKER_PREFIX)?.takeIf { it in STICKERS }

    fun stickerLabel(key: String): String = when (key) {
        "sun" -> "Sonne"; "heart" -> "Herz"; "wave" -> "Welle"; "palm" -> "Palme"
        "boat" -> "Boot"; "star" -> "Stern"; "shell" -> "Muschel"; "lighthouse" -> "Leuchtturm"
        else -> "Sticker"
    }

    /** What a line says in previews, quotes and the action sheet. */
    fun previewText(message: ApiClient.ChatMessage): String = when {
        message.deleted -> "Nachricht gelöscht"
        message.kind == "image" -> "Foto" + message.text.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        message.kind == "voice" -> "Flaschenpost" + message.text.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        message.kind == "sticker" -> "Sticker: " + (stickerKey(message)?.let(::stickerLabel) ?: "Sticker")
        else -> ChatTools.parseReply(message.text).second
    }

    /** Voice notes are short bottle posts. */
    const val VOICE_MAX_MS = 60_000L
    fun voiceLabel(durationMs: Long): String {
        val s = (durationMs / 1000).coerceAtLeast(1)
        return "%d:%02d".format(s / 60, s % 60)
    }

    /** Longest edge of a sent photo; keeps uploads small and fast. */
    const val PHOTO_EDGE = 1280
    fun scaledSize(w: Int, h: Int, edge: Int = PHOTO_EDGE): Pair<Int, Int> {
        if (w <= 0 || h <= 0) return 0 to 0
        val m = maxOf(w, h)
        if (m <= edge) return w to h
        return (w.toLong() * edge / m).toInt().coerceAtLeast(1) to (h.toLong() * edge / m).toInt().coerceAtLeast(1)
    }

    /** The little growth note shown once a shared chat day counts. */
    fun growthNote(presence: ApiClient.ChatPresence): String =
        "Chat-Tag gezählt · ${presence.chatDays} gemeinsame Tage lassen eure Insel wachsen"
}
