package com.fieldtag.ui.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtag.domain.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class ExportType { PDF, ZIP }

data class ExportUiState(
    val isExporting: Boolean = false,
    /** Which export is currently in flight, null when idle. */
    val activeExport: ExportType? = null,
    val exportedFileUri: Uri? = null,
    /** MIME type of the most recently completed export for the share intent. */
    val exportedMimeType: String = "application/octet-stream",
    val errorMessage: String? = null,
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pdfExportService: PdfExportService,
    private val zipExportService: ZipExportService,
    private val projectRepository: ProjectRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val projectId: String = checkNotNull(savedStateHandle["projectId"])

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState

    fun exportPdf() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, activeExport = ExportType.PDF, errorMessage = null) }
            try {
                val outputFile = pdfExportService.exportProject(projectId)
                projectRepository.recordExport(projectId)
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        activeExport = null,
                        exportedFileUri = fileUri(outputFile),
                        exportedMimeType = "application/pdf",
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, activeExport = null, errorMessage = e.message) }
            }
        }
    }

    fun exportZip() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, activeExport = ExportType.ZIP, errorMessage = null) }
            try {
                val outputFile = zipExportService.exportProject(projectId)
                projectRepository.recordExport(projectId)
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        activeExport = null,
                        exportedFileUri = fileUri(outputFile),
                        exportedMimeType = "application/zip",
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, activeExport = null, errorMessage = e.message) }
            }
        }
    }

    fun shareFile(uri: Uri, mimeType: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    fun clearExported() = _uiState.update { it.copy(exportedFileUri = null) }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    private fun fileUri(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
