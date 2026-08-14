package com.example.recoverx.model

enum class FileCategory { PHOTO, VIDEO, DOCUMENT }

data class ScannedFile(
    val id: String,
    val name: String,
    val sizeLabel: String,
    val category: FileCategory,
    val recoveryStatus: String = "Accessible on device",
    val isSelected: Boolean = false,
    val uriString: String? = null,      // Phase 15-এ recovery/copy-এর জন্য দরকার হবে
    val dateAddedLabel: String = ""
)

// Preview/Results-এর জন্য mock fallback — আসল scan না চললে টেস্ট করার জন্য এখনো রাখা হলো
fun sampleScannedFiles(): List<ScannedFile> = listOf(
    ScannedFile("1", "IMG_20260721.jpg", "2.4 MB", FileCategory.PHOTO),
    ScannedFile("2", "VID_20260610.mp4", "24.6 MB", FileCategory.VIDEO)
)