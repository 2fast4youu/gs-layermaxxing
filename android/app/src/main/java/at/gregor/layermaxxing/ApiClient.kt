package at.gregor.layermaxxing

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.dnsoverhttps.DnsOverHttps
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.net.URLEncoder
import java.net.UnknownHostException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

private val proofTimestampFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm").withZone(ZoneId.systemDefault())
private fun formatEpoch(epoch: Long): String = proofTimestampFormat.format(Instant.ofEpochSecond(epoch))

private object ResilientDns : Dns {
    private val cloudflare = listOf(
        InetAddress.getByAddress("cloudflare-dns.com", byteArrayOf(1, 1, 1, 1)),
        InetAddress.getByAddress("cloudflare-dns.com", byteArrayOf(1, 0, 0, 1)),
    )
    private val doh by lazy {
        val bootstrapClient = OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()
        DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            .bootstrapDnsHosts(*cloudflare.toTypedArray())
            .build()
    }

    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            Dns.SYSTEM.lookup(hostname).ifEmpty { throw UnknownHostException(hostname) }
        } catch (systemFailure: UnknownHostException) {
            try {
                doh.lookup(hostname).ifEmpty { throw systemFailure }
            } catch (fallbackFailure: Exception) {
                systemFailure.addSuppressed(fallbackFailure)
                throw systemFailure
            }
        }
    }
}

class ApiClient(
    private val baseUrl: String = ServerProfile.GERFRIED.baseUrl,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .dns(ResilientDns)
        .protocols(listOf(Protocol.HTTP_1_1))
        .retryOnConnectionFailure(true)
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS).build(),
) {
    data class ServerInfo(
        val name: String, val role: String, val version: String, val warning: String,
        val features: List<String>,
    )
    data class Auth(val token: String, val userId: Long, val name: String, val recoveryCode: String?)
    data class Status(
        val userId: Long, val name: String, val needsPassword: Boolean, val avatarEmoji: String,
        val displayColor: String, val discoverable: Boolean,
    )
    data class UserSummary(
        val id: Long, val name: String, val relationship: String = "none",
        val avatarEmoji: String = "🔐", val displayColor: String = "#6750A4",
    )
    data class IncomingRequest(val id: Long, val senderId: Long, val senderName: String, val createdAt: Long)
    data class OutgoingRequest(val id: Long, val recipientId: Long, val recipientName: String, val createdAt: Long)
    data class Reaction(val userId: Long, val name: String, val emoji: String)
    data class Message(
        val id: Long, val peerId: Long, val peerName: String, val incoming: Boolean, val title: String,
        val coverNote: String, val proofStatus: String, val createdAt: Long,
        val mode: String, val releaseAt: Long?, val randomFrom: Long?, val randomTo: Long?, val unlocked: Boolean,
        val oneTime: Boolean, val readAt: Long?, val senderApproved: Boolean, val recipientApproved: Boolean,
        val attachmentName: String?, val attachmentMime: String?, val groupId: Long?, val reactions: List<Reaction>,
        val ciphertext: String?, val nonce: String?, val encryptionKey: String?,
    )
    data class MessageContent(
        val ciphertext: String, val nonce: String, val encryptionKey: String,
        val attachmentName: String?, val attachmentMime: String?,
        val attachmentCiphertext: String?, val attachmentNonce: String?, val canonicalMetadata: String?,
    )
    data class Group(val id: Long, val name: String, val ownerId: Long, val members: List<UserSummary>)
    data class Topic(
        val id: Long, val title: String, val details: String, val creatorName: String,
        val targetType: String, val targetName: String, val createdAt: Long, val completedAt: Long?,
        val completedByName: String?, val canDelete: Boolean,
    )
    data class Session(val id: Long, val deviceName: String, val createdAt: Long, val lastSeenAt: Long, val current: Boolean)
    data class EncryptedAttachment(val name: String, val mime: String, val ciphertext: String, val nonce: String)
    data class SendRequest(
        val recipientIds: List<Long> = emptyList(), val groupId: Long? = null, val encrypted: CryptoBox.Encrypted,
        val title: String, val mode: String, val releaseAt: Long? = null, val randomFrom: Long? = null,
        val randomTo: Long? = null, val oneTime: Boolean = false, val attachment: EncryptedAttachment? = null,
        val coverNote: String = "", val evidence: LetterEvidence? = null,
    )
    data class SettingsProposal(
        val id: Long, val friendId: Long, val friendName: String, val proposerId: Long,
        val lettersEnabled: Boolean, val chatsEnabled: Boolean, val epEnabled: Boolean,
        val minLetterDelaySeconds: Long,
    )
    data class FriendshipSettings(
        val friendId: Long, val friendName: String, val lettersEnabled: Boolean,
        val chatsEnabled: Boolean, val epEnabled: Boolean, val minLetterDelaySeconds: Long,
        val incomingProposal: SettingsProposal?, val outgoingProposal: SettingsProposal?,
    )
    data class EpProposal(
        val id: Long, val proposerId: Long, val proposerName: String, val beneficiaryId: Long,
        val beneficiaryName: String, val points: Int, val title: String, val description: String?,
        val letterId: Long?, val status: String, val createdAt: Long,
    )
    data class EpOverview(
        val incoming: List<EpProposal>, val outgoing: List<EpProposal>, val history: List<EpProposal>,
        val given: Int, val received: Int, val levelName: String,
    )
    data class ChatThread(
        val friendId: Long, val friendName: String, val avatarEmoji: String,
        val displayColor: String, val lastMessageAt: Long, val unreadCount: Int,
    )
    data class ChatMessage(
        val id: Long, val senderId: Long, val recipientId: Long, val text: String,
        val createdAt: Long, val readAt: Long?,
    )
    data class ProofDetails(
        val messageId: Long, val title: String, val legacy: Boolean, val releaseRule: String,
        val evidence: VerificationEvidence?, val rawExport: String,
    )

    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val deviceName get() = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    suspend fun serverInfo(): ServerInfo = io {
        val json = execute(request("api/server-info").get().build())
        ServerInfo(
            json.getString("name"), json.getString("role"), json.getString("version"),
            json.optString("warning"), json.getJSONArray("features").strings(),
        )
    }

    suspend fun register(name: String, password: String): Auth = authCall("api/register", name, password)
    suspend fun login(name: String, password: String): Auth = authCall("api/login", name, password)

    private suspend fun authCall(path: String, name: String, password: String): Auth = io {
        val body = JSONObject().put("name", name).put("password", password).put("device_name", deviceName)
        parseAuth(execute(request(path).post(body.body()).build()))
    }

    suspend fun recover(name: String, code: String, password: String): Auth = io {
        val body = JSONObject().put("name", name).put("recovery_code", code)
            .put("new_password", password).put("device_name", deviceName)
        parseAuth(execute(request("api/recovery/reset").post(body.body()).build()))
    }

    suspend fun status(token: String): Status = io {
        val json = execute(authorized(token, "api/status").get().build())
        Status(json.getLong("user_id"), json.getString("name"), json.getBoolean("needs_password"),
            json.optString("avatar_emoji", "🔐"), json.optString("display_color", "#6750A4"), json.optBoolean("discoverable", true))
    }

    suspend fun setPassword(token: String, password: String): String = io {
        execute(authorized(token, "api/set-password").post(JSONObject().put("password", password).body()).build())
            .getString("recovery_code")
    }

    suspend fun changePassword(token: String, old: String, new: String) = unitCall(
        authorized(token, "api/password/change").post(JSONObject().put("old_password", old).put("new_password", new).body()).build()
    )

    suspend fun newRecoveryCode(token: String): String = io {
        execute(authorized(token, "api/recovery/new").post(emptyBody()).build()).getString("recovery_code")
    }

    suspend fun updateProfile(token: String, name: String, emoji: String, color: String, discoverable: Boolean): Status = io {
        val body = JSONObject().put("name", name).put("avatar_emoji", emoji).put("display_color", color).put("discoverable", discoverable)
        val json = execute(authorized(token, "api/profile").patch(body.body()).build())
        Status(json.getLong("user_id"), json.getString("name"), json.getBoolean("needs_password"),
            json.getString("avatar_emoji"), json.getString("display_color"), json.getBoolean("discoverable"))
    }

    suspend fun sessions(token: String): List<Session> = array(token, "api/sessions") {
        Session(it.getLong("id"), it.getString("device_name"), it.getLong("created_at"), it.getLong("last_seen_at"), it.getBoolean("current"))
    }
    suspend fun revokeSession(token: String, id: Long) = unitCall(authorized(token, "api/sessions/$id").delete().build())
    suspend fun logout(token: String) = unitCall(authorized(token, "api/logout").post(emptyBody()).build())

    suspend fun users(token: String): List<UserSummary> = array(token, "api/users", ::parseUser)
    suspend fun searchUser(token: String, exactName: String): List<UserSummary> = array(
        token, "api/users/search?q=${URLEncoder.encode(exactName, Charsets.UTF_8.name())}", ::parseUser
    )
    suspend fun friends(token: String): List<UserSummary> = array(token, "api/friends", ::parseUser)
    suspend fun blocked(token: String): List<UserSummary> = array(token, "api/blocks", ::parseUser)

    suspend fun incomingRequests(token: String): List<IncomingRequest> = array(token, "api/friend-requests/incoming") {
        IncomingRequest(it.getLong("id"), it.getLong("sender_id"), it.getString("sender_name"), it.getLong("created_at"))
    }
    suspend fun outgoingRequests(token: String): List<OutgoingRequest> = array(token, "api/friend-requests/outgoing") {
        OutgoingRequest(it.getLong("id"), it.getLong("recipient_id"), it.getString("recipient_name"), it.getLong("created_at"))
    }
    suspend fun requestFriend(token: String, id: Long) = unitCall(
        authorized(token, "api/friend-requests").post(JSONObject().put("recipient_id", id).body()).build()
    )
    suspend fun respondFriend(token: String, id: Long, accept: Boolean) = unitCall(
        authorized(token, "api/friend-requests/$id/respond").post(JSONObject().put("accept", accept).body()).build()
    )
    suspend fun withdrawFriendRequest(token: String, id: Long) = unitCall(authorized(token, "api/friend-requests/$id").delete().build())
    suspend fun removeFriend(token: String, id: Long) = unitCall(authorized(token, "api/friends/$id").delete().build())
    suspend fun block(token: String, id: Long) = unitCall(authorized(token, "api/blocks/$id").post(emptyBody()).build())
    suspend fun unblock(token: String, id: Long) = unitCall(authorized(token, "api/blocks/$id").delete().build())

    suspend fun friendshipSettings(token: String): List<FriendshipSettings> = array(token, "api/friendship-settings") {
        FriendshipSettings(
            friendId = it.getLong("friend_id"), friendName = it.getString("friend_name"),
            lettersEnabled = it.getBoolean("letters_enabled"), chatsEnabled = it.getBoolean("chats_enabled"),
            epEnabled = it.getBoolean("ep_enabled"), minLetterDelaySeconds = it.getLong("min_letter_delay_seconds"),
            incomingProposal = it.optJSONObject("incoming_proposal")?.let(::parseSettingsProposal),
            outgoingProposal = it.optJSONObject("outgoing_proposal")?.let(::parseSettingsProposal),
        )
    }
    suspend fun proposeFriendshipSettings(token: String, settings: FriendshipSettings) = unitCall(
        authorized(token, "api/friendship-settings/proposals").post(JSONObject()
            .put("friend_id", settings.friendId).put("letters_enabled", settings.lettersEnabled)
            .put("chats_enabled", settings.chatsEnabled).put("ep_enabled", settings.epEnabled)
            .put("min_letter_delay_seconds", settings.minLetterDelaySeconds).body()).build()
    )
    suspend fun respondFriendshipSettings(token: String, id: Long, accept: Boolean) = unitCall(
        authorized(token, "api/friendship-settings/proposals/$id/respond")
            .post(JSONObject().put("accept", accept).body()).build()
    )
    suspend fun withdrawFriendshipSettings(token: String, id: Long) = unitCall(
        authorized(token, "api/friendship-settings/proposals/$id").delete().build()
    )

    suspend fun ep(token: String): EpOverview = io {
        val json = execute(authorized(token, "api/ep").get().build())
        val totals = json.getJSONObject("totals")
        EpOverview(
            json.getJSONArray("incoming_pending").objects().map(::parseEp),
            json.getJSONArray("outgoing_pending").objects().map(::parseEp),
            json.getJSONArray("history").objects().map(::parseEp),
            totals.getInt("given"), totals.getInt("received"), json.getJSONObject("level").getString("name"),
        )
    }
    suspend fun proposeEp(
        token: String, beneficiaryId: Long, points: Int, title: String, description: String?, letterId: Long?,
    ) = unitCall(authorized(token, "api/ep/proposals").post(JSONObject()
        .put("beneficiary_id", beneficiaryId).put("points", points).put("title", title)
        .put("description", description).put("letter_id", letterId).body()).build())
    suspend fun respondEp(token: String, id: Long, accept: Boolean) = unitCall(
        authorized(token, "api/ep/proposals/$id/respond").post(JSONObject().put("accept", accept).body()).build()
    )

    suspend fun groups(token: String): List<Group> = array(token, "api/groups") { json ->
        Group(json.getLong("id"), json.getString("name"), json.getLong("owner_id"),
            json.getJSONArray("members").objects().map(::parseUser))
    }
    suspend fun createGroup(token: String, name: String, ids: List<Long>) = unitCall(
        authorized(token, "api/groups").post(JSONObject().put("name", name).put("member_ids", JSONArray(ids)).body()).build()
    )

    suspend fun chatThreads(token: String): List<ChatThread> = array(token, "api/chats") {
        ChatThread(
            it.getLong("friend_id"), it.getString("friend_name"), it.optString("avatar_emoji", "🔐"),
            it.optString("display_color", "#6750A4"), it.getLong("last_message_at"), it.getInt("unread_count"),
        )
    }
    suspend fun chatMessages(token: String, friendId: Long): List<ChatMessage> = array(
        token, "api/chats/$friendId/messages",
    ) {
        ChatMessage(
            it.getLong("id"), it.getLong("sender_id"), it.getLong("recipient_id"),
            CryptoBox.decrypt(it.getString("ciphertext"), it.getString("nonce"), it.getString("encryption_key")),
            it.getLong("created_at"), it.nullableLong("read_at"),
        )
    }
    suspend fun sendChat(token: String, friendId: Long, text: String) = io {
        val encrypted = CryptoBox.encrypt(text)
        execute(authorized(token, "api/chats/$friendId/messages").post(JSONObject()
            .put("ciphertext", encrypted.ciphertext).put("nonce", encrypted.nonce)
            .put("encryption_key", encrypted.key).body()).build()).getLong("id")
    }

    suspend fun topics(token: String): List<Topic> = array(token, "api/topics", ::parseTopic)
    suspend fun createTopic(token: String, title: String, details: String, peerId: Long?, groupId: Long?) = io {
        val encrypted = CryptoBox.encrypt(JSONObject().put("title", title.trim()).put("details", details.trim()).toString())
        val body = JSONObject().put("ciphertext", encrypted.ciphertext).put("nonce", encrypted.nonce)
            .put("encryption_key", encrypted.key)
        peerId?.let { body.put("peer_user_id", it) }
        groupId?.let { body.put("group_id", it) }
        execute(authorized(token, "api/topics").post(body.body()).build()).getLong("id")
    }
    suspend fun setTopicCompleted(token: String, id: Long, completed: Boolean) = unitCall(
        authorized(token, "api/topics/$id").patch(JSONObject().put("completed", completed).body()).build()
    )
    suspend fun deleteTopic(token: String, id: Long) = unitCall(authorized(token, "api/topics/$id").delete().build())

    suspend fun send(token: String, payload: SendRequest): List<Long> = io {
        val body = JSONObject().put("recipient_ids", JSONArray(payload.recipientIds))
            .put("ciphertext", payload.encrypted.ciphertext).put("nonce", payload.encrypted.nonce)
            .put("encryption_key", payload.encrypted.key).put("title", payload.title)
            .put("cover_note", payload.coverNote).put("mode", payload.mode).put("one_time", payload.oneTime)
        payload.groupId?.let { body.put("group_id", it) }
        payload.releaseAt?.let { body.put("release_at", it) }
        payload.randomFrom?.let { body.put("random_from", it) }
        payload.randomTo?.let { body.put("random_to", it) }
        payload.attachment?.let {
            body.put("attachment_name", it.name).put("attachment_mime", it.mime)
                .put("attachment_ciphertext", it.ciphertext).put("attachment_nonce", it.nonce)
        }
        payload.evidence?.let {
            body.put("commitment_salt", it.commitmentSalt).put("plaintext_sha256", it.plaintextSha256)
                .put("commitment", it.commitment).put("public_signing_key", it.publicSigningKey)
                .put("signature", it.signature).put("protocol_version", it.protocolVersion)
                .put("canonical_metadata", it.canonicalMetadata)
        }
        execute(authorized(token, "api/messages").post(body.body()).build()).getJSONArray("ids").longs()
    }

    suspend fun messages(token: String): List<Message> = array(token, "api/messages") { parseMessage(it, true) }
    suspend fun outbox(token: String): List<Message> = array(token, "api/outbox") { parseMessage(it, false) }
    suspend fun content(token: String, id: Long): MessageContent = io {
        val json = execute(authorized(token, "api/messages/$id/content").get().build())
        MessageContent(json.getString("ciphertext"), json.getString("nonce"), json.getString("encryption_key"),
            json.nullableString("attachment_name"), json.nullableString("attachment_mime"),
            json.nullableString("attachment_ciphertext"), json.nullableString("attachment_nonce"),
            json.nullableString("canonical_metadata"))
    }
    suspend fun proof(token: String, id: Long): ProofDetails = io {
        val raw = executeRaw(authorized(token, "api/messages/$id/proof").get().build())
        val message = JSONObject(raw).getJSONArray("messages").getJSONObject(0)
        val rule = message.getJSONObject("release_rule")
        ProofDetails(
            messageId = message.getLong("id"), title = message.optString("title"),
            legacy = message.getBoolean("legacy"),
            releaseRule = buildString {
                append(rule.getString("mode"))
                rule.nullableLong("release_at")?.let { append(" · ").append(formatEpoch(it)) }
                rule.nullableLong("random_from")?.let { append(" · ").append(formatEpoch(it)) }
                rule.nullableLong("random_to")?.let { append("–").append(formatEpoch(it)) }
            },
            evidence = message.optJSONObject("evidence")?.let(::parseVerificationEvidence), rawExport = raw,
        )
    }
    suspend fun pendingProofExport(token: String): String = io {
        executeRaw(authorized(token, "api/proofs/pending").get().build())
    }
    suspend fun release(token: String, id: Long) = unitCall(authorized(token, "api/messages/$id/release").post(emptyBody()).build())
    suspend fun approve(token: String, id: Long) = unitCall(authorized(token, "api/messages/$id/approve").post(emptyBody()).build())
    suspend fun retract(token: String, id: Long) = unitCall(authorized(token, "api/messages/$id").delete().build())
    suspend fun react(token: String, id: Long, emoji: String) = unitCall(
        authorized(token, "api/messages/$id/reaction").put(JSONObject().put("emoji", emoji).body()).build()
    )

    suspend fun registerPushToken(token: String, pushToken: String) = unitCall(
        authorized(token, "api/push-token").post(JSONObject().put("token", pushToken).body()).build()
    )

    private fun parseAuth(json: JSONObject) = Auth(json.getString("token"), json.getLong("user_id"), json.getString("name"), json.nullableString("recovery_code"))
    private fun parseUser(json: JSONObject) = UserSummary(json.getLong("id"), json.getString("name"), json.optString("relationship", "none"),
        json.optString("avatar_emoji", "🔐"), json.optString("display_color", "#6750A4"))
    private fun parseTopic(json: JSONObject): Topic {
        val clear = JSONObject(CryptoBox.decrypt(json.getString("ciphertext"), json.getString("nonce"), json.getString("encryption_key")))
        return Topic(
            id = json.getLong("id"), title = clear.optString("title"), details = clear.optString("details"),
            creatorName = json.getString("creator_name"), targetType = json.getString("target_type"),
            targetName = json.getString("target_name"), createdAt = json.getLong("created_at"),
            completedAt = json.nullableLong("completed_at"), completedByName = json.nullableString("completed_by_name"),
            canDelete = json.getBoolean("can_delete"),
        )
    }
    private fun parseSettingsProposal(json: JSONObject) = SettingsProposal(
        json.getLong("id"), json.getLong("friend_id"), json.getString("friend_name"),
        json.getLong("proposer_id"), json.getBoolean("letters_enabled"), json.getBoolean("chats_enabled"),
        json.getBoolean("ep_enabled"), json.getLong("min_letter_delay_seconds"),
    )
    private fun parseEp(json: JSONObject) = EpProposal(
        json.getLong("id"), json.getLong("proposer_id"), json.getString("proposer_name"),
        json.getLong("beneficiary_id"), json.getString("beneficiary_name"), json.getInt("points"),
        json.getString("title"), json.nullableString("description"), json.nullableLong("letter_id"),
        json.getString("status"), json.getLong("created_at"),
    )
    private fun parseVerificationEvidence(json: JSONObject): VerificationEvidence {
        val attachment = json.optJSONObject("attachment")?.let {
            SealedAttachment(it.getString("name"), it.getString("mime"), it.getString("ciphertext"), it.getString("nonce"))
        }
        return VerificationEvidence(
            json.getInt("protocol_version"), json.getString("canonical_metadata"),
            json.getString("commitment_salt"), json.getString("plaintext_sha256"),
            json.getString("commitment"), json.getString("public_signing_key"),
            json.getString("public_key_fingerprint"), json.getString("signature"),
            json.getString("ciphertext"), json.getString("nonce"), json.nullableString("release_key"),
            json.nullableString("release_key_sha256"), attachment,
        )
    }
    private fun parseMessage(json: JSONObject, incoming: Boolean): Message = Message(
        id = json.getLong("id"), peerId = json.getLong(if (incoming) "sender_id" else "recipient_id"),
        peerName = json.getString(if (incoming) "sender_name" else "recipient_name"), incoming = incoming,
        title = json.optString("title"), coverNote = json.optString("cover_note"),
        proofStatus = json.optString("proof_status", "legacy"), createdAt = json.getLong("created_at"),
        mode = json.optString("mode", if (json.optBoolean("manual_release")) "manual" else "timed"),
        releaseAt = json.nullableLong("release_at"), randomFrom = json.nullableLong("random_from"), randomTo = json.nullableLong("random_to"),
        unlocked = json.getBoolean("unlocked"), oneTime = json.optBoolean("one_time"), readAt = json.nullableLong("read_at"),
        senderApproved = json.optBoolean("sender_approved"), recipientApproved = json.optBoolean("recipient_approved"),
        attachmentName = json.nullableString("attachment_name"), attachmentMime = json.nullableString("attachment_mime"),
        groupId = json.nullableLong("group_id"), reactions = json.optJSONArray("reactions")?.objects()?.map {
            Reaction(it.getLong("user_id"), it.getString("name"), it.getString("emoji"))
        }.orEmpty(), ciphertext = json.nullableString("ciphertext"), nonce = json.nullableString("nonce"), encryptionKey = json.nullableString("encryption_key"),
    )

    private suspend fun <T> array(token: String, path: String, mapper: (JSONObject) -> T): List<T> = io {
        readArray(authorized(token, path).get().build(), mapper)
    }
    private suspend fun unitCall(request: Request) = io { execute(request); Unit }
    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
    private fun authorized(token: String, path: String) = request(path).header("Authorization", "Bearer $token")
    private fun request(path: String) = Request.Builder().url(baseUrl.trimEnd('/') + "/" + path.trimStart('/'))
    private fun JSONObject.body() = toString().toRequestBody(jsonType)
    private fun emptyBody() = ByteArray(0).toRequestBody(null)

    private fun executeRaw(request: Request): String {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw apiError(response.code, text)
            return text
        }
    }
    private fun execute(request: Request): JSONObject = executeRaw(request).let { if (it.isBlank()) JSONObject() else JSONObject(it) }
    private fun <T> readArray(request: Request, mapper: (JSONObject) -> T): List<T> {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw apiError(response.code, text)
            if (text.isBlank()) return emptyList()
            return JSONArray(text).objects().map(mapper)
        }
    }
    private fun apiError(code: Int, body: String): IOException {
        val detail = runCatching { JSONObject(body).optString("detail") }.getOrNull().orEmpty()
        return IOException(if (detail.isBlank()) "Serverfehler $code" else detail)
    }
    private fun JSONObject.nullableString(key: String): String? = if (!has(key) || isNull(key)) null else optString(key).ifBlank { null }
    private fun JSONObject.nullableLong(key: String): Long? = if (!has(key) || isNull(key)) null else getLong(key)
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.longs() = (0 until length()).map { getLong(it) }
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
}
