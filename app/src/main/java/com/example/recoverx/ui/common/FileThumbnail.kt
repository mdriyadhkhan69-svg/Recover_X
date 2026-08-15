package com.example.recoverx.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.recoverx.model.FileCategory
import com.example.recoverx.model.DocumentType

@Composable
fun FileThumbnail(
    uriString: String?,
    category: FileCategory,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var loadFailed by remember(uriString) { mutableStateOf(uriString == null) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        if (!loadFailed && uriString != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(uriString)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { loadFailed = true }
            )
            if (category == FileCategory.VIDEO) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
            }
        } else {
            Icon(
                imageVector = when (category) {
                    FileCategory.PHOTO -> Icons.Filled.Image
                    FileCategory.VIDEO -> Icons.Filled.Videocam
                    FileCategory.DOCUMENT -> Icons.Filled.Description // per-type icon set via documentType below if needed
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}