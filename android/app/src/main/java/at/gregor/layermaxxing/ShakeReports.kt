package at.gregor.layermaxxing

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.sqrt

internal val LocalShakeReports = staticCompositionLocalOf<ShakeReports?> { null }

/** Activity-owned, no service/worker/global token holder. Only the main thread changes its gate. */
internal class ShakeReports(private val activity: Activity) : SensorEventListener {
    private val preferences = activity.getSharedPreferences("shake-reports", Context.MODE_PRIVATE)
    private val session = SessionStore(activity)
    private val sessionPreferences = activity.getSharedPreferences("layermaxxing_session", Context.MODE_PRIVATE)
    private val manager = activity.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor = runCatching { manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }.getOrNull()
    private val detector = ShakeDetector()
    private val storage = ShakeReportStorage(File(activity.filesDir, "shake-reports"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    @Volatile private var generation = 0L
    @Volatile private var resumed = false
    @Volatile private var bound: Binding? = null
    private var registered = false
    private var busy = false
    private data class Binding(val token: String, val account: String, val profile: ServerProfile, val role: String?, val api: ApiClient)
    var enabled by mutableStateOf(preferences.getBoolean("local", false)); private set
    var autoUpload by mutableStateOf(preferences.getBoolean("upload", false)); private set
    var status by mutableStateOf(preferences.getString("status", "Noch kein Schüttelbericht gespeichert").orEmpty()); private set
    val sensorAvailable get() = sensor != null
    var captureAvailable by mutableStateOf(false); private set
    private val changed = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in setOf("token", "name", "server_profile", "biometric")) {
            // SessionStore changes synchronously before Compose can update its own state.
            invalidate(); if (key != "biometric") bound = null; refreshSensor()
        }
    }
    init { sessionPreferences.registerOnSharedPreferenceChangeListener(changed) }
    fun bind(token: String?, unlocked: Boolean, profile: ServerProfile, role: String?, api: ApiClient) {
        invalidate()
        bound = if (token != null && unlocked) Binding(token, session.name, profile, role, api) else null
        refreshSensor()
    }
    fun unbind() { invalidate(); bound = null; refreshSensor() }
    fun resume() { resumed = true; refreshSensor() }
    fun pause() { resumed = false; invalidate(); refreshSensor() }
    fun destroy() { pause(); scope.coroutineContext[Job]?.cancel(); sessionPreferences.unregisterOnSharedPreferenceChangeListener(changed) }
    private fun invalidate() { generation++; job?.cancel(); detector.reset() }
    fun enableCapture(value: Boolean) {
        invalidate(); enabled = value
        if (!value) autoUpload = false
        preferences.edit().putBoolean("local", enabled).putBoolean("upload", autoUpload).apply()
        refreshSensor()
    }
    /** Caller must first display the explicit content/upload confirmation. */
    fun setUploadConfirmed(value: Boolean) {
        invalidate(); autoUpload = value && enabled
        preferences.edit().putBoolean("upload", autoUpload).apply()
    }
    private fun updateStatus(value: String) {
        status = value; preferences.edit().putString("status", value).apply()
    }
    private fun deviceOpen(): Boolean =
        (activity.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive &&
            !(activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked &&
            !(activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked
    private fun live(binding: Binding, expected: Long): Boolean = expected == generation && resumed && enabled &&
        bound === binding && session.token == binding.token && session.name == binding.account &&
        session.serverProfile == binding.profile && !activity.isFinishing && !activity.isDestroyed && deviceOpen()
    private fun refreshSensor() {
        runCatching { manager?.unregisterListener(this) }; registered = false; detector.reset()
        captureAvailable = enabled && bound != null && resumed
        if (captureAvailable && sensor != null && deviceOpen()) {
            registered = runCatching { manager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI) == true }.getOrDefault(false)
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onSensorChanged(event: SensorEvent) {
        val b = bound ?: return
        if (busy) return
        val v = event.values
        val g = sqrt((v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble()) / SensorManager.GRAVITY_EARTH
        if (detector.sample(SystemClock.elapsedRealtime(), g, registered && live(b, generation))) capture(claimed = true)
    }
    fun captureAfterDialog() {
        val expected = generation
        activity.window.decorView.postOnAnimation {
            activity.window.decorView.postOnAnimation {
                if (expected == generation) capture()
            }
        }
    }
    fun capture(claimed: Boolean = false) {
        val b = bound ?: return
        val expected = generation
        if (!live(b, expected) || busy) return
        if (!claimed && !detector.claim(SystemClock.elapsedRealtime())) return
        busy = true
        job = scope.launch {
            var bitmap: Bitmap? = null
            var saved = false
            try {
                check(live(b, expected))
                check(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE == 0)
                val view = activity.window.decorView
                check(view.width > 0 && view.height > 0)
                val scale = minOf(1.0, 8192.0 / view.width, 8192.0 / view.height,
                    sqrt(1_500_000.0 / (view.width.toDouble() * view.height)))
                val width = (view.width * scale).toInt().coerceAtLeast(1)
                val height = (view.height * scale).toInt().coerceAtLeast(1)
                // View drawing is always on Main, before any success/status UI changes.
                val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap = image
                val canvas = Canvas(image); canvas.scale(width.toFloat() / view.width, height.toFloat() / view.height)
                view.draw(canvas)
                val file = withContext(Dispatchers.IO) {
                    check(live(b, expected))
                    val text = CrashDiagnostics.shakeSnapshot(activity.applicationContext)
                    val bytes = ByteArrayOutputStream()
                    BoundedOutput(bytes, ShakePng.MAX_BYTES - ShakePng.MAX_META - 128).use {
                        check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                    check(live(b, expected))
                    storage.save(ShakePng.embed(bytes.toByteArray(), text))
                }
                saved = true
                if (!live(b, expected)) return@launch
                updateStatus("PNG lokal gespeichert (private App-Dateien)")
                if (autoUpload && b.profile == ServerProfile.GERFRIED && b.role == "test") {
                    val receipt = b.api.uploadShakeReport(b.token, b.profile, file) {
                        live(b, expected) && autoUpload
                    }
                    if (live(b, expected) && autoUpload) updateStatus("Gerfried TESTserver bestätigt: ${receipt.id}")
                } else if (autoUpload) updateStatus("Lokal gespeichert; Upload nur bei bestätigtem Gerfried TESTserver")
            } catch (_: CancellationException) {
                // A revoked consent/account/lifecycle gate never accepts a late result.
            } catch (_: Throwable) {
                if (live(b, expected)) updateStatus(if (saved) "PNG lokal gespeichert; Upload/Prüfung fehlgeschlagen" else "Aufnahme nicht möglich (z. B. geschütztes Fenster oder Speicher)")
            } finally { bitmap?.recycle(); busy = false }
        }
    }
    fun clear() {
        invalidate()
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { storage.clear() }.getOrDefault(false) }
            updateStatus(if (ok) "Lokale Schüttelberichte gelöscht; Serverkopien bleiben bis Löschung/7 Tage" else "Lokale Löschung fehlgeschlagen")
        }
    }
}
