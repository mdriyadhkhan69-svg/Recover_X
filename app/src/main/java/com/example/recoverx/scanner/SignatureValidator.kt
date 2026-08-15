package com.example.recoverx.scanner

import android.content.Context
import android.net.Uri

/**
 * Magic-number validation for candidates the app already has legitimate read access to
 * (via MediaStore, SAF, or a readable filesystem path). This never touches raw/encrypted
 * storage — it only confirms whether bytes Android is already exposing to this app look
 * like a real file of the claimed type, so we don't show random/corrupt bytes as a result.
 */
object SignatureValidator {

    enum class DetectedFormat { JPEG, PNG, WEBP, GIF, HEIC, MP4_MOV_3GP, MKV, PDF, ZIP_OR_OFFICE, UNKNOWN }

    fun detect(context: Context, uri: Uri): DetectedFormat {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(32)
                val read = stream.read(header)
                if (read <= 0) return DetectedFormat.UNKNOWN
                detectFromBytes(header, read)
            } ?: DetectedFormat.UNKNOWN
        } catch (e: Exception) {
            DetectedFormat.UNKNOWN
        }
    }

    fun detectFromBytes(header: ByteArray, read: Int): DetectedFormat {
        fun matches(offset: Int, vararg bytes: Int): Boolean {
            if (offset + bytes.size > read) return false
            for (i in bytes.indices) {
                if ((header[offset + i].toInt() and 0xFF) != bytes[i]) return false
            }
            return true
        }
        return when {
            matches(0, 0xFF, 0xD8, 0xFF) -> DetectedFormat.JPEG
            matches(0, 0x89, 0x50, 0x4E, 0x47) -> DetectedFormat.PNG
            matches(0, 0x47, 0x49, 0x46, 0x38) -> DetectedFormat.GIF
            matches(0, 0x25, 0x50, 0x44, 0x46) -> DetectedFormat.PDF
            matches(0, 0x50, 0x4B, 0x03, 0x04) -> DetectedFormat.ZIP_OR_OFFICE // also docx/xlsx/pptx
            matches(0, 0x52, 0x49, 0x46, 0x46) && matches(8, 0x57, 0x45, 0x42, 0x50) -> DetectedFormat.WEBP
            read >= 12 && matches(4, 'f'.code, 't'.code, 'y'.code, 'p'.code) -> {
                // ftyp box: covers mp4/mov/3gp/heic variants; brand at offset 8 distinguishes heic
                val brand = String(header, 8, minOf(4, read - 8))
                if (brand.startsWith("heic") || brand.startsWith("mif1") || brand.startsWith("heix")) DetectedFormat.HEIC
                else DetectedFormat.MP4_MOV_3GP
            }
            matches(0, 0x1A, 0x45, 0xDF, 0xA3) -> DetectedFormat.MKV
            else -> DetectedFormat.UNKNOWN
        }
    }

    fun isPlausibleFor(category: com.example.recoverx.model.FileCategory, format: DetectedFormat): Boolean = when (category) {
        com.example.recoverx.model.FileCategory.PHOTO -> format in setOf(DetectedFormat.JPEG, DetectedFormat.PNG, DetectedFormat.WEBP, DetectedFormat.GIF, DetectedFormat.HEIC)
        com.example.recoverx.model.FileCategory.VIDEO -> format in setOf(DetectedFormat.MP4_MOV_3GP, DetectedFormat.MKV)
        com.example.recoverx.model.FileCategory.DOCUMENT -> format in setOf(DetectedFormat.PDF, DetectedFormat.ZIP_OR_OFFICE)
    }
}