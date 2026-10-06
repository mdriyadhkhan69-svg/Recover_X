package com.example.recoverx.backup

import android.app.job.JobParameters
import android.app.job.JobService

class MediaChangeJobService : JobService() {
    @Volatile private var stopped = false

    override fun onStartJob(params: JobParameters): Boolean {
        stopped = false
        Thread {
            val enabled = MediaBackupManager.isEnabled(this)
            if (enabled) {
                try { MediaBackupManager.sync(this) { stopped } } catch (_: Exception) {}
                MediaBackupManager.schedule(this)
            }
            jobFinished(params, stopped)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        stopped = true
        return true
    }
}