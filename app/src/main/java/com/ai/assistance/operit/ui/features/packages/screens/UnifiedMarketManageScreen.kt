package com.ai.assistance.operit.ui.features.packages.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.api.MarketV2PublisherEntrySummary
import com.ai.assistance.operit.ui.features.github.GitHubLoginDialog
import com.ai.assistance.operit.ui.features.packages.components.MarketManageDangerActionButton
import com.ai.assistance.operit.ui.features.packages.components.MarketManageDeleteDialog
import com.ai.assistance.operit.ui.features.packages.components.MarketManageItemCard
import com.ai.assistance.operit.ui.features.packages.components.MarketManageLabelChip
import com.ai.assistance.operit.ui.features.packages.components.MarketManagePrimaryActionButton
import com.ai.assistance.operit.ui.features.packages.components.MarketManageReviewReasonChip
import com.ai.assistance.operit.ui.features.packages.components.MarketManageReviewFlow
import com.ai.assistance.operit.ui.features.packages.components.MarketManageScaffold
import com.ai.assistance.operit.ui.features.packages.components.MarketManageSecondaryActionButton
import com.ai.assistance.operit.ui.features.packages.market.MarketReviewState
import com.ai.assistance.operit.ui.features.packages.market.MarketStatsType
import com.ai.assistance.operit.ui.features.packages.market.PublishArtifactType
import com.ai.assistance.operit.ui.features.packages.market.labelResId
import com.ai.assistance.operit.ui.features.packages.market.resolveMarketReviewSnapshot
import com.ai.assistance.operit.ui.features.packages.screens.market.viewmodel.UnifiedMarketManageKind
import com.ai.assistance.operit.ui.features.packages.screens.market.viewmodel.UnifiedMarketManageViewModel

private enum class ManageTypeTab(val kind: UnifiedMarketManageKind, val labelRes: Int) {
    SCRIPT(UnifiedMarketManageKind.SCRIPT, R.string.market_category_type_script),
    PACKAGE(UnifiedMarketManageKind.PACKAGE, R.string.market_category_type_package),
    SKILL(UnifiedMarketManageKind.SKILL, R.string.market_category_type_skill),
    MCP(UnifiedMarketManageKind.MCP, R.string.market_category_type_mcp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedMarketManageScreen(
    onNavigateToEditArtifact: (MarketV2Entry) -> Unit,
    onNavigateToEditRepo: (MarketStatsType, MarketV2Entry) -> Unit,
    onNavigateToPublishArtifactVersion: (MarketV2Entry) -> Unit,
    onNavigateToPublishRepoVersion: (MarketStatsType, MarketV2Entry) -> Unit,
    onNavigateToPublishArtifact: () -> Unit,
    onNavigateToPublishRepo: (MarketStatsType) -> Unit,
    onNavigateToDetail: (MarketV2Entry) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableStateOf(ManageTypeTab.SCRIPT) }
    val viewModel: UnifiedMarketManageViewModel =
        viewModel(
            key = "market-manage-${selectedTab.name}",
            factory =
                UnifiedMarketManageViewModel.Factory(
                    context.applicationContext,
                    selectedTab.kind
                )
        )

    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val hasLoaded by viewModel.hasLoaded.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val resubmittingEntryId by viewModel.resubmittingEntryId.collectAsState()

    var showDeleteDialog by remember { mutableStateOf<MarketV2PublisherEntrySummary?>(null) }
    var showResubmitDialog by remember { mutableStateOf<MarketV2PublisherEntrySummary?>(null) }
    var showReviewDialog by remember { mutableStateOf<MarketV2PublisherEntrySummary?>(null) }
    var showGitHubLogin by remember { mutableStateOf(false) }
    var showPublishDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggedIn, selectedTab) {
        if (isLoggedIn) {
            viewModel.loadEntries()
        } else {
            viewModel.reset()
        }
    }

    val showManageLoading = isLoggedIn && !hasLoaded && entries.isEmpty()
    val showEmptyState = hasLoaded && errorMessage == null && entries.isEmpty()
    val ownedEntries = remember(entries) { entries.filter { it.isOwnerRelation() } }
    val contributedEntries = remember(entries) { entries.filter { it.isContributorRelation() } }

    MarketManageScaffold(
        isLoggedIn = isLoggedIn,
        isLoading = showManageLoading,
        errorMessage = errorMessage,
        isEmpty = showEmptyState,
        onLogin = { showGitHubLogin = true },
        onPublish = { showPublishDialog = true },
        publishContentDescription = stringResource(R.string.publish_new_artifact),
        loginDescription = stringResource(R.string.need_login_github_manage_artifacts),
        loadingMessage = stringResource(R.string.loading_your_artifacts),
        emptyIcon = Icons.Default.Store,
        emptyTitle = stringResource(R.string.no_published_artifacts_yet),
        emptyDescription = stringResource(R.string.click_button_publish_first_artifact),
        emptyActionLabel = stringResource(R.string.publish_to_market),
        topContent = {
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                edgePadding = 0.dp
            ) {
                ManageTypeTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            androidx.compose.material3.Text(
                                text = stringResource(tab.labelRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
        }
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.loadEntries(refresh = true) },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (ownedEntries.isNotEmpty()) {
                        item(key = "owned-title") {
                            ManageSectionTitle(text = stringResource(R.string.market_manage_owned_section))
                        }
                        items(ownedEntries, key = { "owned-${it.id}" }) { entry ->
                            ManagedEntryCard(
                                entry = entry,
                                canManageEntry = true,
                                viewModel = viewModel,
                                onNavigateToDetail = onNavigateToDetail,
                                onNavigateToEditArtifact = onNavigateToEditArtifact,
                                onNavigateToEditRepo = onNavigateToEditRepo,
                                onNavigateToPublishArtifactVersion = onNavigateToPublishArtifactVersion,
                                onNavigateToPublishRepoVersion = onNavigateToPublishRepoVersion,
                                onRequestResubmit = { showResubmitDialog = it },
                                onShowReview = { showReviewDialog = it },
                                onDelete = { showDeleteDialog = it }
                            )
                        }
                    }
                    if (contributedEntries.isNotEmpty()) {
                        item(key = "contributed-title") {
                            if (ownedEntries.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            ManageSectionTitle(text = stringResource(R.string.market_manage_contributed_section))
                        }
                        items(contributedEntries, key = { "contributed-${it.id}" }) { entry ->
                            ManagedEntryCard(
                                entry = entry,
                                canManageEntry = false,
                                viewModel = viewModel,
                                onNavigateToDetail = onNavigateToDetail,
                                onNavigateToEditArtifact = onNavigateToEditArtifact,
                                onNavigateToEditRepo = onNavigateToEditRepo,
                                onNavigateToPublishArtifactVersion = onNavigateToPublishArtifactVersion,
                                onNavigateToPublishRepoVersion = onNavigateToPublishRepoVersion,
                                onRequestResubmit = { showResubmitDialog = it },
                                onShowReview = { showReviewDialog = it },
                                onDelete = { showDeleteDialog = it }
                            )
                        }
                    }
                }
            }
        }
    }

    showDeleteDialog?.let { entry ->
        MarketManageDeleteDialog(
            text = stringResource(R.string.confirm_remove_artifact_from_market, entry.title),
            onConfirm = {
                viewModel.withdrawEntry(entry)
                showDeleteDialog = null
            },
            onDismiss = { showDeleteDialog = null }
        )
    }

    if (showGitHubLogin) {
        GitHubLoginDialog(
            onDismissRequest = { showGitHubLogin = false }
        )
    }

    if (showPublishDialog) {
        ManagePublishChooserDialog(
            onDismiss = { showPublishDialog = false },
            onPublishArtifact = {
                showPublishDialog = false
                onNavigateToPublishArtifact()
            },
            onPublishRepo = { type ->
                showPublishDialog = false
                onNavigateToPublishRepo(type)
            }
        )
    }

    showResubmitDialog?.let { entry ->
        MarketManageResubmitDialog(
            entry = entry,
            onConfirm = {
                viewModel.resubmitEntry(entry)
                showResubmitDialog = null
            },
            onDismiss = { showResubmitDialog = null }
        )
    }

    showReviewDialog?.let { entry ->
        MarketManageReviewDialog(
            entry = entry,
            onDismiss = { showReviewDialog = null }
        )
    }

    if (resubmittingEntryId != null) {
        MarketManageBlockingProgressDialog(
            text = stringResource(R.string.market_manage_resubmitting_message)
        )
    }
}

@Composable
private fun ManageSectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun MarketManageBlockingProgressDialog(text: String) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.market_manage_resubmitting_title)) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(14.dp))
                Text(text = text)
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun MarketManageResubmitDialog(
    entry: MarketV2PublisherEntrySummary,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.market_manage_resubmit_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.market_manage_resubmit_confirm_message, entry.title))
                MarketManageReviewDialogContent(entry = entry)
                Text(
                    text = stringResource(R.string.market_manage_resubmit_confirm_warning),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.market_manage_resubmit_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun MarketManageReviewDialog(
    entry: MarketV2PublisherEntrySummary,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.market_manage_review_dialog_title)) },
        text = { MarketManageReviewDialogContent(entry = entry) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.confirm))
            }
        }
    )
}

@Composable
private fun MarketManageReviewDialogContent(entry: MarketV2PublisherEntrySummary) {
    val context = LocalContext.current
    val review = remember(entry) { entry.resolveMarketReviewSnapshot() }
    val stateText = remember(review.state) { context.getString(review.state.labelResId()) }
    val reasonText =
        remember(review.reasons) {
            review.reasons.map { reason -> context.getString(reason.labelResId()) }.joinToString(separator = " / ")
        }
    val reviewDetail = remember(entry.reviewDetail) { entry.reviewDetail?.trim().orEmpty() }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.market_manage_resubmit_last_state, stateText),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (reasonText.isNotBlank()) {
            Text(
                text = stringResource(R.string.market_manage_resubmit_last_reason, reasonText),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (reviewDetail.isNotBlank()) {
            Text(
                text = stringResource(R.string.market_manage_review_detail, reviewDetail),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ManagedEntryCard(
    entry: MarketV2PublisherEntrySummary,
    canManageEntry: Boolean,
    viewModel: UnifiedMarketManageViewModel,
    onNavigateToDetail: (MarketV2Entry) -> Unit,
    onNavigateToEditArtifact: (MarketV2Entry) -> Unit,
    onNavigateToEditRepo: (MarketStatsType, MarketV2Entry) -> Unit,
    onNavigateToPublishArtifactVersion: (MarketV2Entry) -> Unit,
    onNavigateToPublishRepoVersion: (MarketStatsType, MarketV2Entry) -> Unit,
    onRequestResubmit: (MarketV2PublisherEntrySummary) -> Unit,
    onShowReview: (MarketV2PublisherEntrySummary) -> Unit,
    onDelete: (MarketV2PublisherEntrySummary) -> Unit
) {
    val review = remember(entry) { entry.resolveMarketReviewSnapshot() }
    val reviewDetail = entry.reviewDetail?.trim().orEmpty()
    // A pending listing has no public detail shard, so public-detail actions cannot be opened yet.
    val canOpenPublicEntry = entry.isOpen() && entry.listingState != "pending_listing"
    MarketManageItemCard(
        title = entry.title,
        description = entry.manageSummaryText(),
        isOpen = canOpenPublicEntry,
        showActions = canManageEntry || canOpenPublicEntry,
        onClick = {
            if (canOpenPublicEntry) {
                viewModel.openEntryDetail(entry, onNavigateToDetail)
            } else {
                onShowReview(entry)
            }
        },
        headerContent = {
            MarketEntryTypeBadge(entry.type)
            MarketManageRelationBadge(entry.relation)
        },
        supportingContent = {
            MarketManageReviewFlow(
                reviewState = review.state,
                isOpen = entry.isOpen(),
                listingState = entry.listingState
            )
            if (review.reasons.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    review.reasons.take(2).forEach { reason ->
                        MarketManageReviewReasonChip(reason = reason)
                    }
                }
            }
            if (reviewDetail.isNotBlank()) {
                Text(
                    text = reviewDetail,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            if (canManageEntry && canOpenPublicEntry) {
                MarketManageSecondaryActionButton(
                    label = stringResource(R.string.edit),
                    icon = Icons.Default.Edit,
                    onClick = {
                        viewModel.openEntryDetail(entry) { fullEntry ->
                            when (val type = fullEntry.marketStatsType()) {
                                MarketStatsType.SCRIPT,
                                MarketStatsType.PACKAGE -> onNavigateToEditArtifact(fullEntry)
                                MarketStatsType.SKILL,
                                MarketStatsType.MCP -> onNavigateToEditRepo(type, fullEntry)
                                null -> Unit
                            }
                        }
                    }
                )
            }
            if (canOpenPublicEntry) {
                MarketManageSecondaryActionButton(
                    label = stringResource(R.string.market_publish_new_version),
                    icon = Icons.Default.NewReleases,
                    onClick = {
                        viewModel.openEntryDetail(entry) { fullEntry ->
                            when (val type = fullEntry.marketStatsType()) {
                                MarketStatsType.SCRIPT,
                                MarketStatsType.PACKAGE -> onNavigateToPublishArtifactVersion(fullEntry)
                                MarketStatsType.SKILL,
                                MarketStatsType.MCP -> onNavigateToPublishRepoVersion(type, fullEntry)
                                null -> Unit
                            }
                        }
                    }
                )
            }
            if (canManageEntry) {
                if (entry.isOpen()) {
                    MarketManageDangerActionButton(
                        label = stringResource(R.string.remove),
                        icon = Icons.Default.Delete,
                        onClick = { onDelete(entry) }
                    )
                } else if (review.state == MarketReviewState.CHANGES_REQUESTED) {
                    MarketManagePrimaryActionButton(
                        label = stringResource(R.string.market_manage_resubmit),
                        icon = Icons.Default.UploadFile,
                        onClick = { onRequestResubmit(entry) }
                    )
                }
            }
        }
    )
}

@Composable
private fun ManagePublishChooserDialog(
    onDismiss: () -> Unit,
    onPublishArtifact: () -> Unit,
    onPublishRepo: (MarketStatsType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.market_section_publish)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ManagePublishAction(
                    title = stringResource(R.string.market_publish_artifact),
                    onClick = onPublishArtifact
                )
                ManagePublishAction(
                    title = stringResource(R.string.market_publish_skill),
                    onClick = { onPublishRepo(MarketStatsType.SKILL) }
                )
                ManagePublishAction(
                    title = stringResource(R.string.market_publish_mcp),
                    onClick = { onPublishRepo(MarketStatsType.MCP) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ManagePublishAction(
    title: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun MarketEntryTypeBadge(type: String) {
    val label =
        when (type.lowercase()) {
            MarketStatsType.SCRIPT.wireValue -> stringResource(R.string.artifact_type_script)
            MarketStatsType.PACKAGE.wireValue -> stringResource(R.string.artifact_type_package)
            MarketStatsType.SKILL.wireValue -> stringResource(R.string.market_category_type_skill)
            MarketStatsType.MCP.wireValue -> stringResource(R.string.market_category_type_mcp)
            else -> type.ifBlank { "-" }
        }

    val isArtifact = PublishArtifactType.fromWireValue(type) != null
    MarketManageLabelChip(
        text = label,
        containerColor =
            if (isArtifact) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        contentColor =
            if (isArtifact) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            }
    )
}

@Composable
private fun MarketManageRelationBadge(relation: String) {
    val isContributor = relation.equals("contributor", ignoreCase = true)
    MarketManageLabelChip(
        text =
            stringResource(
                if (isContributor) {
                    R.string.market_manage_relation_contributor
                } else {
                    R.string.market_manage_relation_owner
                }
            ),
        containerColor =
            if (isContributor) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        contentColor =
            if (isContributor) {
                MaterialTheme.colorScheme.onTertiaryContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            }
    )
}

private fun MarketV2PublisherEntrySummary.isOpen(): Boolean {
    return stateCode.equals("approved", ignoreCase = true) || stateCode.equals("open", ignoreCase = true)
}

private fun MarketV2PublisherEntrySummary.isOwnerRelation(): Boolean {
    return relation.equals("owner", ignoreCase = true)
}

private fun MarketV2PublisherEntrySummary.isContributorRelation(): Boolean {
    return relation.equals("contributor", ignoreCase = true)
}

@Composable
private fun MarketV2PublisherEntrySummary.manageSummaryText(): String {
    val typeText =
        when (type.lowercase()) {
            MarketStatsType.SCRIPT.wireValue -> stringResource(R.string.artifact_type_script)
            MarketStatsType.PACKAGE.wireValue -> stringResource(R.string.artifact_type_package)
            MarketStatsType.SKILL.wireValue -> stringResource(R.string.market_category_type_skill)
            MarketStatsType.MCP.wireValue -> stringResource(R.string.market_category_type_mcp)
            else -> type
        }
    val categoryText =
        categoryId
            .takeIf { it.isNotBlank() }
            ?.let { marketCategoryLabel(it) }
    val updatedText = updatedAt.take(10).takeIf { it.isNotBlank() }
    return listOfNotNull(typeText, categoryText, updatedText).joinToString(" · ")
}

private fun MarketV2Entry.marketStatsType(): MarketStatsType? {
    return MarketStatsType.entries.firstOrNull { it.wireValue == type.lowercase() }
}

private fun MarketV2PublisherEntrySummary.marketStatsType(): MarketStatsType? {
    return MarketStatsType.entries.firstOrNull { it.wireValue == type.lowercase() }
}


