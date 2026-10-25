package at.gregor.layermaxxing

import androidx.compose.runtime.Composable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged

/** Measured on the approved original 1024×1536 paintings, not copied from the tree's geometry.
 * All rectangles stay in picture coordinates; FunctionStage supplies the actual crop/pan.
 */
internal data class PostRegistration(
    val level: Int,
    val sheets: List<LetterTree.Box>,
    val windows: List<LetterTree.Box>,
    val belt: LetterTree.Box,
    val people: List<LetterTree.Box>,
    val rope: LetterTree.Box,
    val padlock: LetterTree.Box,
    val pen: LetterTree.Box,
    val arrowBack: LetterTree.Box,
    val arrowNow: LetterTree.Box,
    val letterRoom: Float,
    val holderTravel: Float,
    val letterTravel: Float,
    val label: String,
) {
    // Bounds measured on the production physical layers, in their common 2:3 canvas.
    val filterTag get() = when (level) {
        1 -> LetterTree.Box(.21f, .625f, .40f, .660f) // ink on the chest front, left of the time caption
        2 -> LetterTree.Box(.32f, .525f, .55f, .560f) // desk, below cubbies
        3 -> LetterTree.Box(.20f, .733f, .40f, .768f) // ink on the counter apron, not floating above the trays
        else -> LetterTree.Box(.24f, .135f, .44f, .18f)
    }
    val timeRail get() = when (level) {
        1 -> LetterTree.Box(.21f, .686f, .80f, .734f) // chest brass band
        2 -> LetterTree.Box(.22f, .657f, .78f, .725f) // drawer front
        else -> LetterTree.Box(.20f, .770f, .80f, .833f) // counter apron
    }
    val tilts get() = if (level == 0) LetterTree.tilts else List(sheets.size) { if (level == 2) 0f else (it % 3 - 1) * 1.2f }
    val knotLeft get() = if (level == 0) LetterTree.knotLeft else LetterTree.Box(rope.l, rope.b + .01f, rope.l + .08f, rope.b + .06f)
    val knotRight get() = if (level == 0) LetterTree.knotRight else LetterTree.Box(rope.r - .08f, rope.b + .01f, rope.r, rope.b + .06f)
    val pegs get() = people.map { (it.l + it.r) / 2 }
    fun room(i: Int) = if (level == 0) LetterTree.room(i) else letterRoom

    companion object {
        private fun b(l: Float, t: Float, r: Float, bottom: Float) = LetterTree.Box(l, t, r, bottom)
        private fun cells(xs: List<Float>, ys: List<Float>, w: Float, h: Float) = ys.flatMap { y -> xs.map { x -> b(x, y, x + w, y + h) } }
        private fun strip(l: Float, top: Float, step: Float, w: Float, h: Float, n: Int) = (0 until n).map { b(l + it * step, top, l + it * step + w, top + h) }
        fun of(level: Int): PostRegistration = when (level) {
            0 -> PostRegistration(0, LetterTree.sheets, LetterTree.windows, LetterTree.belt,
                strip(.18f, .16f, .135f, .12f, .035f, 5), LetterTree.rope, LetterTree.padlock, LetterTree.pen,
                LetterTree.arrowBack, LetterTree.arrowNow, .2f, 0f, 0f, "Briefbaum")
            1 -> PostRegistration(1,
                PhysicalPostalStorage.slots(1).map { it.box },
                strip(.222f, .720f, .158f, .063f, .020f, 4), b(.20f, .690f, .80f, .749f),
                strip(.23f, .642f, .21f, .14f, .032f, 3), b(.20f, .632f, .80f, .683f),
                b(.85f, .56f, .94f, .64f), b(.80f, .65f, .88f, .74f),
                b(.20f, .84f, .26f, .89f), b(.74f, .84f, .80f, .89f), .18f, 0f, 0f, "Postkasten")
            2 -> PostRegistration(2,
                PhysicalPostalStorage.slots(2).map { it.box },
                strip(.26f, .730f, .133f, .076f, .020f, 4), b(.215f, .706f, .785f, .756f),
                strip(.23f, .466f, .21f, .14f, .032f, 3), b(.20f, .455f, .80f, .508f),
                b(.83f, .64f, .91f, .71f), b(.17f, .48f, .29f, .55f),
                b(.20f, .76f, .26f, .81f), b(.74f, .76f, .80f, .81f), .18f, 0f, 0f, "Briefstube")
            else -> PostRegistration(3,
                PhysicalPostalStorage.slots(3).map { it.box },
                strip(.23f, .790f, .14f, .07f, .018f, 4), b(.20f, .765f, .80f, .815f),
                strip(.23f, .636f, .21f, .14f, .028f, 3), b(.20f, .628f, .80f, .670f),
                b(.85f, .64f, .93f, .71f), b(.20f, .34f, .29f, .41f),
                b(.20f, .80f, .26f, .85f), b(.74f, .80f, .80f, .85f), .20f, 0f, 0f, "Postamt")
        }
    }
}

internal data class PostObjectSample(val level: Int, val holder: Float, val letter: Float, val materialReady: Boolean)
internal val PostObjectMotionKey = SemanticsPropertyKey<PostObjectSample>("Post object motion")
internal data class PostHolderCanvasSample(val actual: androidx.compose.ui.unit.IntSize, val expected: androidx.compose.ui.unit.IntSize)
internal val PostHolderCanvasKey = SemanticsPropertyKey<PostHolderCanvasSample>("Postal foreground canvas registration")

/** Full-canvas alpha foreground preserves the original aspect and registration. The controller supplies the two
 * required layers; until then the original painting stays intact and only letters move (reported in STATUS).
 */
@Composable
internal fun PostHolder(g: StageGeometry, registration: PostRegistration, progress: Float, foreground: Int) {
    if (foreground == 0) return
    val res = LocalContext.current.resources
    val bitmap = androidx.compose.runtime.remember(foreground) {
        val decoded = android.graphics.BitmapFactory.decodeResource(res, foreground,
            android.graphics.BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888 })
        require(decoded.hasAlpha()) { "Post holder must retain its transparent full-canvas registration" }
        require(decoded.width * 3 == decoded.height * 2) { "Post holder must preserve the original 2:3 aspect" }
        decoded.asImageBitmap()
    }
    val density = LocalDensity.current
    val measured = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val expected = with(density) { androidx.compose.ui.unit.IntSize(g.picture.w.roundToPx(), g.picture.h.roundToPx()) }
    // size() obeys the viewport's maxWidth and squashes this wide source canvas horizontally.
    // Match FunctionStage's uncropped image measurement and anchor overflow at the same origin.
    Box(Modifier.offset(g.picture.left, g.picture.top)
        .wrapContentSize(androidx.compose.ui.Alignment.TopStart, unbounded = true)
        .requiredSize(g.picture.w, g.picture.h)
        .onSizeChanged { measured.value = it }
        .semantics { this[PostHolderCanvasKey] = PostHolderCanvasSample(measured.value, expected) }
        .testTag("post-holder-canvas").graphicsLayer {
        translationY = g.picture.h.toPx() * registration.holderTravel * progress
    }) {
        Canvas(Modifier.fillMaxSize()) {
            drawImage(bitmap, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()))
        }
    }
}
