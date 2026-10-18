package com.mckimquyen.reader.infrastructure.sync

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mckimquyen.reader.domain.model.article.ArticleReadPosition
import com.mckimquyen.reader.domain.model.article.ReadPositionConflictResolver
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.ArticleReadPositionDao
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.infrastructure.backup.WebDavBackupManager
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString.Companion.encodeUtf8
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Portable position record: excludes per-device articleId and accountId. */
data class WebDavPositionRecord(
    val feedUrl: String,
    val articleLink: String,
    val progress: Float,
    val lastReadTime: Long,
)

@Singleton
class WebDavReadPositionSyncer @Inject constructor(
    private val webDavBackupManager: WebDavBackupManager,
    private val readPositionDao: ArticleReadPositionDao,
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    private val okHttpClient: OkHttpClient,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun syncPositions(accountId: Int): Result<Unit> = withContext(ioDispatcher) {
        val config = webDavBackupManager.getConfig()
        if (!config.isConfigured) {
            return@withContext Result.success(Unit)
        }

        try {
            val fileUrl = buildPositionsFileUrl(config.serverUrl)
            val authHeader = "Basic " + "${config.username}:${config.password}".encodeUtf8().base64().trim()

            // Snapshot local state before any writes; merge must compare same-cycle local and remote data.
            val localPositions = readPositionDao.getAllPositions(accountId)
            val remoteRecords = fetchRemoteRecords(fileUrl, authHeader)
            val mergedForRemote = merge(localPositions, remoteRecords)

            // Persist remote winners locally, keyed back to this device's article IDs.
            for (record in remoteRecords) {
                val matchingFeeds = feedDao.queryByLink(accountId, record.feedUrl)
                for (feed in matchingFeeds) {
                    val localArticle = articleDao.queryArticleByLink(record.articleLink, feed.id, accountId)
                        ?: continue
                    val localPosition = localPositions.firstOrNull { it.articleId == localArticle.id }
                    val remoteCandidate = ArticleReadPosition(
                        articleId = localArticle.id,
                        accountId = accountId,
                        feedUrl = record.feedUrl,
                        articleLink = record.articleLink,
                        progress = record.progress,
                        lastReadTime = record.lastReadTime,
                        isSynced = true,
                    )
                    val winner = ReadPositionConflictResolver.resolve(localPosition, remoteCandidate) ?: remoteCandidate
                    readPositionDao.upsertPosition(winner.copy(articleId = localArticle.id, accountId = accountId, isSynced = false))
                }
            }

            // Upload complete merge. Only mark local rows synced after successful PUT.
            uploadRecords(fileUrl, authHeader, mergedForRemote)
            readPositionDao.markAllSynced(accountId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchRemoteRecords(fileUrl: String, authHeader: String): List<WebDavPositionRecord> {
        val request = Request.Builder()
            .url(fileUrl)
            .get()
            .header("Authorization", authHeader)
            .build()

        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.code == HTTP_NOT_FOUND) return emptyList()
                if (!response.isSuccessful) {
                    throw IOException("WebDAV GET $POSITIONS_FILENAME failed with HTTP ${response.code}")
                }
                val json = response.body?.string().orEmpty()
                deserialize(json)
            }
        }.getOrElse { error ->
            if (error is IOException) throw error
            throw IOException("Failed to read WebDAV $POSITIONS_FILENAME", error)
        }
    }

    private fun uploadRecords(fileUrl: String, authHeader: String, records: List<WebDavPositionRecord>) {
        val json = serialize(records)
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(fileUrl)
            .put(body)
            .header("Authorization", authHeader)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("WebDAV PUT $POSITIONS_FILENAME failed with HTTP ${response.code}")
            }
        }
    }

    companion object {
        const val POSITIONS_FILENAME = "read_positions.json"
        private const val HTTP_NOT_FOUND = 404
        private val gson = Gson()
        private val recordListType = object : TypeToken<List<WebDavPositionRecord>>() {}.type

        fun merge(
            localPositions: List<ArticleReadPosition>,
            remoteRecords: List<WebDavPositionRecord>,
        ): List<WebDavPositionRecord> {
            val merged = LinkedHashMap<String, ArticleReadPosition>()

            remoteRecords.forEach { record ->
                val key = record.key()
                merged[key] = record.asPosition()
            }

            localPositions.forEach { local ->
                val key = local.key()
                val existing = merged[key]
                merged[key] = ReadPositionConflictResolver.resolve(existing, local) ?: local
            }

            return merged.values.map { it.toWebDavRecord() }
        }

        fun serialize(records: List<WebDavPositionRecord>): String = gson.toJson(records)

        fun deserialize(json: String): List<WebDavPositionRecord> =
            if (json.isBlank()) emptyList() else gson.fromJson<List<WebDavPositionRecord>>(json, recordListType).orEmpty()

        fun buildPositionsFileUrl(serverUrl: String): String {
            val base = serverUrl.trim()
            val normalized = if (base.endsWith("/")) base else "$base/"
            return normalized + POSITIONS_FILENAME
        }

        private fun WebDavPositionRecord.key(): String = "$feedUrl|$articleLink"
        private fun ArticleReadPosition.key(): String = "$feedUrl|$articleLink"

        private fun WebDavPositionRecord.asPosition() = ArticleReadPosition(
            articleId = key(),
            accountId = 0,
            feedUrl = feedUrl,
            articleLink = articleLink,
            progress = progress,
            lastReadTime = lastReadTime,
            isSynced = true,
        )

        private fun ArticleReadPosition.toWebDavRecord() = WebDavPositionRecord(
            feedUrl = feedUrl,
            articleLink = articleLink,
            progress = progress,
            lastReadTime = lastReadTime,
        )
    }
}
