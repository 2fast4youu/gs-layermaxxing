package at.gregor.layermaxxing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant

@Composable
fun ExtensionEntry(title: String, detail: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
fun GlossaryScreen() {
    var query by remember { mutableStateOf("") }
    val entries = AppGlossary.search(query)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Begriff suchen") }, singleLine = true) }
        item { Text("Das App-Wörterbuch", style = MaterialTheme.typography.headlineSmall) }
        if (entries.isEmpty()) item { Text("Kein Begriff gefunden. Versuche z. B. Themen, EP oder Tal.") }
        items(entries, key = { it.term }) { entry ->
            Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(entry.term, fontWeight = FontWeight.Bold)
                Text(entry.meaning)
                Text(entry.action, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            } }
        }
    }
}

@Composable
fun TopicsHub(topics: List<ApiClient.Topic>, friends: List<ApiClient.UserSummary>, groups: List<ApiClient.Group>, onOpen: (TopicScope, String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text("Nichts vergessen", style = MaterialTheme.typography.headlineSmall) }
        item { Text("Ein Stichwort reicht. Notiere Details und hake es nach eurem Gespräch ab.") }
        item { val scope = TopicScope.personal(); ExtensionEntry("🔒 Meine Themen", "${scope.filter(topics).count { it.completedAt == null }} offen · nur für dich") { onOpen(scope, "Meine Themen") } }
        items(friends, key = { "friend-${it.id}" }) { friend ->
            val scope = TopicScope.friend(friend.id)
            ExtensionEntry("${friend.avatarEmoji} Mit ${friend.name}", "${scope.filter(topics).count { it.completedAt == null }} offene Themen") { onOpen(scope, friend.name) }
        }
        items(groups, key = { "group-${it.id}" }) { group ->
            val scope = TopicScope.group(group.id)
            ExtensionEntry("👥 ${group.name}", "${scope.filter(topics).count { it.completedAt == null }} offene Gruppenthemen") { onOpen(scope, group.name) }
        }
    }
}

@Composable
fun GroupsHub(groups: List<ApiClient.Group>, friends: List<ApiClient.UserSummary>, topics: List<ApiClient.Topic>, settings: List<ApiClient.FriendshipSettings>, token: String, api: ApiClient, act: ((suspend () -> Unit) -> Unit), onTopics: (TopicScope, String) -> Unit) {
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var creating by remember { mutableStateOf(false) }
    var savingGroup by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var members by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var letterOpen by remember { mutableStateOf(false) }
    var body by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("60") }
    var sending by remember { mutableStateOf(false) }
    val group = groups.firstOrNull { it.id == selectedId }
    BackHandler(enabled = selectedId != null || creating) { selectedId = null; creating = false; letterOpen = false }
    LazyColumn(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (group != null) {
            item { TextButton(onClick = { selectedId = null; letterOpen = false }) { Text("← Alle Gruppen") } }
            item { Text(group.name, style = MaterialTheme.typography.headlineSmall); Text(group.members.joinToString { it.name }) }
            item { ExtensionEntry("📝 Gemeinsame Themen", "${TopicScope.group(group.id).filter(topics).count { it.completedAt == null }} offen") { onTopics(TopicScope.group(group.id), group.name) } }
            item { ExtensionEntry("✉ Gruppenbrief", "Ein versiegelter Brief an die Gruppe, kein Sofort-Gruppenchat") { letterOpen = !letterOpen } }
            if (letterOpen) item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Brief an ${group.name}", fontWeight = FontWeight.Bold)
                OutlinedTextField(title, { title = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Umschlagtitel (öffentlich)") })
                OutlinedTextField(body, { body = it.take(10000) }, Modifier.fillMaxWidth(), label = { Text("Versiegelter Inhalt") }, minLines = 3, maxLines = 8)
                OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(5) }, Modifier.fillMaxWidth(), label = { Text("Freigabe in Minuten") }, singleLine = true)
                val minimum = group.members.mapNotNull { member -> settings.firstOrNull { it.friendId == member.id }?.minLetterDelaySeconds }.maxOrNull() ?: 0
                val duration = minutes.toLongOrNull()?.times(60)
                Text("Die vereinbarte Mindestverzögerung gilt auch für Gruppenbriefe.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = {
                    sending = true
                    act {
                        try {
                            api.send(token, ApiClient.SendRequest(groupId = group.id, encrypted = CryptoBox.encrypt(body.trim()), title = title.trim(), mode = "timed", releaseAt = Instant.now().epochSecond + maxOf(duration ?: 3600, minimum)))
                            body = ""; title = ""; letterOpen = false
                        } finally { sending = false }
                    }
                }, enabled = !sending && body.isNotBlank() && duration != null && duration in 60..31536000, modifier = Modifier.fillMaxWidth()) { Text(if (sending) "Wird gesendet …" else "Gruppenbrief senden") }
            } } }
        } else {
            item { Text("Gemeinsam statt verstreut", style = MaterialTheme.typography.headlineSmall) }
            item { Text("Gruppen bündeln eure Themen und Briefe. Die Mitglieder sehen dieselben Gruppenthemen.") }
            item { Button(onClick = { creating = !creating }, modifier = Modifier.fillMaxWidth(), enabled = friends.isNotEmpty()) { Text(if (creating) "Abbrechen" else "+ Gruppe erstellen") } }
            if (friends.isEmpty()) item { Text("Füge zuerst einen Freund hinzu. Danach könnt ihr eine Gruppe bilden.") }
            if (creating) item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(60) }, Modifier.fillMaxWidth(), label = { Text("Gruppenname") }, singleLine = true)
                friends.forEach { friend -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(friend.id in members, { checked -> members = if (checked) members + friend.id else members - friend.id })
                    Text(friend.name)
                } }
                Button(onClick = {
                    savingGroup = true
                    act {
                        try {
                            api.createGroup(token, name.trim(), members.toList())
                            creating = false; name = ""; members = emptySet()
                        } finally { savingGroup = false }
                    }
                }, enabled = !savingGroup && name.isNotBlank() && members.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(if (savingGroup) "Wird erstellt …" else "Mit ausgewählten Freunden erstellen") }
            } } }
            if (groups.isEmpty()) item { Text("Noch keine Gruppen. Eine kleine Runde reicht für den Anfang.") }
            items(groups, key = { it.id }) { g -> ExtensionEntry("👥 ${g.name}", "${g.members.size} Mitglieder · ${TopicScope.group(g.id).filter(topics).count { it.completedAt == null }} offene Themen") { selectedId = g.id } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValleyActionGuide(friendName: String, openTopics: Int, onChat: () -> Unit, onTopics: () -> Unit, onGroups: () -> Unit, onGlossary: () -> Unit, onCourt: () -> Unit, onFriend: () -> Unit, onFriends: () -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    FilledTonalButton(onClick = { open = true }, modifier = modifier) { Text("☰ Tal-Aktionen · $openTopics Themen") }
    if (open) ModalBottomSheet(onDismissRequest = { open = false }) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
            item { Text("Dein Tal mit $friendName", style = MaterialTheme.typography.titleLarge) }
            item { Text("Tippe Orte an, verschiebe die Karte oder zoome mit zwei Fingern. Hier findest du die direkten Wege.") }
            item { ExtensionEntry("💬 Gespräch öffnen", "Chat und Briefe mit $friendName") { open = false; onChat() } }
            item { ExtensionEntry("📝 Themen am Schwarzen Brett", "$openTopics offen · sammeln und abhaken") { open = false; onTopics() } }
            item { ExtensionEntry("🏠 Eigener Hof", "Poststelle, Schatzkammer und Ausbau") { open = false; onCourt() } }
            item { ExtensionEntry("🏡 Freund besuchen", "Abgeleitete Ansicht, kein Fernzugriff auf dessen Gerät") { open = false; onFriend() } }
            item { ExtensionEntry("🧭 Tal wechseln", "Eine andere Freundschaft auswählen") { open = false; onFriends() } }
            item { ExtensionEntry("👥 Gruppen", "Gemeinsame Themen und Gruppenbriefe") { open = false; onGroups() } }
            item { ExtensionEntry("📖 Begriffe erklärt", "EP, Funke, Tal und Freigaberegeln verstehen") { open = false; onGlossary() } }
        }
    }
}
