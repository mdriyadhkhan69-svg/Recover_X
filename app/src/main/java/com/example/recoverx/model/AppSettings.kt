package com.example.recoverx.model

import androidx.compose.runtime.mutableStateOf

enum class ThemeMode { LIGHT, DARK, SYSTEM }
enum class ResultViewMode { LIST, GRID }

object AppSettings {
    val themeMode = mutableStateOf(ThemeMode.SYSTEM)

    val scanImages = mutableStateOf(true)
    val scanVideos = mutableStateOf(true)
    val scanDocuments = mutableStateOf(true)

    val confirmBeforeRecovery = mutableStateOf(true)

    val safFolderUris = mutableStateOf<Set<String>>(emptySet())

    val resultViewMode = mutableStateOf(ResultViewMode.LIST)

    const val appVersion = "1.0.0 (Beta)"
}