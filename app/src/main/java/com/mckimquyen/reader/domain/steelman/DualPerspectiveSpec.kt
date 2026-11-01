package com.mckimquyen.reader.domain.steelman

/** Data for one two-headline card. Dates are already formatted by the caller. */
data class DualPerspectiveSpec(
    val topTitle: String,
    val topSource: String,
    val topDate: String,
    val bottomTitle: String,
    val bottomSource: String,
    val bottomDate: String,
    val cue: String,
)
