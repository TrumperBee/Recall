package com.kumbukaapp.recall.data.repository

import com.kumbukaapp.recall.data.db.CallDatabase
import com.kumbukaapp.recall.data.model.CallAnalysis
import com.kumbukaapp.recall.data.model.Recording
import com.kumbukaapp.recall.data.model.Transcript

class RecordingRepository(private val database: CallDatabase) {

    suspend fun getAllCalls(): List<Recording> = database.recordingDao().getAll()

    suspend fun getActiveCalls(): List<Recording> = database.recordingDao().getActive()

    suspend fun insertCall(recording: Recording) {
        database.recordingDao().insert(recording)
    }

    suspend fun searchCalls(filter: String): List<Recording> {
        return if (filter.isEmpty()) {
            database.recordingDao().getAll()
        } else {
            database.recordingDao().search("%$filter%")
        }
    }

    /** Delete everything: calls, transcripts and analyses. */
    suspend fun clearHistory() {
        database.transcriptDao().deleteAll()
        database.callAnalysisDao().deleteAll()
        database.recordingDao().clearAll()
    }

    /** Remove a call with all associated private data (transcript, analysis). */
    suspend fun deleteCallData(callId: Int) {
        database.transcriptDao().deleteByCallId(callId)
        database.callAnalysisDao().deleteByCallId(callId)
        database.recordingDao().softDelete(listOf(callId))
    }

    suspend fun getCallById(id: Int): Recording? = database.recordingDao().getById(id)

    suspend fun softDelete(ids: List<Int>) {
        database.recordingDao().softDelete(ids)
    }

    suspend fun insertTranscript(transcript: Transcript) {
        database.transcriptDao().insert(transcript)
    }

    suspend fun getTranscriptByCallId(callId: Int): Transcript? =
        database.transcriptDao().getByCallId(callId)

    suspend fun insertCallAnalysis(analysis: CallAnalysis) {
        database.callAnalysisDao().insert(analysis)
    }

    suspend fun getCallAnalysisByCallId(callId: Int): CallAnalysis? =
        database.callAnalysisDao().getByCallId(callId)
}
