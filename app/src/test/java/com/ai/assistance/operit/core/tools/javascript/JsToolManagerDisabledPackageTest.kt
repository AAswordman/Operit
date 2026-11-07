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
        val result = manager.executeScript("daily_life.get_current_date", emptyMap())

        assertEquals("Package not available (disabled): daily_life", result)
        verify(packages).isPackageEnabled("daily_life")
        verifyNoMoreInteractions(packages)
    }

    @Test
    fun disabledStreamingCallReturnsOneFailureWithoutExecutingTheScript() = runBlocking {
        val tool = AITool(name = "com.example.plugin.child:run", parameters = emptyList())
        val results = manager.executeScript("throw new Error('must not execute');", tool).toList()

        assertEquals(1, results.size)
        assertFalse(results.single().success)
        assertEquals(tool.name, results.single().toolName)
        assertEquals("Package not available (disabled): com.example.plugin.child", results.single().error)
        verify(packages).isPackageEnabled("com.example.plugin.child")
        verifyNoMoreInteractions(packages)
    }

    @Test
    fun streamingCallChecksCurrentActivationWhenCollectionStarts() = runBlocking {
        var enabled = true
        whenever(packages.isPackageEnabled("daily_life")).thenAnswer { enabled }
        val tool = AITool(name = "daily_life:get_current_date", parameters = emptyList())
        val pending = manager.executeScript("throw new Error('must not execute');", tool)

        enabled = false
        val results = pending.toList()

        assertEquals(1, results.size)
        assertFalse(results.single().success)
        assertEquals("Package not available (disabled): daily_life", results.single().error)
        verify(packages).isPackageEnabled("daily_life")
        verifyNoMoreInteractions(packages)
    }
}
