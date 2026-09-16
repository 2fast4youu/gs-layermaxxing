package at.gregor.layermaxxing

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.provider.Settings
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

private enum class HomeTab(val icon: String, val label: String, val title: String) {
    INBOX("✉", "Briefe", "Briefe"), CHAT("◌", "Chat", "Chat"), SEND("＋", "Neu", "Neuer Brief"),
    EP("★", "EP", "Ebenen-Punkte"), TOPICS("☷", "Themen", "Gesprächsthemen"),
    FRIENDS("●", "Leute", "Kontakte"), ACCOUNT("⚙", "Mehr", "Mehr")
}
private enum class ComposeMode(val label: String, val api: String) {
    DURATION("Nach einer Dauer", "timed"), DATE_TIME("Zu einem Zeitpunkt", "timed"), MANUAL("Von mir freigeben", "manual"),
    MUTUAL("Wenn beide zustimmen", "mutual"), PRESENCE("Wenn beide online sind", "presence"), RANDOM("Zufällig", "random")
}
private enum class DelayUnit(val label: String, val seconds: Long) {
    MINUTES("Minuten", 60), HOURS("Stunden", 3600), DAYS("Tage", 86400)
}
private data class OpenedMessage(val text: String, val attachment: ByteArray?, val name: String?, val mime: String?)
data class EpPrompt(val friendId: Long, val letterId: Long?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayerHome(
    token: String, api: ApiClient, store: SessionStore, initialRecoveryCode: String?,
    onRecoveryCodeSeen: () -> Unit, onTheme: (String) -> Unit, onLogout: () -> Unit,
    accounts: List<SavedAccount>, onSwitchAccount: (SavedAccount) -> Unit,
    serverProfile: ServerProfile, onServerProfile: (ServerProfile) -> Unit,
) {
    var tab by remember { mutableStateOf(HomeTab.INBOX) }
    var status by remember { mutableStateOf<ApiClient.Status?>(null) }
    var users by remember { mutableStateOf<List<ApiClient.UserSummary>>(emptyList()) }
    var friends by remember { mutableStateOf<List<ApiClient.UserSummary>>(emptyList()) }
    var blocked by remember { mutableStateOf<List<ApiClient.UserSummary>>(emptyList()) }
    var incoming by remember { mutableStateOf<List<ApiClient.IncomingRequest>>(emptyList()) }
    var outgoing by remember { mutableStateOf<List<ApiClient.OutgoingRequest>>(emptyList()) }
    var groups by remember { mutableStateOf<List<ApiClient.Group>>(emptyList()) }
    var topics by remember { mutableStateOf<List<ApiClient.Topic>>(emptyList()) }
    var messages by remember { mutableStateOf<List<ApiClient.Message>>(emptyList()) }
    var outbox by remember { mutableStateOf<List<ApiClient.Message>>(emptyList()) }
    var sessions by remember { mutableStateOf<List<ApiClient.Session>>(emptyList()) }
    var friendshipSettings by remember { mutableStateOf<List<ApiClient.FriendshipSettings>>(emptyList()) }
    var ep by remember { mutableStateOf<ApiClient.EpOverview?>(null) }
    var chatThreads by remember { mutableStateOf<List<ApiClient.ChatThread>>(emptyList()) }
    var composeRecipient by remember { mutableStateOf<Long?>(null) }
    var chatFriend by remember { mutableStateOf<Long?>(null) }
    var epPrompt by remember { mutableStateOf<EpPrompt?>(null) }
    var epDraft by remember { mutableStateOf<EpPrompt?>(null) }
    var proof by remember { mutableStateOf<ApiClient.ProofDetails?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(true) }
    var recoveryCode by remember { mutableStateOf(initialRecoveryCode) }
    val opened = remember { mutableStateMapOf<Long, OpenedMessage>() }
    val scope = rememberCoroutineScope()
    var accountMenu by remember { mutableStateOf(false) }

    suspend fun refresh() {
        runCatching {
            coroutineScope {
                val jobs = listOf(
                    async { status = api.status(token) }, async { users = api.users(token) },
                    async { friends = api.friends(token) }, async { blocked = api.blocked(token) },
                    async { incoming = api.incomingRequests(token) }, async { outgoing = api.outgoingRequests(token) },
                    async { groups = api.groups(token) }, async { topics = api.topics(token) }, async { messages = api.messages(token) },
                    async { outbox = api.outbox(token) }, async { sessions = api.sessions(token) },
                    async { friendshipSettings = api.friendshipSettings(token) },
                    async { ep = api.ep(token) }, async { chatThreads = api.chatThreads(token) },
                )
                jobs.awaitAll()
            }
            error = null
        }.onFailure { error = it.message ?: "Aktualisierung fehlgeschlagen" }
        busy = false
    }
    fun act(block: suspend () -> Unit) {
        scope.launch { runCatching { block(); refresh() }.onFailure { error = it.message ?: "Vorgang fehlgeschlagen" } }
    }

    LaunchedEffect(token) {
        // Switching accounts must not leak the previous account's transient state.
        opened.clear(); epPrompt = null; epDraft = null; composeRecipient = null; chatFriend = null
        while (true) { refresh(); delay(30_000) }
    }
    if (recoveryCode != null) RecoveryDialog(recoveryCode!!) { recoveryCode = null; onRecoveryCodeSeen() }
    proof?.let { ProofDialog(it, onDismiss = { proof = null }) }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(painterResource(R.drawable.brand_logo), "GS Layermaxxing", Modifier.size(38.dp))
                    Column {
                        Text(tab.title, fontWeight = FontWeight.Bold)
                        Text("${status?.avatarEmoji ?: "🔐"} ${status?.name ?: "GS Layermaxxing"}", fontSize = 12.sp,
                            color = status?.displayColor?.let(::profileColor) ?: MaterialTheme.colorScheme.primary)
                    }
                }
            }, actions = {
                if (accounts.isNotEmpty()) Box {
                    TextButton(onClick = { accountMenu = true }) { Text("⇄ " + store.name.ifBlank { "Konto" }, fontSize = 13.sp) }
                    DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(if (account.name == store.name) "${account.name} ✓" else account.name) },
                                onClick = {
                                    accountMenu = false
                                    if (account.name != store.name) onSwitchAccount(account)
                                },
                            )
                        }
                    }
                }
                TextButton(onClick = { scope.launch { refresh() } }) { Text("Aktualisieren") }
            })
        },
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEach { item -> NavigationBarItem(
                    selected = tab == item, onClick = { tab = item }, icon = {
                        val count = when (item) {
                            HomeTab.TOPICS -> topics.count { it.completedAt == null }
                            HomeTab.FRIENDS -> incoming.size
                            else -> 0
                        }
                        Text(if (count > 0) "${item.icon}${if (count > 9) "9+" else count}" else item.icon, fontSize = 19.sp)
                    },
                    label = { Text(item.label) },
                ) }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                HomeTab.INBOX -> InboxScreen(messages, outbox, opened, token, api, ::act,
                    onCompose = { composeRecipient = null; tab = HomeTab.SEND },
                    onOpened = { epPrompt = EpPrompt(it.peerId, it.id) },
                    onReleased = { epPrompt = EpPrompt(it.peerId, it.id) },
                    onProof = { message -> scope.launch { runCatching { proof = api.proof(token, message.id) }.onFailure { error = it.message } } },
                    openTopics = topics.count { it.completedAt == null }, onTopics = { tab = HomeTab.TOPICS })
                HomeTab.CHAT -> ChatScreen(token, api, status?.userId, friends, friendshipSettings, chatThreads, chatFriend) {
                    chatFriend = it
                }
                HomeTab.SEND -> SendScreen(token, api, friends, groups, friendshipSettings, composeRecipient) { messageId, friendId ->
                    tab = HomeTab.INBOX; if (friendId != null) epPrompt = EpPrompt(friendId, messageId); act {}
                }
                HomeTab.EP -> EpScreen(token, api, friendshipSettings, ep, epDraft, onDraftConsumed = { epDraft = null }, act = ::act)
                HomeTab.TOPICS -> TopicsScreen(topics, friends, groups, token, api, ::act)
                HomeTab.FRIENDS -> FriendsScreen(token, api, users, friends, incoming, outgoing, groups,
                    friendshipSettings, onLetter = { composeRecipient = it; tab = HomeTab.SEND },
                    onChat = { chatFriend = it; tab = HomeTab.CHAT }, act = ::act)
                HomeTab.ACCOUNT -> AccountScreen(token, api, store, status, sessions, blocked,
                    onTheme, onLogout, serverProfile, onServerProfile,
                    onRecovery = { recoveryCode = it }, act = ::act)
            }
            if (busy) CircularProgressIndicator(Modifier.align(Alignment.Center))
            epPrompt?.let { suggestion -> Card(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Hat dieser Brief zu EP geführt?", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                TextButton(onClick = { epDraft = suggestion; epPrompt = null; tab = HomeTab.EP }) { Text("EP vorschlagen") }
                TextButton(onClick = { epPrompt = null }) { Text("Nicht jetzt") }
            } }
            }
            error?.let { message -> Card(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) { Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                TextButton(onClick = { error = null }) { Text("Schließen") }
            } } }
        }
    }
}

@Composable
private fun InboxScreen(
    messages: List<ApiClient.Message>, outbox: List<ApiClient.Message>, opened: MutableMap<Long, OpenedMessage>,
    token: String, api: ApiClient, act: ((suspend () -> Unit) -> Unit), onCompose: () -> Unit,
    onOpened: (ApiClient.Message) -> Unit, onReleased: (ApiClient.Message) -> Unit,
    onProof: (ApiClient.Message) -> Unit, openTopics: Int, onTopics: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingExport by rememberSaveable { mutableStateOf<String?>(null) }
    var exportError by remember { mutableStateOf<String?>(null) }
    val exportPending = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val content = pendingExport
        if (uri != null && content != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                ?: error("Exportdatei konnte nicht geschrieben werden")
        }.onFailure { exportError = it.message ?: "Export fehlgeschlagen" }
        pendingExport = null
    }
    var showSent by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(Instant.now().epochSecond) }
    LaunchedEffect(Unit) { while (true) { now = Instant.now().epochSecond; delay(1000) } }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            val ready = messages.count { it.unlocked && it.readAt == null }
            val waiting = messages.count { !it.unlocked }
            Card(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Geheime Nachrichten für später", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(when {
                        ready > 0 -> "$ready Nachricht${if (ready == 1) " ist" else "en sind"} bereit zum Öffnen"
                        waiting > 0 -> "$waiting Nachricht${if (waiting == 1) " wartet" else "en warten"} auf Freigabe"
                        else -> "Alles ruhig – schreibe jemandem eine Nachricht."
                    }, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Button(onClick = onCompose, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("＋  Neue Nachricht") }
                    OutlinedButton(onClick = { scope.launch {
                        runCatching { api.pendingProofExport(token) }.onSuccess {
                            exportError = null; pendingExport = it; exportPending.launch("offene-briefe.gsverify.json")
                        }.onFailure { exportError = it.message ?: "Export fehlgeschlagen" }
                    } }, modifier = Modifier.fillMaxWidth()) { Text("Öffentliche Prüfdateien exportieren") }
                    exportError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    if (openTopics > 0) TextButton(onClick = onTopics, modifier = Modifier.align(Alignment.End)) {
                        Text("☷ $openTopics offene${if (openTopics == 1) "s Thema" else " Themen"}")
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!showSent) Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("Empfangen (${messages.size})") }
                else OutlinedButton(onClick = { showSent = false }, modifier = Modifier.weight(1f)) { Text("Empfangen (${messages.size})") }
                if (showSent) Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("Gesendet (${outbox.size})") }
                else OutlinedButton(onClick = { showSent = true }, modifier = Modifier.weight(1f)) { Text("Gesendet (${outbox.size})") }
            }
        }
        val shown = if (showSent) outbox else messages
        if (shown.isEmpty()) item { InfoCard(if (showSent) "Noch nichts gesendet." else "Noch keine Nachrichten empfangen.") }
        if (!showSent) {
            shown.groupBy { it.peerName }.forEach { (name, peerMessages) ->
                item { SectionTitle("$name · ${peerMessages.size}") }
                items(peerMessages, key = { "in-${it.id}" }) { message ->
                    IncomingMessageCard(message, opened[message.id], now,
                        onOpen = { act {
                            val content = api.content(token, message.id)
                            val aad = content.canonicalMetadata?.toByteArray() ?: byteArrayOf()
                            val text = CryptoBox.decryptBytes(content.ciphertext, content.nonce, content.encryptionKey, aad).toString(Charsets.UTF_8)
                            val bytes = if (content.attachmentCiphertext != null && content.attachmentNonce != null)
                                CryptoBox.decryptBytes(
                                    content.attachmentCiphertext, content.attachmentNonce, content.encryptionKey,
                                    if (content.canonicalMetadata != null && content.attachmentName != null && content.attachmentMime != null)
                                        EvidenceProtocol.attachmentAad(content.canonicalMetadata, content.attachmentName, content.attachmentMime)
                                    else byteArrayOf(),
                                ) else null
                            opened[message.id] = OpenedMessage(text, bytes, content.attachmentName, content.attachmentMime)
                            onOpened(message)
                        } },
                        onApprove = { act { api.approve(token, message.id) } },
                        onReact = { emoji -> act { api.react(token, message.id, emoji) } },
                        onProof = { onProof(message) },
                    )
                }
            }
        } else items(shown, key = { "out-${it.id}" }) { message ->
            OutboxMessageCard(message, now,
                onRelease = { act { api.release(token, message.id); onReleased(message) } },
                onApprove = { act { api.approve(token, message.id) } },
                onRetract = { act { api.retract(token, message.id) } },
                onProof = { onProof(message) },
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IncomingMessageCard(
    message: ApiClient.Message, opened: OpenedMessage?, now: Long,
    onOpen: () -> Unit, onApprove: () -> Unit, onReact: (String) -> Unit, onProof: () -> Unit,
) {
    val context = LocalContext.current
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null && opened?.attachment != null) context.contentResolver.openOutputStream(uri)?.use { it.write(opened.attachment) }
    }
    val locked = !message.unlocked && opened == null
    Card(Modifier.fillMaxWidth().combinedClickable(
        onClick = { if (locked) playWhip(context) }, onLongClick = onProof,
    ), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(
        containerColor = if (message.unlocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row { Text(message.title.ifBlank { "Geheime Nachricht" }, Modifier.weight(1f), fontWeight = FontWeight.Bold); if (message.oneTime) Text("1×") }
            Text("Von ${message.peerName} · ${formatDate(message.createdAt)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (message.coverNote.isNotBlank()) Text("Öffentlicher Umschlagtext: ${message.coverNote}")
            if (message.proofStatus == "legacy") Text("Legacy – ohne kryptografischen Nachweis", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            when {
                opened != null -> {
                    Text(opened.text, fontSize = 17.sp)
                    if (opened.attachment != null) OutlinedButton(onClick = { save.launch(safeFileName(opened.name ?: "Anhang")) }) { Text("📎 ${opened.name ?: "Anhang"} speichern") }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("❤️", "👍", "😂", "😮").forEach { emoji -> TextButton(onClick = { onReact(emoji) }) { Text(emoji) } } }
                }
                message.unlocked -> Button(onClick = onOpen) { Text(if (message.oneTime) "Einmalig öffnen" else "Nachricht öffnen") }
                message.mode == "manual" -> Text("🔒 Wartet auf Freigabe durch ${message.peerName}")
                message.mode == "mutual" -> {
                    Text("🔒 Beide müssen zustimmen")
                    if (!message.recipientApproved) Button(onClick = onApprove) { Text("Meine Freigabe bestätigen") }
                }
                message.mode == "presence" -> Text("🔒 Öffnet, sobald ihr beide online seid")
                message.mode == "random" -> Text("🔒 Öffnet zufällig bis ${message.randomTo?.let(::formatDate) ?: "später"}")
                else -> Text("🔒 Noch ${formatRemaining(max(0, (message.releaseAt ?: now) - now))}\n${message.releaseAt?.let(::formatDate).orEmpty()}")
            }
            if (message.reactions.isNotEmpty()) Text(message.reactions.joinToString("  ") { "${it.emoji} ${it.name}" }, fontSize = 13.sp)
            Text("Lange drücken: Versiegelungs-Hash, Prüfschlüssel und Prüfdatei", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OutboxMessageCard(
    message: ApiClient.Message, now: Long, onRelease: () -> Unit, onApprove: () -> Unit,
    onRetract: () -> Unit, onProof: () -> Unit,
) {
    var confirmRelease by remember { mutableStateOf(false) }
    var confirmRetract by remember { mutableStateOf(false) }
    if (confirmRelease) ConfirmDialog("Jetzt wirklich freigeben?", "Der Empfänger kann die Nachricht danach lesen.", { confirmRelease = false; onRelease() }) { confirmRelease = false }
    if (confirmRetract) ConfirmDialog("Nachricht zurückziehen?", "Sie wird für beide Seiten gelöscht.", { confirmRetract = false; onRetract() }) { confirmRetract = false }
    Card(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onProof)) { Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(message.title.ifBlank { "An ${message.peerName}" }, fontWeight = FontWeight.Bold)
        Text("An ${message.peerName} · ${formatDate(message.createdAt)}", fontSize = 12.sp)
        if (message.coverNote.isNotBlank()) Text("Öffentlicher Umschlagtext: ${message.coverNote}")
        if (message.proofStatus == "legacy") Text("Legacy – ohne kryptografischen Nachweis", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        when {
            message.unlocked -> Text(if (message.readAt != null) "✓ Gelesen" else "✓ Freigegeben", color = Color(0xFF19703B))
            message.mode == "manual" -> Button(onClick = { confirmRelease = true }) { Text("Jetzt freigeben") }
            message.mode == "mutual" && !message.senderApproved -> Button(onClick = onApprove) { Text("Meine Freigabe bestätigen") }
            message.mode == "mutual" -> Text("Wartet auf Zustimmung von ${message.peerName}")
            message.mode == "presence" -> Text("Wartet, bis ihr beide online seid")
            message.mode == "random" -> Text("Zufällige Freigabe bis ${message.randomTo?.let(::formatDate)}")
            else -> Text("Automatisch in ${formatRemaining(max(0, (message.releaseAt ?: now) - now))}")
        }
        if (!message.unlocked) TextButton(onClick = { confirmRetract = true }) { Text("Zurückziehen") }
        if (message.reactions.isNotEmpty()) Text(message.reactions.joinToString("  ") { "${it.emoji} ${it.name}" })
    } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SendScreen(
    token: String, api: ApiClient, friends: List<ApiClient.UserSummary>, groups: List<ApiClient.Group>,
    settings: List<ApiClient.FriendshipSettings>,
    initialRecipient: Long?, onSent: (Long?, Long?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }; var coverNote by remember { mutableStateOf("") }; var text by remember { mutableStateOf("") }
    var selected by remember(initialRecipient) { mutableStateOf(initialRecipient?.let(::setOf) ?: emptySet()) }; var groupId by remember { mutableStateOf<Long?>(null) }
    var mode by remember { mutableStateOf(ComposeMode.DURATION) }; var amount by remember { mutableStateOf("3") }
    var modeMenu by remember { mutableStateOf(false) }
    var recipientMenu by remember { mutableStateOf(false) }
    var unit by remember { mutableStateOf(DelayUnit.DAYS) }; var dateTime by remember { mutableStateOf(LocalDateTime.now().plusDays(1).withSecond(0).withNano(0)) }
    var randomStart by remember { mutableStateOf("1") }; var randomEnd by remember { mutableStateOf("24") }
    var randomStartUnit by remember { mutableStateOf(DelayUnit.HOURS) }; var randomEndUnit by remember { mutableStateOf(DelayUnit.HOURS) }
    var oneTime by remember { mutableStateOf(false) }
    var attachment by remember { mutableStateOf<Triple<String, String, ByteArray>?>(null) }; var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }; var confirmManual by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                require(bytes.size <= 2_000_000) { "Der Anhang darf höchstens 2 MB groß sein." }
                var name = "Anhang"; context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (cursor.moveToFirst() && index >= 0) name = cursor.getString(index)
                }
                Triple(name, context.contentResolver.getType(uri) ?: "application/octet-stream", bytes)
            } }.onSuccess { attachment = it }.onFailure { notice = it.message }
        }
    }
    val now = Instant.now().epochSecond
    val targetValid = groupId != null || selected.isNotEmpty()
    val minimumDelay = if (groupId == null) selected.mapNotNull { id ->
        settings.firstOrNull { it.friendId == id }?.minLetterDelaySeconds
    }.maxOrNull() ?: 0L else 0L
    val scheduleValid = when (mode) {
        ComposeMode.DURATION -> (amount.toLongOrNull() ?: 0) > 0
        ComposeMode.DATE_TIME -> dateTime.atZone(ZoneId.systemDefault()).toEpochSecond() > now
        ComposeMode.RANDOM -> {
            val from = (randomStart.toLongOrNull() ?: 0) * randomStartUnit.seconds
            val to = (randomEnd.toLongOrNull() ?: 0) * randomEndUnit.seconds
            from >= 60 && to - from >= 60
        }
        else -> true
    }
    suspend fun send() {
        busy = true; notice = null
        runCatching {
            val current = Instant.now().epochSecond
            // The server tolerates a few seconds of clock skew, but add the same
            // slack here so an agreed minimum delay never fails by a hair.
            val minimum = current + minimumDelay + 5
            val release = when (mode) {
                ComposeMode.DURATION -> maxOf(current + (amount.toLongOrNull() ?: 0) * unit.seconds, minimum)
                ComposeMode.DATE_TIME -> dateTime.atZone(ZoneId.systemDefault()).toEpochSecond()
                else -> null
            }
            val randomFrom = if (mode == ComposeMode.RANDOM)
                maxOf(current + (randomStart.toLongOrNull() ?: 1) * randomStartUnit.seconds, minimum) else null
            val randomTo = if (mode == ComposeMode.RANDOM) {
                val requested = current + (randomEnd.toLongOrNull() ?: 24) * randomEndUnit.seconds
                // Keep at least the one-minute window the server requires, also after
                // randomFrom was raised to the agreed minimum delay.
                if (randomFrom != null) maxOf(requested, randomFrom + 60) else requested
            } else null
            val metadata = LetterMetadata(
                title.trim(), coverNote.trim(), if (groupId == null) selected.toList() else emptyList(),
                groupId, mode.api, release, randomFrom, randomTo, oneTime,
            )
            // Sealing runs AES/ECDSA/SHA-256 over the whole body plus an optional
            // 2 MB attachment; keep it off the Compose main dispatcher.
            val sealed = withContext(Dispatchers.Default) {
                EvidenceProtocol.seal(text, metadata, attachment?.first, attachment?.second, attachment?.third)
            }
            val encrypted = CryptoBox.Encrypted(sealed.ciphertext, sealed.nonce, sealed.releaseKey)
            val encryptedAttachment = sealed.attachment?.let {
                ApiClient.EncryptedAttachment(it.name, it.mime, it.ciphertext, it.nonce)
            }
            api.send(token, ApiClient.SendRequest(
                recipientIds = if (groupId == null) selected.toList() else emptyList(), groupId = groupId,
                encrypted = encrypted, title = title.trim(), mode = mode.api, releaseAt = release,
                randomFrom = randomFrom, randomTo = randomTo,
                oneTime = oneTime, attachment = encryptedAttachment, coverNote = coverNote.trim(), evidence = sealed.evidence,
            ))
        }.onSuccess { ids ->
            val likelyFriend = if (groupId == null) selected.firstOrNull() else null
            text = ""; title = ""; coverNote = ""; attachment = null; notice = "✓ Verschlüsselt gesendet"
            onSent(ids.firstOrNull(), likelyFriend)
        }
            .onFailure { notice = it.message ?: "Senden fehlgeschlagen" }
        busy = false
    }
    if (confirmManual) ConfirmDialog("Manuelle Nachricht senden?", "Sie bleibt gesperrt, bis du sie später ausdrücklich freigibst.",
        { confirmManual = false; scope.launch { send() } }) { confirmManual = false }
    if (modeMenu) ModalBottomSheet(onDismissRequest = { modeMenu = false }) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Wann öffnen?", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Wähle, wann die Nachricht sichtbar werden darf.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            ComposeMode.entries.forEach { choice ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { mode = choice; modeMenu = false },
                    shape = RoundedCornerShape(18.dp),
                    color = if (mode == choice) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(modeIcon(choice), fontSize = 22.sp, modifier = Modifier.width(42.dp), color = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(choice.label, fontWeight = FontWeight.SemiBold)
                            Text(modeDescription(choice), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (mode == choice) Text("✓", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionTitle("Neuer versiegelter Brief") }
        item {
            Card { Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Empfänger", fontWeight = FontWeight.Bold)
                if (friends.isEmpty()) Text("Füge zuerst Freunde hinzu.")
                else Box {
                    OutlinedButton(onClick = { recipientMenu = true }, Modifier.fillMaxWidth()) {
                        Text(
                            if (selected.isEmpty()) "Empfänger auswählen"
                            else friends.filter { it.id in selected }.joinToString { "${it.avatarEmoji} ${it.name}" },
                            Modifier.weight(1f),
                        )
                        Text("⌄")
                    }
                    DropdownMenu(recipientMenu, { recipientMenu = false }) {
                        friends.forEach { friend -> DropdownMenuItem(
                            text = { Text("${if (friend.id in selected) "✓ " else ""}${friend.avatarEmoji} ${friend.name}") },
                            onClick = {
                                groupId = null
                                selected = if (friend.id in selected) selected - friend.id else selected + friend.id
                            },
                        ) }
                    }
                }
                if (groups.isNotEmpty()) {
                    HorizontalDivider(); Text("Oder Gruppe", fontWeight = FontWeight.SemiBold)
                    groups.forEach { group -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = groupId == group.id, onCheckedChange = { checked -> groupId = if (checked) group.id else null; if (checked) selected = emptySet() })
                        Text("👥 ${group.name} (${group.members.size})")
                    } }
                }
                OutlinedTextField(title, { title = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("Brieftitel – optional") })
                OutlinedTextField(coverNote, { coverNote = it.take(1000) }, Modifier.fillMaxWidth(),
                    label = { Text("Öffentlicher Umschlagtext") }, supportingText = { Text("Offen und unverschlüsselt sichtbar") }, minLines = 2)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Versiegelter Inhalt", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Surface(color = Color(0xFFB3261E), shape = RoundedCornerShape(50)) { Text("✦ SIEGEL", Modifier.padding(8.dp), color = Color.White, fontSize = 11.sp) }
                        }
                        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("Geheimer Brieftext") }, minLines = 4, maxLines = 10)
                    }
                }
                OutlinedButton(onClick = { picker.launch("*/*") }) { Text(attachment?.let { "📎 ${it.first}" } ?: "Bild oder Datei anhängen") }
                attachment?.let { Text("${it.third.size / 1024} KB · verschlüsselt", fontSize = 12.sp) }
            } }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Wann darf die Nachricht geöffnet werden?", fontWeight = FontWeight.Bold)
                    Box {
                        OutlinedButton(onClick = { modeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("${modeIcon(mode)}  ${mode.label}", modifier = Modifier.weight(1f))
                            Text("⌄")
                        }
                    }
                    when (mode) {
                        ComposeMode.DURATION -> NumberAndUnit(amount, { amount = it }, unit, { unit = it })
                        ComposeMode.DATE_TIME -> DateTimeChooser(dateTime) { dateTime = it }
                        ComposeMode.MANUAL -> Text(modeDescription(mode))
                        ComposeMode.MUTUAL -> Text(modeDescription(mode))
                        ComposeMode.PRESENCE -> Text(modeDescription(mode))
                        ComposeMode.RANDOM -> {
                            Text("Zufällig innerhalb dieses Fensters:")
                            NumberAndUnit(randomStart, { randomStart = it }, randomStartUnit, { randomStartUnit = it }, "Frühestens")
                            NumberAndUnit(randomEnd, { randomEnd = it }, randomEndUnit, { randomEndUnit = it }, "Spätestens")
                            Text("Das Fenster muss mindestens 1 Minute groß sein. Den tatsächlichen Zeitpunkt wählt der Server.", fontSize = 12.sp)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(oneTime, { oneTime = it }); Spacer(Modifier.width(8.dp)); Text("Nur einmal lesbar") }
                    if (minimumDelay > 0) Text(
                        "Diese Freundschaft verlangt mindestens ${formatRemaining(minimumDelay)} bis zur Freigabe; kürzere Zeiten werden automatisch angehoben.",
                        fontSize = 12.sp,
                    )
                }
            }
        }
        item { InfoCard(
            "AES-256-GCM schützt Vertraulichkeit und Integrität in Speicherung und Übertragung. " +
                "Der Server hält den AES-Schlüssel bis zur Freigabe und kann technisch auf gesperrte Inhalte zugreifen. " +
                "Zeit, Zufall, manuell, gegenseitig und Präsenz unterscheiden nur das serverseitige Freigabe-Gate. " +
                "Präsenz ist serverbestätigte Aktivität, keine ausdrückliche Zustimmung. Einmal-Lesen ist App-/Server-Regel und verhindert keine Screenshots oder Caches."
        ) }
        notice?.let { item { Text(it, color = if (it.startsWith("✓")) Color(0xFF19703B) else MaterialTheme.colorScheme.error) } }
        item { Button(
            onClick = { if (mode == ComposeMode.MANUAL) confirmManual = true else scope.launch { send() } },
            enabled = targetValid && text.isNotBlank() && scheduleValid && !busy, modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (busy) "Wird verschlüsselt …" else "Verschlüsselt senden") } }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun FriendsScreen(
    token: String, api: ApiClient, users: List<ApiClient.UserSummary>, friends: List<ApiClient.UserSummary>,
    incoming: List<ApiClient.IncomingRequest>, outgoing: List<ApiClient.OutgoingRequest>, groups: List<ApiClient.Group>,
    settings: List<ApiClient.FriendshipSettings>, onLetter: (Long) -> Unit, onChat: (Long) -> Unit,
    act: ((suspend () -> Unit) -> Unit),
) {
    val scope = rememberCoroutineScope(); var search by remember { mutableStateOf("") }; var result by remember { mutableStateOf<List<ApiClient.UserSummary>>(emptyList()) }
    var groupName by remember { mutableStateOf("") }; var groupMembers by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var creatingGroup by remember { mutableStateOf(false) }
    var friendMenu by remember { mutableStateOf<Long?>(null) }
    var expandedSettings by remember { mutableStateOf<Long?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        if (incoming.isNotEmpty()) { item { SectionTitle("Offene Anfragen") }; items(incoming, key = { "req-${it.id}" }) { request ->
            ActionCard("${request.senderName} möchte mit dir befreundet sein") {
                Button(onClick = { act { api.respondFriend(token, request.id, true) } }) { Text("Bestätigen") }
                OutlinedButton(onClick = { act { api.respondFriend(token, request.id, false) } }) { Text("Ablehnen") }
            }
        } }
        if (outgoing.isNotEmpty()) { item { SectionTitle("Gesendet") }; items(outgoing, key = { "outreq-${it.id}" }) { request ->
            ActionCard("Anfrage an ${request.recipientName}") { TextButton(onClick = { act { api.withdrawFriendRequest(token, request.id) } }) { Text("Zurückziehen") } }
        } }
        item { SectionTitle("Freunde") }
        if (friends.isEmpty()) item { InfoCard("Noch keine bestätigten Freunde.") }
        else items(friends, key = { "friend-${it.id}" }) { friend ->
            val current = settings.firstOrNull { it.friendId == friend.id }
            var draft by remember(current) { mutableStateOf(current) }
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${friend.avatarEmoji} ${friend.name}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Box {
                        TextButton(onClick = { friendMenu = friend.id }) { Text("⋮", fontSize = 22.sp) }
                        DropdownMenu(expanded = friendMenu == friend.id, onDismissRequest = { friendMenu = null }) {
                            DropdownMenuItem(text = { Text("Freund entfernen") }, onClick = { friendMenu = null; act { api.removeFriend(token, friend.id) } })
                            DropdownMenuItem(text = { Text("Blockieren", color = MaterialTheme.colorScheme.error) }, onClick = { friendMenu = null; act { api.block(token, friend.id) } })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Button(onClick = { onLetter(friend.id) }, enabled = current?.lettersEnabled != false) { Text("Brief") }
                    OutlinedButton(onClick = { onChat(friend.id) }, enabled = current?.chatsEnabled != false) { Text("Chat") }
                    TextButton(onClick = { expandedSettings = if (expandedSettings == friend.id) null else friend.id }) { Text("Einstellungen") }
                }
                current?.let { value -> Text(
                    "Briefe ${yesNo(value.lettersEnabled)} · Chats ${yesNo(value.chatsEnabled)} · EP ${yesNo(value.epEnabled)} · Mindestdauer ${formatRemaining(value.minLetterDelaySeconds)}",
                    fontSize = 12.sp,
                ) }
                if (expandedSettings == friend.id && current != null && draft != null) {
                    val proposal = current.incomingProposal
                    if (proposal != null) {
                        Text("Eingehender Einstellungsvorschlag", fontWeight = FontWeight.Bold)
                        Text(settingsSummary(proposal.lettersEnabled, proposal.chatsEnabled, proposal.epEnabled, proposal.minLetterDelaySeconds))
                        Row { Button(onClick = { act { api.respondFriendshipSettings(token, proposal.id, true) } }) { Text("Annehmen") }
                            TextButton(onClick = { act { api.respondFriendshipSettings(token, proposal.id, false) } }) { Text("Ablehnen") } }
                    } else current.outgoingProposal?.let { proposal ->
                        Text("Ausgehender Vorschlag: ${settingsSummary(proposal.lettersEnabled, proposal.chatsEnabled, proposal.epEnabled, proposal.minLetterDelaySeconds)}")
                        TextButton(onClick = { act { api.withdrawFriendshipSettings(token, proposal.id) } }) { Text("Vorschlag zurückziehen") }
                    } ?: run {
                        Text("Änderungen werden als Vorschlag gesendet und gelten erst nach Zustimmung von ${friend.name}.")
                        val value = draft!!
                        ToggleSetting("Briefe", value.lettersEnabled) { draft = value.copy(lettersEnabled = it) }
                        ToggleSetting("Chats", value.chatsEnabled) { draft = value.copy(chatsEnabled = it) }
                        ToggleSetting("EP", value.epEnabled) { draft = value.copy(epEnabled = it) }
                        OutlinedTextField(
                            value.minLetterDelaySeconds.toString(),
                            { entered -> draft = value.copy(minLetterDelaySeconds = entered.filter(Char::isDigit).toLongOrNull() ?: 0) },
                            Modifier.fillMaxWidth(), label = { Text("Mindest-Briefverzögerung in Sekunden") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Button(onClick = { act { api.proposeFriendshipSettings(token, draft!!) } }) { Text("Einstellungsvorschlag wie einen Brief senden") }
                    }
                }
            } }
        }
        item { SectionTitle("Nutzer finden") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(search, { search = it }, Modifier.weight(1f), label = { Text("Exakter Benutzername") }, singleLine = true)
            Button(onClick = { scope.launch { result = runCatching { api.searchUser(token, search.trim()) }.getOrDefault(emptyList()) } }, enabled = search.isNotBlank()) { Text("Suchen") }
        } }
        val discover = if (result.isNotEmpty()) result else users
        items(discover.filter { it.relationship == "none" }, key = { "discover-${it.id}" }) { user ->
            ActionCard("${user.avatarEmoji} ${user.name}") { Button(onClick = { act { api.requestFriend(token, user.id) } }) { Text("Anfragen") } }
        }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Gruppen")
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { creatingGroup = !creatingGroup }) { Text(if (creatingGroup) "Schließen" else "+ Neue Gruppe") }
        } }
        if (groups.isEmpty()) item { InfoCard("Noch keine Gruppe. Erstelle eine aus deinen Freunden.") }
        else items(groups, key = { "group-${it.id}" }) { group -> InfoCard("👥 ${group.name}: ${group.members.joinToString { it.name }}") }
        if (friends.isNotEmpty() && creatingGroup) item {
            Card { Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Neue Gruppe", fontWeight = FontWeight.Bold)
                OutlinedTextField(groupName, { groupName = it }, Modifier.fillMaxWidth(), label = { Text("Gruppenname") })
                friends.forEach { friend -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(friend.id in groupMembers, { checked -> groupMembers = if (checked) groupMembers + friend.id else groupMembers - friend.id }); Text(friend.name)
                } }
                Button(onClick = { act { api.createGroup(token, groupName.trim(), groupMembers.toList()); groupName = ""; groupMembers = emptySet() } },
                    enabled = groupName.isNotBlank() && groupMembers.isNotEmpty()) { Text("Gruppe erstellen") }
            } }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun AccountScreen(
    token: String, api: ApiClient, store: SessionStore, status: ApiClient.Status?, sessions: List<ApiClient.Session>,
    blocked: List<ApiClient.UserSummary>, onTheme: (String) -> Unit, onLogout: () -> Unit,
    serverProfile: ServerProfile, onServerProfile: (ServerProfile) -> Unit,
    onRecovery: (String) -> Unit, act: ((suspend () -> Unit) -> Unit),
) {
    val context = LocalContext.current
    val notificationGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
    var name by remember(status?.name) { mutableStateOf(status?.name.orEmpty()) }; var emoji by remember(status?.avatarEmoji) { mutableStateOf(status?.avatarEmoji ?: "🔐") }
    var color by remember(status?.displayColor) { mutableStateOf(status?.displayColor ?: "#6750A4") }; var discoverable by remember(status?.discoverable) { mutableStateOf(status?.discoverable ?: true) }
    var oldPassword by remember { mutableStateOf("") }; var newPassword by remember { mutableStateOf("") }; var migrationPassword by remember { mutableStateOf("") }
    var biometric by remember { mutableStateOf(store.biometricEnabled) }
    var advanced by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionTitle("Server") }
        item { Card { Column(Modifier.padding(15.dp)) {
            ServerProfileSelector(serverProfile, onServerProfile)
            if (!serverProfile.isConfigured()) Text("Nicht konfiguriert", color = MaterialTheme.colorScheme.error)
        } } }
        if (status?.needsPassword == true) item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Bestehendes Profil absichern", fontWeight = FontWeight.Bold)
                OutlinedTextField(migrationPassword, { migrationPassword = it }, Modifier.fillMaxWidth(), label = { Text("Neues Passwort") }, visualTransformation = PasswordVisualTransformation())
                Button(onClick = { act { onRecovery(api.setPassword(token, migrationPassword)) } }, enabled = migrationPassword.length >= 8) { Text("Passwort setzen") }
            }
        } }
        item { SectionTitle("Profil") }
        item { Card { Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(emoji, { emoji = it.take(8) }, Modifier.width(90.dp), label = { Text("Avatar") })
                OutlinedTextField(name, { name = it }, Modifier.weight(1f), label = { Text("Benutzername") })
            }
            Text("Profilfarbe", fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("#195C3A" to "Grün", "#315B8A" to "Blau", "#8A671B" to "Gold", "#8A3B3B" to "Rot").forEach { (value, label) ->
                    if (color.equals(value, true)) Button(onClick = {}) { Text("● $label") }
                    else OutlinedButton(onClick = { color = value }) { Text("● $label", color = profileColor(value)) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(discoverable, { discoverable = it }); Spacer(Modifier.width(8.dp)); Text("In Nutzerliste sichtbar") }
            Button(onClick = { act { api.updateProfile(token, name, emoji, color, discoverable); store.name = name } },
                enabled = name.isNotBlank() && Regex("^#[0-9A-Fa-f]{6}$").matches(color)) { Text("Profil speichern") }
        } } }
        item { SectionTitle("Darstellung und Schutz") }
        item { Card { Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Theme")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("system" to "System", "light" to "Hell", "dark" to "Dunkel", "antique" to "Altertümlich").forEach { (key, label) ->
                    if (store.theme == key) Button(onClick = {}) { Text(label) } else OutlinedButton(onClick = { onTheme(key) }) { Text(label) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(biometric, { biometric = it; store.biometricEnabled = it }); Spacer(Modifier.width(8.dp)); Text("Beim Start biometrisch sperren") }
            if (!notificationGranted && Build.VERSION.SDK_INT >= 33) {
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        })
                    }
                }) {
                    Text("Benachrichtigungen in Android erlauben")
                }
            } else Text("✓ Benachrichtigungen erlaubt", fontSize = 12.sp, color = Color(0xFF19703B))
        } } }
        item { OutlinedButton(onClick = { advanced = !advanced }, Modifier.fillMaxWidth().height(50.dp)) {
            Text(if (advanced) "Weniger Einstellungen anzeigen" else "Passwort, Geräte und Blockierungen")
        } }
        if (advanced) {
            item { SectionTitle("Passwort und Wiederherstellung") }
            item { Card { Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(oldPassword, { oldPassword = it }, Modifier.fillMaxWidth(), label = { Text("Altes Passwort") }, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(newPassword, { newPassword = it }, Modifier.fillMaxWidth(), label = { Text("Neues Passwort") }, visualTransformation = PasswordVisualTransformation())
                Button(onClick = { act { api.changePassword(token, oldPassword, newPassword); oldPassword = ""; newPassword = "" } }, enabled = oldPassword.isNotBlank() && newPassword.length >= 8) { Text("Passwort ändern") }
                OutlinedButton(onClick = { act { onRecovery(api.newRecoveryCode(token)) } }) { Text("Neuen Wiederherstellungscode erzeugen") }
            } } }
            item { SectionTitle("Angemeldete Geräte") }
            items(sessions, key = { "session-${it.id}" }) { session -> ActionCard("${session.deviceName}\nZuletzt ${formatDate(session.lastSeenAt)}${if (session.current) " · dieses Gerät" else ""}") {
                if (!session.current) TextButton(onClick = { act { api.revokeSession(token, session.id) } }) { Text("Abmelden") }
            } }
            if (blocked.isNotEmpty()) { item { SectionTitle("Blockierte Nutzer") }; items(blocked, key = { "blocked-${it.id}" }) { user ->
                ActionCard(user.name) { TextButton(onClick = { act { api.unblock(token, user.id) } }) { Text("Freigeben") } }
            } }
        }
        item { OutlinedButton(onClick = onLogout, Modifier.fillMaxWidth()) { Text("Auf diesem Gerät abmelden") } }
        item { Text("Neue Nachrichten werden regelmäßig im Hintergrund geprüft.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun NumberAndUnit(
    amount: String, onAmount: (String) -> Unit, unit: DelayUnit, onUnit: (DelayUnit) -> Unit,
    label: String = "Nach",
) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(amount, { onAmount(it.filter(Char::isDigit).take(4)) }, Modifier.width(140.dp), label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Box { OutlinedButton(onClick = { menu = true }) { Text(unit.label) }; DropdownMenu(menu, { menu = false }) {
            DelayUnit.entries.forEach { choice -> DropdownMenuItem({ Text(choice.label) }, { onUnit(choice); menu = false }) }
        } }
    }
}

@Composable
private fun DateTimeChooser(value: LocalDateTime, onChange: (LocalDateTime) -> Unit) {
    val context = LocalContext.current
    Text("Gewählt: ${value.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}", fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { DatePickerDialog(context, { _, y, m, d -> onChange(value.withYear(y).withMonth(m + 1).withDayOfMonth(d)) }, value.year, value.monthValue - 1, value.dayOfMonth).show() }) { Text("Datum") }
        OutlinedButton(onClick = { TimePickerDialog(context, { _, h, min -> onChange(value.withHour(h).withMinute(min)) }, value.hour, value.minute, true).show() }) { Text("Uhrzeit") }
    }
}

@Composable
private fun RecoveryDialog(code: String, onDismiss: () -> Unit) = AlertDialog(
    onDismissRequest = {}, title = { Text("Wiederherstellungscode sichern") },
    text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Notiere diesen Code. Er wird nur einmal angezeigt:"); Text(code, fontWeight = FontWeight.Bold, fontSize = 20.sp); Text("Ein neuer Code macht den vorherigen ungültig.") } },
    confirmButton = { Button(onClick = onDismiss) { Text("Ich habe ihn notiert") } },
)

@Composable
private fun ProofDialog(proof: ApiClient.ProofDetails, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) context.contentResolver.openOutputStream(uri)?.use { it.write(proof.rawExport.toByteArray()) }
    }
    val evidence = proof.evidence
    val verification = remember(evidence) { evidence?.let(EvidenceProtocol::verify) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kryptografischer Nachweis") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (proof.legacy || evidence == null) {
                Text("Legacy – ohne kryptografischen Nachweis", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            } else {
                Text("Protokollversion ${evidence.protocolVersion}")
                Text("Versiegelungs-Hash", fontWeight = FontWeight.Bold)
                SelectionContainer { Text(evidence.commitment, fontSize = 12.sp) }
                Text("Klartext-Hash (bei kurzen Nachrichten möglicherweise erratbar)", fontWeight = FontWeight.Bold)
                SelectionContainer { Text(evidence.plaintextSha256, fontSize = 12.sp) }
                Text("Fingerabdruck des öffentlichen Prüfschlüssels", fontWeight = FontWeight.Bold)
                SelectionContainer { Text(evidence.publicKeyFingerprint, fontSize = 12.sp) }
                Text("Öffentlicher Prüfschlüssel (kopierbar)", fontWeight = FontWeight.Bold)
                SelectionContainer { Text(evidence.publicSigningKey, fontSize = 11.sp) }
                Text("Signatur", fontWeight = FontWeight.Bold)
                SelectionContainer { Text(evidence.signature, fontSize = 11.sp) }
                Text("Freigaberegel/Zeitstempel: ${proof.releaseRule}")
                if (evidence.releaseKey != null) {
                    Text("Fingerabdruck des offengelegten AES-Freigabeschlüssels", fontWeight = FontWeight.Bold)
                    SelectionContainer { Text(evidence.releaseKeyFingerprint.orEmpty(), fontSize = 12.sp) }
                    Text(
                        if (verification?.valid == true && verification.status == "verified") "✓ Lokale Prüfung erfolgreich"
                        else "⚠ Lokale Prüfung fehlgeschlagen: ${verification?.status}",
                        color = if (verification?.valid == true) Color(0xFF19703B) else MaterialTheme.colorScheme.error,
                    )
                } else Text("Der AES-Freigabeschlüssel ist noch nicht offengelegt.")
            }
            OutlinedButton(onClick = { export.launch("brief-${proof.messageId}.gsverify.json") }) {
                Text("Öffentliche Prüfdatei exportieren")
            }
        } },
        confirmButton = { Button(onClick = onDismiss) { Text("Schließen") } },
    )
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: () -> Unit, dismiss: () -> Unit) = AlertDialog(
    onDismissRequest = dismiss, title = { Text(title) }, text = { Text(text) },
    confirmButton = { Button(onClick = confirm) { Text("Bestätigen") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Abbrechen") } },
)

@Composable
private fun ActionCard(text: String, actions: @Composable RowScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Row(horizontalArrangement = Arrangement.spacedBy(3.dp), content = actions)
    } }
}

@Composable
private fun SectionTitle(text: String) = Text(text, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
@Composable
private fun InfoCard(text: String) = Card(Modifier.fillMaxWidth()) { Text(text, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable
private fun ToggleSetting(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) =
    Row(verticalAlignment = Alignment.CenterVertically) { Switch(checked, onChecked); Spacer(Modifier.width(8.dp)); Text(label) }
private fun yesNo(value: Boolean) = if (value) "an" else "aus"
private fun settingsSummary(letters: Boolean, chats: Boolean, ep: Boolean, delay: Long) =
    "Briefe ${yesNo(letters)}, Chats ${yesNo(chats)}, EP ${yesNo(ep)}, Mindestverzögerung ${delay}s"
private fun formatDate(epoch: Long): String = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(epoch))
private fun profileColor(value: String): Color = runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(Color(0xFF5B3FD6))
private fun formatRemaining(seconds: Long): String { val d=seconds/86400; val h=seconds%86400/3600; val m=seconds%3600/60; val s=seconds%60; return when { d>0 -> "$d T $h Std"; h>0 -> "$h Std $m Min"; m>0 -> "$m Min $s Sek"; else -> "$s Sek" } }
private fun safeFileName(name: String): String =
    name.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[\\p{Cntrl}]"), "").trim()
        .trimStart('.').take(120).ifBlank { "Anhang" }
private var nextWhipSound = 0
private val whipSounds = intArrayOf(R.raw.whip, R.raw.whip_2, R.raw.whip_3, R.raw.whip_4, R.raw.whip_5)

private fun playWhip(context: android.content.Context) {
    runCatching {
        val sound = whipSounds[nextWhipSound % whipSounds.size]
        nextWhipSound = (nextWhipSound + 1) % whipSounds.size
        MediaPlayer.create(context, sound)?.apply {
            setOnCompletionListener { player -> player.release() }
            setOnErrorListener { player, _, _ -> player.release(); true }
            start()
        }
    }
}
private fun modeIcon(mode: ComposeMode): String = when (mode) {
    ComposeMode.DURATION -> "◷"; ComposeMode.DATE_TIME -> "▣"; ComposeMode.MANUAL -> "☝"
    ComposeMode.MUTUAL -> "✓✓"; ComposeMode.PRESENCE -> "●"; ComposeMode.RANDOM -> "?"
}
private fun modeDescription(mode: ComposeMode): String = when (mode) {
    ComposeMode.DURATION -> "Öffnet nach einer Anzahl Stunden oder Tage."
    ComposeMode.DATE_TIME -> "Öffnet an einem bestimmten Datum und zu einer Uhrzeit."
    ComposeMode.MANUAL -> "Bleibt gesperrt, bis du sie selbst freigibst."
    ComposeMode.MUTUAL -> "Öffnet erst, nachdem beide zugestimmt haben."
    ComposeMode.PRESENCE -> "Öffnet bei serverbestätigter gleichzeitiger Aktivität; das ist keine ausdrückliche Zustimmung."
    ComposeMode.RANDOM -> "Der Server wählt den Freigabezeitpunkt innerhalb deines Zeitfensters."
}
