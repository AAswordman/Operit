package com.ai.assistance.operit.data.dao

import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.MessageEntity
import com.ai.assistance.operit.data.model.MessageVariantEntity

data class MessageVariantCount(
    val messageTimestamp: Long,
    val variantCount: Int,
)

/** 运行态保留原有候选元数据，仅加载当前选中候选的正文。 */
data class RuntimeChatMessageSnapshot(
    val messages: List<MessageEntity>,
    val selectedVariants: List<MessageVariantEntity>,
    val variantCounts: List<MessageVariantCount>,
) {
    fun toChatMessages(): List<ChatMessage> {
        val selectedByTimestamp = selectedVariants.groupBy { it.messageTimestamp }
        val countsByTimestamp = variantCounts.associate { it.messageTimestamp to it.variantCount }
        return messages.map { entity ->
            val baseMessage = entity.toChatMessage()
            val variantCount = (countsByTimestamp[entity.timestamp] ?: 0) + 1
            if (entity.selectedVariantIndex == 0) {
                baseMessage.copy(selectedVariantIndex = 0, variantCount = variantCount)
            } else {
                // 缺失选中候选仍按原规则报错，避免悄悄改用基础消息而改变模型上下文。
                val selectedVariant = selectedByTimestamp[entity.timestamp].orEmpty()
                    .first { it.variantIndex == entity.selectedVariantIndex }
                selectedVariant.applyTo(baseMessage, variantCount)
            }
        }
    }
}
