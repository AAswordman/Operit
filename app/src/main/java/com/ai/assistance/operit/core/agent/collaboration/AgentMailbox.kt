package com.ai.assistance.operit.core.agent.collaboration

import kotlinx.serialization.Serializable

@Serializable
enum class AgentMessageKind { MESSAGE, NEW_TASK, FINAL_ANSWER, STATUS }

@Serializable
data class AgentMessage(
    val id: String,
    val sender: String,
    val recipient: String,
    val kind: AgentMessageKind,
    val text: String,
) {
    fun render(): String =
        "Message ID: $id\nMessage Type: $kind\nTask name: $recipient\n" +
            "Sender: $sender\nPayload:\n$text"
}

@Serializable
enum class CollaborationStatus { IDLE, RUNNING, COMPLETED, INTERRUPTED, FAILED }

@Serializable
data class CollaborationTurn(
    val kind: String, val content: String, val toolName: String? = null,
    val metadata: Map<String, CollaborationMetadata> = emptyMap(),
) {
    fun toPromptTurn() = com.ai.assistance.operit.core.chat.hooks.PromptTurn(
        com.ai.assistance.operit.core.chat.hooks.PromptTurnKind.valueOf(kind), content, toolName,
        metadata.mapValues { it.value.value() },
    )
}

@Serializable
data class CollaborationAgent(
    val rootChatId: String,
    val path: String,
    val chatId: String,
    val runId: String? = null,
    val parentPath: String? = null,
    val profileId: String = "general",
    val systemPrompt: String = "",
    val modelConfigId: String? = null,
    val modelIndex: Int? = null,
    val status: CollaborationStatus = CollaborationStatus.IDLE,
    val messages: List<AgentMessage> = emptyList(),
    val lastError: String? = null,
    val inheritedHistory: List<CollaborationTurn> = emptyList(),
    val reasoningEffort: String? = null,
    val historyCutoff: Long? = null,
    val roleCardId: String? = null,
    val finalAnswer: String? = null,
)

/**
 * Durable input remains queued until the model loop acknowledges its history handoff.
 * Runtime delivery reservations belong to the coordinator, never to this persisted state.
 */
@Serializable
data class CollaborationState(
    val version: Int = 1,
    val agents: List<CollaborationAgent> = emptyList(),
) {
    fun find(rootChatId: String, path: String): CollaborationAgent? =
        agents.firstOrNull { it.rootChatId == rootChatId && it.path == path }

    fun update(agent: CollaborationAgent): CollaborationState =
        copy(agents = agents.filterNot {
            it.rootChatId == agent.rootChatId && it.path == agent.path
        } + agent)

    fun enqueue(rootChatId: String, message: AgentMessage): CollaborationState {
        val receiver = requireNotNull(find(rootChatId, message.recipient)) {
            "Unknown agent: ${message.recipient}"
        }
        require(find(rootChatId, message.sender) != null) { "Unknown sender" }
        require(message.text.isNotBlank()) { "Empty message cannot be sent" }
        require(message.kind != AgentMessageKind.NEW_TASK || receiver.path != AgentPath.ROOT) {
            "Follow-up tasks cannot target the root agent"
        }
        require(receiver.messages.none { it.id == message.id }) { "Duplicate message ID" }
        return update(receiver.copy(messages = receiver.messages + message))
    }

    fun acknowledge(rootChatId: String, path: String, ids: Set<String>): CollaborationState {
        val agent = requireNotNull(find(rootChatId, path))
        return update(agent.copy(messages = agent.messages.filterNot { it.id in ids }))
    }
}
