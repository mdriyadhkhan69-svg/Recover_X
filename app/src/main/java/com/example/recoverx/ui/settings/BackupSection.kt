package com.example.recoverx.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.recoverx.backup.MediaBackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun BackupSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(MediaBackupManager.isEnabled(context)) }
    var stats by remember { mutableStateOf(MediaBackupManager.stats(context)) }
    var busy by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Auto-backup photos & videos",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = enabled, onCheckedChange = { on ->
            enabled = on
            scope.launch {
                withContext(Dispatchers.IO) {
                    MediaBackupManager.setEnabled(context, on)
                    if (on) MediaBackupManager.sync(context)
                }
                stats = MediaBackupManager.stats(context)
            }
        })
    }
    Text(
        text = "Keeps a private copy of new photos/videos. If you later delete one permanently, " +
                "it can be recovered from here. Uses up to 3 GB of storage. Only works for files " +
                "backed up before they were deleted.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Backed up: ${stats.first} files · " + String.format(Locale.US, "%.1f MB", stats.second / 1048576.0),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            enabled = enabled && !busy,
            onClick = {
                busy = true
                scope.launch {
                    withContext(Dispatchers.IO) { MediaBackupManager.backupExisting(context) }
                    stats = MediaBackupManager.stats(context)
                    busy = false
                }
            }
        ) { Text(if (busy) "Backing up..." else "Backup existing now") }
        OutlinedButton(
            enabled = !busy,
            onClick = {
                MediaBackupManager.clear(context)
                stats = MediaBackupManager.stats(context)
            }
        ) { Text("Clear") }
    }
}