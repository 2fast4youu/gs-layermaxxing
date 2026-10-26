package at.gregor.layermaxxing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Palette version is part of the hash; no contents, authentication or per-frame randomness. */
internal object PostalStamp {
    val subjects = listOf("Leuchtturm", "Segelschiff", "Mond", "Fuchs", "Baum", "Seestern")
    fun variant(id: Long): Int {
        var x = id xor 0x504F535431L
        x = (x xor (x ushr 33)) * -49064778989728563L
        x = (x xor (x ushr 33)) * -4265267296055464877L
        return Math.floorMod(x xor (x ushr 33), subjects.size.toLong()).toInt()
    }
    fun date(seconds: Long): String = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        .withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(seconds))
}

/** Drawn as envelope ink: inherits the original face projection, wood clipping and flap fade. */
@Composable
internal fun PostalStampInk(message: ApiClient.Message, flap: Float = 0f) {
    val variant = PostalStamp.variant(message.id)
    val date = PostalStamp.date(message.createdAt)
    Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = (1f - flap * 5f).coerceIn(0f, 1f) }
        .testTag("post-stamp-${message.id}").semantics {
            contentDescription = "Briefmarke ${PostalStamp.subjects[variant]} · Poststempel $date UTC"
        }) {
        val canvas = drawContext.canvas.nativeCanvas
        canvas.save()
        // Lower right corner: sender line and central wax stay clear.
        canvas.translate(size.width * .77f, size.height * .53f)
        val w = size.width * .19f; val h = size.height * .35f
        canvas.scale(w / 100f, h / 110f)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val colors = intArrayOf(0xFF526D68.toInt(), 0xFF536579.toInt(), 0xFF72618A.toInt(), 0xFF9A5A3E.toInt(), 0xFF59704B.toInt(), 0xFF91764A.toInt())
        paint.color = 0xFFE5CE99.toInt(); canvas.drawRect(0f, 0f, 100f, 110f, paint)
        // Perforation belongs to the printed paper; no emoji or white tile.
        paint.color = 0xFFAA9060.toInt()
        for (i in 0..10) {
            canvas.drawCircle(i * 10f, 0f, 2.5f, paint); canvas.drawCircle(i * 10f, 110f, 2.5f, paint)
            canvas.drawCircle(0f, i * 11f, 2.5f, paint); canvas.drawCircle(100f, i * 11f, 2.5f, paint)
        }
        paint.color = colors[variant]; paint.style = android.graphics.Paint.Style.STROKE; paint.strokeWidth = 2f
        canvas.drawRect(8f, 8f, 92f, 102f, paint)
        fun path(vararg points: Float) {
            val p = android.graphics.Path(); p.moveTo(points[0], points[1])
            for (i in 2 until points.size step 2) p.lineTo(points[i], points[i + 1])
            canvas.drawPath(p, paint)
        }
        when (variant) {
            0 -> { path(33f,80f, 40f,39f, 60f,39f, 67f,80f, 33f,80f); canvas.drawRect(36f,28f,64f,39f,paint); path(31f,28f,50f,16f,69f,28f); path(50f,42f,50f,53f); path(25f,36f,12f,42f); path(75f,36f,88f,42f) }
            1 -> { path(18f,69f,28f,82f,72f,82f,84f,69f,18f,69f); path(49f,20f,49f,67f,24f,61f,49f,20f); path(54f,27f,77f,61f,54f,61f) }
            2 -> { canvas.drawCircle(49f,49f,27f,paint); path(57f,24f,43f,36f,40f,53f,50f,66f,65f,69f); for (i in 0..3) { val x=20f+i*20; path(x,87f,x+3,81f,x+6,87f) } }
            3 -> { path(27f,37f,24f,18f,43f,34f,59f,34f,78f,18f,74f,58f,51f,79f,27f,58f,27f,37f); path(30f,49f,51f,70f,72f,49f); canvas.drawCircle(39f,46f,2f,paint); canvas.drawCircle(62f,46f,2f,paint) }
            4 -> { path(45f,85f,45f,47f,55f,47f,55f,85f); canvas.drawCircle(50f,39f,24f,paint); canvas.drawCircle(31f,53f,15f,paint); canvas.drawCircle(68f,52f,16f,paint); path(24f,87f,78f,87f) }
            else -> { path(50f,18f,59f,43f,85f,44f,65f,61f,72f,86f,50f,72f,28f,86f,35f,61f,15f,44f,41f,43f,50f,18f); canvas.drawCircle(50f,55f,5f,paint) }
        }
        // Fine engraved water/ground hatching.
        for (i in 0..4) path(18f+i*15f,92f,26f+i*15f,89f)
        canvas.restore()
        // Cancellation is the final translucent ink layer and crosses stamp onto envelope paper.
        canvas.save(); canvas.rotate(-12f, size.width * .83f, size.height * .76f)
        paint.color = 0xB34A3D30.toInt(); paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = size.width * .003f
        val cx = size.width * .83f; val cy = size.height * .75f
        canvas.drawOval(cx-size.width*.145f,cy-size.height*.13f,cx+size.width*.145f,cy+size.height*.13f,paint)
        paint.style = android.graphics.Paint.Style.FILL; paint.textAlign = android.graphics.Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
        paint.textSize = size.width * .034f
        canvas.drawText(date,cx,cy+paint.textSize*.3f,paint)
        paint.style = android.graphics.Paint.Style.STROKE
        for (i in 0..2) canvas.drawLine(size.width*.9f,cy+(i-1)*size.height*.045f,size.width*.985f,cy+(i-1)*size.height*.045f,paint)
        canvas.restore()
    }
}
