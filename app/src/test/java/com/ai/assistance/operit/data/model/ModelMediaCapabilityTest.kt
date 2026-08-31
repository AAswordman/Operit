package com.ai.assistance.operit.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 覆盖 issue #976：同一模型配置里混放纯文本模型和视觉模型时，
 * 「模型支持识图」不能再按配置级一刀切，必须落到实际选中的那个模型上。
 */
class ModelMediaCapabilityTest {

    @Test
    fun mixedConfig_textChatModel_doesNotClaimVision() {
        val config = mixedVisionConfig()

        // CHAT 绑定到纯文本模型（索引1）
        assertFalse(config.supportsDirectImageProcessing(TEXT_MODEL_INDEX))
    }

    @Test
    fun mixedConfig_visionModel_stillProcessesImagesDirectly() {
        val config = mixedVisionConfig()

        // IMAGE_RECOGNITION 绑定到视觉模型（索引3）
        assertTrue(config.supportsDirectImageProcessing(VISION_MODEL_INDEX))
    }

    @Test
    fun singleModelConfig_isUnaffected() {
        val config =
            ModelConfigData(
                id = "qwenvl",
                name = "Qwenvl",
                modelName = "qwen3-vl-flash",
                enableDirectImageProcessing = true
            )

        assertTrue(config.supportsDirectImageProcessing())
        assertTrue(config.supportsDirectImageProcessing(0))
    }

    @Test
    fun multiModelConfigWithoutDeclaration_keepsLegacyBehaviour() {
        val config =
            ModelConfigData(
                id = "legacy",
                name = "Legacy",
                modelName = "gpt-4o,gpt-4o-mini",
                enableDirectImageProcessing = true
            )

        assertTrue(config.supportsDirectImageProcessing(0))
        assertTrue(config.supportsDirectImageProcessing(1))
    }

    @Test
    fun configSwitchOff_overridesPerModelDeclaration() {
        val config = mixedVisionConfig().copy(enableDirectImageProcessing = false)

        assertFalse(config.supportsDirectImageProcessing(VISION_MODEL_INDEX))
    }

    @Test
    fun outOfRangeIndex_fallsBackToFirstModel() {
        val config = mixedVisionConfig()

        // 越界索引与索引0一致：第一个模型是纯文本模型
        assertFalse(config.supportsDirectImageProcessing(99))
        assertFalse(config.supportsDirectImageProcessing(-1))
    }

    @Test
    fun declarationMatchesModelNameCaseInsensitively() {
        val config =
            ModelConfigData(
                id = "case",
                name = "Case",
                modelName = "Qwen3-VL-Flash,deepseek-v4-pro",
                enableDirectImageProcessing = true,
                directImageModels = "qwen3-vl-flash"
            )

        assertTrue(config.supportsDirectImageProcessing(0))
        assertFalse(config.supportsDirectImageProcessing(1))
    }

    @Test
    fun staleDeclaration_doesNotLeakCapability() {
        val config =
            ModelConfigData(
                id = "stale",
                name = "Stale",
                modelName = "deepseek-v4-pro",
                enableDirectImageProcessing = true,
                directImageModels = "qwen3-vl-flash"
            )

        assertFalse(config.supportsDirectImageProcessing(0))
    }

    @Test
    fun audioAndVideoDeclarationsAreIndependent() {
        val config =
            ModelConfigData(
                id = "media",
                name = "Media",
                modelName = "text-only,omni",
                enableDirectAudioProcessing = true,
                enableDirectVideoProcessing = true,
                directAudioModels = "omni",
                directVideoModels = "omni"
            )

        assertFalse(config.supportsDirectAudioProcessing(0))
        assertFalse(config.supportsDirectVideoProcessing(0))
        assertTrue(config.supportsDirectAudioProcessing(1))
        assertTrue(config.supportsDirectVideoProcessing(1))
        // 未开启的图片能力不受影响
        assertFalse(config.supportsDirectImageProcessing(1))
    }

    @Test
    fun retainCapableModels_emptyDeclarationMeansEveryModel() {
        assertEquals(listOf("a", "b"), retainCapableModels("a,b", ""))
    }

    @Test
    fun retainCapableModels_followsModelListOrderAndDropsUnknown() {
        assertEquals(listOf("a", "c"), retainCapableModels("a,b,c", "c, removed ,a"))
    }

    @Test
    fun normalizeCapableModels_collapsesFullCoverageToEmpty() {
        assertEquals("", normalizeCapableModels("a,b", "b,a"))
        assertEquals("", normalizeCapableModels("a,b", ""))
    }

    @Test
    fun normalizeCapableModels_keepsSubsetInModelListOrder() {
        assertEquals("a,c", normalizeCapableModels("a,b,c", "c,a"))
    }

    private fun mixedVisionConfig(): ModelConfigData =
        ModelConfigData(
            id = "qwen",
            name = "Qwen",
            modelName = "deepseek-v4-flash-0731,deepseek-v4-pro-0813,qwen-max,qwen3-vl-flash",
            enableDirectImageProcessing = true,
            directImageModels = "qwen3-vl-flash"
        )

    private companion object {
        const val TEXT_MODEL_INDEX = 1
        const val VISION_MODEL_INDEX = 3
    }
}
