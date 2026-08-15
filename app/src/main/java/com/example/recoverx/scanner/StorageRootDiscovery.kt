package com.example.recoverx.scanner

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Discovers every legitimately app-accessible storage root at runtime instead of relying on a
 * hard-coded folder list. Uses StorageManager.getStorageVolumes() (API 24+) as the primary source
 * — this legitimately enumerates internal storage AND any mounted removable/SD card the OS exposes
 * to apps — falling back to getExternalFilesDirs() derivation on older/unusual devices.
 *
 * Nothing here requests elevated access; volumes the OS doesn't expose simply don't appear.
 */
object StorageRootDiscovery {

    private const val TAG = "StorageRootDiscovery"

    private val COMMON_SUBDIRS = listOf(
        Environment.DIRECTORY_DCIM,
        Environment.DIRECTORY_PICTURES,
        Environment.DIRECTORY_MOVIES,
        Environment.DIRECTORY_DOWNLOADS,
        Environment.DIRECTORY_DOCUMENTS,
        Environment.DIRECTORY_MUSIC
    )

    data class DiscoveredRoot(val path: File, val isRemovable: Boolean, val label: String)

    fun discoverRoots(context: Context): List<DiscoveredRoot> {
        val roots = mutableListOf<DiscoveredRoot>()
        val seenPaths = mutableSetOf<String>()

        try {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            storageManager?.storageVolumes?.forEach { volume ->
                try {
                    val dir = volume.javaClass.getMethod("getPath").invoke(volume) as? String
                    // getPath() is hidden API on some versions; prefer the public directory() where available
                    val publicDir = try {
                        volume.directory
                    } catch (e: Throwable) { null }
                    val resolved = publicDir ?: dir?.let { File(it) }
                    if (resolved != null && resolved.canRead() && seenPaths.add(resolved.absolutePath)) {
                        roots.add(DiscoveredRoot(resolved, volume.isRemovable, volume.getDescription(context) ?: "Storage"))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Volume inspect ব্যর্থ: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "StorageManager volumes পড়া যায়নি: ${e.message}")
        }

        // Fallback / supplement: derive shared roots from getExternalFilesDirs, which reliably
        // returns one entry per storage volume (internal + any SD card) even when StorageVolume
        // introspection above fails on a given OEM skin.
        try {
            ContextCompat.getExternalFilesDirs(context, null).forEach { appSpecificDir ->
                if (appSpecificDir == null) return@forEach
                val path = appSpecificDir.absolutePath
                val androidIndex = path.indexOf("/Android/")
                if (androidIndex > 0) {
                    val sharedRoot = File(path.substring(0, androidIndex))
                    if (sharedRoot.canRead() && seenPaths.add(sharedRoot.absolutePath)) {
                        val removable = Environment.isExternalStorageRemovable(appSpecificDir)
                        roots.add(DiscoveredRoot(sharedRoot, removable, if (removable) "SD Card" else "Internal Storage"))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "External files dirs fallback ব্যর্থ: ${e.message}")
        }

        return roots
    }

    /** Standard media subfolders under each discovered root — scanned first since they're highest yield. */
    fun priorityFolders(root: File): List<File> =
        COMMON_SUBDIRS.map { File(root, it) }.filter { it.exists() && it.canRead() }
}