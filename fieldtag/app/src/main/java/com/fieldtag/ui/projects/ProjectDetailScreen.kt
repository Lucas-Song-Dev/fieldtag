package com.fieldtag.ui.projects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fieldtag.ui.instrument.InstrumentListScreen
import com.fieldtag.ui.diagram.DiagramViewerScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    projectId: String,
    onBack: () -> Unit,
    onImportPid: () -> Unit,
    onInstrumentClick: (String) -> Unit,
    onExport: () -> Unit,
    viewModel: ProjectDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val project = uiState.project

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(project?.name ?: "Project", fontWeight = FontWeight.Bold)
                        if (uiState.totalCount > 0) {
                            Text(
                                "${uiState.completeCount}/${uiState.totalCount} instruments complete",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                actions = {
                    if (uiState.pidDocuments.isEmpty()) {
                        IconButton(onClick = onImportPid) {
                            Icon(Icons.Default.FileUpload, contentDescription = "Import P&ID", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    IconButton(onClick = onExport) {
                        Icon(Icons.Default.IosShare, contentDescription = "Export", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            TabRow(selectedTabIndex = uiState.selectedTab) {
                Tab(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("LIST") },
                )
                Tab(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text("DIAGRAM") },
                )
            }

            when (uiState.selectedTab) {
                0 -> InstrumentListScreen(
                    projectId = projectId,
                    instruments = uiState.filteredInstruments(),
                    onInstrumentClick = onInstrumentClick,
                    onImportPid = if (uiState.pidDocuments.isEmpty()) onImportPid else null,
                    ungroupedCount = uiState.ungroupedCount,
                    searchQuery = uiState.searchQuery,
                    sortOrder = uiState.sortOrder,
                    onSearchQueryChange = viewModel::onSearchQueryChange,
                    onSortOrderChange = viewModel::onSortOrderChange,
                )
                1 -> DiagramViewerScreen(
                    projectId = projectId,
                    pidDocuments = uiState.pidDocuments,
                    instruments = uiState.instruments,
                    onInstrumentClick = onInstrumentClick,
                )
            }
        }
    }
}
