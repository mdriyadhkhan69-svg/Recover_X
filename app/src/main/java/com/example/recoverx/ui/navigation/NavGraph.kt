package com.example.recoverx.ui.navigation

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
@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                onQuickScan = { navController.navigate(Screen.Scan.route) },
                onDeepScan = { navController.navigate(Screen.Scan.route) }
            )
        }
        composable(Screen.Scan.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            var hasPermission by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(
                    com.example.recoverx.utils.PermissionUtils.hasAllPermissions(context)
                )
            }

            if (hasPermission) {
                ScanScreen(
                    onScanComplete = { navController.navigate(Screen.Results.route) },
                    onCancel = { navController.popBackStack() }
                )
            } else {
                com.example.recoverx.ui.permission.PermissionScreen(
                    onPermissionGranted = { hasPermission = true },
                    onNotNow = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.Results.route) {
            ResultsScreen(
                onFileClick = { file ->
                    navController.navigate("preview/${file.id}")
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
            val file = com.example.recoverx.model.ScanResultsHolder.results.find { it.id == fileId }
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
            HistoryScreen()
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