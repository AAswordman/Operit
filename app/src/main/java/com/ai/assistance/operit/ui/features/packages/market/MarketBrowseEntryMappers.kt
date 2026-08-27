package com.ai.assistance.operit.ui.features.packages.market

import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.MarketV2Entry

val UnifiedMarketBrowseConfig =
    MarketBrowseSectionConfig(
        searchPlaceholderRes = R.string.artifact_market_search_placeholder,
        headerTitleRes = R.string.available_artifacts_market,
        emptySearchTitleRes = R.string.no_matching_artifacts_found,
        emptyDefaultTitleRes = R.string.no_artifacts_available,
        sortOptions = listOf(
            MarketSortOption.UPDATED,
            MarketSortOption.DOWNLOADS,
            MarketSortOption.LIKES
        )
    )

val UnifiedMarketCategoryConfig =
    MarketBrowseSectionConfig(
        searchPlaceholderRes = R.string.artifact_market_search_placeholder,
        headerTitleRes = R.string.available_artifacts_market,
        emptySearchTitleRes = R.string.no_matching_artifacts_found,
        emptyDefaultTitleRes = R.string.no_artifacts_available,
        sortOptions = listOf(
            MarketSortOption.UPDATED,
            MarketSortOption.DOWNLOADS,
            MarketSortOption.LIKES
        )
    )

fun MarketV2Entry.toUnifiedMarketBrowseEntry(
    installStates: Map<String, MarketInstallProgress>,
    localInstallStates: Map<String, MarketLocalInstallState>,
    onViewDetails: (MarketV2Entry) -> Unit,
    onInstallEntry: (MarketV2Entry) -> Unit
): MarketBrowseEntry {
    val installState = installStates[id]
    val localState = localInstallStates[id]
    val isCurrentAppVersionUnsupported = isUnsupportedByCurrentAppVersion()
    return MarketBrowseEntry(
        model =
            MarketBrowseCardModel(
                title = title,
                description = detail.ifBlank { description }.truncateMarketBrowseDescription(),
                ownerUsername = publisherLogin(),
                thumbsUpCount = likeCount(),
                heartCount = 0,
                downloads = downloadCountValue(),
                showAction = canInstallFromUnifiedMarket(),
                actionState =
                    if (installState != null) {
                        MarketBrowseActionState.Installing(installState.progress)
                    } else if (isCurrentAppVersionUnsupported) {
                        MarketBrowseActionState.Unavailable(MarketUnavailableKind.Warning)
                    } else {
                        localState.toBrowseActionState()
                    }
            ),
        onViewDetails = { onViewDetails(this) },
        onInstall = { onInstallEntry(this) }
    )
}

private fun MarketLocalInstallState?.toBrowseActionState(): MarketBrowseActionState {
    return when (this?.kind) {
        MarketLocalInstallStateKind.INSTALLED -> MarketBrowseActionState.Installed
        MarketLocalInstallStateKind.UPDATE_AVAILABLE -> MarketBrowseActionState.Updatable
        MarketLocalInstallStateKind.NOT_INSTALLED,
        null -> MarketBrowseActionState.Available
    }
}

private fun MarketV2Entry.publisherLogin(): String {
    return publisher?.login?.ifBlank { author?.login.orEmpty() }
        ?.ifBlank { publisherId.removePrefix("gh_").ifBlank { authorId.removePrefix("gh_") } }
        .orEmpty()
}

private fun MarketV2Entry.downloadCountValue(): Int {
    return stats?.downloads ?: downloadCount.takeIf { it > 0 } ?: downloads
}

private fun MarketV2Entry.likeCount(): Int {
    return stats?.likes ?: reactions.sumOf { reaction ->
        val key = reaction.reaction.ifBlank { reaction.content }
        if (key == "+1" || key.equals("like", ignoreCase = true)) reaction.total.coerceAtLeast(1) else 0
    }
}

private fun String.truncateMarketBrowseDescription(maxLength: Int = 100): String {
    return if (length > maxLength) {
        take(maxLength) + "..."
    } else {
        this
    }
}
