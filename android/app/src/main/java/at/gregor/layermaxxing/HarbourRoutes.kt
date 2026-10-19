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
    /** 7.9.13: a legacy (v2..5) pier further than this many grid cells from water stands inland: repair, not a far coast point. */
    const val LEGACY_REACH_CELLS = 3f
    /** Where a waiting letter lies at anchor: in the roadstead in front of the sender's harbour. */
    const val ROADSTEAD = .1f

    /** The berth of [c] as a fraction of its island box (x of the width, y of the height). */
    fun berth(c: ApiClient.IslandConstruction?): Pair<Float, Float> {
        val land = (c?.land ?: 0).coerceIn(0, 5)
        val spot = harbourSpot(c) ?: return fallbackBerth(land)
        // 7.9.13 (rule 6): in front of the pier head; the rowboat on the beach: the water line straight in front of it
        if (c != null && IslandLayout.ground(c)) return IslandGrid.project(IslandGround.berthCell(spot, c.level(IsleBuilding.HARBOUR).coerceIn(0, 3)), land)
        return IslandGrid.project(listOf(spot[0] + IslandMasks.BERTH[0], spot[1] + IslandMasks.BERTH[1]), land)
    }
    /** 7.9.13: where the rowboat's walk to the water starts (its spot), for drawing the short beach path; null for a pier. */
    fun beachWalk(c: ApiClient.IslandConstruction?): Pair<Float, Float>? {
        val spot = harbourSpot(c) ?: return null
        if (c == null || !IslandLayout.ground(c) || c.level(IsleBuilding.HARBOUR) > 0) return null
        return IslandGrid.project(spot, c.land.coerceIn(0, 5))
    }
    /** The harbour's spot when one stands (a stored one, or the server's default spot), else null. */
    fun harbourSpot(c: ApiClient.IslandConstruction?): List<Int>? =
        c?.takeIf { it.supported && it.standing(IsleBuilding.HARBOUR) }?.let { it.positions[IsleBuilding.HARBOUR.name] ?: IslandLayout.defaultPosition(IsleBuilding.HARBOUR) }
    fun hasHarbour(c: ApiClient.IslandConstruction?) = harbourSpot(c) != null
    /**
     * 7.9.13: under the ground rule (v6) a harbour is at a real socket only on one of [IslandGround.harbourSpots] of its
     * stage and land; true/false there, null for the legacy rules (2..5), which keep their own coast spots unchanged.
     */
    fun atSocket(c: ApiClient.IslandConstruction?): Boolean? {
        val spot = harbourSpot(c) ?: return null
        if (c == null || !IslandLayout.ground(c)) return null
        return spot in IslandGround.harbourSpots(c.level(IsleBuilding.HARBOUR).coerceIn(0, 3), c.land.coerceIn(0, 5))
    }

    /**
     * 7.9.13: the visible landing stage of an island without a harbour reaches from the front of the beach in the
     * southern cove ([IslandGround] zone B) out into its open water; the same painted cove on every land stage.
     */
    private val coveRoot: List<Int> by lazy {
        (0..IslandGrid.STEPS).flatMap { j -> (0..IslandGrid.STEPS).map { i -> listOf(i, j) } }
            .filter { IslandGround.zone(it) == 'B' && IslandGround.zone(it[0], it[1] + 1) == 'W' }
            .minBy { kotlin.math.abs(it[0] - 10) * 10 - it[1] }
    }
    /** The visible landing stage of an island without a harbour: its outer end, in the water in front of the beach. */
    fun fallbackBerth(land: Int): Pair<Float, Float> = IslandGrid.project(listOf(coveRoot[0], coveRoot[1] + 2), land)
    /** Where that landing stage starts: on the beach. */
    fun fallbackRoot(land: Int): Pair<Float, Float> = IslandGrid.project(coveRoot, land)

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

/**
 * 7.9.13: THE sea chart of the archipelago map. Obstacles are the islands as they are PAINTED: the solid outline of the
 * terrain (land, sand, rocks; [IslandGround.coast]) scaled to each island's land stage and drawn box, and every rock
 * standing in the water. No oversized circles. A boat is a physical hull of radius [hull] (its hull only: badges, the
 * "+N" chip and name plaques are no hull and never push a route away).
 *
 * A route: from the berth straight out along the pier (towards the viewer) until the hull is clear of every coast (the
 * dock, where only the keel needs water), then the shortest way on a grid of free water ([Field], A* on cells whose
 * clearance is at least the hull), pulled tight, smoothed (Chaikin) and CHECKED: every final segment is sampled against
 * the exact clearance. A smoothing that would cut over land or rocks is dropped for the tight, less smooth polyline;
 * with no free water way at all the answer is null (nothing is drawn, never a straight line through land).
 * The geometry depends only on islands and berths; callers keep it per geometry (never per minute or per boat).
 */
internal object SeaChart {
    /** One island on the map in map px: its painted outline (closed polygon) and its rocks in the water (centre, radius). */
    class Coast(val outline: List<Offset>, val rocks: List<Pair<Offset, Float>>) {
        val bounds: Rect = Rect(outline.minOf { it.x }, outline.minOf { it.y }, outline.maxOf { it.x }, outline.maxOf { it.y })
            .let { r -> rocks.fold(r) { acc, (c, rad) -> Rect(minOf(acc.left, c.x - rad), minOf(acc.top, c.y - rad), maxOf(acc.right, c.x + rad), maxOf(acc.bottom, c.y + rad)) } }
    }

    /** The coast of an island drawn [width] px wide with its box's top-left at [topLeft] on land stage [land]. */
    fun coastOf(topLeft: Offset, width: Float, land: Int): Coast {
        val s = IslandPlans.constructionScale(land)
        fun map(fx: Float, fy: Float) = IslandPlans.onLand(fx, fy, s).let { (x, y) -> HarbourRoute.onMap(topLeft, width, x to y) }
        val c = IslandGround.coast
        return Coast(c.outline.map { (x, y) -> map(x, y) }, c.rocks.map { (x, y, r) -> map(x, y) to r * width * s })
    }

    fun inside(p: Offset, poly: List<Offset>): Boolean {
        var odd = false
        var j = poly.lastIndex
        for (i in poly.indices) {
            val a = poly[i]; val b = poly[j]
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) odd = !odd
            j = i
        }
        return odd
    }

    private fun segDist(p: Offset, a: Offset, b: Offset): Float {
        val d = b - a
        val l2 = d.x * d.x + d.y * d.y
        val t = if (l2 < 1e-6f) 0f else (((p.x - a.x) * d.x + (p.y - a.y) * d.y) / l2).coerceIn(0f, 1f)
        return (p - (a + d * t)).getDistance()
    }

    /** Signed distance of [p] to the coasts: negative inside land / a rock, else the distance to the nearest of them. */
    fun clearance(p: Offset, coasts: List<Coast>): Float {
        var best = Float.MAX_VALUE
        for (c in coasts) {
            val b = c.bounds
            // far away from this island: its box is a lower bound of the distance
            val dx = maxOf(b.left - p.x, 0f, p.x - b.right); val dy = maxOf(b.top - p.y, 0f, p.y - b.bottom)
            if (kotlin.math.hypot(dx, dy) >= best) continue
            var d = Float.MAX_VALUE
            val o = c.outline
            for (i in o.indices) d = minOf(d, segDist(p, o[i], o[(i + 1) % o.size]))
            if (inside(p, o)) return -d
            best = minOf(best, d)
            for ((rc, r) in c.rocks) best = minOf(best, (p - rc).getDistance() - r).also { if (it < 0f) return it }
        }
        return best
    }

    /** A grid of the map's water: clearance per cell (px), computed once per geometry. */
    class Field(val coasts: List<Coast>, val bounds: Rect, val cell: Float) {
        val cols = ((bounds.width / cell).toInt() + 1).coerceAtLeast(1)
        val rows = ((bounds.height / cell).toInt() + 1).coerceAtLeast(1)
        val clear = FloatArray(cols * rows) { k -> clearance(at(k % cols, k / cols), coasts) }
        fun at(c: Int, r: Int) = Offset(bounds.left + c * cell, bounds.top + r * cell)
        fun cellOf(p: Offset) = (((p.x - bounds.left) / cell).let { kotlin.math.round(it).toInt() }.coerceIn(0, cols - 1)) to
            (((p.y - bounds.top) / cell).let { kotlin.math.round(it).toInt() }.coerceIn(0, rows - 1))
    }

    /** Is the straight piece [a] → [b] at least [need] px off every coast and inside [bounds]? Sampled finely. */
    fun clearSegment(a: Offset, b: Offset, coasts: List<Coast>, need: Float, bounds: Rect?, step: Float): Boolean {
        val n = ((b - a).getDistance() / step).toInt().coerceAtLeast(1)
        for (k in 0..n) {
            val p = a + (b - a) * (k.toFloat() / n)
            if (bounds != null && !bounds.contains(p)) return false
            if (clearance(p, coasts) < need) return false
        }
        return true
    }

    /**
     * Where a ship really moors at [berth]: the berth itself if its keel has water there, else (an old harbour standing
     * inland) the nearest point of open water around it; null if there is none within reach.
     */
    fun mooring(berth: Offset, coasts: List<Coast>, keel: Float, reach: Float = 400f): Offset? {
        if (clearance(berth, coasts) >= keel) return berth
        val step = (keel / 2).coerceAtLeast(1f)
        var r = step
        while (r <= reach) {
            val n = (2 * Math.PI * r / step).toInt().coerceAtLeast(8)
            val hit = (0 until n).map { k -> val a = 2 * Math.PI * k / n; berth + Offset(kotlin.math.sin(a).toFloat(), kotlin.math.cos(a).toFloat()) * r }
                .filter { clearance(it, coasts) >= keel }
                .minByOrNull { (it - berth).getDistance() }
            if (hit != null) return hit
            r += step
        }
        return null
    }

    /** 7.9.13: where boats of one island really moor, and whether its harbour needs moving to the coast. */
    data class Mooring(val point: Offset, val repair: Boolean)

    /**
     * 7.9.13 (Phase-2 risk): a berth with water under the keel is used as it is; one a little short of it (rounding at
     * the painted coast, at most [reach]) moors at the nearest water. A harbour standing further inland (old data) is
     * NOT quietly declared moored at some far coast point: boats use the island's real landing stage [fallback] (the
     * southern cove, always water) and the island is marked [Mooring.repair]. null only if not even that has water.
     * [atSocket] (HarbourRoute.atSocket): true = a real v6 socket (moored as before), false = not a socket of its stage
     * (repair at once), null = legacy rule (the [reach] decides).
     * 7.9.13 physical hull: a ship lies where its whole [hull] has water, at most [reach] + [MOOR_HULLS] hulls from the
     * berth (the short mooring distance in front of the pier); a socket without such water close by is a repair case
     * too, never a far point of open water at another side of the island.
     */
    fun moor(berth: Offset, fallback: Offset?, coasts: List<Coast>, keel: Float, reach: Float, atSocket: Boolean? = null, hull: Float = keel): Mooring? {
        val near = reach + MOOR_HULLS * hull
        if (atSocket != false) mooring(berth, coasts, maxOf(keel, hull), near)?.let { return Mooring(it, false) }
        val f = fallback?.let { mooring(it, coasts, maxOf(keel, hull), near) } ?: return null
        return Mooring(f, true)
    }
    /** How far (in hulls, beyond the legacy reach) a berth may lie from the hull water its ship moors at. */
    const val MOOR_HULLS = 2f

    /**
     * From the berth out along the pier (towards the viewer, turning a little if needed) until the hull is clear.
     * 7.9.13: with a [toward] point the clear exit whose direction turns least towards it is taken (a boat bound north
     * no longer leaves southwards and turns ~107° at the dock exit); without, the first clear one as before.
     */
    private fun dock(berth: Offset, field: Field, hull: Float, keel: Float, bounds: Rect, toward: Offset? = null): Offset? {
        if (toward != null) {
            val want = toward - berth
            // a little more room than the hull, so the smoothed turn after the exit still keeps the hull clear
            return (0 until 360 step 15).mapNotNull { deg -> exit(berth, deg, field, hull, keel, bounds, 1.5f) }
                .minByOrNull { e -> val d = e - berth; -(d.x * want.x + d.y * want.y) / (d.getDistance() * want.getDistance()).coerceAtLeast(1e-3f) }
        }
        for (deg in listOf(0, 25, -25, 50, -50, 75, -75, 100, -100)) exit(berth, deg, field, hull, keel, bounds)?.let { return it }
        return null
    }

    /** The first hull-clear point from [berth] in direction [deg] (0 = towards the viewer), reached over keel water; or null. */
    private fun exit(berth: Offset, deg: Int, field: Field, hull: Float, keel: Float, bounds: Rect, room: Float = 1.05f): Offset? {
        val r = deg * PI.toFloat() / 180f
        val dir = Offset(-sin(r), cos(r))
        var d = keel
        while (d <= hull * 8) {
            val e = berth + dir * d
            if (!bounds.contains(e)) break
            if (clearance(e, field.coasts) >= hull * room) {
                // the first clear point in this direction; the dock piece to it keeps the whole hull in water too
                if (clearSegment(berth, e, field.coasts, hull, null, keel)) return e
                break
            }
            d += keel
        }
        return null
    }

    /** A* over free cells (clearance >= hull) between the two dock exits; null without a water way. */
    private fun search(from: Offset, to: Offset, field: Field, hull: Float): List<Offset>? {
        val cols = field.cols; val rows = field.rows
        fun free(c: Int, r: Int) = c in 0 until cols && r in 0 until rows && field.clear[r * cols + c] >= hull &&
            field.bounds.contains(field.at(c, r))
        // the exit point is clear, but its rounded cell may not be: the nearest free cell joined to it by checked water
        fun snap(p: Offset): Pair<Int, Int>? {
            val (pc, pr) = field.cellOf(p)
            return (-3..3).flatMap { dc -> (-3..3).map { dr -> pc + dc to pr + dr } }
                .filter { (c, r) -> free(c, r) && clearSegment(p, field.at(c, r), field.coasts, hull, field.bounds, field.cell / 3) }
                .minByOrNull { (c, r) -> (field.at(c, r) - p).getDistance() }
        }
        val (c0, r0) = snap(from) ?: return null
        val (c1, r1) = snap(to) ?: return null
        val start = r0 * cols + c0; val goal = r1 * cols + c1
        val g = FloatArray(cols * rows) { Float.MAX_VALUE }; val prev = IntArray(cols * rows) { -1 }
        val open = java.util.PriorityQueue<Pair<Float, Int>>(compareBy { it.first })
        fun h(k: Int): Float { val dc = kotlin.math.abs(k % cols - c1).toFloat(); val dr = kotlin.math.abs(k / cols - r1).toFloat(); return (maxOf(dc, dr) + .4142f * minOf(dc, dr)) }
        g[start] = 0f; open += h(start) to start
        val done = BooleanArray(cols * rows)
        while (open.isNotEmpty()) {
            val (_, k) = open.poll()!!
            if (done[k]) continue
            done[k] = true
            if (k == goal) break
            val c = k % cols; val r = k / cols
            for (dc in -1..1) for (dr in -1..1) {
                if (dc == 0 && dr == 0) continue
                val nc = c + dc; val nr = r + dr
                if (!free(nc, nr)) continue
                // never cut a corner between two blocked cells
                if (dc != 0 && dr != 0 && (!free(c + dc, r) || !free(c, r + dr))) continue
                val n = nr * cols + nc
                // a gentle preference for open water: cells closer than two hulls to a coast cost a little more
                val near = (2 * hull - field.clear[n]).coerceAtLeast(0f) / hull * .6f
                val cost = g[k] + (if (dc != 0 && dr != 0) 1.4142f else 1f) * (1f + near)
                if (cost < g[n]) { g[n] = cost; prev[n] = k; open += (cost + h(n)) to n }
            }
        }
        if (g[goal] == Float.MAX_VALUE) return null
        val out = ArrayList<Offset>()
        var k = goal
        while (k >= 0) { out += field.at(k % cols, k / cols); k = prev[k] }
        out.reverse()
        // the snapped end cells stay in the path: the pieces from / to them are the checked ones
        return listOf(from) + out + listOf(to)
    }

    /** Drop every point a straight, checked piece can skip (string pulling). */
    private fun pull(path: List<Offset>, coasts: List<Coast>, hull: Float, bounds: Rect, step: Float): List<Offset> {
        val out = mutableListOf(path.first())
        var i = 0
        while (i < path.lastIndex) {
            var j = path.lastIndex
            while (j > i + 1 && !clearSegment(path[i], path[j], coasts, hull, bounds, step)) j--
            out += path[j]; i = j
        }
        return out
    }

    /** The same polyline with every piece longer than [max] split evenly. */
    private fun densify(p: List<Offset>, max: Float): List<Offset> = listOf(p.first()) + p.zipWithNext().flatMap { (a, b) ->
        val n = kotlin.math.ceil((b - a).getDistance() / max).toInt().coerceAtLeast(1)
        (1..n).map { k -> a + (b - a) * (k.toFloat() / n) }
    }

    /** Chaikin corner cutting, both ends fixed. */
    private fun chaikin(p: List<Offset>): List<Offset> {
        if (p.size < 3) return p
        val out = mutableListOf(p.first())
        for (i in 0 until p.lastIndex) {
            val a = p[i]; val b = p[i + 1]
            if (i > 0) out += a * .75f + b * .25f
            if (i < p.lastIndex - 1) out += a * .25f + b * .75f
        }
        out += p.last()
        return out
    }

    /**
     * The water way from berth [a] to berth [b] (both already moored, see [mooring]), or null. The dock pieces only need
     * the keel's depth; the open-water middle keeps the whole hull clear of every coast and rock and stays in [bounds].
     */
    fun route(a: Offset, b: Offset, field: Field, hull: Float, keel: Float): List<Offset>? {
        val bounds = field.bounds
        // 7.9.13: first the dock exits facing the other end (gentle turn), then the old pier-first exits
        val ta = dock(a, field, hull, keel, bounds, toward = b); val tb = dock(b, field, hull, keel, bounds, toward = a)
        // the open middle first with room to spare (a line pulled tight along a coast at exactly one hull cannot be
        // rounded without cutting closer), so the smoothing below can round it and still keep the whole hull clear
        if (ta != null && tb != null) for (room in listOf(1.6f, 1.3f)) routeVia(a, ta, tb, b, field, hull, keel, room, smoothOnly = true)?.let { return it }
        if (ta != null && tb != null) routeVia(a, ta, tb, b, field, hull, keel)?.let { return it }
        val ea = dock(a, field, hull, keel, bounds) ?: return null
        val eb = dock(b, field, hull, keel, bounds) ?: return null
        return routeVia(a, ea, eb, b, field, hull, keel)
    }

    /** The way over the dock exits [ea] / [eb]; the middle planned [room] hulls off the coast; [smoothOnly]: null unless fully rounded. */
    private fun routeVia(a: Offset, ea: Offset, eb: Offset, b: Offset, field: Field, hull: Float, keel: Float, room: Float = 1f, smoothOnly: Boolean = false): List<Offset>? {
        val bounds = field.bounds
        val step = (keel).coerceAtLeast(1f)
        val plan = hull * room
        val middle = if (clearSegment(ea, eb, field.coasts, plan, bounds, step)) listOf(ea, eb)
            else search(ea, eb, field, plan)?.let { pull(it, field.coasts, plan, bounds, step) } ?: return null
        // 7.9.13: the turn at the dock exits is smoothed too (it was a corner of up to ~107°). The whole line berth → berth
        // is cut round; a piece inside a dock circle (around a berth, radius = its dock piece) needs the keel's water,
        // every other piece the whole hull. The first (smoothest) checked result wins.
        val whole = listOf(a) + middle + listOf(b)
        val ra = (ea - a).getDistance(); val rb = (eb - b).getDistance()
        // wide curves first; then the same with the long pieces cut into short ones, so a corner next to a coast is
        // rounded within a few hulls instead of a quarter of its long arms
        for (way in listOf(whole, densify(whole, hull * 3))) for (rounds in 5 downTo (if (smoothOnly) 3 else 1)) {
            var s = way
            repeat(rounds) { s = chaikin(s) }
            if (checkedWay(s, ra, rb, field, hull, keel)) return s
        }
        if (smoothOnly) return null
        // else smooth the open middle only, as far as the result stays checked water; else the tight polyline
        for (rounds in 3 downTo 1) {
            var s = middle
            repeat(rounds) { s = chaikin(s) }
            if (s.zipWithNext().all { (p, q) -> clearSegment(p, q, field.coasts, hull, bounds, step) }) return listOf(a) + s + listOf(b)
        }
        // the tight polyline is the last answer only if it is checked water as well (never an unchecked piece over land)
        return if (middle.zipWithNext().all { (p, q) -> clearSegment(p, q, field.coasts, hull, bounds, step) }) listOf(a) + middle + listOf(b) else null
    }

    /** 7.9.13: the sharpest turn a drawn (= sailed) line may have between two neighbouring pieces, degrees. */
    const val MAX_TURN_DEG = 30f

    /** The sharpest turn of [p] between neighbouring pieces (degrees; pieces shorter than .5 px are skipped). */
    fun maxTurn(p: List<Offset>): Float {
        var worst = 0f
        val q = p.filterIndexed { i, x -> i == 0 || (x - p[i - 1]).getDistance() >= .5f }
        for (k in 1 until q.size - 1) {
            val a = q[k] - q[k - 1]; val c = q[k + 1] - q[k]
            val cos = ((a.x * c.x + a.y * c.y) / (a.getDistance() * c.getDistance())).coerceIn(-1f, 1f)
            worst = maxOf(worst, Math.toDegrees(kotlin.math.acos(cos).toDouble()).toFloat())
        }
        return worst
    }

    /**
     * Every piece of [s] keeps the whole hull in water (7.9.13: the dock pieces too, the berths are hull water, see
     * [moor]); outside the dock circles (radius [ra] / [rb] around the berths) it also stays inside the boats' sea.
     */
    private fun checkedWay(s: List<Offset>, ra: Float, rb: Float, field: Field, hull: Float, keel: Float): Boolean {
        val a = s.first(); val b = s.last(); val step = keel.coerceAtLeast(1f)
        fun inDock(p: Offset) = (p - a).getDistance() <= ra || (p - b).getDistance() <= rb
        return s.zipWithNext().all { (p, q) -> clearSegment(p, q, field.coasts, hull, if (inDock(p) && inDock(q)) null else field.bounds, step) }
    }

    /**
     * A boat's own lane: the route shifted [side] px sideways in its open middle (0 at both berths); taken only if every
     * piece of it is checked water (middle: hull, dock pieces: keel), else smaller shifts, finally the route itself.
     */
    fun lane(path: List<Offset>, side: Float, field: Field, hull: Float, keel: Float): List<Offset> {
        if (path.size < 4 || side == 0f) return path
        // the same dock circles as the route (7.9.13: the smoothed route has many points inside them)
        val a = path.first(); val b = path.last()
        fun radius(p: Offset, other: Offset) = listOfNotNull(dock(p, field, hull, keel, field.bounds, toward = other), dock(p, field, hull, keel, field.bounds))
            .maxOfOrNull { (it - p).getDistance() } ?: 0f
        val ra = radius(a, b); val rb = radius(b, a)
        for (f in listOf(1f, .75f, .5f, .25f)) {
            val l = HarbourRoute.lane(path, side * f)
            // a shift wider than a bend's radius folds the lane into a loop: such a lane is no curve, take a smaller one
            if (maxTurn(l) <= MAX_TURN_DEG && checkedWay(l, ra, rb, field, hull, keel)) return l
        }
        return path
    }
}
