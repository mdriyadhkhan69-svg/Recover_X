package com.example.recoverx.scanner

import com.example.recoverx.model.ScanSource
import com.example.recoverx.model.ScannedFile

/**
 * Merges the same underlying file when it surfaces via more than one source
 * (e.g. MediaStore + filesystem + SAF), keeping the single best candidate instead
 * of showing duplicates. Priority is source-quality first, then raw size as the
 * best available proxy for completeness (true resolution/codec analysis is out of
 * scope without decoding every file, which would not scale to 10k+ results).
 */
object DeduplicationEngine {

    private val SOURCE_PRIORITY = mapOf(
        ScanSource.TRASH to 0,
        ScanSource.MEDIASTORE to 1,
        ScanSource.SAF to 2,
        ScanSource.SD_CARD to 2,
        ScanSource.SECURE_FOLDER to 2,
        ScanSource.FILESYSTEM to 3,
        ScanSource.CACHE to 4,
        ScanSource.THUMBNAIL to 5
    )

    fun merge(files: List<ScannedFile>): List<ScannedFile> {
        return files
            .groupBy { it.dedupeKey.ifBlank { it.id } }
            .map { (_, group) -> pickBest(group) }
    }

    private fun pickBest(group: List<ScannedFile>): ScannedFile {
        return group.minWithOrNull(
            compareBy<ScannedFile> { SOURCE_PRIORITY[it.source] ?: 9 }
                .thenByDescending { it.sizeBytes }
        ) ?: group.first()
    }
}