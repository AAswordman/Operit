package com.ai.assistance.operit.core.tools

import kotlinx.serialization.Serializable

/** 托管命令返回可重复查询的尾部快照，任务状态与命令成功状态分开呈现。 */
@Serializable
data class TerminalTaskResultData(
    val runId: String,
    val sessionId: String,
    val command: String,
    val status: String,
    val output: String,
    val outputTruncated: Boolean,
    val exitCode: Int?,
    val sessionExitCode: Int?,
    val terminationReason: String?,
    val screen: String?,
    val timedOut: Boolean,
    val cancellationOutcome: String? = null,
    val outputMode: String,
) : ToolResultData() {
    override fun toString(): String = buildString {
        append("Terminal task ").append(runId).append(": ").append(status)
        append("; exitCode=").append(exitCode).append("; sessionExitCode=").append(sessionExitCode)
        cancellationOutcome?.let { append("; cancellationOutcome=").append(it) }
        terminationReason?.let { append("; reason=").append(it) }
        if (output.isNotEmpty()) append("\n").append(output)
        screen?.let { append("\nCurrent terminal screen:\n").append(it) }
    }
}