package com.agentworkspace.checkpoint

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentworkspace.data.model.Checkpoint
import com.agentworkspace.data.repository.CheckpointRepository
import com.agentworkspace.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class CheckpointsUiState(
    val projectId: String? = null,
    val projectName: String? = null,
    val checkpoints: List<Checkpoint> = emptyList(),
    val isLoading: Boolean = true,
)

data class CheckpointDetailUiState(
    val checkpoint: Checkpoint? = null,
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CheckpointsViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val checkpointRepository: CheckpointRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckpointsUiState())
    val uiState = _uiState.asStateFlow()

    private val _detailState = MutableStateFlow(CheckpointDetailUiState())
    val detailState = _detailState.asStateFlow()

    init {
        viewModelScope.launch {
            projectRepository.getRecentProjects(10)
                .flatMapLatest { projects ->
                    val activeProject = projects.firstOrNull()
                    if (activeProject == null) {
                        flowOf(CheckpointsUiState(isLoading = false))
                    } else {
                        checkpointRepository.getCheckpointsForProject(activeProject.id).map { checkpoints ->
                            CheckpointsUiState(
                                projectId = activeProject.id,
                                projectName = activeProject.name,
                                checkpoints = checkpoints,
                                isLoading = false,
                            )
                        }
                    }
                }
                .collect { state -> _uiState.value = state }
        }

        val checkpointId = savedStateHandle.get<String>("checkpointId")
        if (checkpointId == null) {
            _detailState.value = CheckpointDetailUiState(isLoading = false)
        } else {
            viewModelScope.launch {
                _detailState.value = CheckpointDetailUiState(
                    checkpoint = checkpointRepository.getCheckpointById(checkpointId),
                    isLoading = false,
                )
            }
        }
    }
}
