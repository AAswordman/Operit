package com.ai.assistance.operit.core.chat.hooks

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the prompt-hook dispatch layer used by a normal chat request
 * (`EnhancedAIService.sendMessage` dispatches `dispatchSystemPromptComposeHooks`
 * and `dispatchPromptFinalizeHooks`), guarding issue #1258 regression:
 * registered hooks must actually run, mutations must compose in order, and a
 * failing hook must not break the rest of the chain.
 */
class PromptHookRegistryTest {

    private fun composeHook(
        id: String,
        handler: (PromptHookContext) -> PromptHookMutation?
    ): SystemPromptComposeHook = object : SystemPromptComposeHook {
        override val id: String = id
        override fun onEvent(context: PromptHookContext): PromptHookMutation? = handler(context)
    }

    private fun finalizeHook(
        id: String,
        handler: (PromptHookContext) -> PromptHookMutation?
    ): PromptFinalizeHook = object : PromptFinalizeHook {
        override val id: String = id
        override fun onEvent(context: PromptHookContext): PromptHookMutation? = handler(context)
    }

    @Test
    fun `system prompt compose hooks run in registration order and merge mutations`() {
        val hookA = composeHook("a") { PromptHookMutation(systemPrompt = "BASE+a") }
        val hookB = composeHook("b") { ctx ->
            PromptHookMutation(systemPrompt = (ctx.systemPrompt ?: "") + "+b")
        }
        PromptHookRegistry.registerSystemPromptComposeHook(hookA)
        PromptHookRegistry.registerSystemPromptComposeHook(hookB)
        try {
            val result = PromptHookRegistry.dispatchSystemPromptComposeHooks(
                PromptHookContext(stage = "after_compose_system_prompt", systemPrompt = "BASE")
            )
            assertEquals("BASE+a+b", result.systemPrompt)
        } finally {
            PromptHookRegistry.unregisterSystemPromptComposeHook("a")
            PromptHookRegistry.unregisterSystemPromptComposeHook("b")
        }
    }

    @Test
    fun `prompt finalize hooks apply processedInput on the chat send path`() {
        val hook = finalizeHook("finalize") { ctx ->
            PromptHookMutation(processedInput = "[tagged] " + (ctx.processedInput ?: ""))
        }
        PromptHookRegistry.registerPromptFinalizeHook(hook)
        try {
            val result = PromptHookRegistry.dispatchPromptFinalizeHooks(
                PromptHookContext(stage = "before_finalize_prompt", processedInput = "hello")
            )
            assertEquals("[tagged] hello", result.processedInput)
        } finally {
            PromptHookRegistry.unregisterPromptFinalizeHook("finalize")
        }
    }

    @Test
    fun `a throwing hook is skipped without breaking later hooks`() {
        val bad = composeHook("bad") { throw IllegalStateException("boom") }
        val good = composeHook("good") { PromptHookMutation(systemPrompt = "BASE+good") }
        PromptHookRegistry.registerSystemPromptComposeHook(bad)
        PromptHookRegistry.registerSystemPromptComposeHook(good)
        try {
            val result = PromptHookRegistry.dispatchSystemPromptComposeHooks(
                PromptHookContext(stage = "after_compose_system_prompt", systemPrompt = "BASE")
            )
            assertEquals("BASE+good", result.systemPrompt)
        } finally {
            PromptHookRegistry.unregisterSystemPromptComposeHook("bad")
            PromptHookRegistry.unregisterSystemPromptComposeHook("good")
        }
    }

    @Test
    fun `null mutation fields preserve the current context values`() {
        val hook = composeHook("partial") { PromptHookMutation(toolPrompt = "TOOLS") }
        PromptHookRegistry.registerSystemPromptComposeHook(hook)
        try {
            val result = PromptHookRegistry.dispatchSystemPromptComposeHooks(
                PromptHookContext(
                    stage = "after_compose_system_prompt",
                    systemPrompt = "BASE",
                    processedInput = "keep-me",
                    metadata = mapOf("existing" to 1)
                )
            )
            assertEquals("BASE", result.systemPrompt)
            assertEquals("keep-me", result.processedInput)
            assertEquals("TOOLS", result.toolPrompt)
            assertEquals(1, result.metadata["existing"])
        } finally {
            PromptHookRegistry.unregisterSystemPromptComposeHook("partial")
        }
    }

    @Test
    fun `metadata from a hook is shallow merged into the current metadata`() {
        val hook = composeHook("meta") {
            PromptHookMutation(metadata = mapOf("added" to "x", "existing" to 2))
        }
        PromptHookRegistry.registerSystemPromptComposeHook(hook)
        try {
            val result = PromptHookRegistry.dispatchSystemPromptComposeHooks(
                PromptHookContext(
                    stage = "after_compose_system_prompt",
                    metadata = mapOf("existing" to 1)
                )
            )
            assertEquals("x", result.metadata["added"])
            assertEquals(2, result.metadata["existing"])
        } finally {
            PromptHookRegistry.unregisterSystemPromptComposeHook("meta")
        }
    }

    @Test
    fun `dispatch with no registered hooks returns the context unchanged`() {
        val context = PromptHookContext(stage = "before_finalize_prompt", processedInput = "untouched")
        val result = PromptHookRegistry.dispatchPromptFinalizeHooks(context)
        assertEquals("untouched", result.processedInput)
        assertEquals("before_finalize_prompt", result.stage)
    }

    @Test
    fun `registering the same hook id twice keeps only the latest`() {
        val first = composeHook("dup") { PromptHookMutation(systemPrompt = "first") }
        val second = composeHook("dup") { PromptHookMutation(systemPrompt = "second") }
        PromptHookRegistry.registerSystemPromptComposeHook(first)
        PromptHookRegistry.registerSystemPromptComposeHook(second)
        try {
            val result = PromptHookRegistry.dispatchSystemPromptComposeHooks(
                PromptHookContext(stage = "after_compose_system_prompt", systemPrompt = "BASE")
            )
            assertEquals("second", result.systemPrompt)
        } finally {
            PromptHookRegistry.unregisterSystemPromptComposeHook("dup")
        }
        // After unregistering, dispatch must no longer invoke it.
        val after = PromptHookRegistry.dispatchSystemPromptComposeHooks(
            PromptHookContext(stage = "after_compose_system_prompt", systemPrompt = "BASE")
        )
        assertEquals("BASE", after.systemPrompt)
    }
}
