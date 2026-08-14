package com.example.recoverx.ui.recovery

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.recoverx.model.RecoveryHistoryItem
import com.example.recoverx.model.ScannedFile
import kotlinx.coroutines.CompletableDeferred

@Composable
fun RecoveryScreen(
    filesToRecover: List<ScannedFile>,
    onOpenRecovered: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { com.example.recoverx.model.RecoveryHistoryRepository(context) }
    var isComplete by remember { mutableStateOf(false) }
    var successCount by remember { mutableIntStateOf(0) }
    var failedCount by remember { mutableIntStateOf(0) }
    var processedCount by remember { mutableIntStateOf(0) }

    // Trash restore-এর জন্য মাঝপথে system permission dialog লাগলে
    // সেটার ফলাফলের জন্য অপেক্ষা করার bridge
    var pendingDeferred by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        pendingDeferred?.complete(result.resultCode == Activity.RESULT_OK)
        pendingDeferred = null
    }

    LaunchedEffect(filesToRecover) {
        for (file in filesToRecover) {
            var result = RecoveryEngine.recover(context, file)

            if (result is RecoveryResult.NeedsPermission) {
                val deferred = CompletableDeferred<Boolean>()
                pendingDeferred = deferred
                permissionLauncher.launch(
                    IntentSenderRequest.Builder(result.intentSender).build()
                )
                val granted = deferred.await()
                result = if (granted) {
                    RecoveryEngine.recover(context, file)
                } else {
                    RecoveryResult.Failed("Permission দেওয়া হয়নি")
                }
            }

            val status = if (result is RecoveryResult.Success) "Recovered" else "Failed"
            if (status == "Recovered") successCount += 1 else failedCount += 1
            processedCount += 1

            repository.add(
                RecoveryHistoryItem(
                    id = "${file.id}-${System.currentTimeMillis()}",
                    fileName = file.name,
                    dateLabel = "Just now",
                    category = file.category,
                    status = status,
                    uriString = (result as? RecoveryResult.Success)?.recoveredUri
                )
            )
        }
        isComplete = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!isComplete) {
            CircularProgressIndicator(
                modifier = Modifier.size(80.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Recovering files...",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$processedCount of ${filesToRecover.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        } else {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Recovery Complete",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Successfully recovered: $successCount",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Failed: $failedCount",
                style = MaterialTheme.typography.bodyLarge,
                color = if (failedCount > 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Text(
                text = "Location: Downloads/RecoverX/Recovered",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onOpenRecovered) {
                Text("Done")
            }
        }
    }
}