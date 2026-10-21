package com.mckimquyen.reader.domain.sv

import com.mckimquyen.reader.domain.model.article.ArticleBody
import com.mckimquyen.reader.domain.model.article.toListItem
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleHighlights
import com.mckimquyen.reader.domain.model.digest.DAILY_DIGEST_MAX_ARTICLES
import com.mckimquyen.reader.infrastructure.ai.ArticleHighlightsExtractor
import com.mckimquyen.reader.domain.model.digest.DailyDigest
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.DailyDigestDao
import com.mckimquyen.reader.infrastructure.ai.DailyDigestContent
import com.mckimquyen.reader.infrastructure.ai.DailyDigestItem
import com.mckimquyen.reader.infrastructure.ai.DailyDigestSection
import com.mckimquyen.reader.infrastructure.ai.DailyDigestService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

class DailyDigestSvTest {
    private val articleDao = mockk<ArticleDao>()
    private val digestDao = mockk<DailyDigestDao>(relaxed = true)
    private val digestService = mockk<DailyDigestService>()
    private val service = DailyDigestSv(articleDao, digestDao, digestService, Dispatchers.Unconfined)

    @Test
    fun generate_noEligibleArticles_returnsNullWithoutCallingGenerator() = runBlocking {
        coEvery { articleDao.queryUnreadArticleList(1, any()) } returns emptyList()

        assertNull(service.generate(1))
        coVerify(exactly = 0) { digestService.generate(any()) }
        coVerify(exactly = 0) { digestDao.insert(any()) }
    }

    @Test
    fun generate_success_persistsDigestWithValidatedSourceIds() = runBlocking {
        val article = Article(
            id = "a1", date = Date(100L), title = "Title", rawDescription = "Body", shortDescription = "Body",
            link = "https://example.test/a1", feedId = "f1", accountId = 1, isUnread = true,
        )
        coEvery { articleDao.queryUnreadArticleList(1, any()) } returns listOf(article.toListItem())
        coEvery { articleDao.queryArticleBody(1, article.id) } returns ArticleBody(article.id, article.rawDescription, article.fullContent)
        coEvery { digestService.generate(any()) } returns DailyDigestContent(
            "Brief", listOf(DailyDigestSection("Top", listOf(DailyDigestItem("Story", listOf("a1"))))),
        )
        val saved = slot<DailyDigest>()
        coEvery { digestDao.insert(capture(saved)) } returns Unit

        val result = service.generate(1)

        assertEquals("Brief", result?.headline)
        coVerify { digestService.generate(match { it.single().content == "Body" }) }
        assertEquals("[\"a1\"]", saved.captured.sourceArticleIdsJson)
    }
    private fun candidate(id: String, at: Long, summary: String = "") = Article(
        id = id, date = Date(at), title = "Title $id", rawDescription = "", shortDescription = "",
        link = "https://example.test/$id", feedId = "f", accountId = 1, aiSummary = summary,
    )

    @Test fun generate_skipsBlankCandidatesAndFetchesOnlyUntilFull() = runBlocking {
        val candidates = (0..DAILY_DIGEST_MAX_ARTICLES + 2).map { candidate("a$it", -it.toLong()) }
        coEvery { articleDao.queryUnreadArticleList(1, any()) } returns candidates.map { it.toListItem() }
        candidates.forEachIndexed { index, a ->
            coEvery { articleDao.queryArticleBody(1, a.id) } returns ArticleBody(a.id, if (index == 0) "" else "Body ${a.id}", null)
        }
        coEvery { digestService.generate(any()) } returns DailyDigestContent("Brief", emptyList())
        service.generate(1)
        coVerify { digestService.generate(match { it.size == DAILY_DIGEST_MAX_ARTICLES && it.first().id == "a1" && it.last().id == "a20" }) }
        coVerify(exactly = 0) { articleDao.queryArticleBody(1, "a21") }
        coVerify(exactly = 0) { articleDao.queryArticleBody(1, "a22") }
    }

    @Test fun generate_summaryWinsWithoutReadingBody() = runBlocking {
        val a = candidate("summary", 1, ArticleHighlightsExtractor.serialize(ArticleHighlights(tldr = "Summary only")))
        coEvery { articleDao.queryUnreadArticleList(1, any()) } returns listOf(a.toListItem())
        coEvery { digestService.generate(any()) } returns DailyDigestContent("Brief", emptyList())
        service.generate(1)
        coVerify { digestService.generate(match { it.single().content == "Summary only" }) }
        coVerify(exactly = 0) { articleDao.queryArticleBody(any(), any()) }
    }

}
