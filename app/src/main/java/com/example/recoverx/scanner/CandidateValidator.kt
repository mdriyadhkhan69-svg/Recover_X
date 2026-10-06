package com.example.recoverx.scanner

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.recoverx.model.DocumentType
import com.example.recoverx.model.FileCategory
import com.example.recoverx.model.ScannedFile

/** Structural validation. Never throws; any failure means "invalid candidate". */
object CandidateValidator {

    fun isValid(context: Context, f: ScannedFile): Boolean {
        val raw = f.uriString ?: return false
        if (f.sizeBytes <= 0L) return false
        return try {
            val uri = Uri.parse(raw)
            // MediaStore itself is the authority for a trash row. If the bytes can't be opened through
            // the plain URI at all (providers may refuse trashed rows), keep the row rather than silently
            // dropping a genuine trash item. Restore goes through MediaStore, not through reading bytes.
            if (f.sourceKind == com.example.recoverx.model.RecoverySourceKind.MEDIASTORE_TRASH && !canOpen(context, uri)) {
                return true
            }
            when (f.category) {
                FileCategory.PHOTO -> validImage(context, uri)
                FileCategory.VIDEO -> validVideo(context, uri)
                FileCategory.DOCUMENT -> validDocument(context, uri, f)
            }
        } catch (e: Exception) { false }
    }

    private fun canOpen(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.openInputStream(uri)?.use { true } ?: false
    } catch (e: Exception) { false }

    private fun validImage(context: Context, uri: Uri): Boolean {
        val fmt = SignatureValidator.detect(context, uri)
        if (!SignatureValidator.isPlausibleFor(FileCategory.PHOTO, fmt)) return false
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o); true } ?: return false
        return o.outWidth > 0 && o.outHeight > 0
    }

    private fun validVideo(context: Context, uri: Uri): Boolean {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes" &&
                    (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) > 0L
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
    }

    private fun validDocument(context: Context, uri: Uri, f: ScannedFile): Boolean {
        if (f.documentType == DocumentType.OTHER) return false // unknown junk is not a document candidate
        if (f.documentType == DocumentType.TXT) return true
        val fmt = SignatureValidator.detect(context, uri)
        return when (f.documentType) {
            DocumentType.PDF -> fmt == SignatureValidator.DetectedFormat.PDF
            DocumentType.ZIP -> fmt == SignatureValidator.DetectedFormat.ZIP_OR_OFFICE
            // legacy .doc/.xls/.ppt are OLE containers (not detected by SignatureValidator)
            else -> fmt == SignatureValidator.DetectedFormat.ZIP_OR_OFFICE ||
                    fmt == SignatureValidator.DetectedFormat.UNKNOWN
        }
    }
}