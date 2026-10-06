package com.example.recoverx.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.recoverx.model.sampleScannedFiles
import com.example.recoverx.ui.home.HomeScreen
import com.example.recoverx.ui.preview.PreviewScreen
import com.example.recoverx.ui.results.ResultsScreen
import com.example.recoverx.ui.scan.ScanScreen
import com.example.recoverx.model.RecoverySelectionHolder
import com.example.recoverx.ui.recovery.RecoveryScreen
import com.example.recoverx.ui.history.HistoryScreen
import com.example.recoverx.ui.settings.SettingsScreen
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.example.recoverx.scanner.ScanAuthorization
import com.example.recoverx.scanner.ScanModeHolder
import com.example.recoverx.ui.permission.AllFilesAccessScreen
import com.example.recoverx.ui.security.ScanPasswordPrompt
import com.example.recoverx.utils.PermissionUtils
@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                onQuickScan = {
                    ScanModeHolder.deep = false
                    navController.navigate(Screen.Scan.route)
                },
                onDeepScan = {
                    ScanModeHolder.deep = true
                    navController.navigate(Screen.Scan.route)
                }
            )
        }
        composable(Screen.Scan.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val deep = ScanModeHolder.deep
            // 1) password (skipped, and token granted, only if none is set)
            var passwordOk by remember { mutableStateOf(ScanAuthorization.grantIfNoPassword(context)) }
            // 2) normal media permission
            var hasPermission by remember { mutableStateOf(PermissionUtils.hasAllPermissions(context)) }
            // 3) All Files Access, Deep Scan only
            var allFilesOk by remember { mutableStateOf(!deep || PermissionUtils.hasAllFilesAccess()) }

            DisposableEffect(Unit) {
                onDispose {
                    ScanAuthorization.revoke()
                    ScanModeHolder.deep = false
                }
            }

            when {
                !passwordOk -> ScanPasswordPrompt(
                    onVerified = { passwordOk = true },
                    onCancel = { navController.popBackStack() }
                )
                !hasPermission -> com.example.recoverx.ui.permission.PermissionScreen(
                    onPermissionGranted = { hasPermission = true },
                    onNotNow = { navController.popBackStack() }
                )
                !allFilesOk -> AllFilesAccessScreen(
                    onGranted = { allFilesOk = true },
                    onNotNow = { navController.popBackStack() }
                )
                else -> ScanScreen(
                    onScanComplete = { navController.navigate(Screen.Results.route) },
                    onCancel = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.Results.route) {
            ResultsScreen(
                onFileClick = { file ->
                    navController.navigate("preview/${android.net.Uri.encode(file.id)}")
                },
                onRecoverSelected = { selectedFiles ->
                    RecoverySelectionHolder.selectedFiles = selectedFiles
                    navController.navigate(Screen.Recovering.route)
                }
            )
        }
        composable(
            route = Screen.Preview.route,
            arguments = listOf(navArgument("fileId") { type = NavType.StringType })
        ) { backStackEntry ->
            val fileId = backStackEntry.arguments?.getString("fileId")
            // এখন পর্যন্ত mock data থেকে খুঁজছি — Phase 13-এ real scan result থেকে আসবে
            val file = com.example.recoverx.model.ScanResultsHolder.results.find { it.id == fileId || it.id == android.net.Uri.decode(fileId ?: "") }
            if (file != null) {
                PreviewScreen(
                    file = file,
                    onRecoverClick = {
                        RecoverySelectionHolder.selectedFiles = listOf(file)
                        navController.navigate(Screen.Recovering.route)
                    }
                )
            } else {
                PlaceholderScreen("File পাওয়া যায়নি")
            }
        }
        composable(Screen.Recovering.route) {
            RecoveryScreen(
                filesToRecover = RecoverySelectionHolder.selectedFiles,
                onOpenRecovered = { navController.popBackStack(Screen.Home.route, false) }
            )
        }
        composable(Screen.Recovery.route) {
            HistoryScreen(
                onItemClick = { item ->
                    com.example.recoverx.model.HistoryPreviewHolder.item = item
                    navController.navigate(Screen.HistoryPreview.route)
                }
            )
        }
        composable(Screen.HistoryPreview.route) {
            com.example.recoverx.ui.history.HistoryPreviewScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}

@Composable
private fun PlaceholderScreen(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = MaterialTheme.colorScheme.onBackground)
    }
}