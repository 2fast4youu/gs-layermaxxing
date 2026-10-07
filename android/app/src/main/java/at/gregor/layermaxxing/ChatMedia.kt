package at.gregor.layermaxxing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Painted harbour stickers – drawn, never emoji, so they match the island.
// ---------------------------------------------------------------------------

@Composable
internal fun HarbourSticker(key: String, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size).semantics { contentDescription = "Sticker " + ChatExtras.stickerLabel(key) }) {
        val w = this.size.width; val h = this.size.height; val c = Offset(w / 2, h / 2)
        val ink = Color(0xFF22303F)
        // Every sticker sits on a small round paper disc with a white rim.
        drawCircle(Color.White, radius = w * .48f)
        drawCircle(Color(0xFFFFF5DD), radius = w * .44f)
        when (key) {
            "sun" -> {
                repeat(10) { i ->
                    val a = Math.toRadians(i * 36.0)
                    drawLine(Color(0xFFF0A93B), Offset(c.x + cos(a).toFloat() * w * .22f, c.y + sin(a).toFloat() * w * .22f),
                        Offset(c.x + cos(a).toFloat() * w * .36f, c.y + sin(a).toFloat() * w * .36f), strokeWidth = w * .05f)
                }
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE07A), Color(0xFFF5B336)), c, w * .2f), radius = w * .19f)
            }
            "heart" -> {
                val p = Path().apply {
                    moveTo(c.x, h * .74f)
                    cubicTo(w * .14f, h * .52f, w * .2f, h * .22f, c.x, h * .36f)
                    cubicTo(w * .8f, h * .22f, w * .86f, h * .52f, c.x, h * .74f)
                    close()
                }
                drawPath(p, Brush.verticalGradient(listOf(Color(0xFFF07A6E), Color(0xFFC9473F))))
                drawCircle(Color.White.copy(alpha = .55f), radius = w * .04f, center = Offset(w * .36f, h * .38f))
            }
            "wave" -> {
                drawCircle(Color(0xFF8FD0DB), radius = w * .34f, center = c)
                val p = Path().apply {
                    moveTo(w * .18f, h * .62f)
                    quadraticBezierTo(w * .32f, h * .28f, w * .56f, h * .38f)
                    quadraticBezierTo(w * .44f, h * .44f, w * .5f, h * .56f)
                    quadraticBezierTo(w * .66f, h * .66f, w * .82f, h * .56f)
                    lineTo(w * .82f, h * .72f); lineTo(w * .18f, h * .72f); close()
                }
                drawPath(p, Color(0xFF2F7F95))
            }
            "palm" -> {
                drawOval(Color(0xFFEBD6A6), topLeft = Offset(w * .2f, h * .66f), size = Size(w * .6f, h * .14f))
                val trunk = Path().apply {
                    moveTo(w * .47f, h * .7f); quadraticBezierTo(w * .44f, h * .48f, w * .54f, h * .32f)
                    lineTo(w * .58f, h * .34f); quadraticBezierTo(w * .5f, h * .5f, w * .53f, h * .7f); close()
                }
                drawPath(trunk, Color(0xFF8A5A33))
                listOf(-150.0, -110.0, -60.0, -20.0, 20.0).forEach { deg ->
                    val a = Math.toRadians(deg); val tip = Offset(w * .56f + cos(a).toFloat() * w * .26f, h * .32f + sin(a).toFloat() * h * .18f + h * .06f)
                    val leaf = Path().apply {
                        moveTo(w * .56f, h * .32f)
                        quadraticBezierTo((w * .56f + tip.x) / 2, (h * .32f + tip.y) / 2 - h * .08f, tip.x, tip.y)
                        quadraticBezierTo((w * .56f + tip.x) / 2, (h * .32f + tip.y) / 2, w * .56f, h * .32f)
                    }
                    drawPath(leaf, Color(0xFF4E9B4A))
                }
            }
            "boat" -> {
                drawLine(Color(0xFF8FD0DB), Offset(w * .16f, h * .72f), Offset(w * .84f, h * .72f), strokeWidth = w * .04f)
                val hull = Path().apply { moveTo(w * .2f, h * .58f); lineTo(w * .8f, h * .58f); lineTo(w * .68f, h * .7f); lineTo(w * .32f, h * .7f); close() }
                drawPath(hull, Color(0xFFB3473C))
                drawLine(ink, Offset(w * .5f, h * .58f), Offset(w * .5f, h * .22f), strokeWidth = w * .025f)
                val sail = Path().apply { moveTo(w * .52f, h * .24f); lineTo(w * .74f, h * .54f); lineTo(w * .52f, h * .54f); close() }
                drawPath(sail, Color.White); drawPath(sail, ink.copy(alpha = .3f), style = Stroke(w * .012f))
                val flag = Path().apply { moveTo(w * .5f, h * .22f); lineTo(w * .4f, h * .26f); lineTo(w * .5f, h * .3f); close() }
                drawPath(flag, Color(0xFFC8962F))
            }
            "star" -> {
                val p = Path()
                repeat(10) { i ->
                    val r = if (i % 2 == 0) w * .32f else w * .14f
                    val a = Math.toRadians(-90.0 + i * 36.0)
                    val pt = Offset(c.x + cos(a).toFloat() * r, c.y + sin(a).toFloat() * r)
                    if (i == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
                }
                p.close()
                drawPath(p, Brush.verticalGradient(listOf(Color(0xFFFFE18C), Color(0xFFD7A54A))))
            }
            "shell" -> {
                val p = Path().apply {
                    moveTo(c.x, h * .74f)
                    cubicTo(w * .12f, h * .6f, w * .2f, h * .22f, c.x, h * .24f)
                    cubicTo(w * .8f, h * .22f, w * .88f, h * .6f, c.x, h * .74f); close()
                }
                drawPath(p, Brush.verticalGradient(listOf(Color(0xFFFFD3C2), Color(0xFFF0A08A))))
                listOf(-.22f, -.1f, 0f, .1f, .22f).forEach { dx ->
                    drawLine(Color(0xFFC9705A), Offset(c.x, h * .72f), Offset(c.x + dx * w, h * .3f), strokeWidth = w * .015f)
                }
            }
            "lighthouse" -> {
                val body = Path().apply { moveTo(w * .4f, h * .76f); lineTo(w * .6f, h * .76f); lineTo(w * .56f, h * .34f); lineTo(w * .44f, h * .34f); close() }
                drawPath(body, Color.White)
                drawRect(Color(0xFFC9473F), Offset(w * .418f, h * .44f), Size(w * .164f, h * .07f))
                drawRect(Color(0xFFC9473F), Offset(w * .405f, h * .6f), Size(w * .19f, h * .07f))
                drawPath(body, ink.copy(alpha = .35f), style = Stroke(w * .012f))
                drawRect(Color(0xFFFFE07A), Offset(w * .45f, h * .26f), Size(w * .1f, h * .08f))
                val roof = Path().apply { moveTo(w * .42f, h * .26f); lineTo(w * .58f, h * .26f); lineTo(w * .5f, h * .18f); close() }
                drawPath(roof, ink)
                drawLine(Color(0xFFFFE07A).copy(alpha = .6f), Offset(w * .56f, h * .3f), Offset(w * .82f, h * .24f), strokeWidth = w * .03f)
            }
        }
    }
}

/** The sticker drawer above the composer. */
@Composable
internal fun StickerTray(p: HarbourPalette, onPick: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(p.paperDeep).horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChatExtras.STICKERS.forEach { key ->
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(p.card).clickable { onPick(key) }
                    .semantics { role = Role.Button; contentDescription = "Sticker ${ChatExtras.stickerLabel(key)} senden" },
                contentAlignment = Alignment.Center,
            ) { HarbourSticker(key, 52.dp) }
        }
    }
}

// ---------------------------------------------------------------------------
// Reactions
// ---------------------------------------------------------------------------

/** The row of quick reactions at the top of a line's action sheet. */
@Composable
internal fun ReactionBar(current: String?, p: HarbourPalette, onPick: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .background(p.card, RoundedCornerShape(28.dp)).border(1.dp, p.line, RoundedCornerShape(28.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ChatExtras.QUICK_REACTIONS.forEach { emoji ->
            val chosen = emoji == current
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(if (chosen) p.sea.copy(alpha = .22f) else Color.Transparent)
                    .clickable { onPick(emoji) }
                    .semantics { role = Role.Button; contentDescription = if (chosen) "Reaktion $emoji entfernen" else "Mit $emoji reagieren" },
                contentAlignment = Alignment.Center,
            ) { Text(emoji, fontSize = 24.sp) }
        }
    }
}

/** Small reaction chips under a bubble; tapping toggles my reaction. */
@Composable
internal fun ReactionChips(chips: List<ChatExtras.ReactionChip>, p: HarbourPalette, onTap: (String) -> Unit) {
    if (chips.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        chips.forEach { chip ->
            Row(
                Modifier.heightIn(min = 26.dp).clip(RoundedCornerShape(13.dp))
                    .background(if (chip.mine) p.sea.copy(alpha = .2f) else p.card)
                    .border(1.dp, if (chip.mine) p.sea.copy(alpha = .5f) else p.line, RoundedCornerShape(13.dp))
                    .clickable { onTap(chip.emoji) }
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(chip.emoji, fontSize = 13.sp)
                if (chip.count > 1) Text(" ${chip.count}", fontSize = 11.sp, color = p.ink, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Pinned memories
// ---------------------------------------------------------------------------

/** The thin strip of pinned lines under the head; tap jumps, ✕ unpins. */
@Composable
internal fun PinnedStrip(pinned: List<ApiClient.ChatMessage>, p: HarbourPalette, onJump: (Long) -> Unit) {
    if (pinned.isEmpty()) return
    val latest = pinned.maxBy { it.pinnedAt ?: 0 }
    Row(
        Modifier.fillMaxWidth().background(p.card).clickable { onJump(latest.id) }
            .semantics { role = Role.Button; contentDescription = "Angepinnt: ${ChatExtras.previewText(latest)}" }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(30.dp).background(p.gold, RoundedCornerShape(2.dp)))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(if (pinned.size == 1) "Angepinnt" else "${pinned.size} angepinnt", fontSize = 11.sp, color = p.gold, fontWeight = FontWeight.Bold)
            Text(ChatExtras.previewText(latest), fontSize = 13.sp, color = p.ink, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------------------
// Chat → Quest
// ---------------------------------------------------------------------------

/** A chat message as a quest: the same single quest form, prefilled from the message. */
@Composable
internal fun ChatQuestDialog(
    friendName: String, draft: ChatExtras.QuestDraft,
    onCreate: (ChatExtras.QuestDraft) -> Unit, onDismiss: () -> Unit,
) {
    NewQuestSheet(
        pals = listOf(ApiClient.UserSummary(0, friendName, "friends", "", "")), groups = emptyList(),
        presetPeer = 0, presetGroup = null, draft = draft, onDismiss = onDismiss,
        onCreate = { t, d, icon, pts, _, _ -> onCreate(ChatExtras.QuestDraft(t, d, icon, pts)) },
    )
}

internal val QUEST_ICON_GLYPHS = listOf(
    "star" to "⭐", "hike" to "🥾", "grill" to "🔥", "bike" to "🚲", "food" to "🍝",
    "game" to "🎲", "travel" to "🧳", "sport" to "⚽", "music" to "🎵", "help" to "🤝",
)

// ---------------------------------------------------------------------------
// Glossary
// ---------------------------------------------------------------------------

@Composable
internal fun GlossaryTermDialog(term: ApiClient.GlossaryTerm, onDismiss: () -> Unit) {
    val p = Harbour.palette()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { AppIcon(R.drawable.ico_glossary, null, size = 24.dp) }, title = { Text(term.term, style = Harbour.Title.copy(fontSize = 20.sp)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (term.explanations.isEmpty()) Text("Noch keine Erklärung im Wörterbuch.", color = p.inkSoft)
                term.explanations.take(3).forEach { e ->
                    Column {
                        Text(e.text, color = p.ink, fontSize = 14.sp)
                        Text("– ${e.authorName}", color = p.inkSoft, fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}

// ---------------------------------------------------------------------------
// Photos
// ---------------------------------------------------------------------------

/** Reads, downsizes and JPEG-encodes a picked photo off the main thread. */
internal suspend fun preparePhoto(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= ChatExtras.PHOTO_EDGE) sample *= 2
        val raw = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching null
        val (w, h) = ChatExtras.scaledSize(raw.width, raw.height)
        val scaled = if (w == raw.width && h == raw.height) raw else Bitmap.createScaledBitmap(raw, w, h, true)
        ByteArrayOutputStream().use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, 82, out); out.toByteArray() }
    }.getOrNull()
}

/** A photo line: loads and decrypts on demand, then shows the picture. */
@Composable
internal fun ChatPhoto(load: suspend () -> ByteArray, p: HarbourPalette, onOpen: (Bitmap) -> Unit) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { load().let { BitmapFactory.decodeByteArray(it, 0, it.size) } } }
            .onSuccess { bitmap = it; failed = it == null }.onFailure { failed = true }
    }
    Box(
        Modifier.widthIn(max = 240.dp).heightIn(min = 120.dp).clip(RoundedCornerShape(14.dp)).background(p.paperDeep)
            .clickable(enabled = bitmap != null) { bitmap?.let(onOpen) },
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        when {
            b != null -> Image(b.asImageBitmap(), "Foto", Modifier.widthIn(max = 240.dp).heightIn(max = 300.dp), contentScale = ContentScale.Fit)
            failed -> Text("Foto nicht verfügbar", color = p.inkSoft, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
            else -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = p.sea)
        }
    }
}

// ---------------------------------------------------------------------------
// Voice notes – „Flaschenpost“
// ---------------------------------------------------------------------------

/** Records one short voice note to a cache file (AAC in MP4). */
internal class BottleRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    fun start(): Boolean = runCatching {
        val f = File.createTempFile("bottle", ".m4a", context.cacheDir)
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(48_000)
        r.setAudioSamplingRate(22_050)
        r.setMaxDuration(ChatExtras.VOICE_MAX_MS.toInt())
        r.setOutputFile(f.absolutePath)
        r.prepare(); r.start()
        recorder = r; file = f; startedAt = System.currentTimeMillis()
        true
    }.getOrElse { cancel(); false }

    fun elapsedMs(): Long = if (recorder == null) 0 else System.currentTimeMillis() - startedAt

    /** Stops and returns bytes plus duration, or null when too short or failed. */
    fun stop(): Pair<ByteArray, Long>? {
        val r = recorder ?: return null
        val duration = elapsedMs()
        val ok = runCatching { r.stop() }.isSuccess
        r.release(); recorder = null
        val bytes = file?.takeIf { ok && duration >= 700 }?.readBytes()
        file?.delete(); file = null
        return bytes?.let { it to duration }
    }

    fun cancel() {
        runCatching { recorder?.stop() }
        recorder?.release(); recorder = null
        file?.delete(); file = null
    }
}

/** A bottle-post line: a small bottle, play/stop and the length. */
@Composable
internal fun VoiceNote(label: String, load: suspend () -> ByteArray, tint: Color, p: HarbourPalette) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var loading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    DisposableEffect(Unit) { onDispose { player?.release() } }
    LaunchedEffect(player) {
        while (player != null) {
            player?.let { pl -> runCatching { if (pl.duration > 0) progress = pl.currentPosition.toFloat() / pl.duration } }
            delay(120)
        }
        progress = 0f
    }
    var pending by remember { mutableStateOf(false) }
    LaunchedEffect(pending) {
        if (!pending) return@LaunchedEffect
        loading = true
        val f = runCatching {
            withContext(Dispatchers.IO) { File.createTempFile("bottle_play", ".m4a", context.cacheDir).apply { writeBytes(load()) } }
        }.getOrNull()
        loading = false; pending = false
        if (f != null) runCatching {
            MediaPlayer().apply {
                setDataSource(f.absolutePath); prepare(); start()
                setOnCompletionListener { it.release(); player = null; f.delete() }
            }
        }.onSuccess { player = it }
    }
    Row(Modifier.widthIn(min = 180.dp).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(p.sea).clickable {
                val current = player
                if (current != null) { runCatching { current.stop() }; current.release(); player = null } else if (!loading) pending = true
            }.semantics { role = Role.Button; contentDescription = if (player != null) "Flaschenpost stoppen" else "Flaschenpost abspielen" },
            contentAlignment = Alignment.Center,
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
            else AppIcon(if (player != null) R.drawable.ico_stop else R.drawable.ico_play, null, tint = Color.White, size = 16.dp)
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Canvas(Modifier.fillMaxWidth().height(18.dp)) {
                val bars = 22; val gap = size.width / bars
                repeat(bars) { i ->
                    val hgt = size.height * (.3f + .7f * ((i * 37 % 11) / 10f))
                    val played = i.toFloat() / bars < progress
                    drawLine(if (played) p.sea else tint.copy(alpha = .35f), Offset(i * gap + gap / 2, (size.height - hgt) / 2),
                        Offset(i * gap + gap / 2, (size.height + hgt) / 2), strokeWidth = gap * .5f)
                }
            }
            Text("Flaschenpost · $label", fontSize = 11.sp, color = tint.copy(alpha = .7f))
        }
    }
}

/** Full-screen photo view. */
@Composable
internal fun PhotoViewer(bitmap: Bitmap, onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().background(Color.Black).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            Image(bitmap.asImageBitmap(), "Foto", Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
            Spacer(Modifier.height(1.dp))
        }
    }
}
