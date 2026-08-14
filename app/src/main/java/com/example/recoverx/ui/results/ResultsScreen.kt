package com.example.recoverx.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.recoverx.model.FileCategory
import com.example.recoverx.model.ScannedFile
import com.example.recoverx.model.sampleScannedFiles
import androidx.compose.foundation.clickable
import com.example.recoverx.model.sampleScannedFiles
private enum class ResultTab(val label: String) { ALL("All"), PHOTOS("Photos"), VIDEOS("Videos"), DOCS("Documents") }

@Composable
fun ResultsScreen(
    onFileClick: (ScannedFile) -> Unit,
    onRecoverSelected: (List<ScannedFile>) -> Unit
) {
    var files by remember { mutableStateOf(com.example.recoverx.model.ScanResultsHolder.results) }
    var selectedTab by remember { mutableStateOf(ResultTab.ALL) }

    val filtered = when (selectedTab) {
        ResultTab.ALL -> files
        ResultTab.PHOTOS -> files.filter { it.category == FileCategory.PHOTO }
        ResultTab.VIDEOS -> files.filter { it.category == FileCategory.VIDEO }
        ResultTab.DOCS -> files.filter { it.category == FileCategory.DOCUMENT }
    }

    val selectedCount = files.count { it.isSelected }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "${files.size} files found",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(20.dp)
        )

        TabRow(selectedTabIndex = ResultTab.entries.indexOf(selectedTab)) {
            ResultTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.label) }
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filtered, key = { it.id }) { file ->
                FileResultCard(
                    file = file,
                    onClick = { onFileClick(file) },
                    onCheckedChange = { checked ->
                        files = files.map {
                            if (it.id == file.id) it.copy(isSelected = checked) else it
                        }
                    }
                )
            }
        }

        if (selectedCount > 0) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Button(
                    onClick = { onRecoverSelected(files.filter { it.isSelected }) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Recover Selected ($selectedCount)")
                }
            }
        }
    }
}

@Composable
private fun FileResultCard(
    file: ScannedFile,
    onClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = when (file.category) {
                        FileCategory.PHOTO -> Icons.Filled.Image
                        FileCategory.VIDEO -> Icons.Filled.Videocam
                        FileCategory.DOCUMENT -> Icons.Filled.Description
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f)
                        .clickable(onClick = onClick)
                ) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${file.sizeLabel} · ${file.recoveryStatus}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (file.confidence == com.example.recoverx.model.RecoveryConfidence.TRASHED)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            Checkbox(checked = file.isSelected, onCheckedChange = onCheckedChange)
        }
    }
}

