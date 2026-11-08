package com.ai.assistance.operit.ui.features.extensions.packages.market

import androidx.annotation.StringRes
import com.ai.assistance.operit.R

enum class MarketStatsType(val wireValue: String) {
    SCRIPT("script"),
    PACKAGE("package"),
    SKILL("skill"),
    MCP("mcp")
}

enum class MarketSortOption(
    @StringRes val labelRes: Int
) {
    UPDATED(R.string.market_sort_updated),
    DOWNLOADS(R.string.market_sort_downloads),
    LIKES(R.string.market_sort_likes),
}

fun MarketSortOption.toRankMetric(): String {
    return when (this) {
        MarketSortOption.UPDATED -> "updated"
        MarketSortOption.DOWNLOADS -> "downloads"
        MarketSortOption.LIKES -> "likes"
    }
}

fun PublishArtifactType.toMarketStatsType(): MarketStatsType {
    return when (this) {
        PublishArtifactType.SCRIPT -> MarketStatsType.SCRIPT
        PublishArtifactType.PACKAGE -> MarketStatsType.PACKAGE
    }
}
