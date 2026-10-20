package com.mckimquyen.reader.domain.model.article

object AudioUrlCandidates {
    private const val HTTP_PREFIX = "http://"
    private const val HTTPS_PREFIX = "https://"

    /** URLs to try in order: an `http://` enclosure is first tried over https, then as published. */
    fun of(url: String): List<String> =
        if (url.startsWith(HTTP_PREFIX, ignoreCase = true)) {
            listOf(HTTPS_PREFIX + url.substring(HTTP_PREFIX.length), url)
        } else {
            listOf(url)
        }
}
