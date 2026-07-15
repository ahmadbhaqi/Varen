package com.agentworkspace.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentworkspace.data.model.HistoryEntry
import com.agentworkspace.data.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    historyRepository: HistoryRepository,
) : ViewModel() {
    val history: StateFlow<List<HistoryEntry>> = historyRepository.getRecentHistory(200)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
