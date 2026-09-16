package at.gregor.layermaxxing

import android.content.Context

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("layermaxxing_session", Context.MODE_PRIVATE)
    private val profileStore = ServerProfileStore(
        read = { prefs.getString(it, null) },
        write = { key, value -> prefs.edit().putString(key, value).apply() },
        clearSession = {
            prefs.edit().remove("token").remove("name")
                .remove("notified_messages").remove("notified_requests").apply()
        },
    )

    var token: String?
        get() = prefs.getString("token", null)
        set(value) { prefs.edit().putString("token", value).apply() }
    var name: String
        get() = prefs.getString("name", "").orEmpty()
        set(value) { prefs.edit().putString("name", value).apply() }
    var theme: String
        get() = prefs.getString("theme", "system") ?: "system"
        set(value) { prefs.edit().putString("theme", value).apply() }
    var biometricEnabled: Boolean
        get() = prefs.getBoolean("biometric", false)
        set(value) { prefs.edit().putBoolean("biometric", value).apply() }
    val serverProfile: ServerProfile get() = profileStore.selected
    val warningConfirmationRequired: Boolean get() = profileStore.warningConfirmationRequired

    fun selectServerProfile(profile: ServerProfile) = profileStore.select(profile)
    fun acknowledgeTestWarning() = profileStore.acknowledgeWarning()

    // The notification ledger is kept per server profile: message ids belong to one
    // server, and switching profiles or logging out must not replay old notifications.
    private fun ledgerKey(name: String) = "notified_${serverProfile.key}_$name"

    fun notifiedMessages(): Set<String> = prefs.getStringSet(ledgerKey("messages"), emptySet()).orEmpty()
    fun notifiedRequests(): Set<String> = prefs.getStringSet(ledgerKey("requests"), emptySet()).orEmpty()
    fun notifiedMessagesSeeded(): Boolean = prefs.getBoolean(ledgerKey("seeded"), false)
    fun markNotifiedSeeded() { prefs.edit().putBoolean(ledgerKey("seeded"), true).apply() }
    fun saveNotified(messages: Set<String>, requests: Set<String>) {
        prefs.edit().putStringSet(ledgerKey("messages"), messages)
            .putStringSet(ledgerKey("requests"), requests).apply()
    }
    fun clear() {
        val preservedTheme = theme
        val preservedProfile = serverProfile.key
        val warningAcknowledged = !warningConfirmationRequired
        val preservedBiometric = biometricEnabled
        // Remove only session data; the per-profile notification ledger and the
        // user preferences above must survive a logout.
        prefs.edit().clear().putString("theme", preservedTheme)
            .putString("server_profile", preservedProfile)
            .putBoolean("biometric", preservedBiometric)
            .putString("test_warning_acknowledged", warningAcknowledged.toString())
            .putStringSet(ledgerKey("messages"), notifiedMessages())
            .putStringSet(ledgerKey("requests"), notifiedRequests())
            .putBoolean(ledgerKey("seeded"), notifiedMessagesSeeded()).apply()
    }
}
