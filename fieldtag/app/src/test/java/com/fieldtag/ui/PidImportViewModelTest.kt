package com.fieldtag.ui

import androidx.lifecycle.SavedStateHandle
import com.fieldtag.data.db.entities.InstrumentEntity
import com.fieldtag.data.db.entities.ParseStatus
import com.fieldtag.data.db.entities.PidDocumentEntity
import com.fieldtag.domain.repository.InstrumentRepository
import com.fieldtag.domain.repository.PidRepository
import com.fieldtag.ui.pid.PidImportViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [PidImportViewModel].
 *
 * Focuses on the init-block loading behaviour that fixes the empty TagReviewScreen bug:
 * when a new VM is created for the review route, it must eagerly load existing instruments
 * from the DB.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PidImportViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var pidRepository: PidRepository
    private lateinit var instrumentRepository: InstrumentRepository

    private fun makeInstrument(id: String, tagId: String = "LIT-5219") = InstrumentEntity(
        id = id,
        projectId = PROJECT_ID,
        pidDocumentId = "doc1",
        pidPageNumber = 1,
        tagId = tagId,
        tagPrefix = tagId.substringBefore("-"),
        tagNumber = tagId.substringAfter("-"),
    )

    private fun buildViewModel(): PidImportViewModel {
        val handle = SavedStateHandle(mapOf("projectId" to PROJECT_ID))
        return PidImportViewModel(handle, pidRepository, instrumentRepository)
    }

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        pidRepository = mockk(relaxed = true)
        instrumentRepository = mockk(relaxed = true)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun init_loads_existing_instruments_from_db_when_present() = runTest {
        val instruments = listOf(
            makeInstrument("i1", "LIT-5219"),
            makeInstrument("i2", "FIC-5185"),
            makeInstrument("i3", "PIC-5224"),
        )
        val pid = PidDocumentEntity(
            id = "doc1",
            projectId = PROJECT_ID,
            filePath = "/data/test.pdf",
            fileName = "test.pdf",
            parseStatus = ParseStatus.COMPLETE,
        )
        coEvery { instrumentRepository.getByProject(PROJECT_ID) } returns instruments
        coEvery { pidRepository.getByProject(PROJECT_ID) } returns listOf(pid)

        val vm = buildViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(3, state.instruments.size)
        assertEquals("LIT-5219", state.instruments[0].tagId)
        assertEquals(pid, state.pidDocument)
    }

    @Test fun init_does_not_overwrite_state_when_no_instruments_exist() = runTest {
        coEvery { instrumentRepository.getByProject(PROJECT_ID) } returns emptyList()

        val vm = buildViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.instruments.isEmpty())
        assertEquals("", state.searchQuery)
    }

    @Test fun init_populates_parse_warnings_from_pid_document() = runTest {
        val instruments = listOf(makeInstrument("i1"))
        val warningsJson = """["Page 2 has only 3 text tokens","Page 5 low density"]"""
        val pid = PidDocumentEntity(
            id = "doc1",
            projectId = PROJECT_ID,
            filePath = "/data/test.pdf",
            fileName = "test.pdf",
            parseStatus = ParseStatus.NEEDS_REVIEW,
            parseWarnings = warningsJson,
        )
        coEvery { instrumentRepository.getByProject(PROJECT_ID) } returns instruments
        coEvery { pidRepository.getByProject(PROJECT_ID) } returns listOf(pid)

        val vm = buildViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(2, state.parseWarnings.size)
        assertTrue(state.parseWarnings[0].contains("text tokens"))
    }

    companion object {
        private const val PROJECT_ID = "proj-test-1"
    }
}
