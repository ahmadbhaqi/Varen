package com.agentworkspace.model.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentworkspace.data.model.ModelInfo
import com.agentworkspace.data.repository.ConnectionRepository
import com.agentworkspace.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelRow(val model: ModelInfo, val connectionName: String)

/**
 * Backs the Model Catalog with REAL models discovered from the active
 * connections; never sample data. Models are first-class entities: the
 * user must see what is available, where it comes from, and what it can do.
 */
@HiltViewModel
class ModelCatalogViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val projectRepository: ProjectRepository,
) : ViewModel() {

    val rows: StateFlow<List<ModelRow>> = combine(
        connectionRepository.getAllModels(),
        connectionRepository.getAllConnections(),
    ) { models, connections ->
        val nameById = connections.associate { it.id to it.name }
        models.map { ModelRow(it, nameById[it.connectionId] ?: "Unknown") }
            .sortedWith(compareByDescending<ModelRow> { it.model.isRecommended }.thenBy { it.model.name })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProjectId: StateFlow<String?> =
        projectRepository.getRecentProjects(1)
            .map { projects -> projects.firstOrNull()?.id }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun selectedModelId(projectId: String): StateFlow<String?> =
        projectRepository.getProjectById(projectId)
            .map { it?.preferredModelId }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun useModelForProject(projectId: String, model: ModelInfo) {
        viewModelScope.launch {
            projectRepository.updatePreferredModel(projectId, model.id)
            projectRepository.updatePreferredConnection(projectId, model.connectionId)
        }
    }
}
