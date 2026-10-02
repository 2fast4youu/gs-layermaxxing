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
object AppGlossary {
    val entries = listOf(
        GlossaryEntry("Tal / Lehen", "Die grafische Ansicht einer Freundschaft. Jeder Freund hat sein eigenes Tal.", "Wegweiser: Freund wechseln. Haus: eigener Hof. Gegenüber: Freund besuchen."),
        GlossaryEntry("Poststelle / Briefraum", "Hier findest du eure versiegelten, freigegebenen und gelesenen Briefe.", "Im Chat auf den Umschlag oder im Tal auf Post tippen."),
        GlossaryEntry("Themen / Stichworte", "Notizen für das nächste Gespräch: privat, mit einem Freund oder in einer Gruppe.", "Anlegen, Notiz ergänzen, besprochen markieren und bei Bedarf wieder öffnen."),
        GlossaryEntry("Ebenen-Punkte (EP)", "Ein Dankeschön, das die andere Person erst annehmen muss. Nur angenommene Vorschläge zählen.", "In den Freundschaftsregeln gemeinsam aktivieren. Ein Vorschlag gibt einen Punkt."),
        GlossaryEntry("Schatzkammer / Ausbau", "Angenommene EP ermöglichen kosmetischen Ausbau auf diesem Gerät. Dein Chat wird dadurch nicht freigeschaltet oder gesperrt.", "Hof öffnen, Schatzkammer oder Baustelle wählen."),
        GlossaryEntry("Freundesfunke", "Eine kurze positive Nachricht ohne sichtbaren Absender. Der Server kennt den Absender zur Missbrauchsbehandlung.", "In Chats auf ✨ tippen. Empfänger können stummschalten oder melden."),
        GlossaryEntry("Versiegelter Brief", "Inhalt wird nach einer Freigaberegel angezeigt. Umschlagtext ist vorher sichtbar. Der Server verwahrt den Freigabeschlüssel.", "Dauer, Zeitpunkt, manuell, Zustimmung, Online-Präsenz oder Zufallsfenster wählen."),
        GlossaryEntry("Prüfdatei / Nachweis", "Signatur und Commitment helfen, Änderungen am Brief zu erkennen. Sie beweisen nicht die Identität einer Person.", "Im Briefraum den Nachweis öffnen und eine Prüfdatei exportieren."),
        GlossaryEntry("Freundschaftsregeln", "Chat, Briefe, EP und Mindestverzögerung werden beidseitig vereinbart.", "Im Gesprächsmenü Regeln vorschlagen; die andere Person muss zustimmen."),
        GlossaryEntry("Gruppe", "Mehrere Freunde für gemeinsame Themen und Gruppenbriefe. Ein separater Sofort-Gruppenchat ist noch nicht vorhanden.", "Gruppen öffnen, Freunde auswählen, dann Themen sammeln oder einen Gruppenbrief schreiben."),
        GlossaryEntry("Kreativmodus / Testserver", "Nur für berechtigte Testkonten: freier Ausbau und Vorspulen zeitgesteuerter Testbriefe. Keine echten Produktionsdaten.", "Unter Mehr aktivieren. Ein Testkonto gehört nur zum ausgewählten Server."),
    )
    fun search(query: String): List<GlossaryEntry> = query.trim().let { q ->
        entries.filter { q.isEmpty() || (it.term + " " + it.meaning + " " + it.action).contains(q, ignoreCase = true) }
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
}
