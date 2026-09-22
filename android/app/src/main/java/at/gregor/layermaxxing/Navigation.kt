package at.gregor.layermaxxing

/**
 * The primary navigation of the app.
 *
 * Exactly two tabs by default: people are not a place of their own — friends
 * ARE the chat overview, finding someone lives behind its round action there.
 * The third tab, [MainTab.CASTLES], is the hidden "Mittelalter-Modus"
 * experiment and only appears when its flag is on. Keeping the tab list a pure
 * function makes the "flag off means nothing changes" guarantee testable.
 */
enum class MainTab(val icon: String, val label: String) {
    CHATS("💬", "Chats"),
    MORE("⚙", "Mehr"),
    CASTLES("🏔", "Tal"),
}

object Navigation {
    /** Base messenger tabs, plus the castle experiment only when explicitly enabled. */
    fun tabs(castleExperiment: Boolean): List<MainTab> =
        if (castleExperiment) listOf(MainTab.CHATS, MainTab.MORE, MainTab.CASTLES)
        else listOf(MainTab.CHATS, MainTab.MORE)
}

/**
 * Reads and writes the castle experiment flag through injected accessors.
 *
 * Mirrors [ServerProfileStore] so the flag can be exercised without Android.
 * The flag is scoped per server profile: turning it on for the Gerfried test
 * server must never reveal the experiment on the Gregor profile.
 */
class CastleFlagStore(
    private val read: (String) -> String?,
    private val write: (String, String) -> Unit,
) {
    fun enabled(profileKey: String): Boolean = read(key(profileKey)) == "true"

    fun setEnabled(profileKey: String, value: Boolean) = write(key(profileKey), value.toString())

    private fun key(profileKey: String) = "castle_experiment_$profileKey"
}
