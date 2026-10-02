package at.gregor.layermaxxing

enum class ServerProfile(val key: String, val label: String, val expectedRole: String) {
    GREGOR("gregor", "Gregor (Original)", "production"),
    GERFRIED("gerfried", "Gerfried (Testserver)", "test"),
    GREGOR_TEST("gregor_test", "Gregor (Testserver)", "test");

    val baseUrl: String
        get() = when (this) {
            GREGOR -> BuildConfig.GREGOR_API_BASE_URL
            GERFRIED -> BuildConfig.GERFRIED_API_BASE_URL
            GREGOR_TEST -> BuildConfig.GREGOR_TEST_API_BASE_URL
        }

    fun isConfigured(url: String = baseUrl): Boolean =
        url.startsWith("https://") && !url.contains("example.invalid", ignoreCase = true)

    companion object {
        fun fromKey(value: String?): ServerProfile? = entries.firstOrNull { it.key == value }
    }
}

object ServerProfilePolicy {
    /**
     * The honest short form for the strip at the very top.
     *
     * It has to survive one line on a narrow phone without wrapping, so the full
     * sentence moved to [TEST_WARNING_DETAIL] on the confirmation dialog and in
     * "Mehr". What stays here still names the server and still says "test".
     */
    const val TEST_WARNING = "⚠ TESTSERVER – KEINE PRODUKTION"

    const val TEST_WARNING_DETAIL = "Testserver – nur zum Ausprobieren, keine Produktionsdaten."

    fun showTestWarning(profile: ServerProfile, verifiedRole: String?): Boolean = when {
        verifiedRole == "test" -> true
        // A server that positively answers as production is trusted, even when the
        // Gerfried profile points at it (for example after a rehearsal setup).
        verifiedRole == "production" -> false
        // Unknown or unreachable role: warn whenever the selected profile is the
        // test profile, so an offline test server never looks like the real one.
        else -> profile.expectedRole == "test"
    }
}

class ServerProfileStore(
    private val read: (String) -> String?,
    private val write: (String, String) -> Unit,
    private val clearSession: () -> Unit,
) {
    val selected: ServerProfile
        get() = ServerProfile.fromKey(read(PROFILE_KEY)) ?: ServerProfile.GREGOR_TEST

    val warningConfirmationRequired: Boolean
        get() = selected.expectedRole == "test" && read(WARNING_ACK_KEY) != "true"

    fun select(profile: ServerProfile) {
        if (selected == profile) return
        write(PROFILE_KEY, profile.key)
        write(WARNING_ACK_KEY, (profile.expectedRole != "test").toString())
        clearSession()
    }

    fun acknowledgeWarning() = write(WARNING_ACK_KEY, "true")

    private companion object {
        const val PROFILE_KEY = "server_profile"
        const val WARNING_ACK_KEY = "test_warning_acknowledged"
    }
}
