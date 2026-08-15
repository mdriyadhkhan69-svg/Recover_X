package com.example.recoverx.scanner

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.recoverx.model.FileCategory
import com.example.recoverx.model.LiveStatus
import com.example.recoverx.model.ScanSource
import com.example.recoverx.model.ScannedFile
import java.io.File

data class ScanProgressUpdate(
    val scanned: Int,
    val found: Int,
    val currentSourceLabel: String
)

data class ScanOutcome(
    val results: List<ScannedFile>,
    val inaccessibleLocations: List<String>
)

/**
 * Orchestrates every legitimate scan source behind one Deep Scan entry point.
 * Each source runs in isolation — a failure/inaccessible folder in one source
 * never aborts the others (item 18: error isolation).
 */
object ScannerCoordinator {

    private const val TAG = "ScannerCoordinator"

    suspend fun deepScan(
        context: Context,
        includeImages: Boolean,
        includeVideos: Boolean,
        includeDocuments: Boolean,
        extraSafFolderUris: Set<String>,
        onProgress: (ScanProgressUpdate) -> Unit
    ): ScanOutcome {
        val inaccessible = mutableListOf<String>()
        val allResults = mutableListOf<ScannedFile>()
        var totalScanned = 0

        // --- Source: MediaStore (live + trash) + Documents ---
        onProgress(ScanProgressUpdate(totalScanned, allResults.size, "Scanning MediaStore..."))
        val total = MediaStoreScanner.countTotal(context, includeImages, includeVideos, includeDocuments)
        val mediaResults = try {
            MediaStoreScanner.scan(
                context, includeImages, includeVideos, includeDocuments, extraSafFolderUris
            ) { scanned, found ->
                totalScanned = scanned
                onProgress(ScanProgressUpdate(scanned, found, "Scanning MediaStore... ($scanned checked)"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore scan ব্যর্থ: ${e.message}")
            inaccessible.add("MediaStore")
            emptyList()
        }
        allResults.addAll(mediaResults)

        // Build a live-path index from MediaStore live (non-trashed) rows, used to classify
        // filesystem-carved candidates as LIVE vs POSSIBLY_RECOVERABLE.
        val livePaths = mediaResults
            .filter { it.liveStatus == LiveStatus.LIVE }
            .mapNotNull { pathFromUri(it.uriString) }
            .toSet()

        // --- Source: Filesystem (internal + SD card, recursive) ---
        val roots = StorageRootDiscovery.discoverRoots(context)
        val fsRoots = roots.flatMap { root ->
            StorageRootDiscovery.priorityFolders(root.path).ifEmpty { listOf(root.path) }
        }
        onProgress(ScanProgressUpdate(totalScanned, allResults.size, "Scanning device storage..."))
        val fsResults = try {
            FileSystemScanner.scan(
                roots = fsRoots,
                liveMediaPaths = livePaths,
                onProgress = { scanned, found, label ->
                    totalScanned += 0
                    onProgress(ScanProgressUpdate(totalScanned, allResults.size + found, label))
                },
                onSkipped = { skipped -> inaccessible.add(skipped.path) }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Filesystem scan ব্যর্থ: ${e.message}")
            inaccessible.add("Device storage")
            emptyList()
        }
        // Tag SD card items distinctly where the root was flagged removable.
        val removableRootPaths = roots.filter { it.isRemovable }.map { it.path.absolutePath }.toSet()
        val fsTagged = fsResults.map { f ->
            val isSd = removableRootPaths.any { f.uriString?.contains(it) == true }
            if (isSd) f.copy(source = ScanSource.SD_CARD) else f
        }
        allResults.addAll(fsTagged)

        // --- Source: SAF user-granted folders (recursive, already implemented in
        // MediaStoreScanner.scanDocumentTree via the extraSafFolderUris path above for documents;
        // this pass re-runs it explicitly for images/videos too since scan() only routes SAF
        // results through the same include flags). No separate action needed here — SAF is
        // folded into MediaStoreScanner.scan() already when extraFolderUris is non-empty.

        onProgress(ScanProgressUpdate(totalScanned, allResults.size, "Scanning thumbnails & cache..."))
        val thumbResults = try {
            ThumbnailCacheScanner.scan(context, roots.map { it.path }) { label ->
                onProgress(ScanProgressUpdate(totalScanned, allResults.size, label))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Thumbnail/cache scan ব্যর্থ: ${e.message}")
            inaccessible.add("Thumbnail cache")
            emptyList()
        }
        allResults.addAll(thumbResults)

        onProgress(ScanProgressUpdate(totalScanned, allResults.size, "Merging duplicates..."))
        val deduped = DeduplicationEngine.merge(allResults)

        onProgress(ScanProgressUpdate(totalScanned, deduped.size, "Done"))
        return ScanOutcome(results = deduped, inaccessibleLocations = inaccessible.distinct())
    }

    private fun pathFromUri(uriString: String?): String? {
        if (uriString == null) return null
        return try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") uri.path else null
            // content:// MediaStore URIs don't expose a filesystem path directly without another
            // query; live-matching for those is handled by the MediaStore pass itself marking
            // liveStatus = LIVE, which is what livePaths is built from above.
        } catch (e: Exception) { null }
    }
}