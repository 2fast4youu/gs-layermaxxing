package at.gregor.layermaxxing

import kotlin.math.roundToInt

/** What is being placed or edited on the own island. */
internal sealed interface IslandTarget {
    data class Building(val building: IsleBuilding) : IslandTarget
    /** [id] null = a new object from the build bar. */
    data class Decor(val item: String, val id: Int? = null) : IslandTarget
}

/**
 * A local, unsaved placement: [lifted] = long-press edit of something already standing. [rejected] = cells the
 * server refused for this draft although the app's rule allowed them (7.9.11): they are never offered as green
 * targets again, so the same spot is not confirmed over and over.
 */
internal data class IslandDraft(val target: IslandTarget, val position: List<Int>, val lifted: Boolean = false,
    val rejected: Set<List<Int>> = emptySet(),
    /**
     * 7.9.13: the held building is its NEXT stage (an upgrade whose bigger ground does not fit where it stands, e.g. the
     * rowboat on the beach that becomes a pier at the water): the targets are that stage's spots and ✓ writes one atomic
     * "upgrade" with the chosen position, checked by the server before anything is paid.
     */
    val upgrade: Boolean = false)

/** 7.9.11: a write refused with 409 (the island changed meanwhile, e.g. on another device): reloaded, never retried. */
internal const val ISLAND_CONFLICT = "Die Insel wurde inzwischen auf einem anderen Gerät geändert. Stand neu geladen – bitte den Platz prüfen."

/** The rings of one placement version per land stage 0..5. */
internal data class FootprintRings(val big: List<Int>, val starter: List<Int>, val decor: List<Int>)

/**
 * THE footprint shape, the same as the server's (server/app/main.py footprint_cells, pinned in
 * server/app/island_grid.json). A piece occupies the grid cells of its footprint: a diamond of
 * |di| + |dj| <= rings whole grid cells whose FRONT tip is its spot (where the sprite's foot stands, the
 * building's base lies behind it), drawn exactly like that while moving. Two pieces never share a cell; there
 * is no other clearance. How many rings depends on the server's rule ([PlacementRule]).
 */
internal object IslandFootprint {
    /** grid_version 3 (7.9.10): function buildings / special decoration, stage-0 pieces, decoration. */
    val V3 = FootprintRings(listOf(2, 2, 1, 1, 1, 1), listOf(1, 1, 1, 1, 1, 1), listOf(1, 1, 1, 0, 0, 0))
    /**
     * grid_version 4 (7.9.11, server FOOTPRINT_RINGS): compact. On the big land (2..5) every piece holds one real
     * ground cell; only on the small land (0..1) a function building / special decoration keeps one ring, because
     * its sprite is several cells wide there. Never more than [V3], so no saved island gets worse.
     */
    val V4 = FootprintRings(listOf(1, 1, 0, 0, 0, 0), listOf(0, 0, 0, 0, 0, 0), listOf(0, 0, 0, 0, 0, 0))
    /** The middle of the footprint diamond of a piece standing at [spot]: [rings] cells further up the lattice. */
    fun middle(spot: List<Int>, rings: Int) = listOf(spot[0], spot[1] - rings)
    fun cells(spot: List<Int>, rings: Int): Set<List<Int>> = buildSet {
        val (cx, cy) = middle(spot, rings)
        for (dx in -rings..rings) for (dy in -rings..rings) if (kotlin.math.abs(dx) + kotlin.math.abs(dy) <= rings) add(listOf(cx + dx, cy + dy))
    }
    /** Do two footprints share a cell? (Equivalent to comparing the cell sets: their middles are close enough.) */
    fun overlap(p: List<Int>, rp: Int, q: List<Int>, rq: Int): Boolean {
        val a = middle(p, rp); val b = middle(q, rq)
        return kotlin.math.abs(a[0] - b[0]) + kotlin.math.abs(a[1] - b[1]) <= rp + rq
    }
}

/**
 * 7.9.11: the placement rule is the SERVER's, declared per island (construction.grid_version, also in
 * /api/server-info). The app follows exactly that rule for the drawn targets, the green preview and ✓:
 *  - 2 = 7.9.7–7.9.9 servers: circular clearances 3 / 2.4 / 1.5 cells divided by the land factor, the stage-0
 *    special case (a held stage-0 piece keeps only the 2.4 gap to big pieces), no other rule;
 *  - 3 = 7.9.10 servers: the [IslandFootprint.V3] diamonds;
 *  - 4 = 7.9.11 servers: the compact [IslandFootprint.V4] diamonds;
 *  - 5 = 7.9.12 servers: the graphic masks ([IslandMasks]): a piece holds the cells its painted sprite covers;
 *  - 6 = 7.9.13 servers: the ground ([IslandGround]): a piece holds one rectangle, the plate it stands on, on the zone
 *    it belongs to (meadow; the harbour on the beach / at the water). Roofs may cover other pieces.
 *  - 7 = rule 6 plus LIGHTHOUSE ground touching the open-sea coast ([IslandEdge]); an existing building
 *    upgrading in its saved spot is checked for next-stage ground fit, with no new coast requirement.
 *  - 8 = each building retains its stage-0 ground on every upgrade; decoration/terrain/visual sizes unchanged.
 *    Saved-position upgrades preserve even legacy invalid/overlapping layouts; new placements/moves validate fully.
 * The harbour stands on the coast with every rule (a v2 server accepts more, never less). 7.9.12: under the older
 * rules 2–4 the app additionally checks the graphic fit ([IslandLayout.fitProblem]), so even on an old server no
 * new sprite is built into another one (it offers a subset of what that server accepts, never more). A payload without the
 * field ([LEGACY]) or an unknown number is never taken for the newest rule: nothing is green, nothing is written,
 * the hint says why ([problem]).
 */
internal object PlacementRule {
    const val LEGACY = 0
    const val CIRCLES = 2
    const val DIAMONDS = 3
    const val COMPACT = 4
    /** grid_version 5 (7.9.12): the graphic masks of [IslandMasks], the same file the server validates with. */
    const val MASKS = 5
    /** grid_version 6 (7.9.13): ground rectangles and terrain zones of [IslandGround], the same file as the server's. */
    const val GROUND = 6
    /** Rule 7: rule 6 plus LIGHTHOUSE ground touching the open-sea coast. */
    const val EDGE = 7
    // Default constructors and live server remain v7 until coordinated rollout.
    const val CURRENT = EDGE
    const val CONSTANT = 8
    const val NEWEST = CONSTANT
    fun known(version: Int) = version == CIRCLES || version == DIAMONDS || version == COMPACT || version == MASKS || version == GROUND || version == EDGE || version == CONSTANT
    fun rings(version: Int): FootprintRings? = when (version) {
        DIAMONDS -> IslandFootprint.V3
        COMPACT -> IslandFootprint.V4
        else -> null
    }
    /** A grid_version that is there but not a plain JSON integer (string, fraction, out of Int range, …). */
    const val INVALID = -1
    /** Why nothing can be placed under this rule (null = a rule the app knows). */
    fun problem(version: Int): String? = when {
        known(version) -> null
        version == LEGACY -> "Dieser Server nennt keine Bauregel – Platzieren gesperrt, bitte Server aktualisieren"
        version == INVALID -> "Ungültige Bauregel vom Server – Platzieren gesperrt"
        version > NEWEST -> "Der Server nutzt neuere Bauregeln (Version $version) – bitte die App aktualisieren"
        else -> "Unbekannte Bauregel (Version $version) – Platzieren gesperrt"
    }
    /**
     * The grid_version of [json]: null = missing or null; the contract is a JSON integer, so anything else (4.9, 4.0,
     * "4", an overflowing number, true) is [INVALID] and never coerced onto a known rule (7.9.11: optInt turned 4.9
     * and "4" into 4).
     */
    fun read(json: org.json.JSONObject): Int? =
        if (!json.has("grid_version") || json.isNull("grid_version")) null else (json.opt("grid_version") as? Int) ?: INVALID
    /** The construction block's grid_version; missing = [LEGACY], not an integer = [INVALID]. */
    fun parse(construction: org.json.JSONObject): Int = read(construction) ?: LEGACY

    /** Version 2 clearances in grid cells (server 7.9.7–7.9.9 placement_blocked; pinned in island_grid.json legacy_v2). */
    const val V2_BIG = 3.0
    const val V2_DECOR_BUILDING = 2.4
    const val V2_DECOR_DECOR = 1.5
    val LAND_FACTOR = listOf(.46, .58, .70, .82, .92, 1.0)
}

/**
 * Placement rules mirrored from the server (server/app/main.py placement_blocked / building_spot_error) for the
 * island's declared [PlacementRule]: every spot is a cell of the land's build grid ([IslandGrid.buildable]),
 * pieces never block each other ([blocked]), the harbour stands on the coast ([IslandGrid.coast]). [problem] is
 * the one decision; [targets] (drawn), [valid] (green, ✓) and [reason] (hint) all come from it.
 */
internal object IslandLayout {
    /** The drag/snap/preview projection lives in [IslandGrid]; this object holds the rules mirrored from the server. */
    fun defaultPosition(b: IsleBuilding): List<Int> = IslandPlans.homeBuildings.getValue(b).let { listOf((it.x*20).roundToInt(),(it.y*20).roundToInt()) }
    fun buildingPositions(c: ApiClient.IslandConstruction, except: IsleBuilding? = null) =
        IsleBuilding.entries.filter { it != except && c.standing(it) }.map { c.positions[it.name] ?: defaultPosition(it) }
    private fun land(c: ApiClient.IslandConstruction) = c.land.coerceIn(0, 5)
    private fun isStarter(c: ApiClient.IslandConstruction, b: IsleBuilding) = b.name in c.starters && (c.buildings[b.name] ?: 0) == 0
    /**
     * Rings of a standing (or stage-0) building and of a decoration item on this land, under the island's rule.
     * Version 2 has no footprint (circular clearances): every piece is drawn as its own cell.
     */
    fun buildingRings(c: ApiClient.IslandConstruction, b: IsleBuilding) =
        PlacementRule.rings(c.gridVersion)?.let { r -> (if (isStarter(c, b)) r.starter else r.big)[land(c)] } ?: 0
    fun decorRings(c: ApiClient.IslandConstruction, item: String) =
        PlacementRule.rings(c.gridVersion)?.let { r -> (if (SpecialDecor.isSpecial(item)) r.big else r.decor)[land(c)] } ?: 0
    /** Rings of the held piece: a building that does not stand yet is placed at its free stage 0 (7.9.9). */
    fun rings(c: ApiClient.IslandConstruction, t: IslandTarget) = when (t) {
        is IslandTarget.Building -> if (c.standing(t.building)) buildingRings(c, t.building)
            else PlacementRule.rings(c.gridVersion)?.starter?.get(land(c)) ?: 0
        is IslandTarget.Decor -> decorRings(c, t.item)
    }
    /** 7.9.12: the drawn stage of a building in this ledger (not standing yet = its free stage 0). */
    fun stage(c: ApiClient.IslandConstruction, b: IsleBuilding) = if (c.standing(b)) c.level(b).coerceIn(0, 3) else 0
    /** The [IslandMasks] key of a piece: what is drawn there (stage of a building, picture of a decoration). */
    fun key(c: ApiClient.IslandConstruction, t: IslandTarget): String = when (t) {
        is IslandTarget.Building -> IslandMasks.buildingKey(t.building, stage(c, t.building))
        is IslandTarget.Decor -> IslandMasks.decorKey(t.item)
    }
    /** Rule 6: the island's ground rule (7.9.13); before that the graphic masks of 7.9.12. */
    fun ground(c: ApiClient.IslandConstruction) = c.gridVersion in setOf(PlacementRule.GROUND, PlacementRule.EDGE, PlacementRule.CONSTANT)
    /**
     * The cells the piece occupies when it stands at [p]: rule 6 its ground rectangle (and a pier's head), every older rule
     * the cells its painted sprite covers (7.9.12, drawn and checked for those servers exactly as before).
     */
    fun cellsAt(c: ApiClient.IslandConstruction, t: IslandTarget, p: List<Int>): Set<List<Int>> =
        if (ground(c)) IslandGround.cells(key(c, t), land(c), p, c.gridVersion) else IslandMasks.cells(key(c, t), land(c), p)
    /**
     * One other piece while something is moved: its spot and the cells its painted sprite covers (outlined red).
     * [rings] = its footprint under the historic rules 3/4 (kept for those servers' exact rule, not drawn).
     */
    data class Obstacle(val target: IslandTarget, val center: List<Int>, val rings: Int, val cells: Set<List<Int>>)
    /** Every piece except [except] (the held one never blocks itself, so its old spot is free for it). */
    fun obstacles(c: ApiClient.IslandConstruction, except: IslandTarget?): List<Obstacle> =
        IsleBuilding.entries.filter { c.standing(it) && except != IslandTarget.Building(it) }
            .map { val t = IslandTarget.Building(it); val p = c.positions[it.name] ?: defaultPosition(it); Obstacle(t, p, buildingRings(c, it), cellsAt(c, t, p)) } +
        c.decor.filter { d -> !(except is IslandTarget.Decor && except.id != null && except.id == d.id) }
            .map { d -> val t = IslandTarget.Decor(d.item, d.id); Obstacle(t, d.position, decorRings(c, d.item), cellsAt(c, t, d.position)) }
    /** Would the held piece's painted sprite at [p] cover a cell of another piece's sprite? */
    fun maskBlocked(c: ApiClient.IslandConstruction, t: IslandTarget, p: List<Int>): Boolean {
        val mine = cellsAt(c, t, p)
        return obstacles(c, t).any { o -> o.cells.any { it in mine } }
    }
    /**
     * 7.9.12: the graphic fit of the held piece at [p]: its ground on the land (the harbour: coast with water in front,
     * pier on the land) and no shared cell with another piece's sprite. Under rule 5 this IS the server's rule; under
     * the older rules it is checked in addition, so the app never offers a spot where the pictures would overlap.
     */
    fun fitProblem(c: ApiClient.IslandConstruction, t: IslandTarget, p: List<Int>): String? {
        val held = if (t is IslandTarget.Building && !c.standing(t.building)) c.copy(starters = c.starters + t.building.name) else c
        val harbour = t == IslandTarget.Building(IsleBuilding.HARBOUR)
        (if (ground(c)) IslandGround.landProblem(key(held, t), land(c), p, harbour, c.gridVersion) else IslandMasks.landProblem(key(held, t), land(c), p, harbour))?.let { return it }
        return if (maskBlocked(held, t, p)) "Belegt – rot markierte Fläche" else null
    }
    /**
     * Pairs of pieces whose occupied cells already overlap (a layout saved under an older rule): shown, never moved on
     * their own. Rule 6 counts grounds only: roofs drawn over another piece are wanted, never reported.
     */
    fun overlaps(c: ApiClient.IslandConstruction): List<Pair<IslandTarget, IslandTarget>> {
        val all = obstacles(c, null)
        return all.indices.flatMap { i -> (i + 1 until all.size).filter { j -> all[i].cells.any { it in all[j].cells } }.map { j -> all[i].target to all[j].target } }
    }
    /**
     * Why the next stage of [b] cannot be built where it stands (null = it can): the bigger picture must fit there
     * (ground on the land, no other sprite inside) before anything is paid; otherwise the owner moves it first.
     */
    fun upgradeProblem(c: ApiClient.IslandConstruction, b: IsleBuilding): String? {
        if (!c.standing(b) || c.level(b) >= 3) return null
        if (c.gridVersion == PlacementRule.CONSTANT) return null
        val next = c.copy(buildings = c.buildings + (b.name to c.level(b) + 1), starters = c.starters - b.name)
        val spot = c.positions[b.name] ?: defaultPosition(b)
        val why = fitProblem(next, IslandTarget.Building(b), spot) ?: return null
        return "Für ${IslandStages.of(b, c.level(b) + 1).name} ist hier kein Platz ($why) – zuerst verschieben: Platz für die nächste Stufe wählen"
    }
    /** [c] with [b] at its next stage (what an upgrade draft holds and the preview draws). */
    fun upgraded(c: ApiClient.IslandConstruction, b: IsleBuilding): ApiClient.IslandConstruction =
        if (c.level(b) >= 3) c else c.copy(buildings = c.buildings + (b.name to c.level(b) + 1), starters = c.starters - b.name)
    /** The island as an upgrade draft sees it: the held building already at its next stage. */
    fun editing(h: ApiClient.HomeIsland, draft: IslandDraft?): ApiClient.HomeIsland {
        val b = (draft?.target as? IslandTarget.Building)?.building
        return if (draft?.upgrade == true && b != null) h.copy(construction = upgraded(h.construction, b)) else h
    }
    /**
     * Does another piece block [p]? [big] = the piece is a building or special decoration. The moved piece
     * ([ignoreBuilding] / [ignoreDecor]) never blocks itself. Under an unknown rule everything is blocked.
     */
    fun blocked(c: ApiClient.IslandConstruction, p: List<Int>, big: Boolean, ignoreBuilding: IsleBuilding? = null, ignoreDecor: Int? = null): Boolean {
        if (!PlacementRule.known(c.gridVersion)) return true
        // 7.9.9 (server placement_blocked): a held stage-0 piece is small.
        val small = ignoreBuilding != null && ignoreBuilding.name in c.starters && (c.buildings[ignoreBuilding.name] ?: 0) == 0
        if (c.gridVersion == PlacementRule.CIRCLES) return blockedByCircles(c, p, big, small, ignoreBuilding, ignoreDecor)
        // rules 3/4 (rule 5 is decided by the masks in [ruleProblem], never here)
        val r = PlacementRule.rings(c.gridVersion) ?: return true
        val mine = (if (!big) r.decor else if (small) r.starter else r.big)[land(c)]
        return obstacles(c, null).any { o ->
            val own = when (val t = o.target) {
                is IslandTarget.Building -> t.building == ignoreBuilding
                is IslandTarget.Decor -> ignoreDecor != null && t.id == ignoreDecor
            }
            !own && IslandFootprint.overlap(p, mine, o.center, o.rings)
        }
    }
    /** Version 2, exactly the 7.9.7–7.9.9 server: squared distances against clearances / land factor. */
    private fun blockedByCircles(c: ApiClient.IslandConstruction, p: List<Int>, big: Boolean, small: Boolean,
                                 ignoreBuilding: IsleBuilding?, ignoreDecor: Int?): Boolean {
        val f = PlacementRule.LAND_FACTOR[land(c)]
        fun sq(d: Double) = (d / f) * (d / f)
        val gapBig = sq(PlacementRule.V2_BIG); val gapB = sq(PlacementRule.V2_DECOR_BUILDING); val gapD = sq(PlacementRule.V2_DECOR_DECOR)
        fun dist(q: List<Int>) = ((p[0] - q[0]) * (p[0] - q[0]) + (p[1] - q[1]) * (p[1] - q[1])).toDouble()
        for (b in IsleBuilding.entries) if (b != ignoreBuilding && c.standing(b)) {
            val gap = if (!big || small || b.name in c.starters) gapB else gapBig
            if (dist(c.positions[b.name] ?: defaultPosition(b)) < gap) return true
        }
        for (d in c.decor) if (d.id != ignoreDecor) {
            val gap = if (SpecialDecor.isSpecial(d.item)) (if (small) gapB else if (big) gapBig else gapB) else (if (big) gapB else gapD)
            if (dist(d.position) < gap) return true
        }
        return false
    }
    /** A building not standing yet is placed at its free stage 0 (7.9.9), so it has the small footprint. */
    fun valid(c: ApiClient.IslandConstruction, b: IsleBuilding, p: List<Int>): Boolean = problem(c, IslandTarget.Building(b), p) == null
    /**
     * A decoration at [p]: [item] = the new item, or (null) the moved object's own item; unknown = the signpost of an
     * unknown (special) item, like the server counts it.
     */
    fun validDecor(c: ApiClient.IslandConstruction, p: List<Int>, ignoreId: Int? = null, special: Boolean = false, item: String? = null): Boolean =
        problem(c, IslandTarget.Decor(decorItem(c, ignoreId, special, item), ignoreId), p) == null
    private fun decorItem(c: ApiClient.IslandConstruction, id: Int?, special: Boolean, item: String?) =
        item ?: c.decor.firstOrNull { it.id == id && id != null }?.item ?: if (special) "place_?" else "?"
    /** The exact rule of the server's declared version for a decoration (historic rules 2–4 without the graphic fit). */
    fun ruleValidDecor(c: ApiClient.IslandConstruction, p: List<Int>, ignoreId: Int? = null, special: Boolean = false, item: String? = null): Boolean =
        ruleProblem(c, IslandTarget.Decor(decorItem(c, ignoreId, special, item), ignoreId), p) == null
    fun ruleValid(c: ApiClient.IslandConstruction, b: IsleBuilding, p: List<Int>): Boolean = ruleProblem(c, IslandTarget.Building(b), p) == null
    fun valid(h: ApiClient.HomeIsland, draft: IslandDraft): Boolean = reason(h, draft) == null
    /** THE decision for one cell: why the held piece cannot stand at [p] (null = it can, and only then it is drawn). */
    fun problem(c: ApiClient.IslandConstruction, t: IslandTarget, p: List<Int>, rejected: Set<List<Int>> = emptySet()): String? {
        ruleProblem(c, t, p)?.let { return it }
        // rules 5 and 6 are the fit itself; the older rules get the graphic fit in addition (a subset of what they accept)
        if (c.gridVersion < PlacementRule.MASKS) fitProblem(c, t, p)?.let { return it }
        if (p in rejected) return REJECTED
        return null
    }
    /** The server's rule of the declared version, exactly (rules 2–4 historic, 5 = the masks). */
    fun ruleProblem(c: ApiClient.IslandConstruction, t: IslandTarget, p: List<Int>): String? {
        PlacementRule.problem(c.gridVersion)?.let { return it }
        // rule 6: the ground on its zone (the harbour on the beach / at the water, outside the meadow grid) and no shared cell
        if (ground(c)) return fitProblem(c, t, p) ?: IslandEdge.problem(c, t, p)
        if (!IslandGrid.buildable(p, c.land)) return "Außerhalb der Insel"
        if (t == IslandTarget.Building(IsleBuilding.HARBOUR) && !IslandGrid.coast(p, c.land)) return "Hafen nur an der Küste"
        if (c.gridVersion == PlacementRule.MASKS) return fitProblem(c, t, p)
        val free = when (t) {
            is IslandTarget.Building -> {
                if (t.building == IsleBuilding.HARBOUR && !IslandGrid.coast(p, c.land)) return "Hafen nur an der Küste"
                !blocked(if (c.standing(t.building)) c else c.copy(starters = c.starters + t.building.name), p, big = true, ignoreBuilding = t.building)
            }
            is IslandTarget.Decor -> !blocked(c, p, big = SpecialDecor.isSpecial(t.item), ignoreDecor = t.id)
        }
        if (!free) return "Belegt – rot markierte Fläche"
        return null
    }
    /**
     * 7.9.11: the existing piece this draft moves is no longer on the island (removed meanwhile, e.g. on another
     * device): a decoration with an id that is not in the ledger, or a lifted building that no longer stands.
     * Such a draft is never green and never written (a "move" must not turn into a new "build").
     */
    fun gone(c: ApiClient.IslandConstruction, draft: IslandDraft): Boolean = when (val t = draft.target) {
        is IslandTarget.Building -> draft.lifted && !c.standing(t.building)
        is IslandTarget.Decor -> t.id != null && c.decor.none { it.id == t.id }
    }
    const val GONE = "Dieses Objekt wurde inzwischen entfernt – Verschieben beendet, Stand neu geladen"
    // Draft APIs take the ORIGINAL ledger. Only geometry/rendering sees the next stage; positions
    // in a preview must never become the authority for the same-position upgrade exception.
    /** Original saved island + action-aware draft; null = green and confirmable. */
    fun reason(h: ApiClient.HomeIsland, draft: IslandDraft): String? = draftProblem(h.construction, draft)
    private fun draftProblem(c: ApiClient.IslandConstruction, draft: IslandDraft): String? {
        PlacementRule.problem(c.gridVersion)?.let { return it }
        if (gone(c, draft)) return GONE
        val b = (draft.target as? IslandTarget.Building)?.building
        val held = if (draft.upgrade && b != null) upgraded(c, b) else c
        val sameUpgrade = draft.upgrade && b != null && c.standing(b) &&
            draft.position == (c.positions[b.name] ?: defaultPosition(b))
        val why = if (sameUpgrade && c.gridVersion == PlacementRule.CONSTANT) null
            else if (sameUpgrade && c.gridVersion == PlacementRule.EDGE) fitProblem(held, draft.target, draft.position)
            else problem(held, draft.target, draft.position)
        return why ?: if (draft.position in draft.rejected) REJECTED else null
    }
    /** The cells drawn for a draft, including ONLY a standing building's same-position upgrade exception. */
    fun targets(c: ApiClient.IslandConstruction, draft: IslandDraft): List<List<Int>> {
        val b = (draft.target as? IslandTarget.Building)?.building
        val held = if (draft.upgrade && b != null) upgraded(c, b) else c
        if (!PlacementRule.known(c.gridVersion) || gone(c, draft)) return emptyList()
        return candidates(held, draft.target).filter { draftProblem(c, draft.copy(position = it)) == null }
    }
    /** Validate canonical actions too: a move even to its saved position is still a move. */
    fun requestProblem(c: ApiClient.IslandConstruction, request: ApiClient.IslandRequest): String? {
        PlacementRule.problem(c.gridVersion)?.let { return it }
        val b = request.building?.let { name -> IsleBuilding.entries.firstOrNull { it.name == name } }
        val t = when (request.action) {
            "build", "move", "upgrade" -> b?.let { IslandTarget.Building(it) } ?: return "Ungültiger Bauplatz"
            "decor_place" -> IslandTarget.Decor(request.item ?: "?")
            "decor_move" -> c.decor.firstOrNull { it.id == request.decorId }?.let { IslandTarget.Decor(it.item, it.id) } ?: return GONE
            else -> return null
        }
        val p = request.position ?: b?.let { c.positions[it.name] ?: defaultPosition(it) } ?: return "Ungültiger Bauplatz"
        return draftProblem(c, IslandDraft(t, p, lifted = request.action == "move" || request.action == "decor_move",
            upgrade = request.action == "upgrade"))
    }
    const val REJECTED = "Server lehnt diesen Platz ab – Stand neu geladen"
    /**
     * Every cell the held piece may really stand on = exactly the cells the grid draws (7.9.11): for the harbour
     * only free coast cells, for everything else every free buildable cell. Nothing else is drawn as usable.
     */
    fun targets(c: ApiClient.IslandConstruction, t: IslandTarget, rejected: Set<List<Int>> = emptySet()): List<List<Int>> {
        if (!PlacementRule.known(c.gridVersion)) return emptyList()
        return candidates(c, t).filter { problem(c, t, it, rejected) == null }
    }
    /** The spots the server's declared rule accepts, exactly (without the app's additional graphic fit). */
    fun ruleTargets(c: ApiClient.IslandConstruction, t: IslandTarget): List<List<Int>> {
        if (!PlacementRule.known(c.gridVersion)) return emptyList()
        return candidates(c, t).filter { ruleProblem(c, t, it) == null }
    }
    /** Where the held piece may be looked for: rule 6 the meadow (the harbour: every lattice cell), before the build grid / coast. */
    private fun candidates(c: ApiClient.IslandConstruction, t: IslandTarget): List<List<Int>> {
        val harbour = t == IslandTarget.Building(IsleBuilding.HARBOUR)
        return if (ground(c)) IslandGround.spots(land(c), harbour) else if (harbour) IslandGrid.coastCells(c.land) else IslandGrid.buildableCells(c.land)
    }
    /**
     * Nearest free cell to [start] (ring search over the whole 0..20 grid, 7.9.10: 7.9.9 stopped after 10 rings and
     * missed a free spot on the far side, e.g. the last free coast cell), so a new object never starts on a red
     * square. Nothing free: [start] itself, which [ok] refuses, so the preview is red and ✓ stays disabled.
     */
    fun freeCell(start: List<Int>, ok: (List<Int>) -> Boolean): List<Int> {
        if (ok(start)) return start
        for (r in 1..IslandGrid.STEPS) for (dx in -r..r) for (dy in -r..r) {
            if (kotlin.math.max(kotlin.math.abs(dx), kotlin.math.abs(dy)) != r) continue
            val p = listOf(start[0]+dx, start[1]+dy)
            if (ok(p)) return p
        }
        return start
    }
    /**
     * Where a building starts when it is picked up ([viewCentre] = the cell in the middle of the screen). A standing
     * one starts on its spot, a new one on the nearest free cell (stage 0). 7.9.10: the harbour always starts on the
     * coast, also one that still stands inland from old data (only this local draft moves; the server keeps the old
     * spot until ✓). A taken coast cell is kept as it is: the preview is red, nothing jumps elsewhere on its own.
     */
    fun startCell(c: ApiClient.IslandConstruction, b: IsleBuilding, viewCentre: List<Int>?): List<Int> {
        val built = c.standing(b)
        val stored = c.positions[b.name] ?: if (built) defaultPosition(b) else IslandGrid.clampToLand(viewCentre ?: defaultPosition(b), land(c))
        // 7.9.12: the harbour starts on the nearest coast spot where its pier fits (water in front)
        val start = if (b == IsleBuilding.HARBOUR) harbourCellNear(c, stored) else stored
        return if (built) start else freeCell(start) { valid(c, b, it) }
    }
    /**
     * 7.9.13: where an upgrade draft of [b] starts: where it stands if its next stage fits there, else the free spot of
     * that stage nearest to it on screen (the rowboat's pier at the water right in front of it). Nothing free: where it
     * stands (red, ✓ disabled).
     */
    fun upgradeStart(c: ApiClient.IslandConstruction, b: IsleBuilding): List<Int> {
        val next = upgraded(c, b)
        val here = c.positions[b.name] ?: defaultPosition(b)
        if (c.standing(b) && draftProblem(c, IslandDraft(IslandTarget.Building(b), here, upgrade = true)) == null) return here
        val spots = targets(next, IslandTarget.Building(b)).ifEmpty { return here }
        val (x, y) = IslandGrid.project(listOf(here[0].coerceIn(0, IslandGrid.STEPS), here[1].coerceIn(0, IslandGrid.STEPS)), land(c))
        return IslandGrid.nearestOf(spots, x, y, land(c))
    }
    /**
     * 7.9.12: every cell where the held piece's picture fits the land (other pieces ignored): where it snaps to while
     * dragged, so it never hangs over the shore. The harbour: its pier spots ([harbourSpots]).
     */
    fun fitSpots(c: ApiClient.IslandConstruction, t: IslandTarget): List<List<Int>> {
        if (!PlacementRule.known(c.gridVersion)) return emptyList()
        if (t == IslandTarget.Building(IsleBuilding.HARBOUR)) return harbourSpots(c)
        val held = if (t is IslandTarget.Building && !c.standing(t.building)) c.copy(starters = c.starters + t.building.name) else c
        val k = key(held, t)
        if (ground(c)) return IslandGround.spots(land(c), false).filter {
            IslandGround.landProblem(k, land(c), it, harbour = false, version = c.gridVersion) == null && IslandEdge.problem(held, t, it) == null
        }
        return IslandGrid.buildableCells(land(c)).filter { IslandMasks.landProblem(k, land(c), it, harbour = false) == null }.ifEmpty { IslandGrid.buildableCells(land(c)) }
    }
    fun fitSpots(c: ApiClient.IslandConstruction, draft: IslandDraft): List<List<Int>> {
        val b = (draft.target as? IslandTarget.Building)?.building
        val held = if (draft.upgrade && b != null) upgraded(c, b) else c
        val spots = fitSpots(held, draft.target)
        if (!draft.upgrade || b == null || b != IsleBuilding.LIGHTHOUSE || !c.standing(b) || c.gridVersion != PlacementRule.EDGE) return spots
        val here = c.positions[b.name] ?: defaultPosition(b)
        return if (fitProblem(held, draft.target, here) == null) (spots + listOf(here)).distinct() else spots
    }
    /** The spots where the harbour (in its current stage) fits: where a held harbour snaps to (rule 6: beach / water). */
    fun harbourSpots(c: ApiClient.IslandConstruction): List<List<Int>> =
        if (ground(c)) IslandGround.harbourSpots(stage(c, IsleBuilding.HARBOUR), land(c), c.gridVersion).ifEmpty { IslandGrid.coastCells(land(c)) }
        else IslandMasks.harbourSpots(stage(c, IsleBuilding.HARBOUR), land(c)).ifEmpty { IslandGrid.coastCells(land(c)) }
    fun harbourCellNear(c: ApiClient.IslandConstruction, cell: List<Int>): List<Int> {
        val spots = harbourSpots(c)
        if (cell in spots) return cell
        val (x, y) = IslandGrid.project(listOf(cell.getOrElse(0) { 10 }.coerceIn(0, IslandGrid.STEPS), cell.getOrElse(1) { 10 }.coerceIn(0, IslandGrid.STEPS)), land(c))
        return IslandGrid.nearestOf(spots, x, y, land(c))
    }
    /** The island with the draft applied, so the renderer shows the real sprite where it would stand. */
    fun preview(h: ApiClient.HomeIsland, draft: IslandDraft?): ApiClient.HomeIsland {
        draft ?: return h
        val c = h.construction
        return when (val t = draft.target) {
            // A new building appears as its free stage 0 (7.9.9); a standing one keeps its stage.
            is IslandTarget.Building -> h.copy(construction = c.copy(
                starters = if (c.standing(t.building)) c.starters else c.starters + t.building.name,
                positions = c.positions + (t.building.name to draft.position)))
            is IslandTarget.Decor -> h.copy(construction = c.copy(decor =
                if (t.id == null) c.decor + ApiClient.IslandDecor(DRAFT_DECOR_ID, t.item, draft.position)
                else c.decor.map { if (it.id == t.id) it.copy(position = draft.position, slot = null) else it }))
        }
    }
    const val DRAFT_DECOR_ID = -1
}
