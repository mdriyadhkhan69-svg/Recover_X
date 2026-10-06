package com.example.recoverx

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.example.recoverx.backup.MediaBackupManager

class RecoverXApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        if (MediaBackupManager.isEnabled(this)) {
            MediaBackupManager.schedule(this)
            Thread { MediaBackupManager.sync(this) }.start()
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }
}