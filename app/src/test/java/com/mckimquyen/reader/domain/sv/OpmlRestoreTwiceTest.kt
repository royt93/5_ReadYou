package com.mckimquyen.reader.domain.sv

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.infrastructure.rss.OPMLDataSource
import com.mckimquyen.reader.ui.ext.getDefaultGroupId
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

/**
 * Restoring the same OPML twice (a second tap, or the same cloud file) on a REAL Room. Measured, not assumed: a restore only ever adds, so
 * the question is what a second one adds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OpmlRestoreTwiceTest {
    private lateinit var db: AndroidDatabase
    private lateinit var sv: OpmlSv
    private val accountId = 1
    private val defaultGroup = Group(id = accountId.getDefaultGroupId(), name = "Default", accountId = accountId)

    private val file = """<?xml version="1.0" encoding="UTF-8"?><opml version="2.0"><head><title>t</title></head><body>
        <outline text="News"><outline text="A" xmlUrl="https://a.example/feed.xml"/><outline text="B" xmlUrl="https://b.example/feed.xml"/></outline>
        <outline text="Loose" xmlUrl="https://loose.example/feed.xml"/>
        <outline text="Empty group"/>
        </body></opml>""".trimIndent()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java).allowMainThreadQueries().build()
        runBlocking {
            db.accountDao().insert(Account(id = accountId, name = "Local", type = AccountType.Local))
            db.groupDao().insert(defaultGroup)
        }
        val rssSv = mockk<RssSv>()
        coEvery { rssSv.get().isFeedExist(any()) } coAnswers { db.feedDao().queryByLink(accountId, firstArg()).isNotEmpty() }
        coEvery { rssSv.get().isFeedSourceExist(any()) } returns false
        sv = OpmlSv(context, db.groupDao(), db.feedDao(), db.accountDao(), rssSv, OPMLDataSource(context, Dispatchers.Unconfined))
    }

    @After
    fun tearDown() = db.close()

    private fun restoreOnce() = runBlocking { sv.saveToDatabase(ByteArrayInputStream(file.toByteArray())) }
    private fun feedUrls() = runBlocking { db.feedDao().queryAll(accountId).map { it.url }.sorted() }
    private fun groupNames() = runBlocking { db.groupDao().queryAll(accountId).map { it.name }.sorted() }

    @Test
    fun firstRestore_addsEveryFeedAndEveryGroupOnce() {
        restoreOnce()
        assertEquals(listOf("https://a.example/feed.xml", "https://b.example/feed.xml", "https://loose.example/feed.xml"), feedUrls())
        assertEquals(listOf("Default", "Empty group", "News"), groupNames())
    }

    @Test
    fun secondRestoreOfTheSameFile_addsNoFeedTwice() {
        restoreOnce()
        restoreOnce()
        assertEquals(3, feedUrls().size)
        assertEquals(feedUrls().toSet().size, feedUrls().size)
    }

    @Test
    fun secondRestoreOfTheSameFile_doesNotDuplicateTheGroups() {
        restoreOnce()
        restoreOnce()
        assertEquals("a second restore must not create the same group again", listOf("Default", "Empty group", "News"), groupNames())
    }

    @Test
    fun secondRestore_putsTheFeedsOfAnExistingGroupInThatSameGroup() {
        val twoFeedsInNews = """<?xml version="1.0" encoding="UTF-8"?><opml version="2.0"><head><title>t</title></head><body>
            <outline text="News"><outline text="A" xmlUrl="https://a.example/feed.xml"/></outline></body></opml>"""
        runBlocking { sv.saveToDatabase(ByteArrayInputStream(twoFeedsInNews.toByteArray())) }
        val newsId = runBlocking { db.groupDao().queryAll(accountId).single { it.name == "News" }.id }
        val moreInNews = """<?xml version="1.0" encoding="UTF-8"?><opml version="2.0"><head><title>t</title></head><body>
            <outline text="News"><outline text="C" xmlUrl="https://c.example/feed.xml"/></outline></body></opml>"""
        runBlocking { sv.saveToDatabase(ByteArrayInputStream(moreInNews.toByteArray())) }
        val feeds = runBlocking { db.feedDao().queryAll(accountId) }
        assertEquals("the new feed joins the group that already exists", newsId, feeds.single { it.url == "https://c.example/feed.xml" }.groupId)
        assertEquals(1, runBlocking { db.groupDao().queryAll(accountId).count { it.name == "News" } })
    }

    @Test
    fun aGroupWithADifferentName_isStillCreated() {
        restoreOnce()
        val other = """<?xml version="1.0" encoding="UTF-8"?><opml version="2.0"><head><title>t</title></head><body>
            <outline text="Sport"><outline text="S" xmlUrl="https://s.example/feed.xml"/></outline></body></opml>"""
        runBlocking { sv.saveToDatabase(ByteArrayInputStream(other.toByteArray())) }
        assertEquals(listOf("Default", "Empty group", "News", "Sport"), groupNames())
    }

    @Test
    fun theSameGroupNameTwiceInOneFile_staysTwoGroups_andARestoreOfThatFileAddsNoMore() {
        val dup = """<?xml version="1.0" encoding="UTF-8"?><opml version="2.0"><head><title>t</title></head><body>
            <outline text="Tech"><outline text="T1" xmlUrl="https://t1.example/feed.xml"/></outline>
            <outline text="Tech"><outline text="T2" xmlUrl="https://t2.example/feed.xml"/></outline></body></opml>"""
        runBlocking { sv.saveToDatabase(ByteArrayInputStream(dup.toByteArray())) }
        fun techGroups() = runBlocking { db.groupDao().queryAll(accountId).filter { it.name == "Tech" } }
        // Group names are not unique in the app, so two same-named outlines in one file are two groups (a faithful round trip).
        assertEquals("a faithful round trip keeps two groups that share a name", 2, techGroups().size)
        runBlocking { sv.saveToDatabase(ByteArrayInputStream(dup.toByteArray())) }
        assertEquals("a second restore adds no group", 2, techGroups().size)
        assertEquals(2, runBlocking { db.feedDao().queryAll(accountId) }.count { it.url.startsWith("https://t") })
    }
}
