package at.gregor.layermaxxing

/**
 * Creative mode: free cosmetic building and quick letter presets for test
 * accounts. Pure Kotlin, so the three-key rule is pinned by a JVM test.
 *
 * All three keys must be present at the same time: the server answering the
 * current status request calls itself role "test", the account carries the
 * server-side one-time entitlement, and this device has its local switch on.
 * The device stores only the switch — the entitlement is re-read with every
 * status refresh, so a revoked entitlement or a production server wins
 * immediately, whatever the switch says.
 *
 * The mode never touches a server value: builds go into a separate local
 * ledger, the treasury keeps showing the real accepted EP, and letters keep
 * using the real friendship, letter and release gates.
 */
object CreativeMode {
    fun eligible(serverRole: String?, entitled: Boolean): Boolean =
        serverRole == "test" && entitled

    fun active(serverRole: String?, entitled: Boolean, localSwitch: Boolean): Boolean =
        eligible(serverRole, entitled) && localSwitch

    /**
     * Release modes whose clock a test account may pull into the present.
     *
     * Only gates that are pure time: a timed release and the random window. A
     * mutual, presence or manual letter waits for a decision somebody else has
     * to make, and fast-forwarding those would forge that consent. The server
     * enforces exactly the same list and is the authority; this is the client's
     * half so no button ever appears that the server would reject.
     */
    val ADVANCEABLE_MODES = setOf("timed", "random")

    /**
     * Whether the fast-forward action may be offered for one letter at all.
     *
     * Needs the whole creative mode (test server + entitlement + local switch),
     * a letter that is still sealed, and a purely time-based gate. An already
     * released letter has nothing left to advance.
     */
    fun canAdvance(active: Boolean, mode: String, unlocked: Boolean): Boolean =
        active && !unlocked && mode in ADVANCEABLE_MODES

    /**
     * The presets of the quick test letter, as (label, api mode, seconds).
     *
     * Deliberately only the two advanceable gates plus an immediate one, so the
     * whole loop "send → watch it travel → fast-forward → open" can be walked
     * without ever needing the other side to press anything.
     */
    val LETTER_PRESETS: List<Triple<String, String, Long>> = listOf(
        Triple("In 1 Minute", "timed", 60L),
        Triple("In 1 Stunde", "timed", 3_600L),
        Triple("In 1 Tag", "timed", 86_400L),
    )
}
