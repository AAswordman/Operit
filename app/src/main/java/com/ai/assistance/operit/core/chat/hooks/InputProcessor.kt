package com.ai.assistance.operit.core.chat.hooks

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.buildActivePromptHookMetadata
import com.ai.assistance.operit.core.chat.hooks.PromptHookContext
import com.ai.assistance.operit.core.chat.hooks.PromptHookRegistry

/**
 * Utility class for processing user input
 */
object InputProcessor {
    
    /**
     * Process user input with a small delay to show processing feedback
     * 
     * @param input The input text to process
     * @return The processed input text
     */
    suspend fun processUserInput(
        context: Context,
        input: String,
        chatId: String? = null,
        roleCardId: String? = null,
        onHookTimeout: ((String) -> Unit)? = null
    ): String {
        val activePromptMetadata = buildActivePromptHookMetadata(context, chatId, roleCardId)
        val beforeContext =
            PromptHookRegistry.dispatchPromptInputHooks(
                PromptHookContext(
                    stage = "before_process",
                    chatId = chatId,
                    rawInput = input,
                    processedInput = input,
                    metadata = activePromptMetadata,
                    onHookTimeout = onHookTimeout
                )
            )
        val processedInput = beforeContext.processedInput ?: beforeContext.rawInput ?: input
        val afterContext =
            PromptHookRegistry.dispatchPromptInputHooks(
                beforeContext.copy(
                    stage = "after_process",
                    processedInput = processedInput
                )
            )
        return afterContext.processedInput ?: processedInput
    }
}
