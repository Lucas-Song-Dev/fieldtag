package com.fieldtag.ui.diagram

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import com.fieldtag.data.db.entities.OverlayShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.fieldtag.data.db.entities.FieldStatus
import com.fieldtag.data.db.entities.InstrumentEntity
import com.fieldtag.data.db.entities.PidDocumentEntity
import com.fieldtag.data.db.entities.PidPageCalibrationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.sqrt

// ── Status colours ────────────────────────────────────────────────────────────
private fun statusColor(status: FieldStatus) = when (status) {
    FieldStatus.COMPLETE      -> Color(0xFF4CAF50) // green
    FieldStatus.IN_PROGRESS   -> Color(0xFFFFC107) // amber
    FieldStatus.CANNOT_LOCATE -> Color(0xFFF44336) // red
    FieldStatus.NOT_STARTED   -> Color(0xFF9E9E9E) // grey
}

// Fallback normalized size when calibration is absent
private const val DEFAULT_CALIB = 0.025f

@Composable
fun DiagramViewerScreen(
    projectId: String,
    pidDocuments: List<PidDocumentEntity>,
    instruments: List<InstrumentEntity>,
    onInstrumentClick: (String) -> Unit,
    /** Current page index (0-based), driven by ProjectDetailViewModel. */
    currentPage: Int = 0,
    /** Called once when the PDF is first opened and the total page count is known. */
    onTotalPagesLoaded: (Int) -> Unit = {},
    onPointTapped: (bitmap: Bitmap, normX: Float, normY: Float, pageIndex: Int) -> Unit = { _, _, _, _ -> },
    /** Called when the user single-taps an existing instrument node on the diagram. */
    onInstrumentNodeTapped: (instrumentId: String) -> Unit = {},
    /** Document-level fallback calibrated bubble width from PidDocumentEntity (null = not yet calibrated). */
    calibrationWidth: Float? = null,
    /** Document-level fallback calibrated bubble height from PidDocumentEntity (null = not yet calibrated). */
    calibrationHeight: Float? = null,
    /** When true, draw the tag ID label above each outline rectangle. */
    showTooltips: Boolean = false,
    /** Default shape for all instrument overlays (from document calibration). */
    calibrationShape: OverlayShape = OverlayShape.RECTANGLE,
    /**
     * Per-page calibration overrides keyed by 1-based page number.
     * When a page has an entry, its values take precedence over the document-level calibration.
     */
    pageCalibrations: Map<Int, PidPageCalibrationEntity> = emptyMap(),
    centeredInstrumentId: String? = null,
    onCenterConsumed: () -> Unit = {},
) {
    if (pidDocuments.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No P&ID imported yet", color = MaterialTheme.colorScheme.outline)
        }
        return
    }

    val doc = pidDocuments.first()

    var scale      by remember { mutableFloatStateOf(1f) }
    var panOffset  by remember { mutableStateOf(Offset.Zero) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(currentPage) {
        scale = 1f
        panOffset = Offset.Zero
    }

    LaunchedEffect(doc.filePath, currentPage) {
        withContext(Dispatchers.IO) {
            val file = File(doc.filePath)
            if (!file.exists()) return@withContext
            try {
                val pfd      = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val pages    = renderer.pageCount
                val pageIdx  = currentPage.coerceIn(0, pages - 1)
                val page     = renderer.openPage(pageIdx)
                val bmp      = Bitmap.createBitmap(
                    page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888
                )
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                pfd.close()
                withContext(Dispatchers.Main) {
                    pageBitmap?.recycle()
                    pageBitmap = bmp
                    onTotalPagesLoaded(pages)
                }
            } catch (_: Exception) {}
        }
    }

    DisposableEffect(Unit) { onDispose { pageBitmap?.recycle() } }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale     = (scale * zoomChange).coerceIn(0.3f, 12f)
        panOffset += panChange
    }

    val imageRect  = remember { mutableStateOf(Rect.Zero) }
    val canvasSize = remember { mutableStateOf(Size.Zero) }

    // Instruments on this page that have been placed (have normalized coordinates)
    val placedOnPage = instruments.filter {
        it.pidPageNumber == currentPage + 1 && it.pidX != null && it.pidY != null
    }

    LaunchedEffect(centeredInstrumentId, placedOnPage.size) {
        if (centeredInstrumentId != null) {
            val target = placedOnPage.firstOrNull { it.id == centeredInstrumentId }
            val rect = imageRect.value
            val cs = canvasSize.value
            if (target != null && target.pidX != null && target.pidY != null && rect.width > 0f) {
                scale = 3.5f
                val cx = rect.left + target.pidX * rect.width
                val cy = rect.top + target.pidY * rect.height
                val pivotX = cs.width / 2f
                val pivotY = cs.height / 2f
                panOffset = Offset((pivotX - cx) * scale, (pivotY - cy) * scale)
                onCenterConsumed()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val bitmap = pageBitmap
        if (bitmap != null) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(transformState)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { tapPt ->
                                val rect = imageRect.value
                                val cs   = canvasSize.value
                                if (rect.width <= 0f || cs.width <= 0f) return@detectTapGestures

                                val pivotX = cs.width  / 2f
                                val pivotY = cs.height / 2f
                                val cx = (tapPt.x - pivotX - panOffset.x) / scale + pivotX
                                val cy = (tapPt.y - pivotY - panOffset.y) / scale + pivotY
                                val normX = ((cx - rect.left) / rect.width ).coerceIn(0f, 1f)
                                val normY = ((cy - rect.top ) / rect.height).coerceIn(0f, 1f)

                                // Hit threshold: half the calibrated width/height in norm coords
                                val threshX = (calibrationWidth  ?: DEFAULT_CALIB) / 2f
                                val threshY = (calibrationHeight ?: DEFAULT_CALIB) / 2f

                                val hit = placedOnPage.firstOrNull { instr ->
                                    val dx = (instr.pidX ?: 0f) - normX
                                    val dy = (instr.pidY ?: 0f) - normY
                                    kotlin.math.abs(dx) <= threshX && kotlin.math.abs(dy) <= threshY
                                }
                                if (hit != null) onInstrumentNodeTapped(hit.id)
                            },
                            onDoubleTap = { tapPt ->
                                val rect = imageRect.value
                                val cs   = canvasSize.value
                                if (rect.width > 0f && rect.height > 0f && cs.width > 0f) {
                                    val pivotX = cs.width  / 2f
                                    val pivotY = cs.height / 2f
                                    val cx = (tapPt.x - pivotX - panOffset.x) / scale + pivotX
                                    val cy = (tapPt.y - pivotY - panOffset.y) / scale + pivotY
                                    val normX = ((cx - rect.left) / rect.width ).coerceIn(0f, 1f)
                                    val normY = ((cy - rect.top ) / rect.height).coerceIn(0f, 1f)
                                    Log.d("CALIB", "DOUBLE-TAP page=${currentPage + 1}" +
                                        " norm=(${"%.4f".format(normX)}, ${"%.4f".format(normY)})")
                                    onPointTapped(bitmap, normX, normY, currentPage)
                                } else {
                                    Log.w("CALIB", "DOUBLE-TAP ignored — imageRect/canvasSize not ready")
                                }
                            },
                        )
                    }
                    .graphicsLayer(
                        scaleX       = scale,
                        scaleY       = scale,
                        translationX = panOffset.x,
                        translationY = panOffset.y,
                    ),
            ) {
                val bmpW     = bitmap.width.toFloat()
                val bmpH     = bitmap.height.toFloat()
                val fitScale = minOf(size.width / bmpW, size.height / bmpH)
                val drawW    = bmpW * fitScale
                val drawH    = bmpH * fitScale
                val imgLeft  = (size.width  - drawW) / 2f
                val imgTop   = (size.height - drawH) / 2f

                canvasSize.value = size
                imageRect.value  = Rect(imgLeft, imgTop, imgLeft + drawW, imgTop + drawH)

                // ── PDF page ─────────────────────────────────────────────────
                drawImage(
                    image     = bitmap.asImageBitmap(),
                    dstOffset = IntOffset(imgLeft.toInt(), imgTop.toInt()),
                    dstSize   = IntSize(drawW.toInt(), drawH.toInt()),
                )

                // ── Instrument outline overlays ───────────────────────────────
                // Stroke stays visually ~2dp regardless of zoom level
                val strokeWidth = 2f / scale

                // Text paint reused for all tooltips
                val textPaint = if (showTooltips) android.graphics.Paint().apply {
                    color       = android.graphics.Color.WHITE
                    textSize    = (11f * density) / scale   // ~11sp, constant screen size
                    typeface    = android.graphics.Typeface.DEFAULT_BOLD
                    isAntiAlias = true
                    textAlign   = android.graphics.Paint.Align.CENTER
                } else null

                for (instr in placedOnPage) {
                    val nx = instr.pidX ?: continue
                    val ny = instr.pidY ?: continue

                    val cx = imgLeft + nx * drawW
                    val cy = imgTop + ny * drawH

                    // Resolve calibration: per-page → doc-level → default
                    val pageCalib = pageCalibrations[instr.pidPageNumber]
                    val effW = pageCalib?.calibrationWidth ?: calibrationWidth ?: DEFAULT_CALIB
                    val effH = pageCalib?.calibrationHeight ?: calibrationHeight ?: DEFAULT_CALIB

                    // Scale proportionally with the diagram (no /scale — tags grow as user zooms in)
                    val hw = effW * drawW / 2f
                    val hh = effH * drawH / 2f

                    val color = statusColor(instr.fieldStatus)
                    val effectiveShape = pageCalib?.calibrationShape ?: instr.overlayShape ?: calibrationShape
                    val shape = effectiveShape

                    // Colored outline — shape determined by per-instrument override or doc default
                    when (shape) {
                        OverlayShape.RECTANGLE -> drawRect(
                            color   = color,
                            topLeft = Offset(cx - hw, cy - hh),
                            size    = Size(hw * 2f, hh * 2f),
                            style   = Stroke(width = strokeWidth),
                        )
                        OverlayShape.DIAMOND -> {
                            val diamondPath = Path().apply {
                                moveTo(cx,      cy - hh)
                                lineTo(cx + hw, cy)
                                lineTo(cx,      cy + hh)
                                lineTo(cx - hw, cy)
                                close()
                            }
                            drawPath(
                                path  = diamondPath,
                                color = color,
                                style = Stroke(width = strokeWidth),
                            )
                        }
                    }

                    // Tooltip: tag ID in a small pill drawn above the rectangle
                    if (showTooltips && textPaint != null) {
                        drawIntoCanvas { canvas ->
                            val label    = instr.tagId
                            val textW    = textPaint.measureText(label)
                            val padding  = 4f * density / scale
                            val pillW    = textW + padding * 2f
                            val pillH    = textPaint.textSize + padding * 2f
                            val pillLeft = cx - pillW / 2f
                            val pillTop  = cy - hh - pillH - (2f * density / scale)
                            val pillRect = android.graphics.RectF(
                                pillLeft, pillTop,
                                pillLeft + pillW, pillTop + pillH,
                            )
                            val cornerR  = pillH / 3f

                            // Background pill
                            val bgPaint = android.graphics.Paint().apply {
                                this.color = android.graphics.Color.argb(
                                    210,
                                    ((color.red   * 255).toInt()),
                                    ((color.green * 255).toInt()),
                                    ((color.blue  * 255).toInt()),
                                )
                                isAntiAlias = true
                            }
                            canvas.nativeCanvas.drawRoundRect(pillRect, cornerR, cornerR, bgPaint)

                            // Tag text
                            val textY = pillTop + pillH / 2f + textPaint.textSize * 0.35f
                            canvas.nativeCanvas.drawText(label, cx, textY, textPaint)
                        }
                    }
                }
            }

            // Hint badge
            Text(
                text     = "Double-tap a tag label to identify it",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .background(
                        Color.Black.copy(alpha = 0.45f),
                        androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading diagram…", color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
