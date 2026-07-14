package com.ai.assistance.operit.api.chat.enhance

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.chat.hooks.PromptHookContext
import com.ai.assistance.operit.core.chat.hooks.PromptHookRegistry
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.chat.hooks.SummaryHookContext
import com.ai.assistance.operit.core.chat.hooks.SummaryHookRegistry
import com.ai.assistance.operit.core.chat.hooks.buildActivePromptHookMetadata
import com.ai.assistance.operit.core.chat.hooks.toPromptTurns
import com.ai.assistance.operit.core.config.SystemPromptConfig
import com.ai.assistance.operit.core.tools.climode.ToolExposureMode
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.core.tools.UIPageResultData
import com.ai.assistance.operit.core.tools.SimplifiedUINode
import com.ai.assistance.operit.core.config.FunctionalPrompts
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.WaifuPreferences
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.CharacterCardToolAccessResolver
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.preferences.UserProfileDocumentRepository
import com.ai.assistance.operit.core.avatar.impl.factory.AvatarModelFactoryImpl
import com.ai.assistance.operit.data.repository.AvatarRepository
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.core.tools.ToolProgressBus
import com.ai.assistance.operit.util.streamnative.NativeXmlSplitter
import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import com.ai.assistance.operit.core.tools.ComputerDesktopActionResultData
import com.ai.assistance.operit.util.LocaleUtils
import com.ai.assistance.operit.api.chat.enhance.MultiServiceManager
import com.ai.assistance.operit.data.repository.CustomEmojiRepository
import com.ai.assistance.operit.api.chat.llmprovider.MediaLinkBuilder
import com.ai.assistance.operit.data.repository.getCustomMoodDefinitions
import com.ai.assistance.operit.data.repository.getMoodAnimationMapping

/** 处理会话相关功能的服务类，包括会话总结、偏好处理和对话切割准备 */
class ConversationService(
    private val context: Context,
    private val customEmojiRepository: CustomEmojiRepository
    ) {

    companion object {
        private const val TAG = "ConversationService"
        private const val APPLY_FILE_TOOL_NAME = "apply_file"
        private val fileRequestContentRegex = Regex(
            """<file-request-content\b[^>]*><!\[CDATA\[(.*?)\]\]></file-request-content>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
    }

    private val apiPreferences = ApiPreferences.getInstance(context)
    private val displayPreferencesManager = DisplayPreferencesManager.getInstance(context)
    private val waifuPreferences = WaifuPreferences.getInstance(context)
    private val characterCardManager = CharacterCardManager.getInstance(context)
    private val characterCardToolAccessResolver = CharacterCardToolAccessResolver.getInstance(context)
    private val activePromptManager = ActivePromptManager.getInstance(context)
    private val userProfileDocumentRepository = UserProfileDocumentRepository.getInstance(context)
    private val avatarRepository by lazy {
        AvatarRepository.getInstance(context, AvatarModelFactoryImpl())
    }
    private val conversationMutex = Mutex()

    /**
     * 生成对话总结
     * @param messages 要总结的消息列表
     * @return 生成的总结文本
     */
    suspend fun generateSummary(
            messages: List<Pair<String, String>>,
            multiServiceManager: MultiServiceManager
    ): String {
        return generateSummaryFromPromptTurns(messages.toPromptTurns(), null, multiServiceManager)
    }

    /**
     * 生成对话总结，并且包含上一次的总结内容
     * @param messages 要总结的消息列表
     * @param previousSummary 上一次的总结内容，可以为null
     * @return 生成的总结文本
     */
    suspend fun generateSummary(
            messages: List<Pair<String, String>>,
            previousSummary: String?,
            multiServiceManager: MultiServiceManager
    ): String {
        return generateSummaryFromPromptTurns(messages.toPromptTurns(), previousSummary, multiServiceManager)
    }

    suspend fun generateSummaryFromPromptTurns(
            messages: List<PromptTurn>,
            previousSummary: String?,
            multiServiceManager: MultiServiceManager,
            customRules: String? = null
    ): String {
        try {
            val useEnglish = LocaleUtils.getCurrentLanguage(context).lowercase().startsWith("en")
            val activePromptMetadata = buildActivePromptHookMetadata(context)
            var systemPrompt = FunctionalPrompts.buildSummarySystemPrompt(previousSummary, useEnglish)
            // 注入自定义总结规则
            if (!customRules.isNullOrBlank()) {
                systemPrompt += "\n\n${customRules.trim()}"
            }
            val sanitizedMessages =
                ChatUtils.stripOpenAiResponsesReasoningMetaTurns(
                    ChatUtils.stripGeminiThoughtSignatureMetaTurns(messages)
                )

            // Get all model parameters from preferences (with enabled state)
            val modelParameters = multiServiceManager.getModelParametersForFunction(FunctionType.SUMMARY)
            val serializedModelParameters = serializeSummaryHookModelParameters(modelParameters)

            // 获取SUMMARY功能类型的AIService实例
            val summaryService = multiServiceManager.getServiceForFunction(FunctionType.SUMMARY)
            var summaryHistory = sanitizedMessages
            var summaryPrompt = FunctionalPrompts.summaryUserMessage(useEnglish)
            val baseSummaryMetadata =
                mapOf(
                    "providerModel" to summaryService.providerModel,
                    "sourceMessageCount" to summaryHistory.size
                ) + activePromptMetadata

            val beforePrepareContext =
                SummaryHookRegistry.dispatchSummaryGenerateHooks(
                    SummaryHookContext(
                        stage = "before_prepare_summary_prompt",
                        useEnglish = useEnglish,
                        previousSummary = previousSummary,
                        chatHistory = summaryHistory,
                        systemPrompt = systemPrompt,
                        summaryPrompt = summaryPrompt,
                        modelParameters = serializedModelParameters,
                        metadata = baseSummaryMetadata
                    )
                )
            summaryHistory = beforePrepareContext.chatHistory
            systemPrompt = beforePrepareContext.systemPrompt ?: systemPrompt
            summaryPrompt = beforePrepareContext.summaryPrompt ?: summaryPrompt
            var preparedHistory =
                beforePrepareContext.preparedHistory.takeIf { it.isNotEmpty() }
                    ?: buildSummaryPreparedHistory(
                        systemPrompt = systemPrompt,
                        chatHistory = summaryHistory,
                        summaryPrompt = summaryPrompt
                    )

            val beforeSendBasePreparedHistory = preparedHistory
            val beforeSendContext =
                SummaryHookRegistry.dispatchSummaryGenerateHooks(
                    SummaryHookContext(
                        stage = "before_send_to_model",
                        useEnglish = useEnglish,
                        previousSummary = previousSummary,
                        chatHistory = summaryHistory,
                        preparedHistory = preparedHistory,
                        systemPrompt = systemPrompt,
                        summaryPrompt = summaryPrompt,
                        modelParameters = serializedModelParameters,
                        metadata =
                            baseSummaryMetadata + mapOf(
                                "preparedMessageCount" to preparedHistory.size
                            )
                    )
                )
            summaryHistory = beforeSendContext.chatHistory
            systemPrompt = beforeSendContext.systemPrompt ?: systemPrompt
            summaryPrompt = beforeSendContext.summaryPrompt ?: summaryPrompt
            preparedHistory =
                if (beforeSendContext.preparedHistory != beforeSendBasePreparedHistory) {
                    beforeSendContext.preparedHistory
                } else {
                    buildSummaryPreparedHistory(
                        systemPrompt = systemPrompt,
                        chatHistory = summaryHistory,
                        summaryPrompt = summaryPrompt
                    )
                }

            // 使用summaryService发送请求，收集完整响应
            val contentBuilder = StringBuilder()

            ToolProgressBus.update(
                ToolProgressBus.SUMMARY_PROGRESS_TOOL_NAME,
                0.05f,
                context.getString(R.string.conversation_summary_preparing)
            )

            data class Stage(
                val matchers: List<(String) -> Boolean>,
                val progress: Float,
                val message: String
            )

            val stages = listOf(
                Stage(
                    matchers = listOf({ it.contains(FunctionalPrompts.SUMMARY_MARKER_EN) || it.contains(FunctionalPrompts.SUMMARY_MARKER_CN) }),
                    progress = 0.20f,
                    message = context.getString(R.string.conversation_summary_writing_title)
                ),
                Stage(
                    matchers = listOf({ it.contains(FunctionalPrompts.SUMMARY_SECTION_CORE_TASK_EN) || it.contains(FunctionalPrompts.SUMMARY_SECTION_CORE_TASK_CN) }),
                    progress = 0.40f,
                    message = context.getString(R.string.conversation_summary_core_task)
                ),
                Stage(
                    matchers = listOf({ it.contains(FunctionalPrompts.SUMMARY_SECTION_INTERACTION_EN) || it.contains(FunctionalPrompts.SUMMARY_SECTION_INTERACTION_CN) }),
                    progress = 0.55f,
                    message = context.getString(R.string.conversation_summary_interaction)
                ),
                Stage(
                    matchers = listOf({ it.contains(FunctionalPrompts.SUMMARY_SECTION_PROGRESS_EN) || it.contains(FunctionalPrompts.SUMMARY_SECTION_PROGRESS_CN) }),
                    progress = 0.70f,
                    message = context.getString(R.string.conversation_summary_progress)
                ),
                Stage(
                    matchers = listOf({ it.contains(FunctionalPrompts.SUMMARY_SECTION_KEY_INFO_EN) || it.contains(FunctionalPrompts.SUMMARY_SECTION_KEY_INFO_CN) }),
                    progress = 0.85f,
                    message = context.getString(R.string.conversation_summary_key_info)
                ),
                Stage(
                    matchers = listOf({ it.contains("=======================================") || it.contains("============================") }),
                    progress = 0.95f,
                    message = context.getString(R.string.conversation_summary_finishing)
                )
            )

            var lastStageIndex = -1
            fun updateStageIfNeeded() {
                if (lastStageIndex + 1 >= stages.size) return
                val snapshot = contentBuilder.toString()
                while (lastStageIndex + 1 < stages.size) {
                    val next = stages[lastStageIndex + 1]
                    val matched = next.matchers.any { it(snapshot) }
                    if (!matched) break
                    lastStageIndex += 1
                    ToolProgressBus.update(
                        ToolProgressBus.SUMMARY_PROGRESS_TOOL_NAME,
                        next.progress,
                        next.message
                    )
                }
            }

            // 使用新的Stream API
            val stream =
                    summaryService.sendMessage(
                            context = context,
                            chatHistory = preparedHistory,
                            modelParameters = modelParameters
                    )

            // 收集流中的所有内容
            stream.collect { content ->
                contentBuilder.append(content)
                updateStageIfNeeded()
            }

            ToolProgressBus.update(
                ToolProgressBus.SUMMARY_PROGRESS_TOOL_NAME,
                1f,
                context.getString(R.string.conversation_summary_completed)
            )

            // 获取完整的总结内容
            var summaryContent = ChatUtils.removeThinkingContent(contentBuilder.toString().trim())

            // 获取本次总结生成的token统计
            val inputTokens = summaryService.inputTokenCount
            val cachedInputTokens = summaryService.cachedInputTokenCount
            val outputTokens = summaryService.outputTokenCount

            val afterGenerateContext =
                SummaryHookRegistry.dispatchSummaryGenerateHooks(
                    SummaryHookContext(
                        stage = "after_generate_summary",
                        useEnglish = useEnglish,
                        previousSummary = previousSummary,
                        chatHistory = summaryHistory,
                        preparedHistory = preparedHistory,
                        systemPrompt = systemPrompt,
                        summaryPrompt = summaryPrompt,
                        summaryResult = summaryContent,
                        modelParameters = serializedModelParameters,
                        metadata =
                            baseSummaryMetadata + mapOf(
                                "preparedMessageCount" to preparedHistory.size,
                                "inputTokens" to inputTokens,
                                "cachedInputTokens" to cachedInputTokens,
                                "outputTokens" to outputTokens
                            )
                    )
                )
            summaryContent = afterGenerateContext.summaryResult ?: summaryContent

            // 如果内容为空，返回默认消息
            if (summaryContent.isBlank()) {
                return "Conversation Summary: Unable to generate valid summary."
            }

            // 将总结token计数添加到用户偏好分析的token统计中
            try {
                AppLogger.d(TAG, "总结生成使用了输入token: $inputTokens, 缓存token: $cachedInputTokens, 输出token: $outputTokens")
                apiPreferences.updateTokensForProviderModel(summaryService.providerModel, inputTokens, outputTokens, cachedInputTokens)
                
                // Update request count for summary generation
                apiPreferences.incrementRequestCountForProviderModel(summaryService.providerModel)
                
                AppLogger.d(TAG, "已将总结token统计添加到用户偏好分析token计数中")
            } catch (e: Exception) {
                AppLogger.e(TAG, "更新token统计失败", e)
            }

            return summaryContent
        } catch (e: Exception) {
            AppLogger.e(TAG, "生成总结时出错", e)
            // return "对话摘要：生成摘要时出错，但对话仍在继续。"
            throw e
        }
    }



    suspend fun generateConversationTitle(
        userText: String,
        attachmentFileNames: List<String>,
        multiServiceManager: MultiServiceManager
    ): String {
        return try {
            val useEnglish = LocaleUtils.getCurrentLanguage(context).lowercase().startsWith("en")
            val systemPrompt = FunctionalPrompts.conversationTitleSystemPrompt(useEnglish)
            val userPrompt = FunctionalPrompts.conversationTitleUserPrompt(
                userText = userText,
                attachmentFileNames = attachmentFileNames,
                useEnglish = useEnglish
            )
            val preparedHistory = listOf(
                PromptTurn(kind = PromptTurnKind.SYSTEM, content = systemPrompt),
                PromptTurn(kind = PromptTurnKind.USER, content = userPrompt)
            )
            val modelParameters = multiServiceManager.getModelParametersForFunction(FunctionType.TITLE_GENERATION)
            val titleService = multiServiceManager.getServiceForFunction(FunctionType.TITLE_GENERATION)
            val contentBuilder = StringBuilder()
            titleService.sendMessage(
                context = context,
                chatHistory = preparedHistory,
                modelParameters = modelParameters,
                stream = false,
                enableRetry = false
            ).collect { content -> contentBuilder.append(content) }

            val title = sanitizeConversationTitle(
                ChatUtils.removeThinkingContent(contentBuilder.toString().trim())
            )

            try {
                val inputTokens = titleService.inputTokenCount
                val cachedInputTokens = titleService.cachedInputTokenCount
                val outputTokens = titleService.outputTokenCount
                apiPreferences.updateTokensForProviderModel(
                    titleService.providerModel,
                    inputTokens,
                    outputTokens,
                    cachedInputTokens
                )
                apiPreferences.incrementRequestCountForProviderModel(titleService.providerModel)
                AppLogger.d(TAG, "标题生成使用了输入token: $inputTokens, 缓存token: $cachedInputTokens, 输出token: $outputTokens")
            } catch (e: Exception) {
                AppLogger.e(TAG, "更新标题生成token统计失败", e)
            }

            title
        } catch (e: Exception) {
            AppLogger.e(TAG, "生成对话标题时出错", e)
            ""
        }
    }

    private fun sanitizeConversationTitle(rawTitle: String): String {
        val firstLine = rawTitle
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        return firstLine
            .replace(Regex("^[#*`_>\\-\\s]+"), "")
            .replace(Regex("[#*`_]+$"), "")
            .replace(Regex("[\\p{Cntrl}&&[^\\n\\t]]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('"', '\'', '“', '”', '‘', '’', '「', '」', '『', '』')
            .trimEnd('.', '。', '!', '！', '?', '？', ':', '：', ';', '；', ',', '，')
            .take(40)
            .trim()
    }

    private fun buildSummaryPreparedHistory(
        systemPrompt: String,
        chatHistory: List<PromptTurn>,
        summaryPrompt: String
    ): List<PromptTurn> {
        return listOf(PromptTurn(kind = PromptTurnKind.SYSTEM, content = systemPrompt)) +
            chatHistory +
            PromptTurn(
                kind = PromptTurnKind.USER,
                content = summaryPrompt
            )
    }

    private fun serializeSummaryHookModelParameters(
        modelParameters: List<ModelParameter<*>>
    ): List<Map<String, Any?>> {
        return modelParameters.map { parameter ->
            mapOf(
                "id" to parameter.id,
                "name" to parameter.name,
                "apiName" to parameter.apiName,
                "description" to parameter.description,
                "defaultValue" to parameter.defaultValue,
                "currentValue" to parameter.currentValue,
                "isEnabled" to parameter.isEnabled,
                "valueType" to parameter.valueType.name,
                "minValue" to parameter.minValue,
                "maxValue" to parameter.maxValue,
                "category" to parameter.category.name,
                "isCustom" to parameter.isCustom
            )
        }
    }

    /**
     * 为聊天准备对话历史记录
     * @param chatHistory 原始聊天历史
     * @param processedInput 处理后的用户输入
     * @param workspacePath 当前绑定的工作区路径，可以为null
     * @param packageManager 包管理器
     * @param promptFunctionType 提示函数类型
     * @param hasImageRecognition Whether a backend image recognition service is configured
     * @return 准备好的对话历史列表
     */
    suspend fun prepareConversationHistory(
            chatHistory: List<PromptTurn>,
            processedInput: String,
            chatId: String?,
            workspacePath: String?,
            workspaceEnv: String? = null,
            packageManager: PackageManager,
            promptFunctionType: PromptFunctionType,
            customSystemPromptTemplate: String? = null,
            roleCardId: String? = null,
            enableGroupOrchestrationHint: Boolean = false,
            groupParticipantNamesText: String? = null,
            proxySenderName: String? = null,
            hasImageRecognition: Boolean = false,
            hasAudioRecognition: Boolean = false,
            hasVideoRecognition: Boolean = false,
            chatModelHasDirectAudio: Boolean = false,
            chatModelHasDirectVideo: Boolean = false,
            useToolCallApi: Boolean = false,
            chatModelHasDirectImage: Boolean = false,
            toolExposureMode: ToolExposureMode = ToolExposureMode.FULL,
            memorySpaceIdOverride: String? = null,
            dispatchHistoryHooks: (PromptHookContext) -> PromptHookContext = PromptHookRegistry::dispatchPromptHistoryHooks,
            dispatchSystemPromptComposeHooks: (PromptHookContext) -> PromptHookContext = PromptHookRegistry::dispatchSystemPromptComposeHooks,
            dispatchToolPromptComposeHooks: (PromptHookContext) -> PromptHookContext = PromptHookRegistry::dispatchToolPromptComposeHooks
    ): List<PromptTurn> {
        val activePromptMetadata = buildActivePromptHookMetadata(context, chatId, roleCardId)
        val beforeContext =
            dispatchHistoryHooks(
                PromptHookContext(
                    stage = "before_prepare_history",
                    chatId = chatId,
                    promptFunctionType = promptFunctionType.name,
                    processedInput = processedInput,
                    chatHistory = chatHistory,
                    metadata =
                        mapOf(
                            "workspacePath" to workspacePath,
                            "workspaceEnv" to workspaceEnv,
                            "customSystemPromptTemplate" to customSystemPromptTemplate,
                            "enableGroupOrchestrationHint" to enableGroupOrchestrationHint,
                            "groupParticipantNamesText" to groupParticipantNamesText,
                            "proxySenderName" to proxySenderName,
                            "hasImageRecognition" to hasImageRecognition,
                            "hasAudioRecognition" to hasAudioRecognition,
                            "hasVideoRecognition" to hasVideoRecognition,
                            "chatModelHasDirectAudio" to chatModelHasDirectAudio,
                            "chatModelHasDirectVideo" to chatModelHasDirectVideo,
                            "useToolCallApi" to useToolCallApi,
                            "chatModelHasDirectImage" to chatModelHasDirectImage,
                            "toolExposureMode" to toolExposureMode.name
                        ) + activePromptMetadata
                )
            )
        val effectiveChatHistory = beforeContext.chatHistory
        val preparedHistory = mutableListOf<PromptTurn>()
        var resolvedUseEnglish: Boolean? = null
        conversationMutex.withLock {
            // Add system prompt if not already present
            if (!effectiveChatHistory.any { it.kind == PromptTurnKind.SYSTEM }) {
                // user.md describes the one human user. Role cards and group proxy senders describe
                // assistants, so they must never replace or select a different user document.
                val userProfileMarkdown = userProfileDocumentRepository.load().trim()
                val proxyRolePrompt =
                    proxySenderName
                        ?.takeIf { it.isNotBlank() }
                        ?.let { name -> characterCardManager.findCharacterCardByName(name) }
                        ?.let { proxyCard ->
                            characterCardManager.combinePrompts(
                                proxyCard.id,
                                promptFunctionType = promptFunctionType
                            )
                        }
                        .orEmpty()

                // 根据功能类型获取对应的提示词
                val effectiveRoleCardId = roleCardId?.takeIf { it.isNotBlank() }
                val activeCard = effectiveRoleCardId?.let {
                    characterCardManager.getCharacterCardFlow(it).first()
                }
                val introPrompt = activeCard?.let {
                    characterCardManager.combinePrompts(
                        it.id,
                        promptFunctionType = promptFunctionType
                    )
                }.orEmpty()

                // 获取自定义系统提示模板
                val finalCustomSystemPromptTemplate = customSystemPromptTemplate ?: apiPreferences.customSystemPromptTemplateFlow.first()

                // 获取工具启用状态
                val enableTools = apiPreferences.enableToolsFlow.first()
                val disableUserPreferenceDescription =
                        apiPreferences.disableUserPreferenceDescriptionFlow.first()
                val toolPromptVisibility = runCatching {
                    apiPreferences.toolPromptVisibilityFlow.first()
                }.getOrElse { emptyMap() }

                val safBookmarkNames = runCatching {
                    apiPreferences.safBookmarksFlow.first().map { it.name }
                }.getOrElse { emptyList() }

                val useEnglish = LocaleUtils.getCurrentLanguage(context).lowercase().startsWith("en")
                resolvedUseEnglish = useEnglish
                val roleCardToolAccess = characterCardToolAccessResolver.resolve(
                    roleCardId = effectiveRoleCardId,
                    packageManager = packageManager,
                    globalToolVisibility = toolPromptVisibility
                )

                // 获取系统提示词，现在传入workspacePath和识图配置状态
                val systemPrompt = SystemPromptConfig.getSystemPromptWithCustomPrompts(
                    context = context,
                    packageManager = packageManager,
                    chatId = chatId,
                    workspacePath = workspacePath,
                    workspaceEnv = workspaceEnv,
                    safBookmarkNames = safBookmarkNames,
                    customIntroPrompt = introPrompt,
                    useEnglish = useEnglish,
                    customSystemPromptTemplate = finalCustomSystemPromptTemplate,
                    enableTools = enableTools,
                    hasImageRecognition = hasImageRecognition,
                    chatModelHasDirectImage = chatModelHasDirectImage,
                    hasAudioRecognition = hasAudioRecognition,
                    hasVideoRecognition = hasVideoRecognition,
                    chatModelHasDirectAudio = chatModelHasDirectAudio,
                    chatModelHasDirectVideo = chatModelHasDirectVideo,
                    useToolCallApi = useToolCallApi,
                    toolExposureMode = toolExposureMode,
                    toolVisibility = roleCardToolAccess.effectiveBuiltinToolVisibility,
                    allowedPackageNames = roleCardToolAccess.allowedPackageNames,
                    allowedSkillNames = roleCardToolAccess.allowedSkillNames,
                    allowedMcpServerNames = roleCardToolAccess.allowedMcpServerNames,
                    enableGroupOrchestrationHint = enableGroupOrchestrationHint,
                    groupOrchestrationRoleName = activeCard?.name?.takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.app_name),
                    groupParticipantNamesText = groupParticipantNamesText.orEmpty(),
                    hookMetadata = activePromptMetadata,
                    dispatchSystemPromptComposeHooks = dispatchSystemPromptComposeHooks,
                    dispatchToolPromptComposeHooks = dispatchToolPromptComposeHooks
                )

                // 构建waifu特殊规则
                val waifuRulesText = if(waifuPreferences.enableWaifuModeFlow.first()) buildWaifuRulesText() else ""
                // 语音头像模式：添加 <mood> 标签协议
                val avatarMoodRulesText =
                    if (shouldInjectMoodRules(promptFunctionType)) {
                        buildAvatarMoodRulesText(useEnglish)
                    } else {
                        ""
                    }
                AppLogger.d("petRules", avatarMoodRulesText)

                // 构建最终的系统提示词
                val finalSystemPrompt = buildString {
                    append(avatarMoodRulesText)
                    append(systemPrompt)
                    if (proxyRolePrompt.isNotEmpty()) {
                        append("\n\n<assistant_role source=\"proxy_character_card\">\n")
                        append(proxyRolePrompt)
                        append("\n</assistant_role>")
                    }
                    append(waifuRulesText)
                    if (!disableUserPreferenceDescription && userProfileMarkdown.isNotEmpty()) {
                        append("\n\n<user_profile source=\"user.md\">\n")
                        append(userProfileMarkdown)
                        append("\n</user_profile>")
                    }
                }

                // 替换提示词中的占位符
                val aiName = activeCard?.name ?: context.getString(R.string.app_name)
                val finalSystemPromptWithReplacements = replacePromptPlaceholders(
                    finalSystemPrompt,
                    aiName
                )
                preparedHistory.add(
                    0,
                    PromptTurn(
                        kind = PromptTurnKind.SYSTEM,
                        content = finalSystemPromptWithReplacements
                    )
                )
            }

            // Process each message in chat history
            effectiveChatHistory.forEachIndexed { index, message ->
                val kind = message.kind
                val content = message.content

                // If it's an assistant message, check for tool results
                if (kind == PromptTurnKind.ASSISTANT) {
                    val xmlTags = splitXmlTag(content)
                    if (xmlTags.isNotEmpty()) {
                        // Process the message with tool results
                        processChatMessageWithTools(content, xmlTags, preparedHistory, index, effectiveChatHistory.size)
                    } else {
                        // Add the message as is
                        preparedHistory.add(message)
                    }
                } else if (kind == PromptTurnKind.TOOL_RESULT) {
                    preparedHistory.add(message.copy(content = normalizeToolResultMarkupForModel(content)))
                } else {
                    // Add typed turns as is
                    preparedHistory.add(message)
                }
            }
        }
        val afterContext =
            dispatchHistoryHooks(
                beforeContext.copy(
                    stage = "after_prepare_history",
                    useEnglish = resolvedUseEnglish,
                    preparedHistory = preparedHistory
                )
            )
        return afterContext.preparedHistory
    }

    /**
     * 提取内容中的XML标签
     * @param content 要处理的内容
     * @return 提取的XML标签列表，每项包含[标签名称, 标签内容]
     */
    fun splitXmlTag(content: String): List<List<String>> {
        return NativeXmlSplitter.splitXmlTag(content)
    }

    fun normalizeConversationHistoryForModel(
        chatHistory: List<PromptTurn>
    ): List<PromptTurn> {
        return chatHistory.map { turn ->
            when (turn.kind) {
                PromptTurnKind.ASSISTANT,
                PromptTurnKind.TOOL_CALL,
                PromptTurnKind.TOOL_RESULT -> turn.copy(content = normalizeToolResultMarkupForModel(turn.content))
                else -> turn
            }
        }
    }

    private fun normalizeToolResultMarkupForModel(content: String): String {
        return ChatMarkupRegex.toolResultTagWithAttrs.replace(content) { matchResult ->
            val tagName = matchResult.groupValues[1]
            val attrs = matchResult.groupValues[2]
            val body = matchResult.groupValues[3]
            val toolName =
                ChatMarkupRegex.nameAttr.find(attrs)?.groupValues?.getOrNull(1).orEmpty()

            if (!toolName.equals(APPLY_FILE_TOOL_NAME, ignoreCase = true)) {
                return@replace matchResult.value
            }

            val requestContent =
                extractApplyFileRequestContent(body) ?: return@replace matchResult.value
            "<$tagName$attrs><content>$requestContent</content></$tagName>"
        }
    }

    private fun extractApplyFileRequestContent(toolResultBody: String): String? {
        val contentBody =
            ChatMarkupRegex.contentTag.find(toolResultBody)?.groupValues?.getOrNull(1)
                ?: toolResultBody

        return fileRequestContentRegex.find(contentBody)?.groupValues?.getOrNull(1)?.trim()
    }

    /** 处理包含工具结果的聊天消息，并按顺序重新组织消息 任务完成和等待用户响应的status标签算作AI消息，其他status和warning算作用户消息 工具结果为用户消息 */
    suspend fun processChatMessageWithTools(
            content: String,
            xmlTags: List<List<String>>,
            conversationHistory: MutableList<PromptTurn>,
            messageIndex: Int,
            totalMessages: Int
    ) {
        if (xmlTags.isEmpty()) {
            // 如果没有XML标签，直接添加为AI消息
            conversationHistory.add(
                PromptTurn(
                    kind = PromptTurnKind.ASSISTANT,
                    content = content
                )
            )
            return
        }

        // 按顺序处理标签
        val segments = mutableListOf<PromptTurn>()

        for (tag in xmlTags) {
            val tagName = tag[0]
            val normalizedTagName = ChatMarkupRegex.normalizeToolLikeTagName(tagName) ?: tagName
            var tagContent = tag[1]

            // 对于text类型（纯文本），直接作为AI消息
            if (tagName == "text") {
                if (tagContent.isNotBlank()) {
                    segments.add(PromptTurn(kind = PromptTurnKind.ASSISTANT, content = tagContent))
                }
                continue
            }

            // 根据标签类型分配角色
            when (normalizedTagName) {
                "think", "thinking" -> {
                    // 保留完整的think标签（用于DeepSeek推理模式）
                    segments.add(PromptTurn(kind = PromptTurnKind.ASSISTANT, content = tagContent))
                }
                "status" -> {
                    // 判断status类型
                    if (tagContent.contains("type=\"complete\"") ||
                                    tagContent.contains("type=\"wait_for_user_need\"")
                    ) {
                        segments.add(PromptTurn(kind = PromptTurnKind.ASSISTANT, content = tagContent))
                    } else {
                        segments.add(PromptTurn(kind = PromptTurnKind.USER, content = tagContent))
                    }
                }
                "tool_result" -> {
                    segments.add(
                        PromptTurn(
                            kind = PromptTurnKind.TOOL_RESULT,
                            content = normalizeToolResultMarkupForModel(tagContent)
                        )
                    )
                }
                "tool" -> {
                    segments.add(PromptTurn(kind = PromptTurnKind.TOOL_CALL, content = tagContent))
                }
                else -> {
                    segments.add(PromptTurn(kind = PromptTurnKind.ASSISTANT, content = tagContent))
                }
            }
        }

        // 合并连续的相同角色消息
        val mergedSegments = mutableListOf<PromptTurn>()
        var currentKind: PromptTurnKind? = null
        var currentContent = StringBuilder()
        var currentToolName: String? = null
        var currentMetadata: Map<String, Any?> = emptyMap()

        for (segment in segments) {
            val shouldMergeCurrent =
                segment.kind == currentKind &&
                    segment.kind !in setOf(PromptTurnKind.TOOL_CALL, PromptTurnKind.TOOL_RESULT)
            if (shouldMergeCurrent) {
                // 如果角色与当前角色相同，则合并内容
                currentContent.append("\n").append(segment.content)
            } else {
                // 角色不同，先保存当前内容（如果有）
                if (currentContent.isNotEmpty() && currentKind != null) {
                    mergedSegments.add(
                        PromptTurn(
                            kind = currentKind!!,
                            content = currentContent.toString().trim(),
                            toolName = currentToolName,
                            metadata = currentMetadata
                        )
                    )
                    currentContent.clear()
                }
                // 更新当前角色和内容
                currentKind = segment.kind
                currentToolName = segment.toolName
                currentMetadata = segment.metadata
                currentContent.append(segment.content)
            }
        }

        // 添加最后一条消息
        if (currentContent.isNotEmpty() && currentKind != null) {
            mergedSegments.add(
                PromptTurn(
                    kind = currentKind!!,
                    content = currentContent.toString().trim(),
                    toolName = currentToolName,
                    metadata = currentMetadata
                )
            )
        }

        // 将合并后的消息添加到对话历史
        conversationHistory.addAll(mergedSegments)
    }

    /** Data class for search-replace operations, used for JSON deserialization. */
    private data class SearchReplaceOperation(val search: String, val replace: String)

    /**
     * Flattens the hierarchical UI node structure into a simple, flat list of key elements.
     * This provides a much cleaner context for the AI to make decisions.
     */
    private fun flattenUiInfo(pageInfo: UIPageResultData): String {
        val clickableElements = mutableListOf<String>()
        val screenTexts = mutableListOf<String>()

        fun traverse(node: SimplifiedUINode) {
            // If the node is clickable, treat it as an atomic unit. We'll gather all text from
            // its entire subtree to form a comprehensive description for the AI.
            if (node.isClickable) {
                val parts = mutableListOf<String>()
                
                // Start by collecting standard properties like resource ID, class, and bounds.
                node.resourceId?.takeIf { it.isNotBlank() }?.let { parts.add("id: $it") }

                // --- NEW: Recursively find all text and content descriptions in the subtree ---
                val descriptiveTexts = mutableListOf<String>()
                fun findTextsRecursively(n: SimplifiedUINode) {
                    n.text?.takeIf { it.isNotBlank() }?.let { descriptiveTexts.add(it) }
                    n.contentDesc?.takeIf { it.isNotBlank() }?.let { descriptiveTexts.add(it) }
                    n.children.forEach(::findTextsRecursively)
                }
                findTextsRecursively(node)

                // Combine all found texts into a single descriptive string. This is crucial for
                // elements where the text label is in a child node of the clickable area.
                val combinedText = descriptiveTexts.distinct().joinToString(" | ")
                if (combinedText.isNotBlank()) {
                    // Using "desc" to signify this is a constructed description. Increased length.
                    parts.add("desc: \"${combinedText.replace("\"", "'").take(80)}\"")
                }
                // --- END NEW ---

                node.className?.let { parts.add("class: ${it.substringAfterLast('.')}") }
                node.bounds?.let { parts.add("bounds: ${it.replace(' ', ',')}") }

                // Only add the element if it has some identifiable information.
                if (parts.isNotEmpty()) {
                    clickableElements.add("[${parts.joinToString(", ")}]")
                }
                // Once an element is identified as clickable, we don't process its children separately.
            } else {
                // If the node is not clickable, add its text for general context and continue traversal.
                node.text?.takeIf { it.isNotBlank() }?.let {
                    screenTexts.add("\"${it.replace("\"", "'").take(70)}\"")
                }
                node.children.forEach(::traverse)
            }
        }

        traverse(pageInfo.uiElements)

        // Use distinct to remove duplicate text entries from non-clickable elements.
        val distinctScreenTexts = screenTexts.distinct()

        return """
        Package: ${pageInfo.packageName}
        Activity: ${pageInfo.activityName}
        Clickable Elements:
        ${clickableElements.joinToString("\n")}
        Screen Text (
        for context):
        ${distinctScreenTexts.joinToString("\n")}
        """.trimIndent()
    }

    private fun JSONObject.toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keysItr = this.keys()
        while (keysItr.hasNext()) {
            val key = keysItr.next()
            var value = this.get(key)
            if (value is JSONObject) {
                value = value.toMap()
            }
            if (value is JSONArray) {
                value = value.toList()
            }
            map[key] = value
        }
        return map
    }

    private fun JSONArray.toList(): List<Any> {
        val list = mutableListOf<Any>()
        for (i in 0 until this.length()) {
            var value = this.get(i)
            if (value is JSONObject) {
                value = value.toMap()
            }
            if (value is JSONArray) {
                value = value.toList()
            }
            list.add(value)
        }
        return list
    }

    private fun createToolFromJson(type: String, arg: Any): AITool {
        val parameters = mutableListOf<ToolParameter>()
        when (arg) {
            is Map<*, *> -> {
                arg.forEach { (key, value) ->
                    val stringValue = when (value) {
                        is Double -> {
                            // If the double has no fractional part, convert to Int string to avoid parse errors.
                            if (value % 1.0 == 0.0) {
                                value.toInt().toString()
                            } else {
                                value.toString()
                            }
                        }
                        else -> value.toString()
                    }
                    parameters.add(ToolParameter(key.toString(), stringValue))
                }
            }
            is String -> {
                 // Fallback for when the AI returns a raw string instead of a JSON object.
                 when (type) {
                     "press_key" -> parameters.add(ToolParameter("key_code", arg))
                     "set_input_text" -> parameters.add(ToolParameter("text", arg))
                     "start_app" -> parameters.add(ToolParameter("package_name", arg))
                 }
            }
        }
        return AITool(type, parameters)
    }

    /**
     * 构建waifu模式的特殊规则文本
     * @return 格式化的waifu规则文本，如果没有规则则返回空字符串
     */
    private suspend fun buildWaifuRulesText(): String {
        val activePrompt = activePromptManager.getActivePrompt()
        val waifuEnableEmoticons = waifuPreferences.waifuEnableEmoticonsFlow.first()
        val waifuEnableSelfie = waifuPreferences.waifuEnableSelfieFlow.first()
        val waifuCustomPrompt = waifuPreferences.waifuCustomPromptFlow.first()
        val waifuSelfiePrompt = waifuPreferences.waifuSelfiePromptFlow.first()
        val waifuRules = mutableListOf<String>()

        if (waifuEnableEmoticons) {
            // 动态获取当前可用的表情分组
            val availableCategories = try {
                customEmojiRepository.initializeBuiltinEmojis(activePrompt)
                customEmojiRepository.getAllCategories(activePrompt).first()
            } catch (e: Exception) {
                com.ai.assistance.operit.util.AppLogger.e("ConversationService", "获取表情分组失败", e)
                emptyList()
            }
            
            if (availableCategories.isNotEmpty()) {
                val emotionListText = availableCategories.joinToString(", ")
                waifuRules.add(FunctionalPrompts.waifuEmotionRule(emotionListText))
            } else {
                // 如果没有自定义表情，则不添加情绪规则，或明确告知没有可用表情
                waifuRules.add(FunctionalPrompts.waifuNoCustomEmojiRule())
            }
        }
        
        if (waifuEnableSelfie) {
            waifuRules.add(FunctionalPrompts.waifuSelfieRule(waifuSelfiePrompt))
        }

        if (waifuCustomPrompt.isNotBlank()) {
            waifuRules.add(FunctionalPrompts.waifuCustomPromptRule(waifuCustomPrompt))
        }

        return if (waifuRules.isNotEmpty()) {
            buildString {
                append("\n\n[Extra Rules]")
                waifuRules.forEach { rule ->
                    append("\n- $rule")
                }
            }
        } else ""
    }

    /**
     * 虚拟形象的 <mood> 标签规则，仅在语音头像环境下添加到系统提示中。
     * 会自动拼接当前头像启用的自定义 mood 类型。
     */
    private fun buildAvatarMoodRulesText(useEnglish: Boolean): String {
        val currentAvatarId = avatarRepository.currentAvatar.value?.id
        val currentConfig =
            avatarRepository.configs.value.firstOrNull { config ->
                config.id == currentAvatarId
            }

        val moodAnimationMapping = currentConfig?.getMoodAnimationMapping().orEmpty()
        val customMoodDefinitions =
            currentConfig?.getCustomMoodDefinitions()
                .orEmpty()
                .filter { definition ->
                    moodAnimationMapping[definition.key]?.isNotBlank() == true
                }

        return FunctionalPrompts.avatarMoodRulesText(
            customMoodDefinitions = customMoodDefinitions,
            useEnglish = useEnglish
        )
    }

    private fun shouldInjectMoodRules(promptFunctionType: PromptFunctionType): Boolean {
        if (promptFunctionType != PromptFunctionType.VOICE) {
            return false
        }

        val settings = avatarRepository.settings.value
        val currentAvatar = avatarRepository.currentAvatar.value
        return settings.isVoiceCallAvatarEnabled && currentAvatar != null
    }

    /**
     * Replaces placeholders in the system prompt with actual values.
     * This is necessary because the AI might return placeholders like {{user}} or {{char}}.
     *
     * @param prompt The system prompt containing placeholders.
     * @param aiName The actual AI name to replace {{char}}.
     * @return The prompt with placeholders replaced.
     */
    private suspend fun replacePromptPlaceholders(prompt: String, aiName: String): String {
        var finalPrompt = prompt
        
        val globalUserName = displayPreferencesManager.globalUserName.first() ?: "User"
        
        // 替换占位符
        finalPrompt = finalPrompt.replace("{{user}}", globalUserName)
        finalPrompt = finalPrompt.replace("{{char}}", aiName)
        
        return finalPrompt
    }

    /**
     * 翻译文本功能
     * @param text 要翻译的文本
     * @param multiServiceManager 多服务管理器
     * @return 翻译后的文本
     */
    suspend fun translateText(text: String, multiServiceManager: MultiServiceManager): String {
        val currentLanguage = LocaleUtils.getCurrentLanguage(context)
        
        // 根据当前语言确定目标语言
        val targetLanguage = when (currentLanguage) {
            LocaleUtils.LanguageCodes.CHINESE -> context.getString(R.string.conversation_language_chinese)
            LocaleUtils.LanguageCodes.ENGLISH -> "English"
            LocaleUtils.LanguageCodes.KOREAN -> "Korean"
            LocaleUtils.LanguageCodes.SPANISH -> "Spanish"
            LocaleUtils.LanguageCodes.MALAY -> "Malay"
            LocaleUtils.LanguageCodes.INDONESIAN -> "Indonesian"
            LocaleUtils.LanguageCodes.PORTUGUESE_BRAZIL -> "Portuguese (Brazil)"
            else -> context.getString(R.string.conversation_language_chinese) // 默认翻译为中文
        }
        
        val translationPrompt = """
${FunctionalPrompts.translationUserPrompt(targetLanguage, text)}
        """.trim()
        
        val chatHistory = listOf(
            PromptTurn(
                kind = PromptTurnKind.SYSTEM,
                content = FunctionalPrompts.translationSystemPrompt()
            )
        )
        
        val contentBuilder = StringBuilder()
        
        try {
            // 获取翻译功能的AIService实例
            val translationService = multiServiceManager.getServiceForFunction(FunctionType.TRANSLATION)
            
            // 获取模型参数
            val modelParameters = multiServiceManager.getModelParametersForFunction(FunctionType.TRANSLATION)
            
            val stream = translationService.sendMessage(
                context = context,
                chatHistory = chatHistory + PromptTurn(kind = PromptTurnKind.USER, content = translationPrompt),
                modelParameters = modelParameters
            )
            
            stream.collect { content ->
                contentBuilder.append(content)
            }
            
            return contentBuilder.toString().trim()
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * 自动生成工具包描述
     * @param pluginName 工具包名称
     * @param toolDescriptions 工具描述列表
     * @param multiServiceManager 多服务管理器
     * @return 生成的工具包描述
     */
    suspend fun generatePackageDescription(
        pluginName: String,
        toolDescriptions: List<String>,
        multiServiceManager: MultiServiceManager
    ): String {
        if (toolDescriptions.isEmpty()) {
            return ""
        }
        
        val toolList = toolDescriptions.joinToString("\n") { "- $it" }

        val useEnglish = LocaleUtils.getCurrentLanguage(context).lowercase().startsWith("en")
        val descriptionPrompt =
            FunctionalPrompts.packageDescriptionUserPrompt(
                pluginName = pluginName,
                toolList = toolList,
                useEnglish = useEnglish
            )

        val chatHistory =
            listOf(
                PromptTurn(
                    kind = PromptTurnKind.SYSTEM,
                    content = FunctionalPrompts.packageDescriptionSystemPrompt(useEnglish)
                )
            )
        
        val contentBuilder = StringBuilder()
        
        try {
            // 获取总结功能的AIService实例
            val summaryService = multiServiceManager.getServiceForFunction(FunctionType.SUMMARY)
            
            // 获取模型参数
            val modelParameters = multiServiceManager.getModelParametersForFunction(FunctionType.SUMMARY)
            
            val stream = summaryService.sendMessage(
                context = context,
                chatHistory = chatHistory + PromptTurn(kind = PromptTurnKind.USER, content = descriptionPrompt),
                modelParameters = modelParameters
            )
            
            stream.collect { content ->
                contentBuilder.append(content)
            }
            
            val result = ChatUtils.removeThinkingContent(contentBuilder.toString().trim())
            
            // 如果生成失败或内容为空，返回空字符串表示生成失败
            return if (result.isBlank()) {
                ""
            } else {
                result
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "生成工具包描述时出错", e)
            return ""
        }
    }

    /**
     * 使用识图模型分析图片
     * @param imagePath 图片路径
     * @param userIntent 用户意图，例如"这个图片里面有什么"、"图片的题目公式是什么"等
     * @param multiServiceManager 多服务管理器
     * @return AI分析结果
     */
    suspend fun analyzeImageWithIntent(
        imagePath: String,
        userIntent: String?,
        multiServiceManager: MultiServiceManager
    ): String {
        return try {
            val service = multiServiceManager.getServiceForFunction(FunctionType.IMAGE_RECOGNITION)
            
            // 添加图片到池子并获取ID
            val imageId = com.ai.assistance.operit.util.ImagePoolManager.addImage(imagePath)
            if (imageId == "error") {
                return "Failed to load image: $imagePath"
            }

            // 构建提示词，包含用户意图和图片链接
            val imageLink = MediaLinkBuilder.image(context, imageId)
            val prompt = if (userIntent.isNullOrBlank()) {
                "$imageLink\n${context.getString(R.string.conversation_analyze_image_prompt)}"
            } else {
                "$imageLink\n$userIntent"
            }
            
            // 获取模型参数
            val modelParameters = multiServiceManager.getModelParametersForFunction(FunctionType.IMAGE_RECOGNITION)
            
            // 调用AI服务分析图片
            val result = StringBuilder()
            service.sendMessage(
                context = context,
                chatHistory = listOf(PromptTurn(kind = PromptTurnKind.USER, content = prompt)),
                modelParameters = modelParameters
            ).collect { chunk ->
                result.append(chunk)
            }
            
            // 清理图片缓存
            com.ai.assistance.operit.util.ImagePoolManager.removeImage(imageId)
            
            ChatUtils.removeThinkingContent(result.toString()).trim()
        } catch (e: Exception) {
            AppLogger.e(TAG, "识图分析失败", e)
            "Image recognition failed: ${e.message}"
        }
    }

    suspend fun analyzeAudioWithIntent(
        audioPath: String,
        userIntent: String?,
        multiServiceManager: MultiServiceManager
    ): String {
        return try {
            val service = multiServiceManager.getServiceForFunction(FunctionType.AUDIO_RECOGNITION)

            val mimeType = android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(java.io.File(audioPath).extension.lowercase())
                ?: "audio/*"

            val mediaId = com.ai.assistance.operit.util.MediaPoolManager.addMedia(audioPath, mimeType)
            if (mediaId == "error") {
                return "Failed to load audio: $audioPath"
            }

            val audioLink = MediaLinkBuilder.audio(context, mediaId)
            val prompt = if (userIntent.isNullOrBlank()) {
                "$audioLink\n${context.getString(R.string.conversation_analyze_audio_prompt)}"
            } else {
                "$audioLink\n$userIntent"
            }

            val modelParameters = multiServiceManager.getModelParametersForFunction(FunctionType.AUDIO_RECOGNITION)

            val result = StringBuilder()
            service.sendMessage(
                context = context,
                chatHistory = listOf(PromptTurn(kind = PromptTurnKind.USER, content = prompt)),
                modelParameters = modelParameters
            ).collect { chunk ->
                result.append(chunk)
            }

            com.ai.assistance.operit.util.MediaPoolManager.removeMedia(mediaId)
            ChatUtils.removeThinkingContent(result.toString()).trim()
        } catch (e: Exception) {
            AppLogger.e(TAG, "音频识别失败", e)
            "Audio recognition failed: ${e.message}"
        }
    }

    suspend fun analyzeVideoWithIntent(
        videoPath: String,
        userIntent: String?,
        multiServiceManager: MultiServiceManager
    ): String {
        return try {
            val service = multiServiceManager.getServiceForFunction(FunctionType.VIDEO_RECOGNITION)

            val mimeType = android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(java.io.File(videoPath).extension.lowercase())
                ?: "video/*"

            val mediaId = com.ai.assistance.operit.util.MediaPoolManager.addMedia(videoPath, mimeType)
            if (mediaId == "error") {
                return "Failed to load video: $videoPath"
            }

            val videoLink = MediaLinkBuilder.video(context, mediaId)
            val prompt = if (userIntent.isNullOrBlank()) {
                "$videoLink\n${context.getString(R.string.conversation_analyze_video_prompt)}"
            } else {
                "$videoLink\n$userIntent"
            }

            val modelParameters = multiServiceManager.getModelParametersForFunction(FunctionType.VIDEO_RECOGNITION)

            val result = StringBuilder()
            service.sendMessage(
                context = context,
                chatHistory = listOf(PromptTurn(kind = PromptTurnKind.USER, content = prompt)),
                modelParameters = modelParameters
            ).collect { chunk ->
                result.append(chunk)
            }

            com.ai.assistance.operit.util.MediaPoolManager.removeMedia(mediaId)
            ChatUtils.removeThinkingContent(result.toString()).trim()
        } catch (e: Exception) {
            AppLogger.e(TAG, "视频识别失败", e)
            "Video recognition failed: ${e.message}"
        }
    }
}
