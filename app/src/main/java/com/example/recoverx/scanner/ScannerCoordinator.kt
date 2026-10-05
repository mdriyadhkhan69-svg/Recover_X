package com.example.recoverx.scanner

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.recoverx.model.LiveStatus
import com.example.recoverx.model.ScanSource
import com.example.recoverx.model.ScannedFile
import com.example.recoverx.utils.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class ScanProgressUpdate(
    val scanned: Int,
    val found: Int,
    val currentSourceLabel: String,
    val percent: Float
)

data class ScanOutcome(
    val results: List<ScannedFile>,
    val inaccessibleLocations: List<String>
)

/**
 * Pipeline:
 *  current-media index (MediaStore live)  ->  candidate discovery (MediaStore trash, SAF folders,
 *  and, for deep scans, trash/recycle/LOST.DIR style files on disk)  ->  structural validation
 *  ->  existing-media comparison (content based)  ->  content-based dedupe  ->  clean results.
 *
 * Thumbnail/cache hits are intentionally NOT part of the clean results: a thumbnail can never
 * be matched to its original by content, so it cannot be proven "not existing".
 */
object ScannerCoordinator {

    private const val TAG = "ScannerCoordinator"
    private const val MEDIA_PHASE_END = 0.45f
    private const val FS_PHASE_END = 0.75f
    private const val VERIFY_PHASE_END = 0.99f

    suspend fun deepScan(
        context: Context,
        includeImages: Boolean,
        includeVideos: Boolean,
        includeDocuments: Boolean,
        extraSafFolderUris: Set<String>,
        deep: Boolean = true,
        onProgress: (ScanProgressUpdate) -> Unit
    ): ScanOutcome {
        // Single choke point: no scan work happens without the session token.
        ScanAuthorization.requireAuthorized()

        return withContext(Dispatchers.IO) {
            val inaccessible = mutableListOf<String>()
            val index = CurrentMediaIndex()
            val candidates = mutableListOf<ScannedFile>()
            var totalScanned = 0

            // ---- 1. MediaStore: builds the current-media index + trash/SAF candidates ----
            onProgress(ScanProgressUpdate(0, 0, "Indexing current files...", 0f))
            val total = MediaStoreScanner.countTotal(context, includeImages, includeVideos, includeDocuments)
            val mediaResults = try {
                MediaStoreScanner.scan(
                    context, includeImages, includeVideos, includeDocuments, extraSafFolderUris
                ) { scanned, _ ->
                    totalScanned = scanned
                    val fraction = (scanned.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    onProgress(ScanProgressUpdate(scanned, 0, "Indexing current files... ($scanned checked)", MEDIA_PHASE_END * fraction))
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaStore scan failed: ${e.message}")
                inaccessible.add("MediaStore")
                emptyList()
            }
            val (existing, mediaCandidates) = mediaResults.partition { it.liveStatus == LiveStatus.LIVE }
            existing.forEach { index.add(it) }
            candidates.addAll(mediaCandidates)
            onProgress(ScanProgressUpdate(totalScanned, candidates.size, "Current-media index ready", MEDIA_PHASE_END))

            // ---- 2. Filesystem candidates (deep scan only) ----
            if (deep) {
                val roots = StorageRootDiscovery.discoverRoots(context)
                val fsRoots = if (PermissionUtils.hasAllFilesAccess()) {
                    roots.map { it.path }
                } else {
                    roots.flatMap { r -> StorageRootDiscovery.priorityFolders(r.path).ifEmpty { listOf(r.path) } }
                }
                onProgress(ScanProgressUpdate(totalScanned, candidates.size, "Scanning device storage...", MEDIA_PHASE_END))
                val fsResults = try {
                    FileSystemScanner.scan(
                        roots = fsRoots,
                        liveMediaPaths = emptySet(),
                        onProgress = { scanned, found, label ->
                            val fraction = scanned.toFloat() / (scanned.toFloat() + 1500f)
                            val percent = MEDIA_PHASE_END + (FS_PHASE_END - MEDIA_PHASE_END) * fraction
                            onProgress(ScanProgressUpdate(totalScanned, candidates.size + found, label, percent))
                        },
                        onSkipped = { skipped -> inaccessible.add(skipped.path) }
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Filesystem scan failed: ${e.message}")
                    inaccessible.add("Device storage")
                    emptyList()
                }
                val removable = roots.filter { it.isRemovable }.map { it.path.absolutePath }
                candidates.addAll(fsResults.map { f ->
                    if (removable.any { f.uriString?.contains(it) == true }) f.copy(source = ScanSource.SD_CARD) else f
                })
            }

            // ---- 3. Validate -> exclude existing -> fingerprint ----
            val survivors = ArrayList<ScannedFile>()
            val n = candidates.size.coerceAtLeast(1)
            candidates.forEachIndexed { i, f ->
                ensureActive()
                val uri = f.uriString?.let { Uri.parse(it) }
                if (uri != null && CandidateValidator.isValid(context, f)) {
                    val id = ContentFingerprint.identity(context, uri)
                    if (id != null && !index.containsSameContent(context, f, uri, id)) {
                        survivors.add(f.copy(fingerprint = id, dedupeKey = id))
                    }
                }
                if (i % 10 == 0) {
                    val p = FS_PHASE_END + (VERIFY_PHASE_END - FS_PHASE_END) * ((i + 1).toFloat() / n)
                    onProgress(ScanProgressUpdate(totalScanned, survivors.size, "Verifying candidates...", p))
                }
            }

            // ---- 4. Content-based dedupe (sampled hash, then full hash to confirm) ----
            val keyed = survivors.groupBy { it.fingerprint }.flatMap { (_, g) ->
                if (g.size == 1) g else g.map { f ->
                    ensureActive()
                    val full = f.uriString?.let { ContentFingerprint.full(context, Uri.parse(it)) } ?: f.id
                    f.copy(fingerprint = f.fingerprint + ":" + full)
                }
            }
            val clean = DeduplicationEngine.merge(keyed)

            onProgress(ScanProgressUpdate(totalScanned, clean.size, "Done", 1f))
            ScanOutcome(results = clean, inaccessibleLocations = inaccessible.distinct())
        }
    }
}