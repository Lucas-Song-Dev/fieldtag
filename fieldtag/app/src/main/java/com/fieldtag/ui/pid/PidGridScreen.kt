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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.navigation.compose.hiltViewModel
import com.fieldtag.ui.theme.Dimens
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

    // Dialogs for selection results
    val haptic = LocalHapticFeedback.current
    when (val result = uiState.selectionResult) {
        is TagSelectionResult.NotFound -> {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            AlertDialog(
                onDismissRequest = { viewModel.clearResult() },
                modifier = Modifier.fillMaxWidth(Dimens.DialogWidthPercentage),
                title = { Text("No tag found", fontWeight = FontWeight.Bold) },
                text = { Text("No instrument tag was detected at that position.\n\nTips:\n• Double-tap directly on a tag label (e.g. FIC-5185)\n• Check the PDF has a selectable text layer (not a scanned image)") },
                confirmButton = { TextButton(onClick = { viewModel.clearResult() }, modifier = Modifier.height(Dimens.MinTouchTarget)) { Text("OK") } },
            )
        }
        is TagSelectionResult.NotFoundWithSuggestions -> {
            var manualTagId by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { viewModel.clearResult() },
                title = { Text("No tag detected", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        if (result.suggestions.isNotEmpty()) {
                            Text("Nearby text on diagram:", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline)
                            result.suggestions.take(4).forEach { s ->
                                Text("  · $s", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        Text("Enter tag ID to create it manually at this location:",
                            style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = manualTagId,
                            onValueChange = { manualTagId = it.uppercase() },
                            label = { Text("Tag ID (e.g. PIC-5218)") },
                            singleLine = true,
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            // Reuse ProjectDetailViewModel's logic via a direct ViewModel call
                            // isn't possible here; build the instrument inline.
                            if (manualTagId.isNotBlank()) {
                                val rawTag = com.fieldtag.domain.parser.IsaTagDetector
                                    .detectInText(manualTagId.trim(), result.page).firstOrNull()
                                val syntheticTag = com.fieldtag.domain.parser.ParsedTag(
                                    tagId = manualTagId.trim().uppercase(),
                                    prefix = rawTag?.prefix ?: "",
                                    number = rawTag?.number ?: "",
                                    page = result.page,
                                    x = result.normX,
                                    y = result.normY,
                                )
                                viewModel.confirmNewTag(syntheticTag)
                            }
                        },
                        enabled = manualTagId.isNotBlank(),
                        modifier = Modifier.height(Dimens.MinTouchTarget)
                    ) { Text("Create") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { viewModel.clearResult() }, modifier = Modifier.height(Dimens.MinTouchTarget)) { Text("Cancel") }
                },
                modifier = Modifier.fillMaxWidth(Dimens.DialogWidthPercentage)
            )
        }
        is TagSelectionResult.MultipleFound -> {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            AlertDialog(
                onDismissRequest = { viewModel.clearResult() },
                modifier = Modifier.fillMaxWidth(Dimens.DialogWidthPercentage),
                title = { Text("Multiple tags found", fontWeight = FontWeight.Bold) },
                text = { Text("Several tags are close to where you tapped:\n${result.tags.joinToString("\n") { "  • ${it.tagId}" }}\n\nTry tapping more precisely on one tag label.") },
                confirmButton = { TextButton(onClick = { viewModel.clearResult() }, modifier = Modifier.height(Dimens.MinTouchTarget)) { Text("OK") } },
            )
        }
        else -> { /* Idle / SingleFound handled by bottom sheet below */ }
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
                        onPointTapped = { normX, normY ->
                            viewModel.onPointTapped(normX, normY)
                        },
                    )
                }
            }
        }
    }
}

// ─── Grid + double-tap canvas ─────────────────────────────────────────────────

@Composable
private fun PidGridCanvas(
    uiState: PidGridUiState,
    onPointTapped: (normX: Float, normY: Float) -> Unit,
) {
    val bitmap = uiState.pageBitmap ?: return

    var scale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.3f, 12f)
        panOffset += panChange
    }

    // Shared between Canvas DrawScope and tap handler — updated every frame
    val imageRect  = remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val canvasSize = remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    val gridColor = Color.White.copy(alpha = 0.35f)

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .transformable(transformState)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tapScreenPt ->
                            val rect = imageRect.value
                            val cs   = canvasSize.value
                            if (rect.width > 0f && rect.height > 0f && cs.width > 0f) {
                                // Center-pivot inverse (TransformOrigin defaults to 0.5, 0.5)
                                val pivotX = cs.width  / 2f
                                val pivotY = cs.height / 2f
                                val cx = (tapScreenPt.x - pivotX - panOffset.x) / scale + pivotX
                                val cy = (tapScreenPt.y - pivotY - panOffset.y) / scale + pivotY
                                val normX = ((cx - rect.left) / rect.width ).coerceIn(0f, 1f)
                                val normY = ((cy - rect.top ) / rect.height).coerceIn(0f, 1f)
                                onPointTapped(normX, normY)
                            }
                        },
                    )
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = panOffset.x,
                    translationY = panOffset.y,
                ),
        ) {
            val bmpW = bitmap.width.toFloat()
            val bmpH = bitmap.height.toFloat()

            val fitScale = minOf(size.width / bmpW, size.height / bmpH)
            val drawW = bmpW * fitScale
            val drawH = bmpH * fitScale
            val imgLeft = (size.width - drawW) / 2f
            val imgTop = (size.height - drawH) / 2f

            canvasSize.value = size   // publish for tap handler
            imageRect.value = androidx.compose.ui.geometry.Rect(
                imgLeft, imgTop, imgLeft + drawW, imgTop + drawH
            )

            drawImage(
                image = bitmap.asImageBitmap(),
                dstOffset = IntOffset(imgLeft.toInt(), imgTop.toInt()),
                dstSize = IntSize(drawW.toInt(), drawH.toInt()),
            )

            // Subtle grid overlay over the image area only
            for (col in 1..9) {
                val x = imgLeft + drawW * col / 10f
                drawLine(gridColor, Offset(x, imgTop), Offset(x, imgTop + drawH), strokeWidth = 1f)
            }
            for (row in 1..9) {
                val y = imgTop + drawH * row / 10f
                drawLine(gridColor, Offset(imgLeft, y), Offset(imgLeft + drawW, y), strokeWidth = 1f)
            }
        }

        // Subtle hint text at the bottom of the canvas
        Text(
            text = "Double-tap a tag label to identify it",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .background(
                    Color.Black.copy(alpha = 0.45f),
                    androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 12.dp, vertical = 4.dp),
            color = Color.White,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        )
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
                modifier = Modifier.fillMaxWidth().height(Dimens.MinTouchTarget),
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
                modifier = Modifier.fillMaxWidth().height(Dimens.MinTouchTarget),
            ) {
                Text("Add ${result.tag.tagId} to Project")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(Dimens.MinTouchTarget),
        ) {
            Text("Cancel")
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
