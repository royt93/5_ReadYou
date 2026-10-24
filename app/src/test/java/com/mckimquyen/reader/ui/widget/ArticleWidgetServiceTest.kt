package com.mckimquyen.reader.ui.widget

import android.app.Application
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleListItem
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.FeedDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ArticleWidgetServiceTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val articles = mockk<ArticleDao>()
    private val feeds = mockk<FeedDao>()
    private var accountId = 1
    private val factory = ArticleWidgetFactory(context, articles, feeds) { accountId }

    private fun item(id: String) = ArticleListItem(
        id, Date(0), "Title $id", null, "", null, "https://example.test/$id", "feed", accountId,
        true, false, false, null, null, null, null, null, null,
    )

    @Test fun loadsFiveRowsAndRendersOnlyMetadata() {
        coEvery { articles.queryUnreadArticleList(1, ArticleWidgetFactory.MAX_ARTICLES) } returns (1..5).map { item("$it") }
        coEvery { feeds.queryById("feed") } returns Feed("feed", "Feed name", url = "https://example.test", groupId = "g", accountId = 1)
        factory.onDataSetChanged()
        coVerify(exactly = 1) { articles.queryUnreadArticleList(1, ArticleWidgetFactory.MAX_ARTICLES) }
        assertEquals(5, factory.count)
        val remote = factory.getViewAt(0)
        assertNotNull(remote)
        val view = requireNotNull(remote).apply(context, FrameLayout(context))
        assertEquals("Title 1", view.findViewById<TextView>(R.id.widget_item_title).text.toString())
        assertEquals("Feed name", view.findViewById<TextView>(R.id.widget_item_feed).text.toString())
        coVerify(exactly = 5) { feeds.queryById("feed") }
        assertNull(factory.getViewAt(-1))
        assertNull(factory.getViewAt(5))
        assertFalse(factory.hasStableIds())
    }

    @Test fun reloadUsesCurrentAccountAndPropagatesDaoFailure() {
        coEvery { articles.queryUnreadArticleList(1, any()) } returns listOf(item("old"))
        coEvery { feeds.queryById(any()) } returns null
        factory.onDataSetChanged()
        assertEquals(1, factory.count)
        accountId = 2
        coEvery { articles.queryUnreadArticleList(2, any()) } throws IllegalStateException("db unavailable")
        val failure = runCatching { factory.onDataSetChanged() }
        assertEquals(IllegalStateException::class.java, failure.exceptionOrNull()?.javaClass)
        assertEquals(1, factory.count)
        coVerify(exactly = 1) { articles.queryUnreadArticleList(2, ArticleWidgetFactory.MAX_ARTICLES) }
    }

    @Test fun emptyAndDeletedFeedAreSafe() {
        coEvery { articles.queryUnreadArticleList(1, any()) } returns emptyList()
        factory.onDataSetChanged()
        assertEquals(0, factory.count)
        assertNull(factory.getViewAt(0))
        coEvery { articles.queryUnreadArticleList(1, any()) } returns listOf(item("missing-feed"))
        coEvery { feeds.queryById(any()) } returns null
        factory.onDataSetChanged()
        val view = requireNotNull(factory.getViewAt(0)).apply(context, FrameLayout(context))
        assertEquals(context.getString(R.string.unknown), view.findViewById<TextView>(R.id.widget_item_feed).text.toString())
        factory.onDestroy()
        assertEquals(0, factory.count)
    }
}
