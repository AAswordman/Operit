package com.ai.assistance.operit.ui.permissions

import com.ai.assistance.operit.core.agent.collaboration.CollaborationTools
import org.junit.Assert.assertEquals
import org.junit.Test

class CollaborationToolPermissionsTest {
    @Test fun delegationInheritsUserRestrictionsButDoesNotPromptByDefault() {
        for (tool in CollaborationTools.names + CollaborationTools.LEGACY_TASK_NAME) {
            assertEquals(PermissionLevel.ALLOW, effectiveToolPermission(tool, PermissionLevel.ASK, null))
            assertEquals(PermissionLevel.FORBID, effectiveToolPermission(tool, PermissionLevel.FORBID, null))
            for (override in PermissionLevel.values()) {
                assertEquals(override, effectiveToolPermission(tool, PermissionLevel.ASK, override))
            }
        }
        for (name in listOf("write_file", "plugin:spawn_agent")) {
            assertEquals(PermissionLevel.ASK, effectiveToolPermission(name, PermissionLevel.ASK, null))
        }
    }
}
