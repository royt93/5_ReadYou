package com.mckimquyen.reader.ui.page.readnews

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.sv.ReadNewsController
import com.mckimquyen.reader.domain.sv.ReadNewsOutcome
import com.mckimquyen.reader.domain.sv.ReadNewsSpeaker
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class ReadNewsInstrumentationTest {

    private class FakeSpeaker : ReadNewsSpeaker {
        val completionsFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
        override val completions = completionsFlow
        val played = mutableListOf<Triple<String, String, String>>()
        val notices = mutableListOf<String>()
        override fun play(text: String, title: String, subtitle: String) { played += Triple(text, title, subtitle) }
        override fun speakNotice(text: String) { notices += text }
        override fun stop() = Unit
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AndroidDatabase
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        scope.coroutineContext[Job]?.cancel()
        database.close()
    }

    private fun controller(speaker: FakeSpeaker) =
        ReadNewsController(context, database.articleDao(), speaker, Dispatchers.IO, Dispatchers.Default, scope, com.mckimquyen.reader.domain.repository.WidgetRefreshNotifier { })

    private suspend fun seed(vararg ids: String) {
        // currentAccountId mặc định = 1 khi DataStore chưa có khoá.
        database.accountDao().insert(Account(id = ACCOUNT_ID, name = "Test", type = AccountType.Local))
        database.groupDao().insert(Group(id = GROUP_ID, name = "Group", accountId = ACCOUNT_ID))
        database.feedDao().insert(Feed(id = FEED_ID, name = "BBC", url = "https://example.com/rss", groupId = GROUP_ID, accountId = ACCOUNT_ID))
        ids.forEachIndexed { index, id ->
            database.articleDao().insert(
                Article(
                    id = id, date = Date(System.currentTimeMillis() - index * 1_000L), title = "Title $id",
                    rawDescription = "<p>Body <b>$id</b></p>", shortDescription = "", link = "https://example.com/$id",
                    feedId = FEED_ID, accountId = ACCOUNT_ID, isUnread = true,
                )
            )
        }
    }

    private suspend fun isUnread(id: String): Boolean = database.articleDao().queryById(id)!!.article.isUnread

    @Test
    fun startCompletionAndNext_readThroughRealRoomDatabase() = runBlocking {
        seed("a1", "a2")
        val speaker = FakeSpeaker()
        val controller = controller(speaker)

        assertEquals(ReadNewsOutcome.STARTED, controller.start())
        val first = speaker.played.single()
        assertTrue("văn bản đã bỏ thẻ HTML: ${first.first}", first.first.contains("Body a1") && !first.first.contains("<"))
        assertEquals("Title a1", first.second)
        assertEquals("BBC", first.third)

        speaker.completionsFlow.emit(Unit)
        withTimeout(WAIT_MS) { while (isUnread("a1")) kotlinx.coroutines.delay(POLL_MS) }
        assertFalse("bài đã đọc xong phải được đánh dấu đã đọc", isUnread("a1"))
        assertTrue("bài kế chưa được đọc không bị đụng tới", isUnread("a2"))

        assertEquals(ReadNewsOutcome.STARTED, controller.next())
        assertEquals("Title a2", speaker.played.last().second)
    }

    @Test
    fun start_withNoUnreadArticles_returnsNoUnread() = runBlocking {
        database.accountDao().insert(Account(id = ACCOUNT_ID, name = "Test", type = AccountType.Local))
        val speaker = FakeSpeaker()

        assertEquals(ReadNewsOutcome.NO_UNREAD, controller(speaker).start())
        assertEquals(1, speaker.notices.size)
        assertTrue(speaker.played.isEmpty())
    }

    @Test
    fun deepLinkResolvesToReadNewsActivity_andShortcutIsRegistered() {
        val link = Intent(Intent.ACTION_VIEW, Uri.parse("rsscathub://read-news")).setPackage(context.packageName)
        val resolved = context.packageManager.queryIntentActivities(link, 0)
        assertTrue("deep link phải trỏ tới ReadNewsActivity: ${resolved.map { it.activityInfo.name }}",
            resolved.any { it.activityInfo.name == ReadNewsActivity::class.java.name })

        val shortcuts = context.getSystemService(ShortcutManager::class.java).manifestShortcuts
        val shortcut = shortcuts.singleOrNull { it.id == "read_latest_news" }
        assertNotNull("shortcut tĩnh read_latest_news phải được đăng ký: ${shortcuts.map { it.id }}", shortcut)
    }

    private companion object {
        const val ACCOUNT_ID = 1
        const val GROUP_ID = "group-1"
        const val FEED_ID = "feed-1"
        const val WAIT_MS = 5_000L
        const val POLL_MS = 50L
    }
}
