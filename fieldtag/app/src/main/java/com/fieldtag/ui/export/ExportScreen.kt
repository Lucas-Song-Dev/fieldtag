package com.fieldtag.ui.export

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    projectId: String,
    onBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // As soon as a file URI is ready, fire the system share sheet
    LaunchedEffect(uiState.exportedFileUri) {
        uiState.exportedFileUri?.let { uri ->
            val shareIntent = viewModel.shareFile(uri, uiState.exportedMimeType)
            context.startActivity(Intent.createChooser(shareIntent, "Share export"))
            viewModel.clearExported()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Choose export format",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            // ── PDF Report ────────────────────────────────────────────────
            ExportOptionCard(
                icon = Icons.Default.PictureAsPdf,
                title = "PDF Field Report",
                description = "A formatted PDF with a cover page, instrument details, photo thumbnails, and an ungrouped media section. Best for sharing with stakeholders or printing.",
                buttonLabel = if (uiState.activeExport == ExportType.PDF) "Generating PDF…"
                              else "Export PDF Report",
                isLoading = uiState.activeExport == ExportType.PDF,
                enabled = !uiState.isExporting,
                onClick = viewModel::exportPdf,
            )

            // ── ZIP Archive ────────────────────────────────────────────────
            ExportOptionCard(
                icon = Icons.Default.Archive,
                title = "ZIP Photo Archive",
                description = """
                    Full-resolution photos organised by tag in a folder hierarchy:

                      {Project}/
                        {Tag}/
                          BEFORE/   {Tag}_{date}.jpg
                          AFTER/    {Tag}_{date}.jpg
                          DETAIL/   {Tag}_{date}.jpg
                        _ungrouped/
                          photo_{date}.jpg

                    Best for backing up raw photos or transferring to a desktop.
                """.trimIndent(),
                buttonLabel = if (uiState.activeExport == ExportType.ZIP) "Creating ZIP…"
                              else "Export ZIP Archive",
                isLoading = uiState.activeExport == ExportType.ZIP,
                enabled = !uiState.isExporting,
                onClick = viewModel::exportZip,
            )
        }
    }
}

@Composable
private fun ExportOptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    buttonLabel: String,
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    Text(buttonLabel, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Button(
                    onClick = onClick,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(buttonLabel)
                }
            }
        }
    }
}
