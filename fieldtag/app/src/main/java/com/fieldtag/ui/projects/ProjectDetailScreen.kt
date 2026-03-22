package com.fieldtag.ui.projects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LabelOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.fieldtag.data.db.entities.OverlayShape
import com.fieldtag.ui.diagram.DiagramViewerScreen
import com.fieldtag.ui.instrument.InstrumentListScreen
import com.fieldtag.ui.pid.TagSelectionResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    projectId: String,
    onBack: () -> Unit,
    onImportPid: () -> Unit,
    onInstrumentClick: (String) -> Unit,
    onExport: () -> Unit,
    onRecalibrate: (pidDocumentId: String) -> Unit = {},
    viewModel: ProjectDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val project = uiState.project

    // Navigate to an instrument when the diagram tap creates / finds one
    LaunchedEffect(Unit) {
        viewModel.navigateToInstrument.collect { instrumentId ->
            onInstrumentClick(instrumentId)
        }
    }

    // ── Tap result dialogs ────────────────────────────────────────────────────
    when (val tapResult = uiState.diagramTapResult) {
        is TagSelectionResult.NotFound -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearDiagramTapResult() },
                title = { Text("No tag found", fontWeight = FontWeight.Bold) },
                text = { Text("No instrument tag was detected at that position.\n\nTip: Double-tap directly on a tag label (e.g. FIC-5185). The text layer must be selectable (not a scanned image).") },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearDiagramTapResult() }) { Text("OK") }
                },
            )
        }
        is TagSelectionResult.NotFoundWithSuggestions -> {
            // If OCR found text lines, pre-select the first; otherwise jump straight to custom input
            val hasSuggestions = tapResult.suggestions.isNotEmpty()
            var selectedLine     by remember { mutableStateOf(if (hasSuggestions) tapResult.suggestions.first() else null) }
            var showCustomInput  by remember { mutableStateOf(!hasSuggestions) }
            var customTagId      by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { viewModel.clearDiagramTapResult() },
                title = { Text("No ISA tag detected", fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        if (hasSuggestions) {
                            Text(
                                "OCR found these lines — select one or enter your own:",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            tapResult.suggestions.forEach { line ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedLine = line; showCustomInput = false }
                                        .padding(vertical = 2.dp),
                                ) {
                                    RadioButton(
                                        selected = !showCustomInput && selectedLine == line,
                                        onClick  = { selectedLine = line; showCustomInput = false },
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(line, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            // "Enter my own" option
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showCustomInput = true; selectedLine = null }
                                    .padding(vertical = 2.dp),
                            ) {
                                RadioButton(
                                    selected = showCustomInput,
                                    onClick  = { showCustomInput = true; selectedLine = null },
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Enter my own…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color  = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else {
                            Text(
                                "No text was recognized at that location.\nEnter a tag ID manually:",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        if (showCustomInput || !hasSuggestions) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value         = customTagId,
                                onValueChange = { customTagId = it.uppercase() },
                                label         = { Text("Tag ID (e.g. PIC-5218)") },
                                singleLine    = true,
                                modifier      = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                },
                confirmButton = {
                    val finalTag = if (showCustomInput || !hasSuggestions) customTagId.trim() else selectedLine.orEmpty()
                    Button(
                        onClick = {
                            viewModel.moveToPendingCommit(
                                tagId = finalTag,
                                page  = tapResult.page,
                                normX = tapResult.normX,
                                normY = tapResult.normY,
                            )
                        },
                        enabled = finalTag.isNotBlank(),
                    ) { Text("Next") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { viewModel.clearDiagramTapResult() }) { Text("Cancel") }
                },
            )
        }
        is TagSelectionResult.MultipleFound -> {
            // Combine ISA-parsed tag IDs with any raw OCR lines not already covered,
            // so the user can pick "V-62" even if our ISA filter skipped it.
            val tagIds    = tapResult.tags.map { it.tagId }
            val extraLines = tapResult.rawLines.filter { raw ->
                tagIds.none { it.equals(raw, ignoreCase = true) }
            }
            val allOptions = tagIds + extraLines  // parsed tags first, then unrecognized lines

            var selectedOption  by remember { mutableStateOf(allOptions.firstOrNull()) }
            var showCustomInput by remember { mutableStateOf(false) }
            var customTagId     by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { viewModel.clearDiagramTapResult() },
                title = { Text("Select tag", fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            "Pick the correct tag or enter your own:",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(10.dp))

                        // ISA-parsed tags (shown first with a subtle label)
                        if (tagIds.isNotEmpty()) {
                            Text(
                                "Detected tags",
                                style = MaterialTheme.typography.labelSmall,
                                color  = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                            )
                            tagIds.forEach { id ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedOption = id; showCustomInput = false }
                                        .padding(vertical = 2.dp),
                                ) {
                                    RadioButton(
                                        selected = !showCustomInput && selectedOption == id,
                                        onClick  = { selectedOption = id; showCustomInput = false },
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(id, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        // Extra raw OCR lines the ISA filter skipped
                        if (extraLines.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Other recognized text",
                                style = MaterialTheme.typography.labelSmall,
                                color  = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                            )
                            extraLines.forEach { line ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedOption = line; showCustomInput = false }
                                        .padding(vertical = 2.dp),
                                ) {
                                    RadioButton(
                                        selected = !showCustomInput && selectedOption == line,
                                        onClick  = { selectedOption = line; showCustomInput = false },
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(line, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        // "Enter my own" option
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCustomInput = true; selectedOption = null }
                                .padding(vertical = 2.dp),
                        ) {
                            RadioButton(
                                selected = showCustomInput,
                                onClick  = { showCustomInput = true; selectedOption = null },
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Enter my own…",
                                style = MaterialTheme.typography.bodyMedium,
                                color  = MaterialTheme.colorScheme.primary,
                            )
                        }

                        if (showCustomInput) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value         = customTagId,
                                onValueChange = { customTagId = it.uppercase() },
                                label         = { Text("Tag ID (e.g. PIC-5218)") },
                                singleLine    = true,
                                modifier      = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                },
                confirmButton = {
                    val finalTag = if (showCustomInput) customTagId.trim() else selectedOption.orEmpty()
                    Button(
                        enabled = finalTag.isNotBlank(),
                        onClick = {
                            val matchedTag = tapResult.tags.firstOrNull {
                                it.tagId.equals(finalTag, ignoreCase = true)
                            }
                            viewModel.moveToPendingCommit(
                                tagId = finalTag,
                                page  = matchedTag?.page ?: tapResult.tags.firstOrNull()?.page ?: 1,
                                normX = matchedTag?.x    ?: tapResult.tags.firstOrNull()?.x ?: 0f,
                                normY = matchedTag?.y    ?: tapResult.tags.firstOrNull()?.y ?: 0f,
                            )
                        },
                    ) { Text("Next") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { viewModel.clearDiagramTapResult() }) { Text("Cancel") }
                },
            )
        }
        is TagSelectionResult.SingleFound -> {
            val existing = tapResult.existing
            AlertDialog(
                onDismissRequest = { viewModel.clearDiagramTapResult() },
                title = { Text(tapResult.tag.tagId, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        if (existing != null) {
                            Text("Already in project.", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Open its detail page?", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline)
                        } else {
                            Text("New instrument — not yet in this project.", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Add it now?", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (existing != null) viewModel.openExistingInstrumentFromDiagram(existing.id)
                        else viewModel.confirmNewTagFromDiagram(tapResult.tag)
                    }) {
                        Text(if (existing != null) "Open" else "Add to Project")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { viewModel.clearDiagramTapResult() }) { Text("Cancel") }
                },
            )
        }
        is TagSelectionResult.PendingCommit -> {
            // Final edit step: user can tweak the tag ID before it is saved
            var editedTagId by remember(tapResult.tagId) { mutableStateOf(tapResult.tagId) }
            AlertDialog(
                onDismissRequest = { viewModel.clearDiagramTapResult() },
                title = { Text("Confirm tag ID", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "Edit the tag ID if needed, then save:",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value         = editedTagId,
                            onValueChange = { editedTagId = it.uppercase() },
                            label         = { Text("Tag ID") },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    Button(
                        enabled = editedTagId.isNotBlank(),
                        onClick = {
                            viewModel.createManualTagFromDiagram(
                                tagId = editedTagId.trim(),
                                page  = tapResult.page,
                                normX = tapResult.normX,
                                normY = tapResult.normY,
                            )
                        },
                    ) { Text("Save") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { viewModel.clearDiagramTapResult() }) { Text("Cancel") }
                },
            )
        }
        else -> { /* Idle or null — no dialog */ }
    }

    // ── Instrument node-tap dialog (single-tap on a placed outline) ──────────
    val tappedInstrId = uiState.diagramTappedInstrumentId
    if (tappedInstrId != null) {
        val tappedInstr = uiState.instruments.firstOrNull { it.id == tappedInstrId }
        val docShape    = uiState.pidDocuments.firstOrNull()?.calibrationShape ?: OverlayShape.RECTANGLE
        var editedName  by remember(tappedInstrId) { mutableStateOf(tappedInstr?.tagId ?: "") }

        Dialog(
            onDismissRequest = { viewModel.clearInstrumentNodeTap() },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .heightIn(max = 560.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // ── Editable tag name ──────────────────────────────
                    Text(
                        "Tag ID",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value         = editedName,
                        onValueChange = { editedName = it },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        textStyle     = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    )

                    if (tappedInstr != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            tappedInstr.instrumentType ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Text(
                            "Page ${tappedInstr.pidPageNumber}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }

                    // ── Shape selector (placed instruments only) ───────
                    if (tappedInstr?.pidX != null) {
                        Spacer(Modifier.height(16.dp))
                        Text("Overlay shape", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(6.dp))
                        ShapeSelector(
                            activeShape    = tappedInstr.overlayShape ?: docShape,
                            onShapeSelected = { viewModel.updateInstrumentShape(tappedInstrId, it) },
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // ── Action buttons ─────────────────────────────────
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { viewModel.deleteInstrument(tappedInstrId) },
                            colors  = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) { Text("Delete") }
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(
                            onClick = { viewModel.clearInstrumentNodeTap() },
                            modifier = Modifier.padding(end = 8.dp),
                        ) { Text("Cancel") }
                        Button(onClick = {
                            if (editedName.isNotBlank()) viewModel.renameInstrument(tappedInstrId, editedName)
                            viewModel.clearInstrumentNodeTap()
                            onInstrumentClick(tappedInstrId)
                        }) { Text("Open") }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.selectedTab == 1 && uiState.isDiagramSearchActive) {
                        OutlinedTextField(
                            value = uiState.diagramSearchQuery,
                            onValueChange = viewModel::onDiagramSearchQueryChange,
                            placeholder = { Text("Search by Tag ID...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(48.dp).padding(end = 8.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface),
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            )
                        )
                    } else {
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
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                actions = {
                    // Page navigation + recalibrate live in the TopAppBar when Diagram tab is active
                    if (uiState.selectedTab == 1) {
                        IconButton(onClick = { viewModel.toggleDiagramSearch() }) {
                            Icon(Icons.Default.Search, contentDescription = "Search tags", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        if (uiState.diagramTotalPages > 1 && !uiState.isDiagramSearchActive) {
                            IconButton(
                                onClick = { viewModel.prevDiagramPage() },
                                enabled = uiState.diagramCurrentPage > 0,
                            ) {
                                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous page",
                                    tint = MaterialTheme.colorScheme.onPrimary)
                            }
                            Text(
                                text = "${uiState.diagramCurrentPage + 1}/${uiState.diagramTotalPages}",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelMedium,
                            )
                            IconButton(
                                onClick = { viewModel.nextDiagramPage() },
                                enabled = uiState.diagramCurrentPage < uiState.diagramTotalPages - 1,
                            ) {
                                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next page",
                                    tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                        // Recalibrate button — only shown when a PDF is imported
                        val pidDocId = uiState.pidDocuments.firstOrNull()?.id
                        if (pidDocId != null) {
                            // Toggle tag-ID tooltip labels on/off
                            IconButton(onClick = { viewModel.toggleDiagramTooltips() }) {
                                Icon(
                                    if (uiState.diagramShowTooltips) Icons.Default.LabelOff
                                    else Icons.Default.Label,
                                    contentDescription = if (uiState.diagramShowTooltips)
                                        "Hide tag labels" else "Show tag labels",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                            IconButton(onClick = { onRecalibrate(pidDocId) }) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = "Recalibrate instrument size",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
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
                1 -> {
                    if (uiState.isDiagramSearchActive && uiState.diagramSearchQuery.isNotBlank()) {
                        val searchResults = uiState.instruments.filter { 
                            it.tagId.contains(uiState.diagramSearchQuery, ignoreCase = true) 
                        }
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(searchResults) { inst ->
                                androidx.compose.material3.ListItem(
                                    headlineContent = { Text(inst.tagId, fontWeight = FontWeight.Bold) },
                                    supportingContent = { Text("Page ${inst.pidPageNumber}") },
                                    modifier = Modifier.clickable { viewModel.centerOnInstrument(inst) }
                                )
                            }
                            if (searchResults.isEmpty()) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        Text("No instrument matches found.", color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    } else {
                        DiagramViewerScreen(
                            projectId = projectId,
                            pidDocuments = uiState.pidDocuments,
                            instruments = uiState.instruments,
                            onInstrumentClick = onInstrumentClick,
                            currentPage = uiState.diagramCurrentPage,
                            onTotalPagesLoaded = { viewModel.onDiagramTotalPagesLoaded(it) },
                            onPointTapped = { bitmap, normX, normY, pageIndex ->
                                viewModel.onDiagramTapped(bitmap, normX, normY, pageIndex)
                            },
                            onInstrumentNodeTapped = { viewModel.onInstrumentNodeTapped(it) },
                            calibrationWidth  = uiState.pidDocuments.firstOrNull()?.calibrationWidth,
                            calibrationHeight = uiState.pidDocuments.firstOrNull()?.calibrationHeight,
                            showTooltips      = uiState.diagramShowTooltips,
                            calibrationShape  = uiState.pidDocuments.firstOrNull()?.calibrationShape ?: OverlayShape.RECTANGLE,
                            centeredInstrumentId = uiState.diagramCenteredInstrumentId,
                            onCenterConsumed  = viewModel::clearDiagramCenteredInstrument,
                        )
                    }
                }
            }
        }
    }
}

// ── Shape selector helper composable ─────────────────────────────────────────

@Composable
private fun ShapeSelector(
    activeShape: OverlayShape,
    onShapeSelected: (OverlayShape) -> Unit,
) {
    Row {
        OverlayShape.entries.forEach { shape ->
            val isActive = shape == activeShape
            val borderColor = if (isActive) MaterialTheme.colorScheme.primary
                              else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            val bgColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                Color.Transparent

            Surface(
                onClick = { onShapeSelected(shape) },
                shape  = RoundedCornerShape(8.dp),
                color  = bgColor,
                border = androidx.compose.foundation.BorderStroke(
                    if (isActive) 2.dp else 1.dp, borderColor,
                ),
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(24.dp)) {
                        val stroke = Stroke(width = 2.dp.toPx())
                        when (shape) {
                            OverlayShape.RECTANGLE -> drawRect(
                                color   = if (isActive) Color(0xFF1976D2) else Color(0xFF9E9E9E),
                                style   = stroke,
                            )
                            OverlayShape.DIAMOND -> {
                                val path = Path().apply {
                                    moveTo(size.width / 2f, 0f)
                                    lineTo(size.width,       size.height / 2f)
                                    lineTo(size.width / 2f,  size.height)
                                    lineTo(0f,               size.height / 2f)
                                    close()
                                }
                                drawPath(
                                    path  = path,
                                    color = if (isActive) Color(0xFF1976D2) else Color(0xFF9E9E9E),
                                    style = stroke,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
