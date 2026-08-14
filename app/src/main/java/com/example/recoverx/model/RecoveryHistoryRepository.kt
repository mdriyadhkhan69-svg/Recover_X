package com.example.recoverx.model

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class RecoveryHistoryRepository(context: Context) {
    private val dao = AppDatabase.getInstance(context).recoveryHistoryDao()

    fun observeAll(): Flow<List<RecoveryHistoryItem>> =
        dao.observeAll()
            .map { list -> list.map { it.toItem() } }
            .catch { e ->
                Log.w("RecoveryHistoryRepo", "History পড়তে সমস্যা: ${e.message}")
                emit(emptyList())
            }

    suspend fun add(item: RecoveryHistoryItem) {
        try {
            dao.insert(
                RecoveryHistoryEntity(
                    id = item.id,
                    fileName = item.fileName,
                    dateLabel = item.dateLabel,
                    category = item.category.name,
                    status = item.status,
                    timestamp = System.currentTimeMillis(),
                    uriString = item.uriString
                )
            )
        } catch (e: Exception) {
            // History সেভ করতে ব্যর্থ হলেও recovery flow crash করবে না
            Log.w("RecoveryHistoryRepo", "History সেভ করা যায়নি: ${e.message}")
        }
    }

    suspend fun remove(id: String) {
        try {
            dao.deleteById(id)
        } catch (e: Exception) {
            Log.w("RecoveryHistoryRepo", "History entry মুছা যায়নি: ${e.message}")
        }
    }
}