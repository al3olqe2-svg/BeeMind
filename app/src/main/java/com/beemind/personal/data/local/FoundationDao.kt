package com.beemind.personal.data.local

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FoundationDao {
    @Query("SELECT value FROM app_metadata WHERE `key` = :key")
    fun observeValue(key: String): Flow<String?>
}
