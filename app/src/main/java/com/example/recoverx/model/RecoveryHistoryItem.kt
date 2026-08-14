package com.example.recoverx.model

data class RecoveryHistoryItem(
    val id: String,
    val fileName: String,
    val dateLabel: String,
    val category: FileCategory,
    val status: String,
    val uriString: String? = null
)

object RecoveryHistoryHolder {
    private val _items = mutableListOf<RecoveryHistoryItem>()
    val items: List<RecoveryHistoryItem> get() = _items.toList()
    fun add(item: RecoveryHistoryItem) { _items.add(0, item) }
    fun remove(id: String) { _items.removeAll { it.id == id } }
}