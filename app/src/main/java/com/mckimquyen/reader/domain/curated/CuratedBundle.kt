package com.mckimquyen.reader.domain.curated

import androidx.annotation.Keep

/** One RSS source inside a curated bundle. */
@Keep
data class CuratedSource(
    val name: String,
    val url: String,
)

/**
 * A themed, static list of RSS sources shipped inside the app. [verifiedOn] is the date the URLs were last fetched and
 * confirmed to be RSS/Atom; it is a one-time check, not a promise that the feeds stay available.
 */
@Keep
data class CuratedBundle(
    val id: String,
    val titleEn: String,
    val titleVi: String,
    val verifiedOn: String,
    val sources: List<CuratedSource>,
) {
    /** Vietnamese title for Vietnamese UI, English for every other language. */
    fun localizedTitle(language: String): String = if (language == VIETNAMESE) titleVi else titleEn

    private companion object {
        const val VIETNAMESE = "vi"
    }
}
