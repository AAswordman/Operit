package com.ai.assistance.operit.ui.features.packages.market

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class MarketBrowseCardModel(
    val title: String,
    val description: String,
    val ownerUsername: String = "",
    val thumbsUpCount: Int = 0,
    val heartCount: Int = 0,
    val downloads: Int = 0,
    val showAction: Boolean = true,
    val actionState: MarketBrowseActionState = MarketBrowseActionState.Available
)

sealed interface MarketBrowseActionState {
    data object Available : MarketBrowseActionState
    data object Updatable : MarketBrowseActionState
    data object Installed : MarketBrowseActionState
    data class Installing(val progress: Float? = null) : MarketBrowseActionState
    data class Unavailable(val kind: MarketUnavailableKind = MarketUnavailableKind.Info) :
        MarketBrowseActionState
}

enum class MarketUnavailableKind {
    Info,
    Warning
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> MarketBrowseList(
    items: List<T>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    searchQuery: String,
    sortOption: MarketSortOption,
    onSearchQueryChanged: (String) -> Unit,
    onSortOptionChanged: (MarketSortOption) -> Unit,
    featuredOnly: Boolean = true,
    onFeaturedOnlyChanged: (Boolean) -> Unit = {},
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    @StringRes searchPlaceholderRes: Int,
    @StringRes headerTitleRes: Int,
    @StringRes emptySearchTitleRes: Int,
    @StringRes emptyDefaultTitleRes: Int,
    sortOptions: List<MarketSortOption> = MarketSortOption.entries,
    itemKey: (T) -> Any,
    updatedAtSelector: (T) -> String,
    itemContent: @Composable (T) -> Unit,
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
    onScrollPositionChanged: (Int, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialFirstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = initialFirstVisibleItemScrollOffset
    )
    val pullToRefreshState = rememberPullToRefreshState()
    val showInitialLoading = isLoading && items.isEmpty()
    val lastLoadMoreIndex = remember { mutableStateOf(-1) }
    val isRefreshing = isLoading && items.isNotEmpty() && searchQuery.isBlank()

    LaunchedEffect(listState, items.size, searchQuery, hasMore, isLoadingMore) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastVisibleIndex ->
                if (searchQuery.isNotBlank()) return@collect
                val lastItemIndex = items.size - 1
                if (hasMore && !isLoadingMore && items.isNotEmpty() && lastVisibleIndex >= (lastItemIndex - 2) && lastVisibleIndex != lastLoadMoreIndex.value) {
                    lastLoadMoreIndex.value = lastVisibleIndex
                    onLoadMore()
                }
            }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                onScrollPositionChanged(index, offset)
            }
    }

    Column(modifier = modifier.fillMaxSize()) {
        MarketBrowseControls(
            searchQuery = searchQuery,
            onSearchQueryChanged = onSearchQueryChanged,
            sortOption = sortOption,
            onSortOptionChanged = onSortOptionChanged,
            featuredOnly = featuredOnly,
            onFeaturedOnlyChanged = onFeaturedOnlyChanged,
            searchPlaceholderRes = searchPlaceholderRes,
            sortOptions = sortOptions
        )

        Box(modifier = Modifier.fillMaxSize()) {
            if (showInitialLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                val listContent: @Composable () -> Unit = {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (sortOption == MarketSortOption.UPDATED) {
                            groupedMarketItems(
                                items = items,
                                itemKey = itemKey,
                                updatedAtSelector = updatedAtSelector,
                                itemContent = itemContent
                            )
                        } else {
                            items(items, key = itemKey) { item ->
                                itemContent(item)
                            }
                        }

                        if (isLoadingMore) {
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }

                        if (items.isEmpty() && !isLoading) {
                            item {
                                Card(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp),
                                    colors =
                                        CardDefaults.cardColors(
                                            containerColor =
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                ) {
                                    Column(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            if (searchQuery.isNotBlank()) {
                                                Icons.Default.SearchOff
                                            } else {
                                                Icons.Default.Store
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text =
                                                stringResource(
                                                    if (searchQuery.isNotBlank()) {
                                                        emptySearchTitleRes
                                                    } else {
                                                        emptyDefaultTitleRes
                                                    }
                                                ),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text =
                                                stringResource(
                                                    if (searchQuery.isNotBlank()) {
                                                        R.string.try_changing_keywords
                                                    } else {
                                                        R.string.refresh_or_try_again_later
                                                    }
                                                ),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (searchQuery.isBlank()) {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize(),
                        state = pullToRefreshState,
                        indicator = {
                            PullToRefreshDefaults.Indicator(
                                state = pullToRefreshState,
                                isRefreshing = isRefreshing,
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                        }
                    ) {
                        listContent()
                    }
                } else {
                    listContent()
                }
            }
        }
    }
}

private fun <T> LazyListScope.groupedMarketItems(
    items: List<T>,
    itemKey: (T) -> Any,
    updatedAtSelector: (T) -> String,
    itemContent: @Composable (T) -> Unit
) {
    val groupedItems =
        items.groupBy { item ->
            resolveMarketUpdatedDateLabel(updatedAtSelector(item))
        }

    groupedItems.forEach { (dateLabel, groupItems) ->
        item(key = "date-header-$dateLabel") {
            MarketBrowseDateHeader(dateLabel = dateLabel)
        }
        items(groupItems, key = itemKey) { item ->
            itemContent(item)
        }
    }
}

@Composable
private fun MarketBrowseDateHeader(dateLabel: String) {
    Text(
        text = dateLabel,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun resolveMarketUpdatedDateLabel(rawUpdatedAt: String): String {
    val trimmed = rawUpdatedAt.trim()
    if (trimmed.isBlank()) {
        return "更早"
    }

    parseMarketUpdatedDate(trimmed)?.let { date ->
        return MARKET_UPDATED_DATE_FORMATTER.format(date)
    }

    return trimmed.take(10)
}

private fun parseMarketUpdatedDate(value: String): LocalDate? {
    return runCatching {
        Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDate()
    }.recoverCatching {
        LocalDate.parse(value.take(10))
    }.getOrNull()
}

private val MARKET_UPDATED_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd")

@Composable
fun MarketBrowseCard(
    model: MarketBrowseCardModel,
    onViewDetails: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable { onViewDetails() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MarketBrowseLeadingIcon(title = model.title)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (model.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                MarketBrowseMetaRow(
                    ownerUsername = model.ownerUsername,
                    downloads = model.downloads,
                    thumbsUpCount = model.thumbsUpCount,
                    heartCount = model.heartCount
                )
            }

            if (model.showAction) {
                MarketBrowseInstallButton(
                    state = model.actionState,
                    onClick = onInstall
                )
            }
        }
    }
}

@Composable
private fun MarketBrowseLeadingIcon(title: String) {
    val initial =
        title
            .trim()
            .firstOrNull()
            ?.uppercaseChar()
            ?.toString()
            ?: "?"

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun MarketBrowseMetaRow(
    ownerUsername: String,
    downloads: Int,
    thumbsUpCount: Int,
    heartCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (ownerUsername.isNotBlank()) {
            Text(
                text = ownerUsername,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        MarketStatsSummary(
            downloads = downloads
        )

        if (thumbsUpCount > 0) {
            MarketBrowseMetaCount(
                icon = Icons.Default.ThumbUp,
                count = thumbsUpCount,
                tint = MaterialTheme.colorScheme.primary
            )
        }

        if (heartCount > 0) {
            MarketBrowseMetaCount(
                icon = Icons.Default.Favorite,
                count = heartCount,
                tint = Color(0xFFE91E63)
            )
        }
    }
}

@Composable
private fun MarketBrowseMetaCount(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    tint: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = tint
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}

@Composable
private fun MarketBrowseInstallButton(
    state: MarketBrowseActionState,
    onClick: () -> Unit
) {
    val ui = resolveMarketBrowseInstallButtonUi(state, MaterialTheme.colorScheme)

    Surface(shape = CircleShape, color = ui.containerColor) {
        IconButton(
            onClick = {
                if (ui.enabled) {
                    onClick()
                }
            },
            modifier = Modifier.size(34.dp)
        ) {
            if (ui.isLoading) {
                val progress = ui.progress
                if (progress != null) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = ui.contentColor
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = ui.contentColor
                    )
                }
            } else {
                Icon(
                    imageVector = ui.icon,
                    contentDescription = null,
                    tint = ui.contentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private data class MarketBrowseInstallButtonUi(
    val containerColor: Color,
    val contentColor: Color,
    val enabled: Boolean,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val isLoading: Boolean = false,
    val progress: Float? = null
)

private fun resolveMarketBrowseInstallButtonUi(
    state: MarketBrowseActionState,
    colorScheme: ColorScheme
): MarketBrowseInstallButtonUi {
    return when (state) {
        MarketBrowseActionState.Available ->
            MarketBrowseInstallButtonUi(
                containerColor = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
                enabled = true,
                icon = Icons.Default.Download
            )

        MarketBrowseActionState.Updatable ->
            MarketBrowseInstallButtonUi(
                containerColor = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
                enabled = true,
                icon = Icons.Default.Update
            )

        MarketBrowseActionState.Installed ->
            MarketBrowseInstallButtonUi(
                containerColor = colorScheme.secondaryContainer,
                contentColor = colorScheme.onSecondaryContainer,
                enabled = false,
                icon = Icons.Default.Check
            )

        is MarketBrowseActionState.Installing ->
            MarketBrowseInstallButtonUi(
                containerColor = colorScheme.primaryContainer,
                contentColor = colorScheme.onPrimaryContainer,
                enabled = false,
                icon = Icons.Default.Download,
                isLoading = true,
                progress = state.progress
            )

        is MarketBrowseActionState.Unavailable ->
            MarketBrowseInstallButtonUi(
                containerColor = colorScheme.surfaceVariant,
                contentColor = colorScheme.onSurfaceVariant,
                enabled = false,
                icon =
                    when (state.kind) {
                        MarketUnavailableKind.Info -> Icons.Default.Info
                        MarketUnavailableKind.Warning -> Icons.Default.Warning
                    }
            )
    }
}
