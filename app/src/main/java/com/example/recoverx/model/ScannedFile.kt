package com.example.recoverx.model

enum class FileCategory { PHOTO, VIDEO, DOCUMENT }

enum class DocumentType { PDF, DOC, XLS, PPT, TXT, ZIP, OTHER }

enum class RecoveryConfidence {
    TRASHED,       // MediaStore trash-এ আছে — সত্যিকারের recoverable
    ON_DEVICE      // এখনো normally device-এ আছে — এটা "deleted" ফাইল না
}

// UI-facing classification layer (does not change recovery routing logic in RecoveryEngine,
// only how a result is labeled/prioritized in the results list).
enum class ScanSource {
    FILESYSTEM, MEDIASTORE, SAF, THUMBNAIL, CACHE, TRASH, SD_CARD, SECURE_FOLDER
}

enum class LiveStatus {
    LIVE,                 // still normally present/indexed — not a deleted file
    RECOVERABLE,           // confirmed trashed/deleted source
    POSSIBLY_RECOVERABLE,  // found via carving/cache/thumbnail only, can't confirm deletion
    PARTIAL_CORRUPTED      // signature matched but data is incomplete/invalid
}

enum class ConfidenceLevel { HIGH, MEDIUM, LOW }

/** WHY a result is considered recoverable. isOriginal=false means a preview, never the original file. */
enum class RecoverySourceKind(val label: String, val isOriginal: Boolean) {
    LIVE_EXISTING("Existing file", true),
    MEDIASTORE_TRASH("MediaStore Trash", true),
    FILESYSTEM_TRASH("Filesystem Trash", true),
    ORPHAN_FILE("Orphaned File", true),
    RECOVERED_THUMBNAIL("Recovered Thumbnail (preview only)", false),
    CARVED("Carved from raw storage (root)", true),
    UNKNOWN("Unknown", true)
}

data class ScannedFile(
    val id: String,
    val name: String,
    val sizeLabel: String,
    val category: FileCategory,
    val confidence: RecoveryConfidence = RecoveryConfidence.ON_DEVICE,
    val isSelected: Boolean = false,
    val uriString: String? = null,
    val dateAddedLabel: String = "",
    val documentType: DocumentType = DocumentType.OTHER,
    val liveStatus: LiveStatus = LiveStatus.RECOVERABLE,
    val confidenceLevel: ConfidenceLevel = ConfidenceLevel.MEDIUM,
    val sizeBytes: Long = 0L,
    val dedupeKey: String = "",
    val source: ScanSource = ScanSource.MEDIASTORE,
    val fingerprint: String = "",
    val sourceKind: RecoverySourceKind = RecoverySourceKind.UNKNOWN,
    val evidence: String = "",
    val mimeType: String? = null
) {
    val isOriginalFile: Boolean
        get() = sourceKind.isOriginal

    val recoveryStatus: String
        get() {
            val base = when (sourceKind) {
                RecoverySourceKind.MEDIASTORE_TRASH -> "In MediaStore trash — recoverable"
                RecoverySourceKind.FILESYSTEM_TRASH -> "Filesystem trash entry"
                RecoverySourceKind.ORPHAN_FILE -> "Orphaned file"
                RecoverySourceKind.RECOVERED_THUMBNAIL -> "Thumbnail only — not the original"
                RecoverySourceKind.CARVED -> "Carved from raw storage"
                RecoverySourceKind.LIVE_EXISTING, RecoverySourceKind.UNKNOWN -> when (confidence) {
                    RecoveryConfidence.TRASHED -> "In trash — recoverable"
                    RecoveryConfidence.ON_DEVICE -> "Currently on device"
                }
            }
            return if (source == ScanSource.SD_CARD) "$base · SD card" else base
        }
}

fun detectDocumentType(name: String, mimeType: String?): DocumentType {
    val ext = name.substringAfterLast('.', "").lowercase()
    val mime = mimeType?.lowercase() ?: ""
    return when {
        ext == "pdf" || mime.contains("pdf") -> DocumentType.PDF
        ext in setOf("doc", "docx") || mime.contains("msword") || mime.contains("wordprocessingml") -> DocumentType.DOC
        ext in setOf("xls", "xlsx") || mime.contains("ms-excel") || mime.contains("spreadsheetml") -> DocumentType.XLS
        ext in setOf("ppt", "pptx") || mime.contains("ms-powerpoint") || mime.contains("presentationml") -> DocumentType.PPT
        ext == "txt" || mime == "text/plain" -> DocumentType.TXT
        ext == "zip" || mime.contains("zip") -> DocumentType.ZIP
        else -> DocumentType.OTHER
    }
}

fun sampleScannedFiles(): List<ScannedFile> = listOf(
    ScannedFile("1", "IMG_20260721.jpg", "2.4 MB", FileCategory.PHOTO),
    ScannedFile("2", "VID_20260610.mp4", "24.6 MB", FileCategory.VIDEO)
)