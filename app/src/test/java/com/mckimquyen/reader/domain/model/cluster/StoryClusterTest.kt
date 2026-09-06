package com.mckimquyen.reader.domain.model.cluster

import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class StoryClusterTest {

    private val feed1 = Feed(id = "feed1", name = "Reuters", url = "https://reuters.com", groupId = "group_1", accountId = 1)
    private val feed2 = Feed(id = "feed2", name = "Bloomberg", url = "https://bloomberg.com", groupId = "group_1", accountId = 1)

    private fun createArticleWithFeed(id: String, feed: Feed, date: Date = Date()): ArticleWithFeed {
        return ArticleWithFeed(
            article = Article(
                id = id,
                title = "Test Article $id",
                link = "https://example.com/$id",
                rawDescription = "",
                shortDescription = "",
                feedId = feed.id,
                accountId = 1,
                date = date,
            ),
            feed = feed,
        )
    }

    @Test
    fun storyCluster_singleSource_isMultiSourceFalse() {
        val a1 = createArticleWithFeed("1", feed1)
        val a2 = createArticleWithFeed("2", feed1)

        val cluster = StoryCluster(
            id = "cluster_1",
            title = "Single Source Story",
            leadArticle = a1,
            articles = listOf(a1, a2),
        )

        assertEquals(1, cluster.sourceCount)
        assertEquals(2, cluster.articleCount)
        assertFalse(cluster.isMultiSource)
    }

    @Test
    fun storyCluster_multipleSources_isMultiSourceTrue() {
        val a1 = createArticleWithFeed("1", feed1)
        val a2 = createArticleWithFeed("2", feed2)

        val cluster = StoryCluster(
            id = "cluster_1",
            title = "Multi Source Breaking News",
            leadArticle = a1,
            articles = listOf(a1, a2),
        )

        assertEquals(2, cluster.sourceCount)
        assertEquals(2, cluster.articleCount)
        assertTrue(cluster.isMultiSource)
    }

    @Test
    fun storyClusterResult_empty_defaultsAreEmpty() {
        val empty = StoryClusterResult.EMPTY
        assertTrue(empty.clusters.isEmpty())
        assertTrue(empty.leadClusterMap.isEmpty())
        assertTrue(empty.nonLeadIds.isEmpty())
    }
}
