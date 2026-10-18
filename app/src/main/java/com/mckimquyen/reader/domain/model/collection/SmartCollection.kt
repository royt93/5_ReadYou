package com.mckimquyen.reader.domain.model.collection

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.infrastructure.ai.search.SemanticSearchEngine
import java.util.UUID

@Keep
@Entity(
    tableName = "smart_collection",
    foreignKeys = [
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["accountId"]),
    ],
)
data class SmartCollection(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val accountId: Int,
    val name: String,
    val query: String,
    val minScoreThreshold: Float = SemanticSearchEngine.DEFAULT_MIN_SCORE_THRESHOLD,
    val createdAt: Long = System.currentTimeMillis(),
)
