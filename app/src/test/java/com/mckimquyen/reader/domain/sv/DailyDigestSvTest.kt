package com.mckimquyen.reader.domain.sv

import com.mckimquyen.reader.domain.model.article.Article
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
        coEvery { articleDao.queryLatestUnread(1, any()) } returns emptyList()

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
        coEvery { articleDao.queryLatestUnread(1, any()) } returns listOf(article)
        coEvery { digestService.generate(any()) } returns DailyDigestContent(
            "Brief", listOf(DailyDigestSection("Top", listOf(DailyDigestItem("Story", listOf("a1"))))),
        )
        val saved = slot<DailyDigest>()
        coEvery { digestDao.insert(capture(saved)) } returns Unit

        val result = service.generate(1)

        assertEquals("Brief", result?.headline)
        assertEquals("[\"a1\"]", saved.captured.sourceArticleIdsJson)
    }
}
