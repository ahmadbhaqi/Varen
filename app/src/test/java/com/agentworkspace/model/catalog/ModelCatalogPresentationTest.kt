package com.agentworkspace.model.catalog

import com.agentworkspace.data.model.AvailabilityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogPresentationTest {
    @Test
    fun routeProjectTakesPriorityOverActiveProject() {
        assertEquals("route-project", resolveSelectionProjectId("route-project", "active-project"))
    }

    @Test
    fun globalCatalogUsesActiveProjectForSelection() {
        assertEquals("active-project", resolveSelectionProjectId(null, "active-project"))
    }

    @Test
    fun selectionRequiresAProjectAndAvailableModel() {
        assertTrue(isModelSelectable("project", AvailabilityState.AVAILABLE))
        assertFalse(isModelSelectable(null, AvailabilityState.AVAILABLE))
        assertFalse(isModelSelectable("project", AvailabilityState.UNAVAILABLE))
    }

    @Test
    fun selectedModelFallsBackToRecommendedWhenProjectHasNoPreference() {
        assertTrue(isSelectedModel("model-a", null, isRecommended = true))
        assertFalse(isSelectedModel("model-a", null, isRecommended = false))
        assertTrue(isSelectedModel("model-a", "model-a", isRecommended = false))
        assertFalse(isSelectedModel("model-a", "model-b", isRecommended = true))
    }
}
