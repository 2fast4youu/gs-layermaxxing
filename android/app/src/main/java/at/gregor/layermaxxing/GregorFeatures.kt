package at.gregor.layermaxxing

/** Explicit scopes prevent a group's numeric id from accidentally selecting a friend. */
data class TopicScope(val type: String, val id: Long?) {
    fun filter(topics: List<ApiClient.Topic>) = topics.filter { it.targetType == type && it.targetId == id }
    companion object {
        fun friend(id: Long) = TopicScope("friend", id)
        fun group(id: Long) = TopicScope("group", id)
        fun personal() = TopicScope("personal", null)
    }
}

data class GlossaryEntry(val term: String, val meaning: String, val action: String)
/** One line of the merged dictionary: either a built-in app term or a shared community word. */
data class DictionaryRow(
    val key: String, val term: String, val builtIn: GlossaryEntry?, val shared: ApiClient.GlossaryTerm?,
)

object Dictionary {
    /** Built-in terms first, then community words, both alphabetical; search covers terms and every explanation. */
    fun rows(shared: List<ApiClient.GlossaryTerm>, query: String): List<DictionaryRow> {
        val q = query.trim()
        fun hit(vararg parts: String) = q.isEmpty() || parts.any { it.contains(q, ignoreCase = true) }
        val builtIn = AppGlossary.entries.filter { hit(it.term, it.meaning, it.action) }
            .sortedBy { it.term.lowercase() }
            .map { DictionaryRow("app-${it.term}", it.term, it, null) }
        val community = shared.filter { t -> hit(t.term, *t.explanations.map { it.text }.toTypedArray()) }
            .sortedBy { it.term.lowercase() }
            .map { DictionaryRow("word-${it.id}", it.term, null, it) }
        return builtIn + community
    }

    fun canSubmit(term: String, explanation: String) =
        term.isNotBlank() && explanation.isNotBlank() && term.trim().length <= 80 && explanation.trim().length <= 2000
}

object AppGlossary {
    val entries = listOf(
        GlossaryEntry("Insel", "Die grafische Ansicht einer Freundschaft. Jeder Freund hat seine eigene Insel.", "Wegweiser: Freund wechseln. Haus: eigener Hof. Gegenüber: Freund besuchen."),
        GlossaryEntry("Briefe", "Hier findest du eure versiegelten, freigegebenen und gelesenen Briefe.", "Im Chat auf den Umschlag oder auf deiner Insel auf die Post tippen."),
        GlossaryEntry("Themen", "Was ihr beim nächsten Gespräch besprechen wollt: privat, mit einem Freund oder in einer Gruppe.", "Anlegen, Notiz ergänzen, besprochen markieren und bei Bedarf wieder öffnen."),
        GlossaryEntry("Punkte", "Ein Dankeschön, das die andere Person erst annehmen muss. Nur angenommene Vorschläge zählen.", "In den Freundschaftsregeln gemeinsam aktivieren. Ein Vorschlag gibt einen Punkt."),
        GlossaryEntry("Schatzkammer / Ausbau", "Angenommene Punkte ermöglichen kosmetischen Ausbau auf diesem Gerät. Dein Chat wird dadurch nicht freigeschaltet oder gesperrt.", "Hof öffnen, Schatzkammer oder Baustelle wählen."),
        GlossaryEntry("Freundesfunke", "Eine kurze positive Nachricht ohne sichtbaren Absender. Der Server kennt den Absender zur Missbrauchsbehandlung.", "In Chats auf ✨ tippen. Empfänger können stummschalten oder melden."),
        GlossaryEntry("Versiegelter Brief", "Inhalt wird nach einer Freigaberegel angezeigt. Umschlagtext ist vorher sichtbar. Der Server verwahrt den Freigabeschlüssel.", "Dauer, Zeitpunkt, manuell, Zustimmung, Online-Präsenz oder Zufallsfenster wählen."),
        GlossaryEntry("Prüfdatei / Nachweis", "Signatur und Commitment helfen, Änderungen am Brief zu erkennen. Sie beweisen nicht die Identität einer Person.", "Im Briefe den Nachweis öffnen und eine Prüfdatei exportieren."),
        GlossaryEntry("Freundschaftsregeln", "Chat, Briefe, Punkte und Mindestverzögerung werden beidseitig vereinbart.", "Im Gesprächsmenü Regeln vorschlagen; die andere Person muss zustimmen."),
        GlossaryEntry("Gruppe", "Mehrere Freunde für gemeinsame Themen und Gruppenbriefe. Ein separater Sofort-Gruppenchat ist noch nicht vorhanden.", "Gruppen öffnen, Freunde auswählen, dann Themen sammeln oder einen Gruppenbrief schreiben."),
        GlossaryEntry("Kreativmodus / Testserver", "Nur für berechtigte Testkonten: freier Ausbau und Vorspulen zeitgesteuerter Testbriefe. Keine echten Produktionsdaten.", "Unter Mehr aktivieren. Ein Testkonto gehört nur zum ausgewählten Server."),
    )
    /** Old names still find the new entries ("EP" → Punkte, "Briefraum" → Briefe, "Dorf" → Insel). */
    private val aliases = mapOf(
        "Punkte" to "EP Ebenen-Punkte", "Briefe" to "Briefraum Postarchiv Poststelle",
        "Themen" to "Stichworte Notizen", "Insel" to "Tal Dorf Lehen",
    )
    fun search(query: String): List<GlossaryEntry> = query.trim().let { q ->
        entries.filter { q.isEmpty() || (it.term + " " + it.meaning + " " + it.action + " " + aliases[it.term].orEmpty()).contains(q, ignoreCase = true) }
    }
}
object ChatTools {
    fun filterEntries(entries: List<ThreadEntry>, query: String) = query.trim().let { q ->
        entries.filter { q.isEmpty() || when(it) {
            is ThreadEntry.Chat -> it.message.text.contains(q, ignoreCase = true)
            is ThreadEntry.Letter -> (it.message.title + " " + it.message.coverNote).contains(q, ignoreCase = true)
        } }
    }
    fun replyText(quote: String?, body: String) = if (quote == null) body else
        "> " + quote.replace('\n', ' ').take(200) + "\n\n" + body

    /** Splits a message written by [replyText] back into quote and body, so the bubble can draw the quote as a block. */
    fun parseReply(text: String): Pair<String?, String> {
        if (!text.startsWith("> ")) return null to text
        val split = text.indexOf("\n\n")
        if (split < 0) return null to text
        val body = text.substring(split + 2)
        return if (body.isBlank()) null to text else text.substring(2, split) to body
    }

    /** WhatsApp-style list time: clock today, "Gestern", weekday within a week, date otherwise. */
    fun listTime(epoch: Long, now: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
        if (epoch <= 0) return ""
        val then = java.time.Instant.ofEpochSecond(epoch).atZone(zone)
        val today = java.time.Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
        val days = java.time.temporal.ChronoUnit.DAYS.between(then.toLocalDate(), today)
        return when {
            days <= 0L -> java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(then)
            days == 1L -> "Gestern"
            days < 7L -> listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")[then.dayOfWeek.value - 1]
            else -> java.time.format.DateTimeFormatter.ofPattern("dd.MM.yy").format(then)
        }
    }

    /** Chat-list filters; navigation shortcuts (groups, topics …) are chips too but not filters. */
    /**
     * True when a chat line is just one to three emoji (no letters or digits):
     * such a line is drawn as a sticker instead of a text bubble.
     */
    fun isEmojiOnly(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || t.length > 16) return false
        if (t.any { it.isLetterOrDigit() || it in ".,!?;:-_()[]{}<>/\\'\"@#%&*+=~^`|" }) return false
        var count = 0
        var i = 0
        while (i < t.length) {
            val cp = t.codePointAt(i)
            val isJoinerOrMod = cp == 0x200D || cp in 0xFE00..0xFE0F || cp in 0x1F3FB..0x1F3FF
            if (!isJoinerOrMod && !Character.isWhitespace(cp)) {
                val prev = if (i > 0) t.codePointBefore(i) else -1
                if (prev != 0x200D) count++
            }
            i += Character.charCount(cp)
        }
        return count in 1..3
    }

    enum class ListFilter(val label: String) { ALL("Alle"), UNREAD("Ungelesen"), LETTERS("Briefe") }

    fun filterConversations(list: List<Conversation>, query: String, filter: ListFilter): List<Conversation> {
        val q = query.trim()
        return list.filter { c ->
            (q.isEmpty() || (c.friendName + " " + c.preview.text).contains(q, ignoreCase = true)) && when (filter) {
                ListFilter.ALL -> true
                ListFilter.UNREAD -> c.hasNews
                ListFilter.LETTERS -> c.readyLetters + c.lockedLetters + c.awaitingMe > 0
            }
        }
    }
}
