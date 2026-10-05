package com.example.recoverx.scanner

import android.content.Context
import android.net.Uri
import com.example.recoverx.model.ScannedFile

/**
 * Index of files that currently exist normally on the device. Bucketed by size; identity and
 * full hashes are computed lazily, and only for entries whose size matches a candidate.
 * Filename is never used to decide a match.
 */
class CurrentMediaIndex {
    private class Entry(val uriString: String) {
        var identity: String? = null
        var full: String? = null
    }

    private val bySize = HashMap<Long, MutableList<Entry>>()

    fun add(file: ScannedFile) {
        val uri = file.uriString ?: return
        if (file.sizeBytes <= 0L) return
        bySize.getOrPut(file.sizeBytes) { mutableListOf() }.add(Entry(uri))
    }

    fun containsSameContent(context: Context, c: ScannedFile, cUri: Uri, cIdentity: String): Boolean {
        val list = bySize[c.sizeBytes] ?: return false
        var cFull: String? = null
        for (e in list) {
            if (e.uriString == c.uriString) return true
            val eId = e.identity
                ?: (ContentFingerprint.identity(context, Uri.parse(e.uriString)) ?: "").also { e.identity = it }
            if (eId.isEmpty() || eId != cIdentity) continue
            if (cFull == null) cFull = ContentFingerprint.full(context, cUri) ?: return false
            val eFull = e.full
                ?: (ContentFingerprint.full(context, Uri.parse(e.uriString)) ?: "").also { e.full = it }
            if (eFull == cFull) return true
        }
        return false
    }
}