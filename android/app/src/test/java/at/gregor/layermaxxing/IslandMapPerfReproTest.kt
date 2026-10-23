package at.gregor.layermaxxing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 7.9.14 performance repro (JVM, synthetic data only: no real accounts, no database, no device). The archipelago
 * map's geometry exactly as IslandMap computes it (411 × 891 dp at xxhdpi, every friend a v6 island with a harbour
 * on a real socket, several letter boats per friend): coasts, moorings, the water field, the routes, the boats' lanes
 * and the per-frame drawing preparation. Measured before the fix (same machine, JVM): routes 27 919 and lanes 145 686
 * exact clearance probes, 304 ms / 1651 ms; drawing preparation 509 ms per 60 frames (PERFORMANCE.md). The fix must
 * give the very same geometry (bit for bit, see [fieldDecidesExactlyLikeTheExactClearance] and [partAndAtAreTheOldOnes]).
 */
class IslandMapPerfReproTest {
    private val density = 3f
    private val w = 411 * density; private val h = 891 * density

    private fun construction(land: Int, stage: Int) = ApiClient.IslandConstruction(land = land, gridVersion = PlacementRule.GROUND,
        buildings = mapOf("HOUSE" to 1) + (if (stage > 0) mapOf("HARBOUR" to stage) else emptyMap()),
        starters = if (stage == 0) setOf("HARBOUR") else emptySet(),
        positions = mapOf("HOUSE" to listOf(8, 6), "HARBOUR" to IslandGround.harbourSpots(stage, land).let { it[it.size / 2] }))

    internal class Map(val coasts: List<SeaChart.Coast>, val field: SeaChart.Field, val home: Offset, val berths: List<Offset>, val hull: Float, val keel: Float)

    internal fun map(friends: Int): Map {
        val center = Offset(w / 2, h / 2)
        val spots = IsleLayout.positions(friends)
        val hubPx = 180 * density
        val hubTopLeft = Offset(center.x - hubPx / 2, center.y - hubPx * .42f)
        val home = construction(5, 2)
        val cons = List(friends) { i -> construction(i % 6, (i % 4)) }
        val boxes = spots.mapIndexed { i, (x, y) -> val fw = (104 + cons[i].land * 10) * density; Offset(w * x - fw / 2, h * y - fw * .5f) to fw }
        val hull = 12 * density; val keel = 4 * density
        val coasts = boxes.mapIndexed { i, (tl, fw) -> SeaChart.coastOf(tl, fw, cons[i].land) } + SeaChart.coastOf(hubTopLeft, hubPx, home.land)
        fun moor(tl: Offset, px: Float, c: ApiClient.IslandConstruction): Offset {
            val raw = HarbourRoute.onMap(tl, px, HarbourRoute.berth(c))
            return (SeaChart.moor(raw, HarbourRoute.onMap(tl, px, HarbourRoute.fallbackBerth(c.land)), coasts, keel,
                HarbourRoute.LEGACY_REACH_CELLS * IslandGrid.step(c.land) * px, HarbourRoute.atSocket(c), hull) ?: SeaChart.Mooring(raw, true)).point
        }
        val homeBerth = moor(hubTopLeft, hubPx, home)
        val berths = boxes.mapIndexed { i, (tl, fw) -> moor(tl, fw, cons[i]) }
        val boatSea = Rect(42 * density, 21 * density, w - 42 * density, h - 21 * density)
        val field = SeaChart.Field(coasts, boatSea, maxOf(hull * .6f, 4f))
        return Map(coasts, field, homeBerth, berths, hull, keel)
    }

    private inline fun <T> timed(label: String, block: () -> T): T {
        val t0 = System.nanoTime(); val p0 = SeaChart.probes
        val r = block()
        println("PERF $label: ${(System.nanoTime() - t0) / 1_000_000} ms, ${SeaChart.probes - p0} exact clearance probes")
        return r
    }

    @Test fun measureArchipelago() {
        val friends = 12; val lettersPerFriend = 3
        // warm-up (JIT), then the measured run
        map(4).let { m -> SeaChart.route(m.home, m.berths[0], m.field, m.hull, m.keel) }
        val m = timed("coasts+moorings+field ($friends friends)") { map(friends) }
        println("PERF field cells: ${m.field.cols * m.field.rows}")
        val routes = timed("routes ($friends)") { m.berths.map { SeaChart.route(m.home, it, m.field, m.hull, m.keel) } }
        println("PERF route points: ${routes.map { it?.size }}")
        val laneGap = 46 * density
        val lanes = timed("lanes ($friends × $lettersPerFriend boats, every candidate)") {
            routes.filterNotNull().flatMap { r ->
                IsleMotion.laneOffsets(lettersPerFriend).flatMap { own ->
                    IsleMotion.laneCandidates(own).map { it * laneGap }.filter { kotlin.math.abs(it) <= laneGap * 2.2f }.map { side -> SeaChart.lane(r, side, m.field, m.hull, m.keel) }
                }
            }
        }
        println("PERF lanes computed: ${lanes.size}, points: ${lanes.sumOf { it.size }}")
        // one frame of the map's draw lambda: per boat the dotted part ahead and the 4 wake pieces
        val boats = routes.filterNotNull().flatMap { r -> List(lettersPerFriend) { r } }
        timed("draw prep, 60 frames (${boats.size} boats)") {
            repeat(60) { boats.forEachIndexed { k, p ->
                val t = (k % 10) / 10f
                HarbourRoute.part(p, 0f, t); HarbourRoute.length(p)
                for (q in 0 until 4) HarbourRoute.part(p, t, t + .02f, 6)
            } }
        }
        // the exact geometry (float bits) for comparing two implementations, only when asked for
        System.getenv("LM_PERF_DUMP")?.let { out ->
            fun bits(p: List<Offset>?) = p?.joinToString(" ") { "${it.x.toRawBits()},${it.y.toRawBits()}" } ?: "null"
            java.io.File(out).printWriter().use { f ->
                f.println("home ${bits(listOf(m.home))}"); f.println("berths ${bits(m.berths)}")
                routes.forEach { f.println("route ${bits(it)}") }
                lanes.forEach { f.println("lane ${bits(it)}") }
                boats.forEachIndexed { k, p -> val t = (k % 10) / 10f
                    f.println("part ${bits(HarbourRoute.part(p, 0f, t))} ${HarbourRoute.length(p).toRawBits()} ${bits(HarbourRoute.part(p, t, t + .02f, 6))} ${bits(listOf(HarbourRoute.at(p, t)))}") }
            }
        }
    }

    /** 12 islands, 3 boats each: the routes and every candidate lane stay far below the 7.9.13 work (exact probes). */
    @Test fun routesAndLanesStayWithinTheirWorkBudget() {
        val m = map(12)
        var p0 = SeaChart.probes
        val routes = m.berths.map { SeaChart.route(m.home, it, m.field, m.hull, m.keel) }
        val routeProbes = SeaChart.probes - p0
        assertTrue("routes found", routes.count { it != null } >= 10)
        p0 = SeaChart.probes
        val laneGap = 46 * density
        routes.filterNotNull().forEach { r -> IsleMotion.laneOffsets(3).forEach { own ->
            IsleMotion.laneCandidates(own).map { it * laneGap }.filter { kotlin.math.abs(it) <= laneGap * 2.2f }.forEach { SeaChart.lane(r, it, m.field, m.hull, m.keel) } } }
        val laneProbes = SeaChart.probes - p0
        println("PERF budget: routes $routeProbes, lanes $laneProbes exact probes")
        assertTrue("routes $routeProbes exact probes (7.9.13: 27 919)", routeProbes <= 27_919 / 3)
        assertTrue("lanes $laneProbes exact probes (7.9.13: 145 686)", laneProbes <= 145_686 / 10)
    }

    /** The grid's shortcut never decides differently from the exact clearance (dense synthetic samples, every need used). */
    @Test fun fieldDecidesExactlyLikeTheExactClearance() {
        val m = map(12)
        val needs = listOf(m.keel, m.hull, m.hull * 1.05f, m.hull * 1.3f, m.hull * 1.5f, m.hull * 1.6f)
        var checked = 0; var shortcut = 0
        var y = -40f
        while (y < h + 40f) {
            var x = -40f
            while (x < w + 40f) {
                val p = Offset(x, y)
                val exact = SeaChart.clearance(p, m.coasts)
                for (need in needs) {
                    val p0 = SeaChart.probes
                    assertEquals("$p need $need (exact $exact)", exact >= need, m.field.atLeast(p, need))
                    if (SeaChart.probes == p0) shortcut++
                    checked++
                }
                x += 13.7f
            }
            y += 13.7f
        }
        println("PERF field shortcut decided $shortcut of $checked checks")
        assertTrue("the grid decides most checks", shortcut > checked / 2)
    }

    /** The 7.9.13 path walk, kept here as the reference the faster one must match bit for bit. */
    private fun oldLength(path: List<Offset>): Float = path.zipWithNext().sumOf { (p, q) -> (q - p).getDistance().toDouble() }.toFloat()
    private fun oldAt(path: List<Offset>, t: Float): Offset {
        val target = oldLength(path) * t.coerceIn(0f, 1f)
        var run = 0f
        for ((p, q) in path.zipWithNext()) {
            val l = (q - p).getDistance()
            if (run + l >= target) return if (l < 1e-3f) p else p + (q - p) * ((target - run) / l)
            run += l
        }
        return path.last()
    }
    private fun oldPart(path: List<Offset>, t0: Float, t1: Float, steps: Int = 32) = (0..steps).map { oldAt(path, t0 + (t1 - t0) * it / steps) }

    @Test fun partAndAtAreTheOldOnes() {
        val m = map(12)
        val paths = m.berths.mapNotNull { SeaChart.route(m.home, it, m.field, m.hull, m.keel) } +
            // degenerate pieces: a repeated point, a zero-length path, a single point
            listOf(listOf(Offset(1f, 1f), Offset(1f, 1f), Offset(5f, 1f), Offset(5f, 1f), Offset(5f, 9f)), listOf(Offset(3f, 3f), Offset(3f, 3f)), listOf(Offset(2f, 2f)))
        val shares = listOf(-.2f, 0f, .03f, .1f, .25f, .5f, .5001f, .77f, .99f, 1f, 1.3f)
        for (p in paths) {
            assertEquals(oldLength(p).toRawBits(), HarbourRoute.length(p).toRawBits())
            for (t in shares) assertEquals(oldAt(p, t), HarbourRoute.at(p, t))
            for (a in shares) for (b in shares) for (steps in listOf(1, 6, 32)) assertEquals("$a..$b/$steps", oldPart(p, a, b, steps), HarbourRoute.part(p, a, b, steps))
        }
    }

    /** Picking up a piece on a full v6 island (land 5, every building, 40 decorations): what the targets cost. */
    @Test fun measurePlacementTargets() {
        val spots = IslandGround.spots(5, false)
        val base = construction(5, 3).copy(buildings = IsleBuilding.entries.associate { it.name to 2 } + ("HARBOUR" to 3), starters = emptySet())
        val placed = IsleBuilding.entries.filter { it != IsleBuilding.HARBOUR }.fold(base) { c, b ->
            IslandLayout.targets(c, IslandTarget.Building(b)).firstOrNull()?.let { c.copy(positions = c.positions + (b.name to it)) } ?: c }
        val full = (1..40).fold(placed) { c, id ->
            val free = IslandLayout.targets(c, IslandTarget.Decor("decor_flowers", null)).firstOrNull() ?: return@fold c
            c.copy(decor = c.decor + ApiClient.IslandDecor(id, "decor_flowers", free))
        }
        println("PERF placement: ${full.decor.size} decorations on ${spots.size} meadow spots")
        repeat(3) { IslandLayout.targets(full, IslandTarget.Decor("decor_bench", null)) }
        val t0 = System.nanoTime()
        val decor = IslandLayout.targets(full, IslandTarget.Decor("decor_bench", null))
        val t1 = System.nanoTime()
        val harbour = IslandLayout.targets(full, IslandTarget.Building(IsleBuilding.HARBOUR))
        val t2 = System.nanoTime()
        val drag = (0 until 60).count { IslandLayout.problem(full, IslandTarget.Decor("decor_bench", null), spots[it % spots.size]) == null }
        val t3 = System.nanoTime()
        println("PERF placement targets: decor ${decor.size} in ${(t1 - t0) / 1_000_000} ms, harbour ${harbour.size} in ${(t2 - t1) / 1_000_000} ms, 60 drag checks ($drag green) in ${(t3 - t2) / 1_000_000} ms")
    }
}
