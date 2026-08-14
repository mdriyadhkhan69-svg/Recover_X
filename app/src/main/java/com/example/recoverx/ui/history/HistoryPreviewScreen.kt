package com.example.recoverx.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.recoverx.model.HistoryPreviewHolder
import com.example.recoverx.ui.common.MediaPreviewContent

@Composable
fun HistoryPreviewScreen() {
    val item = HistoryPreviewHolder.item
    if (item == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Item পাওয়া যায়নি", color = MaterialTheme.colorScheme.onBackground)
        }
        return
    }
    MediaPreviewContent(
        name = item.fileName,
        uriString = item.uriString,
        category = item.category,
        detailRows = listOf("Date" to item.dateLabel, "Status" to item.status),
        primaryActionLabel = "",
        onPrimaryAction = null
    )
}