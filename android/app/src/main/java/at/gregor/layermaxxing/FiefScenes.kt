package at.gregor.layermaxxing

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The scenes of "Das Lehen" as plain data.
 *
 * Every rule about what the valley shows, what may move, what is usable and what
 * is painted decoration lives here, so the calm, the emptiness and the growth are
 * unit-testable instead of hidden inside Compose. The screens only render.
 *
 * Pure Kotlin: no Android, no Compose, no org.json.
 */

/** Drawable base names. The screen maps them to resource ids exactly once. */
object FiefAssets {
    const val VALLEY_PLATE = "fief_valley_base"
    const val COURTYARD_PLATE = "fief_courtyard_base"
    const val BOARD_PLATE = "fief_board_closeup"
    const val TREASURY_PLATE = "fief_treasury_closeup"
    const val BUILD_PLATE = "fief_build_closeup"

    const val HUT_STAGE0 = "fief_hut_stage0"
    const val HUT_STAGE1 = "fief_hut_stage1"
    const val HUT_STAGE2 = "fief_hut_stage2"
    const val HUT_STAGE3 = "fief_hut_stage3"

    const val WELL = "fief_cy_well"
    const val YARD_RING = "fief_yard_ring"
    const val BARN = "fief_barn_small"
    const val TOWER = "fief_tower_small"
    const val JETTY = "fief_jetty"
    const val FIELD = "fief_field"
    const val ORCHARD = "fief_orchard"
    const val BRIDGE = "fief_bridge"
    const val SIGNPOST = "fief_signpost"

    const val BOARD = "fief_cy_board"
    const val CHEST = "fief_cy_chest"
    const val BUILD_CORNER = "fief_cy_buildcorner"

    const val ENV_SEALED = "fief_env_sealed"
    const val ENV_READY = "fief_env_ready"
    const val ENV_OPEN = "fief_env_open"
    const val ENV_OUT = "fief_env_out"
    const val QUILL = "fief_quill"
    const val COIN = "fief_coin"
    const val CHAIN_LOCK = "fief_chain_lock"
    const val PARCHMENT = "fief_parchment_card"

    /** The upgraded path has no image: it is a drawn overlay along the road spline. */
    const val PATH_OVERLAY = "path_overlay"
}

enum class MotionKind { SWAY, GLINT, FLOAT }

/**
 * Micro-idle of a usable object.
 *
 * Bounded by construction: at most 8 px of travel and never faster than a
 * four-second period, so a scene can breathe without ever pulsing or blinking.
 */
data class Motion(val kind: MotionKind, val ampPx: Float, val periodSec: Float, val phase: Float) {
    init {
        require(ampPx <= FiefScenes.MAX_MOTION_AMPLITUDE_PX)
        require(periodSec >= FiefScenes.MIN_MOTION_PERIOD_SEC)
    }
}

/**
 * One painted thing on a plate.
 *
 * [anchor] is the point on the plate the sprite stands on, [pivotY] says where in
 * the image that point sits (1 = bottom edge, .5 = centred). [hitPoint] is the
 * centre of the touch area; decoration has no touch area at all.
 */
data class SceneSprite(
    val id: String,
    val asset: String,
    val anchor: MapPoint,
    val widthFraction: Float,
    val z: Int,
    val interactive: Boolean = false,
    val motion: Motion? = null,
    val label: String = "",
    val pivotY: Float = 1f,
    val hitPoint: MapPoint = anchor,
    val hitRadius: Float = widthFraction / 2f,
)

data class Scene(
    val id: String,
    val plate: String,
    val image: MapSize,
    val sprites: List<SceneSprite>,
    val maxZoom: Float,
)

/** One envelope hanging from a nail on the board. */
data class BoardSlot(
    val letterId: Long,
    val envelope: String,
    val tag: String?,
    val anchor: MapPoint,
    val incoming: Boolean,
    val bucket: LetterBucket,
    val coinTag: Boolean,
    val label: String,
)

/** One pinned plan on the workshop wall. Costs are coins, never digits. */
data class PlanSlot(
    val step: BuildStep,
    val anchor: MapPoint,
    val preview: String,
    val coins: Int,
    val affordable: Boolean,
    val locked: Boolean,
    /** Creative mode: the plan is buildable without coins and says so. */
    val free: Boolean = false,
)

object FiefScenes {
    const val MAX_MOTION_AMPLITUDE_PX = 8f
    const val MIN_MOTION_PERIOD_SEC = 4f
    const val MAX_MOVING_SPRITES = 6

    val VALLEY_IMAGE = MapSize(940f, 1672f)
    val COURTYARD_IMAGE = MapSize(1086f, 1448f)
    val CLOSEUP_IMAGE = MapSize(940f, 1672f)

    const val VALLEY_MAX_ZOOM = 3f
    const val COURTYARD_MAX_ZOOM = 2.2f

    /** My clearing is always the lower one: the valley is seen from where I stand. */
    val OWN_CLEARING = MapPoint(.245f, .715f)
    val FRIEND_CLEARING = MapPoint(.805f, .225f)
    val SIGNPOST_POINT = MapPoint(.400f, .860f)

    /** The dirt road, used for the upgraded-path overlay and for the courier. */
    val road = listOf(
        MapPoint(.365f, .730f), MapPoint(.355f, .690f), MapPoint(.360f, .650f),
        MapPoint(.362f, .605f), MapPoint(.375f, .565f), MapPoint(.405f, .520f),
        MapPoint(.455f, .475f), MapPoint(.530f, .440f), MapPoint(.610f, .390f),
        MapPoint(.680f, .320f), MapPoint(.740f, .260f), MapPoint(.782f, .220f),
    )

    fun roadPoint(progress: Float): MapPoint {
        val p = progress.coerceIn(0f, 1f)
        if (p == 1f) return road.last()
        val scaled = p * (road.size - 1)
        val index = scaled.toInt().coerceAtMost(road.size - 2)
        val local = scaled - index
        val a = road[index]
        val b = road[index + 1]
        return MapPoint(a.x + (b.x - a.x) * local, a.y + (b.y - a.y) * local)
    }

    // -----------------------------------------------------------------------
    // Deliveries on the road
    // -----------------------------------------------------------------------

    /** The road never shows more couriers than it can keep legible. */
    const val MAX_DELIVERIES = 4

    /**
     * Journey positions per release mode, as fractions of the sender→recipient
     * road. Time-gated letters really travel: their fraction is the elapsed
     * share of the agreed span, so the same letter stands further along on
     * every visit. Everything else stands where its gate lives — the random
     * window keeps its secret in the middle, presence and mutual wait at the
     * recipient's gate, a manual letter waits in the sender's own yard.
     */
    fun journeyFraction(message: ApiClient.Message, now: Long): Float = when (message.mode) {
        "timed" -> {
            val start = message.createdAt
            val end = message.releaseAt ?: start
            if (end <= start) .5f
            else ((now - start).toFloat() / (end - start).toFloat()).coerceIn(.08f, .92f)
        }
        "random" -> .5f
        "presence", "mutual" -> .85f
        else -> .12f
    }

    /**
     * One locked letter as one identifiable courier on the road.
     *
     * Nothing here is decoration: every stop belongs to exactly one existing
     * letter, tapping it opens exactly that letter, and its position follows
     * the letter's own release rule. Ready and opened letters are not on the
     * road any more — they hang at the post office, where the sign counts them.
     */
    data class DeliveryStop(
        val letterId: Long,
        val courier: Courier,
        val outgoing: Boolean,
        /** 0 = own hut, 1 = friend's hut, whatever the direction of travel. */
        val roadPosition: Float,
        val label: String,
    )

    fun deliveryStops(
        letters: List<ApiClient.Message>,
        friendName: String,
        now: Long,
    ): List<DeliveryStop> = letters
        .filter { !it.unlocked }
        .sortedWith(compareByDescending<ApiClient.Message> { it.createdAt }.thenByDescending { it.id })
        .take(MAX_DELIVERIES)
        .map { message ->
            val courier = Castles.courierFor(message.mode)
            val fraction = journeyFraction(message, now)
            val title = message.title.ifBlank { Conversations.SEALED_FALLBACK }
            DeliveryStop(
                letterId = message.id,
                courier = courier,
                outgoing = !message.incoming,
                roadPosition = if (message.incoming) 1f - fraction else fraction,
                label = if (message.incoming) "${courier.title} von $friendName unterwegs: „$title“"
                else "${courier.title} zu $friendName unterwegs: „$title“",
            )
        }

    // -----------------------------------------------------------------------
    // E1: the valley
    // -----------------------------------------------------------------------

    /**
     * The valley.
     *
     * At zero EP this is deliberately boring: the painted landscape, the two huts
     * and a signpost. Everything else has to be built, and every built step is a
     * visible sprite instead of a level number.
     */
    fun valleyScene(vale: Vale, built: Set<BuildStep>): Scene {
        val sprites = mutableListOf<SceneSprite>()

        if (BuildStep.PATH in built) sprites += SceneSprite(
            id = "path", asset = FiefAssets.PATH_OVERLAY, anchor = MapPoint(.5f, .5f),
            widthFraction = 1f, z = 5, pivotY = .5f,
        )
        if (BuildStep.BRIDGE in built) sprites += SceneSprite(
            id = "bridge", asset = FiefAssets.BRIDGE, anchor = MapPoint(.545f, .455f),
            widthFraction = .17f, z = 8, pivotY = .6f,
        )
        if (BuildStep.JETTY in built) sprites += SceneSprite(
            id = "jetty", asset = FiefAssets.JETTY, anchor = MapPoint(.735f, .565f), widthFraction = .15f, z = 9,
        )
        if (BuildStep.FIELD in built) sprites += SceneSprite(
            id = "field", asset = FiefAssets.FIELD, anchor = MapPoint(.500f, .790f), widthFraction = .18f, z = 9,
        )
        if (BuildStep.ORCHARD in built) sprites += SceneSprite(
            id = "orchard", asset = FiefAssets.ORCHARD, anchor = MapPoint(.455f, .640f), widthFraction = .17f, z = 12,
        )
        if (BuildStep.FENCE in built) sprites += SceneSprite(
            id = "yard-ring", asset = FiefAssets.YARD_RING, anchor = MapPoint(.235f, .695f),
            widthFraction = .32f, z = 15, pivotY = .5f,
        )
        if (BuildStep.WELL in built) sprites += SceneSprite(
            id = "well", asset = FiefAssets.WELL, anchor = MapPoint(.330f, .735f), widthFraction = .055f, z = 18,
        )
        if (BuildStep.BARN in built) sprites += SceneSprite(
            id = "barn", asset = FiefAssets.BARN, anchor = MapPoint(.140f, .752f), widthFraction = .105f, z = 18,
        )
        if (BuildStep.TOWER in built) sprites += SceneSprite(
            id = "tower", asset = FiefAssets.TOWER, anchor = MapPoint(.362f, .684f), widthFraction = .065f, z = 18,
        )

        sprites += SceneSprite(
            id = "home",
            asset = Fief.hutStages[Fief.hutStage(built)],
            anchor = OWN_CLEARING,
            widthFraction = .215f,
            z = 20,
            interactive = true,
            motion = Motion(MotionKind.GLINT, 3f, 7f, 0f),
            label = "Dein Hof im Tal mit ${vale.friendName}",
            hitPoint = MapPoint(OWN_CLEARING.x, OWN_CLEARING.y - .035f),
            hitRadius = .105f,
        )
        sprites += SceneSprite(
            id = "friend",
            asset = Fief.hutStages[vale.theirHutStage],
            anchor = FRIEND_CLEARING,
            widthFraction = .145f,
            z = 20,
            interactive = true,
            motion = Motion(MotionKind.GLINT, 3f, 9f, .35f),
            label = "Hof von ${vale.friendName}",
            hitPoint = MapPoint(FRIEND_CLEARING.x, FRIEND_CLEARING.y - .025f),
            hitRadius = .08f,
        )
        sprites += SceneSprite(
            id = "signpost",
            asset = FiefAssets.SIGNPOST,
            anchor = SIGNPOST_POINT,
            widthFraction = .085f,
            z = 22,
            interactive = true,
            motion = Motion(MotionKind.SWAY, 2f, 6f, .6f),
            label = "Wegweiser: andere Täler und Auskunft über dieses Tal",
            hitPoint = MapPoint(SIGNPOST_POINT.x, SIGNPOST_POINT.y - .03f),
            hitRadius = .06f,
        )
        return Scene("vale", FiefAssets.VALLEY_PLATE, VALLEY_IMAGE, sprites.sortedBy { it.z }, VALLEY_MAX_ZOOM)
    }

    /**
     * Where the new sprite of a build step sits, for the dust reveal after a build.
     *
     * A HOF step lands in the courtyard (where the back chain returns to); a LAND
     * step lands out in the valley and is revealed the next time it is seen. Both
     * anchors match the coordinates the scene builders use, so the dust always
     * appears exactly where the sprite grows.
     */
    fun courtyardRevealAnchor(step: BuildStep): MapPoint? = when (step) {
        BuildStep.WELL -> MapPoint(.285f, .430f)
        BuildStep.ROOF, BuildStep.STONEWORK, BuildStep.KEEP -> MapPoint(.505f, .285f)
        BuildStep.FENCE -> MapPoint(.50f, .50f)
        BuildStep.BARN -> MapPoint(.845f, .360f)
        BuildStep.TOWER -> MapPoint(.125f, .355f)
        else -> null
    }

    fun valleyRevealAnchor(step: BuildStep): MapPoint? = when (step.track) {
        Track.HOF -> null
        Track.LAND -> when (step) {
            BuildStep.PATH -> MapPoint(.455f, .490f)
            BuildStep.JETTY -> MapPoint(.735f, .565f)
            BuildStep.FIELD -> MapPoint(.500f, .790f)
            BuildStep.ORCHARD -> MapPoint(.455f, .640f)
            BuildStep.BRIDGE -> MapPoint(.545f, .455f)
            else -> null
        }
    }

    // -----------------------------------------------------------------------
    // E2: the courtyard
    // -----------------------------------------------------------------------

    /**
     * My own yard.
     *
     * The board exists from minute one, the chest appears with the first accepted
     * EP and the build corner disappears for good once everything is built — no
     * place here is ever a dead tap.
     */
    fun courtyardScene(
        vale: Vale,
        built: Set<BuildStep>,
        epEnabled: Boolean,
        lettersEnabled: Boolean,
    ): Scene {
        val sprites = mutableListOf<SceneSprite>()
        if (BuildStep.FENCE in built) sprites += SceneSprite(
            id = "cy-ring", asset = FiefAssets.YARD_RING, anchor = MapPoint(.50f, .50f),
            widthFraction = .96f, z = 6, pivotY = .5f,
        )
        if (BuildStep.TOWER in built) sprites += SceneSprite(
            id = "cy-tower", asset = FiefAssets.TOWER, anchor = MapPoint(.125f, .355f), widthFraction = .13f, z = 12,
        )
        if (BuildStep.BARN in built) sprites += SceneSprite(
            id = "cy-barn", asset = FiefAssets.BARN, anchor = MapPoint(.845f, .360f), widthFraction = .20f, z = 12,
        )
        sprites += SceneSprite(
            id = "cy-hut", asset = Fief.hutStages[Fief.hutStage(built)], anchor = MapPoint(.505f, .285f),
            widthFraction = .32f, z = 14,
        )
        if (BuildStep.WELL in built) sprites += SceneSprite(
            id = "cy-well", asset = FiefAssets.WELL, anchor = MapPoint(.285f, .430f), widthFraction = .11f, z = 16,
        )
        sprites += SceneSprite(
            id = "cy-board",
            asset = FiefAssets.BOARD,
            anchor = MapPoint(.225f, .620f),
            widthFraction = .19f,
            z = 30,
            interactive = true,
            motion = Motion(MotionKind.SWAY, 2.5f, 7f, 0f),
            label = if (lettersEnabled) "Schwarze Tafel, Briefe mit ${vale.friendName}"
            else "Schwarze Tafel, Briefe sind in dieser Freundschaft aus",
            hitPoint = MapPoint(.225f, .565f),
            hitRadius = .12f,
        )
        if (vale.myEarnedEp > 0) {
            sprites += SceneSprite(
                id = "cy-chest",
                asset = FiefAssets.CHEST,
                anchor = MapPoint(.755f, .600f),
                widthFraction = .16f,
                z = 30,
                interactive = true,
                motion = Motion(MotionKind.GLINT, 2f, 8f, .3f),
                label = if (epEnabled) "Truhe, ${vale.balance} Ebenen-Punkte ausgebbar"
                else "Truhe, Ebenen-Punkte sind in dieser Freundschaft aus",
                hitPoint = MapPoint(.755f, .565f),
                hitRadius = .11f,
            )
            if (!epEnabled) sprites += SceneSprite(
                id = "cy-chest-lock", asset = FiefAssets.CHAIN_LOCK, anchor = MapPoint(.755f, .600f),
                widthFraction = .17f, z = 32, pivotY = .82f,
            )
        }
        if (Fief.availablePlans(built).isNotEmpty()) sprites += SceneSprite(
            id = "cy-build",
            asset = FiefAssets.BUILD_CORNER,
            anchor = MapPoint(.500f, .845f),
            widthFraction = .24f,
            z = 30,
            interactive = true,
            motion = Motion(MotionKind.SWAY, 3f, 6f, .65f),
            label = "Baustelle, offene Baupläne",
            hitPoint = MapPoint(.500f, .790f),
            hitRadius = .14f,
        )
        return Scene(
            "courtyard", FiefAssets.COURTYARD_PLATE, COURTYARD_IMAGE,
            sprites.sortedBy { it.z }, COURTYARD_MAX_ZOOM,
        )
    }

    // -----------------------------------------------------------------------
    // E2b: the visit to the other side
    // -----------------------------------------------------------------------

    /**
     * The read-only visit to the friend's court.
     *
     * Everything here is derived from two things I already know honestly: the EP
     * they accepted from me (their hut stage) and my own letters to them. Their
     * local build ledger, their other friendships and their own post are not
     * inputs and cannot be — which is exactly what the sign in this scene says.
     * Nothing in this scene is interactive: a visit looks, it never acts.
     */
    fun friendCourtScene(vale: Vale): Scene {
        val sprites = mutableListOf<SceneSprite>(
            SceneSprite(
                id = "fc-hut", asset = Fief.hutStages[vale.theirHutStage], anchor = MapPoint(.505f, .285f),
                widthFraction = .32f, z = 14,
            ),
            SceneSprite(
                id = "fc-board", asset = FiefAssets.BOARD, anchor = MapPoint(.225f, .620f),
                widthFraction = .19f, z = 30,
                label = "Tafel von ${vale.friendName}",
                hitPoint = MapPoint(.225f, .565f), hitRadius = .12f,
            ),
        )
        if (vale.theirHutStage >= 2) sprites += SceneSprite(
            id = "fc-tower", asset = FiefAssets.TOWER, anchor = MapPoint(.125f, .355f), widthFraction = .13f, z = 12,
        )
        return Scene(
            "friend-court", FiefAssets.COURTYARD_PLATE, COURTYARD_IMAGE,
            sprites.sortedBy { it.z }, COURTYARD_MAX_ZOOM,
        )
    }

    /**
     * The honest count on the sign of a visited court: my letters that are with
     * them right now. Locked ones are still on the road, released ones arrived.
     * Their incoming post from anyone else is none of my business and absent here.
     */
    fun visitStatus(letters: List<ApiClient.Message>): PostStatus {
        val mine = letters.filterNot { it.incoming }
        return PostStatus(total = mine.size, ready = mine.count { it.unlocked && it.readAt == null }, awaitingMe = 0)
    }

    // -----------------------------------------------------------------------
    // E3a: the board
    // -----------------------------------------------------------------------

    const val BOARD_ROWS = 4
    private val BOARD_COLUMN_X = listOf(.295f, .705f)
    private const val BOARD_TOP = .285f
    private const val BOARD_ROW_STEP = .100f

    /** Envelope image per letter state; incoming hangs left, outgoing right. */
    fun envelopeAsset(state: LetterState, incoming: Boolean): String = when (state) {
        LetterState.READY -> FiefAssets.ENV_READY
        LetterState.OPENED -> FiefAssets.ENV_OPEN
        LetterState.DELIVERED, LetterState.READ -> FiefAssets.ENV_OUT
        LetterState.LOCKED_MANUAL -> if (incoming) FiefAssets.ENV_SEALED else FiefAssets.ENV_OUT
        else -> FiefAssets.ENV_SEALED
    }

    /** The little paper tag at the nail. One or two characters, never a sentence. */
    fun envelopeTag(state: LetterState, remainingSeconds: Long): String? = when (state) {
        LetterState.LOCKED_TIMED -> shortRemaining(remainingSeconds)
        LetterState.LOCKED_RANDOM -> "?"
        LetterState.LOCKED_PRESENCE -> "● ●"
        LetterState.LOCKED_MUTUAL_WAITING_PEER -> "✓ –"
        LetterState.LOCKED_MUTUAL_WAITING_ME -> "✓?"
        LetterState.LOCKED_MANUAL -> "☝"
        LetterState.DELIVERED -> "✓"
        LetterState.READ -> "✓✓"
        LetterState.READY, LetterState.OPENED -> null
    }

    /**
     * The board as turnable pages of nails, in bucket order, newest first per
     * column: incoming hangs left, outgoing right, four nails per column.
     *
     * Every letter of the friendship hangs on exactly one page, so nothing is
     * ever cut off: page one carries what asks for attention, turning pages
     * walks back through the older post. The drawer below the board still opens
     * the same letters as a sorted list.
     */
    fun boardPages(
        letters: List<ApiClient.Message>,
        openedLetterIds: Set<Long>,
        now: Long,
        epOpportunityLetterIds: Set<Long>,
    ): List<List<BoardSlot>> {
        val ordered = letters.sortedWith(
            compareBy<ApiClient.Message> { Conversations.letterBucket(it, it.id in openedLetterIds).ordinal }
                .thenByDescending { it.createdAt }.thenByDescending { it.id },
        )
        val columns = listOf(ordered.filter { it.incoming }, ordered.filterNot { it.incoming })
        val pageCount = columns.maxOf { (it.size + BOARD_ROWS - 1) / BOARD_ROWS }
        return (0 until pageCount).map { page ->
            buildList {
                columns.forEachIndexed { column, columnLetters ->
                    columnLetters.drop(page * BOARD_ROWS).take(BOARD_ROWS).forEachIndexed { row, message ->
                        add(boardSlot(message, column, row, openedLetterIds, now, epOpportunityLetterIds))
                    }
                }
            }
        }
    }

    private fun boardSlot(
        message: ApiClient.Message,
        column: Int,
        row: Int,
        openedLetterIds: Set<Long>,
        now: Long,
        epOpportunityLetterIds: Set<Long>,
    ): BoardSlot {
        val state = Conversations.letterState(message, message.id in openedLetterIds)
        val remaining = max(0L, (message.releaseAt ?: now) - now)
        val tag = envelopeTag(state, remaining)
        return BoardSlot(
            letterId = message.id,
            envelope = envelopeAsset(state, message.incoming),
            tag = tag,
            anchor = MapPoint(BOARD_COLUMN_X[column], BOARD_TOP + row * BOARD_ROW_STEP),
            incoming = message.incoming,
            bucket = Conversations.letterBucket(message, message.id in openedLetterIds),
            coinTag = message.id in epOpportunityLetterIds,
            label = boardLabel(message, state, tag),
        )
    }

    private fun boardLabel(message: ApiClient.Message, state: LetterState, tag: String?): String {
        val title = message.title.ifBlank { Conversations.SEALED_FALLBACK }
        val direction = if (message.incoming) "von ${message.peerName}" else "an ${message.peerName}"
        val situation = when {
            state == LetterState.READY -> "bereit"
            state == LetterState.OPENED -> "geöffnet"
            state.locked -> "versiegelt${tag?.let { ", $it" } ?: ""}"
            else -> "zugestellt"
        }
        return "$title, $direction, $situation"
    }

    // -----------------------------------------------------------------------
    // E3b: the chest
    // -----------------------------------------------------------------------

    private const val TREASURY_COIN_COLUMNS = 6
    private val TREASURY_COIN_ORIGIN = MapPoint(.415f, .508f)
    private const val TREASURY_COIN_STEP_X = .060f
    private const val TREASURY_COIN_STEP_Y = .017f

    /** One coin per spendable EP, physically stacked inside the chest. */
    fun treasuryCoins(balance: Int): List<MapPoint> {
        val coins = balance.coerceIn(0, Fief.TOTAL_COST)
        return (0 until coins).map { index ->
            val column = index % TREASURY_COIN_COLUMNS
            val row = index / TREASURY_COIN_COLUMNS
            MapPoint(
                TREASURY_COIN_ORIGIN.x + column * TREASURY_COIN_STEP_X,
                TREASURY_COIN_ORIGIN.y + row * TREASURY_COIN_STEP_Y,
            )
        }
    }

    /** How many coins fall into the chest on this visit. Never negative. */
    fun coinDropCount(earnedEp: Int, lastSeenEarned: Int): Int = (earnedEp - lastSeenEarned).coerceAtLeast(0)

    // -----------------------------------------------------------------------
    // E3c: the build site
    // -----------------------------------------------------------------------

    private val PLAN_ANCHORS = listOf(MapPoint(.635f, .190f), MapPoint(.635f, .455f))

    /**
     * At most two plans, pinned in strand order. Costs are coins, never digits.
     *
     * Creative mode drops only the price: the chain order and the bilateral
     * EP gate stay exactly as they are.
     */
    fun buildPlans(built: Set<BuildStep>, earnedEp: Int, epEnabled: Boolean, free: Boolean = false): List<PlanSlot> =
        Fief.availablePlans(built).take(PLAN_ANCHORS.size).mapIndexed { index, step ->
            PlanSlot(
                step = step,
                anchor = PLAN_ANCHORS[index],
                preview = step.sprite,
                coins = step.cost,
                affordable = epEnabled && (free || Fief.affordable(built, step, earnedEp)),
                locked = !epEnabled,
                free = free && epEnabled,
            )
        }
}

/**
 * Fitted-plate geometry shared by Compose and the JVM tests.
 *
 * The plate is letterboxed into the viewport, then scaled and panned as one
 * layer, so a sprite's screen position and its touch area follow from the same
 * two functions the renderer uses.
 */
object FiefMap {
    const val MIN_ZOOM = 1f

    fun fittedSize(viewport: MapSize, image: MapSize): MapSize {
        if (viewport.width <= 0f || viewport.height <= 0f) return MapSize(0f, 0f)
        val factor = min(viewport.width / image.width, viewport.height / image.height)
        return MapSize(image.width * factor, image.height * factor)
    }

    fun clampPan(viewport: MapSize, image: MapSize, zoom: Float, pan: MapPoint): MapPoint {
        val fitted = fittedSize(viewport, image)
        val maxX = max(0f, (fitted.width * zoom - viewport.width) / 2f)
        val maxY = max(0f, (fitted.height * zoom - viewport.height) / 2f)
        return MapPoint(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
    }

    fun screenPoint(point: MapPoint, viewport: MapSize, image: MapSize, zoom: Float, pan: MapPoint): MapPoint {
        val fitted = fittedSize(viewport, image)
        val left = (viewport.width - fitted.width * zoom) / 2f + pan.x
        val top = (viewport.height - fitted.height * zoom) / 2f + pan.y
        return MapPoint(left + point.x * fitted.width * zoom, top + point.y * fitted.height * zoom)
    }

    /** Pan that puts [point] into the middle of the viewport at [zoom]. */
    fun focusPan(point: MapPoint, viewport: MapSize, image: MapSize, zoom: Float): MapPoint {
        val unpanned = screenPoint(point, viewport, image, zoom, MapPoint(0f, 0f))
        return clampPan(
            viewport, image, zoom,
            MapPoint(viewport.width / 2f - unpanned.x, viewport.height / 2f - unpanned.y),
        )
    }

    /**
     * The usable sprite under a finger.
     *
     * Decoration is not even a candidate, so a tap on a field or a fence starts a
     * pan instead of opening an empty sheet. A small sprite keeps a comfortable
     * touch area at low zoom: the hit circle grows, the picture never does.
     */
    fun hitTest(
        screen: MapPoint,
        viewport: MapSize,
        image: MapSize,
        zoom: Float,
        pan: MapPoint,
        sprites: List<SceneSprite>,
        minTouchPx: Float,
    ): SceneSprite? {
        val fitted = fittedSize(viewport, image)
        if (fitted.width <= 0f) return null
        return sprites.filter { it.interactive }
            .map { sprite ->
                val center = screenPoint(sprite.hitPoint, viewport, image, zoom, pan)
                val radius = max(sprite.hitRadius * fitted.width * zoom, minTouchPx / 2f)
                Triple(sprite, hypot(screen.x - center.x, screen.y - center.y), radius)
            }
            .filter { (_, distance, radius) -> distance <= radius }
            .sortedWith(compareByDescending<Triple<SceneSprite, Float, Float>> { it.first.z }.thenBy { it.second })
            .firstOrNull()?.first
    }
}
