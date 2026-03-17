package com.fieldtag.ui.camera

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtag.data.db.entities.MediaRole
import com.fieldtag.domain.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CameraUiState(
    val selectedRole: MediaRole = MediaRole.DETAIL,
    val isSaving: Boolean = false,
    val photoSaved: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState

    fun setRole(role: MediaRole) = _uiState.update { it.copy(selectedRole = role) }

    fun savePhoto(bitmap: Bitmap, projectId: String, instrumentId: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                mediaRepository.savePhoto(
                    bitmap = bitmap,
                    projectId = projectId,
                    instrumentId = instrumentId,
                    role = _uiState.value.selectedRole,
                )
                _uiState.update { it.copy(isSaving = false, photoSaved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, errorMessage = e.message) }
            } finally {
                bitmap.recycle()
            }
        }
    }

    fun resetPhotoSaved() = _uiState.update { it.copy(photoSaved = false) }

    fun setError(message: String?) = _uiState.update { it.copy(errorMessage = message) }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
