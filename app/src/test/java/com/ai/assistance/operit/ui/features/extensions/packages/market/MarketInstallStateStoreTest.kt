package com.ai.assistance.operit.ui.features.extensions.packages.market

import com.ai.assistance.operit.data.transfer.DownloadTransferProgress
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class MarketInstallStateStoreTest {
    private val firstId = "market-transfer-test-first"
    private val secondId = "market-transfer-test-second"

    @After
    fun clearTestInstalls() {
        MarketInstallStateStore.finish(firstId)
        MarketInstallStateStore.finish(secondId)
    }

    @Test
    fun activeInstallsShareTheirTransferButRejectDuplicateStarts() {
        assertTrue(MarketInstallStateStore.start(firstId))
        assertFalse(MarketInstallStateStore.start(firstId))
        val transfer = DownloadTransferProgress(512L, 1_024L, 256.0, 2_000L)
        MarketInstallStateStore.update(firstId, MarketInstallStage.DOWNLOADING, transfer)
        val state = MarketInstallStateStore.installStates.value.getValue(firstId)
        assertEquals(transfer, state.transfer)
        assertEquals(0.5f, state.progress!!, 0f)
        MarketInstallStateStore.update(firstId, MarketInstallStage.VERIFYING)
        val verifying = MarketInstallStateStore.installStates.value.getValue(firstId)
        assertEquals(MarketInstallStage.VERIFYING, verifying.stage)
        assertNull(verifying.transfer)
        assertNull(verifying.progress)
    }

    @Test
    fun concurrentEntriesKeepIndependentBytesSpeedsAndStages() {
        MarketInstallStateStore.start(firstId)
        MarketInstallStateStore.start(secondId)
        val first = DownloadTransferProgress(100L, 1_000L, 50.0, 2_000L)
        val second = DownloadTransferProgress(2_000L, null, 1_000.0, 2_000L)
        MarketInstallStateStore.update(firstId, MarketInstallStage.DOWNLOADING, first)
        MarketInstallStateStore.update(secondId, MarketInstallStage.DOWNLOADING, second)
        MarketInstallStateStore.update(firstId, MarketInstallStage.INSTALLING)
        assertEquals(MarketInstallStage.INSTALLING, MarketInstallStateStore.installStates.value.getValue(firstId).stage)
        assertEquals(second, MarketInstallStateStore.installStates.value.getValue(secondId).transfer)
        assertNull(MarketInstallStateStore.installStates.value.getValue(secondId).progress)
    }

    @Test
    fun completionClearsStateAndLateUpdatesCannotReviveIt() {
        MarketInstallStateStore.start(firstId)
        MarketInstallStateStore.finish(firstId)
        MarketInstallStateStore.update(firstId, MarketInstallStage.DOWNLOADING, DownloadTransferProgress(1L, 2L, 1.0, 1_000L))
        assertFalse(MarketInstallStateStore.installStates.value.containsKey(firstId))
        assertTrue(MarketInstallStateStore.start(firstId))
    }
}
