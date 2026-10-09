package com.kumbukaapp.recall.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.kumbukaapp.recall.data.model.CallAnalysis
import com.kumbukaapp.recall.data.model.Recording
import com.kumbukaapp.recall.data.model.Transcript
import com.kumbukaapp.recall.ui.viewmodel.CallDetailViewModel
import com.kumbukaapp.recall.util.FormatUtils
import com.kumbukaapp.recall.workmanager.WorkManagerPipeline
import java.io.File
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun CallDetailScreen(
    callId: Int,
    onBack: () -> Unit = {},
    onAttachRequest: ((Int) -> Unit)? = null,
    attachVersion: Int = 0,
    viewModel: CallDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    var recording by remember { mutableStateOf<Recording?>(null) }
    var transcript by remember { mutableStateOf<Transcript?>(null) }
    var analysis by remember { mutableStateOf<CallAnalysis?>(null) }
    var noteText by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }

    LaunchedEffect(callId, attachVersion) {
        try {
            recording = viewModel.getRecording(callId)
            transcript = viewModel.getTranscript(callId)
            analysis = viewModel.getAnalysis(callId)
            noteText = recording?.notes ?: ""
        } catch (e: Exception) {
            loadError = true
        }
    }

    // ExoPlayer
    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var currentPosition by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }

    LaunchedEffect(recording?.recordingPath) {
        val path = recording?.recordingPath
        if (path != null && path.isNotBlank() && File(path).exists()) {
            player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(File(path))))
            player.prepare()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = player.currentPosition
            totalDuration = player.duration.coerceAtLeast(0L)
            isPlaying = player.isPlaying
            delay(500)
        }
    }

    BackHandler(onBack = onBack)

    if (showDeleteConfirm && recording != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this call?") },
            text = { Text("The recording file and call entry will be removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteCallWithConfirmation(
                        recording!!,
                        onConfirmed = onBack,
                        onCancelled = {}
                    )
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Text(
                text = "Call details",
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = { showDeleteConfirm = true }) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }

        when {
            loadError -> {
                Text(
                    text = "Sorry, we couldn't load this call. Please go back and try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
            recording == null -> {
                Text(
                    text = "Loading...",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
            else -> {
                val rec = recording!!

                // Call info card
                InfoCard {
                    Text(
                        text = rec.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = rec.rawNumber,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${directionLabel(rec.direction)} • ${FormatUtils.formatDateLong(rec.startTime)} • " +
                            formatSeconds(rec.durationSeconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Player section
                val hasRecording = rec.recordingPath != null &&
                    rec.recordingPath.isNotBlank() &&
                    File(rec.recordingPath).exists()
                if (hasRecording) {
                    InfoCard {
                        Text("Recording", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "${formatMillis(currentPosition)} / " +
                                (if (totalDuration > 0) formatMillis(totalDuration)
                                else formatSeconds(rec.durationSeconds)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                if (player.isPlaying) {
                                    player.pause()
                                    isPlaying = false
                                } else {
                                    player.play()
                                    isPlaying = true
                                }
                            }) {
                                Text(if (isPlaying) "Pause" else "Play")
                            }
                            OutlinedButton(onClick = {
                                playbackSpeed = when (playbackSpeed) {
                                    1f -> 1.5f
                                    1.5f -> 2f
                                    else -> 1f
                                }
                                player.setPlaybackSpeed(playbackSpeed)
                            }) {
                                Text("${playbackSpeed}x")
                            }
                        }
                    }
                } else if (onAttachRequest != null) {
                    InfoCard {
                        Text(
                            text = "No recording attached to this call.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { onAttachRequest?.invoke(rec.id) },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Attach a recording file")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Transcript section
                if (transcript != null && transcript!!.text.isNotBlank()) {
                    InfoCard {
                        Text("Transcript", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = transcript!!.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Analysis section
                if (analysis != null && analysis!!.status == "DONE") {
                    val ana = analysis!!
                    InfoCard {
                        Text("Summary", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = ana.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        DetectedList(
                            title = "Promises",
                            items = parseJsonObjects(ana.promises).map { obj ->
                                "${obj.optString("who")}: ${obj.optString("what")}"
                            }
                        )
                        DetectedList(
                            title = "Dates mentioned",
                            items = parseJsonObjects(ana.dates).map { it.optString("originalText") }
                        )
                        DetectedList(
                            title = "Amounts",
                            items = parseJsonObjects(ana.amounts).map { obj ->
                                "${obj.optDouble("value")} ${obj.optString("currency")}"
                            }
                        )
                        DetectedList(
                            title = "Locations",
                            items = parseJsonStrings(ana.locations)
                        )
                        if (ana.people.isNotBlank()) {
                            Text(
                                text = "People & organizations: ${ana.people}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Notes
                InfoCard {
                    Text("Notes", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = { Text("Add a note about this call") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        minLines = 2,
                        maxLines = 5
                    )
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { viewModel.saveNote(callId, noteText) }) {
                            Text("Save note")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Reprocess
                OutlinedButton(
                    onClick = {
                        WorkManagerPipeline.forceReprocess(context, callId)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Run AI analysis again")
                }
            }
        }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            content()
        }
    }
}

@Composable
private fun DetectedList(
    title: String,
    items: List<String>
) {
    if (items.isEmpty()) return
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
    items.forEach { item ->
        Text(
            text = "• $item",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

private fun parseJsonObjects(raw: String): List<JSONObject> = try {
    val arr = JSONArray(raw)
    (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
} catch (e: Exception) {
    emptyList()
}

private fun parseJsonStrings(raw: String): List<String> = try {
    val arr = JSONArray(raw)
    (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
} catch (e: Exception) {
    emptyList()
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

private fun formatMillis(ms: Long): String {
    val totalSeconds = ms / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}
