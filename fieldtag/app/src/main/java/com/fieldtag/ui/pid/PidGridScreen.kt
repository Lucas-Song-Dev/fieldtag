package com.fieldtag.ui.pid

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PidGridScreen(
    projectId: String,
    pidDocumentId: String,
    onBack: () -> Unit,
    onInstrumentReady: (instrumentId: String) -> Unit,
    viewModel: PidGridViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState()

    // Observe navigation events from the ViewModel
    LaunchedEffect(Unit) {
        viewModel.navigateToInstrument.collect { instrumentId ->
            onInstrumentReady(instrumentId)
        }
    }

    // Open / close the bottom sheet based on selection result
    LaunchedEffect(uiState.selectionResult) {
        when (uiState.selectionResult) {
            is TagSelectionResult.SingleFound -> scope.launch { scaffoldState.bottomSheetState.expand() }
            else -> scope.launch { scaffoldState.bottomSheetState.partialExpand() }
        }
    }

    // Error dialog state (for NotFound / MultipleFound)
    val showErrorDialog = uiState.selectionResult is TagSelectionResult.NotFound ||
            uiState.selectionResult is TagSelectionResult.MultipleFound

    if (showErrorDialog) {
        val (title, message) = when (val r = uiState.selectionResult) {
            is TagSelectionResult.NotFound -> Pair(
                "No tag found",
                "No instrument tag was detected in that area.\n\nTips:\n• Try a slightly larger selection\n• Check the PDF has a selectable text layer (not a scanned image)",
            )
            is TagSelectionResult.MultipleFound -> Pair(
                "Ambiguous selection",
                "Multiple tags found in that area:\n${r.tags.joinToString("\n") { "  • ${it.tagId}" }}\n\nTry a smaller, more precise selection.",
            )
            else -> Pair("", "")
        }
        AlertDialog(
            onDismissRequest = { viewModel.clearResult() },
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearResult() }) { Text("OK") }
            },
        )
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 0.dp,
        sheetContent = {
            val result = uiState.selectionResult
            if (result is TagSelectionResult.SingleFound) {
                TagFoundSheet(
                    result = result,
                    onConfirmNew = { viewModel.confirmNewTag(result.tag) },
                    onOpenExisting = { viewModel.openExistingInstrument(result.existing!!.id) },
                    onDismiss = { viewModel.clearResult() },
                )
            } else {
                Spacer(Modifier.height(1.dp))
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("P&ID Grid", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (uiState.totalPages > 0) {
                            IconButton(
                                onClick = { viewModel.prevPage() },
                                enabled = uiState.currentPage > 0,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.NavigateBefore,
                                    contentDescription = "Previous page",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                            Text(
                                "${uiState.currentPage + 1} / ${uiState.totalPages}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            IconButton(
                                onClick = { viewModel.nextPage() },
                                enabled = uiState.currentPage < uiState.totalPages - 1,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.NavigateNext,
                                    contentDescription = "Next page",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                },
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
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                uiState.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Loading diagram…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                uiState.errorMessage != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(uiState.errorMessage ?: "", color = MaterialTheme.colorScheme.error)
                    }
                }
                else -> {
                    PidGridCanvas(
                        uiState = uiState,
                        onRegionSelected = { x1, y1, x2, y2 ->
                            viewModel.onRegionSelected(x1, y1, x2, y2)
                        },
                    )
                }
            }
        }
    }
}

// ─── Grid + drag canvas ───────────────────────────────────────────────────────

@Composable
private fun PidGridCanvas(
    uiState: PidGridUiState,
    onRegionSelected: (x1: Float, y1: Float, x2: Float, y2: Float) -> Unit,
) {
    val bitmap = uiState.pageBitmap ?: return

    // Pinch-to-zoom / pan state
    var scale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.3f, 12f)
        panOffset += panChange
    }

    // Drag selection state (screen-space pixels)
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }

    // The drawn image rect is computed each frame inside the Canvas DrawScope
    // and shared with the gesture handler so both use the same coordinate space.
    val imageRect = remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }

    val gridColor = Color.White.copy(alpha = 0.40f)
    val selectionFillColor = Color(0x334FC3F7)
    val selectionBorderColor = Color(0xFF0288D1)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .transformable(transformState)
            .pointerInput(bitmap) {
                detectDragGestures(
                    onDragStart = { pos ->
                        dragStart = pos
                        dragCurrent = pos
                    },
                    onDrag = { change, _ -> dragCurrent = change.position },
                    onDragEnd = {
                        val start = dragStart
                        val end = dragCurrent
                        if (start != null && end != null) {
                            val rect = imageRect.value
                            if (rect.width > 0f && rect.height > 0f) {
                                // Convert screen coord → canvas coord → normalised image coord
                                fun normX(sx: Float): Float {
                                    val cx = (sx - panOffset.x) / scale
                                    return ((cx - rect.left) / rect.width).coerceIn(0f, 1f)
                                }
                                fun normY(sy: Float): Float {
                                    val cy = (sy - panOffset.y) / scale
                                    return ((cy - rect.top) / rect.height).coerceIn(0f, 1f)
                                }
                                onRegionSelected(
                                    minOf(normX(start.x), normX(end.x)),
                                    minOf(normY(start.y), normY(end.y)),
                                    maxOf(normX(start.x), normX(end.x)),
                                    maxOf(normY(start.y), normY(end.y)),
                                )
                            }
                        }
                        dragStart = null
                        dragCurrent = null
                    },
                    onDragCancel = {
                        dragStart = null
                        dragCurrent = null
                    },
                )
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = panOffset.x,
                    translationY = panOffset.y,
                ),
        ) {
            val bmpW = bitmap.width.toFloat()
            val bmpH = bitmap.height.toFloat()

            // Fit the PDF page into the canvas (letterbox / pillarbox)
            val fitScale = minOf(size.width / bmpW, size.height / bmpH)
            val drawW = bmpW * fitScale
            val drawH = bmpH * fitScale
            val imgLeft = (size.width - drawW) / 2f
            val imgTop = (size.height - drawH) / 2f

            // Publish for gesture handler (updated each frame — acceptable side-effect pattern)
            imageRect.value = androidx.compose.ui.geometry.Rect(
                imgLeft, imgTop, imgLeft + drawW, imgTop + drawH
            )

            // 1. Draw the PDF page fitted to canvas
            drawImage(
                image = bitmap.asImageBitmap(),
                dstOffset = IntOffset(imgLeft.toInt(), imgTop.toInt()),
                dstSize = IntSize(drawW.toInt(), drawH.toInt()),
            )

            // 2. Grid overlay — drawn only over the image area
            val cols = 10
            val rows = 10
            for (col in 1..cols - 1) {
                val x = imgLeft + drawW * col / cols
                drawLine(gridColor, Offset(x, imgTop), Offset(x, imgTop + drawH), strokeWidth = 1.5f)
            }
            for (row in 1..rows - 1) {
                val y = imgTop + drawH * row / rows
                drawLine(gridColor, Offset(imgLeft, y), Offset(imgLeft + drawW, y), strokeWidth = 1.5f)
            }

            // 3. Live selection rectangle while the user is dragging
            val start = dragStart
            val curr = dragCurrent
            if (start != null && curr != null) {
                // Convert screen → canvas coords (undo graphicsLayer)
                fun toCanvas(s: Offset) = Offset(
                    (s.x - panOffset.x) / scale,
                    (s.y - panOffset.y) / scale,
                )
                val cs = toCanvas(start)
                val ce = toCanvas(curr)
                val rl = minOf(cs.x, ce.x)
                val rt = minOf(cs.y, ce.y)
                val rw = kotlin.math.abs(ce.x - cs.x)
                val rh = kotlin.math.abs(ce.y - cs.y)
                drawRect(selectionFillColor, Offset(rl, rt), Size(rw, rh))
                drawRect(selectionBorderColor, Offset(rl, rt), Size(rw, rh), style = Stroke(2f))
            }
        }
    }
}

// ─── Result bottom sheet ──────────────────────────────────────────────────────

@Composable
private fun TagFoundSheet(
    result: TagSelectionResult.SingleFound,
    onConfirmNew: () -> Unit,
    onOpenExisting: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(
            text = result.tag.tagId,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        result.tag.prefix.let { prefix ->
            val type = com.fieldtag.domain.parser.IsaTagDetector.instrumentTypeForPrefix(prefix)
            if (type != null) {
                Text(type, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            }
        }
        Text(
            "Page ${result.tag.page}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (result.existing != null) {
            // Tag already exists in the project
            Text(
                "Already in project",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onOpenExisting,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Open ${result.existing.tagId}")
            }
        } else {
            // New tag — offer to create it
            Text(
                "New instrument — not yet in project",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onConfirmNew,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Add ${result.tag.tagId} to Project")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Cancel")
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
