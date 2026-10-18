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
    val rejected: Set<List<Int>> = emptySet())

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
 *  - 4 = 7.9.11 servers: the compact [IslandFootprint.V4] diamonds.
 * The harbour stands on the coast with every rule (a v2 server accepts more, never less). A payload without the
 * field ([LEGACY]) or an unknown number is never taken for the newest rule: nothing is green, nothing is written,
 * the hint says why ([problem]).
 */
internal object PlacementRule {
    const val LEGACY = 0
    const val CIRCLES = 2
    const val DIAMONDS = 3
    const val COMPACT = 4
    const val CURRENT = COMPACT
    fun known(version: Int) = version == CIRCLES || version == DIAMONDS || version == COMPACT
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
        version > CURRENT -> "Der Server nutzt neuere Bauregeln (Version $version) – bitte die App aktualisieren"
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
    /** One other piece while something is moved: its spot and the cells it occupies (outlined red). */
    data class Obstacle(val target: IslandTarget, val center: List<Int>, val rings: Int) {
        val cells: Set<List<Int>> get() = IslandFootprint.cells(center, rings)
    }
    /** Every piece except [except] (the held one never blocks itself, so its old spot is free for it). */
    fun obstacles(c: ApiClient.IslandConstruction, except: IslandTarget?): List<Obstacle> =
        IsleBuilding.entries.filter { c.standing(it) && except != IslandTarget.Building(it) }
            .map { Obstacle(IslandTarget.Building(it), c.positions[it.name] ?: defaultPosition(it), buildingRings(c, it)) } +
        c.decor.filter { d -> !(except is IslandTarget.Decor && except.id != null && except.id == d.id) }
            .map { d -> Obstacle(IslandTarget.Decor(d.item, d.id), d.position, decorRings(c, d.item)) }
    /**
     * Does another piece block [p]? [big] = the piece is a building or special decoration. The moved piece
     * ([ignoreBuilding] / [ignoreDecor]) never blocks itself. Under an unknown rule everything is blocked.
     */
    fun blocked(c: ApiClient.IslandConstruction, p: List<Int>, big: Boolean, ignoreBuilding: IsleBuilding? = null, ignoreDecor: Int? = null): Boolean {
        if (!PlacementRule.known(c.gridVersion)) return true
        // 7.9.9 (server placement_blocked): a held stage-0 piece is small.
        val small = ignoreBuilding != null && ignoreBuilding.name in c.starters && (c.buildings[ignoreBuilding.name] ?: 0) == 0
        if (c.gridVersion == PlacementRule.CIRCLES) return blockedByCircles(c, p, big, small, ignoreBuilding, ignoreDecor)
        val r = PlacementRule.rings(c.gridVersion)!!
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
    fun validDecor(c: ApiClient.IslandConstruction, p: List<Int>, ignoreId: Int? = null, special: Boolean = false): Boolean =
        PlacementRule.known(c.gridVersion) && IslandGrid.buildable(p, c.land) && !blocked(c, p, big = special, ignoreDecor = ignoreId)
    fun valid(h: ApiClient.HomeIsland, draft: IslandDraft): Boolean = reason(h, draft) == null
    /** THE decision for one cell: why the held piece cannot stand at [p] (null = it can, and only then it is drawn). */
    fun problem(c: ApiClient.IslandConstruction, t: IslandTarget, p: List<Int>, rejected: Set<List<Int>> = emptySet()): String? {
        PlacementRule.problem(c.gridVersion)?.let { return it }
        if (!IslandGrid.buildable(p, c.land)) return "Außerhalb der Insel"
        val free = when (t) {
            is IslandTarget.Building -> {
                if (t.building == IsleBuilding.HARBOUR && !IslandGrid.coast(p, c.land)) return "Hafen nur an der Küste"
                !blocked(if (c.standing(t.building)) c else c.copy(starters = c.starters + t.building.name), p, big = true, ignoreBuilding = t.building)
            }
            is IslandTarget.Decor -> !blocked(c, p, big = SpecialDecor.isSpecial(t.item), ignoreDecor = t.id)
        }
        if (!free) return "Belegt – rot markierte Fläche"
        if (p in rejected) return REJECTED
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
    /** Why the held piece cannot stand here (shown at the top while moving), null = it can. */
    fun reason(h: ApiClient.HomeIsland, draft: IslandDraft): String? =
        PlacementRule.problem(h.construction.gridVersion) ?: (if (gone(h.construction, draft)) GONE else null)
            ?: problem(h.construction, draft.target, draft.position, draft.rejected)
    /** The cells drawn for [draft]: none for a piece that is gone. */
    fun targets(c: ApiClient.IslandConstruction, draft: IslandDraft): List<List<Int>> =
        if (gone(c, draft)) emptyList() else targets(c, draft.target, draft.rejected)
    const val REJECTED = "Server lehnt diesen Platz ab – Stand neu geladen"
    /**
     * Every cell the held piece may really stand on = exactly the cells the grid draws (7.9.11): for the harbour
     * only free coast cells, for everything else every free buildable cell. Nothing else is drawn as usable.
     */
    fun targets(c: ApiClient.IslandConstruction, t: IslandTarget, rejected: Set<List<Int>> = emptySet()): List<List<Int>> {
        if (!PlacementRule.known(c.gridVersion)) return emptyList()
        val candidates = if (t == IslandTarget.Building(IsleBuilding.HARBOUR)) IslandGrid.coastCells(c.land) else IslandGrid.buildableCells(c.land)
        return candidates.filter { problem(c, t, it, rejected) == null }
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
        val start = if (b == IsleBuilding.HARBOUR) IslandGrid.coastCellNear(stored, land(c)) else stored
        return if (built) start else freeCell(start) { valid(c, b, it) }
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
