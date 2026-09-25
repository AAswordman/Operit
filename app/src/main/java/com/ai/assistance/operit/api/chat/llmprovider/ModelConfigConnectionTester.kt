package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import android.util.Log
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

enum class ModelConnectionTestOutcome {
    PASSED,
    UNVERIFIED,
    FAILED
}

data class ModelConnectionTestItem(
    val type: ModelConnectionTestType,
    val outcome: ModelConnectionTestOutcome,
    val error: String? = null
) {
    val success: Boolean
        get() = outcome == ModelConnectionTestOutcome.PASSED
}

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
        get() = items.none { it.outcome == ModelConnectionTestOutcome.FAILED }

    val verified: Boolean
        get() = items.isNotEmpty() && items.all { it.outcome == ModelConnectionTestOutcome.PASSED }
}

object ModelConfigConnectionTester {
    private const val TAG = "ModelConfigTester"

    internal fun buildToolCallProbeHistory(toolName: String): List<PromptTurn> {
        val toolTagName = ChatMarkupRegex.generateRandomToolTagName()
        val toolResultTagName = ChatMarkupRegex.generateRandomToolResultTagName()
        return listOf(
            "system" to "You are a helpful assistant.",
            "user" to "Call the $toolName tool with the text \"ping\".",
            "assistant" to
                "<$toolTagName name=\"$toolName\"><param name=\"text\">ping