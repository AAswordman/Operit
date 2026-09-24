package com.ai.assistance.operit.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummarySettingsResolverTest {

    private val globalSettings =
        ContextSummarySettings(
            enableSummary = true,
            summaryTokenThreshold = 0.70f,
            summaryCustomRules = "全局规则"
        )

    private fun card(
        bindingMode: String,
        summary: ContextSummarySettings
    ) = CharacterCard(
        id = "card-1",
        name = "测试角色卡",
        summaryBindingMode = bindingMode,
        summary = summary
    )

    @Test
    fun customCardUsesCardSettings() {
        val cardSettings = ContextSummarySettings(summaryCustomRules = "角色卡规则")
        val resolved =
            SummarySettingsResolver.resolve(
                prompt = ActivePrompt.CharacterCard("card-1"),
                card = card(CharacterCardSummaryBindingMode.CUSTOM, cardSettings),
                globalSettings = globalSettings
            )

        assertEquals(cardSettings, resolved)
        assertTrue(
            SummarySettingsResolver.usesCardSettings(
                ActivePrompt.CharacterCard("card-1"),
                card(CharacterCardSummaryBindingMode.CUSTOM, cardSettings)
            )
        )
    }

    @Test
    fun followGlobalCardUsesGlobalSettings() {
        val cardSettings = ContextSummarySettings(summaryCustomRules = "角色卡规则")
        val prompt = ActivePrompt.CharacterCard("card-1")
        val resolved =
            SummarySettingsResolver.resolve(
                prompt = prompt,
                card = card(CharacterCardSummaryBindingMode.FOLLOW_GLOBAL, cardSettings),
                globalSettings = globalSettings
            )

        assertEquals(globalSettings, resolved)
        assertFalse(
            SummarySettingsResolver.usesCardSettings(
                prompt,
                card(CharacterCardSummaryBindingMode.FOLLOW_GLOBAL, cardSettings)
            )
        )
    }

    @Test
    fun unknownBindingModeFallsBackToGlobal() {
        val prompt = ActivePrompt.CharacterCard("card-1")
        val resolved =
            SummarySettingsResolver.resolve(
                prompt = prompt,
                card = card("anything-else", ContextSummarySettings(summaryCustomRules = "角色卡规则")),
                globalSettings = globalSettings
            )

        assertEquals(globalSettings, resolved)
    }

    @Test
    fun characterGroupUsesGlobalSettings() {
        val prompt = ActivePrompt.CharacterGroup("group-1")
        val resolved =
            SummarySettingsResolver.resolve(
                prompt = prompt,
                // 群聊会话即使传入角色卡也不应生效
                card = card(CharacterCardSummaryBindingMode.CUSTOM, ContextSummarySettings()),
                globalSettings = globalSettings
            )

        assertEquals(globalSettings, resolved)
        assertFalse(
            SummarySettingsResolver.usesCardSettings(
                prompt,
                card(CharacterCardSummaryBindingMode.CUSTOM, ContextSummarySettings())
            )
        )
    }

    @Test
    fun nullCardFallsBackToGlobal() {
        val prompt = ActivePrompt.CharacterCard("missing-card")
        val resolved =
            SummarySettingsResolver.resolve(
                prompt = prompt,
                card = null,
                globalSettings = globalSettings
            )

        assertEquals(globalSettings, resolved)
    }
}
