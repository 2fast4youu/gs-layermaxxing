package at.gregor.layermaxxing

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * THE projection of the island's build grid. Everything that has a place on the own island or on
 * a visited island is drawn through [IslandPlans.onLand]; this object is the same mapping seen
 * from the grid's side, so the drawn cells, the placement preview, the snapping of a finger and the
 * saved building can never drift apart:
 *
 *  - stored coordinates stay the server's integer 0..20 cells ([project] is exactly what
 *    [IslandPlans.placedBuildings] and [decorPieces] use, so existing layouts look identical),
 *  - the cells are drawn as an isometric diamond mesh through those lattice points: its lines run
 *    along the diagonals of the lattice, so they are never axis-parallel and follow the oblique view
 *    of the painted island,
 *  - a footprint is a diamond around a lattice point whose corners are lattice points, i.e. it lies
 *    exactly on the mesh,
 *  - [unproject] is the inverse of [project] (finger -> nearest cell).
 */
internal object IslandGrid {
    const val STEPS = 20
    /** Height/width of one grid diamond on screen: the island box is [IslandPlans.ASPECT] wide per unit of height. */
    const val DIAMOND_ASPECT = 1f / IslandPlans.ASPECT

    /** Distance between two neighbouring cells as a fraction of the island box (same for width and height). */
    fun step(land: Int): Float = IslandPlans.constructionScale(land) / STEPS
    fun x(i: Int, land: Int): Float = IslandPlans.onLand(i / 20f, 0f, IslandPlans.constructionScale(land)).first
    fun y(j: Int, land: Int): Float = IslandPlans.onLand(0f, j / 20f, IslandPlans.constructionScale(land)).second
    /** Cell -> box fraction (x of width, y of height). */
    fun project(cell: List<Int>, land: Int): Pair<Float, Float> = x(cell[0], land) to y(cell[1], land)
    /** Box fraction -> nearest cell on the grid, clamped to the 0..20 range the server accepts. */
    fun unproject(fx: Float, fy: Float, land: Int): List<Int> {
        val s = IslandPlans.constructionScale(land)
        return listOf(
            (((fx - IslandPlans.LAND_CX) / s + IslandPlans.LAND_CX) * STEPS).roundToInt().coerceIn(0, STEPS),
            (((fy - IslandPlans.LAND_CY) / s + IslandPlans.LAND_CY) * STEPS).roundToInt().coerceIn(0, STEPS),
        )
    }
    /*
     * THE build grid (7.9.7), the same rule as the server's grid_buildable (server/app/main.py) and pinned
     * in server/app/island_grid.json: a lattice point is buildable when it lies on the sand/meadow inside
     * the painted rocks and shore. The lattice scales with the land like the painted terrain, so the area
     * grows on screen with every stage; the edge margin is constant on screen (a bit wider in cells on a
     * small island). 7.9.6 used the whole painted ellipse (r .95) for every stage: shore and water.
     */
    private const val GRID_CX = .5
    private const val GRID_CY = .47
    private const val GRID_RX = .37
    private const val GRID_RY = .33
    private const val GRID_EDGE = .035
    private val landFactor = doubleArrayOf(.46, .58, .70, .82, .92, 1.0)
    fun buildable(i: Int, j: Int, land: Int): Boolean {
        if (i !in 0..STEPS || j !in 0..STEPS) return false
        val r = 1.0 - GRID_EDGE / landFactor[land.coerceIn(0, 5)]
        val u = (i.toDouble() / STEPS - GRID_CX) / GRID_RX
        val v = (j.toDouble() / STEPS - GRID_CY) / GRID_RY
        return u * u + v * v <= r * r
    }
    fun buildable(p: List<Int>, land: Int): Boolean = p.size == 2 && buildable(p[0], p[1], land)
    private val cellsByLand = Array(6) { land -> lazy { buildList { for (i in 0..STEPS) for (j in 0..STEPS) if (buildable(i, j, land)) add(listOf(i, j)) } } }
    /** Every buildable cell of a land stage = exactly what the grid draws and what the server accepts. */
    fun buildableCells(land: Int): List<List<Int>> = cellsByLand[land.coerceIn(0, 5)].value
    /**
     * 7.9.10, the server's grid_coast: the rim of the build grid (a buildable cell with a non-buildable
     * 4-neighbour) is the coast, the only cells where the harbour may stand.
     */
    fun coast(i: Int, j: Int, land: Int): Boolean = buildable(i, j, land) &&
        (!buildable(i + 1, j, land) || !buildable(i - 1, j, land) || !buildable(i, j + 1, land) || !buildable(i, j - 1, land))
    fun coast(p: List<Int>, land: Int): Boolean = p.size == 2 && coast(p[0], p[1], land)
    private val coastByLand = Array(6) { land -> lazy { buildableCells(land).filter { coast(it, land) } } }
    fun coastCells(land: Int): List<List<Int>> = coastByLand[land.coerceIn(0, 5)].value
    /** Is a finger (box fraction) over a buildable cell? A tap beside the land is ignored. */
    fun onLand(fx: Float, fy: Float, land: Int): Boolean = unproject(fx, fy, land).let { buildable(it, land) }
    /**
     * Hard clamp of a finger (box fraction) to the grid: on land the snapped cell, in the water the land
     * cell nearest on screen (x distances count [IslandPlans.ASPECT] times as much as y). Never water.
     */
    fun nearestLandCell(fx: Float, fy: Float, land: Int): List<Int> {
        val cell = unproject(fx, fy, land)
        if (buildable(cell, land)) return cell
        val s = IslandPlans.constructionScale(land)
        val gx = ((fx - IslandPlans.LAND_CX) / s + IslandPlans.LAND_CX) * STEPS
        val gy = ((fy - IslandPlans.LAND_CY) / s + IslandPlans.LAND_CY) * STEPS
        return buildableCells(land).minBy { (i, j) -> val dx = (i - gx) * IslandPlans.ASPECT; val dy = j - gy; dx * dx + dy * dy }
    }
    /**
     * 7.9.10: the harbour's clamp. A finger anywhere (inland, on the shore, in the water) gives the coast cell
     * nearest on screen, so the held harbour only ever slides along the rim. A taken coast cell is returned
     * as it is (it turns red); it never jumps to some other free cell on its own.
     */
    fun nearestCoastCell(fx: Float, fy: Float, land: Int): List<Int> {
        val s = IslandPlans.constructionScale(land)
        val gx = ((fx - IslandPlans.LAND_CX) / s + IslandPlans.LAND_CX) * STEPS
        val gy = ((fy - IslandPlans.LAND_CY) / s + IslandPlans.LAND_CY) * STEPS
        return coastCells(land).minBy { (i, j) -> val dx = (i - gx) * IslandPlans.ASPECT; val dy = j - gy; dx * dx + dy * dy }
    }
    /** The cell of [cells] nearest on screen to a finger (box fraction). */
    fun nearestOf(cells: List<List<Int>>, fx: Float, fy: Float, land: Int): List<Int> {
        val s = IslandPlans.constructionScale(land)
        val gx = ((fx - IslandPlans.LAND_CX) / s + IslandPlans.LAND_CX) * STEPS
        val gy = ((fy - IslandPlans.LAND_CY) / s + IslandPlans.LAND_CY) * STEPS
        return cells.minBy { (i, j) -> val dx = (i - gx) * IslandPlans.ASPECT; val dy = j - gy; dx * dx + dy * dy }
    }
    /** The coast cell nearest to a stored cell (lifting a harbour that stands inland from old data). */
    fun coastCellNear(cell: List<Int>, land: Int): List<Int> =
        if (coast(cell, land)) cell
        else project(listOf(cell.getOrElse(0) { 10 }.coerceIn(0, STEPS), cell.getOrElse(1) { 10 }.coerceIn(0, STEPS)), land).let { (x, y) -> nearestCoastCell(x, y, land) }
    /** THE snap of a finger for the held piece: the coast for the harbour, the land for everything else. */
    fun snapCell(fx: Float, fy: Float, land: Int, coastOnly: Boolean): List<Int> =
        if (coastOnly) nearestCoastCell(fx, fy, land) else nearestLandCell(fx, fy, land)
    /** A stored/started cell moved onto the land's grid if it is not (old data, a camera looking at the sea). */
    fun clampToLand(cell: List<Int>, land: Int): List<Int> =
        if (buildable(cell, land)) cell
        else project(listOf(cell.getOrElse(0) { 10 }.coerceIn(0, STEPS), cell.getOrElse(1) { 10 }.coerceIn(0, STEPS)), land).let { (x, y) -> nearestLandCell(x, y, land) }

    /** Diamond corners (top, right, bottom, left) of a footprint of [radius] cells around [cell], in box fractions. */
    fun footprint(cell: List<Int>, land: Int, radius: Int): List<Pair<Float, Float>> =
        project(cell, land).let { (cx, cy) -> footprintAt(cx, cy, land, radius.toFloat()) }
    /** The same diamond around an arbitrary centre (a sprite that stands off-lattice on its unmoved default spot). */
    fun footprintAt(cx: Float, cy: Float, land: Int, radius: Float): List<Pair<Float, Float>> {
        val d = step(land) * radius
        return listOf(cx to cy - d, cx + d to cy, cx to cy + d, cx - d to cy)
    }
    /**
     * 7.9.12: the ground tile of one lattice cell as (left, top, right, bottom) box fractions: half a step to every
     * side. The tiles cover the island without gaps; a painted pixel belongs to exactly one tile, which is what the
     * occupancy masks ([IslandMasks]) count. A mask is drawn as these tiles, so its outline hugs the sprite.
     */
    fun tile(cell: List<Int>, land: Int): FloatArray {
        val (cx, cy) = project(cell, land); val d = step(land) * .5f
        return floatArrayOf(cx - d, cy - d, cx + d, cy + d)
    }
    /** The outline of a set of cells: every tile edge that does not touch another tile of the set (box fractions). */
    fun outline(cells: Set<List<Int>>, land: Int): List<Pair<Pair<Float, Float>, Pair<Float, Float>>> = buildList {
        for (c in cells) {
            val (l, t, r, b) = tile(c, land).toList()
            if (listOf(c[0], c[1] - 1) !in cells) add((l to t) to (r to t))
            if (listOf(c[0], c[1] + 1) !in cells) add((l to b) to (r to b))
            if (listOf(c[0] - 1, c[1]) !in cells) add((l to t) to (l to b))
            if (listOf(c[0] + 1, c[1]) !in cells) add((r to t) to (r to b))
        }
    }
    /** Footprint radius (in cells) that matches a sprite of [size] (fraction of the island width) on this land. */
    fun radiusFor(size: Float, land: Int): Int = (size / (2f * step(land))).roundToInt().coerceIn(1, 4)

    /** One piece of the mesh: a lattice point and its diagonal neighbour, both buildable. */
    class Edge(val i1: Int, val j1: Int, val i2: Int, val j2: Int)
    private val edgesByLand = Array(6) { land -> lazy {
        buildList {
            for (i in 0..STEPS) for (j in 0..STEPS) if (buildable(i, j, land)) {
                if (i < STEPS && j < STEPS && buildable(i + 1, j + 1, land)) add(Edge(i, j, i + 1, j + 1))
                if (i < STEPS && j > 0 && buildable(i + 1, j - 1, land)) add(Edge(i, j, i + 1, j - 1))
            }
        }
    } }
    /** The diagonal mesh through the buildable cells of a land stage (scaled to the screen by [project]). */
    fun edges(land: Int): List<Edge> = edgesByLand[land.coerceIn(0, 5)].value
    /**
     * One grid cell ("Kästchen") on screen: the diamond around a lattice point whose corners lie half a
     * step towards its four neighbours. Neighbouring cells touch at their corners, so the cells and the
     * mesh between them tile the land like a checkerboard of diamonds.
     */
    fun cell(cell: List<Int>, land: Int): List<Pair<Float, Float>> = footprintHalf(project(cell, land), land)
    private fun footprintHalf(c: Pair<Float, Float>, land: Int): List<Pair<Float, Float>> {
        val d = step(land) * .5f
        return listOf(c.first to c.second - d, c.first + d to c.second, c.first to c.second + d, c.first - d to c.second)
    }
}

/** Finger-near-edge -> camera speed, in pixels per second of the island offset (negative = reveals the right/bottom). */
internal object EdgeScroll {
    fun velocity(finger: Offset, view: Size, margin: Float, maxSpeed: Float): Offset {
        fun axis(p: Float, extent: Float): Float {
            val half = extent / 2f
            val over = abs(p) - (half - margin)
            if (over <= 0f) return 0f
            val k = (over / margin).coerceIn(0f, 1f)
            return -(if (p > 0) 1f else -1f) * k * maxSpeed
        }
        return Offset(axis(finger.x, view.width), axis(finger.y, view.height))
    }
}

/** Where the check/cross bubbles sit relative to the held piece: right under its footprint, above it when there is no room below. */
internal object PlacementBubbles {
    fun flipAbove(footprintBottom: Float, bubbleHeightFraction: Float, limit: Float = .97f) = footprintBottom + bubbleHeightFraction > limit
}

/** The camera of the close-up island: lets the editor ask for edge scrolling and follow it with the held piece. */
internal interface IslandCamera {
    /** Finger in island-box coordinates; null = released. Starts/stops the automatic scrolling at the screen edge. */
    fun track(finger: Offset?)
    /** Where a finger that stays at the same spot on screen is now in island-box coordinates. */
    fun fingerNow(finger: Offset, offsetThen: Offset, zoomThen: Float): Offset
    /** The middle of the visible island area, as a fraction of the island box. */
    fun viewCenter(): Offset
    /** The part of an island box of [hub] size (px, centred in the camera) that is on screen, in that box's px. */
    fun visibleIn(hub: Size): androidx.compose.ui.geometry.Rect = androidx.compose.ui.geometry.Rect(0f, 0f, hub.width, hub.height)
    val offset: Offset
    val zoom: Float
}
internal val LocalIslandCamera = androidx.compose.runtime.staticCompositionLocalOf<IslandCamera?> { null }

/** Something the edit surface can pick up: a standing building or a decoration object. */
internal data class EditTarget(val target: IslandTarget, val cell: List<Int>, val x: Float, val y: Float, val size: Float,
    val res: Int? = null, val sign: String? = null) {
    fun spriteRect(width: Float, height: Float): androidx.compose.ui.geometry.Rect {
        val b = res?.let(SpriteBounds::of) ?: floatArrayOf(0f, 0f, 1f, 1f)
        val box = size * width; val left = x * width - box / 2; val top = y * height - box
        return androidx.compose.ui.geometry.Rect(left + b[0] * box, top + b[1] * box, left + b[2] * box, top + b[3] * box)
    }
    fun contains(at: Offset, surface: Size, dp: Float, zoom: Float): Boolean {
        val pad = 2f * dp / zoom.coerceAtLeast(1f)
        val rect = spriteRect(surface.width, surface.height)
        if (rect.inflate(pad).contains(at)) return true
        return containsSign(at, surface, dp, zoom)
    }
    /** Same foot, width and counter-scale as the painted IslandNameSign in HubIsland. */
    fun containsSign(at: Offset, surface: Size, dp: Float, zoom: Float): Boolean {
        val name = sign ?: return false
        val rect = spriteRect(surface.width, surface.height)
        val half = IslandSign.width(name) * dp / (2f * zoom.coerceAtLeast(1f))
        return at.x in x * surface.width - half..x * surface.width + half &&
            at.y in rect.bottom..rect.bottom + IslandSign.HEIGHT * dp / zoom.coerceAtLeast(1f)
    }
}

/** Colours of the placement grid (7.9.10): free target / held-valid / held-invalid / other pieces / coast target of the harbour. */
internal object GridInk {
    val Free = Color(0xFFFFF4D6)
    val Line = Color(0xFF3B2A18)
    val Ok = Color(0xFF47CC78)
    val No = Color(0xFFEF665E)
    /** Other pieces while moving: a muted brick red, like the painted roofs, never a warning red. */
    val Taken = Color(0xFFC4473A)
    val Coast = Color(0xFF8FD3D6)
}

/** 7.9.11: the cells the grid draws as usable while a piece is held (exactly [IslandLayout.targets]), for tests and a11y. */
internal val GridTargetsKey = androidx.compose.ui.semantics.SemanticsPropertyKey<List<List<Int>>>("GridTargets")
/** 7.9.12: the cells drawn as the held piece's sprite area (its [IslandMasks] mask at the spot), for tests and a11y. */
internal val GridHeldMaskKey = androidx.compose.ui.semantics.SemanticsPropertyKey<Set<List<Int>>>("GridHeldMask")
/** 7.9.12: the cells drawn as other pieces' sprite areas while one is held. */
internal val GridTakenKey = androidx.compose.ui.semantics.SemanticsPropertyKey<Set<List<Int>>>("GridTaken")

/**
 * The build grid + the held piece's sprite area + the selection, drawn between terrain and sprites. While a piece
 * is held (7.9.11) ONLY [targets] are drawn as cells: the spots where the held piece may really stand under the
 * server's rule ([IslandLayout.targets], the same decision as the green preview and ✓). No other cell looks
 * buildable:
 *  - the harbour ([coastOnly]): only the coast spots where its whole pier fits, sea-blue;
 *  - every other piece: every free spot where its whole picture fits, nothing on occupied cells;
 *  - 7.9.12: every OTHER piece's area is exactly the ground tiles its painted sprite covers ([IslandMasks],
 *    [IslandLayout.obstacles]), tinted and outlined in a quiet brick red (dezent, not a warning);
 *  - the held piece's area ([focusCells], its mask at the spot) tile by tile: green where it is free, red where it
 *    would overlap (or all red when the spot is impossible for another reason, e.g. the pier would point inland).
 * At rest ([showGrid] false) nothing of this is drawn, only the gold outline of a tapped piece ([selection]).
 */
@Composable
internal fun IslandGridUnderlay(
    land: Int, showGrid: Boolean, focus: List<Int>?, focusCells: Set<List<Int>>, valid: Boolean,
    selection: Set<List<Int>>?, obstacles: List<IslandLayout.Obstacle> = emptyList(), coastOnly: Boolean = false,
    targets: List<List<Int>> = emptyList(),
    /**
     * 7.9.13 (rule 6): the targets are drawn as clean rectangular ground parcels (the lattice tiles, slightly inset with
     * soft corners), the same tiles the ground rectangles are made of; older rules keep their diamond cells.
     */
    parcels: Boolean = false,
) {
    val fade by animateFloatAsState(if (showGrid) 1f else 0f, tween(240, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "grid-fade")
    val taken = androidx.compose.runtime.remember(obstacles) { obstacles.flatMapTo(HashSet()) { it.cells } }
    val drawn = if (showGrid) targets else emptyList()
    Canvas(Modifier.fillMaxSize().semantics {
        contentDescription = if (showGrid) "Baufeld mit Kästchen" else "Boden"
        if (showGrid) {
            stateDescription = "${obstacles.size} belegte Flächen rot markiert" + if (coastOnly) " · Küstenfelder markiert" else ""
            set(GridTargetsKey, drawn)
            set(GridTakenKey, taken)
            if (focus != null) set(GridHeldMaskKey, focusCells)
        }
    }) {
        val w = size.width; val h = size.height
        fun cellPath(c: List<Int>) = Path().apply {
            IslandGrid.cell(c, land).forEachIndexed { n, (fx, fy) -> if (n == 0) moveTo(fx * w, fy * h) else lineTo(fx * w, fy * h) }
            close()
        }
        fun tile(c: List<Int>) = IslandGrid.tile(c, land).let { (l, t, r, b) ->
            androidx.compose.ui.geometry.Rect(l * w, t * h, r * w, b * h)
        }
        fun outline(cells: Set<List<Int>>, color: Color, width: Float) =
            IslandGrid.outline(cells, land).forEach { (a, b) ->
                drawLine(color, Offset(a.first * w, a.second * h), Offset(b.first * w, b.second * h), width, androidx.compose.ui.graphics.StrokeCap.Round)
            }
        if (fade > .01f) {
            val thin = 1.dp.toPx()
            // Other pieces: a quiet red tint on exactly the tiles their sprites cover.
            taken.forEach { c -> tile(c).let { r -> drawRect(GridInk.Taken.copy(alpha = .26f * fade), r.topLeft, r.size) } }
            // Only the real targets of the held piece are drawn as cells.
            drawn.forEach { c ->
                val near = focus?.let { f -> (1f - (abs(c[0] - f[0]) + abs(c[1] - f[1])) / 10f).coerceIn(0f, 1f) } ?: 0f
                val path = if (parcels) Path().apply {
                    val r = tile(c); val inset = r.width * .08f
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(r.deflate(inset), androidx.compose.ui.geometry.CornerRadius(r.width * .18f)))
                } else cellPath(c)
                drawPath(path, (if (coastOnly) GridInk.Coast.copy(alpha = (.42f + .2f * near) * fade) else GridInk.Free.copy(alpha = (.20f + .22f * near) * fade)))
                drawPath(path, GridInk.Line.copy(alpha = (.30f + .25f * near) * fade), style = Stroke(thin))
            }
            // The outline of every other piece's area, hugging its sprite (dezent, not a warning).
            obstacles.forEach { o -> outline(o.cells, GridInk.Taken.copy(alpha = .85f * fade), 1.6.dp.toPx()) }
        }
        selection?.let { cells ->
            val gold = Color(0xFFFFE08A)
            cells.forEach { c -> tile(c).let { r -> drawRect(gold.copy(alpha = .22f), r.topLeft, r.size) } }
            outline(cells, gold, 2.dp.toPx())
        }
        if (focus != null) {
            // Tile by tile: green where the held piece is free, red where it would overlap another piece.
            // An impossible spot is red as a whole; the tiles that actually collide are the strongest.
            focusCells.forEach { c ->
                val color = if (valid) GridInk.Ok.copy(alpha = .42f) else GridInk.No.copy(alpha = if (c in taken) .62f else .30f)
                tile(c).let { r -> drawRect(color, r.topLeft, r.size) }
            }
            outline(focusCells, if (valid) GridInk.Ok else GridInk.No, 3.dp.toPx())
        }
    }
}

/** What a touch on a piece of the own island turned into before the long-press time ran out. */
private enum class PressOutcome { TAP, MOVED, HELD }

/**
 * Waits until the finger is lifted (tap), the gesture becomes a pan/pinch (moved) or the finger has rested
 * within the touch slop for [holdMs] (held). The hold is also recognised from the event time, so a finger
 * that rested and only then starts moving is a long press even if no timer fired in between.
 */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.tapOrMove(down: PointerInputChange, slop: Float, holdMs: Long = Long.MAX_VALUE): PressOutcome {
    while (true) {
        val e = awaitPointerEvent()
        if (e.changes.size > 1) return PressOutcome.MOVED
        val c = e.changes.firstOrNull { it.id == down.id } ?: return PressOutcome.MOVED
        // rested long enough (also when it is only lifted now): a long press, not a tap
        if (!c.isConsumed && c.uptimeMillis - down.uptimeMillis >= holdMs) return PressOutcome.HELD
        // a tap is taken here (consumed), so the camera does not also see it as a tap on the open sea
        if (!c.pressed) return if (c.isConsumed) PressOutcome.MOVED else { c.consume(); PressOutcome.TAP }
        if (c.isConsumed || (c.position - down.position).getDistance() > slop) return PressOutcome.MOVED
    }
}

/**
 * THE touch surface of the own island, in island-box coordinates (correct at every zoom). One node for
 * every mode, so a long press can turn into a move without lifting the finger:
 *  - rest: a short tap on a piece = [onTap] (actions), a tap on empty ground = [onGround]; holding a
 *    piece still for [LONG_PRESS_MS] lifts it ([onLongPress]) and the same finger carries it right away;
 *    a drag or pinch that starts moving earlier belongs to the camera;
 *  - with a held piece (draft): a drag that starts on it carries it with the finger's offset, a touch on
 *    land elsewhere puts it there first, a touch in the water is ignored;
 *  - every position goes through [IslandGrid.snapCell]: the piece never leaves the land, a finger
 *    in the water keeps it on the nearest shore cell; with [coastOnly] (the harbour, 7.9.10) every drag,
 *    tap and camera follow snaps to the nearest coast cell instead, a taken one included (it turns red);
 *  - near the screen edge the camera scrolls on its own and the piece stays under the finger.
 */
@Composable
internal fun BoxWithConstraintsScope.IslandTouchSurface(
    land: Int, draft: IslandDraft?, radius: Int, targets: List<EditTarget>,
    onTap: (IslandTarget) -> Unit, onLongPress: (IslandTarget) -> Unit,
    onGround: () -> Unit, onPosition: (List<Int>) -> Unit, coastOnly: Boolean = false,
    /**
     * 7.9.12: the spots the held piece snaps to: the cells where its whole picture's ground fits the land (the harbour:
     * the coast spots where its pier fits), so it never hangs over the shore. null/empty = the plain grid snap.
     */
    snapSpots: List<List<Int>>? = null,
) {
    val camera = LocalIslandCamera.current
    val haptic = LocalHapticFeedback.current
    val currentDraft by rememberUpdatedState(draft)
    val currentTargets by rememberUpdatedState(targets)
    val currentRadius by rememberUpdatedState(radius)
    val currentCoastOnly by rememberUpdatedState(coastOnly)
    val currentSnapSpots by rememberUpdatedState(snapSpots)
    val move by rememberUpdatedState(onPosition)
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)
    val ground by rememberUpdatedState(onGround)
    // The finger as the last touch event saw it, with the camera it was seen under.
    val held = remember { mutableStateOf<Triple<Offset, Offset, Float>?>(null) }
    val grab = remember { mutableStateOf(Offset.Zero) }
    val surface = remember { mutableStateOf(Size(1f, 1f)) }
    val lastCell = remember { mutableStateOf<List<Int>?>(null) }
    // Fixed per gesture: a harbour lifted by this very finger snaps to the coast before the draft recomposes.
    val coast = remember { mutableStateOf(false) }
    fun apply(finger: Offset) {
        val s = surface.value
        val fx = (finger.x + grab.value.x) / s.width; val fy = (finger.y + grab.value.y) / s.height
        val spots = currentSnapSpots
        val cell = if (!spots.isNullOrEmpty()) IslandGrid.nearestOf(spots, fx, fy, land) else IslandGrid.snapCell(fx, fy, land, coast.value)
        if (cell != lastCell.value) { lastCell.value = cell; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
        move(cell)
    }
    // The camera scrolled while the finger rests at the screen edge: carry the piece along.
    if (camera != null) LaunchedEffect(camera, land) {
        snapshotFlow { camera.offset }.collect { _ ->
            held.value?.let { (finger, off, z) -> apply(camera.fingerNow(finger, off, z)) }
        }
    }
    Box(
        Modifier.fillMaxSize()
            .semantics { contentDescription = if (draft != null) "Bauplatzraster" else "Inselboden" }
            .pointerInput(land) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    surface.value = Size(size.width.toFloat(), size.height.toFloat())
                    val s = surface.value
                    lastCell.value = currentDraft?.position
                    fun anchor(cell: List<Int>) = IslandGrid.project(cell, land).let { (fx, fy) -> Offset(fx * s.width, fy * s.height) }
                    val d0 = currentDraft
                    coast.value = currentCoastOnly
                    if (d0 != null) {
                        val a = anchor(d0.position)
                        val reach = maxOf(36.dp.toPx(), IslandGrid.step(land) * currentRadius * s.width * 1.4f)
                        if ((down.position - a).getDistance() <= reach) grab.value = a - down.position
                        else {
                            // Beside the island: nothing happens, the piece stays where it is.
                            if (!IslandGrid.onLand(down.position.x / s.width, down.position.y / s.height, land)) return@awaitEachGesture
                            grab.value = Offset.Zero
                        }
                        down.consume()
                        if (grab.value == Offset.Zero) apply(down.position)
                    } else {
                        // Signs are drawn above sprites. A front neighbour's sprite must not steal
                        // a finger on the explicitly named sign (including a long press to move).
                        val t = currentTargets.filter { tg ->
                            tg.containsSign(down.position, s, 1.dp.toPx(), camera?.zoom ?: 1f)
                        }.maxByOrNull { it.y } ?: currentTargets.filter { tg ->
                            tg.contains(down.position, s, 1.dp.toPx(), camera?.zoom ?: 1f)
                        }.maxByOrNull { it.y }
                        if (t == null) {
                            // Empty ground: a clean tap closes what is open; a drag or pinch moves the camera.
                            if (tapOrMove(down, viewConfiguration.touchSlop) == PressOutcome.TAP) ground()
                            return@awaitEachGesture
                        }
                        // Short tap = actions. Holding still = pick it up; this same finger then carries it.
                        when (withTimeoutOrNull(LONG_PRESS_MS) { tapOrMove(down, viewConfiguration.touchSlop, LONG_PRESS_MS) } ?: PressOutcome.HELD) {
                            PressOutcome.TAP -> { tap(t.target); return@awaitEachGesture }
                            PressOutcome.MOVED -> return@awaitEachGesture
                            PressOutcome.HELD -> {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                longPress(t.target)
                            }
                        }
                        coast.value = t.target == IslandTarget.Building(IsleBuilding.HARBOUR)
                        grab.value = anchor(t.cell) - down.position
                        lastCell.value = t.cell
                    }
                    val follow = { change: PointerInputChange ->
                        change.consume()
                        if (camera != null) {
                            held.value = Triple(change.position, camera.offset, camera.zoom)
                            camera.track(change.position)
                        }
                        apply(change.position)
                    }
                    var moved = false
                    try {
                        drag(down.id) { moved = true; follow(it) }
                        // A plain tap beside the held piece puts it on the tapped cell (a drag carries it with its offset).
                        if (!moved && d0 != null && grab.value != Offset.Zero) { grab.value = Offset.Zero; apply(down.position) }
                    } finally { held.value = null; camera?.track(null) }
                }
            },
    )
}

/** Holding a piece this long picks it up (CoC-like; a bit quicker than the system long press). */
internal const val LONG_PRESS_MS = 350L
