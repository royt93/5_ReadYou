package com.mckimquyen.reader.infrastructure.di

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit coverage for ENH-06 conditional HTTP cache validation. */
class ConditionalCacheInterceptorTest {

    @Test
    fun getRequest_forcesRevalidationSoOkHttpCanSendCachedValidators() {
        val request = Request.Builder().url("https://example.com/feed.xml").get().build()
        val captured = slot<Request>()
        val chain = mockChain(request, captured)

        ConditionalCacheInterceptor.intercept(chain)

        assertEquals("max-age=0", captured.captured.header("Cache-Control"))
    }

    @Test
    fun nonGetRequest_isNotModified() {
        val request = Request.Builder()
            .url("https://example.com/api")
            .post(okhttp3.FormBody.Builder().add("key", "value").build())
            .build()
        val captured = slot<Request>()
        val chain = mockChain(request, captured)

        ConditionalCacheInterceptor.intercept(chain)

        assertNull(captured.captured.header("Cache-Control"))
        assertEquals("POST", captured.captured.method)
    }

    @Test
    fun existingConditionalHeaders_arePreserved() {
        val request = Request.Builder()
            .url("https://example.com/feed.xml")
            .header("If-None-Match", "\"feed-v2\"")
            .header("If-Modified-Since", "Fri, 26 Sep 2026 00:00:00 GMT")
            .build()
        val captured = slot<Request>()
        val chain = mockChain(request, captured)

        ConditionalCacheInterceptor.intercept(chain)

        assertEquals("\"feed-v2\"", captured.captured.header("If-None-Match"))
        assertEquals("Fri, 26 Sep 2026 00:00:00 GMT", captured.captured.header("If-Modified-Since"))
        assertEquals("max-age=0", captured.captured.header("Cache-Control"))
    }

    private fun mockChain(request: Request, captured: io.mockk.CapturingSlot<Request>): Interceptor.Chain {
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(capture(captured)) } answers {
            Response.Builder()
                .request(captured.captured)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
        }
        return chain
    }
}
