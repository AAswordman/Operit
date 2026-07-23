package com.ai.assistance.operit.core.config

import android.content.Context
import android.os.Environment
import com.ai.assistance.operit.core.chat.hooks.PromptHookContext
import com.ai.assistance.operit.core.chat.hooks.PromptHookRegistry
import com.ai.assistance.operit.core.tools.climode.CliToolModeSupport
import com.ai.assistance.operit.core.tools.climode.ToolExposureMode
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.skill.SkillRepository
import com.ai.assistance.operit.ui.features.chat.webview.workspace.process.WorkspaceRuleFileReader
import com.ai.assistance.operit.util.LocaleUtils

object SystemPromptConfig {

    private const val TOOL_USAGE_GUIDELINES_EN = """
When calling a tool, the user will see your response, and then will automatically send the tool results back to you in a follow-up message.

To use a tool, use this format in your response:

<tool name="tool_name">
<param name="parameter_name">parameter_value</param>
</tool>

When outputting XML (e.g., <tool>), insert a newline before it and ensure the opening tag starts at the beginning of a line.

Based on user needs, proactively select the most appropriate tool or combination of tools. For complex tasks, you can break down the problem and use different tools step by step to solve it. After using each tool, clearly explain the execution results and suggest the next steps."""
    private const val TOOL_USAGE_GUIDELINES_CN = """
调用工具时，用户会看到你的响应，然后会自动将工具结果发送回给你。

使用工具时，请使用以下格式：

<tool name="tool_name">
<param name="parameter_name">parameter_value</param>
</tool>

输出XML（如 <tool>）时，必须在XML前换行，并确保起始标签位于行首。

根据用户需求，主动选择最合适的工具或工具组合。对于复杂任务，你可以分解问题并使用不同的工具逐步解决。使用每个工具后，清楚地解释执行结果并建议下一步。"""

    private const val PACKAGE_SYSTEM_GUIDELINES_EN = """
PACKAGE SYSTEM
- Some additional functionality is available through packages
- To use a package, simply activate it with:
  <tool name="use_package">
  <param name="package_name">package_name_here</param>
  </tool>
- This will show you all the tools in the package and how to use them
- A package listed as activated in a Package Warmup block is already activated; use its attached tool prompt directly and do not activate it again
- Only after activating a package, you can use its tools directly"""
    private const val PACKAGE_SYSTEM_GUIDELINES_CN = """
包系统：
- 一些额外功能通过包提供
- 要使用包，只需激活它：
  <tool name="use_package">
  <param name="package_name">package_name_here</param>
  </tool>
- 这将显示包中的所有工具及其使用方法
- “工具包预热”块中标记为已激活的包已经完成激活；直接使用其中附带的工具提示，不要再次激活
- 只有在激活包后，才能直接使用其工具"""


    // Tool Call API 模式下的包系统说明（不使用XML格式）
    private const val PACKAGE_SYSTEM_GUIDELINES_TOOL_CALL_EN = """
PACKAGE SYSTEM
- Some additional functionality is available through packages
- To use a package, call the use_package function with the package_name parameter
- If use_package for a package has appeared earlier in this chat, treat that package as activated
- If a package is listed as activated in a Package Warmup block, it is also already activated; use its attached tool prompt directly and do not call use_package again
- For package tools, call package_proxy:
  - Set tool_name to the actual package tool name (e.g. packageName:toolName)
  - Put target tool arguments in params as a JSON object"""

    private const val PACKAGE_SYSTEM_GUIDELINES_TOOL_CALL_CN = """
包系统：
- 一些额外功能通过包提供
- 要使用包，调用 use_package 函数并传入 package_name 参数
- 只要本次聊天中该包曾出现过 use_package，就视为该包已激活
- “工具包预热”块中标记为已激活的包也视为已经激活；直接使用其中附带的工具提示，不要再次调用 use_package
- 调用包工具请使用 package_proxy：
  - tool_name 填写真实工具名（例如 packageName:toolName）
  - 将目标工具参数放入 params（JSON对象）"""

    private suspend fun getAvailableToolsEn(
        chatId: String?,
        hasImageRecognition: Boolean,
        chatModelHasDirectImage: Boolean,
        hasAudioRecognition: Boolean,
        hasVideoRecognition: Boolean,
        chatModelHasDirectAudio: Boolean,
        chatModelHasDirectVideo: Boolean,
        safBookmarkNames: List<String>,
        toolVisibility: Map<String, Boolean>,
        hookMetadata: Map<String, Any?> = emptyMap(),
        dispatchToolPromptComposeHooks: (PromptHookContext) -> PromptHookContext = PromptHookRegistry::dispatchToolPromptComposeHooks,
        context: Context? = null
    ): String {
        val toolOrder = if (context != null) {
            ApiPreferences.getInstance(context).getToolPromptOrder()
        } else {
            emptyList()
        }
        return SystemToolPrompts.generateToolsPromptEn(
            chatId = chatId,
            hasBackendImageRecognition = hasImageRecognition,
            includeMemoryTools = false,
            chatModelHasDirectImage = chatModelHasDirectImage,
            hasBackendAudioRecognition = hasAudioRecognition,
            hasBackendVideoRecognition = hasVideoRecognition,
            chatModelHasDirectAudio = chatModelHasDirectAudio,
            chatModelHasDirectVideo = chatModelHasDirectVideo,
            safBookmarkNames = safBookmarkNames,
            toolVisibility = toolVisibility,
            toolOrder = toolOrder,
            hookMetadata = hookMetadata,
            dispatchToolPromptComposeHooks = dispatchToolPromptComposeHooks
        )
    }

    private fun getMemoryToolsEn(toolVisibility: Map<String, Boolean>): String {
        return SystemToolPrompts.generateMemoryToolsPromptEn(toolVisibility)
    }

    private suspend fun getAvailableToolsCn(
        chatId: String?,
        hasImageRecognition: Boolean,
        chatModelHasDirectImage: Boolean,
        hasAudioRecognition: Boolean,
        hasVideoRecognition: Boolean,
        chatModelHasDirectAudio: Boolean,
        chatModelHasDirectVideo: Boolean,
        safBookmarkNames: List<String>,
        toolVisibility: Map<String, Boolean>,
        hookMetadata: Map<String, Any?> = emptyMap(),
        dispatchToolPromptComposeHooks: (PromptHookContext) -> PromptHookContext = PromptHookRegistry::dispatchToolPromptComposeHooks,
        context: Context? = null
    ): String {
        val toolOrder = if (context != null) {
            ApiPreferences.getInstance(context).getToolPromptOrder()
        } else {
            emptyList()
        }
        return SystemToolPrompts.generateToolsPromptCn(
            chatId = chatId,
            hasBackendImageRecognition = hasImageRecognition,
            includeMemoryTools = false,
            chatModelHasDirectImage = chatModelHasDirectImage,
            hasBackendAudioRecognition = hasAudioRecognition,
            hasBackendVideoRecognition = hasVideoRecognition,
            chatModelHasDirectAudio = chatModelHasDirectAudio,
            chatModelHasDirectVideo = chatModelHasDirectVideo,
            safBookmarkNames = safBookmarkNames,
            toolVisibility = toolVisibility,
            toolOrder = toolOrder,
            hookMetadata = hookMetadata,
            dispatchToolPromptComposeHooks = dispatchToolPromptComposeHooks
        )
    }

    private fun getMemoryToolsCn(toolVisibility: Map<String, Boolean>): String {
        return SystemToolPrompts.generateMemoryToolsPromptCn(toolVisibility)
    }


    /** Base system prompt template used by the enhanced AI service */
    val SYSTEM_PROMPT_TEMPLATE =
"""
BEGIN_SELF_INTRODUCTION_SECTION

WORKSPACE_GUIDELINES_SECTION

TOOL_USAGE_GUIDELINES_SECTION

PACKAGE_SYSTEM_GUIDELINES_SECTION

ACTIVE_PACKAGES_SECTION

AVAILABLE_TOOLS_SECTION
""".trimIndent()


    /** 中文版本系统提示模板 */
    val SYSTEM_PROMPT_TEMPLATE_CN =
"""
BEGIN_SELF_INTRODUCTION_SECTION

WORKSPACE_GUIDELINES_SECTION

TOOL_USAGE_GUIDELINES_SECTION

PACKAGE_SYSTEM_GUIDELINES_SECTION

ACTIVE_PACKAGES_SECTION

AVAILABLE_TOOLS_SECTION""".trimIndent()

    /**
     * Prompt for a subtask agent that should be strictly task-focused,
     * without memory or emotional attachment. It is forbidden from waiting for user input.
     */
    val SUBTASK_AGENT_PROMPT_TEMPLATE =
        """
        BEHAVIOR GUIDELINES:
        - You are a subtask-focused AI agent. Your only goal is to complete the assigned task efficiently and accurately.
        - You have no memory of past conversations, user preferences, or personality. You must not exhibit any emotion or personality.
        - **TOOL SCHEDULING**: All tools may be called either in parallel or sequentially. Choose whichever best fits the task. The tool system will decide and handle execution conflicts automatically.
        - **Summarize and Conclude**: If the task requires using tools to gather information (e.g., reading files, searching), you **MUST** process that information and provide a concise, conclusive summary as your final output. Do not output raw data. Your final answer is the only thing passed to the next agent.
        - Be concise and factual. Avoid lengthy explanations.

        TOOL_USAGE_GUIDELINES_SECTION

        PACKAGE_SYSTEM_GUIDELINES_SECTION

        ACTIVE_PACKAGES_SECTION

        AVAILABLE_TOOLS_SECTION
        """.trimIndent()

  /**
   * Applies custom prompt replacements from ApiPreferences to the system prompt
   *
   * @param systemPrompt The original system prompt
   * @param customIntroPrompt The custom introduction prompt (about Operit)
   * @return The system prompt with custom prompts applied
   */
  fun applyCustomPrompts(
          systemPrompt: String,
          customIntroPrompt: String
  ): String {
    // Always replace the introduction placeholder so an empty intro removes it cleanly.
    var result = systemPrompt

    result = result.replace("BEGIN_SELF_INTRODUCTION_SECTION", customIntroPrompt)

    return result
  }

  private fun buildGroupOrchestrationHint(
      useEnglish: Boolean,
      roleName: String,
      participantNamesText: String
  ): String {
    return if (useEnglish) {
      "\n\nRole response plan hint:\n- This chat uses a role response planner. After each user message, the system dynamically decides who responds and in what order.\n- Always keep your own role identity. Never reply as another role or imitate another persona.\n- Answer the user's latest request in your own role, optionally considering prior agents' replies.\n- If you have nothing new, reply briefly in your own role.\n\nRole-scoped history hint:\n- Messages prefixed with [From role: xxx] are historical outputs from other role cards.\n- Treat them as reference context only, not as the current user's new request.\n- Stay in role as $roleName, and do not switch persona to the referenced role.\n\nGroup participants: $participantNamesText"
    } else {
      "\n\n角色回答规划提示：\n- 当前会话启用了角色回答规划，用户每次发言后系统会动态决定谁回答以及回答顺序。\n- 你必须始终牢记并保持你自己的角色身份，严禁使用他人身份回答或模仿其他角色口吻。\n- 用你自己的角色身份回答用户最新请求，可以参考前面角色的回复。\n- 如果没有新的内容，也请用自己的角色简短回应。\n\n角色分视角历史说明：\n- 带有 [From role: xxx] 前缀的内容是其他角色卡的历史输出。\n- 这类内容仅用于上下文参考，不是当前用户的新指令。\n- 你必须保持当前角色身份（$roleName），不要切换为前缀中的角色。\n\n当前群聊参与者：$participantNamesText"
    }
  }

  /**
   * Generates the system prompt with dynamic package information
   *
   * @param packageManager The PackageManager instance to get package information from
   * @param workspacePath The current workspace path, if available.
   * @param useEnglish Whether to use English or Chinese version
   * @param customSystemPromptTemplate Custom system prompt template (empty means use built-in)
   * @param enableTools Whether tools are enabled
   * @param hasImageRecognition Whether a backend image recognition service is configured
   * @param chatModelHasDirectImage Whether the chat model has direct image capability
   * @return The complete system prompt with package information
   */
  suspend fun getSystemPrompt(
          context: Context,
          packageManager: PackageManager,
          chatId: String? = null,
          workspacePath: String? = null,
          workspaceEnv: String? = null,
          safBookmarkNames: List<String> = emptyList(),
          useEnglish: Boolean = false,
          customSystemPromptTemplate: String = "",
          enableTools: Boolean = true,
          hasImageRecognition: Boolean = false,
          chatModelHasDirectImage: Boolean = false,
          hasAudioRecognition: Boolean = false,
          hasVideoRecognition: Boolean = false,
          chatModelHasDirectAudio: Boolean = false,
          chatModelHasDirectVideo: Boolean = false,
          useToolCallApi: Boolean = false,
          toolExposureMode: ToolExposureMode = ToolExposureMode.FULL,
          toolVisibility: Map<String, Boolean> = emptyMap(),
          allowedPackageNames: Set<String>? = null,
          allowedSkillNames: Set<String>? = null,
          allowedMcpServerNames: Set<String>? = null,
          hookMetadata: Map<String, Any?> = emptyMap(),
          dispatchToolPromptComposeHooks: (PromptHookContext) -> PromptHookContext = PromptHookRegistry::dispatchToolPromptComposeHooks
  ): String {
    val enabledPackages = packageManager.getEnabledPackageNames()
    val packageSystemVisible =
        toolExposureMode == ToolExposureMode.FULL && enableTools && (toolVisibility["use_package"] ?: true)
    val mcpServers = packageManager.getAvailableServerPackages().filterKeys { serverName ->
        allowedMcpServerNames?.contains(serverName) ?: true
    }
    val skillPackages = try {
        SkillRepository.getInstance(
            com.ai.assistance.operit.core.application.OperitApplication.instance.applicationContext
        ).getAiVisibleSkillPackages().filterKeys { skillName ->
            allowedSkillNames?.contains(skillName) ?: true
        }
    } catch (_: Exception) {
        emptyMap()
    }

    // Build the available packages section
    val packagesSection = StringBuilder()

    // Filter out imported packages that no longer exist in availablePackages
    val validEnabledPackages = enabledPackages.filter { packageName ->
        packageManager.getPackageTools(packageName) != null &&
            !packageManager.isToolPkgContainer(packageName) &&
            (allowedPackageNames?.contains(packageName) ?: true)
    }

    // Check if any packages (JS, MCP, or Skills) are available
    val hasPackages = packageSystemVisible &&
        (validEnabledPackages.isNotEmpty() || mcpServers.isNotEmpty() || skillPackages.isNotEmpty())

    if (hasPackages) {
      packagesSection.appendLine("Available packages:")

      // List imported JS packages (only those that still exist)
      for (packageName in validEnabledPackages) {
        val packageTools = packageManager.getPackageTools(packageName)
        if (packageTools != null) {
          val preferredLanguage = if (useEnglish) "en" else "zh"
          val resolvedDescription = try {
              packageTools.description.resolve(preferredLanguage)
          } catch (_: Exception) {
              packageTools.description.toString()
          }
          packagesSection.appendLine("- $packageName : $resolvedDescription")
        }
      }

      // List available MCP servers as regular packages
      for ((serverName, serverConfig) in mcpServers) {
        packagesSection.appendLine("- $serverName : ${serverConfig.description}")
      }

      // List available Skills as regular packages
      for ((skillName, skill) in skillPackages) {
        if (skill.description.isNotBlank()) {
          packagesSection.appendLine("- $skillName : ${skill.description}")
        } else {
          packagesSection.appendLine("- $skillName")
        }
      }
    } else if (packageSystemVisible) {
      packagesSection.appendLine("No packages are currently available.")
    }

    if (packageSystemVisible) {
      // Information about using packages
      packagesSection.appendLine()
      packagesSection.appendLine("To use a package:")
      packagesSection.appendLine(
              "<tool name=\"use_package\"><param name=\"package_name\">package_name_here</param></tool>"
      )
    }

    // Select appropriate template based on custom template or language preference
    val templateToUse = if (customSystemPromptTemplate.isNotEmpty()) {
        customSystemPromptTemplate
    } else {
        if (useEnglish) SYSTEM_PROMPT_TEMPLATE else SYSTEM_PROMPT_TEMPLATE_CN
    }
    val workspaceRuleFile =
        WorkspaceRuleFileReader.readWorkspaceRootRuleFile(
            context = context,
            workspacePath = workspacePath,
            workspaceEnv = workspaceEnv
        )

    // Generate workspace guidelines
    val workspaceGuidelines = getWorkspaceGuidelines(
        context = context,
        workspacePath = workspacePath,
        workspaceEnv = workspaceEnv,
        useEnglish = useEnglish,
        workspaceRuleFileName = workspaceRuleFile?.name,
        workspaceRuleFileContent = workspaceRuleFile?.content.orEmpty()
    )

    // Build prompt with appropriate sections
    var prompt = templateToUse
        .replace("ACTIVE_PACKAGES_SECTION", if (enableTools) packagesSection.toString() else "")
        .replace("WORKSPACE_GUIDELINES_SECTION", workspaceGuidelines)

    // Determine the available tools string based on tool visibility and recognition capabilities.
    // 当使用Tool Call API时，不在系统提示词中包含工具描述（工具已通过API的tools字段发送）
    val availableToolsEn = if (useToolCallApi || toolExposureMode == ToolExposureMode.CLI) "" else (
        getMemoryToolsEn(toolVisibility) +
            getAvailableToolsEn(
                chatId = chatId,
                hasImageRecognition = hasImageRecognition,
                chatModelHasDirectImage = chatModelHasDirectImage,
                hasAudioRecognition = hasAudioRecognition,
                hasVideoRecognition = hasVideoRecognition,
                chatModelHasDirectAudio = chatModelHasDirectAudio,
                chatModelHasDirectVideo = chatModelHasDirectVideo,
                safBookmarkNames = safBookmarkNames,
                toolVisibility = toolVisibility,
                hookMetadata = hookMetadata,
                dispatchToolPromptComposeHooks = dispatchToolPromptComposeHooks,
                context = context
            )
    )
    val availableToolsCn = if (useToolCallApi || toolExposureMode == ToolExposureMode.CLI) "" else (
        getMemoryToolsCn(toolVisibility) +
            getAvailableToolsCn(
                chatId = chatId,
                hasImageRecognition = hasImageRecognition,
                chatModelHasDirectImage = chatModelHasDirectImage,
                hasAudioRecognition = hasAudioRecognition,
                hasVideoRecognition = hasVideoRecognition,
                chatModelHasDirectAudio = chatModelHasDirectAudio,
                chatModelHasDirectVideo = chatModelHasDirectVideo,
                safBookmarkNames = safBookmarkNames,
                toolVisibility = toolVisibility,
                hookMetadata = hookMetadata,
                dispatchToolPromptComposeHooks = dispatchToolPromptComposeHooks,
                context = context
            )
    )

    // Handle tools disable/enable
    if (enableTools) {
        if (toolExposureMode == ToolExposureMode.CLI) {
            prompt = prompt
                .replace("TOOL_USAGE_GUIDELINES_SECTION", CliToolModeSupport.buildCliModePrompt(useEnglish))
                .replace("PACKAGE_SYSTEM_GUIDELINES_SECTION", "")
                .replace("ACTIVE_PACKAGES_SECTION", "")
                .replace("AVAILABLE_TOOLS_SECTION", "")
        } else if (useToolCallApi) {
            // 当使用Tool Call API时，移除XML格式说明和工具列表
            val packageGuidelines =
                if (useEnglish) {
                    PACKAGE_SYSTEM_GUIDELINES_TOOL_CALL_EN
                } else {
                    PACKAGE_SYSTEM_GUIDELINES_TOOL_CALL_CN
                }
            prompt = prompt
                .replace("TOOL_USAGE_GUIDELINES_SECTION", "")
                .replace("PACKAGE_SYSTEM_GUIDELINES_SECTION", if (packageSystemVisible) packageGuidelines else "")
                .replace("AVAILABLE_TOOLS_SECTION", "")
        } else {
            prompt = prompt
                .replace("TOOL_USAGE_GUIDELINES_SECTION", if (useEnglish) TOOL_USAGE_GUIDELINES_EN else TOOL_USAGE_GUIDELINES_CN)
                .replace(
                    "PACKAGE_SYSTEM_GUIDELINES_SECTION",
                    if (packageSystemVisible) {
                        if (useEnglish) PACKAGE_SYSTEM_GUIDELINES_EN else PACKAGE_SYSTEM_GUIDELINES_CN
                    } else {
                        ""
                    }
                )
                .replace("AVAILABLE_TOOLS_SECTION", if (useEnglish) availableToolsEn else availableToolsCn)
        }
    } else {
        // Remove all guidance sections when tools are disabled
        // Replace tool-related sections and remove behavior guidelines and workspace guidelines
        prompt = prompt
            .replace("TOOL_USAGE_GUIDELINES_SECTION", "")
            .replace("PACKAGE_SYSTEM_GUIDELINES_SECTION", "")
            .replace("AVAILABLE_TOOLS_SECTION", "")
            .replace(workspaceGuidelines, "")
    }


    // Clean up multiple consecutive blank lines (replace 3+ newlines with 2)
    prompt = prompt.replace(Regex("\n{3,}"), "\n\n")

    return prompt
  }
  
  /**
   * Generates workspace guidelines only when a workspace is actually bound.
   *
   * @param workspacePath The current path of the workspace. Null if not bound.
   * @param useEnglish Whether to use the English or Chinese version of the guidelines.
   * @return A string containing workspace guidelines, or an empty string when no workspace is bound.
   */
  private fun buildWorkspaceRuleFileSection(
      workspaceRuleFileName: String?,
      workspaceRuleFileContent: String,
      useEnglish: Boolean
  ): String {
      if (workspaceRuleFileName.isNullOrBlank() || workspaceRuleFileContent.isBlank()) {
          return ""
      }

      return if (useEnglish) {
          """
          WORKSPACE ROOT RULE FILE:
          - The workspace root contains `${workspaceRuleFileName}`. Treat the following content as project-specific workspace instructions.
          <workspace_rule_file name="${workspaceRuleFileName}">
          $workspaceRuleFileContent
          </workspace_rule_file>
          """.trimIndent()
      } else {
          """
          工作区根目录规则文件：
          - 工作区根目录存在 `${workspaceRuleFileName}`，请将以下内容视为当前项目的工作区专属指令。
          <workspace_rule_file name="${workspaceRuleFileName}">
          $workspaceRuleFileContent
          </workspace_rule_file>
          """.trimIndent()
      }
  }

  private fun getWorkspaceGuidelines(
      context: Context,
      workspacePath: String?,
      workspaceEnv: String?,
      useEnglish: Boolean,
      workspaceRuleFileName: String? = null,
      workspaceRuleFileContent: String = ""
  ): String {
      val envLabel = workspaceEnv?.trim().orEmpty().ifBlank { "android" }
      val shouldShowEnv = envLabel.isNotBlank()
      val externalStoragePath = Environment.getExternalStorageDirectory().absolutePath
      val appFilesPath = context.filesDir.absolutePath
      return if (!workspacePath.isNullOrBlank()) {
          val baseGuidelines =
              if (useEnglish) {
              """
              WORKSPACE GUIDELINES:
              - The current workspace root is `$workspacePath`${if (shouldShowEnv) " (environment=$envLabel)" else ""}.
              - Treat this exact path as the base path for all workspace file operations.
              - When using tools to read, write, search, list, move, or delete workspace files, do not use relative paths; always use absolute paths rooted at `$workspacePath`.
              ${if (shouldShowEnv) "- When operating on workspace files via tools, always pass `environment=\"$envLabel\"` together with the workspace path." else ""}
              - Relative paths are only for file contents or project-internal references, not for tool parameters.
              - Terminal mount note: common mounts include `$externalStoragePath -> /sdcard`, `$externalStoragePath -> $externalStoragePath`, and app sandbox `$appFilesPath -> same path`.
              - If the workspace is under mounted paths, execute workspace files directly in the Linux terminal environment; do not copy files before execution.
              - **Best Practice for Code Modifications**: Before modifying any file, use `grep_code` and `grep_context` to locate and understand relevant code with surrounding context. This ensures you understand the codebase structure before making changes.
              """.trimIndent()
          } else {
              """
              工作区指南：
              - 当前工作区根目录是 `$workspacePath`${if (shouldShowEnv) "（environment=$envLabel）" else ""}。
              - 所有工作区文件操作都要把这个精确路径当作根路径。
              - 使用工具读取、写入、搜索、列目录、移动或删除工作区文件时，不要使用相对路径，必须使用以 `$workspacePath` 为根的绝对路径。
              ${if (shouldShowEnv) "- 通过工具操作工作区文件时，每次都必须同时传入 `environment=\"$envLabel\"` 和对应的工作区路径。" else ""}
              - 相对路径只用于文件内容里的项目内部引用，不用于工具参数。
              - 终端挂载说明：常见挂载包括 `$externalStoragePath -> /sdcard`、`$externalStoragePath -> $externalStoragePath`，以及应用沙箱 `$appFilesPath -> 同路径`。
              - 若工作区位于已挂载路径中，直接在 Linux 终端环境中执行工作区文件；无需先复制再执行。
              - **代码修改最佳实践**：修改任何文件之前，建议组合使用 `grep_code` 与 `grep_context` 定位并理解相关代码及其上下文，避免在未理解项目结构时盲改。
              """.trimIndent()
          }
          val workspaceRuleFileSection = buildWorkspaceRuleFileSection(
              workspaceRuleFileName = workspaceRuleFileName,
              workspaceRuleFileContent = workspaceRuleFileContent,
              useEnglish = useEnglish
          )
          if (workspaceRuleFileSection.isBlank()) {
              baseGuidelines
          } else {
              "$baseGuidelines\n\n$workspaceRuleFileSection"
          }
      } else {
          ""
      }
  }

  /**
   * Generates the system prompt with dynamic package information and custom prompts
   *
   * @param packageManager The PackageManager instance to get package information from
   * @param workspacePath The current workspace path, if available.
   * @param customIntroPrompt Custom introduction prompt text
   * @param customSystemPromptTemplate Custom system prompt template (empty means use built-in)
   * @param enableTools Whether tools are enabled
   * @param hasImageRecognition Whether image recognition service is configured
   * @param chatModelHasDirectImage Whether the chat model has direct image capability
   * @return The complete system prompt with custom prompts and package information
   */
  suspend fun getSystemPromptWithCustomPrompts(
          context: Context,
          packageManager: PackageManager,
          chatId: String?,
          workspacePath: String?,
          workspaceEnv: String? = null,
          safBookmarkNames: List<String> = emptyList(),
          customIntroPrompt: String,
          useEnglish: Boolean = false,
          customSystemPromptTemplate: String = "",
          enableTools: Boolean = true,
          hasImageRecognition: Boolean = false,
          chatModelHasDirectImage: Boolean = false,
          hasAudioRecognition: Boolean = false,
          hasVideoRecognition: Boolean = false,
          chatModelHasDirectAudio: Boolean = false,
          chatModelHasDirectVideo: Boolean = false,
          useToolCallApi: Boolean = false,
          toolExposureMode: ToolExposureMode = ToolExposureMode.FULL,
          toolVisibility: Map<String, Boolean> = emptyMap(),
          allowedPackageNames: Set<String>? = null,
          allowedSkillNames: Set<String>? = null,
          allowedMcpServerNames: Set<String>? = null,
          enableGroupOrchestrationHint: Boolean = false,
          groupOrchestrationRoleName: String = "",
          groupParticipantNamesText: String = "",
          hookMetadata: Map<String, Any?> = emptyMap(),
          dispatchSystemPromptComposeHooks: (PromptHookContext) -> PromptHookContext = PromptHookRegistry::dispatchSystemPromptComposeHooks,
          dispatchToolPromptComposeHooks: (PromptHookContext) -> PromptHookContext = PromptHookRegistry::dispatchToolPromptComposeHooks
  ): String {
    val beforeContext =
        dispatchSystemPromptComposeHooks(
            PromptHookContext(
                stage = "before_compose_system_prompt",
                chatId = chatId,
                useEnglish = useEnglish,
                metadata =
                    mapOf(
                        "workspacePath" to workspacePath,
                        "workspaceEnv" to workspaceEnv,
                        "safBookmarkNames" to safBookmarkNames,
                        "customSystemPromptTemplate" to customSystemPromptTemplate,
                        "customIntroPrompt" to customIntroPrompt,
                        "enableTools" to enableTools,
                        "hasImageRecognition" to hasImageRecognition,
                        "chatModelHasDirectImage" to chatModelHasDirectImage,
                        "hasAudioRecognition" to hasAudioRecognition,
                        "hasVideoRecognition" to hasVideoRecognition,
                        "chatModelHasDirectAudio" to chatModelHasDirectAudio,
                        "chatModelHasDirectVideo" to chatModelHasDirectVideo,
                        "useToolCallApi" to useToolCallApi,
                        "toolExposureMode" to toolExposureMode.name,
                        "toolVisibility" to toolVisibility,
                        "allowedPackageNames" to allowedPackageNames.orEmpty().toList(),
                        "allowedSkillNames" to allowedSkillNames.orEmpty().toList(),
                        "allowedMcpServerNames" to allowedMcpServerNames.orEmpty().toList(),
                        "enableGroupOrchestrationHint" to enableGroupOrchestrationHint,
                        "groupOrchestrationRoleName" to groupOrchestrationRoleName,
                        "groupParticipantNamesText" to groupParticipantNamesText
                    ) + hookMetadata
            )
        )

    val basePrompt =
        beforeContext.systemPrompt ?: getSystemPrompt(
            context = context,
            packageManager = packageManager,
            chatId = chatId,
            workspacePath = workspacePath,
            workspaceEnv = workspaceEnv,
            safBookmarkNames = safBookmarkNames,
            useEnglish = useEnglish,
            customSystemPromptTemplate = customSystemPromptTemplate,
            enableTools = enableTools,
            hasImageRecognition = hasImageRecognition,
            chatModelHasDirectImage = chatModelHasDirectImage,
            hasAudioRecognition = hasAudioRecognition,
            hasVideoRecognition = hasVideoRecognition,
            chatModelHasDirectAudio = chatModelHasDirectAudio,
            chatModelHasDirectVideo = chatModelHasDirectVideo,
            useToolCallApi = useToolCallApi,
            toolExposureMode = toolExposureMode,
            toolVisibility = toolVisibility,
            allowedPackageNames = allowedPackageNames,
            allowedSkillNames = allowedSkillNames,
            allowedMcpServerNames = allowedMcpServerNames,
            hookMetadata = hookMetadata,
            dispatchToolPromptComposeHooks = dispatchToolPromptComposeHooks
        )

    var composedPrompt = applyCustomPrompts(basePrompt, customIntroPrompt)
    if (enableGroupOrchestrationHint) {
      val safeRoleName = groupOrchestrationRoleName.ifBlank { if (useEnglish) "assistant" else "助手" }
      composedPrompt += buildGroupOrchestrationHint(
          useEnglish = useEnglish,
          roleName = safeRoleName,
          participantNamesText = groupParticipantNamesText
      )
    }

    val composeContext =
        dispatchSystemPromptComposeHooks(
            beforeContext.copy(
                stage = "compose_system_prompt_sections",
                systemPrompt = composedPrompt
            )
        )
    val afterComposePrompt = composeContext.systemPrompt ?: composedPrompt
    val afterContext =
        dispatchSystemPromptComposeHooks(
            composeContext.copy(
                stage = "after_compose_system_prompt",
                systemPrompt = afterComposePrompt
            )
        )
    return afterContext.systemPrompt ?: afterComposePrompt
  }

  /** Convenience overload for default prompt generation. */
  suspend fun getSystemPrompt(context: Context, packageManager: PackageManager): String {
    return getSystemPrompt(
        context = context,
        packageManager = packageManager,
        chatId = null,
        workspacePath = null,
        workspaceEnv = null,
        safBookmarkNames = emptyList(),
        useEnglish = false,
        customSystemPromptTemplate = "",
        enableTools = true,
        hasImageRecognition = false,
        chatModelHasDirectImage = false,
        hasAudioRecognition = false,
        hasVideoRecognition = false,
        chatModelHasDirectAudio = false,
        chatModelHasDirectVideo = false,
        useToolCallApi = false
    )
  }
}
