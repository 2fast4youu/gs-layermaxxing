package at.gregor.layermaxxing

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "Mein Leben als Insel": the own island holds real places (home, the hut, uni,
 * the club ...) on building plots. Land grows with quest points; the figure
 * stands where I am, and only I see it. The catalogue is open: a new kind is
 * one entry here, one sprite and one name on the server whitelist.
 */
internal object LifePlaces {
    data class Kind(val key: String, val label: String, val res: Int, val hint: String)

    val kinds = listOf(
        Kind("home", "Zuhause", R.drawable.place_home, "Wo du wohnst"),
        Kind("bude", "Bude", R.drawable.place_bude, "WG, Studentenbude, eigene Wohnung"),
        Kind("uni", "Uni", R.drawable.place_uni, "Hörsaal, Bibliothek, Lernen"),
        Kind("hut", "Hütte", R.drawable.place_hut, "Berghütte, z. B. das Brennerhaus"),
        Kind("club", "Verein", R.drawable.place_club, "Turnverein, Sportverein, Training"),
        Kind("station", "Zug", R.drawable.place_station, "Unterwegs, Pendeln, Bahnhof"),
        Kind("city", "Stadt", R.drawable.place_city, "In der Stadt, Altstadt, Ausgehen"),
        Kind("desk", "Am Berg coden", R.drawable.place_desk, "Schreibtisch mit Aussicht"),
    )
    private val byKey = kinds.associateBy { it.key }
    fun kind(key: String): Kind? = byKey[key]

    /** Bottom-centre anchors of the building plots on the painted home terrain, in unlock order. */
    val plots = listOf(
        .50f to .30f, .28f to .40f, .72f to .40f,
        .26f to .63f, .74f to .63f, .36f to .22f,
        .64f to .22f, .40f to .73f, .61f to .73f,
    )
    const val PLACE_SIZE = .17f

    /** Where the figure stands: next to its place, or on the plaza when nowhere. */
    fun figureAt(here: Int?, places: Map<Int, ApiClient.LifePlace>): Pair<Float, Float> {
        val plot = here?.takeIf { places.containsKey(it) } ?: return .50f to .52f
        val (x, y) = plots.getOrElse(plot) { return .50f to .52f }
        // Stand beside the place, feet on the ground just above its name tag.
        return (x + if (x < .5f) .10f else -.10f) to (y - .012f)
    }

    /** First locked plot and its price, for a small "next land" sign. */
    fun nextLand(plots: Int, unlocks: List<Int>): Pair<Int, Int>? =
        unlocks.getOrNull(plots)?.let { plots to it }

    fun label(place: ApiClient.LifePlace): String = place.name.ifBlank { kind(place.kind)?.label ?: "Ort" }
}

/** Flat, quiet name tag: no frame, no shadow, sits on the ground under a place. */
@Composable
internal fun PlaceTag(text: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Text(
        text, maxLines = 1, fontFamily = Kit.Body, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, lineHeight = 12.sp,
        letterSpacing = .2.sp, color = Color(0xFFF6EFE2),
        modifier = modifier.clip(RoundedCornerShape(6.dp))
            .background(if (accent) Color(0xCC2F5D50) else Color(0xB3263A40))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** Places, empty plots, the next land sign and my figure, drawn in island-box coordinates. */
@Composable
internal fun BoxWithConstraintsScope.LifeLayer(
    places: Map<Int, ApiClient.LifePlace>,
    plots: Int,
    unlocks: List<Int>,
    here: Int?,
    showFigure: Boolean,
    onPlot: ((Int) -> Unit)?,
    onFigure: (() -> Unit)?,
    tags: Boolean = true,
) {
    val w = maxWidth; val h = maxHeight
    val zoom = LocalIslandZoom.current
    val gate = LocalIslandTapGate.current
    val keep = Modifier.graphicsLayer { val k = 1f / zoom().coerceAtLeast(1f); scaleX = k; scaleY = k; transformOrigin = TransformOrigin(.5f, 0f) }
    LifePlaces.plots.forEachIndexed { i, (x, y) ->
        val place = places[i]
        val open = i < plots
        if (place == null && (!open || onPlot == null)) return@forEachIndexed
        val box = w * LifePlaces.PLACE_SIZE
        val res = place?.let { LifePlaces.kind(it.kind)?.res } ?: R.drawable.place_plot
        val label = place?.let(LifePlaces::label) ?: "Bauplatz"
        Image(
            painterResource(res), label,
            Modifier.offset(x = w * x - box / 2, y = h * y - box * .78f).size(box)
                .alpha(if (place == null) .78f else 1f)
                .then(if (onPlot != null) Modifier.worldTap(if (place == null) "Bauplatz bebauen" else "$label öffnen") { gate { onPlot(i) } } else Modifier),
            alignment = Alignment.BottomCenter,
        )
        if (tags) Box(Modifier.offset(x = w * x - 60.dp, y = h * y + 2.dp).width(120.dp).then(keep), Alignment.TopCenter) {
            PlaceTag(if (place == null) "＋ Bauplatz" else label, accent = place == null)
        }
    }
    if (showFigure) {
        val (fx, fy) = LifePlaces.figureAt(here, places)
        val fh = w * .085f
        Image(
            painterResource(R.drawable.char_me), "Du",
            Modifier.offset(x = w * fx - fh * .2f, y = h * fy - fh).height(fh).width(fh * .4f)
                .then(if (onFigure != null) Modifier.worldTap("Du – Profil öffnen") { gate { onFigure() } } else Modifier),
        )
    }
}

/** Build on an empty plot: pick a real place, give it a name. */
@Composable
internal fun BuildPlaceContent(
    current: ApiClient.LifePlace?,
    onBuild: (ApiClient.LifePlace) -> Unit,
    onDemolish: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var kind by remember { mutableStateOf(current?.kind ?: "home") }
    var name by remember { mutableStateOf(current?.name.orEmpty()) }
    Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
        Text(if (current == null) "Was steht hier in deinem Leben?" else "Umbauen", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Isle.Ink)
        Text("Ein Ort aus deinem echten Leben. Den Namen bestimmst du.", color = Isle.Muted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(GridCells.Fixed(4), Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(LifePlaces.kinds, key = { it.key }) { k ->
                val selected = k.key == kind
                Column(
                    Modifier.clip(RoundedCornerShape(14.dp))
                        .background(if (selected) Isle.Teal.copy(alpha = .14f) else Isle.Sand)
                        .border(if (selected) 2.dp else 0.dp, if (selected) Isle.Teal else Color.Transparent, RoundedCornerShape(14.dp))
                        .clickable { kind = k.key }.padding(6.dp)
                        .semantics { contentDescription = k.label; role = Role.RadioButton },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(painterResource(k.res), null, Modifier.size(54.dp))
                    Text(k.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Isle.Ink, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val k = LifePlaces.kind(kind)
        OutlinedTextField(
            name, { if (it.length <= 40) name = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Name (optional)") }, placeholder = { Text(k?.hint ?: "") },
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onDemolish != null) TextButton(onClick = onDemolish) { Text("Abreißen", color = Color(0xFFB3412E)) }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onDismiss) { Text("Abbrechen") }
            Button(
                onClick = { onBuild(ApiClient.LifePlace(kind, name.trim())) },
                colors = ButtonDefaults.buttonColors(containerColor = Isle.TealDark),
            ) { Text(if (current == null) "Bauen" else "Speichern") }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** A built place: be here (moves my figure), optional short status, rebuild. */
@Composable
internal fun PlaceSheetContent(
    place: ApiClient.LifePlace,
    imHere: Boolean,
    status: String,
    onHere: (Boolean, String) -> Unit,
    onRebuild: () -> Unit,
) {
    var text by remember(imHere) { mutableStateOf(if (imHere) status else "") }
    val k = LifePlaces.kind(place.kind)
    Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        k?.let { Image(painterResource(it.res), null, Modifier.size(120.dp)) }
        Text(LifePlaces.label(place), fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Isle.Ink)
        Text(if (place.name.isNotBlank()) k?.label.orEmpty() else k?.hint.orEmpty(), color = Isle.Muted, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            text, { if (it.length <= 60) text = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Was machst du gerade? (optional)") },
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { onHere(true, text.trim()) }, Modifier.fillMaxWidth().heightIn(min = 50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Isle.TealDark),
        ) { Text(if (imHere) "Status speichern" else "Ich bin hier", fontWeight = FontWeight.Bold) }
        if (imHere) TextButton(onClick = { onHere(false, "") }) { Text("Ich bin nicht mehr hier") }
        TextButton(onClick = onRebuild) { Text("Umbauen oder umbenennen") }
        Text("Nur du siehst, wo deine Figur steht.", color = Isle.Muted, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
    }
}
