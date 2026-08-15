package com.example.recoverx.scanner

import android.content.Context
import android.net.Uri
import com.example.recoverx.model.FileCategory
import com.example.recoverx.model.LiveStatus
import com.example.recoverx.model.RecoveryConfidence
import com.example.recoverx.model.ScanSource
import com.example.recoverx.model.ScannedFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Looks for recoverable evidence in app-accessible cache/thumbnail directories only — e.g. this
 * app's own cache dir and any world-readable ".thumbnails" folder still exposed under scoped
 * storage on the current device. Does NOT attempt to read other apps' private cache (not
 * accessible without root, which is explicitly out of scope). Results are always labeled
 * Thumbnail Recovery, never claimed as the original file.
 */
object ThumbnailCacheScanner {

    private val THUMB_DIR_NAMES = setOf(".thumbnails", "thumbnails", "thumbs")

    fun scan(
        context: Context,
        storageRoots: List<File>,
        onProgress: (String) -> Unit
    ): List<ScannedFile> {
        val results = mutableListOf<ScannedFile>()

        // App's own cache — always legitimately accessible.
        context.externalCacheDir?.let { scanDir(it, results) }
        context.cacheDir.let { scanDir(it, results) }

        // Shallow probe (2 levels) of known thumbnail folder names under each discovered root;
        // most are inaccessible under modern scoped storage and will simply be skipped.
        for (root in storageRoots) {
            onProgress("Scanning thumbnails in ${root.name}...")
            THUMB_DIR_NAMES.forEach { name ->
                val candidate = File(root, name)
                if (candidate.exists() && candidate.canRead()) {
                    scanDir(candidate, results, maxDepth = 2)
                }
            }
        }
        return results
    }

    private fun scanDir(dir: File, results: MutableList<ScannedFile>, depth: Int = 0, maxDepth: Int = 1) {
        if (depth > maxDepth) return
        val children = try { dir.listFiles() } catch (e: Exception) { null } ?: return
        for (child in children) {
            try {
                if (child.isDirectory) {
                    scanDir(child, results, depth + 1, maxDepth)
                    continue
                }
                val ext = child.extension.lowercase()
                if (ext !in setOf("jpg", "jpeg", "png", "webp")) continue
                if (child.length() <= 0) continue
                results.add(
                    ScannedFile(
                        id = "thumb-${child.absolutePath.hashCode()}",
                        name = child.name,
                        sizeLabel = formatSize(child.length()),
                        category = FileCategory.PHOTO,
                        confidence = RecoveryConfidence.ON_DEVICE,
                        uriString = Uri.fromFile(child).toString(),
                        dateAddedLabel = formatDate(child.lastModified()),
                        liveStatus = LiveStatus.POSSIBLY_RECOVERABLE,
                        sizeBytes = child.length(),
                        dedupeKey = "${child.name}-${child.length()}",
                        source = ScanSource.THUMBNAIL
                    )
                )
            } catch (e: Exception) {
                // skip unreadable entry, don't abort the rest of the cache scan
            }
        }
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