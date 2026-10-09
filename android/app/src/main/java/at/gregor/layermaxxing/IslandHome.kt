package at.gregor.layermaxxing

import androidx.compose.ui.graphics.graphicsLayer

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

    fun res(key: String): Int? = when (key) {
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
    fun share(key: String): Float = when (key) {
        "windmill", "maibaum", "palm" -> .12f
        "flowers", "campfire" -> .075f
        else -> .09f
    }

    /** Taller items get a bit more room so they read at the same visual weight. */
    fun size(key: String, islandWidth: Dp): Dp = islandWidth * when (key) {
        "windmill", "maibaum", "palm" -> .15f
        "flowers", "campfire" -> .10f
        else -> .12f
    }
}

/**
 * Draws the hub island with its decoration. [onBuilding] null = visitor view:
 * labels stay, but nothing opens. [onSlot] non-null = edit mode: every lawn
 * shows a round "+" (or the item to swap).
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
    onSlot: ((Int) -> Unit)? = null,
    life: ApiClient.HomeIsland? = null,
    showFigure: Boolean = false,
    onPlot: ((Int) -> Unit)? = null,
    onFigure: (() -> Unit)? = null,
    lifeTags: Boolean = true,
    editingPlots: Boolean = false,
) {
    val plan = androidx.compose.runtime.remember(seed, decor, life?.score, life?.construction) {
        val base = IslandPlans.home(seed, life?.score ?: 0, life?.construction ?: ApiClient.IslandConstruction())
        val decorPieces = IslandPlans.homeSlots.mapIndexedNotNull { i, (sx, sy) ->
            val key = decor[i] ?: return@mapIndexedNotNull null
            val (x, y) = IslandPlans.onLand(sx, sy, base.landScale)
            IsleDecor.res(key)?.let { IslandPiece(it, x, y, IsleDecor.share(key), sway = key == "palm" || key == "flag", name = IsleDecor.labels[key]) }
        }
        base.copy(pieces = base.pieces + decorPieces)
    }
    DynamicIsland(plan, modifier, animate = animate) {
        val w = maxWidth; val h = maxHeight
        val ls = plan.landScale
        if (labels && (life?.construction?.land ?: 0) > 0) androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val (ix, iy) = IslandPlans.onLand(.51f, .77f, ls)
            val (tx, ty) = IslandPlans.onLand(.51f, .93f, ls)
            val inland = androidx.compose.ui.geometry.Offset(size.width * ix, size.height * iy)
            val tip = androidx.compose.ui.geometry.Offset(size.width * tx, size.height * ty)
            drawIslandJetty(tip, inland, size.width * .035f * IslandPlans.pieceScale(ls))
        }
        if (life != null) LifeLayer(
            places = life.places, plots = life.plots, unlocks = life.plotUnlocks, here = life.here?.plot,
            showFigure = showFigure && onSlot == null, onPlot = if (onSlot == null) onPlot else null, onFigure = onFigure,
            tags = lifeTags, landScale = ls, showEmptyPlots = editingPlots,
        )
        if (onSlot != null) IslandPlans.homeSlots.forEachIndexed { i, (sx, sy) ->
            val (x, y) = IslandPlans.onLand(sx, sy, ls)
            val item = decor[i]
            val s = 44.dp
            Box(
                Modifier.offset(x = w * x - s / 2, y = h * y - s / 2).size(s).clip(CircleShape)
                    .background(Color.White.copy(alpha = if (item == null) .85f else .55f))
                    .border(2.dp, Isle.Teal, CircleShape)
                    .clickable(onClickLabel = if (item == null) "Deko setzen" else "Deko ändern") { onSlot(i) },
                Alignment.Center,
            ) { AppIcon(if (item == null) R.drawable.ico_add else R.drawable.ico_edit, null, tint = Isle.TealDark, size = 17.dp) }
        }
        val gate = LocalIslandTapGate.current
        // Signs and touch targets follow the buildings onto the grown land (not the full-size anchors).
        val placed = androidx.compose.runtime.remember(life?.score, life?.construction) { IslandPlans.placedBuildings(life?.score ?: 0, life?.construction ?: ApiClient.IslandConstruction()) }
        if (labels && onSlot == null) placed.forEach { (b, piece) ->
            val count = badges[b] ?: 0
            val box = w * piece.size
            // The whole building is the touch target; the pill sits at its foot.
            if (onBuilding != null) Box(
                Modifier.offset(x = w * piece.x - box / 2, y = h * piece.y - box).size(box)
                    .worldTap("${b.label} öffnen") { gate { onBuilding(b) } },
            )
            val zoom = LocalIslandZoom.current
            val (nx, ny) = 0f to 0f
            val tagWidth = if (plan.landScale < .7f) 64.dp else 100.dp
            Row(
                Modifier.offset(x = w * (piece.x + nx) - tagWidth / 2, y = h * (piece.y + ny) - 2.dp).width(tagWidth)
                    // Counter-scale: when the island is zoomed, the sign keeps its on-screen size.
                    .graphicsLayer { val k = 1f / zoom().coerceAtLeast(1f); scaleX = k; scaleY = k; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 0f) }
                    .then(if (onBuilding != null) Modifier.worldTap("${b.label} öffnen") { gate { onBuilding(b) } } else Modifier)
                    .semantics { contentDescription = b.label; if (onBuilding != null) role = Role.Button },
                horizontalArrangement = Arrangement.Center,
            ) {
                Box {
                    PlaceTag(if (piece.primitive != null) when (b) {
                        IsleBuilding.HOUSE -> "Zuhause"
                        IsleBuilding.LIBRARY -> "Wörter"
                        else -> b.label
                    } else b.label)
                    if (count > 0) Box(
                        Modifier.align(Alignment.TopEnd).offset(x = 7.dp, y = (-7).dp).size(16.dp)
                            .background(Color(0xFFA4533F), CircleShape).border(1.dp, Color(0xFFFFF3DC), CircleShape),
                        Alignment.Center,
                    ) { Text(if (count > 9) "9+" else "$count", fontSize = 9.sp, lineHeight = 9.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
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
