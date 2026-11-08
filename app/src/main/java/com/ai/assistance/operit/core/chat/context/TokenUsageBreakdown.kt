package com.ai.assistance.operit.core.chat.context

import org.json.JSONArray
import org.json.JSONObject

/** 九类输入与真实输出分别累计，展示时将输出计入 AI 消息。 */
data class TokenUsageBreakdown(
    val userMessageTokens: Long = 0L,
    val assistantMessageTokens: Long = 0L,
    val toolResultTokens: Long = 0L,
    val systemPromptTokens: Long = 0L,
    val residentToolTokens: Long = 0L,
    val mcpToolTokens: Long = 0L,
    val skillTokens: Long = 0L,
    val summaryTokens: Long = 0L,
    val otherTokens: Long = 0L,
    val outputTokens: Long = 0L,
) {
    private fun inputParts() = longArrayOf(userMessageTokens, assistantMessageTokens, toolResultTokens,
        systemPromptTokens, residentToolTokens, mcpToolTokens, skillTokens, summaryTokens, otherTokens)
    val inputTokens: Long get() = inputParts().sum()
    val totalTokens: Long get() = inputTokens + outputTokens
    val categoryTokens: List<Long> get() = inputParts().also { it[1] += outputTokens }.toList()

    operator fun plus(other: TokenUsageBreakdown): TokenUsageBreakdown =
        fromParts(inputParts().zip(other.inputParts()) { a, z -> a + z }.toLongArray(), outputTokens + other.outputTokens)

    /** 分类是估算；总数始终与提供方记账一致，未分项输入明确计入其他。 */
    fun withTotals(input: Long, output: Long): TokenUsageBreakdown {
        val parts = ContextWindowBreakdownEstimator.calibrateToWindow(inputParts(), input.coerceAtLeast(0L))
        parts[8] += input.coerceAtLeast(0L) - parts.sum()
        return fromParts(parts, output.coerceAtLeast(0L))
    }

    fun encode(): String = JSONObject().put("version", 1)
        .put("input", JSONArray(inputParts().toList())).put("output", outputTokens).toString()

    companion object {
        private fun fromParts(p: LongArray, output: Long) = TokenUsageBreakdown(
            p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8], output,
        )

        /** 旧会话没有分类记录，保留历史总额而不根据当前窗口猜测历史比例。 */
        fun fromStored(value: String?, input: Long, output: Long): TokenUsageBreakdown {
            if (value == null) return TokenUsageBreakdown(otherTokens = input.coerceAtLeast(0L), outputTokens = output.coerceAtLeast(0L))
            val json = JSONObject(value)
            require(json.getInt("version") == 1)
            val parts = json.getJSONArray("input")
            require(parts.length() == 9)
            val counts = LongArray(9) { parts.getLong(it).also { count -> require(count >= 0L) } }
            val result = fromParts(counts, json.getLong("output").also { require(it >= 0L) })
            return result.withTotals(input, output)
        }

        fun fromRequest(snapshot: RequestWindowSnapshot, output: Long): TokenUsageBreakdown {
            val parts = ContextWindowBreakdownEstimator.fromSnapshot(snapshot, 0L, 0f, false)
            return TokenUsageBreakdown(parts.userMessageTokens, parts.assistantMessageTokens, parts.toolResultTokens,
                parts.systemPromptTokens, parts.residentToolTokens, parts.mcpToolTokens, parts.skillTokens,
                parts.summaryTokens, parts.otherTokens, output.coerceAtLeast(0L))
        }

        /** 重复或迟到的统计保存只累计尚未入账的差额。 */
        fun advance(stored: String?, oldInput: Long, oldOutput: Long, input: Long, output: Long,
            turn: TokenUsageBreakdown?): TokenUsageBreakdown {
            val previous = fromStored(stored, oldInput, oldOutput)
            val deltaInput = (input - oldInput).coerceAtLeast(0L)
            val deltaOutput = (output - oldOutput).coerceAtLeast(0L)
            val addition = if (turn == null) TokenUsageBreakdown(otherTokens = deltaInput, outputTokens = deltaOutput)
                else turn.withTotals(deltaInput, deltaOutput)
            return previous + addition
        }
    }
}
