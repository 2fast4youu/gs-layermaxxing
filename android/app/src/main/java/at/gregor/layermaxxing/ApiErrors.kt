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

    /**
     * What a person should read: no "[LM-HTTP-500]" codes in the UI. Network
     * failures collapse to one calm sentence; server texts stay as written.
     */
    fun friendly(raw: String?): String {
        val text = raw.orEmpty().trim()
        val code = Regex("""^\[([A-Z0-9-]+)]\s*""").find(text)?.groupValues?.get(1)
        val body = text.replace(Regex("""^\[[A-Z0-9-]+]\s*"""), "").trim()
        val generic = body.isBlank() || body.startsWith("Serverfehler") || body == NOT_FOUND_EXPLANATION
        return when {
            code != null && code.startsWith("LM-NET") -> "Keine Verbindung zum Server. Ich versuche es gleich wieder."
            !generic -> body
            code == "LM-HTTP-401" -> "Deine Anmeldung ist abgelaufen. Bitte melde dich neu an."
            code == "LM-HTTP-404" -> "Diese Funktion gibt es auf diesem Server noch nicht."
            code != null && code.startsWith("LM-HTTP-5") -> "Der Server hat gerade ein Problem. Bitte gleich nochmal versuchen."
            else -> "Das hat nicht geklappt. Bitte nochmal versuchen."
        }
    }

    /** True when the failure means "no connection", not "you did something wrong". */
    fun isOffline(raw: String?): Boolean = raw.orEmpty().trimStart().startsWith("[LM-NET")

    fun fromNetworkFailure(error: IOException): ApiException = when (error) {
        is UnknownHostException -> ApiException(0, "[LM-NET-DNS] Server nicht erreichbar (DNS-Auflösung fehlgeschlagen).")
        is SSLException -> ApiException(0, "[LM-NET-TLS] Sichere Verbindung zum Server fehlgeschlagen.")
        is SocketTimeoutException -> ApiException(0, "[LM-NET-TIMEOUT] Zeitüberschreitung bei der Verbindung zum Server.")
        else -> ApiException(0, "[LM-NET-IO] Verbindung zum Server fehlgeschlagen.")
    }
}
