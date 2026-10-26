package at.gregor.layermaxxing

import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CoroutineStart
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** In-memory interceptors terminate every request before any DNS/socket. */
class ShakeReportApiTest {
    private val id = "12345678-1234-4123-8123-123456789abc"
    private fun exercise(role: String = "test", wrongDigest: Boolean = false, revoke: Boolean = false, oversized: Boolean = false): Pair<Throwable?, List<String>> = runBlocking {
        val directory = Files.createTempDirectory("shake-api").toFile()
        val file = java.io.File(directory, "report.png").apply { writeBytes(shakeFixturePng()) }
        val token = "SECRET_AUTH_CANARY"
        val live = AtomicBoolean(true)
        val calls = mutableListOf<String>()
        val client = OkHttpClient.Builder().dns(object : Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> = error("No DNS allowed")
        }).addInterceptor { chain ->
            val request = chain.request()
            calls += "${request.method} /api/${request.url.encodedPath.substringAfter("/api/")}"
            val json = if (request.url.encodedPath.endsWith("server-info")) {
                JSONObject().put("role", role).apply { if (oversized) put("padding", "x".repeat(5000)) }
            } else {
                assertEquals("Bearer $token", request.header("Authorization"))
                if (request.method == "POST") {
                    assertEquals("image/png", request.body!!.contentType().toString())
                    val buffer = Buffer(); request.body!!.writeTo(buffer)
                    val bytes = buffer.readByteArray()
                    assertArrayEquals(file.readBytes(), bytes)
                    assertFalse(ShakePng.extract(bytes).contains(token))
                    if (revoke) live.set(false)
                }
                JSONObject().put("id", id).put("sha256", if (wrongDigest) "wrong" else ShakePng.sha256(file)).put("bytes", file.length())
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body(json.toString().toResponseBody("application/json".toMediaType())).build()
        }.build()
        try {
            val api = ApiClient(ServerProfile.GERFRIED.baseUrl, client)
            val failure = runCatching { api.uploadShakeReport(token, ServerProfile.GERFRIED, file) { live.get() } }.exceptionOrNull()
            failure to calls
        } finally { directory.deleteRecursively(); client.dispatcher.executorService.shutdown() }
    }
    @Test fun authenticatedRawUploadRequiresMatchingOwnerReadback() {
        val (failure, calls) = exercise()
        if (failure != null) throw AssertionError("Valid owner receipt rejected after $calls", failure)
        assertEquals(listOf("GET /api/server-info", "POST /api/debug-reports", "GET /api/debug-reports/$id"), calls)
    }
    @Test fun productionRoleWrongDigestAndRevokedAccountNeverClaimSuccess() {
        assertEquals(listOf("GET /api/server-info"), exercise(role = "production").second)
        assertNotNull(exercise(role = "production").first)
        val mismatch = exercise(wrongDigest = true)
        assertNotNull(mismatch.first); assertEquals(2, mismatch.second.size)
        val cancelled = exercise(revoke = true)
        assertNotNull(cancelled.first); assertEquals(2, cancelled.second.size)
    }
    @Test fun oversizedReceiptIsRejectedBeforeAnyPngUpload() {
        val result=exercise(oversized=true)
        assertNotNull(result.first)
        assertEquals(listOf("GET /api/server-info"),result.second)
    }
    @Test fun cancellationStopsUnderlyingPendingHttpCall() = runBlocking {
        val file = Files.createTempFile("shake-cancel", ".png").toFile().apply { writeBytes(shakeFixturePng()) }
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val active = java.util.concurrent.atomic.AtomicReference<okhttp3.Call>()
        val client = OkHttpClient.Builder().dns(object : Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> = error("No DNS")
        }).addInterceptor { chain ->
            active.set(chain.call()); entered.countDown()
            check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body("{\"role\":\"test\"}".toResponseBody("application/json".toMediaType())).build()
        }.build()
        try {
            val api = ApiClient(ServerProfile.GERFRIED.baseUrl, client)
            val pending = async(start = CoroutineStart.UNDISPATCHED) {
                api.uploadShakeReport("synthetic-token", ServerProfile.GERFRIED, file) { true }
            }
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            pending.cancelAndJoin()
            assertTrue(active.get().isCanceled())
        } finally { release.countDown(); file.delete(); client.dispatcher.executorService.shutdown() }
    }

    @Test fun otherProfilesAreRejectedBeforeTransport() = runBlocking {
        val file = Files.createTempFile("shake-reject", ".png").toFile()
        try {
            file.writeText("fixture")
            val api = ApiClient(ServerProfile.GREGOR_TEST.baseUrl, OkHttpClient.Builder().addInterceptor { error("Must not send") }.build())
            assertTrue(runCatching { api.uploadShakeReport("secret", ServerProfile.GREGOR_TEST, file) { true } }.exceptionOrNull() is IllegalArgumentException)
        } finally { file.delete() }
    }
}
