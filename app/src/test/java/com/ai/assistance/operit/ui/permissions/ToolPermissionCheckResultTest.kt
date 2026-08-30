package com.ai.assistance.operit.ui.permissions

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
        assertNull(result.errorCode)
        assertNull(result.errorMessage)
    }

    @Test
    fun permissionFailuresExposeStableErrorMetadata() {
        val expected =
            mapOf(
                ToolPermissionCheckResult.DENIED to "permission_denied",
                ToolPermissionCheckResult.OVERLAY_PERMISSION_REQUIRED to
                    "overlay_permission_required",
                ToolPermissionCheckResult.CONFIRMATION_TIMEOUT to
                    "permission_confirmation_timeout"
            )

        expected.forEach { (result, errorCode) ->
            assertFalse(result.isGranted)
            assertEquals(errorCode, result.errorCode)
            assertTrue(result.errorMessage?.isNotBlank() == true)
        }
    }
}
