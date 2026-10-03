package at.gregor.layermaxxing

/** Presentation only: never changes friendship permissions or stored progress. */
enum class AppMode(val key: String, val label: String, val detail: String) {
    MESSENGER("messenger", "Nur Messenger", "Gespräche, Briefe, Themen und Gruppen – ohne Tal."),
    GAME("game", "Nur Spiel", "Start im Tal. Gespräche und Themen direkt von dort öffnen."),
    BOTH("both", "Messenger & Spiel", "Start bei den Gesprächen; das Tal bleibt erreichbar.");
    val startTab: MainTab get() = if (this == GAME) MainTab.CASTLES else MainTab.CHATS
    val showsValley: Boolean get() = this != MESSENGER
    fun tabs(): List<MainTab> = when (this) {
        MESSENGER -> listOf(MainTab.CHATS, MainTab.MORE)
        GAME -> listOf(MainTab.CASTLES, MainTab.MORE)
        BOTH -> listOf(MainTab.CHATS, MainTab.CASTLES, MainTab.MORE)
    }
    companion object {
        fun fromKey(key: String?, legacyCastle: Boolean = false): AppMode =
            entries.firstOrNull { it.key == key } ?: if (legacyCastle) BOTH else MESSENGER
    }
}
