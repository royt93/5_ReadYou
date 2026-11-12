package com.mckimquyen.reader.domain.model.oracle

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

/** The one local wallet of virtual Intel Points (ORACLE-02). No money, ads or purchases ever feed it. */
@Entity(tableName = "user_wallet")
@Keep
data class UserWallet(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val balance: Long,
    val lastGrantEpochDay: Long,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
