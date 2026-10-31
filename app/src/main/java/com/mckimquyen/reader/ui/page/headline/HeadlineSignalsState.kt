package com.mckimquyen.reader.ui.page.headline

import androidx.compose.runtime.compositionLocalOf
import com.mckimquyen.reader.domain.headline.HeadlineAnalyzer
import com.mckimquyen.reader.domain.headline.HeadlineSignal

/** Single feature gate supplied at the flow/reader boundary; avoids DataStore access in individual rows. */
val LocalHeadlineSignalsEnabled = compositionLocalOf { true }

/** Bounded deterministic analysis, memoized by Compose at each article boundary. */
fun headlineSignalFor(title: String, enabled: Boolean): HeadlineSignal? =
    if (enabled) HeadlineAnalyzer.analyze(title) else null
