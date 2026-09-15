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
import java.util.concurrent.TimeUnit

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
    private val baseUrl: String = BuildConfig.API_BASE_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .dns(ResilientDns)
        .protocols(listOf(Protocol.HTTP_1_1))
        .retryOnConnectionFailure(true)
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build(),
) {
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
        val id: Long, val peerName: String, val incoming: Boolean, val title: String, val createdAt: Long,
        val mode: String, val releaseAt: Long?, val randomFrom: Long?, val randomTo: Long?, val unlocked: Boolean,
        val oneTime: Boolean, val readAt: Long?, val senderApproved: Boolean, val recipientApproved: Boolean,
        val attachmentName: String?, val attachmentMime: String?, val groupId: Long?, val reactions: List<Reaction>,
        val ciphertext: String?, val nonce: String?, val encryptionKey: String?,
    )
    data class MessageContent(
        val ciphertext: String, val nonce: String, val encryptionKey: String,
        val attachmentName: String?, val attachmentMime: String?,
        val attachmentCiphertext: String?, val attachmentNonce: String?,
    )
    data class Group(val id: Long, val name: String, val ownerId: Long, val members: List<UserSummary>)
    data class Session(val id: Long, val deviceName: String, val createdAt: Long, val lastSeenAt: Long, val current: Boolean)
    data class EncryptedAttachment(val name: String, val mime: String, val ciphertext: String, val nonce: String)
    data class SendRequest(
        val recipientIds: List<Long> = emptyList(), val groupId: Long? = null, val encrypted: CryptoBox.Encrypted,
        val title: String, val mode: String, val releaseAt: Long? = null, val randomFrom: Long? = null,
        val randomTo: Long? = null, val oneTime: Boolean = false, val attachment: EncryptedAttachment? = null,
    )

    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val deviceName get() = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

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

    suspend fun groups(token: String): List<Group> = array(token, "api/groups") { json ->
        Group(json.getLong("id"), json.getString("name"), json.getLong("owner_id"),
            json.getJSONArray("members").objects().map(::parseUser))
    }
    suspend fun createGroup(token: String, name: String, ids: List<Long>) = unitCall(
        authorized(token, "api/groups").post(JSONObject().put("name", name).put("member_ids", JSONArray(ids)).body()).build()
    )

    suspend fun send(token: String, payload: SendRequest): List<Long> = io {
        val body = JSONObject().put("recipient_ids", JSONArray(payload.recipientIds))
            .put("ciphertext", payload.encrypted.ciphertext).put("nonce", payload.encrypted.nonce)
            .put("encryption_key", payload.encrypted.key).put("title", payload.title)
            .put("mode", payload.mode).put("one_time", payload.oneTime)
        payload.groupId?.let { body.put("group_id", it) }
        payload.releaseAt?.let { body.put("release_at", it) }
        payload.randomFrom?.let { body.put("random_from", it) }
        payload.randomTo?.let { body.put("random_to", it) }
        payload.attachment?.let {
            body.put("attachment_name", it.name).put("attachment_mime", it.mime)
                .put("attachment_ciphertext", it.ciphertext).put("attachment_nonce", it.nonce)
        }
        execute(authorized(token, "api/messages").post(body.body()).build()).getJSONArray("ids").longs()
    }

    suspend fun messages(token: String): List<Message> = array(token, "api/messages") { parseMessage(it, true) }
    suspend fun outbox(token: String): List<Message> = array(token, "api/outbox") { parseMessage(it, false) }
    suspend fun content(token: String, id: Long): MessageContent = io {
        val json = execute(authorized(token, "api/messages/$id/content").get().build())
        MessageContent(json.getString("ciphertext"), json.getString("nonce"), json.getString("encryption_key"),
            json.nullableString("attachment_name"), json.nullableString("attachment_mime"),
            json.nullableString("attachment_ciphertext"), json.nullableString("attachment_nonce"))
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
    private fun parseMessage(json: JSONObject, incoming: Boolean): Message = Message(
        id = json.getLong("id"), peerName = json.getString(if (incoming) "sender_name" else "recipient_name"), incoming = incoming,
        title = json.optString("title"), createdAt = json.getLong("created_at"), mode = json.optString("mode", if (json.optBoolean("manual_release")) "manual" else "timed"),
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

    private fun execute(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw apiError(response.code, text)
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }
    private fun <T> readArray(request: Request, mapper: (JSONObject) -> T): List<T> {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw apiError(response.code, text)
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
}
