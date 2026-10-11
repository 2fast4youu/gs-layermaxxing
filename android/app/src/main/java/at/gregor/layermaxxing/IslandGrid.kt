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
    /** Every lattice point on land. The border rows/columns (0 and 20) are always water. */
    val landCells: List<List<Int>> by lazy { buildList { for (i in 0..STEPS) for (j in 0..STEPS) if (landCell(i, j)) add(listOf(i, j)) } }
    /** Is a finger (box fraction) over a land cell? A tap beside the island is ignored. */
    fun onLand(fx: Float, fy: Float, land: Int): Boolean = unproject(fx, fy, land).let { landCell(it[0], it[1]) }
    /**
     * Hard clamp of a finger (box fraction) to the grid: on land the snapped cell, in the water the land
     * cell nearest on screen (x distances count [IslandPlans.ASPECT] times as much as y). Never water.
     */
    fun nearestLandCell(fx: Float, fy: Float, land: Int): List<Int> {
        val cell = unproject(fx, fy, land)
        if (landCell(cell[0], cell[1])) return cell
        val s = IslandPlans.constructionScale(land)
        val gx = ((fx - IslandPlans.LAND_CX) / s + IslandPlans.LAND_CX) * STEPS
        val gy = ((fy - IslandPlans.LAND_CY) / s + IslandPlans.LAND_CY) * STEPS
        return landCells.minBy { (i, j) -> val dx = (i - gx) * IslandPlans.ASPECT; val dy = j - gy; dx * dx + dy * dy }
    }
    /** A stored/started cell moved onto land if it is not (old data, a camera looking at the sea). */
    fun clampToLand(cell: List<Int>, land: Int): List<Int> =
        if (cell.size == 2 && cell[0] in 0..STEPS && cell[1] in 0..STEPS && landCell(cell[0], cell[1])) cell
        else project(listOf(cell.getOrElse(0) { 10 }.coerceIn(0, STEPS), cell.getOrElse(1) { 10 }.coerceIn(0, STEPS)), land).let { (x, y) -> nearestLandCell(x, y, land) }

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
    /** The part of an island box of [hub] size (px, centred in the camera) that is on screen, in that box's px. */
    fun visibleIn(hub: Size): androidx.compose.ui.geometry.Rect = androidx.compose.ui.geometry.Rect(0f, 0f, hub.width, hub.height)
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
    val fade by animateFloatAsState(if (showGrid) 1f else 0f, tween(240, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "grid-fade")
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
 *  - edit mode: touching a piece lifts it ([onLift]) and a drag carries it at once;
 *  - with a held piece (draft): a drag that starts on it carries it with the finger's offset, a touch on
 *    land elsewhere puts it there first, a touch in the water is ignored;
 *  - every position goes through [IslandGrid.nearestLandCell]: the piece never leaves the land, a finger
 *    in the water keeps it on the nearest shore cell;
 *  - near the screen edge the camera scrolls on its own and the piece stays under the finger.
 */
@Composable
internal fun BoxWithConstraintsScope.IslandTouchSurface(
    land: Int, draft: IslandDraft?, radius: Int, targets: List<EditTarget>, editing: Boolean,
    onTap: (IslandTarget) -> Unit, onLongPress: (IslandTarget) -> Unit, onLift: (IslandTarget) -> Unit,
    onGround: () -> Unit, onPosition: (List<Int>) -> Unit,
) {
    val camera = LocalIslandCamera.current
    val haptic = LocalHapticFeedback.current
    val currentDraft by rememberUpdatedState(draft)
    val currentTargets by rememberUpdatedState(targets)
    val currentRadius by rememberUpdatedState(radius)
    val currentEditing by rememberUpdatedState(editing)
    val move by rememberUpdatedState(onPosition)
    val lift by rememberUpdatedState(onLift)
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)
    val ground by rememberUpdatedState(onGround)
    // The finger as the last touch event saw it, with the camera it was seen under.
    val held = remember { mutableStateOf<Triple<Offset, Offset, Float>?>(null) }
    val grab = remember { mutableStateOf(Offset.Zero) }
    val surface = remember { mutableStateOf(Size(1f, 1f)) }
    val lastCell = remember { mutableStateOf<List<Int>?>(null) }
    fun apply(finger: Offset) {
        val s = surface.value
        val cell = IslandGrid.nearestLandCell((finger.x + grab.value.x) / s.width, (finger.y + grab.value.y) / s.height, land)
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
            .semantics { contentDescription = when { draft != null -> "Bauplatzraster"; editing -> "Bearbeitungsfläche"; else -> "Inselboden" } }
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
                        if ((down.position - a).getDistance() <= reach) grab.value = a - down.position
                        else {
                            // Beside the island: nothing happens, the piece stays where it is.
                            if (!IslandGrid.onLand(down.position.x / s.width, down.position.y / s.height, land)) return@awaitEachGesture
                            grab.value = Offset.Zero
                        }
                        down.consume()
                        if (grab.value == Offset.Zero) apply(down.position)
                    } else {
                        val t = currentTargets.filter { tg ->
                            val box = tg.size * s.width
                            val ax = tg.x * s.width; val ay = tg.y * s.height; val pad = 6.dp.toPx()
                            // the name sign under the foot counts as part of the building (it keeps its screen size)
                            val sign = 26.dp.toPx() / (camera?.zoom ?: 1f).coerceAtLeast(1f)
                            down.position.x in ax - box / 2 - pad..ax + box / 2 + pad && down.position.y in ay - box - pad..ay + sign
                        }.maxByOrNull { it.y }
                        if (t == null) {
                            // Empty ground: a clean tap closes what is open; a drag or pinch moves the camera.
                            if (!currentEditing && tapOrMove(down, viewConfiguration.touchSlop) == PressOutcome.TAP) ground()
                            return@awaitEachGesture
                        }
                        if (currentEditing) {
                            // Edit mode: a tap lifts it; a drag lifts it and carries it with the same touch.
                            val slop = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                            lift(t.target)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (slop == null) return@awaitEachGesture
                        } else {
                            // Short tap = actions. Holding still = pick it up; this same finger then carries it.
                            when (withTimeoutOrNull(LONG_PRESS_MS) { tapOrMove(down, viewConfiguration.touchSlop, LONG_PRESS_MS) } ?: PressOutcome.HELD) {
                                PressOutcome.TAP -> { tap(t.target); return@awaitEachGesture }
                                PressOutcome.MOVED -> return@awaitEachGesture
                                PressOutcome.HELD -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    longPress(t.target)
                                }
                            }
                        }
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
