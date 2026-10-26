package com.mckimquyen.reader.domain.model.rpg

/** Metadata-only view of another article, used as a distractor source. Never carries a body column. */
data class QuizPeer(
    val title: String,
    val shortDescription: String,
)
