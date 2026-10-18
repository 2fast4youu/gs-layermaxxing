package at.gregor.layermaxxing

import kotlin.math.roundToInt

/** What is being placed or edited on the own island. */
internal sealed interface IslandTarget {
    data class Building(val building: IsleBuilding) : IslandTarget
    /** [id] null = a new object from the build bar. */
    data class Decor(val item: String, val id: Int? = null) : IslandTarget
}

/** A local, unsaved placement: [lifted] = long-press edit of something already standing. */
internal data class IslandDraft(val target: IslandTarget, val position: List<Int>, val lifted: Boolean = false)

/**
 * 7.9.10: THE occupancy rule, the same as the server's (server/app/main.py FOOTPRINT_RINGS, pinned in
 * server/app/island_grid.json). A piece occupies the grid cells of its footprint: a diamond of
 * |di| + |dj| <= rings whole grid cells whose FRONT tip is its spot (where the sprite's foot stands, the
 * building's base lies behind it), drawn exactly like that while moving. Two pieces never share a
 * cell; there is no other clearance. The sprites keep their size while the lattice grows with the land, so a
 * small island needs more rings. 7.9.9 used circular distances (3 / 2.4 / 1.5 cells ÷ land scale) that
 * reached far beyond the drawn footprint and could not be seen on the grid.
 */
internal object IslandFootprint {
    /** Function buildings at stage 1..3 and special decoration, per land stage. */
    val BIG = intArrayOf(2, 2, 1, 1, 1, 1)
    /** Free stage-0 pieces (hammock, letter tree, ...). */
    val STARTER = intArrayOf(1, 1, 1, 1, 1, 1)
    /** Ordinary decoration. */
    val DECOR = intArrayOf(1, 1, 1, 0, 0, 0)
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
 * Placement rules mirrored from the server (server/app/main.py placement_blocked / building_spot_error):
 * every spot is a cell of the land's build grid ([IslandGrid.buildable]), footprints never overlap
 * ([IslandFootprint]), the harbour stands on the coast ([IslandGrid.coast]).
 */
internal object IslandLayout {
    /** The drag/snap/preview projection lives in [IslandGrid]; this object holds the rules mirrored from the server. */
    fun defaultPosition(b: IsleBuilding): List<Int> = IslandPlans.homeBuildings.getValue(b).let { listOf((it.x*20).roundToInt(),(it.y*20).roundToInt()) }
    fun buildingPositions(c: ApiClient.IslandConstruction, except: IsleBuilding? = null) =
        IsleBuilding.entries.filter { it != except && c.standing(it) }.map { c.positions[it.name] ?: defaultPosition(it) }
    private fun land(c: ApiClient.IslandConstruction) = c.land.coerceIn(0, 5)
    /** Rings of a standing (or stage-0) building and of a decoration item on this land. */
    fun buildingRings(c: ApiClient.IslandConstruction, b: IsleBuilding) =
        (if (b.name in c.starters && (c.buildings[b.name] ?: 0) == 0) IslandFootprint.STARTER else IslandFootprint.BIG)[land(c)]
    fun decorRings(c: ApiClient.IslandConstruction, item: String) = (if (SpecialDecor.isSpecial(item)) IslandFootprint.BIG else IslandFootprint.DECOR)[land(c)]
    /** Rings of the held piece: a building that does not stand yet is placed at its free stage 0 (7.9.9). */
    fun rings(c: ApiClient.IslandConstruction, t: IslandTarget) = when (t) {
        is IslandTarget.Building -> if (c.standing(t.building)) buildingRings(c, t.building) else IslandFootprint.STARTER[land(c)]
        is IslandTarget.Decor -> decorRings(c, t.item)
    }
    /** One other piece while something is moved: its spot and the cells it occupies (drawn red). */
    data class Obstacle(val target: IslandTarget, val center: List<Int>, val rings: Int) {
        val cells: Set<List<Int>> get() = IslandFootprint.cells(center, rings)
    }
    /** Every piece except [except] (the held one never blocks itself, so its old spot is free for it). */
    fun obstacles(c: ApiClient.IslandConstruction, except: IslandTarget?): List<Obstacle> =
        IsleBuilding.entries.filter { c.standing(it) && except != IslandTarget.Building(it) }
            .map { Obstacle(IslandTarget.Building(it), c.positions[it.name] ?: defaultPosition(it), buildingRings(c, it)) } +
        c.decor.filter { d -> !(except is IslandTarget.Decor && except.id != null && except.id == d.id) }
            .map { d -> Obstacle(IslandTarget.Decor(d.item, d.id), d.position, decorRings(c, d.item)) }
    /** Does another piece's footprint share a cell with this one? [big] = the piece is a building or special decoration. */
    fun blocked(c: ApiClient.IslandConstruction, p: List<Int>, big: Boolean, ignoreBuilding: IsleBuilding? = null, ignoreDecor: Int? = null): Boolean {
        // 7.9.9 (server placement_blocked): a stage-0 piece has the small footprint.
        val small = ignoreBuilding != null && ignoreBuilding.name in c.starters && (c.buildings[ignoreBuilding.name] ?: 0) == 0
        val mine = (if (!big) IslandFootprint.DECOR else if (small) IslandFootprint.STARTER else IslandFootprint.BIG)[land(c)]
        return obstacles(c, null).any { o ->
            val own = when (val t = o.target) {
                is IslandTarget.Building -> t.building == ignoreBuilding
                is IslandTarget.Decor -> ignoreDecor != null && t.id == ignoreDecor
            }
            !own && IslandFootprint.overlap(p, mine, o.center, o.rings)
        }
    }
    /** A building not standing yet is placed at its free stage 0 (7.9.9), so it has the small footprint. */
    fun valid(c: ApiClient.IslandConstruction, b: IsleBuilding, p: List<Int>): Boolean =
        IslandGrid.buildable(p, c.land) && (b != IsleBuilding.HARBOUR || IslandGrid.coast(p, c.land)) &&
            !blocked(if (c.standing(b)) c else c.copy(starters = c.starters + b.name), p, big = true, ignoreBuilding = b)
    fun validDecor(c: ApiClient.IslandConstruction, p: List<Int>, ignoreId: Int? = null, special: Boolean = false): Boolean =
        IslandGrid.buildable(p, c.land) && !blocked(c, p, big = special, ignoreDecor = ignoreId)
    fun valid(h: ApiClient.HomeIsland, draft: IslandDraft): Boolean = reason(h, draft) == null
    /** Why the held piece cannot stand here (shown at the top while moving), null = it can. */
    fun reason(h: ApiClient.HomeIsland, draft: IslandDraft): String? {
        val c = h.construction; val p = draft.position
        if (!IslandGrid.buildable(p, c.land)) return "Außerhalb der Insel"
        return when (val t = draft.target) {
            is IslandTarget.Building -> when {
                t.building == IsleBuilding.HARBOUR && !IslandGrid.coast(p, c.land) -> "Hafen nur an der Küste"
                !valid(c, t.building, p) -> "Belegt – rot markierte Fläche"
                else -> null
            }
            is IslandTarget.Decor -> if (validDecor(c, p, t.id, SpecialDecor.isSpecial(t.item))) null else "Belegt – rot markierte Fläche"
        }
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
