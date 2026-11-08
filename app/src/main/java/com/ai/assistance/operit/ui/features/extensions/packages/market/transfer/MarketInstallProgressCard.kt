package com.ai.assistance.operit.ui.features.extensions.packages.market.transfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.extensions.packages.market.MarketInstallProgress
import com.ai.assistance.operit.ui.features.extensions.packages.market.MarketInstallStage

/** One compact transfer row shared by list, details and installed-extension updates. */
@Composable
internal fun MarketInstallProgressCard(state: MarketInstallProgress, modifier: Modifier = Modifier, compact: Boolean = false) {
    val transfer = state.transfer
    val fraction = state.progress
    Column(modifier.fillMaxWidth().padding(horizontal = if (compact) 8.dp else 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(state.stage.installStageLabel(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (fraction != null) Text(stringResource(R.string.market_transfer_percent, (fraction * 100).toInt().coerceIn(0, 100)),
                style = MaterialTheme.typography.labelSmall)
        }
        if (fraction == null) androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        else androidx.compose.material3.LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        if (transfer != null) {
            Text(stringResource(R.string.market_transfer_amount, formatTransferBytes(transfer.downloadedBytes.toDouble()),
                transfer.totalBytes?.let { formatTransferBytes(it.toDouble()) } ?: stringResource(R.string.market_transfer_unknown_total)),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.market_transfer_elapsed, formatTransferDuration(transfer.elapsedMillis / 1000L)) + " · " +
                (transfer.remainingSeconds?.let { stringResource(R.string.market_transfer_remaining, formatTransferDuration(it)) }
                    ?: stringResource(R.string.market_transfer_unknown_remaining)),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            transfer.bytesPerSecond?.let { speed -> Text(stringResource(R.string.market_transfer_speed, formatTransferBytes(speed)),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
@Composable
internal fun MarketInstallProgress.marketInstallLabel(): String =
    if (stage == MarketInstallStage.DOWNLOADING && progress != null) {
        stringResource(R.string.market_install_downloading_percent, (progress * 100).toInt().coerceIn(0, 100))
    } else {
        stage.installStageLabel()
    }

@Composable
private fun MarketInstallStage.installStageLabel(): String = stringResource(
    when (this) {
        MarketInstallStage.CONNECTING -> R.string.market_install_connecting
        MarketInstallStage.FETCHING_METADATA -> R.string.market_install_fetching_metadata
        MarketInstallStage.CHECKING_LOCAL -> R.string.market_install_checking_local
        MarketInstallStage.DOWNLOADING -> R.string.market_install_downloading
        MarketInstallStage.VERIFYING -> R.string.market_install_verifying
        MarketInstallStage.IMPORTING_REPOSITORY -> R.string.market_install_importing_repository
        MarketInstallStage.IMPORTING_CONFIG -> R.string.market_install_importing_config
        MarketInstallStage.INSTALLING -> R.string.market_install_installing
        MarketInstallStage.RECORDING -> R.string.market_install_recording
    }
)
