package at.gregor.layermaxxing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch

@Composable
fun ExtensionEntry(title: String, detail: String, onClick: () -> Unit) {
    // "🦊 Gerfried" → avatar disc + name: emoji become portraits, not inline glyphs.
    val head = title.substringBefore(' ')
    val hasAvatar = title.contains(' ') && head.none { it.isLetterOrDigit() }
    val name = if (hasAvatar) title.substringAfter(' ') else title
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasAvatar) {
            Box(
                Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) { Text(head, style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text("›", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.outline)
    }
}

/**
 * The shared dictionary: built-in app terms plus words anyone added.
 *
 * Everyone signed in can add a word with an explanation or add another
 * explanation to an existing word; only the author edits or deletes their own
 * text. It loads by itself so a server without the glossary endpoint degrades
 * to the built-in terms instead of breaking the screen.
 */
@Composable
fun GlossaryScreen(api: ApiClient? = null, token: String? = null) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var shared by remember { mutableStateOf<List<ApiClient.GlossaryTerm>>(emptyList()) }
    var sharedAvailable by remember { mutableStateOf(api != null && token != null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var newTerm by remember { mutableStateOf("") }
    var newText by remember { mutableStateOf("") }
    var explainFor by remember { mutableStateOf<ApiClient.GlossaryTerm?>(null) }
    var editing by remember { mutableStateOf<ApiClient.GlossaryExplanation?>(null) }
    var draft by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        if (api == null || token == null) return
        runCatching { api.glossary(token) }
            .onSuccess { shared = it; sharedAvailable = true; error = null }
            .onFailure {
                // Older servers simply lack the endpoint: keep the built-in terms.
                if ((it as? ApiException)?.code == 404) sharedAvailable = false else error = it.message ?: "Wörterbuch nicht geladen"
            }
    }
    fun run(block: suspend () -> Unit) {
        if (api == null || token == null || busy) return
        busy = true
        scope.launch {
            runCatching { block() }.onFailure { error = it.message ?: "Speichern fehlgeschlagen" }
            reload(); busy = false
        }
    }
    LaunchedEffect(token) { reload() }

    val rows = Dictionary.rows(shared, query)
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
        ) {
            item {
                OutlinedTextField(
                    query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("Wort oder Erklärung suchen") }, leadingIcon = { Text("🔍") },
                    shape = RoundedCornerShape(24.dp),
                )
            }
            error?.let { item { Text(ApiErrors.friendly(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
            if (!sharedAvailable) item {
                Text(
                    "Nur lesen: Dein aktueller Server (Original) kennt das gemeinsame Wörterbuch noch nicht. Bearbeiten geht auf dem Testserver (Mehr → Server).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (rows.isEmpty()) item {
                Column(Modifier.padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("„${query.trim()}“ steht noch nicht drin.", fontWeight = FontWeight.Bold)
                    if (sharedAvailable) Button(onClick = { newTerm = query.trim(); newText = ""; adding = true }) { Text("Als neues Wort eintragen") }
                }
            }
            items(rows, key = { it.key }) { row ->
                val open = expanded == row.key
                Surface(
                    Modifier.fillMaxWidth().clickable { expanded = if (open) null else row.key },
                    shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(row.term, Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (row.builtIn != null) "App" else "${row.shared!!.explanations.size} ✍",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        row.builtIn?.let { entry ->
                            Text(entry.meaning)
                            if (open) Text(entry.action, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                            // Built-in words are editable too: the first own explanation turns them into a shared entry.
                            if (open && sharedAvailable) OutlinedButton(onClick = { newTerm = entry.term; newText = ""; adding = true }) { Text("＋ Eigene Erklärung") }
                        }
                        row.shared?.let { term ->
                            val shown = if (open) term.explanations else term.explanations.take(1)
                            shown.forEachIndexed { i, ex ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .3f))
                                Text(ex.text)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "— ${ex.authorName}" + if (ex.updatedAt > ex.createdAt) " · bearbeitet" else "",
                                        Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (open && ex.mine) {
                                        TextButton(onClick = { editing = ex; draft = ex.text }) { Text("Ändern") }
                                        TextButton(onClick = { run { api!!.deleteGlossaryExplanation(token!!, ex.id) } }) {
                                            Text("Löschen", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                            if (!open && term.explanations.size > 1) Text(
                                "+ ${term.explanations.size - 1} weitere Erklärung" + if (term.explanations.size > 2) "en" else "",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                            )
                            if (open) OutlinedButton(onClick = { explainFor = term; draft = "" }) { Text("＋ Eigene Erklärung") }
                        }
                    }
                }
            }
        }
        if (sharedAvailable) ExtendedFloatingActionButton(
            onClick = { newTerm = query.trim(); newText = ""; adding = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
            text = { Text("Wort hinzufügen") }, icon = { Text("＋") },
        )
    }

    if (adding) AlertDialog(
        onDismissRequest = { adding = false },
        title = { Text("Neues Wort") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(newTerm, { newTerm = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("Wort") }, singleLine = true)
                OutlinedTextField(newText, { newText = it.take(2000) }, Modifier.fillMaxWidth().heightIn(min = 110.dp), label = { Text("Erklärung") })
                Text(
                    "Für alle sichtbar. Gibt es das Wort schon, wird deine Erklärung dort ergänzt.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(enabled = Dictionary.canSubmit(newTerm, newText) && !busy, onClick = {
                val t = newTerm; val x = newText; adding = false
                expanded = null
                run { api!!.addGlossaryTerm(token!!, t, x) }
            }) { Text("Eintragen") }
        },
        dismissButton = { TextButton(onClick = { adding = false }) { Text("Abbrechen") } },
    )
    explainFor?.let { term ->
        ExplanationDialog("Erklärung zu „${term.term}“", draft, { draft = it }, busy, onDismiss = { explainFor = null }) {
            val x = draft; explainFor = null
            run { api!!.addGlossaryExplanation(token!!, term.id, x) }
        }
    }
    editing?.let { ex ->
        ExplanationDialog("Erklärung ändern", draft, { draft = it }, busy, onDismiss = { editing = null }) {
            val x = draft; editing = null
            run { api!!.editGlossaryExplanation(token!!, ex.id, x) }
        }
    }
}

@Composable
private fun ExplanationDialog(title: String, value: String, onValue: (String) -> Unit, busy: Boolean, onDismiss: () -> Unit, onSave: () -> Unit) =
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { onValue(it.take(2000)) }, Modifier.fillMaxWidth().heightIn(min = 120.dp), label = { Text("Erklärung") }) },
        confirmButton = { Button(enabled = value.isNotBlank() && !busy, onClick = onSave) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )

internal fun openTopics(n: Int): String = when (n) { 0 -> "Keine offenen Themen"; 1 -> "1 offenes Thema"; else -> "$n offene Themen" }

@Composable
fun TopicsHub(topics: List<ApiClient.Topic>, friends: List<ApiClient.UserSummary>, groups: List<ApiClient.Group>, onOpen: (TopicScope, String) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { Text("Themen fürs nächste Gespräch – abhaken, wenn erledigt.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
        item { val scope = TopicScope.personal(); ExtensionEntry("🔒 Nur für mich", openTopics(scope.filter(topics).count { it.completedAt == null })) { onOpen(scope, "Meine Themen") } }
        items(friends, key = { "friend-${it.id}" }) { friend ->
            val scope = TopicScope.friend(friend.id)
            ExtensionEntry("${friend.avatarEmoji} ${friend.name}", openTopics(scope.filter(topics).count { it.completedAt == null })) { onOpen(scope, friend.name) }
        }
        items(groups, key = { "group-${it.id}" }) { group ->
            val scope = TopicScope.group(group.id)
            ExtensionEntry("👥 ${group.name}", openTopics(scope.filter(topics).count { it.completedAt == null })) { onOpen(scope, group.name) }
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
    FilledTonalButton(onClick = { open = true }, modifier = modifier) { Text("☰ Insel-Aktionen · $openTopics Themen") }
    if (open) ModalBottomSheet(onDismissRequest = { open = false }) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
            item { Text("Eure Insel, $friendName", style = MaterialTheme.typography.titleLarge) }
            item { Text("Tippe Orte an, verschiebe die Karte oder zoome mit zwei Fingern. Hier findest du die direkten Wege.") }
            item { ExtensionEntry("💬 Gespräch öffnen", "Chat und Briefe mit $friendName") { open = false; onChat() } }
            item { ExtensionEntry("📝 Themen am Schwarzen Brett", "$openTopics offen · sammeln und abhaken") { open = false; onTopics() } }
            item { ExtensionEntry("🏠 Eigener Hof", "Poststelle, Schatzkammer und Ausbau") { open = false; onCourt() } }
            item { ExtensionEntry("🏡 Freund besuchen", "Abgeleitete Ansicht, kein Fernzugriff auf dessen Gerät") { open = false; onFriend() } }
            item { ExtensionEntry("🧭 Insel wechseln", "Eine andere Freundschaft auswählen") { open = false; onFriends() } }
            item { ExtensionEntry("👥 Gruppen", "Gemeinsame Themen und Gruppenbriefe") { open = false; onGroups() } }
            item { ExtensionEntry("📖 Begriffe erklärt", "Punkte, Funke, Inseln und Freigaberegeln verstehen") { open = false; onGlossary() } }
        }
    }
}
