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
    private val castleFlagStore = CastleFlagStore(
        read = { prefs.getString(it, null) },
        write = { key, value -> prefs.edit().putString(key, value).apply() },
    )

    val serverProfile: ServerProfile get() = profileStore.selected
    val warningConfirmationRequired: Boolean get() = profileStore.warningConfirmationRequired

    fun selectServerProfile(profile: ServerProfile) = profileStore.select(profile)
    fun acknowledgeTestWarning() = profileStore.acknowledgeWarning()

    // The castle experiment is a purely local visibility flag, scoped per server
    // profile so enabling it on Gerfried never reveals it on the Gregor profile.
    val castleExperiment: Boolean get() = castleFlagStore.enabled(serverProfile.key)
    fun setCastleExperiment(value: Boolean) = castleFlagStore.setEnabled(serverProfile.key, value)

    // Device-local build ledger for the experiment, keyed per profile and account
    // so nothing leaks across accounts or servers. No server ever sees this.
    // Creative mode writes into its own ledger: switching it off returns to the
    // honestly earned state, and free builds never mix into real progress.
    private fun castleBuildKey(creative: Boolean) =
        if (creative) "castles_creative_${serverProfile.key}_$name" else "castles_${serverProfile.key}_$name"
    private fun fiefSeenKey(friendId: Long) = "castle_seen_${serverProfile.key}_${name}_$friendId"
    private fun creativeModeKey() = "creative_${serverProfile.key}_$name"

    /** Only the local activation switch. The entitlement is never stored. */
    val creativeMode: Boolean get() = prefs.getString(creativeModeKey(), null) == "true"

    fun setCreativeMode(value: Boolean) {
        prefs.edit().putString(creativeModeKey(), value.toString()).apply()
    }

    /**
     * The build ledger, in format v2: one set of chain steps per friendship.
     *
     * A ledger from the retired building/level format is read through
     * [Fief.migrateLegacy] on every read and rewritten as v2 on the next build,
     * so an old test device keeps its progress and never owes EP.
     */
    fun fiefBuilds(creative: Boolean = false): Map<Long, Set<BuildStep>> {
        val raw = prefs.getString(castleBuildKey(creative), null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            obj.keys().asSequence().associate { key -> key.toLong() to parseLedgerEntry(obj.get(key)) }
        }.getOrDefault(emptyMap())
    }

    private fun parseLedgerEntry(saved: Any?): Set<BuildStep> = when (saved) {
        // v2: the chain itself.
        is JSONObject -> if (saved.optInt("v") == 2) {
            val steps = saved.optJSONArray("steps")
            (0 until (steps?.length() ?: 0)).mapNotNull { index ->
                runCatching { BuildStep.valueOf(steps!!.getString(index)) }.getOrNull()
            }.toSet()
        } else {
            // v1: building -> level.
            Fief.migrateLegacy(
                saved.keys().asSequence().mapNotNull { buildingName ->
                    runCatching {
                        Building.valueOf(buildingName) to
                            saved.getInt(buildingName).coerceIn(1, Castles.MAX_BUILDING_LEVEL)
                    }.getOrNull()
                }.toMap(),
            )
        }
        // v0: a set of buildings stored as an array.
        is JSONArray -> Fief.migrateLegacy(
            (0 until saved.length()).mapNotNull { index ->
                runCatching { Building.valueOf(saved.getString(index)) to 1 }.getOrNull()
            }.toMap(),
        )
        else -> emptySet()
    }

    /** Enforces affordability again at persistence time, so rapid taps cannot overdraw EP. */
    fun buildFiefStep(friendId: Long, step: BuildStep, earnedEp: Int, creative: Boolean = false): Boolean {
        val current = fiefBuilds(creative).toMutableMap()
        val before = current[friendId].orEmpty()
        val after = if (creative) Fief.buildFree(before, step) else Fief.build(before, step, earnedEp)
        if (after == before) return false
        current[friendId] = after
        val obj = JSONObject()
        current.forEach { (id, steps) ->
            val array = JSONArray()
            Fief.chain.filter { it in steps }.forEach { array.put(it.name) }
            obj.put(id.toString(), JSONObject().put("v", 2).put("steps", array))
        }
        prefs.edit().putString(castleBuildKey(creative), obj.toString()).apply()
        return true
    }

    /** Accepted EP at the last visit to the chest, so new coins can fall in once. */
    fun fiefSeenEarned(friendId: Long): Int =
        prefs.getString(fiefSeenKey(friendId), null)?.toIntOrNull() ?: 0

    fun setFiefSeenEarned(friendId: Long, earnedEp: Int) {
        prefs.edit().putString(fiefSeenKey(friendId), earnedEp.coerceAtLeast(0).toString()).apply()
    }

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

    fun removeAccount(name: String) {
        prefs.edit().putString(accountKey(), accountsJson(savedAccounts().filterNot { it.name == name })).apply()
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
        // Test accounts and the local castle state (flag + ledgers + creative
        // switch) are kept per profile/account and must survive a logout.
        val preservedAccounts = prefs.all.filterKeys {
            it.startsWith("accounts_") || it.startsWith("castle") || it.startsWith("creative_")
        }
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
