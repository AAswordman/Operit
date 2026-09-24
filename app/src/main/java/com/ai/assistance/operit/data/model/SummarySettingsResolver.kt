package com.ai.assistance.operit.data.model

/**
 * 上下文总结配置的解析核心。
 *
 * 从 ApiConfigDelegate 抽出为纯函数：界面、运行时提示词、触发阈值都汇合到同一份判定，
 * 不会再出现不同链路解析出不同配置。判定规则：
 * 角色卡绑定模式为 CUSTOM 时用卡内 summary；FOLLOW_GLOBAL、群聊会话与角色卡读取失败时
 * 用全局默认总结配置。
 */
object SummarySettingsResolver {

    /** 是否使用角色卡内的总结配置。 */
    fun usesCardSettings(prompt: ActivePrompt, card: CharacterCard?): Boolean {
        return card != null &&
            prompt is ActivePrompt.CharacterCard &&
            CharacterCardSummaryBindingMode.normalize(card.summaryBindingMode) ==
                CharacterCardSummaryBindingMode.CUSTOM
    }

    fun resolve(
        prompt: ActivePrompt,
        card: CharacterCard?,
        globalSettings: ContextSummarySettings
    ): ContextSummarySettings {
        return if (card != null && usesCardSettings(prompt, card)) {
            card.summary
        } else {
            globalSettings
        }
    }
}
