package com.mckimquyen.reader.domain.model

import androidx.annotation.Keep

@Keep
data class RssSource(
    val name: String,
    val link: String
)

@Keep
data class RssSourcesData(
    val en: List<RssSource>? = null,
    val vi: List<RssSource>? = null,
    val ja: List<RssSource>? = null,
    val ko: List<RssSource>? = null,
    val zh: List<RssSource>? = null,
    val id: List<RssSource>? = null,
    val th: List<RssSource>? = null,
    val fr: List<RssSource>? = null,
    val de: List<RssSource>? = null,
    val es: List<RssSource>? = null,
    val pt: List<RssSource>? = null,
    val ar: List<RssSource>? = null
)