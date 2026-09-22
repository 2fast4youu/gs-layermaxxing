package at.gregor.layermaxxing

import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Single place that turns a raw HTTP response or network failure into a stable,
 * machine-readable code plus a German message — so every screen that today just
 * shows `Throwable.message` gets a usable error without duplicating this logic.
 */
object ApiErrors {
    private const val NOT_FOUND_EXPLANATION =
        "Serverfunktion nicht gefunden. App und Server passen möglicherweise nicht zusammen."

    fun fromHttpResponse(status: Int, body: String): ApiException {
        val json = runCatching { JSONObject(body) }.getOrNull()
        val detail = json?.optString("detail").orEmpty()
        val serverCode = json?.optString("error_code").orEmpty().ifBlank { null }
        val code = serverCode ?: "LM-HTTP-$status"
        val message = when {
            status == 404 && (detail.isBlank() || detail.equals("Not Found", ignoreCase = true)) -> NOT_FOUND_EXPLANATION
            detail.isNotBlank() -> detail
            else -> "Serverfehler $status"
        }
        return ApiException(status, "[$code] $message")
    }

    fun fromNetworkFailure(error: IOException): ApiException = when (error) {
        is UnknownHostException -> ApiException(0, "[LM-NET-DNS] Server nicht erreichbar (DNS-Auflösung fehlgeschlagen).")
        is SSLException -> ApiException(0, "[LM-NET-TLS] Sichere Verbindung zum Server fehlgeschlagen.")
        is SocketTimeoutException -> ApiException(0, "[LM-NET-TIMEOUT] Zeitüberschreitung bei der Verbindung zum Server.")
        else -> ApiException(0, "[LM-NET-IO] Verbindung zum Server fehlgeschlagen.")
    }
}
