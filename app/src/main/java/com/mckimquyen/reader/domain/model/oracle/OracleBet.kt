package com.mckimquyen.reader.domain.model.oracle

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

class OracleBetConverters {
    @TypeConverter
    fun sideToStorage(value: OracleBetSide): String = value.name

    @TypeConverter
    fun sideFromStorage(value: String): OracleBetSide = OracleBetSide.valueOf(value)

    @TypeConverter
    fun statusToStorage(value: OracleBetStatus): String = value.name

    @TypeConverter
    fun statusFromStorage(value: String): OracleBetStatus = OracleBetStatus.valueOf(value)
}

/**
 * One stake on one prediction. Deliberately NO foreign key: the prediction row is replaced and later cleaned up
 * (ORACLE-01) and an article can be deleted, and neither may make staked points disappear, so the question and the
 * deadline are copied here.
 */
@Entity(tableName = "oracle_bet", indices = [Index(value = ["articleId"], unique = true)])
@Keep
data class OracleBet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val articleId: String,
    val question: String,
    val expiresAt: Long,
    val side: OracleBetSide,
    val stake: Int,
    val status: OracleBetStatus,
    val placedAt: Long,
    val settledAt: Long? = null,
)
