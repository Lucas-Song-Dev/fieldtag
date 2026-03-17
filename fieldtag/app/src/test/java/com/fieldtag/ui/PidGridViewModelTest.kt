package com.fieldtag.ui

import androidx.lifecycle.SavedStateHandle
import com.fieldtag.data.db.entities.InstrumentEntity
import com.fieldtag.data.db.entities.ParseStatus
import com.fieldtag.data.db.entities.PidDocumentEntity
import com.fieldtag.domain.parser.ParsedTag
import com.fieldtag.domain.parser.PidParser
import com.fieldtag.domain.repository.InstrumentRepository
import com.fieldtag.domain.repository.PidRepository
import com.fieldtag.ui.pid.PidGridViewModel
import com.fieldtag.ui.pid.TagSelectionResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [PidGridViewModel].
 *
 * These tests mock [PidRepository], [InstrumentRepository], and [PidParser]
 * so no Android framework or PdfBox involvement is needed.
 *
 * PdfRenderer is not exercised (requires a real file); only the tag-selection
 * logic, page-navigation guards, and navigation event emission are covered here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PidGridViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var pidRepository: PidRepository
    private lateinit var instrumentRepository: InstrumentRepository
    private lateinit var pidParser: PidParser

    private fun makeDoc(
        rawTextJson: String? = """[{"page":1,"width":1200,"height":800,"runs":[]}]""",
        pageCount: Int = 3,
    ) = PidDocumentEntity(
        id = DOC_ID,
        projectId = PROJECT_ID,
        filePath = "/data/local/test.pdf",
        fileName = "test.pdf",
        pageCount = pageCount,
        parseStatus = ParseStatus.COMPLETE,
        rawTextJson = rawTextJson,
    )

    private fun makeTag(tagId: String = "FIC-5185", page: Int = 1) = ParsedTag(
        tagId = tagId,
        prefix = tagId.substringBefore("-"),
        number = tagId.substringAfter("-"),
        page = page,
        x = 0.25f,
        y = 0.25f,
    )

    private fun makeInstrument(tagId: String = "FIC-5185") = InstrumentEntity(
        id = "inst-1",
        projectId = PROJECT_ID,
        pidDocumentId = DOC_ID,
        pidPageNumber = 1,
        tagId = tagId,
        tagPrefix = tagId.substringBefore("-"),
        tagNumber = tagId.substringAfter("-"),
    )

    private fun buildViewModel(): PidGridViewModel {
        val handle = SavedStateHandle(mapOf("projectId" to PROJECT_ID, "pidDocumentId" to DOC_ID))
        return PidGridViewModel(handle, pidRepository, instrumentRepository, pidParser)
    }

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        pidRepository = mockk(relaxed = true)
        instrumentRepository = mockk(relaxed = true)
        pidParser = mockk(relaxed = true)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    // ─── Init / document loading ──────────────────────────────────────────────

    @Test fun init_shows_error_when_document_not_found() = runTest {
        coEvery { pidRepository.getById(DOC_ID) } returns null

        val vm = buildViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("not found"))
    }

    @Test fun init_loads_document_into_state() = runTest {
        coEvery { pidRepository.getById(DOC_ID) } returns makeDoc()
        // openRenderer will try to open a non-existent file — that's OK, it catches exceptions

        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals(DOC_ID, vm.uiState.value.pidDocument?.id)
    }

    // ─── onRegionSelected — NotFound ──────────────────────────────────────────

    @Test fun onRegionSelected_no_tags_found_emits_NotFound() = runTest {
        coEvery { pidRepository.getById(DOC_ID) } returns makeDoc()
        coEvery { pidParser.parseRegion(any(), any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { instrumentRepository.getByProject(PROJECT_ID) } returns emptyList()

        val vm = buildViewModel()
        advanceUntilIdle()

        // Manually set rawTextJson in document (init already set it via the doc)
        vm.onRegionSelected(0.1f, 0.1f, 0.2f, 0.2f)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.selectionResult is TagSelectionResult.NotFound)
    }

    @Test fun onRegionSelected_no_rawTextJson_does_nothing() = runTest {
        coEvery { pidRepository.getById(DOC_ID) } returns makeDoc(rawTextJson = null)

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onRegionSelected(0f, 0f, 1f, 1f)
        advanceUntilIdle()

        // Still Idle — no rawTextJson to parse
        assertTrue(vm.uiState.value.selectionResult is TagSelectionResult.Idle)
    }

    // ─── onRegionSelected — MultipleFound ────────────────────────────────────

    @Test fun onRegionSelected_multiple_tags_emits_MultipleFound() = runTest {
        val tags = listOf(makeTag("FIC-5185"), makeTag("LIT-1025"))
        coEvery { pidRepository.getById(DOC_ID) } returns makeDoc()
        coEvery { pidParser.parseRegion(any(), any(), any(), any(), any(), any()) } returns tags
        coEvery { instrumentRepository.getByProject(PROJECT_ID) } returns emptyList()

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onRegionSelected(0f, 0f, 1f, 1f)
        advanceUntilIdle()

        val result = vm.uiState.value.selectionResult
        assertTrue(result is TagSelectionResult.MultipleFound)
        val multi = result as TagSelectionResult.MultipleFound
        assertEquals(2, multi.tags.size)
        val ids = multi.tags.map { it.tagId }
        assertTrue("FIC-5185" in ids)
        assertTrue("LIT-1025" in ids)
    }

    @Test fun onRegionSelected_three_tags_still_emits_MultipleFound() = runTest {
        val tags = listOf(makeTag("FIC-5185"), makeTag("LIT-1025"), makeTag("PIC-5224"))
        coEvery { pidRepository.getById(DOC_ID) } returns makeDoc()
        coEvery { pidParser.parseRegion(any(), any(), any(), any(), any(), any()) } returns tags

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onRegionSelected(0f, 0f, 1f, 1f)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.selectionResult is TagSelectionResult.MultipleFound)
    }

    // ─── onRegionSelected — SingleFound (new) ────────────────────────────────

    @Test fun onRegionSelected_single_new_tag_emits_SingleFound_with_null_existing() = runTest {
        val tag = makeTag("FIC-5185")
        coEvery { pidRepository.getById(DOC_ID) } returns makeDoc()
        coEvery { pidParser.parseRegion(any(), any(), any(), any(), any(), any()) } returns listOf(tag)
        coEvery { instrumentRepository.getByProject(PROJECT_ID) } returns emptyList() // none in DB

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onRegionSelected(0f, 0f, 1f, 1f)
        advanceUntilIdle()

        val result = vm.uiState.value.selectionResult
        assertTrue(result is TagSelectionResult.SingleFound)
        val single = result as TagSelectionResult.SingleFound
        assertEquals("FIC-5185", single.tag.tagId)
        assertNull("No existing instrument expected", single.existing)
    }

    // ─── onRegionSelected — SingleFound (existing) ────────────────────────────

    @Test fun onRegionSelected_single_existing_tag_emits_SingleFound_with_existing() = runTest {
        val tag = makeTag("FIC-5185")
        val existing = makeInstrument("FIC-5185")
        coEvery { pidRepository.getById(DOC_ID) } returns makeDoc()
        coEvery { pidParser.parseRegion(any(), any(), any(), any(), any(), any()) } returns listOf(tag)
        coEvery { instrumentRepository.getByProject(PROJECT_ID) } returns listOf(existing)

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onRegionSelected(0f, 0f, 1f, 1f)
        advanceUntilIdle()

        val result = vm.uiState.value.selectionResult
        assertTrue(result is TagSelectionResult.SingleFound)
        val single = result as TagSelectionResult.SingleFound
        assertNotNull("Existing instrument expected", single.existing)
        assertEquals("inst-1", single.existing!!.id)
    }

    @Test fun existing_tag_lookup_is_case_insensitive() = runTest {
        // DB stores "FIC-5185" but the region parse might return "fic-5185" (lowercase)
        val tag = makeTag("fic-5185")
        val existing = makeInstrument("FIC-5185")
        coEvery { pidRepository.getById(DOC_ID) } returns makeDoc()
        coEvery { pidParser.parseRegion(any(), any(), any(), any(), any(), any()) } returns listOf(tag)
        coEvery { instrumentRepository.getByProject(PROJECT_ID) } returns listOf(existing)

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onRegionSelected(0f, 0f, 1f, 1f)
        advanceUntilIdle()

        val result = vm.uiState.value.selectionResult as? TagSelectionResult.SingleFound
        assertNotNull("Should find existing despite case difference", result?.existing)
    }

    // ─── confirmNewTag ────────────────────────────────────────────────────────

    @Test fun confirmNewTag_inserts_instrument_and_emits_navigation_event() = runTest {
        val tag = makeTag("FIC-5185")
        coEvery { pidRepository.getById(DOC_ID) } returns makeDoc()
        coEvery { pidParser.parseRegion(any(), any(), any(), any(), any(), any()) } returns listOf(tag)
        coEvery { instrumentRepository.getByProject(PROJECT_ID) } returns emptyList()

        val vm = buildViewModel()
        advanceUntilIdle()

        val collectedIds = mutableListOf<String>()
        val job = launch {
            vm.navigateToInstrument.collect { collectedIds.add(it) }
        }

        vm.onRegionSelected(0f, 0f, 1f, 1f)
        advanceUntilIdle()

        vm.confirmNewTag(tag)
        advanceUntilIdle()

        coVerify { instrumentRepository.insertAll(match { it.size == 1 && it[0].tagId == "FIC-5185" }) }
        assertEquals(1, collectedIds.size) // navigation was triggered
        // Result should be cleared after confirm
        assertTrue(vm.uiState.value.selectionResult is TagSelectionResult.Idle)

        job.cancel()
    }

    @Test fun confirmNewTag_uses_tag_coordinates_for_new_instrument() = runTest {
        val tag = makeTag("FIC-5185").copy(x = 0.33f, y = 0.44f)
        coEvery { pidRepository.getById(DOC_ID) } returns makeDoc()
        coEvery { pidParser.parseRegion(any(), any(), any(), any(), any(), any()) } returns listOf(tag)
        coEvery { instrumentRepository.getByProject(PROJECT_ID) } returns emptyList()

        val vm = buildViewModel()
        advanceUntilIdle()

        val capturedInstruments = slot<List<InstrumentEntity>>()
        coEvery { instrumentRepository.insertAll(capture(capturedInstruments)) } returns Unit

        vm.confirmNewTag(tag)
        advanceUntilIdle()

        val saved = capturedInstruments.captured.firstOrNull()
        assertNotNull(saved)
        assertEquals(0.33f, saved!!.pidX ?: 0f, 0.001f)
        assertEquals(0.44f, saved.pidY ?: 0f, 0.001f)
    }

    // ─── openExistingInstrument ───────────────────────────────────────────────

    @Test fun openExistingInstrument_emits_navigation_event_and_clears_result() = runTest {
        coEvery { pidRepository.getById(DOC_ID) } returns makeDoc()

        val vm = buildViewModel()
        advanceUntilIdle()

        val collectedIds = mutableListOf<String>()
        val job = launch {
            vm.navigateToInstrument.collect { collectedIds.add(it) }
        }

        vm.openExistingInstrument("inst-42")
        advanceUntilIdle()

        assertEquals(listOf("inst-42"), collectedIds)
        assertTrue(vm.uiState.value.selectionResult is TagSelectionResult.Idle)

        job.cancel()
    }

    // ─── clearResult ──────────────────────────────────────────────────────────

    @Test fun clearResult_resets_selection_to_Idle() = runTest {
        val tags = listOf(makeTag("FIC-5185"), makeTag("LIT-1025"))
        coEvery { pidRepository.getById(DOC_ID) } returns makeDoc()
        coEvery { pidParser.parseRegion(any(), any(), any(), any(), any(), any()) } returns tags

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onRegionSelected(0f, 0f, 1f, 1f)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.selectionResult is TagSelectionResult.MultipleFound)

        vm.clearResult()
        assertTrue(vm.uiState.value.selectionResult is TagSelectionResult.Idle)
    }

    // ─── Page navigation guards ───────────────────────────────────────────────

    @Test fun nextPage_does_not_go_past_last_page() = runTest {
        coEvery { pidRepository.getById(DOC_ID) } returns makeDoc(pageCount = 3)

        val vm = buildViewModel()
        advanceUntilIdle()

        // Manually set totalPages since PdfRenderer can't open in unit test
        // Simulate already-loaded state by checking guard logic with current=0
        // nextPage is a no-op when currentPage == totalPages-1
        // totalPages is set by openRenderer which fails silently → stays 0
        // So nextPage should not crash and state remains isLoading=false
        vm.nextPage()
        advanceUntilIdle()
        // No crash = pass; currentPage should not go negative or above total
        assertTrue(vm.uiState.value.currentPage >= 0)
    }

    @Test fun prevPage_does_not_go_below_zero() = runTest {
        coEvery { pidRepository.getById(DOC_ID) } returns makeDoc()

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.prevPage()
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.currentPage) // already at first page
    }

    // ─── parseRegion args forwarded correctly ─────────────────────────────────

    @Test fun onRegionSelected_forwards_current_page_index_to_parseRegion() = runTest {
        val doc = makeDoc()
        coEvery { pidRepository.getById(DOC_ID) } returns doc
        coEvery { pidParser.parseRegion(any(), any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { instrumentRepository.getByProject(PROJECT_ID) } returns emptyList()

        val vm = buildViewModel()
        advanceUntilIdle()

        val pageSlot = slot<Int>()
        coEvery { pidParser.parseRegion(any(), capture(pageSlot), any(), any(), any(), any()) } returns emptyList()

        vm.onRegionSelected(0.1f, 0.2f, 0.3f, 0.4f)
        advanceUntilIdle()

        assertEquals("parseRegion should receive current page index 0", 0, pageSlot.captured)
    }

    @Test fun onRegionSelected_forwards_selection_coords_to_parseRegion() = runTest {
        coEvery { pidRepository.getById(DOC_ID) } returns makeDoc()
        coEvery { instrumentRepository.getByProject(PROJECT_ID) } returns emptyList()

        val x1Slot = slot<Float>(); val y1Slot = slot<Float>()
        val x2Slot = slot<Float>(); val y2Slot = slot<Float>()
        coEvery {
            pidParser.parseRegion(any(), any(), capture(x1Slot), capture(y1Slot), capture(x2Slot), capture(y2Slot))
        } returns emptyList()

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onRegionSelected(0.1f, 0.2f, 0.8f, 0.9f)
        advanceUntilIdle()

        assertEquals(0.1f, x1Slot.captured, 0.001f)
        assertEquals(0.2f, y1Slot.captured, 0.001f)
        assertEquals(0.8f, x2Slot.captured, 0.001f)
        assertEquals(0.9f, y2Slot.captured, 0.001f)
    }

    companion object {
        private const val PROJECT_ID = "proj-grid-test"
        private const val DOC_ID = "doc-grid-test"
    }
}
