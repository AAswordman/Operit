package com.ai.assistance.operit.core.tools.javascript

import com.ai.assistance.operit.core.tools.packTool.runtime.PackageManager
import com.ai.assistance.operit.core.tools.protocol.AITool
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class JsToolManagerDisabledPackageTest {
    private val packages = mock<PackageManager>()

    // Execute the real entry points without constructing Android/QuickJS engines.
    // A disabled request must return before it touches any of those dependencies.
    private val manager = mock<JsToolManager>(defaultAnswer = CALLS_REAL_METHODS).also { instance ->
        JsToolManager::class.java.getDeclaredField("packageManager").apply {
            isAccessible = true
            set(instance, packages)
        }
    }

    @Test
    fun disabledSynchronousCallDoesNotReadOrExecuteThePackageScript() {
        whenever(packages.packageAvailabilityError("daily_life")).thenReturn("script is disabled: daily_life")
        val result = manager.executeScript("daily_life.get_current_date", emptyMap())

        assertEquals("script is disabled: daily_life", result)
        verify(packages).packageAvailabilityError("daily_life")
        verifyNoMoreInteractions(packages)
    }

    @Test
    fun disabledStreamingCallReturnsOneFailureWithoutExecutingTheScript() = runBlocking {
        whenever(packages.packageAvailabilityError("com.example.plugin.child")).thenReturn("package is disabled: com.example.plugin.child")
        val tool = AITool(name = "com.example.plugin.child:run", parameters = emptyList())
        val results = manager.executeScript("throw new Error('must not execute');", tool).toList()

        assertEquals(1, results.size)
        assertFalse(results.single().success)
        assertEquals(tool.name, results.single().toolName)
        assertEquals("package is disabled: com.example.plugin.child", results.single().error)
        verify(packages).packageAvailabilityError("com.example.plugin.child")
        verifyNoMoreInteractions(packages)
    }

    @Test
    fun streamingCallChecksCurrentActivationWhenCollectionStarts() = runBlocking {
        var enabled = true
        whenever(packages.packageAvailabilityError("daily_life")).thenAnswer { if (enabled) null else "script is disabled: daily_life" }
        val tool = AITool(name = "daily_life:get_current_date", parameters = emptyList())
        val pending = manager.executeScript("throw new Error('must not execute');", tool)

        enabled = false
        val results = pending.toList()

        assertEquals(1, results.size)
        assertFalse(results.single().success)
        assertEquals("script is disabled: daily_life", results.single().error)
        verify(packages).packageAvailabilityError("daily_life")
        verifyNoMoreInteractions(packages)
    }
    @Test
    fun updatingPackageAndScriptRequestsDoNotReachTheEngine() = runBlocking {
        for ((name, kind) in listOf("com.example.plugin.child" to "package", "daily_life" to "script")) {
            val error = "$kind is unavailable: $name"
            whenever(packages.packageAvailabilityError(name)).thenReturn(error)
            assertEquals(error, manager.executeScript("$name.run", emptyMap()))
            val result = manager.executeScript("throw new Error('must not execute');",
                AITool(name = "$name:run", parameters = emptyList())).toList().single()
            assertFalse(result.success)
            assertEquals(error, result.error)
        }
    }

}
