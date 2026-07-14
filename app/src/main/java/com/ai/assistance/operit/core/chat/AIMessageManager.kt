package com.ai.assistance.operit.core.chat

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.enhance.InputProcessor
import com.ai.assistance.operit.api.chat.llmprovider.MediaLinkParser
import com.ai.assistance.operit.api.chat.llmprovider.MediaLinkBuilder
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.chat.plugins.MessageProcessingController
import com.ai.assistance.operit.core.chat.plugins.MessageProcessingHookParams
import com.ai.assistance.operit.core.chat.plugins.MessageProcessingPluginRegistry
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.MemoryQueryResultData
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageTimestampAllocator
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.ui.features.chat.webview.workspace.process.WorkspaceAttachmentProcessor
import com.ai.assistance.operit.ui.features.chat.webview.workspace.process.WorkspaceChangeTracker
import com.ai.assistance.operit.util.ImagePoolManager
import com.ai.assistance.operit.util.MediaPoolManager
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.util.LocaleUtils
import com.ai.assistance.operit.util.stream.SharedStream
import com.ai.assistance.operit.util.stream.share
import com.ai.assistance.operit.util.stream.shareRevisable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

internal const val MESSAGE_PROCESS_TIMING_TAG = "MessageProcessTiming"

internal fun messageTimingNow(): Long = SystemClock.elapsedRealtime()

internal fun logMessageTiming(
    stage: String,
    startTimeMs: Long,
    details: String? = null
) {
    val elapsed = SystemClock.elapsedRealtime() - startTimeMs
    val suffix = details?.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""
    AppLogger.d(MESSAGE_PROCESS_TIMING_TAG, "$stage 耗时=${elapsed}ms$suffix")
}

/**
 * 单例对象，负责管理与 EnhancedAIService 的所有通信。
 *
 * 主要职责:
 * - 构建发送给AI的消息请求。
 * - 发送消息并处理流式响应。
 * - 请求生成对话总结。
 *
 * 设计原则:
 * - **无状态**: 本身不持有任何特定聊天的状态。所有需要的数据都通过方法参数传入。
 * - **职责明确**: 仅处理与AI服务的交互，UI更新和数据持久化由调用方负责。
 * - **封装逻辑**: 内部封装了与AI交互的策略，如是否需要总结、如何从历史中提取记忆等。
 */
@SuppressLint("StaticFieldLeak")
object AIMessageManager {
    private const val TAG = "AIMessageManager"
    // 聊天总结的消息数量阈值 - 移除硬编码，改用动态设置
    // private const val SUMMARY_CHUNK_SIZE = 4

    // 使用独立的协程作用域，确保AI操作的生命周期独立于任何特定的ViewModel
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private const val DEFAULT_CHAT_KEY = "__DEFAULT_CHAT__"

    private val activeEnhancedAiServiceByChatId = ConcurrentHashMap<String, EnhancedAIService>()
    private val activeMessageProcessingControllerByChatId = ConcurrentHashMap<String, MessageProcessingController>()

    @Volatile private var lastActiveChatKey: String = DEFAULT_CHAT_KEY

    private data class PackageUsageStat(
        val packageName: String,
        val count: Int
    )

    private lateinit var toolHandler: AIToolHandler
    private lateinit var packageManager: PackageManager
    private lateinit var context: Context
    private lateinit var apiPreferences: ApiPreferences

    fun initialize(context: Context) {
        this.context = context
        toolHandler = AIToolHandler.getInstance(context)
        packageManager = PackageManager.getInstance(context, toolHandler)
        apiPreferences = ApiPreferences.getInstance(context)
    }

    /**
     * 构建用户消息的完整内容，包括附件和记忆标签。
     *
     * @param messageText 用户输入的原始文本。
     * @param attachments 附件列表。
     * @param workspacePath 工作区路径。
     * @param workspaceEnv 工作区环境标签。
     * @param replyToMessage 回复消息。
     * @param enableDirectImageProcessing 是否将图片附件转换为link标签（用于直接图片处理）。
     * @param enableDirectAudioProcessing 是否将音频附件转换为link标签（用于直接音频处理）。
     * @param enableDirectVideoProcessing 是否将视频附件转换为link标签（用于直接视频处理）。
     * @return 格式化后的完整消息字符串。
     */
    suspend fun buildUserMessageContent(
        context: Context,
        messageText: String,
        proxySenderName: String? = null,
        attachments: List<AttachmentInfo>,
        workspacePath: String? = null,
        workspaceEnv: String? = null,
        replyToMessage: ChatMessage? = null,
        enableDirectImageProcessing: Boolean = false,
        enableDirectAudioProcessing: Boolean = false,
        enableDirectVideoProcessing: Boolean = false,
        chatId: String? = null,
        roleCardId: String? = null
    ): String {
        val totalStartTime = messageTimingNow()
        val promptInputStartTime = messageTimingNow()
        val processedMessageText = InputProcessor.processUserInput(context, messageText, chatId, roleCardId)
        logMessageTiming(
            stage = "buildUserMessageContent.processUserInput",
            startTimeMs = promptInputStartTime,
            details = "originalLength=${messageText.length}, processedLength=${processedMessageText.length}"
        )
        val proxySenderTag =
            if (!proxySenderName.isNullOrBlank() &&
                !processedMessageText.contains("<proxy_sender", ignoreCase = true)
            ) {
                val safeProxySenderName = proxySenderName.replace("\"", "'")
                "<proxy_sender name=\"$safeProxySenderName\"/>"
            } else {
                ""
            }

        // 1. 构建回复标签（如果有回复消息）
        val replyTagStartTime = messageTimingNow()
        val replyTag = replyToMessage?.let { message ->
            val cleanContent = message.content
                .replace(Regex("<[^>]*>"), "") // 移除XML标签
                .trim()
                .let { if (it.length > 100) it.take(100) + "..." else it }

            val roleName = message.roleName ?: if (message.sender == "ai") "AI" else context.getString(R.string.ai_message_user)
            val instruction = context.getString(R.string.ai_message_replying_to_previous)
            "<reply_to sender=\"${roleName}\" timestamp=\"${message.timestamp}\">${instruction}\"${cleanContent}\"</reply_to>"
        } ?: ""
        logMessageTiming(
            stage = "buildUserMessageContent.replyTag",
            startTimeMs = replyTagStartTime,
            details = "hasReply=${replyToMessage != null}, length=${replyTag.length}"
        )

        // 2. 构建工作区标签
        val workspaceTagStartTime = messageTimingNow()
        val normalizedWorkspacePath = workspacePath?.trim().orEmpty()
        val workspaceTag =
            if (normalizedWorkspacePath.isNotEmpty() &&
                !processedMessageText.contains("<workspace_attachment", ignoreCase = true)
            ) {
                val workspaceChanges =
                    WorkspaceChangeTracker.getInstance(context)
                        .consumeChanges(chatId, normalizedWorkspacePath, workspaceEnv)
                "<workspace_attachment>" +
                    WorkspaceAttachmentProcessor.generateWorkspaceAttachment(
                        context,
                        workspaceEnv,
                        workspaceChanges
                    ) +
                    "</workspace_attachment>"
            } else {
                ""
            }
        logMessageTiming(
            stage = "buildUserMessageContent.workspaceTag",
            startTimeMs = workspaceTagStartTime,
            details = "hasWorkspace=${normalizedWorkspacePath.isNotEmpty()}, length=${workspaceTag.length}"
        )

        // 3. 构建附件标签
        val attachmentTagsStartTime = messageTimingNow()
        val attachmentTags = if (attachments.isNotEmpty()) {
            attachments.joinToString(" ") { attachment ->
                // 如果启用直接图片处理且附件是图片，转换为link标签
                if (enableDirectImageProcessing && attachment.mimeType.startsWith("image/", ignoreCase = true)) {
                    try {
                        val imageId = ImagePoolManager.addImage(attachment.filePath)
                        val attributes = buildString {
                            append("id=\"${attachment.filePath}\" ")
                            append("filename=\"${attachment.fileName}\" ")
                            append("type=\"${attachment.mimeType}\"")
                            if (attachment.fileSize > 0) {
                                append(" size=\"${attachment.fileSize}\"")
                            }
                        }
                        val attachedContent = buildString {
                            append(context.getString(R.string.ai_message_image_attached_multimodal_notice))
                            if (attachment.content.isNotBlank()) {
                                append("\n")
                                append(attachment.content)
                            }
                        }
                        "${MediaLinkBuilder.image(context, imageId)} <attachment $attributes>$attachedContent</attachment>"
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "添加图片到池失败: ${attachment.filePath}", e)
                        // 失败时回退到普通附件格式
                        val attributes = buildString {
                            append("id=\"${attachment.filePath}\" ")
                            append("filename=\"${attachment.fileName}\" ")
                            append("type=\"${attachment.mimeType}\"")
                            if (attachment.fileSize > 0) {
                                append(" size=\"${attachment.fileSize}\"")
                            }
                        }
                        "<attachment $attributes>${attachment.content}</attachment>"
                    }
                } else if (enableDirectAudioProcessing && attachment.mimeType.startsWith("audio/", ignoreCase = true)) {
                    try {
                        val audioId = MediaPoolManager.addMedia(attachment.filePath, attachment.mimeType)
                        if (audioId == "error") {
                            throw IllegalStateException("addMedia returned error")
                        }
                        MediaLinkBuilder.audio(context, audioId)
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "添加音频到池失败: ${attachment.filePath}", e)
                        val attributes = buildString {
                            append("id=\"${attachment.filePath}\" ")
                            append("filename=\"${attachment.fileName}\" ")
                            append("type=\"${attachment.mimeType}\"")
                            if (attachment.fileSize > 0) {
                                append(" size=\"${attachment.fileSize}\"")
                            }
                        }
                        "<attachment $attributes>${attachment.content}</attachment>"
                    }
                } else if (enableDirectVideoProcessing && attachment.mimeType.startsWith("video/", ignoreCase = true)) {
                    try {
                        val videoId = MediaPoolManager.addMedia(attachment.filePath, attachment.mimeType)
                        if (videoId == "error") {
                            throw IllegalStateException("addMedia returned error")
                        }
                        MediaLinkBuilder.video(context, videoId)
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "添加视频到池失败: ${attachment.filePath}", e)
                        val attributes = buildString {
                            append("id=\"${attachment.filePath}\" ")
                            append("filename=\"${attachment.fileName}\" ")
                            append("type=\"${attachment.mimeType}\"")
                            if (attachment.fileSize > 0) {
                                append(" size=\"${attachment.fileSize}\"")
                            }
                        }
                        "<attachment $attributes>${attachment.content}</attachment>"
                    }
                } else {
                    // 非图片或未启用直接图片处理，使用普通附件格式
                    val attributes = buildString {
                        append("id=\"${attachment.filePath}\" ")
                        append("filename=\"${attachment.fileName}\" ")
                        append("type=\"${attachment.mimeType}\"")
                        if (attachment.fileSize > 0) {
                            append(" size=\"${attachment.fileSize}\"")
                        }
                    }
                    "<attachment $attributes>${attachment.content}</attachment>"
                }
            }
        } else ""
        logMessageTiming(
            stage = "buildUserMessageContent.attachmentTags",
            startTimeMs = attachmentTagsStartTime,
            details = "attachments=${attachments.size}, length=${attachmentTags.length}, directImage=$enableDirectImageProcessing, directAudio=$enableDirectAudioProcessing, directVideo=$enableDirectVideoProcessing"
        )

        // 4. 组合最终消息
        val finalMessageContent = listOf(proxySenderTag, processedMessageText, attachmentTags, workspaceTag, replyTag)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        logMessageTiming(
            stage = "buildUserMessageContent.total",
            startTimeMs = totalStartTime,
            details = "messageLength=${processedMessageText.length}, finalLength=${finalMessageContent.length}, attachments=${attachments.size}"
        )
        return finalMessageContent
    }

    /**
     * 发送消息给AI服务。
     *
     * @param enhancedAiService AI服务实例。
     * @param chatId 聊天ID。
     * @param messageContent 已经构建好的完整消息内容。
     * @param chatHistory 完整的聊天历史记录。
     * @param workspacePath 当前工作区路径。
     * @param promptFunctionType 提示功能类型。
     * @param enableThinking 是否启用思考过程。
     * @param enableMemoryAutoUpdate 是否允许在当前轮回复结束后自动更新记忆。
     * @param maxTokens 最大token数量。
     * @param tokenUsageThreshold token使用阈值。
     * @param onNonFatalError 非致命错误回调。
     * @param onTokenLimitExceeded token限制超出回调。
     * @param characterName 角色名称，用于通知。
     * @param avatarUri 角色头像URI，用于通知。
     * @param roleCardId 角色卡片ID。
     * @return 包含AI响应流的ChatMessage对象。
     */
    suspend fun sendMessage(
        enhancedAiService: EnhancedAIService,
        chatId: String? = null,
        messageContent: String,
        chatHistory: List<ChatMessage>,
        workspacePath: String?,
        promptFunctionType: PromptFunctionType,
        enableThinking: Boolean,
        enableMemoryAutoUpdate: Boolean,
        maxTokens: Int,
        tokenUsageThreshold: Double,
        onNonFatalError: suspend (error: String) -> Unit,
        onTokenLimitExceeded: (suspend () -> Unit)? = null,
        characterName: String? = null,
        avatarUri: String? = null,
        roleCardId: String,
        currentRoleName: String? = null,
        splitHistoryByRole: Boolean = false,
        groupOrchestrationMode: Boolean = false,
        groupParticipantNamesText: String? = null,
        proxySenderName: String? = null,
        onToolInvocation: (suspend (String) -> Unit)? = null,
        notifyReplyOverride: Boolean? = null,
        chatModelConfigIdOverride: String? = null,
        chatModelIndexOverride: Int? = null,
        memorySpaceIdOverride: String? = null,
        disableWarning: Boolean = false
    ): SharedStream<String> {
        val totalStartTime = messageTimingNow()
        val chatKey = chatId ?: DEFAULT_CHAT_KEY
        lastActiveChatKey = chatKey
        activeEnhancedAiServiceByChatId[chatKey] = enhancedAiService

        val buildMemoryStartTime = messageTimingNow()
        val memory = getMemoryFromMessages(
            messages = chatHistory,
            splitByRole = splitHistoryByRole,
            targetRoleName = currentRoleName,
            groupOrchestrationMode = groupOrchestrationMode
        )
        logMessageTiming(
            stage = "sendMessage.buildMemory",
            startTimeMs = buildMemoryStartTime,
            details = "chatKey=$chatKey, source=${chatHistory.size}, result=${memory.size}, splitByRole=$splitHistoryByRole, groupOrchestration=$groupOrchestrationMode"
        )
        if (splitHistoryByRole && !currentRoleName.isNullOrBlank()) {
            val assistantCount = memory.count { it.kind == PromptTurnKind.ASSISTANT }
            val userCount = memory.count { it.kind == PromptTurnKind.USER }
            AppLogger.d(
                TAG,
                "按角色拆解历史: role=$currentRoleName, assistant=$assistantCount, user=$userCount, total=${memory.size}"
            )
        }

        return withContext(Dispatchers.IO) {
            val limitHistoryStartTime = messageTimingNow()
            val maxImageHistoryUserTurns = apiPreferences.maxImageHistoryUserTurnsFlow.first()
            val maxMediaHistoryUserTurns = apiPreferences.maxMediaHistoryUserTurnsFlow.first()

            val memoryAfterImageLimit = limitImageLinksInChatHistory(memory, maxImageHistoryUserTurns)
            val memoryForRequest = limitMediaLinksInChatHistory(memoryAfterImageLimit, maxMediaHistoryUserTurns)
            val beforeImageLinkCount = memory.count { MediaLinkParser.hasImageLinks(it.content) }
            val afterImageLinkCount = memoryForRequest.count { MediaLinkParser.hasImageLinks(it.content) }
            if (beforeImageLinkCount != afterImageLinkCount) {
                AppLogger.d(
                    TAG,
                    "历史图片裁剪生效: limit=$maxImageHistoryUserTurns, before=$beforeImageLinkCount, after=$afterImageLinkCount"
                )
            }

            val beforeMediaLinkCount = memory.count { MediaLinkParser.hasMediaLinks(it.content) }
            val afterMediaLinkCount = memoryForRequest.count { MediaLinkParser.hasMediaLinks(it.content) }
            if (beforeMediaLinkCount != afterMediaLinkCount) {
                AppLogger.d(
                    TAG,
                    "历史音视频裁剪生效: limit=$maxMediaHistoryUserTurns, before=$beforeMediaLinkCount, after=$afterMediaLinkCount"
                )
            }
            logMessageTiming(
                stage = "sendMessage.limitHistory",
                startTimeMs = limitHistoryStartTime,
                details = "chatKey=$chatKey, before=${memory.size}, after=${memoryForRequest.size}, imageLimit=$maxImageHistoryUserTurns, mediaLimit=$maxMediaHistoryUserTurns"
            )

            val matchPluginStartTime = messageTimingNow()
            val pluginExecution = MessageProcessingPluginRegistry.createExecutionIfMatched(
                params = MessageProcessingHookParams(
                    context = context,
                    enhancedAIService = enhancedAiService,
                    chatId = chatId,
                    messageContent = messageContent,
                    chatHistory = memoryForRequest,
                    workspacePath = workspacePath,
                    maxTokens = maxTokens,
                    tokenUsageThreshold = tokenUsageThreshold,
                    onNonFatalError = onNonFatalError
                )
            )
            logMessageTiming(
                stage = "sendMessage.matchPlugin",
                startTimeMs = matchPluginStartTime,
                details = "chatKey=$chatKey, matched=${pluginExecution != null}"
            )
            if (pluginExecution != null) {
                activeMessageProcessingControllerByChatId[chatKey] = pluginExecution.controller
                AppLogger.d(TAG, "消息处理插件已接管消息处理")
                val pluginStream = pluginExecution.stream.share(
                    scope = scope,
                    replay = Int.MAX_VALUE,
                    onComplete = {
                        activeMessageProcessingControllerByChatId.remove(chatKey)
                        activeEnhancedAiServiceByChatId.remove(chatKey)
                    }
                )
                logMessageTiming(
                    stage = "sendMessage.total",
                    startTimeMs = totalStartTime,
                    details = "chatKey=$chatKey, mode=plugin, history=${memoryForRequest.size}"
                )
                return@withContext pluginStream
            } else {
                activeMessageProcessingControllerByChatId.remove(chatKey)
                AppLogger.d(TAG, "消息处理插件未接管，使用普通模式")
            }

            // 获取流式输出设置
            val readStreamSettingStartTime = messageTimingNow()
            val disableStreamOutput = apiPreferences.disableStreamOutputFlow.first()
            val enableStream = !disableStreamOutput
            logMessageTiming(
                stage = "sendMessage.readStreamSetting",
                startTimeMs = readStreamSettingStartTime,
                details = "chatKey=$chatKey, enableStream=$enableStream"
            )

            // 使用普通模式
            val prepareRequestStartTime = messageTimingNow()
            val responseStream = enhancedAiService.sendMessage(
                EnhancedAIService.SendMessageOptions(
                    message = messageContent,
                    chatId = chatId,
                    chatHistory = memoryForRequest,
                    workspacePath = workspacePath,
                    promptFunctionType = promptFunctionType,
                    enableThinking = enableThinking,
                    enableMemoryAutoUpdate = enableMemoryAutoUpdate,
                    maxTokens = maxTokens,
                    tokenUsageThreshold = tokenUsageThreshold,
                    onNonFatalError = onNonFatalError,
                    onTokenLimitExceeded = onTokenLimitExceeded,
                    characterName = characterName,
                    avatarUri = avatarUri,
                    roleCardId = roleCardId,
                    enableGroupOrchestrationHint = groupOrchestrationMode,
                    groupParticipantNamesText = groupParticipantNamesText,
                    proxySenderName = proxySenderName,
                    onToolInvocation = onToolInvocation,
                    notifyReplyOverride = notifyReplyOverride,
                    chatModelConfigIdOverride = chatModelConfigIdOverride,
                    chatModelIndexOverride = chatModelIndexOverride,
                    memorySpaceIdOverride = memorySpaceIdOverride,
                    stream = enableStream,
                    disableWarning = disableWarning
                )
            ).shareRevisable(
                scope = scope,
                replay = Int.MAX_VALUE,
                onComplete = {
                    activeMessageProcessingControllerByChatId.remove(chatKey)
                    activeEnhancedAiServiceByChatId.remove(chatKey)
                }
            )
            logMessageTiming(
                stage = "sendMessage.prepareRequest",
                startTimeMs = prepareRequestStartTime,
                details = "chatKey=$chatKey, history=${memoryForRequest.size}, stream=$enableStream, prompt=$promptFunctionType"
            )
            logMessageTiming(
                stage = "sendMessage.total",
                startTimeMs = totalStartTime,
                details = "chatKey=$chatKey, mode=default, history=${memoryForRequest.size}"
            )
            responseStream
        }
    }

    suspend fun calculateStableContextWindow(
        enhancedAiService: EnhancedAIService,
        chatId: String? = null,
        messageContent: String = "",
        chatHistory: List<ChatMessage>,
        workspacePath: String? = null,
        workspaceEnv: String? = null,
        promptFunctionType: PromptFunctionType = PromptFunctionType.CHAT,
        roleCardId: String? = null,
        currentRoleName: String? = null,
        splitHistoryByRole: Boolean = true,
        groupOrchestrationMode: Boolean = false,
        groupParticipantNamesText: String? = null,
        proxySenderName: String? = null,
        chatModelConfigIdOverride: String? = null,
        chatModelIndexOverride: Int? = null,
        memorySpaceIdOverride: String? = null,
        publishEstimate: Boolean = true
    ): Int {
        val memory =
            getMemoryFromMessages(
                messages = chatHistory,
                splitByRole = splitHistoryByRole,
                targetRoleName = currentRoleName,
                groupOrchestrationMode = groupOrchestrationMode
            )
        val maxImageHistoryUserTurns = apiPreferences.maxImageHistoryUserTurnsFlow.first()
        val maxMediaHistoryUserTurns = apiPreferences.maxMediaHistoryUserTurnsFlow.first()
        val memoryAfterImageLimit = limitImageLinksInChatHistory(memory, maxImageHistoryUserTurns)
        val memoryForRequest =
            limitMediaLinksInChatHistory(memoryAfterImageLimit, maxMediaHistoryUserTurns)

        val windowSize =
            enhancedAiService.estimateRequestWindowFromMemory(
                message = messageContent,
                chatHistory = memoryForRequest,
                chatId = chatId,
                workspacePath = workspacePath,
                workspaceEnv = workspaceEnv,
                promptFunctionType = promptFunctionType,
                roleCardId = roleCardId,
                enableGroupOrchestrationHint = groupOrchestrationMode,
                groupParticipantNamesText = groupParticipantNamesText,
                proxySenderName = proxySenderName,
                chatModelConfigIdOverride = chatModelConfigIdOverride,
                chatModelIndexOverride = chatModelIndexOverride,
                memorySpaceIdOverride = memorySpaceIdOverride,
                publishEstimate = publishEstimate
            )
        return windowSize
    }

    private fun limitMediaLinksInChatHistory(
        history: List<PromptTurn>,
        keepLastUserMediaTurns: Int
    ): List<PromptTurn> {
        val limit = keepLastUserMediaTurns.coerceAtLeast(0)
        val totalUserTurns = history.count { it.kind == PromptTurnKind.USER }
        val keepFromTurn = (totalUserTurns - limit).coerceAtLeast(0)

        var currentUserTurnIndex = -1
        return history.map { turn ->
            if (turn.kind == PromptTurnKind.USER) {
                currentUserTurnIndex += 1
            }

            val shouldKeepMedia = limit > 0 && currentUserTurnIndex >= keepFromTurn
            if (!shouldKeepMedia && MediaLinkParser.hasMediaLinks(turn.content)) {
                val removed = MediaLinkParser.removeMediaLinks(turn.content).trim()
                turn.copy(content = removed.ifBlank { context.getString(R.string.ai_message_media_omitted) })
            } else {
                turn
            }
        }
    }

    private fun limitImageLinksInChatHistory(
        history: List<PromptTurn>,
        keepLastUserImageTurns: Int
    ): List<PromptTurn> {
        val limit = keepLastUserImageTurns.coerceAtLeast(0)
        val totalUserTurns = history.count { it.kind == PromptTurnKind.USER }
        val keepFromTurn = (totalUserTurns - limit).coerceAtLeast(0)

        var currentUserTurnIndex = -1
        return history.map { turn ->
            if (turn.kind == PromptTurnKind.USER) {
                currentUserTurnIndex += 1
            }

            val shouldKeepImages = limit > 0 && currentUserTurnIndex >= keepFromTurn
            if (!shouldKeepImages && MediaLinkParser.hasImageLinks(turn.content)) {
                val removed = MediaLinkParser.removeImageLinks(turn.content).trim()
                turn.copy(content = removed.ifBlank { context.getString(R.string.ai_message_image_omitted) })
            } else {
                turn
            }
        }
    }

    /**
     * 取消当前正在进行的AI操作。
     * 这会同时尝试取消插件接管执行（如果正在进行）和底层的AI流。
     */
    fun cancelCurrentOperation() {
        cancelOperation(lastActiveChatKey)
    }

    fun cancelOperation(chatId: String) {
        val chatKey = chatId.ifBlank { DEFAULT_CHAT_KEY }
        AppLogger.d(TAG, "请求取消AI操作: chatId=$chatKey")

        activeMessageProcessingControllerByChatId.remove(chatKey)?.let {
            AppLogger.d(TAG, "正在取消消息处理插件执行: chatId=$chatKey")
            it.cancel()
        }

        activeEnhancedAiServiceByChatId.remove(chatKey)?.let {
            AppLogger.d(TAG, "正在取消 EnhancedAIService 对话: chatId=$chatKey")
            it.cancelConversation()
        }

        if (chatId.isNotBlank()) {
            runCatching {
                packageManager.cancelToolPkgExecutionsForChat(chatKey, "User cancelled")
            }.onFailure { error ->
                AppLogger.e(TAG, "取消ToolPkg JS执行失败: chatId=$chatKey", error)
            }
        }

        AppLogger.d(TAG, "AI操作取消请求已发送: chatId=$chatKey")
    }

    fun cancelAllOperations() {
        AppLogger.d(TAG, "请求取消所有AI操作...")
        val keys = (activeEnhancedAiServiceByChatId.keys + activeMessageProcessingControllerByChatId.keys).toSet()
        keys.forEach { cancelOperation(it) }
        AppLogger.d(TAG, "所有AI操作取消请求已发送。")
    }

    /**
     * 请求AI服务生成对话总结。
     *
     * @param enhancedAiService AI服务实例。
     * @param messages 需要总结的消息列表。
     * @param autoContinue 是否为自动续写模式，如果是则在总结消息尾部添加续写提示。
     * @return 包含总结内容的ChatMessage对象，如果无需总结或总结失败则返回null。
     */
    suspend fun summarizeMemory(
        enhancedAiService: EnhancedAIService,
        messages: List<ChatMessage>,
        autoContinue: Boolean = false,
        isGroupChat: Boolean = false,
        summaryCustomRules: String? = null
    ): ChatMessage? {
        val lastSummaryIndex = messages.indexOfLast { it.sender == "summary" }
        val previousSummary = if (lastSummaryIndex != -1) messages[lastSummaryIndex].content.trim() else null

        val messagesToSummarize = when {
            lastSummaryIndex == -1 -> messages.filter { it.sender == "user" || it.sender == "ai" }
            else -> messages.subList(lastSummaryIndex + 1, messages.size)
                .filter { it.sender == "user" || it.sender == "ai" }
        }

        if (messagesToSummarize.isEmpty()) {
            AppLogger.d(TAG, "没有新消息需要总结")
            return null
        }

        val memoryTagRegex = Regex("<memory>.*?</memory>", RegexOption.DOT_MATCHES_ALL)
        val conversationReviewEntries = mutableListOf<Pair<String, String>>()
        fun normalizeForReview(text: String): String {
            return text
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        fun condenseHeadTail(text: String, headChars: Int, tailChars: Int): String {
            val normalized = normalizeForReview(text)
            val head = headChars.coerceAtLeast(0)
            val tail = tailChars.coerceAtLeast(0)
            val minTotal = head + tail
            if (normalized.length <= minTotal + 3) return normalized
            if (head == 0 && tail == 0) return "..."
            if (head == 0) return "..." + normalized.takeLast(tail)
            if (tail == 0) return normalized.take(head) + "..."
            return normalized.take(head) + "..." + normalized.takeLast(tail)
        }

        fun stripMediaLinksForAssistant(text: String): String {
            var cleaned = text
            val removedImages = MediaLinkParser.hasImageLinks(cleaned)
            if (removedImages) {
                cleaned = MediaLinkParser.removeImageLinks(cleaned)
            }
            val removedMedia = MediaLinkParser.hasMediaLinks(cleaned)
            if (removedMedia) {
                cleaned = MediaLinkParser.removeMediaLinks(cleaned)
            }
            cleaned = cleaned.trim()
            if (cleaned.isBlank()) {
                return when {
                    removedImages -> context.getString(R.string.ai_message_image_omitted)
                    removedMedia -> context.getString(R.string.ai_message_media_omitted)
                    else -> ""
                }
            }
            return cleaned
        }

        fun extractXmlBody(block: String): String {
            val openingEnd = block.indexOf('>')
            val closingStart = block.lastIndexOf("</")
            if (openingEnd == -1 || closingStart <= openingEnd) return ""
            return block.substring(openingEnd + 1, closingStart).trim()
        }

        fun stripXmlTagsForReview(text: String): String {
            return ChatMarkupRegex.anyXmlTag.replace(text, " ").trim()
        }

        fun condenseToolParams(block: String, maxParams: Int = 8): String {
            val params = ChatMarkupRegex.toolParamPattern.findAll(block).mapNotNull { match ->
                val paramName = match.groupValues.getOrNull(1)?.trim().orEmpty()
                val rawValue = stripXmlTagsForReview(match.groupValues.getOrNull(2).orEmpty())
                val preview = condenseHeadTail(rawValue, headChars = 72, tailChars = 32)
                if (paramName.isBlank() || preview.isBlank()) {
                    null
                } else {
                    "$paramName=$preview"
                }
            }.toList()
            if (params.isEmpty()) return ""
            val visible = params.take(maxParams)
            val omittedCount = (params.size - visible.size).coerceAtLeast(0)
            return buildString {
                append(visible.joinToString("; "))
                if (omittedCount > 0) {
                    append("; ...+")
                    append(omittedCount)
                }
            }
        }

        fun condenseToolBodyPreview(block: String): String {
            val bodyWithoutParams = ChatMarkupRegex.toolParamPattern.replace(extractXmlBody(block), " ")
            val cleanedBody = stripXmlTagsForReview(bodyWithoutParams)
            return condenseHeadTail(cleanedBody, headChars = 120, tailChars = 48)
        }

        fun condenseToolResultPreview(block: String): String {
            val resultBody =
                ChatMarkupRegex.contentTag.find(block)?.groupValues?.getOrNull(1)
                    ?: ChatMarkupRegex.errorTag.find(block)?.groupValues?.getOrNull(1)
                    ?: extractXmlBody(block)
            val cleanedBody = stripXmlTagsForReview(resultBody)
            return condenseHeadTail(cleanedBody, headChars = 140, tailChars = 56)
        }

        fun pruneUserMessageForReview(text: String): String {
            val removedLargeTags = text
                .replace(
                    Regex("<workspace_attachment>[\\s\\S]*?</workspace_attachment>", RegexOption.DOT_MATCHES_ALL),
                    context.getString(R.string.ai_message_workspace_omitted)
                )
                .replace(
                    Regex("<attachment[\\s\\S]*?</attachment>", RegexOption.DOT_MATCHES_ALL),
                    context.getString(R.string.ai_message_attachment_omitted)
                )
                .replace(
                    Regex("<reply_to[\\s\\S]*?</reply_to>", RegexOption.DOT_MATCHES_ALL),
                    context.getString(R.string.ai_message_reply_omitted)
                )

            return ChatMarkupRegex.toolResultTagWithAttrs.replace(removedLargeTags) { mr ->
                val attrs = mr.groupValues.getOrNull(2) ?: ""
                val name = ChatMarkupRegex.nameAttr
                    .find(attrs)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.ifBlank { null }
                val status = ChatMarkupRegex.statusAttr
                    .find(attrs)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.ifBlank { null }
                val statusSuffix = status?.let { " $it" }.orEmpty()
                val preview = condenseToolResultPreview(mr.value)
                buildString {
                    if (name != null) {
                        append(context.getString(R.string.ai_message_result_prefix))
                        append(name)
                        append(statusSuffix)
                        append(context.getString(R.string.ai_message_result_suffix))
                    } else {
                        append(context.getString(R.string.ai_message_tool_result_omitted_short))
                    }
                    if (preview.isNotBlank()) {
                        append(" ")
                        append(preview)
                    }
                }
            }
        }

        fun condenseUserForReview(text: String): String {
            val pruned = pruneUserMessageForReview(text)
            return condenseHeadTail(pruned, headChars = 240, tailChars = 96)
        }

        fun condenseAssistantForReview(text: String): String {
            val cleaned = ChatUtils.removeThinkingContent(text)
            val normalized = normalizeForReview(cleaned)
            if (normalized.isBlank()) return "[Empty]"

            data class Segment(
                val kind: String,
                val raw: String,
                val toolName: String? = null,
                val status: String? = null
            )

            val blockRegex = ChatMarkupRegex.toolOrToolResultBlock
            val nameAttrRegex = ChatMarkupRegex.nameAttr
            val statusAttrRegex = ChatMarkupRegex.statusAttr

            val segments = mutableListOf<Segment>()
            var lastEnd = 0
            for (m in blockRegex.findAll(normalized)) {
                val start = m.range.first
                val endExclusive = m.range.last + 1
                if (start > lastEnd) {
                    segments.add(Segment(kind = "text", raw = normalized.substring(lastEnd, start)))
                }

                val block = m.value
                val tagName =
                    ChatMarkupRegex.normalizeToolLikeTagName(
                        ChatMarkupRegex.extractOpeningTagName(block)
                    )
                if (tagName == "tool") {
                    val toolName =
                        nameAttrRegex.find(block)?.groupValues?.getOrNull(1)?.ifBlank { null } ?: "tool"
                    segments.add(Segment(kind = "tool", raw = block, toolName = toolName))
                } else {
                    val toolName =
                        nameAttrRegex.find(block)?.groupValues?.getOrNull(1)?.ifBlank { null } ?: "tool"
                    val status = statusAttrRegex.find(block)?.groupValues?.getOrNull(1)?.ifBlank { null }
                    segments.add(Segment(kind = "tool_result", raw = block, toolName = toolName, status = status))
                }

                lastEnd = endExclusive
            }
            if (lastEnd < normalized.length) {
                segments.add(Segment(kind = "text", raw = normalized.substring(lastEnd)))
            }

            val cleanedSegments = segments
                .mapNotNull { seg ->
                    when (seg.kind) {
                        "text" -> {
                            val stripped = stripXmlTagsForReview(seg.raw)
                            if (stripped.isBlank()) null else seg.copy(raw = stripped)
                        }
                        else -> seg
                    }
                }
                .toMutableList()

            val maxSegments = 25
            if (cleanedSegments.size > maxSegments) {
                val head = cleanedSegments.take(12)
                val tail = cleanedSegments.takeLast(10)
                val omitted = (cleanedSegments.size - head.size - tail.size).coerceAtLeast(0)
                cleanedSegments.clear()
                cleanedSegments.addAll(head)
                cleanedSegments.add(Segment(kind = "text", raw = context.getString(R.string.ai_message_omitted_segment, omitted) ) )
                cleanedSegments.addAll(tail)
            }

            val lastTextIndex = cleanedSegments.indexOfLast { it.kind == "text" }
            val parts = cleanedSegments.mapIndexedNotNull { index, seg ->
                when (seg.kind) {
                    "text" -> {
                        val headChars = if (index == lastTextIndex) 280 else 120
                        val tailChars = if (index == lastTextIndex) 120 else 48
                        condenseHeadTail(seg.raw, headChars = headChars, tailChars = tailChars).takeIf { it.isNotBlank() }
                    }
                    "tool" -> {
                        val name = seg.toolName ?: "tool"
                        val paramsPreview = condenseToolParams(seg.raw)
                        val bodyPreview = condenseToolBodyPreview(seg.raw)
                        buildString {
                            append(context.getString(R.string.ai_message_tool_start, name))
                            if (paramsPreview.isNotBlank()) {
                                append(" ")
                                append(paramsPreview)
                            }
                            if (bodyPreview.isNotBlank()) {
                                if (paramsPreview.isNotBlank()) append(" | ")
                                else append(" ")
                                append(bodyPreview)
                            }
                        }.trim().ifBlank { null }
                    }
                    "tool_result" -> {
                        val s = seg.status?.lowercase()
                        val statusText = when {
                            s == null -> ""
                            s == "success" -> context.getString(R.string.ai_message_success)
                            s == "error" -> context.getString(R.string.ai_message_failure)
                            else -> s
                        }
                        val name = seg.toolName ?: "tool"
                        val resultPreview = condenseToolResultPreview(seg.raw)
                        buildString {
                            if (statusText.isBlank()) {
                                append(context.getString(R.string.ai_message_result_prefix))
                                append(name)
                                append(context.getString(R.string.ai_message_result_suffix))
                            } else {
                                append(context.getString(R.string.ai_message_result_prefix))
                                append(name)
                                append(" ")
                                append(statusText)
                                append(context.getString(R.string.ai_message_result_suffix))
                            }
                            if (resultPreview.isNotBlank()) {
                                append(" ")
                                append(resultPreview)
                            }
                        }
                    }
                    else -> null
                }
            }

            val combined = parts.joinToString(" ").trim()
            return if (combined.isBlank()) "[Empty]" else combined
        }

        // 群聊模式：将消息打包成多角色格式
        val conversationToSummarize = if (isGroupChat) {
            // 打包所有消息到一条用户消息
            val packedContent = buildString {
                messagesToSummarize.forEach { message ->
                    // 清理消息内容：移除 memory 标签和 thinking 内容
                    val cleanedContent = if (message.sender == "user") {
                        stripMediaLinksForAssistant(
                            message.content.replace(memoryTagRegex, "").trim()
                        )
                    } else {
                        // AI 消息需要先移除 thinking 内容，再移除媒体链接
                        val withoutThinking = ChatUtils.removeThinkingContent(message.content)
                        stripMediaLinksForAssistant(withoutThinking)
                    }

                    if (cleanedContent.isNotBlank()) {
                        val displayContent = if (message.sender == "ai") {
                            condenseAssistantForReview(cleanedContent)
                        } else {
                            condenseUserForReview(cleanedContent)
                        }

                        val speakerLabel = if (message.sender == "user") {
                            "user"
                        } else {
                            message.roleName?.takeIf { it.isNotBlank() } ?: "AI"
                        }

                        conversationReviewEntries.add(speakerLabel to displayContent)

                        if (isNotEmpty()) append(" ")
                        append("$speakerLabel: $cleanedContent")
                    }
                }
            }
            listOf(Pair("user", packedContent))
        } else {
            // 非群聊模式：保持原有逻辑
            messagesToSummarize.mapIndexed { index, message ->
                val role = if (message.sender == "user") "user" else "assistant"
                val cleanedContent = if (role == "user") {
                    stripMediaLinksForAssistant(
                        message.content.replace(memoryTagRegex, "").trim()
                    )
                } else {
                    stripMediaLinksForAssistant(message.content)
                }
                if (cleanedContent.isNotBlank()) {
                    val displayContent =
                        if (role == "assistant") condenseAssistantForReview(cleanedContent) else condenseUserForReview(cleanedContent)
                    val speakerLabel =
                        if (message.sender == "user") {
                            "user"
                        } else {
                            val roleName = message.roleName?.takeIf { it.isNotBlank() }
                            if (roleName != null) roleName else "AI"
                        }
                    conversationReviewEntries.add(speakerLabel to displayContent)
                }
                Pair(role, "#${index + 1}: $cleanedContent")
            }
        }

        return try {
            AppLogger.d(TAG, "开始使用AI生成对话总结：总结 ${messagesToSummarize.size} 条消息")
            val summary = enhancedAiService.generateSummary(conversationToSummarize, previousSummary, summaryCustomRules)
            AppLogger.d(TAG, "AI生成总结完成: ${summary.take(50)}...")

            if (summary.isBlank()) {
                AppLogger.e(TAG, "AI生成的总结内容为空，放弃本次总结")
                null
            } else {
                // 如果是自动续写，在总结消息尾部添加续写提示
                val trimmedSummary = summary.trim()
                val useEnglish = LocaleUtils.getCurrentLanguage(context).lowercase().startsWith("en")
                val packageWarmupBlock = buildPackageWarmupBlock(messagesToSummarize, useEnglish)
                val summaryWithQuotes = buildString {
                    append(trimmedSummary)
                    if (conversationReviewEntries.isNotEmpty()) {
                        append(context.getString(R.string.ai_message_dialogue_review))
                        conversationReviewEntries.forEach { (speaker, content) ->
                            append("- ")
                            append(speaker)
                            append(": ")
                            append(content)
                            append("\n")
                        }
                    }
                    if (packageWarmupBlock.isNotBlank()) {
                        append("\n\n")
                        append(packageWarmupBlock)
                    }
                }.trimEnd()

                val finalSummary = if (autoContinue) {
                    context.getString(R.string.ai_message_continue_task_if_complete, summaryWithQuotes)
                } else {
                    summaryWithQuotes
                }
                
                ChatMessage(
                    sender = "summary",
                    content = finalSummary,
                    timestamp = ChatMessageTimestampAllocator.next(),
                    roleName = "system" // 总结消息的角色名
                )
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                throw e
            }
            AppLogger.e(TAG, "AI生成总结过程中发生异常", e)
            throw e
        }
    }

    private suspend fun buildPackageWarmupBlock(
        messagesToSummarize: List<ChatMessage>,
        useEnglish: Boolean
    ): String {
        val title = context.getString(R.string.ai_message_package_warmup_title)
        val topPackages = extractTopPackageUsages(messagesToSummarize, limit = 2)

        if (topPackages.isEmpty()) {
            val emptyMessage =
                if (useEnglish) {
                    "No package-prefixed tool usage was detected in this summary window, so no package was preheated."
                } else {
                    context.getString(R.string.ai_message_package_warmup_empty)
                }
            return "$title\n$emptyMessage"
        }

        val intro =
            if (useEnglish) {
                "The following high-frequency packages were automatically activated from the summarized tool usage, and their use_package results are attached for the next-turn warmup."
            } else {
                context.getString(R.string.ai_message_package_warmup_intro)
            }

        val body = withContext(Dispatchers.IO) {
            val packageManager = toolHandler.getOrCreatePackageManager()
            buildString {
                appendLine(intro)
                appendLine()
                topPackages.forEachIndexed { index, stat ->
                    val resultText =
                        runCatching { packageManager.usePackage(stat.packageName).trim() }
                            .getOrElse { throwable ->
                                if (useEnglish) {
                                    "use_package failed: ${throwable.message ?: "unknown error"}"
                                } else {
                                    context.getString(
                                        R.string.ai_message_use_package_failed,
                                        throwable.message ?: context.getString(R.string.unknown_error)
                                    )
                                }
                            }
                            .ifBlank {
                                if (useEnglish) {
                                    "use_package returned empty content."
                                } else {
                                    context.getString(R.string.ai_message_use_package_empty)
                                }
                            }

                    if (useEnglish) {
                        appendLine("${index + 1}. Package ${stat.packageName} (${stat.count} hits)")
                    } else {
                        appendLine(
                            context.getString(
                                R.string.ai_message_package_warmup_item,
                                index + 1,
                                stat.packageName,
                                stat.count
                            )
                        )
                    }
                    appendLine(indentBlock(resultText, "   "))
                    if (index != topPackages.lastIndex) {
                        appendLine()
                    }
                }
            }.trimEnd()
        }

        return buildString {
            appendLine(title)
            append(body)
        }.trimEnd()
    }

    private fun extractTopPackageUsages(
        messagesToSummarize: List<ChatMessage>,
        limit: Int
    ): List<PackageUsageStat> {
        if (limit <= 0) {
            return emptyList()
        }

        data class PackageUsageCounter(
            var count: Int,
            val firstSeenOrder: Int
        )

        val packageUsage = linkedMapOf<String, PackageUsageCounter>()
        var nextOrder = 0

        fun recordPackageUsage(toolName: String) {
            val normalizedToolName = toolName.trim()
            if (normalizedToolName.isBlank() || !normalizedToolName.contains(':')) {
                return
            }

            val packageName = normalizedToolName.substringBefore(':').trim()
            if (packageName.isBlank()) {
                return
            }

            val existing = packageUsage[packageName]
            if (existing == null) {
                packageUsage[packageName] = PackageUsageCounter(count = 1, firstSeenOrder = nextOrder)
                nextOrder += 1
            } else {
                existing.count += 1
            }
        }

        messagesToSummarize
            .asSequence()
            .filter { it.sender == "ai" }
            .forEach { message ->
                val content = ChatUtils.removeThinkingContent(message.content)
                ChatMarkupRegex.toolCallPattern.findAll(content).forEach { match ->
                    val toolName = match.groupValues.getOrNull(2).orEmpty().trim()
                    val toolBody = match.groupValues.getOrNull(3).orEmpty()

                    when {
                        toolName == "package_proxy" || toolName == "proxy" -> {
                            val proxiedToolName =
                                ChatMarkupRegex.toolParamPattern
                                    .findAll(toolBody)
                                    .firstOrNull { it.groupValues.getOrNull(1)?.trim() == "tool_name" }
                                    ?.groupValues
                                    ?.getOrNull(2)
                                    ?.trim()
                                    .orEmpty()
                            recordPackageUsage(proxiedToolName)
                        }
                        toolName == "use_package" -> Unit
                        else -> recordPackageUsage(toolName)
                    }
                }
            }

        return packageUsage.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, PackageUsageCounter>> { it.value.count }
                    .thenBy { it.value.firstSeenOrder }
            )
            .take(limit)
            .map { entry ->
                PackageUsageStat(
                    packageName = entry.key,
                    count = entry.value.count
                )
            }
    }

    private fun indentBlock(text: String, prefix: String): String {
        return text
            .lines()
            .joinToString("\n") { line ->
                if (line.isBlank()) {
                    line
                } else {
                    prefix + line
                }
            }
    }

    /**
     * 判断是否应该生成对话总结。
     *
     * @param messages 完整的消息列表。
     * @param currentTokens 当前上下文的token数量。
     * @param maxTokens 上下文窗口的最大token数量。
     * @return 如果应该生成总结，则返回true。
     */
    fun shouldGenerateSummary(
        messages: List<ChatMessage>,
        currentTokens: Int,
        maxTokens: Int,
        tokenUsageThreshold: Double,
        enableSummary: Boolean,
        enableSummaryByMessageCount: Boolean,
        summaryMessageCountThreshold: Int
    ): Boolean {
        // 首先检查总结功能是否启用
        if (!enableSummary) {
            return false
        }

        // 检查Token阈值
        if (maxTokens > 0) {
            val usageRatio = currentTokens.toDouble() / maxTokens.toDouble()
            if (usageRatio >= tokenUsageThreshold) {
                AppLogger.d(TAG, "Token usage ($usageRatio) exceeds threshold ($tokenUsageThreshold). Triggering summary.")
                return true
            }
        }

        // 检查消息条数阈值（如果启用）
        if (enableSummaryByMessageCount) {
            val lastSummaryIndex = messages.indexOfLast { it.sender == "summary" }
            val relevantMessages = if (lastSummaryIndex != -1) {
                messages.subList(lastSummaryIndex + 1, messages.size)
            } else {
                messages
            }
            val userAiMessagesSinceLastSummary = relevantMessages.count { it.sender == "user"}

            if (userAiMessagesSinceLastSummary >= summaryMessageCountThreshold) {
                AppLogger.d(TAG, "自上次总结后新消息数量达到阈值 ($userAiMessagesSinceLastSummary)，生成总结.")
                return true
            }
        }

        AppLogger.d(TAG, "未达到生成总结的条件. Token使用率: ${if (maxTokens > 0) currentTokens.toDouble() / maxTokens else 0.0}")
        return false
    }

    /**
     * 从完整的聊天记录中提取用于AI上下文的“记忆”。
     * 这会获取上次总结之后的所有消息。
     *
     * @param messages 完整的聊天记录。
     * @return 一个Pair列表，包含角色和内容，用于AI请求。
     */
    fun getMemoryFromMessages(
        messages: List<ChatMessage>,
        splitByRole: Boolean = false,
        targetRoleName: String? = null,
        groupOrchestrationMode: Boolean = false
    ): List<PromptTurn> {
        val totalStartTime = messageTimingNow()
        // 1. 找到最后一条总结消息，只处理总结之后的消息
        val lastSummaryIndex = messages.indexOfLast { it.sender == "summary" }
        val relevantMessages = if (lastSummaryIndex != -1) {
            messages.subList(lastSummaryIndex, messages.size)
        } else {
            messages
        }

        // 2. 判断是否启用角色隔离模式
        val isRoleScopedMode = splitByRole && !targetRoleName.isNullOrBlank()
        val normalizedTargetRole = targetRoleName?.trim().orEmpty()

        // 3. 辅助函数：移除状态标签
        fun removeStatusTags(text: String): String {
            val noStatus = ChatMarkupRegex.statusTag.replace(text, " ")
            return ChatMarkupRegex.statusSelfClosingTag.replace(noStatus, " ").trim()
        }

        // 4. 处理每条消息
        val processedMessages = relevantMessages
            .filter { it.sender == "user" || it.sender == "ai" || it.sender == "summary" }
            .mapNotNull { message ->
                when (message.sender) {
                    "ai" -> processAiMessage(
                        message,
                        isRoleScopedMode,
                        normalizedTargetRole,
                        ::removeStatusTags
                    )
                    "user" -> processUserMessage(
                        message,
                        isRoleScopedMode,
                        groupOrchestrationMode
                    )
                    "summary" ->
                        PromptTurn(
                            kind = PromptTurnKind.SUMMARY,
                            content = message.content
                        )
                    else -> null
                }
            }
        val assistantCount = processedMessages.count { it.kind == PromptTurnKind.ASSISTANT }
        val userCount = processedMessages.count { it.kind == PromptTurnKind.USER }
        logMessageTiming(
            stage = "getMemoryFromMessages.total",
            startTimeMs = totalStartTime,
            details = "source=${messages.size}, relevant=${relevantMessages.size}, result=${processedMessages.size}, assistant=$assistantCount, user=$userCount, splitByRole=$splitByRole, roleScoped=$isRoleScopedMode, groupOrchestration=$groupOrchestrationMode"
        )
        return processedMessages
    }

    private fun processAiMessage(
        message: ChatMessage,
        isRoleScopedMode: Boolean,
        targetRoleName: String,
        removeStatusTags: (String) -> String
    ): PromptTurn? {
        // 清理思考内容
        val cleanedContent = ChatUtils.removeThinkingContent(message.content).trim()
        val contentWithoutStatus = removeStatusTags(cleanedContent)

        // 非角色隔离模式：直接返回 assistant 消息
        if (!isRoleScopedMode) {
            return PromptTurn(
                kind = PromptTurnKind.ASSISTANT,
                content = message.content
            )
        }

        // 角色隔离模式：判断是当前角色还是其他角色
        val messageRoleName = message.roleName.trim()
        return if (messageRoleName == targetRoleName) {
            // 当前角色的消息：作为 assistant 返回
            PromptTurn(
                kind = PromptTurnKind.ASSISTANT,
                content = message.content
            )
        } else {
            // 其他角色的消息：转换为 user 消息，添加角色标签
            val roleLabel = if (messageRoleName.isNotBlank()) messageRoleName else "unknown"
            val bridgedContent = removeStatusTags(cleanedContent)
            if (bridgedContent.isBlank()) {
                null
            } else {
                PromptTurn(
                    kind = PromptTurnKind.USER,
                    content = "[From role: $roleLabel]\n$bridgedContent"
                )
            }
        }
    }

    private fun processUserMessage(
        message: ChatMessage,
        isRoleScopedMode: Boolean,
        groupOrchestrationMode: Boolean
    ): PromptTurn {
        val baseContent = message.content

        // 群组编排模式 + 角色隔离模式：给用户消息添加 [From user] 前缀
        if (groupOrchestrationMode && isRoleScopedMode) {
            val trimmed = baseContent.trim()
            return when {
                trimmed.isBlank() ->
                    PromptTurn(kind = PromptTurnKind.USER, content = baseContent)
                trimmed.startsWith("[From user]") ->
                    PromptTurn(kind = PromptTurnKind.USER, content = trimmed)
                else ->
                    PromptTurn(kind = PromptTurnKind.USER, content = "[From user]\n$trimmed")
            }
        }

        // 其他模式：直接返回
        return PromptTurn(kind = PromptTurnKind.USER, content = baseContent)
    }
}
