package at.gregor.layermaxxing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * 7.9.12: every building × stage is a FUNCTION STAGE, not "hero picture on top + parchment card below" (7.9.11,
 * rejected). Each stage has one big painted place the function lives in: an outdoor set piece (the letter tree, the
 * mailbox stand, the fire ring, the jetty …) or a painted room (the small post room, the meeting hut, the
 * hall …). The real data objects sit at [Scene.slots], anchor points IN that picture (fractions of the picture
 * itself): envelopes pinned to the tree's branches, name plates on the mailbox stand, members on the stools around the
 * fire, notes on the jetty posts. More data than slots = scenic pages, nothing is filtered away. Names, functions,
 * data and actions stay those of the building ([IslandStages]); the header/back/action bar is shared.
 */
internal object StageScenes {
    enum class Backdrop { DAY_BEACH, EVENING_BEACH, NIGHT_SEA, INTERIOR }
    /** How the building's data is held at this stage. */
    enum class Holder {
        // Post
        PINNED_ENVELOPE, MAILBOX, CUBBY, CUBBY_WALL,
        // Leuchtturm
        ROWBOAT, SAILBOAT, SIGNAL_LIGHT, PORTHOLE,
        // Bibliothek
        PARCHMENT, OPEN_BOOK, SHELF_BOOK, LEATHER_BOOK,
        // Gemeindehaus
        FIRE_RING, CRATE_TABLE, BENCH_GROUP, ROUND_TABLE,
        // Hafen
        NOTES_ON_POST, JETTY_NOTES, PLANK_BOARD, CORK_BOARD,
        // Mein Haus
        KEEPSAKES, CHEST_SHELF, PORCH, LIVING_ROOM,
    }
    /** What the anchor points refer to: the set piece's picture, the room's picture, or the screen (open sea). */
    enum class Space { PIECE, ROOM, SCREEN }
    /** A point in a picture's own space (fractions of its width and height). */
    data class Anchor(val x: Float, val y: Float, val tilt: Float = 0f)
    /** The big painted place: centre x and foot y as screen fractions, its width as a fraction of the screen width. */
    data class SetPiece(val res: Int, val cx: Float, val foot: Float, val width: Float)
    /** A painted prop: centre x and foot y as screen fractions, width as fraction of the screen width. */
    data class Prop(val res: Int, val x: Float, val y: Float, val width: Float, val rotation: Float = 0f)
    /** A rectangle in the anchors' space (the open book's pages). */
    data class Region(val left: Float, val top: Float, val right: Float, val bottom: Float)

    data class Scene(
        val building: IsleBuilding, val level: Int, val backdrop: Backdrop,
        /** The painted room (Crop over the whole screen); null = outdoor. */
        val room: Int?,
        /** Painted ground of an outdoor stage, from [groundTop] (screen fraction) down. */
        val ground: Int? = null,
        val groundTop: Float = .74f,
        val piece: SetPiece?,
        val props: List<Prop> = emptyList(),
        val holder: Holder,
        val caption: String,
        val space: Space,
        /** Where the overview objects sit (one data object per slot and page). */
        val slots: List<Anchor>,
        /** Where the objects of one opened group sit (one friend's letters …); default the same slots. */
        val detailSlots: List<Anchor> = slots,
        /** The middle of a circle scene (fire, crate table) the members sit around. */
        val centre: Anchor? = null,
        /** Radii of the seat ring around [centre], fractions of the picture's width/height. */
        val ring: Pair<Float, Float> = .36f to .38f,
        /** The open book's pages (library). */
        val book: Region? = null,
    ) {
        val stage: IslandStages.Stage get() = IslandStages.of(building, level)
        /** The stage's own island piece (header picture), the same as on the island. */
        val hero: Int get() = stage.res
        val outdoor: Boolean get() = room == null
        val interior: Int? get() = room
        /** Night and the lighthouse gallery need light ink for titles drawn straight onto the scene. */
        val dark: Boolean get() = backdrop == Backdrop.NIGHT_SEA || building == IsleBuilding.LIGHTHOUSE && level == 3
        /** Everything that makes the stage look like it does, for tests and the review. */
        val signature: String get() = listOf("$building-$level", backdrop, room ?: "-", piece?.res ?: "-", ground ?: "-", holder,
            slots.size, props.joinToString("+") { "${it.res}" }).joinToString(" · ")
    }

    /** 7.9.13: ordinary starters (stage 0) stand on the island's land, a clearing; only the harbour's starter on the beach. */
    private val sand = R.drawable.terrain_sand
    private val meadow = R.drawable.terrain_home
    private fun a(x: Float, y: Float, t: Float = 0f) = Anchor(x, y, t)

    private val table: Map<IsleBuilding, List<Scene>> = mapOf(
        IsleBuilding.POST to listOf(
            Scene(IsleBuilding.POST, 0, Backdrop.DAY_BEACH, null, ground = meadow, groundTop = .84f,
                piece = SetPiece(R.drawable.n_tree, .5f, .97f, 1.3f), holder = Holder.PINNED_ENVELOPE,
                caption = "Großer Briefbaum: an Stamm und Ästen hängt für jeden Freund ein Umschlag an einer Nadel",
                props = listOf(Prop(R.drawable.n_rocks, .1f, .95f, .18f), Prop(R.drawable.n_flowerbush, .9f, .95f, .16f)),
                space = Space.PIECE,
                slots = listOf(a(.44f, .24f, -8f), a(.68f, .22f, 7f), a(.26f, .42f, 5f), a(.50f, .37f, -4f), a(.74f, .41f, -9f), a(.36f, .58f, 6f), a(.64f, .58f, -6f), a(.50f, .76f, 3f))),
            Scene(IsleBuilding.POST, 1, Backdrop.DAY_BEACH, null, ground = meadow, groundTop = .78f,
                piece = SetPiece(R.drawable.lm_post_ruin, .5f, .97f, 1.42f), holder = Holder.MAILBOX,
                caption = "Postkasten unter dem Schilfdach: Abholfächer mit Namensschildern am Stand, die Briefe liegen auf dem Brett davor",
                props = listOf(Prop(R.drawable.decor_lantern, .92f, .9f, .08f), Prop(R.drawable.n_bush, .08f, .97f, .16f)),
                space = Space.PIECE,
                slots = listOf(a(.31f, .40f), a(.67f, .36f), a(.31f, .58f), a(.68f, .55f), a(.50f, .48f), a(.50f, .68f)),
                detailSlots = listOf(a(.30f, .66f, -6f), a(.50f, .70f, 4f), a(.72f, .66f, -3f), a(.40f, .82f, 5f), a(.62f, .83f, -5f))),
            Scene(IsleBuilding.POST, 2, Backdrop.INTERIOR, R.drawable.fief_build_closeup, piece = null, holder = Holder.CUBBY,
                caption = "In der kleinen Poststube: Fächer an der Bretterwand, der Schalter ist die Werkbank darunter",
                space = Space.ROOM,
                slots = listOf(a(.38f, .19f), a(.62f, .19f), a(.38f, .37f), a(.62f, .37f), a(.50f, .53f)),
                detailSlots = listOf(a(.30f, .66f, -5f), a(.52f, .64f, 4f), a(.72f, .68f, -3f), a(.40f, .80f, 5f), a(.64f, .82f, -4f))),
            Scene(IsleBuilding.POST, 3, Backdrop.INTERIOR, R.drawable.bg_post, piece = null, holder = Holder.CUBBY_WALL,
                caption = "Großer Fächerraum des Postamts: eine ganze Wand voller Postfächer über dem Schalter",
                space = Space.ROOM,
                slots = listOf(a(.30f, .25f), a(.50f, .25f), a(.70f, .25f), a(.30f, .39f), a(.50f, .39f), a(.70f, .39f), a(.30f, .53f), a(.50f, .53f), a(.70f, .53f)),
                detailSlots = listOf(a(.28f, .66f, -4f), a(.48f, .65f, 3f), a(.68f, .66f, -3f), a(.36f, .80f, 4f), a(.60f, .81f, -4f))),
        ),
        IsleBuilding.CAMPFIRE to listOf(
            Scene(IsleBuilding.CAMPFIRE, 0, Backdrop.EVENING_BEACH, null, ground = meadow, groundTop = .36f,
                piece = SetPiece(R.drawable.lm_campfire, .5f, .72f, 1.0f), holder = Holder.FIRE_RING,
                caption = "Feuerkreis auf der Lichtung am Abend: die Gruppe sitzt auf Steinen und Hockern rund um das Feuer",
                props = listOf(Prop(R.drawable.n_rocks, .1f, .4f, .14f), Prop(R.drawable.n_pine, .9f, .4f, .12f)),
                space = Space.PIECE, slots = emptyList(), centre = a(.52f, .44f), ring = .36f to .74f),
            Scene(IsleBuilding.CAMPFIRE, 1, Backdrop.INTERIOR, R.drawable.fief_build_closeup,
                piece = SetPiece(R.drawable.p_table, .5f, .80f, .58f), holder = Holder.CRATE_TABLE,
                caption = "In der Treffhütte: die Gruppe auf Hockern um den Kerzentisch, Aushänge an der Bretterwand",
                props = listOf(Prop(R.drawable.n_crates, .1f, .97f, .22f)),
                space = Space.PIECE, slots = emptyList(), centre = a(.50f, .40f), ring = .64f to .56f),
            Scene(IsleBuilding.CAMPFIRE, 2, Backdrop.INTERIOR, R.drawable.bg_hall, piece = null, holder = Holder.BENCH_GROUP,
                caption = "Gemeindehaus: Sitzgruppen auf dem Dielenboden, jede Gruppe auf ihren Bänken",
                space = Space.ROOM, slots = listOf(a(.5f, .44f), a(.5f, .72f))),
            Scene(IsleBuilding.CAMPFIRE, 3, Backdrop.INTERIOR, R.drawable.bg_hall, piece = null, holder = Holder.ROUND_TABLE,
                caption = "Festhalle mit Girlanden: jede Gruppe an ihrem gedeckten Festtisch",
                space = Space.ROOM, slots = listOf(a(.33f, .44f), a(.67f, .56f), a(.42f, .76f))),
        ),
        IsleBuilding.HOUSE to listOf(
            Scene(IsleBuilding.HOUSE, 0, Backdrop.EVENING_BEACH, null, ground = meadow, groundTop = .5f,
                piece = SetPiece(R.drawable.decor_hammock, .5f, .86f, 1.0f), holder = Holder.KEEPSAKES,
                caption = "Schlafplatz: du in der Hängematte zwischen den Pfosten, deine Schätze liegen darunter im Gras",
                props = listOf(Prop(R.drawable.decor_palm, .14f, .52f, .26f), Prop(R.drawable.decor_campfire, .86f, .54f, .14f)),
                space = Space.PIECE, slots = listOf(a(.18f, .9f), a(.39f, .95f), a(.61f, .95f), a(.82f, .9f)),
                centre = a(.52f, .5f)),
            Scene(IsleBuilding.HOUSE, 1, Backdrop.INTERIOR, R.drawable.fief_treasury_closeup, piece = null, holder = Holder.CHEST_SHELF,
                caption = "Innen in der kleinen Holzhütte: deine Schätze in der offenen Truhe und auf dem Tisch",
                space = Space.ROOM, slots = listOf(a(.56f, .44f), a(.80f, .44f), a(.18f, .66f), a(.40f, .70f)),
                centre = a(.42f, .22f)),
            Scene(IsleBuilding.HOUSE, 2, Backdrop.INTERIOR, R.drawable.bg_house, piece = null, holder = Holder.PORCH,
                caption = "Wohnwinkel im Häuschen: dein Bild über der Fensterbank, Schätze auf der Bank und auf dem Tischchen am Sessel",
                props = listOf(Prop(R.drawable.p_table, .5f, .84f, .30f), Prop(R.drawable.p_cactus, .42f, .545f, .08f), Prop(R.drawable.decor_flowers, .58f, .55f, .13f)),
                space = Space.ROOM, slots = listOf(a(.27f, .47f), a(.40f, .69f), a(.60f, .69f), a(.73f, .47f)),
                centre = a(.50f, .26f)),
            Scene(IsleBuilding.HOUSE, 3, Backdrop.INTERIOR, R.drawable.bg_house, piece = null, holder = Holder.LIVING_ROOM,
                caption = "Wohnzimmer im Landhaus: dein Bild an der Wand, die Trophäen auf dem Wandbord",
                space = Space.ROOM, slots = listOf(a(.24f, .5f), a(.41f, .5f), a(.59f, .5f), a(.76f, .5f)), centre = a(.55f, .24f)),
        ),
        IsleBuilding.LIBRARY to listOf(
            Scene(IsleBuilding.LIBRARY, 0, Backdrop.DAY_BEACH, null, ground = meadow, groundTop = .7f,
                piece = SetPiece(R.drawable.n_crates, .5f, .99f, .62f), holder = Holder.PARCHMENT,
                caption = "Treibholzkisten auf der Lichtung: das Wörterbuch liegt aufgeschlagen auf der obersten Kiste",
                props = listOf(Prop(R.drawable.n_rowboat, .1f, .84f, .16f), Prop(R.drawable.n_rocks, .9f, .9f, .14f)),
                space = Space.SCREEN, slots = emptyList(), book = Region(.04f, .17f, .96f, .74f)),
            Scene(IsleBuilding.LIBRARY, 1, Backdrop.DAY_BEACH, null, ground = meadow, groundTop = .8f,
                piece = SetPiece(R.drawable.lm_library_ruin, .5f, 1.02f, 1.3f), holder = Holder.OPEN_BOOK,
                caption = "Unter dem Lesedach: das Buch liegt auf dem Leseplatz im Schatten der Zweige",
                space = Space.SCREEN, slots = emptyList(), book = Region(.06f, .44f, .94f, .9f)),
            Scene(IsleBuilding.LIBRARY, 2, Backdrop.INTERIOR, R.drawable.fief_treasury_closeup,
                piece = null, holder = Holder.SHELF_BOOK,
                caption = "In der Bücherstube: Bücher auf dem Wandbord am Fenster, das Wörterbuch offen auf dem Lesepult",
                props = listOf(Prop(R.drawable.p_books, .2f, .24f, .16f), Prop(R.drawable.p_books, .8f, .24f, .15f, -3f)),
                space = Space.SCREEN, slots = emptyList(), book = Region(.05f, .27f, .95f, .86f)),
            Scene(IsleBuilding.LIBRARY, 3, Backdrop.INTERIOR, R.drawable.bg_library, piece = null, holder = Holder.LEATHER_BOOK,
                caption = "Steinerne Bibliothek: das ledergebundene Buch liegt auf dem großen Lesetisch",
                space = Space.SCREEN, slots = emptyList(), book = Region(.05f, .17f, .95f, .82f)),
        ),
        IsleBuilding.LIGHTHOUSE to listOf(
            Scene(IsleBuilding.LIGHTHOUSE, 0, Backdrop.DAY_BEACH, null, ground = meadow, groundTop = .82f,
                piece = SetPiece(R.drawable.decor_palm, .17f, .97f, .62f), holder = Holder.ROWBOAT,
                caption = "Von der Aussichtspalme auf der Lichtung: die Boote deiner Freunde treiben in Sichtweite, Flaschenpost im Gras",
                space = Space.SCREEN,
                slots = listOf(a(0.55f, 0.30f), a(0.82f, 0.30f), a(0.55f, 0.46f), a(0.82f, 0.46f), a(0.55f, 0.62f), a(0.82f, 0.62f))),
            Scene(IsleBuilding.LIGHTHOUSE, 1, Backdrop.EVENING_BEACH, null, ground = sand, groundTop = .84f,
                piece = SetPiece(R.drawable.fief_tower_small, .16f, .97f, .42f), holder = Holder.SAILBOAT,
                caption = "Vom Ausguck übers Abendmeer: Segelboote deiner Freunde am Horizont",
                space = Space.SCREEN,
                slots = listOf(a(0.54f, 0.27f), a(0.80f, 0.27f), a(0.54f, 0.43f), a(0.80f, 0.43f), a(0.54f, 0.59f), a(0.80f, 0.59f))),
            Scene(IsleBuilding.LIGHTHOUSE, 2, Backdrop.NIGHT_SEA, null, ground = sand, groundTop = .84f,
                piece = SetPiece(R.drawable.lm_lighthouse, .16f, .98f, .46f), holder = Holder.SIGNAL_LIGHT,
                caption = "Leuchtfeuer bei Nacht: deine Freunde antworten mit Lichtsignalen von ihren Booten",
                props = listOf(Prop(R.drawable.n_rocks, .34f, .98f, .16f)),
                space = Space.SCREEN,
                slots = listOf(a(0.56f, 0.29f), a(0.82f, 0.29f), a(0.56f, 0.45f), a(0.82f, 0.45f), a(0.56f, 0.61f), a(0.82f, 0.61f))),
            Scene(IsleBuilding.LIGHTHOUSE, 3, Backdrop.INTERIOR, R.drawable.bg_lighthouse, piece = null, holder = Holder.PORTHOLE,
                caption = "Galerie des Leuchtturms: der Lichtkegel streicht übers Meer, deine Freunde in seinem Schein",
                space = Space.ROOM,
                slots = listOf(a(.30f, .38f), a(.52f, .34f), a(.72f, .40f), a(.34f, .53f), a(.64f, .53f), a(.48f, .68f))),
        ),
        IsleBuilding.HARBOUR to listOf(
            Scene(IsleBuilding.HARBOUR, 0, Backdrop.DAY_BEACH, null, ground = sand, groundTop = .6f,
                piece = SetPiece(R.drawable.fief_signpost, .36f, .94f, 1.0f), holder = Holder.NOTES_ON_POST,
                caption = "Strandtreff: Auftragszettel an den Pfahl gebunden, das Ruderboot mit Wimpel liegt daneben",
                props = listOf(Prop(R.drawable.n_rowboat, .82f, .95f, .3f), Prop(R.drawable.decor_flag, .9f, .78f, .12f)),
                space = Space.PIECE, slots = listOf(a(.36f, .18f, -6f), a(.84f, .44f, 5f), a(.50f, .76f, 4f))),
            Scene(IsleBuilding.HARBOUR, 1, Backdrop.DAY_BEACH, null, ground = null,
                piece = SetPiece(R.drawable.fief_jetty, .52f, .9f, 1.24f), holder = Holder.JETTY_NOTES,
                caption = "Bootssteg aus Treibholz: die Aufträge an die Pfähle genagelt, wo die Boote anlegen",
                // 7.9.13: three notes large enough for font 1.5 (title, partner, 48 dp buttons), more = pages
                space = Space.PIECE, slots = listOf(a(.32f, .18f, -5f), a(.68f, .44f, 4f), a(.36f, .72f, 5f))),
            Scene(IsleBuilding.HARBOUR, 2, Backdrop.EVENING_BEACH, null, ground = sand, groundTop = .76f,
                piece = SetPiece(R.drawable.b_board, .5f, .9f, 1.16f), holder = Holder.PLANK_BOARD,
                caption = "Fester Anleger mit Aushangtafel unter dem Dach, Laterne im Abendlicht",
                props = listOf(Prop(R.drawable.boat_sail, .14f, .3f, .14f)),
                // 7.9.13: two readable notes on the board per page (four 104 dp ones were too small), more = pages
                space = Space.PIECE, slots = listOf(a(.31f, .32f, -2f), a(.67f, .32f, 2f))),
            Scene(IsleBuilding.HARBOUR, 3, Backdrop.INTERIOR, R.drawable.bg_harbour, piece = null, holder = Holder.CORK_BOARD,
                caption = "Hafen mit großem Auftragsbrett am Steg",
                // 7.9.13: four readable notes on the cork board per page (six were too small), more = pages
                space = Space.ROOM, slots = listOf(a(.34f, .40f), a(.66f, .40f), a(.34f, .60f), a(.66f, .60f))),
        ),
    )

    fun of(b: IsleBuilding, level: Int): Scene = table.getValue(b)[level.coerceIn(0, 3)]
    val all: List<Scene> get() = IsleBuilding.entries.flatMap { table.getValue(it) }

    fun sky(backdrop: Backdrop): List<Color> = when (backdrop) {
        Backdrop.DAY_BEACH -> listOf(Color(0xFF8FCFEA), Color(0xFFCDEDF6))
        Backdrop.EVENING_BEACH -> listOf(Color(0xFFE7936A), Color(0xFFF6D3A0))
        Backdrop.NIGHT_SEA -> listOf(Color(0xFF0F1D3D), Color(0xFF2A4677))
        Backdrop.INTERIOR -> listOf(Color.Transparent, Color.Transparent)
    }
    /** The light on the painted ground and props: none by day, warm in the evening, moonlit blue at night. */
    fun light(backdrop: Backdrop): androidx.compose.ui.graphics.ColorFilter? = when (backdrop) {
        Backdrop.EVENING_BEACH -> androidx.compose.ui.graphics.ColorFilter.tint(Color(0x40B8502A), androidx.compose.ui.graphics.BlendMode.SrcAtop)
        Backdrop.NIGHT_SEA -> androidx.compose.ui.graphics.ColorFilter.tint(Color(0x88081733), androidx.compose.ui.graphics.BlendMode.SrcAtop)
        else -> null
    }
    /** A tint over the painted sea tile (none by day). */
    fun seaTint(backdrop: Backdrop): Color = when (backdrop) {
        Backdrop.DAY_BEACH, Backdrop.INTERIOR -> Color.Transparent
        Backdrop.EVENING_BEACH -> Color(0x55C2603A)
        Backdrop.NIGHT_SEA -> Color(0xAA081733)
    }
    /** Where the open sea starts on an outdoor stage (fraction of the screen height). */
    const val HORIZON = .16f
}

/**
 * Where everything of a function stage is on screen: [screen] = the whole place, [room] = the painted room's picture
 * (Crop), [piece] = the set piece's picture. [at] maps a scene anchor to its point on screen.
 */
internal class StageGeometry(val scene: StageScenes.Scene, val screen: SceneMap, val room: SceneMap?, val piece: SceneMap?) {
    /** Kept free at the bottom: the golden action, and the page arrows above it when the data has pages. */
    var reserve: Dp = 96.dp
    val space: SceneMap get() = when (scene.space) {
        StageScenes.Space.PIECE -> piece ?: screen
        StageScenes.Space.ROOM -> room ?: screen
        StageScenes.Space.SCREEN -> screen
    }
    fun at(a: StageScenes.Anchor): DpOffset = DpOffset(space.x(a.x), space.y(a.y))
    val slots: List<DpOffset> get() = scene.slots.map(::at)
    val detailSlots: List<DpOffset> get() = scene.detailSlots.map(::at)
    val centre: DpOffset? get() = scene.centre?.let(::at)
    /** The size of one data object: as big as the slots leave room for, readable, never overlapping its neighbour. */
    fun objectSize(slots: List<DpOffset>, max: Dp = 112.dp, min: Dp = 64.dp, aspect: Float = 1f): Dp {
        if (slots.size < 2) return max
        var best = Float.MAX_VALUE
        // [aspect] = height / width of one object: two neighbours may be close side by side or one above the other
        for (i in slots.indices) for (j in i + 1 until slots.size) {
            val dx = kotlin.math.abs((slots[i].x - slots[j].x).value); val dy = kotlin.math.abs((slots[i].y - slots[j].y).value)
            best = minOf(best, maxOf(dx, dy / aspect))
        }
        return (best * .94f).dp.coerceIn(min, max)
    }
}

/**
 * The stage itself: sky + sea + ground (outdoor), the painted room, the props and the big set piece,
 * then [content] with the geometry. No hero above a card: the place IS the picture the data lives in.
 */
@Composable
internal fun FunctionStage(scene: StageScenes.Scene, content: @Composable BoxScope.(StageGeometry) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().semantics {
        contentDescription = "Bühne: ${scene.caption}"
        stateDescription = scene.signature
    }) {
        val w = maxWidth; val h = maxHeight
        val screen = SceneMap(0.dp, 0.dp, w, h)
        val light = StageScenes.light(scene.backdrop)
        var room: SceneMap? = null
        when {
            scene.room != null -> {
                val painter = painterResource(scene.room)
                val aspect = painter.intrinsicSize.width / painter.intrinsicSize.height
                val byHeight = w / h < aspect
                val iw = if (byHeight) h * aspect else w
                val ih = if (byHeight) h else w / aspect
                room = SceneMap((w - iw) / 2, (h - ih) / 2, iw, ih)
                Image(painter, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            else -> {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(StageScenes.sky(scene.backdrop))))
                val sea = rememberSeaBrush()
                Canvas(Modifier.fillMaxSize()) {
                    val top = size.height * StageScenes.HORIZON
                    drawRect(sea, Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, size.height - top))
                    drawRect(StageScenes.seaTint(scene.backdrop), Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, size.height - top))
                    if (scene.backdrop == StageScenes.Backdrop.NIGHT_SEA) for (i in 0 until 26) {
                        val x = ((i * 73) % 100) / 100f * size.width; val y = ((i * 37) % 100) / 100f * top * .95f
                        drawCircle(Color(0xCCFFF3C4), radius = (1.2f + (i % 3)) * density, center = Offset(x, y))
                    }
                }
                scene.ground?.let { g ->
                    // the painted island ground the stage stands on, reaching under the bottom edge
                    Image(painterResource(g), null, Modifier.offset(x = w * -.15f, y = h * scene.groundTop).wrapContentSize(androidx.compose.ui.Alignment.TopStart, unbounded = true).size(width = w * 1.3f, height = h * (1.08f - scene.groundTop) * 1.25f),
                        contentScale = ContentScale.FillBounds, colorFilter = light)
                }
            }
        }
        scene.props.forEach { p -> StageProp(p, w, h, if (scene.outdoor) light else null) }
        var piece: SceneMap? = null
        scene.piece?.let { sp ->
            val painter = painterResource(sp.res)
            val pw = w * sp.width; val ph = pw * (painter.intrinsicSize.height / painter.intrinsicSize.width)
            piece = SceneMap(w * sp.cx - pw / 2, h * sp.foot - ph, pw, ph)
            Image(painter, "Bühnenbild: ${scene.caption.substringBefore(":")}", Modifier.offset(x = w * sp.cx - pw / 2, y = h * sp.foot - ph)
                // a set piece may be wider than the screen: never squeezed into it (the anchors use its real size)
                .wrapContentSize(androidx.compose.ui.Alignment.TopStart, unbounded = true).size(pw, ph),
                contentScale = ContentScale.FillBounds, colorFilter = if (scene.outdoor) light else null)
        }
        content(StageGeometry(scene, screen, room, piece))
    }
}

@Composable
private fun StageProp(p: StageScenes.Prop, w: Dp, h: Dp, light: androidx.compose.ui.graphics.ColorFilter?) {
    val pw = w * p.width
    Image(painterResource(p.res), null, Modifier.offset(x = w * p.x - pw / 2, y = h * p.y - pw).size(pw).rotate(p.rotation),
        contentScale = ContentScale.Fit, alignment = androidx.compose.ui.Alignment.BottomCenter, colorFilter = light)
}
