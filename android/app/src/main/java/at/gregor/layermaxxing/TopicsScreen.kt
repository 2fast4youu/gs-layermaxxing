package at.gregor.layermaxxing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicsScreen(
    topics: List<ApiClient.Topic>,
    friends: List<ApiClient.UserSummary>,
    groups: List<ApiClient.Group>,
    token: String,
    api: ApiClient,
    act: ((suspend () -> Unit) -> Unit),
) {
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var peerId by remember { mutableStateOf<Long?>(null) }
    var groupId by remember { mutableStateOf<Long?>(null) }
    var targetSheet by remember { mutableStateOf(false) }
    var showDone by remember { mutableStateOf(false) }
    var deleteTopic by remember { mutableStateOf<ApiClient.Topic?>(null) }

    val open = topics.filter { it.completedAt == null }
    val done = topics.filter { it.completedAt != null }
    val targetLabel = when {
        groupId != null -> "👥 ${groups.firstOrNull { it.id == groupId }?.name ?: "Gruppe"}"
        peerId != null -> {
            val friend = friends.firstOrNull { it.id == peerId }
            "${friend?.avatarEmoji ?: "●"} ${friend?.name ?: "Freund"}"
        }
        else -> "🔒 Nur für mich"
    }

    if (targetSheet) ModalBottomSheet(onDismissRequest = { targetSheet = false }) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Text("Für wen ist das Thema?", fontSize = 23.sp, fontWeight = FontWeight.Bold) }
            item { Text("Gemeinsame Themen erscheinen automatisch bei allen Beteiligten.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                TopicTargetRow("🔒", "Nur für mich", "Private Merkliste", peerId == null && groupId == null) {
                    peerId = null; groupId = null; targetSheet = false
                }
            }
            items(friends, key = { "topic-friend-${it.id}" }) { friend ->
                TopicTargetRow(friend.avatarEmoji, friend.name, "Gemeinsame Gesprächsliste", peerId == friend.id) {
                    peerId = friend.id; groupId = null; targetSheet = false
                }
            }
            items(groups, key = { "topic-group-${it.id}" }) { group ->
                TopicTargetRow("👥", group.name, "Für alle ${group.members.size} Gruppenmitglieder", groupId == group.id) {
                    groupId = group.id; peerId = null; targetSheet = false
                }
            }
            item { Spacer(Modifier.height(22.dp)) }
        }
    }

    deleteTopic?.let { topic ->
        AlertDialog(
            onDismissRequest = { deleteTopic = null },
            title = { Text("Thema löschen?") },
            text = { Text("„${topic.title}“ wird für alle Beteiligten entfernt.") },
            confirmButton = { Button(onClick = { deleteTopic = null; act { api.deleteTopic(token, topic.id) } }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { deleteTopic = null }) { Text("Abbrechen") } },
        )
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Nichts mehr vergessen", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (open.isEmpty()) "Die Gesprächsliste ist leer." else "${open.size} offene${if (open.size == 1) "s Thema" else " Themen"} für eure nächsten Treffen.",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(120) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Thema") },
                        placeholder = { Text("Was möchten wir besprechen?") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = details,
                        onValueChange = { details = it.take(1000) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Notiz – optional") },
                        minLines = 2,
                        maxLines = 4,
                    )
                    OutlinedButton(onClick = { targetSheet = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(targetLabel, Modifier.weight(1f))
                        Text("Ändern")
                    }
                    Button(
                        onClick = {
                            act {
                                api.createTopic(token, title, details, peerId, groupId)
                                title = ""; details = ""
                            }
                        },
                        enabled = title.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) { Text("＋ Thema merken") }
                }
            }
        }

        item { Text("Offen", fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
        if (open.isEmpty()) item { TopicEmptyCard() }
        items(open, key = { "topic-open-${it.id}" }) { topic ->
            TopicCard(topic, completed = false, onToggle = { act { api.setTopicCompleted(token, topic.id, true) } }, onDelete = { deleteTopic = topic })
        }

        if (done.isNotEmpty()) {
            item {
                OutlinedButton(onClick = { showDone = !showDone }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showDone) "Erledigte ausblenden" else "Erledigt (${done.size}) anzeigen")
                }
            }
            if (showDone) items(done, key = { "topic-done-${it.id}" }) { topic ->
                TopicCard(topic, completed = true, onToggle = { act { api.setTopicCompleted(token, topic.id, false) } }, onDelete = { deleteTopic = topic })
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun TopicTargetRow(icon: String, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 22.sp, modifier = Modifier.width(42.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Text("✓", color = MaterialTheme.colorScheme.primary, fontSize = 20.sp)
        }
    }
}

@Composable
private fun TopicCard(topic: ApiClient.Topic, completed: Boolean, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (completed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
            Checkbox(checked = completed, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f).padding(top = 3.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(topic.title, fontWeight = FontWeight.SemiBold, color = if (completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                if (topic.details.isNotBlank()) Text(topic.details, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${topicTargetIcon(topic.targetType)} ${topic.targetName} · von ${topic.creatorName} · ${topicDate(topic.createdAt)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (completed) Text(
                    "Besprochen${topic.completedByName?.let { " von $it" }.orEmpty()}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (topic.canDelete) TextButton(onClick = onDelete) { Text("Löschen") }
        }
    }
}

@Composable
private fun TopicEmptyCard() = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("✓", fontSize = 32.sp, color = MaterialTheme.colorScheme.primary)
        Text("Alles besprochen", fontWeight = FontWeight.Bold)
        Text("Neue Gedanken kannst du oben sofort festhalten.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun topicTargetIcon(type: String) = when (type) { "group" -> "👥"; "friend" -> "●"; else -> "🔒" }
private fun topicDate(epoch: Long): String = DateTimeFormatter.ofPattern("dd.MM., HH:mm")
    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(epoch))
