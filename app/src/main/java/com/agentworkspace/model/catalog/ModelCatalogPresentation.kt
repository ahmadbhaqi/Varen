package com.agentworkspace.model.catalog

import com.agentworkspace.data.model.AvailabilityState

fun resolveSelectionProjectId(routeProjectId: String?, activeProjectId: String?): String? =
    routeProjectId ?: activeProjectId

fun isModelSelectable(projectId: String?, availabilityState: AvailabilityState): Boolean =
    projectId != null && availabilityState == AvailabilityState.AVAILABLE

fun isSelectedModel(modelId: String, selectedModelId: String?, isRecommended: Boolean): Boolean =
    if (selectedModelId == null) isRecommended else modelId == selectedModelId
