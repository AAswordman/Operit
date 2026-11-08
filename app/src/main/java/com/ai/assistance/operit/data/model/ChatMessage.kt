package com.ai.assistance.operit.data.model

import com.ai.assistance.operit.core.chat.tools.timing.ToolTimingState
import android.os.Parcel
import android.os.Parcelable
import com.ai.assistance.operit.util.stream.Stream
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import com.ai.assistance.operit.core.chat.message.ChatMessageTimestampAllocator
import com.ai.assistance.operit.core.chat.protocol.MessageSectionCodec
import com.ai.assistance.operit.data.chat.archive.ChatMessageSerializer
import com.ai.assistance.operit.data.chat.serialization.MessageSectionStorage

@Serializable(with = ChatMessageSerializer::class)
data class ChatMessage(
        val sender: String, // "user" or "ai"
        var content: String = "",
        var sections: List<MessageSection> = emptyList(),
        val timestamp: Long = ChatMessageTimestampAllocator.next(),
        val roleName: String = "", // 角色名字字段
        val selectedVariantIndex: Int = 0, // 当前选中的回答版本，0 表示原始回答
        val variantCount: Int = 1, // 当前消息可切换的回答版本数量
        val provider: String = "", // 供应商
        val modelName: String = "", // 模型名称
        val inputTokens: Long = 0L, // 本轮输入 token
        val outputTokens: Long = 0L, // 本轮输出 token
        val cachedInputTokens: Long = 0L, // 本轮缓存命中的输入 token
        val sentAt: Long = 0L, // 本轮请求发送时间（时间戳）
        val outputDurationMs: Long = 0L, // 本轮输出耗时
        val waitDurationMs: Long = 0L, // 本轮等待首包耗时
        val completedAt: Long = 0L, // 本轮消息完成时间（时间戳）
        val displayMode: ChatMessageDisplayMode = ChatMessageDisplayMode.NORMAL,
        val isFavorite: Boolean = false,
        @Transient
        val isVariantPreview: Boolean = false,
        @Transient
        var contentStream: Stream<String>? =
                null, // 修改为Stream<String>类型，与EnhancedAIService.sendMessage返回类型匹配
        @Transient
        val toolTimingState: ToolTimingState? = null,
) : Parcelable {
    init {
        ChatMessageTimestampAllocator.observe(timestamp)
        if (sections.isEmpty() && content.isNotEmpty()) {
            sections = MessageSectionCodec.parse(content)
        }
    }

    /** 编辑和流式更新仍会修改 content，保存前必须消除旧片段快照。 */
    fun resolvedSections(content: String = this.content): List<MessageSection> {
        val current = if (MessageSectionCodec.render(sections) == content) sections
            else MessageSectionCodec.parse(content)
        val records = toolTimingState?.records?.value?.associateBy { it.callIndex }
        val savedCalls = sections.filterIsInstance<MessageSection.ToolCall>()
        var callIndex = 0
        return current.map { section ->
            if (section !is MessageSection.ToolCall) section else {
                val index = callIndex++
                val saved = savedCalls.getOrNull(index)?.takeIf { it.name == section.name && it.raw == section.raw }
                val execution = records?.get(index) ?: section.execution ?: saved?.execution
                if (execution == section.execution) section else section.copy(execution = execution)
            }
        }
    }

    /** 定稿保存执行快照，回答版本切换和编辑不再持有上一轮运行态。 */
    fun freezeToolTimings(content: String = this.content): ChatMessage = copy(
        content = content,
        sections = resolvedSections(content),
        toolTimingState = null,
    )

    fun displaySections(): List<MessageSection> =
        resolvedSections().filterNot { it is MessageSection.Protocol }

    fun displayContent(): String {
        return MessageSectionCodec.render(displaySections())
    }

    constructor(
            parcel: Parcel
    ) : this(
        sender = parcel.readString() ?: "",
        content = parcel.readString() ?: "",
        timestamp = parcel.readLong(),
        roleName = parcel.readString() ?: "",
        selectedVariantIndex = parcel.readInt(),
        variantCount = parcel.readInt(),
        provider = parcel.readString() ?: "",
        modelName = parcel.readString() ?: "",
        inputTokens = parcel.readLong(),
        outputTokens = parcel.readLong(),
        cachedInputTokens = parcel.readLong(),
        sentAt = parcel.readLong(),
        outputDurationMs = parcel.readLong(),
        waitDurationMs = parcel.readLong(),
        displayMode = readDisplayModeFromParcel(parcel),
        isFavorite = readBooleanFromParcel(parcel),
        completedAt = if (parcel.dataAvail() > 0) parcel.readLong() else 0L,
        sections = if (parcel.dataAvail() > 0) MessageSectionStorage.decode(parcel.readString().orEmpty()) else emptyList(),
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(sender)
        parcel.writeString(content)
        parcel.writeLong(timestamp)
        parcel.writeString(roleName)
        parcel.writeInt(selectedVariantIndex)
        parcel.writeInt(variantCount)
        parcel.writeString(provider)
        parcel.writeString(modelName)
        parcel.writeLong(inputTokens)
        parcel.writeLong(outputTokens)
        parcel.writeLong(cachedInputTokens)
        parcel.writeLong(sentAt)
        parcel.writeLong(outputDurationMs)
        parcel.writeLong(waitDurationMs)
        parcel.writeString(displayMode.name)
        parcel.writeInt(if (isFavorite) 1 else 0)
        parcel.writeLong(completedAt)
        parcel.writeString(MessageSectionStorage.encode(resolvedSections()))
        // 不需要序列化contentStream，因为它是暂时性的
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ChatMessage> {
        private fun readDisplayModeFromParcel(parcel: Parcel): ChatMessageDisplayMode {
            if (parcel.dataAvail() <= 0) {
                return ChatMessageDisplayMode.NORMAL
            }
            return runCatching {
                ChatMessageDisplayMode.valueOf(
                    parcel.readString() ?: ChatMessageDisplayMode.NORMAL.name
                )
            }.getOrDefault(ChatMessageDisplayMode.NORMAL)
        }

        private fun readBooleanFromParcel(parcel: Parcel): Boolean {
            return parcel.dataAvail() > 0 && parcel.readInt() != 0
        }

        override fun createFromParcel(parcel: Parcel): ChatMessage {
            return ChatMessage(parcel)
        }

        override fun newArray(size: Int): Array<ChatMessage?> {
            return arrayOfNulls(size)
        }
    }
}
