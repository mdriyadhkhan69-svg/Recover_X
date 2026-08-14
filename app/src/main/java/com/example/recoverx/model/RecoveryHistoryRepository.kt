package com.example.recoverx.model

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RecoveryHistoryRepository(context: Context) {
    private val dao = AppDatabase.getInstance(context).recoveryHistoryDao()

    fun observeAll(): Flow<List<RecoveryHistoryItem>> =
        dao.observeAll().map { list -> list.map { it.toItem() } }

    suspend fun add(item: RecoveryHistoryItem) {
        dao.insert(
            RecoveryHistoryEntity(
                id = item.id,
                fileName = item.fileName,
                dateLabel = item.dateLabel,
                category = item.category.name,
                status = item.status,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun remove(id: String) {
        dao.deleteById(id)
    }
}