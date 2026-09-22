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
            LayermaxxingTheme(theme) {
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
            }
        }
    }
}

private enum class AuthMode { LOGIN, REGISTER, RECOVER }

@Composable
private fun AuthScreen(
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
    Box(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().widthIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.brand_mark),
                contentDescription = "GS Layermaxxing Logo",
                modifier = Modifier.size(92.dp).clip(RoundedCornerShape(24.dp)).align(Alignment.CenterHorizontally),
            )
            Text("GS Layermaxxing", fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(
                "Geheime Nachrichten – sichtbar, wenn die Zeit reif ist.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            ServerProfileSelector(profile, onProfile)
            if (!profile.isConfigured()) Text(
                "${profile.label} ist in diesem Build nicht konfiguriert.",
                color = MaterialTheme.colorScheme.error,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mode == AuthMode.LOGIN) Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("Anmelden") }
                else OutlinedButton(onClick = { mode = AuthMode.LOGIN; error = null }, modifier = Modifier.weight(1f)) { Text("Anmelden") }
                if (mode == AuthMode.REGISTER) Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("Registrieren") }
                else OutlinedButton(onClick = { mode = AuthMode.REGISTER; error = null }, modifier = Modifier.weight(1f)) { Text("Registrieren") }
            }
            TextButton(onClick = { mode = AuthMode.RECOVER; error = null }, modifier = Modifier.align(Alignment.End)) {
                Text("Passwort vergessen?")
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
                        }.onSuccess(onAuthenticated).onFailure { error = it.message ?: "Vorgang fehlgeschlagen" }
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
            onCancel?.let { cancel ->
                TextButton(onClick = cancel, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Abbrechen") }
            }
        }
    }
}

@Composable
fun ServerProfileSelector(selected: ServerProfile, onSelected: (ServerProfile) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Server", fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ServerProfile.entries.forEach { profile ->
                if (selected == profile) Button(onClick = {}, modifier = Modifier.weight(1f)) { Text(profile.label) }
                else OutlinedButton(onClick = { onSelected(profile) }, modifier = Modifier.weight(1f)) { Text(profile.label) }
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
            Text("🔐", fontSize = 52.sp); Text("App gesperrt", fontWeight = FontWeight.Bold, fontSize = 22.sp)
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
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFF9FD5B5), primaryContainer = Color(0xFF174F35), background = Color(0xFF0D1712), surface = Color(0xFF142019), surfaceVariant = Color(0xFF24352B))
        else -> lightColorScheme(primary = Color(0xFF195C3A), primaryContainer = Color(0xFFD6F3E1), secondary = Color(0xFF8A671B), background = Color(0xFFF6F9F6), surface = Color.White, surfaceVariant = Color(0xFFE7EEE9))
    }
    MaterialTheme(colorScheme = scheme, shapes = Shapes(
        small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(28.dp),
    ), typography = if (mode == "antique") MaterialTheme.typography.copy(
        headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontFamily = FontFamily.Serif),
        titleLarge = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif),
    ) else MaterialTheme.typography) {
        if (mode == "antique") CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = FontFamily.Serif), content = content,
        ) else content()
    }
}
