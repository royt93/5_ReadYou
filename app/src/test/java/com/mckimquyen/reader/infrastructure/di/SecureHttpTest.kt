package com.mckimquyen.reader.infrastructure.di

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
import javax.net.ssl.X509TrustManager

/**
 * The shared app client accepts every certificate and host name, so the Gemini API key in `?key=` could be read by anyone on the same Wi-Fi. Calls that carry that key
 * now take the @SecureHttp client instead.
 */
class SecureHttpTest {
    private val trustAll = OkHttpClient.Builder().apply { trustAllCerts() }.build()

    @Test
    fun theSharedClientReallyTrustsEverything() {
        // Documents why the second client exists: if this ever turns false, the split is no longer needed.
        val tm = trustAll.x509TrustManager
        assertTrue(tm != null && tm.acceptedIssuers.isEmpty())
        assertTrue(trustAll.hostnameVerifier.verify("example.com", null))
    }

    @Test
    fun theSecureClientUsesTheSystemTrustStoreAndRealHostChecks() {
        val secure = secureHttpClientFrom(trustAll)
        assertNotSame(trustAll, secure)
        val tm = secure.x509TrustManager
        assertTrue("a real trust manager has issuers", tm != null && tm.acceptedIssuers.isNotEmpty())
        assertFalse("a bogus host must not verify against a null session", runCatching { secure.hostnameVerifier.verify("example.com", null) }.getOrDefault(false))
    }

    @Test
    fun theSecureClientKeepsTheTimeoutsOfItsBase() {
        val base = OkHttpClient.Builder().readTimeout(61, TimeUnit.SECONDS).connectTimeout(7, TimeUnit.SECONDS).build()
        val secure = secureHttpClientFrom(base)
        assertEquals(61_000, secure.readTimeoutMillis)
        assertEquals(7_000, secure.connectTimeoutMillis)
    }

    @Test
    fun everyCallerThatSendsTheGeminiKeyUsesTheSecureClient() {
        val root = listOf(File("src/main/java/com/mckimquyen/reader"), File("app/src/main/java/com/mckimquyen/reader")).first { it.exists() }
        val callers = root.walkTopDown().filter { it.extension == "kt" && "generativelanguage.googleapis.com" in it.readText() }.toList()
        assertTrue("expected at least the gateway and the commute script service", callers.size >= 2)
        val offenders = callers.filter { "@SecureHttp" !in it.readText() }.map { it.name }
        assertEquals("files that call Gemini without the secure client: $offenders", emptyList<String>(), offenders)
    }

    private val OkHttpClient.x509TrustManager: X509TrustManager?
        get() = runCatching {
            val f = OkHttpClient::class.java.getDeclaredField("x509TrustManager").apply { isAccessible = true }
            f.get(this) as? X509TrustManager
        }.getOrNull()
}
