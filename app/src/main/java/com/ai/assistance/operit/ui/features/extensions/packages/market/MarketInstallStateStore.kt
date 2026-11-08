package com.ai.assistance.operit.ui.features.extensions.packages.market

import com.ai.assistance.operit.data.transfer.DownloadTransferProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MarketInstallStage {
    CONNECTING,
    FETCHING_METADATA,
    CHECKING_LOCAL,
    DOWNLOADING,
    VERIFYING,
    IMPORTING_REPOSITORY,
    IMPORTING_CONFIG,
    INSTALLING,
    RECORDING
}

data class MarketInstallProgress(
    val entryId: String,
    val stage: MarketInstallStage,
    val progress: Float? = null,
    val transfer: DownloadTransferProgress? = null,
)

typealias MarketInstallProgressReporter = (MarketInstallStage, DownloadTransferProgress?) -> Unit

object MarketInstallStateStore {
    private val _installStates = MutableStateFlow<Map<String, MarketInstallProgress>>(emptyMap())
    val installStates: StateFlow<Map<String, MarketInstallProgress>> = _installStates.asStateFlow()

    @Synchronized
    fun start(entryId: String): Boolean {
        val id = entryId.trim()
        if (id.isBlank() || _installStates.value.containsKey(id)) return false
        _installStates.value =
            _installStates.value + (id to MarketInstallProgress(id, MarketInstallStage.CONNECTING))
        return true
    }

    @Synchronized
    fun update(entryId: String, stage: MarketInstallStage, transfer: DownloadTransferProgress? = null) {
        val id = entryId.trim()
        if (id.isBlank() || !_installStates.value.containsKey(id)) return
        _installStates.value =
            _installStates.value + (id to MarketInstallProgress(id, stage, transfer?.fraction, transfer))
    }

    @Synchronized
    fun finish(entryId: String) {
        val id = entryId.trim()
        if (id.isBlank() || !_installStates.value.containsKey(id)) return
        _installStates.value = _installStates.value - id
    }
}
