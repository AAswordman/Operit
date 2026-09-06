package com.ai.assistance.operit.ui.features.packages.market

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.MarketV2Entry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 市场检查更新按钮 + 结果弹窗（包 / MCP / 技能三类界面共用）。
 * @param markerProvider 读取当前安装项市场安装标记的挂起函数；返回 null 视为非市场来源
 * @param onOpenMarketDetail 有更新时点击"前往更新"跳转市场详情页
 */
@Composable
fun MarketUpdateCheckButton(
    markerProvider: suspend () -> MarketInstallMarker?,
    onOpenMarketDetail: (MarketV2Entry) -> Unit,
    modifier: Modifier = Modifier
) {
    var isChecking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<MarketUpdateCheckResult?>(null) }
    val scope = rememberCoroutineScope()

    OutlinedButton(
        onClick = {
            if (isChecking) return@OutlinedButton
            isChecking = true
            result = null
            scope.launch {
                val marker =
                    try {
                        withContext(Dispatchers.IO) { markerProvider() }
                    } catch (e: Exception) {
                        null
                    }
                result = checkMarketUpdate(marker)
                isChecking = false
            }
        },
        modifier = modifier,
        enabled = !isChecking
    ) {
        if (isChecking) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(stringResource(R.string.pkg_check_update))
    }

    val current = result
    if (current != null) {
        AlertDialog(
            onDismissRequest = { result = null },
            title = { Text(stringResource(R.string.pkg_check_update)) },
            text = {
                Text(
                    text =
                        when (current) {
                            is MarketUpdateCheckResult.UpToDate ->
                                stringResource(R.string.pkg_check_update_up_to_date)
                            is MarketUpdateCheckResult.UpdateAvailable ->
                                stringResource(R.string.pkg_check_update_available, current.latestVersion)
                            MarketUpdateCheckResult.NotFromMarket ->
                                stringResource(R.string.pkg_check_update_not_market)
                            MarketUpdateCheckResult.EntryMissing ->
                                stringResource(R.string.pkg_check_update_entry_missing)
                            MarketUpdateCheckResult.Failed ->
                                stringResource(R.string.pkg_check_update_failed)
                        }
                )
            },
            confirmButton = {
                if (current is MarketUpdateCheckResult.UpdateAvailable) {
                    TextButton(
                        onClick = {
                            result = null
                            onOpenMarketDetail(current.entry)
                        }
                    ) {
                        Text(stringResource(R.string.pkg_check_update_go))
                    }
                } else {
                    TextButton(onClick = { result = null }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { result = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}