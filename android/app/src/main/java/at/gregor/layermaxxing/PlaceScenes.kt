package at.gregor.layermaxxing


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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Every building on the home island opens its OWN place. 7.9.14 (style E): the place is the stage's painted picture
 * ([StageScenes], the generated detail scene); the real data is dynamic UI on the surfaces painted for it:
 *  - Post: letters nailed to the trunk / standing in the drawer / on the desk / in the counter tray, the brass slider
 *    with the four views, the person filter on the stamps / cubbies, the search when there are many.
 *  - Freunde: friends out on the sea of the view, requests as bottle post, the spyglass searches people.
 *  - Wörterbuch: the book that lies there, opened; its ribbons / card drawers / banners are the categories.
 *  - Gruppen: the focus group around the fire / table, other groups on the picker; empty, one and many groups.
 *  - Hafen: the quest notes on the notice board of the landing; topics and arrived letters beside it.
 *  - Zuhause: my picture on the disc / in the frame, name, notes and counters where the picture keeps them.
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
internal fun PaintedScene(bg: Int, imgAspect: Float, content: @Composable BoxScope.(SceneMap) -> Unit) {
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
 * Shell of every function stage: the stage's painted place ([FunctionStage]) fills the screen, the shared head (round
 * back button, wooden name sign, the stage's island piece, the stage strip) and the golden action stay. [content] puts
 * the real data onto the place's surfaces via the [StageGeometry].
 */
@Composable
internal fun PlaceStage(
    place: IsleBuilding,
    onClose: () -> Unit,
    /** 7.9.9: the building's current stage (0–3): title, head picture, icon and line follow [IslandStages]. */
    level: Int = 3,
    action: Pair<String, () -> Unit>? = null,
    /** 7.9.13: the stage story's things with their real handlers (StageStory.bind), beside the golden action. */
    story: List<Pair<StageStory.Item, StoryHook>> = emptyList(),
    /** 1011: the object of the picture the view looks at closely (null = the whole place). */
    camera: StageScenes.Region? = null,
    content: @Composable BoxScope.(StageGeometry) -> Unit,
) {
    val st = place.style()
    val chapter = StageStory.of(place, level)
    val scene = StageScenes.of(place, level)
    Box(Modifier.fillMaxSize().background(st.bottom).semantics { stateDescription = "Geschichte: ${chapter.core}" }) {
        FunctionStage(scene, camera) { g -> content(g) }
        // Soft top vignette so the sign always reads (the pictures keep a calm header zone).
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
                    SignTitle(stage.name, Modifier.weight(1f, fill = false))
                }
            }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(58.dp).shadow(4.dp, CircleShape).background(Brush.radialGradient(listOf(Color(0xFFFFF6E5), Color(0xFFE9D3A6))), CircleShape)
                .border(2.dp, Color(0xFF8A5A2B), CircleShape).padding(5.dp), Alignment.BottomCenter) {
                PaintedIslandPlace(place, level, stage.art, Modifier.fillMaxSize(), description = "Kopfbild: ${stage.name}")
            }
        }
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 70.dp, start = 68.dp, end = 12.dp)) {
            val stage = IslandStages.of(place, level)
            Row(Modifier.shadow(2.dp, RoundedCornerShape(50)).background(IslandWood.Paper, RoundedCornerShape(50))
                .border(1.dp, IslandWood.PaperEdge.copy(alpha = .7f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically) {
                StagePips(level); Spacer(Modifier.width(6.dp))
                // the stage line: function and stage, whole (the look is shown in the upgrade view)
                Text(stage.subtitle, fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp, color = IslandWood.Ink, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { contentDescription = "Stufe: ${stage.subtitle}" })
            }
        }
        if (story.isEmpty()) action?.let { (label, onClick) -> GoldButton(label, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 18.dp), onClick) }
        else Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(start = 8.dp, end = 8.dp, bottom = 16.dp)
                .semantics { contentDescription = "Gegenstände: ${chapter.core}" },
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            // left thing · golden action (it may wrap, never covered) · right thing; a missing side keeps its room
            story.getOrNull(0)?.let { (item, hook) -> StoryThing(item, hook) } ?: Spacer(Modifier.size(56.dp))
            action?.let { (label, onClick) -> GoldButton(label, Modifier.weight(1f, fill = false).padding(horizontal = 6.dp), onClick, compact = true) }
            story.getOrNull(1)?.let { (item, hook) -> StoryThing(item, hook) } ?: Spacer(Modifier.size(56.dp))
        }
    }
}

/**
 * 7.9.14: the place's name on the wooden sign, whole at every font size: the largest size (21 sp down to 11 sp, measured
 * with the font scale) at which it fits on one line, else two lines (never cut, never "…": "Versammlungshaus" at
 * 360 dp with a 1.5× font).
 */
@Composable
private fun SignTitle(name: String, modifier: Modifier = Modifier) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val base = TextStyle(fontFamily = Display, color = Color(0xFFFFF6E5), shadow = androidx.compose.ui.graphics.Shadow(Color(0xAA3B2410), Offset(0f, 3f), 2f))
    BoxWithConstraints(modifier.semantics { contentDescription = "Ort: $name" }, Alignment.CenterStart) {
        val px = constraints.maxWidth
        val (sp, lines) = remember(name, px) {
            val one = generateSequence(21f) { it - 1f }.takeWhile { it >= 11f }
                .firstOrNull { measurer.measure(name, base.copy(fontSize = it.sp), maxLines = 1, softWrap = false).size.width <= px }
            if (one != null) one to 1 else 11f to 2
        }
        Text(name, style = base.copy(fontSize = sp.sp, lineHeight = (sp * 1.05f).sp), maxLines = lines, softWrap = lines > 1,
            modifier = Modifier.testTag("place-title"))
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
internal fun GoldButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit, compact: Boolean = false) {
    Box(
        modifier.shadow(10.dp, RoundedCornerShape(30.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFE2CB93), Color(0xFFC9A55C), Color(0xFFA9843F))), RoundedCornerShape(30.dp))
            .border(2.dp, Color(0xFF7E6236), RoundedCornerShape(30.dp))
            .clip(RoundedCornerShape(30.dp)).clickable(onClick = onClick)
            .padding(horizontal = if (compact) 14.dp else 26.dp, vertical = 13.dp)
            .semantics { role = Role.Button },
    ) {
        Text(
            label, color = Color(0xFF5A3200), fontFamily = Display, fontSize = if (compact) 16.sp else 18.sp, textAlign = TextAlign.Center,
            style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0x88FFFFFF), Offset(0f, 2f), 0f)),
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

/** The anchor of every data object, for tests and accessibility: "<kind>:<id>" at its surface. */
internal val SceneSlotKey = androidx.compose.ui.semantics.SemanticsPropertyKey<String>("SceneSlot")

/** A box exactly on a rectangle of the screen (dp). */
private fun DpRect.box(): Modifier = Modifier.offset(x = left, y = top).size(w, h)

/**
 * Scenic pages when there is more data than places in the picture: two wooden arrows and a plaque right above the
 * golden action. Nothing is filtered away; every object is reachable.
 */
@Composable
private fun BoxScope.ScenePager(page: Int, pages: Int, what: String, onPage: (Int) -> Unit) {
    if (pages <= 1) return
    Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp), verticalAlignment = Alignment.CenterVertically) {
        PagerArrow("‹", "Vorige Seite: $what", page > 0) { onPage(page - 1) }
        Box(Modifier.padding(horizontal = 6.dp).heightIn(min = 34.dp).widthIn(min = 132.dp), Alignment.Center) {
            // a plain wooden plate (the painted plate's rivets covered the text at a large font)
            Box(Modifier.matchParentSize().shadow(3.dp, RoundedCornerShape(8.dp)).background(Brush.verticalGradient(listOf(Color(0xFFE9D3A6), Color(0xFFCDAE78))), RoundedCornerShape(8.dp))
                .border(1.5.dp, Color(0xFF7A4B26), RoundedCornerShape(8.dp)))
            Text("$what ${page + 1}/$pages", fontFamily = Display, fontSize = 13.sp, color = Color(0xFF4A2A08), maxLines = 1, softWrap = false,
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

/**
 * Surfaces stacked in their picture order: every child gets its rectangle; a child that would overlap the one before
 * (a large font, a short phone) is pushed down; a fill child takes the room to its surface's bottom (at least
 * [minFill]). Never above the header.
 */
@Composable
private fun SurfaceStack(g: StageGeometry, parts: List<Pair<DpRect, Boolean>>, minFill: Dp = 150.dp, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content, Modifier.fillMaxSize()) { measurables, constraints ->
        val limit = (g.screen.h - g.reserve).roundToPx()
        val header = g.header.roundToPx()
        val gap = 4.dp.roundToPx()
        val least = 104.dp.roundToPx()
        // the fixed parts have their own height; the fill part (letters, notes) takes the rest
        val fixed = measurables.mapIndexed { i, m ->
            if (parts[i].second) null else m.measure(androidx.compose.ui.unit.Constraints(minWidth = parts[i].first.w.roundToPx().coerceAtLeast(1), maxWidth = parts[i].first.w.roundToPx().coerceAtLeast(1)))
        }
        val n = measurables.size
        val tops = IntArray(n); val heights = IntArray(n)
        // 1. on the painted surfaces (each at its surface's top, below the one before)
        var y = header
        for (i in 0 until n) {
            val (r, fill) = parts[i]
            tops[i] = maxOf(r.top.roundToPx(), y)
            heights[i] = if (fill) maxOf(r.bottom.roundToPx(), tops[i] + minFill.roundToPx()) - tops[i] else fixed[i]!!.height
            y = tops[i] + heights[i] + gap
        }
        // 2. a short phone / large font (the parts do not fit on their surfaces above the bottom reserve): packed closely
        // from the header down in the picture's order, the fill part (letters, notes) takes all the rest – nothing lies
        // under the golden action and the letters keep as much room as there is
        if (y - gap > limit) {
            val fixedSum = (0 until n).filter { !parts[it].second }.sumOf { heights[it] + gap }
            var cursor = header
            for (i in 0 until n) {
                tops[i] = cursor
                if (parts[i].second) heights[i] = maxOf(limit - header - fixedSum - gap, least)
                cursor = tops[i] + heights[i] + gap
            }
        }
        val placed = measurables.mapIndexed { i, m ->
            val w = parts[i].first.w.roundToPx().coerceAtLeast(1)
            (fixed[i] ?: m.measure(androidx.compose.ui.unit.Constraints.fixed(w, heights[i]))) to i
        }
        layout(constraints.maxWidth, constraints.maxHeight) { placed.forEach { (p, i) -> p.place(parts[i].first.left.roundToPx(), tops[i]) } }
    }
}

// ---------------------------------------------------------------- Post

/**
 * 1011 clean pilot (user: "die ganzen funktionen drauf verdecken … das soll eben so clean wow effekt sein"): the post
 * opens as the painting alone. What it holds is told by the place itself, engraved on its plate ("6 Briefe · 2
 * ungelesen"), and its objects are what one touches:
 *  - the letters' place (the trunk, the drawer, the desk, the tray) → the view moves in to it and the letters stand there;
 *  - the stamp sheet / the cubbies / the name tags under the brass belt → the stamps of every person (the filter);
 *  - the brass slider → its four windows: Alle / Ungelesen / Gelesen / Gesendet, each with its real count.
 * Nothing is lost (every letter, both filters combined and kept, the search, travelling and sealed letters with their
 * state, opening, the seal, writing); it is only shown where and when its object is touched. Back = one step out.
 */
internal enum class PostFocus { REST, LETTERS, PEOPLE, VIEW }

/**
 * Letters, 7.9.14 / 1011: the post keeps ALL letters in the stage's own place and works them with what the place has:
 * the brass slider shows the four views (real counts), the stamps / cubbies / name tags filter by person (kept when the
 * view changes), a search appears when there are many. Reading state and availability are separate ([PostViews]).
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
    /** Tests / review renders: the object looked at when the place opens. */
    startFocus: PostFocus = PostFocus.REST,
    openRequests: Map<Long, OpenRequest> = emptyMap(),
    writer: LetterWriter? = null,
    /** Briefbaum 22.10.: the app's "unread again" marker (the letter tree offers it on a read letter); null = not offered. */
    unread: UnreadMarker? = null,
) {
    LetterTreePlace(pals, letters, opened, onOpen, onLocked, onWrite, onClose,
        startFocus = when (startFocus) {
            PostFocus.PEOPLE -> TreeFocus.ROPE
            PostFocus.VIEW -> TreeFocus.BELT
            else -> TreeFocus.REST
        }, requests = openRequests, writer = writer, unread = unread, level = level.coerceIn(0, 3))
}

/**
 * The post at rest: only the painting. Its objects are the controls (invisible, labelled for TalkBack, at least
 * 48 dp): the letters' place, the person surface, the brass slider. One engraved line on the place's own plate (the
 * name plate of the drawer, the stamp strip, the glass case; on the tree the brass belt) says what lies there; a warm
 * lamplight breathes on the letters' place while unread letters wait (still when animations are off).
 */
@Composable
private fun BoxScope.PostRest(
    g: StageGeometry, scene: StageScenes.Scene, big: String, small: String, summary: String, place: String, total: Int, unread: Int, people: Int,
    view: PostViews.View, counts: Map<PostViews.View, Int>, onFocus: (PostFocus) -> Unit,
) {
    fun clip(r: DpRect): DpRect {
        val l = r.left.coerceAtLeast(0.dp); val t = r.top.coerceAtLeast(g.header); val rr = r.right.coerceAtMost(g.screen.w)
        val b = r.bottom.coerceAtMost(g.screen.h - g.reserve)
        return DpRect(l, t, (rr - l).coerceAtLeast(0.dp), (b - t).coerceAtLeast(0.dp))
    }
    val letters = clip(g.rect(StageScenes.Surface.LETTERS)!!)
    if (unread > 0) {
        val tr = rememberInfiniteTransition(label = "lamp")
        val context = androidx.compose.ui.platform.LocalContext.current
        val still = remember(context) { android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
        val a by tr.animateFloat(.10f, .24f, infiniteRepeatable(tween(2400), RepeatMode.Reverse), label = "glow")
        Box(letters.box().background(Brush.radialGradient(listOf(Color(0xFFFFD58A).copy(alpha = if (still) .16f else a), Color.Transparent))))
    }
    @Composable
    fun hotspot(r: DpRect, focus: PostFocus, label: String) {
        val box = DpRect(r.left, r.top, maxOf(r.w, 48.dp), maxOf(r.h, 48.dp))
        Box(box.box().clickable(onClickLabel = label) { onFocus(focus) }
            .semantics { contentDescription = label; role = Role.Button }.testTag("post-hotspot-${focus.name}"))
    }
    hotspot(letters, PostFocus.LETTERS, "Briefe $place ansehen: $total, davon $unread ungelesen")
    hotspot(clip(g.rect(StageScenes.Surface.PEOPLE)!!), PostFocus.PEOPLE, "Nach Person filtern: $people Personen")
    hotspot(clip(g.rect(StageScenes.Surface.TABS)!!), PostFocus.VIEW, "Ansicht wählen: jetzt ${view.label}, ${counts[view] ?: 0} Briefe")
    // the engraved line on the place's own plate (on the tree: the brass belt), a short way in to the letters
    val plate = clip((g.rect(StageScenes.Surface.PLATE) ?: g.rect(StageScenes.Surface.TABS)!!))
    // the plate of the drawer / strip / case leads to the letters; the tree's brass belt is its slider (the views)
    val toward = if (scene.surface(StageScenes.Surface.PLATE) != null) PostFocus.LETTERS else PostFocus.VIEW
    // centred on the plate, as high as the two engraved lines need (a large font may reach over the plate's rim)
    val engLeft = (plate.left - 24.dp).coerceAtLeast(8.dp); val engRight = (plate.right + 24.dp).coerceAtMost(g.screen.w - 8.dp)
    // centred on the plate's middle (the touch area keeps 48 dp)
    val engH = maxOf(plate.h, 48.dp)
    Box(Modifier.offset(x = engLeft, y = plate.top + plate.h / 2 - engH / 2).width(engRight - engLeft).heightIn(min = engH)
        .clickable(onClickLabel = if (toward == PostFocus.LETTERS) "Briefe $place ansehen" else "Ansicht wählen") { onFocus(toward) }
        .semantics(mergeDescendants = true) { contentDescription = "Auf dem Schild: $summary"; role = Role.Button; set(SceneSlotKey, "post-engraving") }
        .testTag("post-engraving"), Alignment.Center) {
        // letterpress: dark ink pressed into the plate, a light lower edge
        val press = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0xB3FFF1CF), Offset(0f, 1.5f), 0f))
        // a large font on a small phone: the painted card is too small for the letters, so a larger card of the same paper
        // sits in the holder (brass edge), never ink on the bare wood
        val fs = LocalDensity.current.fontScale
        val card = fs > 1.15f || plate.w < 100.dp
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = if (!card) Modifier else Modifier
            .shadow(2.dp, RoundedCornerShape(3.dp)).background(Color(0xFFEADCBE), RoundedCornerShape(3.dp))
            .border(2.dp, Color(0xFFA9843F), RoundedCornerShape(3.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
            Text(big, fontFamily = Display, fontSize = 15.sp, lineHeight = 15.sp, color = Color(0xFF2A1A08), maxLines = 1, softWrap = false, style = press)
            Text(small, fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, lineHeight = 11.sp, color = Color(0xFF4A2E12), maxLines = 1,
                softWrap = false, style = press)
        }
    }
}

/**
 * Close up at one object (the camera has moved in): a slim brass rail on its upper edge – the way back and, at the
 * letters, the two filters as engraved plates – and the object's content on the object itself.
 */
@Composable
private fun PostClose(
    g: StageGeometry, at: DpRect, title: String, onBack: () -> Unit, rail: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
    fill: Boolean = true, minBody: Dp = 168.dp, plates: Boolean = false, content: @Composable () -> Unit,
) {
    val r = g.onScreen(at, minW = 300.dp)
    // with a large font the rail keeps its plates and the way back; the title is then only read out (heading)
    val showTitle = !plates || LocalDensity.current.fontScale < 1.3f
    androidx.compose.ui.layout.Layout({
        Row(Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFD9B66A), Color(0xFFB08A3E), Color(0xFF8C6A2C))), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF5E4518), RoundedCornerShape(10.dp)).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = "Zurück zum ganzen Ort", onClick = onBack)
                .semantics { contentDescription = "Zurück zum ganzen Ort"; role = Role.Button }.testTag("post-back"), Alignment.Center) {
                AppIcon(R.drawable.ico_back, null, tint = Color(0xFF2E1E0C), size = 22.dp)
            }
            if (showTitle) Text(title, fontFamily = Display, fontSize = 14.sp, color = Color(0xFF2E1E0C), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() })
            else Spacer(Modifier.weight(1f).semantics { heading(); contentDescription = title })
            rail()
        }
        Box(Modifier.fillMaxWidth().let { if (fill) it.fillMaxSize() else it }) { content() }
    }, Modifier.fillMaxSize()) { ms, cs ->
        // the rail on the object's upper edge; the content on the object down to its lower edge (at least [least]);
        // only when that runs into the bottom reserve the two move up together, never above the header
        val w = r.w.roundToPx(); val x = r.left.roundToPx()
        val limit = (g.screen.h - g.reserve).roundToPx(); val header = g.header.roundToPx()
        val least = (if (fill) minBody else 0.dp).roundToPx()
        val rail = ms[0].measure(androidx.compose.ui.unit.Constraints(minWidth = w, maxWidth = w))
        val gap = 4.dp.roundToPx()
        val fixedBody = if (fill) null else ms[1].measure(androidx.compose.ui.unit.Constraints(minWidth = w, maxWidth = w))
        // what the content wants: its own height, or (letters, stamps) the object's height below the rail, at least [least]
        val want = fixedBody?.height ?: maxOf(least, at.bottom.roundToPx() - (maxOf(r.top.roundToPx(), header) + rail.height + gap))
        var top = maxOf(r.top.roundToPx(), header)
        val room = limit - header - rail.height - gap
        if (top + rail.height + gap + want > limit) top = maxOf(header, limit - rail.height - gap - minOf(want, room))
        val bodyTop = top + rail.height + gap
        val body = fixedBody ?: ms[1].measure(androidx.compose.ui.unit.Constraints.fixed(w, minOf(want, limit - bodyTop).coerceAtLeast(0)))
        layout(cs.maxWidth, cs.maxHeight) { rail.place(x, top); body.place(x, bodyTop) }
    }
}

/** An engraved plate on the rail: one filter, tap = change it there (48 dp). */
@Composable
private fun RailPlate(text: String, label: String, tag: String, onClick: () -> Unit) {
    Text(text, fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Color(0xFF2E1E0C), maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 132.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFFFF1CF))
            .border(1.dp, Color(0xFF6B5020), RoundedCornerShape(6.dp)).clickable(onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label; role = Role.Button }.testTag(tag).padding(horizontal = 8.dp, vertical = 14.dp))
}

/**
 * The stamps of the place (the sheet in the mailbox lid; the same perforated stamps in the cubbies of the letter room
 * and the post office, the nailed tags of the tree): "Alle" and every person with the count in the chosen view. A
 * stamp is at least 48 dp; many scroll.
 */
@Composable
private fun StampSheet(holder: StageScenes.Holder, people: List<PostViews.Person>, counts: Map<Long, Int>, chosen: Long?, onChoose: (Long?) -> Unit) {
    val cells: List<PostViews.Person?> = listOf<PostViews.Person?>(null) + people
    LazyVerticalGrid(GridCells.Adaptive(96.dp), Modifier.fillMaxSize().testTag("post-people"),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(4.dp)) {
        items(cells.size) { i ->
            val p = cells[i]
            val on = p?.id == chosen
            val n = if (p == null) counts.values.sum() else counts[p.id] ?: 0
            val label = p?.name ?: "Alle"
            Column(
                Modifier.heightIn(min = 72.dp).shadow(if (on) 5.dp else 2.dp, RoundedCornerShape(3.dp))
                    .background(if (on) Color(0xFFF3D98E) else Color(0xFFF7EBCF), RoundedCornerShape(3.dp))
                    .drawPerforation(on).clip(RoundedCornerShape(3.dp))
                    .clickable(onClickLabel = if (p == null) "Alle Personen zeigen" else "Nur ${p.name} zeigen") { onChoose(p?.id) }
                    .semantics(mergeDescendants = true) { selected = on; set(SceneSlotKey, "person:${p?.id ?: "alle"}"); contentDescription = "$label: $n" }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Text(p?.emoji ?: "✉", fontSize = 22.sp)
                Text(label, fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Color(0xFF2E1E0C), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$n", fontFamily = Display, fontSize = 12.sp, color = Color(0xFF8A5A2B), maxLines = 1)
            }
        }
    }
}

/** The perforated edge of a stamp: small half circles of the paper's gap along all four sides. */
private fun Modifier.drawPerforation(on: Boolean): Modifier = this.then(Modifier.drawWithContent {
    drawContent()
    val r = 2.2.dp.toPx(); val step = 7.dp.toPx()
    val gap = Color(0x664A3220)
    var x = step / 2
    while (x < size.width) { drawCircle(gap, r, Offset(x, 0f)); drawCircle(gap, r, Offset(x, size.height)); x += step }
    var y = step / 2
    while (y < size.height) { drawCircle(gap, r, Offset(0f, y)); drawCircle(gap, r, Offset(size.width, y)); y += step }
    if (on) drawRect(Color(0xFF8A5A2B), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
})

/** The brass slider: four windows, each labelled with its real count; the chosen one is pushed in (lit). */
@Composable
private fun BrassSlider(view: PostViews.View, counts: Map<PostViews.View, Int>, onView: (PostViews.View) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().semantics { contentDescription = "Messingschieber: Ansicht der Briefe" }) {
        val fontScale = LocalDensity.current.fontScale
        // one row when every window has room for "Ungelesen" and its count; otherwise two rows of two
        val oneRow = maxWidth / 4 >= 74.dp * fontScale.coerceAtLeast(1f)
        val rows = if (oneRow) listOf(PostViews.View.entries.toList()) else PostViews.View.entries.chunked(2)
        Column(
            Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(8.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFD9B66A), Color(0xFFB08A3E), Color(0xFF8C6A2C))), RoundedCornerShape(8.dp))
                .border(1.5.dp, Color(0xFF5E4518), RoundedCornerShape(8.dp)).padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { v ->
                        val on = v == view
                        Column(
                            Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(5.dp))
                                .background(if (on) Color(0xFF3B2A12) else Color(0xFFFFF4DA))
                                .border(1.dp, Color(0xFF6B5020), RoundedCornerShape(5.dp))
                                .clickable(onClickLabel = "${v.label} zeigen") { onView(v) }
                                .semantics(mergeDescendants = true) { selected = on; role = Role.Tab; contentDescription = "${v.label}: ${counts[v] ?: 0}" }
                                .testTag("post-view-${v.name}").padding(horizontal = 2.dp, vertical = 3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Text(v.label, fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, lineHeight = 13.sp, maxLines = 1, softWrap = false,
                                color = if (on) Color(0xFFFFE6A8) else Color(0xFF3B2410))
                            Text("${counts[v] ?: 0}", fontFamily = Display, fontSize = 14.sp, lineHeight = 15.sp, maxLines = 1,
                                color = if (on) Color(0xFFFFE6A8) else Color(0xFF8A5A2B))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Where the letters are: nailed to the trunk, standing in the drawer, on the desk, in the tray. A grid that scrolls
 * (never a hidden letter), the search on top when there are many, an empty view says what is empty.
 */
@Composable
private fun LetterPlace(
    holder: StageScenes.Holder, shown: List<ApiClient.Message>, opened: Map<Long, OpenedMessage>, search: Boolean, query: String,
    onQuery: (String) -> Unit, view: PostViews.View, person: String?, onOpen: (ApiClient.Message) -> Unit, onLocked: (ApiClient.Message) -> Unit,
) {
    val inside = when (holder) {
        StageScenes.Holder.NAILED_TO_TRUNK -> Color.Transparent
        StageScenes.Holder.COUNTER_TRAY -> Color(0x66551A12)
        else -> Color(0x55203018)
    }
    Column(Modifier.fillMaxSize().background(inside, RoundedCornerShape(6.dp)).padding(4.dp).semantics { contentDescription = "Briefe: ${shown.size}" }) {
        if (search) Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).shadow(2.dp, RoundedCornerShape(24.dp)).background(Color(0xFFFFF8E6), RoundedCornerShape(24.dp))
                .border(1.dp, Color(0xFF8A6A3A), RoundedCornerShape(24.dp)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(R.drawable.ico_search, null, tint = Color(0xFF5A3A12), size = 18.dp)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Name oder Betreff suchen", fontFamily = Body, fontSize = 13.sp, color = Color(0xFF8A7A62), maxLines = 1)
                BasicTextField(query, onQuery, singleLine = true, textStyle = TextStyle(fontFamily = Body, fontSize = 14.sp, color = Color(0xFF3B2410)),
                    cursorBrush = SolidColor(Color(0xFF3B2410)), modifier = Modifier.fillMaxWidth().testTag("post-search")
                        .semantics { contentDescription = "Briefe durchsuchen" })
            }
            if (query.isNotEmpty()) Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = "Suche leeren") { onQuery("") }, Alignment.Center) {
                AppIcon(R.drawable.ico_close, null, tint = Color(0xFF8A6A3A), size = 14.dp)
            }
        }
        if (shown.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
            SceneSlip(
                when (view) {
                    PostViews.View.ALL -> "Noch keine Briefe"; PostViews.View.UNREAD -> "Nichts Ungelesenes"
                    PostViews.View.READ -> "Noch nichts gelesen"; PostViews.View.SENT -> "Noch nichts gesendet"
                } + (person?.let { " mit $it" } ?: ""),
                if (query.isNotBlank()) "Keine Treffer für „${query.trim()}“." else "Hier erscheint jeder Brief, auch unterwegs oder versiegelt.",
                Modifier.widthIn(max = 260.dp).semantics { set(SceneSlotKey, "empty:${view.name}") },
            )
        } else LazyVerticalGrid(
            GridCells.Adaptive(if (holder == StageScenes.Holder.NAILED_TO_TRUNK) 132.dp else 140.dp), Modifier.fillMaxWidth().weight(1f).testTag("post-letters"),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp),
        ) {
            items(shown, key = { it.id }) { m ->
                val state = Conversations.letterState(m, opened[m.id] != null)
                LetterObject(m, state, PostViews.viewOf(m, opened[m.id] != null), holder) { if (state.locked) onLocked(m) else onOpen(m) }
            }
        }
    }
}

/** One letter as the stage holds it: a sheet nailed to the bark, an envelope standing in the drawer / tray. */
@Composable
private fun LetterObject(m: ApiClient.Message, state: LetterState, view: PostViews.View, holder: StageScenes.Holder, onClick: () -> Unit) {
    val nailed = holder == StageScenes.Holder.NAILED_TO_TRUNK
    val shape = RoundedCornerShape(if (nailed) 2.dp else 6.dp)
    val paper = if (state.locked) Color(0xFFE7DCC6) else Color(0xFFFFF8E8)
    val readInk = when (view) { PostViews.View.UNREAD -> Color(0xFFA4533F); PostViews.View.READ -> Color(0xFF5E7F5A); else -> Color(0xFF3F6A7C) }
    val availability = PostViews.availability(m, state)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 80.dp).shadow(3.dp, shape).background(paper, shape)
            .border(1.dp, Color(0xFFC9AE80), shape).clip(shape)
            .clickable(onClickLabel = if (state.locked) "Versiegelt – Status zeigen" else "Brief öffnen", onClick = onClick)
            .semantics(mergeDescendants = true) {
                set(SceneSlotKey, "letter:${m.id}")
                contentDescription = "Brief " + (if (m.incoming) "von " else "an ") + "${m.peerName}: ${Conversations.letterPreviewText(m)} · ${view.label} · $availability"
            },
    ) {
        if (nailed) Box(Modifier.align(Alignment.TopCenter).padding(top = 3.dp).size(8.dp).background(Color(0xFF4A3A2A), CircleShape))
        // the envelope's flap line
        if (!nailed) Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            drawLine(Color(0x66B08A55), Offset(0f, 0f), Offset(size.width / 2, size.height), 1.5f * density)
            drawLine(Color(0x66B08A55), Offset(size.width, 0f), Offset(size.width / 2, size.height), 1.5f * density)
        }
        Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (m.incoming) "von ${m.peerName}" else "an ${m.peerName}", fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp,
                    color = Color(0xFF3B2410), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                // a wax seal on a letter that cannot be opened yet
                if (state.locked) Box(Modifier.size(18.dp).background(Color(0xFF9A3B2E), CircleShape).border(1.dp, Color(0xFF6E2318), CircleShape))
            }
            Text(Conversations.letterPreviewText(m), fontFamily = Display, fontSize = 13.sp, lineHeight = 15.sp, color = Color(0xFF2E2418), maxLines = 1, overflow = TextOverflow.Ellipsis)
            // reading state (coloured) and where the letter is, on one line
            Text(androidx.compose.ui.text.buildAnnotatedString {
                pushStyle(androidx.compose.ui.text.SpanStyle(color = readInk, fontWeight = FontWeight.ExtraBold)); append(view.label); pop()
                append(" · $availability")
            }, fontFamily = Body, fontSize = 11.sp, lineHeight = 13.sp, color = Color(0xFF5A4A3A), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------- Hafen

/**
 * Quests and topics, 7.9.14: the quest notes hang on the notice surface of the landing (the driftwood sign, the roofed
 * board on the jetty, the shed's board, the cork board on the quay); open / done as two wooden tabs on its top edge,
 * more notes scroll on the board. The jetty always starts on the shore; boats that come in are LETTER boats only (the
 * arrival opens the post); topics are a thing beside the board.
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
    /** 7.9.13: letters that really arrived (unlocked, unread) and the way to them (the post); null = not offered here. */
    arrivals: Int = 0,
    onArrivals: (() -> Unit)? = null,
) {
    var showDone by rememberSaveable { mutableStateOf(false) }
    val openTopics = topics.count { it.completedAt == null }
    val openQuests = quests.count { it.completedAt == null }
    val story = StageStory.bind(IsleBuilding.HARBOUR, level, buildMap {
        put(StageStory.Act.TOPICS, StoryHook(openTopics, onAllTopics))
        put(StageStory.Act.OPEN_QUESTS, StoryHook(openQuests, if (openQuests > 0) ({ showDone = false }) else null, "Keine offene Quest – pinne eine an"))
        if (onArrivals != null) put(StageStory.Act.ARRIVALS, StoryHook(arrivals, if (arrivals > 0) onArrivals else null, "Gerade ist kein Brief angekommen"))
    })
    PlaceStage(IsleBuilding.HARBOUR, onClose, level, action = "Quest anpinnen" to onNewQuest, story = story) { g ->
        val shown = quests.filter { (it.completedAt != null) == showDone }
        val board = g.onScreen(g.rect(StageScenes.Surface.BOARD)!!, minW = 300.dp)
        SurfaceStack(g, listOf(board to false, board to true), minFill = 170.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                BoardTab("Offen ${quests.count { it.completedAt == null }}", !showDone) { showDone = false }
                BoardTab("Erledigt ${quests.count { it.completedAt != null }}", showDone) { showDone = true }
            }
            Box(Modifier.fillMaxSize().background(Color(0x33241608), RoundedCornerShape(6.dp))) {
                if (shown.isEmpty()) SceneSlip(if (showDone) "Noch nichts abgehakt" else "Noch kein Auftrag", "Pinne eine Quest an.", Modifier.align(Alignment.Center))
                else LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize().testTag("harbour-board"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp)) {
                    items(shown, key = { it.id }) { q ->
                        Box(Modifier.fillMaxWidth().height(158.dp).semantics { set(SceneSlotKey, "quest:${q.id}") }) {
                            QuestNote(q, onQuestDone, if (q.canDelete) onQuestDelete else null, pinned = true, tilt = if (q.id % 2 == 0L) 1.5f else -1.5f)
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
        text, fontFamily = Display, fontSize = 13.sp,
        color = if (selected) Color(0xFFFFF6E5) else Color(0xFF5A3A12),
        modifier = Modifier.shadow(if (selected) 3.dp else 0.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50))
            .background(if (selected) Color(0xFF7A4B26) else Color(0xEEFFF6E5))
            .clickable(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 13.dp)
            .semantics { this.selected = selected; role = Role.Tab },
    )
}

@Composable
private fun QuestNote(q: ApiClient.Quest, onDone: (ApiClient.Quest, Boolean) -> Unit, onDelete: ((ApiClient.Quest) -> Unit)?, pinned: Boolean = true, tilt: Float = 0f) {
    val done = q.completedAt != null
    Box(Modifier.fillMaxSize().rotate(tilt).testTag("quest-note")) {
        // 7.9.14: the note is drawn paper exactly as big as its text area (the old p_note picture had a transparent margin
        // and a green cut-out seam: the text lay beside the paper); a deckled lower edge and a soft shadow
        Box(Modifier.fillMaxSize().padding(top = 6.dp).shadow(4.dp, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 10.dp, bottomEnd = 2.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFFBF3DE), Color(0xFFF1E3C2))), RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 10.dp, bottomEnd = 2.dp))
            .border(1.dp, Color(0xFFC9AE80), RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 10.dp, bottomEnd = 2.dp)).testTag("quest-paper"))
        // 7.9.13 (renders Anleger / Hafen, 360 dp, font 1.5): three rows in order, nothing on top of another: title with
        // the × beside it (one 48 dp row), quest icon · partner · points, then "Abhaken" alone (48 dp). Title and partner
        // end with "…" when long, never under a button.
        Column(Modifier.fillMaxSize().padding(start = 10.dp, end = 6.dp, top = 14.dp, bottom = 6.dp)) {
            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(q.title, fontFamily = Display, color = Color(0xFF3B2410), fontSize = 13.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).testTag("quest-title"))
                if (onDelete != null) Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = "Quest löschen") { onDelete(q) }.testTag("quest-delete"), Alignment.Center) {
                    AppIcon(R.drawable.ico_close, "Quest löschen", tint = Color(0xFF8A6A3A), size = 14.dp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(AppIcons.questIcon(q.icon), AppIcons.questLabels[q.icon], tint = Color(0xFF5A3A12), size = 16.dp)
                Spacer(Modifier.width(4.dp))
                Text(if (q.targetType == "group") q.targetName else "mit ${q.targetName}", fontFamily = Body, fontSize = 10.sp, lineHeight = 12.sp, color = Color(0xFF8A6A3A), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).testTag("quest-partner"))
                Sprite(R.drawable.p_star, Modifier.size(12.dp))
                Text("${q.points}", fontFamily = Display, color = Color(0xFF9A6B00), fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.testTag("quest-done").heightIn(min = 48.dp).clip(RoundedCornerShape(50)).clickable { onDone(q, !done) }
                    .background(if (done) Color(0xFF5E7F5A) else Color(0xFF3F7C78)),
                Alignment.Center,
            ) { Text(if (done) "Erledigt" else "Abhaken", fontFamily = Display, fontSize = 11.sp, color = Color.White, maxLines = 1, modifier = Modifier.padding(horizontal = 12.dp)) }
        }
        // pinned to the board
        if (pinned) Sprite(R.drawable.p_pin, Modifier.align(Alignment.TopCenter).size(20.dp))
        if (done) Text(
            "ERLEDIGT", color = Color(0xB32E7D32), fontFamily = Display, fontSize = 14.sp,
            modifier = Modifier.align(Alignment.Center).rotate(-18f).border(2.dp, Color(0xB32E7D32), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp),
        )
    }
}

// ---------------------------------------------------------------- Freunde (Ausschau)

/**
 * Friends and requests, 7.9.14: from the place at the island's edge one looks out to sea – from the palm top, the
 * lookout deck, over the signal station's wall, from the lighthouse gallery. Friends are seen out there (a light with
 * their picture and name on the sea of the picture), requests not answered yet are faded signals, requests to me are
 * bottle post with Yes / No. More friends = pages ("Im Blick"). No boats: boats are the harbour's letter boats.
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
    val t = rememberInfiniteTransition(label = "sea")
    val bob by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "bob")
    val scene = StageScenes.of(IsleBuilding.LIGHTHOUSE, level)
    var page by rememberSaveable { mutableStateOf(0) }
    var bottlePage by rememberSaveable { mutableStateOf(0) }
    val perPage = 6
    val seenCount = pals.size + outgoing.size
    // the pages the view really shows (fewer places per page when the request card covers one), set by the stage below
    var livePages by remember { mutableStateOf((seenCount + perPage - 1) / perPage) }
    val beamPages = livePages
    val requestPages = incoming.size
    var bottleH by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val story = StageStory.bind(IsleBuilding.LIGHTHOUSE, level, mapOf(
        StageStory.Act.FIND to StoryHook(null, onFind),
        StageStory.Act.REQUESTS to StoryHook(incoming.size, if (incoming.isNotEmpty()) ({ bottlePage = (bottlePage + 1) % requestPages.coerceAtLeast(1) }) else null, "Keine offene Anfrage an dich"),
        StageStory.Act.FRIENDS to StoryHook(pals.size, if (beamPages > 1) ({ page = (page + 1) % beamPages }) else null,
            if (pals.isEmpty()) "Noch kein Freund im Blick" else "Alle Freunde sind im Blick"),
    ))
    PlaceStage(IsleBuilding.LIGHTHOUSE, onClose, level, action = "Freunde finden" to onFind, story = story) { g ->
        val seen: List<Pair<ApiClient.UserSummary?, ApiClient.OutgoingRequest?>> = pals.map { it to null } + outgoing.map { null to it }
        val sea = g.onScreen(g.rect(StageScenes.Surface.SEA)!!, minW = 280.dp)
        // three rows of places fit between the sea's top and the request card (or the bottom row): the lights get
        // smaller (never below 84 dp) rather than sending a friend to the next page
        val below = if (incoming.isNotEmpty()) g.screen.h - 136.dp - maxOf(bottleH, 100.dp) - 4.dp else g.screen.h - g.reserve
        val size = minOf((sea.w * .44f).coerceIn(84.dp, 112.dp), ((below - sea.top - 12.dp) / 3 / 1.05f)).coerceAtLeast(84.dp)
        val oh = size * 1.05f
        // six places on the sea of the picture: two columns, three rows (closer together when the sea is low)
        val rowStep = maxOf(oh + 6.dp, minOf((sea.h - oh) / 2, (below - sea.top - oh) / 2))
        val slots = (0 until 6).map { i -> DpOffset(sea.left + sea.w * (if (i % 2 == 0) .26f else .74f), sea.top + oh / 2 + rowStep * (i / 2)) }
        fun topOf(s: DpOffset) = (s.y - oh / 2).coerceAtMost(g.screen.h - g.reserve - oh).coerceAtLeast(g.header)
        fun box(p: DpOffset): Modifier =
            Modifier.offset(x = (p.x - size / 2).coerceIn(4.dp, g.screen.w - size - 4.dp), y = topOf(p)).size(size, oh)
        if (seen.isEmpty()) SceneSlip(if (level < 2) "Noch winkt niemand zurück" else "Noch leuchtet niemand zurück", "Finde Freunde – sie tauchen hier draußen auf.",
            Modifier.offset(x = sea.left + 8.dp, y = sea.top + 8.dp).width(sea.w - 16.dp))
        // no friend lies under the request card (measured: its text wraps at a large font); a slot it covers stays
        // empty and its friend moves to the next page, nobody is dropped
        val cardBottom = g.screen.h - 136.dp; val cardTop = cardBottom - bottleH - 4.dp
        fun covered(s: DpOffset) = topOf(s).let { tp -> s.x - size / 2 < 264.dp && tp < cardBottom && tp + oh > cardTop }
        // two places never overlap (a short phone pushes the lower rows up against the reserve)
        val distinct = slots.fold(listOf<DpOffset>()) { acc, s -> if (acc.all { o -> kotlin.math.abs((o.x - s.x).value) >= size.value || kotlin.math.abs((topOf(o) - topOf(s)).value) >= oh.value }) acc + s else acc }
        fun apart(a: DpOffset, b: DpOffset) = kotlin.math.abs((a.x - b.x).value) >= size.value || kotlin.math.abs((topOf(a) - topOf(b)).value) >= oh.value
        // a covered place first moves up right above the card if that spot is free (7.9.13); only otherwise it stays empty
        val free = if (incoming.isEmpty() || bottleH == 0.dp) distinct else {
            val kept = distinct.filter { !covered(it) }.toMutableList()
            distinct.filter { covered(it) }.forEach { s ->
                val up = DpOffset(s.x, cardTop - oh / 2 - 2.dp)
                if (cardTop - oh - 2.dp >= g.header && !covered(up) && kept.all { apart(it, up) }) kept += up
            }
            kept.ifEmpty { distinct.take(1) }
        }
        if (seen.size > free.size) g.reserve = 132.dp
        val pages = seen.chunked(free.size.coerceAtLeast(1))
        androidx.compose.runtime.SideEffect { livePages = pages.size.coerceAtLeast(1) }
        pages.getOrNull(page.coerceAtMost(pages.lastIndex.coerceAtLeast(0)))?.forEachIndexed { i, (f, out) ->
            val m = box(free[i])
            if (f != null) Lookout(f.avatarEmoji, f.name, profileColor(f.displayColor), levels[f.id] ?: 1, scene.night || level == 2, bob, waiting = false,
                m.semantics { set(SceneSlotKey, "friend:${f.id}") }.clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = "${f.name} öffnen") { onFriend(f.id) })
            else if (out != null) Lookout("✉", out.recipientName, Color(0xFF9FB4C8), 0, scene.night || level == 2, bob, waiting = true,
                m.semantics { set(SceneSlotKey, "waiting:${out.id}"); contentDescription = "Signal an ${out.recipientName} – noch keine Antwort" })
        }
        ScenePager(page, pages.size, "Im Blick") { page = it }
        // requests to me: bottle post washed up below the place; one per page, "weiter ›" pages
        val bottles = incoming.chunked(1)
        val shownBottles = bottles.getOrNull(bottlePage.coerceAtMost(bottles.lastIndex.coerceAtLeast(0))).orEmpty()
        if (shownBottles.isNotEmpty()) Column(
            Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 10.dp, bottom = 136.dp).width(250.dp)
                .onSizeChanged { bottleH = with(density) { it.height.toDp() } },
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

/** A friend as the place sees them out on the sea: a light with their picture and name (faded while unanswered). */
@Composable
private fun Lookout(emoji: String, name: String, color: Color, stars: Int, night: Boolean, bob: Float, waiting: Boolean, modifier: Modifier) {
    Column(modifier.alpha(if (waiting) .62f else 1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().weight(1f).offset(y = (bob * 2).dp), Alignment.Center) {
            Box(Modifier.fillMaxSize(.95f).background(Brush.radialGradient(listOf(if (night) Color(0xAAFFE7A0) else Color(0x88FFFFFF), Color.Transparent)), CircleShape))
            Box(
                Modifier.size(44.dp).shadow(3.dp, CircleShape)
                    .background(Brush.radialGradient(listOf(Color.White, color)), CircleShape).border(2.dp, Color(0xFFFFF6E5), CircleShape),
                Alignment.Center,
            ) { Text(emoji, fontSize = 21.sp) }
        }
        // the name may be wider than the light (up to 140 dp), so it is whole at a large font
        Text(if (waiting) "$name?" else name, color = Color.White, fontFamily = Display, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.wrapContentWidth(unbounded = true).widthIn(max = 140.dp).background(Color(0xCC102040), RoundedCornerShape(50)).padding(horizontal = 7.dp))
        if (stars > 0) Row { repeat(stars.coerceIn(1, 4)) { Sprite(R.drawable.p_star, Modifier.size(10.dp)) } }
    }
}

@Composable
private fun Bottle(name: String, bob: Float, onYes: () -> Unit, onNo: () -> Unit) {
    // 7.9.13: request text above, the two answers in their own row: in one row (250 dp, font 1.5) the text was cut.
    Column(
        Modifier.fillMaxWidth().offset(y = (bob * 2).dp).shadow(5.dp, RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color(0xF2FFFBF1), Color(0xF2F6E7C8))), RoundedCornerShape(20.dp))
            .border(1.5.dp, Color(0xFFD9BE8A), RoundedCornerShape(20.dp)).padding(6.dp).testTag("bottle-card"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Sprite(R.drawable.p_bottle, Modifier.size(40.dp).rotate(-35f + bob * 6))
            Column(Modifier.weight(1f)) {
                Text(name, fontFamily = Display, color = Color(0xFF3B2410), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("bottle-name"))
                Text("Freundschaftsanfrage", fontFamily = Body, color = Color(0xFF8A6A3A), fontSize = 11.sp, lineHeight = 13.sp, maxLines = 2,
                    modifier = Modifier.testTag("bottle-ask"))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            Text("Nein", fontFamily = Body, fontWeight = FontWeight.Bold, color = Color(0xFF8A6A3A), maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onNo).heightIn(min = 48.dp).testTag("bottle-no").padding(horizontal = 10.dp, vertical = 10.dp))
            Text(
                "Annehmen", fontFamily = Display, color = Color.White, fontSize = 13.sp, maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF3F7C78)).clickable(onClick = onYes).heightIn(min = 48.dp).testTag("bottle-yes").padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- Wörterbuch

/** The four categories' colours of the painted register (blue, ochre, green, red ribbons / drawers / banners). */
private val RegisterColours = mapOf(
    "Begriffe" to Color(0xFF3F5E7E), "Witze" to Color(0xFF9C7424), "Sprüche" to Color(0xFF55703A), "Zitate" to Color(0xFF8E3B32),
)

/**
 * The shared glossary, 7.9.14: the book that lies in the place (on the stump, on the sheltered desk, on the study's
 * table, under the library's lamp) is opened where it lies: its pages rise from the painted book's edge and hold the
 * glossary (search, categories, entries, explanations to read / add / change / delete). The place's register picks the
 * category: the ribbons hanging from the book, the card drawers of the study, the banners of the library.
 */
@Composable
internal fun LibraryBook(onClose: () -> Unit, glossary: @Composable () -> Unit, level: Int = 3) {
    val scene = StageScenes.of(IsleBuilding.LIBRARY, level)
    // the place's register (ribbons, drawers, banners) is the category control: the book's pages keep the room for words
    val commands = remember { GlossaryCommands().apply { register = true } }
    val story = StageStory.bind(IsleBuilding.LIBRARY, level, mapOf(
        StageStory.Act.SEARCH to StoryHook(null, { commands.search++ }),
        StageStory.Act.CATEGORIES to StoryHook(null, { commands.category++ }),
        StageStory.Act.ADD_WORD to StoryHook(null, if (commands.canAdd) ({ commands.add++ }) else null, "Nur lesen: dieser Server kennt das gemeinsame Wörterbuch nicht"),
    )).map { (item, hook) -> if (item.act == StageStory.Act.CATEGORIES) item.copy(label = item.label + " (jetzt: ${commands.shown ?: "Alle"})") to hook else item to hook }
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    PlaceStage(IsleBuilding.LIBRARY, onClose, level, story = story) { g ->
        val painted = g.rect(StageScenes.Surface.BOOK)!!
        val register = g.rect(StageScenes.Surface.REGISTER)!!
        val drawerW = 96.dp * fontScale.coerceAtMost(1.3f)
        // the study's card drawers stand right of the book when the book keeps 300 dp beside them; on a narrow phone or
        // with a large font they hang as a row below the book like the ribbons
        val beside = scene.holder == StageScenes.Holder.STUDY_BOOK && g.onScreen(register).let { r -> minOf(r.left, g.screen.w - 8.dp - drawerW) } - 14.dp >= 300.dp
        val above = scene.holder == StageScenes.Holder.LIBRARY_BOOK
        // the register as ribbons / banners: one row, or two rows when the labels would not fit whole in one
        val rowW = g.screen.w - 16.dp
        val twoRows = rowW / 5 < 66.dp * fontScale
        val rowH = maxOf(48.dp, 20.dp * fontScale + 18.dp)
        val ribbonH = rowH * (if (twoRows) 2 else 1) + (if (twoRows) 4.dp else 0.dp)
        val drawers = if (beside) g.onScreen(register).let { r -> DpRect(minOf(r.left, g.screen.w - 8.dp - drawerW), r.top, drawerW, maxOf(r.h, 50.dp * fontScale * 5)) } else null
        val banners = if (above) g.onScreen(register, minW = rowW).let { r -> DpRect(r.left, r.top, r.w, ribbonH) } else null
        // the opened book: from the painted book's lower edge up (below the banners), as wide as the screen allows (the
        // study's book left of the card drawers); on a short phone it reaches further down over the stump / table so its
        // pages keep at least 360 dp; the ribbons hang below it
        val below = if (beside || above) 0.dp else ribbonH + 10.dp
        val limit = g.screen.h - g.reserve - below
        val top = banners?.let { it.bottom + 6.dp } ?: (g.header + 2.dp)
        val bottom = if (painted.bottom - top >= 360.dp) minOf(painted.bottom, limit) else minOf(top + 360.dp, limit).coerceAtLeast(minOf(painted.bottom, limit))
        val right = drawers?.let { it.left - 6.dp } ?: (g.screen.w - 8.dp)
        val book = DpRect(8.dp, top, right - 8.dp, maxOf(bottom - top, 220.dp))
        androidx.compose.runtime.CompositionLocalProvider(LocalGlossaryCommands provides commands) {
            Box(book.box().semantics { set(SceneSlotKey, "book:${scene.holder}") }) {
                // the page block's thickness under the pages: it lies there as a book
                Box(Modifier.align(Alignment.BottomCenter).offset(y = 6.dp).fillMaxWidth(.98f).height(12.dp).shadow(6.dp, RoundedCornerShape(3.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFF3E6CA), Color(0xFFD9C29A), Color(0xFFEFE0BF), Color(0xFFC9AE80))), RoundedCornerShape(3.dp)))
                // the cover of the painted book: green cloth, the library's brown leather
                val cover = if (scene.holder == StageScenes.Holder.LIBRARY_BOOK) listOf(Color(0xFF5B3A22), Color(0xFF3E2716)) else listOf(Color(0xFF4F6340), Color(0xFF3A4A2E))
                Box(Modifier.fillMaxSize().shadow(12.dp, RoundedCornerShape(10.dp)).background(Brush.verticalGradient(cover), RoundedCornerShape(10.dp))
                    .border(1.5.dp, Color(0xFF2A2014), RoundedCornerShape(10.dp)).padding(horizontal = 7.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFFE6D6B4), Color(0xFFFFF9EC), Color(0xFFFFF9EC), Color(0xFFF3E6CA))))) {
                    GlossaryPage(glossary)
                    // the spine in the middle of the open book
                    Box(Modifier.align(Alignment.TopCenter).width(2.dp).fillMaxHeight(.04f).background(Color(0x553B2410)))
                }
            }
            val ribbons = if (!beside && !above) g.onScreen(register, minW = rowW).let { r -> DpRect(r.left, book.bottom + 10.dp, r.w, ribbonH) } else null
            Register(if (drawers == null && scene.holder == StageScenes.Holder.STUDY_BOOK) StageScenes.Holder.SHELTERED_BOOK else scene.holder,
                commands.shown, drawers ?: banners ?: ribbons!!, twoRows) { commands.choose(it) }
        }
    }
}

/** The register of the place: ribbons hanging from the book, card drawers, banners; Alle + the four categories. */
@Composable
private fun Register(holder: StageScenes.Holder, shown: String?, at: DpRect, twoRows: Boolean, onPick: (String?) -> Unit) {
    val all = listOf<String?>(null) + GlossaryCategories.all
    @Composable
    fun mark(c: String?, modifier: Modifier) {
        val on = c == shown
        val colour = c?.let { RegisterColours[it] } ?: Color(0xFF6B5B48)
        val shape = when (holder) {
            StageScenes.Holder.STUDY_BOOK -> RoundedCornerShape(3.dp)
            StageScenes.Holder.LIBRARY_BOOK -> RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)
            else -> RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp)
        }
        Box(modifier.heightIn(min = 48.dp).shadow(if (on) 5.dp else 2.dp, shape).clip(shape).background(colour)
            .border(if (on) 2.dp else 1.dp, if (on) Color(0xFFFFE6A8) else Color(0x66000000), shape)
            .clickable(onClickLabel = "Kategorie ${c ?: "Alle"} aufschlagen") { onPick(c) }
            .semantics { selected = on; role = Role.Tab; contentDescription = "Register ${c ?: "Alle"}" }.testTag("library-register")
            .padding(horizontal = 2.dp, vertical = 4.dp), Alignment.Center) {
            Text(c ?: "Alle", fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = Color(0xFFFFF6E5), maxLines = 1, softWrap = false,
                textAlign = TextAlign.Center, modifier = Modifier.testTag("library-register-label"))
        }
    }
    if (holder == StageScenes.Holder.STUDY_BOOK) Column(at.box().semantics { contentDescription = "Karteischubladen" }
        .background(Color(0x55241608), RoundedCornerShape(6.dp)).padding(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        all.forEach { c -> mark(c, Modifier.fillMaxWidth().weight(1f)) }
    } else Column(at.box().semantics { contentDescription = if (holder == StageScenes.Holder.LIBRARY_BOOK) "Banner der Bibliothek" else "Bänder des Buchs" },
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (if (twoRows) all.chunked(3) else listOf(all)).forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) { row.forEach { c -> mark(c, Modifier.weight(1f).fillMaxHeight()) } }
        }
    }
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

// ---------------------------------------------------------------- Gruppen

/** 7.9.14: which group sits at the place's fire / table ([focus] = the chosen id, else the first). */
internal object GroupFocus {
    fun of(groups: List<ApiClient.Group>, focus: Long?): ApiClient.Group? = groups.firstOrNull { it.id == focus } ?: groups.firstOrNull()
}

/**
 * Groups, 7.9.14: the place is cosy and close – the fire on the clearing, the round table under the open roof, the round
 * table before the hearth, the table in the glasshouse at the first fire. ONE group sits there at a time, right away
 * (nothing to swipe when there is one): its members around the fire / table with their names, its name on the pennant /
 * the beam / the mantel (tap: all names). More groups: their pennants on the ground in front, each one tap away; no
 * empty phantom tables. No group: the place says so and the golden action founds one. Tapping the fire / table = a plan
 * (quest) for that group; the notice = the real group plans.
 */
@Composable
internal fun CommunityHall(
    groups: List<ApiClient.Group>,
    quests: List<ApiClient.Quest>,
    onGroupQuest: (Long) -> Unit,
    onManage: () -> Unit,
    onClose: () -> Unit,
    level: Int = 3,
    /** 7.9.13: the group plans on the harbour's board; null = not offered here. */
    onPlans: (() -> Unit)? = null,
) {
    val scene = StageScenes.of(IsleBuilding.CAMPFIRE, level)
    var focus by rememberSaveable { mutableStateOf<Long?>(null) }
    var namesOpen by rememberSaveable { mutableStateOf(false) }
    val here = GroupFocus.of(groups, focus)
    val groupQuests = quests.filter { it.targetType == "group" && groups.any { g -> g.id == it.targetId } }
    val openPlans = groupQuests.count { it.completedAt == null }
    val donePlans = groupQuests.count { it.completedAt != null }
    val story = StageStory.bind(IsleBuilding.CAMPFIRE, level, buildMap {
        put(StageStory.Act.GROUP_QUEST, StoryHook(null, here?.let { gr -> { onGroupQuest(gr.id) } }, "Noch keine Gruppe – lege eine an"))
        if (onPlans != null) put(StageStory.Act.GROUP_PLANS, StoryHook(if (level == 3) openPlans + donePlans else openPlans,
            if (groupQuests.isNotEmpty()) onPlans else null, "Noch kein Gruppenplan"))
    })
    PlaceStage(IsleBuilding.CAMPFIRE, onClose, level, action = (if (groups.isEmpty()) "Gruppe gründen" else "Gruppen verwalten") to onManage, story = story) { g ->
        val c = g.centre!!
        if (here == null) {
            SceneSlip(when (level) { 0 -> "Noch sitzt niemand am Feuer"; 1 -> "Der Treffplatz ist noch leer"; 2 -> "Noch keine Runde am Kamin"; else -> "Das Glashaus ist noch leer" },
                "Gründe eine Gruppe – sie sitzt dann hier.", Modifier.offset(x = (c.x - 130.dp).coerceIn(8.dp, g.screen.w - 268.dp), y = (c.y - 50.dp).coerceAtLeast(g.header)).width(260.dp)
                    .semantics { set(SceneSlotKey, "empty:groups") })
            return@PlaceStage
        }
        val open = quests.count { it.targetType == "group" && it.targetId == here.id && it.completedAt == null }
        // the other groups' pennants lie on the ground in front: the seats stay above them
        val picker = g.onScreen(g.rect(StageScenes.Surface.PICKER)!!, minW = 300.dp)
        val pickerTop = if (groups.size > 1) minOf(picker.top, g.screen.h - g.reserve - 52.dp) else g.screen.h - g.reserve
        val nameH = 24.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
        // the members around the fire / table, each with their name
        val members = here.members.take(10)
        val rx = g.picture.w * scene.ring.first; val ry = g.picture.h * scene.ring.second
        val seat = 46.dp
        val tight = c.y + ry + seat / 2 + nameH + 4.dp > pickerTop
        members.forEachIndexed { i, m ->
            // round the ring from the front; when the ground in front is short (a small phone, the pennants), the ring starts
            // half a step round so nobody sits straight on the fire / table's plan button
            val a = (i.toFloat() / members.size) * 2f * PI.toFloat() + PI.toFloat() / 2 + (if (tight) PI.toFloat() / members.size else 0f)
            val x = (c.x + rx * cos(a)).coerceIn(48.dp, g.screen.w - 48.dp)
            val y = (c.y + ry * sin(a)).coerceIn(g.header + seat / 2 + 56.dp, maxOf(g.header + seat / 2 + 56.dp, pickerTop - seat / 2 - nameH - 4.dp))
            Column(Modifier.offset(x = x - 44.dp, y = y - seat / 2).width(88.dp)
                .semantics(mergeDescendants = true) { set(SceneSlotKey, "member:${here.id}:${m.id}"); contentDescription = "${m.name} sitzt bei ${here.name}" },
                horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(seat).shadow(4.dp, CircleShape).background(Color.White, CircleShape).padding(2.dp)
                    .background(Brush.radialGradient(listOf(Color.White, profileColor(m.displayColor).copy(alpha = .6f))), CircleShape), Alignment.Center) {
                    Text(m.avatarEmoji, fontSize = 20.sp)
                }
                Text(m.name, fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = Color(0xFF2E2418), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.background(Color(0xE6FFF6E5), RoundedCornerShape(50)).padding(horizontal = 6.dp))
            }
        }
        if (here.members.size > members.size) Tag("+${here.members.size - members.size} weitere",
            Modifier.offset(x = (c.x + rx * .6f).coerceAtMost(g.screen.w - 110.dp), y = (c.y + ry + 30.dp).coerceAtMost(g.screen.h - g.reserve - 24.dp))
                .clickable { namesOpen = true }, size = 12)
        // the fire / the table itself: a plan (quest) for the group sitting there
        Box(Modifier.offset(x = c.x - 56.dp, y = c.y - 40.dp).size(112.dp, 80.dp).clip(RoundedCornerShape(40.dp))
            .clickable(onClickLabel = "Plan für ${here.name}") { onGroupQuest(here.id) }
            .semantics { set(SceneSlotKey, "group:${here.id}"); contentDescription = "Plan für ${here.name}" + if (open > 0) ": $open offen" else ""; role = Role.Button },
            Alignment.BottomCenter) {
            if (open > 0) Row(Modifier.shadow(2.dp, RoundedCornerShape(50)).background(Color(0xF2FFF6E5), RoundedCornerShape(50)).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Sprite(R.drawable.p_star, Modifier.size(13.dp)); Text(" $open", fontFamily = Display, fontSize = 12.sp, color = Color(0xFF9A6B00))
            }
        }
        // the other groups: their pennants on the ground in front, each one tap away
        if (groups.size > 1) {
            LazyRow(Modifier.offset(x = picker.left, y = pickerTop).width(picker.w).semantics { contentDescription = "Gruppen am Ort: ${groups.size}" },
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(groups, key = { it.id }) { gr ->
                    val on = gr.id == here.id
                    val shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 14.dp, bottomEnd = 14.dp)
                    Row(Modifier.heightIn(min = 48.dp).shadow(if (on) 4.dp else 2.dp, shape)
                        .background(if (on) Color(0xFFC94F3D) else Color(0xF2F3E6C8), shape).clip(shape)
                        .clickable(onClickLabel = "${gr.name} an den Platz holen") { focus = gr.id; namesOpen = false }
                        .semantics(mergeDescendants = true) { selected = on; set(SceneSlotKey, "pennant:${gr.id}"); contentDescription = "${gr.name}: ${gr.members.size}" }
                        .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(gr.name, fontFamily = Display, fontSize = 14.sp, color = if (on) Color.White else Color(0xFF3B2410), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 150.dp))
                        Text(" ${gr.members.size}", fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (on) Color(0xFFFFE6D0) else Color(0xFF8A6A3A))
                    }
                }
            }
        }
        // the group's name on the place's sign (pennant, beam, mantel), tap = its member names (drawn last: on top)
        val sign = g.onScreen(g.rect(StageScenes.Surface.SIGN)!!, minW = 220.dp)
        Column(Modifier.offset(x = sign.left, y = sign.top).width(sign.w), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.heightIn(min = 48.dp).shadow(4.dp, RoundedCornerShape(6.dp)).background(Color(0xF2F3E6C8), RoundedCornerShape(6.dp))
                .border(1.5.dp, Color(0xFF8A5A2B), RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp))
                .clickable(onClickLabel = if (namesOpen) "Namen schließen" else "Alle Namen zeigen") { namesOpen = !namesOpen }
                .semantics { set(SceneSlotKey, "sign:${here.id}"); contentDescription = "${here.name}: ${here.members.size} in der Runde" }
                .padding(horizontal = 12.dp, vertical = 6.dp), Alignment.Center) {
                Text("${here.name} · ${here.members.size}", fontFamily = Display, fontSize = 16.sp, color = Color(0xFF3B2410), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (namesOpen) Column(Modifier.padding(top = 4.dp).shadow(4.dp, RoundedCornerShape(6.dp)).background(Color(0xF7FFF8E6), RoundedCornerShape(6.dp))
                .padding(8.dp).semantics { contentDescription = "Mitglieder von ${here.name}" }) {
                here.members.forEach { m -> Text("${m.avatarEmoji} ${m.name}", fontFamily = Body, fontSize = 13.sp, color = Color(0xFF3B2410), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

// ---------------------------------------------------------------- Zuhause

/**
 * My place, 7.9.14: where I live at this stage – the night camp under the old beech (my picture on the wooden disc on
 * the branch, my name on the bag's tag, my notes in the open notebook, my counts on the slips at the root), the shelter,
 * the log cabin (picture in the frame, slips on the shelf), the country house's desk. The profile is here; technical
 * settings (account, server, sign out) stay outside the island ("Mehr", and the golden action).
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
    val counters = listOf(Triple("$friends", "Freunde", "friends"), Triple("$letters", "Briefe", "letters"),
        Triple("$questsDone", if (questsDone == 1) "Quest" else "Quests", "quests"))
    val story = StageStory.bind(IsleBuilding.HOUSE, level, mapOf(
        StageStory.Act.PROFILE to StoryHook(null, onSettings, accent = if (level == 3) profileColor(color) else null),
        StageStory.Act.EDIT to StoryHook(null, onEdit),
    ))
    PlaceStage(IsleBuilding.HOUSE, onClose, level, action = "Profil & Einstellungen" to onSettings, story = story) { g ->
        val framed = scene.holder == StageScenes.Holder.CABIN_ROOM || scene.holder == StageScenes.Holder.MANOR_DESK
        // me: on the wooden disc on the branch / under the roof, in the frame on the wall / on the desk
        // centred on the painted disc / frame itself (the picture is panned so it is on screen); only a disc under the
        // header (a short phone) moves down below it
        val av = g.rect(StageScenes.Surface.AVATAR)!!
        val side = (minOf(av.w, av.h) * .92f).coerceIn(64.dp, 132.dp)
        val meLeft = (av.left + (av.w - side) / 2).coerceIn(8.dp, g.screen.w - 8.dp - side)
        val meTop = maxOf(av.top + (av.h - side) / 2, g.header)
        Box(Modifier.offset(x = meLeft, y = meTop).size(side)
            .clip(if (framed) RoundedCornerShape(4.dp) else CircleShape).clickable(onClickLabel = "Profil öffnen", onClick = onSettings)
            .semantics { set(SceneSlotKey, "me"); contentDescription = "Ich: $name"; role = Role.Button }, Alignment.Center) {
            Box(Modifier.fillMaxSize(.78f).background(Brush.radialGradient(listOf(Color(0xF2FFFFFF), profileColor(color).copy(alpha = .55f))),
                if (framed) RoundedCornerShape(3.dp) else CircleShape), Alignment.Center) { Text(emoji, fontSize = (side.value * .34f).sp) }
        }
        // my name on the bag's tag / the plaque / the sheet, my notes, my counts – stacked in the picture's order
        val order = listOf(StageScenes.Surface.NAME, StageScenes.Surface.NOTES, StageScenes.Surface.COUNTERS)
            .sortedBy { scene.surface(it)!!.top }
        // a surface under the picture of me (moved on screen by a large font or a narrow phone) starts below it
        val meBottom = meTop + side + 4.dp
        val parts = order.map { s ->
            val r = g.onScreen(g.rect(s)!!, minW = when (s) { StageScenes.Surface.COUNTERS -> 228.dp; StageScenes.Surface.NOTES -> 170.dp; else -> 140.dp })
            (if (r.left < meLeft + side && r.right > meLeft && r.top < meBottom) r.copy(top = meBottom) else r) to false
        }
        SurfaceStack(g, parts) {
            order.forEach { s ->
                when (s) {
                    StageScenes.Surface.NAME -> Box(Modifier.fillMaxWidth().semantics { set(SceneSlotKey, "name") }, Alignment.Center) {
                        Text(name, fontFamily = Display, fontSize = 16.sp, color = Color(0xFF3B2410), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.shadow(3.dp, RoundedCornerShape(4.dp)).background(Color(0xFFE8D2A8), RoundedCornerShape(4.dp))
                                .border(1.dp, Color(0xFF7A5230), RoundedCornerShape(4.dp)).padding(horizontal = 10.dp, vertical = 3.dp))
                    }
                    StageScenes.Surface.NOTES -> Column(Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(3.dp))
                        .background(Color(0xFFFFF9EC), RoundedCornerShape(3.dp)).border(1.dp, Color(0xFFC9AE80), RoundedCornerShape(3.dp))
                        .clickable(onClickLabel = "Profil öffnen", onClick = onSettings).heightIn(min = 48.dp)
                        .semantics(mergeDescendants = true) { set(SceneSlotKey, "notes") }.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("$qp Punkte", fontFamily = Display, fontSize = 15.sp, color = Color(0xFF3B2410), maxLines = 1)
                        Text("Profil & Farbe ›", fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF5E4630), maxLines = 1)
                    }
                    else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        counters.forEachIndexed { i, (v, l, key) ->
                            Column(Modifier.weight(1f).rotate((i - 1) * 3f).shadow(2.dp, RoundedCornerShape(2.dp)).background(Color(0xFFF7EBCF), RoundedCornerShape(2.dp))
                                .border(1.dp, Color(0xFFB59A6A), RoundedCornerShape(2.dp)).padding(vertical = 3.dp)
                                .semantics(mergeDescendants = true) { set(SceneSlotKey, "keepsake:$key") },
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(v, fontFamily = Display, fontSize = 16.sp, color = Color(0xFF3B2410), maxLines = 1)
                                Text(l, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF3B2410), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
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
