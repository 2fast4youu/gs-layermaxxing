package at.gregor.layermaxxing

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 1074 end-to-end: ONE letter draft and ONE send path for every composer look. The messenger's sheet ([SendScreen]) and
 * the letter tree's paper (PostTree.kt, TreeLetterComposer) both edit a [LetterDraft] and send it with [send] – the same
 * release gates, the same minimum delay of the friendship, the same sealing (EvidenceProtocol, AES-GCM, signature) and
 * the same attachment rule (≤ 2 MB). Only the drawing differs.
 */
@Stable
internal class LetterDraft(recipient: Long?, text: String = "") {
    var id: String = java.util.UUID.randomUUID().toString()
        internal set
    internal var repository: LetterDraftRepository? = null
    internal var consumed = false
    var saveStatus by mutableStateOf<String?>(null)
    private var clearing = false
    private fun <T> field(value: T) = DraftField(value,
        changed = { repository?.edited(this, it) }, allowed = { clearing || (!busy && !consumed) })
    fun flush(): Boolean = repository?.autosave(this) ?: true
    fun discard(): Boolean = runCatching {
        repository?.delete(id); consumed = true
    }.onFailure { saveStatus = "Fehler: Entwurf konnte nicht gelöscht werden. Bitte erneut versuchen." }.isSuccess

    var text by field(text)
    var title by field("")
    var coverNote by field("")
    var selected by field(recipient?.let(::setOf) ?: emptySet())
    var groupId by field<Long?>(null)
    var mode by field(ComposeMode.DATE_TIME)
    var timeBasis by field(TimeBasis.RELATIVE)
    var windowStart by field(LocalDateTime.now().plusDays(1).withSecond(0).withNano(0))
    var windowEnd by field(LocalDateTime.now().plusDays(2).withSecond(0).withNano(0))
    var amount by field("3")
    var unit by field(DelayUnit.DAYS)
    var dateTime by field(LocalDateTime.now().plusDays(1).withSecond(0).withNano(0))
    var randomStart by field("1")
    var randomEnd by field("24")
    var randomStartUnit by field(DelayUnit.HOURS)
    var randomEndUnit by field(DelayUnit.HOURS)
    var oneTime by field(false)
    var attachment by field<Triple<String, String, ByteArray>?>(null)
    var busy by mutableStateOf(false)
    /** "✓ …" after sending, else the last error (never silently dropped). */
    var notice by mutableStateOf<String?>(null)

    companion object {
        val Saver = androidx.compose.runtime.saveable.Saver<LetterDraft, String>(
            save = { d -> org.json.JSONObject().apply {
                put("id", d.id); put("text", d.text); put("title", d.title); put("cover", d.coverNote)
                put("selected", org.json.JSONArray(d.selected.toList())); put("group", d.groupId)
                put("mode", d.mode.name); put("basis", d.timeBasis.name)
                put("amount", d.amount); put("unit", d.unit.name); put("date", d.dateTime.toString())
                put("start", d.randomStart); put("end", d.randomEnd)
                put("startUnit", d.randomStartUnit.name); put("endUnit", d.randomEndUnit.name)
                put("windowStart", d.windowStart.toString()); put("windowEnd", d.windowEnd.toString())
                put("once", d.oneTime)
            }.toString() },
            restore = { saved -> org.json.JSONObject(saved).let { j -> LetterDraft(null).apply {
                id = j.optString("id").takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString()
                text = j.getString("text"); title = j.getString("title"); coverNote = j.getString("cover")
                selected = j.getJSONArray("selected").let { a -> (0 until a.length()).map { a.getLong(it) }.toSet() }
                groupId = if (j.has("group") && !j.isNull("group")) j.getLong("group") else null
                mode = ComposeMode.valueOf(j.getString("mode")); timeBasis = TimeBasis.valueOf(j.getString("basis"))
                amount = j.getString("amount"); unit = DelayUnit.valueOf(j.getString("unit")); dateTime = LocalDateTime.parse(j.getString("date"))
                randomStart = j.getString("start"); randomEnd = j.getString("end")
                randomStartUnit = DelayUnit.valueOf(j.getString("startUnit")); randomEndUnit = DelayUnit.valueOf(j.getString("endUnit"))
                windowStart = LocalDateTime.parse(j.getString("windowStart")); windowEnd = LocalDateTime.parse(j.getString("windowEnd"))
                oneTime = j.getBoolean("once")
            } } },
        )
        val StateSaver = androidx.compose.runtime.saveable.Saver<androidx.compose.runtime.MutableState<LetterDraft?>, String>(
            save = { state -> state.value?.let { with(Saver) { save(it) } } ?: "null" },
            restore = { saved -> mutableStateOf(if (saved == "null") null else Saver.restore(saved)) },
        )
    }

    val targetValid: Boolean get() = groupId != null || selected.isNotEmpty()

    /** The longest minimum delay any chosen friendship asks for (a group has none of its own). */
    fun minimumDelay(settings: List<ApiClient.FriendshipSettings>): Long = if (groupId == null) selected.mapNotNull { id ->
        settings.firstOrNull { it.friendId == id }?.minLetterDelaySeconds
    }.maxOrNull() ?: 0L else 0L

    fun scheduleValid(now: Long = Instant.now().epochSecond): Boolean =
        runCatching { ReleaseSchedule.resolve(this, now, ZoneId.systemDefault()) }.isSuccess

    fun canSend(now: Long = Instant.now().epochSecond) = targetValid && text.isNotBlank() && scheduleValid(now) && !busy && !consumed

    fun problem(now: Long = Instant.now().epochSecond): String? = when {
        !targetValid -> "Noch kein Empfänger gewählt."
        text.isBlank() -> "Der Brief ist noch leer."
        else -> runCatching { ReleaseSchedule.resolve(this, now, ZoneId.systemDefault()) }.exceptionOrNull()?.message
    }

    fun releaseSummary(): String = when (mode) {
        ComposeMode.MANUAL -> "Manuell freigeben · bleibt versiegelt bis zu deiner Freigabe"
        ComposeMode.RANDOM -> if (timeBasis == TimeBasis.RELATIVE)
            "Zeitfenster · $randomStart ${randomStartUnit.label} bis $randomEnd ${randomEndUnit.label} nach dem Senden"
            else "Zeitfenster · $windowStart bis $windowEnd · ${ZoneId.systemDefault()}"
        else -> if (timeBasis == TimeBasis.RELATIVE || mode == ComposeMode.DURATION)
            "Zeitpunkt · $amount ${unit.label} nach dem Senden"
            else "Zeitpunkt · $dateTime · ${ZoneId.systemDefault()}"
    }

    /** Seals the draft exactly as the server expects it (release times raised to the friendship's minimum). */
    suspend fun seal(minimumDelay: Long, current: Long = Instant.now().epochSecond,
        zone: ZoneId = ZoneId.systemDefault()): ApiClient.SendRequest {
        require(targetValid && text.isNotBlank()) { problem(current) ?: "Empfänger und Brieftext fehlen." }
        val times = ReleaseSchedule.resolve(this, current, zone, minimumDelay)
        val release = times.releaseAt
        val randomFrom = times.from
        val randomTo = times.to
        val metadata = LetterMetadata(
            title.trim(), coverNote.trim(), if (groupId == null) selected.toList() else emptyList(),
            groupId, mode.api, release, randomFrom, randomTo, oneTime,
        )
        val text = text; val attachment = attachment
        val sealed = withContext(Dispatchers.Default) {
            EvidenceProtocol.seal(text, metadata, attachment?.first, attachment?.second, attachment?.third)
        }
        val encrypted = CryptoBox.Encrypted(sealed.ciphertext, sealed.nonce, sealed.releaseKey)
        val encryptedAttachment = sealed.attachment?.let { ApiClient.EncryptedAttachment(it.name, it.mime, it.ciphertext, it.nonce) }
        return ApiClient.SendRequest(
            recipientIds = metadata.recipientIds, groupId = metadata.groupId,
            encrypted = encrypted, title = metadata.title, mode = metadata.mode, releaseAt = release,
            randomFrom = randomFrom, randomTo = randomTo,
            oneTime = metadata.oneTime, attachment = encryptedAttachment, coverNote = metadata.coverNote, evidence = sealed.evidence,
        )
    }

    /** Seal and hand to [transmit] (the app: ApiClient.send with the account's token). One send at a time. */
    suspend fun send(minimumDelay: Long, transmit: suspend (ApiClient.SendRequest) -> List<Long>, onSent: (Long?, Long?) -> Unit) {
        if (busy || consumed) return
        if (!flush()) { notice = "Entwurf konnte nicht gespeichert werden. Bitte erneut versuchen."; return }
        problem()?.let { notice = it; return }
        busy = true; notice = null
        try {
            val ids = transmit(seal(minimumDelay))
            require(ids.isNotEmpty()) { "Keine Sendebestätigung erhalten. Der Entwurf bleibt erhalten." }
            // Freeze this instance before cleanup: late picker results and disposal cannot resurrect it.
            consumed = true
            val likelyFriend = if (groupId == null) selected.firstOrNull() else null
            val removed = runCatching { repository?.confirmedSent(id, ids) }.isSuccess
            if (!removed) {
                notice = "✓ Gesendet. Der lokale Entwurf konnte nicht entfernt werden; bitte in Entwürfe löschen. Nicht erneut senden."
            } else {
                clearing = true
                text = ""; title = ""; coverNote = ""; attachment = null; clearing = false; notice = "✓ Verschlüsselt gesendet"
                onSent(ids.firstOrNull(), likelyFriend)
            }
        } catch (cancelled: CancellationException) {
            notice = "Senden abgebrochen. Der Entwurf bleibt erhalten."
            throw cancelled
        } catch (error: Exception) {
            notice = error.message ?: "Senden fehlgeschlagen"
        } finally { busy = false }
    }
}

/** The system file picker for the draft's attachment (≤ 2 MB, read off the main thread); errors land in [LetterDraft.notice]. */
@Composable
internal fun rememberAttachmentPicker(draft: LetterDraft): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(draft)
    val pending = remember { arrayOfNulls<LetterDraft>(1) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val target = pending[0]
        pending[0] = null
        if (uri != null && target != null && !target.consumed) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readDraftBytes(2_000_001) }
                require(bytes.size <= 2_000_000) { "Der Anhang darf höchstens 2 MB groß sein." }
                var name = "Anhang"; context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (cursor.moveToFirst() && index >= 0) name = cursor.getString(index)
                }
                Triple(name, context.contentResolver.getType(uri) ?: "application/octet-stream", bytes)
            } }.onSuccess { target.attachment = it }.onFailure { target.notice = it.message }
        }
    }
    return { if (!current.busy && !current.consumed) { pending[0] = current; picker.launch("*/*") } }
}

/**
 * What a place needs to write a letter in its own picture (the letter tree): the people and groups one may write to,
 * the friendships' settings (minimum delay), the creative presets, and the app's real send ([transmit] = ApiClient.send
 * with the account's token). [draft] lives as long as the account – going back to the tree keeps it.
 */
@Stable
internal class LetterWriter(
    val friends: List<ApiClient.UserSummary>,
    val groups: List<ApiClient.Group>,
    val settings: List<ApiClient.FriendshipSettings>,
    val creativeActive: Boolean,
    val transmit: suspend (ApiClient.SendRequest) -> List<Long>,
    val onSent: () -> Unit,
    private val draftState: androidx.compose.runtime.MutableState<LetterDraft?>,
    val repository: LetterDraftRepository? = null,
) {
    var draft: LetterDraft?
        get() = draftState.value?.takeUnless { it.consumed }
        set(value) {
            if (draftState.value !== value && draftState.value?.flush() == false) return
            if (value != null) repository?.bind(value)
            draftState.value = value
        }
}

/** The state of the one request that opens (fetches + decrypts) a letter. Absent = no request running or failed. */
internal enum class OpenRequest { RUNNING, FAILED }

/**
 * 1074 end-to-end: the app's ONE request per letter to open it. While a letter's request runs, every further open of it
 * (a second seal tap after going back, a double tap, the inbox, the thread) is not sent again; a view that is left keeps
 * the request running (it is not taken as finished). The result lands in [opened]; a failure is kept as [OpenRequest.FAILED]
 * (and reported once) so the next tap may try again. [reset] (account switch) lets late answers of the old account fall.
 */
@Stable
internal class LetterOpener(
    private val scope: CoroutineScope,
    private val opened: MutableMap<Long, OpenedMessage>,
    private val fetch: () -> (suspend (ApiClient.Message) -> OpenedMessage),
    private val onDone: () -> (() -> Unit),
    private val onError: () -> ((String) -> Unit),
    private val timeoutMillis: Long = 45_000L,
) {
    val requests = mutableStateMapOf<Long, OpenRequest>()
    private var generation = 0
    private class Attempt { var work: Job? = null; var deadline: Job? = null }
    private val attempts = mutableMapOf<Long, Attempt>()

    /** Deadline includes transport, response body and authenticated decryption, not just socket reads. */
    fun open(message: ApiClient.Message): Boolean {
        if (requests[message.id] == OpenRequest.RUNNING) return false
        val attempt = Attempt()
        attempts[message.id] = attempt
        requests[message.id] = OpenRequest.RUNNING
        val gen = generation
        fun current() = gen == generation && attempts[message.id] === attempt
        fun fail(label: String) {
            if (!current()) return
            attempts.remove(message.id)
            attempt.deadline?.cancel()
            requests[message.id] = OpenRequest.FAILED
            runCatching { onError()(label) }
        }
        // Capture the account's transport before launching; a later recomposition may carry
        // another token. Factory failures are terminal too, rather than leaving RUNNING behind.
        val get = try { fetch() } catch (e: Exception) {
            fail(failureLabel(e)); return true
        }
        attempt.deadline = scope.launch {
            delay(timeoutMillis)
            fail("Öffnen dauert zu lange – bitte erneut versuchen")
            attempt.work?.cancel()
        }
        attempt.work = scope.launch {
            try {
                val result = get(message)
                if (!current()) return@launch
                attempts.remove(message.id)
                attempt.deadline?.cancel()
                opened[message.id] = result
                requests.remove(message.id)
                // Refresh/report callbacks cannot turn a verified success into a failed open.
                runCatching { onDone()() }
            } catch (e: Exception) {
                fail(failureLabel(e))
            }
        }
        // Also handles a scope cancelled before the worker ever enters its body.
        attempt.work?.invokeOnCompletion { cause ->
            if (cause != null) fail("Öffnen abgebrochen – bitte erneut versuchen")
        }
        return true
    }

    private fun failureLabel(error: Exception): String = when {
        error is CancellationException -> "Öffnen abgebrochen – bitte erneut versuchen"
        error is ApiException && error.code == 0 -> "Keine Verbindung zum Server – bitte erneut öffnen"
        error is ApiException -> ApiErrors.friendly(error.message)
        else -> "Der Brief konnte nicht geprüft oder geöffnet werden – bitte erneut versuchen"
    }

    fun cancel(id: Long) {
        val attempt = attempts.remove(id) ?: return
        requests[id] = OpenRequest.FAILED
        attempt.deadline?.cancel(); attempt.work?.cancel()
    }

    fun reset() {
        generation++
        val old = attempts.values.toList()
        attempts.clear(); requests.clear()
        old.forEach { it.deadline?.cancel(); it.work?.cancel() }
    }

}

@Composable
internal fun rememberLetterOpener(
    opened: MutableMap<Long, OpenedMessage>,
    fetch: suspend (ApiClient.Message) -> OpenedMessage,
    onDone: () -> Unit,
    onError: (String) -> Unit,
    timeoutMillis: Long = 45_000L,
): LetterOpener {
    val scope = rememberCoroutineScope()
    val f by rememberUpdatedState(fetch); val d by rememberUpdatedState(onDone); val e by rememberUpdatedState(onError)
    return remember(opened, timeoutMillis) { LetterOpener(scope, opened, { f }, { d }, { e }, timeoutMillis) }
}

/** The state of the one request that marks a read letter unread again. Absent = none; DONE stays until the next mark. */
internal enum class MarkRequest { RUNNING, DONE, FAILED }

/**
 * Briefbaum 22.10.: "auf ungelesen setzen" – the recipient's own marker, kept by the SERVER ([ApiClient.markUnread];
 * persistent, refused for read-once letters and outside the letter gates). One request per letter at a time. Success
 * drops the letter's fetched text here ([opened]) so it is sealed again for real: opening it once more is the normal
 * open call (gates checked again, the marker lifted by the server). Until the next refresh brings the server's
 * `marked_unread`, [effective] shows the letter as marked. Never touches the read time or the sender's receipt.
 */
@Stable
internal class UnreadMarker(
    private val scope: CoroutineScope,
    private val opened: MutableMap<Long, OpenedMessage>,
    private val transmit: () -> (suspend (ApiClient.Message) -> Unit),
    private val onDone: () -> (() -> Unit),
    private val onError: () -> ((String) -> Unit),
) {
    val requests = mutableStateMapOf<Long, MarkRequest>()
    private var generation = 0

    /** Whether [message] may be offered "unread again" at all (the server decides finally). */
    fun offered(message: ApiClient.Message, openedHere: Boolean): Boolean =
        message.incoming && message.unlocked && !message.oneTime && message.groupId == null && (openedHere || message.readAt != null)

    fun mark(message: ApiClient.Message): Boolean {
        if (requests[message.id] == MarkRequest.RUNNING) return false
        requests[message.id] = MarkRequest.RUNNING
        val gen = generation
        val send = transmit()
        scope.launch {
            val result = runCatching { send(message) }
            if (gen != generation) return@launch
            result.onSuccess { opened.remove(message.id); requests[message.id] = MarkRequest.DONE; onDone()() }
                .onFailure { requests[message.id] = MarkRequest.FAILED; onError()(it.message ?: "Markieren fehlgeschlagen") }
        }
        return true
    }

    /** The letter as the tree shows it: marked here and not opened again since (the server's list may lag behind). */
    fun effective(message: ApiClient.Message): ApiClient.Message =
        if (!message.markedUnread && requests[message.id] == MarkRequest.DONE && opened[message.id] == null) message.copy(markedUnread = true) else message

    fun reset() { generation++; requests.clear() }
}

@Composable
internal fun rememberUnreadMarker(
    opened: MutableMap<Long, OpenedMessage>,
    transmit: suspend (ApiClient.Message) -> Unit,
    onDone: () -> Unit,
    onError: (String) -> Unit,
): UnreadMarker {
    val scope = rememberCoroutineScope()
    val t by rememberUpdatedState(transmit); val d by rememberUpdatedState(onDone); val e by rememberUpdatedState(onError)
    return remember(opened) { UnreadMarker(scope, opened, { t }, { d }, { e }) }
}

/** Production content endpoint and AES-GCM authentication, shared by every open surface. */
internal suspend fun openLetterContent(api: ApiClient, token: String, message: ApiClient.Message): OpenedMessage {
    val content = api.content(token, message.id)
    return withContext(Dispatchers.Default) {
        val aad = content.canonicalMetadata?.toByteArray() ?: byteArrayOf()
        val text = CryptoBox.decryptBytes(content.ciphertext, content.nonce, content.encryptionKey, aad).toString(Charsets.UTF_8)
        val bytes = if (content.attachmentCiphertext != null && content.attachmentNonce != null)
            CryptoBox.decryptBytes(content.attachmentCiphertext, content.attachmentNonce, content.encryptionKey,
                if (content.canonicalMetadata != null && content.attachmentName != null && content.attachmentMime != null)
                    EvidenceProtocol.attachmentAad(content.canonicalMetadata, content.attachmentName, content.attachmentMime)
                else byteArrayOf()) else null
        OpenedMessage(text, bytes, content.attachmentName, content.attachmentMime)
    }
}
