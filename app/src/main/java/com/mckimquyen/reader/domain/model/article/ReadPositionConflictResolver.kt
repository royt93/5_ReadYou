package com.mckimquyen.reader.domain.model.article

object ReadPositionConflictResolver {

    fun resolve(
        local: ArticleReadPosition?,
        remote: ArticleReadPosition?
    ): ArticleReadPosition? {
        if (local == null) return remote
        if (remote == null) return local

        return when {
            remote.progress > local.progress -> remote
            local.progress > remote.progress -> local
            remote.lastReadTime > local.lastReadTime -> remote
            else -> local
        }
    }
}
