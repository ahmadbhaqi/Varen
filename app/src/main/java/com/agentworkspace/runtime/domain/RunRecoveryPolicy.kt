package com.agentworkspace.runtime.domain

import com.agentworkspace.data.db.entity.runtime.RunEventEntity
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class RunRecoveryPolicy @Inject constructor() {
    fun hasUncertainSideEffect(events: List<RunEventEntity>): Boolean {
        val openCalls = mutableSetOf<String>()
        events.sortedBy { it.sequence }.forEach { event ->
            when (event.kind) {
                RunEventKind.TOOL_STARTED -> {
                    openCalls += event.callId() ?: "missing:${event.id}"
                }
                RunEventKind.TOOL_FINISHED -> event.callId()?.let(openCalls::remove)
                else -> Unit
            }
        }
        return openCalls.isNotEmpty()
    }

    private fun RunEventEntity.callId(): String? {
        val payload = runCatching { JSON.parseToJsonElement(payloadJson) as? JsonObject }
            .getOrNull()
            ?: return null
        return payload["callId"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
