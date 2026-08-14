package com.example.recoverx.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.CheckCircle
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Filled.Home)
    object Scan : Screen("scan", "Scan", Icons.Filled.Search)
    object Recovery : Screen("recovery", "Recovery", Icons.Filled.History)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
    object Results : Screen("results", "Results", Icons.Filled.Search)
    object Preview : Screen("preview/{fileId}", "Preview", Icons.Filled.Image)
    object Recovering : Screen("recovering", "Recovering", Icons.Filled.CheckCircle)
}

val bottomNavItems = listOf(Screen.Home, Screen.Scan, Screen.Recovery, Screen.Settings)