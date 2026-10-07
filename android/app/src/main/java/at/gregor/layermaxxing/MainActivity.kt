package at.gregor.layermaxxing

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.Typography
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalTextStyle
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onPostResume() {
        super.onPostResume()
        if (Build.VERSION.SDK_INT < 33) return
        val permissionPrefs = getSharedPreferences("layermaxxing_permissions", MODE_PRIVATE)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !permissionPrefs.getBoolean("notifications_asked", false)) {
            permissionPrefs.edit().putBoolean("notifications_asked", true).apply()
            window.decorView.post {
                if (!isFinishing && !isDestroyed) ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 3102,
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val store = remember { SessionStore(this) }
            var theme by remember { mutableStateOf(store.theme) }
            remember { DebugMode.hitboxes.value = store.debugHitboxes; true }
            LayermaxxingTheme(theme) { androidx.compose.runtime.CompositionLocalProvider(LocalHitboxDebug provides DebugMode.hitboxes.value) {
                var profile by remember { mutableStateOf(store.serverProfile) }
                val api = remember(profile) { ApiClient(profile.baseUrl) }
                var token by remember { mutableStateOf(store.token) }
                var accounts by remember { mutableStateOf(store.savedAccounts()) }
                var addingAccount by remember { mutableStateOf(false) }
                var authNotice by remember { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()
                var recoveryCode by remember { mutableStateOf<String?>(null) }
                var biometricPassed by remember { mutableStateOf(!store.biometricEnabled) }
                var verifiedRole by remember(profile) { mutableStateOf<String?>(null) }
                var confirmTestServer by remember(profile) { mutableStateOf(store.warningConfirmationRequired) }

                LaunchedEffect(profile) {
                    verifiedRole = if (profile.isConfigured()) runCatching { api.serverInfo().role }.getOrNull() else null
                }

                fun selectProfile(selected: ServerProfile) {
                    if (selected == profile) return
                    store.selectServerProfile(selected)
                    profile = selected
                    token = store.token
                    recoveryCode = null
                    biometricPassed = !store.biometricEnabled
                }

                LaunchedEffect(token) {
                    if (token != null) {
                        // OEMs may reject WorkManager/permission launches during the
                        // login composition transition. Neither may crash the app.
                        runCatching { NotificationScheduler.start(applicationContext) }
                    }
                }

                if (confirmTestServer) AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Testserver ausgewählt") },
                    text = {
                        Text(
                            ServerProfilePolicy.TEST_WARNING_DETAIL +
                                " Verwende hier keine vertraulichen Produktionsdaten.",
                        )
                    },
                    confirmButton = { Button(onClick = {
                        store.acknowledgeTestWarning(); confirmTestServer = false
                    }) { Text("Verstanden") } },
                )

                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column {
                        val warn = ServerProfilePolicy.showTestWarning(profile, verifiedRole)
                        if (warn) TestServerBanner()
                        // The strip already sits under the status bar, so everything
                        // below it must stop reserving that inset a second time —
                        // otherwise an edge-to-edge screen pushes its own chrome down
                        // by a full status bar for nothing.
                        Box(
                            Modifier.weight(1f).then(
                                if (warn) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier,
                            ),
                        ) { when {
                        token == null -> AuthScreen(api, profile, ::selectProfile, notice = authNotice) { auth ->
                            store.token = auth.token; store.name = auth.name
                            store.saveAccount(auth.name, auth.token)
                            accounts = store.savedAccounts()
                            authNotice = null
                            token = auth.token; recoveryCode = auth.recoveryCode
                            biometricPassed = !store.biometricEnabled
                        }
                        !biometricPassed -> BiometricGate(this@MainActivity) { biometricPassed = true }
                        addingAccount -> AuthScreen(api, profile, ::selectProfile, onCancel = { addingAccount = false }) { auth ->
                            store.token = auth.token; store.name = auth.name
                            store.saveAccount(auth.name, auth.token)
                            accounts = store.savedAccounts()
                            token = auth.token; recoveryCode = auth.recoveryCode
                            biometricPassed = !store.biometricEnabled
                            addingAccount = false
                        }
                        else -> LayerHome(
                            token = token!!, api = api, store = store, initialRecoveryCode = recoveryCode,
                            onRecoveryCodeSeen = { recoveryCode = null },
                            onTheme = { store.theme = it; theme = it },
                            accounts = accounts,
                            onAddAccount = { addingAccount = true },
                            onSwitchAccount = { account ->
                                scope.launch {
                                    val failure = runCatching { api.status(account.token) }.exceptionOrNull()
                                    if (failure is ApiException && failure.code == 401) {
                                        store.removeAccount(account.name)
                                        accounts = store.savedAccounts()
                                        authNotice = "Die gespeicherte Anmeldung von ${account.name} ist nicht mehr gültig. Bitte neu anmelden."
                                        store.token = null; token = null; recoveryCode = null
                                    } else {
                                        store.token = account.token; store.name = account.name
                                        authNotice = null
                                        recoveryCode = null; token = account.token
                                    }
                                }
                            },
                            serverProfile = profile,
                            onServerProfile = ::selectProfile,
                            onLogout = {
                                val active = token
                                val activeName = store.name
                                store.removeAccount(activeName)
                                accounts = store.savedAccounts()
                                store.clear(); token = null; recoveryCode = null
                                if (active != null) kotlinx.coroutines.MainScope().launch { runCatching { api.logout(active) } }
                            },
                        )
                    } }
                    }
                }
            } }
        }
    }
}

private enum class AuthMode { LOGIN, REGISTER, RECOVER }

@Composable
internal fun AuthScreen(
    api: ApiClient, profile: ServerProfile, onProfile: (ServerProfile) -> Unit,
    notice: String? = null, onCancel: (() -> Unit)? = null,
    onAuthenticated: (ApiClient.Auth) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(AuthMode.LOGIN) }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(notice) }
    // The village greets you before you even log in: same painting, softly dimmed.
    Box(Modifier.fillMaxSize().background(Color(0xFF14202F))) {
        Image(
            painterResource(R.drawable.village_plate), contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop, alpha = .55f,
        )
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
            listOf(Color(0x3314202F), Color(0x9914202F), Color(0xE614202F)),
        )))
    }
    Box(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().widthIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(vertical = 24.dp)
                .shadow(16.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.brand_mark),
                contentDescription = "GS Layermaxxing Logo",
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).align(Alignment.CenterHorizontally),
            )
            Text("GS Layermaxxing", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(
                "Geheime Nachrichten – sichtbar, wenn die Zeit reif ist.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp),
            )
            if (!profile.isConfigured()) Text(
                "${profile.label} ist in diesem Build nicht konfiguriert.",
                color = MaterialTheme.colorScheme.error,
            )
            // Tabs, not buttons: only the big button at the bottom submits.
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(AuthMode.LOGIN to "Ich habe ein Konto", AuthMode.REGISTER to "Neu hier").forEach { (m, label) ->
                    val on = mode == m || (m == AuthMode.LOGIN && mode == AuthMode.RECOVER)
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(50))
                            .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { mode = m; error = null },
                        contentAlignment = Alignment.Center,
                    ) { Text(label, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium, fontSize = 14.sp,
                        color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Benutzername") }, singleLine = true)
            if (mode == AuthMode.RECOVER) {
                OutlinedTextField(code, { code = it }, Modifier.fillMaxWidth(), label = { Text("Wiederherstellungscode") }, singleLine = true)
            }
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(),
                label = { Text(if (mode == AuthMode.RECOVER) "Neues Passwort" else "Passwort") },
                singleLine = true, visualTransformation = PasswordVisualTransformation())
            if (mode == AuthMode.REGISTER) {
                OutlinedTextField(password2, { password2 = it }, Modifier.fillMaxWidth(),
                    label = { Text("Passwort wiederholen") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation())
                if (password2.isNotEmpty() && password != password2) Text(
                    "Die Passwörter stimmen nicht überein.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error,
                )
            }
            if (mode != AuthMode.LOGIN) Text("Mindestens 8 Zeichen", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (mode == AuthMode.LOGIN) TextButton(onClick = { mode = AuthMode.RECOVER; error = null }, modifier = Modifier.align(Alignment.End)) {
                Text("Passwort vergessen?")
            }
            error?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(14.dp),
                ) { Text(it, Modifier.fillMaxWidth().padding(12.dp)) }
            }
            Button(
                onClick = {
                    scope.launch {
                        busy = true; error = null
                        runCatching {
                            when (mode) {
                                AuthMode.REGISTER -> api.register(name.trim(), password)
                                AuthMode.LOGIN -> api.login(name.trim(), password)
                                AuthMode.RECOVER -> api.recover(name.trim(), code.trim(), password)
                            }
                        }.onSuccess(onAuthenticated).onFailure { error = ApiErrors.friendly(it.message) }
                        busy = false
                    }
                },
                enabled = profile.isConfigured() && name.isNotBlank() && password.length >= 8 &&
                    (mode != AuthMode.RECOVER || code.isNotBlank()) &&
                    (mode != AuthMode.REGISTER || password == password2) && !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (busy) CircularProgressIndicator(strokeWidth = 2.dp)
                else Text(when (mode) { AuthMode.REGISTER -> "Konto erstellen"; AuthMode.LOGIN -> "Anmelden"; AuthMode.RECOVER -> "Konto wiederherstellen" })
            }
            ServerProfileSelector(profile, onProfile)
            onCancel?.let { cancel ->
                TextButton(onClick = cancel, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Abbrechen") }
            }
        }
    }
}

@Composable
fun ServerProfileSelector(selected: ServerProfile, onSelected: (ServerProfile) -> Unit) {
    // One quiet line; the choice opens on tap. Still always visible, so nobody
    // logs into the wrong server by accident.
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).then(
            if (open) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)) else Modifier,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("Server: ${selected.label}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (open) "  ▴" else "  ▾", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (open) ServerProfile.entries.forEach { profile ->
            Row(
                Modifier.fillMaxWidth().clickable { onSelected(profile); open = false }
                    .background(if (profile == selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(profile.label, Modifier.weight(1f), fontSize = 14.sp)
                if (profile == selected) AppIcon(R.drawable.ico_check, null, tint = MaterialTheme.colorScheme.primary, size = 20.dp)
            }
        }
    }
}

/**
 * The test-server strip: one compact line, still unmistakable.
 *
 * It is the thinnest thing that can still be honest — a red band with the server
 * named on it. The full sentence lives on the confirmation dialog and in "Mehr",
 * so this strip never has to wrap into a second row of chrome.
 */
@Composable
private fun TestServerBanner() {
    Surface(color = Color(0xFFD84315), contentColor = Color.White) {
        Text(
            ServerProfilePolicy.TEST_WARNING,
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 2.dp),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            maxLines = 1,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
private fun BiometricGate(activity: FragmentActivity, onSuccess: () -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    fun authenticate() {
        val manager = BiometricManager.from(activity)
        if (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL) != BiometricManager.BIOMETRIC_SUCCESS) {
            error = "Biometrie oder Gerätesperre ist nicht verfügbar."
            return
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(code: Int, text: CharSequence) { error = text.toString() }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("GS Layermaxxing entsperren")
            .setSubtitle("Nachrichten mit Biometrie oder Gerätesperre schützen")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL).build())
    }
    LaunchedEffect(Unit) { authenticate() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon(R.drawable.ico_app_lock, null, size = 57.dp); Text("App gesperrt", fontWeight = FontWeight.Bold, fontSize = 22.sp)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = ::authenticate) { Text("Entsperren") }
        }
    }
}

@Composable
fun LayermaxxingTheme(mode: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = mode == "dark" || (mode == "system" && systemDark)
    val scheme = when {
        mode == "antique" -> lightColorScheme(
            primary = Color(0xFF5A3A1E), primaryContainer = Color(0xFFE8D4AD), secondary = Color(0xFF7A5B35),
            background = Color(0xFFF4E8C9), surface = Color(0xFFFFF5DC), surfaceVariant = Color(0xFFE6D6B5),
        )
        // Optional: the phone's own Material You colours.
        mode == "material" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        // Default: the neutral "Hafenpost" look of the chat list for every screen
        // outside the island world – harbour paper, sea teal, navy ink, gold trim.
        dark -> darkColorScheme(
            primary = Color(0xFF6FB9C9), onPrimary = Color(0xFF08222B), primaryContainer = Color(0xFF1E4A50), onPrimaryContainer = Color(0xFFE4F4EF),
            secondary = Color(0xFFE2B457), onSecondary = Color(0xFF2A1E05), secondaryContainer = Color(0xFF3A3220), onSecondaryContainer = Color(0xFFF6E6C2),
            tertiary = Color(0xFF8ED3E0),
            background = Color(0xFF111B26), onBackground = Color(0xFFEDE6D6), surface = Color(0xFF111B26), onSurface = Color(0xFFEDE6D6),
            surfaceVariant = Color(0xFF1E2B39), onSurfaceVariant = Color(0xFFA9A08E), outline = Color(0xFF4A5666), outlineVariant = Color(0xFF2A3644),
            surfaceContainerLowest = Color(0xFF0C141D), surfaceContainerLow = Color(0xFF16212D), surfaceContainer = Color(0xFF1A2633),
            surfaceContainerHigh = Color(0xFF1E2B39), surfaceContainerHighest = Color(0xFF243243),
            error = Color(0xFFD06A5C),
        )
        else -> lightColorScheme(
            primary = Color(0xFF1C4E66), onPrimary = Color.White, primaryContainer = Color(0xFFD5ECE6), onPrimaryContainer = Color(0xFF173A40),
            secondary = Color(0xFF2F7F95), onSecondary = Color.White, secondaryContainer = Color(0xFFEFE3CA), onSecondaryContainer = Color(0xFF3B3122),
            tertiary = Color(0xFFC8962F),
            background = Color(0xFFF7F0E1), onBackground = Color(0xFF22303F), surface = Color(0xFFF7F0E1), onSurface = Color(0xFF22303F),
            surfaceVariant = Color(0xFFEFE3CA), onSurfaceVariant = Color(0xFF6E6555), outline = Color(0xFFB5A688), outlineVariant = Color(0xFFE0D2B4),
            surfaceContainerLowest = Color(0xFFFFFDF8), surfaceContainerLow = Color(0xFFFFFBF2), surfaceContainer = Color(0xFFFBF5E8),
            surfaceContainerHigh = Color(0xFFF5EDDC), surfaceContainerHighest = Color(0xFFEFE6D2),
            error = Color(0xFFB3473C),
        )
    }
    MaterialTheme(colorScheme = scheme, shapes = Shapes(
        small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(28.dp),
    ), typography = if (mode == "antique") MaterialTheme.typography.copy(
        headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontFamily = FontFamily.Serif),
        titleLarge = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif),
    ) else BrandType.typography()) {
        if (mode == "antique") CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = FontFamily.Serif), content = content,
        ) else CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = Kit.Body), content = content)
    }
}

/**
 * One voice across messenger and village: Lilita One for headings (the same
 * letters as the village HUD), Nunito for everything you read.
 */
internal object BrandType {
    fun typography(): Typography {
        val b = Typography()
        fun androidx.compose.ui.text.TextStyle.body() = copy(fontFamily = Kit.Body)
        fun androidx.compose.ui.text.TextStyle.display() = copy(fontFamily = Kit.Display, fontWeight = FontWeight.Normal)
        return Typography(
            displayLarge = b.displayLarge.display(), displayMedium = b.displayMedium.display(), displaySmall = b.displaySmall.display(),
            headlineLarge = b.headlineLarge.display(), headlineMedium = b.headlineMedium.display(), headlineSmall = b.headlineSmall.display(),
            titleLarge = b.titleLarge.display(),
            titleMedium = b.titleMedium.body().copy(fontWeight = FontWeight.ExtraBold), titleSmall = b.titleSmall.body().copy(fontWeight = FontWeight.ExtraBold),
            bodyLarge = b.bodyLarge.body(), bodyMedium = b.bodyMedium.body(), bodySmall = b.bodySmall.body(),
            labelLarge = b.labelLarge.body().copy(fontWeight = FontWeight.ExtraBold), labelMedium = b.labelMedium.body().copy(fontWeight = FontWeight.Bold),
            labelSmall = b.labelSmall.body(),
        )
    }
}
