package com.mckimquyen.reader.infrastructure.archivequery

import com.mckimquyen.reader.domain.archivequery.ArchiveEvidence
import com.mckimquyen.reader.domain.archivequery.ArchiveMonth
import com.mckimquyen.reader.domain.archivequery.ArchiveMonthWithEvidence
import com.mckimquyen.reader.domain.archivequery.ArchivePerspectiveShift
import com.mckimquyen.reader.domain.archivequery.ArchiveQueryRange
import com.mckimquyen.reader.domain.archivequery.ArchiveQueryResult
import com.mckimquyen.reader.domain.archivequery.EvidenceExtractor
import com.mckimquyen.reader.domain.archivequery.ParsedArchiveQuery
import com.mckimquyen.reader.domain.archivequery.PerspectiveShiftDetector
import com.mckimquyen.reader.domain.archivequery.RankedArchiveArticle
import com.mckimquyen.reader.domain.archivequery.TemporalGrouper
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.infrastructure.ai.search.SemanticSearchEngine
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * EXC-11: scans account-scoped article metadata page by page, ranks it with the existing semantic engine, keeps only a
 * month counter and the top candidates per month, then reads at most [MAX_BODY_READS] bodies to quote verbatim.
 * Nothing leaves the device and no body is read before an article has been selected.
 */
class ArchiveQueryRepository @Inject constructor(
    private val articleDao: ArticleDao,
    private val searchEngine: SemanticSearchEngine,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun search(
        accountId: Int,
        parsed: ParsedArchiveQuery,
        range: ArchiveQueryRange,
        now: Instant,
        zone: ZoneId,
    ): ArchiveQueryResult = withContext(ioDispatcher) {
        val topic = parsed.topic.trim()
        val grouper = TemporalGrouper()
        if (topic.isNotEmpty()) scanMetadata(accountId, topic, range, zone, grouper)
        val buckets = grouper.result()

        val topicTokens = searchEngine.tokenize(topic)
        val oversized = mutableListOf<String>()
        var bodyReads = 0
        val months = monthsIn(range, zone).map { yearMonth ->
            val bucket = buckets[yearMonth] ?: ArchiveMonth(yearMonth.year, yearMonth.monthValue, 0, emptyList())
            val evidence = mutableListOf<ArchiveEvidence>()
            for (candidate in bucket.topCandidates) {
                if (bodyReads >= MAX_BODY_READS) break
                coroutineContext.ensureActive()
                bodyReads++
                when (val read = readEvidence(accountId, candidate, topicTokens)) {
                    is BodyRead.Quote -> evidence += read.evidence
                    BodyRead.TooLarge -> oversized += candidate.articleId
                    BodyRead.Unavailable -> Unit
                }
            }
            ArchiveMonthWithEvidence(bucket, evidence)
        }

        val shifts = months.zipWithNext().mapNotNull { (earlier, later) -> PerspectiveShiftDetector.detect(earlier, later) }
        ArchiveQueryResult(
            range = range,
            months = months,
            shifts = shifts,
            totalRelevantArticles = months.sumOf { it.month.articleCount },
            oversizedArticleIds = oversized,
        )
    }

    private suspend fun scanMetadata(
        accountId: Int,
        topic: String,
        range: ArchiveQueryRange,
        zone: ZoneId,
        grouper: TemporalGrouper,
    ) {
        var beforeDate: Long? = null
        var beforeId: String? = null
        while (true) {
            coroutineContext.ensureActive()
            val page = articleDao.queryArticleMetadataPage(
                accountId = accountId,
                sinceMillis = range.startInclusive.toEpochMilli(),
                untilMillis = range.endExclusive.toEpochMilli(),
                beforeDate = beforeDate,
                beforeId = beforeId,
                limit = PAGE_SIZE,
            )
            if (page.isEmpty()) return
            // limit = page.size so the engine's default top-50 cut never hides an article before month counting.
            val candidates = page.map { it.toArticleWithFeed() }
            val queryTokens = searchEngine.tokenize(topic)
            val queryConcepts = searchEngine.detectConceptsStrict(topic)
            val ranked = searchEngine.rank(topic, candidates, limit = page.size)
                .filter { result ->
                    val article = result.articleWithFeed.article
                    val text = "${article.title} ${article.shortDescription}"
                    val sharesTopicToken = queryTokens.any { token -> token in searchEngine.tokenize(text) }
                    val sharesWholeWordConcept = queryConcepts.any { concept ->
                        concept in searchEngine.detectConceptsStrict(text)
                    }
                    sharesTopicToken || sharesWholeWordConcept
                }
            for (result in ranked) {
                val article = result.articleWithFeed.article
                val date = article.date.toInstant()
                grouper.add(
                    YearMonth.from(date.atZone(zone)),
                    RankedArchiveArticle(
                        articleId = article.id,
                        date = date,
                        title = article.title,
                        feedName = result.articleWithFeed.feed.name,
                        score = result.score,
                        shortDescription = article.shortDescription,
                    ),
                )
            }
            val last = page.last().article
            beforeDate = last.date.time
            beforeId = last.id
            if (page.size < PAGE_SIZE) return
        }
    }

    private sealed interface BodyRead {
        data class Quote(val evidence: ArchiveEvidence) : BodyRead
        data object TooLarge : BodyRead
        data object Unavailable : BodyRead
    }

    private suspend fun readEvidence(accountId: Int, candidate: RankedArchiveArticle, topicTokens: Set<String>): BodyRead =
        try {
            val lengths = articleDao.queryArticleBodyLengths(accountId, candidate.articleId)
            when {
                lengths == null -> BodyRead.Unavailable
                // queryArticleBody loads both columns, so the sum is what actually lands in memory.
                lengths.rawBytes.toLong() + (lengths.fullBytes ?: 0) > MAX_BODY_BYTES -> BodyRead.TooLarge
                else -> {
                    val body = articleDao.queryArticleBody(accountId, candidate.articleId)
                    val html = body?.fullContent?.takeIf { it.isNotBlank() } ?: body?.rawDescription.orEmpty()
                    EvidenceExtractor.extract(candidate, html, topicTokens)
                        ?.let { BodyRead.Quote(it) } ?: BodyRead.Unavailable
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A deleted or unreadable article only loses its own quote; it must not fail the whole search.
            BodyRead.Unavailable
        }

    /** Every calendar month touched by the range, oldest first, so empty months stay visible. */
    private fun monthsIn(range: ArchiveQueryRange, zone: ZoneId): List<YearMonth> {
        if (!range.endExclusive.isAfter(range.startInclusive)) return emptyList()
        val first = YearMonth.from(range.startInclusive.atZone(zone))
        val last = YearMonth.from(range.endExclusive.minusMillis(1).atZone(zone))
        return generateSequence(first) { it.plusMonths(1) }.takeWhile { !it.isAfter(last) }.toList()
    }

    companion object {
        const val PAGE_SIZE = 200
        const val MAX_BODY_READS = 36
        const val MAX_BODY_BYTES = 1_048_576
    }
}
