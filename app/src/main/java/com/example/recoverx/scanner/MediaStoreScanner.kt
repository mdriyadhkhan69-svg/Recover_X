package com.example.recoverx.scanner

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.recoverx.model.ConfidenceLevel
import com.example.recoverx.model.FileCategory
import com.example.recoverx.model.LiveStatus
import com.example.recoverx.model.RecoveryConfidence
import com.example.recoverx.model.RecoverySourceKind
import com.example.recoverx.model.ScanSource
import com.example.recoverx.model.ScannedFile
import com.example.recoverx.model.detectDocumentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MediaStoreScanner {

    private const val TAG = "MediaStoreScanner"
    private const val PROGRESS_BATCH_SIZE = 25

    suspend fun countTotal(
        context: Context,
        includeImages: Boolean,
        includeVideos: Boolean,
        includeDocuments: Boolean
    ): Int = withContext(Dispatchers.IO) {
        var total = 0
        if (includeImages) {
            total += safeCountRows(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, trashed = false)
            total += safeCountRows(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, trashed = true)
        }
        if (includeVideos) {
            total += safeCountRows(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, trashed = false)
            total += safeCountRows(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, trashed = true)
        }
        if (includeDocuments && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val files = MediaStore.Files.getContentUri("external")
            total += safeCountRows(context, files, trashed = false, documentsOnly = true)
            total += safeCountRows(context, files, trashed = true, documentsOnly = true)
        }
        total.coerceAtLeast(1)
    }

    private fun safeCountRows(context: Context, uri: Uri, trashed: Boolean, documentsOnly: Boolean = false): Int {
        return try {
            countRows(context, uri, trashed, documentsOnly)
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to count: ${e.message}")
            0
        } catch (e: Exception) {
            Log.w(TAG, "Count failed: ${e.message}")
            0
        }
    }

    private fun countRows(context: Context, uri: Uri, trashed: Boolean, documentsOnly: Boolean = false): Int {
        if (trashed && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return 0
        val (selection, args) = buildSelection(trashed, documentsOnly)
        return queryMedia(context, uri, arrayOf(MediaStore.MediaColumns._ID), selection, args, null, trashed)
            ?.use { it.count } ?: 0
    }

    /**
     * Documented way to ask MediaStore for trashed rows: the Bundle overload (API 30+).
     * trashedOnly=false explicitly excludes trashed rows so live and trash never mix.
     */
    private fun queryMedia(
        context: Context,
        uri: Uri,
        projection: Array<String>,
        selection: String?,
        args: Array<String>?,
        sortOrder: String?,
        trashedOnly: Boolean
    ): Cursor? {
        val bundle = Bundle().apply {
            if (selection != null) putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            if (args != null) putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
            if (sortOrder != null) putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                putInt(
                    MediaStore.QUERY_ARG_MATCH_TRASHED,
                    if (trashedOnly) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE
                )
            }
        }
        return context.contentResolver.query(uri, projection, bundle, null)
    }

    private fun buildSelection(trashed: Boolean, documentsOnly: Boolean): Pair<String?, Array<String>?> {
        if (documentsOnly) {
            // MediaStore only auto-classifies a narrow set of mime types as MEDIA_TYPE_DOCUMENT,
            // so we also match known document MIME types directly.
            val mimePrefixes = arrayOf(
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument%",
                "application/vnd.ms-excel",
                "application/vnd.ms-powerpoint",
                "text/plain",
                "application/zip",
                "application/x-zip-compressed"
            )
            val clauses = mimePrefixes.joinToString(" OR ") { "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?" }
            val selection = "($clauses) OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
            val args = mimePrefixes.map { if (it.endsWith("%")) it else "$it%" }.toTypedArray() +
                    arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_DOCUMENT.toString())
            return selection to args
        }
        return null to null
    }

    suspend fun scan(
        context: Context,
        includeImages: Boolean,
        includeVideos: Boolean,
        includeDocuments: Boolean,
        extraFolderUris: Set<String> = emptySet(),
        onProgress: (scanned: Int, found: Int) -> Unit
    ): List<ScannedFile> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ScannedFile>()
        var scanned = 0

        if (includeImages) {
            scanned = safeScanMedia(
                context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, FileCategory.PHOTO,
                trashed = false, results = results, startScanned = scanned, onProgress = onProgress
            )
            scanned = safeScanMedia(
                context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, FileCategory.PHOTO,
                trashed = true, results = results, startScanned = scanned, onProgress = onProgress
            )
        }
        if (includeVideos) {
            scanned = safeScanMedia(
                context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, FileCategory.VIDEO,
                trashed = false, results = results, startScanned = scanned, onProgress = onProgress
            )
            scanned = safeScanMedia(
                context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, FileCategory.VIDEO,
                trashed = true, results = results, startScanned = scanned, onProgress = onProgress
            )
        }
        if (includeDocuments && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scanned = safeScanDocuments(context, results, scanned, trashed = false, onProgress = onProgress)
            scanned = safeScanDocuments(context, results, scanned, trashed = true, onProgress = onProgress)
        }
        if (extraFolderUris.isNotEmpty()) {
            scanned = safeScanSafFolders(
                context, extraFolderUris, includeImages, includeVideos, includeDocuments,
                results, scanned, onProgress
            )
        }
        onProgress(results.size, results.size)
        results
    }

    private fun safeScanSafFolders(
        context: Context,
        treeUris: Set<String>,
        includeImages: Boolean,
        includeVideos: Boolean,
        includeDocuments: Boolean,
        results: MutableList<ScannedFile>,
        startScanned: Int,
        onProgress: (Int, Int) -> Unit
    ): Int {
        var scanned = startScanned
        for (treeUriString in treeUris) {
            try {
                val treeUri = Uri.parse(treeUriString)
                val root = DocumentFile.fromTreeUri(context, treeUri)
                if (root != null && root.isDirectory) {
                    scanned = scanDocumentTree(
                        root, includeImages, includeVideos, includeDocuments,
                        results, scanned, onProgress
                    )
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "No SAF folder access: ${e.message}")
            } catch (e: Exception) {
                Log.w(TAG, "SAF folder scan failed: ${e.message}")
            }
        }
        return scanned
    }

    /**
     * SAF only exposes ALLOCATED files. A live file is never a candidate. Only files with real
     * recovery evidence (RecoveryEvidence: .trashed-/.pending- names, trash/LOST.DIR folders) are kept.
     * ancestorNames: nearest-first names of the folders above [dir].
     */
    private fun scanDocumentTree(
        dir: DocumentFile,
        includeImages: Boolean,
        includeVideos: Boolean,
        includeDocuments: Boolean,
        results: MutableList<ScannedFile>,
        startScanned: Int,
        onProgress: (Int, Int) -> Unit,
        ancestorNames: List<String> = emptyList()
    ): Int {
        var scanned = startScanned
        val trail = (listOf(dir.name ?: "") + ancestorNames).take(8)
        val children = try { dir.listFiles() } catch (e: Exception) { emptyArray() }
        for (child in children) {
            try {
                if (child.isDirectory) {
                    scanned = scanDocumentTree(
                        child, includeImages, includeVideos, includeDocuments,
                        results, scanned, onProgress, trail
                    )
                    continue
                }
                val rawName = child.name ?: "Unknown file"
                val evidence = RecoveryEvidence.classify(rawName, trail)
                if (evidence != null) {
                    val name = RecoveryEvidence.cleanName(rawName)
                    val mime = child.type ?: ""
                    val category = when {
                        mime.startsWith("image/") -> FileCategory.PHOTO
                        mime.startsWith("video/") -> FileCategory.VIDEO
                        RecoveryEvidence.isDocumentName(name) -> FileCategory.DOCUMENT
                        else -> null
                    }
                    val wanted = when (category) {
                        FileCategory.PHOTO -> includeImages
                        FileCategory.VIDEO -> includeVideos
                        FileCategory.DOCUMENT -> includeDocuments
                        null -> false
                    }
                    val size = child.length()
                    if (category != null && wanted && size > 0) {
                        results.add(
                            ScannedFile(
                                id = "saf-${child.uri}",
                                name = name,
                                sizeLabel = formatSize(size),
                                category = category,
                                confidence = RecoveryConfidence.ON_DEVICE,
                                uriString = child.uri.toString(),
                                dateAddedLabel = formatDate(child.lastModified() / 1000),
                                documentType = detectDocumentType(name, child.type),
                                liveStatus = LiveStatus.POSSIBLY_RECOVERABLE,
                                confidenceLevel = evidence.level,
                                sizeBytes = size,
                                dedupeKey = "$name-$size",
                                source = ScanSource.SAF,
                                sourceKind = evidence.kind,
                                evidence = evidence.text,
                                mimeType = child.type
                            )
                        )
                    }
                }
            } catch (rowError: Exception) {
                Log.w(TAG, "SAF entry unreadable, skipped: ${rowError.message}")
            }
            scanned++
            if (scanned % PROGRESS_BATCH_SIZE == 0) {
                onProgress(scanned, results.size)
            }
        }
        return scanned
    }

    private fun safeScanMedia(
        context: Context, baseUri: Uri, category: FileCategory, trashed: Boolean,
        results: MutableList<ScannedFile>, startScanned: Int, onProgress: (Int, Int) -> Unit
    ): Int {
        return try {
            scanMedia(context, baseUri, category, trashed, results, startScanned, onProgress)
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to scan (${category.name}): ${e.message}")
            startScanned
        } catch (e: Exception) {
            Log.w(TAG, "Scan failed (${category.name}): ${e.message}")
            startScanned
        }
    }

    private fun safeScanDocuments(
        context: Context, results: MutableList<ScannedFile>, startScanned: Int,
        trashed: Boolean, onProgress: (Int, Int) -> Unit
    ): Int {
        return try {
            scanDocuments(context, results, startScanned, trashed, onProgress)
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission for document scan: ${e.message}")
            startScanned
        } catch (e: Exception) {
            Log.w(TAG, "Document scan failed: ${e.message}")
            startScanned
        }
    }

    private fun trashEvidence(relativePath: String?, expirySec: Long): String = buildString {
        append("MediaStore marks this item as trashed")
        if (!relativePath.isNullOrBlank()) append(" (was in $relativePath)")
        if (expirySec > 0) append("; Android permanently deletes it on ${formatDate(expirySec)}")
    }

    private fun scanMedia(
        context: Context,
        baseUri: Uri,
        category: FileCategory,
        trashed: Boolean,
        results: MutableList<ScannedFile>,
        startScanned: Int,
        onProgress: (Int, Int) -> Unit
    ): Int {
        if (trashed && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return startScanned
        var scanned = startScanned

        val projection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.MIME_TYPE
        )
        if (trashed) {
            projection += MediaStore.MediaColumns.IS_TRASHED
            projection += MediaStore.MediaColumns.DATE_EXPIRES
            projection += MediaStore.MediaColumns.RELATIVE_PATH
        }

        val cursor = queryMedia(
            context, baseUri, projection.toTypedArray(), null, null,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC", trashed
        ) ?: return scanned

        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val mimeCol = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val trashedCol = if (trashed) c.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED) else -1
            val expCol = if (trashed) c.getColumnIndex(MediaStore.MediaColumns.DATE_EXPIRES) else -1
            val relCol = if (trashed) c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH) else -1

            while (c.moveToNext()) {
                try {
                    // Hard guard: a row is only ever called "trashed" if the provider says IS_TRASHED=1.
                    val reallyTrashed = !trashed || (trashedCol >= 0 && c.getInt(trashedCol) == 1)
                    if (reallyTrashed) {
                        val id = c.getLong(idCol)
                        val rawName = c.getString(nameCol) ?: "Unknown file"
                        val name = if (trashed) RecoveryEvidence.cleanName(rawName) else rawName
                        val size = c.getLong(sizeCol)
                        val dateAdded = c.getLong(dateCol)
                        val mime = if (mimeCol >= 0) c.getString(mimeCol) else null
                        val prefix = when (category) { FileCategory.PHOTO -> "img"; FileCategory.VIDEO -> "vid"; else -> "file" }
                        val expiry = if (expCol >= 0) c.getLong(expCol) else 0L
                        val rel = if (relCol >= 0) c.getString(relCol) else null
                        results.add(
                            ScannedFile(
                                id = "$prefix-$id-${if (trashed) "trash" else "live"}",
                                name = name,
                                sizeLabel = formatSize(size),
                                category = category,
                                confidence = if (trashed) RecoveryConfidence.TRASHED else RecoveryConfidence.ON_DEVICE,
                                uriString = ContentUris.withAppendedId(baseUri, id).toString(),
                                dateAddedLabel = formatDate(dateAdded),
                                liveStatus = if (trashed) LiveStatus.RECOVERABLE else LiveStatus.LIVE,
                                confidenceLevel = if (trashed) ConfidenceLevel.HIGH else ConfidenceLevel.MEDIUM,
                                sizeBytes = size,
                                dedupeKey = "$name-$size",
                                source = if (trashed) ScanSource.TRASH else ScanSource.MEDIASTORE,
                                sourceKind = if (trashed) RecoverySourceKind.MEDIASTORE_TRASH else RecoverySourceKind.LIVE_EXISTING,
                                evidence = if (trashed) trashEvidence(rel, expiry) else "",
                                mimeType = mime
                            )
                        )
                    }
                } catch (rowError: Exception) {
                    Log.w(TAG, "Row unreadable, skipped: ${rowError.message}")
                }
                scanned++
                if (scanned % PROGRESS_BATCH_SIZE == 0) {
                    onProgress(scanned, results.size)
                }
            }
        }
        return scanned
    }

    private fun scanDocuments(
        context: Context,
        results: MutableList<ScannedFile>,
        startScanned: Int,
        trashed: Boolean,
        onProgress: (Int, Int) -> Unit
    ): Int {
        if (trashed && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return startScanned
        var scanned = startScanned
        val uri = MediaStore.Files.getContentUri("external")
        val projection = mutableListOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.MIME_TYPE
        )
        if (trashed) {
            projection += MediaStore.MediaColumns.IS_TRASHED
            projection += MediaStore.MediaColumns.DATE_EXPIRES
            projection += MediaStore.MediaColumns.RELATIVE_PATH
        }
        val (selection, args) = buildSelection(trashed = trashed, documentsOnly = true)

        val cursor = queryMedia(
            context, uri, projection.toTypedArray(), selection, args,
            "${MediaStore.Files.FileColumns.DATE_ADDED} DESC", trashed
        ) ?: return scanned

        cursor.use { c ->
            val idCol = c.getColumnIndex(MediaStore.Files.FileColumns._ID)
            val nameCol = c.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
            val dateCol = c.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)
            val mimeCol = c.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
            val trashedCol = if (trashed) c.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED) else -1
            val expCol = if (trashed) c.getColumnIndex(MediaStore.MediaColumns.DATE_EXPIRES) else -1
            val relCol = if (trashed) c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH) else -1
            if (idCol < 0 || nameCol < 0) {
                Log.w(TAG, "Document cursor missing required columns, skipping")
                return@use
            }
            while (c.moveToNext()) {
                try {
                    val reallyTrashed = !trashed || (trashedCol >= 0 && c.getInt(trashedCol) == 1)
                    if (reallyTrashed) {
                        val id = c.getLong(idCol)
                        val rawName = c.getString(nameCol) ?: "Unknown document"
                        val name = if (trashed) RecoveryEvidence.cleanName(rawName) else rawName
                        val size = if (sizeCol >= 0) c.getLong(sizeCol) else 0L
                        val dateAdded = if (dateCol >= 0) c.getLong(dateCol) else 0L
                        val mime = if (mimeCol >= 0) c.getString(mimeCol) else null
                        val expiry = if (expCol >= 0) c.getLong(expCol) else 0L
                        val rel = if (relCol >= 0) c.getString(relCol) else null
                        results.add(
                            ScannedFile(
                                id = if (trashed) "doc-$id-trash" else "doc-$id",
                                name = name,
                                sizeLabel = formatSize(size),
                                category = FileCategory.DOCUMENT,
                                confidence = if (trashed) RecoveryConfidence.TRASHED else RecoveryConfidence.ON_DEVICE,
                                uriString = ContentUris.withAppendedId(uri, id).toString(),
                                dateAddedLabel = formatDate(dateAdded),
                                documentType = detectDocumentType(name, mime),
                                liveStatus = if (trashed) LiveStatus.RECOVERABLE else LiveStatus.LIVE,
                                confidenceLevel = if (trashed) ConfidenceLevel.HIGH else ConfidenceLevel.MEDIUM,
                                sizeBytes = size,
                                dedupeKey = "$name-$size",
                                source = if (trashed) ScanSource.TRASH else ScanSource.MEDIASTORE,
                                sourceKind = if (trashed) RecoverySourceKind.MEDIASTORE_TRASH else RecoverySourceKind.LIVE_EXISTING,
                                evidence = if (trashed) trashEvidence(rel, expiry) else "",
                                mimeType = mime
                            )
                        )
                    }
                } catch (rowError: Exception) {
                    Log.w(TAG, "Document row unreadable, skipped: ${rowError.message}")
                }
                scanned++
                if (scanned % PROGRESS_BATCH_SIZE == 0) {
                    onProgress(scanned, results.size)
                }
            }
        }
        return scanned
    }

    private fun formatSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1) String.format(Locale.US, "%.1f MB", mb)
        else String.format(Locale.US, "%.0f KB", kb)
    }

    private fun formatDate(epochSeconds: Long): String {
        return try {
            SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(epochSeconds * 1000))
        } catch (e: Exception) { "" }
    }
}