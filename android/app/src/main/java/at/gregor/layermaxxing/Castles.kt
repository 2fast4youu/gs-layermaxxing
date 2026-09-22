package at.gregor.layermaxxing

/** Pure, local model for the hidden valley experiment ("Das Lehen"). */

/** Existing release modes shown as couriers. No courier changes a letter's actual release. */
enum class Courier(val title: String, val mode: String, val note: String) {
    RIDER("Reiter", "timed", "Freigabezeit steht fest"),
    PIGEON("Brieftaube", "random", "Freigabe bleibt im vereinbarten Zufallsfenster"),
    OWL("Eule", "presence", "wartet auf serverbestätigte gleichzeitige Aktivität"),
    HERALD("Herold", "mutual", "wartet auf beide Zustimmungen"),
    COURTYARD("Hofbote", "manual", "wartet auf die manuelle Freigabe"),
}

/**
 * The buildings of the retired v0/v1 ledger.
 *
 * Nothing is built from these any more; they exist so an old ledger on a test
 * device can be read once and migrated into the build chain without losing EP.
 */
enum class Building(val price: Int) {
    WELL(1), PALISADE(2), WATCHTOWER(3), GATEHOUSE(5), KEEP(8),
}

data class Vale(
    val friendId: Long,
    val friendName: String,
    val avatarEmoji: String,
    val myEarnedEp: Int,
    val theirEarnedEp: Int,
    val built: Set<BuildStep>,
    val lettersInTransit: Int,
    val epEnabled: Boolean,
    val lettersEnabled: Boolean,
    val abandoned: Boolean,
    /** Creative mode: [built] is the free ledger and spends no EP at all. */
    val creative: Boolean = false,
) {
    val spent: Int get() = if (creative) 0 else Fief.spent(built)

    /** In creative mode nothing is ever spent: the chest keeps the honest EP. */
    val balance: Int get() = if (creative) myEarnedEp else Fief.balance(myEarnedEp, built)

    /** My own hut, from what I actually built here. */
    val hutStage: Int get() = Fief.hutStage(built)

    /** Their hut, derived only from the EP they accepted from me. */
    val theirHutStage: Int get() = Fief.friendHutStage(theirEarnedEp)
}

/** Normalized image point; both axes are in 0..1 of the plate. */
data class MapPoint(val x: Float, val y: Float)
data class MapSize(val width: Float, val height: Float)

/**
 * Where the valley experiment currently stands, as a plain chain of places.
 *
 * Keeping the spatial hierarchy in an enum makes the back chain a pure function
 * that a JVM test can walk, instead of an implicit tangle of Compose state.
 * [FRIEND] is the read-only visit to the other side's court.
 */
enum class CastlePlace { VALE, COURTYARD, BOARD, TREASURY, BUILD_SITE, COMPOSER, FRIEND }

object Castles {
    const val MAX_BUILDING_LEVEL = 3

    /** One step up the spatial chain. `null` means "leave the valley experiment". */
    fun back(place: CastlePlace): CastlePlace? = when (place) {
        CastlePlace.VALE -> null
        CastlePlace.COURTYARD, CastlePlace.FRIEND -> CastlePlace.VALE
        CastlePlace.BOARD, CastlePlace.TREASURY, CastlePlace.BUILD_SITE -> CastlePlace.COURTYARD
        CastlePlace.COMPOSER -> CastlePlace.BOARD
    }

    fun courierFor(mode: String): Courier = Courier.entries.firstOrNull { it.mode == mode } ?: Courier.RIDER

    /** What an old ledger was worth, in the prices it was written with. */
    fun spentFor(builds: Map<Building, Int>): Int = builds.entries.sumOf { (building, level) ->
        val safeLevel = level.coerceIn(0, MAX_BUILDING_LEVEL)
        building.price * safeLevel * (safeLevel + 1) / 2
    }

    private fun earnedBetween(history: List<ApiClient.EpProposal>, beneficiary: Long, proposer: Long): Int =
        history.filter { it.beneficiaryId == beneficiary && it.proposerId == proposer }.sumOf { it.points }

    fun vales(
        friends: List<ApiClient.UserSummary>,
        settings: List<ApiClient.FriendshipSettings>,
        epHistory: List<ApiClient.EpProposal>,
        lockedLettersByFriend: Map<Long, Int>,
        builtByFriend: Map<Long, Set<BuildStep>>,
        ownUserId: Long?,
        creative: Boolean = false,
    ): List<Vale> {
        val own = ownUserId ?: return emptyList()
        return friends.map { friend ->
            val rules = settings.firstOrNull { it.friendId == friend.id }
            Vale(
                friendId = friend.id,
                friendName = friend.name,
                avatarEmoji = friend.avatarEmoji,
                myEarnedEp = earnedBetween(epHistory, beneficiary = own, proposer = friend.id),
                theirEarnedEp = earnedBetween(epHistory, beneficiary = friend.id, proposer = own),
                built = builtByFriend[friend.id].orEmpty(),
                lettersInTransit = lockedLettersByFriend[friend.id] ?: 0,
                epEnabled = rules?.epEnabled == true,
                lettersEnabled = rules?.lettersEnabled == true,
                abandoned = rules == null,
                creative = creative,
            )
        }.sortedWith(compareByDescending<Vale> { it.myEarnedEp + it.theirEarnedEp }.thenBy { it.friendName.lowercase() })
    }

}
