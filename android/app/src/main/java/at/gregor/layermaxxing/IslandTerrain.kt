package at.gregor.layermaxxing

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp

/**
 * Islands are assembled at runtime: one painted, EMPTY terrain base (coast,
 * cliffs, lawns, paths, no buildings) plus single sprites for every building,
 * tree and decoration, depth-sorted with contact shadows; trees sway, light
 * breathes on the water. Friend islands mirror the base per owner and gain
 * buildings with every level.
 * A friendship island gains parts with every level; the home island carries the
 * menu buildings and the decoration lawns.
 */
internal data class IslandPiece(
    val res: Int,
    /** Bottom-centre anchor, fraction of the island box. */
    val x: Float,
    val y: Float,
    /** Size of the sprite's longer side, fraction of the box width. */
    val size: Float,
    val sway: Boolean = false,
    val name: String? = null,
    val primitive: IsleBuilding? = null,
    val tier: Int = 0,
    /** Picked up for moving (long press / placement): drawn raised above its footprint. */
    val lifted: Boolean = false,
)

internal data class IslandPlan(
    val seed: Long,
    /** 0..1: how much of the box the land fills. */
    val scale: Float,
    val pieces: List<IslandPiece>,
    val paths: List<Pair<Offset, Offset>>,
    /** Friend islands flip the painted base so neighbours never look identical. */
    val mirror: Boolean = false,
    /** 0..1: Insel-Ausbau land size from the point score (1 = full painted island). */
    val landScale: Float = 1f,
    /** 7.9.13: narrow trodden paths between the standing buildings' doors and the beach (box fractions), [IslandTrails]. */
    val trails: List<List<Offset>> = emptyList(),
)

internal object IslandPlans {
    /** Painted terrain base (terrain_home) is 1367 x 1116. */
    const val ASPECT = 1367f / 1116f
    private const val CX = .5f
    private const val CY = .55f
    private const val RX = .46f
    private const val RY = .40f

    /** Land is within this normalised radius (grass = .9, sand = 1). */
    fun inside(x: Float, y: Float, scale: Float = 1f, radius: Float = .9f): Boolean {
        val u = (x - CX) / (RX * scale); val v = (y - CY) / (RY * scale)
        return u * u + v * v <= radius * radius
    }

    /**
     * Insel-Ausbau: how big the land itself is at a given point score. A fresh island
     * is a small sand mound; every built-upgrade grows the land a step (Aufschüttung).
     */
    fun landScale(score: Int): Float = when {
        score >= 240 -> 1f
        score >= 160 -> .86f
        score >= 100 -> .74f
        score >= 60 -> .62f
        score >= 25 -> .5f
        else -> .3f
    }
    /** How green the land is (0 = bare sand bank, 1 = full meadow) for a land scale. */
    fun meadow(landScale: Float): Float = ((landScale - .46f) / (1f - .46f)).coerceIn(0f, 1f)
    /** Things on a small island shrink a little so the sand bank is not crammed. */
    fun pieceScale(landScale: Float): Float = .6f + .4f * landScale
    /**
     * On a small sand bank the five signs would pile up at the shore; spread them apart
     * (fraction of island width/height). Zero on the full island.
     */
    fun tagNudge(b: IsleBuilding, landScale: Float): Pair<Float, Float> {
        val k = ((1f - landScale) / .7f).coerceIn(0f, 1f)
        val (dx, dy) = when (b) {
            IsleBuilding.CAMPFIRE -> -.11f to 0f
            IsleBuilding.LIBRARY -> .11f to 0f
            IsleBuilding.HARBOUR -> 0f to .05f
            IsleBuilding.POST -> -.05f to 0f
            IsleBuilding.LIGHTHOUSE -> .05f to 0f
            else -> 0f to 0f
        }
        return dx * k to dy * k
    }
    /** Anchor a land point onto the scaled island (same centre the terrain layer scales about). */
    fun onLand(x: Float, y: Float, s: Float): Pair<Float, Float> = (CX + (x - CX) * s) to (CY + (y - CY) * s)
    const val LAND_CX = CX
    const val LAND_CY = CY

    /** Home: the menu buildings around a plaza. Anchors were picked so paths and lawns never cross. */
    /** The app's own places sit at the coast; the inland plots belong to the player's real life (LifePlaces). */
    val homeBuildings = linkedMapOf(
        // 7.9.7: default spots are cells of the build grid of every land stage (the old coast spots were in the water).
        IsleBuilding.HOUSE to IslandPiece(R.drawable.b_house, .50f, .30f, .11f, name = "Mein Haus"),
        IsleBuilding.LIGHTHOUSE to IslandPiece(R.drawable.lm_lighthouse, .80f, .45f, .105f, name = "Freunde"),
        IsleBuilding.POST to IslandPiece(R.drawable.lm_post, .20f, .45f, .105f, name = "Post"),
        IsleBuilding.CAMPFIRE to IslandPiece(R.drawable.lm_campfire, .35f, .70f, .10f, name = "Gruppen"),
        IsleBuilding.LIBRARY to IslandPiece(R.drawable.lm_library, .65f, .70f, .10f, name = "Wörterbuch"),
        IsleBuilding.HARBOUR to IslandPiece(R.drawable.lm_jetty, .50f, .75f, .115f, name = "Hafen"),
    )
    val plaza = Offset(.5f, .55f)
    /** Small decoration lawns ringing the plaza, clear of the building plots. */
    val homeSlots = listOf(.41f to .45f, .59f to .45f, .36f to .53f, .64f to .53f, .43f to .61f, .57f to .61f)

    /**
     * Where each app building stands for a score: ruin or finished sprite, moved onto the
     * grown land and sized for it. Sprites, signs and touch targets all read from here.
     */
    fun constructionScale(land: Int): Float = listOf(.46f, .58f, .70f, .82f, .92f, 1f)[land.coerceIn(0, 5)]

    fun placedBuildings(score: Int, construction: ApiClient.IslandConstruction? = null): Map<IsleBuilding, IslandPiece> {
        val s = construction?.let { constructionScale(it.land) } ?: landScale(score)
        return homeBuildings.filter { (b, _) -> if (construction != null) construction.standing(b) else score >= 25 }.mapValues { (b, p) ->
            val tier = if (construction != null) construction.buildings[b.name] ?: 0 else if (score >= 25) 1 else 0
            val position = construction?.positions?.get(b.name)
            val (x, y) = onLand(position?.get(0)?.div(20f) ?: p.x, position?.get(1)?.div(20f) ?: p.y, s)
            // 7.9.12: on a construction ledger the size is the one its occupancy mask was measured at (IslandMasks,
            // the same file the server validates with): sprite, touch target and grid can never disagree.
            val size = construction?.let { IslandMasks.building(b, tier, it.land).size } ?: (p.size * (.9f + tier * .16f)).coerceAtMost(.145f)
            p.copy(res = IslandArtwork.resource(b, tier, construction), x = x, y = y, size = size,
                primitive = b.takeIf { tier == 0 }, tier = tier)
        }
    }

    fun home(seed: Long, score: Int = 0, construction: ApiClient.IslandConstruction? = null): IslandPlan {
        val s = construction?.let { constructionScale(it.land) } ?: landScale(score)
        val nature = if (s < .7f) emptyList() else listOf(
            IslandPiece(R.drawable.n_pine, .17f, .30f, .07f, sway = true),
            IslandPiece(R.drawable.n_pine, .83f, .29f, .065f, sway = true),
            IslandPiece(R.drawable.n_tree, .14f, .66f, .07f, sway = true),
            IslandPiece(R.drawable.n_cypress, .88f, .66f, .06f, sway = true),
        ).map { n -> val (x,y) = onLand(n.x,n.y,s); n.copy(x=x,y=y) }
        val trails = construction?.takeIf { it.supported }?.let { c -> IslandTrails.of(c).map { t -> t.map { (x, y) -> onLand(x / 20f, y / 20f, s).let { (fx, fy) -> Offset(fx, fy) } } } }.orEmpty()
        return IslandPlan(seed, 1f, placedBuildings(score, construction).values.toList() + nature, emptyList(), landScale = s, trails = trails)
    }

    /** What a friendship island holds at each level, placed on the painted clearings. */
    private data class Part(val level: Int, val res: Int, val x: Float, val y: Float, val size: Float, val sway: Boolean = false)
    private val friendParts = listOf(
        Part(1, R.drawable.b_house, .48f, .31f, .26f),
        Part(1, R.drawable.n_tree, .30f, .44f, .13f, sway = true),
        Part(1, R.drawable.n_flowerbush, .62f, .52f, .08f),
        Part(2, R.drawable.b_workshop, .75f, .40f, .22f),
        Part(2, R.drawable.n_pine, .24f, .70f, .13f, sway = true),
        Part(2, R.drawable.n_bush, .40f, .66f, .07f),
        Part(3, R.drawable.b_post, .26f, .45f, .20f),
        Part(3, R.drawable.n_cypress, .68f, .66f, .10f, sway = true),
        Part(3, R.drawable.n_crates, .55f, .74f, .07f),
        Part(4, R.drawable.b_lighthouse, .76f, .66f, .20f),
        Part(4, R.drawable.b_hall, .25f, .72f, .20f),
        Part(4, R.drawable.n_tree, .62f, .25f, .10f, sway = true),
    )

    /** Land grows with the friendship; parts keep their on-screen size while the island gets bigger. */
    fun growth(level: Int) = Isle.islandWidth(level).value / Isle.islandWidth(1).value

    fun friend(level: Int, seed: Long): IslandPlan {
        val lv = level.coerceIn(1, 4)
        // From level 3 the post hut takes the left clearing where the first tree stood.
        val parts = friendParts.filter { it.level <= lv && !(lv >= 3 && it.level == 1 && it.res == R.drawable.n_tree) }
        val g = growth(lv)
        val pieces = parts.map { p -> IslandPiece(p.res, p.x, p.y, (p.size / g).coerceAtLeast(p.size * .7f), p.sway) }
        return IslandPlan(seed, 1f, pieces, emptyList(), mirror = seed % 2L == 1L)
    }


}

/**
 * 7.9.14: raster pictures the island screens show many times at once (the painted terrain under every island of the
 * map, the letter boats), decoded once per process and shared. painterResource decodes a raster resource again for
 * every place it is used (Compose UI 1.7: one remember per call site), so a map of 13 islands decoded the 1100 × 898
 * terrain 13 times on the main thread. Same picture, same drawing, one decode.
 */
internal object IslandBitmaps {
    private val cache = java.util.concurrent.ConcurrentHashMap<Int, androidx.compose.ui.graphics.ImageBitmap>()
    private val decoded = java.util.concurrent.atomic.AtomicInteger()
    /** How many pictures were really decoded (tests). */
    val decodes: Int get() = decoded.get()
    fun cached(id: Int): Boolean = cache.containsKey(id)
    fun of(res: android.content.res.Resources, id: Int): androidx.compose.ui.graphics.ImageBitmap =
        cache.getOrPut(id) { decoded.incrementAndGet(); androidx.compose.ui.graphics.ImageBitmap.imageResource(res, id) }
}

@Composable
internal fun islandBitmap(id: Int): androidx.compose.ui.graphics.ImageBitmap =
    IslandBitmaps.of(androidx.compose.ui.platform.LocalContext.current.resources, id)

/** Terrain + paths + parts. [overlay] draws labels/slots on top in the same box coordinates. */
@Composable
internal fun DynamicIsland(
    plan: IslandPlan,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    /** Edit mode: buildings and decoration wiggle a little, like movable pieces in a base-builder. */
    wobble: Boolean = false,
    /** 0..1: how far a lifted (held) piece has risen off the ground. */
    lift: Float = 1f,
    /** Drawn between terrain and sprites, so sprites cover grid and footprint (the footprint lies on the ground). */
    underlay: @Composable BoxWithConstraintsScope.() -> Unit = {},
    overlay: @Composable BoxWithConstraintsScope.() -> Unit = {},
) {
    val t = rememberInfiniteTransition(label = "isle")
    val sway by if (animate) t.animateFloat(-1f, 1f, infiniteRepeatable(tween(5200), RepeatMode.Reverse), label = "sway")
    else remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val wiggle by if (wobble) t.animateFloat(-1f, 1f, infiniteRepeatable(tween(190), RepeatMode.Reverse), label = "wiggle")
    else remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    BoxWithConstraints(modifier.aspectRatio(IslandPlans.ASPECT)) {
        val w = maxWidth; val h = maxHeight
        val ls = plan.landScale
        // Gently breathing light on the shallow water, under the painted island.
        Canvas(Modifier.fillMaxSize()) {
            drawOval(
                Color.White.copy(alpha = .10f),
                topLeft = Offset(size.width * (IslandPlans.LAND_CX - .47f * ls), size.height * (IslandPlans.LAND_CY - .46f * ls)),
                size = androidx.compose.ui.geometry.Size(size.width * .94f * ls, size.height * .92f * ls),
            )
        }
        // Insel-Ausbau: the land grows with upgrades. 7.9.13: the painted terrain (meadow, the beach of the southern cove,
        // rocks, water) is the same on every land stage, because its zones are the build rule (IslandGround): a small
        // island's meadow is only drier (less saturated), never a bare sand bank that looks like beach everywhere.
        val landLayer = Modifier.fillMaxSize().graphicsLayer {
            val k = ls * (if (plan.mirror) -1f else 1f)
            scaleX = k; scaleY = ls
            transformOrigin = TransformOrigin(IslandPlans.LAND_CX, IslandPlans.LAND_CY)
        }
        val green = IslandPlans.meadow(ls)
        // Painted terrain in the app's existing coast/detail style.
        // Do not replace it with geometric Canvas terrain: it breaks the visual family.
        val dry = remember(green) {
            androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(.62f + .38f * green) })
        }
        Image(islandBitmap(R.drawable.terrain_home), null, landLayer, contentScale = ContentScale.FillBounds,
            colorFilter = if (green < 1f) dry else null)
        if (plan.trails.isNotEmpty()) Canvas(Modifier.fillMaxSize()) {
            // Trodden paths: a soft worn edge and a narrow sandy middle, under every sprite and shadow.
            val wide = size.width * ls / IslandGrid.STEPS * .45f // a little less than half a cell
            plan.trails.forEach { t ->
                val path = androidx.compose.ui.graphics.Path().apply {
                    t.forEachIndexed { n, o -> val px = (if (plan.mirror) 1f - o.x else o.x) * size.width; if (n == 0) moveTo(px, o.y * size.height) else lineTo(px, o.y * size.height) }
                }
                drawPath(path, Color(0x24B59A62), style = androidx.compose.ui.graphics.drawscope.Stroke(wide * 1.7f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                drawPath(path, Color(0x66D8C69A), style = androidx.compose.ui.graphics.drawscope.Stroke(wide * .7f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            // Soft contact shadows ground every part on the grass.
            plan.pieces.forEach { pc ->
                val sw = size.width * pc.size * (if (pc.lifted) .82f else .62f)
                val px = (if (plan.mirror) 1f - pc.x else pc.x) * size.width
                drawOval(
                    if (pc.lifted) Color(0x44000000) else Color(0x26000000),
                    topLeft = Offset(px - sw / 2, pc.y * size.height - sw * .12f),
                    size = androidx.compose.ui.geometry.Size(sw, sw * .24f),
                )
            }
        }
        underlay()
        plan.pieces.sortedBy { it.y }.forEachIndexed { i, p ->
            val box: Dp = w * p.size
            val building = p.name?.let { name -> IsleBuilding.entries.firstOrNull { it.label == name } }
            PaintedIslandPlace(building, p.tier, p.res, description = p.name, onGround = true,
                modifier = Modifier.offset(x = w * (if (plan.mirror) 1f - p.x else p.x) - box / 2, y = h * p.y - box).size(box)
                    .then(
                        if (p.lifted) Modifier.graphicsLayer {
                            // Picked up: rises off the ground a little and grows, like a held game piece.
                            translationY = -box.toPx() * .16f * lift; scaleX = 1f + .08f * lift; scaleY = 1f + .08f * lift
                            rotationZ = wiggle * 1.2f * lift
                            transformOrigin = TransformOrigin(.5f, 1f)
                        } else if (wobble && p.name != null) Modifier.graphicsLayer {
                            // Edit mode: movable pieces wiggle, neighbours out of phase.
                            rotationZ = wiggle * (if (i % 2 == 0) 1.6f else -1.6f)
                            transformOrigin = TransformOrigin(.5f, 1f)
                        } else if (p.sway || p.primitive == IsleBuilding.POST || p.primitive == IsleBuilding.LIGHTHOUSE) Modifier.graphicsLayer {
                            rotationZ = sway * (1.4f + (i % 3) * .5f)
                            transformOrigin = TransformOrigin(.5f, 1f)
                        } else Modifier,
                    ),
            )
        }
        overlay()
    }
}
/**
 * 7.9.13: natural trodden paths, derived from what stands NOW (never from a fixed centre, never from a piece that is not
 * there): every standing building's door (half a cell in front of its ground) and the beach access above the harbour
 * are joined by the shortest tree of short links; each link is a gentle curve that keeps off every other piece's ground,
 * rocks and water (a link that cannot is left out). Paths are pure decoration: they never block a spot and are computed
 * again from the construction, so they follow every build and move (also the held piece's preview).
 */
internal object IslandTrails {
    /** Polylines in lattice units (x, y = i, j as floats). */
    fun of(c: ApiClient.IslandConstruction): List<List<Pair<Float, Float>>> {
        val land = c.land.coerceIn(0, 5)
        val standing = IsleBuilding.entries.filter { c.standing(it) }
        fun spot(b: IsleBuilding) = c.positions[b.name] ?: IslandLayout.defaultPosition(b)
        val grounds = standing.associateWith { b -> IslandLayout.cellsAt(c, IslandTarget.Building(b), spot(b)) }
        val decor = c.decor.flatMap { d -> IslandLayout.cellsAt(c, IslandTarget.Decor(d.item, d.id), d.position) }.toSet()
        // the nodes: doors in front of the ground; for the harbour the beach access at the meadow's edge above it
        val nodes = standing.map { b ->
            val p = spot(b)
            if (b == IsleBuilding.HARBOUR) {
                val top = (IslandGround.spots(land, false).filter { it[0] == p[0].coerceIn(0, IslandGrid.STEPS) }.maxOfOrNull { it[1] } ?: p[1] - 2)
                b to (p[0].toFloat() to top + .55f)
            } else b to (p[0].toFloat() to p[1] + .55f)
        }
        if (nodes.size < 2) return emptyList()
        fun d(a: Pair<Float, Float>, b: Pair<Float, Float>) = kotlin.math.hypot(a.first - b.first, a.second - b.second)
        // Prim: the shortest tree over the doors (deterministic: ties by building order)
        val inTree = mutableListOf(0)
        val links = mutableListOf<Pair<Int, Int>>()
        while (inTree.size < nodes.size) {
            var best: Triple<Int, Int, Float>? = null
            for (i in inTree) for (j in nodes.indices) if (j !in inTree) {
                val dd = d(nodes[i].second, nodes[j].second)
                if (best == null || dd < best.third) best = Triple(i, j, dd)
            }
            inTree += best!!.second; links += best.first to best.second
        }
        return links.mapNotNull { (i, j) ->
            val (ba, a) = nodes[i]; val (bb, b) = nodes[j]
            val blocked = grounds.filterKeys { it != ba && it != bb }.values.flatten().toSet() + decor
            curve(a, b, blocked, land)
        }
    }

    /** A gentle quadratic curve a → b that stays on meadow/beach and off [blocked] cells, or null. */
    private fun curve(a: Pair<Float, Float>, b: Pair<Float, Float>, blocked: Set<List<Int>>, land: Int): List<Pair<Float, Float>>? {
        val dx = b.first - a.first; val dy = b.second - a.second
        val len = kotlin.math.hypot(dx, dy).takeIf { it > .5f } ?: return null
        val nx = -dy / len; val ny = dx / len
        for (bend in listOf(.12f, -.12f, .3f, -.3f, .5f, -.5f)) {
            val cx = (a.first + b.first) / 2 + nx * bend * len; val cy = (a.second + b.second) / 2 + ny * bend * len
            val pts = (0..24).map { k ->
                val t = k / 24f; val u = 1 - t
                (u * u * a.first + 2 * u * t * cx + t * t * b.first) to (u * u * a.second + 2 * u * t * cy + t * t * b.second)
            }
            val ok = pts.all { (x, y) ->
                val cell = listOf(kotlin.math.round(x).toInt(), kotlin.math.round(y).toInt())
                cell !in blocked && IslandGround.zone(cell).let { z -> z == 'B' || (z == 'L' && IslandGrid.buildable(cell, land)) }
            }
            if (ok) return pts
        }
        return null
    }
}
