package com.example.recoverx.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.recoverx.model.AppSettings
import com.example.recoverx.model.ScanResultsHolder
import com.example.recoverx.scanner.MediaStoreScanner

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

    LaunchedEffect(Unit) {
        val includeImages = AppSettings.scanImages.value
        val includeVideos = AppSettings.scanVideos.value
        val includeDocuments = AppSettings.scanDocuments.value

        val total = MediaStoreScanner.countTotal(context, includeImages, includeVideos, includeDocuments)

        val results = MediaStoreScanner.scan(
            context = context,
            includeImages = includeImages,
            includeVideos = includeVideos,
            includeDocuments = includeDocuments
        ) { scanned, found ->
            if (!cancelled) {
                filesScanned = scanned
                filesFound = found
                progress = (scanned.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            }
        }

        if (!cancelled) {
            ScanResultsHolder.results = results
            progress = 1f
            isComplete = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isComplete) "Scan Complete" else "Scanning Storage",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(32.dp))

        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(160.dp),
                strokeWidth = 10.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Text(
                text = "${(progress * 100).toInt()}%",
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

        Spacer(modifier = Modifier.height(40.dp))

        if (!isComplete) {
            OutlinedButton(onClick = {
                cancelled = true
                onCancel()
            }) {
                Text("Cancel")
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$filesFound files found on your device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { onScanComplete(filesFound) }) {
                    Text("View Results")
                }
            }
        }
    }
}