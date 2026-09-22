package at.gregor.layermaxxing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/** Central formatter that turns raw HTTP responses and network failures into stable, German error text. */
class ApiErrorsTest {
    @Test
    fun bareNotFoundGetsStableCodeAndGermanExplanation() {
        val error = ApiErrors.fromHttpResponse(404, """{"detail":"Not Found"}""")
        assertEquals(404, error.code)
        assertEquals(
            "[LM-HTTP-404] Serverfunktion nicht gefunden. App und Server passen möglicherweise nicht zusammen.",
            error.message,
        )
    }

    @Test
    fun resourceNotFoundKeepsGermanDetailAlongsideCode() {
        val error = ApiErrors.fromHttpResponse(404, """{"detail":"Sitzung nicht gefunden"}""")
        assertEquals("[LM-HTTP-404] Sitzung nicht gefunden", error.message)
    }

    @Test
    fun validServerErrorCodeIsUsedInsteadOfDerivedCode() {
        val error = ApiErrors.fromHttpResponse(
            404, """{"detail":"Sitzung nicht gefunden","error_code":"LM-HTTP-404-SESSION"}""",
        )
        assertEquals("[LM-HTTP-404-SESSION] Sitzung nicht gefunden", error.message)
    }

    @Test
    fun missingServerErrorCodeFallsBackToDerivedHttpCode() {
        val error = ApiErrors.fromHttpResponse(401, """{"detail":"Token ungültig"}""")
        assertEquals(401, error.code)
        assertEquals("[LM-HTTP-401] Token ungültig", error.message)
    }

    @Test
    fun dnsFailureGetsStableLocalCodeWithoutLeakingHostname() {
        val error = ApiErrors.fromNetworkFailure(UnknownHostException("layermaxxing.derkellner.duckdns.org"))
        assertTrue(error.message!!.startsWith("[LM-NET-DNS]"))
        assertFalse(error.message!!.contains("duckdns"))
    }

    @Test
    fun timeoutFailureGetsStableLocalCode() {
        val error = ApiErrors.fromNetworkFailure(SocketTimeoutException("timeout"))
        assertTrue(error.message!!.startsWith("[LM-NET-TIMEOUT]"))
    }

    @Test
    fun tlsFailureGetsStableLocalCode() {
        val error = ApiErrors.fromNetworkFailure(SSLHandshakeException("handshake failed"))
        assertTrue(error.message!!.startsWith("[LM-NET-TLS]"))
    }

    @Test
    fun genericIoFailureGetsStableLocalCodeWithoutLeakingDetail() {
        val error = ApiErrors.fromNetworkFailure(IOException("broken pipe on 10.0.0.5"))
        assertTrue(error.message!!.startsWith("[LM-NET-IO]"))
        assertFalse(error.message!!.contains("broken pipe"))
    }
}
