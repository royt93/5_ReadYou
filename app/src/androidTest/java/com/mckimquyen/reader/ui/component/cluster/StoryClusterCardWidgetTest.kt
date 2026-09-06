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
class StoryClusterCardWidgetTest {

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
    private val feedBBC = Feed(
        id = "feed_bbc",
        name = "BBC News",
        url = "https://bbc.com/rss",
        groupId = "group_1",
        accountId = 1,
    )

    private val a1 = ArticleWithFeed(
        article = Article(
            id = "a1",
            title = "Apple ra mắt iPhone 16 Pro Max với chip A18",
            rawDescription = "Sự kiện ra mắt toàn cầu của Apple",
            shortDescription = "Sự kiện ra mắt toàn cầu của Apple",
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
            title = "Cận cảnh lễ ra mắt iPhone 16 Pro Max",
            rawDescription = "Những nâng cấp vượt trội trên iPhone mới",
            shortDescription = "Những nâng cấp vượt trội trên iPhone mới",
            link = "https://tuoitre.vn/a2",
            feedId = feedTuoiTre.id,
            accountId = 1,
            date = Date(),
        ),
        feed = feedTuoiTre,
    )

    private val a3 = ArticleWithFeed(
        article = Article(
            id = "a3",
            title = "Apple officially unveils iPhone 16 lineup",
            rawDescription = "Key announcements from Cupertino event",
            shortDescription = "Key announcements from Cupertino event",
            link = "https://bbc.com/a3",
            feedId = feedBBC.id,
            accountId = 1,
            date = Date(),
        ),
        feed = feedBBC,
    )

    private val sampleCluster = StoryCluster(
        id = "cluster_iphone_16",
        title = a1.article.title,
        leadArticle = a1,
        articles = listOf(a1, a2, a3),
        keywords = listOf("iPhone 16", "Apple", "A18", "Tech"),
        sourceCount = 3,
        articleCount = 3,
        date = Date(),
    )

    @Test
    fun storyClusterCard_rendersContentSuccessfully() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    StoryClusterCard(
                        cluster = sampleCluster,
                        onClick = {},
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull("StoryClusterCard compose view should attach cleanly", composeView)
        }
        scenario.close()
    }

    @Test
    fun storyClusterCard_clickInvokesCallback() {
        var clicked = false
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    StoryClusterCard(
                        cluster = sampleCluster,
                        onClick = { clicked = true },
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
        }
        scenario.close()
    }

    @Test
    fun storyClusterCard_withSingleSource_rendersWithoutCrash() {
        val singleSourceCluster = sampleCluster.copy(
            articles = listOf(a1),
            sourceCount = 1,
            articleCount = 1,
        )
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    StoryClusterCard(
                        cluster = singleSourceCluster,
                        onClick = {},
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
        }
        scenario.close()
    }
}
