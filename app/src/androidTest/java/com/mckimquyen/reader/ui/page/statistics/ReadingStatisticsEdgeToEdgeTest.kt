package com.mckimquyen.reader.ui.page.statistics

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavHostController
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.model.statistics.ReadingStatisticsCalendar
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.ui.component.statistics.READING_HEATMAP_TAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class ReadingStatisticsEdgeToEdgeTest {

    private data class Measured(
        val titleTop: Int,
        val statusInsetTop: Int,
        val bodyBottom: Int,
        val decorHeight: Int,
        val navInsetBottom: Int,
        val extraFound: Boolean,
    )

    @Test
    fun emptyState_clearsSystemBarInsets() {
        val database = newDatabase()
        try {
            val measured = measure(database, bodyTag = STATS_EMPTY_TAG, extraTag = null)
            assertTrue("Title under status bar: $measured", measured.titleTop >= measured.statusInsetTop)
            assertTrue(
                "Empty text overlaps navigation bar: $measured",
                measured.bodyBottom <= measured.decorHeight - measured.navInsetBottom,
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun contentState_clearsSystemBarInsetsAndShowsHeatmap() {
        val database = newDatabase()
        try {
            seedOneReadArticle(database)
            val measured = measure(database, bodyTag = STATS_CONTENT_TAG, extraTag = READING_HEATMAP_TAG)
            assertTrue("Title under status bar: $measured", measured.titleTop >= measured.statusInsetTop)
            assertTrue(
                "Content list overlaps navigation bar: $measured",
                measured.bodyBottom <= measured.decorHeight - measured.navInsetBottom,
            )
            assertTrue("Heatmap not rendered: $measured", measured.extraFound)
        } finally {
            database.close()
        }
    }

    private fun newDatabase(): AndroidDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        AndroidDatabase::class.java,
    ).allowMainThreadQueries().build()

    private fun seedOneReadArticle(database: AndroidDatabase) = runBlocking {
        // currentAccountId mặc định = 1 khi DataStore chưa có khoá.
        database.accountDao().insert(Account(id = ACCOUNT_ID, name = "Test", type = AccountType.Local))
        database.groupDao().insert(Group(id = GROUP_ID, name = "Group", accountId = ACCOUNT_ID))
        database.feedDao().insert(
            Feed(id = FEED_ID, name = "Feed", url = "https://example.com/feed", groupId = GROUP_ID, accountId = ACCOUNT_ID)
        )
        database.articleDao().insert(
            Article(
                id = "read-1",
                date = Date(),
                title = "Read article",
                rawDescription = "",
                shortDescription = "",
                link = "https://example.com/read-1",
                feedId = FEED_ID,
                accountId = ACCOUNT_ID,
                isUnread = false,
                readAt = System.currentTimeMillis(),
            )
        )
    }

    private fun measure(database: AndroidDatabase, bodyTag: String, extraTag: String?): Measured {
        var result: Measured? = null
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val viewModel = ReadingStatisticsViewModel(
                    context = activity.application,
                    articleDao = database.articleDao(),
                    calendar = ReadingStatisticsCalendar(),
                    ioDispatcher = Dispatchers.Unconfined,
                )
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                activity.setContentView(
                    ComposeView(activity).apply {
                        setContent {
                            ReadingStatisticsPage(
                                navController = NavHostController(activity),
                                viewModel = viewModel,
                            )
                        }
                    }
                )
            }

            val deadline = System.currentTimeMillis() + TEST_TIMEOUT_MS
            while (result == null && System.currentTimeMillis() < deadline) {
                scenario.onActivity { activity ->
                    val decor = activity.window.decorView
                    val insets = ViewCompat.getRootWindowInsets(decor)
                    val topInset = insets?.getInsets(
                        WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
                    )?.top ?: 0
                    val bottomInset = insets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
                    val root = findComposeRoot(decor)
                    val title = root?.findTagBounds(STATS_PAGE_TITLE_TAG)
                    val body = root?.findTagBounds(bodyTag)
                    val extraFound = extraTag == null || root?.findTagBounds(extraTag) != null
                    if (topInset > 0 && bottomInset > 0 && title != null && body != null && extraFound) {
                        result = Measured(title.top, topInset, body.bottom, decor.height, bottomInset, extraFound)
                    }
                }
                if (result == null) Thread.sleep(POLL_DELAY_MS)
            }
        }
        return result ?: throw AssertionError("Statistics $bodyTag/${extraTag.orEmpty()} or system insets not observed within deadline")
    }

    private fun findComposeRoot(view: View): ViewRootForTest? {
        if (view is ViewRootForTest) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) findComposeRoot(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
    private fun ViewRootForTest.findTagBounds(tag: String): android.graphics.Rect? {
        val stack = ArrayDeque<SemanticsNode>().apply { add(semanticsOwner.unmergedRootSemanticsNode) }
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (node.config.getOrNull(SemanticsProperties.TestTag) == tag) {
                val b = node.boundsInWindow
                return android.graphics.Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
            }
            stack.addAll(node.children)
        }
        return null
    }

    private companion object {
        const val TEST_TIMEOUT_MS = 10_000L
        const val POLL_DELAY_MS = 50L
        const val ACCOUNT_ID = 1
        const val GROUP_ID = "group-1"
        const val FEED_ID = "feed-1"
    }
}
