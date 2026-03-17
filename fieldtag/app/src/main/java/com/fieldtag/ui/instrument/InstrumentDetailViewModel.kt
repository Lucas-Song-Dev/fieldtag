package com.fieldtag.ui.instrument

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtag.data.db.entities.FieldStatus
import com.fieldtag.data.db.entities.InstrumentEntity
import com.fieldtag.data.db.entities.MediaEntity
import com.fieldtag.data.db.entities.MediaRole
import com.fieldtag.domain.repository.InstrumentRepository
import com.fieldtag.domain.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InstrumentDetailUiState(
    val instrument: InstrumentEntity? = null,
    val mediaList: List<MediaEntity> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val selectedMediaId: String? = null,
)

@HiltViewModel
class InstrumentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val instrumentRepository: InstrumentRepository,
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val instrumentId: String = checkNotNull(savedStateHandle["instrumentId"])

    private val _uiState = MutableStateFlow(InstrumentDetailUiState())
    val uiState: StateFlow<InstrumentDetailUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                instrumentRepository.observeById(instrumentId),
                mediaRepository.observeByInstrument(instrumentId),
            ) { instrument, mediaList ->
                _uiState.value.copy(
                    instrument = instrument,
                    mediaList = mediaList,
                    isLoading = false,
                )
            }.collect { state -> _uiState.value = state }
        }
    }

    fun markComplete() {
        viewModelScope.launch {
            instrumentRepository.markComplete(instrumentId)
        }
    }

    fun markCannotLocate() {
        viewModelScope.launch {
            instrumentRepository.markCannotLocate(instrumentId)
        }
    }

    fun resetStatus() {
        viewModelScope.launch {
            instrumentRepository.resetStatus(instrumentId)
        }
    }

    fun updateNotes(notes: String) {
        viewModelScope.launch {
            instrumentRepository.updateNotes(instrumentId, notes.takeIf { it.isNotBlank() })
        }
    }

    fun updateMediaRole(mediaId: String, role: MediaRole) {
        viewModelScope.launch {
            mediaRepository.updateRole(mediaId, role)
        }
    }

    fun deleteMedia(media: MediaEntity) {
        viewModelScope.launch {
            mediaRepository.delete(media)
        }
    }

    fun assignMediaToInstrument(mediaId: String) {
        viewModelScope.launch {
            mediaRepository.assignToInstrument(mediaId, instrumentId)
        }
    }

    fun selectMedia(mediaId: String?) = _uiState.update { it.copy(selectedMediaId = mediaId) }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
