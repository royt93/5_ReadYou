package com.mckimquyen.reader.domain.model.bounty

import java.time.LocalDate
import java.util.Date

data class QuotedSentence(
    val text: String,
    val feedName: String,
    val date: Date,
    val articleId: String,
)

data class TimelineEntry(val day: LocalDate, val articles: List<DossierArticleRef>)

data class DossierArticleRef(
    val articleId: String,
    val title: String,
    val feedName: String,
    val date: Date,
)

data class RecurringPhrase(val phrase: String, val articleCount: Int)

data class SourceFraming(val feedName: String, val headlines: List<DossierArticleRef>)

data class Dossier(
    val query: String,
    val summary: List<QuotedSentence>,
    val timeline: List<TimelineEntry>,
    val phrases: List<RecurringPhrase>,
    val framings: List<SourceFraming>,
    val sourceCount: Int,
    val generatedAtMillis: Long,
)
