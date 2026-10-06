package com.example.recoverx.backup

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * Dumpster-style protection: copies new photos/videos into app-private storage, so a file that is
 * later permanently deleted from the gallery still exists here and can be recovered for real.
 * Only files added AFTER enabling (plus the newest ~200 existing) or after "Backup existing now".
 */
object MediaBackupManager {
    private const val TAG = "MediaBackup"
    private const val PREFS = "media_backup"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_IMG = "last_img"
    private const val KEY_LAST_VID = "last_vid"
    private const val MAX_BYTES = 3L * 1024 * 1024 * 1024
    private const val LOOKBACK = 200L
    private const val JOB_TRIGGER = 7101
    private const val JOB_PERIODIC = 7102

    private val IMG = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    private val VID = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_ENABLED, false)

    fun backupDir(c: Context): File = File(c.filesDir, "media_backup").apply { mkdirs() }

    fun setEnabled(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean(KEY_ENABLED, on).apply()
        if (on) {
            ensureBaseline(c)
            schedule(c)
        } else {
            cancel(c)
        }
    }

    fun schedule(c: Context) {
        try {
            val js = c.getSystemService(JobScheduler::class.java) ?: return
            val comp = ComponentName(c, MediaChangeJobService::class.java)
            val trigger = JobInfo.Builder(JOB_TRIGGER, comp)
                .addTriggerContentUri(JobInfo.TriggerContentUri(IMG, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                .addTriggerContentUri(JobInfo.TriggerContentUri(VID, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                .setTriggerContentUpdateDelay(2000)
                .setTriggerContentMaxDelay(10000)
                .build()
            js.schedule(trigger)
            if (js.getPendingJob(JOB_PERIODIC) == null) {
                js.schedule(
                    JobInfo.Builder(JOB_PERIODIC, comp)
                        .setPeriodic(15 * 60 * 1000L)
                        .setPersisted(true)
                        .build()
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "schedule failed: ${e.message}")
        }
    }

    private fun cancel(c: Context) {
        try {
            val js = c.getSystemService(JobScheduler::class.java) ?: return
            js.cancel(JOB_TRIGGER)
            js.cancel(JOB_PERIODIC)
        } catch (_: Exception) {}
    }

    @Synchronized
    fun sync(c: Context, stop: () -> Boolean = { false }): Int {
        return try {
            ensureBaseline(c)
            syncTable(c, IMG, "img", KEY_LAST_IMG, stop) + syncTable(c, VID, "vid", KEY_LAST_VID, stop)
        } catch (e: SecurityException) {
            Log.w(TAG, "no permission: ${e.message}"); 0
        } catch (e: Exception) {
            Log.w(TAG, "sync failed: ${e.message}"); 0
        }
    }

    @Synchronized
    fun backupExisting(c: Context, stop: () -> Boolean = { false }): Int {
        prefs(c).edit().putLong(KEY_LAST_IMG, 0L).putLong(KEY_LAST_VID, 0L).apply()
        return sync(c, stop)
    }

    fun stats(c: Context): Pair<Int, Long> {
        val files = backupDir(c).listFiles()?.filter { !it.name.endsWith(".part") } ?: emptyList()
        return files.size to files.sumOf { it.length() }
    }

    fun clear(c: Context) {
        backupDir(c).listFiles()?.forEach { it.delete() }
    }

    private fun ensureBaseline(c: Context) {
        val p = prefs(c)
        if (!p.contains(KEY_LAST_IMG)) maxId(c, IMG)?.let { p.edit().putLong(KEY_LAST_IMG, it).apply() }
        if (!p.contains(KEY_LAST_VID)) maxId(c, VID)?.let { p.edit().putLong(KEY_LAST_VID, it).apply() }
    }

    private fun maxId(c: Context, base: Uri): Long? = try {
        c.contentResolver.query(
            base, arrayOf(MediaStore.MediaColumns._ID), null, null,
            "${MediaStore.MediaColumns._ID} DESC"
        )?.use { if (it.moveToFirst()) it.getLong(0) else 0L }
    } catch (e: Exception) { null }

    private fun syncTable(c: Context, base: Uri, prefix: String, key: String, stop: () -> Boolean): Int {
        val p = prefs(c)
        val last = p.getLong(key, -1L)
        if (last < 0) return 0
        val from = (last - LOOKBACK).coerceAtLeast(0L)
        val dir = backupDir(c)
        var used = dir.listFiles()?.sumOf { it.length() } ?: 0L
        var copied = 0
        c.contentResolver.query(
            base,
            arrayOf(
                MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_ADDED
            ),
            "${MediaStore.MediaColumns._ID} > ?", arrayOf(from.toString()),
            "${MediaStore.MediaColumns._ID} ASC"
        )?.use { cur ->
            while (cur.moveToNext() && !stop()) {
                val id = cur.getLong(0)
                val name = cur.getString(1) ?: "file"
                val size = cur.getLong(2)
                val added = cur.getLong(3)
                val dest = File(dir, "$prefix${id}__${name.replace(Regex("[^A-Za-z0-9._\\- ]"), "_")}")
                if (size > 0 && !dest.exists()) {
                    if (used + size > MAX_BYTES) break
                    if (copy(c, ContentUris.withAppendedId(base, id), dest, added)) {
                        used += size
                        copied++
                    }
                }
                if (id > last) p.edit().putLong(key, id).apply()
            }
        }
        return copied
    }

    private fun copy(c: Context, src: Uri, dest: File, addedSec: Long): Boolean {
        val tmp = File(dest.parentFile, dest.name + ".part")
        return try {
            val input = c.contentResolver.openInputStream(src) ?: return false
            input.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            if (!tmp.renameTo(dest)) { tmp.delete(); return false }
            dest.setLastModified(addedSec * 1000)
            true
        } catch (e: Exception) {
            tmp.delete()
            false
        }
    }
}