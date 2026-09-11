package com.ai.assistance.operit.ui.features.packages.market

import com.ai.assistance.operit.data.api.MarketStatsApiService
import com.ai.assistance.operit.data.api.MarketV2Entry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 市场更新检查的统一结果（包 / MCP / 技能三类界面共用） */
sealed interface MarketUpdateCheckResult {
    /** 本地安装无市场来源标记（例如手工导入） */
    data object NotFromMarket : MarketUpdateCheckResult

    /** 市场条目已不存在 */
    data object EntryMissing : MarketUpdateCheckResult

    /** 网络或服务端错误 */
    data object Failed : MarketUpdateCheckResult

    /** 已是最新版 */
    data class UpToDate(val entry: MarketV2Entry) : MarketUpdateCheckResult

    /** 有更新可用 */
    data class UpdateAvailable(val entry: MarketV2Entry, val latestVersion: String) : MarketUpdateCheckResult
}

/**
 * 依据安装标记执行一次市场更新检查。
 * @param marker 安装标记；为 null 时直接返回 NotFromMarket
 */
suspend fun checkMarketUpdate(marker: MarketInstallMarker?): MarketUpdateCheckResult {
    if (marker == null) return MarketUpdateCheckResult.NotFromMarket
    val entryResult =
        withContext(Dispatchers.IO) {
            MarketStatsApiService().getEntry(marker.entryId)
        }
    if (entryResult.isFailure) return MarketUpdateCheckResult.Failed
    val entry = entryResult.getOrNull() ?: return MarketUpdateCheckResult.EntryMissing
    val latest = entry.latestVersion
    return if (latest == null || latest.id.isBlank() || latest.id == marker.versionId) {
        MarketUpdateCheckResult.UpToDate(entry)
    } else {
        MarketUpdateCheckResult.UpdateAvailable(
            entry = entry,
            latestVersion = latest.version.ifBlank { latest.id }
        )
    }
}