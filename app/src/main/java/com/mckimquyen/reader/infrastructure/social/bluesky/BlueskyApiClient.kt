package com.mckimquyen.reader.infrastructure.social.bluesky

import com.mckimquyen.reader.infrastructure.di.IODispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads one account from the public Bluesky API: its profile, then its latest own posts. Read-only, no login, nothing sent
 * but the account name. Nothing here logs: the API sees the account being read, so the app must not also write it down.
 */
@Singleton
class BlueskyApiClient internal constructor(
    private val client: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher,
    private val baseUrl: String,
    private val timeoutMs: Long,
    private val maxBodyBytes: Long,
) {
    @Inject constructor(client: OkHttpClient, @IODispatcher ioDispatcher: CoroutineDispatcher) :
        this(client, ioDispatcher, API_BASE, TIMEOUT_MS, MAX_BODY_BYTES)

    // The app-wide client follows redirects; here a redirect would let the answer come from another host, so none is followed.
    private val strict: OkHttpClient = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    suspend fun fetch(actor: String): BlueskyOutcome = withContext(ioDispatcher) {
        if (!ACTOR_REGEX.matches(actor)) return@withContext BlueskyOutcome.Failed
        try {
            withTimeout(timeoutMs) { read(actor) }
        } catch (_: TimeoutCancellationException) {
            BlueskyOutcome.Failed
        }
    }

    private suspend fun read(actor: String): BlueskyOutcome {
        val profileCall = when (val answer = get("/xrpc/app.bsky.actor.getProfile", "actor" to actor)) {
            is Answer.Body -> answer
            Answer.NotFound -> return BlueskyOutcome.NotFound
            Answer.Failed -> return BlueskyOutcome.Failed
        }
        val profile = parseObject(profileCall.text) ?: return BlueskyOutcome.Failed
        val did = profile.optString("did").takeIf { it.startsWith("did:") && ACTOR_REGEX.matches(it) } ?: return BlueskyOutcome.Failed
        val feed = when (val answer = get(
            "/xrpc/app.bsky.feed.getAuthorFeed", "actor" to did, "limit" to POST_LIMIT.toString(), "filter" to FEED_FILTER,
        )) {
            is Answer.Body -> answer
            Answer.NotFound, Answer.Failed -> return BlueskyOutcome.Failed
        }
        val items: JSONArray = parseObject(feed.text)?.optJSONArray("feed") ?: return BlueskyOutcome.Failed
        return BlueskyOutcome.Found(
            BlueskyFetchResult(
                items = items,
                did = did,
                handle = profile.optString("handle"),
                displayName = BlueskyFeedMapper.cleanDisplayName(profile.opt("displayName") as? String),
                avatarUrl = BlueskyFeedMapper.iconUrl(profile.opt("avatar") as? String),
            ),
        )
    }

    private sealed interface Answer {
        class Body(val text: String) : Answer
        object NotFound : Answer
        object Failed : Answer
    }

    private suspend fun get(path: String, vararg query: Pair<String, String>): Answer {
        val url = (baseUrl + path).toHttpUrlOrNull()?.newBuilder()?.apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }?.build()
            ?: return Answer.Failed
        val call: Call = strict.newCall(Request.Builder().url(url).header("Accept", JSON).build())
        // The whole exchange, body included, runs in the callback: cancelling the caller (or its timeout) then cancels the call
        // itself, which a blocking execute() cannot be told to stop.
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) = continuation.resume(Answer.Failed)

                override fun onResponse(call: Call, response: Response) {
                    val answer = try {
                        response.use { answerOf(it) }
                    } catch (_: IOException) {
                        Answer.Failed
                    }
                    continuation.resume(answer)
                }
            })
        }
    }

    private fun answerOf(response: Response): Answer = when {
        response.code == HTTP_BAD_REQUEST -> {
            val text = readLimited(response.body?.source(), NOT_FOUND_PEEK_BYTES)
            if (text != null && text.contains(PROFILE_NOT_FOUND)) Answer.NotFound else Answer.Failed
        }
        !response.isSuccessful -> Answer.Failed
        else -> {
            val body = response.body
            // Any redirect is already "not successful" above (redirects are never followed), and readLimited alone enforces the size.
            if (body == null) Answer.Failed else readLimited(body.source(), maxBodyBytes)?.let { Answer.Body(it) } ?: Answer.Failed
        }
    }

    /** Reads at most [limit] bytes; returns null when the body is longer (nothing partial is ever parsed). */
    private fun readLimited(source: okio.BufferedSource?, limit: Long): String? {
        if (source == null) return null
        val buffer = Buffer()
        while (buffer.size <= limit) {
            if (source.read(buffer, minOf(limit + 1 - buffer.size, READ_CHUNK_BYTES)) == -1L) break
        }
        return if (buffer.size > limit) null else buffer.readUtf8()
    }

    private fun parseObject(text: String): JSONObject? = try {
        JSONObject(text)
    } catch (_: JSONException) {
        null
    }

    companion object {
        const val API_BASE = "https://public.api.bsky.app"
        const val TIMEOUT_MS = 8_000L
        const val MAX_BODY_BYTES = 512 * 1024L
        const val POST_LIMIT = 50
        private const val FEED_FILTER = "posts_no_replies"
        private const val JSON = "application/json"
        private const val HTTP_BAD_REQUEST = 400
        private const val PROFILE_NOT_FOUND = "Profile not found"
        private const val NOT_FOUND_PEEK_BYTES = 2_048L
        private const val READ_CHUNK_BYTES = 8_192L
        private val ACTOR_REGEX = Regex("^[A-Za-z0-9][A-Za-z0-9:._-]{0,252}$")
    }
}
