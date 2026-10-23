package at.gregor.layermaxxing

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.composed
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import kotlin.math.roundToInt

/**
 * The player's home island, assembled at runtime (see [DynamicIsland]): its
 * buildings ARE the menus (harbour, post, library, hall, lighthouse, house),
 * the open lawns are decoration slots.
 */
/** Icons live in [AppIcons.building]; the painted sprites stay on the island itself. */
internal enum class IsleBuilding(val label: String) {
    HOUSE("Mein Haus"),
    LIGHTHOUSE("Freunde"),
    POST("Post"),
    LIBRARY("Wörterbuch"),
    CAMPFIRE("Gruppen"),
    HARBOUR("Hafen"),
}

internal object IsleDecor {

    val labels = linkedMapOf(
        "flowers" to "Blumen", "bench" to "Bank", "lantern" to "Laterne", "flag" to "Fahne", "palm" to "Palme",
        "campfire" to "Lagerfeuer", "hammock" to "Hängematte", "fountain" to "Brunnen", "windmill" to "Windmühle",
        "maibaum" to "Maibaum",
    )

    fun res(key: String): Int? = if (SpecialDecor.isSpecial(key)) SpecialDecor.res(key) else when (key) {
        "flowers" -> R.drawable.decor_flowers
        "bench" -> R.drawable.decor_bench
        "lantern" -> R.drawable.decor_lantern
        "flag" -> R.drawable.decor_flag
        "palm" -> R.drawable.decor_palm
        "campfire" -> R.drawable.decor_campfire
        "hammock" -> R.drawable.decor_hammock
        "fountain" -> R.drawable.decor_fountain
        "windmill" -> R.drawable.decor_windmill
        "maibaum" -> R.drawable.decor_maibaum
        else -> null
    }

    /** Sprite size as share of the island width. */
    fun share(key: String): Float = when {
        SpecialDecor.isSpecial(key) -> .14f
        else -> when (key) {
        "windmill", "maibaum", "palm" -> .12f
        "flowers", "campfire" -> .075f
        else -> .09f
    } }
    /**
     * 7.9.11: an item this app has no picture for (old data, or a newer server) still stands on the island and the
     * server counts it as an obstacle. It is drawn as a painted wooden signpost with its key in the name, so the
     * blocked cell is visible and the piece can be tapped (and removed by the owner); it is never hidden, ignored
     * or deleted automatically.
     */
    val UNKNOWN_RES = R.drawable.fief_signpost
    fun known(key: String) = res(key) != null
    /** The sprite that is drawn: the item's own picture or the placeholder signpost. */
    fun shown(key: String): Int = res(key) ?: UNKNOWN_RES
    /** Name on the island: the own name of a special piece ("Coden am Berg"), else the item's label. */
    fun name(d: ApiClient.IslandDecor): String = when {
        !known(d.item) -> d.name.ifBlank { "Unbekannte Deko (${d.item})" }
        SpecialDecor.isSpecial(d.item) -> d.name.ifBlank { SpecialDecor.label(d.item) }
        else -> labels[d.item] ?: "Deko"
    }

    /** Taller items get a bit more room so they read at the same visual weight. */
    fun size(key: String, islandWidth: Dp): Dp = islandWidth * when (key) {
        "windmill", "maibaum", "palm" -> .15f
        "flowers", "campfire" -> .10f
        else -> .12f
    }
}

/** Decoration sprites on the island: free objects from the construction ledger, legacy slots as fallback. */
internal fun decorPieces(decor: Map<Int, String>, life: ApiClient.HomeIsland?, landScale: Float, lifted: Int? = null): List<Pair<ApiClient.IslandDecor, IslandPiece>> {
    val objects = if (life?.construction?.supported == true) life.construction.decor
        else IslandPlans.homeSlots.mapIndexedNotNull { i, (sx, sy) -> decor[i]?.let { ApiClient.IslandDecor(i, it, listOf((sx * 20).toInt(), (sy * 20).toInt()), i) } } +
            // A server without the construction ledger still lists old life places: shown as the special pieces they are now.
            SpecialDecor.legacyPlaces(life?.places.orEmpty())
    // 7.9.11: unknown items are not filtered out any more (they are obstacles): they get the placeholder sprite.
    return objects.map { d ->
        val (x, y) = IslandPlans.onLand(d.position[0] / 20f, d.position[1] / 20f, landScale)
        // 7.9.12: with a construction ledger the size is the one the occupancy mask was measured at (IslandMasks).
        val size = life?.construction?.takeIf { it.supported }?.let { IslandMasks.decor(d.item, it.land).size }
            ?: IsleDecor.share(d.item) * IslandPlans.pieceScale(landScale) / IslandPlans.pieceScale(1f)
        d to IslandPiece(IsleDecor.shown(d.item), x, y, size,
            sway = d.item == "palm" || d.item == "flag", name = IsleDecor.name(d), lifted = d.id == lifted)
    }
}

/**
 * Draws the hub island with its buildings, decoration and life places. [onBuilding] null = visitor
 * view: labels stay, but nothing opens. On the own island one touch surface ([IslandTouchSurface])
 * handles every touch: tap = [onBuilding]/[onDecor], long press = [onBuildingLongPress]/[onDecorLongPress]
 * and the same finger keeps moving the lifted piece, tap on empty ground = [onGround]. A [placement]
 * draft shows the isometric grid and lifts its sprite; its check/cross bubbles hang at the piece.
 * [selected] outlines the footprint of the
 * tapped piece and shows [selectionBar] (its compact actions) right at it.
 */
@Composable
internal fun HubIsland(
    decor: Map<Int, String>,
    modifier: Modifier = Modifier,
    labels: Boolean = true,
    badges: Map<IsleBuilding, Int> = emptyMap(),
    seed: Long = 1L,
    animate: Boolean = true,
    onBuilding: ((IsleBuilding) -> Unit)? = null,
    life: ApiClient.HomeIsland? = null,
    showFigure: Boolean = false,
    onFigure: (() -> Unit)? = null,
    lifeTags: Boolean = true,
    placement: IslandDraft? = null,
    placementValid: Boolean = true,
    onPosition: (List<Int>)->Unit = {},
    onBuildingLongPress: ((IsleBuilding) -> Unit)? = null,
    onDecor: ((ApiClient.IslandDecor) -> Unit)? = null,
    onDecorLongPress: ((ApiClient.IslandDecor) -> Unit)? = null,
    placementBusy: Boolean = false,
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit = {},
    selected: IslandTarget? = null,
    onGround: (() -> Unit)? = null,
    selectionBar: (@Composable () -> Unit)? = null,
    /** While a piece is held: every other piece's footprint, outlined in quiet red (7.9.10). */
    obstacles: List<IslandLayout.Obstacle> = emptyList(),
    /** While a piece is held: the only cells drawn as usable, [IslandLayout.targets] (7.9.11). */
    placementTargets: List<List<Int>> = emptyList(),
    /** Snap geometry computed from the saved ledger, before the rendered preview overrides positions. */
    placementSnapSpots: List<List<Int>>? = null,
) {
    val liftedBuilding = (placement?.target as? IslandTarget.Building)?.building
    val liftedDecor = (placement?.target as? IslandTarget.Decor)?.let { it.id ?: IslandLayout.DRAFT_DECOR_ID }
    val plan = androidx.compose.runtime.remember(seed, decor, life?.score, life?.construction, liftedBuilding, liftedDecor) {
        val base = IslandPlans.home(seed, life?.score ?: 0, life?.construction ?: ApiClient.IslandConstruction())
        val lifted = liftedBuilding?.let { IslandPlans.homeBuildings[it]?.name }
        base.copy(pieces = base.pieces.map { if (lifted != null && it.name == lifted) it.copy(lifted = true) else it } +
            decorPieces(decor, life, base.landScale, liftedDecor).map { it.second })
    }
    val land = life?.construction?.land ?: 0
    // Signs and touch targets follow the buildings onto the grown land (not the full-size anchors).
    val placed = androidx.compose.runtime.remember(life?.score, life?.construction) { IslandPlans.placedBuildings(life?.score ?: 0, life?.construction ?: ApiClient.IslandConstruction()) }
    val decorObjects = if (onDecor != null) decorPieces(decor, life, plan.landScale) else emptyList()
    fun sizeOf(t: IslandTarget): Float = when (t) {
        is IslandTarget.Building -> placed[t.building]?.size ?: .12f
        is IslandTarget.Decor -> IslandMasks.decor(t.item, land).size
    }
    val surfaceActive = placement != null
    // 7.9.12: the held piece's area = the tiles its painted sprite covers at the spot ([IslandMasks], like the server).
    val construction = life?.construction ?: ApiClient.IslandConstruction()
    val heldCells = placement?.let { IslandLayout.cellsAt(construction, it.target, it.position) }.orEmpty()
    val snapSpots = placementSnapSpots ?: androidx.compose.runtime.remember(construction, placement?.target) { placement?.let { IslandLayout.fitSpots(construction, it.target) } }
    val heldReach = placement?.let { IslandMasks.shape(IslandLayout.key(construction, it.target), land).reach } ?: 0
    // 7.9.10: the held harbour only ever stands on (and is drawn over) the coast cells.
    val coastOnly = liftedBuilding == IsleBuilding.HARBOUR
    // Picked up: the sprite rises and grows a little (the shadow darkens with it in DynamicIsland).
    val lift by androidx.compose.animation.core.animateFloatAsState(if (placement != null) 1f else 0f,
        androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "lift")
    DynamicIsland(plan, modifier, animate = animate, lift = lift,
        underlay = {
            if (onBuilding != null) {
                val outline = selected?.takeIf { placement == null }?.let { t ->
                    val piece = when (t) {
                        is IslandTarget.Building -> placed[t.building]
                        is IslandTarget.Decor -> decorObjects.firstOrNull { it.first.id == t.id }?.second
                    }
                    // 7.9.12: the tiles its painted sprite covers, outlined (the same area the rule counts).
                    piece?.let {
                        val spot = when (t) {
                            is IslandTarget.Building -> construction.positions[t.building.name] ?: IslandLayout.defaultPosition(t.building)
                            is IslandTarget.Decor -> construction.decor.firstOrNull { d -> d.id == t.id }?.position
                        }
                        spot?.let { p -> IslandLayout.cellsAt(construction, t, p) }
                    }
                }
                IslandGridUnderlay(land, showGrid = placement != null, focus = placement?.position,
                    focusCells = heldCells, valid = placementValid, selection = outline,
                    obstacles = if (placement != null) obstacles else emptyList(),
                    coastOnly = coastOnly, targets = if (placement != null) placementTargets else emptyList(),
                    parcels = IslandLayout.ground(construction))
            }
        },
    ) {
        val w = maxWidth; val h = maxHeight
        val ls = plan.landScale
        val gate = LocalIslandTapGate.current
        // One touch surface for the whole own island (rest, edit and placement): it stays the same node,
        // so a long press can lift a piece and the same finger carries it on.
        if (onBuilding != null) {
            val targets = if (placement == null) buildList {
                placed.forEach { (b, piece) -> add(EditTarget(IslandTarget.Building(b), life?.construction?.positions?.get(b.name) ?: IslandLayout.defaultPosition(b), piece.x, piece.y, piece.size, piece.res, IslandStages.of(b, piece.tier).name)) }
                decorObjects.forEach { (d, piece) -> add(EditTarget(IslandTarget.Decor(d.item, d.id), d.position, piece.x, piece.y, piece.size, piece.res, piece.name.takeIf { SpecialDecor.isSpecial(d.item) })) }
            } else emptyList()
            fun decorOf(t: IslandTarget.Decor) = decorObjects.firstOrNull { it.first.id == t.id }?.first
            IslandTouchSurface(
                land, placement, heldReach + 1, targets,
                onTap = { t -> gate { when (t) { is IslandTarget.Building -> onBuilding(t.building); is IslandTarget.Decor -> decorOf(t)?.let { d -> onDecor?.invoke(d) } } } },
                onLongPress = { t -> when (t) { is IslandTarget.Building -> onBuildingLongPress?.invoke(t.building); is IslandTarget.Decor -> decorOf(t)?.let { d -> onDecorLongPress?.invoke(d) } } },
                onGround = { onGround?.let { g -> gate { g() } } },
                onPosition = onPosition, coastOnly = coastOnly,
                snapSpots = snapSpots,
            )
        }
        if (life != null && showFigure) {
            // The figure stands on the plaza unless a piece covers that spot.
            val (fx, fy) = IslandPlans.onLand(LifeFigure.X, LifeFigure.Y, ls)
            val covered = placed.values.any { p -> kotlin.math.abs(p.x - fx) < p.size * .6f && fy <= p.y && fy >= p.y - p.size * IslandPlans.ASPECT } ||
                decorPieces(decor, life, ls).any { (_, p) -> kotlin.math.abs(p.x - fx) < p.size * .5f && kotlin.math.abs(p.y - fy) < p.size * .5f }
            if (!covered) FigureLayer(onFigure = if (placement == null) onFigure else null, landScale = ls)
        }
        // Decoration (short = actions, long = move): the touch surface handles the finger, these nodes
        // carry the accessible actions and the debug hitbox.
        if (placement == null && !surfaceActive && onDecor != null) decorObjects.forEach { (d, piece) ->
            val box = w * piece.size
            val name = piece.name ?: "Deko"
            Box(
                spriteHitbox(piece, w, h)
                    .semantics {
                        contentDescription = name; role = Role.Button
                        onClick("$name bearbeiten") { gate { onDecor(d) }; true }
                        onDecorLongPress?.let { long -> onLongClick("$name verschieben") { long(d); true } }
                    },
            )
        }
        if (labels && placement == null) placed.forEach { (b, piece) ->
            val count = badges[b] ?: 0
            val name = IslandStages.of(b, piece.tier).name
            val box = w * piece.size
            val interactive = onBuilding != null && !surfaceActive
            // The whole building is the touch target (handled by the surface); the sign sits at its foot.
            if (interactive) Box(
                spriteHitbox(piece, w, h)
                    .semantics {
                        onClick("${b.label} öffnen") { gate { onBuilding?.invoke(b) }; true }
                        onBuildingLongPress?.let { long -> onLongClick("${b.label} verschieben") { long(b); true } }
                    },
            )
            val zoom = LocalIslandZoom.current
            // Wide enough for every name ("Mein Haus" must never be cut off); the sign itself wraps its text.
            Box(
                Modifier.offset(x = w * piece.x - IslandSign.width(name).dp / 2, y = h * piece.y - box * (1f - SpriteBounds.of(piece.res)[3])).width(IslandSign.width(name).dp)
                    // Counter-scale: when the island is zoomed, the sign keeps its on-screen size.
                    .graphicsLayer { val k = 1f / zoom().coerceAtLeast(1f); scaleX = k; scaleY = k; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 0f) },
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    Modifier.semantics {
                        contentDescription = b.label
                        if (onBuilding != null) role = Role.Button
                        if (interactive) {
                            onClick("${b.label} öffnen") { gate { onBuilding?.invoke(b) }; true }
                            onBuildingLongPress?.let { long -> onLongClick("${b.label} verschieben") { long(b); true } }
                        }
                    },
                ) {
                    IslandNameSign(name)
                    if (count > 0) Box(
                        Modifier.align(Alignment.TopEnd).offset(x = 7.dp, y = (-7).dp).size(16.dp)
                            .background(Color(0xFFA4533F), CircleShape).border(1.dp, Color(0xFFFFF3DC), CircleShape),
                        Alignment.Center,
                    ) { Text(if (count > 9) "9+" else "$count", fontSize = 9.sp, lineHeight = 9.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
        if (labels && placement == null) decorPieces(decor, life, ls).forEach { (d, piece) ->
            if (SpecialDecor.isSpecial(d.item)) {
                val name = IsleDecor.name(d); val box = w * piece.size; val zoom = LocalIslandZoom.current
                Box(Modifier.offset(x = w * piece.x - IslandSign.width(name).dp / 2, y = h * piece.y - box * (1f - SpriteBounds.of(piece.res)[3]))
                    .graphicsLayer { val k = 1f / zoom().coerceAtLeast(1f); scaleX = k; scaleY = k; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 0f) }
                    .semantics { contentDescription = name; if (onDecor != null) onClick("$name auswählen") { gate { onDecor(d) }; true } }) {
                    IslandNameSign(name)
                }
            }
        }
        // Check / cross hang right at the held piece and follow it: under its footprint, above it near the shore.
        placement?.let { draft ->
            val (ax, ay) = IslandGrid.project(draft.position, land)
            val size = sizeOf(draft.target)
            val z = LocalIslandZoom.current().coerceAtLeast(1f)
            val rowW = 120.dp; val rowH = PlacementBubbleSize
            // the footprint's front tip is half a cell below the foot
            val bottom = ay + IslandGrid.step(land) * .5f
            val flip = PlacementBubbles.flipAbove(bottom, (rowH / z) / h)
            val cx = (w * ax).coerceIn(rowW / z / 2, w - rowW / z / 2)
            val top = if (!flip) h * bottom + 2.dp else h * ay - w * size * 1.3f - rowH / z - 2.dp
            Box(
                Modifier.offset(x = cx - rowW / 2, y = top).width(rowW)
                    .graphicsLayer { val k = 1f / z; scaleX = k; scaleY = k; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, if (flip) 1f else 0f) },
                Alignment.TopCenter,
            ) {
                // Pops in once when the piece is picked up, then simply follows it.
                val shown = androidx.compose.runtime.remember { androidx.compose.animation.core.MutableTransitionState(false) }.apply { targetState = true }
                androidx.compose.animation.AnimatedVisibility(shown,
                    enter = androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing), initialScale = .7f) +
                        androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180))) {
                    PlacementBubbleRow(placementValid, placementBusy, onCancel, onConfirm)
                }
            }
        }
        // The tapped piece's compact actions hang right at it (one context at a time; never a bottom card).
        val holder = androidx.compose.runtime.remember { SelectionHolder() }
        val selectedPiece = selected?.takeIf { placement == null }?.let { t ->
            when (t) {
                is IslandTarget.Building -> placed[t.building]
                is IslandTarget.Decor -> decorObjects.firstOrNull { it.first.id == t.id }?.second
            }
        }
        if (selectionBar != null && selectedPiece != null) { holder.bar = selectionBar; holder.anchor = Triple(selectedPiece.x, selectedPiece.y, selectedPiece.size) }
        holder.anchor?.let { (ax, ay, asz) ->
            AtPiece(ax, ay, asz, visible = selectionBar != null && selectedPiece != null) { holder.bar?.invoke() }
        }
    }
}

/** Keeps the last selection's actions for the exit animation after the selection is gone. */
private class SelectionHolder { var bar: (@Composable () -> Unit)? = null; var anchor: Triple<Float, Float, Float>? = null }

/** Compact painted signs: text width only, kept at screen size while zooming. */
internal object IslandSign {
    const val HEIGHT = 30f
    fun width(name: String): Float = (name.length * 6f + 22f).coerceIn(58f, 148f)
}

@Composable
internal fun IslandNameSign(name: String) {
    Box(Modifier.width(IslandSign.width(name).dp).height(IslandSign.HEIGHT.dp), Alignment.TopCenter) {
        Image(painterResource(R.drawable.p_sign), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Text(name, color = Color(0xFFFFF8E6), fontSize = 10.5.sp, fontFamily = Kit.Body, fontWeight = FontWeight.Bold,
            maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 2.dp, start = 6.dp, end = 6.dp))
    }
}

private fun spriteHitbox(piece: IslandPiece, w: Dp, h: Dp): Modifier {
    val b = SpriteBounds.of(piece.res); val box = w * piece.size
    return Modifier.offset(x = w * piece.x - box / 2 + box * b[0], y = h * piece.y - box + box * b[1])
        .width(box * (b[2] - b[0])).height(box * (b[3] - b[1])).islandHitbox()
}

/** World hitbox without its own touch handling (the island's touch surface handles it); debug mode outlines it as before. */
private fun Modifier.islandHitbox(): Modifier = composed {
    if (DebugMode.showsHitboxes(LocalHitboxDebug.current)) Modifier.background(DebugMode.HitboxFill).border(1.5.dp, DebugMode.HitboxLine) else Modifier
}

/**
 * Places [content] at a piece's foot (island-box fractions) at its on-screen size: under the name sign,
 * always inside the part of the island that is on screen (the camera centres the piece, see FocusPan).
 * Pops in with a short scale + fade, never moving the island.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxWithConstraintsScope.AtPiece(ax: Float, ay: Float, pieceSize: Float, visible: Boolean, content: @Composable () -> Unit) {
    val camera = LocalIslandCamera.current
    val zoom = LocalIslandZoom.current
    val boxW = constraints.maxWidth.toFloat(); val boxH = constraints.maxHeight.toFloat()
    val ease = androidx.compose.animation.core.FastOutSlowInEasing
    Box(
        Modifier.layout { measurable, _ ->
            val p = measurable.measure(androidx.compose.ui.unit.Constraints())
            layout(0, 0) {
                val k = 1f / zoom().coerceAtLeast(1f)
                val vis = camera?.visibleIn(androidx.compose.ui.geometry.Size(boxW, boxH)) ?: androidx.compose.ui.geometry.Rect(0f, 0f, boxW, boxH)
                val m = 8.dp.toPx() * k
                val bw = p.width * k; val bh = p.height * k
                // Under the name sign. While the camera is still panning towards the piece it is held
                // inside the visible part and slides along continuously (no flip above/below, no jump).
                val below = ay * boxH + (IslandSign.HEIGHT + 6f).dp.toPx() * k
                val y = below.coerceAtMost(vis.bottom - m - bh).coerceAtLeast(vis.top + m)
                val lo = vis.left + m + bw / 2; val hi = vis.right - m - bw / 2
                val cx = if (lo <= hi) (ax * boxW).coerceIn(lo, hi) else (vis.left + vis.right) / 2
                p.placeWithLayer((cx - bw / 2).roundToInt(), y.roundToInt()) {
                    scaleX = k; scaleY = k; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                }
            }
        },
    ) {
        androidx.compose.animation.AnimatedVisibility(visible,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200, easing = ease)) +
                androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(220, easing = ease), initialScale = .82f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 0f)),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150, easing = ease)) +
                androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(150, easing = ease), targetScale = .9f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 0f)),
        ) { content() }
    }
}

/** Picker for one lawn: unlocked items, locked ones show the score they need. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DecorPicker(
    current: String?,
    items: List<ApiClient.DecorItem>,
    score: Int,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Isle.Card, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        DecorPickerContent(current, items, score, onPick, onDismiss)
    }
}

@Composable
internal fun DecorPickerContent(
    current: String?,
    items: List<ApiClient.DecorItem>,
    score: Int,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    run {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("Deko wählen", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Isle.Ink)
            Text("Rein zur Zierde. Neue Deko schaltest du mit Quests frei · du hast $score Punkte", color = Isle.Muted, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(GridCells.Fixed(3), Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.key }) { item ->
                    val selected = item.key == current
                    Column(
                        Modifier.clip(RoundedCornerShape(16.dp))
                            .background(if (selected) Isle.Teal.copy(alpha = .15f) else Isle.Sand)
                            .border(if (selected) 2.dp else 0.dp, Isle.Teal, RoundedCornerShape(16.dp))
                            .then(if (item.unlocked) Modifier.clickable { onPick(item.key) } else Modifier)
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        IsleDecor.res(item.key)?.let {
                            Image(painterResource(it), null, Modifier.size(56.dp).alpha(if (item.unlocked) 1f else .35f))
                        }
                        Text(IsleDecor.labels[item.key] ?: item.key, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Isle.Ink, textAlign = TextAlign.Center, maxLines = 1)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!item.unlocked) AppIcon(R.drawable.ico_lock, null, tint = Isle.Muted, size = 11.dp)
                    Text(if (item.unlocked) " " else " ab ${item.unlockAt}", fontSize = 10.sp, color = Isle.Muted)
                }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.End) {
                if (current != null) TextButton(onClick = { onPick(null) }, Modifier.heightIn(min = 48.dp)) { Text("Platz leeren", color = Isle.Muted) }
                TextButton(onClick = onDismiss, Modifier.heightIn(min = 48.dp)) { Text("Fertig", color = Isle.TealDark) }
            }
        }
    }
}


/** Current zoom of a close-up island, read lazily (inside layers) so zooming never recomposes. */
/** Holds a building tap back for a moment so a double-tap can zoom instead (default: immediate). */
internal val LocalIslandTapGate = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit) -> Unit> { { it() } }

internal val LocalIslandZoom = androidx.compose.runtime.staticCompositionLocalOf<() -> Float> { { 1f } }
