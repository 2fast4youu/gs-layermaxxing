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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.semantics.stateDescription
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

/**
 * Shell of every function stage (7.9.12): the stage's own place ([FunctionStage]) fills the screen, the shared
 * head (round back button, wooden name sign, the stage's island piece, the stage strip) and the golden action stay.
 * [content] puts the real data objects into the picture via the [StageGeometry].
 */
@Composable
internal fun PlaceStage(
    place: IsleBuilding,
    onClose: () -> Unit,
    /** 7.9.9: the building's current stage (0–3): title, head picture, icon and line follow [IslandStages]. */
    level: Int = 3,
    action: Pair<String, () -> Unit>? = null,
    content: @Composable androidx.compose.foundation.layout.BoxScope.(StageGeometry) -> Unit,
) {
    val st = place.style()
    val scene = StageScenes.of(place, level)
    Box(Modifier.fillMaxSize().background(st.bottom)) {
        FunctionStage(scene) { g -> content(g) }
        // Soft top vignette so the sign always reads.
        Box(Modifier.fillMaxWidth().height(110.dp).background(Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent))))
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
            val stage = IslandStages.of(place, level)
            Box(Modifier.height(58.dp).weight(1f, fill = false).width(214.dp), Alignment.Center) {
                Image(painterResource(R.drawable.p_sign), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.FillBounds)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp)) {
                    AppIcon(stage.icon, null, tint = Color(0xFFFFF6E5), size = 18.dp)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        stage.name, fontFamily = Display, fontSize = if (stage.name.length > 14) 17.sp else 21.sp, color = Color(0xFFFFF6E5), maxLines = 1,
                        style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0xAA3B2410), Offset(0f, 3f), 2f)),
                        modifier = Modifier.semantics { contentDescription = "Ort: ${stage.name}" },
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(58.dp).shadow(4.dp, CircleShape).background(Brush.radialGradient(listOf(Color(0xFFFFF6E5), Color(0xFFE9D3A6))), CircleShape)
                .border(2.dp, Color(0xFF8A5A2B), CircleShape).padding(5.dp), Alignment.BottomCenter) {
                PaintedIslandPlace(place, level, stage.res, Modifier.fillMaxSize(), description = "Kopfbild: ${stage.name}")
            }
        }
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 70.dp, start = 68.dp, end = 12.dp)) {
            val stage = IslandStages.of(place, level)
            Row(Modifier.shadow(2.dp, RoundedCornerShape(50)).background(IslandWood.Paper, RoundedCornerShape(50))
                .border(1.dp, IslandWood.PaperEdge.copy(alpha = .7f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically) {
                StagePips(level); Spacer(Modifier.width(6.dp))
                Text("${stage.subtitle} · ${stage.look}", fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp, color = IslandWood.Ink, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { contentDescription = "Stufe: ${stage.subtitle}" })
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

/** Small cream label (name tags, plaques). */
@Composable
private fun Tag(text: String, modifier: Modifier = Modifier, size: Int = 12, ink: Color = Color(0xFF3B2410), bg: Color = Color(0xF2FFF6E5)) {
    Text(
        text, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = size.sp, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier.shadow(2.dp, RoundedCornerShape(50)).background(bg, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun Sprite(res: Int, modifier: Modifier) = Image(painterResource(res), null, modifier)

/**
 * A data object centred on its scene anchor, kept between the shared header and the pages / golden action at the
 * bottom (a short phone pushes it up a little rather than letting the controls cover it).
 */
private fun StageGeometry.box(p: androidx.compose.ui.unit.DpOffset, w: Dp, h: Dp = w): Modifier {
    val top = (p.y - h / 2).coerceAtMost(screen.h - reserve - h).coerceAtLeast(104.dp)
    return Modifier.offset(x = p.x - w / 2, y = top).size(w, h)
}

/** The anchor of every data object, for tests and accessibility: "<kind>:<id>" at its scene slot. */
internal val SceneSlotKey = androidx.compose.ui.semantics.SemanticsPropertyKey<String>("SceneSlot")

/**
 * Scenic pages when there is more data than places in the picture (7.9.12): two wooden arrows and a plaque, right
 * above the golden action. Nothing is filtered away; every object is reachable.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.ScenePager(page: Int, pages: Int, what: String, onPage: (Int) -> Unit) {
    if (pages <= 1) return
    Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp), verticalAlignment = Alignment.CenterVertically) {
        PagerArrow("‹", "Vorige Seite: $what", page > 0) { onPage(page - 1) }
        Box(Modifier.padding(horizontal = 6.dp).height(34.dp).width(132.dp), Alignment.Center) {
            Sprite(R.drawable.p_plate, Modifier.fillMaxSize())
            Text("$what ${page + 1}/$pages", fontFamily = Display, fontSize = 13.sp, color = Color(0xFF4A2A08), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp).semantics { contentDescription = "Seite ${page + 1} von $pages: $what" })
        }
        PagerArrow("›", "Nächste Seite: $what", page < pages - 1) { onPage(page + 1) }
    }
}

@Composable
private fun PagerArrow(glyph: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).shadow(if (enabled) 4.dp else 0.dp, CircleShape)
            .background(if (enabled) Color(0xFF7A4B26) else Color(0x667A4B26), CircleShape).border(2.dp, Color(0xFFE9D3A6), CircleShape)
            .clip(CircleShape).clickable(enabled = enabled, onClickLabel = label, onClick = onClick).semantics { contentDescription = label; role = Role.Button },
        Alignment.Center,
    ) { Text(glyph, fontFamily = Display, fontSize = 24.sp, color = Color(0xFFFFF6E5)) }
}

/** A short paper slip pinned into the scene (empty states), never a card over the whole place. */
@Composable
private fun SceneSlip(title: String, text: String, modifier: Modifier) {
    Column(
        modifier.rotate(-2f).shadow(5.dp, RoundedCornerShape(6.dp)).background(Color(0xFFFFF8E6), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFFD9BE8A), RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontFamily = Display, fontSize = 15.sp, color = Color(0xFF3B2410), textAlign = TextAlign.Center)
        Text(text, fontFamily = Body, fontSize = 12.sp, color = Color(0xFF6B5B48), textAlign = TextAlign.Center)
    }
}

// ---------------------------------------------------------------- Post

/**
 * Letters, 7.9.12: every friend's letters are ONE object in the picture, at an anchor of the stage's place: an
 * envelope pinned to a branch of the letter tree, a pick-up slot with name plate on the mailbox stand, a cubby on the
 * board of the small post room, a cubby in the post office's wall. Tapping it opens that friend's letters right
 * there (on the tree, on the plank in front of the mailbox, on the counter). More friends or letters = pages.
 */
@Composable
internal fun PostOffice(
    pals: List<ApiClient.UserSummary>,
    letters: List<ApiClient.Message>,
    opened: Map<Long, OpenedMessage>,
    onOpen: (ApiClient.Message) -> Unit,
    onLocked: (ApiClient.Message) -> Unit,
    onWrite: (Long) -> Unit,
    onClose: () -> Unit,
    level: Int = 3,
) {
    var fach by rememberSaveable { mutableStateOf<Long?>(null) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable(fach) { mutableStateOf(0) }
    val scene = StageScenes.of(IsleBuilding.POST, level)
    PlaceStage(IsleBuilding.POST, onClose, level, action = "Brief schreiben" to { if (fach != null) onWrite(fach!!) else choosing = true }) { g ->
        val selected = fach?.let { id -> pals.firstOrNull { it.id == id } }
        if (selected == null) {
            val slots = g.slots
            val size = g.objectSize(slots, max = 104.dp, aspect = 1.15f)
            if (pals.isEmpty()) SceneSlip(
                when (scene.holder) { StageScenes.Holder.PINNED_ENVELOPE -> "Noch nichts am Briefbaum"; StageScenes.Holder.MAILBOX -> "Der Postkasten ist leer"; else -> "Noch keine Fächer" },
                "Für jeden Freund entsteht hier sein eigener Platz.", g.box(slots.first(), 200.dp, 90.dp))
            val pages = pals.chunked(slots.size)
            if (pages.size > 1) g.reserve = 132.dp
            pages.getOrNull(page.coerceAtMost(pages.lastIndex.coerceAtLeast(0)))?.forEachIndexed { i, f ->
                val mine = letters.filter { it.peerId == f.id && it.groupId == null }
                val ready = mine.count { it.incoming && Conversations.letterState(it, opened[it.id] != null) == LetterState.READY }
                val travelling = mine.count { Conversations.letterState(it, opened[it.id] != null).locked }
                LetterGroup(f, mine.size, ready, travelling, scene.holder, scene.slots[i].tilt, g.box(slots[i], size, size * 1.15f).semantics { set(SceneSlotKey, "friend:${f.id}") }) { fach = f.id }
            }
            ScenePager(page, pages.size, "Freunde") { page = it }
        } else {
            val mine = letters.filter { it.peerId == selected.id && it.groupId == null }.sortedByDescending { it.createdAt }
            val slots = g.detailSlots
            val size = g.objectSize(slots, max = 104.dp, aspect = 1.15f)
            // the opened group's sign hangs above its letters; "Alle" brings back every friend's place
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 104.dp, start = 16.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Tag("‹ Alle", Modifier.clip(RoundedCornerShape(50)).clickable(onClickLabel = "Alle Freunde zeigen") { fach = null }.heightIn(min = 32.dp), size = 15)
                Spacer(Modifier.width(8.dp))
                Tag("${selected.avatarEmoji} Briefe von und an ${selected.name}", size = 14)
            }
            if (mine.isEmpty()) SceneSlip("Hier ist noch nichts", "Schreib ${selected.name} den ersten Brief.", g.box(slots.first(), 220.dp, 90.dp))
            val pages = mine.chunked(slots.size)
            if (pages.size > 1) g.reserve = 132.dp
            pages.getOrNull(page.coerceAtMost(pages.lastIndex.coerceAtLeast(0)))?.forEachIndexed { i, m ->
                val state = Conversations.letterState(m, opened[m.id] != null)
                LetterToken(m, state, scene.holder, scene.detailSlots[i].tilt, g.box(slots[i], size, size * 1.15f).semantics { set(SceneSlotKey, "letter:${m.id}") }) {
                    if (state.locked) onLocked(m) else onOpen(m)
                }
            }
            ScenePager(page, pages.size, "Briefe") { page = it }
        }
    }
    if (choosing) FriendPicker("An wen geht der Brief?", pals, onDismiss = { choosing = false }) { choosing = false; onWrite(it) }
}

/** One friend's letters as a thing of the stage. */
@Composable
private fun LetterGroup(friend: ApiClient.UserSummary, total: Int, ready: Int, travelling: Int, holder: StageScenes.Holder, tilt: Float, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Briefe von ${friend.name} öffnen", onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "${friend.name}: $total Briefe" + (if (ready > 0) ", $ready neu" else "") },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (holder) {
                StageScenes.Holder.PINNED_ENVELOPE -> {
                    Sprite(if (total > 0) R.drawable.p_env else R.drawable.p_env_open, Modifier.align(Alignment.Center).offset(y = 6.dp).fillMaxWidth(.9f).rotate(tilt))
                    Sprite(R.drawable.p_pin, Modifier.align(Alignment.TopCenter).size(24.dp))
                }
                StageScenes.Holder.MAILBOX -> {
                    // a pick-up slot of the mailbox: the plate is the slot, the letters stick out of it
                    Sprite(R.drawable.p_cubby, Modifier.align(Alignment.Center).fillMaxSize(.92f))
                    if (total > 0) Sprite(if (ready > 0) R.drawable.fief_env_ready else R.drawable.fief_env_sealed, Modifier.align(Alignment.Center).fillMaxWidth(.66f).rotate(tilt - 4f))
                }
                else -> {
                    Sprite(R.drawable.p_cubby, Modifier.fillMaxSize())
                    val n = total.coerceAtMost(3)
                    for (i in 0 until n) Sprite(R.drawable.p_env, Modifier.align(Alignment.Center).offset(x = ((i - (n - 1) / 2f) * 5).dp, y = (8 - i * 6).dp).fillMaxWidth(.56f).rotate((i - 1) * 6f))
                }
            }
            if (total > 0) Text("$total", fontFamily = Display, fontSize = 12.sp, color = Color(0xFF4A2A08),
                modifier = Modifier.align(Alignment.BottomEnd).shadow(2.dp, CircleShape).background(Color(0xFFFFF6E5), CircleShape).padding(horizontal = 7.dp, vertical = 1.dp))
            if (ready > 0) Box(
                Modifier.align(Alignment.TopEnd).size(22.dp).shadow(3.dp, CircleShape).background(Color(0xFFA4533F), CircleShape).border(2.dp, Color.White, CircleShape),
                Alignment.Center,
            ) { Text("$ready", color = Color.White, fontSize = 11.sp, fontFamily = Display) }
            if (travelling > 0) Row(
                Modifier.align(Alignment.TopStart).shadow(2.dp, RoundedCornerShape(50)).background(Color(0xFF3F7C78), RoundedCornerShape(50)).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Sprite(R.drawable.boat_sail, Modifier.size(12.dp))
                Text(" $travelling", color = Color.White, fontSize = 10.sp, fontFamily = Display)
            }
        }
        Box(Modifier.fillMaxWidth().height(22.dp), Alignment.Center) {
            Sprite(R.drawable.p_plate, Modifier.fillMaxSize())
            Text(friend.name, fontFamily = Display, fontSize = 11.sp, color = Color(0xFF4A2A08), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp))
        }
    }
}

/** One letter of the opened group: pinned to the tree, lying on the plank or on the counter. */
@Composable
private fun LetterToken(m: ApiClient.Message, state: LetterState, holder: StageScenes.Holder, tilt: Float, modifier: Modifier, onClick: () -> Unit) {
    val boat = LetterBoat.forMode(m.mode)
    val status = when {
        state.locked -> "${boat.label.substringBefore(" ·")} unterwegs"
        state == LetterState.READY -> "Neu – öffnen"
        m.incoming -> "Gelesen"
        else -> "Zugestellt"
    }
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = if (state.locked) "Versiegelt" else "Brief öffnen", onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "Brief " + (if (m.incoming) "von " else "an ") + "${m.peerName}: ${Conversations.letterPreviewText(m)}, $status" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Sprite(if (state.locked || state == LetterState.READY) R.drawable.p_env else R.drawable.p_env_open, Modifier.align(Alignment.Center).fillMaxWidth(.92f).rotate(tilt))
            if (holder == StageScenes.Holder.PINNED_ENVELOPE) Sprite(R.drawable.p_pin, Modifier.align(Alignment.TopCenter).size(22.dp))
            Sprite(boat.res, Modifier.align(Alignment.BottomEnd).size(26.dp))
        }
        Text(Conversations.letterPreviewText(m), fontFamily = Display, fontSize = 11.sp, color = Color(0xFF3B2410), maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.background(Color(0xEEFFF6E5), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp))
        Text(status, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (state == LetterState.READY) Color(0xFFA4533F) else Color(0xFF2F5D5A),
            modifier = Modifier.background(Color(0xDDFFF6E5), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp))
    }
}

// ---------------------------------------------------------------- Hafen

/**
 * Quests and topics, 7.9.12: the notes hang where the stage keeps its orders: tied to the beach pole, nailed to the
 * jetty posts, pinned to the landing's notice board, on the big harbour board. Open / done as two wooden tabs,
 * more notes = pages. The topics sit on a sticky note on the planks.
 */
@Composable
internal fun HarbourBoard(
    quests: List<ApiClient.Quest>,
    topics: List<ApiClient.Topic>,
    onQuestDone: (ApiClient.Quest, Boolean) -> Unit,
    onQuestDelete: (ApiClient.Quest) -> Unit,
    onNewQuest: () -> Unit,
    onAllTopics: () -> Unit,
    onClose: () -> Unit,
    level: Int = 3,
) {
    var showDone by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable(showDone) { mutableStateOf(0) }
    val scene = StageScenes.of(IsleBuilding.HARBOUR, level)
    PlaceStage(IsleBuilding.HARBOUR, onClose, level, action = "Quest anpinnen" to onNewQuest) { g ->
        val shown = quests.filter { (it.completedAt != null) == showDone }
        Row(Modifier.statusBarsPadding().padding(top = 104.dp, start = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BoardTab("Offen ${quests.count { it.completedAt == null }}", !showDone) { showDone = false }
            BoardTab("Erledigt ${quests.count { it.completedAt != null }}", showDone) { showDone = true }
        }
        val slots = g.slots
        val size = g.objectSize(slots, max = 124.dp, min = 104.dp, aspect = 1.05f)
        if (shown.isEmpty()) SceneSlip(if (showDone) "Noch nichts abgehakt" else "Noch kein Auftrag", "Pinne eine Quest an.", g.box(slots.first(), 180.dp, 80.dp))
        val pages = shown.chunked(slots.size)
        if (pages.size > 1) g.reserve = 132.dp
        pages.getOrNull(page.coerceAtMost(pages.lastIndex.coerceAtLeast(0)))?.forEachIndexed { i, q ->
            Box(g.box(slots[i], size, size * 1.05f).semantics { set(SceneSlotKey, "quest:${q.id}") }) {
                QuestNote(q, onQuestDone, if (q.canDelete) onQuestDelete else null, pinned = scene.holder != StageScenes.Holder.NOTES_ON_POST, tilt = scene.slots[i].tilt)
            }
        }
        ScenePager(page, pages.size, "Aufträge") { page = it }
        val open = topics.count { it.completedAt == null }
        Box(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 10.dp, bottom = 134.dp).size(96.dp, 84.dp).worldTap("Themen öffnen", onAllTopics),
            Alignment.Center,
        ) {
            Sprite(R.drawable.p_sticky, Modifier.fillMaxSize().rotate(4f))
            Column(Modifier.rotate(4f).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Themen", fontFamily = Display, fontSize = 15.sp, color = Color(0xFF4A3B00))
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
            .background(if (selected) Color(0xFF7A4B26) else Color(0xEEFFF6E5))
            .clickable(onClick = onClick).heightIn(min = 32.dp).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun QuestNote(q: ApiClient.Quest, onDone: (ApiClient.Quest, Boolean) -> Unit, onDelete: ((ApiClient.Quest) -> Unit)?, pinned: Boolean = true, tilt: Float = 0f) {
    val done = q.completedAt != null
    Box(Modifier.fillMaxSize().rotate(tilt)) {
        Sprite(R.drawable.p_note, Modifier.fillMaxSize().padding(top = 6.dp))
        Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 10.dp, top = 18.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(AppIcons.questIcon(q.icon), AppIcons.questLabels[q.icon], tint = Color(0xFF5A3A12), size = 18.dp)
                Spacer(Modifier.weight(1f))
                Sprite(R.drawable.p_star, Modifier.size(14.dp))
                Text("${q.points}", fontFamily = Display, color = Color(0xFF9A6B00), fontSize = 12.sp)
            }
            Text(q.title, fontFamily = Display, color = Color(0xFF3B2410), fontSize = 13.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (q.targetType == "group") q.targetName else "mit ${q.targetName}", fontFamily = Body, fontSize = 10.sp, color = Color(0xFF8A6A3A), maxLines = 1)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (done) "Erledigt" else "Abhaken", fontFamily = Display, fontSize = 11.sp, color = Color.White, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (done) Color(0xFF5E7F5A) else Color(0xFF3F7C78))
                        .clickable { onDone(q, !done) }.padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Spacer(Modifier.weight(1f))
                if (onDelete != null) AppIcon(R.drawable.ico_close, "Quest löschen", modifier = Modifier.clip(CircleShape).clickable { onDelete(q) }.padding(4.dp), tint = Color(0xFF8A6A3A), size = 13.dp)
            }
        }
        // pinned to a board / nailed to a post; tied to the beach pole with a cord
        if (pinned) Sprite(R.drawable.p_pin, Modifier.align(Alignment.TopCenter).size(20.dp))
        else Canvas(Modifier.align(Alignment.TopCenter).size(30.dp, 12.dp)) { drawLine(Color(0xFF8A6A3A), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 3f * density) }
        if (done) Text(
            "ERLEDIGT", color = Color(0xB32E7D32), fontFamily = Display, fontSize = 14.sp,
            modifier = Modifier.align(Alignment.Center).rotate(-18f).border(2.dp, Color(0xB32E7D32), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp),
        )
    }
}

// ---------------------------------------------------------------- Leuchtturm

/**
 * Friends and requests, 7.9.12: friends are what one sees from this stage, at places in its field of view: rowing
 * boats in sight of the palm, sailing boats on the evening horizon from the lookout, signal lights on the night sea
 * from the beacon, boats in the beam from the gallery. Requests not answered yet are boats that still wait (faded),
 * requests to me are bottles washed ashore with Yes / No. More friends = pages.
 */
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
    level: Int = 3,
) {
    val t = rememberInfiniteTransition(label = "beam")
    val angle by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val bob by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "bob")
    val scene = StageScenes.of(IsleBuilding.LIGHTHOUSE, level)
    var page by rememberSaveable { mutableStateOf(0) }
    var bottlePage by rememberSaveable { mutableStateOf(0) }
    PlaceStage(IsleBuilding.LIGHTHOUSE, onClose, level, action = "Freunde finden" to onFind) { g ->
        if (level >= 2) Canvas(Modifier.fillMaxSize()) {
            // the beam: from the beacon's lamp on the rocks, from below the gallery in the tower
            val origin = g.piece?.let { Offset(it.x(.5f).toPx(), it.y(.12f).toPx()) } ?: Offset(size.width * .5f, size.height * 1.02f)
            val a = angle * .9f + (if (level == 2) .9f else 0f)
            val len = size.height * 1.4f
            val p = Path().apply {
                moveTo(origin.x, origin.y)
                lineTo(origin.x + len * sin(a - .16f), origin.y - len * cos(a - .16f))
                lineTo(origin.x + len * sin(a + .16f), origin.y - len * cos(a + .16f))
                close()
            }
            drawPath(p, Brush.radialGradient(listOf(Color(0x66FFF3B0), Color(0x22FFF3B0), Color(0x00FFF3B0)), origin, len))
        }
        val seen: List<Pair<ApiClient.UserSummary?, ApiClient.OutgoingRequest?>> = pals.map { it to null } + outgoing.map { null to it }
        val slots = g.slots
        val size = g.objectSize(slots, max = 96.dp, aspect = 1.2f)
        if (seen.isEmpty()) SceneSlip(if (level < 2) "Noch winkt niemand zurück" else "Noch leuchtet niemand zurück", "Finde Freunde – sie tauchen hier auf.", g.box(slots.first(), 200.dp, 80.dp))
        val pages = seen.chunked(slots.size)
        if (pages.size > 1) g.reserve = 132.dp
        pages.getOrNull(page.coerceAtMost(pages.lastIndex.coerceAtLeast(0)))?.forEachIndexed { i, (f, out) ->
            val m = g.box(slots[i], size, size * 1.2f)
            if (f != null) Lookout(f.avatarEmoji, f.name, profileColor(f.displayColor), levels[f.id] ?: 1, scene.holder, bob, waiting = false,
                m.semantics { set(SceneSlotKey, "friend:${f.id}") }.clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = "${f.name} öffnen") { onFriend(f.id) })
            else if (out != null) Lookout("✉", out.recipientName, Color(0xFF9FB4C8), 0, scene.holder, bob, waiting = true,
                m.semantics { set(SceneSlotKey, "waiting:${out.id}"); contentDescription = "Signal an ${out.recipientName} – noch keine Antwort" })
        }
        ScenePager(page, pages.size, if (level < 2) "In Sichtweite" else "Im Lichtkegel") { page = it }
        // requests to me: bottles washed ashore at the beach (at the gallery's railing in the tower)
        val bottles = incoming.chunked(2)
        val shownBottles = bottles.getOrNull(bottlePage.coerceAtMost(bottles.lastIndex.coerceAtLeast(0))).orEmpty()
        if (shownBottles.isNotEmpty()) Column(
            Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 10.dp, bottom = 136.dp).width(250.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (bottles.size > 1) Row(verticalAlignment = Alignment.CenterVertically) {
                Tag("Flaschenpost ${bottlePage + 1}/${bottles.size}", size = 12)
                Spacer(Modifier.width(6.dp))
                Tag("weiter ›", Modifier.clip(RoundedCornerShape(50)).clickable(onClickLabel = "Nächste Flaschenpost") { bottlePage = (bottlePage + 1) % bottles.size }.heightIn(min = 32.dp), size = 12)
            }
            shownBottles.forEach { r -> Bottle(r.senderName, bob, onYes = { onRespond(r.id, true) }, onNo = { onRespond(r.id, false) }) }
        }
    }
}

/** A friend as the stage sees them: a rowing boat, a sail on the horizon, a light signal, a boat in the beam. */
@Composable
private fun Lookout(emoji: String, name: String, color: Color, stars: Int, holder: StageScenes.Holder, bob: Float, waiting: Boolean, modifier: Modifier) {
    Column(modifier.alpha(if (waiting) .62f else 1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().weight(1f).offset(y = (bob * 2).dp), Alignment.Center) {
            when (holder) {
                StageScenes.Holder.SIGNAL_LIGHT -> {
                    Box(Modifier.fillMaxSize(.9f).background(Brush.radialGradient(listOf(Color(0xCCFFF3B0), Color(0x33FFF3B0), Color.Transparent)), CircleShape))
                    Sprite(R.drawable.boat_row, Modifier.align(Alignment.BottomCenter).fillMaxWidth(.7f))
                }
                StageScenes.Holder.SAILBOAT -> Sprite(R.drawable.boat_sail, Modifier.align(Alignment.BottomCenter).fillMaxSize(.86f))
                else -> Sprite(R.drawable.boat_row, Modifier.align(Alignment.BottomCenter).fillMaxWidth(.95f))
            }
            Box(
                Modifier.align(if (holder == StageScenes.Holder.SAILBOAT) Alignment.TopStart else Alignment.TopCenter).size(38.dp).shadow(3.dp, CircleShape)
                    .background(Brush.radialGradient(listOf(Color.White, color)), CircleShape).border(2.dp, Color(0xFFFFF6E5), CircleShape),
                Alignment.Center,
            ) { Text(emoji, fontSize = 19.sp) }
        }
        Text(if (waiting) "$name?" else name, color = Color.White, fontFamily = Display, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.background(Color(0xAA102040), RoundedCornerShape(50)).padding(horizontal = 7.dp))
        if (stars > 0) Row { repeat(stars.coerceIn(1, 4)) { Sprite(R.drawable.p_star, Modifier.size(10.dp)) } }
    }
}

@Composable
private fun Bottle(name: String, bob: Float, onYes: () -> Unit, onNo: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().offset(y = (bob * 2).dp).shadow(5.dp, RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color(0xF2FFFBF1), Color(0xF2F6E7C8))), RoundedCornerShape(20.dp))
            .border(1.5.dp, Color(0xFFD9BE8A), RoundedCornerShape(20.dp)).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sprite(R.drawable.p_bottle, Modifier.size(40.dp).rotate(-35f + bob * 6))
        Column(Modifier.weight(1f)) {
            Text(name, fontFamily = Display, color = Color(0xFF3B2410), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("möchte dein Freund werden", fontFamily = Body, color = Color(0xFF8A6A3A), fontSize = 11.sp, maxLines = 1)
        }
        Text("Nein", fontFamily = Body, fontWeight = FontWeight.Bold, color = Color(0xFF8A6A3A), modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onNo).heightIn(min = 40.dp).padding(horizontal = 6.dp, vertical = 10.dp))
        Text(
            "Annehmen", fontFamily = Display, color = Color.White, fontSize = 13.sp,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF3F7C78)).clickable(onClick = onYes).heightIn(min = 40.dp).padding(horizontal = 10.dp, vertical = 10.dp),
        )
    }
}

// ---------------------------------------------------------------- Bibliothek

/**
 * The shared glossary, 7.9.12: the book lies IN the stage: opened on the top driftwood crate on the beach, on the
 * reading place under the roof of twigs, on the reading desk between the shelf boards of the book room, leather-bound
 * on the library's big table. Its pages are the glossary (read, search, categories, add).
 */
@Composable
internal fun LibraryBook(onClose: () -> Unit, glossary: @Composable () -> Unit, level: Int = 3) {
    val scene = StageScenes.of(IsleBuilding.LIBRARY, level)
    PlaceStage(IsleBuilding.LIBRARY, onClose, level) { g ->
        val r = scene.book!!
        val pages = Brush.horizontalGradient(listOf(Color(0xFFE6D6B4), Color(0xFFFFF9EC), Color(0xFFFFF9EC), Color(0xFFF3E6CA)))
        val box = Modifier.offset(x = g.screen.x(r.left), y = g.screen.y(r.top)).size(g.screen.w * (r.right - r.left), g.screen.h * (r.bottom - r.top))
            .semantics { set(SceneSlotKey, "book:${scene.holder}") }
        Box(box) {
            // the book's page edges (thickness) under the open pages, so it lies there as a book
            Box(Modifier.align(Alignment.BottomCenter).offset(y = 7.dp).fillMaxWidth(.98f).height(14.dp).shadow(6.dp, RoundedCornerShape(3.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFF3E6CA), Color(0xFFD9C29A), Color(0xFFEFE0BF), Color(0xFFC9AE80))), RoundedCornerShape(3.dp)))
            when (scene.holder) {
                // stage 0: the parchment lashed onto a driftwood board lying on the top crate
                StageScenes.Holder.PARCHMENT -> Box(Modifier.fillMaxSize().shadow(10.dp, RoundedCornerShape(6.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFA88B66), Color(0xFF8A6E4E), Color(0xFF9C7F5C))), RoundedCornerShape(6.dp))
                    .border(1.5.dp, Color(0xFF5E4630), RoundedCornerShape(6.dp)).padding(horizontal = 9.dp, vertical = 11.dp)
                    .shadow(3.dp, RoundedCornerShape(3.dp)).background(pages, RoundedCornerShape(3.dp))
                    .border(1.dp, Color(0xFFB08A55), RoundedCornerShape(3.dp))) { GlossaryPage(glossary) }
                // stage 2: the open book on the dark wooden reading desk of the book room
                StageScenes.Holder.SHELF_BOOK -> Box(Modifier.fillMaxSize().shadow(12.dp, RoundedCornerShape(10.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF6B4527), Color(0xFF4A2F18))), RoundedCornerShape(10.dp))
                    .border(2.dp, Color(0xFF2E1C0E), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(5.dp)).background(pages)) { GlossaryPage(glossary) }
                StageScenes.Holder.OPEN_BOOK -> Box(
                    Modifier.fillMaxSize().shadow(8.dp, RoundedCornerShape(8.dp)).background(pages, RoundedCornerShape(8.dp))
                        .border(1.5.dp, Color(0xFF8A5A2B), RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)),
                ) { GlossaryPage(glossary) }
                else -> LeatherBook(pages, glossary)
            }
            // the spine in the middle of the open book
            Box(Modifier.align(Alignment.TopCenter).width(2.dp).fillMaxHeight(.04f).background(Color(0x553B2410)))
            if (scene.holder != StageScenes.Holder.PARCHMENT) Sprite(R.drawable.p_quill, Modifier.align(Alignment.BottomStart).offset(x = (-6).dp, y = 4.dp).size(54.dp))
        }
    }
}

@Composable
private fun LeatherBook(pages: Brush, glossary: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().shadow(14.dp, RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF4F5D63), Color(0xFF34414A))), RoundedCornerShape(18.dp))
            .border(2.dp, Color(0xFFC9A55C), RoundedCornerShape(18.dp)).padding(7.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(pages),
    ) { GlossaryPage(glossary) }
}

/** The glossary in the library's ink and paper colours (the same on every stage). */
@Composable
private fun GlossaryPage(glossary: @Composable () -> Unit) {
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

// ---------------------------------------------------------------- Gemeindehaus

/**
 * Groups, 7.9.12: at the fire ring and in the meeting hut ONE group sits around THE fire / the crate table (the
 * members on stools in a ring around it, the group's sign before it; tapping the fire / table = a quest for the
 * group); other groups by turning the page. In the community hall the groups sit in their seating groups, in the
 * festival hall at their laid tables, several per page.
 */
@Composable
internal fun CommunityHall(
    groups: List<ApiClient.Group>,
    quests: List<ApiClient.Quest>,
    onGroupQuest: (Long) -> Unit,
    onManage: () -> Unit,
    onClose: () -> Unit,
    level: Int = 3,
) {
    val scene = StageScenes.of(IsleBuilding.CAMPFIRE, level)
    var page by rememberSaveable { mutableStateOf(0) }
    PlaceStage(IsleBuilding.CAMPFIRE, onClose, level, action = "Gruppen verwalten" to onManage) { g ->
        fun open(gr: ApiClient.Group) = quests.count { it.targetType == "group" && it.targetId == gr.id && it.completedAt == null }
        if (scene.holder == StageScenes.Holder.ROUND_TABLE) Canvas(Modifier.fillMaxSize()) {
            // the festival hall: bunting garlands across the hall
            val colours = listOf(Color(0xFFC94F3D), Color(0xFFE2A93C), Color(0xFF3F7C78), Color(0xFFF1E3C4))
            for ((row, y0) in listOf(.30f, .37f).withIndex()) {
                val y = size.height * y0; val sag = size.height * .035f
                val n = 14
                for (i in 0 until n) {
                    val x0 = size.width * i / n; val x1 = size.width * (i + 1) / n
                    fun yy(x: Float) = y + sag * (1 - ((x / size.width) * 2 - 1).let { it * it })
                    drawLine(Color(0xFF5A3A12), Offset(x0, yy(x0)), Offset(x1, yy(x1)), 1.5f * density)
                    val p = Path().apply { moveTo(x0 + 3, yy(x0)); lineTo(x1 - 3, yy(x1)); lineTo((x0 + x1) / 2, yy((x0 + x1) / 2) + size.height * .03f); close() }
                    drawPath(p, colours[(i + row) % colours.size])
                }
            }
        }
        val around = scene.centre != null
        if (groups.isEmpty()) {
            val at = g.centre ?: g.slots.first()
            SceneSlip(if (level == 0) "Noch niemand am Feuer" else "Noch keine Tische", "Gründe eine Gruppe – sie bekommt hier ihren eigenen Platz.", g.box(at, 220.dp, 90.dp))
        } else if (around) {
            val pages = groups.size
            if (pages > 1) g.reserve = 132.dp
            val gr = groups[page.coerceIn(0, pages - 1)]
            val c = g.centre!!
            val sp = g.space
            val rx = sp.w * scene.ring.first; val ry = sp.h * scene.ring.second
            val members = gr.members.take(16)
            val seat: Dp = (if (members.size <= 8) 52.dp else 52.dp * (8f / members.size).coerceAtLeast(.66f))
            // the group's sign stuck in the ground before the circle
            Box(Modifier.offset(x = c.x - 130.dp, y = c.y - ry - seat - 40.dp).width(260.dp), Alignment.Center) {
                Tag("${gr.name} · ${gr.members.size} " + if (scene.holder == StageScenes.Holder.FIRE_RING) "am Feuer" else "am Tisch", size = 15)
            }
            members.forEachIndexed { i, m ->
                val a = (i.toFloat() / members.size) * 2f * PI.toFloat() - PI.toFloat() / 2
                val p = androidx.compose.ui.unit.DpOffset(c.x + rx * cos(a), c.y + ry * sin(a))
                Box(g.box(p, seat).semantics { set(SceneSlotKey, "member:${gr.id}:${m.id}"); contentDescription = "${m.name} sitzt bei ${gr.name}" }, Alignment.Center) {
                    Sprite(if (scene.holder == StageScenes.Holder.FIRE_RING && i % 2 == 0) R.drawable.n_rocks else R.drawable.p_stool, Modifier.fillMaxSize().offset(y = seat * .3f))
                    Box(Modifier.fillMaxSize(.78f).offset(y = -seat * .08f).shadow(4.dp, CircleShape).background(Color.White, CircleShape).padding(2.dp)
                        .background(Brush.radialGradient(listOf(Color.White, profileColor(m.displayColor).copy(alpha = .55f))), CircleShape), Alignment.Center) {
                        Text(m.avatarEmoji, fontSize = 18.sp)
                    }
                }
            }
            if (gr.members.size > members.size) Tag("+${gr.members.size - members.size} weitere", Modifier.offset(x = c.x + rx - 30.dp, y = c.y + ry + seat / 2), size = 12)
            // the fire / the crate table itself: a quest for the group
            Box(g.box(c, 96.dp, 80.dp).worldTap("Quest für ${gr.name}") { onGroupQuest(gr.id) }.semantics { set(SceneSlotKey, "group:${gr.id}") }, Alignment.BottomCenter) {
                val n = open(gr)
                if (n > 0) Row(Modifier.shadow(2.dp, RoundedCornerShape(50)).background(Color(0xF2FFF6E5), RoundedCornerShape(50)).padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Sprite(R.drawable.p_star, Modifier.size(13.dp)); Text(" $n", fontFamily = Display, fontSize = 12.sp, color = Color(0xFF9A6B00))
                }
            }
            ScenePager(page, pages, "Gruppe") { page = it }
        } else {
            val slots = g.slots
            val size = g.objectSize(slots, max = 176.dp, min = 132.dp, aspect = 1.12f)
            val pages = groups.chunked(slots.size)
            if (pages.size > 1) g.reserve = 132.dp
            pages.getOrNull(page.coerceAtMost(pages.lastIndex))?.forEachIndexed { i, gr ->
                Box(g.box(slots[i], size, size * 1.12f).semantics { set(SceneSlotKey, "group:${gr.id}") }) { SeatGroup(gr, open(gr), scene.holder) { onGroupQuest(gr.id) } }
            }
            ScenePager(page, pages.size, if (scene.holder == StageScenes.Holder.BENCH_GROUP) "Sitzgruppen" else "Festtische") { page = it }
        }
    }
}

/** A group in the hall: benches around a low table (community hall) or stools around a laid table (festival hall). */
@Composable
private fun SeatGroup(g: ApiClient.Group, openQuests: Int, holder: StageScenes.Holder, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = "Quest für ${g.name}", onClick = onClick)) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val w = maxWidth; val hgt = maxHeight
            val members = g.members.take(12)
            val seat: Dp = if (members.size <= 8) w * .24f else w * .24f * (8f / members.size).coerceAtLeast(.62f)
            val placed = members.mapIndexed { i, m ->
                val a = (i.toFloat() / members.size) * 2f * PI.toFloat() - PI.toFloat() / 2
                Triple(m, w / 2 + w * .37f * cos(a) - seat / 2, hgt / 2 + hgt * .33f * sin(a) - seat / 2)
            }
            if (holder == StageScenes.Holder.BENCH_GROUP) {
                Sprite(R.drawable.decor_bench, Modifier.align(Alignment.TopCenter).fillMaxWidth(.7f))
                Sprite(R.drawable.decor_bench, Modifier.align(Alignment.BottomCenter).fillMaxWidth(.7f))
            } else placed.forEach { (_, x, y) -> Sprite(R.drawable.p_stool, Modifier.offset(x = x, y = y + seat * .25f).size(seat)) }
            Sprite(R.drawable.p_table, Modifier.align(Alignment.Center).size(w * (if (holder == StageScenes.Holder.BENCH_GROUP) .42f else .56f)))
            if (holder == StageScenes.Holder.ROUND_TABLE) Sprite(R.drawable.decor_flag, Modifier.align(Alignment.Center).offset(y = -w * .16f).size(w * .2f))
            if (openQuests > 0) Row(
                Modifier.align(Alignment.Center).shadow(2.dp, RoundedCornerShape(50)).background(Color(0xF2FFF6E5), RoundedCornerShape(50)).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { Sprite(R.drawable.p_star, Modifier.size(13.dp)); Text(" $openQuests", fontFamily = Display, fontSize = 12.sp, color = Color(0xFF9A6B00)) }
            placed.forEach { (m, x, y) ->
                Box(
                    Modifier.offset(x = x + seat * .1f, y = y - seat * .05f).size(seat * .8f)
                        .shadow(4.dp, CircleShape).background(Color.White, CircleShape).padding(2.dp)
                        .background(Brush.radialGradient(listOf(Color.White, profileColor(m.displayColor).copy(alpha = .55f))), CircleShape)
                        .semantics { contentDescription = m.name },
                    Alignment.Center,
                ) { Text(m.avatarEmoji, fontSize = 15.sp) }
            }
            if (g.members.size > members.size) Text("+${g.members.size - members.size}", fontFamily = Display, fontSize = 11.sp, color = Color(0xFFFFF6E5),
                modifier = Modifier.align(Alignment.BottomEnd).background(Color(0xFF4A2414), RoundedCornerShape(50)).padding(horizontal = 6.dp))
        }
        Tag("${g.name} · ${g.members.size}", size = 13)
    }
}

// ---------------------------------------------------------------- Mein Haus

/**
 * My place, 7.9.12: me and my keepsakes where I live at this stage: lying in the hammock with the treasures in the
 * sand below, inside the log hut with them in the open chest and on the table, my picture beside the cottage door
 * with the treasures on the steps, my portrait in the country house's living room with the trophies on the shelf.
 */
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
    level: Int = 3,
) {
    val scene = StageScenes.of(IsleBuilding.HOUSE, level)
    val items = listOf(
        Triple(R.drawable.p_medal, "$friends", "Freunde"),
        Triple(R.drawable.p_trophy, "$questsDone", if (questsDone == 1) "Quest" else "Quests"),
        Triple(R.drawable.p_env, "$letters", "Briefe"),
        Triple(R.drawable.p_star, "$qp", "Punkte"),
    )
    PlaceStage(IsleBuilding.HOUSE, onClose, level, action = "Profil & Einstellungen" to onSettings) { g ->
        val me = g.centre!!
        // me: in the hammock (no frame), a picture on the hut wall / beside the door / on the living-room wall
        val framed = scene.holder != StageScenes.Holder.KEEPSAKES
        val pic = if (framed) 92.dp else 64.dp
        Box(g.box(me, pic, if (framed) pic * 1.2f else pic).semantics { set(SceneSlotKey, "me"); contentDescription = "Ich: $name" }, Alignment.Center) {
            Box(Modifier.fillMaxSize(if (framed) .72f else 1f).shadow(if (framed) 0.dp else 4.dp, CircleShape)
                .background(Brush.radialGradient(listOf(Color.White, profileColor(color).copy(alpha = .6f))), if (framed) RoundedCornerShape(2.dp) else CircleShape), Alignment.Center) {
                Text(emoji, fontSize = if (framed) 38.sp else 30.sp)
            }
            if (framed) Sprite(R.drawable.p_frame, Modifier.fillMaxSize())
        }
        Box(Modifier.offset(x = me.x - 80.dp, y = me.y + (if (framed) pic * .6f else pic / 2) + 2.dp).width(160.dp), Alignment.TopCenter) { Tag(name, size = 15) }
        val slots = g.slots
        items.forEachIndexed { i, (res, v, l) ->
            Column(g.box(slots[i], 70.dp, 92.dp).semantics(mergeDescendants = true) { set(SceneSlotKey, "keepsake:$l") }, horizontalAlignment = Alignment.CenterHorizontally) {
                Sprite(res, Modifier.size(44.dp).rotate(if (scene.holder == StageScenes.Holder.KEEPSAKES) (i - 1.5f) * 9f else 0f))
                Text(v, fontFamily = Display, fontSize = 17.sp, color = Color(0xFF3B2410),
                    modifier = Modifier.background(Color(0xCCFFF6E5), RoundedCornerShape(50)).padding(horizontal = 6.dp))
                Text(l, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF3B2410), maxLines = 1,
                    modifier = Modifier.background(Color(0xCCFFF6E5), RoundedCornerShape(50)).padding(horizontal = 6.dp))
            }
        }
        GoldButtonSmall("Insel bearbeiten", Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 104.dp, end = 12.dp).width(150.dp), onEdit)
    }
}

@Composable
private fun GoldButtonSmall(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.shadow(6.dp, RoundedCornerShape(24.dp)).background(Color(0xFFFFFBF1), RoundedCornerShape(24.dp))
            .border(2.dp, Color(0xFF3F7C78), RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick).heightIn(min = 44.dp).padding(vertical = 10.dp),
        Alignment.Center,
    ) { Text(label, fontFamily = Display, fontSize = 15.sp, color = Color(0xFF2F5D5A)) }
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
