package com.mckimquyen.reader.ui.page.radar

import com.mckimquyen.reader.domain.radar.DiffPart
import com.mckimquyen.reader.domain.radar.TextDiff
import com.mckimquyen.reader.domain.repository.ArticleRadarDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

data class ArticleDiff(
    val hasHistory: Boolean,
    val removed: Boolean,
    val editedAt: Long?,
    val titleDiff: List<DiffPart>,
    val bodyDiff: List<DiffPart>,
)

/** Reads only the first and newest snapshot (two bounded rows) and diffs them off the main thread. */
class ArticleDiffLoader(
    private val dao: ArticleRadarDao,
    private val dispatcher: CoroutineDispatcher,
) {
    suspend fun load(articleId: String): ArticleDiff = withContext(dispatcher) {
        val first = dao.firstSnapshot(articleId)
        val latest = dao.snapshots(articleId, 1).firstOrNull()
        val state = dao.state(articleId)
        if (first == null || latest == null) {
            return@withContext ArticleDiff(false, state?.removedAt != null, state?.lastEditedAt, emptyList(), emptyList())
        }
        ArticleDiff(
            hasHistory = true,
            removed = state?.removedAt != null,
            editedAt = state?.lastEditedAt,
            titleDiff = TextDiff.wordDiff(first.title, latest.title),
            bodyDiff = TextDiff.wordDiff(first.contentText, latest.contentText),
        )
    }
}
