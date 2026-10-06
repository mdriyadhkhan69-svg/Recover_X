package com.example.recoverx.ui.scan

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.recoverx.model.AppSettings
import com.example.recoverx.model.ScanResultsHolder
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun ScanScreen(
    onScanComplete: (foundCount: Int) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    var progress by remember { mutableFloatStateOf(0f) }
    var filesScanned by remember { mutableIntStateOf(0) }
    var filesFound by remember { mutableIntStateOf(0) }
    var isComplete by remember { mutableStateOf(false) }
    var cancelled by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentSourceLabel by remember { mutableStateOf("") }
    var inaccessibleLocations by remember { mutableStateOf(emptyList<String>()) }
    var sourceReports by remember { mutableStateOf(emptyList<com.example.recoverx.scanner.SourceReport>()) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    // Filter চাপার জন্য নতুন state — শুধু Scan Complete screen-এর নিজস্ব Filter button-এর জন্য।
    // এটা কখনো raw ScanResultsHolder.results replace করে না; শুধু filtered id set আলাদাভাবে
    // ResultsFilterHolder-এ পাঠায়, Results screen সেটা পড়ে filtered view দেখায়।
    var isFilteringOnScan by remember { mutableStateOf(false) }
    var scanFilterProgress by remember { mutableFloatStateOf(0f) }
    val scanFilterScope = rememberCoroutineScope()
    // Secure Folder-সহ যেকোনো accessible location manually add করার জন্য SAF picker।
    // Knox/encrypted storage bypass করে না — শুধু user explicitly grant করা tree-ই যোগ হয়।
    val addFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            AppSettings.safFolderUris.value = AppSettings.safFolderUris.value + treeUri.toString()
            retryTrigger++
        }
    }
    fun runFilterThenViewResults() {
        scanFilterScope.launch {
            isFilteringOnScan = true
            scanFilterProgress = 0f
            val snapshot = ScanResultsHolder.results
            val chunkSize = (snapshot.size / 10).coerceAtLeast(1)
            val survivedIds = mutableSetOf<String>()
            var index = 0
            while (index < snapshot.size) {
                val end = (index + chunkSize).coerceAtMost(snapshot.size)
                for (i in index until end) {
                    val f = snapshot[i]
                    if (f.liveStatus != com.example.recoverx.model.LiveStatus.LIVE) {
                        survivedIds.add(f.id)
                    }
                }
                index = end
                scanFilterProgress = (index.toFloat() / snapshot.size.toFloat()).coerceIn(0f, 1f)
                delay(60)
            }
            com.example.recoverx.model.ResultsFilterHolder.pendingFilteredIds = survivedIds
            scanFilterProgress = 1f
            delay(150)
            isFilteringOnScan = false
            onScanComplete(filesFound)
        }
    }
    LaunchedEffect(retryTrigger) {
        errorMessage = null
        progress = 0f
        filesScanned = 0
        filesFound = 0
        isComplete = false

        try {
            val includeImages = AppSettings.scanImages.value
            val includeVideos = AppSettings.scanVideos.value
            val includeDocuments = AppSettings.scanDocuments.value

            if (!includeImages && !includeVideos && !includeDocuments) {
                errorMessage = "Settings-এ কোনো ক্যাটাগরি বাছাই করা নেই। Settings থেকে অন্তত একটা চালু করো।"
                return@LaunchedEffect
            }

            val outcome = com.example.recoverx.scanner.ScannerCoordinator.deepScan(
                context = context,
                includeImages = includeImages,
                includeVideos = includeVideos,
                includeDocuments = includeDocuments,
                extraSafFolderUris = AppSettings.safFolderUris.value,
                deep = com.example.recoverx.scanner.ScanModeHolder.deep
            ) { update ->
                if (!cancelled) {
                    filesScanned = update.scanned
                    filesFound = update.found
                    currentSourceLabel = update.currentSourceLabel
                    // Percent now comes directly from ScannerCoordinator, which tracks real,
                    // continuously-moving progress across every phase (MediaStore, filesystem,
                    // thumbnail/cache) — the bar no longer freezes at a cap while later phases work.
                    progress = update.percent.coerceIn(0f, 1f)
                }
            }
            if (!cancelled) {
                ScanResultsHolder.results = outcome.results
                inaccessibleLocations = outcome.inaccessibleLocations
                sourceReports = outcome.sourceReports
                filesFound = outcome.results.size
                progress = 1f
                isComplete = true
            }
        } catch (e: com.example.recoverx.scanner.ScanNotAuthorizedException) {
            errorMessage = "Scan not authorized. Go back and start the scan again."
        } catch (e: SecurityException) {
            errorMessage = "No Storage access permission. Go settings give permission try again."
        } catch (e: Exception) {
            errorMessage = "Faced scanning problem. Try again."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (errorMessage != null) {
            // Error state
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Failed to Scanning",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { retryTrigger++ }) {
                Text("Try again.")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }
            return@Column
        }

        Text(
            text = if (isComplete) "Scan Complete" else "Scanning Storage",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Visually animates every real progress update from the scanner into a smooth
        // transition instead of a hard jump — purely a UI-level tween, no fake/delayed
        // progress and no change to the underlying scan speed or completion timing.
        val animatedProgress by animateFloatAsState(
            targetValue = progress,
            animationSpec = tween(durationMillis = 300),
            label = "scanProgress"
        )

        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.size(160.dp),
                strokeWidth = 10.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Text(
                text = "${(animatedProgress * 100).toInt()}%",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "$filesScanned files scanned",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )
        Text(
            text = "$filesFound files found",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
        if (currentSourceLabel.isNotBlank() && !isComplete) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = currentSourceLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
        if (isComplete && inaccessibleLocations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Some locations could not be accessed (${inaccessibleLocations.size})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
        Spacer(modifier = Modifier.height(40.dp))

        if (!isComplete) {
            OutlinedButton(onClick = { addFolderLauncher.launch(null) }) {
                Text("add folder")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = {
                cancelled = true
                onCancel()
            }) {
                Text("Cancel")
            }
        } else if (isFilteringOnScan) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { scanFilterProgress },
                    modifier = Modifier.size(120.dp),
                    strokeWidth = 8.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = "${(scanFilterProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Filtering Results...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (filesFound == 0)
                        "No deleted files were found in accessible recovery sources."
                    else
                        "$filesFound recoverable candidates found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                if (sourceReports.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    sourceReports.forEach { r ->
                        val mark = when (r.state.name) {
                            "CHECKED" -> "✓"
                            "NOT_RUN" -> "–"
                            else -> "✗"
                        }
                        val detail = if (r.state.name == "CHECKED") {
                            "${r.found}" + if (r.note.isNotBlank()) " (${r.note})" else ""
                        } else r.note
                        Text(
                            text = "$mark ${r.label}: $detail",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    onScanComplete(filesFound)
                }) {
                    Text("View Results")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { runFilterThenViewResults() }) {
                    Text("Filter")
                }
            }
        }
    }
}