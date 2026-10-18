package com.mckimquyen.reader.domain.model.statistics

import androidx.annotation.Keep

@Keep
data class FeedReadCount(val feedId: String, val feedName: String, val count: Int)
