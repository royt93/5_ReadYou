package com.mckimquyen.reader.infrastructure.articlehistory

import com.mckimquyen.reader.domain.model.radar.ArticleRadarState
import com.mckimquyen.reader.domain.model.radar.ArticleSnapshot
import com.mckimquyen.reader.domain.radar.RemovalUpdate
import com.mckimquyen.reader.domain.radar.RetractionDetector
import com.mckimquyen.reader.domain.radar.RetractionVerdict
import com.mckimquyen.reader.domain.radar.SnapshotHashes
import com.mckimquyen.reader.domain.repository.ArticleRadarDao
import javax.inject.Inject
import javax.inject.Singleton

/** Stores article snapshots and edit/removal flags (EXC-10). Callers decide whether the feature is enabled. */
@Singleton
class ArticleHistoryRepository @Inject constructor(
    private val dao: ArticleRadarDao,
) {

    /** Compares incoming text to the newest snapshot; stores a baseline or a new version when needed. */
    suspend fun record(articleId: String, title: String, html: String, now: Long = System.currentTimeMillis()): RetractionVerdict {
        val incoming = RetractionDetector.snapshotOf(title, html)
        val previous = dao.latestHashes(articleId)?.let { SnapshotHashes(it.titleHash, it.contentHash) }
        val verdict = RetractionDetector.evaluate(previous, incoming)
        if (verdict == RetractionVerdict.UNCHANGED) return verdict
        dao.insertSnapshot(
            ArticleSnapshot(
                articleId = articleId,
                capturedAt = now,
                titleHash = incoming.titleHash,
                contentHash = incoming.contentHash,
                title = incoming.title,
                contentText = incoming.contentText,
            )
        )
        if (verdict == RetractionVerdict.EDITED) {
            val state = dao.state(articleId) ?: ArticleRadarState(articleId)
            dao.upsertState(state.copy(editCount = state.editCount + 1, lastEditedAt = now))
        }
        return verdict
    }

    suspend fun applyRemoval(updates: List<RemovalUpdate>, now: Long = System.currentTimeMillis()) {
        updates.forEach { update ->
            val state = dao.state(update.articleId) ?: ArticleRadarState(update.articleId)
            dao.upsertState(
                state.copy(
                    missedSyncs = update.missedSyncs,
                    removedAt = if (update.removed) state.removedAt ?: now else null,
                )
            )
        }
    }

    suspend fun purge(now: Long = System.currentTimeMillis()) {
        dao.trimSnapshots(KEEP_LATEST_SNAPSHOTS)
        val cutoff = now - REMOVED_RETENTION_DAYS * MILLIS_PER_DAY
        dao.deleteSnapshotsOfLongRemoved(cutoff)
        dao.deleteLongRemovedState(cutoff)
    }

    companion object {
        /** Besides the original snapshot, how many newest versions are kept per article. */
        const val KEEP_LATEST_SNAPSHOTS = 4
        const val REMOVED_RETENTION_DAYS = 90L
        private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}
