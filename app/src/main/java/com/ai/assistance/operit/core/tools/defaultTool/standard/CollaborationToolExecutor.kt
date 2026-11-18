package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.api.chat.enhance.ToolExecutionManager
import com.ai.assistance.operit.core.agent.collaboration.AgentFork
import com.ai.assistance.operit.core.agent.collaboration.CollaborationCoordinator
import com.ai.assistance.operit.core.agent.collaboration.CollaborationToolPolicy
import com.ai.assistance.operit.core.agent.collaboration.CollaborationModels
import com.ai.assistance.operit.core.agent.collaboration.CollaborationTools
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.util.LocaleUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

class CollaborationToolExecutor(context: Context) : ToolExecutor {
    private val appContext = context.applicationContext
    private val coordinator get() = CollaborationCoordinator.getInstance(appContext)

    override fun invoke(tool: AITool): ToolResult {
        val runtime = ToolExecutionManager.currentToolRuntimeContext()
        return runBlocking(Dispatchers.IO) { execute(tool, runtime) }
    }

    override fun invokeAndStream(tool: AITool): Flow<ToolResult> = flow {
        val runtime = ToolExecutionManager.currentToolRuntimeContext()
        emit(execute(tool, runtime))
    }

    private suspend fun execute(tool: AITool, runtime: ToolExecutionManager.ToolRuntimeContext?): ToolResult {
        fun optional(name: String) = tool.parameters.firstOrNull { it.name == name }?.value?.trim()?.takeIf { it.isNotEmpty() }
        fun required(name: String) = requireNotNull(optional(name)) { "$name is required" }
        return try {
            com.ai.assistance.operit.core.agent.collaboration.CollaborationArguments.validate(
                tool.name, tool.parameters.map { it.name },
            )
            val chatId = requireNotNull(runtime?.callerChatId?.takeIf { it.isNotBlank() }) { "Collaboration requires a caller chat" }
            require(CollaborationToolPolicy.visibility(appContext, chatId, runtime.isSubagent, runtime.isGroupOrchestration, isPluginSubtask = runtime.subtaskExecution != null)[tool.name] == true) {
                "This tool is not available in the caller's chat mode"
            }
            val result = when (tool.name) {
                "list_agent_models" -> {
                    val manager = ModelConfigManager(appContext)
                    buildJsonObject {
                        // The list exists so a caller can honour an explicit model request, so say
                        // so where it is read; seeing the choices is not itself a mandate to switch.
                        put("note", CollaborationTools.listNote(isChineseLanguage()))
                        put("models", buildJsonArray {
                            CollaborationModels.choices(manager.getAllConfigSummaries()).forEach { choice ->
                                add(buildJsonObject {
                                    put("model", choice.selector)
                                    put("provider", choice.provider)
                                })
                            }
                        })
                    }
                }
                "spawn_agent" -> {
                    val model = optional("model")
                    val effort = optional("reasoning_effort")
                    require(effort == null || effort in setOf("none", "minimal", "low", "medium", "high", "xhigh", "max", "ultra")) {
                        "Invalid reasoning_effort"
                    }
                    val selectedModel = if (model != null) {
                        val manager = ModelConfigManager(appContext)
                        CollaborationModels.resolve(model, manager.getAllConfigSummaries())
                    } else null
                    val agent = coordinator.spawn(
                        chatId, required("task_name"), required("message"), optional("agent_type") ?: "general",
                        AgentFork.parse(optional("fork_turns")), selectedModel?.configId ?: runtime.parentModelConfigId,
                        selectedModel?.modelIndex ?: runtime.parentModelIndex, runtime.callId,
                        inheritModel = model != null ||
                            (AgentFork.parse(optional("fork_turns")) == AgentFork.All && optional("agent_type") == null),
                        reasoningEffort = effort,
                        roleCardId = runtime.callerCardId,
                        includeProfilePrompt = AgentFork.parse(optional("fork_turns")) != AgentFork.All ||
                            optional("agent_type") != null,
                    )
                    buildJsonObject { put("task_name", agent.path); put("agent_id", agent.chatId) }
                }
                "send_message", "followup_task" -> {
                    coordinator.send(chatId, required("target"), required("message"), tool.name == "followup_task")
                    buildJsonObject { put("accepted", true) }
                }
                "interrupt_agent" -> buildJsonObject {
                    put("previous_status", coordinator.interrupt(chatId, required("target")).name.lowercase())
                }
                "list_agents" -> buildJsonObject {
                    val limits = com.ai.assistance.operit.core.agent.AgentProfileRepository.instance.collaborationLimits.value
                    val listing = coordinator.list(chatId, optional("path_prefix"))
                    put("total_subagents", listing.totalSubagents)
                    if (listing.totalSubagents > listing.agents.count { it.path != "/root" }) {
                        put("note", "Only the most recently active subagents are shown. Old idle agents are archived automatically and their names can be reused.")
                    }
                    put("max_active_subagents", limits.maxActive)
                    put("max_depth", limits.maxDepth)
                    put("agents", buildJsonArray {
                        listing.agents.forEach {
                            // A run the review stopped stores the English instruction written for
                            // the model, and polling status would otherwise replay it verbatim into
                            // the caller's context and the transcript. Report the outcome instead.
                            val reportedError = it.lastError
                            add(buildJsonObject {
                                put("task_name", it.path)
                                put("agent_name", it.path)
                                put("agent_status", when (it.status) {
                                    com.ai.assistance.operit.core.agent.collaboration.CollaborationStatus.COMPLETED ->
                                        // The mailbox delivers the final text. Polling status must
                                        // not replay that same output into the parent's context.
                                        buildJsonObject { put("completed", JsonNull) }
                                    com.ai.assistance.operit.core.agent.collaboration.CollaborationStatus.FAILED ->
                                        buildJsonObject { put("errored", reportedError.orEmpty()) }
                                    com.ai.assistance.operit.core.agent.collaboration.CollaborationStatus.IDLE ->
                                        JsonPrimitive("pending_init")
                                    else -> JsonPrimitive(it.status.name.lowercase())
                                })
                                put("agent_id", it.chatId)
                                put("status", it.status.name.lowercase())
                                reportedError?.let { error -> put("error", error) }
                            })
                        }
                    })
                }
                "wait_agent" -> {
                    val timeout = optional("timeout_ms")?.let {
                        requireNotNull(it.toLongOrNull()) { "timeout_ms must be an integer" }
                    } ?: com.ai.assistance.operit.core.agent.AgentProfileRepository.instance.collaborationLimits.value.defaultWaitMs
                    val waited = coordinator.wait(chatId, timeout)
                    buildJsonObject {
                        put("timed_out", waited.timedOut)
                        put("message", waited.message)
                    }
                }
                else -> error("Unknown collaboration tool")
            }
            ToolResult(toolName = tool.name, success = true, result = StringResultData(result.toString()))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = error.message ?: "Collaboration failed")
        }
    }

    /** Keeps result text in the same language the caller's tool prompts were rendered in. */
    private fun isChineseLanguage(): Boolean = LocaleUtils.getCurrentLanguage(appContext) != "en"
}
