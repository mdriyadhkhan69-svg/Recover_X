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
        detailRows = listOfNotNull(
            "Size" to file.sizeLabel,
            "Type" to file.category.name.lowercase().replaceFirstChar { it.uppercase() },
            "Recovery status" to file.recoveryStatus,
            "Source" to file.sourceKind.label,
            "Confidence" to file.confidenceLevel.name.lowercase().replaceFirstChar { it.uppercase() },
            file.evidence.takeIf { it.isNotBlank() }?.let { "Why" to it },
            if (!file.isOriginalFile) "Note" to "Preview only, not the original file" else null
        ),
        primaryActionLabel = "Recover File",
        onPrimaryAction = onRecoverClick
    )
}