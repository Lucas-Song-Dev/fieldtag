package com.fieldtag.ui.diagram

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.fieldtag.data.db.entities.InstrumentEntity
import com.fieldtag.data.db.entities.PidDocumentEntity
import com.fieldtag.ui.instrument.statusColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagramViewerScreen(
    projectId: String,
    pidDocuments: List<PidDocumentEntity>,
    instruments: List<InstrumentEntity>,
    onInstrumentClick: (String) -> Unit,
) {
    if (pidDocuments.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No P&ID imported yet", color = MaterialTheme.colorScheme.outline)
        }
        return
    }

    val doc = pidDocuments.first()
    val scope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState()

    var selectedInstrument by remember { mutableStateOf<InstrumentEntity?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Page state
    var currentPage by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Load the correct page whenever currentPage changes; dispose on exit
    LaunchedEffect(doc.filePath, currentPage) {
        withContext(Dispatchers.IO) {
            val file = File(doc.filePath)
            if (!file.exists()) return@withContext
            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val pages = renderer.pageCount
                val pageIdx = currentPage.coerceIn(0, pages - 1)
                val page = renderer.openPage(pageIdx)
                val newBitmap = Bitmap.createBitmap(
                    page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888
                )
                page.render(newBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                pfd.close()
                // Swap on Main thread; recycle old bitmap
                withContext(Dispatchers.Main) {
                    pageBitmap?.recycle()
                    pageBitmap = newBitmap
                    if (totalPages == 0) totalPages = pages
                }
            } catch (_: Exception) {}
        }
    }

    // Clean up the last bitmap when the composable leaves composition
    DisposableEffect(Unit) { onDispose { pageBitmap?.recycle() } }

    // Only show instruments that belong to the currently displayed page (1-based)
    val pageInstruments = remember(instruments, currentPage) {
        instruments.filter { it.pidPageNumber == currentPage + 1 }
    }

    LaunchedEffect(selectedInstrument) {
        if (selectedInstrument != null) scaffoldState.bottomSheetState.expand()
        else scaffoldState.bottomSheetState.partialExpand()
    }

    // imageRect is set by Canvas DrawScope each frame so the tap handler uses the same rect
    val imageRect = remember { mutableStateOf(Rect.Zero) }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 0.dp,
        sheetContent = {
            selectedInstrument?.let { instrument ->
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    Text(
                        instrument.tagId,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    instrument.instrumentType?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Status: ${instrument.fieldStatus.name.replace("_", " ")}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { onInstrumentClick(instrument.id) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text("Open") }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
    ) { paddingValues ->

        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            scale = (scale * zoomChange).coerceIn(0.3f, 12f)
            panOffset += panChange
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            val bitmap = pageBitmap
            if (bitmap != null) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(transformState)
                        .pointerInput(pageInstruments) {
                            detectTapGestures { tapScreenPt ->
                                // Convert screen → canvas space (undo graphicsLayer transform)
                                val cx = (tapScreenPt.x - panOffset.x) / scale
                                val cy = (tapScreenPt.y - panOffset.y) / scale
                                // Normalise within the drawn image rect
                                val rect = imageRect.value
                                if (rect.width == 0f || rect.height == 0f) return@detectTapGestures
                                val normX = ((cx - rect.left) / rect.width).coerceIn(0f, 1f)
                                val normY = ((cy - rect.top) / rect.height).coerceIn(0f, 1f)
                                val hit = pageInstruments.firstOrNull { instr ->
                                    val ix = instr.pidX ?: return@firstOrNull false
                                    val iy = instr.pidY ?: return@firstOrNull false
                                    kotlin.math.hypot(
                                        (ix - normX).toDouble(),
                                        (iy - normY).toDouble()
                                    ) < 0.025
                                }
                                selectedInstrument = hit
                                if (hit == null) scope.launch {
                                    scaffoldState.bottomSheetState.partialExpand()
                                }
                            }
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

                    // Fit the bitmap to the canvas (letterbox / pillarbox), keep aspect ratio
                    val fitScale = minOf(size.width / bmpW, size.height / bmpH)
                    val drawW = bmpW * fitScale
                    val drawH = bmpH * fitScale
                    val imgLeft = (size.width - drawW) / 2f
                    val imgTop = (size.height - drawH) / 2f

                    // Publish the drawn rect for the tap handler (same-frame accuracy is fine)
                    imageRect.value = Rect(imgLeft, imgTop, imgLeft + drawW, imgTop + drawH)

                    // Draw the PDF page fitted to canvas
                    drawImage(
                        image = bitmap.asImageBitmap(),
                        dstOffset = IntOffset(imgLeft.toInt(), imgTop.toInt()),
                        dstSize = IntSize(drawW.toInt(), drawH.toInt()),
                    )

                    // Draw instrument dots at their tag text positions
                    pageInstruments.forEach { instr ->
                        val nx = instr.pidX ?: return@forEach
                        val ny = instr.pidY ?: return@forEach
                        // Map normalised [0,1] coords to canvas pixel position within the drawn image
                        val dotCx = imgLeft + nx * drawW
                        val dotCy = imgTop + ny * drawH
                        val dotColor = statusColor(instr.fieldStatus)
                        drawCircle(dotColor, radius = 14f, center = Offset(dotCx, dotCy))
                        // Highlight ring when selected
                        if (instr == selectedInstrument) {
                            drawCircle(
                                Color.White,
                                radius = 18f,
                                center = Offset(dotCx, dotCy),
                                style = Stroke(width = 3f),
                            )
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading diagram…", color = MaterialTheme.colorScheme.outline)
                }
            }

            // Page navigation bar — shown only when the document has multiple pages
            if (totalPages > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.60f),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            currentPage--
                            scale = 1f
                            panOffset = Offset.Zero
                        },
                        enabled = currentPage > 0,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.NavigateBefore,
                            contentDescription = "Previous page",
                            tint = Color.White,
                        )
                    }
                    Text(
                        text = "${currentPage + 1} / $totalPages",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    IconButton(
                        onClick = {
                            currentPage++
                            scale = 1f
                            panOffset = Offset.Zero
                        },
                        enabled = currentPage < totalPages - 1,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.NavigateNext,
                            contentDescription = "Next page",
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}
