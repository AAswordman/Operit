package com.ai.assistance.operit.data.mcp

import java.util.Locale

/** 解析 stdio 服务的目录与安装规则，避免用应用管理目录覆盖用户配置的运行文件。 */
internal object McpStdioConfigResolver {
    private val PACKAGE_RUNNER_COMMANDS = setOf("npx", "uvx", "uv")
    private val EVAL_FLAGS = setOf("-e", "--eval", "-c", "--command", "-m", "--module")
    private const val APP_MANAGED_PREFIX = "mcp_plugins/"

    fun commandName(command: String): String =
        command.trim().substringAfterLast('/').substringAfterLast('\\').lowercase(Locale.ROOT)

    /** 判断参数是否指向终端中的具体路径。 */
    fun isTerminalPath(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.startsWith("/") ||
            trimmed == "~" ||
            trimmed.startsWith("~/") ||
            trimmed.startsWith("\$HOME/")
    }

    private fun normalizeHomePath(value: String): String {
        val trimmed = value.trim()
        return when {
            trimmed == "~" -> ""
            trimmed.startsWith("~/") -> trimmed.removePrefix("~/")
            trimmed.startsWith("\$HOME/") -> trimmed.removePrefix("\$HOME/")
            trimmed.startsWith("/root/") -> trimmed.removePrefix("/root/")
            else -> trimmed.removePrefix("/")
        }
    }

    /** 判断路径是否位于应用管理的运行目录中。 */
    fun isAppManagedRuntimePath(value: String): Boolean {
        if (!isTerminalPath(value)) return false
        val relative = normalizeHomePath(value)
        return relative == "mcp_plugins" || relative.startsWith(APP_MANAGED_PREFIX)
    }

    /** 终端中的外部路径由用户维护，无需复制应用侧文件。 */
    private fun isExternalTerminalPath(value: String): Boolean =
        isTerminalPath(value) && !isAppManagedRuntimePath(value)

    /** 寻找首个位置参数，跳过选项和内联求值内容。 */
    private fun firstPositionalArgument(args: List<String>): String? {
        var index = 0
        while (index < args.size) {
            val argument = args[index]
            when {
                argument.isBlank() -> index += 1
                EVAL_FLAGS.contains(argument) -> index += 2
                argument.startsWith("-") -> index += 1
                else -> return argument
            }
        }
        return null
    }

    /** 判断服务是否使用应用管理目录。 */
    fun usesAppManagedRuntimeDirectory(
        command: String,
        args: List<String>,
        cwd: String?
    ): Boolean {
        if (!cwd.isNullOrBlank()) {
            // 显式指定应用管理目录时仍需部署，其他目录由用户维护。
            return isAppManagedRuntimePath(cwd)
        }
        val entry = firstPositionalArgument(args)
        if (entry != null) return !isExternalTerminalPath(entry)
        return !isExternalTerminalPath(command)
    }

    /** 判断启动前是否需要复制应用侧插件文件；包运行器自行准备依赖。 */
    fun requiresAppManagedInstallation(
        command: String,
        args: List<String>,
        cwd: String?
    ): Boolean {
        if (commandName(command) in PACKAGE_RUNNER_COMMANDS) return false
        return usesAppManagedRuntimeDirectory(command, args, cwd)
    }

    /** 按显式目录、外部入口目录和应用管理目录解析工作目录。 */
    fun resolveWorkingDirectory(
        configuredCwd: String?,
        pluginId: String,
        command: String,
        args: List<String>
    ): String {
        val explicit = configuredCwd?.trim().orEmpty()
        if (explicit.isNotEmpty()) return explicit

        val entry = firstPositionalArgument(args)
        val anchor = when {
            entry != null && isExternalTerminalPath(entry) -> entry
            entry == null && isExternalTerminalPath(command) -> command
            else -> null
        }
        if (anchor != null) {
            val parent = anchor.substringBeforeLast('/', missingDelimiterValue = "")
            if (parent.isNotEmpty()) return parent
        }

        val shortName = pluginId.split("/").last()
        return "~/mcp_plugins/$shortName"
    }
}
