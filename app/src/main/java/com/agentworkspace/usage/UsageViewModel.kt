package com.agentworkspace.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentworkspace.data.model.UsageRecord
import com.agentworkspace.data.repository.UsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

const val MAX_USAGE_RECORDS = 1_000

@HiltViewModel
class UsageViewModel @Inject constructor(
    private val usageRepository: UsageRepository,
) : ViewModel() {

    val usageRecords: StateFlow<List<UsageRecord>> =
        usageRepository.getRecentUsage(MAX_USAGE_RECORDS)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
