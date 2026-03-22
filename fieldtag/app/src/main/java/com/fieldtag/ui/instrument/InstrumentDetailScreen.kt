package com.fieldtag.ui.instrument

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.fieldtag.data.db.entities.FieldStatus
import com.fieldtag.data.db.entities.MediaEntity
import com.fieldtag.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstrumentDetailScreen(
    instrumentId: String,
    onBack: () -> Unit,
    onOpenCamera: (String) -> Unit,
    viewModel: InstrumentDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val instrument = uiState.instrument
    val haptic = LocalHapticFeedback.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(instrument?.tagId ?: "Instrument", fontWeight = FontWeight.Bold)
                        instrument?.instrumentType?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBack() 
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = when (instrument?.fieldStatus) {
                        FieldStatus.COMPLETE -> Color(0xFF2E7D32)
                        FieldStatus.CANNOT_LOCATE -> Color.Red
                        FieldStatus.IN_PROGRESS -> Color(0xFFF57C00)
                        else -> MaterialTheme.colorScheme.primary
                    },
                    titleContentColor = Color.White,
                ),
            )
        },
        bottomBar = {
            if (instrument != null) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentPadding = PaddingValues(horizontal = Dimens.PaddingMedium, vertical = Dimens.PaddingSmall)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium),
                    ) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.markComplete()
                            },
                            modifier = Modifier.weight(1f).height(Dimens.MinTouchTarget),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            enabled = instrument.fieldStatus != FieldStatus.COMPLETE,
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(Dimens.PaddingSmall))
                            Text("Mark Complete")
                        }
                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.markCannotLocate()
                            },
                            modifier = Modifier.weight(1f).height(Dimens.MinTouchTarget),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFFFEBEE)),
                            enabled = instrument.fieldStatus != FieldStatus.CANNOT_LOCATE,
                            elevation = ButtonDefaults.filledTonalButtonElevation(defaultElevation = 2.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red)
                            Spacer(modifier = Modifier.width(Dimens.PaddingSmall))
                            Text("Cannot Locate", color = Color.Red)
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    instrument?.let { onOpenCamera(it.id) } 
                },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimens.FabSize)
            ) {
                Icon(Icons.Default.Camera, contentDescription = "Take Photo", tint = Color.White, modifier = Modifier.size(32.dp))
            }
        },
    ) { paddingValues ->
        if (uiState.isLoading || instrument == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = Dimens.PaddingMedium),
        ) {
            Spacer(modifier = Modifier.height(Dimens.PaddingMedium))

            // Notes
            var notes by remember(instrument.notes) { mutableStateOf(instrument.notes ?: "") }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it; viewModel.updateNotes(it) },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
            )

            Spacer(modifier = Modifier.height(Dimens.PaddingLarge))

            // Media grid
            Text(
                "Photos & Videos (${uiState.mediaList.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(Dimens.PaddingMedium))

            if (uiState.mediaList.isEmpty()) {
                Text(
                    "No media yet — tap the camera button to take the first photo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
                    verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall),
                ) {
                    items(uiState.mediaList, key = { it.id }) { media ->
                        MediaThumbnail(media = media)
                    }
                }
            }
        }
    }
}

@Composable
fun MediaThumbnail(media: MediaEntity) {
    AsyncImage(
        model = media.thumbnailPath,
        contentDescription = media.role.name,
        contentScale = ContentScale.Crop,
        modifier = Modifier.height(140.dp).fillMaxWidth(),
    )
}
