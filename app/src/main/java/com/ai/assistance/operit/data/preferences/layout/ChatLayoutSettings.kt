package com.ai.assistance.operit.data.preferences.layout

/** 全局聊天布局，不属于角色主题；保存时仍使用既有的五个偏好键。 */
data class ChatLayoutSettings(
    val chatSettingsButtonEndPadding: Float = 2f,
    val chatAreaHorizontalPadding: Float = 16f,
    val aiMarkdownLineHeightMultiplier: Float = 1f,
    val aiMarkdownLetterSpacing: Float = 0f,
    val aiMarkdownParagraphSpacing: Float = 12f,
) {
    fun validate() {
        require(chatSettingsButtonEndPadding.isFinite() && chatSettingsButtonEndPadding in 0f..50f)
        require(chatAreaHorizontalPadding.isFinite() && chatAreaHorizontalPadding in 0f..50f)
        require(
            aiMarkdownLineHeightMultiplier.isFinite() && aiMarkdownLineHeightMultiplier in 0.8f..2f
        )
        require(aiMarkdownLetterSpacing.isFinite() && aiMarkdownLetterSpacing in -1f..8f)
        require(aiMarkdownParagraphSpacing.isFinite() && aiMarkdownParagraphSpacing in 0f..48f)
    }
}
