package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.database.EncryptedPhotoEntity
import com.example.domain.model.PhotoMetadata
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivatePhotoViewerScreen(
    currentPhotoId: String,
    allPhotos: List<EncryptedPhotoEntity>,
    onBack: () -> Unit,
    onDeletePhoto: (String) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    loadFullPhoto: suspend (EncryptedPhotoEntity) -> Bitmap?,
    loadMetadata: suspend (EncryptedPhotoEntity) -> PhotoMetadata?,
    modifier: Modifier = Modifier
) {
    var activeId by remember { mutableStateOf(currentPhotoId) }
    val currentIndex = remember(activeId, allPhotos) {
        allPhotos.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
    }
    val currentPhoto = remember(currentIndex, allPhotos) {
        allPhotos.getOrNull(currentIndex)
    }

    var fullBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var metadata by remember { mutableStateOf<PhotoMetadata?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    // Zoom and pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(activeId) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        isLoading = true
        fullBitmap = null
        if (currentPhoto != null) {
            fullBitmap = loadFullPhoto(currentPhoto)
            metadata = loadMetadata(currentPhoto)
        }
        isLoading = false
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "${currentIndex + 1} of ${allPhotos.size}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("viewer_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (currentPhoto != null) {
                        IconButton(
                            onClick = { onToggleFavorite(currentPhoto.id, currentPhoto.isFavorite) },
                            modifier = Modifier.testTag("viewer_favorite_button")
                        ) {
                            Icon(
                                imageVector = if (currentPhoto.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (currentPhoto.isFavorite) Color.Red else Color.White
                            )
                        }
                        IconButton(
                            onClick = { showInfoDialog = true },
                            modifier = Modifier.testTag("viewer_info_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Information",
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.testTag("viewer_delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color.White
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.8f)
                )
            )
        },
        containerColor = Color.Black
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            val bmp = fullBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Full photo view",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                )
            } else if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Text(
                    text = "Unable to open photo.",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Navigation Arrows (Previous / Next)
            if (allPhotos.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .align(Alignment.Center),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            if (currentIndex > 0) {
                                activeId = allPhotos[currentIndex - 1].id
                            }
                        },
                        enabled = currentIndex > 0,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                            .testTag("viewer_prev_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Photo",
                            tint = if (currentIndex > 0) Color.White else Color.Gray
                        )
                    }

                    IconButton(
                        onClick = {
                            if (currentIndex < allPhotos.size - 1) {
                                activeId = allPhotos[currentIndex + 1].id
                            }
                        },
                        enabled = currentIndex < allPhotos.size - 1,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                            .testTag("viewer_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next Photo",
                            tint = if (currentIndex < allPhotos.size - 1) Color.White else Color.Gray
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog && currentPhoto != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Photo") },
            text = { Text("Are you sure you want to delete this private photo? The encrypted files will be securely removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeletePhoto(currentPhoto.id)
                        onBack()
                    },
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Info Dialog
    if (showInfoDialog && metadata != null) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Photo Details") },
            text = {
                val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
                Column {
                    Text("File: ${metadata!!.originalFileName}", fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Dimensions: ${metadata!!.width} x ${metadata!!.height}")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Size: ${metadata!!.sizeBytes / 1024} KB")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("MIME Type: ${metadata!!.mimeType}")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Imported: ${dateFormat.format(Date(metadata!!.importedAt))}")
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
