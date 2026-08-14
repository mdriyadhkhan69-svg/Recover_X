package com.example.recoverx.model

enum class FileCategory { PHOTO, VIDEO, DOCUMENT }

enum class RecoveryConfidence {
    TRASHED,       // MediaStore trash-এ আছে — সত্যিকারের recoverable
    ON_DEVICE      // এখনো normally device-এ আছে — এটা "deleted" ফাইল না
}

data class ScannedFile(
    val id: String,
    val name: String,
    val sizeLabel: String,
    val category: FileCategory,
    val confidence: RecoveryConfidence = RecoveryConfidence.ON_DEVICE,
    val isSelected: Boolean = false,
    val uriString: String? = null,
    val dateAddedLabel: String = ""
) {
    val recoveryStatus: String
        get() = when (confidence) {
            RecoveryConfidence.TRASHED -> "In trash — recoverable"
            RecoveryConfidence.ON_DEVICE -> "Currently on device"
        }
}

fun sampleScannedFiles(): List<ScannedFile> = listOf(
    ScannedFile("1", "IMG_20260721.jpg", "2.4 MB", FileCategory.PHOTO),
    ScannedFile("2", "VID_20260610.mp4", "24.6 MB", FileCategory.VIDEO)
)