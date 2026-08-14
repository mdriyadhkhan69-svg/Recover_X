package com.example.recoverx.ui.recovery

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.recoverx.model.FileCategory
import com.example.recoverx.model.RecoveryConfidence
import com.example.recoverx.model.ScannedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class RecoveryResult {
    data class Success(val recoveredUri: String? = null) : RecoveryResult()
    data class NeedsPermission(val intentSender: IntentSender) : RecoveryResult()
    data class Failed(val reason: String) : RecoveryResult()
}

object RecoveryEngine {

    suspend fun recover(context: Context, file: ScannedFile): RecoveryResult = withContext(Dispatchers.IO) {
        val uriString = file.uriString
        if (uriString == null) return@withContext RecoveryResult.Failed("File location অজানা")
        val uri = Uri.parse(uriString)

        when (file.confidence) {
            RecoveryConfidence.TRASHED -> untrash(context, uri)
            RecoveryConfidence.ON_DEVICE -> copyToRecoveredFolder(context, file, uri)
        }
    }

    // Trash থেকে ফাইল restore করা — এটাই সবচেয়ে "genuine" recovery ফিচার এই app-এর
    private fun untrash(context: Context, uri: Uri): RecoveryResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return RecoveryResult.Failed("এই Android version-এ trash restore সম্ভব না")
        }
        return try {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 0) }
            val rows = context.contentResolver.update(uri, values, null, null)
            if (rows > 0) RecoveryResult.Success(uri.toString()) else RecoveryResult.Failed("Restore করা যায়নি")
        } catch (e: RecoverableSecurityException) {
            // Android অনেক সময় owner app না হলে user-এর explicit confirmation চায়
            RecoveryResult.NeedsPermission(e.userAction.actionIntent.intentSender)
        } catch (e: SecurityException) {
            RecoveryResult.Failed("Permission নেই এই ফাইল restore করার জন্য")
        } catch (e: Exception) {
            RecoveryResult.Failed(e.message ?: "Unknown error")
        }
    }

    // এখনো device-এ থাকা ফাইলের একটা কপি app-এর নিজস্ব Recovered ফোল্ডারে রাখা
    private fun copyToRecoveredFolder(context: Context, file: ScannedFile, sourceUri: Uri): RecoveryResult {
        return try {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(sourceUri) ?: guessMimeType(file.category)

            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/RecoverX/Recovered")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val destUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return RecoveryResult.Failed("Destination তৈরি করা যায়নি")

            val inputStream = resolver.openInputStream(sourceUri)
                ?: return RecoveryResult.Failed("Source file পড়া যায়নি")
            val outputStream = resolver.openOutputStream(destUri)
                ?: return RecoveryResult.Failed("Destination-এ লেখা যায়নি")

            inputStream.use { input -> outputStream.use { output -> input.copyTo(output) } }

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(destUri, values, null, null)

            RecoveryResult.Success(destUri.toString())
        } catch (e: Exception) {
            RecoveryResult.Failed(e.message ?: "Unknown error")
        }
    }

    private fun guessMimeType(category: FileCategory): String = when (category) {
        FileCategory.PHOTO -> "image/jpeg"
        FileCategory.VIDEO -> "video/mp4"
        FileCategory.DOCUMENT -> "application/octet-stream"
    }
}