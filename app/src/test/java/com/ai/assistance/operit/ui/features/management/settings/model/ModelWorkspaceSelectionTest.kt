package com.ai.assistance.operit.ui.features.management.settings.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelWorkspaceSelectionTest {
    @Test fun openingSelectsActiveConfigurationInsteadOfFirst() {
        assertEquals("active", resolveWorkspaceConfig(listOf("first", "active"), null, "active", "first"))
    }
    @Test fun refreshDoesNotReplaceAnExplicitEditingSelection() {
        assertEquals("editing", resolveWorkspaceConfig(listOf("active", "editing"), "editing", "active", null))
    }
    @Test fun switchingGroupsRestoresItsRememberedConfiguration() {
        assertEquals("remembered", resolveWorkspaceConfig(listOf("first", "remembered"), null, "other-group", "remembered"))
    }
    @Test fun deletedSelectionsDoNotEscapeTheCurrentGroup() {
        assertEquals("first", resolveWorkspaceConfig(listOf("first"), "deleted", "other-group", "deleted"))
    }
    @Test fun emptyGroupHasNoSelection() {
        assertEquals("", resolveWorkspaceConfig(emptyList(), "previous", "active", "remembered"))
    }
}
