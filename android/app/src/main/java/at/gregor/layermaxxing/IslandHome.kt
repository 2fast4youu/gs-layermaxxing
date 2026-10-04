package at.gregor.layermaxxing

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
 * The player's home island, drawn from one painted base: its buildings ARE the
 * menus (harbour, post, library, campfire, lighthouse, house), the open lawns
 * are decoration slots. Positions are fractions of the isle_hub sprite and were
 * checked against the artwork so labels and decor sit on buildings and grass.
 */
internal enum class IsleBuilding(val emoji: String, val label: String, val x: Float, val y: Float) {
    HOUSE("🏠", "Mein Haus", .47f, .03f),
    LIGHTHOUSE("👥", "Freunde", .82f, .06f),
    POST("✉", "Post", .15f, .22f),
    LIBRARY("📖", "Wörterbuch", .86f, .33f),
    CAMPFIRE("🔥", "Gruppen", .20f, .80f),
    HARBOUR("⚓", "Hafen", .62f, .95f),
}

internal object IsleDecor {
    /** Open lawns on isle_hub (fractions of the sprite). */
    val slots = listOf(.36f to .30f, .66f to .36f, .47f to .50f, .70f to .47f, .30f to .49f, .62f to .58f)
    const val HUB_ASPECT = 900f / 744f

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
    onBuilding: ((IsleBuilding) -> Unit)? = null,
    onSlot: ((Int) -> Unit)? = null,
) {
    BoxWithConstraints(modifier.aspectRatio(IsleDecor.HUB_ASPECT)) {
        val w = maxWidth; val h = maxHeight
        Image(painterResource(R.drawable.isle_hub), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
        IsleDecor.slots.forEachIndexed { i, (x, y) ->
            val item = decor[i]
            item?.let(IsleDecor::res)?.let { res ->
                val s = IsleDecor.size(item, w)
                Image(
                    painterResource(res), IsleDecor.labels[item],
                    Modifier.offset(x = w * x - s / 2, y = h * y - s * .85f).size(s),
                )
            }
            if (onSlot != null) {
                val s = 34.dp
                Box(
                    Modifier.offset(x = w * x - s / 2, y = h * y - s / 2).size(s).clip(CircleShape)
                        .background(Color.White.copy(alpha = if (item == null) .85f else .55f))
                        .border(2.dp, Isle.Teal, CircleShape)
                        .clickable(onClickLabel = if (item == null) "Deko setzen" else "Deko ändern") { onSlot(i) },
                    Alignment.Center,
                ) { Text(if (item == null) "+" else "✎", color = Isle.TealDark, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        }
        if (labels && onSlot == null) IsleBuilding.entries.forEach { b ->
            val count = badges[b] ?: 0
            Row(
                Modifier.offset(x = w * b.x - 50.dp, y = h * b.y - 15.dp).width(100.dp)
                    .then(if (onBuilding != null) Modifier.clickable(onClickLabel = "${b.label} öffnen") { onBuilding(b) } else Modifier)
                    .semantics { contentDescription = b.label; if (onBuilding != null) role = Role.Button },
                horizontalArrangement = Arrangement.Center,
            ) {
                Row(
                    Modifier.shadow(3.dp, RoundedCornerShape(50)).background(Isle.Card, RoundedCornerShape(50))
                        .heightIn(min = 30.dp).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(b.emoji, fontSize = 12.sp)
                    Spacer(Modifier.width(3.dp))
                    Text(b.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Isle.Ink, maxLines = 1)
                    if (count > 0) Text(
                        " $count", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 3.dp).background(Isle.Teal, CircleShape).padding(horizontal = 4.dp),
                    )
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
            Text("Rein zur Zierde. Neue Deko schaltest du mit Quests frei · du hast ⭐$score", color = Isle.Muted, fontSize = 13.sp)
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
                        Text(if (item.unlocked) " " else "🔒 ab ⭐${item.unlockAt}", fontSize = 10.sp, color = Isle.Muted)
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
