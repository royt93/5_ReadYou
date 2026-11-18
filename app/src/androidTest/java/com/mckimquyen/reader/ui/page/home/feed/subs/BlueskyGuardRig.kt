package com.mckimquyen.reader.ui.page.home.feed.subs

import androidx.lifecycle.ViewModelProvider
import com.mckimquyen.reader.RApp
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.sv.BlueskyDeviceFixtures
import com.mckimquyen.reader.domain.sv.LocalRssSv
import com.mckimquyen.reader.domain.sv.LocalRssSvFactory
import com.mckimquyen.reader.domain.sv.SyncResult
import com.mckimquyen.reader.infrastructure.newsletter.NewsletterUrlResolver
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyApiClient
import com.mckimquyen.reader.infrastructure.social.nostr.NostrRelayClient
import com.mckimquyen.reader.ui.ext.currentAccountId
import kotlinx.coroutines.Dispatchers
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

private const val GUARD_TIMEOUT_MS = 5_000L
private const val GUARD_MAX_BODY_BYTES = 512 * 1024L
private const val HTTP_SERVER_ERROR = 500

/**
 * A fake Bluesky server on 127.0.0.1, the phone's real Room, and the real SubscribeViewModel and LocalRssSv wired to them.
 * Every row this rig writes has an id starting with [PROBE_PREFIX]; [cleanUp] deletes them all and [probeRowCount] proves it.
 */
class BlueskyGuardRig : AutoCloseable {
    private val app: RApp = LocalRssSvFactory.app()
    private val db = app.androidDatabase
    val accountId: Int = app.currentAccountId

    private val profileCalls = AtomicInteger(0)
    private val feedCalls = AtomicInteger(0)
    @Volatile private var profileBody: String = BlueskyDeviceFixtures.PROFILE_BODY
    @Volatile private var feedBodyText: String = authorFeedBody(BlueskyDeviceFixtures.DID, BlueskyDeviceFixtures.HANDLE)
    @Volatile private var profileFails = false

    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.requestLine.contains("getProfile")) {
                    profileCalls.incrementAndGet()
                    if (profileFails) MockResponse().setResponseCode(HTTP_SERVER_ERROR) else json(profileBody)
                } else {
                    feedCalls.incrementAndGet()
                    json(feedBodyText)
                }
        }
        start()
    }

    private val blueskyApi = BlueskyApiClient(
        app.okHttpClient, Dispatchers.IO, server.url("/").toString().trimEnd('/'), GUARD_TIMEOUT_MS, GUARD_MAX_BODY_BYTES,
    )
    private val blocked = AtomicInteger(0)

    /** The app's own client, except that any host other than 127.0.0.1 is refused and counted: nothing here may reach the network. */
    private val guardedClient: OkHttpClient = app.okHttpClient.newBuilder().addInterceptor(
        Interceptor { chain ->
            if (chain.request().url.host == LOOPBACK_HOST) {
                chain.proceed(chain.request())
            } else {
                blocked.incrementAndGet()
                throw IOException("front02r: a request to a host other than 127.0.0.1 was refused")
            }
        },
    ).build()

    val viewModelFactory: ViewModelProvider.Factory = subscribeViewModelFactoryWith(
        blueskyApi = blueskyApi,
        rssHelper = RssHelper(app, Dispatchers.IO, guardedClient),
        newsletterUrlResolver = NewsletterUrlResolver(guardedClient),
    )
    private val service: LocalRssSv = LocalRssSvFactory.build(
        blueskyApi, NostrRelayClient(app.okHttpClient, Dispatchers.IO, listOf("ws://127.0.0.1:1"), false, GUARD_TIMEOUT_MS),
    )
    private val filters = LocalRssSvFactory.smartFilterManager(app)
    val groupId = "${PROBE_PREFIX}group"

    /** What the fake profile endpoint answers next: this account's DID and the handle the server names. */
    fun profile(did: String, handle: String) {
        profileFails = false
        profileBody = """{"did":"$did","handle":"$handle","displayName":"Probe","avatar":"https://cdn.bsky.app/img/avatar/probe@jpeg"}"""
        // The app keeps only posts whose author DID is the profile's DID, so the fake feed must be written by that DID.
        feedBodyText = authorFeedBody(did, handle)
    }

    fun failProfileWith500() {
        profileFails = true
    }

    /** The feed answers with a repost only: no own post, so a sync must count it as a failure, not as an empty feed. */
    fun onlyRepostsInTheFeed() {
        feedBodyText = BlueskyDeviceFixtures.feedBody(BlueskyDeviceFixtures.repost("3front02rrp"))
    }

    fun profileRequests(): Int = profileCalls.get()
    fun feedRequests(): Int = feedCalls.get()

    /** Requests the guarded client refused because they were not for 127.0.0.1; must stay 0. */
    fun blockedRequests(): Int = blocked.get()

    suspend fun createGroup() {
        db.groupDao().insert(Group(id = groupId, name = "front02r probe", accountId = accountId))
    }

    /** One stored Bluesky source of the probe group; [sourceUrl] null means "same as url" like a source saved before FRONT-02o. */
    suspend fun seed(id: String, url: String, sourceUrl: String?, sourceType: String = Feed.BLUESKY_SOURCE_TYPE) {
        db.feedDao().insert(
            Feed(
                id = "$PROBE_PREFIX$id", name = "Bluesky · Probe", icon = null, url = url, sourceUrl = sourceUrl,
                groupId = groupId, accountId = accountId, isNotification = false, sourceType = sourceType,
            ),
        )
    }

    suspend fun stored(id: String): Feed? = db.feedDao().queryById("$PROBE_PREFIX$id")

    /** The same three steps one background sync round makes for ONE feed (copied from the 02h rig, not shared). */
    suspend fun syncOnce(feed: Feed): SyncResult {
        val outcome = service.syncSingleFeed(feed)
        val inserted = if (outcome == null) emptyList() else db.articleDao().insertListIfNotExist(filters.applyRules(outcome.articles))
        return SyncResult(outcome, inserted)
    }

    fun articleCountOf(feedId: String): Int = scalar("SELECT COUNT(*) FROM article WHERE feedId = '$PROBE_PREFIX$feedId'").toInt()

    /** Any row anywhere whose id starts with the probe prefix; must be 0 after cleanUp. */
    fun probeRowCount(): Long = listOf(
        "SELECT COUNT(*) FROM feed WHERE id LIKE '$PROBE_PREFIX%'",
        "SELECT COUNT(*) FROM \"group\" WHERE id LIKE '$PROBE_PREFIX%'",
        "SELECT COUNT(*) FROM feed_health_record WHERE feedId LIKE '$PROBE_PREFIX%'",
        "SELECT COUNT(*) FROM article WHERE feedId LIKE '$PROBE_PREFIX%'",
    ).sumOf { scalar(it) }

    /** Feed and article rows that are NOT probe rows: the owner's real data; must be the same before and after every test. */
    fun realRowCounts(): Pair<Long, Long> = Pair(
        scalar("SELECT COUNT(*) FROM feed WHERE id NOT LIKE '$PROBE_PREFIX%'"),
        scalar("SELECT COUNT(*) FROM article WHERE feedId NOT LIKE '$PROBE_PREFIX%'"),
    )

    private fun scalar(sql: String): Long = db.openHelper.writableDatabase.query(sql).use { c ->
        c.moveToFirst()
        c.getLong(0)
    }

    /** Deleting the group cascades to its feeds and their articles; the health rows have no foreign key, so they go first. */
    suspend fun cleanUp() {
        val feeds = db.openHelper.writableDatabase.query("SELECT id FROM feed WHERE id LIKE '$PROBE_PREFIX%'").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
        feeds.forEach { db.feedHealthDao().deleteByFeedId(it) }
        db.groupDao().queryById(groupId)?.let { db.groupDao().delete(it) }
    }

    override fun close() {
        server.shutdown()
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    companion object {
        const val PROBE_PREFIX = "front02r_probe_"
        private const val LOOPBACK_HOST = "127.0.0.1"

        /** One own post written by [did]; the fixtures' own post is fixed to another DID, which would be dropped here. */
        fun authorFeedBody(did: String, handle: String): String =
            """{"feed":[{"post":{"uri":"at://$did/app.bsky.feed.post/$RKEY","cid":"c$RKEY","author":{"did":"$did","handle":"$handle"},""" +
                """"record":{"${'$'}type":"app.bsky.feed.post","text":"front02r own post","createdAt":"2026-11-10T08:00:00.000Z"}}}]}"""
        private const val RKEY = "3front02r"
    }
}
