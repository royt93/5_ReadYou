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
    val vi: List<RssSource>? = null
)