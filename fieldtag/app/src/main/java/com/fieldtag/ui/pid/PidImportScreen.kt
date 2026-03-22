package com.fieldtag.ui.pid

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fieldtag.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PidImportScreen(
    projectId: String,
    onBack: () -> Unit,
    /** Called with the pidDocumentId once the text layer has been extracted. */
    onParseComplete: (pidDocumentId: String) -> Unit,
    viewModel: PidImportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(uiState.completedDocId) {
        uiState.completedDocId?.let { onParseComplete(it) }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment ?: "document.pdf"
            viewModel.importPdf(uri, fileName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import P&ID", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBack() 
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                uiState.isImporting || uiState.isParsing -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(Dimens.PaddingExtraLarge),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(Dimens.PaddingExtraLarge))
                        Text(
                            if (uiState.isImporting) "Importing PDF..." else "Extracting text layer...",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        uiState.pidDocument?.fileName?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
                uiState.errorMessage != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(Dimens.PaddingExtraLarge),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("Import Failed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                        Text(uiState.errorMessage ?: "", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(Dimens.PaddingLarge))
                        Button(
                            onClick = { 
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                pdfPickerLauncher.launch(arrayOf("application/pdf")) 
                            },
                            modifier = Modifier.height(Dimens.MinTouchTarget)
                        ) {
                            Text("Try Again")
                        }
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(Dimens.PaddingExtraLarge),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(Dimens.PaddingExtraLarge))
                        Text("Import P&ID PDF", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
                        Text(
                            "Select a P&ID PDF with a text layer. Tags will be automatically extracted and the instrument list will be ready before you go on site.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(modifier = Modifier.height(Dimens.PaddingExtraLarge))
                        Button(
                            onClick = { 
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                pdfPickerLauncher.launch(arrayOf("application/pdf")) 
                            },
                            modifier = Modifier.fillMaxWidth().height(Dimens.MinTouchTarget),
                        ) {
                            Text("Select PDF File")
                        }
                    }
                }
            }
        }
    }
}
