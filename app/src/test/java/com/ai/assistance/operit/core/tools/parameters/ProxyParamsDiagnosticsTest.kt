package com.ai.assistance.operit.core.tools.parameters

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProxyParamsDiagnosticsTest {
    private fun diagnostic(source: String): String {
        try {
            JSONObject(source)
            fail("示例应当被实际 JSON 解析器拒绝")
        } catch (error: org.json.JSONException) {
            return ProxyParamsDiagnostics.describe(source, error.message)
        }
        error("无法到达")
    }

    @Test fun missingQuoteReportsLocationWithoutEchoingCommand() {
        val source = "{\n  \"command\":\"private-command"
        val message = diagnostic(source)
        assertTrue(message, message.contains("unterminated double-quoted string"))
        assertTrue(message, message.contains("line 2, column 13"))
        assertTrue(message, message.contains("target tool was not executed"))
        assertFalse(message, message.contains("private-command"))
    }

    @Test fun danglingEscapeIsExplainedWithoutChangingInput() {
        val source = "{\"command\":\"private" + "\\"
        val before = source.toByteArray()
        assertTrue(diagnostic(source).contains("escape embedded quotes"))
        assertArrayEquals(before, source.toByteArray())
    }

    @Test fun missingBracesAndMismatchedBracketsAreDistinct() {
        assertTrue(diagnostic("{\"params\":[1,2]").contains("missing closing '}'"))
        assertTrue(diagnostic("{\"params\":[1,2}").contains("expected ']'"))
    }

    @Test fun quotedBracketsDoNotAffectTheStructureScan() {
        assertTrue(diagnostic("{\"command\":\"[{}]\"").contains("missing closing '}'"))
    }

    @Test fun objectTypeErrorsNameTheTypeWithoutPrintingValues() {
        for ((source, type) in listOf("[]" to "array", "\"secret\"" to "string", "true" to "boolean", "123" to "number", "null" to "null")) {
            val message = ProxyParamsDiagnostics.describe(source)
            assertTrue(message, message.contains("received $type"))
            assertFalse(message, message.contains("secret"))
        }
        assertTrue(ProxyParamsDiagnostics.describe("  ").contains("empty input"))
    }

    @Test fun androidAndJvmOffsetsDoNotLeakParserPayload() {
        val source = "{\"secret\" 1}"
        val android = ProxyParamsDiagnostics.describe(source, "Expected ':' at character 10 of $source")
        val jvm = ProxyParamsDiagnostics.describe(source, "Expected a ':' after a key at 10 [character 11 line 1]")
        assertTrue(android, android.contains("offset 10"))
        assertTrue(jvm, jvm.contains("offset 10"))
        assertFalse(android, android.contains("secret"))
        assertFalse(jvm, jvm.contains("secret"))
    }

    @Test fun escapedShellContentRemainsAnUnchangedValidObject() {
        val source = """{"command":"printf \"[{}]\\n\"","close":false,"nested":{"quoted":"a\\\"b"}}"""
        val parsed = JSONObject(source)
        assertEquals("printf \"[{}]\\n\"", parsed.getString("command"))
        assertFalse(parsed.getBoolean("close"))
        assertTrue(source.contains("nested"))
    }
}
