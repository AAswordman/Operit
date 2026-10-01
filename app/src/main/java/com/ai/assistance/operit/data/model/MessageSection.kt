package com.ai.assistance.operit.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/** 一条消息里按顺序排列的完整片段。 */
@Serializable
sealed class MessageSection {
    abstract val type: String

    @Serializable
    @SerialName("text")
    data class Text(
        val content: String,
    ) : MessageSection() {
        override val type: String get() = TYPE

        companion object {
            const val TYPE = "text"
        }
    }

    @Serializable
    @SerialName("thinking")
    data class Thinking(
        val content: String,
    ) : MessageSection() {
        override val type: String get() = TYPE

        companion object {
            const val TYPE = "thinking"
        }
    }

    @Serializable
    @SerialName("tool_call")
    data class ToolCall(
        val name: String,
        val params: Map<String, String> = emptyMap(),
        val raw: String = "",
    ) : MessageSection() {
        override val type: String get() = TYPE

        companion object {
            const val TYPE = "tool_call"
        }
    }

    @Serializable
    @SerialName("tool_result")
    data class ToolResult(
        val name: String,
        val status: String = "",
        val content: String,
        val raw: String = "",
    ) : MessageSection() {
        override val type: String get() = TYPE

        companion object {
            const val TYPE = "tool_result"
        }
    }

    /** Responses 下一轮需要原样带回的数据，只保存，不展示。 */
    @Serializable
    @SerialName("protocol")
    data class Protocol(
        val provider: String,
        val payload: String,
        val raw: String = "",
    ) : MessageSection() {
        override val type: String get() = TYPE

        companion object {
            const val TYPE = "protocol"
        }
    }

    @Serializable
    @SerialName("search")
    data class Search(
        val raw: String,
    ) : MessageSection() {
        override val type: String get() = TYPE

        companion object {
            const val TYPE = "search"
        }
    }

    @Serializable
    @SerialName("status")
    data class Status(
        val raw: String,
    ) : MessageSection() {
        override val type: String get() = TYPE

        companion object {
            const val TYPE = "status"
        }
    }
}
