package at.gregor.layermaxxing

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import androidx.compose.runtime.*
import java.io.File
import java.security.MessageDigest
import kotlin.reflect.KProperty
import org.json.JSONObject

/** Every editor field notifies its frozen repository; close/lifecycle/send synchronously flush. */
internal class DraftField<T>(value: T, private val changed: (String) -> Unit, private val allowed: () -> Boolean) {
    private var state by mutableStateOf(value)
    operator fun getValue(owner: Any?, property: KProperty<*>): T = state
    operator fun setValue(owner: Any?, property: KProperty<*>, value: T) {
        if (!allowed() || state == value) return
        state = value
        changed(property.name)
    }
}

/** App-private plaintext, never uploaded. One atomic record owns its bounded attachment; deleting
 * the record deletes the private copy too. No URI permission, token, or password is serialized. */
internal class LetterDraftRepository(private val directory: File, private val clock: () -> Long = System::currentTimeMillis) {
    data class Entry(val draft: LetterDraft, val edited: Long)
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val pending = mutableMapOf<String, Runnable>()
    private val unreadable = mutableSetOf<String>()
    private val instances = mutableMapOf<String, MutableList<java.lang.ref.WeakReference<LetterDraft>>>()
    private val canonical = mutableMapOf<String, java.lang.ref.WeakReference<LetterDraft>>()
    // A stale editor must keep its own baseline even after list() refreshes the canonical view.
    private val baseline = java.util.WeakHashMap<LetterDraft, String>()
    var revision by mutableStateOf(0)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    companion object {
        private const val MAX_RECORD = 4_000_000L
        fun scope(server: String, account: String): String = MessageDigest.getInstance("SHA-256")
            .digest("draft-v1\u0000$server\u0000$account".toByteArray()).joinToString("") { "%02x".format(it) }
        fun forAccount(context: Context, server: String, account: String, stableId: Long? = null): LetterDraftRepository {
            require(account.isNotBlank()) { "Kein angemeldetes Konto." }
            val prefs = context.getSharedPreferences("letter_draft_scopes", Context.MODE_PRIVATE)
            val key = scope(server, account)
            // Resolve once; status refresh and SessionStore.clear cannot strand earlier drafts.
            val identity = prefs.getString(key, null) ?: (stableId?.let { "id:$it" } ?: "name:$account").also {
                prefs.edit().putString(key, it).commit()
            }
            return LetterDraftRepository(File(context.filesDir, "letter-drafts/${scope(server, identity)}"))
        }
    }
    private fun file(id: String): AtomicFile {
        require(java.util.UUID.fromString(id).toString() == id) { "Ungültiger Entwurf." }
        return AtomicFile(File(directory, "$id.json"))
    }
    fun bind(draft: LetterDraft): LetterDraft {
        require(draft.repository == null || draft.repository === this) { "Entwurf gehört zu einem anderen Konto." }
        draft.repository = this
        track(draft)
        autosave(draft)
        return draft
    }
    private fun track(draft: LetterDraft) {
        val refs = instances.getOrPut(draft.id) { mutableListOf() }
        refs.removeAll { it.get() == null }
        if (refs.none { it.get() === draft }) refs += java.lang.ref.WeakReference(draft)
        if (canonical[draft.id]?.get() == null) canonical[draft.id] = java.lang.ref.WeakReference(draft)
    }
    private fun encode(draft: LetterDraft): String = with(LetterDraft.Saver) {
        val scope = object : androidx.compose.runtime.saveable.SaverScope { override fun canBeSaved(value: Any) = true }
        with(scope) { save(draft) }!!
    }
    private fun draftRecord(draft: LetterDraft): JSONObject {
        val attachment = draft.attachment
        return JSONObject().put("v", 1).put("draft", JSONObject(encode(draft))).also { record ->
            attachment?.let { record.put("attachment", JSONObject().put("name", it.first).put("mime", it.second)
                .put("bytes", Base64.encodeToString(it.third, Base64.NO_WRAP))) }
        }
    }
    private fun diskContent(id: String): String? {
        val atomic = file(id)
        if (!atomic.baseFile.exists() && !File(directory, "$id.json.bak").exists()) return null
        val bytes = atomic.openRead().use { it.readDraftBytes(MAX_RECORD.toInt() + 1) }
        require(bytes.size <= MAX_RECORD)
        val record = JSONObject(bytes.toString(Charsets.UTF_8))
        require(record.getInt("v") == 1)
        record.remove("edited")
        return record.toString()
    }
    private fun begun(draft: LetterDraft) = draft.text.isNotBlank() || draft.title.isNotBlank() ||
        draft.coverNote.isNotBlank() || draft.attachment != null || draft.targetValid

    fun edited(draft: LetterDraft, field: String) {
        if (draft.consumed) return
        if (field == "attachment") { autosave(draft); return }
        pending.remove(draft.id)?.let(handler::removeCallbacks)
        draft.saveStatus = "Wird gespeichert …"
        val task = Runnable { autosave(draft) }
        pending[draft.id] = task
        handler.postDelayed(task, 350L)
    }
    @Synchronized fun autosave(draft: LetterDraft): Boolean {
        pending.remove(draft.id)?.let(handler::removeCallbacks)
        if (draft.consumed) return true
        if (File(directory, "${draft.id}.sent").exists()) { draft.consumed = true; return true }
        return runCatching {
            require(draft.repository === this) { "Entwurf gehört zu einem anderen Konto." }
            check(draft.id !in unreadable) { "Gespeicherter Entwurf ist nicht lesbar. Bitte erneut laden." }
            require(draft.attachment == null || draft.attachment!!.third.size <= 2_000_000) { "Der Anhang darf höchstens 2 MB groß sein." }
            val record = draftRecord(draft)
            val content = record.toString()
            val previous = baseline[draft]
            // Unchanged stale views may flush, but cannot rewrite a newer disk record.
            if (content == previous) {
                draft.saveStatus = "Gespeichert"
                error = null
                return true
            }
            val disk = diskContent(draft.id)
            check(disk == previous || disk == content) { "Entwurf wurde anderweitig geändert. Lokale Änderungen bleiben erhalten." }
            if (!begun(draft)) {
                if (file(draft.id).baseFile.exists()) {
                    file(draft.id).delete()
                    check(!file(draft.id).baseFile.exists()) { "Leerer Entwurf konnte nicht entfernt werden." }
                    baseline.remove(draft); revision++
                }
                draft.saveStatus = null
                return true
            }
            // Include attachment bytes in equality and atomic transaction. No detached orphan files.
            if (disk != content) {
                record.put("edited", clock())
                val bytes = record.toString().toByteArray()
                require(bytes.size <= MAX_RECORD) { "Der Entwurf ist zu groß zum Speichern." }
                check(directory.isDirectory || directory.mkdirs()) { "Speicher nicht verfügbar." }
                val atomic = file(draft.id)
                val output = atomic.startWrite()
                try { output.write(bytes); atomic.finishWrite(output) }
                catch (failure: Exception) { atomic.failWrite(output); throw failure }
                revision++
            }
            baseline[draft] = content
            draft.saveStatus = "Gespeichert"
            error = null
        }.onFailure {
            error = "Fehler: Entwurf konnte nicht gespeichert werden. Speicher prüfen und erneut versuchen."
            draft.saveStatus = error
        }.isSuccess
    }
    @Synchronized fun list(): List<Entry> {
        if (!directory.exists()) return emptyList()
        check(directory.isDirectory) { "Entwürfe können nicht gelesen werden." }
        val failures = mutableListOf<String>()
        val files = directory.listFiles() ?: error("Entwürfe können nicht gelesen werden.")
        // AtomicFile recovers interrupted writes; include .bak even if the primary is absent.
        val ids = files.map { it.name.removeSuffix(".bak").removeSuffix(".new") }
            .filter { it.endsWith(".json") }.map { it.removeSuffix(".json") }.distinct()
        val entries = ids.filterNot { File(directory, "$it.sent").exists() }.mapNotNull { id -> runCatching {
            val bytes = file(id).openRead().use { it.readDraftBytes(MAX_RECORD.toInt() + 1) }
            require(bytes.size <= MAX_RECORD)
            val record = JSONObject(bytes.toString(Charsets.UTF_8))
            require(record.getInt("v") == 1)
            val draft = LetterDraft.Saver.restore(record.getJSONObject("draft").toString())!!
            require(draft.id == id)
            record.optJSONObject("attachment")?.let {
                val bytes = Base64.decode(it.getString("bytes"), Base64.NO_WRAP)
                require(bytes.size <= 2_000_000)
                draft.attachment = Triple(it.getString("name"), it.getString("mime"), bytes)
            }
            draft.repository = this
            unreadable.remove(id)
            val edited = record.getLong("edited")
            record.remove("edited")
            val content = record.toString()
            val live = canonical[id]?.get()?.takeUnless { it.consumed }
            // Keep pending/conflicting edits and their original baseline. Only a clean cached
            // view can be replaced; old references retain their baseline for safe later flushes.
            if (live != null && (baseline[live] == content || draftRecord(live).toString() != baseline[live])) {
                Entry(live, edited)
            } else {
                track(draft)
                canonical[id] = java.lang.ref.WeakReference(draft)
                baseline[draft] = content
                draft.saveStatus = "Gespeichert"
                Entry(draft, edited)
            }
        }.onFailure { failures += id; unreadable += id }.getOrNull() }
        error = if (failures.isEmpty()) null else "Fehler: ${failures.size} Entwurf/Entwürfe nicht lesbar. Gespeicherte Dateien wurden behalten."
        return entries.sortedWith(compareByDescending<Entry> { it.edited }.thenBy { it.draft.id })
    }
    /** Receipt marker prevents stale views from recreating a successfully sent letter. */
    @Synchronized fun confirmedSent(id: String, receipts: List<Long>) {
        require(receipts.isNotEmpty())
        file(id) // Validate UUID before constructing any marker path.
        val marker = AtomicFile(File(directory, "$id.sent"))
        val output = marker.startWrite()
        try {
            output.write(JSONObject().put("receipts", org.json.JSONArray(receipts)).toString().toByteArray())
            marker.finishWrite(output)
        } catch (failure: Exception) { marker.failWrite(output); throw failure }
        delete(id)
    }
    @Synchronized fun delete(id: String) {
        pending.remove(id)?.let(handler::removeCallbacks)
        val atomic = file(id)
        atomic.delete()
        check(!atomic.baseFile.exists() && !File(directory, "$id.json.bak").exists()) { "Entwurf konnte nicht gelöscht werden." }
        instances.remove(id)?.forEach { ref -> ref.get()?.let { it.consumed = true; baseline.remove(it) } }
        canonical.remove(id)
        unreadable.remove(id)
        revision++
    }
}

/** Debounced edits plus synchronous lifecycle/close/send flushing, including storage-error retry. */
@Composable
internal fun DraftPersistence(draft: LetterDraft) {
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(draft, lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP || event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) draft.flush()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); draft.flush() }
    }
}

/** Bounded on every Android API supported by the app; does not depend on readNBytes API level. */
internal fun java.io.InputStream.readDraftBytes(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (output.size() < limit) {
        val count = read(buffer, 0, minOf(buffer.size, limit - output.size()))
        if (count < 0) break
        if (count == 0) continue
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
