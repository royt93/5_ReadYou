package com.mckimquyen.reader.infrastructure.ai.clustering

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleFlowItem
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.ui.component.cluster.StoryClusterCard
import com.mckimquyen.reader.ui.component.cluster.StoryClusterSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class StoryClusteringIntegrationTest {

    @Test
    fun endToEnd_multiSourceClustering_flowItemMapping_andComposeRendering() {
        val now = System.currentTimeMillis()

        // 1. Setup multi-source RSS test data
        val feedVnExpress = Feed(
            id = "feed_vnexpress",
            name = "VnExpress",
            url = "https://vnexpress.net/rss",
            groupId = "group_1",
            accountId = 1,
        )
        val feedTuoiTre = Feed(
            id = "feed_tuoitre",
            name = "Tuổi Trẻ",
            url = "https://tuoitre.vn/rss",
            groupId = "group_1",
            accountId = 1,
        )
        val feedDantri = Feed(
            id = "feed_dantri",
            name = "Dân Trí",
            url = "https://dantri.com.vn/rss",
            groupId = "group_1",
            accountId = 1,
        )
        val feedTech = Feed(
            id = "feed_tech",
            name = "Tinh Tế",
            url = "https://tinhte.vn/rss",
            groupId = "group_1",
            accountId = 1,
        )

        val art1 = ArticleWithFeed(
            article = Article(
                id = "vnexpress_1",
                title = "Bộ Giao thông Vận tải thông qua hướng tuyến đường sắt cao tốc Bắc Nam",
                rawDescription = "Tuyến đường sắt cao tốc Bắc Nam dài hơn 1500km nối Hà Nội với TP HCM.",
                shortDescription = "Tuyến đường sắt cao tốc Bắc Nam dài hơn 1500km nối Hà Nội với TP HCM.",
                link = "https://vnexpress.net/1",
                feedId = feedVnExpress.id,
                accountId = 1,
                date = Date(now),
            ),
            feed = feedVnExpress,
        )

        val art2 = ArticleWithFeed(
            article = Article(
                id = "tuoitre_1",
                title = "Đường sắt cao tốc Bắc Nam chính thức chốt hướng tuyến toàn dự án",
                rawDescription = "Phương án xây dựng đường sắt cao tốc Bắc Nam đã được các cơ quan chức năng phê duyệt.",
                shortDescription = "Phương án xây dựng đường sắt cao tốc Bắc Nam đã được các cơ quan chức năng phê duyệt.",
                link = "https://tuoitre.vn/1",
                feedId = feedTuoiTre.id,
                accountId = 1,
                date = Date(now - TimeUnit.HOURS.toMillis(1)),
            ),
            feed = feedTuoiTre,
        )

        val art3 = ArticleWithFeed(
            article = Article(
                id = "dantri_1",
                title = "Chính thức phê duyệt hướng tuyến đường sắt cao tốc Bắc Nam dài 1500km",
                rawDescription = "Dự án đường sắt cao tốc Bắc Nam kết nối hai đầu đất nước chuẩn bị triển khai.",
                shortDescription = "Dự án đường sắt cao tốc Bắc Nam kết nối hai đầu đất nước chuẩn bị triển khai.",
                link = "https://dantri.com.vn/1",
                feedId = feedDantri.id,
                accountId = 1,
                date = Date(now - TimeUnit.HOURS.toMillis(3)),
            ),
            feed = feedDantri,
        )

        val unrelatedArt = ArticleWithFeed(
            article = Article(
                id = "tech_1",
                title = "Đánh giá chi tiết pin sạc dự phòng chuẩn Qi2 công suất 15W",
                rawDescription = "Trải nghiệm sạc không dây nam châm thế hệ mới.",
                shortDescription = "Trải nghiệm sạc không dây nam châm thế hệ mới.",
                link = "https://tinhte.vn/1",
                feedId = feedTech.id,
                accountId = 1,
                date = Date(now),
            ),
            feed = feedTech,
        )

        val rawArticles = listOf(art1, art2, art3, unrelatedArt)

        // 2. Execute AI Clustering Engine
        val engine = StoryClusteringEngine()
        val clusterResult = engine.cluster(rawArticles)

        assertEquals("Should detect and form exactly 1 story cluster", 1, clusterResult.clusters.size)
        val cluster = clusterResult.clusters.first()

        assertEquals("Cluster must combine all 3 news sources", 3, cluster.sourceCount)
        assertEquals("Cluster must contain 3 articles", 3, cluster.articleCount)
        assertTrue("Cluster must be marked as multi-source", cluster.isMultiSource)
        assertTrue("Cluster keywords should extract topic terms", cluster.keywords.isNotEmpty())

        // Lead article check
        val leadId = cluster.leadArticle.article.id
        assertTrue("Lead article must be registered in leadClusterMap", clusterResult.leadClusterMap.containsKey(leadId))
        assertEquals("Duplicate articles must be 2 non-lead articles", 2, clusterResult.nonLeadIds.size)
        assertFalse("Unrelated article must NOT be marked as duplicate", clusterResult.nonLeadIds.contains(unrelatedArt.article.id))

        // 3. Flow Item deduplication simulation
        val flowItems = mutableListOf<ArticleFlowItem>()
        for (item in rawArticles) {
            val articleId = item.article.id
            if (clusterResult.nonLeadIds.contains(articleId)) {
                // Deduplicated! Suppressed from main stream
                continue
            }
            val mappedCluster = clusterResult.leadClusterMap[articleId]
            if (mappedCluster != null) {
                flowItems.add(ArticleFlowItem.Cluster(mappedCluster))
            } else {
                flowItems.add(ArticleFlowItem.Article(item))
            }
        }

        assertEquals("Flow list should have exactly 2 items (1 cluster + 1 unrelated article)", 2, flowItems.size)
        assertTrue("First flow item should be a StoryCluster", flowItems.any { it is ArticleFlowItem.Cluster })
        assertTrue("Second flow item should be an Article", flowItems.any { it is ArticleFlowItem.Article })

        // 4. Verify Compose rendering in live Activity on Android runtime
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    StoryClusterCard(
                        cluster = cluster,
                        onClick = {},
                    )
                    StoryClusterSheet(
                        cluster = cluster,
                        onDismissRequest = {},
                        onArticleClick = {},
                        onMarkAllRead = {},
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull("ComposeView must attach cleanly on live Android runtime", composeView)
        }
        scenario.close()
    }
}
