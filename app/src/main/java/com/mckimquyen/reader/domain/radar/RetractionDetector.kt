package com.mckimquyen.reader.domain.radar

data class SnapshotHashes(val titleHash: String, val contentHash: String)

data class ArticleSnapshotData(
    val title: String,
    val contentText: String,
    val titleHash: String,
    val contentHash: String,
) {
    fun hashes() = SnapshotHashes(titleHash, contentHash)
}

enum class RetractionVerdict { NEW, UNCHANGED, EDITED }

object RetractionDetector {

    fun snapshotOf(title: String, html: String): ArticleSnapshotData {
        val plainTitle = SnapshotNormalizer.toPlainText(title)
        val plainBody = SnapshotNormalizer.toPlainText(html)
        return ArticleSnapshotData(
            title = plainTitle,
            contentText = plainBody,
            titleHash = SnapshotNormalizer.hash(plainTitle),
            contentHash = SnapshotNormalizer.hash(plainBody),
        )
    }

    /**
     * A blank title or body on the incoming side is a feed quirk (the field was simply not delivered), not an edit,
     * so only the non-blank parts are compared.
     */
    fun evaluate(previous: SnapshotHashes?, incoming: ArticleSnapshotData): RetractionVerdict {
        if (previous == null) return RetractionVerdict.NEW
        val titleChanged = incoming.title.isNotBlank() && incoming.titleHash != previous.titleHash
        val bodyChanged = incoming.contentText.isNotBlank() && incoming.contentHash != previous.contentHash
        return if (titleChanged || bodyChanged) RetractionVerdict.EDITED else RetractionVerdict.UNCHANGED
    }
}
