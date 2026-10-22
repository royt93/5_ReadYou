package com.mckimquyen.reader.domain.model.article

object FullContentPolicy {
    const val MAX_STORED_FULL_CONTENT_BYTES = 1_048_576

    fun byteSize(content: String): Int = content.toByteArray(Charsets.UTF_8).size

    fun logOversize(source: String, articleId: String, content: String) {
        android.util.Log.w(TAG, "Reject oversized fullContent source=$source articleId=$articleId bytes=${byteSize(content)}")
    }

    private const val TAG = "FullContentPolicy"

    /** Returns null for absent or over-limit content; an empty string is still an accepted empty body. */
    fun accept(content: String?): String? = content?.takeIf { byteSize(it) <= MAX_STORED_FULL_CONTENT_BYTES }
}
