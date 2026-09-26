package com.mckimquyen.reader.infrastructure.rss.provider.googleReader

import com.mckimquyen.reader.infrastructure.di.UserAgentInterceptor
import com.mckimquyen.reader.infrastructure.di.cachingHttpClient
import com.mckimquyen.reader.infrastructure.rss.provider.ProviderAPI
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.executeAsync
import java.util.concurrent.ConcurrentHashMap

/**
 * Client for the "Google Reader API" v1 protocol implemented by FreshRSS, Miniflux, Nextcloud News,
 * BazQux and similar self-hosted RSS servers.
 *
 * Auth flow (ClientLogin, RFC-like, unrelated to OAuth):
 * 1. POST /accounts/ClientLogin -> "Auth=xxxx" token, cached for subsequent calls.
 * 2. Reader API calls send header `Authorization: GoogleLogin auth=<token>`.
 * 3. State-changing calls (edit-tag) additionally require a short-lived POST token from
 *    GET /reader/api/0/token, sent as form field `T`.
 */
class GoogleReaderApi private constructor(
    private val serverUrl: String,
    private val username: String,
    private val password: String,
    // ponytail: default is production's shared caching client; tests inject one pointed at a
    // local stub server instead of adding a MockWebServer-style dependency.
    httpClient: OkHttpClient = cachingHttpClient().newBuilder().addNetworkInterceptor(UserAgentInterceptor).build(),
) : ProviderAPI(httpClient) {

    @Volatile
    private var authToken: String? = null

    private fun apiUrl(path: String) = "${serverUrl.trimEnd('/')}$path"

    private suspend fun ensureAuthToken(): String {
        authToken?.let { return it }
        return login()
    }

    private suspend fun login(): String {
        val body = FormBody.Builder()
            .add("Email", username)
            .add("Passwd", password)
            .build()
        val response = client.newCall(
            Request.Builder()
                .url(apiUrl("/accounts/ClientLogin"))
                .post(body)
                .build()
        ).executeAsync()

        if (response.code == 401 || response.code == 403) {
            throw Exception("Unauthorized")
        }
        if (!response.isSuccessful) {
            throw Exception("ClientLogin failed with HTTP ${response.code}")
        }

        val text = response.body.string()
        val token = text.lineSequence()
            .firstOrNull { it.startsWith("Auth=") }
            ?.removePrefix("Auth=")
            ?.trim()
            ?: throw Exception("ClientLogin response missing Auth token")

        authToken = token
        return token
    }

    private suspend inline fun <reified T> authorizedGet(path: String): T {
        val token = ensureAuthToken()
        val response = client.newCall(
            Request.Builder()
                .url(apiUrl(path))
                .header("Authorization", "GoogleLogin auth=$token")
                .build()
        ).executeAsync()

        if (response.code == 401) {
            // Token expired: retry once with a fresh login.
            authToken = null
            val freshToken = ensureAuthToken()
            val retryResponse = client.newCall(
                Request.Builder()
                    .url(apiUrl(path))
                    .header("Authorization", "GoogleLogin auth=$freshToken")
                    .build()
            ).executeAsync()
            if (!retryResponse.isSuccessful) throw Exception("Unauthorized")
            return toDTO(retryResponse.body.string())
        }
        if (!response.isSuccessful) {
            throw Exception("Request failed with HTTP ${response.code}")
        }
        return toDTO(response.body.string())
    }

    private suspend fun postToken(): String {
        val token = ensureAuthToken()
        val response = client.newCall(
            Request.Builder()
                .url(apiUrl("/reader/api/0/token"))
                .header("Authorization", "GoogleLogin auth=$token")
                .build()
        ).executeAsync()
        if (!response.isSuccessful) throw Exception("Unable to fetch POST token")
        return response.body.string().trim()
    }

    @Throws(Exception::class)
    suspend fun validCredentials(): Boolean {
        authToken = null
        login()
        return true
    }

    suspend fun getSubscriptionList(): GoogleReaderApiDto.SubscriptionList =
        authorizedGet("/reader/api/0/subscription/list?output=json")

    suspend fun getUnreadItemIds(maxCount: Int = MAX_ITEM_IDS): Set<String> =
        getStreamItemIds("/reader/api/0/stream/items/ids?output=json&n=$maxCount&s=user/-/state/com.google/reading-list&xt=user/-/state/com.google/read")

    suspend fun getStarredItemIds(maxCount: Int = MAX_ITEM_IDS): Set<String> =
        getStreamItemIds("/reader/api/0/stream/items/ids?output=json&n=$maxCount&s=user/-/state/com.google/starred")

    private suspend fun getStreamItemIds(path: String): Set<String> {
        val dto: GoogleReaderApiDto.ItemRefs = authorizedGet(path)
        return dto.itemRefs.orEmpty().mapNotNull { it.id }.toSet()
    }

    suspend fun getStreamContents(
        streamId: String = "user/-/state/com.google/reading-list",
        continuation: String? = null,
        count: Int = ITEMS_PAGE_SIZE,
    ): GoogleReaderApiDto.ReadingList {
        val encodedStream = java.net.URLEncoder.encode(streamId, "UTF-8")
        val continuationParam = continuation?.let { "&c=$it" }.orEmpty()
        return authorizedGet("/reader/api/0/stream/contents/$encodedStream?output=json&n=$count$continuationParam")
    }

    /** Marks or unmarks [itemId] read/starred by posting the matching Reader tag. */
    suspend fun editTag(itemId: String, tag: String, add: Boolean) {
        val token = ensureAuthToken()
        val postTokenValue = postToken()
        val body = FormBody.Builder()
            .add("i", itemId)
            .add(if (add) "a" else "r", tag)
            .add("T", postTokenValue)
            .build()
        val response = client.newCall(
            Request.Builder()
                .url(apiUrl("/reader/api/0/edit-tag"))
                .header("Authorization", "GoogleLogin auth=$token")
                .post(body)
                .build()
        ).executeAsync()
        if (!response.isSuccessful) {
            throw Exception("editTag failed with HTTP ${response.code}")
        }
    }

    companion object {
        const val TAG_READ = "user/-/state/com.google/read"
        const val TAG_STARRED = "user/-/state/com.google/starred"
        const val ITEMS_PAGE_SIZE = 50
        const val MAX_ITEM_IDS = 1000

        private val instances: ConcurrentHashMap<String, GoogleReaderApi> = ConcurrentHashMap()

        fun getInstance(serverUrl: String, username: String, password: String): GoogleReaderApi =
            instances.getOrPut("$serverUrl$username") {
                GoogleReaderApi(serverUrl, username, password)
            }

        /** Visible for testing: same caching semantics as [getInstance] but with an injectable [OkHttpClient]. */
        fun getInstanceForTest(
            serverUrl: String,
            username: String,
            password: String,
            httpClient: OkHttpClient,
        ): GoogleReaderApi =
            instances.getOrPut("$serverUrl$username") {
                GoogleReaderApi(serverUrl, username, password, httpClient)
            }
    }
}
