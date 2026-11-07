package com.ai.assistance.operit.ui.features.extensions.packages.screens

import com.ai.assistance.operit.ui.common.oktoast.OKToast

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.api.MarketV2ManifestCategory
import com.ai.assistance.operit.data.api.MarketV2Notification
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.data.preferences.GitHubUser
import com.ai.assistance.operit.ui.features.extensions.github.GitHubLoginDialog
import com.ai.assistance.operit.ui.features.extensions.packages.market.BindMarketSearchToTopBar
import com.ai.assistance.operit.ui.features.extensions.packages.market.MarketBrowseSection
import com.ai.assistance.operit.ui.features.extensions.packages.market.MarketStatsType
import com.ai.assistance.operit.ui.features.extensions.packages.market.UnifiedMarketBrowseConfig
import com.ai.assistance.operit.ui.features.extensions.packages.market.UnifiedMarketCategoryConfig
import com.ai.assistance.operit.ui.features.extensions.packages.market.toUnifiedMarketBrowseEntry
import com.ai.assistance.operit.ui.features.extensions.packages.screens.market.viewmodel.UnifiedMarketBrowseScope
import com.ai.assistance.operit.ui.features.extensions.packages.screens.market.viewmodel.UnifiedMarketBrowseViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class MarketHomeTab(@StringRes val labelRes: Int) {
    ALL(R.string.market_tab_all),
    CATEGORIES(R.string.market_tab_categories),
    MINE(R.string.market_tab_mine)
}

private enum class MarketCategoryTypeFilter(
    @StringRes val labelRes: Int,
    val wireType: String
) {
    ALL(R.string.market_category_type_all, ""),
    SCRIPT(R.string.market_category_type_script, "script"),
    PACKAGE(R.string.market_category_type_package, "package"),
    SKILL(R.string.market_category_type_skill, "skill"),
    MCP(R.string.market_category_type_mcp, "mcp")
}

private val MarketCategoryNameResById =
    mapOf(
        "search_research" to R.string.market_category_search_research,
        "dev_code" to R.string.market_category_dev_code,
        "automation_workflow" to R.string.market_category_automation_workflow,
        "docs_knowledge" to R.string.market_category_docs_knowledge,
        "media_content" to R.string.market_category_media_content,
        "chat_communication" to R.string.market_category_chat_communication,
        "integration_api" to R.string.market_category_integration_api,
        "system_data" to R.string.market_category_system_data,
        "business_productivity" to R.string.market_category_business_productivity,
        "life_entertainment" to R.string.market_category_life_entertainment,
        "other" to R.string.market_category_other
    )

private val MarketCategoryIconById =
    mapOf(
        "search_research" to Icons.Default.Search,
        "dev_code" to Icons.Default.Code,
        "automation_workflow" to Icons.Default.AutoAwesome,
        "docs_knowledge" to Icons.Default.Description,
        "media_content" to Icons.Default.Image,
        "chat_communication" to Icons.Default.Chat,
        "integration_api" to Icons.Default.Api,
        "system_data" to Icons.Default.Storage,
        "business_productivity" to Icons.Default.Dashboard,
        "life_entertainment" to Icons.Default.Apps,
        "other" to Icons.Default.Folder
    )

@StringRes
private fun marketCategoryNameResOrNull(categoryId: String): Int? {
    return MarketCategoryNameResById[categoryId]
}

@Composable
fun marketCategoryLabel(categoryId: String): String {
    return marketCategoryNameResOrNull(categoryId)?.let { stringResource(it) } ?: categoryId
}

fun marketCategoryLabel(context: Context, categoryId: String): String {
    return marketCategoryNameResOrNull(categoryId)?.let { context.getString(it) } ?: categoryId
}

private data class MarketMineAuthState(
    val isLoading: Boolean = true,
    val isLoggedIn: Boolean = false,
    val currentUser: GitHubUser? = null
)

@Composable
fun UnifiedMarketScreen(
    initialTab: MarketHomeTab = MarketHomeTab.ALL,
    onNavigateToArtifactPublish: () -> Unit = {},
    onNavigateToRepoPublish: (MarketStatsType) -> Unit = {},
    onNavigateToMarketManage: () -> Unit = {},
    onNavigateToDetail: (MarketV2Entry) -> Unit = {},
    onNavigateToCategory: (String) -> Unit = {},
    onNavigateToNotifications: () -> Unit = {}
) {
    var selectedTab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    var showMarketAgreementDialog by remember { mutableStateOf(false) }

    val openEntry: (MarketV2Entry) -> Unit = onNavigateToDetail

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                MarketHomeTab.ALL -> MarketTypedListPane(
                    stateKey = "market-all-types",
                    scopeFactory = { selectedType ->
                        if (selectedType == MarketCategoryTypeFilter.ALL) {
                            UnifiedMarketBrowseScope.All
                        } else {
                            UnifiedMarketBrowseScope.type(selectedType.wireType)
                        }
                    },
                    config = UnifiedMarketBrowseConfig,
                    viewModelKeyFactory = { selectedType -> "market-all-${selectedType.name}" },
                    onOpenEntry = openEntry
                )

                MarketHomeTab.CATEGORIES -> MarketCategoryIndexPane(
                    onOpenCategory = onNavigateToCategory
                )

                MarketHomeTab.MINE -> MarketMinePane(
                    onManage = onNavigateToMarketManage,
                    onPublishArtifact = onNavigateToArtifactPublish,
                    onPublishRepo = onNavigateToRepoPublish,
                    onOpenNotifications = onNavigateToNotifications,
                    onOpenAgreement = { showMarketAgreementDialog = true }
                )
            }
        }

        NavigationBar(
            modifier = Modifier.height(56.dp)
        ) {
            MarketHomeTab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = selectedTab == tab,
                    onClick = {
                        selectedTab = tab
                    },
                    icon = {
                        Icon(
                            imageVector = when (tab) {
                                MarketHomeTab.ALL -> Icons.Default.Store
                                MarketHomeTab.CATEGORIES -> Icons.Default.List
                                MarketHomeTab.MINE -> Icons.Default.Person
                            },
                            contentDescription = stringResource(tab.labelRes)
                        )
                    },
                    label = null
                )
            }
        }
    }

    if (showMarketAgreementDialog) {
        MarketAgreementDialog(onDismissRequest = { showMarketAgreementDialog = false })
    }

}

@Composable
fun UnifiedMarketNotificationsScreen() {
    MarketNotificationsPane()
}

@Composable
fun UnifiedMarketCategoryScreen(
    categoryId: String,
    onNavigateToArtifactDetail: (MarketV2Entry) -> Unit = {},
    onNavigateToSkillDetail: (MarketV2Entry) -> Unit = {},
    onNavigateToMcpDetail: (MarketV2Entry) -> Unit = {}
) {
    val openEntry: (MarketV2Entry) -> Unit = { entry ->
        when (entry.type.lowercase()) {
            "skill" -> onNavigateToSkillDetail(entry)
            "mcp" -> onNavigateToMcpDetail(entry)
            "script",
            "package" -> onNavigateToArtifactDetail(entry)
            else -> onNavigateToArtifactDetail(entry)
        }
    }

    MarketCategoryDetailPane(
        categoryId = categoryId,
        onOpenEntry = openEntry
    )
}

@Composable
private fun UnifiedMarketListPane(
    scope: UnifiedMarketBrowseScope,
    config: com.ai.assistance.operit.ui.features.extensions.packages.market.MarketBrowseSectionConfig,
    viewModelKey: String,
    onOpenEntry: (MarketV2Entry) -> Unit
) {
    val context = LocalContext.current
    val viewModel: UnifiedMarketBrowseViewModel =
        viewModel(
            key = viewModelKey,
            factory = UnifiedMarketBrowseViewModel.Factory(
                context.applicationContext,
                scope
            )
        )
    val entries by viewModel.entries.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val featuredOnly by viewModel.featuredOnly.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val installStates by viewModel.installStates.collectAsState()
    val localInstallStates by viewModel.localInstallStates.collectAsState()
    val listScrollIndex by viewModel.listScrollIndex.collectAsState()
    val listScrollOffset by viewModel.listScrollOffset.collectAsState()
    BindMarketSearchToTopBar(
        enabled = true,
        searchQuery = searchQuery,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        searchPlaceholderRes = config.searchPlaceholderRes
    )

    LaunchedEffect(viewModelKey) {
        viewModel.loadEntriesIfNeeded()
    }

    errorMessage?.let { error ->
        LaunchedEffect(error) {
            OKToast.error(context, error, OKToast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    MarketBrowseSection(
        builtInType = scope.type.takeIf { it in setOf("", "script", "package") },
        items = entries,
        isLoading = isLoading,
        isLoadingMore = isLoadingMore,
        hasMore = hasMore,
        searchQuery = searchQuery,
        sortOption = sortOption,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onSortOptionChanged = viewModel::onSortOptionChanged,
        featuredOnly = featuredOnly,
        onFeaturedOnlyChanged = viewModel::onFeaturedOnlyChanged,
        onRefresh = viewModel::loadEntries,
        onLoadMore = viewModel::loadMoreEntries,
        config = config,
        itemKey = { it.id },
        initialFirstVisibleItemIndex = listScrollIndex,
        initialFirstVisibleItemScrollOffset = listScrollOffset,
        onScrollPositionChanged = viewModel::updateListScrollPosition,
        updatedAtSelector = { entry ->
            if (sortOption == com.ai.assistance.operit.ui.features.extensions.packages.market.MarketSortOption.UPDATED) {
                entry.updatedAt ?: entry.publishedAt ?: entry.createdAt.orEmpty()
            } else {
                entry.publishedAt ?: entry.updatedAt ?: entry.createdAt.orEmpty()
            }
        },
        entryFactory = { entry ->
            entry.toUnifiedMarketBrowseEntry(
                installStates = installStates,
                localInstallStates = localInstallStates,
                onViewDetails = onOpenEntry,
                onInstallEntry = viewModel::installEntry
            )
        }
    )
}

@Composable
private fun MarketCategoryIndexPane(
    onOpenCategory: (String) -> Unit
) {
    val context = LocalContext.current
    val viewModel: UnifiedMarketBrowseViewModel =
        viewModel(
            key = "market-categories",
            factory = UnifiedMarketBrowseViewModel.Factory(
                context.applicationContext,
                UnifiedMarketBrowseScope.All
            )
        )
    val categories by viewModel.categories.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    BindMarketSearchToTopBar(
        enabled = false,
        searchQuery = "",
        onSearchQueryChanged = { _ -> },
        searchPlaceholderRes = UnifiedMarketBrowseConfig.searchPlaceholderRes
    )

    LaunchedEffect(Unit) {
        viewModel.loadManifest()
    }

    errorMessage?.let { error ->
        LaunchedEffect(error) {
            OKToast.error(context, error, OKToast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    if (categories.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
        ) {
            items(categories, key = { it.id }) { category ->
                MarketCategoryCard(
                    category = category,
                    onClick = { onOpenCategory(category.id) }
                )
            }
        }
    }
}

@Composable
private fun MarketCategoryCard(
    category: MarketV2ManifestCategory,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = marketCategoryIcon(category.id),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = marketCategoryLabel(category.id),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun marketCategoryIcon(categoryId: String): ImageVector {
    return MarketCategoryIconById[categoryId] ?: Icons.Default.Folder
}

@Composable
private fun MarketCategoryDetailPane(
    categoryId: String,
    onOpenEntry: (MarketV2Entry) -> Unit
) {
    MarketTypedListPane(
        stateKey = "market-category-$categoryId-types",
        scopeFactory = { selectedType ->
            if (selectedType == MarketCategoryTypeFilter.ALL) {
                UnifiedMarketBrowseScope.category(categoryId)
            } else {
                UnifiedMarketBrowseScope.typeCategory(selectedType.wireType, categoryId)
            }
        },
        config = UnifiedMarketCategoryConfig,
        viewModelKeyFactory = { selectedType -> "market-category-${categoryId}-${selectedType.name}" },
        onOpenEntry = onOpenEntry
    )
}

@Composable
private fun MarketTypedListPane(
    stateKey: String,
    scopeFactory: (MarketCategoryTypeFilter) -> UnifiedMarketBrowseScope,
    config: com.ai.assistance.operit.ui.features.extensions.packages.market.MarketBrowseSectionConfig,
    viewModelKeyFactory: (MarketCategoryTypeFilter) -> String,
    onOpenEntry: (MarketV2Entry) -> Unit
) {
    var selectedType by rememberSaveable(stateKey) { mutableStateOf(MarketCategoryTypeFilter.ALL) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedType.ordinal,
            edgePadding = 12.dp
        ) {
            MarketCategoryTypeFilter.entries.forEach { filter ->
                Tab(
                    selected = selectedType == filter,
                    onClick = { selectedType = filter },
                    text = {
                        Text(
                            text = stringResource(filter.labelRes),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }

        UnifiedMarketListPane(
            scope = scopeFactory(selectedType),
            config = config,
            viewModelKey = viewModelKeyFactory(selectedType),
            onOpenEntry = onOpenEntry
        )
    }
}

@Composable
private fun MarketNotificationsPane() {
    val context = LocalContext.current
    val viewModel: UnifiedMarketBrowseViewModel =
        viewModel(
            key = "market-notifications",
            factory = UnifiedMarketBrowseViewModel.Factory(
                context.applicationContext,
                UnifiedMarketBrowseScope.All
            )
        )
    val notifications by viewModel.notifications.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    BindMarketSearchToTopBar(
        enabled = false,
        searchQuery = "",
        onSearchQueryChanged = { _ -> },
        searchPlaceholderRes = UnifiedMarketBrowseConfig.searchPlaceholderRes
    )

    LaunchedEffect(Unit) {
        viewModel.loadNotifications()
    }

    errorMessage?.let { error ->
        LaunchedEffect(error) {
            OKToast.error(context, error, OKToast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isLoading && notifications.isEmpty()) {
            MarketAccountLoadingCard()
        } else if (notifications.isEmpty()) {
            MarketEmptyCard(
                title = stringResource(R.string.market_notifications_empty_title),
                description = stringResource(R.string.market_notifications_empty_description)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notifications, key = { it.id }) { notification ->
                    MarketNotificationCard(notification = notification)
                }
            }
        }
    }
}

@Composable
private fun MarketNotificationCard(notification: MarketV2Notification) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = notificationKindIcon(notification.kind),
                contentDescription = null,
                tint = notificationKindColor(notification.kind),
                modifier = Modifier.size(24.dp).padding(top = 2.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = notificationKindLabel(notification.kind),
                        style = MaterialTheme.typography.labelMedium,
                        color = notificationKindColor(notification.kind)
                    )
                    Text(
                        text = relativeTime(notification.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                Text(
                    text = notification.title.ifBlank { notificationKindLabel(notification.kind) },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (notification.body.isNotBlank()) {
                    Text(
                        text = notification.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun notificationKindIcon(kind: String): ImageVector {
    return when (kind) {
        "comment_new", "comment_reply" -> Icons.Default.Comment
        "review_approved", "entry_curated" -> Icons.Default.CheckCircle
        "review_rejected" -> Icons.Default.Cancel
        "review_changes" -> Icons.Default.Refresh
        else -> Icons.Default.Notifications
    }
}

private fun notificationKindLabel(kind: String): String {
    return when (kind) {
        "comment_new" -> "新评论"
        "comment_reply" -> "回复了你的评论"
        "review_approved" -> "已通过审核"
        "review_rejected" -> "未通过审核"
        "review_changes" -> "需要修改"
        "entry_curated" -> "入选精选"
        else -> kind
    }
}

@Composable
private fun notificationKindColor(kind: String): Color {
    return when (kind) {
        "comment_new", "comment_reply" -> MaterialTheme.colorScheme.primary
        "review_approved" -> MaterialTheme.colorScheme.primary
        "entry_curated" -> MaterialTheme.colorScheme.tertiary
        "review_rejected" -> MaterialTheme.colorScheme.error
        "review_changes" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun relativeTime(isoDate: String): String {
    if (isoDate.isBlank()) return ""
    return try {
        val instant = java.time.Instant.parse(isoDate)
        val now = java.time.Instant.now()
        val duration = java.time.Duration.between(instant, now)
        val seconds = duration.seconds
        when {
            seconds < 60 -> "刚刚"
            seconds < 3600 -> "${seconds / 60} 分钟前"
            seconds < 86400 -> "${seconds / 3600} 小时前"
            seconds < 2592000 -> "${seconds / 86400} 天前"
            seconds < 31104000 -> "${seconds / 2592000} 个月前"
            else -> "${seconds / 31104000} 年前"
        }
    } catch (e: Exception) {
        isoDate.take(16).replace("T", " ")
    }
}


@Composable
private fun MarketMinePane(
    onManage: () -> Unit,
    onPublishArtifact: () -> Unit,
    onPublishRepo: (MarketStatsType) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAgreement: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val githubAuth = remember { GitHubAuthPreferences.getInstance(context) }
    val authState by produceState(initialValue = MarketMineAuthState(), githubAuth) {
        value = MarketMineAuthState(isLoading = true)

        val initialLoggedIn = githubAuth.isLoggedIn()
        val initialUser = githubAuth.getCurrentUserInfo()
        value =
            MarketMineAuthState(
                isLoading = false,
                isLoggedIn = initialLoggedIn,
                currentUser = if (initialLoggedIn) initialUser else null
            )

        githubAuth.isLoggedInFlow
            .combine(githubAuth.userInfoFlow) { isLoggedIn, currentUser ->
                MarketMineAuthState(
                    isLoading = false,
                    isLoggedIn = isLoggedIn,
                    currentUser = if (isLoggedIn) currentUser else null
                )
            }
            .collect { value = it }
    }
    var showLoginDialog by remember { mutableStateOf(false) }
    var showPublishDialog by remember { mutableStateOf(false) }

    BindMarketSearchToTopBar(
        enabled = false,
        searchQuery = "",
        onSearchQueryChanged = { _ -> },
        searchPlaceholderRes = UnifiedMarketBrowseConfig.searchPlaceholderRes
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (authState.isLoading) {
            MarketAccountLoadingCard()
        } else {
            MarketAccountCard(
                isLoggedIn = authState.isLoggedIn,
                currentUser = authState.currentUser,
                onLogin = { showLoginDialog = true },
                onLogout = {
                    coroutineScope.launch { githubAuth.logout() }
                }
            )

            MarketMineActionCard(
                title = stringResource(R.string.market_section_manage),
                onClick = {
                    if (authState.isLoggedIn) onManage() else showLoginDialog = true
                },
                icon = Icons.Default.Settings
            )
            MarketMineActionCard(
                title = stringResource(R.string.market_section_publish),
                onClick = {
                    if (authState.isLoggedIn) showPublishDialog = true else showLoginDialog = true
                },
                icon = Icons.Default.Add
            )
            MarketMineActionCard(
                title = stringResource(R.string.market_notifications_title),
                onClick = {
                    if (authState.isLoggedIn) onOpenNotifications() else showLoginDialog = true
                },
                icon = Icons.Default.Notifications
            )
            MarketMineActionCard(
                title = stringResource(R.string.market_agreement_entry),
                onClick = onOpenAgreement,
                icon = Icons.Default.Description
            )
        }
    }

    if (showPublishDialog) {
        MarketActionChooserDialog(
            title = stringResource(R.string.market_section_publish),
            onDismiss = { showPublishDialog = false },
            actions = listOf(
                MarketDialogAction(stringResource(R.string.market_publish_artifact), onPublishArtifact),
                MarketDialogAction(stringResource(R.string.market_publish_skill)) {
                    onPublishRepo(MarketStatsType.SKILL)
                },
                MarketDialogAction(stringResource(R.string.market_publish_mcp)) {
                    onPublishRepo(MarketStatsType.MCP)
                }
            )
        )
    }

    if (showLoginDialog) {
        GitHubLoginDialog(
            onDismissRequest = { showLoginDialog = false },
            onLoginSuccess = { showLoginDialog = false }
        )
    }
}

private data class MarketDialogAction(
    val title: String,
    val onClick: () -> Unit
)

@Composable
private fun MarketActionChooserDialog(
    title: String,
    onDismiss: () -> Unit,
    actions: List<MarketDialogAction>
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEach { action ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                action.onClick()
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = action.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun MarketAccountLoadingCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
            Text(
                text = stringResource(R.string.app_content_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MarketAccountCard(
    isLoggedIn: Boolean,
    currentUser: GitHubUser?,
    onLogin: () -> Unit,
    onLogout: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        if (isLoggedIn && currentUser != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                currentUser.avatarUrl.takeIf { it.isNotBlank() }?.let { avatarUrl ->
                    Image(
                        painter = rememberAsyncImagePainter(avatarUrl),
                        contentDescription = stringResource(R.string.user_avatar),
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } ?: Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentUser.name?.takeIf { it.isNotBlank() } ?: currentUser.login,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "@${currentUser.login}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(onClick = onLogout) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.please_login_github_first),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.market_login_required_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(onClick = onLogin) {
                    Icon(
                        imageVector = Icons.Default.Login,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.login_github))
                }
            }
        }
    }
}

@Composable
private fun MarketMineActionCard(
    title: String,
    onClick: () -> Unit,
    icon: ImageVector
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MarketEmptyCard(
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
