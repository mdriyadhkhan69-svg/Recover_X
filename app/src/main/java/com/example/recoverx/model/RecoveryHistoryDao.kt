package com.example.recoverx.model

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecoveryHistoryDao {
    @Query("SELECT * FROM recovery_history ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<RecoveryHistoryEntity>>

    @Insert
    suspend fun insert(item: RecoveryHistoryEntity)

    @Query("DELETE FROM recovery_history WHERE id = :id")
    suspend fun deleteById(id: String)
}