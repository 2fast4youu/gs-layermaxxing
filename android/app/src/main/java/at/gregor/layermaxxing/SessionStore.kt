package at.gregor.layermaxxing

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedAccount(val name: String, val token: String)

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
    // Fast account switching for testing: logins are remembered per server
    // profile and survive a logout, so rehearsing a flow between two accounts
    // never asks for the password twice.
    private fun accountKey() = "accounts_${serverProfile.key}"

    fun savedAccounts(): List<SavedAccount> {
        val raw = prefs.getString(accountKey(), null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val entry = array.optJSONObject(index) ?: return@mapNotNull null
                val name = entry.optString("name")
                val token = entry.optString("token")
                if (name.isBlank() || token.isBlank()) null else SavedAccount(name, token)
            }
        }.getOrDefault(emptyList())
    }

    fun saveAccount(name: String, token: String) {
        val ordered = listOf(SavedAccount(name, token)) + savedAccounts().filterNot { it.name == name }
        prefs.edit().putString(accountKey(), accountsJson(ordered.take(8))).apply()
    }

    private fun accountsJson(accounts: List<SavedAccount>): String {
        val array = JSONArray()
        accounts.forEach { array.put(JSONObject().put("name", it.name).put("token", it.token)) }
        return array.toString()
    }

    fun clear() {
        val preservedTheme = theme
        val preservedProfile = serverProfile.key
        val warningAcknowledged = !warningConfirmationRequired
        val preservedBiometric = biometricEnabled
        val preservedAccounts = prefs.all.filterKeys { it.startsWith("accounts_") }
        // Remove only session data; preferences, the per-profile notification
        // ledger and the saved test accounts must survive a logout.
        val editor = prefs.edit().clear()
            .putString("theme", preservedTheme)
            .putString("server_profile", preservedProfile)
            .putBoolean("biometric", preservedBiometric)
            .putString("test_warning_acknowledged", warningAcknowledged.toString())
            .putStringSet(ledgerKey("messages"), notifiedMessages())
            .putStringSet(ledgerKey("requests"), notifiedRequests())
            .putBoolean(ledgerKey("seeded"), notifiedMessagesSeeded())
        preservedAccounts.forEach { (key, value) -> if (value is String) editor.putString(key, value) }
        editor.apply()
    }
}
