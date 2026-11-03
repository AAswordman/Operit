package com.ai.assistance.operit.ui.features.management.settings.contextsummary

import com.ai.assistance.operit.data.config.model.SummarySectionConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContextSummaryDraftTest {
    private fun draft() = ContextSummaryDraft(
        contextLength = "96",
        maxContextLength = "128",
        enableSummary = true,
        thresholdPercent = "70",
        byMessageCount = true,
        messageCount = "16",
        rules = "原有规则",
        previousReview = false,
        dialogueReview = true,
        reviewTitle = "对话回顾",
        sections = listOf(SummarySectionConfig(id = "core", title = "任务状态")),
    )

    @Test
    fun databaseEchoDoesNotOverwriteContinuedTyping() {
        val previous = draft()
        val local = previous.copy(rules = "保存后继续输入的内容", contextLength = "80")
        val incoming = previous.copy(rules = "先前保存的内容", thresholdPercent = "75", previousReview = true)
        val merged = local.mergeUnedited(previous, incoming)
        assertEquals("保存后继续输入的内容", merged.rules)
        assertEquals("80", merged.contextLength)
        assertEquals("75", merged.thresholdPercent)
        assertEquals(true, merged.previousReview)
    }

    @Test
    fun uneditedDraftTracksExternalConfigurationChanges() {
        val previous = draft()
        val incoming = previous.copy(contextLength = "64", reviewTitle = "新的回顾标题")
        assertEquals(incoming, previous.mergeUnedited(previous, incoming))
    }

    @Test
    fun editingSectionDoesNotLoseItsTitleWhenAnotherSettingIsSaved() {
        val previous = draft()
        val local = previous.copy(sections = listOf(previous.sections.single().copy(title = "正在编辑的标题")))
        val incoming = previous.copy(previousReview = true)
        val merged = local.mergeUnedited(previous, incoming)
        assertEquals("正在编辑的标题", merged.sections.single().title)
        assertEquals(true, merged.previousReview)
    }

    @Test
    fun disablingSummaryDoesNotRequireItsInactiveInputs() {
        assertNull(draft().copy(enableSummary = false, thresholdPercent = "", messageCount = "").validationError())
        assertNull(draft().copy(byMessageCount = false, messageCount = "").validationError())
    }

    @Test
    fun nonFiniteAndOutOfRangeValuesAreRejected() {
        assertEquals(ContextSummaryValidation.CONTEXT_LENGTH, draft().copy(contextLength = "NaN").validationError())
        assertEquals(ContextSummaryValidation.MAX_CONTEXT_LENGTH, draft().copy(maxContextLength = "Infinity").validationError())
        assertEquals(ContextSummaryValidation.SUMMARY_THRESHOLD, draft().copy(thresholdPercent = "Infinity").validationError())
        assertEquals(ContextSummaryValidation.SUMMARY_THRESHOLD, draft().copy(thresholdPercent = "0").validationError())
        assertEquals(ContextSummaryValidation.SUMMARY_THRESHOLD, draft().copy(thresholdPercent = "100").validationError())
    }

    @Test
    fun contextLengthCannotExceedApiMaximum() {
        assertEquals(ContextSummaryValidation.CONTEXT_LIMIT, draft().copy(contextLength = "129").validationError())
        assertNull(draft().copy(contextLength = "128").validationError())
    }

    @Test
    fun activeMessageThresholdAcceptsOneThroughFiftyOnly() {
        assertEquals(ContextSummaryValidation.MESSAGE_COUNT, draft().copy(messageCount = "0").validationError())
        assertEquals(ContextSummaryValidation.MESSAGE_COUNT, draft().copy(messageCount = "1.5").validationError())
        assertNull(draft().copy(messageCount = "1").validationError())
        assertNull(draft().copy(messageCount = "20").validationError())
        assertNull(draft().copy(messageCount = "21").validationError())
        assertNull(draft().copy(messageCount = "50").validationError())
        assertEquals(ContextSummaryValidation.MESSAGE_COUNT, draft().copy(messageCount = "51").validationError())
    }

}
