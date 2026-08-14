package com.example.recoverx.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import com.example.recoverx.model.FileCategory
import com.example.recoverx.model.RecoveryConfidence
import com.example.recoverx.model.ScannedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MediaStoreScanner {

    suspend fun countTotal(
        context: Context,
        includeImages: Boolean,
        includeVideos: Boolean,
        includeDocuments: Boolean
    ): Int = withContext(Dispatchers.IO) {
        var total = 0
        if (includeImages) {
            total += countRows(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, trashed = false)
            total += countRows(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, trashed = true)
        }
        if (includeVideos) {
            total += countRows(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, trashed = false)
            total += countRows(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, trashed = true)
        }
        if (includeDocuments && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            total += countRows(context, MediaStore.Files.getContentUri("external"), trashed = false, documentsOnly = true)
        }
        total.coerceAtLeast(1)
    }

    private fun countRows(context: Context, uri: Uri, trashed: Boolean, documentsOnly: Boolean = false): Int {
        val (selection, args) = buildSelection(trashed, documentsOnly)
        val queryUri = if (trashed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            uri.buildUpon().appendQueryParameter(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY).build()
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
            scanned = scanMedia(
                context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, FileCategory.PHOTO,
                trashed = false, results = results, startScanned = scanned, onProgress = onProgress
            )
            scanned = scanMedia(
                context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, FileCategory.PHOTO,
                trashed = true, results = results, startScanned = scanned, onProgress = onProgress
            )
        }
        if (includeVideos) {
            scanned = scanMedia(
                context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, FileCategory.VIDEO,
                trashed = false, results = results, startScanned = scanned, onProgress = onProgress
            )
            scanned = scanMedia(
                context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, FileCategory.VIDEO,
                trashed = true, results = results, startScanned = scanned, onProgress = onProgress
            )
        }
        if (includeDocuments && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scanned = scanDocuments(context, results, scanned, onProgress)
        }
        results
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
            baseUri.buildUpon().appendQueryParameter(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY).build()
        } else if (trashed) {
            // Android 11-এর নিচে trash concept-ই নেই, তাই কিছুই করার নেই
            return scanned
        } else baseUri

        context.contentResolver.query(queryUri, projection, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            while (cursor.moveToNext()) {
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
                scanned++
                onProgress(scanned, results.size)
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
                scanned++
                onProgress(scanned, results.size)
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