package com.mckimquyen.reader.domain.sv

import com.google.gson.Gson
import com.mckimquyen.reader.domain.model.digest.DailyDigest
import com.mckimquyen.reader.domain.model.digest.DigestArticleSelector
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.DailyDigestDao
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import com.mckimquyen.reader.infrastructure.ai.DailyDigestService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DailyDigestSv @Inject constructor(
    private val articleDao: ArticleDao,
    private val digestDao: DailyDigestDao,
    private val digestService: DailyDigestService,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun generate(accountId: Int): DailyDigest? = withContext(ioDispatcher) {
        val sources = DigestArticleSelector.select(articleDao.queryLatestUnread(accountId, MAX_CANDIDATE_ARTICLES))
        if (sources.isEmpty()) return@withContext null
        val content = digestService.generate(sources)
        DailyDigest(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            headline = content.headline,
            contentJson = Gson().toJson(content),
            sourceArticleIdsJson = Gson().toJson(content.sections.flatMap { section -> section.items.flatMap { it.sourceIds } }.distinct()),
            accountId = accountId,
        ).also { digestDao.insert(it) }
    }

    suspend fun history(accountId: Int): List<DailyDigest> = withContext(ioDispatcher) {
        digestDao.queryRecent(accountId)
    }

    companion object {
        private const val MAX_CANDIDATE_ARTICLES = 100
    }
}
