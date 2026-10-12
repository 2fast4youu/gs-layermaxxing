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
 * Grid rules mirrored from the server (server/app/main.py placement_blocked): big pieces (function
 * buildings and special decoration) keep 3 cells to each other, 2.4 to ordinary decoration, decoration
 * keeps 1.5 to decoration, all divided by the land scale. Every spot must be a cell of the land's build
 * grid ([IslandGrid.buildable]).
 */
internal object IslandLayout {
    /** The drag/snap/preview projection lives in [IslandGrid]; this object holds the rules mirrored from the server. */
    const val DECOR_BUILDING_GAP = 2.4f
    const val DECOR_DECOR_GAP = 1.5f
    const val BUILDING_GAP = 3f
    fun defaultPosition(b: IsleBuilding): List<Int> = IslandPlans.homeBuildings.getValue(b).let { listOf((it.x*20).roundToInt(),(it.y*20).roundToInt()) }
    fun separation(c: ApiClient.IslandConstruction) = BUILDING_GAP / IslandPlans.constructionScale(c.land)
    private fun d2(p: List<Int>, q: List<Int>) = ((q[0]-p[0])*(q[0]-p[0])+(q[1]-p[1])*(q[1]-p[1])).toDouble()
    fun buildingPositions(c: ApiClient.IslandConstruction, except: IsleBuilding? = null) =
        IsleBuilding.entries.filter { it != except && (c.buildings[it.name] ?: 0) > 0 }.map { c.positions[it.name] ?: defaultPosition(it) }
    /** Does something else stand too close? [big] = the piece is a building or special decoration. */
    fun blocked(c: ApiClient.IslandConstruction, p: List<Int>, big: Boolean, ignoreBuilding: IsleBuilding? = null, ignoreDecor: Int? = null): Boolean {
        val f = IslandPlans.constructionScale(c.land).toDouble()
        fun sq(gap: Float) = (gap / f) * (gap / f)
        val toBuilding = if (big) sq(BUILDING_GAP) else sq(DECOR_BUILDING_GAP)
        if (buildingPositions(c, ignoreBuilding).any { d2(p, it) < toBuilding }) return true
        return c.decor.any { d ->
            d.id != ignoreDecor && d2(p, d.position) < when {
                SpecialDecor.isSpecial(d.item) -> toBuilding
                big -> sq(DECOR_BUILDING_GAP)
                else -> sq(DECOR_DECOR_GAP)
            }
        }
    }
    fun valid(c: ApiClient.IslandConstruction, b: IsleBuilding, p: List<Int>): Boolean =
        IslandGrid.buildable(p, c.land) && !blocked(c, p, big = true, ignoreBuilding = b)
    fun validDecor(c: ApiClient.IslandConstruction, p: List<Int>, ignoreId: Int? = null, special: Boolean = false): Boolean =
        IslandGrid.buildable(p, c.land) && !blocked(c, p, big = special, ignoreDecor = ignoreId)
    fun valid(h: ApiClient.HomeIsland, draft: IslandDraft): Boolean = when (val t = draft.target) {
        is IslandTarget.Building -> valid(h.construction, t.building, draft.position)
        is IslandTarget.Decor -> validDecor(h.construction, draft.position, t.id, SpecialDecor.isSpecial(t.item))
    }
    /** Buildable cells where the held piece may NOT stand (drawn darker in the grid). */
    fun blockedCells(h: ApiClient.HomeIsland, target: IslandTarget): Set<List<Int>> =
        IslandGrid.buildableCells(h.construction.land).filterTo(HashSet()) { !valid(h, IslandDraft(target, it)) }
    /** Nearest free cell to [start] (ring search), so a new object never starts on a red square. */
    fun freeCell(start: List<Int>, ok: (List<Int>) -> Boolean): List<Int> {
        if (ok(start)) return start
        for (r in 1..10) for (dx in -r..r) for (dy in -r..r) {
            if (kotlin.math.max(kotlin.math.abs(dx), kotlin.math.abs(dy)) != r) continue
            val p = listOf(start[0]+dx, start[1]+dy)
            if (ok(p)) return p
        }
        return start
    }
    /** The island with the draft applied, so the renderer shows the real sprite where it would stand. */
    fun preview(h: ApiClient.HomeIsland, draft: IslandDraft?): ApiClient.HomeIsland {
        draft ?: return h
        val c = h.construction
        return when (val t = draft.target) {
            is IslandTarget.Building -> h.copy(construction = c.copy(
                buildings = c.buildings + (t.building.name to (c.buildings[t.building.name] ?: 0).coerceAtLeast(1)),
                positions = c.positions + (t.building.name to draft.position)))
            is IslandTarget.Decor -> h.copy(construction = c.copy(decor =
                if (t.id == null) c.decor + ApiClient.IslandDecor(DRAFT_DECOR_ID, t.item, draft.position)
                else c.decor.map { if (it.id == t.id) it.copy(position = draft.position, slot = null) else it }))
        }
    }
    const val DRAFT_DECOR_ID = -1
}
