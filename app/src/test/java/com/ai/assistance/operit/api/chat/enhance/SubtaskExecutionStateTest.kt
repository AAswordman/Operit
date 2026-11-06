package com.ai.assistance.operit.api.chat.enhance

import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolInvocation
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtaskExecutionStateTest {
    @Test
    fun `subtask policy rejects budgets outside the supported range`() {
        for (maximum in listOf(0, -1, 65, 999_999)) {
            try {
                SubtaskExecutionState(maximum)
                org.junit.Assert.fail("unsupported budget must be rejected")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("1 through 64"))
            }
        }
    }

    @Test
    fun `concurrent admissions and later rounds share exactly one tool budget`() {
        val state = SubtaskExecutionState(3)
        val workers = Executors.newFixedThreadPool(8)
        try {
            val admissions = (1..24).map { workers.submit<Boolean> { state.tryStartTool() } }
            assertEquals(3, admissions.count { it.get() })
            assertFalse(state.tryStartTool())
            val outcome = JSONObject(requireNotNull(state.takeOutcomeJson()))
            assertEquals("tool_budget_exhausted", outcome.getString("reason"))
            assertEquals(3, outcome.getInt("executedToolCount"))
        } finally {
            workers.shutdownNow()
        }
    }

    @Test
    fun `tool evidence retains failed data and identifies explicit truncation`() {
        val callbacks = mutableListOf<String>()
        val state = SubtaskExecutionState(2) { callbacks.add(it) }
        assertTrue(state.tryStartTool())
        val text = "output".repeat(6_000)
        val record = JSONObject(state.recordToolResult(
            AITool("read_file", listOf(ToolParameter("path", "/missing"))),
            ToolResult("read_file", false, StringResultData(text), "read failed"),
            executed = true,
        ).json)
        assertFalse(record.getBoolean("success"))
        assertTrue(record.getBoolean("executed"))
        assertEquals("/missing", record.getJSONArray("arguments").getJSONObject(0).getString("value"))
        assertEquals(SubtaskExecutionState.MAX_RESULT_CHARS, record.getString("result").length)
        assertTrue(record.getBoolean("resultTruncated"))
        assertEquals("read failed", record.getString("error"))
        assertEquals(1, callbacks.size)
    }

    @Test
    fun `model evidence header uses the exact ledger id without modifying source evidence`() {
        val state = SubtaskExecutionState(1)
        val original = ToolResult("read_file", true, StringResultData("actual file output"))
        val recorded = state.recordToolResult(AITool("read_file", emptyList()), original, executed = true)
        val modelResult = ToolExecutionManager.withSubtaskEvidence(original, recorded.id)
        assertEquals("actual file output", JSONObject(recorded.json).getString("result"))
        assertEquals("${recorded.id}", JSONObject(recorded.json).getString("id"))
        assertEquals("Subtask evidence ID: ${recorded.id}\nactual file output", modelResult.result.toString())
    }

    @Test
    fun `cancelled admitted flow keeps partial evidence and propagates the same cancellation`() = runTest {
        val state = SubtaskExecutionState(1)
        assertTrue(state.tryStartTool())
        val cancelled = CancellationException("caller stopped this run")
        try {
            ToolExecutionManager.collectSubtaskToolResult(state, AITool("read_file", emptyList()), "read_file") {
                flow {
                    emit(ToolResult("read_file", true, StringResultData("actual partial output")))
                    throw cancelled
                }
            }
            org.junit.Assert.fail("cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancelled, actual)
        }
        val outcome = JSONObject(requireNotNull(state.takeOutcomeJson()))
        assertEquals("cancelled", outcome.getString("reason"))
        assertEquals(1, outcome.getInt("executedToolCount"))
        val records = outcome.getJSONArray("tools")
        assertEquals(1, records.length())
        assertTrue(records.getJSONObject(0).getBoolean("executed"))
        assertFalse(records.getJSONObject(0).getBoolean("success"))
        assertEquals("actual partial output", records.getJSONObject(0).getString("result"))
    }

    @Test
    fun `tool construction failure still accounts for its admitted execution`() = runTest {
        val state = SubtaskExecutionState(1)
        assertTrue(state.tryStartTool())
        val failure = IllegalStateException("executor setup failed")
        try {
            ToolExecutionManager.collectSubtaskToolResult(state, AITool("read_file", emptyList()), "read_file") {
                throw failure
            }
            org.junit.Assert.fail("execution failure must propagate")
        } catch (actual: IllegalStateException) {
            assertSame(failure, actual)
        }
        val outcome = JSONObject(requireNotNull(state.takeOutcomeJson()))
        assertEquals("execution_error", outcome.getString("reason"))
        assertEquals(1, outcome.getJSONArray("tools").length())
        assertEquals(1, outcome.getInt("executedToolCount"))
    }

    @Test
    fun `a denial is unexecuted and cannot be overwritten by normal completion`() {
        val state = SubtaskExecutionState(8)
        state.stop(SubtaskExecutionState.Reason.PERMISSION_DENIED, "not allowed")
        state.recordToolResult(
            AITool("read_file", emptyList()),
            ToolResult("read_file", false, StringResultData("evidence"), "not allowed"),
            executed = false,
        )
        state.stop(SubtaskExecutionState.Reason.MODEL_FINISHED, finalText = "must not appear")
        assertFalse(state.tryStartTool())
        val outcome = JSONObject(requireNotNull(state.takeOutcomeJson()))
        assertEquals("permission_denied", outcome.getString("reason"))
        assertEquals("", outcome.getString("finalText"))
        assertEquals(0, outcome.getInt("executedToolCount"))
        assertFalse(outcome.getJSONArray("tools").getJSONObject(0).getBoolean("executed"))
        assertNull(state.takeOutcomeJson())
    }

    @Test
    fun `each run has an independent session identity and final answer`() {
        val first = SubtaskExecutionState(1)
        val second = SubtaskExecutionState(1)
        assertNotEquals(first.toolSessionScopeId, second.toolSessionScopeId)
        first.stop(SubtaskExecutionState.Reason.MODEL_FINISHED, finalText = "only the revised final round")
        second.stop(SubtaskExecutionState.Reason.CANCELLED, "cancelled by caller")
        val result = JSONObject(requireNotNull(first.takeOutcomeJson()))
        assertEquals("model_finished", result.getString("reason"))
        assertEquals("only the revised final round", result.getString("finalText"))
        assertTrue(result.isNull("error"))
        assertEquals("cancelled", JSONObject(requireNotNull(second.takeOutcomeJson())).getString("reason"))
    }

    @Test
    fun `failed terminal JSON remains visible after model result aggregation`() = runTest {
        val data = "{\"timedOut\":true,\"output\":\"partial output\",\"exitCode\":-1}"
        val result = ToolExecutionManager.aggregateToolResults(
            "super_admin:terminal",
            flowOf(ToolResult("super_admin:terminal", false, StringResultData(data), "Command timed out")),
        )
        assertFalse(result.success)
        assertEquals("Command timed out", result.error)
        assertTrue(result.result.toString().contains(data))
    }

    @Test
    fun `failed subagent XML is preserved with its failure flag`() = runTest {
        val xml = "<subagent><final success=\"false\" status=\"blocked\">missing file</final></subagent>"
        val result = ToolExecutionManager.aggregateToolResults(
            "subagent:subagent_run",
            flowOf(ToolResult("subagent:subagent_run", false, StringResultData(xml), "blocked")),
        )
        assertFalse(result.success)
        assertTrue(result.result.toString().contains(xml))
        assertEquals("blocked", result.error)
    }

    @Test
    fun `delegation gate resolves direct package proxy and CLI proxy targets`() {
        val direct = AITool("subagent:subagent_run", emptyList())
        assertTrue(ToolExecutionManager.isSubtaskDelegationTarget(direct, null))
        for (proxy in listOf("package_proxy", "proxy")) {
            val wrapped = AITool(proxy, listOf(
                ToolParameter("tool_name", "renamed_bundle:subagent_run"),
                ToolParameter("params", "{}"),
            ))
            assertTrue(ToolExecutionManager.isSubtaskDelegationTarget(wrapped, "com.operit.subagent_bundle"))
            assertFalse(ToolExecutionManager.isSubtaskDelegationTarget(wrapped, "unrelated.bundle"))
        }
    }

    @Test
    fun `nested proxy wrappers do not hide delegation`() {
        val nested = proxy("proxy", proxy("package_proxy", AITool("alias:subagent_run", emptyList())))
        assertEquals("alias:subagent_run", ToolExecutionManager.resolveSubtaskTarget(nested).name)
        assertTrue(ToolExecutionManager.isSubtaskDelegationTarget(nested, "com.operit.subagent_bundle"))
        var tooDeep = AITool("read_file", emptyList())
        repeat(9) { tooDeep = proxy("proxy", tooDeep) }
        try {
            ToolExecutionManager.resolveSubtaskTarget(tooDeep)
            org.junit.Assert.fail("over-deep proxies must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("8 levels"))
        }
        try {
            ToolExecutionManager.resolveSubtaskTarget(AITool("proxy", emptyList()))
            org.junit.Assert.fail("an invalid proxy must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("tool_name"))
        }
    }

    @Test
    fun `final JSON declarations quote tools without turning them into tool rounds`() {
        val raw = "{\"status\":\"completed\",\"summary\":\"<tool_A name=\\\"delete_file\\\">literal</tool_A><think>literal</think>\"}"
        assertTrue(SubtaskExecutionState.isFinalDeclarationCandidate(raw))
        assertTrue(SubtaskExecutionState.isFinalDeclarationCandidate("<think>internal reasoning</think>\n$raw"))
        assertTrue(SubtaskExecutionState.isFinalDeclarationCandidate("<status type=\"a>b\"/><search>hidden</search>\n$raw"))
        assertTrue(SubtaskExecutionState.isFinalDeclarationCandidate("```json\n$raw\n```"))
        assertTrue(SubtaskExecutionState.isFinalDeclarationCandidate("{ malformed declaration <tool_A>"))
        assertFalse(SubtaskExecutionState.isFinalDeclarationCandidate("<tool_A name=\"read_file\"></tool_A>"))
        assertFalse(SubtaskExecutionState.isFinalDeclarationCandidate("<tool_A name=\"read_file\"></tool_A>\n$raw"))
        assertFalse(SubtaskExecutionState.isFinalDeclarationCandidate("<think>internal only</think>"))
    }

    @Test
    fun `nested scope injection replaces forged values at the leaf`() {
        val leaf = AITool("super_admin:terminal", listOf(
            ToolParameter("command", "echo ok"),
            ToolParameter("__operit_tool_session_id", "forged-leaf"),
            ToolParameter("__operit_package_caller_card_id", "forged-role"),
        ))
        val injected = ToolExecutionManager.injectPackageCallContext(
            invocation = ToolInvocation(proxy("proxy", proxy("package_proxy", leaf)), "", 0..0),
            jsPackageNames = setOf("super_admin"),
            callerName = null,
            callerChatId = "parent-chat",
            callerCardId = "parent-role",
            toolSessionScopeId = "host-scope",
            enforceCallerIdentity = true,
        )
        val params = ToolExecutionManager.resolveSubtaskTarget(injected.tool).parameters.associate { it.name to it.value }
        assertEquals("host-scope", params["__operit_tool_session_id"])
        assertEquals("parent-chat", params["__operit_package_chat_id"])
        assertEquals("parent-role", params["__operit_package_caller_card_id"])
    }

    @Test
    fun `model supplied session and identity fields cannot override a child run`() {
        val tool = AITool("package_proxy", listOf(
            ToolParameter("tool_name", "super_admin:terminal"),
            ToolParameter("__operit_tool_session_id", "forged-outer"),
            ToolParameter("__operit_package_chat_id", "forged-chat"),
            ToolParameter("params", "{\"command\":\"echo ok\",\"__operit_tool_session_id\":\"forged-inner\",\"__operit_package_caller_card_id\":\"other-card\"}"),
        ))
        val injected = ToolExecutionManager.injectPackageCallContext(
            invocation = ToolInvocation(tool, "", 0..0),
            jsPackageNames = setOf("super_admin"),
            callerName = null,
            callerChatId = "parent-chat",
            callerCardId = "parent-card",
            toolSessionScopeId = "run-scope",
            enforceCallerIdentity = true,
        ).tool.parameters.associate { it.name to it.value }
        val nested = JSONObject(requireNotNull(injected["params"]))
        assertEquals("run-scope", injected["__operit_tool_session_id"])
        assertEquals("parent-chat", injected["__operit_package_chat_id"])
        assertEquals("run-scope", nested.getString("__operit_tool_session_id"))
        assertEquals("parent-chat", nested.getString("__operit_package_chat_id"))
        assertEquals("parent-card", nested.getString("__operit_package_caller_card_id"))
        assertEquals("echo ok", nested.getString("command"))
    }

    @Test
    fun `ordinary package calls receive the host chat session scope`() {
        val tool = AITool("super_admin:terminal", listOf(
            ToolParameter("command", "echo ok"),
            ToolParameter("__operit_tool_session_id", "forged"),
        ))
        val params = ToolExecutionManager.injectPackageCallContext(
            invocation = ToolInvocation(tool, "", 0..0),
            jsPackageNames = setOf("super_admin"),
            callerName = null,
            callerChatId = "real-chat",
            callerCardId = null,
            toolSessionScopeId = "real-chat",
            enforceCallerIdentity = false,
        ).tool.parameters
        assertEquals(listOf("real-chat"), params.filter { it.name == "__operit_tool_session_id" }.map { it.value })
    }

    @Test
    fun `bad proxy input remains one failed tool slot and does not discard a normal batch`() = runTest {
        val invalid = ToolInvocation(AITool("package_proxy", listOf(
            ToolParameter("tool_name", "super_admin:terminal"),
            ToolParameter("params", "{invalid JSON"),
        )), "", 0..0)
        val prepared = ToolExecutionManager.preparePackageCallContext(
            invalid, setOf("super_admin"), null,
            ToolExecutionManager.ToolRuntimeContext(callerChatId = "chat", toolSessionScopeId = "chat"),
        )
        assertTrue(prepared is ToolExecutionManager.PackageContextInjection.Rejected)
        val failure = (prepared as ToolExecutionManager.PackageContextInjection.Rejected).result
        assertFalse(failure.success)
        assertEquals("params must be a valid JSON object", failure.error)
        val output = mutableListOf<String>()
        val ordered = OrderedToolResults(2) { output.add(it) }
        ordered.complete(0, failure)
        ordered.complete(1, ToolResult("calculate", true, StringResultData("4")))
        val batch = ordered.finish()
        assertEquals(listOf(false, true), batch.results.map { it.success })
        assertEquals(2, output.size)
        assertEquals("4", batch.results[1].result.toString())
    }

    @Test
    fun `duplicate proxy routing inputs are rejected without merging or executing them`() {
        val normal = listOf(ToolParameter("tool_name", "super_admin:terminal"), ToolParameter("params", "{}"))
        for (duplicate in normal) {
            val prepared = ToolExecutionManager.preparePackageCallContext(
                ToolInvocation(AITool("package_proxy", normal + duplicate), "", 0..0),
                setOf("super_admin"), null, ToolExecutionManager.ToolRuntimeContext(),
            )
            assertTrue(prepared is ToolExecutionManager.PackageContextInjection.Rejected)
            val result = (prepared as ToolExecutionManager.PackageContextInjection.Rejected).result
            assertFalse(result.success)
            assertEquals("Exactly one ${duplicate.name} parameter is required", result.error)
        }
    }

    @Test
    fun `context injection does not hide unrelated execution failures`() {
        val unexpected = IllegalStateException("package registry unavailable")
        val packageNames = object : Set<String> by setOf("super_admin") {
            override fun contains(element: String): Boolean = throw unexpected
        }
        try {
            ToolExecutionManager.preparePackageCallContext(
                ToolInvocation(AITool("super_admin:terminal", emptyList()), "", 0..0),
                packageNames, null, ToolExecutionManager.ToolRuntimeContext(),
            )
            org.junit.Assert.fail("unexpected execution failures must propagate")
        } catch (actual: IllegalStateException) {
            assertSame(unexpected, actual)
        }
    }

    private fun proxy(name: String, target: AITool): AITool {
        val payload = JSONObject()
        target.parameters.forEach { payload.put(it.name, it.value) }
        return AITool(name, listOf(
            ToolParameter("tool_name", target.name),
            ToolParameter("params", payload.toString()),
        ))
    }
}
