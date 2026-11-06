package com.ai.assistance.operit.core.tools.defaultTool.standard

import com.ai.assistance.operit.terminal.CommandExecutionEvent

/** Shared by ordinary and streaming PTY execution so neither infers success from completion. */
internal class TerminalCommandOutcome {
    var exitCode: Int = -1
        private set
    var completed: Boolean = false
        private set
    var timedOut: Boolean = false
        private set
    var sessionExited: Boolean = false
        private set
    private var exitStatusReported: Boolean = false

    fun complete(event: CommandExecutionEvent) {
        require(event.isCompleted)
        completed = true
        sessionExited = event.sessionExited
        if (timedOut) return
        val reported = event.exitCode
        if (reported != null) {
            exitCode = reported
            exitStatusReported = true
        }
    }

    fun timeout() {
        timedOut = true
        exitCode = -1
    }

    fun error(timeoutMs: Long): String? = when {
        timedOut -> "Command timed out after ${timeoutMs}ms"
        !completed -> "Terminal command ended without a completion event"
        !exitStatusReported -> "Terminal command completed without an exit status"
        exitCode != 0 && sessionExited -> "Terminal session exited with code $exitCode"
        exitCode != 0 -> "Command exited with code $exitCode"
        else -> null
    }
}
