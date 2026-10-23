package at.gregor.layermaxxing

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * 7.9.14 (style E): every building × stage is ONE painted place (scripts/style_e_assets.py: e_<b>_<s>_scene.webp,
 * 900 × 1350, the generated detail picture of that stage) – the furniture, the tree, the drawer, the fire are in the
 * picture. Nothing static is painted over it any more (7.9.12/7.9.13 laid workshop props, tables and benches over old
 * backgrounds). The real data is dynamic UI on the SURFACES the picture was painted for: [Scene.surfaces] are rectangles
 * in the picture's own space (fractions of its width and height), measured on the actual pictures (docs/style-e-scenes.md),
 * not the plan values of the image brief. The picture fills the screen (Crop); on a phone only about the middle 69 %
 * (411 × 891 dp) to 84 % (360 × 640 dp) of its width are visible, so the views keep their objects on screen
 * ([StageGeometry.onScreen]).
 */
internal object StageScenes {
    /** How the building's data is held at this stage. */
    enum class Holder {
        // Post
        NAILED_TO_TRUNK, MAIL_DRAWER, DESK_DRAWER, COUNTER_TRAY,
        // Freunde
        PALM_VIEW, LOOKOUT_DECK, SIGNAL_WALL, LANTERN_GALLERY,
        // Wörterbuch
        STUMP_BOOK, SHELTERED_BOOK, STUDY_BOOK, LIBRARY_BOOK,
        // Gruppen
        CAMPFIRE_RING, OPEN_TABLE, HEARTH_TABLE, GLASSHOUSE_TABLE,
        // Hafen
        LANDING_SIGN, JETTY_BOARD, SHED_BOARD, HARBOUR_BOARD,
        // Zuhause
        NIGHT_CAMP, SHELTER, CABIN_ROOM, MANOR_DESK,
    }
    /** A point in the picture's own space (fractions of its width and height). */
    data class Anchor(val x: Float, val y: Float, val tilt: Float = 0f)
    /** A rectangle in the picture's own space. */
    data class Region(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
        val centre: Anchor get() = Anchor((left + right) / 2, (top + bottom) / 2)
    }

    /** The named surfaces a stage offers; a view uses the ones of its building. */
    enum class Surface {
        // Post: the status slider, the person filter (stamps, cubbies, name tags), the letters' place, the filter plate
        TABS, PEOPLE, LETTERS, PLATE,
        // Zuhause: picture of me, name, notes, the counters
        AVATAR, NAME, NOTES, COUNTERS,
        // Freunde: where friends are seen; Wörterbuch: the book, its register; Gruppen: the group's sign, the picker
        SEA, BOOK, REGISTER, SIGN, PICKER,
        // Hafen: the notice surface for quests
        BOARD,
    }

    data class Scene(
        val building: IsleBuilding, val level: Int,
        /** The painted place (the whole screen, Crop). */
        val picture: Int,
        val holder: Holder,
        val caption: String,
        val surfaces: Map<Surface, Region>,
        /** Gruppen: the fire / table the focus group sits around, and the seat ring (radii in picture fractions). */
        val centre: Anchor? = null,
        val ring: Pair<Float, Float> = .30f to .12f,
        /** Dark picture (night): titles straight on it use light ink. */
        val night: Boolean = false,
    ) {
        val stage: IslandStages.Stage get() = IslandStages.of(building, level)
        /** The stage's island piece (header picture), the same as on the island. */
        val hero: Int get() = stage.art
        fun surface(s: Surface): Region? = surfaces[s]
        /**
         * stage-ui-redesign: the stage is its own place without the shared shell (no header sign, golden action, story
         * buttons): POST 0, the letter tree (PostTree.kt). The others still use the shell until their own design.
         */
        val clean: Boolean get() = building == IsleBuilding.POST
        /** Everything that makes the stage look and work like it does, for tests and the review. */
        val signature: String get() = listOf("$building-$level", picture, holder,
            surfaces.entries.sortedBy { it.key }.joinToString("+") { it.key.name }).joinToString(" · ")
    }

    private fun r(l: Float, t: Float, rr: Float, b: Float) = Region(l, t, rr, b)
    private fun a(x: Float, y: Float) = Anchor(x, y)
    private val S = Surface.entries.associateBy { it.name }

    private val table: Map<IsleBuilding, List<Scene>> = mapOf(
        IsleBuilding.POST to listOf(
            Scene(IsleBuilding.POST, 0, R.drawable.e_post_0_scene, Holder.NAILED_TO_TRUNK,
                "Briefbaum: die Briefe sind an den Stamm genagelt, der Messinggurt mit Schieber zeigt, welche Briefe gerade hängen",
                mapOf(Surface.TABS to r(.21f, .185f, .80f, .255f), Surface.PEOPLE to r(.21f, .262f, .80f, .33f),
                    Surface.LETTERS to r(.20f, .335f, .82f, .76f))),
            Scene(IsleBuilding.POST, 1, R.drawable.e_post_1_scene, Holder.MAIL_DRAWER,
                "Briefschub am Baum: im Deckel die Marken deiner Freunde, im Schub die Briefe, vorne der Messingschieber",
                mapOf(Surface.PEOPLE to r(.33f, .12f, .74f, .28f), Surface.LETTERS to r(.35f, .305f, .86f, .585f),
                    Surface.PLATE to r(.41f, .628f, .59f, .661f), Surface.TABS to r(.30f, .662f, .88f, .748f))),
            Scene(IsleBuilding.POST, 2, R.drawable.e_post_2_scene, Holder.DESK_DRAWER,
                "Briefstube: ein Fach für jeden Freund, die Briefe auf dem Pult und im Schub, der Schieber an der Schublade",
                mapOf(Surface.PEOPLE to r(.06f, .10f, .79f, .37f), Surface.PLATE to r(.12f, .427f, .77f, .464f),
                    Surface.LETTERS to r(.09f, .458f, .88f, .725f), Surface.TABS to r(.23f, .728f, .79f, .785f))),
            Scene(IsleBuilding.POST, 3, R.drawable.e_post_3_scene, Holder.COUNTER_TRAY,
                "Postamt: die Fächerwand hinter dem Schalter, Schieber und Ablage auf dem Tresen",
                mapOf(Surface.PEOPLE to r(.05f, .09f, .62f, .31f), Surface.PLATE to r(.26f, .555f, .74f, .59f),
                    Surface.TABS to r(.24f, .578f, .81f, .635f), Surface.LETTERS to r(.11f, .640f, .93f, .865f))),
        ),
        IsleBuilding.HOUSE to listOf(
            Scene(IsleBuilding.HOUSE, 0, R.drawable.e_house_0_scene, Holder.NIGHT_CAMP,
                "Nachtlager unter der alten Buche: die Holzscheibe am Ast trägt dein Bild, Anhänger, Heft und Zettel an der Wurzel sind deine",
                mapOf(Surface.AVATAR to r(.355f, .194f, .565f, .328f), Surface.NAME to r(.18f, .455f, .42f, .52f),
                    Surface.NOTES to r(.28f, .53f, .52f, .61f), Surface.COUNTERS to r(.70f, .43f, .96f, .535f)), night = true),
            Scene(IsleBuilding.HOUSE, 1, R.drawable.e_house_1_scene, Holder.SHELTER,
                "Unterschlupf an der Buche: die Scheibe unter dem Dach, die Zettel am Querbalken, die Tasche am Stamm",
                mapOf(Surface.AVATAR to r(.38f, .186f, .50f, .279f), Surface.COUNTERS to r(.33f, .218f, .55f, .275f),
                    Surface.NAME to r(.16f, .37f, .38f, .425f), Surface.NOTES to r(.70f, .40f, .97f, .475f)), night = true),
            Scene(IsleBuilding.HOUSE, 2, R.drawable.e_house_2_scene, Holder.CABIN_ROOM,
                "Holzhütte: dein Bild im Rahmen an der Wand, die Karten auf dem Bord, das Heft auf dem Tisch",
                mapOf(Surface.AVATAR to r(.139f, .233f, .303f, .345f), Surface.NAME to r(.15f, .368f, .30f, .398f),
                    Surface.COUNTERS to r(.64f, .295f, .89f, .35f), Surface.NOTES to r(.44f, .43f, .68f, .49f))),
            Scene(IsleBuilding.HOUSE, 3, R.drawable.e_house_3_scene, Holder.MANOR_DESK,
                "Landhaus: am Schreibtisch dein Bild im Rahmen, das Blatt mit deinen Angaben, die Karten im Halter",
                mapOf(Surface.AVATAR to r(.082f, .40f, .214f, .51f), Surface.NOTES to r(.27f, .505f, .76f, .605f),
                    Surface.COUNTERS to r(.68f, .395f, .88f, .485f), Surface.NAME to r(.27f, .455f, .62f, .50f))),
        ),
        IsleBuilding.LIGHTHOUSE to listOf(
            Scene(IsleBuilding.LIGHTHOUSE, 0, R.drawable.e_lighthouse_0_scene, Holder.PALM_VIEW,
                "Aussichtspalme am Rand: oben im Wipfel, unten das Meer – wer zurückwinkt, erscheint dort draußen",
                mapOf(Surface.SEA to r(.18f, .30f, .86f, .58f))),
            Scene(IsleBuilding.LIGHTHOUSE, 1, R.drawable.e_lighthouse_1_scene, Holder.LOOKOUT_DECK,
                "Ausguck an der Küste: das Fernrohr auf dem Dreibein, rechts das Abendmeer mit deinen Freunden",
                mapOf(Surface.SEA to r(.38f, .30f, .92f, .62f))),
            Scene(IsleBuilding.LIGHTHOUSE, 2, R.drawable.e_lighthouse_2_scene, Holder.SIGNAL_WALL,
                "Signalstation: über die Steinmauer aufs Meer, die Flaggen am Mast melden die Anfragen",
                mapOf(Surface.SEA to r(.14f, .38f, .80f, .66f))),
            Scene(IsleBuilding.LIGHTHOUSE, 3, R.drawable.e_lighthouse_3_scene, Holder.LANTERN_GALLERY,
                "Galerie des Leuchtturms: der Strahl liegt auf dem Meer, deine Freunde in seinem Schein",
                mapOf(Surface.SEA to r(.14f, .34f, .84f, .64f)), night = true),
        ),
        IsleBuilding.LIBRARY to listOf(
            Scene(IsleBuilding.LIBRARY, 0, R.drawable.e_library_0_scene, Holder.STUMP_BOOK,
                "Schreibplatz auf dem Baumstumpf: das Buch liegt offen, seine vier Bänder sind die Kategorien",
                mapOf(Surface.BOOK to r(.06f, .355f, .93f, .60f), Surface.REGISTER to r(.28f, .598f, .70f, .70f))),
            Scene(IsleBuilding.LIBRARY, 1, R.drawable.e_library_1_scene, Holder.SHELTERED_BOOK,
                "Geschützter Schreibplatz: unter dem Rindendach liegt das Buch, die Bänder hängen über die Tischkante",
                mapOf(Surface.BOOK to r(.11f, .53f, .81f, .695f), Surface.REGISTER to r(.30f, .694f, .64f, .785f))),
            Scene(IsleBuilding.LIBRARY, 2, R.drawable.e_library_2_scene, Holder.STUDY_BOOK,
                "Schreibstube: das Buch auf dem Tisch am Fenster, rechts die Karteischubladen der Kategorien",
                mapOf(Surface.BOOK to r(.06f, .61f, .66f, .755f), Surface.REGISTER to r(.68f, .27f, .97f, .57f))),
            Scene(IsleBuilding.LIBRARY, 3, R.drawable.e_library_3_scene, Holder.LIBRARY_BOOK,
                "Bibliothek: das große Buch unter der grünen Lampe, die Banner über den Regalen sind die Kategorien",
                mapOf(Surface.BOOK to r(.06f, .60f, .94f, .70f), Surface.REGISTER to r(.14f, .095f, .96f, .185f))),
        ),
        IsleBuilding.CAMPFIRE to listOf(
            Scene(IsleBuilding.CAMPFIRE, 0, R.drawable.e_campfire_0_scene, Holder.CAMPFIRE_RING,
                "Lagerfeuer auf der Lichtung: eine Runde sitzt auf Stämmen und Stümpfen ums Feuer, der Wimpel trägt ihren Namen",
                mapOf(Surface.SIGN to r(.34f, .19f, .58f, .34f), Surface.PICKER to r(.08f, .76f, .92f, .835f)),
                centre = a(.48f, .555f), ring = .31f to .135f, night = true),
            Scene(IsleBuilding.CAMPFIRE, 1, R.drawable.e_campfire_1_scene, Holder.OPEN_TABLE,
                "Offener Treffplatz: unter dem Dach ein runder Tisch mit Hockern, draußen glimmt das erste Feuer",
                mapOf(Surface.SIGN to r(.32f, .225f, .70f, .29f), Surface.PICKER to r(.08f, .79f, .92f, .865f)),
                centre = a(.53f, .585f), ring = .32f to .125f, night = true),
            Scene(IsleBuilding.CAMPFIRE, 2, R.drawable.e_campfire_2_scene, Holder.HEARTH_TABLE,
                "Versammlungshaus: hinten der Kamin, davor der runde Tisch mit Stühlen – eine Runde ohne Trennwände",
                mapOf(Surface.SIGN to r(.28f, .245f, .72f, .30f), Surface.PICKER to r(.08f, .80f, .92f, .875f)),
                centre = a(.47f, .62f), ring = .33f to .115f, night = true),
            Scene(IsleBuilding.CAMPFIRE, 3, R.drawable.e_campfire_3_scene, Holder.GLASSHOUSE_TABLE,
                "Glashaus am ersten Feuer: unter Glas um den alten Baum, Ofen und Laternen, die Runde am Tisch",
                mapOf(Surface.SIGN to r(.28f, .285f, .72f, .345f), Surface.PICKER to r(.08f, .82f, .92f, .89f)),
                centre = a(.53f, .585f), ring = .33f to .12f, night = true),
        ),
        IsleBuilding.HARBOUR to listOf(
            Scene(IsleBuilding.HARBOUR, 0, R.drawable.e_harbour_0_scene, Holder.LANDING_SIGN,
                "Anlegestelle an der Kiesbucht: der Steg reicht vom Ufer ins Wasser, am Treibholzbrett hängen die Quests",
                mapOf(Surface.BOARD to r(.30f, .34f, .96f, .66f))),
            Scene(IsleBuilding.HARBOUR, 1, R.drawable.e_harbour_1_scene, Holder.JETTY_BOARD,
                "Bootssteg mit Anschlagtafel unter dem Dach: der Steg beginnt am Ufer, an der Tafel hängen die Quests",
                mapOf(Surface.BOARD to r(.15f, .27f, .66f, .58f))),
            Scene(IsleBuilding.HARBOUR, 2, R.drawable.e_harbour_2_scene, Holder.SHED_BOARD,
                "Anleger mit Schuppen: am Brett der Schuppenwand hängen die Quests, die Laterne brennt am Steg",
                mapOf(Surface.BOARD to r(.50f, .28f, .97f, .62f))),
            Scene(IsleBuilding.HARBOUR, 3, R.drawable.e_harbour_3_scene, Holder.HARBOUR_BOARD,
                "Hafen: die große Korktafel am Kai, dahinter die Stege, an denen die Briefboote festmachen",
                mapOf(Surface.BOARD to r(.27f, .38f, .80f, .64f))),
        ),
    )

    fun of(b: IsleBuilding, level: Int): Scene = table.getValue(b)[level.coerceIn(0, 3)]
    val all: List<Scene> get() = IsleBuilding.entries.flatMap { table.getValue(it) }

    /**
     * 7.9.14: where the picture's left edge lies on a screen [w] wide when the picture is [iw] wide (Crop by height, any
     * unit): centred, but panned sideways as little as needed so every surface of the scene lies whole on screen with
     * [margin] to spare (the frame on the landhaus desk, the stamps of the mailbox). When the surfaces together are wider
     * than the screen, their middle is centred. Never beyond the picture's edges.
     */
    fun panLeft(scene: Scene, w: Float, iw: Float, margin: Float): Float {
        if (iw <= w) return (w - iw) / 2
        val centred = (w - iw) / 2
        fun fit(regions: Collection<Region>): Float? {
            val l = regions.minOf { it.left } * iw; val r = regions.maxOf { it.right } * iw
            return if (r - l <= w - 2 * margin) centred.coerceIn(margin - l, w - margin - r) else null
        }
        // all surfaces if they fit; else the surface the picture is anchored on (my picture's frame, the book, the
        // letters' place …): the others are moved on screen by the views ([StageGeometry.onScreen])
        val o = fit(scene.surfaces.values) ?: scene.surface(PINNED.getValue(scene.building))?.let { fit(listOf(it)) } ?: centred
        return o.coerceIn(w - iw, 0f)
    }
    /**
     * 1011: where the picture lies (left, top, width, height; same unit as the arguments) when the view looks closely at
     * [r]: zoomed (1..2.4×) until the region fills the room between the header and the bottom reserve, centred there,
     * and never so far that the screen shows past the picture's edge.
     */
    fun focus(r: Region, restLeft: Float, restTop: Float, restW: Float, restH: Float, w: Float, h: Float, header: Float, reserve: Float, maxZoom: Float = 2.4f): FloatArray {
        val top = header + 8f; val bottom = h - reserve - 8f
        // as wide as the screen; a flat, wide object (a tray, a slider) is still brought 1.5× closer and cut at the sides
        val zW = (w - 24f) / (r.width * restW); val zH = (bottom - top) / (r.height * restH)
        val z = minOf(zH, maxOf(zW, 1.5f), maxZoom).coerceAtLeast(1f)
        val nw = restW * z; val nh = restH * z
        val left = (w / 2 - (r.left + r.width / 2) * nw).coerceIn(w - nw, 0f).let { if (nw <= w) restLeft else it }
        val t = ((top + bottom) / 2 - (r.top + r.height / 2) * nh).coerceIn(h - nh, 0f).let { if (nh <= h) restTop else it }
        return floatArrayOf(left, t, nw, nh)
    }
    /** The surface that must lie exactly on its painted place when not all of them fit on screen. */
    private val PINNED = mapOf(IsleBuilding.HOUSE to Surface.AVATAR, IsleBuilding.POST to Surface.LETTERS, IsleBuilding.LIGHTHOUSE to Surface.SEA,
        IsleBuilding.LIBRARY to Surface.BOOK, IsleBuilding.CAMPFIRE to Surface.SIGN, IsleBuilding.HARBOUR to Surface.BOARD)
}

/** The painted place on screen (Crop): maps picture fractions to dp. */
internal class StageGeometry(val scene: StageScenes.Scene, val screen: SceneMap, val picture: SceneMap) {
    /** Kept free at the bottom: the golden action and the story things. */
    var reserve: Dp = 104.dp
    /** Kept free at the top: the shared header (back button, name sign, stage strip). */
    val header: Dp = 104.dp
    fun at(a: StageScenes.Anchor): DpOffset = DpOffset(picture.x(a.x), picture.y(a.y))
    val centre: DpOffset? get() = scene.centre?.let(::at)
    /** A surface on screen: (left, top, width, height) in dp, unclipped. */
    fun rect(s: StageScenes.Surface): DpRect? = scene.surface(s)?.let { r ->
        DpRect(picture.x(r.left), picture.y(r.top), picture.w * r.width, picture.h * r.height)
    }
    /**
     * [r] moved and cut so it lies on screen between the header and the reserve, at least [minW] wide (centred on the
     * surface when it has to grow): a surface at the cropped edge of the picture keeps its object on screen.
     */
    fun onScreen(r: DpRect, minW: Dp = 0.dp, margin: Dp = 8.dp): DpRect {
        val w = minOf(maxOf(r.w, minW), screen.w - margin * 2)
        var left = r.left + (r.w - w) / 2
        left = left.coerceIn(margin, screen.w - margin - w)
        val top = r.top.coerceAtLeast(header)
        val bottom = (r.top + r.h).coerceAtMost(screen.h - reserve)
        return DpRect(left, top, w, (bottom - top).coerceAtLeast(0.dp))
    }
}

internal data class DpRect(val left: Dp, val top: Dp, val w: Dp, val h: Dp) {
    val right: Dp get() = left + w
    val bottom: Dp get() = top + h
}

/**
 * 7.9.14: the scene pictures are 900 × 1350 (≈ 4.9 MB as ARGB). One is decoded when a place opens (inPreferredConfig
 * RGB_565: 2.4 MB, the pictures have no alpha) and only the one on screen (and one close-up of it) is kept: no cache of all 24 (the island's
 * sprite cache is separate, [IslandSprites]).
 */
internal object ScenePictures {
    /**
     * The place and its close-ups (1074 letter tree: the sheet, the sealed and the opening envelope, cross-faded on one
     * camera): at most 4 × 2.4 MB (RGB_565), never all 24.
     */
    private const val KEEP = 4
    private val kept = ArrayDeque<Pair<Int, ImageBitmap>>()
    private val decoded = java.util.concurrent.atomic.AtomicInteger()
    val decodes: Int get() = decoded.get()
    @Synchronized fun of(res: android.content.res.Resources, id: Int): ImageBitmap {
        kept.firstOrNull { it.first == id }?.let { return it.second }
        val opts = android.graphics.BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.RGB_565; inScaled = false }
        val bmp = android.graphics.BitmapFactory.decodeResource(res, id, opts).asImageBitmap()
        decoded.incrementAndGet()
        kept.addFirst(id to bmp)
        while (kept.size > KEEP) kept.removeLast()
        return bmp
    }
}

/**
 * The stage itself: the painted place over the whole screen (Crop), then [content] with the geometry. No hero above a
 * card and no props over the picture: the place IS the picture the data lives in.
 *
 * 1011 (clean pilot): [camera] = a region of the picture the view looks at closely (the drawer, the stamp sheet, the
 * brass slider). The painting itself moves in (an eased zoom, [StageScenes.focus]); the geometry follows, so what is
 * shown there sits on the zoomed object. null = the whole place, drawn exactly as before.
 */
@Composable
internal fun FunctionStage(scene: StageScenes.Scene, camera: StageScenes.Region? = null, maxZoom: Float = 2.4f, header: Dp = 104.dp, reserve: Dp = 104.dp, pictureOverride: Int? = null, preservePictureOverflow: Boolean = false, content: @Composable BoxScope.(StageGeometry) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().semantics {
        contentDescription = "Bühne: ${scene.caption}"
        stateDescription = scene.signature + (camera?.let { " · nah: ${it.left},${it.top}" } ?: "")
    }) {
        val w = maxWidth; val h = maxHeight
        val res = LocalContext.current.resources
        val bitmap = remember(scene.picture, pictureOverride) { ScenePictures.of(res, pictureOverride ?: scene.picture) }
        val aspect = bitmap.width.toFloat() / bitmap.height
        val byHeight = w / h < aspect
        val iw = if (byHeight) h * aspect else w
        val ih = if (byHeight) h else w / aspect
        // panned so every surface is on screen (StageScenes.panLeft); the image follows with the same bias
        val left = if (byHeight) StageScenes.panLeft(scene, w.value, iw.value, 8f).dp else 0.dp
        val bias = if (byHeight && iw > w) (2f * (left / (w - iw)) - 1f) else 0f
        val rest = SceneMap(if (byHeight) left else (w - iw) / 2, (h - ih) / 2, iw, ih)
        // the last region looked at stays the zoom's target while the camera moves back out
        var last by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(camera) }
        if (camera != null) last = camera
        val t by androidx.compose.animation.core.animateFloatAsState(if (camera != null) 1f else 0f,
            androidx.compose.animation.core.tween(420, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "camera")
        val map = last?.takeIf { t > 0f }?.let { r ->
            val f = StageScenes.focus(r, rest.left.value, rest.top.value, rest.w.value, rest.h.value, w.value, h.value, header.value, reserve.value, maxZoom)
            SceneMap(lerp(rest.left, f[0].dp, t), lerp(rest.top, f[1].dp, t), lerp(rest.w, f[2].dp, t), lerp(rest.h, f[3].dp, t))
        } ?: rest
        Box(Modifier.fillMaxSize()) {
            if (map === rest && !preservePictureOverflow) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = androidx.compose.ui.BiasAlignment(bias, 0f))
            else Image(bitmap, null, Modifier.offset(x = map.left, y = map.top).wrapContentSize(androidx.compose.ui.Alignment.TopStart, unbounded = true)
                .requiredSize(map.w, map.h), contentScale = ContentScale.FillBounds)
        }
        content(StageGeometry(scene, SceneMap(0.dp, 0.dp, w, h), map))
    }
}

private fun lerp(a: Dp, b: Dp, t: Float): Dp = a + (b - a) * t
