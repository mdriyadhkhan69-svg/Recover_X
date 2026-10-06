package com.example.recoverx.scanner

import com.example.recoverx.model.ConfidenceLevel
import com.example.recoverx.model.FileCategory
import com.example.recoverx.model.RecoverySourceKind
import java.io.File

/**
 * Single rule for "does this path carry real recovery evidence?". A normal file in DCIM/Pictures/
 * Movies matches NOTHING here, so it can never become a candidate just because a scan reached it.
 */
object RecoveryEvidence {

    data class Evidence(val kind: RecoverySourceKind, val level: ConfidenceLevel, val text: String)

    private val TRASH_DIR_HINTS = listOf("trash", "recycle", ".trashed", ".recently", "deleted")
    private val ORPHAN_DIR_NAMES = setOf("lost.dir", "found.000", "found.001", "found.002", "lost+found")
    private val TRASHED_FILE = Regex("^\\.trashed-\\d+-")
    private val PENDING_FILE = Regex("^\\.pending-(\\d+)-")
    private val ANY_PREFIX = Regex("^\\.(trashed|pending)-\\d+-")
    private val DOC_EXT = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "zip")

    fun cleanName(name: String): String = name.replace(ANY_PREFIX, "").ifBlank { name }

    fun isDocumentName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in DOC_EXT

    fun classify(file: File): Evidence? {
        val parents = ArrayList<String>(8)
        var p = file.parentFile
        while (p != null && parents.size < 8) {
            parents.add(p.name)
            p = p.parentFile
        }
        return classify(file.name, parents)
    }

    /** parentNamesNearestFirst: immediate parent first. */
    fun classify(
        fileName: String,
        parentNamesNearestFirst: List<String>,
        nowSec: Long = System.currentTimeMillis() / 1000
    ): Evidence? {
        val lower = fileName.lowercase()
        if (TRASHED_FILE.containsMatchIn(lower)) {
            return Evidence(
                RecoverySourceKind.FILESYSTEM_TRASH, ConfidenceLevel.HIGH,
                "File carries Android's .trashed- marker (a trashed media file still on disk)"
            )
        }
        PENDING_FILE.find(lower)?.let { m ->
            val expiry = m.groupValues[1].toLongOrNull() ?: return null
            // Unexpired .pending- files are downloads/writes in progress: NOT recovery candidates.
            return if (expiry < nowSec) {
                Evidence(
                    RecoverySourceKind.ORPHAN_FILE, ConfidenceLevel.MEDIUM,
                    "Expired .pending- file: an interrupted or abandoned write Android has not purged yet"
                )
            } else null
        }
        for (dir in parentNamesNearestFirst.take(8)) {
            val d = dir.lowercase()
            if (d in ORPHAN_DIR_NAMES) {
                return Evidence(
                    RecoverySourceKind.ORPHAN_FILE, ConfidenceLevel.MEDIUM,
                    "Found in file-system repair folder '$dir'"
                )
            }
            if (TRASH_DIR_HINTS.any { d.contains(it) }) {
                return Evidence(
                    RecoverySourceKind.FILESYSTEM_TRASH, ConfidenceLevel.MEDIUM,
                    "Found inside trash-like folder '$dir'"
                )
            }
        }
        return null
    }

    fun categoryFor(fmt: SignatureValidator.DetectedFormat): FileCategory? = when (fmt) {
        SignatureValidator.DetectedFormat.JPEG, SignatureValidator.DetectedFormat.PNG,
        SignatureValidator.DetectedFormat.WEBP, SignatureValidator.DetectedFormat.GIF,
        SignatureValidator.DetectedFormat.HEIC -> FileCategory.PHOTO
        SignatureValidator.DetectedFormat.MP4_MOV_3GP, SignatureValidator.DetectedFormat.MKV -> FileCategory.VIDEO
        SignatureValidator.DetectedFormat.PDF, SignatureValidator.DetectedFormat.ZIP_OR_OFFICE -> FileCategory.DOCUMENT
        SignatureValidator.DetectedFormat.UNKNOWN -> null
    }

    fun extensionFor(fmt: SignatureValidator.DetectedFormat): String? = when (fmt) {
        SignatureValidator.DetectedFormat.JPEG -> "jpg"
        SignatureValidator.DetectedFormat.PNG -> "png"
        SignatureValidator.DetectedFormat.WEBP -> "webp"
        SignatureValidator.DetectedFormat.GIF -> "gif"
        SignatureValidator.DetectedFormat.HEIC -> "heic"
        SignatureValidator.DetectedFormat.MP4_MOV_3GP -> "mp4"
        SignatureValidator.DetectedFormat.MKV -> "mkv"
        SignatureValidator.DetectedFormat.PDF -> "pdf"
        SignatureValidator.DetectedFormat.ZIP_OR_OFFICE -> "zip"
        SignatureValidator.DetectedFormat.UNKNOWN -> null
    }
}