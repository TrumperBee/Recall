package com.kumbukaapp.recall.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kumbukaapp.recall.data.model.CallAnalysis
import com.kumbukaapp.recall.data.model.Recording
import com.kumbukaapp.recall.data.model.Transcript
import com.kumbukaapp.recall.data.repository.RecordingRepository
import com.kumbukaapp.recall.service.RecordingFileManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class CallDetailViewModel @Inject constructor(
    private val repository: RecordingRepository
) : ViewModel() {

    /** Get a call recording by ID */
    suspend fun getRecording(callId: Int): Recording? {
        return repository.getCallById(callId)
    }

    /** Get the stored transcript for a call */
    suspend fun getTranscript(callId: Int): Transcript? {
        return repository.getTranscriptByCallId(callId)
    }

    /** Get the stored analysis for a call */
    suspend fun getAnalysis(callId: Int): CallAnalysis? {
        return repository.getCallAnalysisByCallId(callId)
    }

    /** Delete the recording file from disk (keeps the call record) */
    fun deleteRecordingFile(recording: Recording) {
        viewModelScope.launch {
            RecordingFileManager.deleteAppRecording(recording.id, repository)
        }
    }

    /** Delete the recording file and soft-delete the call record */
    fun deleteCallWithConfirmation(
        recording: Recording,
        onConfirmed: () -> Unit,
        onCancelled: () -> Unit
    ) {
        viewModelScope.launch {
            RecordingFileManager.deleteAppRecording(recording.id, repository)
            repository.deleteCallData(recording.id)
            onConfirmed()
        }
    }

    /** Save a user note to the call recording */
    fun saveNote(callId: Int, note: String) {
        viewModelScope.launch {
            val recording = repository.getCallById(callId) ?: return@launch
            repository.insertCall(recording.copy(notes = note))
        }
    }
}
