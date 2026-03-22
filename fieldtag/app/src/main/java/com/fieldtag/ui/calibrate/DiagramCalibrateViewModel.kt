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
    val boxWidth: Float  = 0.06f,
    val boxHeight: Float = 0.06f,
    /** Shape currently selected for calibration. */
    val boxShape: OverlayShape = OverlayShape.RECTANGLE,
)

@HiltViewModel
class DiagramCalibrateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pidRepository: PidRepository,
) : ViewModel() {

    val pidDocumentId: String = checkNotNull(savedStateHandle["pidDocumentId"])

    private val _uiState = MutableStateFlow(CalibrateUiState())
    val uiState: StateFlow<CalibrateUiState> = _uiState

    /** Emitted once after the calibration is saved — UI navigates on receipt. */
    private val _calibrated = MutableSharedFlow<Unit>()
    val calibrated: SharedFlow<Unit> = _calibrated

    init {
        viewModelScope.launch { renderFirstPage() }
    }

    private suspend fun renderFirstPage() {
        val doc = pidRepository.getById(pidDocumentId) ?: run {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Document not found") }
            return
        }
        withContext(Dispatchers.IO) {
            try {
                val file = File(doc.filePath)
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val page = renderer.openPage(0)
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                pfd.close()
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(pageBitmap = bitmap, isLoading = false) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to render PDF: ${e.message}") }
                }
            }
        }
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
                0 -> { // top-left: drag expands left and up
                    newW  = (s.boxWidth  - dx).coerceAtLeast(minSize)
                    newH  = (s.boxHeight - dy).coerceAtLeast(minSize)
                    newCx = s.boxCenterX - (newW - s.boxWidth)  / 2f
                    newCy = s.boxCenterY - (newH - s.boxHeight) / 2f
                }
                1 -> { // top-right
                    newW  = (s.boxWidth  + dx).coerceAtLeast(minSize)
                    newH  = (s.boxHeight - dy).coerceAtLeast(minSize)
                    newCx = s.boxCenterX + (newW - s.boxWidth)  / 2f
                    newCy = s.boxCenterY - (newH - s.boxHeight) / 2f
                }
                2 -> { // bottom-right
                    newW  = (s.boxWidth  + dx).coerceAtLeast(minSize)
                    newH  = (s.boxHeight + dy).coerceAtLeast(minSize)
                    newCx = s.boxCenterX + (newW - s.boxWidth)  / 2f
                    newCy = s.boxCenterY + (newH - s.boxHeight) / 2f
                }
                else -> { // bottom-left
                    newW  = (s.boxWidth  - dx).coerceAtLeast(minSize)
                    newH  = (s.boxHeight + dy).coerceAtLeast(minSize)
                    newCx = s.boxCenterX - (newW - s.boxWidth)  / 2f
                    newCy = s.boxCenterY + (newH - s.boxHeight) / 2f
                }
            }
            s.copy(
                boxWidth   = newW.coerceAtMost(0.5f),
                boxHeight  = newH.coerceAtMost(0.5f),
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
                OverlayShape.DIAMOND   -> OverlayShape.RECTANGLE
            }
            s.copy(boxShape = next)
        }
    }

    /** Persist the current box size + shape and emit a navigation event. */
    fun confirmCalibration() {
        viewModelScope.launch {
            val s = _uiState.value
            pidRepository.updateCalibration(pidDocumentId, s.boxWidth, s.boxHeight, s.boxShape)
            _calibrated.emit(Unit)
        }
    }

    override fun onCleared() {
        super.onCleared()
        _uiState.value.pageBitmap?.recycle()
    }
}
