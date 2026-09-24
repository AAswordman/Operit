package com.ai.assistance.operit.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextSummarySettingsTest {

    @Test fun `default matches legacy model config defaults`() {
        val settings = ContextSummarySettings()
        assertTrue(settings.enableSummary)
        assertEquals(0.70f, settings.summaryTokenThreshold)
        assertTrue(settings.enableSummaryByMessageCount)
        assertEquals(16, settings.summaryMessageCountThreshold)
        assertEquals("", settings.summaryCustomRules)
        assertTrue(settings.summarySectionOverrides.isEmpty())
        assertTrue(settings.dialogueReviewEnabled)
        assertEquals("", settings.dialogueReviewTitle)
    }

    @Test
    fun serializationRoundTripPreservesAllFields() {
        val settings = ContextSummarySettings(
            enableSummary = false,
            summaryTokenThreshold = 0.5f,
            enableSummaryByMessageCount = false,
            summaryMessageCountThreshold = 8,
            summaryCustomRules = "custom rules",
            summarySectionOverrides = listOf(
                SummarySectionOverride(
                    id = "core_task",
                    enabled = false,
                    title = "工程状态",
                    instruction = "简述工程状态"
                )
            ),
            dialogueReviewEnabled = false,
            dialogueReviewTitle = "回顾"
        )

        val encoded = Json.encodeToString(ContextSummarySettings.serializer(), settings)
        val decoded = Json.decodeFromString(ContextSummarySettings.serializer(), encoded)

        assertEquals(settings, decoded)
    }

    @Test fun `serialization defaults produce valid json without unknown keys`() {
        val encoded = Json.encodeToString(ContextSummarySettings.serializer(), ContextSummarySettings())
        // 旧版本数据缺少新字段时，反序列化必须仍能工作。
        val decoded = Json {
            ignoreUnknownKeys = true
        }.decodeFromString(ContextSummarySettings.serializer(), encoded)
        assertEquals(ContextSummarySettings(), decoded)
    }

    @Test fun `summary binding mode normalize accepts only CUSTOM`() {
        assertEquals(
            CharacterCardSummaryBindingMode.CUSTOM,
            CharacterCardSummaryBindingMode.normalize("CUSTOM")
        )
        assertEquals(
            CharacterCardSummaryBindingMode.FOLLOW_GLOBAL,
            CharacterCardSummaryBindingMode.normalize("FOLLOW_GLOBAL")
        )
        assertEquals(
            CharacterCardSummaryBindingMode.FOLLOW_GLOBAL,
            CharacterCardSummaryBindingMode.normalize(null)
        )
        assertEquals(
            CharacterCardSummaryBindingMode.FOLLOW_GLOBAL,
            CharacterCardSummaryBindingMode.normalize("anything-else")
        )
    }

    @Test fun `character card defaults are follow global with default settings`() {
        val card = CharacterCard(id = "card-1", name = "测试角色卡")
        assertEquals(
            CharacterCardSummaryBindingMode.FOLLOW_GLOBAL,
            card.summaryBindingMode
        )
        assertEquals(ContextSummarySettings(), card.summary)
    }

    @Test fun `character card copy keeps summary binding and settings`() {
        val card = CharacterCard(
            id = "card-1",
            name = "测试角色卡",
            summaryBindingMode = CharacterCardSummaryBindingMode.CUSTOM,
            summary = ContextSummarySettings(summaryCustomRules = "专属规则")
        ).copy(name = "改名")

        assertEquals(CharacterCardSummaryBindingMode.CUSTOM, card.summaryBindingMode)
        assertEquals("专属规则", card.summary.summaryCustomRules)
    }

    @Test fun `explicit default equals implicit default`() {
        assertEquals(
            ContextSummarySettings(),
            ContextSummarySettings(
                enableSummary = true,
                summaryTokenThreshold = 0.70f,
                enableSummaryByMessageCount = true,
                summaryMessageCountThreshold = 16,
                summaryCustomRules = "",
                summarySectionOverrides = emptyList(),
                dialogueReviewEnabled = true,
                dialogueReviewTitle = ""
            )
        )
        assertFalse(ContextSummarySettings() == ContextSummarySettings(enableSummary = false))
    }
}
