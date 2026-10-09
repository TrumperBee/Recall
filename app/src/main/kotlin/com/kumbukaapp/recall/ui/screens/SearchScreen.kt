package com.kumbukaapp.recall.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kumbukaapp.recall.data.model.Recording
import com.kumbukaapp.recall.ui.viewmodel.HistoryViewModel
import com.kumbukaapp.recall.util.FormatUtils

@Composable
fun SearchScreen(
    onCallSelected: (Recording) -> Unit = { _ -> },
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val callHistory by viewModel.callHistory.collectAsState()

    var query by remember { mutableStateOf("") }
    var askQuery by remember { mutableStateOf("") }
    var showAsk by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf("") }

    // Recent searches (persisted in SharedPreferences)
    val prefs = remember {
        context.getSharedPreferences("recall_prefs", android.content.Context.MODE_PRIVATE)
    }
    var recentSearches by remember {
        mutableStateOf(
            prefs.getStringSet("recent_searches", emptySet())?.toList() ?: emptyList()
        )
    }

    // Filtered calls based on query (name, numbers, notes)
    val filteredCalls = remember(query, callHistory) {
        if (query.isBlank()) {
            callHistory
        } else {
            val q = query.trim().lowercase()
            callHistory.filter { recording ->
                val contactLower = recording.contactName?.lowercase() ?: ""
                val normalizedLower = recording.normalizedNumber.lowercase()
                val rawLower = recording.rawNumber.lowercase()
                val notesLower = recording.notes.lowercase()

                contactLower.contains(q) ||
                    normalizedLower.contains(q) ||
                    rawLower.contains(q) ||
                    notesLower.contains(q)
            }
        }
    }

    fun persistRecentSearch(term: String) {
        if (term.isBlank()) return
        val updated = (listOf(term) + recentSearches).distinct().take(10)
        recentSearches = updated
        prefs.edit().putStringSet("recent_searches", updated.toSet()).apply()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        // Search bar at the top
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search conversations...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            singleLine = true
        )

        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { showAsk = !showAsk }) {
                Text(if (showAsk) "Hide ask" else "Ask about your calls")
            }
            if (recentSearches.isNotEmpty() && query.isBlank()) {
                OutlinedButton(onClick = {
                    recentSearches = emptyList()
                    prefs.edit().remove("recent_searches").apply()
                }) {
                    Text("Clear recent")
                }
            }
        }

        // Ask box
        if (showAsk) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = askQuery,
                        onValueChange = { askQuery = it },
                        placeholder = { Text("e.g., When did I last talk to Alice?") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedButton(
                        onClick = {
                            answer = answerMetadataQuestion(askQuery, callHistory)
                            persistRecentSearch(askQuery)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Text("Ask")
                    }
                    if (answer.isNotBlank()) {
                        Text(
                            text = answer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }

        // Recent searches chips (show when query is empty)
        if (recentSearches.isNotEmpty() && query.isBlank()) {
            Text(
                text = "Recent searches",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                recentSearches.take(5).forEach { term ->
                    FilterChip(
                        selected = false,
                        onClick = { query = term },
                        label = { Text(term, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Results list
        if (filteredCalls.isEmpty()) {
            Text(
                text = if (query.isBlank()) "No calls yet" else "No calls match \"$query\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredCalls, key = { it.id }) { recording ->
                    HistoryCallItem(
                        recording = recording,
                        onSelect = {
                            persistRecentSearch(query)
                            onCallSelected(recording)
                        }
                    )
                }
            }
        }
    }
}

/** Answer a metadata question directly from the call history */
private fun answerMetadataQuestion(question: String, calls: List<Recording>): String {
    val lower = question.trim().lowercase()
    return when {
        lower.startsWith("when did") || lower.startsWith("when have") -> {
            val name = lower
                .replace(Regex("^(when did i (last )?(talk to|speak to|call)|when have i (talked to|spoken to|called))"), "")
                .trim()
            val personCalls = calls.filter { c ->
                (c.contactName?.lowercase()?.contains(name) == true) ||
                    c.rawNumber.contains(name) ||
                    c.normalizedNumber.contains(name)
            }
            if (personCalls.isNotEmpty()) {
                val last = personCalls.maxByOrNull { it.startTime }
                "Last talked: " + FormatUtils.formatDateLong(last!!.startTime)
            } else {
                "No calls found with that person"
            }
        }
        lower.startsWith("who") -> {
            val name = lower.removePrefix("who").trim()
            val personCalls = calls.filter { c ->
                (c.contactName?.lowercase()?.contains(name) == true) ||
                    c.rawNumber.contains(name) ||
                    c.normalizedNumber.contains(name)
            }
            if (personCalls.isNotEmpty()) {
                "You've had ${personCalls.size} calls with $name"
            } else {
                "No calls found with that person"
            }
        }
        lower.contains("last talk") || lower.contains("last spoke") || lower.contains("last call") -> {
            val last = calls.maxByOrNull { it.startTime }
            if (last != null) {
                "Last talked: " + FormatUtils.formatDateLong(last.startTime)
            } else {
                "No calls yet"
            }
        }
        lower.contains("how many") -> {
            "You have ${calls.size} calls in your history"
        }
        else -> {
            // Fall back to plain search over call content
            val matches = calls.count { c ->
                c.displayName.lowercase().contains(lower) ||
                    c.notes.lowercase().contains(lower)
            }
            if (matches > 0) {
                "Found $matches calls matching your question"
            } else {
                "No relevant information found in your calls."
            }
        }
    }
}
