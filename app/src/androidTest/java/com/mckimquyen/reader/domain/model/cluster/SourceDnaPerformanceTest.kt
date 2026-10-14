package com.mckimquyen.reader.domain.model.cluster

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.infrastructure.ai.clustering.StoryClusteringEngine
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class SourceDnaPerformanceTest {
    @Test fun twentyArticleAnalysisStaysUnder200MsAfterWarmup_andUsesAtMost190Comparisons() {
        val engine = StoryClusteringEngine()
        val articles = (1..ARTICLE_COUNT).map(::article)
        var comparisons = 0
        val similarity: (ArticleWithFeed, ArticleWithFeed) -> Float = { first, second ->
            comparisons++
            engine.calculateSimilarity(first, second)
        }

        val coldNanos = measure { SourceDnaAnalyzer.analyze(articles, similarity) }
        val coldComparisons = comparisons
        SourceDnaAnalyzer.analyze(articles, similarity)
        val warm = (1..WARM_RUNS).map { measure { SourceDnaAnalyzer.analyze(articles, similarity) } }.sorted()
        val medianMillis = warm[warm.size / 2] / NANOS_PER_MILLI
        val worstMillis = warm.last() / NANOS_PER_MILLI
        println("SOURCE_DNA_PERF cold=${coldNanos / NANOS_PER_MILLI}ms warmMedian=${medianMillis}ms warmWorst=${worstMillis}ms comparisons=$coldComparisons")

        assertTrue("comparisons=$coldComparisons", coldComparisons <= MAX_COMPARISONS)
        assertTrue("warm median ${medianMillis}ms", medianMillis < MAX_WARM_MEDIAN_MILLIS)
    }

    private fun measure(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return System.nanoTime() - start
    }

    private fun article(index: Int): ArticleWithFeed {
        val feed = Feed(id = "feed_$index", name = "Feed $index", url = "https://feed$index.example/rss", groupId = "group", accountId = 1)
        return ArticleWithFeed(
            article = Article(
                id = "article_$index",
                date = Date(BASE_TIME + index * ONE_MINUTE_MILLIS),
                title = "Prime minister opens national railway project $index in Hanoi",
                rawDescription = "Officials announce railway construction phase $index with regional transport details.",
                shortDescription = "Officials announce railway construction phase $index with regional transport details.",
                link = "https://feed$index.example/article_$index",
                feedId = feed.id,
                accountId = 1,
            ),
            feed = feed,
        )
    }

    private companion object {
        const val ARTICLE_COUNT = 20
        const val WARM_RUNS = 10
        const val MAX_COMPARISONS = 190
        const val MAX_WARM_MEDIAN_MILLIS = 200L
        const val BASE_TIME = 1_800_000_000_000L
        const val ONE_MINUTE_MILLIS = 60_000L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
