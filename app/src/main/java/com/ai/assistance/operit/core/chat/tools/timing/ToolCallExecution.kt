package com.ai.assistance.operit.core.chat.tools.timing

import kotlinx.serialization.Serializable

/** 应用侧执行记录，不参与消息正文、提示词或工具结果协议。 */
@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.tooltiming.ToolCallExecution")
@Serializable
data class ToolCallExecution(
    val callIndex: Int,
    val clockId: String,
    val startedMs: Long? = null,
    val durationMs: Long? = null,
    val endedMs: Long? = null,
    val stopped: Boolean = false,
)
