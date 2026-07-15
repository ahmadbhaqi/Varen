package com.agentworkspace.task

import com.agentworkspace.data.db.entity.runtime.AgentRunEntity
import com.agentworkspace.data.db.entity.runtime.ApprovalRequestEntity
import com.agentworkspace.data.db.entity.runtime.RunEventEntity
import com.agentworkspace.data.model.Task
import com.agentworkspace.runtime.domain.ApprovalStatus
import com.agentworkspace.runtime.domain.RunEventKind
import com.agentworkspace.runtime.domain.RunStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class ChatLine(val role: Role, val text: String) {
    enum class Role { USER, ASSISTANT, TOOL, TOOL_RESULT, SYSTEM, ERROR }
}

data class PendingApproval(
    val id: String,
    val label: String,
    val reason: String,
    val destructive: Boolean,
)

object RuntimePresenter {
    private val json = Json { ignoreUnknownKeys = true }

    fun timeline(
        task: Task?,
        run: AgentRunEntity?,
        events: List<RunEventEntity>,
    ): List<ChatLine> {
        if (task == null || run == null) return emptyList()
        val lines = buildList {
            events.sortedBy { it.sequence }
                .mapNotNullTo(this) { eventLine(it) }
        }
        return lines.fold(emptyList()) { result, line ->
            val previous = result.lastOrNull()
            when {
                line.role == ChatLine.Role.ASSISTANT && previous?.role == ChatLine.Role.ASSISTANT ->
                    result.dropLast(1) + previous.copy(text = previous.text + line.text)
                line == previous -> result
                else -> result + line
            }
        }
    }

    fun isRunning(run: AgentRunEntity?): Boolean =
        run != null && !run.status.isTerminal && run.status != RunStatus.PAUSED

    fun pendingApproval(entity: ApprovalRequestEntity?): PendingApproval? = entity
        ?.takeIf { it.status == ApprovalStatus.PENDING }
        ?.let {
            PendingApproval(
                id = it.id,
                label = it.label,
                reason = it.reason,
                destructive = it.risk.equals("destructive", ignoreCase = true),
            )
        }

    private fun eventLine(event: RunEventEntity): ChatLine? {
        val payload = event.payload()
        return when (event.kind) {
            RunEventKind.MESSAGE -> {
                val content = payload.string("content") ?: return null
                ChatLine(role(payload.string("role")), content)
            }
            RunEventKind.TOOL_STARTED -> ChatLine(
                ChatLine.Role.TOOL,
                "» ${payload.string("tool") ?: "tool"}(${payload.string("args").orEmpty().take(120)})",
            )
            RunEventKind.TOOL_FINISHED -> ChatLine(
                ChatLine.Role.TOOL_RESULT,
                "✓ ${payload.string("tool") ?: "tool"}: ${payload.string("result").orEmpty().take(220)}",
            )
            RunEventKind.APPROVAL_REQUESTED -> ChatLine(
                ChatLine.Role.SYSTEM,
                "Approval required: ${payload.string("label") ?: "action"}",
            )
            RunEventKind.APPROVAL_RESOLVED -> ChatLine(
                ChatLine.Role.SYSTEM,
                if (payload.boolean("approved") == true) "Approved" else "Denied",
            )
            RunEventKind.ITERATION -> ChatLine(
                ChatLine.Role.SYSTEM,
                "iteration ${payload.string("iteration") ?: ""}".trimEnd(),
            )
            RunEventKind.RECOVERY_STARTED -> ChatLine(ChatLine.Role.SYSTEM, "Restoring task")
            RunEventKind.PAUSED -> ChatLine(ChatLine.Role.SYSTEM, "Task paused")
            RunEventKind.CANCELLED -> ChatLine(ChatLine.Role.SYSTEM, "Task cancelled by user")
            RunEventKind.COMPLETED -> ChatLine(ChatLine.Role.SYSTEM, "Task complete")
            RunEventKind.FAILED -> ChatLine(
                ChatLine.Role.ERROR,
                payload.string("message")?.let { "Task failed: $it" } ?: "Task failed",
            )
            RunEventKind.RUN_CREATED,
            RunEventKind.STATUS_CHANGED,
            RunEventKind.LEASE_ACQUIRED,
            RunEventKind.LEASE_RELEASED,
            -> null
        }
    }

    private fun role(raw: String?): ChatLine.Role = when (raw) {
        "user" -> ChatLine.Role.USER
        "tool" -> ChatLine.Role.TOOL_RESULT
        "system" -> ChatLine.Role.SYSTEM
        "error" -> ChatLine.Role.ERROR
        else -> ChatLine.Role.ASSISTANT
    }

    private fun RunEventEntity.payload(): JsonObject =
        runCatching { json.parseToJsonElement(payloadJson) as? JsonObject }
            .getOrNull()
            ?: JsonObject(emptyMap())

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.boolean(key: String): Boolean? =
        this[key]?.jsonPrimitive?.booleanOrNull
}
