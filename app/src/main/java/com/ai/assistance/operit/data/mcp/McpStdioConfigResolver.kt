package com.ai.assistance.operit.data.mcp

import java.util.Locale

/**
 * Pure rules for a local stdio MCP server declared in `mcpServers`.
 *
 * A config is "self-contained" when it points at files that already exist in the Linux terminal:
 * an absolute command with no relative entry, an absolute entry argument, or a `cwd` outside
 * `~/mcp_plugins`. Such servers must not be re-deployed, otherwise a copy under
 * `~/mcp_plugins/<id>` would shadow the configured files. Everything else is launched from the
 * app-managed directory `~/mcp_plugins/<id>`.
 */
internal object McpStdioConfigResolver {
    private val PACKAGE_RUNNER_COMMANDS = setOf("npx", "uvx", "uv")
    private val EVAL_FLAGS = setOf("-e", "--eval", "-c", "--command", "-m", "--module")
    private const val APP_MANAGED_PREFIX = "mcp_plugins/"

    fun commandName(command: String): String =
        command.trim().substringAfterLast('/').substringAfterLast('\\').lowercase(Locale.ROOT)

    /** True when the value points at a concrete path in the terminal environment. */
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

    /** True when the path belongs to the directory Operit populates by copying a plugin. */
    fun isAppManagedRuntimePath(value: String): Boolean {
        if (!isTerminalPath(value)) return false
        val relative = normalizeHomePath(value)
        return relative == "mcp_plugins" || relative.startsWith(APP_MANAGED_PREFIX)
    }

    /** A path outside `~/mcp_plugins` already exists in the terminal and needs no app copy. */
    private fun isExternalTerminalPath(value: String): Boolean =
        isTerminalPath(value) && !isAppManagedRuntimePath(value)

    /** First argument that is not a flag or the value of an inline evaluation flag. */
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

    /**
     * True when the service runs from `~/mcp_plugins/<id>` instead of a directory the user
     * configured. Package runners use it as a cache dir; interpreters need the copied sources.
     */
    fun usesAppManagedRuntimeDirectory(
        command: String,
        args: List<String>,
        cwd: String?
    ): Boolean {
        if (!cwd.isNullOrBlank()) {
            // An explicit cwd that points back into ~/mcp_plugins still needs the deployer to
            // copy the plugin there; other explicit directories are maintained by the user.
            return isAppManagedRuntimePath(cwd)
        }
        val entry = firstPositionalArgument(args)
        if (entry != null) return !isExternalTerminalPath(entry)
        return !isExternalTerminalPath(command)
    }

    /**
     * True when Operit must copy an Android-side plugin directory into the Linux runtime before
     * the command can run. Package runners install their own payload; an explicit `cwd` outside
     * `~/mcp_plugins`, or an external absolute entry path, means the files already exist in the
     * terminal.
     */
    fun requiresAppManagedInstallation(
        command: String,
        args: List<String>,
        cwd: String?
    ): Boolean {
        if (commandName(command) in PACKAGE_RUNNER_COMMANDS) return false
        return usesAppManagedRuntimeDirectory(command, args, cwd)
    }

    /**
     * Working directory used when launching the service. An explicit `cwd` always wins. For a
     * self-contained config the entry script's directory keeps relative imports working; a bare
     * command or relative entry falls back to the documented app-managed directory.
     */
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
