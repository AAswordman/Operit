package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.ModelOption
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexModelPolicyTest {
    @Test
    fun explicitOpenCodeModelsAreAllowed() {
        assertTrue(CodexModelPolicy.allows("gpt-5.4"))
        assertTrue(CodexModelPolicy.allows("gpt-5.3-codex-spark"))
    }

    @Test
    fun fastGptVersionsWithSuffixAreAllowed() {
        assertTrue(CodexModelPolicy.allows("gpt-5.6-luna"))
        assertTrue(CodexModelPolicy.allows("gpt-5.7-codex"))
        assertTrue(CodexModelPolicy.allows("gpt-5.6-luna", "fast"))
        assertFalse(CodexModelPolicy.allows("gpt-5.6-luna", "pro"))
    }

    @Test
    fun disallowedAndBareVersionModelsAreRemoved() {
        assertFalse(CodexModelPolicy.allows("gpt-5.5-pro"))
        assertFalse(CodexModelPolicy.allows("gpt-5.6"))
        assertFalse(CodexModelPolicy.allows("claude-3"))
    }

    @Test
    fun fastVariantUsesCanonicalApiModelAndPriorityTier() {
        val request = JSONObject().put("model", "gpt-5.6-luna-fast")

        CodexModelVariant.applyRequestParameters(request, "gpt-5.6-luna-fast")

        assertEquals("gpt-5.6-luna", request.getString("model"))
        assertEquals("priority", request.getString("service_tier"))
    }

    @Test
    fun baseModelDoesNotAddVariantParameters() {
        val request = JSONObject().put("model", "gpt-5.6-luna")

        CodexModelVariant.applyRequestParameters(request, "gpt-5.6-luna")

        assertEquals("gpt-5.6-luna", request.getString("model"))
        assertFalse(request.has("service_tier"))
    }

    @Test
    fun policyOnlyKeepsCanonicalIds() {
        val models = listOf(
            ModelOption("gpt-5.6-luna", "Luna"),
            ModelOption("gpt-5.5-pro", "Pro"),
            ModelOption("gpt-5.4", "5.4"),
        )

        assertTrue(
            models.filter { CodexModelPolicy.allows(it.id) }.map { it.id } ==
                listOf("gpt-5.6-luna", "gpt-5.4")
        )
    }
}
