package com.fieldtag.ui.calibrate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.fieldtag.data.db.entities.OverlayShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Calibration screen shown after a PDF is imported (and accessible via "Recalibrate" on the
 * diagram page).
 *
 * The user:
 *  1. Pinch-zooms / pans to get an instrument bubble to a comfortable size on screen.
 *  2. Drags the dashed box body to position it over the bubble.
 *  3. Drags a corner handle to resize the box to match the bubble exactly.
 *  4. Taps "Confirm Size" to save the calibration.
 *
 * Both zoom and box-drag are handled in a single [awaitEachGesture] block to prevent the
 * common Compose conflict where [detectDragGestures] consuming events causes [transformable]
 * to receive a "cancelled" signal and abort the pinch-zoom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagramCalibrateScreen(
    pidDocumentId: String,
    onBack: () -> Unit,
    onCalibrated: () -> Unit,
    viewModel: DiagramCalibrateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.calibrated.collect { onCalibrated() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.totalPages > 0) {
                        Text(
                            "Calibrate · Page ${uiState.currentPage + 1} / ${uiState.totalPages}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    } else {
                        Text("Calibrate Instrument Size", style = MaterialTheme.typography.titleMedium)
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
                actions = {
                    // Page navigation
                    if (uiState.totalPages > 1) {
                        IconButton(
                            onClick = { viewModel.goToPage(uiState.currentPage - 1) },
                            enabled = !uiState.isLoading && uiState.currentPage > 0,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.NavigateBefore,
                                contentDescription = "Previous page",
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        IconButton(
                            onClick = { viewModel.goToPage(uiState.currentPage + 1) },
                            enabled = !uiState.isLoading && uiState.currentPage < uiState.totalPages - 1,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.NavigateNext,
                                contentDescription = "Next page",
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                    val shapeLabel = when (uiState.boxShape) {
                        OverlayShape.RECTANGLE -> "□ Rect"
                        OverlayShape.DIAMOND -> "◇ Diamond"
                    }
                    TextButton(onClick = { viewModel.cycleShape() }) {
                        Text(shapeLabel, color = MaterialTheme.colorScheme.onPrimary)
                    }
                    Button(
                        onClick = { viewModel.confirmCalibration() },
                        enabled = !uiState.isLoading,
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Confirm Size") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.errorMessage != null -> {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(uiState.errorMessage ?: "", color = MaterialTheme.colorScheme.error)
                    }
                }
                uiState.pageBitmap != null -> {
                    CalibrateCanvas(
                        uiState = uiState,
                        modifier = Modifier.weight(1f),
                        onMove = { dx, dy -> viewModel.moveBox(dx, dy) },
                        onResize = { corner, dx, dy -> viewModel.resizeBox(corner, dx, dy) },
                    )
                }
            }

            Text(
                text = if (uiState.totalPages > 1)
                    "Use < > to navigate pages · Page 1 is the default for all pages · Drag box to move · Drag corner to resize"
                else
                    "Pinch to zoom · Drag empty area to pan · Drag box to move · Drag corner to resize",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

// ─── Canvas ──────────────────────────────────────────────────────────────────

private const val HANDLE_RADIUS_PX   = 36f  // touch hit area (screen pixels, before zoom)
private const val HANDLE_DRAW_RADIUS = 16f  // visual dot radius (canvas pixels)

private sealed class DragTarget {
    object Body               : DragTarget()
    data class Corner(val i: Int) : DragTarget()
    object None               : DragTarget()
}

@Composable
private fun CalibrateCanvas(
    uiState: CalibrateUiState,
    modifier: Modifier = Modifier,
    onMove: (dx: Float, dy: Float) -> Unit,
    onResize: (cornerIndex: Int, dx: Float, dy: Float) -> Unit,
) {
    val bitmap = uiState.pageBitmap ?: return

    // rememberUpdatedState makes the latest uiState visible inside the never-restarting
    // pointerInput coroutine (key = Unit) so hit-tests always use the current box position.
    val latestState = rememberUpdatedState(uiState)

    val imageRect  = remember { mutableStateOf(Rect.Zero) }
    val canvasSize = remember { mutableStateOf(Size.Zero) }

    var scale     by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        // ── 1. Wait for the first finger to touch down ────
                        // Use awaitPointerEvent instead of awaitFirstDown to avoid a Compose
                        // import quirk with that function in this BOM version.
                        val firstEvent = awaitPointerEvent(PointerEventPass.Initial)
                        val down = firstEvent.changes.firstOrNull { it.pressed } ?: return@awaitEachGesture
                        down.consume()

                        val rect  = imageRect.value
                        val cs    = canvasSize.value
                        val state = latestState.value

                        // ── 2. Hit-test: figure out what the finger landed on ──
                        var localTarget: DragTarget = DragTarget.None
                        if (rect.width > 0f && cs.width > 0f) {
                            val pivotX  = cs.width  / 2f
                            val pivotY  = cs.height / 2f
                            val s = scale; val p = panOffset
                            val cx = (down.position.x - pivotX - p.x) / s + pivotX
                            val cy = (down.position.y - pivotY - p.y) / s + pivotY
                            val nx = ((cx - rect.left) / rect.width ).coerceIn(0f, 1f)
                            val ny = ((cy - rect.top ) / rect.height).coerceIn(0f, 1f)
                            localTarget = calibrateHitTest(state, nx, ny, rect, s)
                        }

                        // ── 3. Track the gesture until all fingers lift ──
                        var inZoom = false
                        while (true) {
                            val event   = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break

                            when {
                                pressed.size >= 2 -> {
                                    // Two or more fingers → pinch-to-zoom
                                    inZoom = true
                                    localTarget = DragTarget.None

                                    val c1 = pressed[0]; val c2 = pressed[1]
                                    val span      = (c1.position         - c2.position        ).getDistance()
                                    val prevSpan  = (c1.previousPosition - c2.previousPosition).getDistance()
                                    val centroid  = (c1.position         + c2.position        ) / 2f
                                    val prevCent  = (c1.previousPosition + c2.previousPosition) / 2f

                                    if (prevSpan > 0f) {
                                        scale = (scale * (span / prevSpan)).coerceIn(0.5f, 15f)
                                    }
                                    panOffset += centroid - prevCent
                                    pressed.forEach { it.consume() }
                                }

                                pressed.size == 1 && !inZoom -> {
                                    // Single finger → box drag or view pan
                                    val change = pressed.first()
                                    val delta  = change.positionChange()
                                    val r = imageRect.value
                                    val s = scale

                                    when (val t = localTarget) {
                                        is DragTarget.Body ->
                                            onMove(delta.x / (r.width * s), delta.y / (r.height * s))
                                        is DragTarget.Corner ->
                                            onResize(t.i, delta.x / (r.width * s), delta.y / (r.height * s))
                                        DragTarget.None ->
                                            panOffset += delta
                                    }
                                    change.consume()
                                }
                                // pressed.size == 1 && inZoom → user lifted one finger after
                                // zoom; do nothing until both fingers are up (handled by break above)
                            }
                        }
                    }
                }
                .graphicsLayer(
                    scaleX       = scale,
                    scaleY       = scale,
                    translationX = panOffset.x,
                    translationY = panOffset.y,
                ),
        ) {
            canvasSize.value = size

            val bmpW     = bitmap.width.toFloat()
            val bmpH     = bitmap.height.toFloat()
            val fitScale = minOf(size.width / bmpW, size.height / bmpH)
            val drawW    = bmpW * fitScale
            val drawH    = bmpH * fitScale
            val imgLeft  = (size.width  - drawW) / 2f
            val imgTop   = (size.height - drawH) / 2f

            imageRect.value = Rect(imgLeft, imgTop, imgLeft + drawW, imgTop + drawH)

            drawImage(
                image     = bitmap.asImageBitmap(),
                dstOffset = IntOffset(imgLeft.toInt(), imgTop.toInt()),
                dstSize   = IntSize(drawW.toInt(), drawH.toInt()),
            )

            drawCalibrationBox(
                uiState = uiState,
                imgLeft = imgLeft,
                imgTop  = imgTop,
                drawW   = drawW,
                drawH   = drawH,
                zoom    = scale,
            )
        }

        // "Reset zoom" — floating ⊞ button
        IconButton(
            onClick  = { scale = 1f; panOffset = Offset.Zero },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .background(
                    Color.Black.copy(alpha = 0.45f),
                    androidx.compose.foundation.shape.CircleShape,
                ),
        ) {
            Icon(
                imageVector    = Icons.Default.FitScreen,
                contentDescription = "Reset zoom",
                tint           = Color.White,
            )
        }
    }
}

// ─── Hit-test helper ─────────────────────────────────────────────────────────

private fun calibrateHitTest(
    state: CalibrateUiState,
    normX: Float,
    normY: Float,
    rect: Rect,
    currentScale: Float,
): DragTarget {
    val bx = state.boxCenterX; val by_ = state.boxCenterY
    val hw = state.boxWidth / 2f; val hh = state.boxHeight / 2f

    val corners = listOf(
        Offset(bx - hw, by_ - hh), // 0 top-left
        Offset(bx + hw, by_ - hh), // 1 top-right
        Offset(bx + hw, by_ + hh), // 2 bottom-right
        Offset(bx - hw, by_ + hh), // 3 bottom-left
    )

    // Threshold shrinks as user zooms in, keeping the touch target a constant screen size
    val hitThreshX = HANDLE_RADIUS_PX / (rect.width  * currentScale)
    val hitThreshY = HANDLE_RADIUS_PX / (rect.height * currentScale)

    val hitCorner = corners.indexOfFirst { c ->
        kotlin.math.abs(normX - c.x) <= hitThreshX &&
        kotlin.math.abs(normY - c.y) <= hitThreshY
    }

    return when {
        hitCorner >= 0                                              -> DragTarget.Corner(hitCorner)
        normX in (bx - hw)..(bx + hw) && normY in (by_ - hh)..(by_ + hh) -> DragTarget.Body
        else                                                        -> DragTarget.None
    }
}

// ─── Calibration overlay drawing ─────────────────────────────────────────────

private fun DrawScope.drawCalibrationBox(
    uiState: CalibrateUiState,
    imgLeft: Float,
    imgTop: Float,
    drawW: Float,
    drawH: Float,
    zoom: Float,
) {
    val cx = imgLeft + uiState.boxCenterX * drawW
    val cy = imgTop  + uiState.boxCenterY * drawH
    val hw = uiState.boxWidth  * drawW / 2f
    val hh = uiState.boxHeight * drawH / 2f

    val boxLeft   = cx - hw
    val boxTop    = cy - hh
    val boxRight  = cx + hw
    val boxBottom = cy + hh

    // Dim everything outside the box
    drawRect(
        color   = Color.Black.copy(alpha = 0.35f),
        topLeft = Offset(imgLeft, imgTop),
        size    = Size(drawW, drawH),
    )
    // Highlight the selected region
    drawRect(
        color   = Color.White.copy(alpha = 0.15f),
        topLeft = Offset(boxLeft, boxTop),
        size    = Size(boxRight - boxLeft, boxBottom - boxTop),
    )

    // Dashed border — keep stroke width constant in screen space as user zooms
    val strokePx   = (3f / zoom).coerceAtLeast(1f)
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f / zoom, 8f / zoom), 0f)
    val stroke     = Stroke(width = strokePx, pathEffect = dashEffect)

    when (uiState.boxShape) {
        OverlayShape.RECTANGLE -> drawRect(
            color   = Color.Yellow,
            topLeft = Offset(boxLeft, boxTop),
            size    = Size(boxRight - boxLeft, boxBottom - boxTop),
            style   = stroke,
        )
        OverlayShape.DIAMOND -> {
            val diamondPath = Path().apply {
                moveTo(cx,       boxTop)
                lineTo(boxRight, cy)
                lineTo(cx,       boxBottom)
                lineTo(boxLeft,  cy)
                close()
            }
            drawPath(path = diamondPath, color = Color.Yellow, style = stroke)
        }
    }

    // Corner handles — constant screen size regardless of zoom
    val handleR = HANDLE_DRAW_RADIUS / zoom
    listOf(
        Offset(boxLeft,  boxTop),
        Offset(boxRight, boxTop),
        Offset(boxRight, boxBottom),
        Offset(boxLeft,  boxBottom),
    ).forEach { corner ->
        drawCircle(color = Color.Yellow,                   radius = handleR,          center = corner)
        drawCircle(color = Color.Black.copy(alpha = 0.6f), radius = handleR * 0.6f,  center = corner)
    }
}
