package com.fieldtag.ui.pid

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtag.data.db.entities.InstrumentEntity
import com.fieldtag.data.db.entities.PidDocumentEntity
import com.fieldtag.domain.repository.InstrumentRepository
import com.fieldtag.domain.repository.PidRepository
import com.fieldtag.ui.common.InstrumentSortOrder
import com.fieldtag.ui.common.applyFilterAndSort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PidImportUiState(
    val pidDocument: PidDocumentEntity? = null,
    val instruments: List<InstrumentEntity> = emptyList(),
    val isImporting: Boolean = false,
    val isParsing: Boolean = false,
    /** Non-null once text extraction completes; holds the pidDocumentId to navigate to the grid. */
    val completedDocId: String? = null,
    val errorMessage: String? = null,
    val parseWarnings: List<String> = emptyList(),
    val searchQuery: String = "",
    val sortOrder: InstrumentSortOrder = InstrumentSortOrder.BY_PAGE,
) {
    /** Filtered + sorted view of instruments based on current search and sort state. */
    fun filteredInstruments(): List<InstrumentEntity> = applyFilterAndSort(instruments, searchQuery, sortOrder)
}

@HiltViewModel
class PidImportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pidRepository: PidRepository,
    private val instrumentRepository: InstrumentRepository,
) : ViewModel() {

    private val projectId: String = checkNotNull(savedStateHandle["projectId"])

    private val _uiState = MutableStateFlow(PidImportUiState())
    val uiState: StateFlow<PidImportUiState> = _uiState

    init {
        // When this VM is created for TagReviewScreen (after the import screen is popped),
        // load any already-parsed instruments from the DB so the list is not empty.
        viewModelScope.launch {
            val existing = instrumentRepository.getByProject(projectId)
            if (existing.isNotEmpty()) {
                val pid = pidRepository.getByProject(projectId).firstOrNull()
                val warnings = pid?.parseWarnings?.let { json ->
                    try {
                        val arr = org.json.JSONArray(json)
                        (0 until arr.length()).map { arr.getString(it) }
                    } catch (_: Exception) { emptyList() }
                } ?: emptyList()
                _uiState.update { it.copy(instruments = existing, pidDocument = pid, parseWarnings = warnings) }
            }
        }
    }

    fun importPdf(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, errorMessage = null) }
            try {
                val doc = pidRepository.importPdf(projectId, uri, fileName)
                _uiState.update { it.copy(pidDocument = doc, isImporting = false, isParsing = true) }

                // Extract text layer only — no instruments are auto-created.
                // The user will manually identify tags on the grid view.
                pidRepository.extractTextOnly(doc.id)

                val updatedDoc = pidRepository.getById(doc.id)
                _uiState.update {
                    it.copy(
                        pidDocument = updatedDoc,
                        isParsing = false,
                        completedDocId = updatedDoc?.id,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isImporting = false, isParsing = false, errorMessage = e.message ?: "Unknown error")
                }
            }
        }
    }

    fun deleteInstrument(instrumentId: String) {
        viewModelScope.launch {
            instrumentRepository.deleteById(instrumentId)
            _uiState.update { state ->
                state.copy(instruments = state.instruments.filter { it.id != instrumentId })
            }
        }
    }

    fun updateInstrumentTag(instrumentId: String, newTagId: String) {
        viewModelScope.launch {
            val instrument = instrumentRepository.getById(instrumentId) ?: return@launch
            // Parse the new tag ID into prefix + number + suffix
            val match = Regex("""^([A-Z]{1,5})[-\s]?(\d{3,6})([A-Z]?)$""").find(newTagId.trim())
            if (match != null) {
                val updated = instrument.copy(
                    tagId = newTagId.trim(),
                    tagPrefix = match.groupValues[1],
                    tagNumber = match.groupValues[2],
                )
                instrumentRepository.update(updated)
                _uiState.update { state ->
                    state.copy(instruments = state.instruments.map { if (it.id == instrumentId) updated else it })
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) = _uiState.update { it.copy(searchQuery = query) }

    fun onSortOrderChange(order: InstrumentSortOrder) = _uiState.update { it.copy(sortOrder = order) }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
