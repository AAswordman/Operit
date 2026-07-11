package com.ai.assistance.operit.api.chat.enhance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.json.JSONObject
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter

/**
 * Regression tests for GitHub issue #657:
 * `package_proxy` calls failed with "params must be a valid JSON object" when a tool-call
 * `params` JSON contained literal (unescaped) quotation marks inside a string value
 * (e.g. dialogue `她说"你好"`). The root cause was `unescapeXml()` turning `&quot;` into `"`
 * indiscriminately, breaking the JSON before `JSONObject()` parsed it.
 *
 * The fix adds [ToolExecutionManager.repairUnescapedQuotesInJson] (a pure, stdlib-only best-effort
 * repair) which is invoked from [ToolExecutionManager.resolveProxyParameters] only after the
 * primary [JSONObject] parse fails. If the repair also fails, the call safely falls back to an
 * empty parameter list instead of crashing.
 *
 * Note: this test file lives in the unit-test source set (`app/src/test`). It exercises the two
 * `@VisibleForTesting internal` members directly. Because the host CI/build environment here cannot
 * run the Android instrumentation, the algorithmic correctness is also proven independently by the
 * standalone Node.js script `verify_repair_657.mjs` at the repository root.
 */
class ToolExecutionManagerTest {

    // -------------------------------------------------------------------------------------------
    // Unit tests for the pure helper: repairUnescapedQuotesInJson
    // -------------------------------------------------------------------------------------------

    @Test
    fun repair_issueRepro_literalInteriorQuotes_fixed() {
        // Issue #657 exact repro: literal quotes inside the value.
        val raw = """{"content": "她说"你好""}"""
        val repaired = ToolExecutionManager.repairUnescapedQuotesInJson(raw)
        val obj = JSONObject(repaired)
        assertEquals("她说\"你好\"", obj.getString("content"))
    }

    @Test
    fun repair_alreadyValidJson_unchanged() {
        val raw = """{"content": "她说\"你好\""}"""
        val repaired = ToolExecutionManager.repairUnescapedQuotesInJson(raw)
        assertEquals(raw, repaired)
        assertEquals("她说\"你好\"", JSONObject(repaired).getString("content"))
    }

    @Test
    fun repair_emptyString_unchanged() {
        assertEquals("", ToolExecutionManager.repairUnescapedQuotesInJson(""))
    }

    @Test
    fun repair_noQuotes_unchanged() {
        val raw = "hello world without quotes"
        assertEquals(raw, ToolExecutionManager.repairUnescapedQuotesInJson(raw))
    }

    @Test
    fun repair_preEscapedQuotes_notDoubleEscaped() {
        // A pre-escaped \" inside the value must NOT be turned into \\".
        val raw = """{"text": "a \"b\" c"}"""
        val repaired = ToolExecutionManager.repairUnescapedQuotesInJson(raw)
        assertEquals(raw, repaired)
        assertEquals("a \"b\" c", JSONObject(repaired).getString("text"))
    }

    @Test
    fun repair_multipleInteriorQuotes_fixed() {
        val raw = """{"a": "he said "hi" then "bye""}"""
        val repaired = ToolExecutionManager.repairUnescapedQuotesInJson(raw)
        assertEquals("he said \"hi\" then \"bye\"", JSONObject(repaired).getString("a"))
    }

    @Test
    fun repair_nestedObjectAndArray_withInteriorQuotes_fixed() {
        val raw = """{"outer": {"inner": "he said "hi""}, "arr": ["a", "she said "bye""]}"""
        val repaired = ToolExecutionManager.repairUnescapedQuotesInJson(raw)
        val obj = JSONObject(repaired)
        assertEquals("he said \"hi\"", obj.getJSONObject("outer").getString("inner"))
        assertEquals("she said \"bye\"", obj.getJSONArray("arr").getString(1))
    }

    @Test
    fun repair_valueBoundaryHeuristic_stillInvalid_fallbackSafe() {
        // Known best-effort edge: a `,` after a value-boundary quote fools the heuristic into
        // treating the boundary quote as a structural close, so the repaired output is still
        // invalid JSON. We document this behavior and assert it cannot be parsed; the caller
        // (resolveProxyParameters) must fall back to emptyList() rather than crash.
        val raw = """{"content": "他说 hi" , bye"}"""
        val repaired = ToolExecutionManager.repairUnescapedQuotesInJson(raw)
        var parsed = true
        try {
            JSONObject(repaired)
        } catch (_: Exception) {
            parsed = false
        }
        assertEquals(false, parsed)
    }

    // -------------------------------------------------------------------------------------------
    // Integration regression for resolveProxyParameters (the function that actually had the bug)
    // -------------------------------------------------------------------------------------------

    @Test
    fun resolveProxyParameters_issue657_literalQuotesResolved() {
        val rawParams = """{"content": "她说"你好""}"""
        val tool = AITool(
            name = "package_proxy",
            parameters = listOf(ToolParameter("params", rawParams))
        )
        val resolved = ToolExecutionManager.resolveProxyParameters(tool)
        val content = resolved.firstOrNull { it.name == "content" }?.value
        assertEquals("她说\"你好\"", content)
    }

    @Test
    fun resolveProxyParameters_alreadyValidJson_resolved() {
        val rawParams = """{"content": "她说\"你好\""}"""
        val tool = AITool(
            name = "package_proxy",
            parameters = listOf(ToolParameter("params", rawParams))
        )
        val resolved = ToolExecutionManager.resolveProxyParameters(tool)
        assertEquals("她说\"你好\"", resolved.firstOrNull { it.name == "content" }?.value)
    }

    @Test
    fun resolveProxyParameters_blankParams_returnsEmptyList() {
        val tool = AITool(
            name = "package_proxy",
            parameters = listOf(ToolParameter("params", "   "))
        )
        assertTrue(ToolExecutionManager.resolveProxyParameters(tool).isEmpty())
    }

    @Test
    fun resolveProxyParameters_unfixableJson_fallsBackToEmptyList() {
        // Neither the primary parse nor the repair can produce valid JSON -> safe emptyList().
        val tool = AITool(
            name = "package_proxy",
            parameters = listOf(ToolParameter("params", "totally not json ::::"))
        )
        assertTrue(ToolExecutionManager.resolveProxyParameters(tool).isEmpty())
    }
}
