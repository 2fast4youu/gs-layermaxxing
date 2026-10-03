package at.gregor.layermaxxing

/**
 * The village grows with use: a new player starts with three places and earns
 * the others by actually doing things. Unlocks come only from real data, are
 * remembered so a building never disappears again, and anything with real news
 * opens at once — growth may hide empty features, never information.
 *
 * Nothing here is a permission: every feature stays reachable from the messenger.
 */
data class GrowthFacts(
    val friends: Int = 0,
    val chatsWithActivity: Int = 0,
    val letters: Int = 0,
    val topics: Int = 0,
    val groups: Int = 0,
    val sparks: Int = 0,
    val epEnabledFriendships: Int = 0,
    val epReceived: Int = 0,
)

data class GrowthStep(val destination: ValleyDestination, val task: String, val hint: String)

object VillageGrowth {
    /** Always standing: meet people, find people, and the town hall (settings must never be locked). */
    val START = setOf(ValleyDestination.CONVERSATIONS, ValleyDestination.PEOPLE, ValleyDestination.SETTINGS)

    /** The order a player grows into the village; also the order of the "next step" guide. */
    val PATH: List<GrowthStep> = listOf(
        GrowthStep(ValleyDestination.TOPICS, "Finde deinen ersten Freund", "Am Wegweiser jemanden suchen und anfragen."),
        GrowthStep(ValleyDestination.SPARKS, "Schreib deine erste Nachricht", "Im Treffpunkt einen Chat öffnen und etwas schreiben."),
        GrowthStep(ValleyDestination.GLOSSARY, "Notiere ein erstes Thema", "Am Schwarzen Brett ein Stichwort für euer nächstes Gespräch anlegen."),
        GrowthStep(ValleyDestination.ARCHIVE, "Schick oder bekomm einen Brief", "Im Chat auf ✦ tippen und einen versiegelten Brief schreiben."),
        GrowthStep(ValleyDestination.GROUPS, "Hab zwei Freunde", "Mit zwei Freunden entsteht der Gruppenplatz."),
        GrowthStep(ValleyDestination.EP, "Schalte EP in einer Freundschaft ein", "In den Freundschaftsregeln gemeinsam Ebenen-Punkte aktivieren."),
    )

    fun earned(d: ValleyDestination, f: GrowthFacts): Boolean = when (d) {
        ValleyDestination.CONVERSATIONS, ValleyDestination.PEOPLE, ValleyDestination.SETTINGS -> true
        ValleyDestination.TOPICS -> f.friends >= 1 || f.topics > 0
        ValleyDestination.SPARKS -> f.chatsWithActivity >= 1 || f.sparks > 0
        ValleyDestination.GLOSSARY -> f.topics >= 1
        ValleyDestination.ARCHIVE -> f.letters >= 1
        ValleyDestination.GROUPS -> f.friends >= 2 || f.groups > 0
        ValleyDestination.EP -> f.epEnabledFriendships >= 1 || f.epReceived > 0
    }

    /**
     * Unlocked places = start set + everything earned now + everything earned
     * before + everything that carries news right now.
     */
    fun unlocked(facts: GrowthFacts, remembered: Set<ValleyDestination>, news: Map<ValleyDestination, Int>): Set<ValleyDestination> =
        START + remembered + ValleyDestination.entries.filter { earned(it, facts) || (news[it] ?: 0) > 0 }

    /** The single next thing to do, or null when the whole village stands. */
    fun nextStep(unlocked: Set<ValleyDestination>): GrowthStep? = PATH.firstOrNull { it.destination !in unlocked }

    /** Where the guide should send the player to complete [step]. */
    fun guideTarget(step: GrowthStep): ValleyDestination = when (step.destination) {
        ValleyDestination.TOPICS, ValleyDestination.GROUPS -> ValleyDestination.PEOPLE
        ValleyDestination.SPARKS, ValleyDestination.ARCHIVE, ValleyDestination.EP -> ValleyDestination.CONVERSATIONS
        ValleyDestination.GLOSSARY -> ValleyDestination.TOPICS
        else -> step.destination
    }

    fun level(unlocked: Set<ValleyDestination>): Int = unlocked.size - START.size + 1
    val MAX_LEVEL = ValleyDestination.entries.size - START.size + 1

    fun encode(set: Set<ValleyDestination>): String = set.joinToString(",") { it.name }
    fun decode(raw: String?): Set<ValleyDestination> =
        raw.orEmpty().split(',').mapNotNull { n -> ValleyDestination.entries.firstOrNull { it.name == n } }.toSet()
}
