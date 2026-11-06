package com.ai.assistance.operit.api.chat.enhance

import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** One sendMessage collection owns this state; neither the budget nor evidence is shared by runs. */
class SubtaskExecutionState(
    val maxToolCalls: Int,
    private val onToolResult: (String) -> Unit = {},
) {
    enum class Reason(val wireValue: String) {
        MODEL_FINISHED("model_finished"),
        TOOL_BUDGET_EXHAUSTED("tool_budget_exhausted"),
        DELEGATION_DENIED("delegation_denied"),
        PERMISSION_DENIED("permission_denied"),
        TOKEN_LIMIT("token_limit"),
        CANCELLED("cancelled"),
        EXECUTION_ERROR("execution_error"),
    }

    private data class TerminalState(val reason: Reason, val error: String?, val finalText: String)
    data class RecordedResult(val id: String, val json: String)

    val toolSessionScopeId: String = "subtask_${UUID.randomUUID()}"
    private val lock = Any()
    private var terminal: TerminalState? = null
    private var outcomeDelivered = false
    private var executedToolCount = 0
    private var nextRecordId = 0
    private val records = mutableListOf<JSONObject>()

    init {
        require(maxToolCalls in 1..64) { "subtaskMaxToolCalls must be an integer from 1 through 64" }
    }

    fun isStopped(): Boolean = synchronized(lock) { terminal != null }

    fun stop(reason: Reason, error: String? = null, finalText: String = "") = synchronized(lock) {
        if (terminal == null) terminal = TerminalState(reason, error, finalText)
    }

    /** Called after permission and parameter validation, immediately before a model-selected tool. */
    fun tryStartTool(): Boolean = synchronized(lock) {
        if (terminal != null) return@synchronized false
        if (executedToolCount == maxToolCalls) {
            terminal = TerminalState(
                Reason.TOOL_BUDGET_EXHAUSTED,
                "Subtask tool budget exhausted ($maxToolCalls calls)",
                "",
            )
            return@synchronized false
        }
        executedToolCount++
        true
    }

    fun stopMessage(): String = synchronized(lock) {
        val state = checkNotNull(terminal) { "Subtask has not stopped" }
        state.error ?: "Subtask execution has ended (${state.reason.wireValue})"
    }

    /** Keep the real final result before the model-facing stream aggregation turns it into text. */
    fun recordToolResult(tool: AITool, result: ToolResult, executed: Boolean): RecordedResult {
        val resultText = result.result.toString()
        val json = synchronized(lock) {
            val record = JSONObject()
                .put("id", "$toolSessionScopeId:${++nextRecordId}")
                .put("toolName", tool.name)
                .put("arguments", JSONArray().apply {
                    tool.parameters.forEach { parameter ->
                        put(JSONObject().put("name", parameter.name).put("value", parameter.value))
                    }
                })
                .put("success", result.success)
                .put("result", resultText.take(MAX_RESULT_CHARS))
                .put("resultTruncated", resultText.length > MAX_RESULT_CHARS)
                .put("error", result.error ?: JSONObject.NULL)
                .put("executed", executed)
            records.add(record)
            RecordedResult(record.getString("id"), record.toString())
        }
        onToolResult(json.json)
        return json
    }

    /** The outer collection publishes the outcome once, after all its tool jobs have completed. */
    fun takeOutcomeJson(): String? = synchronized(lock) {
        if (outcomeDelivered) return@synchronized null
        val state = checkNotNull(terminal) { "Subtask ended without a terminal outcome" }
        outcomeDelivered = true
        JSONObject()
            .put("reason", state.reason.wireValue)
            .put("error", state.error ?: JSONObject.NULL)
            .put("toolSessionScopeId", toolSessionScopeId)
            .put("finalText", state.finalText)
            .put("executedToolCount", executedToolCount)
            .put("tools", JSONArray(records))
            .toString()
    }

    companion object {
        const val MAX_RESULT_CHARS = 32_000

        /** Only leading hidden blocks are syntax; XML text inside JSON strings is task data. */
        internal fun isFinalDeclarationCandidate(rawRound: String): Boolean {
            var body = rawRound.trim()
            val opening = Regex("^<(think|thinking|search|status)(?=[\\s/>]|$)", RegexOption.IGNORE_CASE)
            while (true) {
                val tag = opening.find(body) ?: break
                var quote: Char? = null
                var tagEnd = -1
                for (index in tag.range.last + 1 until body.length) {
                    val char = body[index]
                    if (quote != null) {
                        if (char == quote) quote = null
                    } else if (char == '\'' || char == '"') {
                        quote = char
                    } else if (char == '>') {
                        tagEnd = index
                        break
                    }
                }
                if (tagEnd < 0) return false
                if (tagEnd > 0 && body[tagEnd - 1] == '/') {
                    body = body.substring(tagEnd + 1).trimStart()
                    continue
                }
                val closing = Regex("</${tag.groupValues[1]}\\s*>", RegexOption.IGNORE_CASE)
                    .find(body, tagEnd + 1) ?: return false
                body = body.substring(closing.range.last + 1).trimStart()
            }
            if (body.startsWith("{")) return true
            return Regex("^```json[ \\t]*\\r?\\n[\\s\\S]*\\r?\\n```$", RegexOption.IGNORE_CASE)
                .matches(body)
        }
    }
}
