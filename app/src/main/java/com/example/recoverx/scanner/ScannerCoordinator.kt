package com.example.recoverx.scanner

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.recoverx.model.LiveStatus
import com.example.recoverx.model.RecoverySourceKind
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

/** What was actually checked, so a 0-result scan can say something honest and useful. */
data class SourceReport(
    val label: String,
    val state: State,
    val found: Int,
    val note: String = ""
) {
    enum class State { CHECKED, NOT_RUN, UNAVAILABLE }
}

/** TEMP DIAGNOSTIC: remove after debugging. */

data class ScanOutcome(
    val results: List<ScannedFile>,
    val inaccessibleLocations: List<String>,
    val sourceReports: List<SourceReport> = emptyList()
)

/**
 * Pipeline:
 *  current-media index -> MediaStore trash -> filesystem remnants -> thumbnail/cache remnants
 *  -> (removable volumes are covered by the filesystem/thumbnail passes) -> validation
 *  -> existing-media comparison (originals only) -> content dedupe -> confidence-ordered results.
 * No candidate is ever created without evidence from one of those sources.
 * Raw / unallocated space is not reachable from a normal app and is reported as such.
 */
object ScannerCoordinator {

    private const val TAG = "ScannerCoordinator"
    private const val MEDIA_PHASE_END = 0.45f
    private const val FS_PHASE_END = 0.70f
    private const val THUMB_PHASE_END = 0.80f
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
        ScanAuthorization.requireAuthorized()

        return withContext(Dispatchers.IO) {
            val inaccessible = mutableListOf<String>()
            val index = CurrentMediaIndex()
            val candidates = mutableListOf<ScannedFile>()
            var totalScanned = 0
            var roots = emptyList<StorageRootDiscovery.DiscoveredRoot>()
            var fsRan = false
            var thumbRan = false

            // ---- 1. MediaStore: current-media index + trash candidates (+ evidence-only SAF) ----
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

            // ---- 2 + 3. Filesystem remnants and thumbnail/cache remnants (deep scan only) ----
            if (deep) {
                roots = StorageRootDiscovery.discoverRoots(context)
                val removablePaths = roots.filter { it.isRemovable }.map { it.path.absolutePath }
                val tag: (ScannedFile) -> ScannedFile = { f ->
                    if (removablePaths.any { f.uriString?.contains(it) == true }) f.copy(source = ScanSource.SD_CARD) else f
                }

                val fsRoots = if (PermissionUtils.hasAllFilesAccess()) {
                    roots.map { it.path }
                } else {
                    roots.flatMap { r -> StorageRootDiscovery.priorityFolders(r.path).ifEmpty { listOf(r.path) } }
                }
                onProgress(ScanProgressUpdate(totalScanned, candidates.size, "Looking for trash and orphaned files...", MEDIA_PHASE_END))
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
                    ).also { fsRan = true }
                } catch (e: Exception) {
                    Log.w(TAG, "Filesystem scan failed: ${e.message}")
                    inaccessible.add("Device storage")
                    emptyList()
                }
                candidates.addAll(fsResults.map(tag))

                onProgress(ScanProgressUpdate(totalScanned, candidates.size, "Checking thumbnail and cache remnants...", FS_PHASE_END))
                val thumbResults = try {
                    ThumbnailCacheScanner.scan(roots.map { it.path }) { count, label ->
                        onProgress(
                            ScanProgressUpdate(
                                totalScanned, candidates.size + count, label,
                                FS_PHASE_END + (THUMB_PHASE_END - FS_PHASE_END) * 0.5f
                            )
                        )
                    }.also { thumbRan = true }
                } catch (e: Exception) {
                    Log.w(TAG, "Thumbnail scan failed: ${e.message}")
                    inaccessible.add("Thumbnail caches")
                    emptyList()
                }
                val knownIds = mediaResults.mapNotNull {
                    Regex("^(?:img|vid|doc)-(\\d+)-(?:live|trash)$").find(it.id)?.groupValues?.get(1)
                }.toHashSet()
                candidates.addAll(
                    thumbResults
                        .filter { t -> t.name.substringBeforeLast('.') !in knownIds }
                        .take(300)
                        .map(tag)
                )
                onProgress(ScanProgressUpdate(totalScanned, candidates.size, "Remnant search finished", THUMB_PHASE_END))
            }

            // ---- 4. Validate -> exclude existing (originals only) -> fingerprint ----
            val survivors = ArrayList<ScannedFile>()
            val n = candidates.size.coerceAtLeast(1)
            candidates.forEachIndexed { i, f ->
                ensureActive()
                val uri = f.uriString?.let { Uri.parse(it) }
                val tagInfo = "kind=${f.sourceKind} src=${f.source} cat=${f.category} size=${f.sizeBytes} name=${f.name} uri=${f.uriString}"
                if (uri != null && CandidateValidator.isValid(context, f)) {
                    val id = ContentFingerprint.identity(context, uri)
                        ?: if (f.sourceKind == RecoverySourceKind.MEDIASTORE_TRASH) "mstrash:${f.id}" else null
                    if (id != null) {
                        val duplicatesLive = f.isOriginalFile && index.containsSameContent(context, f, uri, id)
                        if (!duplicatesLive) {
                            survivors.add(f.copy(fingerprint = id, dedupeKey = id))
                        }
                    }
                }
                if (i % 10 == 0) {
                    val p = THUMB_PHASE_END + (VERIFY_PHASE_END - THUMB_PHASE_END) * ((i + 1).toFloat() / n)
                    onProgress(ScanProgressUpdate(totalScanned, survivors.size, "Verifying candidates...", p))
                }
            }

            // ---- 5. Content-based dedupe (sampled hash, then full hash to confirm) ----
            val keyed = survivors.groupBy { it.fingerprint }.flatMap { (_, g) ->
                if (g.size == 1) g else g.map { f ->
                    ensureActive()
                    val full = f.uriString?.let { ContentFingerprint.full(context, Uri.parse(it)) } ?: f.id
                    f.copy(fingerprint = f.fingerprint + ":" + full)
                }
            }
            // ---- 6. Confidence ordering: HIGH first, thumbnails (LOW) last ----
            val clean = DeduplicationEngine.merge(keyed)
                .sortedWith(compareBy<ScannedFile> { it.confidenceLevel.ordinal }.thenByDescending { it.sizeBytes })

            // ---- 7. Honest report of what was actually checked ----
            val reports = mutableListOf<SourceReport>()
            reports += if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                SourceReport("MediaStore trash", SourceReport.State.CHECKED,
                    clean.count { it.sourceKind == RecoverySourceKind.MEDIASTORE_TRASH })
            } else {
                SourceReport("MediaStore trash", SourceReport.State.UNAVAILABLE, 0, "needs Android 11+")
            }
            if (extraSafFolderUris.isNotEmpty()) {
                reports += SourceReport("Folders you added", SourceReport.State.CHECKED,
                    clean.count { it.source == ScanSource.SAF }, "trash-type files only")
            }
            reports += if (deep && fsRan) {
                SourceReport("Filesystem trash and orphan folders", SourceReport.State.CHECKED,
                    clean.count { it.sourceKind == RecoverySourceKind.FILESYSTEM_TRASH || it.sourceKind == RecoverySourceKind.ORPHAN_FILE })
            } else {
                SourceReport("Filesystem trash and orphan folders", SourceReport.State.NOT_RUN,
                    0, if (deep) "could not run" else "Deep Scan only")
            }
            reports += if (deep && thumbRan) {
                SourceReport("Thumbnail / cache previews", SourceReport.State.CHECKED,
                    clean.count { it.sourceKind == RecoverySourceKind.RECOVERED_THUMBNAIL }, "previews only")
            } else {
                SourceReport("Thumbnail / cache previews", SourceReport.State.NOT_RUN,
                    0, if (deep) "could not run" else "Deep Scan only")
            }
            val removable = roots.filter { it.isRemovable }
            reports += when {
                !deep -> SourceReport("Removable storage", SourceReport.State.NOT_RUN, 0, "Deep Scan only")
                removable.isEmpty() -> SourceReport("Removable storage", SourceReport.State.UNAVAILABLE, 0, "none exposed to the app")
                else -> SourceReport("Removable storage", SourceReport.State.CHECKED,
                    clean.count { it.source == ScanSource.SD_CARD }, "file level only")
            }
            reports += SourceReport("Raw / unallocated space", SourceReport.State.UNAVAILABLE, 0,
                "not accessible to a normal Android app")

            onProgress(ScanProgressUpdate(totalScanned, clean.size, "Done", 1f))
            ScanOutcome(results = clean, inaccessibleLocations = inaccessible.distinct(), sourceReports = reports)
        }
    }
}