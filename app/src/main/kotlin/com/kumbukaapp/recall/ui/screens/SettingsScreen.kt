package com.kumbukaapp.recall.ui.screens

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.view.WindowManager
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.kumbukaapp.recall.service.AppLock
import com.kumbukaapp.recall.service.CallSyncWorker
import com.kumbukaapp.recall.service.MicFallbackService
import com.kumbukaapp.recall.ui.viewmodel.SettingsViewModel
import androidx.fragment.app.FragmentActivity

/** Privacy action types */
enum class PrivacyAction {
    none,
    deleteRecordings,
    deleteEverything,
    exportData
}

@Composable
fun SettingsScreen(
    onPrivacyAction: (PrivacyAction) -> Unit = { _ -> },
    onPickFolder: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    var backfillDays by remember { mutableStateOf("30") }
    val daysOptions = listOf("7" to "7 days", "30" to "30 days", "90" to "90 days", "all" to "All time")

    val prefs = remember {
        context.getSharedPreferences("recall_prefs", android.content.Context.MODE_PRIVATE)
    }
    var folderUri by remember { mutableStateOf(prefs.getString("folder_uri", null)) }

    var micFallbackEnabled by remember { mutableStateOf(false) }
    var appLockEnabled by remember {
        mutableStateOf(prefs.getBoolean("app_lock_enabled", false))
    }
    var flagSecureEnabled by remember { mutableStateOf(false) }
    var autoLockMinutes by remember { mutableStateOf(5) }
    var autoLockMenuOpen by remember { mutableStateOf(false) }
    var autoDeleteDays by remember { mutableStateOf("") }
    var confirmAction by remember { mutableStateOf<PrivacyAction?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ---- Call history sync ----
        SettingsCard(title = "Call history") {
            Text(
                text = "Backfill window",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                daysOptions.forEach { (value, label) ->
                    FilterChip(
                        selected = backfillDays == value,
                        onClick = { backfillDays = value },
                        label = { Text(label) }
                    )
                }
            }
            Button(
                onClick = {
                    WorkManager.getInstance(context).enqueueUniqueWork(
                        "manual_call_sync",
                        ExistingWorkPolicy.REPLACE,
                        OneTimeWorkRequestBuilder<CallSyncWorker>().build()
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text("Sync call history now")
            }
        }

        // ---- Recorder folder ----
        SettingsCard(title = "Call recording folder") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (folderUri != null) "Folder selected" else "No folder selected",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = { onPickFolder?.invoke() }) {
                    Text(if (folderUri != null) "Change" else "Select folder")
                }
            }
            if (folderUri != null) {
                TextButton(onClick = {
                    prefs.edit().remove("folder_uri").apply()
                    folderUri = null
                }) {
                    Text("Clear folder selection", color = MaterialTheme.colorScheme.error)
                }
            }
            Text(
                text = "Open your phone's Phone app > Settings > Call recording to turn it on and find the folder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
                maxLines = 3
            )
        }

        // ---- Mic fallback ----
        SettingsCard(title = "Mic fallback (speakerphone)") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Record from microphone", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "When speakerphone is active during a call, record into private storage. A persistent notification is shown during recording.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.padding(8.dp))
                Switch(
                    checked = micFallbackEnabled,
                    onCheckedChange = { enabled ->
                        micFallbackEnabled = enabled
                        if (enabled) {
                            if (ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                ContextCompat.startForegroundService(
                                    context,
                                    Intent(context, MicFallbackService::class.java)
                                )
                            }
                        } else {
                            context.stopService(Intent(context, MicFallbackService::class.java))
                        }
                    }
                )
            }
        }

        // ---- Privacy / security ----
        SettingsCard(title = "Privacy & security") {
            // App lock
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("App lock", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Requires fingerprint, face, or device PIN when reopening the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.padding(8.dp))
                Switch(
                    checked = appLockEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && activity != null) {
                            AppLock.authenticate(
                                activity = activity,
                                onSuccess = {
                                    appLockEnabled = true
                                    prefs.edit().putBoolean("app_lock_enabled", true).apply()
                                },
                                onFailed = { appLockEnabled = false }
                            )
                        } else {
                            appLockEnabled = false
                            AppLock.unlock()
                            prefs.edit().putBoolean("app_lock_enabled", false).apply()
                        }
                    }
                )
            }

            // Auto-lock timeout
            if (appLockEnabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Auto-lock after $autoLockMinutes min", style = MaterialTheme.typography.bodyMedium)
                    DropdownMenu(
                        expanded = autoLockMenuOpen,
                        onDismissRequest = { autoLockMenuOpen = false }
                    ) {
                        listOf(1, 5, 15, 30).forEach { minutes ->
                            DropdownMenuItem(
                                text = { Text("$minutes minutes") },
                                onClick = {
                                    autoLockMinutes = minutes
                                    AppLock.setAutoLockTimeout(minutes * 60 * 1000L)
                                    autoLockMenuOpen = false
                                }
                            )
                        }
                    }
                    OutlinedButton(onClick = { autoLockMenuOpen = true }) {
                        Text("Change")
                    }
                }
            }

            // FLAG_SECURE
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Prevent screenshots", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Blocks this app from appearing in screenshots, screen recorders, and recents.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.padding(8.dp))
                Switch(
                    checked = flagSecureEnabled,
                    onCheckedChange = { enabled ->
                        flagSecureEnabled = enabled
                        val window = (context as? Activity)?.window
                        if (enabled) {
                            window?.setFlags(
                                WindowManager.LayoutParams.FLAG_SECURE,
                                WindowManager.LayoutParams.FLAG_SECURE
                            )
                        } else {
                            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }
                )
            }

            // Auto-delete older than N days
            OutlinedTextField(
                value = autoDeleteDays,
                onValueChange = { autoDeleteDays = it },
                placeholder = { Text("Auto-delete calls older than N days (empty = never)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Delete all recordings
            OutlinedButton(
                onClick = { confirmAction = PrivacyAction.deleteRecordings },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Delete all recordings", style = MaterialTheme.typography.bodySmall)
            }

            // Export data
            OutlinedButton(
                onClick = { onPrivacyAction(PrivacyAction.exportData) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Text("Export call data", style = MaterialTheme.typography.bodySmall)
            }

            // Delete everything
            OutlinedButton(
                onClick = { confirmAction = PrivacyAction.deleteEverything },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text("Delete everything", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Confirmation dialogs
    confirmAction?.let { action ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = {
                Text(if (action == PrivacyAction.deleteEverything) "Delete everything?" else "Delete all recordings?")
            },
            text = {
                Text(
                    if (action == PrivacyAction.deleteEverything) {
                        "All calls, recordings, transcripts and analyses will be permanently removed."
                    } else {
                        "All audio files will be removed. Call records are kept."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    when (action) {
                        PrivacyAction.deleteRecordings -> viewModel.deleteAllRecordings()
                        PrivacyAction.deleteEverything -> viewModel.deleteEverything()
                        else -> onPrivacyAction(action)
                    }
                    confirmAction = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            content()
        }
    }
}
