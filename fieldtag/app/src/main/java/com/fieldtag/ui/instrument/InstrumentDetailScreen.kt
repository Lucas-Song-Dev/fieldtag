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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.fieldtag.data.db.entities.FieldStatus
import com.fieldtag.data.db.entities.MediaEntity

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
                    IconButton(onClick = onBack) {
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = { instrument?.let { onOpenCamera(it.id) } },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.Camera, contentDescription = "Take Photo", tint = Color.White)
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
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Status actions - always visible and prominent (min 56dp per spec)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = viewModel::markComplete,
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    enabled = instrument.fieldStatus != FieldStatus.COMPLETE,
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Mark Complete")
                }
                FilledTonalButton(
                    onClick = viewModel::markCannotLocate,
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFFFEBEE)),
                    enabled = instrument.fieldStatus != FieldStatus.CANNOT_LOCATE,
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cannot Locate", color = Color.Red)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Notes
            var notes by remember(instrument.notes) { mutableStateOf(instrument.notes ?: "") }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it; viewModel.updateNotes(it) },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Media grid
            Text(
                "Photos & Videos (${uiState.mediaList.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.mediaList.isEmpty()) {
                Text(
                    "No media yet — tap the camera button to take the first photo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
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
        modifier = Modifier.size(110.dp),
    )
}
