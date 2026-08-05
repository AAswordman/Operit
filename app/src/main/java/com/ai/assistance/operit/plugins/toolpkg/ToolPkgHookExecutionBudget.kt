package com.ai.assistance.operit.plugins.toolpkg

import com.ai.assistance.operit.core.application.OperitApplication
import com.ai.assistance.operit.core.tools.javascript.extractJsExecutionErrorMessage
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.TimeUnit

/**
 * A shared deadline for one synchronous ToolPkg hook dispatch chain.
 *
 * A chain may invoke several hooks serially. Giving each hook the full configured timeout would
 * multiply the time spent before a message can continue, so each invocation receives only the
 * time still available from this single deadline.
 */
internal class ToolPkgHookExecutionBudget private constructor(
    private val startedAtNanos: Long,
    private val deadlineNanos: Long
) {
    companion object {
        fun create(): ToolPkgHookExecutionBudget {
            val context = OperitApplication.instance.applicationContext
            val timeoutSeconds =
                DisplayPreferencesManager.getInstance(context).getToolPkgHookTimeoutSeconds()
            val startedAtNanos = System.nanoTime()
            return ToolPkgHookExecutionBudget(
                startedAtNanos = startedAtNanos,
                deadlineNanos = startedAtNanos + TimeUnit.SECONDS.toNanos(timeoutSeconds.toLong())
            )
        }
    }

    fun remainingMillis(): Long? {
        val remainingNanos = deadlineNanos - System.nanoTime()
        if (remainingNanos <= 0L) {
            return null
        }
        return TimeUnit.NANOSECONDS.toMillis(remainingNanos).coerceAtLeast(1L)
    }

    fun elapsedMillis(): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)

    fun logDeadlineReached(
        tag: String,
        stage: String,
        containerPackageName: String,
        hookId: String
    ) {
        AppLogger.w(
            tag,
            "ToolPkg hook skipped after timeout: stage=$stage, container=$containerPackageName, hook=$hookId, elapsedMs=${elapsedMillis()}"
        )
    }

    fun logTimeoutIfPresent(
        result: Result<Any?>,
        tag: String,
        stage: String,
        containerPackageName: String,
        hookId: String
    ): Boolean {
        val failureMessage =
            result.getOrNull()?.let(::extractJsExecutionErrorMessage)
                ?: result.exceptionOrNull()?.message
        if (failureMessage?.contains("timed out", ignoreCase = true) != true) {
            return false
        }
        AppLogger.w(
            tag,
            "ToolPkg hook timed out: stage=$stage, container=$containerPackageName, hook=$hookId, elapsedMs=${elapsedMillis()}, reason=$failureMessage"
        )
        return true
    }
}
