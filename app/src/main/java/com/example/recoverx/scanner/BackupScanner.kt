package com.example.recoverx.scanner

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import com.example.recoverx.backup.MediaBackupManager
import com.example.recoverx.model.ConfidenceLevel
import com.example.recoverx.model.FileCategory
import com.example.recoverx.model.LiveStatus
import com.example.recoverx.model.RecoveryConfidence
import com.example.recoverx.model.RecoverySourceKind
import com.example.recoverx.model.ScanSource
import com.example.recoverx.model.ScannedFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lists auto-backup copies whose original is no longer in MediaStore (live or trash). */
object BackupScanner {
    private val NAME = Regex("^(img|vid)(\\d+)__(.+)$")

    fun scan(context: Context, includeImages: Boolean, includeVideos: Boolean): List<ScannedFile> {
        val files = MediaBackupManager.backupDir(context).listFiles() ?: return emptyList()
        val out = mutableListOf<ScannedFile>()
        for (f in files) {
            try {
                val m = NAME.matchEntire(f.name) ?: continue
                val isImg = m.groupValues[1] == "img"
                if (isImg && !includeImages) continue
                if (!isImg && !includeVideos) continue
                val id = m.groupValues[2].toLong()
                val len = f.length()
                if (len <= 0L) continue
                val base = if (isImg) MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                if (existsInMediaStore(context, base, id, len)) continue
                val name = m.groupValues[3]
                out.add(
                    ScannedFile(
                        id = "backup-${f.name}",
                        name = name,
                        sizeLabel = formatSize(len),
                        category = if (isImg) FileCategory.PHOTO else FileCategory.VIDEO,
                        confidence = RecoveryConfidence.ON_DEVICE,
                        uriString = Uri.fromFile(f).toString(),
                        dateAddedLabel = formatDate(f.lastModified()),
                        liveStatus = LiveStatus.RECOVERABLE,
                        confidenceLevel = ConfidenceLevel.HIGH,
                        sizeBytes = len,
                        dedupeKey = "$name-$len",
                        source = ScanSource.BACKUP,
                        sourceKind = RecoverySourceKind.BACKUP_COPY,
                        evidence = "Copy saved by RecoverX auto-backup. The original is no longer in the gallery or trash."
                    )
                )
            } catch (e: Exception) {
                // skip unreadable entry
            }
        }
        return out
    }

    private fun existsInMediaStore(context: Context, base: Uri, id: Long, size: Long): Boolean {
        val b = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.MediaColumns._ID} = ? AND ${MediaStore.MediaColumns.SIZE} = ?"
            )
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(id.toString(), size.toString()))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
            }
        }
        return context.contentResolver.query(base, arrayOf(MediaStore.MediaColumns._ID), b, null)
            ?.use { it.count > 0 } ?: false
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