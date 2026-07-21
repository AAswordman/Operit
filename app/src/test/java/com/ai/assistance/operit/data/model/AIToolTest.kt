package com.ai.assistance.operit.data.model

import com.ai.assistance.operit.core.tools.StringResultData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AIToolTest {

    @Test fun `create tool parameter`() {
        val param = ToolParameter(name = "location", value = "Paris")
        assertEquals("location", param.name)
        assertEquals("Paris", param.value)
    }

    @Test fun `create ai tool with minimal fields`() {
        val tool = AITool(name = "get_weather")
        assertEquals("get_weather", tool.name)
        assertTrue(tool.parameters.isEmpty())
        assertEquals("", tool.description)
    }

    @Test fun `create ai tool with parameters`() {
        val params = listOf(
            ToolParameter("city", "Tokyo"),
            ToolParameter("units", "metric"),
        )
        val tool = AITool(name = "get_weather", parameters = params, description = "Get weather")
        assertEquals(2, tool.parameters.size)
        assertEquals("Tokyo", tool.parameters[0].value)
        assertEquals("metric", tool.parameters[1].value)
        assertEquals("Get weather", tool.description)
    }

    @Test fun `create tool invocation`() {
        val tool = AITool(name = "calculator", parameters = listOf(ToolParameter("expr", "2+2")))
        val invocation = ToolInvocation(tool = tool, rawText = "<tool>...</tool>", responseLocation = 0..10)
        assertEquals("calculator", invocation.tool.name)
        assertEquals("<tool>...</tool>", invocation.rawText)
        assertEquals(0..10, invocation.responseLocation)
    }

    @Test fun `create tool result with success`() {
        val resultData = StringResultData("42")
        val result = ToolResult(toolName = "calculator", success = true, result = resultData)
        assertEquals("calculator", result.toolName)
        assertTrue(result.success)
        assertEquals("42", result.result.toString())
        assertNull(result.error)
    }

    @Test fun `create tool result with failure`() {
        val resultData = StringResultData("error occurred")
        val result = ToolResult(toolName = "search", success = false, result = resultData, error = "Not found")
        assertEquals("search", result.toolName)
        assertFalse(result.success)
        assertEquals("Not found", result.error)
    }

    @Test fun `create tool validation result valid`() {
        val vr = ToolValidationResult(valid = true)
        assertTrue(vr.valid)
        assertEquals("", vr.errorMessage)
    }

    @Test fun `create tool validation result invalid`() {
        val vr = ToolValidationResult(valid = false, errorMessage = "Missing required parameter")
        assertFalse(vr.valid)
        assertEquals("Missing required parameter", vr.errorMessage)
    }

    @Test fun `ai tool copy with different parameters`() {
        val tool = AITool(name = "search", parameters = listOf(ToolParameter("q", "hello")))
        val copy = tool.copy(parameters = listOf(ToolParameter("q", "world")))
        assertEquals("world", copy.parameters[0].value)
    }

    @Test fun `tool result copy with error`() {
        val result = ToolResult(toolName = "test", success = true, result = StringResultData("ok"))
        val copy = result.copy(success = false, error = "failed")
        assertFalse(copy.success)
        assertEquals("failed", copy.error)
    }
}
