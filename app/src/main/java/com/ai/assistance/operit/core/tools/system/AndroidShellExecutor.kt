package com.ai.assistance.operit.core.tools.system

import android.content.Context
import com.ai.assistance.operit.core.tools.permissions.PermissionBackend
import com.ai.assistance.operit.core.tools.permissions.PermissionCapabilityResolver
import com.ai.assistance.operit.core.tools.system.shell.ShellExecutor
import com.ai.assistance.operit.core.tools.system.shell.ShellExecutorFactory
import com.ai.assistance.operit.core.tools.system.shell.ShellProcess
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences

/** 根据模式和当前授权选择 Shell 后端；命令提交后不自动重放。 */
class AndroidShellExecutor {
    companion object {
        @Volatile private var context: Context? = null

        fun setContext(appContext: Context) {
            context = appContext.applicationContext
        }

        private fun resolveExecutor(ctx: Context, identity: ShellIdentity): ShellExecutor {
            if (identity == ShellIdentity.APP) {
                return ShellExecutorFactory.getBackendExecutor(ctx, PermissionBackend.STANDARD)
            }
            val mode = androidPermissionPreferences.getPreferredPermissionLevel() ?: AndroidPermissionLevel.STANDARD
            val capabilities = PermissionCapabilityResolver.shellSnapshot(ctx, mode)
            val backend = capabilities.shellBackendFor(identity)
            checkNotNull(backend) { "$identity identity is not available in $mode mode with the current authorization" }
            return ShellExecutorFactory.getBackendExecutor(ctx, backend)
        }

        suspend fun executeShellCommand(command: String): CommandResult = executeShellCommand(command, null)

        suspend fun executeShellCommand(command: String, identityOverride: ShellIdentity?): CommandResult {
            val ctx = context ?: return CommandResult(false, "", "Context not initialized")
            val identity = identityOverride ?: ShellIdentity.DEFAULT
            val executor = try {
                resolveExecutor(ctx, identity)
            } catch (e: IllegalStateException) {
                return CommandResult(false, "", e.message.orEmpty())
            }
            val result = executor.executeCommand(command, identity)
            return CommandResult(result.success, result.stdout, result.stderr, result.exitCode)
        }

        suspend fun startShellProcess(command: String): ShellProcess {
            val ctx = context ?: error("Context not initialized")
            val preferredExecutor = resolveExecutor(ctx, ShellIdentity.DEFAULT)
            return preferredExecutor.startProcess(command)
        }
    }

    data class CommandResult(
        val success: Boolean,
        val stdout: String,
        val stderr: String = "",
        val exitCode: Int = -1
    )
}

enum class ShellIdentity { DEFAULT, APP, ROOT, SHELL }
