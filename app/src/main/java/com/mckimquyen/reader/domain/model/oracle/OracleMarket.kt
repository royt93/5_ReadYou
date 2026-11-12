package com.mckimquyen.reader.domain.model.oracle

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.mckimquyen.reader.domain.model.article.Article

/** PENDING: the AI answer was rejected but attempts remain; HAS_CLAIM: a usable prediction; NO_CLAIM: nothing to ask again. */
enum class OracleMarketStatus { PENDING, HAS_CLAIM, NO_CLAIM }

class OracleMarketStatusConverters {
    @TypeConverter
    fun toStorage(value: OracleMarketStatus): String = value.name

    @TypeConverter
    fun fromStorage(value: String): OracleMarketStatus = OracleMarketStatus.valueOf(value)
}

/** One row per article the prediction finder has looked at (ORACLE-01). No points, bets or money live here. */
@Entity(
    tableName = "oracle_market",
    foreignKeys = [
        ForeignKey(
            entity = Article::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["articleId"], unique = true)],
)
@Keep
data class OracleMarket(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val articleId: String,
    val status: OracleMarketStatus,
    val attempts: Int = 0,
    val question: String? = null,
    val expiresAt: Long? = null,
    val resolutionCriteria: String? = null,
    val createdAt: Long,
)
