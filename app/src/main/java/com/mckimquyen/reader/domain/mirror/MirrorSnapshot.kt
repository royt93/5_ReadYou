package com.mckimquyen.reader.domain.mirror

/** The only thing the mirror ever sends: one article reduced to plain text. */
data class MirrorSnapshot(
    val title: String,
    val source: String,
    val dateMillis: Long,
    val paragraphs: List<String>,
    val truncated: Boolean,
    val version: Long,
) {
    // Real serialisation arrives with the snapshot builder; until then this keeps the router compiling.
    fun toJson(): String = "{}"

    companion object {
        const val EMPTY_JSON = """{"title":"","source":"","date":0,"paragraphs":[],"truncated":false,"version":0}"""
    }
}
