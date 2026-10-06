package com.ai.assistance.operit.core.tools.defaultTool.standard

import com.ai.assistance.operit.ui.permissions.PermissionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPermissionModeSupportTest {
    @Test
    fun `read result exposes the current permission mode`() {
        val result = ToolPermissionModeSupport.result(PermissionLevel.ASK)

        assertEquals("ASK", result.permissionLevel)
        assertNull(result.previousPermissionLevel)
        assertFalse(result.changed)
    }

    @Test
    fun `accepts all supported permission modes`() {
        assertEquals(PermissionLevel.ALLOW, ToolPermissionModeSupport.parse("ALLOW"))
        assertEquals(PermissionLevel.ASK, ToolPermissionModeSupport.parse("ASK"))
        assertEquals(PermissionLevel.FORBID, ToolPermissionModeSupport.parse("FORBID"))
    }

    @Test
    fun `reports a changed mode with the previous value`() {
        val result = ToolPermissionModeSupport.result(PermissionLevel.ALLOW, PermissionLevel.ASK)

        assertEquals("ALLOW", result.permissionLevel)
        assertEquals("ASK", result.previousPermissionLevel)
        assertTrue(result.changed)
    }

    @Test
    fun `rejects aliases and non canonical values`() {
        assertNull(ToolPermissionModeSupport.parse("CAUTION"))
        assertNull(ToolPermissionModeSupport.parse("allow"))
        assertNull(ToolPermissionModeSupport.parse(""))
        assertNull(ToolPermissionModeSupport.parse(null))
    }
}
