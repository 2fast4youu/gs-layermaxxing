package at.gregor.layermaxxing

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Debug
import java.io.File

class LayermaxxingApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashDiagnostics.install(this)
    }
}

internal object CrashDiagnostics {
    private var handler: CrashUncaughtHandler? = null
    internal var storage: CrashStorage? = null
        private set
    private lateinit var metadata: CrashMetadata

    @Synchronized fun install(context: Context) {
        if (handler != null) return
        // Construct private storage and static metadata before installing; no SessionStore/API access.
        val store = CrashStorage(File(context.filesDir, "crash-diagnostics"))
        metadata = CrashMetadata(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.BUILD_TYPE, Build.VERSION.SDK_INT, Build.MODEL)
        storage = store
        // Prebuilt fallback avoids traversing stacks/causes when the VM is out of heap.
        val emergency = CrashFormatter.header(metadata, CrashMemory(0, 0, 0, 0, 0), System.currentTimeMillis()) +
            "Java crash: java.lang.OutOfMemoryError; emergency metadata captured at startup; memory unavailable.\n"
        val installed = CrashUncaughtHandler(Thread.getDefaultUncaughtExceptionHandler(), { error ->
            if (error is OutOfMemoryError) store.save(emergency)
            else store.save(CrashFormatter.report(error, metadata, memory(), store.events(), System.currentTimeMillis()))
        }, {
            android.os.Process.killProcess(android.os.Process.myPid())
            kotlin.system.exitProcess(10)
        })
        handler = installed
        Thread.setDefaultUncaughtExceptionHandler(installed)
        store.start()
    }

    private fun memory(): CrashMemory {
        val rt = Runtime.getRuntime()
        return CrashMemory(rt.totalMemory() - rt.freeMemory(), rt.totalMemory(), rt.maxMemory(),
            runCatching { Debug.getNativeHeapAllocatedSize() }.getOrDefault(0),
            runCatching { Debug.getPss() }.getOrDefault(0))
    }
    fun tab(tab: MainTab) { runCatching { storage?.tab(CrashTab.valueOf(tab.name)) } }
    fun island(view: CrashIslandView, friends: Int, messages: Int, gridVersion: Int) {
        runCatching { storage?.island(view, friends, messages, gridVersion) }
    }
    fun pending(): Boolean = runCatching { storage?.pending() == true }.getOrDefault(false)
    fun dismiss() { runCatching { storage?.dismiss() } }
    fun clear(): Boolean = runCatching { storage?.clear() == true }.getOrDefault(false)
    fun preview(context: Context): String = try {
        val saved = storage?.saved().orEmpty()
        ("Lokaler Fehlerbericht · nur auf diesem Gerät\n" +
            "Keine automatische Übertragung. Kopieren nur auf Tastendruck.\n" +
            (if (saved.isEmpty()) "Kein Java-Absturz aufgezeichnet.\n" else "Letzter gespeicherter Java-Absturz\n$saved\n") +
            "Aktueller Lauf\n" + CrashFormatter.header(metadata, memory(), System.currentTimeMillis()) +
            storage?.events().orEmpty() + "\nVorheriger Lauf (auch ohne Java-Absturz)\n" +
            storage?.previousEvents().orEmpty() + "\n" + historicalExits(context)).take(64 * 1024)
    } catch (_: Throwable) { "Lokale Diagnose derzeit nicht lesbar. Keine automatische Übertragung." }

    /** Called only when the user opens diagnostics, never during startup or exception handling. */
    internal fun historicalExits(context: Context): String {
        if (Build.VERSION.SDK_INT < 30) return "System-Prozessenden: erst ab Android 11 verfügbar. Nicht alle Systemabbrüche werden erfasst."
        return try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val reasons = manager.getHistoricalProcessExitReasons(context.packageName, 0, 5)
            "System-Prozessenden (begrenzte Historie; nicht alle Abbrüche erfasst):\n" +
                if (reasons.isEmpty()) "Keine Systemeinträge verfügbar.\n" else reasons.joinToString("\n") {
                    // Never access description, processName or traceInputStream.
                    "UTC ${java.time.Instant.ofEpochMilli(it.timestamp)} reasonCode=${it.reason} pssKB=${it.pss.coerceIn(0, 1L shl 50)} rssKB=${it.rss.coerceIn(0, 1L shl 50)}"
                }
        } catch (_: Throwable) { "System-Prozessenden derzeit nicht verfügbar." }
    }
}
