package com.mckimquyen.reader.domain.p2psync

data class SyncFeed(val url: String, val name: String, val groupName: String)
data class SyncArticleState(
    val feedUrl: String,
    val link: String,
    val isUnread: Boolean,
    val isStarred: Boolean,
    val readAt: Long?,
)
data class SyncPackage(
    val version: Int,
    val sourceId: String,
    val createdAt: Long,
    val feeds: List<SyncFeed>,
    val articles: List<SyncArticleState>,
)

sealed interface SyncPackageResult {
    data class Valid(val data: SyncPackage) : SyncPackageResult
    data class Invalid(val reason: SyncPackageError) : SyncPackageResult
}

enum class SyncPackageError {
    TOO_LARGE, BAD_JSON, WRONG_SCHEMA, UNSUPPORTED_VERSION, INVALID_VALUE, DUPLICATE_KEY, LIMIT_EXCEEDED,
}
