package com.fieldtag.ui.projects

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtag.data.db.entities.InstrumentEntity
import com.fieldtag.data.db.entities.OverlayShape
import com.fieldtag.data.db.entities.PidDocumentEntity
import com.fieldtag.data.db.entities.PidPageCalibrationEntity
import com.fieldtag.data.db.entities.ProjectEntity
import com.fieldtag.domain.ocr.OcrTagDetector
import com.fieldtag.domain.parser.IsaTagDetector
import com.fieldtag.domain.parser.ParsedTag
import com.fieldtag.domain.parser.PidParser
import com.fieldtag.domain.repository.InstrumentRepository
import com.fieldtag.domain.repository.MediaRepository
import com.fieldtag.domain.repository.PidRepository
import com.fieldtag.domain.repository.ProjectRepository
import com.fieldtag.ui.common.InstrumentSortOrder
import com.fieldtag.ui.common.applyFilterAndSort
import com.fieldtag.ui.pid.TagSelectionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class ProjectDetailUiState(
    val project: ProjectEntity? = null,
    val pidDocuments: List<PidDocumentEntity> = emptyList(),
    val instruments: List<InstrumentEntity> = emptyList(),
    val totalCount: Int = 0,
    val completeCount: Int = 0,
    val inProgressCount: Int = 0,
    val cannotLocateCount: Int = 0,
    val ungroupedCount: Int = 0,
    val isLoading: Boolean = true,
    val selectedTab: Int = 0,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val sortOrder: InstrumentSortOrder = InstrumentSortOrder.BY_PAGE,
    /** Result of a double-tap on the diagram view; null = no active tap. */
    val diagramTapResult: TagSelectionResult? = null,
    /** Current page displayed in the Diagram tab (0-based). */
    val diagramCurrentPage: Int = 0,
    /** Total number of pages in the current P&ID document; 0 until first render. */
    val diagramTotalPages: Int = 0,
    /** Set when the user single-taps an existing instrument node on the diagram. */
    val diagramTappedInstrumentId: String? = null,
    /** Whether tag ID tooltip labels are shown on top of the diagram outlines. */
    val diagramShowTooltips: Boolean = false,
    val diagramSearchQuery: String = "",
    val isDiagramSearchActive: Boolean = false,
    val diagramCenteredInstrumentId: String? = null,
    /** Per-page calibration overrides keyed by 1-based page number. */
    val pageCalibrations: Map<Int, PidPageCalibrationEntity> = emptyMap(),
) {
    /** Filtered + sorted instrument list based on current search/sort state. */
    fun filteredInstruments(): List<InstrumentEntity> = applyFilterAndSort(instruments, searchQuery, sortOrder)
}

@HiltViewModel
class ProjectDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projectRepository: ProjectRepository,
    private val pidRepository: PidRepository,
    private val instrumentRepository: InstrumentRepository,
    private val mediaRepository: MediaRepository,
    private val pidParser: PidParser,
    private val ocrTagDetector: OcrTagDetector,
) : ViewModel() {

    private val projectId: String = checkNotNull(savedStateHandle["projectId"])

    private val _uiState = MutableStateFlow(ProjectDetailUiState())
    val uiState: StateFlow<ProjectDetailUiState> = _uiState

    /** Emits an instrumentId to navigate to after a tap-to-create on the diagram. */
    private val _navigateToInstrument = MutableSharedFlow<String>()
    val navigateToInstrument: SharedFlow<String> = _navigateToInstrument.asSharedFlow()

    init {
        // Combine first 5 flows (max typed overload)
        viewModelScope.launch {
            combine(
                projectRepository.observeById(projectId),
                pidRepository.observeByProject(projectId),
                instrumentRepository.observeByProject(projectId),
                instrumentRepository.observeCompleteCount(projectId),
                instrumentRepository.observeInProgressCount(projectId),
            ) { project, docs, instruments, completeCount, inProgressCount ->
                _uiState.value.copy(
                    project = project,
                    pidDocuments = docs,
                    instruments = instruments,
                    totalCount = instruments.size,
                    completeCount = completeCount,
                    inProgressCount = inProgressCount,
                    isLoading = false,
                )
            }.collect { state -> _uiState.value = state }
        }
        // Collect remaining counters separately
        viewModelScope.launch {
            instrumentRepository.observeCannotLocateCount(projectId).collect { count ->
                _uiState.update { it.copy(cannotLocateCount = count) }
            }
        }
        viewModelScope.launch {
            mediaRepository.observeUngroupedCount(projectId).collect { count ->
                _uiState.update { it.copy(ungroupedCount = count) }
            }
        }
        // Observe per-page calibrations for the first P&ID document of this project
        viewModelScope.launch {
            @OptIn(ExperimentalCoroutinesApi::class)
            pidRepository.observeByProject(projectId)
                .flatMapLatest { docs ->
                    val firstDocId = docs.firstOrNull()?.id
                    if (firstDocId == null) flowOf(emptyList())
                    else pidRepository.observePageCalibrations(firstDocId)
                }
                .map { list -> list.associateBy { it.pageNumber } }
                .collect { map -> _uiState.update { it.copy(pageCalibrations = map) } }
        }
    }

    fun selectTab(index: Int) = _uiState.update { it.copy(selectedTab = index) }

    fun onSearchQueryChange(query: String) = _uiState.update { it.copy(searchQuery = query) }

    fun onSortOrderChange(order: InstrumentSortOrder) = _uiState.update { it.copy(sortOrder = order) }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    // ── Diagram double-tap ────────────────────────────────────────────────────

    /**
     * Called when the user double-taps the diagram.
     *
     * Crops a region around the tap position using the calibrated instrument size (with a 1.5×
     * margin), runs on-device ML Kit OCR on the crop, then feeds recognized text through
     * [IsaTagDetector] to find the ISA instrument tag.
     *
     * Falls back to a manual-entry dialog when OCR finds no tag.
     *
     * @param bitmap    The currently rendered page bitmap (2× resolution).
     * @param normX     Tap X in normalized page coords [0, 1].
     * @param normY     Tap Y in normalized page coords [0, 1].
     * @param pageIndex 0-based page index.
     */
    fun onDiagramTapped(bitmap: Bitmap, normX: Float, normY: Float, pageIndex: Int) {
        val doc = _uiState.value.pidDocuments.firstOrNull() ?: return
        val calibW = doc.calibrationWidth
        val calibH = doc.calibrationHeight

        if (calibW == null || calibH == null) {
            android.util.Log.w("DiagramTap", "No calibration set; showing not-found dialog")
            _uiState.update {
                it.copy(diagramTapResult = TagSelectionResult.NotFoundWithSuggestions(
                    suggestions = listOf("No calibration set — please re-import the PDF to calibrate instrument size."),
                    page = pageIndex + 1, normX = normX, normY = normY,
                ))
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            // Re-render the tap region directly from the PDF at ~400+ DPI rather than cropping
            // the low-resolution display bitmap (144 DPI, transparent background).
            val ocrBitmap = renderRegionForOcr(
                filePath  = doc.filePath,
                pageIndex = pageIndex,
                normX     = normX,
                normY     = normY,
                calibW    = calibW,
                calibH    = calibH,
            )

            val ocrResult  = ocrTagDetector.detect(ocrBitmap, pageIndex + 1)
            ocrBitmap.recycle()

            val rawLines   = ocrResult.rawLines
            val positioned = ocrResult.tags.map { it.copy(x = normX, y = normY) }
            val deduped    = positioned.distinctBy { it.tagId }

            val result: TagSelectionResult = when {
                deduped.isEmpty() -> TagSelectionResult.NotFoundWithSuggestions(
                    // Pass every recognized OCR line so the user can pick one, even lines
                    // that our ISA filter rejected (e.g. "V-62").
                    suggestions = rawLines,
                    page  = pageIndex + 1,
                    normX = normX,
                    normY = normY,
                )
                deduped.size > 1 -> TagSelectionResult.MultipleFound(
                    tags     = deduped,
                    rawLines = rawLines,
                )
                else -> {
                    val tag = deduped.first()
                    val existing = withContext(Dispatchers.IO) {
                        instrumentRepository.getByProject(projectId)
                            .firstOrNull { it.tagId.equals(tag.tagId, ignoreCase = true) }
                    }
                    TagSelectionResult.SingleFound(tag, existing)
                }
            }

            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(diagramTapResult = result) }
            }
        }
    }

    /**
     * Re-renders a small region around the tap point from the PDF file at high resolution.
     *
     * This produces a ~400 DPI bitmap suitable for ML Kit OCR, with a solid white background.
     * Using the display bitmap directly is unreliable because PdfRenderer renders with a
     * transparent background and at only ~144 DPI — too small for ML Kit to detect text.
     *
     * The region captured is 3× the calibrated instrument size in each direction so the
     * full bubble + surrounding text is always included.
     */
    private fun renderRegionForOcr(
        filePath: String,
        pageIndex: Int,
        normX: Float,
        normY: Float,
        calibW: Float,
        calibH: Float,
    ): Bitmap {
        val targetSize = 600 // target output pixels (square)
        val margin     = 3.0f

        // Normalized region [0,1] around the tap
        val halfW = (calibW * margin / 2f).coerceAtMost(0.5f)
        val halfH = (calibH * margin / 2f).coerceAtMost(0.5f)
        val nx1   = (normX - halfW).coerceAtLeast(0f)
        val ny1   = (normY - halfH).coerceAtLeast(0f)
        val nx2   = (normX + halfW).coerceAtMost(1f)
        val ny2   = (normY + halfH).coerceAtMost(1f)

        val result = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        result.eraseColor(Color.WHITE)

        try {
            val file = File(filePath)
            if (!file.exists()) return result

            val pfd      = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val safeIdx  = pageIndex.coerceIn(0, renderer.pageCount - 1)
            val page     = renderer.openPage(safeIdx)

            val pageW = page.width.toFloat()   // PDF points
            val pageH = page.height.toFloat()

            // Region in PDF-point space
            val rx1 = nx1 * pageW
            val ry1 = ny1 * pageH
            val rw  = (nx2 - nx1) * pageW
            val rh  = (ny2 - ny1) * pageH

            // Matrix: scale the region to fill the target bitmap
            val scaleX = targetSize / rw
            val scaleY = targetSize / rh
            val matrix = Matrix().apply {
                setScale(scaleX, scaleY)
                postTranslate(-rx1 * scaleX, -ry1 * scaleY)
            }

            android.util.Log.d("DiagramTap",
                "Re-render OCR: norm=(${"%.3f".format(nx1)},${"%.3f".format(ny1)})" +
                "→(${"%.3f".format(nx2)},${"%.3f".format(ny2)})" +
                " pagePts=(${rx1.toInt()},${ry1.toInt()}) ${rw.toInt()}×${rh.toInt()}" +
                " scale=(${scaleX.toInt()},${scaleY.toInt()})" +
                " out=${targetSize}×${targetSize}")

            page.render(result, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            pfd.close()
        } catch (e: Exception) {
            android.util.Log.e("DiagramTap", "renderRegionForOcr failed: ${e.message}")
        }

        return result
    }

    /** Creates a new instrument for [tag] and navigates to it. */
    fun confirmNewTagFromDiagram(tag: ParsedTag) {
        val pidDocumentId = _uiState.value.pidDocuments.firstOrNull()?.id ?: return
        viewModelScope.launch {
            val instrument = InstrumentEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                pidDocumentId = pidDocumentId,
                pidPageNumber = tag.page,
                tagId = tag.tagId,
                tagPrefix = tag.prefix,
                tagNumber = tag.number,
                instrumentType = IsaTagDetector.instrumentTypeForPrefix(tag.prefix),
                pidX = tag.x,
                pidY = tag.y,
            )
            instrumentRepository.insertAll(listOf(instrument))
            _uiState.update { it.copy(diagramTapResult = null) }
            _navigateToInstrument.emit(instrument.id)
        }
    }

    /**
     * Creates a new instrument from a manually entered [tagId] at the tap location
     * provided by [NotFoundWithSuggestions].  Used when auto-detection found no ISA tag.
     */
    fun createManualTagFromDiagram(tagId: String, page: Int, normX: Float, normY: Float) {
        if (tagId.isBlank()) return
        val pidDocumentId = _uiState.value.pidDocuments.firstOrNull()?.id ?: return
        viewModelScope.launch {
            // Check if this tag already exists in the project
            val existing = instrumentRepository.getByProject(projectId)
                .firstOrNull { it.tagId.equals(tagId.trim(), ignoreCase = true) }
            if (existing != null) {
                _uiState.update { it.copy(diagramTapResult = null) }
                _navigateToInstrument.emit(existing.id)
                return@launch
            }
            val parsed = IsaTagDetector.detectInText(tagId.trim(), page).firstOrNull()
            val instrument = InstrumentEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                pidDocumentId = pidDocumentId,
                pidPageNumber = page,
                tagId = tagId.trim().uppercase(),
                tagPrefix = parsed?.prefix ?: "",
                tagNumber = parsed?.number ?: "",
                instrumentType = parsed?.let { IsaTagDetector.instrumentTypeForPrefix(it.prefix) },
                pidX = normX,
                pidY = normY,
            )
            instrumentRepository.insertAll(listOf(instrument))
            _uiState.update { it.copy(diagramTapResult = null) }
            _navigateToInstrument.emit(instrument.id)
        }
    }

    /** Opens an existing instrument record (already in project). */
    fun openExistingInstrumentFromDiagram(instrumentId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(diagramTapResult = null) }
            _navigateToInstrument.emit(instrumentId)
        }
    }

    fun clearDiagramTapResult() = _uiState.update { it.copy(diagramTapResult = null) }

    fun toggleDiagramTooltips() =
        _uiState.update { it.copy(diagramShowTooltips = !it.diagramShowTooltips) }

    /**
     * Transitions from a selection dialog to the editable confirmation step.
     * Called when the user picks an option from MultipleFound / NotFoundWithSuggestions.
     */
    fun moveToPendingCommit(tagId: String, page: Int, normX: Float, normY: Float) {
        _uiState.update {
            it.copy(diagramTapResult = TagSelectionResult.PendingCommit(tagId, page, normX, normY))
        }
    }

    // ── Instrument node taps (single-tap on placed dots) ─────────────────────

    fun onInstrumentNodeTapped(instrumentId: String) {
        _uiState.update { it.copy(diagramTappedInstrumentId = instrumentId) }
    }

    fun clearInstrumentNodeTap() {
        _uiState.update { it.copy(diagramTappedInstrumentId = null) }
    }

    fun deleteInstrument(instrumentId: String) {
        viewModelScope.launch {
            instrumentRepository.deleteById(instrumentId)
            _uiState.update { it.copy(diagramTappedInstrumentId = null) }
        }
    }

    fun updateInstrumentShape(instrumentId: String, shape: OverlayShape) {
        viewModelScope.launch {
            instrumentRepository.updateShape(instrumentId, shape)
        }
    }

    fun renameInstrument(instrumentId: String, newTagId: String) {
        val trimmed = newTagId.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            instrumentRepository.updateTagId(instrumentId, trimmed)
        }
    }

    // ── Diagram page navigation ───────────────────────────────────────────────

    /** Called once the DiagramViewerScreen renders its first page and knows the total. */
    fun onDiagramTotalPagesLoaded(total: Int) {
        if (_uiState.value.diagramTotalPages == 0 && total > 0) {
            _uiState.update { it.copy(diagramTotalPages = total) }
        }
    }

    fun prevDiagramPage() {
        _uiState.update { s ->
            if (s.diagramCurrentPage > 0)
                s.copy(diagramCurrentPage = s.diagramCurrentPage - 1)
            else s
        }
    }

    fun nextDiagramPage() {
        _uiState.update { s ->
            if (s.diagramCurrentPage < s.diagramTotalPages - 1)
                s.copy(diagramCurrentPage = s.diagramCurrentPage + 1)
            else s
        }
    }

    /** Reset page state when a new P&ID document is imported. */
    fun resetDiagramPage() = _uiState.update { it.copy(diagramCurrentPage = 0, diagramTotalPages = 0) }

    // ── Diagram Tag Search ────────────────────────────────────────────────────

    fun toggleDiagramSearch() = _uiState.update { 
        it.copy(isDiagramSearchActive = !it.isDiagramSearchActive, diagramSearchQuery = "") 
    }
    
    fun onDiagramSearchQueryChange(query: String) = _uiState.update { it.copy(diagramSearchQuery = query) }
    
    fun centerOnInstrument(instrument: InstrumentEntity) {
        _uiState.update { 
            it.copy(
                diagramCurrentPage = instrument.pidPageNumber - 1, // pidPageNumber is 1-indexed
                diagramCenteredInstrumentId = instrument.id,
                isDiagramSearchActive = false
            )
        }
    }
    
    fun clearDiagramCenteredInstrument() = _uiState.update { it.copy(diagramCenteredInstrumentId = null) }
}
