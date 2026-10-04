package at.gregor.layermaxxing

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Every building on the home island opens its OWN place, built from the thing it
 * stands for – not one shared text list:
 *  - Post: pigeonholes per friend, envelopes with wax seals and boats.
 *  - Hafen: a cork board with pinned quest notes and topic sticky notes.
 *  - Leuchtturm: night sea with a sweeping beam, friends as portholes, requests as bottles.
 *  - Bibliothek: a leather-bound book with an A–Z ribbon (the shared glossary).
 *  - Gemeindehaus: groups as round tables, members seated around them.
 *  - Mein Haus: my room – portrait on the wall, trophies on the shelf.
 * All data is real (server lists); every action goes to an existing endpoint.
 */
internal data class PlaceStyle(val top: Color, val bottom: Color, val ink: Color, val tagline: String)

internal fun IsleBuilding.style(): PlaceStyle = when (this) {
    IsleBuilding.POST -> PlaceStyle(Color(0xFFFFE7B8), Color(0xFFF7C77C), Color(0xFF5A3A12), "Deine Briefe, Fach für Fach")
    IsleBuilding.HARBOUR -> PlaceStyle(Color(0xFFBFE9F2), Color(0xFF7FCFE0), Color(0xFF173C4A), "Schwarzes Brett am Steg")
    IsleBuilding.LIGHTHOUSE -> PlaceStyle(Color(0xFF243B6B), Color(0xFF0F1D3D), Color.White, "Wer leuchtet für dich?")
    IsleBuilding.LIBRARY -> PlaceStyle(Color(0xFFE6DAF5), Color(0xFFB9A5DD), Color(0xFF2E2150), "Euer gemeinsames Wörterbuch")
    IsleBuilding.CAMPFIRE -> PlaceStyle(Color(0xFFF6D9C2), Color(0xFFE2A982), Color(0xFF4A2414), "Wer sitzt an welchem Tisch?")
    IsleBuilding.HOUSE -> PlaceStyle(Color(0xFFDDF1E1), Color(0xFFA9DDB4), Color(0xFF173F26), "Dein Zuhause auf der Insel")
}

internal fun IsleBuilding.sprite(): Int = when (this) {
    IsleBuilding.POST -> R.drawable.b_post
    IsleBuilding.HARBOUR -> R.drawable.b_board
    IsleBuilding.LIGHTHOUSE -> R.drawable.b_lighthouse
    IsleBuilding.LIBRARY -> R.drawable.b_library
    IsleBuilding.CAMPFIRE -> R.drawable.b_hall
    IsleBuilding.HOUSE -> R.drawable.b_house
}

internal fun IsleBuilding.placeName(): String = when (this) {
    IsleBuilding.POST -> "Postamt"
    IsleBuilding.HARBOUR -> "Hafen"
    IsleBuilding.LIGHTHOUSE -> "Leuchtturm"
    IsleBuilding.LIBRARY -> "Bibliothek"
    IsleBuilding.CAMPFIRE -> "Gemeindehaus"
    IsleBuilding.HOUSE -> "Mein Haus"
}

/** Shell: the building stands at the top in its own light, then the place itself. */
@Composable
internal fun PlaceStage(
    place: IsleBuilding,
    onClose: () -> Unit,
    action: Pair<String, () -> Unit>? = null,
    content: @Composable () -> Unit,
) {
    val st = place.style()
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(st.top, st.bottom)))) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).shadow(3.dp, CircleShape).background(Color.White, CircleShape).clip(CircleShape)
                        .clickable(onClickLabel = "Zurück zur Insel", onClick = onClose),
                    Alignment.Center,
                ) { Text("‹", fontSize = 24.sp, color = Isle.Ink, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(10.dp))
                Image(painterResource(place.sprite()), null, Modifier.size(64.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(place.placeName(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = st.ink, maxLines = 1)
                    Text(st.tagline, fontSize = 13.sp, color = st.ink.copy(alpha = .75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
        action?.let { (label, onClick) ->
            Box(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 18.dp)
                    .shadow(8.dp, RoundedCornerShape(28.dp)).background(Isle.Teal, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp)).clickable(onClick = onClick)
                    .padding(horizontal = 22.dp, vertical = 14.dp)
                    .semantics { role = Role.Button },
            ) { Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}

// ---------------------------------------------------------------- Post

/** One pigeonhole per friend; envelopes inside show how and when they open. */
@Composable
internal fun PostOffice(
    pals: List<ApiClient.UserSummary>,
    letters: List<ApiClient.Message>,
    opened: Map<Long, OpenedMessage>,
    onOpen: (ApiClient.Message) -> Unit,
    onLocked: (ApiClient.Message) -> Unit,
    onWrite: (Long) -> Unit,
    onClose: () -> Unit,
) {
    var fach by rememberSaveable { mutableStateOf<Long?>(null) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    PlaceStage(IsleBuilding.POST, onClose, action = "✒  Brief schreiben" to { choosing = true }) {
        val selected = fach?.let { id -> pals.firstOrNull { it.id == id } }
        if (selected == null) {
            if (pals.isEmpty()) EmptyPlace("Noch keine Fächer", "Für jeden Freund entsteht hier ein eigenes Postfach.")
            else LazyVerticalGrid(
                GridCells.Fixed(3), Modifier.fillMaxSize().padding(horizontal = 14.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(pals, key = { it.id }) { f ->
                    val mine = letters.filter { it.peerId == f.id && it.groupId == null }
                    val ready = mine.count { it.incoming && Conversations.letterState(it, opened[it.id] != null) == LetterState.READY }
                    val travelling = mine.count { Conversations.letterState(it, opened[it.id] != null).locked }
                    Pigeonhole(f, mine.size, ready, travelling) { fach = f.id }
                }
            }
        } else {
            val mine = letters.filter { it.peerId == selected.id && it.groupId == null }.sortedByDescending { it.createdAt }
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("‹ Alle Fächer", color = Color(0xFF5A3A12), fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { fach = null }.padding(8.dp))
                    Spacer(Modifier.weight(1f))
                    Text("${selected.avatarEmoji} ${selected.name}", color = Color(0xFF5A3A12), fontWeight = FontWeight.Bold)
                }
                if (mine.isEmpty()) EmptyPlace("Fach ist leer", "Schreib ${selected.name} den ersten Brief.")
                else LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(mine, key = { it.id }) { m ->
                        val state = Conversations.letterState(m, opened[m.id] != null)
                        Envelope(m, state) { if (state.locked) onLocked(m) else onOpen(m) }
                    }
                }
            }
        }
    }
    if (choosing) FriendPicker("An wen geht der Brief?", pals, onDismiss = { choosing = false }) { choosing = false; onWrite(it) }
}

@Composable
private fun Pigeonhole(friend: ApiClient.UserSummary, total: Int, ready: Int, travelling: Int, onClick: () -> Unit) {
    Column(
        Modifier.shadow(4.dp, RoundedCornerShape(10.dp)).background(Color(0xFF8A5A2B), RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = "Fach von ${friend.name} öffnen", onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The dark opening with envelopes peeking out.
        Box(Modifier.fillMaxWidth().aspectRatio(1.25f).background(Color(0xFF3B2410), RoundedCornerShape(6.dp)), Alignment.BottomCenter) {
            Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                val n = total.coerceAtMost(4)
                for (i in 0 until n) {
                    val w = size.width * .78f; val h = size.height * .42f
                    val x = (size.width - w) / 2 + (i - n / 2f) * size.width * .05f
                    val y = size.height - h - i * size.height * .1f
                    drawRoundRect(Color(0xFFFFF6E5), Offset(x, y), Size(w, h), androidx.compose.ui.geometry.CornerRadius(4f))
                    val flap = Path().apply { moveTo(x, y); lineTo(x + w / 2, y + h * .55f); lineTo(x + w, y) }
                    drawPath(flap, Color(0xFFD9C7A3), style = Stroke(2.5f))
                }
            }
            if (ready > 0) Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).background(Color(0xFFC62828), CircleShape),
                Alignment.Center,
            ) { Text("$ready", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            if (travelling > 0) Text(
                "⛵ $travelling unterwegs", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp).background(Color(0x66000000), RoundedCornerShape(50)).padding(horizontal = 5.dp, vertical = 1.dp),
            )
            if (total == 0) Text("leer", fontSize = 11.sp, color = Color.White.copy(alpha = .5f), modifier = Modifier.align(Alignment.Center))
        }
        // Brass name plate.
        Row(
            Modifier.padding(top = 6.dp).background(Brush.verticalGradient(listOf(Color(0xFFF3D58A), Color(0xFFC99A3D))), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(friend.avatarEmoji, fontSize = 12.sp)
            Spacer(Modifier.width(3.dp))
            Text(friend.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3B2410), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Envelope(m: ApiClient.Message, state: LetterState, onClick: () -> Unit) {
    val boat = LetterBoat.forMode(m.mode)
    val seal = when {
        state.locked -> Color(0xFF8D99A6)
        state == LetterState.READY -> Color(0xFFC62828)
        else -> Color(0xFFB08A5A)
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(2.4f).shadow(4.dp, RoundedCornerShape(8.dp))
            .background(Color(0xFFFFF8EA), RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = if (state.locked) "Versiegelt" else "Brief öffnen", onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val flap = Path().apply { moveTo(0f, 0f); lineTo(size.width / 2, size.height * .55f); lineTo(size.width, 0f) }
            drawPath(flap, Color(0xFFE9DCC0), style = Stroke(4f))
            drawCircle(seal, size.height * .14f, Offset(size.width / 2, size.height * .55f))
            drawCircle(Color.White.copy(alpha = .25f), size.height * .07f, Offset(size.width / 2 - size.height * .04f, size.height * .51f))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(
                (if (m.incoming) "von " else "an ") + m.peerName, fontSize = 11.sp, color = Color(0xFF8A6A3A),
            )
            Text(Conversations.letterPreviewText(m), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3B2410), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // Stamp corner: how the letter travels.
        Column(
            Modifier.align(Alignment.TopEnd).padding(8.dp).border(2.dp, Color(0xFFB08A5A), RoundedCornerShape(4.dp))
                .background(Color.White, RoundedCornerShape(4.dp)).padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painterResource(boat.res), null, Modifier.size(34.dp))
            Text(
                when {
                    state.locked -> "unterwegs"
                    state == LetterState.READY -> "neu!"
                    m.incoming -> "gelesen"
                    else -> "zugestellt"
                },
                fontSize = 9.sp, color = Color(0xFF5A3A12), fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ---------------------------------------------------------------- Hafen

/** Cork board: quests as pinned notes, topics as sticky notes. */
@Composable
internal fun HarbourBoard(
    quests: List<ApiClient.Quest>,
    topics: List<ApiClient.Topic>,
    onQuestDone: (ApiClient.Quest, Boolean) -> Unit,
    onQuestDelete: (ApiClient.Quest) -> Unit,
    onNewQuest: () -> Unit,
    onAllTopics: () -> Unit,
    onClose: () -> Unit,
) {
    var showDone by rememberSaveable { mutableStateOf(false) }
    PlaceStage(IsleBuilding.HARBOUR, onClose, action = "📌  Quest anpinnen" to onNewQuest) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            // The board itself.
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(bottom = 86.dp)
                    .shadow(6.dp, RoundedCornerShape(14.dp))
                    .background(Color(0xFF7A4B26), RoundedCornerShape(14.dp)).padding(8.dp)
                    .background(Brush.radialGradient(listOf(Color(0xFFD6A46B), Color(0xFFB98149))), RoundedCornerShape(8.dp)),
            ) {
                val shown = quests.filter { (it.completedAt != null) == showDone }
                LazyVerticalGrid(
                    GridCells.Fixed(2), Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BoardTab("Offen · ${quests.count { it.completedAt == null }}", !showDone) { showDone = false }
                            BoardTab("Erledigt · ${quests.count { it.completedAt != null }}", showDone) { showDone = true }
                        }
                    }
                    if (shown.isEmpty()) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                        Text(
                            if (showDone) "Noch nichts abgehakt." else "Das Brett ist leer – pinn die erste Quest an!",
                            color = Color(0xFF3B2410), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        )
                    }
                    items(shown, key = { it.id }) { q -> QuestNote(q, onQuestDone, if (q.canDelete) onQuestDelete else null) }
                    val open = topics.count { it.completedAt == null }
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                        Box(
                            Modifier.padding(top = 6.dp).rotate(-1.5f).shadow(3.dp).background(Color(0xFFFFF176))
                                .clickable(onClickLabel = "Themen öffnen", onClick = onAllTopics).padding(14.dp).fillMaxWidth(),
                        ) {
                            Column {
                                Text("💬 Themen fürs nächste Treffen", fontWeight = FontWeight.Bold, color = Color(0xFF4A3B00))
                                Text(openTopics(open), color = Color(0xFF6B5A10), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text, fontWeight = FontWeight.Bold, fontSize = 13.sp,
        color = if (selected) Color.White else Color(0xFF3B2410),
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (selected) Color(0xFF3B2410) else Color(0x33FFFFFF))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

private val noteColors = listOf(Color(0xFFFFFDF5), Color(0xFFE8F6FF), Color(0xFFFFEFEF), Color(0xFFEFFFF0))

@Composable
private fun QuestNote(q: ApiClient.Quest, onDone: (ApiClient.Quest, Boolean) -> Unit, onDelete: ((ApiClient.Quest) -> Unit)?) {
    val tilt = ((q.id % 5) - 2) * 1.2f
    val done = q.completedAt != null
    Box(Modifier.rotate(tilt)) {
        Column(
            Modifier.padding(top = 8.dp).fillMaxWidth().shadow(4.dp).background(noteColors[(q.id % 4).toInt()]).padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Isle.questEmoji(q.icon), fontSize = 24.sp)
                Spacer(Modifier.weight(1f))
                Text("⭐${q.points}", fontWeight = FontWeight.Bold, color = Color(0xFF9A6B00), fontSize = 13.sp)
            }
            Text(q.title, fontWeight = FontWeight.Bold, color = Isle.Ink, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                if (q.targetType == "group") "Gruppe ${q.targetName}" else "mit ${q.targetName}",
                fontSize = 11.sp, color = Isle.Muted, maxLines = 1,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (done) "✓ Erledigt" else "Abhaken",
                    fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    color = if (done) Color(0xFF2E7D32) else Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(if (done) Color(0x222E7D32) else Isle.Teal)
                        .clickable { onDone(q, !done) }.padding(horizontal = 10.dp, vertical = 5.dp),
                )
                Spacer(Modifier.weight(1f))
                if (onDelete != null) Text("✕", color = Isle.Muted, modifier = Modifier.clip(CircleShape).clickable { onDelete(q) }.padding(6.dp))
            }
        }
        // The pin.
        Box(
            Modifier.align(Alignment.TopCenter).size(16.dp).shadow(2.dp, CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFFFF8A80), Color(0xFFC62828))), CircleShape),
        )
        if (done) Text(
            "ERLEDIGT", color = Color(0x992E7D32), fontWeight = FontWeight.Bold, fontSize = 18.sp,
            modifier = Modifier.align(Alignment.Center).rotate(-18f).border(2.dp, Color(0x992E7D32), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp),
        )
    }
}

// ---------------------------------------------------------------- Leuchtturm

/** Night sea, a sweeping beam; friends as portholes, requests drift in as bottles. */
@Composable
internal fun LighthouseView(
    pals: List<ApiClient.UserSummary>,
    levels: Map<Long, Int>,
    incoming: List<ApiClient.IncomingRequest>,
    outgoing: List<ApiClient.OutgoingRequest>,
    onRespond: (Long, Boolean) -> Unit,
    onFriend: (Long) -> Unit,
    onFind: () -> Unit,
    onClose: () -> Unit,
) {
    val t = rememberInfiniteTransition(label = "beam")
    val angle by t.animateFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart), label = "a")
    val bob by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "bob")
    PlaceStage(IsleBuilding.LIGHTHOUSE, onClose, action = "🔭  Freunde finden" to onFind) {
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                val origin = Offset(size.width * .5f, -size.height * .02f)
                val a = (angle * 2 * PI).toFloat()
                val spread = .28f
                val len = size.height * 1.3f
                val p = Path().apply {
                    moveTo(origin.x, origin.y)
                    lineTo(origin.x + len * sin(a - spread), origin.y + len * cos(a - spread).let { kotlin.math.abs(it) })
                    lineTo(origin.x + len * sin(a + spread), origin.y + len * cos(a + spread).let { kotlin.math.abs(it) })
                    close()
                }
                drawPath(p, Brush.radialGradient(listOf(Color(0x55FFF3B0), Color(0x00FFF3B0)), origin, len))
                for (i in 0 until 40) {
                    val x = ((i * 0.618f) % 1f) * size.width; val y = ((i * 0.377f) % 1f) * size.height * .5f
                    drawCircle(Color.White.copy(alpha = .25f + (i % 3) * .15f), 1.6f + (i % 2), Offset(x, y))
                }
            }
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (incoming.isNotEmpty()) {
                    item { Text("Flaschenpost angespült", color = Color(0xFFFFE9A8), fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                    items(incoming, key = { "in-${it.id}" }) { r -> Bottle(r.senderName, bob, onYes = { onRespond(r.id, true) }, onNo = { onRespond(r.id, false) }) }
                }
                item {
                    Text(
                        if (pals.isEmpty()) "Noch leuchtet niemand zurück." else "Im Lichtkegel · ${pals.size} ${if (pals.size == 1) "Freund" else "Freunde"}",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    )
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        pals.forEach { f -> Porthole(f, levels[f.id] ?: 1) { onFriend(f.id) } }
                    }
                }
                if (outgoing.isNotEmpty()) {
                    item { Text("Deine Signale draußen", color = Color.White.copy(alpha = .8f), fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    items(outgoing, key = { "out-${it.id}" }) { r ->
                        Text("📡  ${r.recipientName} hat noch nicht geantwortet", color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Porthole(f: ApiClient.UserSummary, level: Int, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(84.dp).clickable(onClickLabel = "${f.name} öffnen", onClick = onClick)) {
        Box(
            Modifier.size(74.dp).shadow(6.dp, CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFFE8C66A), Color(0xFF9C7524))), CircleShape).padding(6.dp)
                .background(profileColor(f.displayColor).copy(alpha = .9f), CircleShape),
            Alignment.Center,
        ) { Text(f.avatarEmoji, fontSize = 32.sp) }
        Text(f.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(Isle.levelName(level), color = Color(0xFFFFE9A8), fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun Bottle(name: String, bob: Float, onYes: () -> Unit, onNo: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().offset(y = (bob * 3).dp).background(Color(0x33FFFFFF), RoundedCornerShape(20.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🍾", fontSize = 30.sp, modifier = Modifier.rotate(-30f + bob * 6))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("möchte dein Freund werden", color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
        }
        Text("Nein", color = Color.White, modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onNo).padding(horizontal = 10.dp, vertical = 8.dp))
        Text(
            "Annehmen", color = Color(0xFF0F1D3D), fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFFFE9A8)).clickable(onClick = onYes).padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

// ---------------------------------------------------------------- Bibliothek

/** The glossary as a book: leather cover, spine with A–Z, parchment page. */
@Composable
internal fun LibraryBook(onClose: () -> Unit, glossary: @Composable () -> Unit) {
    PlaceStage(IsleBuilding.LIBRARY, onClose) {
        Row(
            Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, bottom = 12.dp).navigationBarsPadding()
                .shadow(10.dp, RoundedCornerShape(16.dp))
                .background(Color(0xFF5B3A8C), RoundedCornerShape(16.dp)).padding(6.dp),
        ) {
            // Spine with the alphabet stamped in gold.
            Column(
                Modifier.width(22.dp).fillMaxSize().padding(vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                "ABCDEFGHIJKLMNOPRSTUWZ".forEach { Text("$it", color = Color(0xFFE9C46A), fontSize = 9.sp, fontWeight = FontWeight.Bold) }
            }
            Box(
                Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp, topStart = 4.dp, bottomStart = 4.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFFE9DFC8), Color(0xFFFFF9EC), Color(0xFFFFF9EC)))),
            ) {
                MaterialTheme(
                    colorScheme = lightColorScheme(
                        primary = Color(0xFF5B3A8C), onPrimary = Color.White,
                        primaryContainer = Color(0xFFE6DAF5), onPrimaryContainer = Color(0xFF2E2150),
                        surface = Color(0xFFFFF9EC), onSurface = Color(0xFF2E2150),
                        surfaceVariant = Color(0xFFF1E7D2), onSurfaceVariant = Color(0xFF6B5B48),
                        background = Color(0xFFFFF9EC), outline = Color(0xFFC9B48E),
                    ),
                    typography = MaterialTheme.typography,
                ) { glossary() }
                // Silk bookmark ribbon.
                Box(Modifier.align(Alignment.TopEnd).padding(end = 22.dp).size(width = 14.dp, height = 46.dp).background(Color(0xFFC62828)))
            }
        }
    }
}

// ---------------------------------------------------------------- Gemeindehaus

/** Each group is a round table; its members sit around it. */
@Composable
internal fun CommunityHall(
    groups: List<ApiClient.Group>,
    quests: List<ApiClient.Quest>,
    onGroupQuest: (Long) -> Unit,
    onManage: () -> Unit,
    onClose: () -> Unit,
) {
    PlaceStage(IsleBuilding.CAMPFIRE, onClose, action = "🪑  Gruppen verwalten" to onManage) {
        if (groups.isEmpty()) EmptyPlace("Noch keine Tische", "Gründe eine Gruppe – sie bekommt hier ihren eigenen Tisch.")
        else LazyVerticalGrid(
            GridCells.Fixed(2), Modifier.fillMaxSize().padding(horizontal = 14.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(groups, key = { it.id }) { g ->
                val open = quests.count { it.targetType == "group" && it.targetId == g.id && it.completedAt == null }
                RoundTable(g, open) { onGroupQuest(g.id) }
            }
        }
    }
}

@Composable
private fun RoundTable(g: ApiClient.Group, openQuests: Int, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = "Quest für ${g.name}", onClick = onClick)) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val w = maxWidth
            val seat: Dp = 34.dp
            Box(
                Modifier.align(Alignment.Center).size(w * .52f).shadow(6.dp, CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFFC88A55), Color(0xFF8A5A2B))), CircleShape),
                Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (openQuests > 0) "⭐$openQuests" else "🕯", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    if (openQuests > 0) Text("offen", fontSize = 10.sp, color = Color.White)
                }
            }
            val members = g.members.take(8)
            members.forEachIndexed { i, m ->
                val a = (i.toFloat() / members.size) * 2f * PI.toFloat() - PI.toFloat() / 2
                val r = w * .38f
                Box(
                    Modifier.offset(x = w / 2 + r * cos(a) - seat / 2, y = w / 2 + r * sin(a) - seat / 2).size(seat)
                        .shadow(3.dp, CircleShape).background(Color.White, CircleShape).padding(2.dp)
                        .background(profileColor(m.displayColor).copy(alpha = .35f), CircleShape)
                        .semantics { contentDescription = m.name },
                    Alignment.Center,
                ) { Text(m.avatarEmoji, fontSize = 17.sp) }
            }
        }
        Text(g.name, fontWeight = FontWeight.Bold, color = Color(0xFF4A2414), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${g.members.size} am Tisch", fontSize = 12.sp, color = Color(0xFF7A4B30))
    }
}

// ---------------------------------------------------------------- Mein Haus

/** My room: portrait on the wall, trophies on the shelf, the door to settings. */
@Composable
internal fun MyHouse(
    name: String,
    emoji: String,
    color: String,
    friends: Int,
    questsDone: Int,
    letters: Int,
    qp: Int,
    onSettings: () -> Unit,
    onEdit: () -> Unit,
    onClose: () -> Unit,
) {
    PlaceStage(IsleBuilding.HOUSE, onClose, action = "⚙  Profil & Einstellungen" to onSettings) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(bottom = 90.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Wall: window onto the sea on the left, framed portrait in the middle, a plant on the right.
            Box(
                Modifier.fillMaxWidth().weight(1f).shadow(4.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFFFF3DC), Color(0xFFF6E2BD)))),
            ) {
                // Wainscot / floor.
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(70.dp).background(Brush.verticalGradient(listOf(Color(0xFFC89463), Color(0xFFA9743F)))))
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 70.dp).fillMaxWidth().height(4.dp).background(Color(0xFF8A5A2B)))
                // Window with sea and sky.
                Box(
                    Modifier.align(Alignment.TopStart).padding(18.dp).size(width = 74.dp, height = 92.dp)
                        .border(5.dp, Color.White, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFFBFE9FF), Color(0xFF8FE3E0), Color(0xFF4FC3C7)))),
                ) {
                    Box(Modifier.align(Alignment.Center).fillMaxWidth().height(4.dp).background(Color.White))
                    Box(Modifier.align(Alignment.Center).width(4.dp).fillMaxSize().background(Color.White))
                }
                Image(painterResource(R.drawable.n_flowerbush), null, Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 54.dp).size(64.dp))
                Column(Modifier.align(Alignment.TopCenter).padding(top = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(120.dp).shadow(8.dp, RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFFE8C66A), Color(0xFF9C7524))), RoundedCornerShape(10.dp))
                            .padding(9.dp).background(profileColor(color).copy(alpha = .85f), RoundedCornerShape(4.dp)),
                        Alignment.Center,
                    ) { Text(emoji, fontSize = 58.sp) }
                    Spacer(Modifier.height(8.dp))
                    Text(name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5A3A12))
                }
                Text(
                    "✎ Insel bearbeiten", color = Isle.TealDark, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp).shadow(3.dp, RoundedCornerShape(50))
                        .clip(RoundedCornerShape(50)).background(Color.White)
                        .clickable(onClick = onEdit).padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
            // Shelf with trophies.
            Text("Trophäenregal", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF173F26), modifier = Modifier.fillMaxWidth())
            Column(Modifier.fillMaxWidth().background(Color(0x22FFFFFF), RoundedCornerShape(12.dp)).padding(top = 8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Trophy("👥", "$friends", "Freunde")
                    Trophy("🏅", "$questsDone", "Quests")
                    Trophy("✉", "$letters", "Briefe")
                    Trophy("⭐", "$qp", "Punkte")
                }
                Box(Modifier.fillMaxWidth().height(12.dp).shadow(3.dp, RoundedCornerShape(3.dp)).background(Color(0xFF8A5A2B), RoundedCornerShape(3.dp)))
            }
        }
    }
}

@Composable
private fun Trophy(icon: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(icon, fontSize = 28.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF173F26))
        Text(label, fontSize = 11.sp, color = Color(0xFF3F6B4C))
    }
}

// ---------------------------------------------------------------- shared bits

@Composable
private fun EmptyPlace(title: String, text: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Isle.Ink, textAlign = TextAlign.Center)
        Text(text, color = Isle.Ink.copy(alpha = .7f), textAlign = TextAlign.Center)
    }
}

/** Avatar row to pick a friend – used to address letters. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun FriendPicker(title: String, pals: List<ApiClient.UserSummary>, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Isle.Card) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Isle.Ink)
            Spacer(Modifier.height(12.dp))
            if (pals.isEmpty()) Text("Du hast noch keine Freunde.", color = Isle.Muted)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                pals.forEach { f ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp).clip(RoundedCornerShape(14.dp)).clickable { onPick(f.id) }.padding(4.dp)) {
                        Box(Modifier.size(58.dp).background(profileColor(f.displayColor).copy(alpha = .25f), CircleShape), Alignment.Center) {
                            Text(f.avatarEmoji, fontSize = 28.sp)
                        }
                        Text(f.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Isle.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
