package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.enhance.ToolExecutionManager
import com.ai.assistance.operit.core.agent.SubagentCoordinator
import com.ai.assistance.operit.core.agent.SubagentExecutionException
import com.ai.assistance.operit.core.agent.SubagentResultExtractor
import com.ai.assistance.operit.core.agent.SubagentTaskRequest
import com.ai.assistance.operit.core.agent.SubagentTaskResult
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking

class TaskToolExecutor(context: Context) : ToolExecutor {
    private val appContext = context.applicationContext
    private val coordinator = SubagentCoordinator.getInstance(appContext)

    override fun invoke(tool: AITool): ToolResult {
        val runtime = ToolExecutionManager.currentToolRuntimeContext()
        return runBlocking(Dispatchers.IO) { execute(tool, runtime) }
    }

    override fun invokeAndStream(tool: AITool): Flow<ToolResult> = flow {
        val runtime = ToolExecutionManager.currentToolRuntimeContext()
        emit(execute(tool, runtime))
    }

    private suspend fun execute(
        tool: AITool,
        runtime: ToolExecutionManager.ToolRuntimeContext?,
    ): ToolResult {
        val title = tool.parameter("title")
        val prompt = tool.parameter("prompt")
        val subagentType = tool.parameter("subagent_type")
        val taskId = tool.parameter("task_id").ifBlank { null }
        val parentChatId = runtime?.callerChatId.orEmpty()
        val parentAgentName =
            runtime
                ?.callerName
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: runtime
                    ?.callerCardId
                    ?.takeIf(String::isNotBlank)
                    ?.let { cardId ->
                        runCatching {
                                CharacterCardManager.getInstance(appContext)
                                    .getCharacterCard(cardId)
                                    .name
                            }
                            .getOrNull()
                    }
                ?: "Operit"

        if (title.isBlank()) {
            return invalid(tool, "title is required")
        }
        if (prompt.isBlank()) {
            return invalid(tool, "prompt is required")
        }
        if (subagentType.isBlank()) {
            return invalid(tool, "subagent_type is required")
        }
        if (parentChatId.isBlank()) {
            return invalid(tool, "task requires a caller chat")
        }

        return try {
            require(
                com.ai.assistance.operit.core.agent.collaboration.CollaborationToolPolicy
                    .visibility(appContext, parentChatId, runtime?.isSubagent == true)["task"] == true
            ) { "The legacy task tool is internal-only. Use spawn_agent or followup_task in chats." }
            if (taskId != null) {
                val existing = coordinator.getRun(taskId)
                require(existing == null || !com.ai.assistance.operit.core.agent.collaboration.CollaborationCoordinator
                    .getInstance(appContext).isAgent(existing.childChatId)) { "Use followup_task to continue a v2 agent" }
            }
            com.ai.assistance.operit.core.agent.AgentProfileRepository.instance.requireSubagent(subagentType)
            val result =
                coordinator.runTask(
                    SubagentTaskRequest(
                        parentChatId = parentChatId,
                        parentToolCallId = runtime?.callId,
                        parentAgentName = parentAgentName,
                        title = title,
                        prompt = prompt,
                        subagentType = subagentType,
                        taskId = taskId,
                        parentModelConfigId = runtime?.parentModelConfigId,
                        parentModelIndex = runtime?.parentModelIndex,
                    )
                )
            when (result) {
                is SubagentTaskResult.Completed ->
                    ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                            StringResultData(
                                buildCompletedXml(
                                    taskId = result.run.id,
                                    title = result.run.title,
                                    result =
                                        SubagentResultExtractor.extract(
                                            result.outcome.finalAssistantText,
                                            appContext.getString(R.string.subagent_result_empty),
                                        ),
                                )
                            ),
                    )
                is SubagentTaskResult.AlreadyRunning ->
                    ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                            StringResultData(
                                buildRunningXml(
                                    taskId = result.run.id,
                                    title = result.run.title,
                                )
                            ),
                    )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: SubagentExecutionException) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error =
                    buildErrorXml(
                        taskId = error.taskId,
                        title = title,
                        error = error.message ?: "Subagent execution failed",
                    ),
            )
        } catch (error: Exception) {
            invalid(tool, error.message ?: error.javaClass.simpleName)
        }
    }

    private fun AITool.parameter(name: String): String =
        parameters.firstOrNull { it.name == name }?.value?.trim().orEmpty()

    private fun invalid(tool: AITool, error: String): ToolResult =
        ToolResult(
            toolName = tool.name,
            success = false,
            result = StringResultData(""),
            error = error,
        )

    private fun buildCompletedXml(
        taskId: String,
        title: String,
        result: String,
    ): String =
        """
        <task id="${escapeXml(taskId)}" state="completed">
          <summary>${escapeXml(title)}</summary>
          <task_result>${escapeXml(result)}</task_result>
        </task>
        """.trimIndent()

    private fun buildErrorXml(
        taskId: String,
        title: String,
        error: String,
    ): String =
        """
        <task id="${escapeXml(taskId)}" state="error">
          <summary>${escapeXml(title)}</summary>
          <task_error>${escapeXml(error)}</task_error>
        </task>
        """.trimIndent()

    private fun buildRunningXml(
        taskId: String,
        title: String,
    ): String =
        """
        <task id="${escapeXml(taskId)}" state="running">
          <summary>${escapeXml(title)}</summary>
        </task>
        """.trimIndent()

    private fun escapeXml(value: String): String =
        buildString(value.length) {
            value.forEach { char ->
                append(
                    when (char) {
                        '&' -> "&amp;"
                        '<' -> "&lt;"
                        '>' -> "&gt;"
                        '"' -> "&quot;"
                        '\'' -> "&apos;"
                        else -> char
                    }
                )
            }
        }
}
