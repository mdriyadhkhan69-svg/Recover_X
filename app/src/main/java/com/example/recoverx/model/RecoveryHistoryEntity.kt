package com.example.recoverx.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recovery_history")
data class RecoveryHistoryEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val dateLabel: String,
    val category: String, // FileCategory enum-কে String হিসেবে সেভ করছি, সহজ রাখার জন্য
    val status: String,
    val timestamp: Long
)

fun RecoveryHistoryEntity.toItem() = RecoveryHistoryItem(
    id = id,
    fileName = fileName,
    dateLabel = dateLabel,
    category = FileCategory.valueOf(category),
    status = status
)