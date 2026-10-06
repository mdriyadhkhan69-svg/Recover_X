package com.example.recoverx.scanner

import android.net.Uri
import android.util.Log
import com.example.recoverx.model.DocumentType
import com.example.recoverx.model.FileCategory
import com.example.recoverx.model.LiveStatus
import com.example.recoverx.model.RecoveryConfidence
import com.example.recoverx.model.ScanSource
import com.example.recoverx.model.ScannedFile
import com.example.recoverx.model.detectDocumentType
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Recursive walk over app-accessible storage roots. A file becomes a candidate ONLY if
 * RecoveryEvidence finds real evidence (.trashed- marker, expired .pending-, trash-like folder,
 * LOST.DIR/FOUND.000). Live files in DCIM/Pictures/etc. are never candidates.
 * Extension-less fragments inside recovery folders are classified by header bytes.
 * Cycle protection: canonical paths are tracked per scan.
 */
object FileSystemScanner {

    private const val TAG = "FileSystemScanner"
    private const val MAX_DEPTH = 40
    private const val PROGRESS_BATCH = 12

    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif")
    private val VIDEO_EXT = setOf("mp4", "mov", "3gp", "mkv", "avi")
    private val DOC_EXT = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "zip")

    data class SkippedRoot(val path: String, val reason: String)

    fun scan(
        roots: List<File>,
        liveMediaPaths: Set<String>,
        onProgress: (scanned: Int, found: Int, currentLabel: String) -> Unit,
        onSkipped: (SkippedRoot) -> Unit
    ): List<ScannedFile> {
        val results = mutableListOf<ScannedFile>()
        var scanned = 0
        val visitedCanonical = mutableSetOf<String>()

        for (root in roots) {
            if (!root.exists() || !root.canRead()) {
                onSkipped(SkippedRoot(root.absolutePath, "Inaccessible"))
                continue
            }
            try {
                scanned = walk(root, 0, visitedCanonical, liveMediaPaths, results, scanned, onProgress, onSkipped)
            } catch (e: SecurityException) {
                onSkipped(SkippedRoot(root.absolutePath, "Permission denied"))
            } catch (e: Exception) {
                onSkipped(SkippedRoot(root.absolutePath, e.message ?: "Unknown error"))
            }
        }
        onProgress(scanned, results.size, "Filesystem scan complete")
        return results
    }

    private fun walk(
        dir: File,
        depth: Int,
        visitedCanonical: MutableSet<String>,
        liveMediaPaths: Set<String>,
        results: MutableList<ScannedFile>,
        startScanned: Int,
        onProgress: (Int, Int, String) -> Unit,
        onSkipped: (SkippedRoot) -> Unit
    ): Int {
        var scanned = startScanned
        if (depth > MAX_DEPTH) return scanned

        val canonical = try { dir.canonicalPath } catch (e: Exception) { dir.absolutePath }
        if (!visitedCanonical.add(canonical)) return scanned

        if (dir.name == "data" && dir.parentFile?.name == "Android" && !dir.canRead()) {
            onSkipped(SkippedRoot(dir.absolutePath, "Restricted (Android/data)"))
            return scanned
        }

        val children = try { dir.listFiles() } catch (e: Exception) { null }
        if (children == null) {
            onSkipped(SkippedRoot(dir.absolutePath, "Could not list"))
            return scanned
        }

        for (child in children) {
            try {
                if (child.isDirectory) {
                    scanned++
                    if (scanned % PROGRESS_BATCH == 0) {
                        onProgress(scanned, results.size, "Scanning ${child.name}...")
                    }
                    scanned = walk(child, depth + 1, visitedCanonical, liveMediaPaths, results, scanned, onProgress, onSkipped)
                    continue
                }
                val evidence = RecoveryEvidence.classify(child)
                if (evidence != null && !liveMediaPaths.contains(child.absolutePath)) {
                    toCandidate(child, evidence)?.let { results.add(it) }
                }
            } catch (rowError: Exception) {
                Log.w(TAG, "Entry unreadable, skipped: ${rowError.message}")
            }
            scanned++
            if (scanned % PROGRESS_BATCH == 0) {
                onProgress(scanned, results.size, "Scanning ${dir.name}...")
            }
        }
        return scanned
    }

    private fun toCandidate(file: File, ev: RecoveryEvidence.Evidence): ScannedFile? {
        val len = file.length()
        if (len <= 0L) return null
        val ext = file.extension.lowercase()
        var category: FileCategory? = when {
            ext in IMAGE_EXT -> FileCategory.PHOTO
            ext in VIDEO_EXT -> FileCategory.VIDEO
            ext in DOC_EXT -> FileCategory.DOCUMENT
            else -> null
        }
        var displayName = RecoveryEvidence.cleanName(file.name)
        if (category == null) {
            // Extension-less fragment inside a recovery folder: decide from header bytes only.
            val fmt = sniff(file)
            category = RecoveryEvidence.categoryFor(fmt) ?: return null
            displayName += "." + (RecoveryEvidence.extensionFor(fmt) ?: return null)
        }
        return ScannedFile(
            id = "fs-${file.absolutePath}",
            name = displayName,
            sizeLabel = formatSize(len),
            category = category,
            confidence = RecoveryConfidence.ON_DEVICE,
            uriString = Uri.fromFile(file).toString(),
            dateAddedLabel = formatDate(file.lastModified()),
            documentType = if (category == FileCategory.DOCUMENT) detectDocumentType(displayName, null) else DocumentType.OTHER,
            liveStatus = LiveStatus.POSSIBLY_RECOVERABLE,
            confidenceLevel = ev.level,
            sizeBytes = len,
            dedupeKey = "${file.name}-$len",
            source = ScanSource.FILESYSTEM,
            sourceKind = ev.kind,
            evidence = ev.text
        )
    }

    private fun sniff(file: File): SignatureValidator.DetectedFormat = try {
        FileInputStream(file).use { s ->
            val h = ByteArray(32)
            val n = s.read(h)
            if (n <= 0) SignatureValidator.DetectedFormat.UNKNOWN else SignatureValidator.detectFromBytes(h, n)
        }
    } catch (e: Exception) {
        SignatureValidator.DetectedFormat.UNKNOWN
    }

    private fun formatSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1) String.format(Locale.US, "%.1f MB", mb) else String.format(Locale.US, "%.0f KB", kb)
    }

    private fun formatDate(epochMillis: Long): String = try {
        SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(epochMillis))
    } catch (e: Exception) { "" }
}