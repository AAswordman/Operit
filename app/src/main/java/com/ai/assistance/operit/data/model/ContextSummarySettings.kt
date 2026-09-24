package com.ai.assistance.operit.data.model

import kotlinx.serialization.Serializable

/**
 * 上下文总结配置。
 *
 * 历史上这些配置字段挂在 ModelConfigData 上，导致总结配置实际随模型而非角色卡；
 * 随后设置界面、提示词读取、触发阈值对"用哪份模型配置"产生了不一致的解析，
 * 出现界面改动运行时无效的问题。现在统一收敛到本类型，由角色卡或全局默认配置持有。
 */
@Serializable
data class ContextSummarySettings(
        val enableSummary: Boolean = DEFAULT_ENABLE_SUMMARY,
        val summaryTokenThreshold: Float = DEFAULT_SUMMARY_TOKEN_THRESHOLD,
        val enableSummaryByMessageCount: Boolean = DEFAULT_ENABLE_SUMMARY_BY_MESSAGE_COUNT,
        val summaryMessageCountThreshold: Int = DEFAULT_SUMMARY_MESSAGE_COUNT_THRESHOLD,
        val summaryCustomRules: String = "",
        val summarySectionOverrides: List<SummarySectionOverride> = emptyList(),
        val dialogueReviewEnabled: Boolean = true,
        val dialogueReviewTitle: String = ""
) {
    companion object {
        const val DEFAULT_ENABLE_SUMMARY = true
        const val DEFAULT_SUMMARY_TOKEN_THRESHOLD = 0.70f
        const val DEFAULT_ENABLE_SUMMARY_BY_MESSAGE_COUNT = true
        const val DEFAULT_SUMMARY_MESSAGE_COUNT_THRESHOLD = 16
    }
}
