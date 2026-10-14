package at.gregor.layermaxxing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object ChatSelection {
    fun toggle(ids: Set<Long>, id: Long): Set<Long> = if (id in ids) ids - id else ids + id
    fun canDeleteAll(messages: List<ApiClient.ChatMessage>, ids: Set<Long>, ownUserId: Long?): Boolean =
        ids.isNotEmpty() && messages.filter { it.id in ids }.let { selected ->
            selected.size == ids.size && selected.all { ChatExtras.canDelete(it, ownUserId) }
        }
    fun fileName(message: ApiClient.ChatMessage): String {
        val extension = when (message.attachmentMime?.substringBefore(';')) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "audio/ogg" -> "ogg"
            "audio/mpeg" -> "mp3"
            else -> if (message.kind == "image") "jpg" else "m4a"
        }
        return "Layermaxxing-${message.id}.$extension"
    }
}

@Composable
internal fun SelectionToolbar(count: Int, onCancel: () -> Unit, onCopy: () -> Unit, canDelete: Boolean, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Harbour.palette().paperDeep).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onCancel) { AppIcon(R.drawable.ico_close, "Auswahl beenden", size = 20.dp) }
        Text("$count ausgewählt", Modifier.weight(1f), fontSize = 14.sp)
        TextButton(onClick = onCopy) { AppIcon(R.drawable.ico_copy, "Auswahl kopieren", size = 20.dp) }
        if (canDelete) TextButton(onClick = onDelete) { AppIcon(R.drawable.ico_delete, "Auswahl löschen", size = 20.dp) }
    }
}

/** Same picker for composing and reacting, with stable categories instead of sticker-shaped emoji. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EmojiPicker(title: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val categories = linkedMapOf(
        "Gesichter" to listOf("😀", "😊", "😂", "😚", "😍", "😘", "😎", "🤔", "😮", "😢", "😭", "😡", "😉", "😴", "🙃", "😌"),
        "Gesten" to listOf("👍", "👎", "👏", "🙌", "🙏", "🤝", "💪", "👋", "👌", "✌️", "🤞", "💌"),
        "Herzen" to listOf("❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "💟", "💕", "💖", "💔", "💯"),
        "Dinge" to listOf("🎉", "✨", "🔥", "⭐", "☀️", "🌴", "🏝️", "🌊", "🐻", "🦊", "🍕", "🍻", "☕", "🎵", "✅", "❓"),
    )
    var category by remember { mutableStateOf(categories.keys.first()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                categories.keys.forEach { name -> FilterChip(selected = category == name, onClick = { category = name }, label = { Text(name, fontSize = 12.sp) }) }
            }
            FlowRow(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                categories.getValue(category).forEach { emoji ->
                    Box(Modifier.size(48.dp).background(Harbour.palette().card, RoundedCornerShape(12.dp))
                        .clickable { onPick(emoji) }.semantics { contentDescription = "Emoji $emoji"; role = Role.Button }, Alignment.Center) {
                        Text(emoji, fontSize = 27.sp)
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } })
}
