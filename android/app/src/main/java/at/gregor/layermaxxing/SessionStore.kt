package at.gregor.layermaxxing

import android.content.Context

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("layermaxxing_session", Context.MODE_PRIVATE)

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

    fun notifiedMessages(): Set<String> = prefs.getStringSet("notified_messages", emptySet()).orEmpty()
    fun notifiedRequests(): Set<String> = prefs.getStringSet("notified_requests", emptySet()).orEmpty()
    fun saveNotified(messages: Set<String>, requests: Set<String>) {
        prefs.edit().putStringSet("notified_messages", messages).putStringSet("notified_requests", requests).apply()
    }
    fun clear() {
        val preservedTheme = theme
        prefs.edit().clear().putString("theme", preservedTheme).apply()
    }
}
