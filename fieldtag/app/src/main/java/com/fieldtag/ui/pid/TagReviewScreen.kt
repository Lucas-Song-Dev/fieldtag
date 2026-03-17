package com.fieldtag.ui.pid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fieldtag.data.db.entities.InstrumentEntity
import com.fieldtag.ui.common.SearchSortBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagReviewScreen(
    projectId: String,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    viewModel: PidImportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    // filtered list for display; confirm button uses total (unfiltered) count
    val displayed = uiState.filteredInstruments()
    val totalCount = uiState.instruments.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Review Tags", fontWeight = FontWeight.Bold)
                        Text(
                            "$totalCount instruments found",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Show total count so user knows how many instruments will be confirmed,
                // regardless of what's currently filtered in the search.
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    enabled = totalCount > 0,
                ) {
                    Text("Confirm All ($totalCount)")
                }
            }
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.padding(paddingValues),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item(key = "search_sort") {
                SearchSortBar(
                    searchQuery = uiState.searchQuery,
                    sortOrder = uiState.sortOrder,
                    onSearchQueryChange = viewModel::onSearchQueryChange,
                    onSortOrderChange = viewModel::onSortOrderChange,
                )
                HorizontalDivider()
            }

            if (uiState.parseWarnings.isNotEmpty()) {
                item(key = "warnings") {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Parse Warnings", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            uiState.parseWarnings.forEach { warning ->
                                Text(warning, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            if (displayed.isEmpty() && uiState.searchQuery.isNotBlank()) {
                item {
                    Text(
                        "No tags match \"${uiState.searchQuery}\"",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            } else {
                items(displayed, key = { it.id }) { instrument ->
                    TagReviewRow(
                        instrument = instrument,
                        onDelete = { viewModel.deleteInstrument(instrument.id) },
                        onEdit = { newTagId -> viewModel.updateInstrumentTag(instrument.id, newTagId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TagReviewRow(
    instrument: InstrumentEntity,
    onDelete: () -> Unit,
    onEdit: (String) -> Unit,
) {
    var showEditDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(instrument.tagId, fontWeight = FontWeight.Bold)
                instrument.instrumentType?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                Text("Page ${instrument.pidPageNumber}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            IconButton(onClick = { showEditDialog = true }) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showEditDialog) {
        var editText by remember { mutableStateOf(instrument.tagId) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Tag ID") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    label = { Text("Tag ID (e.g. LIT-5219)") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(onClick = { onEdit(editText); showEditDialog = false }, enabled = editText.isNotBlank()) {
                    Text("Save")
                }
            },
            dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text("Cancel") } },
        )
    }
}
