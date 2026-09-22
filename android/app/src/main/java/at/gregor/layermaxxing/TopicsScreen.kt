package at.gregor.layermaxxing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Conversation-scoped tags, entered like chat messages.
 *
 * The list reads top-down and the input line sits at the bottom, so noting a
 * keyword is one tap and one Done — not a form with two fields and a button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicsScreen(
    topics: List<ApiClient.Topic>,
    friend: ApiClient.UserSummary,
    token: String,
    api: ApiClient,
    act: ((suspend () -> Unit) -> Unit),
    autoFocus: Boolean = false,
) {
    var title by remember(friend.id) { mutableStateOf("") }
    var details by remember(friend.id) { mutableStateOf("") }
    var noteOpen by remember(friend.id) { mutableStateOf(false) }
    var showDone by remember(friend.id) { mutableStateOf(false) }
    var detailTopic by remember { mutableStateOf<ApiClient.Topic?>(null) }
    var deleteTopic by remember { mutableStateOf<ApiClient.Topic?>(null) }
    // Opened as a sheet the keyword field owns the moment: one tap, then type.
    val keywordFocus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(friend.id) { delay(250); runCatching { keywordFocus.requestFocus() } }

    val conversationTopics = Conversations.topicsWith(friend.id, friend.name, topics)
    val open = conversationTopics.filter { it.completedAt == null }
    val done = conversationTopics.filter { it.completedAt != null }

    fun create() {
        val keyword = title.trim()
        if (keyword.isBlank()) return
        val note = details.trim()
        act {
            api.createTopic(token, keyword, note, friend.id, null)
            title = ""
            details = ""
            noteOpen = false
        }
    }

    deleteTopic?.let { topic ->
        AlertDialog(
            onDismissRequest = { deleteTopic = null },
            title = { Text("Thema löschen?") },
            text = { Text("„${topic.title}“ wird für euch beide entfernt.") },
            confirmButton = {
                Button(onClick = { deleteTopic = null; detailTopic = null; act { api.deleteTopic(token, topic.id) } }) {
                    Text("Löschen")
                }
            },
            dismissButton = { TextButton(onClick = { deleteTopic = null }) { Text("Abbrechen") } },
        )
    }

    // Tapping a keyword opens its details; the list itself stays a plain list.
    detailTopic?.let { topic ->
        val current = conversationTopics.firstOrNull { it.id == topic.id } ?: topic
        ModalBottomSheet(onDismissRequest = { detailTopic = null }) {
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text(current.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "von ${current.creatorName} · ${topicDate(current.createdAt)}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (current.details.isNotBlank()) Text(current.details)
                else Text(
                    "Keine Notiz. Notizen entstehen beim Anlegen des Stichworts; nachträgliches Bearbeiten kann der Server noch nicht.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        val completed = current.completedAt == null
                        detailTopic = null
                        act { api.setTopicCompleted(token, current.id, completed) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (current.completedAt == null) "Als besprochen markieren" else "Wieder öffnen") }
                if (current.canDelete) OutlinedButton(
                    onClick = { deleteTopic = current },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Löschen") }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            item {
                Text(
                    "Stichworte mit ${friend.name} · ${open.size} offen",
                    Modifier.padding(top = 10.dp),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (open.isEmpty()) item { TopicEmptyCard() }
            items(open, key = { "topic-open-${it.id}" }) { topic ->
                TopicRow(
                    topic,
                    completed = false,
                    onToggle = { act { api.setTopicCompleted(token, topic.id, true) } },
                    onOpen = { detailTopic = topic },
                )
            }
            if (done.isNotEmpty()) {
                item {
                    OutlinedButton(onClick = { showDone = !showDone }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (showDone) "Erledigte ausblenden" else "Erledigt (${done.size}) anzeigen")
                    }
                }
                if (showDone) items(done, key = { "topic-done-${it.id}" }) { topic ->
                    TopicRow(
                        topic,
                        completed = true,
                        onToggle = { act { api.setTopicCompleted(token, topic.id, false) } },
                        onOpen = { detailTopic = topic },
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().imePadding()) {
            if (noteOpen) OutlinedTextField(
                details, { details = it.take(1000) },
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
                label = { Text("Kurze Notiz – optional") }, minLines = 2, maxLines = 4,
            )
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(120) },
                    modifier = Modifier.weight(1f).focusRequester(keywordFocus),
                    placeholder = { Text("Stichwort notieren …") },
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { create() }),
                )
                Surface(
                    modifier = Modifier.clickable { noteOpen = !noteOpen },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) { Text(if (noteOpen) "－" else "＋", Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) }
                Button(onClick = { create() }, enabled = title.isNotBlank()) { Text("Merken") }
            }
        }
    }
}

@Composable
private fun TopicRow(topic: ApiClient.Topic, completed: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = if (completed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = completed, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(topic.title, fontWeight = FontWeight.SemiBold)
                if (topic.details.isNotBlank()) Text(
                    topic.details, fontSize = 12.sp, maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TopicEmptyCard() = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("✓", fontSize = 28.sp, color = MaterialTheme.colorScheme.primary)
        Text("Keine offenen Stichworte", fontWeight = FontWeight.Bold)
    }
}

private fun topicDate(epoch: Long): String = DateTimeFormatter.ofPattern("dd.MM., HH:mm")
    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(epoch))
