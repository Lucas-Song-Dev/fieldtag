package com.fieldtag.ui.common

import com.fieldtag.data.db.entities.InstrumentEntity

enum class InstrumentSortOrder(val label: String) {
    BY_PAGE("Page"),
    ALPHABETICAL("A–Z"),
    BY_TYPE("By Type"),
}

/**
 * Shared filter + sort logic used by both PidImportViewModel (TagReviewScreen)
 * and ProjectDetailViewModel (InstrumentListScreen).
 *
 * Search is a case-insensitive prefix match against the full tagId string,
 * so typing "PIC" shows all tags starting with "PIC", and "LIT-52" shows
 * all tags starting with "LIT-52".
 */
fun applyFilterAndSort(
    instruments: List<InstrumentEntity>,
    searchQuery: String,
    sortOrder: InstrumentSortOrder,
): List<InstrumentEntity> {
    val q = searchQuery.trim().uppercase()
    val filtered = if (q.isBlank()) instruments
    else instruments.filter { it.tagId.uppercase().startsWith(q) }
    return when (sortOrder) {
        InstrumentSortOrder.BY_PAGE      -> filtered.sortedWith(compareBy({ it.pidPageNumber }, { it.sortOrder }))
        InstrumentSortOrder.ALPHABETICAL -> filtered.sortedBy { it.tagId }
        InstrumentSortOrder.BY_TYPE      -> filtered.sortedWith(compareBy({ it.tagPrefix }, { it.tagNumber }))
    }
}
