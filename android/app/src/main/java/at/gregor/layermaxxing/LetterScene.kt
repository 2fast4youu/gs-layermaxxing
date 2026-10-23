package at.gregor.layermaxxing

import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.draw.drawWithContent

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Briefbaum 22.10.2026 (Veit: "es soll einfach so ein cleaner Zoom sein … das würde man sich immer in der gleichen Szene
 * bewegen"): the letter is opened IN the tree's scene, natively – no video, no pre-rendered close-up pictures:
 *  - the camera moves into the same painted tree (one transform over the whole scene: bark, belt, envelopes, ink);
 *  - the envelope is a classic LANDSCAPE envelope built from layers the app moves itself (back panel, letter sheet,
 *    pocket, flap, wax seal – painted raster material, see scripts/letter_layers.py);
 *  - a second tap anywhere on the envelope lets the seal go (it lifts, cracks and falls away), the flap folds up, the
 *    letter slides out and floats large above the envelope; a tap beside it puts it back and the camera moves out.
 */

/** Where the letter scene is. Animated phases end by themselves; a tap / back only acts in the phases that take it. */
internal enum class ScenePhase { REST, APPROACH, SEALED, WAITING, UNSEALING, EMERGING, READING, STOWING, RESEALING, LEAVING }

/**
 * The letter scene's state machine (pure: no Compose UI, no clock). [LetterTreePlace] runs the animations of a phase and
 * reports their end; taps, back and the server's answers are events that are ignored in phases that do not take them
 * (rapid taps, a tap during an animation, a late answer after the view let go).
 */
@Stable
internal class LetterScene {
    var phase by mutableStateOf(ScenePhase.REST); private set
    var letter by mutableStateOf<Long?>(null); private set
    /** After stowing: the seal is pressed on again (the letter was marked unread). */
    var reseal by mutableStateOf(false); private set
    private var readOnArrival = false
    private var outAsked = false
    private var then: Pair<Long, Boolean>? = null

    /** A letter on the trunk tapped: the camera moves to its envelope. [read] = it comes straight out (no seal). */
    fun approach(id: Long, read: Boolean): Boolean {
        if (phase != ScenePhase.REST) return false
        letter = id; readOnArrival = read; reseal = false; outAsked = false; phase = ScenePhase.APPROACH
        return true
    }
    fun arrived() { if (phase == ScenePhase.APPROACH) phase = if (readOnArrival) ScenePhase.EMERGING else ScenePhase.SEALED }

    /**
     * A tap anywhere on the envelope. true = the caller asks the server now (exactly then and only then).
     * [openedHere]: the text is here already (it came while the view was away) – no second seal; [busy]: the app's request
     * for it still runs – wait for it, never a second call.
     */
    fun tapEnvelope(openedHere: Boolean, busy: Boolean): Boolean {
        if (phase != ScenePhase.SEALED) return false
        if (openedHere) { phase = ScenePhase.EMERGING; return false }
        phase = ScenePhase.WAITING
        return !busy
    }
    fun answered() { if (phase == ScenePhase.WAITING) phase = ScenePhase.UNSEALING }
    fun failed() { if (phase == ScenePhase.WAITING) phase = ScenePhase.SEALED }
    fun unsealed() { if (phase == ScenePhase.UNSEALING) phase = ScenePhase.EMERGING }
    fun emerged() {
        if (phase != ScenePhase.EMERGING) return
        phase = if (outAsked) ScenePhase.STOWING else ScenePhase.READING
        outAsked = false
    }

    /** A tap beside the letter / the envelope, or back. false = nothing to step out of. */
    fun out(): Boolean = when (phase) {
        ScenePhase.READING -> { phase = ScenePhase.STOWING; true }
        ScenePhase.APPROACH, ScenePhase.SEALED, ScenePhase.WAITING -> { then = null; phase = ScenePhase.LEAVING; true }
        // the seal is breaking / the letter coming out: it goes back in as soon as it is out (never cut off mid-way)
        ScenePhase.UNSEALING, ScenePhase.EMERGING -> { outAsked = true; true }
        ScenePhase.STOWING, ScenePhase.RESEALING, ScenePhase.LEAVING -> { then = null; true }
        ScenePhase.REST -> false
    }

    /** The server marked the letter unread: it goes back in and the seal is pressed on again. */
    fun markedUnread() { if (phase == ScenePhase.READING) { reseal = true; then = null; phase = ScenePhase.STOWING } }

    /** The same person's previous / newer letter: this one goes back in, out, then in to that one. */
    fun goTo(id: Long, read: Boolean) { if (phase == ScenePhase.READING && id != letter) { then = id to read; phase = ScenePhase.STOWING } }

    fun stowed() { if (phase == ScenePhase.STOWING) phase = if (reseal) ScenePhase.RESEALING else ScenePhase.LEAVING }
    fun resealed() { if (phase == ScenePhase.RESEALING) { reseal = false; phase = ScenePhase.SEALED } }

    /** The camera is back at the tree. Returns the letter to move to next (then [approach] it), else null. */
    fun left(): Pair<Long, Boolean>? {
        if (phase != ScenePhase.LEAVING) return null
        phase = ScenePhase.REST; letter = null
        return then.also { then = null }
    }

    /** The letter is gone (account switch, a closed gate): the scene ends at once, nothing kept. */
    fun drop() { phase = ScenePhase.REST; letter = null; reseal = false; outAsked = false; then = null }

    /** Whether a tap on the trunk's letters, the pen, the belt … is taken (only with the camera at the tree). */
    val atRest: Boolean get() = phase == ScenePhase.REST

    companion object {
        /** A restored scene (rotation, process death) settles in a still phase: no half animation, no lost letter. */
        val Saver: Saver<LetterScene, Any> = Saver(
            save = { s -> listOf(s.phase.name, s.letter ?: -1L) },
            restore = { v ->
                val l = v as List<*>; val id = (l[1] as Long).takeIf { it >= 0 }
                LetterScene().apply {
                    val p = ScenePhase.valueOf(l[0] as String)
                    if (id != null) {
                        letter = id
                        phase = when (p) {
                            ScenePhase.REST, ScenePhase.LEAVING, ScenePhase.STOWING -> ScenePhase.REST
                            ScenePhase.UNSEALING, ScenePhase.EMERGING, ScenePhase.READING -> ScenePhase.READING
                            else -> ScenePhase.SEALED
                        }
                        if (phase == ScenePhase.REST) letter = null
                    }
                }
            },
        )
    }
}

/** The same person's letters in time (both directions, direct only), for "früherer / neuerer Brief". */
internal object PeerLetters {
    fun of(letters: List<ApiClient.Message>, peerId: Long): List<ApiClient.Message> =
        PostViews.direct(letters).filter { it.peerId == peerId }.sortedWith(compareBy({ it.createdAt }, { it.id }))

    /** (previous, newer) of [current] among its person's letters; null at the ends. Never another person's letter. */
    fun neighbours(letters: List<ApiClient.Message>, current: ApiClient.Message): Pair<ApiClient.Message?, ApiClient.Message?> {
        val line = of(letters, current.peerId)
        val i = line.indexOfFirst { it.id == current.id }
        if (i < 0) return null to null
        return line.getOrNull(i - 1) to line.getOrNull(i + 1)
    }
}

/**
 * The camera into the tree: one uniform scale [k] and translation (px) over the whole scene, so the envelope's rest
 * place [rest] lands on [target] at cam = 1. The scale grows exponentially (an even zoom), the envelope's centre moves
 * on a straight line; the painted picture [picture] always covers the screen (never an empty edge).
 */
internal object LetterCamera {
    /** The envelope's place when looked at: wide on the screen, upper middle (room for its caption below). */
    fun target(w: Float, h: Float, top: Float, bottom: Float): Rect {
        val tw = minOf(w * .86f, w - 32f, (h - top - bottom) * EnvelopeArt.ASPECT * .9f)
        val th = tw / EnvelopeArt.ASPECT
        val cy = top + (h - top - bottom) * .42f
        return Rect(w / 2 - tw / 2, cy - th / 2, w / 2 + tw / 2, cy + th / 2)
    }

    /** (k, tx, ty): screen = rest × k + t. */
    fun transform(rest: Rect, target: Rect, picture: Rect, w: Float, h: Float, cam: Float): FloatArray {
        if (cam <= 0f || rest.width <= 0f) return floatArrayOf(1f, 0f, 0f)
        val k1 = target.width / rest.width
        val k = exp(ln(k1) * cam)
        val cx = rest.center.x + (target.center.x - rest.center.x) * cam
        val cy = rest.center.y + (target.center.y - rest.center.y) * cam
        var tx = cx - rest.center.x * k
        var ty = cy - rest.center.y * k
        // the picture keeps covering the screen
        val lo = w - picture.right * k; val hi = -picture.left * k
        if (lo <= hi) tx = tx.coerceIn(lo, hi)
        val loY = h - picture.bottom * k; val hiY = -picture.top * k
        if (loY <= hiY) ty = ty.coerceIn(loY, hiY)
        return floatArrayOf(k, tx, ty)
    }

    fun apply(r: Rect, t: FloatArray): Rect = Rect(r.left * t[0] + t[1], r.top * t[0] + t[2], r.right * t[0] + t[1], r.bottom * t[0] + t[2])
}

/** How an envelope looks right now (all 0..1). */
internal data class EnvelopeLook(
    /** 0 closed .. 1 folded fully up (180°). */
    val flap: Float = 0f,
    /** the wax seal: 0 none .. 1 pressed on (RESEALING animates it on). */
    val seal: Float = 0f,
    /** the seal letting go: 0 whole .. .3 lifted and cracked .. 1 halves fallen away. */
    val crack: Float = 0f,
    /** waiting for the server: the seal rocks a little (-1..1). */
    val rock: Float = 0f,
    /** the letter inside: null = none drawn (it floats), else 0 inside .. 1 half out above the pocket. */
    val rise: Float? = null,
    /** a faint stain where a seal was (opened letters). */
    val waxMark: Boolean = false,
)

/**
 * The envelope's material and geometry – the delivered N1–N4 layers (scripts/letter_layers.py, docs/letter-layer-assets.json;
 * all numbers here are measured there, nothing is stretched):
 *  - body: N2 below its fold line (the open, empty envelope: the back panel's inside and the pocket), 1.8625 : 1 – the
 *    delivered envelope is flatter than the requested 1.45 and is used as it is (a classic landscape envelope);
 *  - the pocket is the SAME picture clipped to its measured opening (back and pocket can never be out of register);
 *  - flap outside: N1's closed flap cut on its outline, scaled uniformly to the body's width, hinged on the fold line;
 *  - flap inside: N2 above its fold line (the flap folded up), the same scale as the body;
 *  - seal: N4; sheet: N3 with its deckled edge and its two folds (natural writing paper as delivered, 1 : 1.49).
 * The outside flap reaches .71 of the body's height, the inside one .50 (as painted): they meet at 90°, where the flap
 * has no height on screen.
 */
internal object EnvelopeArt {
    const val ASPECT = 1.8625f
    /** The closed flap: its height and its tip (where the seal sits), fractions of the body's height. */
    const val FLAP_OUT = .7193f
    const val TIP = .7093f
    /** The flap folded up (inner face): its height above the fold line. */
    const val FLAP_IN = .4968f
    /** The pocket's opening (the side flaps' upper edges, the bottom flap's notch), fractions of the body. */
    val POCKET = listOf(0f to .0113f, .3301f to .3673f, .4579f to .4595f, .5387f to .4595f, .6603f to .3673f, 1f to .0113f)
    const val SEAL_D = .28f
    /** The folded letter inside: x and width, top (fractions of the envelope). */
    const val SHEET_L = .06f
    const val SHEET_W = .88f
    const val SHEET_TOP = .17f
    /** How far the letter rises out (fraction of the envelope's height). */
    const val RISE = .74f
    /** The delivered sheet: height / width, and its first fold (it is folded in three; the top panel shows folded). */
    const val PAPER = 1.4895f
    const val FOLD = .304f

    class Pics(val body: ImageBitmap, val flapIn: ImageBitmap, val flapOut: ImageBitmap, val seal: ImageBitmap, val sheet: ImageBitmap)
    @Volatile private var pics: Pics? = null
    fun of(res: android.content.res.Resources): Pics = pics ?: synchronized(this) {
        pics ?: run {
            fun decode(id: Int) = android.graphics.BitmapFactory.decodeResource(res, id, android.graphics.BitmapFactory.Options().apply {
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888; inScaled = false
            }).asImageBitmap()
            Pics(decode(R.drawable.e_post_0_lay_body), decode(R.drawable.e_post_0_lay_flap_in), decode(R.drawable.e_post_0_lay_flap_out),
                decode(R.drawable.e_post_0_lay_seal), decode(R.drawable.e_post_0_lay_sheet)).also { pics = it }
        }
    }

    /** The folded letter's height as a fraction of the envelope's height (the sheet's top panel, down to its first fold). */
    val sheetH: Float get() = SHEET_W * ASPECT * PAPER * FOLD

    /** The letter's rect in the envelope at [rise] (fractions; top may be negative = above the envelope). */
    fun sheet(rise: Float): Rect = Rect(SHEET_L, SHEET_TOP - RISE * rise, SHEET_L + SHEET_W, SHEET_TOP - RISE * rise + sheetH)

    fun drawBounds(look: EnvelopeLook): Rect {
        val sheetTop = look.rise?.let { sheet(it).top } ?: 0f
        return Rect(-.04f, minOf(0f, -FLAP_IN, sheetTop) - .04f, 1.04f, 1.06f)
    }

    private fun light(r: Float, g: Float, b: Float) = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        r, 0f, 0f, 0f, 0f, 0f, g, 0f, 0f, 0f, 0f, 0f, b, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
    /** The tree's evening light on the generated paper (as the trunk's sprites, [TreeArt.sceneLight]). */
    val front = light(.66f, .66f, .70f)
    /**
     * N1's flap is painted lighter and cooler than N2's body (measured means 231/195/142 against 219/175/109): the same
     * light, matched per channel to the body, a touch brighter as the outer face that catches the light.
     */
    val flapOut = light(.645f, .61f, .575f)
    val sheetLight = light(.86f, .85f, .84f)
    /** N4's wax is painted bright (in daylight): the same evening light as the envelope it sits on. */
    val sealLight = light(.72f, .68f, .70f)
    val shadow = ColorFilter.tint(Color(0xFF140A04))
}

/** The envelope drawn in this DrawScope (its size = the envelope's body), back to front, with [look]. */
internal fun DrawScope.drawEnvelope(p: EnvelopeArt.Pics, look: EnvelopeLook) {
    val w = size.width; val h = size.height
    val whole = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1))
    // its shadow on the bark: the body's own silhouette, soft (three steps)
    for (k in 1..3) drawImage(p.body, dstOffset = IntOffset((w * .006f * k).roundToInt(), (h * .014f * k).roundToInt()), dstSize = whole,
        alpha = .14f, colorFilter = EnvelopeArt.shadow)
    // 1 the open envelope: the back panel's inside (and the pocket, drawn again over the letter below)
    drawImage(p.body, dstSize = whole, colorFilter = EnvelopeArt.front)
    val a = look.flap.coerceIn(0f, 1f) * PI.toFloat()
    val c = cos(a)
    // 2 the flap folded up past 90°: its inner face above the envelope, behind the letter (foreshortened by the turn)
    if (c < 0f) {
        val fh = (-c * EnvelopeArt.FLAP_IN * h).roundToInt()
        if (fh > 0) drawImage(p.flapIn, dstOffset = IntOffset(0, -fh), dstSize = IntSize(whole.width, fh), colorFilter = EnvelopeArt.front)
    }
    // 3 the letter inside (the sheet's top panel, folded), rising out of the pocket
    look.rise?.let { rise ->
        val s = EnvelopeArt.sheet(rise)
        drawSheet(p.sheet, Rect(s.left * w, s.top * h, s.right * w, s.bottom * h), unfold = EnvelopeArt.FOLD)
    }
    // 4 the pocket over the letter: the same picture, clipped to its measured opening (and everything below it)
    val pocket = Path().apply {
        moveTo(0f, h); EnvelopeArt.POCKET.forEachIndexed { i, (x, y) -> if (i == 0) lineTo(x * w, y * h) else lineTo(x * w, y * h) }
        lineTo(w, h); close()
    }
    clipPath(pocket) { drawImage(p.body, dstSize = whole, colorFilter = EnvelopeArt.front) }
    // 5 the flap closed / closing: its outer face hinged on the fold line, a soft shadow under its edges
    if (c >= 0f) {
        val fh = (c * EnvelopeArt.FLAP_OUT * h).roundToInt()
        if (fh > 0) {
            drawImage(p.flapOut, dstOffset = IntOffset(0, (h * .012f).roundToInt()), dstSize = IntSize(whole.width, fh), alpha = .35f, colorFilter = EnvelopeArt.shadow)
            drawImage(p.flapOut, dstSize = IntSize(whole.width, fh), colorFilter = EnvelopeArt.flapOut)
        }
    }
    // 6 the seal on the flap's tip, or the faint stain where it was
    val d = EnvelopeArt.SEAL_D * h
    val centre = Offset(w / 2, EnvelopeArt.TIP * h - d * .08f)
    if (look.waxMark && look.seal <= 0f && c > .9f)
        drawImage(p.seal, dstOffset = IntOffset((centre.x - d * .4f).roundToInt(), (centre.y - d * .4f).roundToInt()),
            dstSize = IntSize((d * .8f).roundToInt(), (d * .8f).roundToInt()), alpha = .12f)
    if (look.seal > 0f) drawSeal(p.seal, centre, d, look)
}

/** The wax seal: pressed on ([EnvelopeLook.seal]), rocking while the server is asked, lifting, cracking, falling away. */
private fun DrawScope.drawSeal(seal: ImageBitmap, c: Offset, d: Float, look: EnvelopeLook) {
    val on = look.seal.coerceIn(0f, 1f)
    // pressed on: it comes down from a little above, squashes and settles
    val press = if (on < 1f) 1f + (1f - on) * .5f else 1f
    val alpha = (on * 1.6f).coerceAtMost(1f)
    val k = look.crack.coerceIn(0f, 1f)
    val lift = (k / .3f).coerceAtMost(1f)
    val apart = ((k - .3f) / .7f).coerceIn(0f, 1f)
    val size = d * press * (1f + .06f * lift)
    val tl = Offset(c.x - size / 2, c.y - size / 2)
    // its shadow grows as it lifts off the paper
    drawCircle(Color(0x33140804).copy(alpha = (.20f + .25f * lift) * alpha * (1f - apart)), size * .52f, c + Offset(size * .05f * (1 + lift), size * .08f * (1 + 2 * lift)))
    fun half(left: Boolean) {
        val sign = if (left) -1f else 1f
        // a jagged crack through the middle
        val crack = Path().apply {
            val x0 = c.x; val top = tl.y - 2f; val bot = tl.y + size + 2f
            moveTo(if (left) tl.x - 4f else tl.x + size + 4f, top)
            lineTo(x0 + size * .02f, top); lineTo(x0 - size * .06f, tl.y + size * .3f); lineTo(x0 + size * .07f, tl.y + size * .52f)
            lineTo(x0 - size * .04f, tl.y + size * .74f); lineTo(x0 + size * .03f, bot)
            lineTo(if (left) tl.x - 4f else tl.x + size + 4f, bot); close()
        }
        val fall = apart * apart
        withTransform({
            translate(sign * size * .30f * apart, size * .9f * fall)
            rotate(sign * 34f * apart + look.rock * 4f, c)
        }) {
            clipPath(crack) {
                drawImage(seal, dstOffset = IntOffset(tl.x.roundToInt(), tl.y.roundToInt()), dstSize = IntSize(size.roundToInt(), size.roundToInt()),
                    alpha = alpha * (1f - ((apart - .55f) / .45f).coerceIn(0f, 1f)), colorFilter = EnvelopeArt.sealLight)
            }
        }
    }
    if (k <= 0f) {
        withTransform({ rotate(look.rock * 4f, c) }) {
            drawImage(seal, dstOffset = IntOffset(tl.x.roundToInt(), tl.y.roundToInt()), dstSize = IntSize(size.roundToInt(), size.roundToInt()), alpha = alpha, colorFilter = EnvelopeArt.sealLight)
        }
    } else {
        half(true); half(false)
        // the crack itself while the halves are still together
        if (apart < .2f) drawLine(Color(0x99200604), Offset(c.x, tl.y + size * .12f), Offset(c.x - size * .03f, tl.y + size * .88f), size * .025f * lift)
    }
}

/**
 * Letter paper in [r]: the delivered sheet (N3, deckled edge, its own folds) at its own aspect – the top part as high
 * as [r] at the same scale (never stretched); [unfold] < 1 shades the edge where it is still folded under.
 */
internal fun DrawScope.drawSheet(sheet: ImageBitmap, r: Rect, unfold: Float) {
    if (r.width <= 1f || r.height <= 1f) return
    val srcH = (sheet.width * r.height / r.width).roundToInt().coerceIn(1, sheet.height)
    val dst = IntSize(r.width.roundToInt(), r.height.roundToInt())
    // its shadow: the sheet's own silhouette, offset and soft
    for (k in 1..3) drawImage(sheet, srcOffset = IntOffset.Zero, srcSize = IntSize(sheet.width, srcH),
        dstOffset = IntOffset((r.left + k * r.width * .005f).roundToInt(), (r.top + k * r.width * .008f).roundToInt()), dstSize = dst,
        alpha = .12f, colorFilter = EnvelopeArt.shadow)
    drawImage(sheet, srcOffset = IntOffset.Zero, srcSize = IntSize(sheet.width, srcH),
        dstOffset = IntOffset(r.left.roundToInt(), r.top.roundToInt()), dstSize = dst, colorFilter = EnvelopeArt.sheetLight)
    if (unfold < .99f) drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0x30241608)), startY = r.bottom - r.height * .2f, endY = r.bottom),
        topLeft = Offset(r.left, r.bottom - r.height * .2f), size = Size(r.width, r.height * .2f))
}

/** An envelope with its layers at [rect] (dp, in the scene) – the same object on the trunk and looked at closely. */
@Composable
internal fun SceneEnvelope(rect: DpRect, tilt: Float, look: EnvelopeLook, tag: String,
                           pose: StoragePose = StoragePose(), storage: StorageFrame? = null, ink: (@Composable () -> Unit)? = null) {
    val pics = EnvelopeArt.of(LocalContext.current.resources)
    Box(Modifier.offset(x = rect.left, y = rect.top).size(rect.w, rect.h).graphicsLayer {
        rotationX = if (storage == null) pose.pitch else 0f; rotationY = if (storage == null) pose.yaw else 0f
        rotationZ = if (storage == null) tilt + pose.roll else 0f
        cameraDistance = 22f * density
        transformOrigin = if (pose == StoragePose()) androidx.compose.ui.graphics.TransformOrigin.Center
            else androidx.compose.ui.graphics.TransformOrigin(.5f, .7f)
    }.drawWithContent {
        if (storage == null) { drawContent() } else {
            val slot = storage.slot
            val scale = size.width / slot.width
            val canvas = drawContext.canvas.nativeCanvas
            canvas.save()
            // Clip in the furniture plane BEFORE projecting the whole face (including native ink).
            // The background's actual wood is revealed, rather than an invented cover rectangle.
            val wood = android.graphics.Path().apply {
                storage.occluders.forEach { polygon ->
                polygon.forEachIndexed { i, p ->
                    val x = (p.x - slot.left) * scale
                    val y = (p.y - slot.top - storage.dy) * scale
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }; close()
                }
            }
            // Once the raised face has cleared its own lip, it is in front of the furniture.
            // Keeping rear shelf boards as an all-phase mask would slice the reading envelope.
            if (storage.clearance < 1f) canvas.clipOutPath(wood)
            val dst = storage.corners.flatMap { p -> listOf((p.x - slot.left) * scale,
                (p.y - slot.top - storage.dy) * scale) }.toFloatArray()
            val matrix = android.graphics.Matrix().apply {
                check(setPolyToPoly(floatArrayOf(0f,0f,size.width,0f,size.width,size.height,0f,size.height),
                    0, dst, 0, 4)) { "Degenerate registered postal face" }
            }
            canvas.concat(matrix)
            val shade = (storage.light * 255).toInt().coerceIn(0,255)
            val paint = android.graphics.Paint().apply {
                colorFilter = android.graphics.LightingColorFilter(android.graphics.Color.rgb(shade,shade,shade), 0)
            }
            // Flap and extracted sheet legitimately extend above the envelope body.
            val bounds = EnvelopeArt.drawBounds(look)
            canvas.saveLayer(bounds.left * size.width, bounds.top * size.height,
                bounds.right * size.width, bounds.bottom * size.height, paint)
            drawContent()
            canvas.restore()
            canvas.restore()
        }
    }.semantics { this[StoragePoseKey] = pose; storage?.let { this[StorageFrameKey] = it } }.testTag(tag)) {
        Canvas(Modifier.fillMaxSize()) { drawEnvelope(pics, look) }
        ink?.invoke()
    }
}

/**
 * The letter floating above the scene: it grows from [from] (dp; the folded letter as it came out of the envelope, or the
 * pen) to a large sheet of natural proportions ([lift] 0 → 1), unfolding (more of the same sheet shows, nothing is
 * stretched). A tap beside it calls [onOutside]; taps, scrolling and typing on it never do. [footer] stays at the
 * paper's bottom (never scrolled away, never clipped).
 */
@Composable
internal fun FloatingPaper(
    from: DpRect, lift: Float, onOutside: () -> Unit, outsideLabel: String,
    /** Kept free under the paper (reading: the envelope's open top shows there, the letter floats over it). */
    below: Float = 0f, dim: Float = .30f,
    footer: (@Composable RowScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit,
) {
    val pics = EnvelopeArt.of(LocalContext.current.resources)
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime)) {
        val top = 58.dp; val side = 14.dp; val bottom = maxOf(14.dp, maxHeight * below)
        val pw = maxWidth - side * 2
        val ph = minOf(pw * EnvelopeArt.PAPER, maxHeight - top - bottom).coerceAtLeast(120.dp)
        val to = DpRect(side, top, pw, ph)
        val e = lift.coerceIn(0f, 1f)
        // uniform scale from the folded letter to the open sheet; the visible height unfolds from a third to all of it
        val w = from.w + (to.w - from.w) * e
        val u0 = (from.h / (to.h * (from.w / to.w))).coerceIn(.05f, 1f)
        val u = u0 + (1f - u0) * e
        val h = to.h * (w / to.w) * u
        val left = from.left + (to.left - from.left) * e
        val y = from.top + (to.top - from.top) * e
        // beside the paper: the outside (the tree dims a little behind a floating letter)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim * e))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = outsideLabel, onClick = onOutside)
            .semantics { contentDescription = outsideLabel; role = Role.Button }.testTag("tree-outside"))
        Box(Modifier.offset(x = left, y = y).size(w, h).pointerInput(Unit) { detectTapGestures { } }.testTag("tree-lifted")) {
            Canvas(Modifier.fillMaxSize()) { drawSheet(pics.sheet, Rect(0f, 0f, size.width, size.height), unfold = u) }
            if (e > .85f) Column(Modifier.fillMaxSize().graphicsLayer { alpha = ((e - .85f) / .15f).coerceIn(0f, 1f) }
                .padding(horizontal = 18.dp, vertical = 14.dp)) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("tree-page"), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
                if (footer != null) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).size(1.dp).background(Color(0x55241608)))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = footer)
                }
            }
        }
    }
}
