package com.mckimquyen.reader.domain.model.collection

import androidx.room.Embedded

 data class SmartCollectionWithCount(
    @Embedded
    val collection: SmartCollection,
    val articleCount: Int,
)
