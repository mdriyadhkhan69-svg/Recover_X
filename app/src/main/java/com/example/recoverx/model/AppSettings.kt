package com.example.recoverx.model

import androidx.compose.runtime.mutableStateOf

enum class ThemeMode { LIGHT, DARK, SYSTEM }

// App-wide settings-এর জন্য সহজ in-memory holder।
// Phase 16 বা তার আশেপাশে চাইলে এটা DataStore দিয়ে persist করা যাবে,
// আপাতত app চালু থাকা অবস্থায় settings মনে রাখলেই যথেষ্ট।
object AppSettings {
    val themeMode = mutableStateOf(ThemeMode.SYSTEM)

    val scanImages = mutableStateOf(true)
    val scanVideos = mutableStateOf(true)
    val scanDocuments = mutableStateOf(true)

    val confirmBeforeRecovery = mutableStateOf(true)

    // SAF দিয়ে user manually add করা folder (যেমন Secure Folder-এর accessible tree)।
    // MediaStore-এর বাইরের কিন্তু legitimately accessible location কভার করার জন্য।
    val safFolderUris = mutableStateOf<Set<String>>(emptySet())

    const val appVersion = "1.0.0 (Beta)"
}
