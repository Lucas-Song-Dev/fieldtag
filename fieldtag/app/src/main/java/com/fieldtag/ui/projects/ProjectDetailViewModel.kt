package com.fieldtag.ui.projects

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtag.data.db.entities.InstrumentEntity
import com.fieldtag.data.db.entities.PidDocumentEntity
import com.fieldtag.data.db.entities.ProjectEntity
import com.fieldtag.domain.repository.InstrumentRepository
import com.fieldtag.domain.repository.MediaRepository
import com.fieldtag.domain.repository.PidRepository
import com.fieldtag.domain.repository.ProjectRepository
import com.fieldtag.ui.common.InstrumentSortOrder
import com.fieldtag.ui.common.applyFilterAndSort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
) : ViewModel() {

    private val projectId: String = checkNotNull(savedStateHandle["projectId"])

    private val _uiState = MutableStateFlow(ProjectDetailUiState())
    val uiState: StateFlow<ProjectDetailUiState> = _uiState

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
    }

    fun selectTab(index: Int) = _uiState.update { it.copy(selectedTab = index) }

    fun onSearchQueryChange(query: String) = _uiState.update { it.copy(searchQuery = query) }

    fun onSortOrderChange(order: InstrumentSortOrder) = _uiState.update { it.copy(sortOrder = order) }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
