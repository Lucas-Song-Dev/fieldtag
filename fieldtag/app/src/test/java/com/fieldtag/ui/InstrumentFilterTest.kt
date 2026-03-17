package com.fieldtag.ui

import com.fieldtag.data.db.entities.InstrumentEntity
import com.fieldtag.ui.common.InstrumentSortOrder
import com.fieldtag.ui.common.applyFilterAndSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [applyFilterAndSort] — the shared filter/sort logic used by
 * InstrumentListScreen and TagReviewScreen.
 */
class InstrumentFilterTest {

    private fun instrument(
        id: String,
        tagId: String,
        page: Int = 1,
        sortOrder: Int = 0,
    ): InstrumentEntity {
        val prefix = tagId.substringBefore("-")
        val number = tagId.substringAfter("-")
        return InstrumentEntity(
            id = id,
            projectId = "p1",
            pidDocumentId = "d1",
            pidPageNumber = page,
            tagId = tagId,
            tagPrefix = prefix,
            tagNumber = number,
            sortOrder = sortOrder,
        )
    }

    private val sampleInstruments = listOf(
        instrument("i1", "FIC-5185", page = 1, sortOrder = 0),
        instrument("i2", "FIC-5186", page = 1, sortOrder = 1),
        instrument("i3", "LIT-1025", page = 2, sortOrder = 2),
        instrument("i4", "PIC-5224", page = 3, sortOrder = 3),
        instrument("i5", "PV-5188",  page = 3, sortOrder = 4),
        instrument("i6", "FIT-1023", page = 2, sortOrder = 5),
    )

    @Test fun filteredInstruments_returns_all_when_query_blank() {
        val result = applyFilterAndSort(sampleInstruments, "", InstrumentSortOrder.BY_PAGE)
        assertEquals(sampleInstruments.size, result.size)
    }

    @Test fun filteredInstruments_filters_by_prefix_PIC() {
        val result = applyFilterAndSort(sampleInstruments, "PIC", InstrumentSortOrder.BY_PAGE)
        assertEquals(1, result.size)
        assertEquals("PIC-5224", result[0].tagId)
    }

    @Test fun filteredInstruments_filter_is_case_insensitive() {
        val lower = applyFilterAndSort(sampleInstruments, "fic", InstrumentSortOrder.ALPHABETICAL)
        val upper = applyFilterAndSort(sampleInstruments, "FIC", InstrumentSortOrder.ALPHABETICAL)
        assertEquals(upper.size, lower.size)
        assertEquals(2, upper.size)
        assertTrue(upper.all { it.tagId.startsWith("FIC") })
    }

    @Test fun filteredInstruments_sort_alphabetical_orders_by_tagId() {
        val result = applyFilterAndSort(sampleInstruments, "", InstrumentSortOrder.ALPHABETICAL)
        val tagIds = result.map { it.tagId }
        assertEquals(tagIds.sorted(), tagIds)
    }

    @Test fun filteredInstruments_sort_by_type_groups_same_prefix_together() {
        val result = applyFilterAndSort(sampleInstruments, "", InstrumentSortOrder.BY_TYPE)
        // FIC-5185 and FIC-5186 should be adjacent (same prefix)
        val ficIndices = result.mapIndexedNotNull { i, it -> if (it.tagPrefix == "FIC") i else null }
        assertTrue("FIC tags should be consecutive", ficIndices.last() - ficIndices.first() == ficIndices.size - 1)
        // PIC and PV should both come after FIC and FIT (alphabetical by prefix)
        val ficEnd = ficIndices.last()
        val fitIndices = result.mapIndexedNotNull { i, it -> if (it.tagPrefix == "FIT") i else null }
        assertTrue("FIC comes before FIT", ficEnd < fitIndices.first())
    }
}
