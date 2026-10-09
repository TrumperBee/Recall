package com.kumbukaapp.recall.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kumbukaapp.recall.data.model.Recording
import com.kumbukaapp.recall.ui.viewmodel.HistoryViewModel
import com.kumbukaapp.recall.util.FormatUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(
    onCallSelected: (Recording) -> Unit = { _ -> },
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val callHistory by viewModel.callHistory.collectAsState()

    // Filter state
    var filterByDirection by remember { mutableStateOf<String?>(null) }
    var filterByRecording by remember { mutableStateOf(false) }

    // Filtered calls
    val filteredCalls = remember(callHistory, filterByDirection, filterByRecording) {
        callHistory.filter { recording ->
            val directionMatch = filterByDirection == null ||
                recording.direction == filterByDirection
            val recordingMatch = !filterByRecording ||
                (recording.recordingPath != null && recording.recordingPath.isNotBlank())
            directionMatch && recordingMatch
        }
    }

    // Group calls by day (newest day first)
    val groupedByDay = remember(filteredCalls) {
        filteredCalls.groupBy { getDayKey(it.startTime) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        // Header
        Text(
            text = "Recall",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 16.dp, start = 8.dp, bottom = 2.dp)
        )
        Text(
            text = "Your private call memory",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        // Filter chips row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filterByDirection == null && !filterByRecording,
                onClick = { filterByDirection = null; filterByRecording = false },
                label = { Text("All") }
            )
            FilterChip(
                selected = filterByDirection == "incoming",
                onClick = { filterByDirection = if (filterByDirection == "incoming") null else "incoming" },
                label = { Text("Incoming") }
            )
            FilterChip(
                selected = filterByDirection == "outgoing",
                onClick = { filterByDirection = if (filterByDirection == "outgoing") null else "outgoing" },
                label = { Text("Outgoing") }
            )
            FilterChip(
                selected = filterByDirection == "missed",
                onClick = { filterByDirection = if (filterByDirection == "missed") null else "missed" },
                label = { Text("Missed") }
            )
            FilterChip(
                selected = filterByRecording,
                onClick = { filterByRecording = !filterByRecording },
                label = { Text("Recorded") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // History list
        if (filteredCalls.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No calls match your filters",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "All your calls appear here as they happen. Use filters to narrow down.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                groupedByDay.forEach { (dayKey, calls) ->
                    item(key = "header_$dayKey") {
                        HistoryDayHeader(dayKey = dayKey)
                    }
                    items(
                        items = calls,
                        key = { it.id }
                    ) { recording ->
                        HistoryCallItem(
                            recording = recording,
                            onSelect = { onCallSelected(recording) }
                        )
                    }
                }
            }
        }
    }
}

/** Group calls by day key: Today, Yesterday, or a full date */
private fun getDayKey(startTime: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(startTime).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy"))
    }
}

/** History day header */
@Composable
fun HistoryDayHeader(dayKey: String) {
    Text(
        text = dayKey,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp)
    )
}

/** History call item UI */
@Composable
fun HistoryCallItem(
    recording: Recording,
    onSelect: () -> Unit,
    onDeleteRecording: ((Int) -> Unit)? = null
) {
    Card(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recording.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = directionLabel(recording.direction) +
                            if (recording.recordingPath != null &&
                                recording.recordingPath.isNotBlank()
                            ) " • Has recording" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Recording indicator dot
                if (recording.recordingPath != null && recording.recordingPath.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = FormatUtils.formatDateLong(recording.startTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatSeconds(recording.durationSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onSelect) {
                    Text("Details")
                }
                if (onDeleteRecording != null) {
                    TextButton(onClick = { onDeleteRecording(recording.id) }) {
                        Text("Remove recording", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

private fun directionLabel(direction: String): String = when (direction.lowercase()) {
    "incoming" -> "Incoming"
    "outgoing" -> "Outgoing"
    "missed" -> "Missed"
    "rejected" -> "Rejected"
    else -> "Unknown"
}

private fun formatSeconds(seconds: Int): String {
    if (seconds <= 0) return "—"
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "%d:%02d".format(m, s) else "${s}s"
}
