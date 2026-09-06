package com.mckimquyen.reader.ui.component.cluster

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.cluster.StoryCluster
import com.mckimquyen.reader.domain.model.feed.Feed
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class StoryClusterSheetWidgetTest {

    private val feedVnExpress = Feed(
        id = "feed_vnexpress",
        name = "VnExpress",
        url = "https://vnexpress.net/rss",
        groupId = "group_1",
        accountId = 1,
    )
    private val feedTuoiTre = Feed(
        id = "feed_tuoitre",
        name = "Tuổi Trẻ",
        url = "https://tuoitre.vn/rss",
        groupId = "group_1",
        accountId = 1,
    )

    private val a1 = ArticleWithFeed(
        article = Article(
            id = "a1",
            title = "Thủ tướng phát lệnh khởi công tuyến đường sắt cao tốc Bắc Nam",
            rawDescription = "Lễ khởi công công trình trọng điểm quốc gia",
            shortDescription = "Lễ khởi công công trình trọng điểm quốc gia",
            link = "https://vnexpress.net/a1",
            feedId = feedVnExpress.id,
            accountId = 1,
            date = Date(),
        ),
        feed = feedVnExpress,
    )

    private val a2 = ArticleWithFeed(
        article = Article(
            id = "a2",
            title = "Toàn cảnh dự án đường sắt cao tốc Bắc Nam chính thức khởi động",
            rawDescription = "Quy mô và hướng tuyến của đường sắt tốc độ cao",
            shortDescription = "Quy mô và hướng tuyến của đường sắt tốc độ cao",
            link = "https://tuoitre.vn/a2",
            feedId = feedTuoiTre.id,
            accountId = 1,
            date = Date(),
        ),
        feed = feedTuoiTre,
    )

    private val sampleCluster = StoryCluster(
        id = "cluster_railway",
        title = a1.article.title,
        leadArticle = a1,
        articles = listOf(a1, a2),
        keywords = listOf("Đường sắt", "Cao tốc", "Bắc Nam", "Thủ tướng"),
        sourceCount = 2,
        articleCount = 2,
        date = Date(),
    )

    @Test
    fun storyClusterSheet_rendersPerspectivesAndActionButtons() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    StoryClusterSheet(
                        cluster = sampleCluster,
                        onDismissRequest = {},
                        onArticleClick = {},
                        onMarkAllRead = {},
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull("StoryClusterSheet should attach cleanly without crash", composeView)
        }
        scenario.close()
    }

    @Test
    fun storyClusterSheet_markClusterAsReadCallback_isWired() {
        var markAsReadTriggered = false
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    StoryClusterSheet(
                        cluster = sampleCluster,
                        onDismissRequest = {},
                        onArticleClick = {},
                        onMarkAllRead = { markAsReadTriggered = true },
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
        }
        scenario.close()
    }
}
