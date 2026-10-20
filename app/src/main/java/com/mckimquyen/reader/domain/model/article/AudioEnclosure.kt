package com.mckimquyen.reader.domain.model.article

import com.rometools.rome.feed.synd.SyndEnclosure
import com.rometools.rome.feed.synd.SyndEntry
import java.net.URI

object AudioEnclosure {
    private const val AUDIO_MIME_PREFIX = "audio/"
    private val ALLOWED_SCHEMES = setOf("https", "http")
    private const val ITUNES_PREFIX = "itunes"
    private const val DURATION_NAME = "duration"
    private val AUDIO_EXTENSIONS = listOf(".mp3", ".m4a", ".aac", ".ogg")

    /** First audio enclosure with an http(s) URL, or null (http is upgraded to https at playback time). Duration comes from `<itunes:duration>`. */
    fun from(entry: SyndEntry): AudioInfo? {
        val enclosure = entry.enclosures.orEmpty().firstOrNull { isAudio(it) } ?: return null
        val url = enclosure.url?.trim().orEmpty()
        if (!isWebUrl(url)) return null
        return AudioInfo(url, enclosure.type?.takeIf { it.isNotBlank() }, durationOf(entry))
    }

    private fun isAudio(enclosure: SyndEnclosure): Boolean {
        val type = enclosure.type
        if (!type.isNullOrBlank()) return type.startsWith(AUDIO_MIME_PREFIX, ignoreCase = true)
        val path = runCatching { URI(enclosure.url.orEmpty()).path }.getOrNull().orEmpty().lowercase()
        return AUDIO_EXTENSIONS.any { path.endsWith(it) }
    }

    private fun isWebUrl(url: String): Boolean =
        runCatching { URI(url).scheme?.lowercase() in ALLOWED_SCHEMES }.getOrDefault(false)

    private fun durationOf(entry: SyndEntry): Int? =
        entry.foreignMarkup.orEmpty()
            .firstOrNull { it.name == DURATION_NAME && it.namespacePrefix == ITUNES_PREFIX }
            ?.let { AudioDuration.parse(it.text) }
}
