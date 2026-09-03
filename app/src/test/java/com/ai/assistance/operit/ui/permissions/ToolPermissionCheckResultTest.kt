package com.ai.assistance.operit.ui.permissions

import com.ai.assistance.operit.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPermissionCheckResultTest {
    @Test
    fun grantedResultHasNoErrorMetadata() {
        val result = ToolPermissionCheckResult.GRANTED

        assertTrue(result.isGranted)
        assertNull(result.errorMessageResId)
    }

    @Test
    fun permissionFailuresExposeLocalizedErrorMessages() {
        val expected =
            mapOf(
                ToolPermissionCheckResult.DENIED to R.string.tool_permission_execution_denied,
                ToolPermissionCheckResult.OVERLAY_PERMISSION_REQUIRED to
                    R.string.tool_permission_overlay_required_for_confirmation,
                ToolPermissionCheckResult.CONFIRMATION_TIMEOUT to
                    R.string.tool_permission_confirmation_timeout
            )

        expected.forEach { (result, messageResId) ->
            assertFalse(result.isGranted)
            assertEquals(messageResId, result.errorMessageResId)
        }
    }
}
