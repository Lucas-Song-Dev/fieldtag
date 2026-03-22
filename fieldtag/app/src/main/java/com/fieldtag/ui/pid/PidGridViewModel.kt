package com.fieldtag.ui.pid

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtag.data.db.entities.InstrumentEntity
import com.fieldtag.data.db.entities.PidDocumentEntity
import com.fieldtag.domain.parser.IsaTagDetector
import com.fieldtag.domain.parser.ParsedTag
import com.fieldtag.domain.parser.PidParser
import com.fieldtag.domain.repository.InstrumentRepository
import com.fieldtag.domain.repository.PidRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

// ─── Result types ─────────────────────────────────────────────────────────────

sealed class TagSelectionResult {
    object Idle : TagSelectionResult()
    object NotFound : TagSelectionResult()
    /**
     * No ISA tag was detected at the tap location, but [suggestions] lists the nearest
     * raw text strings extracted from the PDF — so the user can manually enter the correct
     * tag ID.  [page] (1-based), [normX], [normY] are the tap coordinates so the created
     * instrument can be stored with an approximate position.
     */
    data class NotFoundWithSuggestions(
        val suggestions: List<String>,
        val page: Int,
        val normX: Float,
        val normY: Float,
    ) : TagSelectionResult()
    data class MultipleFound(
        val tags: List<ParsedTag>,
        /** Every raw OCR / text-extraction line so the user can pick one even if it didn't
         *  pass the ISA filter (e.g. "V-62"). */
        val rawLines: List<String> = emptyList(),
    ) : TagSelectionResult()
    /**
     * The user has selected (or typed) a tag ID from a previous dialog step.
     * Shown as an editable confirmation step before the instrument is actually saved.
     */
    data class PendingCommit(
        val tagId: String,
        val page: Int,
        val normX: Float,
        val normY: Float,
    ) : TagSelectionResult()
    /** [existing] is non-null when the tag is already in the project's instrument list. */
    data class SingleFound(val tag: ParsedTag, val existing: InstrumentEntity?) : TagSelectionResult()
}

// ─── UI state ─────────────────────────────────────────────────────────────────

data class PidGridUiState(
    val pidDocument: PidDocumentEntity? = null,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val pageBitmap: Bitmap? = null,
    val selectionResult: TagSelectionResult = TagSelectionResult.Idle,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class PidGridViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pidRepository: PidRepository,
    private val instrumentRepository: InstrumentRepository,
    private val pidParser: PidParser,
) : ViewModel() {

    private val projectId: String = checkNotNull(savedStateHandle["projectId"])
    private val pidDocumentId: String = checkNotNull(savedStateHandle["pidDocumentId"])

    private val _uiState = MutableStateFlow(PidGridUiState())
    val uiState: StateFlow<PidGridUiState> = _uiState

    /** Emits the instrumentId to navigate to after a tag is confirmed. */
    private val _navigateToInstrument = MutableSharedFlow<String>()
    val navigateToInstrument: SharedFlow<String> = _navigateToInstrument.asSharedFlow()

    // PdfRenderer is opened once per VM lifetime and closed in onCleared()
    private var pdfRenderer: PdfRenderer? = null
    private var pfd: ParcelFileDescriptor? = null

    init {
        viewModelScope.launch {
            val doc = pidRepository.getById(pidDocumentId)
            if (doc == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "P&ID document not found") }
                return@launch
            }
            _uiState.update { it.copy(pidDocument = doc) }
            openRenderer(doc)
        }
    }

    private suspend fun openRenderer(doc: PidDocumentEntity) = withContext(Dispatchers.IO) {
        try {
            val file = File(doc.filePath)
            val newPfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(newPfd)
            pfd = newPfd
            pdfRenderer = renderer
            val total = renderer.pageCount
            _uiState.update { it.copy(totalPages = total, isLoading = false) }
            renderPage(0)
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to open PDF: ${e.message}") }
        }
    }

    private suspend fun renderPage(pageIndex: Int) = withContext(Dispatchers.IO) {
        val renderer = pdfRenderer ?: return@withContext
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext
        try {
            val page = renderer.openPage(pageIndex)
            val bitmap = Bitmap.createBitmap(
                page.width * 2,
                page.height * 2,
                Bitmap.Config.ARGB_8888,
            )
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            val old = _uiState.value.pageBitmap
            _uiState.update { it.copy(currentPage = pageIndex, pageBitmap = bitmap) }
            old?.recycle()
        } catch (_: Exception) {}
    }

    fun nextPage() {
        val state = _uiState.value
        if (state.currentPage < state.totalPages - 1) {
            viewModelScope.launch { renderPage(state.currentPage + 1) }
        }
    }

    fun prevPage() {
        val state = _uiState.value
        if (state.currentPage > 0) {
            viewModelScope.launch { renderPage(state.currentPage - 1) }
        }
    }

    /**
     * Called when the user double-taps a position on the diagram.
     * Searches a small radius around the tap point for an ISA tag.
     *
     * @param normX, normY  Normalized [0, 1] position in image space (y=0 at top).
     */
    fun onPointTapped(normX: Float, normY: Float) {
        val radius = 0.035f
        onRegionSelected(
            (normX - radius).coerceAtLeast(0f),
            (normY - radius).coerceAtLeast(0f),
            (normX + radius).coerceAtMost(1f),
            (normY + radius).coerceAtMost(1f),
        )
    }

    /**
     * Called when the user finishes drawing a selection rectangle on the diagram.
     *
     * @param x1f, y1f, x2f, y2f  Normalized [0, 1] coordinates in screen space (y=0 at top).
     */
    fun onRegionSelected(x1f: Float, y1f: Float, x2f: Float, y2f: Float) {
        val rawTextJson = _uiState.value.pidDocument?.rawTextJson ?: return
        val pageIndex = _uiState.value.currentPage

        viewModelScope.launch {
            val regionResult = pidParser.parseRegion(rawTextJson, pageIndex, x1f, y1f, x2f, y2f)
            val tags = regionResult.tags
            val result: TagSelectionResult = when {
                tags.isEmpty() -> TagSelectionResult.NotFoundWithSuggestions(
                    suggestions = regionResult.suggestions,
                    page = pageIndex + 1,
                    normX = (x1f + x2f) / 2f,
                    normY = (y1f + y2f) / 2f,
                )
                tags.size > 1 -> TagSelectionResult.MultipleFound(tags)
                else -> {
                    val tag = tags.first()
                    val existing = instrumentRepository.getByProject(projectId)
                        .firstOrNull { it.tagId.equals(tag.tagId, ignoreCase = true) }
                    TagSelectionResult.SingleFound(tag, existing)
                }
            }
            _uiState.update { it.copy(selectionResult = result) }
        }
    }

    /**
     * Creates a new [InstrumentEntity] for [tag] and emits its ID to [navigateToInstrument].
     * Should only be called when [TagSelectionResult.SingleFound.existing] is null.
     */
    fun confirmNewTag(tag: ParsedTag) {
        viewModelScope.launch {
            val pidDocument = _uiState.value.pidDocument ?: return@launch
            val instrument = InstrumentEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                pidDocumentId = pidDocument.id,
                pidPageNumber = tag.page,
                tagId = tag.tagId,
                tagPrefix = tag.prefix,
                tagNumber = tag.number,
                instrumentType = IsaTagDetector.instrumentTypeForPrefix(tag.prefix),
                pidX = tag.x,
                pidY = tag.y,
            )
            instrumentRepository.insertAll(listOf(instrument))
            clearResult()
            _navigateToInstrument.emit(instrument.id)
        }
    }

    /** Navigates to an existing instrument (found via tag search). */
    fun openExistingInstrument(instrumentId: String) {
        viewModelScope.launch {
            clearResult()
            _navigateToInstrument.emit(instrumentId)
        }
    }

    fun clearResult() = _uiState.update { it.copy(selectionResult = TagSelectionResult.Idle) }

    override fun onCleared() {
        super.onCleared()
        pdfRenderer?.close()
        pfd?.close()
        _uiState.value.pageBitmap?.recycle()
    }
}
