package com.fieldtag.ui.calibrate

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtag.data.db.entities.OverlayShape
import com.fieldtag.domain.repository.PidRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class CalibrateUiState(
    val pageBitmap: Bitmap? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    /**
     * Current box dimensions as fractions of the page [0, 1].
     * Defaults to a small square in the center of the page.
     */
    val boxCenterX: Float = 0.5f,
    val boxCenterY: Float = 0.5f,
    val boxWidth: Float = 0.06f,
    val boxHeight: Float = 0.06f,
    /** Shape currently selected for calibration. */
    val boxShape: OverlayShape = OverlayShape.RECTANGLE,
    /** Current page displayed (0-based). */
    val currentPage: Int = 0,
    /** Total pages in the PDF, 0 until first render. */
    val totalPages: Int = 0,
)

@HiltViewModel
class DiagramCalibrateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pidRepository: PidRepository,
) : ViewModel() {

    val pidDocumentId: String = checkNotNull(savedStateHandle["pidDocumentId"])

    /**
     * Page to open first. 0-based index from nav args (defaults to 0 = first page).
     * The route passes this as the `page` query parameter.
     */
    private val initialPage: Int = savedStateHandle.get<Int>("pageNumber") ?: 0

    private val _uiState = MutableStateFlow(CalibrateUiState(currentPage = initialPage))
    val uiState: StateFlow<CalibrateUiState> = _uiState

    /** Emitted once after the calibration is saved — UI navigates on receipt. */
    private val _calibrated = MutableSharedFlow<Unit>()
    val calibrated: SharedFlow<Unit> = _calibrated

    init {
        viewModelScope.launch { renderPage(initialPage) }
    }

    private suspend fun renderPage(pageIndex: Int) {
        val doc = pidRepository.getById(pidDocumentId) ?: run {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Document not found") }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        withContext(Dispatchers.IO) {
            try {
                val file = File(doc.filePath)
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val totalPages = renderer.pageCount
                val safeIndex = pageIndex.coerceIn(0, totalPages - 1)
                val page = renderer.openPage(safeIndex)
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                pfd.close()

                // Resolve initial box size: per-page → doc-level → defaults
                // Page numbers in the DB are 1-based (matches InstrumentEntity.pidPageNumber)
                val pageCalib = pidRepository.getPageCalibration(pidDocumentId, safeIndex + 1)
                val initW = pageCalib?.calibrationWidth ?: doc.calibrationWidth ?: 0.06f
                val initH = pageCalib?.calibrationHeight ?: doc.calibrationHeight ?: 0.06f
                val initShape = pageCalib?.calibrationShape ?: doc.calibrationShape

                withContext(Dispatchers.Main) {
                    _uiState.value.pageBitmap?.recycle()
                    _uiState.update {
                        it.copy(
                            pageBitmap = bitmap,
                            isLoading = false,
                            currentPage = safeIndex,
                            totalPages = totalPages,
                            boxWidth = initW,
                            boxHeight = initH,
                            boxShape = initShape,
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to render PDF: ${e.message}") }
                }
            }
        }
    }

    /** Navigate to a different page within the calibration screen. */
    fun goToPage(newPage: Int) {
        viewModelScope.launch { renderPage(newPage) }
    }

    /** Update the box center while the user drags it (normalized coords). */
    fun moveBox(dx: Float, dy: Float) {
        _uiState.update { s ->
            s.copy(
                boxCenterX = (s.boxCenterX + dx).coerceIn(s.boxWidth / 2f, 1f - s.boxWidth / 2f),
                boxCenterY = (s.boxCenterY + dy).coerceIn(s.boxHeight / 2f, 1f - s.boxHeight / 2f),
            )
        }
    }

    /**
     * Resize the box by dragging a corner.
     * [cornerIndex]: 0=topLeft, 1=topRight, 2=bottomRight, 3=bottomLeft
     */
    fun resizeBox(cornerIndex: Int, dx: Float, dy: Float) {
        _uiState.update { s ->
            val minSize = 0.003f
            var newW: Float
            var newH: Float
            val newCx: Float
            val newCy: Float
            when (cornerIndex) {
                0 -> {
                    newW = (s.boxWidth - dx).coerceAtLeast(minSize)
                    newH = (s.boxHeight - dy).coerceAtLeast(minSize)
                    newCx = s.boxCenterX - (newW - s.boxWidth) / 2f
                    newCy = s.boxCenterY - (newH - s.boxHeight) / 2f
                }
                1 -> {
                    newW = (s.boxWidth + dx).coerceAtLeast(minSize)
                    newH = (s.boxHeight - dy).coerceAtLeast(minSize)
                    newCx = s.boxCenterX + (newW - s.boxWidth) / 2f
                    newCy = s.boxCenterY - (newH - s.boxHeight) / 2f
                }
                2 -> {
                    newW = (s.boxWidth + dx).coerceAtLeast(minSize)
                    newH = (s.boxHeight + dy).coerceAtLeast(minSize)
                    newCx = s.boxCenterX + (newW - s.boxWidth) / 2f
                    newCy = s.boxCenterY + (newH - s.boxHeight) / 2f
                }
                else -> {
                    newW = (s.boxWidth - dx).coerceAtLeast(minSize)
                    newH = (s.boxHeight + dy).coerceAtLeast(minSize)
                    newCx = s.boxCenterX - (newW - s.boxWidth) / 2f
                    newCy = s.boxCenterY + (newH - s.boxHeight) / 2f
                }
            }
            s.copy(
                boxWidth = newW.coerceAtMost(0.5f),
                boxHeight = newH.coerceAtMost(0.5f),
                boxCenterX = newCx.coerceIn(newW / 2f, 1f - newW / 2f),
                boxCenterY = newCy.coerceIn(newH / 2f, 1f - newH / 2f),
            )
        }
    }

    /** Toggle between RECTANGLE and DIAMOND. */
    fun cycleShape() {
        _uiState.update { s ->
            val next = when (s.boxShape) {
                OverlayShape.RECTANGLE -> OverlayShape.DIAMOND
                OverlayShape.DIAMOND -> OverlayShape.RECTANGLE
            }
            s.copy(boxShape = next)
        }
    }

    /**
     * Persist the current box size + shape for the current page.
     *
     * - Always saves a per-page row for the current page.
     * - For page 1 (index 0) also updates the document-level columns so it acts as the
     *   global default for all pages that have no specific calibration.
     */
    fun confirmCalibration() {
        viewModelScope.launch {
            val s = _uiState.value
            val pageNumber1Based = s.currentPage + 1  // DB is 1-based
            pidRepository.upsertPageCalibration(pidDocumentId, pageNumber1Based, s.boxWidth, s.boxHeight, s.boxShape)
            // Page 1 always doubles as the document-level default
            if (s.currentPage == 0) {
                pidRepository.updateCalibration(pidDocumentId, s.boxWidth, s.boxHeight, s.boxShape)
            }
            _calibrated.emit(Unit)
        }
    }

    override fun onCleared() {
        super.onCleared()
        _uiState.value.pageBitmap?.recycle()
    }
}
