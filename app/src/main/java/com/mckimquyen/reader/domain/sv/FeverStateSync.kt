package com.mckimquyen.reader.domain.sv

/**
 * Decides which local read and starred flags to change after the server's `unread_item_ids` and `saved_item_ids` answers.
 *
 * A missing field (null) used to be read as "no article at all", so one truncated answer marked every article unread and removed every star on the phone. A null list now
 * means "the server did not say": that flag is left alone. An empty string is a real, empty answer.
 */
object FeverStateSync {
    fun parseIds(raw: String?): Set<String>? = raw?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()

    /** The flag [local] should become, or null to leave it as it is. */
    fun targetFlag(articleId: String, serverIds: Set<String>?, local: Boolean): Boolean? {
        if (serverIds == null) return null
        val shouldBe = articleId in serverIds
        return if (shouldBe != local) shouldBe else null
    }
}
