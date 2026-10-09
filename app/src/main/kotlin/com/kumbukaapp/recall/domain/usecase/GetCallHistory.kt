package com.kumbukaapp.recall.domain.usecase

import com.kumbukaapp.recall.data.model.Recording
import com.kumbukaapp.recall.data.repository.RecordingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class GetCallHistory(private val repository: RecordingRepository) {
    fun callHistoryFlow(): Flow<List<Recording>> = flow {
        emit(repository.getAllCalls())
    }

    fun searchFlow(filter: String): Flow<List<Recording>> = flow {
        emit(repository.searchCalls(filter))
    }

    suspend fun clearHistory() {
        repository.clearHistory()
    }
}
