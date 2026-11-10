package com.mckimquyen.reader.domain.model.podcast

/** Metadata-only row for the car's podcast list. Never carries an audio URL or a body column (FIX-17). */
data class PodcastBrowseItem(
    val id: String,
    val title: String,
    val feedName: String,
    val durationSec: Int?,
)
