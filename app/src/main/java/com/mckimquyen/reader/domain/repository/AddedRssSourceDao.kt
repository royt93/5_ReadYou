package com.mckimquyen.reader.domain.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mckimquyen.reader.domain.model.addedsource.AddedRssSource
import kotlinx.coroutines.flow.Flow

@Dao
interface AddedRssSourceDao {

    @Query("SELECT * FROM added_rss_source WHERE accountId = :accountId")
    fun getAddedSourcesByAccount(accountId: Int): Flow<List<AddedRssSource>>

    @Query("SELECT COUNT(*) FROM added_rss_source WHERE url = :url AND accountId = :accountId")
    suspend fun isSourceAdded(url: String, accountId: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAddedSource(addedSource: AddedRssSource)

    @Query("DELETE FROM added_rss_source WHERE url = :url AND accountId = :accountId")
    suspend fun removeAddedSource(url: String, accountId: Int)
}