package at.gregor.layermaxxing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 7.9.12: THE harbour geometry of the archipelago. The harbour is the arrival place: every route and every boat
 * starts and ends at an island's BERTH, the water cell right in front of its harbour's pier ([IslandMasks.BERTH],
 * the cell the occupancy rule keeps as water), computed from the island's CURRENT construction (harbour position,
 * land stage) with the same projection the island renderer uses ([IslandGrid.project]). Moved harbour = moved berth,
 * at once. An island without a harbour (old data, not built yet) gets a visible landing stage at its southern shore
 * ([fallbackBerth], drawn by the map), never an invisible point.
 *
 * Routes go through water only: out from the berth to the open sea (the pier's direction), around every island in
 * the way, in to the other berth. A boat's place on it follows the real trip share: 0 = at the sender's berth,
 * 1 = at the receiver's berth; a letter that waits (approval, presence) lies in the sender's roadstead.
 */
internal object HarbourRoute {
    /** Where a waiting letter lies at anchor: in the roadstead in front of the sender's harbour. */
    const val ROADSTEAD = .1f

    /** The berth of [c] as a fraction of its island box (x of the width, y of the height). */
    fun berth(c: ApiClient.IslandConstruction?): Pair<Float, Float> {
        val land = (c?.land ?: 0).coerceIn(0, 5)
        val spot = harbourSpot(c) ?: return fallbackBerth(land)
        return IslandGrid.project(listOf(spot[0] + IslandMasks.BERTH[0], spot[1] + IslandMasks.BERTH[1]), land)
    }
    /** The harbour's spot when one stands (a stored one, or the server's default spot), else null. */
    fun harbourSpot(c: ApiClient.IslandConstruction?): List<Int>? =
        c?.takeIf { it.supported && it.standing(IsleBuilding.HARBOUR) }?.let { it.positions[IsleBuilding.HARBOUR.name] ?: IslandLayout.defaultPosition(IsleBuilding.HARBOUR) }
    fun hasHarbour(c: ApiClient.IslandConstruction?) = harbourSpot(c) != null

    private fun southCell(land: Int): List<Int> = IslandGrid.buildableCells(land).filter { it[0] == 10 }.maxBy { it[1] }
    /** The visible landing stage of an island without a harbour: its outer end, in the water below the south shore. */
    fun fallbackBerth(land: Int): Pair<Float, Float> = southCell(land).let { IslandGrid.project(listOf(it[0], it[1] + 2), land) }
    /** Where that landing stage starts on the land. */
    fun fallbackRoot(land: Int): Pair<Float, Float> = IslandGrid.project(southCell(land), land)

    /** A point of an island box drawn [width] px wide with its top-left at [topLeft] (the box is width / ASPECT high). */
    fun onMap(topLeft: Offset, width: Float, frac: Pair<Float, Float>) = topLeft + Offset(frac.first * width, frac.second * width / IslandPlans.ASPECT)

    /** An island on the map as an obstacle: no route may cross it. */
    data class Island(val centre: Offset, val radius: Float)

    /**
     * The water route from berth [a] of island [ia] to berth [b] of island [ib]: out to [margin] beyond the own
     * island's rim in the pier's direction, then the shortest way through open water around every island ([all], both
     * ends included, each kept [margin] × .6 clear) inside [bounds], then in to [b]. Only the two dock pieces (berth ↔
     * exit) may touch an island, and only their own island and only outward of the berth: the dock entry is allowed,
     * the island is not exempt. When no such water way exists (an exit inside another island, islands closing every
     * gap, the map edge) the answer is null: no route is drawn rather than one through land.
     */
    fun route(a: Offset, ia: Island, b: Offset, ib: Island, all: List<Island>, margin: Float, bounds: Rect? = null): List<Offset>? {
        // out in the pier's direction; a pier facing the map edge (no room there for a boat) turns up to 60° towards
        // the open sea, still checked by the dock rule
        fun exit(berth: Offset, island: Island): Offset? {
            val d = berth - island.centre
            val n = d.getDistance().takeIf { it > .5f }?.let { d / it } ?: Offset(0f, 1f)
            for (deg in listOf(0, 20, -20, 40, -40, 60, -60)) {
                val r = deg * PI.toFloat() / 180f
                val e = island.centre + Offset(n.x * cos(r) - n.y * sin(r), n.x * sin(r) + n.y * cos(r)) * (island.radius + margin)
                if (dockClear(berth, e, island, all, bounds)) return e
            }
            return null
        }
        val ea = exit(a, ia) ?: return null; val eb = exit(b, ib) ?: return null
        val obstacles = all.map { Island(it.centre, it.radius + margin * .6f) }
        val middle = waterWay(ea, eb, obstacles, bounds) ?: return null
        return listOf(a) + middle + listOf(b)
    }

    /** Points around each obstacle a way may bend at (just outside it, so the ring's chords stay in the water). */
    private const val RING = 16
    private const val RING_OUT = 1.14f

    /** Shortest polyline [p] → [q] whose every segment stays clear of every obstacle and inside [bounds], or null. */
    private fun waterWay(p: Offset, q: Offset, obstacles: List<Island>, bounds: Rect?): List<Offset>? {
        val nodes = mutableListOf(p, q)
        for (o in obstacles) for (k in 0 until RING) {
            val ang = 2 * PI.toFloat() * k / RING
            val v = o.centre + Offset(cos(ang), sin(ang)) * (o.radius * RING_OUT)
            if (obstacles.none { inside(v, it) } && (bounds == null || bounds.contains(v))) nodes += v
        }
        if (obstacles.any { inside(p, it) || inside(q, it) }) return null
        val n = nodes.size
        val dist = FloatArray(n) { Float.MAX_VALUE }; val prev = IntArray(n) { -1 }; val done = BooleanArray(n)
        dist[0] = 0f
        while (true) {
            var u = -1
            for (i in 0 until n) if (!done[i] && dist[i] < Float.MAX_VALUE && (u < 0 || dist[i] < dist[u])) u = i
            if (u < 0 || u == 1) break
            done[u] = true
            for (v in 0 until n) if (!done[v]) {
                val d = dist[u] + (nodes[v] - nodes[u]).getDistance()
                if (d < dist[v] && segmentClear(nodes[u], nodes[v], obstacles)) { dist[v] = d; prev[v] = u }
            }
        }
        if (dist[1] == Float.MAX_VALUE) return null
        val way = mutableListOf<Offset>()
        var at = 1
        while (at >= 0) { way += nodes[at]; at = prev[at] }
        return way.reversed()
    }

    private fun inside(p: Offset, o: Island) = (p - o.centre).getDistance() < o.radius - 1e-2f
    private fun segmentClear(p: Offset, q: Offset, obstacles: List<Island>) = obstacles.none { (closest(p, q, it.centre) - it.centre).getDistance() < it.radius - 1e-2f }

    /**
     * The dock piece [berth] → [exit] of [own]: it may not cross any other island and may not reach deeper into its own
     * island than the berth itself (that is the pier, nothing more).
     */
    private fun dockClear(berth: Offset, exit: Offset, own: Island, all: List<Island>, bounds: Rect?): Boolean {
        if (bounds != null && !bounds.contains(exit)) return false
        val depth = (berth - own.centre).getDistance()
        val others = all.filter { it != own }
        return segmentClear(berth, exit, others) && segmentClear(berth, exit, listOf(Island(own.centre, depth)))
    }

    private fun closest(p: Offset, q: Offset, c: Offset): Offset {
        val d = q - p
        val l2 = d.x * d.x + d.y * d.y
        if (l2 < 1e-3f) return p
        val t = (((c.x - p.x) * d.x + (c.y - p.y) * d.y) / l2).coerceIn(0f, 1f)
        return p + d * t
    }

    /**
     * The same route on a boat's own lane: the open-water middle is shifted [side] px sideways (a sine that is 0 at
     * both berths), so boats sail side by side and every lane still ends exactly at the berths.
     */
    fun lane(path: List<Offset>, side: Float): List<Offset> {
        if (side == 0f || path.size < 2) return path
        val total = length(path)
        var run = 0f
        return path.mapIndexed { i, p ->
            if (i > 0) run += (p - path[i - 1]).getDistance()
            if (i == 0 || i == path.lastIndex) return@mapIndexed p // the berths themselves never move
            val a = path[(i - 1).coerceAtLeast(0)]; val b = path[(i + 1).coerceAtMost(path.lastIndex)]
            val d = (b - a).let { it / it.getDistance().coerceAtLeast(1e-3f) }
            p + Offset(-d.y, d.x) * (side * sin(PI.toFloat() * (run / total).coerceIn(0f, 1f)))
        }
    }

    /**
     * Whether a (shifted) lane of the route between [ia] and [ib] is still water: its middle clears every island in
     * [all] (by [clearance]) and stays in [bounds], its dock pieces only touch their own island outward of the berth.
     */
    fun laneClear(path: List<Offset>, ia: Island, ib: Island, all: List<Island>, clearance: Float, bounds: Rect? = null): Boolean {
        if (path.size < 3) return false
        val obstacles = all.map { Island(it.centre, it.radius + clearance) }
        val mid = path.subList(1, path.size - 1)
        if (bounds != null && mid.any { !bounds.contains(it) }) return false
        if (mid.zipWithNext().any { (p, q) -> !segmentClear(p, q, obstacles) }) return false
        return dockClear(path.first(), path[1], ia, all, null) && dockClear(path.last(), path[path.size - 2], ib, all, null)
    }

    /**
     * The lane a boat actually sails: the wanted [side] shift if that lane is still water, else the nearest smaller
     * shift that is, finally the validated route itself (side 0). Never an unchecked lane.
     */
    fun safeLane(path: List<Offset>, side: Float, ia: Island, ib: Island, all: List<Island>, clearance: Float, bounds: Rect? = null): List<Offset> {
        for (f in listOf(1f, .75f, .5f, .25f)) {
            val l = lane(path, side * f)
            if (laneClear(l, ia, ib, all, clearance, bounds)) return l
        }
        return path
    }

    fun length(path: List<Offset>): Float = path.zipWithNext().sumOf { (p, q) -> (q - p).getDistance().toDouble() }.toFloat()

    /** The point at share [t] (0..1) of the path's length. */
    fun at(path: List<Offset>, t: Float): Offset {
        val target = length(path) * t.coerceIn(0f, 1f)
        var run = 0f
        for ((p, q) in path.zipWithNext()) {
            val l = (q - p).getDistance()
            if (run + l >= target) return if (l < 1e-3f) p else p + (q - p) * ((target - run) / l)
            run += l
        }
        return path.last()
    }
    /** The part of the path between shares [t0] and [t1] (for drawing the sailed and the open part). */
    fun part(path: List<Offset>, t0: Float, t1: Float, steps: Int = 32): List<Offset> = (0..steps).map { at(path, t0 + (t1 - t0) * it / steps) }

    /**
     * Where along the route from HOME to the friend a letter's boat is: an outgoing letter sails home → friend, an
     * incoming one friend → home; [share] = the real trip share (null = waiting in the sender's roadstead).
     */
    fun boatT(incoming: Boolean, share: Float?): Float {
        val s = share ?: ROADSTEAD
        return if (incoming) 1f - s else s
    }
}
