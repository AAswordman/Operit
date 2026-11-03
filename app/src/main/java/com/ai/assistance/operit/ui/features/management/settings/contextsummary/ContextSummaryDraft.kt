package com.ai.assistance.operit.ui.features.management.settings.contextsummary

import com.ai.assistance.operit.data.config.model.ModelConfigDefaults

import com.ai.assistance.operit.core.config.FunctionalPrompts
import com.ai.assistance.operit.data.config.model.ModelConfigData
import com.ai.assistance.operit.data.config.model.SummarySectionConfig

internal enum class ContextSummaryValidation {
    CONTEXT_LENGTH, MAX_CONTEXT_LENGTH, CONTEXT_LIMIT, SUMMARY_THRESHOLD, MESSAGE_COUNT
}

internal data class ContextSummaryDraft(
    val contextLength: String,
    val maxContextLength: String,
    val enableSummary: Boolean,
    val thresholdPercent: String,
    val byMessageCount: Boolean,
    val messageCount: String,
    val rules: String,
    val previousReview: Boolean,
    val dialogueReview: Boolean,
    val reviewTitle: String,
    val sections: List<SummarySectionConfig>,
) {
    companion object {
        fun from(config: ModelConfigData, useEnglish: Boolean) =
            ContextSummaryDraft(
                contextLength = formatSummaryNumber(config.contextLength),
                maxContextLength = formatSummaryNumber(config.maxContextLength),
                enableSummary = config.enableSummary,
                thresholdPercent = formatSummaryNumber(config.summaryTokenThreshold * 100f),
                byMessageCount = config.enableSummaryByMessageCount,
                messageCount = config.summaryMessageCountThreshold.toString(),
                rules = config.summaryCustomRules,
                previousReview = config.enablePreviousSummaryReview,
                dialogueReview = config.enableSummaryDialogueReview,
                reviewTitle = config.summaryDialogueReviewTitle,
                sections = FunctionalPrompts.resolveSummarySections(config.summarySectionOverrides, useEnglish),
            )
    }

    fun validationError(): ContextSummaryValidation? {
        val context = contextLength.toFloatOrNull()
        val maximum = maxContextLength.toFloatOrNull()
        val threshold = thresholdPercent.toFloatOrNull()
        val messages = messageCount.toIntOrNull()
        return when {
            context == null || !context.isFinite() || context <= 0f -> ContextSummaryValidation.CONTEXT_LENGTH
            maximum == null || !maximum.isFinite() || maximum <= 0f -> ContextSummaryValidation.MAX_CONTEXT_LENGTH
            context > maximum -> ContextSummaryValidation.CONTEXT_LIMIT
            enableSummary && (threshold == null || !threshold.isFinite() || threshold <= 0f || threshold >= 100f) -> ContextSummaryValidation.SUMMARY_THRESHOLD
            enableSummary && byMessageCount && (messages == null || messages !in 1..ModelConfigDefaults.MAX_SUMMARY_MESSAGE_COUNT_THRESHOLD) -> ContextSummaryValidation.MESSAGE_COUNT
            else -> null
        }
    }

    // 只同步尚未编辑的字段，数据库回写不会覆盖继续输入中的草稿。
    fun mergeUnedited(previous: ContextSummaryDraft, incoming: ContextSummaryDraft) = copy(
        contextLength = mergeField(contextLength, previous.contextLength, incoming.contextLength),
        maxContextLength = mergeField(maxContextLength, previous.maxContextLength, incoming.maxContextLength),
        enableSummary = mergeField(enableSummary, previous.enableSummary, incoming.enableSummary),
        thresholdPercent = mergeField(thresholdPercent, previous.thresholdPercent, incoming.thresholdPercent),
        byMessageCount = mergeField(byMessageCount, previous.byMessageCount, incoming.byMessageCount),
        messageCount = mergeField(messageCount, previous.messageCount, incoming.messageCount),
        rules = mergeField(rules, previous.rules, incoming.rules),
        previousReview = mergeField(previousReview, previous.previousReview, incoming.previousReview),
        dialogueReview = mergeField(dialogueReview, previous.dialogueReview, incoming.dialogueReview),
        reviewTitle = mergeField(reviewTitle, previous.reviewTitle, incoming.reviewTitle),
        sections = mergeField(sections, previous.sections, incoming.sections),
    )
}

private fun <T> mergeField(local: T, previous: T, incoming: T): T =
    if (local == previous) incoming else local

private fun formatSummaryNumber(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else value.toString()
