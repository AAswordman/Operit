package com.ai.assistance.operit.ui.features.extensions.packages.market.transfer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.extensions.packages.market.MarketInstallProgress
import com.ai.assistance.operit.ui.features.extensions.packages.market.MarketInstallStage

/** 详情标题下和市场条目共用环形进度面板，传输统计随条目状态更新。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MarketInstallProgressCard(
    state: MarketInstallProgress,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val transfer = state.transfer
    val fraction = state.progress
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (compact) 16.dp else 20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
    ) {
        Row(
            modifier = Modifier.padding(if (compact) 12.dp else 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(if (compact) 48.dp else 58.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (fraction != null) {
                    CircularProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.matchParentSize(),
                        strokeWidth = 3.dp,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    )
                    Text(
                        stringResource(R.string.market_transfer_percent, (fraction * 100).toInt().coerceIn(0, 100)),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.matchParentSize(),
                        strokeWidth = 3.dp,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    )
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    state.stage.installStageLabel(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (transfer != null) {
                    Text(
                        stringResource(
                            R.string.market_transfer_amount,
                            formatTransferBytes(transfer.downloadedBytes.toDouble()),
                            transfer.totalBytes?.let { formatTransferBytes(it.toDouble()) }
                                ?: stringResource(R.string.market_transfer_unknown_total),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val speed = transfer.bytesPerSecond
                        Text(
                            if (speed != null) {
                                stringResource(R.string.market_transfer_speed, formatTransferBytes(speed))
                            } else {
                                stringResource(R.string.market_transfer_measuring)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            stringResource(R.string.market_transfer_elapsed, formatTransferDuration(transfer.elapsedMillis / 1000L)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            transfer.remainingSeconds?.let {
                                stringResource(R.string.market_transfer_remaining, formatTransferDuration(it))
                            } ?: stringResource(R.string.market_transfer_unknown_remaining),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
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
