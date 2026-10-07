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

/** Painted background (Crop) plus a mapper from image fractions to on-screen boxes. */
internal class SceneMap(val left: Dp, val top: Dp, val w: Dp, val h: Dp) {
    fun x(f: Float): Dp = left + w * f
    fun y(f: Float): Dp = top + h * f
    fun region(x0: Float, y0: Float, x1: Float, y1: Float): Modifier =
        Modifier.offset(x = x(x0), y = y(y0)).size(width = w * (x1 - x0), height = h * (y1 - y0))
}

@Composable
internal fun PaintedScene(bg: Int, imgAspect: Float, content: @Composable androidx.compose.foundation.layout.BoxScope.(SceneMap) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sw = maxWidth; val sh = maxHeight
        // Crop: scale until both sides are covered, centered.
        val byHeight = sw / sh < imgAspect
        val iw = if (byHeight) sh * imgAspect else sw
        val ih = if (byHeight) sh else sw / imgAspect
        val map = SceneMap((sw - iw) / 2, (sh - ih) / 2, iw, ih)
        Image(
            painterResource(bg), null, Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
        content(map)
    }
}

private val Display = Kit.Display
private val Body = Kit.Body

/** Shell: painted place, a wooden sign as title, round back button, golden action. */
@Composable
internal fun PlaceStage(
    place: IsleBuilding,
    onClose: () -> Unit,
    action: Pair<String, () -> Unit>? = null,
    bg: Int = place.background(),
    imgAspect: Float = 1024f / 1536f,
    content: @Composable androidx.compose.foundation.layout.BoxScope.(SceneMap) -> Unit,
) {
    val st = place.style()
    Box(Modifier.fillMaxSize().background(st.bottom)) {
        PaintedScene(bg, imgAspect) { map -> content(map) }
        // Soft top vignette so the sign always reads.
        Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent))))
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(46.dp).shadow(6.dp, CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFFFFF6E5), Color(0xFFF1DDB4))), CircleShape)
                    .border(2.dp, Color(0xFF8A5A2B), CircleShape).clip(CircleShape)
                    .clickable(onClickLabel = "Zurück zur Insel", onClick = onClose),
                Alignment.Center,
            ) { AppIcon(R.drawable.ico_back, null, tint = Color(0xFF5A3A12), size = 26.dp) }
            Spacer(Modifier.width(10.dp))
            // The wooden sign.
            Box(Modifier.height(58.dp).width(230.dp), Alignment.Center) {
                Image(painterResource(R.drawable.p_sign), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.FillBounds)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        place.placeName(), fontFamily = Display, fontSize = 22.sp, color = Color(0xFFFFF6E5),
                        style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0xAA3B2410), Offset(0f, 3f), 2f)),
                    )
                }
            }
        }
        action?.let { (label, onClick) -> GoldButton(label, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 18.dp), onClick) }
    }
}

internal fun IsleBuilding.background(): Int = when (this) {
    IsleBuilding.POST -> R.drawable.bg_post
    IsleBuilding.HARBOUR -> R.drawable.bg_harbour
    IsleBuilding.LIGHTHOUSE -> R.drawable.bg_lighthouse
    IsleBuilding.LIBRARY -> R.drawable.bg_library
    IsleBuilding.CAMPFIRE -> R.drawable.bg_hall
    IsleBuilding.HOUSE -> R.drawable.bg_house
}

@Composable
internal fun GoldButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.shadow(10.dp, RoundedCornerShape(30.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFE2CB93), Color(0xFFC9A55C), Color(0xFFA9843F))), RoundedCornerShape(30.dp))
            .border(2.dp, Color(0xFF7E6236), RoundedCornerShape(30.dp))
            .clip(RoundedCornerShape(30.dp)).clickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 13.dp)
            .semantics { role = Role.Button },
    ) {
        Text(
            label, color = Color(0xFF5A3200), fontFamily = Display, fontSize = 18.sp,
            style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0x88FFFFFF), Offset(0f, 2f), 0f)),
        )
    }
}

/** Small cream label (name tags on cubbies, plaques under trophies). */
@Composable
private fun Tag(text: String, modifier: Modifier = Modifier, size: Int = 12, ink: Color = Color(0xFF3B2410), bg: Color = Color(0xF2FFF6E5)) {
    Text(
        text, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = size.sp, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier.shadow(2.dp, RoundedCornerShape(50)).background(bg, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun Sprite(res: Int, modifier: Modifier) = Image(painterResource(res), null, modifier)

// ---------------------------------------------------------------- Post

/** The back wall holds one wooden cubby per friend; letters sit inside as sealed envelopes. */
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
    PlaceStage(IsleBuilding.POST, onClose, action = "Brief schreiben" to { choosing = true }) { map ->
        val selected = fach?.let { id -> pals.firstOrNull { it.id == id } }
        val wall = Modifier.padding(top = 96.dp, bottom = 92.dp).fillMaxSize()
        if (selected == null) {
            if (pals.isEmpty()) Box(wall, Alignment.Center) { Parchment("Noch keine Fächer", "Für jeden Freund entsteht hier ein eigenes Postfach.") }
            else LazyVerticalGrid(
                GridCells.Fixed(3), wall.padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(pals, key = { it.id }) { f ->
                    val mine = letters.filter { it.peerId == f.id && it.groupId == null }
                    val ready = mine.count { it.incoming && Conversations.letterState(it, opened[it.id] != null) == LetterState.READY }
                    val travelling = mine.count { Conversations.letterState(it, opened[it.id] != null).locked }
                    Cubby(f, mine.size, ready, travelling) { fach = f.id }
                }
            }
        } else {
            val mine = letters.filter { it.peerId == selected.id && it.groupId == null }.sortedByDescending { it.createdAt }
            Column(wall) {
                Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tag("‹ Alle Fächer", Modifier.clickable { fach = null }, size = 14)
                    Spacer(Modifier.weight(1f))
                    Tag("${selected.avatarEmoji} Fach von ${selected.name}", size = 14)
                }
                if (mine.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Parchment("Fach ist leer", "Schreib ${selected.name} den ersten Brief.") }
                else LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 18.dp),
                    contentPadding = PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
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
private fun Cubby(friend: ApiClient.UserSummary, total: Int, ready: Int, travelling: Int, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Fach von ${friend.name} öffnen", onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Sprite(R.drawable.p_cubby, Modifier.fillMaxSize())
            // Envelopes stacked inside the opening.
            val n = total.coerceAtMost(3)
            for (i in 0 until n) Sprite(
                if (ready > 0 && i == n - 1) R.drawable.p_env else R.drawable.p_env,
                Modifier.align(Alignment.Center).offset(x = ((i - (n - 1) / 2f) * 6).dp, y = (10 - i * 7).dp).fillMaxWidth(.56f).rotate((i - 1) * 6f),
            )
            if (ready > 0) Box(
                Modifier.align(Alignment.TopEnd).size(24.dp).shadow(3.dp, CircleShape).background(Color(0xFFA4533F), CircleShape).border(2.dp, Color.White, CircleShape),
                Alignment.Center,
            ) { Text("$ready", color = Color.White, fontSize = 12.sp, fontFamily = Display) }
            if (travelling > 0) Row(
                Modifier.align(Alignment.TopStart).shadow(2.dp, RoundedCornerShape(50)).background(Color(0xFF3F7C78), RoundedCornerShape(50)).padding(horizontal = 5.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Sprite(R.drawable.boat_sail, Modifier.size(14.dp))
                Text(" $travelling", color = Color.White, fontSize = 11.sp, fontFamily = Display)
            }
        }
        Box(Modifier.offset(y = (-8).dp).fillMaxWidth(.95f).height(26.dp), Alignment.Center) {
            Sprite(R.drawable.p_plate, Modifier.fillMaxSize())
            Text(friend.name, fontFamily = Display, fontSize = 12.sp, color = Color(0xFF4A2A08), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 12.dp))
        }
    }
}

@Composable
private fun Envelope(m: ApiClient.Message, state: LetterState, onClick: () -> Unit) {
    val boat = LetterBoat.forMode(m.mode)
    Row(
        Modifier.fillMaxWidth().shadow(5.dp, RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFFFFBF1), Color(0xFFF6E7C8))), RoundedCornerShape(14.dp))
            .border(1.5.dp, Color(0xFFD9BE8A), RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClickLabel = if (state.locked) "Versiegelt" else "Brief öffnen", onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sprite(if (state.locked || state == LetterState.READY) R.drawable.p_env else R.drawable.p_env_open, Modifier.size(64.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text((if (m.incoming) "von " else "an ") + m.peerName, fontSize = 12.sp, color = Color(0xFF8A6A3A), fontFamily = Body)
            Text(Conversations.letterPreviewText(m), fontSize = 16.sp, fontFamily = Display, color = Color(0xFF3B2410), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    state.locked -> "${boat.label.substringBefore(" ·")} ist noch unterwegs"
                    state == LetterState.READY -> "Neu – zum Öffnen tippen"
                    m.incoming -> "Gelesen"
                    else -> "Zugestellt"
                },
                fontSize = 12.sp, fontFamily = Body, fontWeight = FontWeight.Bold,
                color = if (state == LetterState.READY) Color(0xFFA4533F) else Color(0xFF2F5D5A),
            )
        }
        Sprite(boat.res, Modifier.size(44.dp))
    }
}

// ---------------------------------------------------------------- Hafen

/** The harbour's cork board: quest notes are pinned straight onto the painted cork. */
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
    PlaceStage(IsleBuilding.HARBOUR, onClose, action = "Quest anpinnen" to onNewQuest) { map ->
        val shown = quests.filter { (it.completedAt != null) == showDone }
        Column(map.region(.17f, .315f, .83f, .615f).padding(horizontal = 6.dp)) {
            Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BoardTab("Offen ${quests.count { it.completedAt == null }}", !showDone) { showDone = false }
                BoardTab("Erledigt ${quests.count { it.completedAt != null }}", showDone) { showDone = true }
            }
            LazyVerticalGrid(
                GridCells.Fixed(2), Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                if (shown.isEmpty()) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                    Box(Modifier.fillMaxWidth().padding(top = 18.dp), Alignment.Center) {
                        Tag(if (showDone) "Noch nichts abgehakt" else "Das Brett ist leer", size = 14)
                    }
                }
                items(shown, key = { it.id }) { q -> QuestNote(q, onQuestDone, if (q.canDelete) onQuestDelete else null) }
            }
        }
        // Topics: a yellow sticky note slapped on the post below the board.
        val open = topics.count { it.completedAt == null }
        Box(
            map.region(.58f, .655f, .86f, .775f).worldTap("Themen öffnen", onAllTopics),
            Alignment.Center,
        ) {
            Sprite(R.drawable.p_sticky, Modifier.fillMaxSize().rotate(4f))
            Column(Modifier.rotate(4f).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Themen", fontFamily = Display, fontSize = 16.sp, color = Color(0xFF4A3B00))
                Text(if (open == 0) "alles besprochen" else "$open offen", fontFamily = Body, fontSize = 11.sp, color = Color(0xFF6B5A10), textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

@Composable
private fun BoardTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text, fontFamily = Display, fontSize = 13.sp,
        color = if (selected) Color(0xFFFFF6E5) else Color(0xFF5A3A12),
        modifier = Modifier.shadow(if (selected) 3.dp else 0.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50))
            .background(if (selected) Color(0xFF7A4B26) else Color(0xCCFFF6E5))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
private fun QuestNote(q: ApiClient.Quest, onDone: (ApiClient.Quest, Boolean) -> Unit, onDelete: ((ApiClient.Quest) -> Unit)?) {
    val tilt = ((q.id % 5) - 2) * 1.6f
    val done = q.completedAt != null
    Box(Modifier.rotate(tilt).aspectRatio(.95f)) {
        Sprite(R.drawable.p_note, Modifier.fillMaxSize().padding(top = 6.dp))
        Column(Modifier.fillMaxSize().padding(start = 14.dp, end = 12.dp, top = 22.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(AppIcons.questIcon(q.icon), AppIcons.questLabels[q.icon], tint = Color(0xFF5A3A12), size = 20.dp)
                Spacer(Modifier.weight(1f))
                Sprite(R.drawable.p_star, Modifier.size(15.dp))
                Text("${q.points}", fontFamily = Display, color = Color(0xFF9A6B00), fontSize = 13.sp)
            }
            Text(q.title, fontFamily = Display, color = Color(0xFF3B2410), fontSize = 14.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (q.targetType == "group") q.targetName else "mit ${q.targetName}", fontFamily = Body, fontSize = 10.sp, color = Color(0xFF8A6A3A), maxLines = 1)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (done) "✓" else "Abhaken", fontFamily = Display, fontSize = 11.sp,
                    color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (done) Color(0xFF5E7F5A) else Color(0xFF3F7C78))
                        .clickable { onDone(q, !done) }.padding(horizontal = 9.dp, vertical = 3.dp),
                )
                Spacer(Modifier.weight(1f))
                if (onDelete != null) AppIcon(R.drawable.ico_close, null, modifier = Modifier.clip(CircleShape).clickable { onDelete(q) }.padding(4.dp), tint = Color(0xFF8A6A3A), size = 13.dp)
            }
        }
        Sprite(R.drawable.p_pin, Modifier.align(Alignment.TopCenter).size(22.dp))
        if (done) Text(
            "ERLEDIGT", color = Color(0xB32E7D32), fontFamily = Display, fontSize = 16.sp,
            modifier = Modifier.align(Alignment.Center).rotate(-18f).border(2.dp, Color(0xB32E7D32), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp),
        )
    }
}

// ---------------------------------------------------------------- Leuchtturm

/** From the lighthouse gallery at night: the beam sweeps, friends glow in brass portholes. */
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
    val angle by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val bob by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "bob")
    PlaceStage(IsleBuilding.LIGHTHOUSE, onClose, action = "Freunde finden" to onFind, imgAspect = 941f / 1672f) { map ->
        Canvas(Modifier.fillMaxSize()) {
            val origin = Offset(size.width * .5f, size.height * 1.02f)
            val a = angle * .9f
            val len = size.height * 1.4f
            val spread = .16f
            val p = Path().apply {
                moveTo(origin.x, origin.y)
                lineTo(origin.x + len * sin(a - spread), origin.y - len * cos(a - spread))
                lineTo(origin.x + len * sin(a + spread), origin.y - len * cos(a + spread))
                close()
            }
            drawPath(p, Brush.radialGradient(listOf(Color(0x66FFF3B0), Color(0x22FFF3B0), Color(0x00FFF3B0)), origin, len))
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 18.dp),
            contentPadding = PaddingValues(top = 100.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (incoming.isNotEmpty()) {
                item { NightTitle("Flaschenpost angespült") }
                items(incoming, key = { "in-${it.id}" }) { r -> Bottle(r.senderName, bob, onYes = { onRespond(r.id, true) }, onNo = { onRespond(r.id, false) }) }
            }
            item { NightTitle(if (pals.isEmpty()) "Noch leuchtet niemand zurück" else "Im Lichtkegel") }
            item {
                LazyVerticalGrid(
                    GridCells.Fixed(3), Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp), userScrollEnabled = false,
                ) { items(pals, key = { it.id }) { f -> Porthole(f, levels[f.id] ?: 1) { onFriend(f.id) } } }
            }
            if (outgoing.isNotEmpty()) items(outgoing, key = { "out-${it.id}" }) { r ->
                Tag("Signal an ${r.recipientName} – noch keine Antwort", ink = Color(0xFFFFF3B0), bg = Color(0x66000000), size = 12)
            }
        }
    }
}

@Composable
private fun NightTitle(text: String) {
    Text(
        text, fontFamily = Display, fontSize = 19.sp, color = Color(0xFFFFE9A8),
        style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0xCC000000), Offset(0f, 2f), 6f)),
    )
}

@Composable
private fun Porthole(f: ApiClient.UserSummary, level: Int, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = "${f.name} öffnen", onClick = onClick)) {
        Box(Modifier.size(88.dp), Alignment.Center) {
            Box(
                Modifier.size(56.dp).background(Brush.radialGradient(listOf(Color(0xFFFFF3B0), profileColor(f.displayColor))), CircleShape),
                Alignment.Center,
            ) { Text(f.avatarEmoji, fontSize = 28.sp) }
            Sprite(R.drawable.p_porthole, Modifier.fillMaxSize())
        }
        Text(
            f.name, color = Color.White, fontFamily = Display, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.background(Color(0x99102040), RoundedCornerShape(50)).padding(horizontal = 8.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(level.coerceIn(1, 4)) { Sprite(R.drawable.p_star, Modifier.size(11.dp)) }
        }
    }
}

@Composable
private fun Bottle(name: String, bob: Float, onYes: () -> Unit, onNo: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().offset(y = (bob * 3).dp).shadow(6.dp, RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Color(0xF2FFFBF1), Color(0xF2F6E7C8))), RoundedCornerShape(22.dp))
            .border(1.5.dp, Color(0xFFD9BE8A), RoundedCornerShape(22.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sprite(R.drawable.p_bottle, Modifier.size(52.dp).rotate(-35f + bob * 6))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontFamily = Display, color = Color(0xFF3B2410), fontSize = 16.sp)
            Text("möchte dein Freund werden", fontFamily = Body, color = Color(0xFF8A6A3A), fontSize = 12.sp)
        }
        Text("Nein", fontFamily = Body, fontWeight = FontWeight.Bold, color = Color(0xFF8A6A3A), modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onNo).padding(horizontal = 8.dp, vertical = 8.dp))
        Text(
            "Annehmen", fontFamily = Display, color = Color.White, fontSize = 14.sp,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF3F7C78)).clickable(onClick = onYes).padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

// ---------------------------------------------------------------- Bibliothek

/** The glossary lies open on the reading desk among the shelves. */
@Composable
internal fun LibraryBook(onClose: () -> Unit, glossary: @Composable () -> Unit) {
    PlaceStage(IsleBuilding.LIBRARY, onClose) { map ->
        Box(
            Modifier.fillMaxSize().padding(top = 92.dp, start = 14.dp, end = 14.dp, bottom = 14.dp).navigationBarsPadding(),
        ) {
            // Leather cover peeking out behind the page.
            Box(
                Modifier.fillMaxSize().shadow(14.dp, RoundedCornerShape(18.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF4F5D63), Color(0xFF34414A))), RoundedCornerShape(18.dp))
                    .border(2.dp, Color(0xFFC9A55C), RoundedCornerShape(18.dp)).padding(7.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFFE6D6B4), Color(0xFFFFF9EC), Color(0xFFFFF9EC), Color(0xFFF3E6CA)))),
            ) {
                MaterialTheme(
                    colorScheme = lightColorScheme(
                        primary = Color(0xFF3F5A63), onPrimary = Color.White,
                        primaryContainer = Color(0xFFE6DAF5), onPrimaryContainer = Color(0xFF2E2150),
                        secondaryContainer = Color(0xFFF1E3C4), onSecondaryContainer = Color(0xFF3B2410),
                        surface = Color.Transparent, onSurface = Color(0xFF2E2150),
                        surfaceContainer = Color(0x14FFFFFF), surfaceContainerLow = Color(0x14FFFFFF), surfaceContainerHigh = Color(0x22FFFFFF),
                        surfaceVariant = Color(0xFFF1E7D2), onSurfaceVariant = Color(0xFF6B5B48),
                        background = Color.Transparent, outline = Color(0xFFC9B48E), outlineVariant = Color(0xFFE2D3B0),
                    ),
                    typography = MaterialTheme.typography,
                ) { glossary() }
            }
            Sprite(R.drawable.p_quill, Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 4.dp).size(64.dp))
        }
    }
}

// ---------------------------------------------------------------- Gemeindehaus

/** The hall floor: every group is a round table with its members on stools. */
@Composable
internal fun CommunityHall(
    groups: List<ApiClient.Group>,
    quests: List<ApiClient.Quest>,
    onGroupQuest: (Long) -> Unit,
    onManage: () -> Unit,
    onClose: () -> Unit,
) {
    PlaceStage(IsleBuilding.CAMPFIRE, onClose, action = "Gruppen verwalten" to onManage) { map ->
        if (groups.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Parchment("Noch keine Tische", "Gründe eine Gruppe – sie bekommt hier ihren eigenen Tisch.") }
        else LazyVerticalGrid(
            GridCells.Fixed(2), Modifier.fillMaxSize().padding(horizontal = 10.dp),
            contentPadding = PaddingValues(top = map.y(.3f).coerceAtLeast(120.dp), bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
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
            val seat: Dp = w * .25f
            val members = g.members.take(8)
            // Stools first (behind), then the table, then the people on the front stools.
            val placed = members.mapIndexed { i, m ->
                val a = (i.toFloat() / members.size) * 2f * PI.toFloat() - PI.toFloat() / 2
                Triple(m, w / 2 + w * .37f * cos(a) - seat / 2, w / 2 + w * .33f * sin(a) - seat / 2)
            }
            placed.forEach { (_, x, y) -> Sprite(R.drawable.p_stool, Modifier.offset(x = x, y = y + seat * .25f).size(seat)) }
            Sprite(R.drawable.p_table, Modifier.align(Alignment.Center).size(w * .58f))
            if (openQuests > 0) Row(
                Modifier.align(Alignment.Center).offset(y = (-4).dp).shadow(2.dp, RoundedCornerShape(50)).background(Color(0xF2FFF6E5), RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Sprite(R.drawable.p_star, Modifier.size(13.dp))
                Text(" $openQuests", fontFamily = Display, fontSize = 12.sp, color = Color(0xFF9A6B00))
            }
            placed.forEach { (m, x, y) ->
                Box(
                    Modifier.offset(x = x + seat * .1f, y = y - seat * .05f).size(seat * .8f)
                        .shadow(4.dp, CircleShape).background(Color.White, CircleShape).padding(2.dp)
                        .background(Brush.radialGradient(listOf(Color.White, profileColor(m.displayColor).copy(alpha = .55f))), CircleShape)
                        .semantics { contentDescription = m.name },
                    Alignment.Center,
                ) { Text(m.avatarEmoji, fontSize = 16.sp) }
            }
        }
        Tag(g.name, size = 14, modifier = Modifier.offset(y = (-6).dp))
        Text("${g.members.size} am Tisch", fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFFFF6E5),
            style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0xAA3B2410), Offset(0f, 1f), 3f)),
            modifier = Modifier.offset(y = (-4).dp))
    }
}

// ---------------------------------------------------------------- Mein Haus

/** The cottage living room: portrait on the wall, trophies on the painted shelf. */
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
    PlaceStage(IsleBuilding.HOUSE, onClose, action = "Profil & Einstellungen" to onSettings) { map ->
        // Portrait in a golden frame, centred on the empty wall.
        Box(map.region(.36f, .16f, .7f, .43f), Alignment.Center) {
            Box(
                Modifier.fillMaxSize(.72f).background(Brush.radialGradient(listOf(Color.White, profileColor(color).copy(alpha = .6f)))),
                Alignment.Center,
            ) { Text(emoji, fontSize = 54.sp) }
            Sprite(R.drawable.p_frame, Modifier.fillMaxSize())
        }
        Box(Modifier.offset(x = map.x(.36f), y = map.y(.425f)).width(map.w * .34f), Alignment.TopCenter) { Tag(name, size = 15) }
        // Trophies standing on the shelf: their bottoms touch the shelf top.
        val shelfTop = map.y(.535f)
        val items = listOf(
            Triple(R.drawable.p_medal, "$friends", "Freunde"),
            Triple(R.drawable.p_trophy, "$questsDone", if (questsDone == 1) "Quest" else "Quests"),
            Triple(R.drawable.p_env, "$letters", "Briefe"),
            Triple(R.drawable.p_star, "$qp", "Punkte"),
        )
        Row(
            Modifier.fillMaxWidth().offset(y = shelfTop - 64.dp).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEach { (res, v, l) ->
                Column(Modifier.width(76.dp).semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
                    Sprite(res, Modifier.size(56.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(v, fontFamily = Display, fontSize = 20.sp, color = Color(0xFF3B2410))
                    Text(l, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF3B2410),
                        modifier = Modifier.background(Color(0xCCFFF6E5), RoundedCornerShape(50)).padding(horizontal = 6.dp))
                }
            }
        }
        Box(Modifier.fillMaxWidth().offset(y = map.y(.7f)), Alignment.TopCenter) { GoldButtonSmall("Insel bearbeiten", Modifier.width(170.dp), onEdit) }
    }
}

@Composable
private fun GoldButtonSmall(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.shadow(6.dp, RoundedCornerShape(24.dp)).background(Color(0xFFFFFBF1), RoundedCornerShape(24.dp))
            .border(2.dp, Color(0xFF3F7C78), RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick).padding(vertical = 10.dp),
        Alignment.Center,
    ) { Text(label, fontFamily = Display, fontSize = 15.sp, color = Color(0xFF2F5D5A)) }
}

/** Cream parchment card for empty states inside a place. */
@Composable
private fun Parchment(title: String, text: String) {
    Column(
        Modifier.padding(30.dp).shadow(8.dp, RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFFFFBF1), Color(0xFFF6E7C8))), RoundedCornerShape(18.dp))
            .border(1.5.dp, Color(0xFFD9BE8A), RoundedCornerShape(18.dp)).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontFamily = Display, fontSize = 20.sp, color = Color(0xFF3B2410), textAlign = TextAlign.Center)
        Text(text, fontFamily = Body, color = Color(0xFF6B5B48), textAlign = TextAlign.Center)
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
