package com.mckimquyen.reader.infrastructure.p2psync

import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.mirror.MirrorStartResult
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.util.Date

/**
 * Real crypto provider, real SQLite, real loopback socket on the device. Uses an in-memory database, so it never reads
 * or writes the app's own Room files or DataStore.
 */
@RunWith(AndroidJUnit4::class)
class LocalSyncDeviceTest {
    private val loopback = InetAddress.getByName("127.0.0.1")
    private lateinit var senderDb: AndroidDatabase
    private lateinit var receiverDb: AndroidDatabase
    private val server = MirrorServer()
    private val feedUrl = "https://example.org/rss"
    private val secret = PairingSecret.fromHex("0123456789abcdef".repeat(2))

    @Before fun setUp() = runBlocking {
        senderDb = memoryDb().also { seed(it, unread = false, starred = true, readAt = 50L) }
        receiverDb = memoryDb().also { seed(it, unread = true, starred = false, readAt = null) }
    }

    @After fun tearDown() {
        server.stop()
        senderDb.close()
        receiverDb.close()
    }

    private fun memoryDb() = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java,
    ).build()

    private suspend fun seed(db: AndroidDatabase, unread: Boolean, starred: Boolean, readAt: Long?) {
        db.accountDao().insert(Account(id = 1, name = "Local", type = AccountType.Local))
        db.accountDao().insert(Account(id = 2, name = "Fever", type = AccountType.Fever))
        db.groupDao().insert(Group(id = 1.getDefaultGroupId(), name = "Default", accountId = 1))
        db.groupDao().insert(Group(id = 2.getDefaultGroupId(), name = "Default", accountId = 2))
        db.feedDao().insert(Feed("feed-local", "News", url = feedUrl, groupId = 1.getDefaultGroupId(), accountId = 1))
        db.feedDao().insert(Feed("feed-fever", "Fever", url = feedUrl, groupId = 2.getDefaultGroupId(), accountId = 2))
        listOf("feed-local" to 1, "feed-fever" to 2).forEach { (feedId, account) ->
            db.articleDao().insert(
                Article(
                    id = "article-$account", date = Date(1L), title = "Keep $account", rawDescription = "RAW",
                    shortDescription = "SHORT", link = "https://example.org/story", feedId = feedId, accountId = account,
                    isUnread = if (account == 1) unread else true, isStarred = if (account == 1) starred else false,
                    readAt = if (account == 1) readAt else null,
                ),
            )
        }
    }

    private fun start(bytes: ByteArray): MirrorStartResult.Started =
        server.startSync(loopback, bytes) as MirrorStartResult.Started

    private fun url(started: MirrorStartResult.Started) = "http://127.0.0.1:${started.port}/s/${started.token.value}"

    private suspend fun encryptedSnapshot(): ByteArray {
        val snapshot = LocalSyncRepository(senderDb).snapshot(1)
        return SyncEnvelopeCrypto.encrypt(SyncPackageCodec.encode(snapshot), secret)
    }

    @Test fun envelopeCrossesRealLoopbackAndAppliesOnlyToTheLocalAccount() = runBlocking {
        val envelope = encryptedSnapshot()
        val started = start(envelope)
        val fetched = SyncSessionTransport(5_000, 5_000) { it == "127.0.0.1" }.fetch(url(started))
        assertArrayEquals(envelope, fetched)
        assertFalse(String(fetched, Charsets.ISO_8859_1).contains("example.org"))

        val plain = SyncEnvelopeCrypto.decrypt(fetched, secret)
        val incoming = (SyncPackageCodec.decodeAndValidate(plain) as SyncPackageResult.Valid).data
        val result = LocalSyncRepository(receiverDb).apply(1, incoming)
        assertEquals(1, result.updatesApplied)

        val local = receiverDb.articleDao().queryById("article-1")!!.article
        assertFalse(local.isUnread)
        assertTrue(local.isStarred)
        assertEquals(50L, local.readAt)
        assertEquals("Keep 1", local.title)
        val fever = receiverDb.articleDao().queryById("article-2")!!.article
        assertTrue(fever.isUnread)
        assertFalse(fever.isStarred)
    }

    @Test fun wrongSecretAndTamperedEnvelopeChangeNothing() = runBlocking {
        val envelope = encryptedSnapshot()
        assertTrue(runCatching { SyncEnvelopeCrypto.decrypt(envelope, PairingSecret.fromHex("f".repeat(32))) }.isFailure)
        val tampered = envelope.copyOf().also { it[it.lastIndex] = (it[it.lastIndex].toInt() xor 1).toByte() }
        assertTrue(runCatching { SyncEnvelopeCrypto.decrypt(tampered, secret) }.isFailure)
        val local = receiverDb.articleDao().queryById("article-1")!!.article
        assertTrue(local.isUnread)
        assertFalse(local.isStarred)
    }

    @Test fun expiredTokenNoLongerServesTheEnvelope() {
        var now = 1_000L
        val timed = MirrorServer(clock = { now })
        try {
            val started = timed.startSync(loopback, ByteArray(80) { it.toByte() }) as MirrorStartResult.Started
            val address = "http://127.0.0.1:${started.port}/s/${started.token.value}"
            val transport = SyncSessionTransport(5_000, 5_000) { it == "127.0.0.1" }
            assertEquals(80, transport.fetch(address).size)
            now = started.token.expiresAtMillis
            assertTrue(runCatching { transport.fetch(address) }.isFailure)
        } finally {
            timed.stop()
        }
    }

    @Test fun measuresRealPbkdf2CostOnThisDevice() {
        val plain = ByteArray(4_096) { it.toByte() }
        val encryptMillis = time { SyncEnvelopeCrypto.encrypt(plain, secret) }
        val envelope = SyncEnvelopeCrypto.encrypt(plain, secret)
        val decryptMillis = time { SyncEnvelopeCrypto.decrypt(envelope, secret) }
        Log.i("EXC05A", "pbkdf2_310000 encryptMs=$encryptMillis decryptMs=$decryptMillis")
        assertTrue("encrypt took ${encryptMillis}ms", encryptMillis < 20_000)
        assertTrue("decrypt took ${decryptMillis}ms", decryptMillis < 20_000)
    }

    private fun time(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }
}
