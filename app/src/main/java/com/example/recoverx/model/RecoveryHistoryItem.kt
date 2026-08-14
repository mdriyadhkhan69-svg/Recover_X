package com.example.recoverx.model

data class RecoveryHistoryItem(
    val id: String,
    val fileName: String,
    val dateLabel: String,
    val category: FileCategory,
    val status: String // "Recovered" অথবা "Failed"
)

// In-memory history holder — app বন্ধ হলে হারিয়ে যাবে।
// Phase 16-এ Room DB দিয়ে replace হবে, তখন এটা disk-এ persist থাকবে।
object RecoveryHistoryHolder {
    private val _items = mutableListOf<RecoveryHistoryItem>()
    val items: List<RecoveryHistoryItem> get() = _items.toList()

    fun add(item: RecoveryHistoryItem) {
        _items.add(0, item) // নতুনটা সবার উপরে দেখাবে
    }

    fun remove(id: String) {
        _items.removeAll { it.id == id }
    }
}