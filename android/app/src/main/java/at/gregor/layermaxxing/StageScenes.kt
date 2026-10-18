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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.unit.dp

/**
 * 7.9.11: what a building's own place looks like at each of its stages 0–3. 7.9.10 drew every stage in the same
 * finished room (the letter tree showed the post office's wooden cubbies). Now every (building, stage) has its own
 * stage configuration from the painted island assets: a backdrop (beach by day / evening / night, or the existing
 * painted room, kept only for the final stage), the stage's own painted piece as the hero (exactly the island
 * sprite of [IslandStages]), props, and the way the function's real data is held at that stage ([Holder]:
 * envelopes pinned to the tree, a mailbox, cubbies, ...). Names, functions, data and actions stay those of the
 * building; only the stage's look changes.
 */
internal object StageScenes {
    enum class Backdrop { DAY_BEACH, EVENING_BEACH, NIGHT_SEA, INTERIOR }
    /** How the building's data is shown at this stage. */
    enum class Holder {
        // Post
        PINNED_ENVELOPE, MAILBOX, CUBBY,
        // Leuchtturm
        ROWBOAT, SAILBOAT, PORTHOLE,
        // Bibliothek
        PARCHMENT, OPEN_BOOK, LEATHER_BOOK,
        // Gemeindehaus
        FIRE_RING, CRATE_TABLE, ROUND_TABLE,
        // Hafen
        NOTES_ON_POST, PLANK_BOARD, CORK_BOARD,
        // Mein Haus
        KEEPSAKES, CHEST_SHELF, PORCH, LIVING_ROOM,
    }
    /** A painted prop on the outdoor stage: centre x and foot y as fractions of the screen, width as fraction of its width. */
    data class Prop(val res: Int, val x: Float, val y: Float, val width: Float, val rotation: Float = 0f)
    data class Scene(
        val building: IsleBuilding, val level: Int, val backdrop: Backdrop,
        /** The painted room of the final stage (null = outdoor stage built from island assets). */
        val interior: Int?,
        /** Painted ground the outdoor stage stands on (sand bank or meadow island). */
        val ground: Int?,
        val props: List<Prop>,
        val holder: Holder,
        /** Painted panel behind the data on an outdoor stage. */
        val panel: Int?,
        /** What one sees (accessibility, review). */
        val caption: String,
    ) {
        val stage: IslandStages.Stage get() = IslandStages.of(building, level)
        /** The stage's own painted piece, the same as on the island. */
        val hero: Int get() = stage.res
        val outdoor: Boolean get() = interior == null
        /** Night and evening stages need light ink for titles drawn straight onto the scene. */
        val dark: Boolean get() = backdrop == Backdrop.NIGHT_SEA || backdrop == Backdrop.INTERIOR && building == IsleBuilding.LIGHTHOUSE
        /** Everything that makes the stage look like it does, for tests and the review. */
        val signature: String get() = listOf("$building-$level", backdrop, interior ?: hero, ground ?: "-", holder, panel ?: "-",
            props.joinToString("+") { "${it.res}" }).joinToString(" · ")
    }

    /** Where the data starts on an outdoor stage (fraction of the screen height): below the scene with the hero. */
    const val CONTENT_TOP = .47f
    /** Foot line and width of the hero on an outdoor stage. */
    const val HERO_FOOT = .45f
    const val HERO_WIDTH = .44f

    private val sand = R.drawable.terrain_sand
    private val meadow = R.drawable.terrain_home
    private val parchment = R.drawable.fief_parchment_card

    private fun outdoor(b: IsleBuilding, level: Int, backdrop: Backdrop, ground: Int, holder: Holder, caption: String, vararg props: Prop, panel: Int? = parchment) =
        Scene(b, level, backdrop, null, ground, props.toList(), holder, panel, caption)
    private fun room(b: IsleBuilding, holder: Holder, caption: String) =
        Scene(b, 3, Backdrop.INTERIOR, b.background(), null, emptyList(), holder, null, caption)

    private val table: Map<IsleBuilding, List<Scene>> = mapOf(
        IsleBuilding.POST to listOf(
            outdoor(IsleBuilding.POST, 0, Backdrop.DAY_BEACH, sand, Holder.PINNED_ENVELOPE, "Briefbaum am Strand, Briefe hängen an Nadeln am Stamm",
                Prop(R.drawable.p_env, .37f, .31f, .075f, -14f), Prop(R.drawable.p_env, .62f, .28f, .07f, 11f),
                Prop(R.drawable.n_rocks, .18f, .46f, .16f), Prop(R.drawable.n_flowerbush, .83f, .46f, .14f)),
            outdoor(IsleBuilding.POST, 1, Backdrop.DAY_BEACH, meadow, Holder.MAILBOX, "Postkasten mit Dach auf der Wiese, versiegelte Briefe davor",
                Prop(R.drawable.fief_env_sealed, .78f, .455f, .11f, 6f), Prop(R.drawable.n_bush, .2f, .46f, .15f), Prop(R.drawable.decor_lantern, .9f, .43f, .06f)),
            outdoor(IsleBuilding.POST, 2, Backdrop.EVENING_BEACH, meadow, Holder.CUBBY, "Kleine Poststube mit Fächern im Abendlicht",
                Prop(R.drawable.p_cubby, .8f, .455f, .13f), Prop(R.drawable.fief_env_ready, .2f, .455f, .11f, -6f), Prop(R.drawable.decor_flowers, .66f, .47f, .08f)),
            room(IsleBuilding.POST, Holder.CUBBY, "Großes Postamt mit Schalter und Fächern"),
        ),
        IsleBuilding.LIGHTHOUSE to listOf(
            outdoor(IsleBuilding.LIGHTHOUSE, 0, Backdrop.DAY_BEACH, sand, Holder.ROWBOAT, "Aussichtspalme am Strand, Ruderboote in Sichtweite",
                Prop(R.drawable.boat_row, .84f, .2f, .13f), Prop(R.drawable.n_rocks, .2f, .46f, .15f)),
            outdoor(IsleBuilding.LIGHTHOUSE, 1, Backdrop.EVENING_BEACH, sand, Holder.SAILBOAT, "Hölzerner Ausguck am Abend, Segelboote am Horizont",
                Prop(R.drawable.boat_sail, .16f, .21f, .1f), Prop(R.drawable.n_pine, .82f, .46f, .1f)),
            outdoor(IsleBuilding.LIGHTHOUSE, 2, Backdrop.NIGHT_SEA, sand, Holder.PORTHOLE, "Leuchtfeuer auf den Felsen bei Nacht, das Licht kreist",
                Prop(R.drawable.boat_sail, .84f, .2f, .07f), Prop(R.drawable.n_rocks, .18f, .46f, .15f), panel = null),
            room(IsleBuilding.LIGHTHOUSE, Holder.PORTHOLE, "Galerie des großen Leuchtturms, der Lichtkegel streicht über das Meer"),
        ),
        IsleBuilding.LIBRARY to listOf(
            outdoor(IsleBuilding.LIBRARY, 0, Backdrop.DAY_BEACH, sand, Holder.PARCHMENT, "Treibholzkisten am Strand, ein Buch liegt darauf",
                Prop(R.drawable.p_book, .26f, .46f, .13f, -8f), Prop(R.drawable.n_rowboat, .84f, .22f, .12f), panel = null),
            outdoor(IsleBuilding.LIBRARY, 1, Backdrop.DAY_BEACH, meadow, Holder.OPEN_BOOK, "Lesedach aus Zweigen über dem Bücherregal",
                Prop(R.drawable.p_books, .8f, .46f, .12f), Prop(R.drawable.decor_lantern, .17f, .44f, .06f), panel = null),
            outdoor(IsleBuilding.LIBRARY, 2, Backdrop.EVENING_BEACH, meadow, Holder.LEATHER_BOOK, "Bücherstube mit Lampe am Abend",
                Prop(R.drawable.p_books, .82f, .46f, .12f), Prop(R.drawable.p_quill, .18f, .46f, .08f, -10f), Prop(R.drawable.decor_lantern, .3f, .45f, .05f), panel = null),
            room(IsleBuilding.LIBRARY, Holder.LEATHER_BOOK, "Steinerne Bibliothek mit Regalen und Lesetisch"),
        ),
        IsleBuilding.CAMPFIRE to listOf(
            outdoor(IsleBuilding.CAMPFIRE, 0, Backdrop.EVENING_BEACH, sand, Holder.FIRE_RING, "Feuerkreis aus Steinen am Abendstrand",
                Prop(R.drawable.p_stool, .22f, .46f, .08f), Prop(R.drawable.p_stool, .78f, .46f, .08f), Prop(R.drawable.n_rocks, .9f, .43f, .1f)),
            outdoor(IsleBuilding.CAMPFIRE, 1, Backdrop.EVENING_BEACH, meadow, Holder.CRATE_TABLE, "Holzhütte für Treffen, Kisten als Tisch",
                Prop(R.drawable.n_crates, .8f, .46f, .12f), Prop(R.drawable.p_stool, .2f, .46f, .07f)),
            outdoor(IsleBuilding.CAMPFIRE, 2, Backdrop.DAY_BEACH, meadow, Holder.ROUND_TABLE, "Weißes Gemeindehaus, runde Tische davor",
                Prop(R.drawable.p_table, .81f, .46f, .13f), Prop(R.drawable.decor_bench, .19f, .46f, .13f)),
            room(IsleBuilding.CAMPFIRE, Holder.ROUND_TABLE, "Festhalle mit Kamin und runden Tischen"),
        ),
        IsleBuilding.HARBOUR to listOf(
            outdoor(IsleBuilding.HARBOUR, 0, Backdrop.DAY_BEACH, sand, Holder.NOTES_ON_POST, "Ruderboot mit Wimpel am Strand, Zettel an einem Pfahl",
                Prop(R.drawable.fief_signpost, .2f, .46f, .13f), Prop(R.drawable.p_bottle, .84f, .46f, .06f, -30f)),
            outdoor(IsleBuilding.HARBOUR, 1, Backdrop.DAY_BEACH, sand, Holder.PLANK_BOARD, "Einfacher Steg aus Treibholz mit Brett",
                Prop(R.drawable.fief_cy_board, .2f, .46f, .15f), Prop(R.drawable.n_rowboat, .84f, .24f, .12f), panel = R.drawable.fief_board_closeup),
            outdoor(IsleBuilding.HARBOUR, 2, Backdrop.EVENING_BEACH, sand, Holder.PLANK_BOARD, "Fester Anleger mit Pollern und Aushangbrett am Abend",
                Prop(R.drawable.b_board, .8f, .46f, .17f), Prop(R.drawable.boat_sail, .17f, .22f, .09f), panel = R.drawable.fief_board_closeup),
            room(IsleBuilding.HARBOUR, Holder.CORK_BOARD, "Hafen mit großer Korktafel am Steg"),
        ),
        IsleBuilding.HOUSE to listOf(
            outdoor(IsleBuilding.HOUSE, 0, Backdrop.EVENING_BEACH, sand, Holder.KEEPSAKES, "Hängematte zwischen Pfosten, Schätze im Sand",
                Prop(R.drawable.decor_palm, .17f, .45f, .17f), Prop(R.drawable.decor_campfire, .82f, .46f, .1f)),
            outdoor(IsleBuilding.HOUSE, 1, Backdrop.DAY_BEACH, sand, Holder.CHEST_SHELF, "Kleine Blockhütte am Strand mit Truhe davor",
                Prop(R.drawable.fief_cy_chest, .81f, .46f, .13f), Prop(R.drawable.n_bush, .18f, .46f, .14f)),
            outdoor(IsleBuilding.HOUSE, 2, Backdrop.DAY_BEACH, meadow, Holder.PORCH, "Häuschen mit rotem Dach, Bild auf der Veranda, Bank und Blumen",
                Prop(R.drawable.decor_bench, .82f, .46f, .13f), Prop(R.drawable.decor_flowers, .18f, .47f, .1f)),
            room(IsleBuilding.HOUSE, Holder.LIVING_ROOM, "Wohnstube des Landhauses mit Bild und Regal"),
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
        Backdrop.EVENING_BEACH -> androidx.compose.ui.graphics.ColorFilter.tint(Color(0x59B8502A), androidx.compose.ui.graphics.BlendMode.SrcAtop)
        Backdrop.NIGHT_SEA -> androidx.compose.ui.graphics.ColorFilter.tint(Color(0x99081733), androidx.compose.ui.graphics.BlendMode.SrcAtop)
        else -> null
    }
    /** A tint over the painted sea tile (none by day). */
    fun seaTint(backdrop: Backdrop): Color = when (backdrop) {
        Backdrop.DAY_BEACH, Backdrop.INTERIOR -> Color.Transparent
        Backdrop.EVENING_BEACH -> Color(0x55C2603A)
        Backdrop.NIGHT_SEA -> Color(0xAA081733)
    }
}

/**
 * The outdoor stage: sky, the painted sea tile, the painted island ground, props and the stage's own hero piece
 * (drawn through [PaintedIslandPlace], exactly like on the island), then the panel for the data. [content] gets a
 * [SceneMap] of the whole screen.
 */
@Composable
internal fun StageBackdrop(scene: StageScenes.Scene, content: @Composable BoxScope.(SceneMap) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().semantics {
        contentDescription = "Bühne: ${scene.caption}"
        stateDescription = scene.signature
    }) {
        val w = maxWidth; val h = maxHeight
        val map = SceneMap(0.dp, 0.dp, w, h)
        val seaTop = h * .16f
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(StageScenes.sky(scene.backdrop))))
        val sea = rememberSeaBrush()
        Canvas(Modifier.fillMaxSize()) {
            val top = seaTop.toPx()
            drawRect(sea, Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, size.height - top))
            drawRect(StageScenes.seaTint(scene.backdrop), Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, size.height - top))
            if (scene.backdrop == StageScenes.Backdrop.NIGHT_SEA) for (i in 0 until 26) {
                // a few fixed stars over the night sea
                val x = ((i * 73) % 100) / 100f * size.width; val y = ((i * 37) % 100) / 100f * top * .95f
                drawCircle(Color(0xCCFFF3C4), radius = (1.2f + (i % 3)) * density, center = Offset(x, y))
            }
        }
        // The painted island ground the stage stands on.
        val light = StageScenes.light(scene.backdrop)
        scene.ground?.let { g ->
            Image(painterResource(g), null, Modifier.offset(x = w * -.08f, y = h * .19f).size(width = w * 1.16f, height = h * .36f),
                contentScale = ContentScale.FillBounds, colorFilter = light)
        }
        // Props behind and beside the hero, foot on the ground line, in the same light.
        scene.props.forEach { p -> StageProp(p, w, h, light) }
        val heroW = w * StageScenes.HERO_WIDTH
        PaintedIslandPlace(scene.building, scene.level, scene.hero,
            Modifier.offset(x = (w - heroW) / 2, y = h * StageScenes.HERO_FOOT - heroW).size(heroW),
            description = "Bühnenbild: ${scene.stage.name}")
        // The data lies on a painted panel below the scene (parchment, notice board) so it reads on every backdrop.
        scene.panel?.let { res ->
            Image(painterResource(res), null,
                Modifier.offset(x = 8.dp, y = h * (StageScenes.CONTENT_TOP - .015f)).size(width = w - 16.dp, height = h * (1f - StageScenes.CONTENT_TOP) - 70.dp).alpha(.97f),
                contentScale = if (res == R.drawable.fief_parchment_card) ContentScale.FillBounds else ContentScale.Crop)
        }
        content(map)
    }
}

@Composable
private fun StageProp(p: StageScenes.Prop, w: Dp, h: Dp, light: androidx.compose.ui.graphics.ColorFilter?) {
    val pw = w * p.width
    Image(painterResource(p.res), null, Modifier.offset(x = w * p.x - pw / 2, y = h * p.y - pw).size(pw).rotate(p.rotation),
        contentScale = ContentScale.Fit, alignment = androidx.compose.ui.Alignment.BottomCenter, colorFilter = light)
}
