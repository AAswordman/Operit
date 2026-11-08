package com.ai.assistance.operit.plugins.toolpkg

import com.ai.assistance.operit.core.tools.ADBResultData
import com.ai.assistance.operit.core.tools.BooleanResultData
import com.ai.assistance.operit.core.tools.SandboxScriptExecutionResultData
import com.ai.assistance.operit.core.tools.StringResultData
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ToolPkgToolResultPayloadTest {
    @Test
    fun largeAdbOutputReusesOriginalStringWithoutTruncation() {
        val output = "中文输出\n\"quoted\" \\path\n".repeat(100_000)
        val data = ADBResultData("dumpsys usagestats", output, 0)
        val payload = encodeToolLifecycleResult(data)
        assertSame(output, payload["output"])
        assertEquals(output.length, (payload["output"] as String).length)
        assertEquals(data.command, payload["command"])
        assertEquals(0, payload["exitCode"])
        assertEquals("com.ai.assistance.operit.core.tools.ADBResultData", payload["__type"])
    }

    @Test
    fun stringValuesKeepEscapesAndUnicodeAsOriginalText() {
        val value = "中文、emoji 🌙、换行\n、制表符\t、引号\"、斜杠\\、零字符\u0000"
        val payload = encodeToolLifecycleResult(StringResultData(value))
        assertSame(value, payload["value"])
        assertEquals("com.ai.assistance.operit.core.tools.StringResultData", payload["__type"])
    }

    @Test
    fun booleanResultPreservesBooleanType() {
        assertEquals(true, encodeToolLifecycleResult(BooleanResultData(true))["value"])
        assertEquals(false, encodeToolLifecycleResult(BooleanResultData(false))["value"])
    }

    @Test
    fun nestedScriptResultPreservesTypesNullsAndDiscriminator() {
        val result =
            SandboxScriptExecutionResultData(
                success = true,
                scriptPath = "example.js",
                functionName = "main",
                startedAtMs = 1_700_000_000_000L,
                finishedAtMs = 1_700_000_000_004L,
                durationMs = 4,
                params =
                    buildJsonObject {
                        put("count", 7)
                        put("ratio", 0.25)
                        put("enabled", true)
                        put("missing", JsonNull)
                        put("items", JsonArray(listOf(JsonPrimitive("first"), JsonPrimitive(2))))
                    },
            )
        val payload = encodeToolLifecycleResult(result)
        val params = payload["params"] as Map<*, *>
        assertEquals(7, params["count"])
        assertEquals(0.25, params["ratio"])
        assertEquals(true, params["enabled"])
        assertNull(params["missing"])
        assertEquals(listOf("first", 2), params["items"])
        assertEquals(1_700_000_000_000L, payload["startedAtMs"])
        assertEquals(
            "com.ai.assistance.operit.core.tools.SandboxScriptExecutionResultData",
            payload["__type"],
        )
        assertFalse(payload.containsKey("error"))
    }
}
