package at.gregor.layermaxxing

/**
 * Presentation only: never changes friendship permissions or stored progress.
 *
 * Two modes on purpose. A third "game only" mode used to rebuild the navigation
 * a third way; the village already opens every conversation, so it is folded
 * into [BOTH]. Stored "game" keys migrate there and start in the village.
 */
enum class AppMode(val key: String, val label: String, val detail: String) {
    MESSENGER("messenger", "Messenger", "Chats, Briefe, Themen und Gruppen – ohne Dorf."),
    BOTH("both", "Messenger + Dorf", "Alles vom Messenger, dazu das Dorf, das mit euch wächst.");
    val startTab: MainTab get() = MainTab.CHATS
    val showsValley: Boolean get() = this != MESSENGER
    fun tabs(): List<MainTab> = when (this) {
        MESSENGER -> listOf(MainTab.CHATS, MainTab.MORE)
        BOTH -> listOf(MainTab.CHATS, MainTab.CASTLES, MainTab.MORE)
    }
    companion object {
        const val LEGACY_GAME_KEY = "game"
        fun fromKey(key: String?, legacyCastle: Boolean = false): AppMode =
            entries.firstOrNull { it.key == key }
                ?: if (key == LEGACY_GAME_KEY || legacyCastle) BOTH else MESSENGER
    }
}
