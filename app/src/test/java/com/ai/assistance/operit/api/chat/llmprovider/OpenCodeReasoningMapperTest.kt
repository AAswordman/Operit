package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenCodeReasoningMapperTest {
    @Test
    fun sixDeclaredValuesDropNoneAndMapOneToOne() {
        val values = listOf<String?>(null, "low", "medium", "high", "xhigh", "max")

        assertEquals(
            listOf("low", "medium", "high", "xhigh", "max"),
            (1..5).map { OpenCodeReasoningMapper.effortForQuality(values, it) }
        )
    }

    @Test
    fun threeDeclaredValuesUseTwoTwoOneDistribution() {
        val values = listOf<String?>("low", "high", "max")

        assertEquals(
            listOf("low", "low", "high", "high", "max"),
            (1..5).map { OpenCodeReasoningMapper.effortForQuality(values, it) }
        )
    }

    @Test
    fun fourDeclaredValuesUseOneTwoOneOneDistribution() {
        val values = listOf<String?>("low", "medium", "high", "max")

        assertEquals(
            listOf("low", "medium", "medium", "high", "max"),
            (1..5).map { OpenCodeReasoningMapper.effortForQuality(values, it) }
        )
    }

    @Test
    fun twoDeclaredValuesUseAStableLowerAndUpperSplit() {
        val values = listOf<String?>("low", "max")

        assertEquals(
            listOf("low", "low", "max", "max", "max"),
            (1..5).map { OpenCodeReasoningMapper.effortForQuality(values, it) }
        )
    }

    @Test
    fun effortWithoutNoneLeavesThinkingOffAsAnUnsetVariant() {
        val capability = OpenCodeReasoningCapability(
            reasoning = true,
            options = listOf(OpenCodeReasoningOption.Effort(listOf("low", "high"))),
            outputLimit = 64_000
        )

        assertNull(OpenCodeReasoningMapper.select(capability, enableThinking = false, qualityLevel = 3))
    }

    @Test
    fun toggleHasTheSameVariantAtEveryQuality() {
        val capability = OpenCodeReasoningCapability(
            reasoning = true,
            options = listOf(OpenCodeReasoningOption.Toggle),
            outputLimit = 64_000
        )

        val selected = (1..5).map {
            OpenCodeReasoningMapper.select(capability, enableThinking = true, qualityLevel = it)
        }
        assertEquals(List(5) { OpenCodeReasoningVariant.Toggle(true) }, selected)
        assertEquals(
            OpenCodeReasoningVariant.Toggle(false),
            OpenCodeReasoningMapper.select(capability, enableThinking = false, qualityLevel = 3)
        )
    }

    @Test
    fun budgetOptionsMirrorOpenCodeHighAndMaxVariants() {
        val capability = OpenCodeReasoningCapability(
            reasoning = true,
            options = listOf(
                OpenCodeReasoningOption.Toggle,
                OpenCodeReasoningOption.BudgetTokens(min = null, max = 81_920)
            ),
            outputLimit = 65_536
        )

        assertEquals(
            OpenCodeReasoningVariant.BudgetTokens(32_768),
            OpenCodeReasoningMapper.select(capability, enableThinking = true, qualityLevel = 1)
        )
        assertEquals(
            OpenCodeReasoningVariant.BudgetTokens(65_535),
            OpenCodeReasoningMapper.select(capability, enableThinking = true, qualityLevel = 5)
        )
    }
}
