package com.example.recoverx.scanner

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.example.recoverx.model.FileCategory
import com.example.recoverx.model.ScannedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MediaStoreScanner {

    // Scan শুরুর আগে progress bar-এর জন্য মোটামুটি কতগুলো ফাইল আছে তা count করা
    suspend fun countTotal(
        context: Context,
        includeImages: Boolean,
        includeVideos: Boolean,
        includeDocuments: Boolean
    ): Int = withContext(Dispatchers.IO) {
        var total = 0
        if (includeImages) total += countRows(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, null)
        if (includeVideos) total += countRows(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null, null)
        if (includeDocuments && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            total += countRows(
                context,
                MediaStore.Files.getContentUri("external"),
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?",
                arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_DOCUMENT.toString())
            )
        }
        total.coerceAtLeast(1) // divide-by-zero এড়ানোর জন্য
    }

    private fun countRows(context: Context, uri: android.net.Uri, selection: String?, args: Array<String>?): Int {
        return context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), selection, args, null)
            ?.use { it.count } ?: 0
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
            scanned = scanImages(context, results, scanned, onProgress)
        }
        if (includeVideos) {
            scanned = scanVideos(context, results, scanned, onProgress)
        }
        if (includeDocuments) {
            scanned = scanDocuments(context, results, scanned, onProgress)
        }
        results
    }

    private fun scanImages(
        context: Context,
        results: MutableList<ScannedFile>,
        startScanned: Int,
        onProgress: (Int, Int) -> Unit
    ): Int {
        var scanned = startScanned
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED
        )
        context.contentResolver.query(uri, projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "Unknown image"
                val size = cursor.getLong(sizeCol)
                val dateAdded = cursor.getLong(dateCol)
                results.add(
                    ScannedFile(
                        id = "img-$id",
                        name = name,
                        sizeLabel = formatSize(size),
                        category = FileCategory.PHOTO,
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

    private fun scanVideos(
        context: Context,
        results: MutableList<ScannedFile>,
        startScanned: Int,
        onProgress: (Int, Int) -> Unit
    ): Int {
        var scanned = startScanned
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED
        )
        context.contentResolver.query(uri, projection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "Unknown video"
                val size = cursor.getLong(sizeCol)
                val dateAdded = cursor.getLong(dateCol)
                results.add(
                    ScannedFile(
                        id = "vid-$id",
                        name = name,
                        sizeLabel = formatSize(size),
                        category = FileCategory.VIDEO,
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

    // Documents: Android 10 (API 29)+ এ MediaStore.Files দিয়ে metadata পাওয়া যায়।
    // এর নিচের Android version-এ এবং app-এর নিজের বাইরে থাকা অনেক ডকুমেন্টের ক্ষেত্রে
    // কভারেজ সীমিত থাকবে — এটা Android-এর scoped storage limitation, library দিয়ে বাইপাস সম্ভব না।
    private fun scanDocuments(
        context: Context,
        results: MutableList<ScannedFile>,
        startScanned: Int,
        onProgress: (Int, Int) -> Unit
    ): Int {
        var scanned = startScanned
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return scanned

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
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.US)
            sdf.format(Date(epochSeconds * 1000))
        } catch (e: Exception) {
            ""
        }
    }
}