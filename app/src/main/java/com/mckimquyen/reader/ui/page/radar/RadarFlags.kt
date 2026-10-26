package com.mckimquyen.reader.ui.page.radar

import androidx.compose.runtime.compositionLocalOf
import com.mckimquyen.reader.domain.model.radar.ArticleRadarState

enum class RadarFlag { EDITED, REMOVED }

fun ArticleRadarState.toFlag(): RadarFlag? = when {
    removedAt != null -> RadarFlag.REMOVED
    editCount > 0 -> RadarFlag.EDITED
    else -> null
}

/** articleId -> flag for flagged articles only; empty when the radar is off or nothing is flagged. */
val LocalRadarFlags = compositionLocalOf { emptyMap<String, RadarFlag>() }

/** Opens the diff screen for an article; no-op where navigation is unavailable (previews, tests). */
val LocalOpenRadarDiff = compositionLocalOf<(String) -> Unit> { {} }
