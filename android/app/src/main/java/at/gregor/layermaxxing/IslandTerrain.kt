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
 * Islands are not pictures. Each one is assembled at runtime from parts:
 *  - terrain drawn in code (shallow-water ring, foam, sand, cliff, grass) whose
 *    outline wobbles per owner seed and whose size follows the friendship level;
 *  - footpaths drawn between the plaza and every building;
 *  - single sprites for buildings, trees, rocks and boats, depth-sorted, trees swaying.
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
)

internal object IslandPlans {
    const val ASPECT = 1.25f
    private const val CX = .5f
    private const val CY = .55f
    private const val RX = .46f
    private const val RY = .40f

    /** Land is within this normalised radius (grass = .9, sand = 1). */
    fun inside(x: Float, y: Float, scale: Float = 1f, radius: Float = .9f): Boolean {
        val u = (x - CX) / (RX * scale); val v = (y - CY) / (RY * scale)
        return u * u + v * v <= radius * radius
    }

    /** Home: the menu buildings around a plaza. Anchors were picked so paths and lawns never cross. */
    val homeBuildings = linkedMapOf(
        IsleBuilding.HOUSE to IslandPiece(R.drawable.b_house, .50f, .31f, .23f, name = "Mein Haus"),
        IsleBuilding.LIGHTHOUSE to IslandPiece(R.drawable.b_lighthouse, .80f, .38f, .20f, name = "Freunde"),
        IsleBuilding.POST to IslandPiece(R.drawable.b_post, .20f, .43f, .19f, name = "Post"),
        IsleBuilding.LIBRARY to IslandPiece(R.drawable.b_library, .79f, .64f, .19f, name = "Wörterbuch"),
        IsleBuilding.CAMPFIRE to IslandPiece(R.drawable.b_hall, .23f, .70f, .19f, name = "Gruppen"),
        IsleBuilding.HARBOUR to IslandPiece(R.drawable.b_board, .50f, .82f, .13f, name = "Hafen"),
    )
    val plaza = Offset(.5f, .55f)
    val homeSlots = listOf(.36f to .40f, .65f to .37f, .31f to .56f, .69f to .52f, .40f to .71f, .63f to .75f)

    fun home(seed: Long): IslandPlan {
        val nature = listOf(
            IslandPiece(R.drawable.n_pine, .32f, .23f, .12f, sway = true),
            IslandPiece(R.drawable.n_pine, .66f, .22f, .11f, sway = true),
            IslandPiece(R.drawable.n_tree, .89f, .52f, .11f, sway = true),
            IslandPiece(R.drawable.n_cypress, .11f, .58f, .10f, sway = true),
            IslandPiece(R.drawable.n_bush, .37f, .86f, .07f),
            IslandPiece(R.drawable.n_flowerbush, .62f, .88f, .07f),
            IslandPiece(R.drawable.n_rocks, .90f, .74f, .08f),
            IslandPiece(R.drawable.b_jetty, .66f, .99f, .17f),
            IslandPiece(R.drawable.n_rowboat, .86f, 1.02f, .09f),
        )
        val pieces = homeBuildings.values + nature
        val paths = homeBuildings.values.map { plaza to Offset(it.x, it.y + .01f) } + (plaza to Offset(.64f, .93f))
        return IslandPlan(seed, 1f, pieces, paths)
    }

    /** What a friendship island holds at each level (u,v = normalised island coords). */
    private data class Part(val level: Int, val res: Int, val u: Float, val v: Float, val size: Float, val sway: Boolean = false, val path: Boolean = false)
    private val friendParts = listOf(
        Part(1, R.drawable.b_house, 0f, -.32f, .30f, path = true),
        Part(1, R.drawable.n_tree, -.58f, -.05f, .22f, sway = true),
        Part(1, R.drawable.n_bush, .48f, .28f, .13f),
        Part(1, R.drawable.n_flowerbush, -.28f, .48f, .12f),
        Part(2, R.drawable.b_workshop, .52f, -.12f, .25f, path = true),
        Part(2, R.drawable.n_pine, -.32f, -.60f, .20f, sway = true),
        Part(2, R.drawable.b_jetty, .22f, .98f, .26f),
        Part(2, R.drawable.n_rocks, .80f, .36f, .13f),
        Part(3, R.drawable.n_crates, -.60f, .36f, .12f),
        Part(3, R.drawable.n_cypress, .70f, -.48f, .17f, sway = true),
        Part(3, R.drawable.n_tree, -.80f, .06f, .16f, sway = true),
        Part(3, R.drawable.n_rowboat, .72f, 1.04f, .13f),
        Part(4, R.drawable.b_lighthouse, -.70f, -.42f, .24f, path = true),
        Part(4, R.drawable.b_hall, -.12f, .22f, .24f, path = true),
        Part(4, R.drawable.n_pine, .28f, -.66f, .17f, sway = true),
        Part(4, R.drawable.n_flowerbush, .40f, .52f, .10f),
    )

    /** Land grows with the friendship; parts keep their on-screen size while the island gets bigger. */
    fun growth(level: Int) = Isle.islandWidth(level).value / Isle.islandWidth(1).value

    fun friend(level: Int, seed: Long): IslandPlan {
        val lv = level.coerceIn(1, 4)
        val mirror = seed % 2L == 1L
        val g = growth(lv)
        fun at(u: Float, v: Float) = Offset(CX + (if (mirror) -u else u) * RX * .9f, CY + v * RY * .9f)
        val parts = friendParts.filter { it.level <= lv }
        val centre = at(0f, .12f)
        val pieces = parts.map { p -> at(p.u, p.v).let { IslandPiece(p.res, it.x, it.y, p.size / g, p.sway) } }
        val paths = parts.filter { it.path }.map { centre to at(it.u, it.v + .02f) } +
            (if (lv >= 2) listOf(centre to at(.22f, .86f)) else emptyList())
        return IslandPlan(seed, 1f, pieces, paths)
    }

    /** Outline radius multiplier at angle a: a gentle, seed-stable wobble so no two islands match. */
    fun wobble(seed: Long, a: Float): Float {
        val s = (seed * 2654435761L).toInt()
        val p1 = (s and 0xFF) / 255f * 6.28f
        val p2 = ((s shr 8) and 0xFF) / 255f * 6.28f
        val p3 = ((s shr 16) and 0xFF) / 255f * 6.28f
        return 1f + .045f * sin(3 * a + p1) + .03f * sin(5 * a + p2) + .02f * sin(7 * a + p3)
    }
}

private fun DrawScope.blob(seed: Long, scale: Float, grow: Float, dy: Float = 0f, time: Float = 0f, breathe: Float = 0f): Path {
    val path = Path()
    val n = 72
    for (i in 0..n) {
        val a = (i.toFloat() / n) * 2f * PI.toFloat()
        val r = IslandPlans.wobble(seed, a) * grow * (1f + breathe * sin(a * 4 + time * 2f * PI.toFloat()))
        val x = size.width * (.5f + .46f * scale * r * cos(a))
        val y = size.height * (.55f + .40f * scale * r * sin(a)) + dy
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private val Shallow = Color(0xFF9FE6DF)
private val Foam = Color(0xFFF4FFFD)
private val SandC = Color(0xFFF3DFAE)
private val Cliff = Color(0xFFC79B69)
private val CliffDark = Color(0xFFA67C52)
private val Grass = Color(0xFF8ED07A)
private val GrassLight = Color(0xFFB4E39B)
private val PathEdge = Color(0xFFD8BD86)
private val PathC = Color(0xFFF1E0B8)

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
    val sway by if (animate) t.animateFloat(-1f, 1f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "sway")
    else remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    BoxWithConstraints(modifier.aspectRatio(IslandPlans.ASPECT)) {
        val w = maxWidth; val h = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val s = plan.scale
            val cliff = size.height * .045f
            // Shallow water ring that slowly breathes, then foam that runs around the shore.
            drawPath(blob(plan.seed, s, 1.13f, time = time, breathe = .012f), Shallow.copy(alpha = .75f))
            drawPath(blob(plan.seed, s, 1.05f, time = time, breathe = .008f), Shallow)
            drawPath(blob(plan.seed, s, 1.0f, dy = cliff), CliffDark)
            drawPath(
                blob(plan.seed, s, 1.02f, dy = cliff * .7f), Foam.copy(alpha = .9f),
                style = Stroke(size.width * .008f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f), -time * 320f)),
            )
            drawPath(blob(plan.seed, s, 1.0f, dy = cliff * .5f), Cliff)
            drawPath(blob(plan.seed, s, 1.0f), SandC)
            drawPath(blob(plan.seed, s, .9f), Grass)
            translate(left = -size.width * .03f, top = -size.height * .05f) {
                drawPath(blob(plan.seed + 7, s, .62f), Brush.radialGradient(listOf(GrassLight, Grass), radius = size.width * .4f))
            }
            plan.paths.forEach { (a, b) ->
                val p0 = Offset(a.x * size.width, a.y * size.height); val p1 = Offset(b.x * size.width, b.y * size.height)
                drawLine(PathEdge, p0, p1, size.width * .042f, StrokeCap.Round)
                drawLine(PathC, p0, p1, size.width * .032f, StrokeCap.Round)
            }
            // Soft contact shadows ground every part on the grass.
            plan.pieces.forEach { pc ->
                val sw = size.width * pc.size * .62f
                drawOval(
                    Color(0x22000000),
                    topLeft = Offset(pc.x * size.width - sw / 2, pc.y * size.height - sw * .12f),
                    size = androidx.compose.ui.geometry.Size(sw, sw * .24f),
                )
            }
            plan.paths.firstOrNull()?.first?.let { c ->
                drawCircle(PathEdge, size.width * .075f, Offset(c.x * size.width, c.y * size.height))
                drawCircle(PathC, size.width * .066f, Offset(c.x * size.width, c.y * size.height))
            }
        }
        plan.pieces.sortedBy { it.y }.forEachIndexed { i, p ->
            val box: Dp = w * p.size
            Image(
                painterResource(p.res), p.name, contentScale = ContentScale.Fit, alignment = Alignment.BottomCenter,
                modifier = Modifier.offset(x = w * p.x - box / 2, y = h * p.y - box).size(box)
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
