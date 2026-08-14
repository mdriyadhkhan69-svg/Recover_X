package com.example.recoverx.ui.preview

import androidx.compose.runtime.Composable
import com.example.recoverx.model.ScannedFile
import com.example.recoverx.ui.common.MediaPreviewContent

@Composable
fun PreviewScreen(
    file: ScannedFile,
    onRecoverClick: () -> Unit
) {
    MediaPreviewContent(
        name = file.name,
        uriString = file.uriString,
        category = file.category,
        detailRows = listOf(
            "Size" to file.sizeLabel,
            "Type" to file.category.name.lowercase().replaceFirstChar { it.uppercase() },
            "Recovery status" to file.recoveryStatus
        ),
        primaryActionLabel = "Recover File",
        onPrimaryAction = onRecoverClick
    )
}