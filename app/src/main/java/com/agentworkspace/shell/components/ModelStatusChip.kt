package com.agentworkspace.shell.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.agentworkspace.data.model.AuthState
import com.agentworkspace.data.repository.ConnectionRepository
import com.agentworkspace.data.repository.ProjectRepository
import com.agentworkspace.shell.theme.AppTheme
import com.agentworkspace.shell.theme.MonoCaption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ModelStatusUiState(
    val modelName: String = "No model selected",
    val connectionName: String? = null,
    val hasToolSupport: Boolean = false,
    val isReady: Boolean = false,
)

@HiltViewModel
class ModelStatusViewModel @Inject constructor(
    projectRepository: ProjectRepository,
    connectionRepository: ConnectionRepository,
) : ViewModel() {
    val state: StateFlow<ModelStatusUiState> = combine(
        projectRepository.getRecentProjects(1),
        connectionRepository.getAllConnections(),
    ) { projects, connections ->
        val project = projects.firstOrNull()
        val enabled = connections.filter { it.isEnabled }
        val authenticated = enabled.filter { it.authState == AuthState.AUTHENTICATED }
        val preferredConnection = project?.preferredConnectionId?.let { id ->
            authenticated.firstOrNull { it.id == id }
        }
        val connection = preferredConnection ?: authenticated.firstOrNull()
        val preferredModel = project?.preferredModelId?.let { id ->
            connections.flatMap { it.models }.firstOrNull { it.id == id }
        }
        val model = preferredModel
            ?: connection?.models?.firstOrNull { it.isRecommended }
            ?: connection?.models?.firstOrNull()

        ModelStatusUiState(
            modelName = model?.name ?: "No model selected",
            connectionName = connection?.name,
            hasToolSupport = model?.capabilities?.toolUse == true,
            isReady = connection != null && model != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ModelStatusUiState())
}

@Composable
fun ModelStatusChip(
    modifier: Modifier = Modifier,
    modelName: String? = null,
    connectionName: String? = null,
    hasToolSupport: Boolean? = null,
    viewModel: ModelStatusViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resolvedModelName = modelName ?: state.modelName
    val resolvedConnectionName = connectionName ?: state.connectionName
    val resolvedToolSupport = hasToolSupport ?: state.hasToolSupport
    val ready = state.isReady || modelName != null
    val accent = if (ready) AppTheme.colors.accent else AppTheme.colors.warning
    val label = resolvedConnectionName?.let { "$resolvedModelName / $it" } ?: resolvedModelName

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .border(1.dp, accent.copy(alpha = 0.34f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.14f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Memory,
                contentDescription = "Active model",
                modifier = Modifier.padding(3.dp),
                tint = accent,
            )
        }
        Spacer(Modifier.size(7.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AppTheme.colors.textPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (resolvedToolSupport) {
            Spacer(Modifier.size(7.dp))
            Text(
                text = "TOOLS",
                style = MonoCaption,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}
