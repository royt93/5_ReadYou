package com.mckimquyen.reader.domain.steelman

import java.util.Date

data class ContrastingArticle(
    val articleId: String,
    val title: String,
    val feedName: String,
    val date: Date,
    val cue: String,
)

sealed interface ContrastingCoverageState {
    object Idle : ContrastingCoverageState
    object Searching : ContrastingCoverageState
    object None : ContrastingCoverageState
    data class Ready(
        val phrases: List<String>,
        val selectedPhrase: String,
        val items: List<ContrastingArticle>,
    ) : ContrastingCoverageState
}
