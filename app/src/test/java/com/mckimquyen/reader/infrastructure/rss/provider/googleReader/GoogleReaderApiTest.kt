package com.mckimquyen.reader.infrastructure.rss.provider.googleReader

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class GoogleReaderApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: GoogleReaderApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        // Unique username per test avoids GoogleReaderApi's static instance cache returning a
        // stale client with an already-cached auth token from a previous test.
        api = GoogleReaderApi.getInstanceForTest(
            serverUrl = server.url("/").toString().trimEnd('/'),
            username = "demo_${System.nanoTime()}",
            password = "demodemo",
            httpClient = OkHttpClient(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun validCredentials_whenLoginSucceeds_returnsTrue() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("SID=xxx\nLSID=yyy\nAuth=abc123token"))

        val result = runBlocking { api.validCredentials() }

        assertTrue(result)
        val request = server.takeRequest(2, TimeUnit.SECONDS)
        assertEquals("/accounts/ClientLogin", request?.path)
        assertEquals("POST", request?.method)
    }

    @Test
    fun validCredentials_whenUnauthorized_throwsException() {
        server.enqueue(MockResponse().setResponseCode(403))

        assertThrows(Exception::class.java) {
            runBlocking { api.validCredentials() }
        }
    }

    @Test
    fun getSubscriptionList_sendsAuthorizationHeaderWithToken() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("Auth=my_token_123"))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"subscriptions":[{"id":"feed/1","title":"Tech News","url":"https://example.com/rss","categories":[{"id":"user/-/label/Tech","label":"Tech"}]}]}"""
            )
        )

        val result = runBlocking { api.getSubscriptionList() }

        assertEquals(1, result.subscriptions?.size)
        assertEquals("feed/1", result.subscriptions?.first()?.id)

        server.takeRequest(2, TimeUnit.SECONDS) // ClientLogin
        val subRequest = server.takeRequest(2, TimeUnit.SECONDS)
        assertEquals("GoogleLogin auth=my_token_123", subRequest?.getHeader("Authorization"))
        assertTrue(subRequest?.path?.startsWith("/reader/api/0/subscription/list") == true)
    }

    @Test
    fun editTag_postsCorrectFormBodyForMarkRead() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("Auth=tok"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("post_token_value"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        runBlocking { api.editTag(itemId = "item123", tag = GoogleReaderApi.TAG_READ, add = true) }

        server.takeRequest(2, TimeUnit.SECONDS) // ClientLogin
        server.takeRequest(2, TimeUnit.SECONDS) // token
        val editRequest = server.takeRequest(2, TimeUnit.SECONDS)
        assertEquals("/reader/api/0/edit-tag", editRequest?.path)
        val body = editRequest?.body?.readUtf8()
        assertTrue(body?.contains("i=item123") == true)
        assertTrue(body?.contains("a=user%2F-%2Fstate%2Fcom.google%2Fread") == true)
    }
}
