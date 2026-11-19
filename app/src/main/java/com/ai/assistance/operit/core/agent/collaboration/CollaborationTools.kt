package com.ai.assistance.operit.core.agent.collaboration

import com.ai.assistance.operit.core.agent.AgentProfile
import com.ai.assistance.operit.data.model.SystemToolPromptCategory
import com.ai.assistance.operit.data.model.ToolParameterSchema
import com.ai.assistance.operit.data.model.ToolPrompt

/** The separate Codex v2 tool surface. The legacy task schema remains owned by v1. */
object CollaborationTools {
    /** The v1 dispatcher the v2 surface replaced: both hand work to another agent. */
    const val LEGACY_TASK_NAME = "task"

    val names = setOf(
        "spawn_agent", "send_message", "followup_task", "interrupt_agent", "list_agents", "wait_agent",
        "list_agent_models",
    )

    fun isCollaborationTool(name: String): Boolean = name in names || name == LEGACY_TASK_NAME

    /**
     * Travelling with the model catalogue rather than in the tool description: the catalogue exists
     * so a caller can honour a model the user asked for, which is not a licence to switch by itself.
     */
    fun listNote(chinese: Boolean): String = if (chinese) {
        "除非用户偏好或同意，不要调用其他模型。"
    } else {
        "Do not use a different model unless the user prefers or agrees to it."
    }

    fun category(chinese: Boolean, profiles: List<AgentProfile>): SystemToolPromptCategory {
        val limits = com.ai.assistance.operit.core.agent.AgentProfileRepository.instance.collaborationLimits.value
        fun text(cn: String, en: String) = if (chinese) cn else en
        fun parameter(name: String, cn: String, en: String, required: Boolean = false) =
            ToolParameterSchema(name = name, type = "string", description = text(cn, en), required = required)
        val target = parameter(
            "target", "目标代理 ID、当前代理下的相对任务名或 /root 开始的完整路径。",
            "Agent ID, task name relative to the caller, or canonical path beginning with /root.", true,
        )
        val message = parameter("message", "发送给代理的非空消息。", "Non-empty message for the agent.", true)
        return SystemToolPromptCategory(
            categoryName = "Subagent v2",
            tools = listOf(
                ToolPrompt(
                    name = "spawn_agent",
                    description = text(
                        "异步创建具名子代理；立即返回路径。当前每个根会话最多同时运行 ${limits.maxActive} 个子代理（整个子代理树共享，正在创建的也占名额，主代理不占名额）；最大深度 ${limits.maxDepth}（根代理深度 0）。达到并行上限时等待现有代理结束，再创建或唤醒空闲代理；给运行中代理发消息不占新名额。不要靠重复失败调用探测上限。仅为可独立执行的具体子任务创建代理。分工时说明依赖关系，必要时给出协作者的完整路径；收到会影响其他任务的发现时及时转发。完成消息会投递到父代理邮箱，但不会唤醒你；若本轮答复依赖子代理结果，必须在结束本轮前调用 wait_agent。无需重复发送终稿。",
                        "Spawn a named child asynchronously and return its path immediately. Current limit per root conversation: ${limits.maxActive} concurrent subagents shared across the entire tree, including pending creation; the root does not occupy a slot. Maximum depth: ${limits.maxDepth}, with root at depth 0. At capacity, wait for an existing agent to finish before spawning or waking an idle agent. Messaging a running agent takes no additional slot. Do not discover limits through repeated failed calls. Spawn only for a concrete independent subtask. Explain dependencies and, when relevant, provide collaborators' canonical paths. Relay findings that affect other tasks promptly. Completion is delivered to the parent's mailbox but does not wake you; if this turn's answer depends on a subagent's result, call wait_agent before ending the turn. Do not send a duplicate final report.",
                    ),
                    parametersStructured = listOf(
                        parameter("task_name", "任务名，只含小写字母、数字和下划线，不可为 root。", "Task name: lowercase letters, digits and underscores; root is reserved.", true),
                        message,
                        parameter("fork_turns", "继承父上下文：all（默认）、none 或正整数轮数。", "Parent context: all (default), none, or a positive number of turns."),
                        parameter("agent_type", "代理配置 ID：" + profiles.joinToString { it.id }, "Agent profile ID: " + profiles.joinToString { it.id }),
                        parameter("model", "可选。需要指定模型时先调用 list_agent_models，原样填写返回的 model（配置名称 / 模型名称，重名时带区分标记）。不填则沿用原有继承或子代理预设规则。", "Optional. To select a model, first call list_agent_models and copy its model value (configuration name / model name, disambiguated when needed). Omit to retain the existing inheritance or agent-profile selection."),
                        parameter("reasoning_effort", "可选思考强度：none、minimal、low、medium、high、xhigh、max、ultra；模型需支持。", "Optional reasoning effort: none, minimal, low, medium, high, xhigh, max, ultra; requires model support."),
                    ),
                ),
                ToolPrompt(
                    name = "list_agent_models",
                    description = text(
                        "仅在需要为 spawn_agent 指定模型时，按需读取已配置的模型清单。每个 model 可直接用于 spawn_agent.model；同一配置中的模型分别列出。无需指定模型时不必调用，也不要反复查询。",
                        "Read configured model choices only when selecting a model for spawn_agent. Copy a returned model value into spawn_agent.model; models within one configuration are listed separately. Do not call when no model override is needed or poll repeatedly.",
                    ),
                    parametersStructured = emptyList(),
                ),
                ToolPrompt(
                    name = "send_message",
                    description = text(
                        "向已有代理交流关键信息、纠正、阻塞或具体问题。需要回复代理的消息时，用此工具投递给其 Sender，不要只在自己的对话里作答。同级代理不会收到你自动回报父代理的终稿。普通文本或手写信封都不会投递，必须实际调用工具并确认 accepted。完整路径可用 list_agents 查询。遵守独立审计等隔离要求，避免无内容的状态消息和向父代理重复发送自动终稿。运行中在下一消息边界接收；空闲时只入队，需要其继续工作请用 followup_task（不能唤醒根代理）。",
                        "Share actionable findings, corrections, blockers or concrete questions with an existing agent. To reply to an agent message, deliver your answer to its Sender with this tool rather than answering only in your own conversation. Peers do not receive your automatic final report to your parent. Ordinary prose or a handwritten envelope does not deliver anything: call the tool and confirm accepted. Discover canonical paths with list_agents. Respect required independence; avoid empty updates or duplicating your automatic final report to your parent. Active agents receive input at the next boundary; idle agents only queue it. Use followup_task when an idle non-root agent needs to work.",
                    ),
                    parametersStructured = listOf(target, message),
                ),
                ToolPrompt(
                    name = "followup_task",
                    description = text(
                        "给现有代理继续分配任务。空闲时启动一轮，运行中在消息边界投递；不能指向根代理。",
                        "Give an existing agent another task. Start a turn if idle; deliver at an input boundary if running. Cannot target root.",
                    ),
                    parametersStructured = listOf(target, message),
                ),
                ToolPrompt(
                    name = "interrupt_agent",
                    description = text(
                        "中断目标代理当前轮并返回先前状态。代理身份和对话保留，可继续分配任务。不能中断根代理或自己。",
                        "Interrupt the target's current turn and return its previous status. Retain identity and conversation for follow-up tasks. Cannot target root or self.",
                    ),
                    parametersStructured = listOf(target),
                ),
                ToolPrompt(
                    name = "list_agents",
                    description = text("仅列出当前根会话中的代理身份及状态，不重复返回终稿；完整结果由邮箱投递。需要协作者的完整路径时查询，使用返回路径而不要猜测。不必为报进度反复轮询。", "List agent identities and status in the current root conversation without replaying final answers; full results arrive through the mailbox. Use returned canonical paths to contact collaborators instead of guessing; avoid repeated polling just for progress."),
                    parametersStructured = listOf(parameter("path_prefix", "可选完整或当前代理下的相对路径前缀，按路径段筛选。", "Optional canonical or caller-relative path prefix, matched by path segment.")),
                ),
                ToolPrompt(
                    name = "wait_agent",
                    description = text(
                        "等待其它代理的邮箱消息或完成通知。消息通过上下文投递；此工具只报告等待结果。若此刻没有其它代理在运行、你的邮箱也为空，会立即返回「无事可等」——不要靠反复调用来空转：若刚创建的子代理仍在创建中，等它创建完成后再调用一次，否则直接结束本轮。子代理的完成消息不会唤醒你：不调用此工具的时候，结果只留在邮箱里，要等到下一轮（通常是用户下一条消息）才会进入上下文。",
                        "Wait for mailbox messages or completion notices from other agents. Messages arrive through context; this tool only reports the wait outcome. When, at that moment, no other agent is running and your mailbox is empty it returns immediately as 'nothing to wait for': do not spin on it. If a child you just created is still being set up, call it once more after that finishes; otherwise end the turn. A child's completion message does not wake you: without this tool the result stays in the mailbox and only enters context on a later turn (usually the user's next message).",
                    ),
                    parametersStructured = listOf(parameter("timeout_ms",
                        "等待毫秒数，默认 ${limits.defaultWaitMs}，最少 ${limits.minWaitMs}，最多 ${limits.maxWaitMs}。",
                        "Timeout in milliseconds: default ${limits.defaultWaitMs}, minimum ${limits.minWaitMs}, maximum ${limits.maxWaitMs}.")),
                ),
            ),
        )
    }
}
