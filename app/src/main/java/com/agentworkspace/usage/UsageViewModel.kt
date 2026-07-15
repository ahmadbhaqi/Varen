package com.agentworkspace.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentworkspace.data.model.UsageRecord
import com.agentworkspace.data.model.UsageSummary
import com.agentworkspace.data.repository.UsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class UsageViewModel @Inject constructor(
    private val usageRepository: UsageRepository,
) : ViewModel() {

    val totalUsage: StateFlow<UsageSummary?> =
        usageRepository.getTotalUsage()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val recentUsage: StateFlow<List<UsageRecord>> =
        usageRepository.getRecentUsage(50)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
