package com.mckimquyen.reader.domain.oracle

/**
 * The set of bets the user was already reminded about (ORACLE-03), stored as one string. Oldest first, so trimming to the
 * cap drops the front. Article ids never contain a line break, which makes it a safe separator.
 */
object OracleRemindedCodec {
    const val REMINDED_CAP = 200
    private const val SEPARATOR = "\n"

    fun decode(raw: String?): List<String> =
        raw.orEmpty().split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    fun encode(ids: List<String>): String = ids.joinToString(SEPARATOR)

    fun append(existing: List<String>, newIds: List<String>): List<String> =
        (existing + newIds).distinct().takeLast(REMINDED_CAP)

    fun prune(existing: List<String>, stillOpen: Set<String>): List<String> = existing.filter { it in stillOpen }
}
