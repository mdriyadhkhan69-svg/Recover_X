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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

/**
 * Recursive filesystem walker over app-accessible storage roots (internal + SD card + the
 * standard media subfolders). Does NOT touch other apps' private storage, does NOT attempt
 * Android/data or Android/obb unless the OS already grants read access to this app for it, and
 * does NOT follow paths outside what File.canRead() confirms is legitimately readable.
 *
 * Cycle/symlink protection: canonical paths are tracked per top-level walk so a symlink loop
 * cannot cause unbounded recursion.
 */
object FileSystemScanner {

    private const val TAG = "FileSystemScanner"
    private const val MAX_DEPTH = 40
    private const val PROGRESS_BATCH = 40

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
        if (!visitedCanonical.add(canonical)) return scanned // already visited -> cycle guard

        // Don't attempt other apps' private sandboxes; only skip if unreadable, never force access.
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
                    scanned = walk(child, depth + 1, visitedCanonical, liveMediaPaths, results, scanned, onProgress, onSkipped)
                    continue
                }
                val ext = child.extension.lowercase()
                val category = when {
                    ext in IMAGE_EXT -> FileCategory.PHOTO
                    ext in VIDEO_EXT -> FileCategory.VIDEO
                    ext in DOC_EXT -> FileCategory.DOCUMENT
                    else -> null
                }
                if (category != null && child.length() > 0) {
                    val isLive = liveMediaPaths.contains(child.absolutePath)
                    results.add(
                        ScannedFile(
                            id = "fs-${child.absolutePath.hashCode()}",
                            name = child.name,
                            sizeLabel = formatSize(child.length()),
                            category = category,
                            confidence = RecoveryConfidence.ON_DEVICE,
                            uriString = Uri.fromFile(child).toString(),
                            dateAddedLabel = formatDate(child.lastModified()),
                            documentType = if (category == FileCategory.DOCUMENT) detectDocumentType(child.name, null) else DocumentType.OTHER,
                            liveStatus = if (isLive) LiveStatus.LIVE else LiveStatus.POSSIBLY_RECOVERABLE,
                            sizeBytes = child.length(),
                            dedupeKey = "${child.name}-${child.length()}",
                            source = ScanSource.FILESYSTEM
                        )
                    )
                }
            } catch (rowError: Exception) {
                Log.w(TAG, "Entry পড়া যায়নি, স্কিপ করা হলো: ${rowError.message}")
            }
            scanned++
            if (scanned % PROGRESS_BATCH == 0) {
                onProgress(scanned, results.size, "Scanning ${dir.name}...")
            }
        }
        return scanned
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