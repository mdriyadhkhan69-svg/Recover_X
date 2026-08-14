package com.example.recoverx.model

data class RecoveryHistoryItem(
    val id: String,
    val fileName: String,
    val dateLabel: String,
    val category: FileCategory,
    val status: String
)