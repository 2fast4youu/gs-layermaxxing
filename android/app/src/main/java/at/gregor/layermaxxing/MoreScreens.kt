package at.gregor.layermaxxing

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.drawBehind
import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
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

/** Where the "Mehr" hub can dive into a page without a new tab. */
internal enum class MoreDest(val title: String) {
    LETTERS("Briefe"), EP("Punkte"), GLOSSARY("Wörterbuch"), PROFILE("Profil"),
    APPEARANCE("Darstellung"), ACCOUNT("Konto & Sicherheit"), ABOUT("Über die App"),
}

@Composable
internal fun MoreScreen(
    token: String, api: ApiClient, store: SessionStore, status: ApiClient.Status?, sessions: List<ApiClient.Session>,
    blocked: List<ApiClient.UserSummary>, messages: List<ApiClient.Message>, outbox: List<ApiClient.Message>,
    ep: ApiClient.EpOverview?, settings: List<ApiClient.FriendshipSettings>, opened: Map<Long, OpenedMessage>,
    onTheme: (String) -> Unit, onLogout: () -> Unit,
    serverProfile: ServerProfile, onServerProfile: (ServerProfile) -> Unit,
    castleExperiment: Boolean, onCastleExperiment: (Boolean) -> Unit,
    creativeEligible: Boolean, creativeSwitch: Boolean, onCreativeSwitch: (Boolean) -> Unit,
    onRecovery: (String) -> Unit, onProof: (ApiClient.Message) -> Unit, onOpenLetter: (ApiClient.Message) -> Unit,
    onLockedTap: (ApiClient.Message) -> Unit, act: ((suspend () -> Unit) -> Unit),
    onExit: (() -> Unit)? = null, onAccounts: (() -> Unit)? = null,
) {
    var dest by remember { mutableStateOf<MoreDest?>(null) }
    BackHandler(enabled = dest != null) { dest = null }
    val current = dest
    if (current != null) SubScreen(current.title, onBack = { dest = null }) {
        when (current) {
            MoreDest.LETTERS -> InboxScreen(messages, outbox, opened, token, api, act, onOpenLetter, onLockedTap, onProof)
            MoreDest.EP -> EpScreen(token, api, settings, ep, draft = null, onDraftConsumed = {}, act = act)
            MoreDest.GLOSSARY -> GlossaryScreen(api, token)
            MoreDest.PROFILE -> ProfilePage(token, api, store, status, act, onRecovery)
            MoreDest.APPEARANCE -> AppearancePage(store, onTheme, onCastleExperiment)
            MoreDest.ACCOUNT -> AccountPage(token, api, sessions, blocked, creativeEligible, creativeSwitch, onCreativeSwitch, onRecovery, act)
            MoreDest.ABOUT -> AboutPage(status, serverProfile, onServerProfile)
        }
    } else Column(Modifier.fillMaxSize()) {
        if (onExit != null) Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = onExit) { Text("← Inseln") }
            if (onAccounts != null) TextButton(onClick = onAccounts) { Text("Konten wechseln") }
        }
        Box(Modifier.weight(1f)) { MoreHub(status, ep, onLogout, onDest = { dest = it }, placesOnIsland = castleExperiment) }
    }
}

/**
 * "Mehr" is a short menu, not a wall of settings: one profile card, six rows,
 * each opening its own page. Every former setting still exists one tap deeper.
 */
@Composable
private fun MoreHub(
    status: ApiClient.Status?, ep: ApiClient.EpOverview?, onLogout: () -> Unit, onDest: (MoreDest) -> Unit,
    placesOnIsland: Boolean = false,
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp).clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f))
                    .clickable { onDest(MoreDest.PROFILE) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(profileColor(status?.displayColor ?: "#315B8A").copy(alpha = .22f)),
                    contentAlignment = Alignment.Center,
                ) { Text(status?.avatarEmoji ?: "🙂", fontSize = 30.sp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(status?.name ?: "Profil", style = MaterialTheme.typography.titleLarge)
                    Text("Profil bearbeiten", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (status?.needsPassword == true) item {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.errorContainer).clickable { onDest(MoreDest.PROFILE) }.padding(14.dp),
            ) { Text("⚠  Dein Konto hat noch kein Passwort – jetzt absichern", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold) }
        }
        if (!placesOnIsland) item { MenuRow("✉", "Briefe", "Eingang, Ausgang und Nachweise") { onDest(MoreDest.LETTERS) } }
        if (!placesOnIsland) item { MenuRow("★", "Punkte", if ((ep?.given ?: 0) + (ep?.received ?: 0) > 0) "Gegeben ${ep?.given ?: 0} · Erhalten ${ep?.received ?: 0}" else "Noch keine") { onDest(MoreDest.EP) } }
        if (!placesOnIsland) item { MenuRow("📖", "Wörterbuch", "Begriffe der App, gemeinsam erklärt") { onDest(MoreDest.GLOSSARY) } }
        item { HorizontalDivider(Modifier.padding(vertical = 6.dp)) }
        item { MenuRow("🎨", "Darstellung", "Modus, Design, App-Sperre") { onDest(MoreDest.APPEARANCE) } }
        item { MenuRow("🔐", "Konto & Sicherheit", "Passwort, Geräte, Blockierungen") { onDest(MoreDest.ACCOUNT) } }
        item { MenuRow("ℹ", "Über die App", "Server und Version") { onDest(MoreDest.ABOUT) } }
        item { Spacer(Modifier.height(12.dp)) }
        item { OutlinedButton(onClick = onLogout, Modifier.fillMaxWidth().height(48.dp)) { Text("Auf diesem Gerät abmelden") } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun MenuRow(glyph: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Text(glyph, fontSize = 18.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text("›", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The 12 avatars to pick from; a custom emoji set earlier stays selectable. */
internal val AVATAR_CHOICES = listOf("🦊", "🐻", "🦉", "🐺", "🦌", "🐗", "🦅", "🐿", "🦡", "🐴", "🐱", "🐶")
internal val PROFILE_COLORS = listOf("#195C3A" to "Grün", "#315B8A" to "Blau", "#8A671B" to "Gold", "#8A3B3B" to "Rot")

@Composable
private fun ProfilePage(
    token: String, api: ApiClient, store: SessionStore, status: ApiClient.Status?,
    act: ((suspend () -> Unit) -> Unit), onRecovery: (String) -> Unit,
) {
    var name by remember(status?.name) { mutableStateOf(status?.name.orEmpty()) }
    var emoji by remember(status?.avatarEmoji) { mutableStateOf(status?.avatarEmoji ?: "🦊") }
    var color by remember(status?.displayColor) { mutableStateOf(status?.displayColor ?: "#315B8A") }
    var discoverable by remember(status?.discoverable) { mutableStateOf(status?.discoverable ?: true) }
    var migrationPassword by remember { mutableStateOf("") }
    val choices = if (emoji in AVATAR_CHOICES || emoji.isBlank()) AVATAR_CHOICES else listOf(emoji) + AVATAR_CHOICES
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (status?.needsPassword == true) item { Card { Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Konto absichern", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            OutlinedTextField(migrationPassword, { migrationPassword = it }, Modifier.fillMaxWidth(), label = { Text("Neues Passwort") }, visualTransformation = PasswordVisualTransformation())
            Button(onClick = { act { onRecovery(api.setPassword(token, migrationPassword)) } }, enabled = migrationPassword.length >= 8) { Text("Passwort setzen") }
        } } }
        item {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(84.dp).clip(CircleShape).background(profileColor(color).copy(alpha = .22f)), contentAlignment = Alignment.Center) {
                    Text(emoji, fontSize = 46.sp)
                }
            }
        }
        item { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Benutzername") }, singleLine = true) }
        item { Text("Avatar", style = MaterialTheme.typography.titleMedium) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.chunked(6).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { option ->
                            val on = option == emoji
                            Box(
                                Modifier.size(48.dp).clip(CircleShape)
                                    .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f))
                                    .border(if (on) 2.dp else 0.dp, if (on) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                                    .clickable { emoji = option }.semantics { contentDescription = "Avatar $option" },
                                contentAlignment = Alignment.Center,
                            ) { Text(option, fontSize = 24.sp) }
                        }
                        repeat(6 - row.size) { Spacer(Modifier.size(48.dp)) }
                    }
                }
            }
        }
        item { Text("Farbe", style = MaterialTheme.typography.titleMedium) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PROFILE_COLORS.forEach { (value, label) ->
                    val on = color.equals(value, true)
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(profileColor(value))
                            .border(if (on) 3.dp else 0.dp, if (on) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                            .clickable { color = value }.semantics { contentDescription = "Farbe $label" },
                        contentAlignment = Alignment.Center,
                    ) { if (on) Text("✓", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Auffindbar", fontWeight = FontWeight.SemiBold)
                Text("Andere finden dich in der Nutzersuche", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(discoverable, { discoverable = it })
        } }
        item {
            Button(
                onClick = { act { api.updateProfile(token, name, emoji, color, discoverable); store.name = name } },
                enabled = name.isNotBlank() && Regex("^#[0-9A-Fa-f]{6}$").matches(color),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("Speichern") }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun AppearancePage(store: SessionStore, onTheme: (String) -> Unit, onCastleExperiment: (Boolean) -> Unit) {
    val context = LocalContext.current
    val notificationGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
    var biometric by remember { mutableStateOf(store.biometricEnabled) }
    var debugHitboxes by remember { mutableStateOf(store.debugHitboxes) }
    var theme by remember { mutableStateOf(store.theme) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionTitle("Modus") }
        item { Card { Column(Modifier.padding(vertical = 6.dp)) {
            AppMode.entries.forEach { mode ->
                Row(Modifier.fillMaxWidth().clickable { store.appMode = mode; onCastleExperiment(mode.showsValley) }.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(selected = store.appMode == mode, onClick = { store.appMode = mode; onCastleExperiment(mode.showsValley) })
                    Column(Modifier.weight(1f)) { Text(mode.label, fontWeight = FontWeight.SemiBold); Text(mode.detail, style = MaterialTheme.typography.bodySmall) }
                }
            }
        } } }
        item { Text("Nachrichten und Insel-Fortschritt bleiben beim Wechseln erhalten.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { SectionTitle("Design") }
        item { Card { Column(Modifier.padding(vertical = 6.dp)) {
            listOf("system" to "Wie das Handy", "light" to "Hell", "dark" to "Dunkel", "antique" to "Altertümlich").forEach { (key, label) ->
                Row(Modifier.fillMaxWidth().clickable { theme = key; onTheme(key) }.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(selected = theme == key, onClick = { theme = key; onTheme(key) })
                    Text(label)
                }
            }
        } } }
        item { SectionTitle("Schutz") }
        item { Card { Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("App mit Fingerabdruck sperren", Modifier.weight(1f))
                Switch(biometric, { biometric = it; store.biometricEnabled = it })
            }
            if (!notificationGranted && Build.VERSION.SDK_INT >= 33) {
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        })
                    }
                }) { Text("Benachrichtigungen erlauben") }
            } else Text("✓ Benachrichtigungen erlaubt", fontSize = 12.sp, color = Color(0xFF19703B))
        } } }
        item { SectionTitle("Entwickler") }
        item { Card { Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Debug-Modus", fontWeight = FontWeight.SemiBold)
                Text("Zeigt die Tippflächen (Hitboxen) in der Inselwelt rot umrandet.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(debugHitboxes, { debugHitboxes = it; store.debugHitboxes = it; DebugMode.hitboxes.value = it })
        } } }
    }
}

@Composable
private fun AccountPage(
    token: String, api: ApiClient, sessions: List<ApiClient.Session>, blocked: List<ApiClient.UserSummary>,
    creativeEligible: Boolean, creativeSwitch: Boolean, onCreativeSwitch: (Boolean) -> Unit,
    onRecovery: (String) -> Unit, act: ((suspend () -> Unit) -> Unit),
) {
    var oldPassword by remember { mutableStateOf("") }; var newPassword by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionTitle("Passwort") }
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
        item { SectionTitle("Blockiert") }
        if (blocked.isEmpty()) item { Text("Niemand blockiert.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) }
        else items(blocked, key = { "blocked-${it.id}" }) { user ->
            ActionCard(user.name) { TextButton(onClick = { act { api.unblock(token, user.id) } }) { Text("Freigeben") } }
        }
        if (creativeEligible) {
            item { SectionTitle("Testserver") }
            item { Card { Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Kreativmodus", fontWeight = FontWeight.SemiBold)
                    Text("Frei bauen zum Testen; Briefe und Punkte laufen echt.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(creativeSwitch, onCreativeSwitch)
            } } }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun AboutPage(status: ApiClient.Status?, serverProfile: ServerProfile, onServerProfile: (ServerProfile) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionTitle("Server") }
        item { ServerProfileSelector(serverProfile, onServerProfile) }
        if (!serverProfile.isConfigured()) item { Text("Nicht konfiguriert", color = MaterialTheme.colorScheme.error) }
        if (status?.serverRole == "test") item { Text(ServerProfilePolicy.TEST_WARNING_DETAIL, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
        item { SectionTitle("App") }
        item { Text("GS Layermaxxing ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Text("Neue Nachrichten werden regelmäßig im Hintergrund geprüft.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}


/** Test seam: the hub without its pages. */
@Composable
internal fun MoreHubPreview(status: ApiClient.Status?, ep: ApiClient.EpOverview?) = MoreHub(status, ep, onLogout = {}, onDest = {})
