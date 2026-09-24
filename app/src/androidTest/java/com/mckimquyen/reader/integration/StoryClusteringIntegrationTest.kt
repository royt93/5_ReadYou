package com.mckimquyen.reader.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.domain.repository.GroupDao
import com.mckimquyen.reader.infrastructure.ai.clustering.StoryClusteringEngine
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * End-to-end integration test for KNOW-04:
 * DB -> ArticleDao.queryRecentArticlesWithFeed -> StoryClusteringEngine (Cache + Inverted Index + Real Similarity).
 */
@RunWith(AndroidJUnit4::class)
class StoryClusteringIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: AndroidDatabase
    private lateinit var groupDao: GroupDao
    private lateinit var feedDao: FeedDao
    private lateinit var articleDao: ArticleDao
    private lateinit var clusteringEngine: StoryClusteringEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        groupDao = database.groupDao()
        feedDao = database.feedDao()
        articleDao = database.articleDao()
        clusteringEngine = StoryClusteringEngine()
        clusteringEngine.clearCache()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun databaseToClusteringPipeline_fullEndToEndLifecycle() = runBlocking {
        // 1. Setup Groups and Feeds
        val group = Group(id = "g_tech", name = "Tech News", accountId = 1)
        groupDao.insert(group)

        val feed1 = Feed(
            id = "f_vnexpress",
            name = "VnExpress Số Hóa",
            url = "https://vnexpress.net/rss/so-hoa.rss",
            groupId = group.id,
            accountId = 1,
            isFullContent = false,
        )
        val feed2 = Feed(
            id = "f_tuoitre",
            name = "Tuổi Trẻ Công Nghệ",
            url = "https://tuoitre.vn/rss/nhip-song-so.rss",
            groupId = group.id,
            accountId = 1,
            isFullContent = false,
        )
        val feed3 = Feed(
            id = "f_bbc",
            name = "BBC World Tech",
            url = "https://bbc.com/tech.rss",
            groupId = group.id,
            accountId = 1,
            isFullContent = false,
        )
        feedDao.insert(feed1, feed2, feed3)

        val now = System.currentTimeMillis()

        // 2. Insert duplicate breaking news articles across multiple feeds into DB
        val article1 = Article(
            id = "art_rail_1",
            title = "Thủ tướng phát lệnh khởi công tuyến đường sắt cao tốc Bắc Nam",
            rawDescription = "Dự án giao thông thế kỷ chính thức được triển khai tại Hà Nội với quy mô lớn.",
            shortDescription = "Dự án giao thông thế kỷ chính thức được triển khai tại Hà Nội với quy mô lớn.",
            link = "https://vnexpress.net/art1",
            feedId = feed1.id,
            accountId = 1,
            date = Date(now),
        )
        val article2 = Article(
            id = "art_rail_2",
            title = "Chính thức khởi công đường sắt cao tốc Bắc Nam với quy mô lớn",
            rawDescription = "Lễ khởi công đường sắt cao tốc Bắc Nam diễn ra trọng thể với sự tham gia của Thủ tướng.",
            shortDescription = "Lễ khởi công đường sắt cao tốc Bắc Nam diễn ra trọng thể với sự tham gia của Thủ tướng.",
            link = "https://tuoitre.vn/art2",
            feedId = feed2.id,
            accountId = 1,
            date = Date(now - TimeUnit.HOURS.toMillis(1)),
        )
        val article3 = Article(
            id = "art_unrelated",
            title = "Thị trường vàng trong nước ghi nhận mức giảm kỷ lục trong phiên sáng",
            rawDescription = "Giá vàng miếng SJC bất ngờ quay đầu lao dốc mạnh sau chuỗi ngày tăng nóng.",
            shortDescription = "Giá vàng miếng SJC bất ngờ quay đầu lao dốc mạnh sau chuỗi ngày tăng nóng.",
            link = "https://bbc.com/art3",
            feedId = feed3.id,
            accountId = 1,
            date = Date(now),
        )
        articleDao.insert(article1, article2, article3)

        // 3. Query recent articles via ArticleDao exactly as HomeViewModel does
        val queriedArticles = articleDao.queryRecentArticlesWithFeed(accountId = 1, limit = 150)
        assertEquals(3, queriedArticles.size)

        // 4. Run StoryClusteringEngine on DB results (Cold cache run)
        assertEquals(0, clusteringEngine.cacheSize())
        val result1 = clusteringEngine.cluster(queriedArticles)

        assertEquals("Should form exactly 1 cluster for high-speed rail", 1, result1.clusters.size)
        val cluster = result1.clusters.first()
        assertEquals(2, cluster.articleCount)
        assertEquals(2, cluster.sourceCount)
        assertTrue(cluster.isMultiSource)
        assertTrue("Cluster similarityScore must be >= threshold (0.45)", cluster.similarityScore >= 0.45f)
        assertFalse("Cluster similarityScore must not be default 0.85f", cluster.similarityScore == 0.85f)
        assertEquals(2, result1.clusters.first().articles.size)
        assertEquals(1, result1.nonLeadIds.size)
        assertFalse("Unrelated article must not be grouped into nonLeadIds", result1.nonLeadIds.contains(article3.id))

        // Cache should now hold features for all 3 articles
        assertEquals(3, clusteringEngine.cacheSize())

        // 5. Subsequent clustering run (Warm cache run): must reuse cached features and produce identical results
        val result2 = clusteringEngine.cluster(queriedArticles)
        assertEquals(3, clusteringEngine.cacheSize()) // No extra cache allocation
        assertEquals(result1.clusters.size, result2.clusters.size)
        assertEquals(result1.clusters.first().similarityScore, result2.clusters.first().similarityScore, 0.0001f)
    }
}
