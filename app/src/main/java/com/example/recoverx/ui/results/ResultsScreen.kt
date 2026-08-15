package com.example.recoverx.ui.results

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.recoverx.model.AppSettings
import com.example.recoverx.model.FileCategory
import com.example.recoverx.model.ResultViewMode
import com.example.recoverx.model.ScannedFile
import com.example.recoverx.ui.common.FastScrollbar
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
private enum class ResultTab(val label: String) { ALL("All"), PHOTOS("Photos"), VIDEOS("Videos"), DOCS("Documents") }

@Composable
fun ResultsScreen(
    onFileClick: (ScannedFile) -> Unit,
    onRecoverSelected: (List<ScannedFile>) -> Unit
) {
    var files by remember { mutableStateOf(com.example.recoverx.model.ScanResultsHolder.results) }
    var selectedTab by remember { mutableStateOf(ResultTab.ALL) }
    var viewMode by AppSettings.resultViewMode
    // Default: only actual recovery candidates (excludes files that are just LIVE/currently
    // present on device). "View All" shows every scanned file including live ones.
    var showAllFiles by remember { mutableStateOf(false) }

    val curated by remember(showAllFiles, files) {
        derivedStateOf {
            if (showAllFiles) files
            else files.filter { it.liveStatus != com.example.recoverx.model.LiveStatus.LIVE }
        }
    }

    // Filtering is derived straight from `curated`, and the header count below is derived from
    // the SAME `files` list — so the header count and the rendered list can never disagree.
    val filtered by remember {
        derivedStateOf {
            when (selectedTab) {
                ResultTab.ALL -> curated
                ResultTab.PHOTOS -> curated.filter { it.category == FileCategory.PHOTO }
                ResultTab.VIDEOS -> curated.filter { it.category == FileCategory.VIDEO }
                ResultTab.DOCS -> curated.filter { it.category == FileCategory.DOCUMENT }
            }
        }
    }

    val selectedCount by remember { derivedStateOf { files.count { it.isSelected } } }

    fun updateFile(id: String, checked: Boolean) {
        val updated = files.map { if (it.id == id) it.copy(isSelected = checked) else it }
        files = updated
        com.example.recoverx.model.ScanResultsHolder.results = updated
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${files.size} files found",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(onClick = {
                viewMode = if (viewMode == ResultViewMode.LIST) ResultViewMode.GRID else ResultViewMode.LIST
            }) {
                Icon(
                    imageVector = if (viewMode == ResultViewMode.LIST) Icons.Filled.GridView else Icons.Filled.ViewList,
                    contentDescription = "Toggle view"
                )
            }
        }

        TabRow(selectedTabIndex = ResultTab.entries.indexOf(selectedTab)) {
            ResultTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.label) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.material3.FilterChip(
                selected = !showAllFiles,
                onClick = { showAllFiles = false },
                label = { Text("Recoverable Only") }
            )
            androidx.compose.material3.FilterChip(
                selected = showAllFiles,
                onClick = { showAllFiles = true },
                label = { Text("View All") }
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (viewMode == ResultViewMode.LIST) {
                val listState = rememberLazyListState()
                val scope = rememberCoroutineScope()

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 24.dp, top = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { file ->
                        FileResultCard(
                            file = file,
                            onClick = { onFileClick(file) },
                            onCheckedChange = { checked -> updateFile(file.id, checked) }
                        )
                    }
                }

                if (filtered.size > 30) {
                    FastScrollbar(
                        totalItems = filtered.size,
                        firstVisibleIndex = listState.firstVisibleItemIndex,
                        onScrollToIndex = { index -> scope.launch { listState.scrollToItem(index) } },
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                    )
                }
            } else {
                val gridState = rememberLazyGridState()
                val scope = rememberCoroutineScope()

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 96.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 8.dp, end = 22.dp, top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.id }) { file ->
                        GridResultCell(
                            file = file,
                            onClick = { onFileClick(file) },
                            onCheckedChange = { checked -> updateFile(file.id, checked) }
                        )
                    }
                }

                if (filtered.size > 60) {
                    FastScrollbar(
                        totalItems = filtered.size,
                        firstVisibleIndex = gridState.firstVisibleItemIndex,
                        onScrollToIndex = { index -> scope.launch { gridState.scrollToItem(index) } },
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                    )
                }
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                com.example.recoverx.ui.common.FileThumbnail(
                    uriString = file.uriString,
                    category = file.category,
                    modifier = Modifier.size(48.dp)
                )
                Column(
                    modifier = Modifier.padding(start = 12.dp).weight(1f).clickable(onClick = onClick)
                ) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val metaParts = listOfNotNull(
                        file.sizeLabel,
                        file.category.name.lowercase().replaceFirstChar { it.uppercase() },
                        file.dateAddedLabel.ifBlank { null }
                    )
                    Text(
                        text = metaParts.joinToString(" • ") + "  ·  ${file.recoveryStatus}",
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

@Composable
private fun GridResultCell(
    file: ScannedFile,
    onClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        com.example.recoverx.ui.common.FileThumbnail(
            uriString = file.uriString,
            category = file.category,
            modifier = Modifier.fillMaxSize()
        )
        Checkbox(
            checked = file.isSelected,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
        )
    }
}