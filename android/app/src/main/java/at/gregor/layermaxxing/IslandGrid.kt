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
    fun landCell(i: Int, j: Int) = IslandPlans.inside(i / 20f, j / 20f, radius = .95f)

    /** Diamond corners (top, right, bottom, left) of a footprint of [radius] cells around [cell], in box fractions. */
    fun footprint(cell: List<Int>, land: Int, radius: Int): List<Pair<Float, Float>> =
        project(cell, land).let { (cx, cy) -> footprintAt(cx, cy, land, radius) }
    /** The same diamond around an arbitrary centre (a sprite that stands off-lattice on its unmoved default spot). */
    fun footprintAt(cx: Float, cy: Float, land: Int, radius: Int): List<Pair<Float, Float>> {
        val d = step(land) * radius
        return listOf(cx to cy - d, cx + d to cy, cx to cy + d, cx - d to cy)
    }
    /** Footprint radius (in cells) that matches a sprite of [size] (fraction of the island width) on this land. */
    fun radiusFor(size: Float, land: Int): Int = (size / (2f * step(land))).roundToInt().coerceIn(1, 4)

    /** One piece of the mesh: a lattice point and its diagonal neighbour, both on land. */
    class Edge(val i1: Int, val j1: Int, val i2: Int, val j2: Int)
    /** All mesh segments on land. Independent of the land size: the grid is scaled by [project]. */
    val edges: List<Edge> by lazy {
        buildList {
            for (i in 0..STEPS) for (j in 0..STEPS) if (landCell(i, j)) {
                if (i < STEPS && j < STEPS && landCell(i + 1, j + 1)) add(Edge(i, j, i + 1, j + 1))
                if (i < STEPS && j > 0 && landCell(i + 1, j - 1)) add(Edge(i, j, i + 1, j - 1))
            }
        }
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
    val offset: Offset
    val zoom: Float
}
internal val LocalIslandCamera = androidx.compose.runtime.staticCompositionLocalOf<IslandCamera?> { null }

/** Something the edit surface can pick up: a standing building or a decoration object. */
internal data class EditTarget(val target: IslandTarget, val cell: List<Int>, val x: Float, val y: Float, val size: Float)

/** Soft mesh + held piece's footprint + selection footprint, drawn between terrain and sprites. */
@Composable
internal fun IslandGridUnderlay(
    land: Int, showGrid: Boolean, focus: List<Int>?, focusRadius: Int, valid: Boolean,
    selection: Triple<Float, Float, Int>?,
) {
    val fade by animateFloatAsState(if (showGrid) 1f else 0f, tween(300), label = "grid-fade")
    Canvas(Modifier.fillMaxSize().semantics { contentDescription = if (showGrid) "Isometrisches Raster" else "Boden" }) {
        val w = size.width; val h = size.height
        fun px(i: Int, j: Int) = Offset(IslandGrid.x(i, land) * w, IslandGrid.y(j, land) * h)
        if (fade > .01f) {
            val dark = Color(0xFF24515A); val light = Color(0xFFFFF4D6)
            val thin = 1.1.dp.toPx()
            IslandGrid.edges.forEach { e ->
                val near = focus?.let { f ->
                    val d = abs((e.i1 + e.i2) / 2f - f[0]) + abs((e.j1 + e.j2) / 2f - f[1])
                    (1f - d / 12f).coerceIn(0f, 1f)
                } ?: 0f
                val a = (.26f + .42f * near) * fade
                val p1 = px(e.i1, e.j1); val p2 = px(e.i2, e.j2)
                drawLine(dark.copy(alpha = a * .55f), p1, p2, thin * 2f)
                drawLine(light.copy(alpha = a), p1, p2, thin)
            }
        }
        fun diamond(cx: Float, cy: Float, radius: Int) = Path().apply {
            IslandGrid.footprintAt(cx, cy, land, radius).forEachIndexed { n, (fx, fy) ->
                if (n == 0) moveTo(fx * w, fy * h) else lineTo(fx * w, fy * h)
            }
            close()
        }
        selection?.let { (sx, sy, r) ->
            val gold = Color(0xFFFFE08A)
            diamond(sx, sy, r).let { drawPath(it, gold.copy(alpha = .22f)); drawPath(it, gold, style = Stroke(2.dp.toPx())) }
        }
        if (focus != null) {
            val color = if (valid) Color(0xFF47CC78) else Color(0xFFEF665E)
            val (fx, fy) = IslandGrid.project(focus, land)
            diamond(fx, fy, focusRadius).let { drawPath(it, color.copy(alpha = .30f)); drawPath(it, color, style = Stroke(3.dp.toPx())) }
        }
    }
}

/**
 * One full-size touch surface for editing, in island-box coordinates (so it is correct at every zoom):
 *  - with a held piece (draft): a drag that starts on the piece carries it (it keeps the finger's offset),
 *    a touch somewhere else puts it there first; everything snaps to the grid through [IslandGrid.unproject];
 *  - in edit mode without a held piece: touching a piece lifts it and a drag carries it right away;
 *  - near the screen edge the camera scrolls on its own and the piece stays under the finger.
 */
@Composable
internal fun BoxWithConstraintsScope.IslandEditSurface(
    land: Int, draft: IslandDraft?, radius: Int,
    targets: List<EditTarget>, onLift: (IslandTarget) -> Unit, onPosition: (List<Int>) -> Unit,
) {
    val camera = LocalIslandCamera.current
    val haptic = LocalHapticFeedback.current
    val currentDraft by rememberUpdatedState(draft)
    val currentTargets by rememberUpdatedState(targets)
    val currentRadius by rememberUpdatedState(radius)
    val move by rememberUpdatedState(onPosition)
    val lift by rememberUpdatedState(onLift)
    // The finger as the last touch event saw it, with the camera it was seen under.
    val held = remember { mutableStateOf<Triple<Offset, Offset, Float>?>(null) }
    val grab = remember { mutableStateOf(Offset.Zero) }
    val surface = remember { mutableStateOf(Size(1f, 1f)) }
    val lastCell = remember { mutableStateOf<List<Int>?>(null) }
    fun apply(finger: Offset) {
        val s = surface.value
        val cell = IslandGrid.unproject((finger.x + grab.value.x) / s.width, (finger.y + grab.value.y) / s.height, land)
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
            .semantics { contentDescription = if (draft != null) "Bauplatzraster" else "Bearbeitungsfläche" }
            .pointerInput(land) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    surface.value = Size(size.width.toFloat(), size.height.toFloat())
                    val s = surface.value
                    lastCell.value = currentDraft?.position
                    fun anchor(cell: List<Int>) = IslandGrid.project(cell, land).let { (fx, fy) -> Offset(fx * s.width, fy * s.height) }
                    val d0 = currentDraft
                    if (d0 != null) {
                        val a = anchor(d0.position)
                        val reach = maxOf(36.dp.toPx(), IslandGrid.step(land) * currentRadius * s.width * 1.4f)
                        grab.value = if ((down.position - a).getDistance() <= reach) a - down.position else Offset.Zero
                        down.consume()
                        if (grab.value == Offset.Zero) apply(down.position)
                    } else {
                        val t = currentTargets.filter { tg ->
                            val box = tg.size * s.width
                            val ax = tg.x * s.width; val ay = tg.y * s.height; val pad = 6.dp.toPx()
                            // the name sign under the foot counts as part of the building (it keeps its screen size)
                            val sign = 26.dp.toPx() / (camera?.zoom ?: 1f).coerceAtLeast(1f)
                            down.position.x in ax - box / 2 - pad..ax + box / 2 + pad && down.position.y in ay - box - pad..ay + sign
                        }.maxByOrNull { it.y } ?: return@awaitEachGesture
                        // A tap lifts it; a drag lifts it and carries it with the same touch.
                        val slop = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        lift(t.target)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (slop == null) return@awaitEachGesture
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