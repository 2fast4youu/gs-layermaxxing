package at.gregor.layermaxxing

/**
 * "Das Lehen": the local, device-only build economy of the valley experiment.
 *
 * One authored chain of twelve discrete steps in two strands replaces the old
 * five buildings with three invisible levels each. Every step is exactly one
 * sprite or layer swap on the valley or the courtyard, so nothing here can ever
 * claim a visual change that does not happen.
 *
 * This file is pure Kotlin: no Android, no Compose, no org.json.
 */

enum class Track { HOF, LAND }

/**
 * The authorized build chain, in chain order.
 *
 * Costs sum to exactly 18 EP and no single step costs more than 3, so the first
 * meaningful purchase happens after a single thanked letter and the whole chain
 * still lasts for months at roughly one EP per letter.
 */
enum class BuildStep(
    val track: Track,
    val cost: Int,
    val title: String,
    /** The sprite this step becomes. [FiefAssets.PATH_OVERLAY] is drawn, not an image. */
    val sprite: String,
    /** One honest line: what visibly changes where. Shown before building. */
    val effect: String,
) {
    WELL(Track.HOF, 1, "Brunnen", FiefAssets.WELL, "Ein Brunnen erscheint in deinem Hof."),
    PATH(Track.LAND, 1, "Weg", FiefAssets.PATH_OVERLAY, "Der Weg durchs Tal wird befestigt."),
    ROOF(Track.HOF, 1, "Schindeldach", FiefAssets.HUT_STAGE1, "Deine Hütte bekommt ein Schindeldach."),
    JETTY(Track.LAND, 1, "Steg", FiefAssets.JETTY, "Ein Steg führt unten an den Fluss."),
    FENCE(Track.HOF, 1, "Zaun", FiefAssets.YARD_RING, "Ein Zaun fasst deinen Hof ein."),
    FIELD(Track.LAND, 1, "Feld", FiefAssets.FIELD, "Ein Feld wird unterhalb des Hofs angelegt."),
    BARN(Track.HOF, 2, "Stall", FiefAssets.BARN, "Ein Stall entsteht neben der Hütte."),
    ORCHARD(Track.LAND, 1, "Obstbäume", FiefAssets.ORCHARD, "Obstbäume wachsen am Weg ins Tal."),
    STONEWORK(Track.HOF, 2, "Steinsockel", FiefAssets.HUT_STAGE2, "Deine Hütte bekommt einen Steinsockel."),
    TOWER(Track.HOF, 2, "Türmchen", FiefAssets.TOWER, "Ein Türmchen bewacht fortan deinen Hof."),
    BRIDGE(Track.LAND, 2, "Brücke", FiefAssets.BRIDGE, "Eine Brücke quert den Fluss im Tal."),
    KEEP(Track.HOF, 3, "Kleine Feste", FiefAssets.HUT_STAGE3, "Deine Hütte wird zur kleinen Feste."),
}

object Fief {
    /** Full cost of the chain. Deliberately small: the valley is a slow place. */
    const val TOTAL_COST = 18

    val chain: List<BuildStep> = BuildStep.entries.toList()

    /** The huts, indexed by stage. Stage 0 is the patched log hut of day one. */
    val hutStages: List<String> =
        listOf(FiefAssets.HUT_STAGE0, FiefAssets.HUT_STAGE1, FiefAssets.HUT_STAGE2, FiefAssets.HUT_STAGE3)

    /** At most two plans: the next open step of each strand. */
    fun availablePlans(built: Set<BuildStep>): List<BuildStep> =
        Track.entries.mapNotNull { track -> chain.firstOrNull { it.track == track && it !in built } }

    fun spent(built: Set<BuildStep>): Int = built.sumOf { it.cost }

    /** Spendable coins. Clamped at zero, so a legacy ledger can never owe EP. */
    fun balance(earnedEp: Int, built: Set<BuildStep>): Int = (earnedEp - spent(built)).coerceAtLeast(0)

    fun affordable(built: Set<BuildStep>, step: BuildStep, earnedEp: Int): Boolean =
        step !in built && step in availablePlans(built) && balance(earnedEp, built) >= step.cost

    /** Returns the unchanged set when a step is unaffordable, already built or skipped. */
    fun build(built: Set<BuildStep>, step: BuildStep, earnedEp: Int): Set<BuildStep> =
        if (affordable(built, step, earnedEp)) built + step else built

    /**
     * Creative mode: the chain order still holds, the price does not.
     *
     * Free builds live in their own device-local ledger and never touch the
     * earned-EP ledger, so they can neither overdraw anything nor pose as
     * real progress once the mode is switched off.
     */
    fun buildFree(built: Set<BuildStep>, step: BuildStep): Set<BuildStep> =
        if (step in availablePlans(built)) built + step else built

    /** 0..3, derived from the three hut-changing steps. Never falls back. */
    fun hutStage(built: Set<BuildStep>): Int = when {
        BuildStep.KEEP in built -> 3
        BuildStep.STONEWORK in built -> 2
        BuildStep.ROOF in built -> 1
        else -> 0
    }

    /**
     * The other side grows honestly: from the EP they accepted from me, nothing else.
     *
     * Their local build ledger lives on their device and is unknowable here, which
     * is exactly what the plaque in the valley says.
     */
    fun friendHutStage(theirEarnedEp: Int): Int = when {
        theirEarnedEp >= 14 -> 3
        theirEarnedEp >= 8 -> 2
        theirEarnedEp >= 3 -> 1
        else -> 0
    }

    /**
     * Turns a v0/v1 building ledger into chain steps without losing a single EP.
     *
     * The old levels bought pixels that no longer exist, so their credit is spent
     * on the longest chain prefix it covers and the remainder flows back into the
     * treasury. Nothing built disappears, nothing goes negative.
     */
    fun migrateLegacy(levels: Map<Building, Int>): Set<BuildStep> {
        val credit = Castles.spentFor(levels).coerceAtMost(TOTAL_COST)
        var remaining = credit
        val result = LinkedHashSet<BuildStep>()
        for (step in chain) {
            if (step.cost > remaining) break
            remaining -= step.cost
            result += step
        }
        return result
    }

    /**
     * The deterministic order in which the once-per-scene light sweep touches the
     * usable objects — and the traversal order for accessibility. Replaces the
     * old pulsing tutorial marker and its text chip.
     */
    fun glintOrder(scene: Scene): List<String> = scene.sprites
        .filter { it.interactive }
        .sortedWith(compareBy({ it.anchor.y }, { it.anchor.x }, { it.id }))
        .map { it.id }
}

/** Short countdown for a paper tag on the board: "3 T", "2 Std", "14 Min". */
fun shortRemaining(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val days = safe / 86400
    val hours = safe % 86400 / 3600
    val minutes = safe % 3600 / 60
    return when {
        days > 0 -> "$days T"
        hours > 0 -> "$hours Std"
        minutes > 0 -> "$minutes Min"
        else -> "gleich"
    }
}
