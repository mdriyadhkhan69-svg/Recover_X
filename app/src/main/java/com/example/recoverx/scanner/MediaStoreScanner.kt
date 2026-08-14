package com.example.recoverx.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.recoverx.model.FileCategory
import com.example.recoverx.model.RecoveryConfidence
import com.example.recoverx.model.ScannedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MediaStoreScanner {

    private const val TAG = "MediaStoreScanner"
    private const val PROGRESS_BATCH_SIZE = 25 // এতগুলো ফাইলে একবার UI update হবে

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
            total += safeCountRows(context, MediaStore.Files.getContentUri("external"), trashed = false, documentsOnly = true)
        }
        total.coerceAtLeast(1)
    }

    private fun safeCountRows(context: Context, uri: Uri, trashed: Boolean, documentsOnly: Boolean = false): Int {
        return try {
            countRows(context, uri, trashed, documentsOnly)
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission নেই count করার জন্য: ${e.message}")
            0
        } catch (e: Exception) {
            Log.w(TAG, "Count ব্যর্থ হয়েছে: ${e.message}")
            0
        }
    }

    private fun countRows(context: Context, uri: Uri, trashed: Boolean, documentsOnly: Boolean = false): Int {
        val (selection, args) = buildSelection(trashed, documentsOnly)
        val queryUri = if (trashed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            uri.buildUpon().appendQueryParameter(MediaStore.QUERY_ARG_MATCH_TRASHED,MediaStore.MATCH_ONLY.toString()).build()
        } else uri
        return context.contentResolver.query(queryUri, arrayOf(MediaStore.MediaColumns._ID), selection, args, null)
            ?.use { it.count } ?: 0
    }

    private fun buildSelection(trashed: Boolean, documentsOnly: Boolean): Pair<String?, Array<String>?> {
        if (documentsOnly) {
            return "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?" to
                    arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_DOCUMENT.toString())
        }
        return null to null
    }

    suspend fun scan(
        context: Context,
        includeImages: Boolean,
        includeVideos: Boolean,
        includeDocuments: Boolean,
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
            safeScanDocuments(context, results, scanned, onProgress)
        }
        // চূড়ান্ত নিশ্চিতভাবে একবার সঠিক ফাইনাল কাউন্ট পাঠানো
        onProgress(results.size, results.size)
        results
    }

    private fun safeScanMedia(
        context: Context, baseUri: Uri, category: FileCategory, trashed: Boolean,
        results: MutableList<ScannedFile>, startScanned: Int, onProgress: (Int, Int) -> Unit
    ): Int {
        return try {
            scanMedia(context, baseUri, category, trashed, results, startScanned, onProgress)
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission নেই scan করার জন্য (${category.name}): ${e.message}")
            startScanned
        } catch (e: Exception) {
            Log.w(TAG, "Scan ব্যর্থ (${category.name}): ${e.message}")
            startScanned
        }
    }

    private fun safeScanDocuments(
        context: Context, results: MutableList<ScannedFile>, startScanned: Int, onProgress: (Int, Int) -> Unit
    ): Int {
        return try {
            scanDocuments(context, results, startScanned, onProgress)
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission নেই document scan করার জন্য: ${e.message}")
            startScanned
        } catch (e: Exception) {
            Log.w(TAG, "Document scan ব্যর্থ: ${e.message}")
            startScanned
        }
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
        var scanned = startScanned
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED
        )

        val queryUri = if (trashed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            baseUri.buildUpon().appendQueryParameter(MediaStore.QUERY_ARG_MATCH_TRASHED,MediaStore.MATCH_ONLY.toString()).build()
        } else if (trashed) {
            return scanned
        } else baseUri

        context.contentResolver.query(queryUri, projection, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            while (cursor.moveToNext()) {
                try {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Unknown file"
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateCol)
                    val prefix = when (category) { FileCategory.PHOTO -> "img"; FileCategory.VIDEO -> "vid"; else -> "file" }
                    results.add(
                        ScannedFile(
                            id = "$prefix-$id-${if (trashed) "trash" else "live"}",
                            name = name,
                            sizeLabel = formatSize(size),
                            category = category,
                            confidence = if (trashed) RecoveryConfidence.TRASHED else RecoveryConfidence.ON_DEVICE,
                            uriString = ContentUris.withAppendedId(baseUri, id).toString(),
                            dateAddedLabel = formatDate(dateAdded)
                        )
                    )
                } catch (rowError: Exception) {
                    Log.w(TAG, "একটা row পড়া যায়নি, স্কিপ করা হলো: ${rowError.message}")
                }
                scanned++
                // পারফরম্যান্সের জন্য প্রতি ফাইলে না, ব্যাচে UI update করা হচ্ছে
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
        onProgress: (Int, Int) -> Unit
    ): Int {
        var scanned = startScanned
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_ADDED
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
        val args = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_DOCUMENT.toString())

        context.contentResolver.query(uri, projection, selection, args, "${MediaStore.Files.FileColumns.DATE_ADDED} DESC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            while (cursor.moveToNext()) {
                try {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Unknown document"
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateCol)
                    results.add(
                        ScannedFile(
                            id = "doc-$id",
                            name = name,
                            sizeLabel = formatSize(size),
                            category = FileCategory.DOCUMENT,
                            confidence = RecoveryConfidence.ON_DEVICE,
                            uriString = ContentUris.withAppendedId(uri, id).toString(),
                            dateAddedLabel = formatDate(dateAdded)
                        )
                    )
                } catch (rowError: Exception) {
                    Log.w(TAG, "একটা document row পড়া যায়নি, স্কিপ করা হলো: ${rowError.message}")
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