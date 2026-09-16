package at.gregor.layermaxxing

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    token: String,
    api: ApiClient,
    ownUserId: Long?,
    friends: List<ApiClient.UserSummary>,
    settings: List<ApiClient.FriendshipSettings>,
    threads: List<ApiClient.ChatThread>,
    initialFriend: Long?,
    onSelected: (Long?) -> Unit,
) {
    var selected by remember(initialFriend) { mutableStateOf(initialFriend) }
    var messages by remember { mutableStateOf<List<ApiClient.ChatMessage>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    // Chat is offered only where the friendship settings actually enabled it;
    // an absent settings row must not look like an enabled chat.
    val enabledFriends = friends.filter { friend -> settings.firstOrNull { it.friendId == friend.id }?.chatsEnabled == true }

    suspend fun reload() {
        selected?.let { friend ->
            runCatching { api.chatMessages(token, friend) }
                .onSuccess { messages = it; error = null }
                .onFailure { error = it.message }
        }
    }
    LaunchedEffect(selected) {
        // Clear the previous thread so a switch never shows stale messages.
        messages = emptyList(); error = null
        while (selected != null) { reload(); delay(5_000) }
    }

    if (selected == null) LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item { SocialTitle("Freundschafts-Chat") }
        item { Text("Sofortnachrichten sind AES-GCM-verschlüsselt gespeichert, aber ohne Zeitsperre sofort freigegeben.") }
        if (enabledFriends.isEmpty()) item { SocialInfo("Kein Freund hat Chat aktiviert.") }
        items(enabledFriends, key = { it.id }) { friend ->
            val thread = threads.firstOrNull { it.friendId == friend.id }
            Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("${friend.avatarEmoji} ${friend.name}", fontWeight = FontWeight.Bold)
                    Text(
                        thread?.let { "Zuletzt ${socialDate(it.lastMessageAt)} · ${it.unreadCount} ungelesen" } ?: "Noch keine Nachrichten",
                        fontSize = 12.sp,
                    )
                }
                Button(onClick = { selected = friend.id; onSelected(friend.id) }) { Text("Öffnen") }
            } }
        }
    } else Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val friend = friends.firstOrNull { it.id == selected }
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = { selected = null; onSelected(null) }) { Text("← Chats") }
            Text(friend?.name.orEmpty(), Modifier.weight(1f).padding(top = 12.dp), fontWeight = FontWeight.Bold)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(messages, key = { it.id }) { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.senderId == ownUserId) Arrangement.End else Arrangement.Start) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (message.senderId == ownUserId)
                            MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(18.dp),
                    ) { Column(Modifier.padding(11.dp)) {
                        Text(message.text)
                        Text(if (message.senderId == ownUserId && message.readAt != null) "✓ gelesen" else socialDate(message.createdAt), fontSize = 10.sp)
                    } }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), label = { Text("Nachricht") }, maxLines = 4)
            Button(onClick = { scope.launch {
                val sent = text.trim()
                if (sent.isNotEmpty()) runCatching { api.sendChat(token, selected!!, sent) }
                    .onSuccess { text = ""; reload() }.onFailure { error = it.message }
            } }, enabled = text.isNotBlank()) { Text("Senden") }
        }
    }
}

@Composable
fun EpScreen(
    token: String,
    api: ApiClient,
    settings: List<ApiClient.FriendshipSettings>,
    overview: ApiClient.EpOverview?,
    draft: EpPrompt?,
    onDraftConsumed: () -> Unit,
    act: ((suspend () -> Unit) -> Unit),
) {
    val suggestedFriend = draft?.friendId
    val suggestedLetter = draft?.letterId
    val enabled = settings.filter { it.epEnabled }
    var beneficiary by remember { mutableStateOf<Long?>(null) }
    var points by remember { mutableStateOf("10") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val linkedLetter = suggestedLetter.takeIf { beneficiary == suggestedFriend }
    LaunchedEffect(suggestedFriend, suggestedLetter) {
        if (suggestedFriend != null) beneficiary = suggestedFriend
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item { SocialTitle("EP") }
        item { Text("Ebenen-Punkte (wie Experience Points, nur auf mehreren Ebenen)") }
        item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(16.dp)) {
                Text(overview?.levelName ?: "Ebenen-Neuling", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Gegeben: ${overview?.given ?: 0} EP · Erhalten: ${overview?.received ?: 0} EP")
            }
        } }
        if (enabled.isEmpty()) item { SocialInfo("EP wird erst verfügbar, nachdem beide einer Freundschaftseinstellung mit aktivierten EP zugestimmt haben.") }
        else item { Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("EP vorschlagen", fontWeight = FontWeight.Bold)
            Text("Empfänger (nicht du selbst)")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                enabled.forEach { value ->
                    if (beneficiary == value.friendId) Button(onClick = {}) { Text(value.friendName) }
                    else OutlinedButton(onClick = { beneficiary = value.friendId }) { Text(value.friendName) }
                }
            }
            OutlinedTextField(points, { points = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(),
                label = { Text("EP (1–1000)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(title, { title = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("Titel (erforderlich)") })
            OutlinedTextField(description, { description = it.take(1000) }, Modifier.fillMaxWidth(), label = { Text("Beschreibung – optional") }, minLines = 2)
            if (linkedLetter != null) Text("Mit Brief #$linkedLetter verknüpft", fontSize = 12.sp)
            Button(onClick = { act {
                api.proposeEp(token, beneficiary!!, points.toInt(), title.trim(), description.trim().ifBlank { null }, linkedLetter)
                title = ""; description = ""; onDraftConsumed()
            } }, enabled = beneficiary != null && (points.toIntOrNull() ?: 0) in 1..1000 && title.isNotBlank()) { Text("EP-Vorschlag senden") }
        } } }
        overview?.incoming?.takeIf { it.isNotEmpty() }?.let { incoming ->
            item { SocialTitle("Eingehende EP") }
            items(incoming, key = { "ep-in-${it.id}" }) { proposal -> EpCard(proposal) {
                Button(onClick = { act { api.respondEp(token, proposal.id, true) } }) { Text("Annehmen") }
                TextButton(onClick = { act { api.respondEp(token, proposal.id, false) } }) { Text("Ablehnen") }
            } }
        }
        overview?.outgoing?.takeIf { it.isNotEmpty() }?.let { outgoing ->
            item { SocialTitle("Ausgehende EP") }
            items(outgoing, key = { "ep-out-${it.id}" }) { proposal -> EpCard(proposal) { Text("wartet") } }
        }
        item { SocialTitle("Angenommene EP-Geschichte") }
        overview?.history?.let { history ->
            if (history.isEmpty()) item { SocialInfo("Noch keine angenommenen EP.") }
            else items(history, key = { "ep-history-${it.id}" }) { proposal -> EpCard(proposal) {
                proposal.letterId?.let { Text("Brief #$it", fontSize = 12.sp) }
            } }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun EpCard(proposal: ApiClient.EpProposal, action: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(14.dp)) {
        Column(Modifier.weight(1f)) {
            Text("${proposal.points} EP · ${proposal.title}", fontWeight = FontWeight.Bold)
            Text("${proposal.proposerName} → ${proposal.beneficiaryName}", fontSize = 12.sp)
            proposal.description?.let { Text(it) }
        }
        Column { action() }
    } }
}

@Composable
private fun SocialTitle(text: String) = Text(text, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))

@Composable
private fun SocialInfo(text: String) = Card(Modifier.fillMaxWidth()) { Text(text, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }

private fun socialDate(epoch: Long): String = java.time.format.DateTimeFormatter.ofPattern("dd.MM. HH:mm")
    .withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochSecond(epoch))
