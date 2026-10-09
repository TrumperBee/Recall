package com.kumbukaapp.recall.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kumbukaapp.recall.data.repository.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: RecordingRepository
) : ViewModel() {

    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled

    private val _recordingsPath = MutableStateFlow("")
    val recordingsPath: StateFlow<String> = _recordingsPath

    fun toggleNotifications(enabled: Boolean) {
        _notificationsEnabled.value = enabled
    }

    fun setRecordingsPath(path: String) {
        _recordingsPath.value = path
    }

    /** Delete all recording files, keeping the call records */
    fun deleteAllRecordings() {
        viewModelScope.launch {
            val calls = repository.getAllCalls()
            calls.forEach { call ->
                if (call.recordingPath != null && call.recordingPath.isNotBlank()) {
                    com.kumbukaapp.recall.service.RecordingFileManager.deleteAppRecording(
                        call.id,
                        repository
                    )
                }
            }
        }
    }

    /** Delete everything: all calls, transcripts, analyses and recordings */
    fun deleteEverything() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}
