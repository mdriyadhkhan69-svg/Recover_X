package com.example.recoverx.scanner

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import com.example.recoverx.model.ConfidenceLevel
import com.example.recoverx.model.FileCategory
import com.example.recoverx.model.LiveStatus
import com.example.recoverx.model.RecoveryConfidence
import com.example.recoverx.model.RecoverySourceKind
import com.example.recoverx.model.ScanSource
import com.example.recoverx.model.ScannedFile
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Finds cached image previews in .thumbnails-style folders readable by this app.
 * Fixes vs. the old version:
 *  - probes DCIM/.thumbnails, Pictures/.thumbnails, etc. (not only <root>/.thumbnails)
 *  - no longer scans this app's own cache (that only held previews the app itself made)
 *  - accepts extension-less thumbnails by header bytes
 * Every result is RECOVERED_THUMBNAIL, LOW confidence, isOriginal=false. Nothing here can prove the
 * original is gone: the original may still exist, and the UI says so.
 * .thumbdata blobs are not parsed (they are not JPEG files); they are skipped, not misreported.
 */
object ThumbnailCacheScanner {

    private val THUMB_DIR_NAMES = setOf(".thumbnails", "thumbnails", ".thumbs", "thumbs")
    private val PARENT_DIRS = listOf(
        "", Environment.DIRECTORY_DCIM, Environment.DIRECTORY_PICTURES,
        Environment.DIRECTORY_MOVIES, Environment.DIRECTORY_DOWNLOADS
    )
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp")
    private const val MIN_BYTES = 2L * 1024 // below this it is an icon/stub, not a usable preview
    private const val MAX_DEPTH = 3

    fun scan(
        storageRoots: List<File>,
        onProgress: (count: Int, label: String) -> Unit
    ): List<ScannedFile> {
        val results = mutableListOf<ScannedFile>()
        val seen = mutableSetOf<String>()
        for (root in storageRoots) {
            onProgress(results.size, "Checking thumbnail caches in ${root.name}...")
            for (parent in PARENT_DIRS) {
                val base = if (parent.isEmpty()) root else File(root, parent)
                for (name in THUMB_DIR_NAMES) {
                    val dir = File(base, name)
                    if (dir.isDirectory && dir.canRead() && seen.add(dir.absolutePath)) {
                        scanDir(dir, results, 0)
                    }
                }
            }
            onProgress(results.size, "Checking thumbnail caches in ${root.name}...")
        }
        return results
    }

    private fun scanDir(dir: File, results: MutableList<ScannedFile>, depth: Int) {
        if (depth > MAX_DEPTH) return
        val children = try { dir.listFiles() } catch (e: Exception) { null } ?: return
        for (child in children) {
            try {
                if (child.isDirectory) {
                    scanDir(child, results, depth + 1)
                    continue
                }
                val len = child.length()
                if (len < MIN_BYTES) continue
                val fmt = sniff(child)
                if (!SignatureValidator.isPlausibleFor(FileCategory.PHOTO, fmt)) continue

                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(child.absolutePath, opts)
                if (opts.outWidth <= 0 || opts.outHeight <= 0) continue

                val ext = child.extension.lowercase()
                val name = if (ext in IMAGE_EXT) child.name
                else child.name + "." + (RecoveryEvidence.extensionFor(fmt) ?: "jpg")

                results.add(
                    ScannedFile(
                        id = "thumb-${child.absolutePath}",
                        name = name,
                        sizeLabel = formatSize(len),
                        category = FileCategory.PHOTO,
                        confidence = RecoveryConfidence.ON_DEVICE,
                        uriString = Uri.fromFile(child).toString(),
                        dateAddedLabel = formatDate(child.lastModified()),
                        liveStatus = LiveStatus.POSSIBLY_RECOVERABLE,
                        confidenceLevel = ConfidenceLevel.LOW,
                        sizeBytes = len,
                        dedupeKey = "${child.name}-$len",
                        source = ScanSource.THUMBNAIL,
                        sourceKind = RecoverySourceKind.RECOVERED_THUMBNAIL,
                        evidence = "Cached preview (${opts.outWidth}×${opts.outHeight}px) found in '${dir.name}'. " +
                                "This is a small preview, not the original file, and the original may still exist."
                    )
                )
            } catch (e: Exception) {
                // skip unreadable entry, keep scanning
            }
        }
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