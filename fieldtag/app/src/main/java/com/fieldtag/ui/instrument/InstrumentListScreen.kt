package com.fieldtag.ui.instrument

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fieldtag.data.db.entities.FieldStatus
import com.fieldtag.data.db.entities.InstrumentEntity
import com.fieldtag.ui.common.InstrumentSortOrder
import com.fieldtag.ui.common.SearchSortBar

@Composable
fun InstrumentListScreen(
    projectId: String,
    instruments: List<InstrumentEntity>,
    onInstrumentClick: (String) -> Unit,
    onImportPid: (() -> Unit)?,
    ungroupedCount: Int = 0,
    searchQuery: String = "",
    sortOrder: InstrumentSortOrder = InstrumentSortOrder.BY_PAGE,
    onSearchQueryChange: (String) -> Unit = {},
    onSortOrderChange: (InstrumentSortOrder) -> Unit = {},
) {
    if (instruments.isEmpty() && searchQuery.isBlank()) {
        EmptyInstrumentState(onImportPid = onImportPid)
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item(key = "search_sort_bar") {
            SearchSortBar(
                searchQuery = searchQuery,
                sortOrder = sortOrder,
                onSearchQueryChange = onSearchQueryChange,
                onSortOrderChange = onSortOrderChange,
            )
            HorizontalDivider()
        }

        if (instruments.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No tags match \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        } else {
            items(instruments, key = { it.id }) { instrument ->
                InstrumentRow(
                    instrument = instrument,
                    onClick = { onInstrumentClick(instrument.id) },
                )
            }
        }
    }
}

@Composable
fun InstrumentRow(instrument: InstrumentEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(status = instrument.fieldStatus)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(instrument.tagId, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                instrument.instrumentType?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                Text(
                    "Page ${instrument.pidPageNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            StatusChip(instrument.fieldStatus)
        }
    }
}

@Composable
fun StatusDot(status: FieldStatus, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(14.dp)
            .background(statusColor(status), CircleShape),
    )
}

@Composable
fun StatusChip(status: FieldStatus) {
    val label = when (status) {
        FieldStatus.NOT_STARTED -> ""
        FieldStatus.IN_PROGRESS -> "IN PROGRESS"
        FieldStatus.COMPLETE -> "DONE"
        FieldStatus.CANNOT_LOCATE -> "NOT FOUND"
    }
    if (label.isNotEmpty()) {
        Box(
            modifier = Modifier
                .background(statusColor(status).copy(alpha = 0.15f), MaterialTheme.shapes.small)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = statusColor(status))
        }
    }
}

fun statusColor(status: FieldStatus): Color = when (status) {
    FieldStatus.NOT_STARTED -> Color.Gray
    FieldStatus.IN_PROGRESS -> Color(0xFFF9A825)
    FieldStatus.COMPLETE -> Color(0xFF2E7D32)
    FieldStatus.CANNOT_LOCATE -> Color.Red
}

@Composable
private fun EmptyInstrumentState(onImportPid: (() -> Unit)?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
        Text("No instruments yet", style = MaterialTheme.typography.titleMedium)
        Text("Import a P&ID PDF to extract the instrument list", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        if (onImportPid != null) {
            Button(onClick = onImportPid, modifier = Modifier.padding(top = 16.dp)) {
                Text("Import P&ID")
            }
        }
    }
}
