package at.gregor.layermaxxing

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Islands are assembled at runtime: one painted, EMPTY terrain base (coast,
 * cliffs, lawns, paths, no buildings) plus single sprites for every building,
 * tree and decoration, depth-sorted with contact shadows; trees sway, light
 * breathes on the water. Friend islands mirror the base per owner and gain
 * buildings with every level.
 * A friendship island gains parts with every level; the home island carries the
 * menu buildings and the decoration lawns.
 */
internal data class IslandPiece(
    val res: Int,
    /** Bottom-centre anchor, fraction of the island box. */
    val x: Float,
    val y: Float,
    /** Size of the sprite's longer side, fraction of the box width. */
    val size: Float,
    val sway: Boolean = false,
    val name: String? = null,
)

internal data class IslandPlan(
    val seed: Long,
    /** 0..1: how much of the box the land fills. */
    val scale: Float,
    val pieces: List<IslandPiece>,
    val paths: List<Pair<Offset, Offset>>,
    /** Friend islands flip the painted base so neighbours never look identical. */
    val mirror: Boolean = false,
    /** 0..1: Insel-Ausbau land size from the point score (1 = full painted island). */
    val landScale: Float = 1f,
)

internal object IslandPlans {
    /** Painted terrain base (terrain_home) is 1367 x 1116. */
    const val ASPECT = 1367f / 1116f
    private const val CX = .5f
    private const val CY = .55f
    private const val RX = .46f
    private const val RY = .40f

    /** Land is within this normalised radius (grass = .9, sand = 1). */
    fun inside(x: Float, y: Float, scale: Float = 1f, radius: Float = .9f): Boolean {
        val u = (x - CX) / (RX * scale); val v = (y - CY) / (RY * scale)
        return u * u + v * v <= radius * radius
    }

    /**
     * Insel-Ausbau: how big the land itself is at a given point score. A fresh island
     * is a small sand mound; every built-upgrade grows the land a step (Aufschüttung).
     */
    fun landScale(score: Int): Float = when {
        score >= 240 -> 1f
        score >= 160 -> .86f
        score >= 100 -> .74f
        score >= 60 -> .62f
        score >= 25 -> .5f
        else -> .3f
    }
    /** How green the land is (0 = bare sand bank, 1 = full meadow) for a land scale. */
    fun meadow(landScale: Float): Float = ((landScale - .3f) / .7f).coerceIn(0f, 1f)
    /** Things on a small island shrink a little so the sand bank is not crammed. */
    fun pieceScale(landScale: Float): Float = .6f + .4f * landScale
    /**
     * On a small sand bank the five signs would pile up at the shore; spread them apart
     * (fraction of island width/height). Zero on the full island.
     */
    fun tagNudge(b: IsleBuilding, landScale: Float): Pair<Float, Float> {
        val k = ((1f - landScale) / .7f).coerceIn(0f, 1f)
        val (dx, dy) = when (b) {
            IsleBuilding.CAMPFIRE -> -.11f to 0f
            IsleBuilding.LIBRARY -> .11f to 0f
            IsleBuilding.HARBOUR -> 0f to .05f
            IsleBuilding.POST -> -.05f to 0f
            IsleBuilding.LIGHTHOUSE -> .05f to 0f
            else -> 0f to 0f
        }
        return dx * k to dy * k
    }
    /** Anchor a land point onto the scaled island (same centre the terrain layer scales about). */
    fun onLand(x: Float, y: Float, s: Float): Pair<Float, Float> = (CX + (x - CX) * s) to (CY + (y - CY) * s)
    const val LAND_CX = CX
    const val LAND_CY = CY

    /** Home: the menu buildings around a plaza. Anchors were picked so paths and lawns never cross. */
    /** The app's own places sit at the coast; the inland plots belong to the player's real life (LifePlaces). */
    val homeBuildings = linkedMapOf(
        IsleBuilding.LIGHTHOUSE to IslandPiece(R.drawable.lm_lighthouse, .88f, .44f, .105f, name = "Freunde"),
        IsleBuilding.POST to IslandPiece(R.drawable.lm_post, .12f, .47f, .105f, name = "Post"),
        IsleBuilding.CAMPFIRE to IslandPiece(R.drawable.lm_campfire, .28f, .80f, .10f, name = "Gruppen"),
        IsleBuilding.LIBRARY to IslandPiece(R.drawable.lm_library, .74f, .79f, .10f, name = "Wörterbuch"),
        IsleBuilding.HARBOUR to IslandPiece(R.drawable.lm_jetty, .51f, .85f, .115f, name = "Hafen"),
    )
    /** Stufe-1-Ruinen: unter diesem Punktestand steht die primitive Baumstamm-Version. */
    val ruinCosts = mapOf(
        IsleBuilding.CAMPFIRE to 25,   // Feuerstelle zuerst – die günstige Keimzelle
        IsleBuilding.POST to 60,
        IsleBuilding.HARBOUR to 100,
        IsleBuilding.LIBRARY to 160,
        IsleBuilding.LIGHTHOUSE to 240,
    )
    /** The same buildings as level-1 ruins; anchors and sizes are identical. */
    private val homeRuins = linkedMapOf(
        IsleBuilding.LIGHTHOUSE to IslandPiece(R.drawable.lm_lighthouse_ruin, .88f, .44f, .105f, name = "Freunde"),
        IsleBuilding.POST to IslandPiece(R.drawable.lm_post_ruin, .12f, .47f, .105f, name = "Post"),
        IsleBuilding.CAMPFIRE to IslandPiece(R.drawable.lm_campfire_ruin, .28f, .80f, .10f, name = "Gruppen"),
        IsleBuilding.LIBRARY to IslandPiece(R.drawable.lm_library_ruin, .74f, .79f, .10f, name = "Wörterbuch"),
        IsleBuilding.HARBOUR to IslandPiece(R.drawable.lm_jetty_ruin, .51f, .85f, .115f, name = "Hafen"),
    )
    val plaza = Offset(.5f, .55f)
    /** Small decoration lawns ringing the plaza, clear of the building plots. */
    val homeSlots = listOf(.41f to .45f, .59f to .45f, .36f to .53f, .64f to .53f, .43f to .61f, .57f to .61f)

    /**
     * Where each app building stands for a score: ruin or finished sprite, moved onto the
     * grown land and sized for it. Sprites, signs and touch targets all read from here.
     */
    fun placedBuildings(score: Int): Map<IsleBuilding, IslandPiece> {
        val s = landScale(score)
        val ps = pieceScale(s)
        return homeBuildings.mapValues { (b, piece) ->
            // Insel-Ausbau Scheibe 1: below its price a building shows its primitive log-hut look.
            val p = if (score < (ruinCosts[b] ?: 0)) homeRuins[b] ?: piece else piece
            val (lx, ly) = onLand(p.x, p.y, s)
            p.copy(x = lx, y = ly, size = p.size * ps)
        }
    }

    fun home(seed: Long, score: Int = Int.MAX_VALUE): IslandPlan {
        // The painted base already carries coast, rocks and bushes; a few calm trees frame the plots.
        val nature = listOf(
            IslandPiece(R.drawable.n_pine, .17f, .30f, .07f, sway = true),
            IslandPiece(R.drawable.n_pine, .83f, .29f, .065f, sway = true),
            IslandPiece(R.drawable.n_tree, .14f, .66f, .07f, sway = true),
            IslandPiece(R.drawable.n_cypress, .88f, .66f, .06f, sway = true),
        )
        val s = landScale(score)
        val ps = pieceScale(s)
        val buildings = placedBuildings(score).values.toList()
        // A bare sand bank carries no trees yet; they return once the meadow has grown in.
        val grown = if (meadow(s) < .3f) emptyList() else nature.map { n ->
            val (lx, ly) = onLand(n.x, n.y, s)
            n.copy(x = lx, y = ly, size = n.size * ps)
        }
        return IslandPlan(seed, 1f, buildings + grown, emptyList(), landScale = s)
    }

    /** What a friendship island holds at each level, placed on the painted clearings. */
    private data class Part(val level: Int, val res: Int, val x: Float, val y: Float, val size: Float, val sway: Boolean = false)
    private val friendParts = listOf(
        Part(1, R.drawable.b_house, .48f, .31f, .26f),
        Part(1, R.drawable.n_tree, .30f, .44f, .13f, sway = true),
        Part(1, R.drawable.n_flowerbush, .62f, .52f, .08f),
        Part(2, R.drawable.b_workshop, .75f, .40f, .22f),
        Part(2, R.drawable.n_pine, .24f, .70f, .13f, sway = true),
        Part(2, R.drawable.n_bush, .40f, .66f, .07f),
        Part(3, R.drawable.b_post, .26f, .45f, .20f),
        Part(3, R.drawable.n_cypress, .68f, .66f, .10f, sway = true),
        Part(3, R.drawable.n_crates, .55f, .74f, .07f),
        Part(4, R.drawable.b_lighthouse, .76f, .66f, .20f),
        Part(4, R.drawable.b_hall, .25f, .72f, .20f),
        Part(4, R.drawable.n_tree, .62f, .25f, .10f, sway = true),
    )

    /** Land grows with the friendship; parts keep their on-screen size while the island gets bigger. */
    fun growth(level: Int) = Isle.islandWidth(level).value / Isle.islandWidth(1).value

    fun friend(level: Int, seed: Long): IslandPlan {
        val lv = level.coerceIn(1, 4)
        // From level 3 the post hut takes the left clearing where the first tree stood.
        val parts = friendParts.filter { it.level <= lv && !(lv >= 3 && it.level == 1 && it.res == R.drawable.n_tree) }
        val g = growth(lv)
        val pieces = parts.map { p -> IslandPiece(p.res, p.x, p.y, (p.size / g).coerceAtLeast(p.size * .7f), p.sway) }
        return IslandPlan(seed, 1f, pieces, emptyList(), mirror = seed % 2L == 1L)
    }


}

/** Terrain + paths + parts. [overlay] draws labels/slots on top in the same box coordinates. */
@Composable
internal fun DynamicIsland(
    plan: IslandPlan,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    overlay: @Composable BoxWithConstraintsScope.() -> Unit = {},
) {
    val t = rememberInfiniteTransition(label = "isle")
    val time by if (animate) t.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "time")
    else remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val sway by if (animate) t.animateFloat(-1f, 1f, infiniteRepeatable(tween(5200), RepeatMode.Reverse), label = "sway")
    else remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    BoxWithConstraints(modifier.aspectRatio(IslandPlans.ASPECT)) {
        val w = maxWidth; val h = maxHeight
        val ls = plan.landScale
        // Gently breathing light on the shallow water, under the painted island.
        Canvas(Modifier.fillMaxSize()) {
            drawOval(
                Color.White.copy(alpha = .10f),
                topLeft = Offset(size.width * (IslandPlans.LAND_CX - .47f * ls), size.height * (IslandPlans.LAND_CY - .46f * ls)),
                size = androidx.compose.ui.geometry.Size(size.width * .94f * ls, size.height * .92f * ls),
            )
        }
        // Insel-Ausbau: the land starts as a small bare sand bank and grows with upgrades;
        // the meadow fades in over the sand as it grows (fully green at the full island).
        val landLayer = Modifier.fillMaxSize().graphicsLayer {
            val k = ls * (if (plan.mirror) -1f else 1f)
            scaleX = k; scaleY = ls
            transformOrigin = TransformOrigin(IslandPlans.LAND_CX, IslandPlans.LAND_CY)
        }
        val green = IslandPlans.meadow(ls)
        if (green < 1f) Image(painterResource(R.drawable.terrain_sand), null, landLayer, contentScale = ContentScale.FillBounds)
        if (green > 0f) Image(
            painterResource(R.drawable.terrain_home), null, landLayer.graphicsLayer { alpha = green },
            contentScale = ContentScale.FillBounds,
        )
        Canvas(Modifier.fillMaxSize()) {
            // Soft contact shadows ground every part on the grass.
            plan.pieces.forEach { pc ->
                val sw = size.width * pc.size * .62f
                val px = (if (plan.mirror) 1f - pc.x else pc.x) * size.width
                drawOval(
                    Color(0x26000000),
                    topLeft = Offset(px - sw / 2, pc.y * size.height - sw * .12f),
                    size = androidx.compose.ui.geometry.Size(sw, sw * .24f),
                )
            }
        }
        plan.pieces.sortedBy { it.y }.forEachIndexed { i, p ->
            val box: Dp = w * p.size
            Image(
                painterResource(p.res), p.name, contentScale = ContentScale.Fit, alignment = Alignment.BottomCenter,
                modifier = Modifier.offset(x = w * (if (plan.mirror) 1f - p.x else p.x) - box / 2, y = h * p.y - box).size(box)
                    .then(
                        if (p.sway) Modifier.graphicsLayer {
                            rotationZ = sway * (1.4f + (i % 3) * .5f)
                            transformOrigin = TransformOrigin(.5f, 1f)
                        } else Modifier,
                    ),
            )
        }
        overlay()
    }
}
