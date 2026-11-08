package com.ai.assistance.operit.core.tools.system.uiautomator

import com.ai.assistance.operit.core.tools.system.shell.AndroidShellExecutor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 将旋转状态的保存、页面读取与恢复串行执行，避免 UIAutomator 覆盖系统设置。 */
internal object UiAutomatorDumpGuard {
    private val dumpMutex = Mutex()

    suspend fun execute(
        command: String,
        executeShell: suspend (String) -> AndroidShellExecutor.CommandResult,
    ): AndroidShellExecutor.CommandResult = dumpMutex.withLock {
        executeShell(wrapCommand(command))
    }

    internal fun wrapCommand(command: String): String {
        require(command.startsWith("uiautomator dump "))
        val script = """
            operit_rotation_auto=${'$'}(settings --user current get system accelerometer_rotation) || exit ${'$'}?
            operit_rotation_value=${'$'}(settings --user current get system user_rotation) || exit ${'$'}?
            case "${'$'}operit_rotation_auto" in
                0|1|null) ;;
                *) echo "UIAutomator: invalid accelerometer_rotation snapshot" >&2; exit 1 ;;
            esac
            case "${'$'}operit_rotation_value" in
                0|1|2|3|null) ;;
                *) echo "UIAutomator: invalid user_rotation snapshot" >&2; exit 1 ;;
            esac
            operit_restore_setting() {
                operit_setting_current=${'$'}(settings --user current get system "${'$'}1") || return ${'$'}?
                [ "${'$'}operit_setting_current" = "${'$'}2" ] && return 0
                if [ "${'$'}2" = null ]; then
                    settings --user current delete system "${'$'}1" >/dev/null || return ${'$'}?
                else
                    settings --user current put system "${'$'}1" "${'$'}2" >/dev/null || return ${'$'}?
                fi
                operit_setting_restored=${'$'}(settings --user current get system "${'$'}1") || return ${'$'}?
                if [ "${'$'}operit_setting_restored" != "${'$'}2" ]; then
                    echo "UIAutomator: failed to restore ${'$'}1" >&2
                    return 1
                fi
                return 0
            }
            operit_restore_rotation() {
                operit_dump_status=${'$'}?
                trap - 0 1 2 15
                operit_restore_status=0
                operit_restore_setting user_rotation "${'$'}operit_rotation_value" || operit_restore_status=${'$'}?
                operit_restore_setting accelerometer_rotation "${'$'}operit_rotation_auto" || operit_restore_status=${'$'}?
                if [ "${'$'}operit_dump_status" -ne 0 ]; then
                    exit "${'$'}operit_dump_status"
                fi
                exit "${'$'}operit_restore_status"
            }
            trap operit_restore_rotation 0
            trap 'exit 129' 1
            trap 'exit 130' 2
            trap 'exit 143' 15
            if $command; then
                exit 0
            else
                exit ${'$'}?
            fi
        """.trimIndent()
        // Root 的 Shell 身份启动器直接执行 argv；显式启动解释器，且不让脚本展开到外层 Shell。
        return "sh -c '" + script.replace("'", "'\\''") + "'"
    }
}
