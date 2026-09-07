package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.ToolParameterSchema
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.data.model.getModelByIndex
import com.ai.assistance.operit.data.model.getValidModelIndex
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.chat.hooks.toPromptTurns
import com.ai.assistance.operit.util.AssetCopyUtils
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.util.ImagePoolManager
import com.ai.assistance.operit.util.MediaPoolManager
import kotlinx.coroutines.CancellationException

enum class ModelConnectionTestType {
    CHAT,
    TOOL_CALL,
    IMAGE,
    AUDIO,
    VIDEO
}

data class ModelConnectionTestItem(
    val type: ModelConnectionTestType,
    val success: Boolean,
    val error: String? = null
)

data class ModelConnectionTestReport(
    val configId: String,
    val configName: String,
    val providerType: String,
    val requestedModelIndex: Int,
    val actualModelIndex: Int,
    val testedModelName: String,
    val items: List<ModelConnectionTestItem>
) {
    val success: Boolean
        get() = items.all { it.success }
}

object ModelConfigConnectionTester {
    /**
     * One complete tool-call round trip for the Tool Call probe: the user asks, the assistant calls
     * the tool, the tool answers.
     *
     * The turn kinds matter. The answer has to be a [PromptTurnKind.TOOL_RESULT] turn so that the
     * provider pairs it with the call instead of reporting the call as never answered, and the
     * probe has to open with a user turn because gateways that translate the OpenAI payload into
     * another protocol (Poe onto Anthropic, for instance) reject a conversation that starts with an
     * assistant message and leave its `tool_result` without a matching `tool_use`.
     */
    internal fun buildToolCallProbeHistory(toolName: String): List<PromptTurn> {
        val toolTagName = ChatMarkupRegex.generateRandomToolTagName()
        val toolResultTagName = ChatMarkupRegex.generateRandomToolResultTagName()
        return listOf(
            "system" to "You are a helpful assistant.",
            "user" to "Call the $toolName tool with the text \"ping\".",
            "assistant" to
                "<$toolTagName name=\"$toolName\"><param name=\"text\">ping</param></$toolTagName>",
            "tool_result" to
                "<$toolResultTagName name=\"$toolName\" status=\"success\"><content>pong</content></$toolResultTagName>"
        ).toPromptTurns()
    }

    suspend fun run(
        context: Context,
        modelConfigManager: ModelConfigManager,
        config: ModelConfigData,
        requestedModelIndex: Int = 0,
        onActiveServiceChanged: (AIService?) -> Unit = {}
    ): ModelConnectionTestReport {
        val actualModelIndex = getValidModelIndex(config.modelName, requestedModelIndex)
        val testedModelName = getModelByIndex(config.modelName, actualModelIndex)
        val configForTest = config.copy(modelName = testedModelName)
        val items = mutableListOf<ModelConnectionTestItem>()

        val service =
            AIServiceFactory.createService(
                config = configForTest,
                modelConfigManager = modelConfigManager,
                context = context
            )
        onActiveServiceChanged(service)

        try {
            val parameters = modelConfigManager.getModelParametersForConfig(configForTest.id)

            suspend fun runCase(type: ModelConnectionTestType, block: suspend () -> Unit) {
                val result =
                    try {
                        block()
                        Result.success(Unit)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                items.add(
                    ModelConnectionTestItem(
                        type = type,
                        success = result.isSuccess,
                        error = result.exceptionOrNull()?.message
                    )
                )
            }

            runCase(ModelConnectionTestType.CHAT) {
                service.sendMessage(
                    context,
                    listOf(PromptTurn(kind = PromptTurnKind.USER, content = "Hi")),
                    parameters,
                    stream = false,
                    enableRetry = false,
                    recordTokenUsage = false,
                ).collect { }
            }

            if (configForTest.enableToolCall) {
                runCase(ModelConnectionTestType.TOOL_CALL) {
                    val availableTools =
                        listOf(
                            ToolPrompt(
                                name = "echo",
                                description = "Echoes the provided text.",
                                parametersStructured =
                                    listOf(
                                        ToolParameterSchema(
                                            name = "text",
                                            type = "string",
                                            description = "Text to echo.",
                                            required = true
                                        )
                                    )
                            )
                        )

                    suspend fun runToolCallTest(toolName: String) {
                        service.sendMessage(
                            context,
                            buildToolCallProbeHistory(toolName),
                            parameters,
                            stream = false,
                            availableTools = availableTools,
                            enableRetry = false,
                            recordTokenUsage = false,
                        ).collect { }
                    }

                    runToolCallTest("echo")
                }
            }

            if (configForTest.enableDirectImageProcessing) {
                runCase(ModelConnectionTestType.IMAGE) {
                    val imageFile = AssetCopyUtils.copyAssetToCache(context, "test/1.jpg")
                    val imageId = ImagePoolManager.addImage(imageFile.absolutePath)
                    if (imageId == "error") {
                        throw IllegalStateException("Failed to create test image")
                    }
                    try {
                        val prompt =
                            buildString {
                                append(MediaLinkBuilder.image(context, imageId))
                                append("\n")
                                append(context.getString(R.string.conversation_analyze_image_prompt))
                            }
                        service.sendMessage(
                            context,
                            listOf(PromptTurn(kind = PromptTurnKind.USER, content = prompt)),
                            parameters,
                            stream = false,
                            enableRetry = false,
                            recordTokenUsage = false,
                        ).collect { }
                    } finally {
                        ImagePoolManager.removeImage(imageId)
                        runCatching { imageFile.delete() }
                    }
                }
            }

            if (configForTest.enableDirectAudioProcessing) {
                runCase(ModelConnectionTestType.AUDIO) {
                    val audioFile = AssetCopyUtils.copyAssetToCache(context, "test/1.mp3")
                    val audioId = MediaPoolManager.addMedia(audioFile.absolutePath, "audio/mpeg")
                    if (audioId == "error") {
                        throw IllegalStateException("Failed to create test audio")
                    }
                    try {
                        val prompt =
                            buildString {
                                append(MediaLinkBuilder.audio(context, audioId))
                                append("\n")
                                append(context.getString(R.string.conversation_analyze_audio_prompt))
                            }
                        service.sendMessage(
                            context,
                            listOf(PromptTurn(kind = PromptTurnKind.USER, content = prompt)),
                            parameters,
                            stream = false,
                            enableRetry = false,
                            recordTokenUsage = false,
                        ).collect { }
                    } finally {
                        MediaPoolManager.removeMedia(audioId)
                        runCatching { audioFile.delete() }
                    }
                }
            }

            if (configForTest.enableDirectVideoProcessing) {
                runCase(ModelConnectionTestType.VIDEO) {
                    val videoFile = AssetCopyUtils.copyAssetToCache(context, "test/1.mp4")
                    val videoId = MediaPoolManager.addMedia(videoFile.absolutePath, "video/mp4")
                    if (videoId == "error") {
                        throw IllegalStateException("Failed to create test video")
                    }
                    try {
                        val prompt =
                            buildString {
                                append(MediaLinkBuilder.video(context, videoId))
                                append("\n")
                                append(context.getString(R.string.conversation_analyze_video_prompt))
                            }
                        service.sendMessage(
                            context,
                            listOf(PromptTurn(kind = PromptTurnKind.USER, content = prompt)),
                            parameters,
                            stream = false,
                            enableRetry = false,
                            recordTokenUsage = false,
                        ).collect { }
                    } finally {
                        MediaPoolManager.removeMedia(videoId)
                        runCatching { videoFile.delete() }
                    }
                }
            }
        } catch (e: CancellationException) {
            runCatching { service.cancelStreaming() }
            throw e
        } catch (e: Exception) {
            if (items.none { it.type == ModelConnectionTestType.CHAT }) {
                items.add(
                    ModelConnectionTestItem(
                        type = ModelConnectionTestType.CHAT,
                        success = false,
                        error = e.message ?: "Unknown error"
                    )
                )
            }
        } finally {
            onActiveServiceChanged(null)
            service.release()
        }

        return ModelConnectionTestReport(
            configId = configForTest.id,
            configName = configForTest.name,
            providerType = configForTest.apiProviderTypeId,
            requestedModelIndex = requestedModelIndex,
            actualModelIndex = actualModelIndex,
            testedModelName = testedModelName,
            items = items
        )
    }
}
