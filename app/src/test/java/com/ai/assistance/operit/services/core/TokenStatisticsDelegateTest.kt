package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TokenStatisticsDelegateTest {
    private lateinit var scope: CoroutineScope
    private lateinit var statistics: TokenStatisticsDelegate
    private var previousSystemLog = true
    private var previousFileLog = true

    @Before
    fun setUp() {
        previousSystemLog = AppLogger.enableSystemLog
        previousFileLog = AppLogger.enableFileLogging
        AppLogger.enableSystemLog = false
        AppLogger.enableFileLogging = false
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        statistics = TokenStatisticsDelegate(scope) { null }
        statistics.setActiveChatId("chat")
    }

    @After
    fun tearDown() {
        scope.cancel()
        AppLogger.enableSystemLog = previousSystemLog
        AppLogger.enableFileLogging = previousFileLog
    }

    @Test
    fun initialZero_doesNotMeanStatisticsAreLoaded() {
        assertEquals(0L to 0L, statistics.getCumulativeTokenCounts("chat"))
        assertFalse(statistics.hasTokenStatistics("chat"))

        statistics.setTokenCounts("chat", 0L, 0L, 0L)

        assertTrue(statistics.hasTokenStatistics("chat"))
        assertFalse(statistics.hasTokenStatistics("other"))
    }

    @Test
    fun windowOnlyUpdate_doesNotInitializeCumulativeCounts() {
        statistics.setCurrentWindowSize("chat", 362_370L)

        assertFalse(statistics.hasTokenStatistics("chat"))
        assertEquals(0L to 0L, statistics.getCumulativeTokenCounts("chat"))
        assertEquals(362_370L, statistics.currentWindowSizeFlow.value)
    }

    @Test
    fun largeCounts_arePreservedBeyondTheIntegerLimit() {
        statistics.setTokenCounts("chat", 2_600_000_000L, 120_000L, 362_370L)

        assertEquals(2_600_000_000L to 120_000L, statistics.getCumulativeTokenCounts("chat"))
        assertEquals(2_600_000_000L, statistics.cumulativeInputTokensFlow.value)
        assertEquals(120_000L, statistics.cumulativeOutputTokensFlow.value)
    }

    @Test
    fun uninitializedConversation_doesNotStartAccumulatingFromZero() {
        val service = mock<EnhancedAIService>()
        whenever(service.getCurrentInputTokenCount()).thenReturn(1_000L)
        whenever(service.getCurrentOutputTokenCount()).thenReturn(200L)

        statistics.updateCumulativeStatistics("chat", service)

        assertFalse(statistics.hasTokenStatistics("chat"))
        assertEquals(0L to 0L, statistics.getCumulativeTokenCounts("chat"))
    }

    @Test
    fun restoredCounts_continueAccumulatingFromThePersistedBase() {
        val service = mock<EnhancedAIService>()
        whenever(service.getCurrentInputTokenCount()).thenReturn(1_000L)
        whenever(service.getCurrentOutputTokenCount()).thenReturn(200L)
        statistics.setTokenCounts("chat", 2_600_000_000L, 120_000L, 362_370L)

        statistics.updateCumulativeStatistics("chat", service)

        assertEquals(2_600_001_000L to 120_200L, statistics.getCumulativeTokenCounts("chat"))
        assertEquals(2_600_001_000L, statistics.cumulativeInputTokensFlow.value)
        assertEquals(120_200L, statistics.cumulativeOutputTokensFlow.value)
    }

    @Test
    fun lateZeroSnapshot_doesNotEraseCumulativeCounts() {
        statistics.setTokenCounts("chat", 2_600_000_000L, 120_000L, 362_370L)
        statistics.setTokenCounts("chat", 0L, 0L, 100L)

        assertEquals(2_600_000_000L to 120_000L, statistics.getCumulativeTokenCounts("chat"))
        assertEquals(2_600_000_000L, statistics.cumulativeInputTokensFlow.value)
        assertEquals(120_000L, statistics.cumulativeOutputTokensFlow.value)
        assertEquals(100L, statistics.currentWindowSizeFlow.value)
    }

    @Test
    fun lateSnapshot_preservesTheNewestValueOfBothCounters() {
        statistics.setTokenCounts("chat", 2_600_000_000L, 120_000L, 10L)
        statistics.setTokenCounts("chat", 2_500_000_000L, 120_500L, 20L)

        assertEquals(2_600_000_000L to 120_500L, statistics.getCumulativeTokenCounts("chat"))
    }

    @Test
    fun windowRefresh_canShrinkWithoutChangingUsage() {
        statistics.setTokenCounts("chat", 2_600_000_000L, 120_000L, 362_370L)
        statistics.setCurrentWindowSize("chat", 100L)

        assertEquals(2_600_000_000L to 120_000L, statistics.getCumulativeTokenCounts("chat"))
        assertEquals(100L, statistics.getLastCurrentWindowSize("chat"))
        assertEquals(100L, statistics.currentWindowSizeFlow.value)
    }

    @Test
    fun backgroundRestore_doesNotSwitchTheActiveConversation() {
        statistics.setTokenCounts("chat", 100L, 20L, 30L)
        statistics.setTokenCounts("background", 2_600_000_000L, 120_000L, 362_370L)

        assertEquals(100L, statistics.cumulativeInputTokensFlow.value)
        assertEquals(20L, statistics.cumulativeOutputTokensFlow.value)
        assertEquals(30L, statistics.currentWindowSizeFlow.value)
        assertEquals(2_600_000_000L to 120_000L, statistics.getCumulativeTokenCounts("background"))

        statistics.setActiveChatId("background")
        assertEquals(2_600_000_000L, statistics.cumulativeInputTokensFlow.value)
        assertEquals(120_000L, statistics.cumulativeOutputTokensFlow.value)
        assertEquals(362_370L, statistics.currentWindowSizeFlow.value)
    }

    @Test
    fun explicitReset_clearsCountersAndInitialization() {
        statistics.setTokenCounts("chat", 2_600_000_000L, 120_000L, 362_370L)
        statistics.resetTokenStatistics()

        assertFalse(statistics.hasTokenStatistics("chat"))
        assertEquals(0L to 0L, statistics.getCumulativeTokenCounts("chat"))
        assertEquals(0L, statistics.cumulativeInputTokensFlow.value)
        assertEquals(0L, statistics.cumulativeOutputTokensFlow.value)
        assertEquals(0L, statistics.currentWindowSizeFlow.value)
    }

    @Test
    fun explicitChatClear_resetsOnlyTheTargetConversation() {
        statistics.setTokenCounts("chat", 2_600_000_000L, 120_000L, 362_370L)
        statistics.setTokenCounts("other", 100L, 20L, 30L)
        statistics.clearChatTokenStatistics("chat")

        assertTrue(statistics.hasTokenStatistics("chat"))
        assertEquals(0L to 0L, statistics.getCumulativeTokenCounts("chat"))
        assertEquals(0L, statistics.currentWindowSizeFlow.value)
        assertEquals(100L to 20L, statistics.getCumulativeTokenCounts("other"))
    }

    @Test
    fun negativeInitialValues_areStoredAsInitializedZero() {
        statistics.setTokenCounts("chat", -1L, -2L, -3L)

        assertTrue(statistics.hasTokenStatistics("chat"))
        assertEquals(0L to 0L, statistics.getCumulativeTokenCounts("chat"))
        assertEquals(0L, statistics.currentWindowSizeFlow.value)
    }
}
