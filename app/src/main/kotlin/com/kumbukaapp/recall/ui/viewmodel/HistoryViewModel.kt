package com.kumbukaapp.recall.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kumbukaapp.recall.data.model.Recording
import com.kumbukaapp.recall.data.repository.RecordingRepository
import com.kumbukaapp.recall.domain.usecase.GetCallHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: RecordingRepository
) : ViewModel() {

    private val getCallHistory = GetCallHistory(repository)

    private val _callHistory = MutableStateFlow<List<Recording>>(emptyList())
    val callHistory: StateFlow<List<Recording>> = _callHistory

    init {
        viewModelScope.launch {
            _callHistory.value = repository.getAllCalls()
        }
    }

    fun searchCalls(filter: String) {
        viewModelScope.launch {
            _callHistory.value = if (filter.isBlank()) {
                repository.getAllCalls()
            } else {
                repository.searchCalls(filter)
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            getCallHistory.clearHistory()
            _callHistory.value = emptyList()
        }
    }
}
