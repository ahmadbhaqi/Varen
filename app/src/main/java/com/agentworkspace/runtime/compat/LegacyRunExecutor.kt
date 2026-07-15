package com.agentworkspace.runtime.compat

import android.net.Uri
import com.agentworkspace.agent.runtime.AgentEvent
import com.agentworkspace.agent.runtime.AgentRuntime
import com.agentworkspace.data.db.entity.runtime.AgentRunEntity
import com.agentworkspace.data.model.Connection
import com.agentworkspace.data.model.Project
import com.agentworkspace.data.model.Task
import com.agentworkspace.data.repository.ConnectionRepository
import com.agentworkspace.data.repository.ProjectRepository
import com.agentworkspace.data.repository.TaskRepository
import com.agentworkspace.github.GitHubAgentRuntime
import com.agentworkspace.github.isGitHubProjectPath
import com.agentworkspace.github.parseGitHubProjectPath
import com.agentworkspace.runtime.application.ApprovalRequester
import com.agentworkspace.runtime.data.AgentRunRepository
import com.agentworkspace.runtime.data.RunConfigurationCodec
import com.agentworkspace.runtime.data.RunProgressStore
import com.agentworkspace.runtime.domain.RunConfiguration
import com.agentworkspace.runtime.domain.RunStatus
import com.agentworkspace.runtime.service.RunExecutor
import com.agentworkspace.trust.policy.AgentAction
import com.agentworkspace.trust.policy.ApprovalDecision
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class LegacyRuntimeKind {
    LOCAL,
    GITHUB,
}

data class LegacyExecutionContext(
    val run: AgentRunEntity,
    val task: Task,
    val project: Project,
    val connection: Connection,
    val configuration: RunConfiguration,
)

fun interface LegacyRunContextLoader {
    suspend fun load(runId: String): LegacyExecutionContext
}

@Singleton
class RepositoryLegacyRunContextLoader @Inject constructor(
    private val runs: AgentRunRepository,
    private val tasks: TaskRepository,
    private val projects: ProjectRepository,
    private val connections: ConnectionRepository,
) : LegacyRunContextLoader {
    override suspend fun load(runId: String): LegacyExecutionContext {
        val run = runs.getRun(runId) ?: error("Run not found: $runId")
        val configuration = RunConfigurationCodec.decode(run.configurationJson)
        check(configuration.connectionId == run.connectionId) { "Run connection snapshot is inconsistent" }
        check(configuration.providerModelId == run.providerModelId) { "Run model snapshot is inconsistent" }
        check(configuration.workspaceId == run.workspaceId) { "Run workspace snapshot is inconsistent" }

        val task = tasks.getTaskById(run.taskId).first() ?: error("Task not found: ${run.taskId}")
        check(task.projectId == run.projectId) { "Task does not belong to the run project" }
        val project = projects.getProjectById(run.projectId).first()
            ?: error("Project not found: ${run.projectId}")
        val connection = connections.getConnectionById(configuration.connectionId).first()
            ?.takeIf { it.isEnabled }
            ?: error("Selected connection is unavailable: ${configuration.connectionId}")
        check(connection.id == run.connectionId) { "Resolved connection does not match run snapshot" }

        return LegacyExecutionContext(run, task, project, connection, configuration)
    }
}

data class LegacyExecutionRequest(
    val task: Task,
    val connection: Connection,
    val configuration: RunConfiguration,
)

interface LegacyRuntimeGateway {
    fun events(kind: LegacyRuntimeKind): Flow<AgentEvent>

    suspend fun execute(
        kind: LegacyRuntimeKind,
        request: LegacyExecutionRequest,
        approve: suspend (AgentAction, ApprovalDecision) -> Boolean,
    ): Result<Task>
}

@Singleton
class AndroidLegacyRuntimeGateway @Inject constructor(
    private val localRuntime: AgentRuntime,
    private val githubRuntime: GitHubAgentRuntime,
) : LegacyRuntimeGateway {
    override fun events(kind: LegacyRuntimeKind): Flow<AgentEvent> = when (kind) {
        LegacyRuntimeKind.LOCAL -> localRuntime.events
        LegacyRuntimeKind.GITHUB -> githubRuntime.events
    }

    override suspend fun execute(
        kind: LegacyRuntimeKind,
        request: LegacyExecutionRequest,
        approve: suspend (AgentAction, ApprovalDecision) -> Boolean,
    ): Result<Task> = when (kind) {
        LegacyRuntimeKind.LOCAL -> localRuntime.executeTask(
            task = request.task,
            connection = request.connection,
            modelId = request.configuration.providerModelId,
            treeUri = Uri.parse(request.configuration.workspaceId),
            workingDir = request.configuration.workspaceId,
            trustMode = request.configuration.trustMode,
            approve = approve,
        )
        LegacyRuntimeKind.GITHUB -> githubRuntime.executeTask(
            task = request.task,
            connection = request.connection,
            modelId = request.configuration.providerModelId,
            project = parseGitHubProjectPath(request.configuration.workspaceId),
            trustMode = request.configuration.trustMode,
            approve = approve,
        )
    }
}

@Singleton
class LegacyRunExecutor @Inject constructor(
    private val contextLoader: LegacyRunContextLoader,
    private val progress: RunProgressStore,
    private val gateway: LegacyRuntimeGateway,
    private val approvals: ApprovalRequester,
    private val eventWriter: LegacyEventWriter,
) : RunExecutor {
    override suspend fun execute(runId: String) = coroutineScope {
        val context = contextLoader.load(runId)
        val kind = if (isGitHubProjectPath(context.configuration.workspaceId)) {
            LegacyRuntimeKind.GITHUB
        } else {
            LegacyRuntimeKind.LOCAL
        }
        val eventJob = launch(start = CoroutineStart.UNDISPATCHED) {
            eventWriter.collect(runId, context.task.id, gateway.events(kind))
        }
        val result = try {
            gateway.execute(
                kind = kind,
                request = LegacyExecutionRequest(
                    task = context.task,
                    connection = context.connection,
                    configuration = context.configuration,
                ),
                approve = { action, decision ->
                    val approved = approvals.requestAndAwait(runId, action, decision)
                    val current = progress.getRun(runId) ?: error("Run not found: $runId")
                    if (current.status == RunStatus.WAITING_APPROVAL) {
                        progress.transition(
                            runId,
                            if (approved) RunStatus.EXECUTING_TOOL else RunStatus.CALLING_MODEL,
                        )
                    }
                    approved
                },
            )
        } catch (error: Throwable) {
            eventJob.cancelAndJoin()
            throw error
        }
        eventJob.join()
        result.getOrThrow()

        when (val status = progress.getRun(runId)?.status ?: error("Run not found: $runId")) {
            RunStatus.CALLING_MODEL,
            RunStatus.EXECUTING_TOOL,
            RunStatus.EDITING,
            -> progress.transition(runId, RunStatus.VERIFYING)
            RunStatus.VERIFYING -> Unit
            else -> error("Legacy runtime returned from unexpected run state: $status")
        }
    }
}
