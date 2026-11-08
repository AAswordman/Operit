package com.ai.assistance.operit.data.preferences

/** 角色卡编辑中的发言草稿，沿用现有 Waifu 配置项和默认值。 */
data class WaifuSettings(
    val enabled: Boolean = WaifuPreferences.DEFAULT_ENABLE_WAIFU_MODE,
    val charDelay: Int = WaifuPreferences.DEFAULT_WAIFU_CHAR_DELAY,
    val removePunctuation: Boolean = WaifuPreferences.DEFAULT_WAIFU_REMOVE_PUNCTUATION,
    val enableEmoticons: Boolean = WaifuPreferences.DEFAULT_WAIFU_ENABLE_EMOTICONS,
    val enableSelfie: Boolean = WaifuPreferences.DEFAULT_WAIFU_ENABLE_SELFIE,
    val enableMergeSend: Boolean = WaifuPreferences.DEFAULT_WAIFU_ENABLE_MERGE_SEND,
    val mergeSendDelayMs: Int = WaifuPreferences.DEFAULT_WAIFU_MERGE_SEND_DELAY_MS,
    val customPrompt: String = WaifuPreferences.DEFAULT_WAIFU_CUSTOM_PROMPT,
    val selfiePrompt: String = WaifuPreferences.DEFAULT_WAIFU_SELFIE_PROMPT,
)
