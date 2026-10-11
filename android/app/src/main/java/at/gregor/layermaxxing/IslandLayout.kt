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
 * Grid rules mirrored from the server (server/app/main.py): building clearance 3, building/place
 * to decoration 2.4, decoration to decoration 1.5 cells, all divided by the land scale.
 */
internal object IslandLayout {
    /** The drag/snap/preview projection lives in [IslandGrid]; this object holds the rules mirrored from the server. */
    const val DECOR_BUILDING_GAP = 2.4f
    const val DECOR_DECOR_GAP = 1.5f
    fun defaultPosition(b: IsleBuilding): List<Int> = IslandPlans.homeBuildings.getValue(b).let { listOf((it.x*20).roundToInt(),(it.y*20).roundToInt()) }
    fun separation(c: ApiClient.IslandConstruction) = 3f / IslandPlans.constructionScale(c.land)
    /** Life places (plots) are part of the same island in both modes. */
    fun obstacles(h: ApiClient.HomeIsland): List<List<Int>> =
        h.places.keys.mapNotNull { LifePlaces.plots.getOrNull(it) }.map { (x,y) -> listOf((x*20).roundToInt(),(y*20).roundToInt()) }
    private fun d2(p: List<Int>, q: List<Int>) = ((q[0]-p[0])*(q[0]-p[0])+(q[1]-p[1])*(q[1]-p[1])).toFloat()
    private fun onIsland(p: List<Int>) = p.size == 2 && p[0] in 0..20 && p[1] in 0..20 && IslandPlans.inside(p[0]/20f,p[1]/20f,radius=.95f)
    fun buildingPositions(c: ApiClient.IslandConstruction, except: IsleBuilding? = null) =
        IsleBuilding.entries.filter { it != except && (c.buildings[it.name] ?: 0) > 0 }.map { c.positions[it.name] ?: defaultPosition(it) }
    fun valid(c: ApiClient.IslandConstruction, b: IsleBuilding, p: List<Int>, obstacles: List<List<Int>> = emptyList()): Boolean {
        if (!onIsland(p)) return false
        val clearance=separation(c)*separation(c)
        if(obstacles.any { q -> d2(p,q) < clearance }) return false
        val gap = DECOR_BUILDING_GAP / IslandPlans.constructionScale(c.land)
        if(c.decor.any { d2(p,it.position) < gap*gap }) return false
        return buildingPositions(c, b).all { d2(p,it) >= clearance }
    }
    fun validDecor(c: ApiClient.IslandConstruction, p: List<Int>, obstacles: List<List<Int>> = emptyList(), ignoreId: Int? = null): Boolean {
        if (!onIsland(p)) return false
        val s = IslandPlans.constructionScale(c.land)
        val gb = DECOR_BUILDING_GAP / s; val gd = DECOR_DECOR_GAP / s
        if (buildingPositions(c).any { d2(p,it) < gb*gb }) return false
        if (obstacles.any { d2(p,it) < gb*gb }) return false
        return c.decor.none { it.id != ignoreId && d2(p,it.position) < gd*gd }
    }
    fun valid(h: ApiClient.HomeIsland, draft: IslandDraft): Boolean = when (val t = draft.target) {
        is IslandTarget.Building -> valid(h.construction, t.building, draft.position, obstacles(h))
        is IslandTarget.Decor -> validDecor(h.construction, draft.position, obstacles(h), t.id)
    }
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
