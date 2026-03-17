package com.fieldtag.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtag.data.db.entities.ProjectEntity
import com.fieldtag.domain.repository.InstrumentRepository
import com.fieldtag.domain.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProjectListUiState(
    val projects: List<ProjectEntity> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showCreateDialog: Boolean = false,
)

@HiltViewModel
class ProjectListViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectListUiState(isLoading = true))
    val uiState: StateFlow<ProjectListUiState> = _uiState

    val projects: StateFlow<List<ProjectEntity>> = projectRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            projects.collect { list ->
                _uiState.update { it.copy(projects = list, isLoading = false) }
            }
        }
    }

    fun showCreateDialog() = _uiState.update { it.copy(showCreateDialog = true) }

    fun dismissCreateDialog() = _uiState.update { it.copy(showCreateDialog = false) }

    fun createProject(name: String, notes: String? = null) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                projectRepository.createProject(name.trim(), notes?.takeIf { it.isNotBlank() })
                dismissCreateDialog()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun archiveProject(projectId: String) {
        viewModelScope.launch { projectRepository.archiveProject(projectId) }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch { projectRepository.deleteProject(projectId) }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
