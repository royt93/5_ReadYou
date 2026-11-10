package com.mckimquyen.reader.infrastructure.p2psync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.mirror.MirrorStartResult
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.p2psync.FeedUrlNormalizer
import com.mckimquyen.reader.domain.p2psync.LocalSyncApplyResult
import com.mckimquyen.reader.domain.p2psync.PairingSecret
import com.mckimquyen.reader.domain.p2psync.SyncEnvelopeCrypto
import com.mckimquyen.reader.domain.p2psync.SyncPackageCodec
import com.mckimquyen.reader.domain.p2psync.SyncPackageResult
import com.mckimquyen.reader.domain.p2psync.SyncSessionTransport
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.infrastructure.mirror.MirrorServer
import com.mckimquyen.reader.ui.ext.getDefaultGroupId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.util.Date

private const val SHARED = "https://rt.example.invalid/shared.xml"
private const val A_ONLY = "https://rt.example.invalid/a-only.xml"
private const val B_ONLY = "https://rt.example.invalid/b-only.xml"
private const val A_ONLY_LINK = "https://rt.example.invalid/a-only-story"
private const val B_ONLY_LINK = "https://rt.example.invalid/b-only-story"
private const val STORY = "https://rt.example.invalid/"
private const val SOCKET_TIMEOUT_MILLIS = 5_000
private const val LOCAL = 1
private const val FEVER = 2

/**
 * Two real in-memory Room databases joined by two real [MirrorServer] sockets and the real [SyncSessionTransport]:
 * A sends, B previews and applies, then B sends a snapshot taken after that, and A previews and applies. Nothing in
 * the path is a fake, so this proves the two-way behaviour of the existing protocol, not of a test double.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LocalSyncRoundTripIntegrationTest {
    private lateinit var dbA: AndroidDatabase
    private lateinit var dbB: AndroidDatabase
    private val loopback = InetAddress.getByName("127.0.0.1")
    private val secret = PairingSecret.fromHex("00112233445566778899aabbccddeeff")
    private val serverA = MirrorServer()
    private val serverB = MirrorServer()
    private val transport = SyncSessionTransport(SOCKET_TIMEOUT_MILLIS, SOCKET_TIMEOUT_MILLIS) { it == "127.0.0.1" }
    private lateinit var beforeA: List<List<Any?>>
    private lateinit var beforeB: List<List<Any?>>

    @Before fun setUp() = runBlocking {
        dbA = memoryDb()
        dbB = memoryDb()
        seed(dbA, "A", ownFeed = A_ONLY, ownLink = A_ONLY_LINK, ownFeedId = "a-own")
        seed(dbB, "B", ownFeed = B_ONLY, ownLink = B_ONLY_LINK, ownFeedId = "b-own")
        // A: s1 read+starred early, s2 untouched, s3 starred. B: s1 untouched, s2 starred late, s3 read.
        story(dbA, "s1", unread = false, starred = true, readAt = 100L)
        story(dbA, "s2", unread = true, starred = false, readAt = null)
        story(dbA, "s3", unread = true, starred = true, readAt = 200L)
        story(dbB, "s1", unread = true, starred = false, readAt = null)
        story(dbB, "s2", unread = true, starred = true, readAt = 300L)
        story(dbB, "s3", unread = false, starred = false, readAt = 150L)
        beforeA = dump(dbA)
        beforeB = dump(dbB)
    }

    @After fun tearDown() {
        serverA.stop()
        serverB.stop()
        dbA.close()
        dbB.close()
    }

    private fun memoryDb() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java)
        .allowMainThreadQueries().build()

    private suspend fun seed(db: AndroidDatabase, side: String, ownFeed: String, ownLink: String, ownFeedId: String) {
        db.accountDao().insert(Account(id = LOCAL, name = "Local", type = AccountType.Local))
        db.accountDao().insert(Account(id = FEVER, name = "Fever", type = AccountType.Fever))
        db.groupDao().insert(
            Group(id = LOCAL.getDefaultGroupId(), name = "Default", accountId = LOCAL),
            Group(id = FEVER.getDefaultGroupId(), name = "Default", accountId = FEVER),
        )
        db.feedDao().insert(
            Feed("shared-$side", "Shared on $side", url = SHARED, groupId = LOCAL.getDefaultGroupId(), accountId = LOCAL),
            Feed(ownFeedId, "Own on $side", url = ownFeed, groupId = LOCAL.getDefaultGroupId(), accountId = LOCAL),
            Feed("fever-$side", "Fever", url = "https://fever.example.invalid/rss", groupId = FEVER.getDefaultGroupId(), accountId = FEVER),
        )
        db.articleDao().insert(
            article("own-$side", ownFeedId, LOCAL, ownLink, unread = false, starred = true, readAt = 50L),
            article("fever-article-$side", "fever-$side", FEVER, "https://fever.example.invalid/a", unread = true, starred = false, readAt = null),
        )
    }

    private suspend fun story(db: AndroidDatabase, suffix: String, unread: Boolean, starred: Boolean, readAt: Long?) {
        val side = if (db === dbA) "A" else "B"
        db.articleDao().insert(article("$suffix-$side", "shared-$side", LOCAL, "$STORY$suffix", unread, starred, readAt))
    }

    private fun article(id: String, feedId: String, accountId: Int, link: String, unread: Boolean, starred: Boolean, readAt: Long?) = Article(
        id = id, date = Date(123L), title = "Title $id", rawDescription = "RAW $id", shortDescription = "SHORT $id", link = link,
        feedId = feedId, accountId = accountId, isUnread = unread, isStarred = starred, isReadLater = false, readAt = readAt,
    )

    private suspend fun feedIdOn(db: AndroidDatabase, url: String): String =
        db.feedDao().queryAll(LOCAL).single { FeedUrlNormalizer.normalize(it.url) == url }.id

    /** (read, starred, readAt) of a shared story. */
    private suspend fun state(db: AndroidDatabase, suffix: String): Triple<Boolean, Boolean, Long?> {
        val article = requireNotNull(db.articleDao().queryArticleByLink("$STORY$suffix", feedIdOn(db, SHARED), LOCAL))
        return Triple(!article.isUnread, article.isStarred, article.readAt)
    }

    private suspend fun feverRows(db: AndroidDatabase): List<List<Any?>> =
        db.articleDao().querySyncSnapshots(FEVER).map { listOf(it.id, it.isUnread, it.isStarred, it.readAt) }

    private suspend fun dump(db: AndroidDatabase): List<List<Any?>> =
        db.feedDao().queryAll(LOCAL).sortedBy { it.url }.map { listOf("feed", it.url, it.name, it.groupId) } +
            db.articleDao().querySyncSnapshots(LOCAL).sortedBy { it.link }
                .map { listOf("article", it.link, it.isUnread, it.isStarred, it.readAt) } +
            feverRows(db)

    private suspend fun ciphertextOf(db: AndroidDatabase): ByteArray =
        SyncEnvelopeCrypto.encrypt(SyncPackageCodec.encode(LocalSyncRepository(db).snapshot(LOCAL)), secret)

    /**
     * One manual leg over the real sockets. The snapshot is taken now, the receiver previews (which must not write),
     * and only [apply] decides whether it also confirms.
     */
    private suspend fun leg(from: AndroidDatabase, server: MirrorServer, to: AndroidDatabase, apply: Boolean): LocalSyncApplyResult? {
        val started = server.startSync(loopback, ciphertextOf(from)) as MirrorStartResult.Started
        try {
            val bytes = transport.fetch("http://127.0.0.1:${started.port}/s/${started.token.value}")
            val incoming = (SyncPackageCodec.decodeAndValidate(SyncEnvelopeCrypto.decrypt(bytes, secret)) as SyncPackageResult.Valid).data
            val repository = LocalSyncRepository(to)
            val dumpBeforePreview = dump(to)
            repository.preview(LOCAL, incoming)
            assertEquals("preview must not write", dumpBeforePreview, dump(to))
            return if (apply) repository.apply(LOCAL, incoming) else null
        } finally {
            server.stop()
        }
    }

    @Test fun theRoundTripConvergesSharedStoriesAndKeepsTheEarliestReadAt() = runBlocking {
        leg(dbA, serverA, dbB, apply = true)
        leg(dbB, serverB, dbA, apply = true)
        listOf("s1", "s2", "s3").forEach { assertEquals("$it converges", state(dbA, it), state(dbB, it)) }
        assertEquals(Triple(true, true, 100L), state(dbA, "s1"))
        // s2 is unread on both phones, so it stays unread (read wins only if one side read it); the star and the only readAt survive.
        assertEquals(Triple(false, true, 300L), state(dbA, "s2"))
        assertEquals(Triple(true, true, 150L), state(dbA, "s3"))
    }

    @Test fun theReturnLegSendsTheStateThatTheFirstApplyJustProduced() = runBlocking {
        leg(dbA, serverA, dbB, apply = true)
        val snapshotB = LocalSyncRepository(dbB).snapshot(LOCAL)
        assertTrue(
            snapshotB.articles.any { it.link == "${STORY}s1" && !it.isUnread && it.isStarred && it.readAt == 100L },
        )
    }

    @Test fun feedsAreAddedOnceAndExistingMetadataIsKept() = runBlocking {
        leg(dbA, serverA, dbB, apply = true)
        leg(dbB, serverB, dbA, apply = true)
        listOf(dbA, dbB).forEach { db ->
            val urls = db.feedDao().queryAll(LOCAL).map { FeedUrlNormalizer.normalize(it.url) }
            assertEquals("no duplicate feed urls", urls.size, urls.toSet().size)
            assertTrue(urls.containsAll(listOf(SHARED, A_ONLY, B_ONLY)))
        }
        assertEquals("Shared on A", dbA.feedDao().queryAll(LOCAL).single { FeedUrlNormalizer.normalize(it.url) == SHARED }.name)
        assertEquals("Shared on B", dbB.feedDao().queryAll(LOCAL).single { FeedUrlNormalizer.normalize(it.url) == SHARED }.name)
        assertEquals(LOCAL.getDefaultGroupId(), dbB.feedDao().queryAll(LOCAL).single { FeedUrlNormalizer.normalize(it.url) == A_ONLY }.groupId)
        assertEquals(LOCAL.getDefaultGroupId(), dbA.feedDao().queryAll(LOCAL).single { FeedUrlNormalizer.normalize(it.url) == B_ONLY }.groupId)
    }

    @Test fun anArticleMissingOnTheReceiverIsSkippedInBothDirections() = runBlocking {
        leg(dbA, serverA, dbB, apply = true)
        assertNull(dbB.articleDao().queryArticleByLink(A_ONLY_LINK, feedIdOn(dbB, A_ONLY), LOCAL))
        leg(dbB, serverB, dbA, apply = true)
        assertNull(dbA.articleDao().queryArticleByLink(B_ONLY_LINK, feedIdOn(dbA, B_ONLY), LOCAL))
    }

    @Test fun otherAccountsAreNeverTouched() = runBlocking {
        val before = feverRows(dbA) to feverRows(dbB)
        leg(dbA, serverA, dbB, apply = true)
        leg(dbB, serverB, dbA, apply = true)
        assertEquals(before, feverRows(dbA) to feverRows(dbB))
    }

    @Test fun contentIsNeverChangedOnlyTheThreeStateColumns() = runBlocking {
        leg(dbA, serverA, dbB, apply = true)
        leg(dbB, serverB, dbA, apply = true)
        val a = requireNotNull(dbA.articleDao().queryById("s1-A")).article
        assertEquals("Title s1-A", a.title)
        assertEquals("RAW s1-A", a.rawDescription)
        assertEquals(Date(123L), a.date)
    }

    @Test fun cancellingTheFirstLegWritesNothingAndTheReturnLegSendsTheUnchangedDatabase() = runBlocking {
        leg(dbA, serverA, dbB, apply = false)
        assertEquals(beforeB, dump(dbB))
        leg(dbB, serverB, dbA, apply = true)
        assertEquals(Triple(true, true, 100L), state(dbA, "s1"))   // A keeps what it had
        assertEquals(Triple(false, true, 300L), state(dbA, "s2"))  // B's star and readAt reached A, still unread
        assertEquals(beforeB, dump(dbB))                           // B still never applied A
    }

    @Test fun anAppliedLegIsNotRolledBackWhenTheOtherPhoneNeverApplies() = runBlocking {
        leg(dbA, serverA, dbB, apply = true)
        val afterFirst = dump(dbB)
        leg(dbB, serverB, dbA, apply = false)
        assertEquals(afterFirst, dump(dbB))
        assertEquals(beforeA, dump(dbA))
    }

    @Test fun runningTheRoundTripAgainChangesNothing() = runBlocking {
        leg(dbA, serverA, dbB, apply = true)
        leg(dbB, serverB, dbA, apply = true)
        val settled = dump(dbA) to dump(dbB)
        val second = leg(dbA, serverA, dbB, apply = true)
        val third = leg(dbB, serverB, dbA, apply = true)
        assertEquals(settled, dump(dbA) to dump(dbB))
        assertEquals(0, second?.updatesApplied)
        assertEquals(0, third?.updatesApplied)
        assertEquals(0, second?.feedsAdded)
    }

    @Test fun aWrongTokenIsRejectedByTheRealServerAndNothingIsWritten() = runBlocking {
        val started = serverA.startSync(loopback, ciphertextOf(dbA)) as MirrorStartResult.Started
        try {
            val bad = "http://127.0.0.1:${started.port}/s/${"0".repeat(32)}"
            assertTrue(runCatching { transport.fetch(bad) }.isFailure)
            assertEquals(beforeB, dump(dbB))
        } finally {
            serverA.stop()
        }
    }

    @Test fun afterStopTheRealServerRefusesConnections() = runBlocking {
        val started = serverA.startSync(loopback, ciphertextOf(dbA)) as MirrorStartResult.Started
        val url = "http://127.0.0.1:${started.port}/s/${started.token.value}"
        assertNotNull(transport.fetch(url))
        serverA.stop()
        assertFalse(serverA.isRunning)
        assertTrue(runCatching { transport.fetch(url) }.isFailure)
    }
}
