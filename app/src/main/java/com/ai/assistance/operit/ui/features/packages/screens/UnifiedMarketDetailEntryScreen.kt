package com.ai.assistance.operit.ui.features.packages.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberAsyncImagePainter
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.api.MarketV2Version
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.ui.features.packages.market.MarketReviewState
import com.ai.assistance.operit.ui.features.packages.market.MarketInstallProgress
import com.ai.assistance.operit.ui.features.packages.market.MarketInstallStage
import com.ai.assistance.operit.ui.features.packages.market.MarketLocalInstallState
import com.ai.assistance.operit.ui.features.packages.market.MarketLocalInstallStateKind
import com.ai.assistance.operit.ui.features.packages.market.UnifiedMarketDetailAction
import com.ai.assistance.operit.ui.features.packages.market.UnifiedMarketDetailBanner
import com.ai.assistance.operit.ui.features.packages.market.UnifiedMarketDetailCommentDialog
import com.ai.assistance.operit.ui.features.packages.market.UnifiedMarketDetailCommentsState
import com.ai.assistance.operit.ui.features.packages.market.UnifiedMarketDetailHeader
import com.ai.assistance.operit.ui.features.packages.market.UnifiedMarketDetailIconAction
import com.ai.assistance.operit.ui.features.packages.market.UnifiedMarketDetailInfoRow
import com.ai.assistance.operit.ui.features.packages.market.UnifiedMarketDetailMetric
import com.ai.assistance.operit.ui.features.packages.market.UnifiedMarketDetailParticipant
import com.ai.assistance.operit.ui.features.packages.market.UnifiedMarketDetailReactionOption
import com.ai.assistance.operit.ui.features.packages.market.UnifiedMarketDetailReactionsState
import com.ai.assistance.operit.ui.features.packages.market.UnifiedMarketDetailScreen
import com.ai.assistance.operit.ui.features.packages.market.UnifiedMarketDetailSection
import com.ai.assistance.operit.ui.features.packages.market.canInstallFromUnifiedMarket
import com.ai.assistance.operit.ui.features.packages.market.formatMarketDetailCompactDate
import com.ai.assistance.operit.ui.features.packages.market.formatMarketDetailDate
import com.ai.assistance.operit.ui.features.packages.market.isUnsupportedByCurrentAppVersion
import com.ai.assistance.operit.ui.features.packages.market.labelResId
import com.ai.assistance.operit.ui.features.packages.market.marketDetailInitial
import com.ai.assistance.operit.ui.features.packages.market.resolveMarketReviewSnapshot
import com.ai.assistance.operit.ui.features.packages.screens.market.viewmodel.UnifiedMarketDetailViewModel

@Composable
fun UnifiedMarketDetailEntryScreen(
    initialEntry: MarketV2Entry,
    fromManage: Boolean = false,
    onNavigateBack: () -> Unit = {},
    onPublishNewVersion: (MarketV2Entry) -> Unit = {},
    onNavigateToAuthor: (String, String, String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val viewModel: UnifiedMarketDetailViewModel =
        viewModel(
            key = "market-detail-${initialEntry.id}",
            factory = UnifiedMarketDetailViewModel.Factory(context.applicationContext)
        )
    val githubAuth = remember { GitHubAuthPreferences.getInstance(context) }
    val currentUser by githubAuth.userInfoFlow.collectAsState(initial = null)

    val loadedEntry by viewModel.entry.collectAsState()
    val entry = loadedEntry ?: initialEntry
    val errorMessage by viewModel.errorMessage.collectAsState()
    val commentsMap by viewModel.entryComments.collectAsState()
    val isLoadingComments by viewModel.isLoadingComments.collectAsState()
    val isPostingComment by viewModel.isPostingComment.collectAsState()
    val isDeletingComment by viewModel.isDeletingComment.collectAsState()
    val reactionsMap by viewModel.entryReactions.collectAsState()
    val isLoadingReactions by viewModel.isLoadingReactions.collectAsState()
    val isReacting by viewModel.isReacting.collectAsState()
    val installStates by viewModel.installStates.collectAsState()
    val localInstallStates by viewModel.localInstallStates.collectAsState()
    val entryId = entry.id
    val sourceUrl = entry.source?.url.orEmpty()
    val review = remember(entry) { entry.resolveMarketReviewSnapshot() }
    val currentComments = commentsMap[entryId].orEmpty()
    val currentReactions = reactionsMap[entryId].orEmpty()
    val installProgress = installStates[entryId]
    val localInstallState = localInstallStates[entryId]
    val isCurrentAppVersionUnsupported = entry.isUnsupportedByCurrentAppVersion()
    val likes =
        if (currentReactions.isNotEmpty()) {
            currentReactions.sumOf { if (it.reaction.ifBlank { it.content } == "+1") it.total.coerceAtLeast(1) else 0 }
        } else {
            entry.marketLikeCount()
        }
    val isPreviewMode = fromManage && entry.isOpen() && review.state != MarketReviewState.APPROVED
    var hasThumbsUp by remember { mutableStateOf(false) }
    var showCommentDialog by remember { mutableStateOf(false) }
    var showEditCommentDialog by remember { mutableStateOf(false) }
    var editingCommentId by remember { mutableStateOf<String?>(null) }
    var replyingCommentId by remember { mutableStateOf<String?>(null) }
    var commentText by remember { mutableStateOf("") }
    var waitingForCommentPost by remember { mutableStateOf(false) }
    var commentPostStarted by remember { mutableStateOf(false) }
    var selectedEntry by remember(initialEntry.id) { mutableStateOf(initialEntry) }
    var showVersionHistoryDialog by remember { mutableStateOf(false) }

    LaunchedEffect(selectedEntry) {
        viewModel.loadEntry(selectedEntry)
    }

    LaunchedEffect(entryId) {
        if (entryId.isNotBlank()) {
            viewModel.loadEntryComments(entryId)
            viewModel.loadEntryReactions(entry)
        }
        if (sourceUrl.isNotBlank()) viewModel.fetchRepositoryInfo(sourceUrl)
    }

    val isPostingCurrentComment = entryId in isPostingComment
    LaunchedEffect(isPostingCurrentComment, waitingForCommentPost, commentPostStarted) {
        if (waitingForCommentPost && isPostingCurrentComment) {
            commentPostStarted = true
        } else if (waitingForCommentPost && commentPostStarted) {
            showCommentDialog = false
            replyingCommentId = null
            commentText = ""
            waitingForCommentPost = false
            commentPostStarted = false
        }
    }

    errorMessage?.let { error ->
        LaunchedEffect(error) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    UnifiedMarketDetailScreen(
        onNavigateBack = onNavigateBack,
        header =
            UnifiedMarketDetailHeader(
                title = entry.title,
                fallbackAvatarText = marketDetailInitial(entry.title),
                participants =
                    listOf(
                        UnifiedMarketDetailParticipant(
                            roleLabel = stringResource(R.string.market_detail_author_role),
                            name = entry.marketAuthorName(),
                            avatarUrl = entry.marketAuthorAvatar(),
                            fallbackAvatarText = marketDetailInitial(entry.marketAuthorName()),
                            authorId = entry.marketAuthorId(),
                            onClick = onNavigateToAuthor
                        ),
                        UnifiedMarketDetailParticipant(
                            roleLabel = stringResource(R.string.market_detail_sharer_role),
                            name = entry.marketPublisherName(),
                            avatarUrl = entry.marketPublisherAvatar(),
                            fallbackAvatarText = marketDetailInitial(entry.marketPublisherName()),
                            authorId = entry.marketPublisherId(),
                            onClick = onNavigateToAuthor
                        )
                    ),
                badges = entry.detailBadges(),
                metrics =
                    listOf(
                        UnifiedMarketDetailMetric(
                            value = entry.downloadsCount().toString(),
                            label = stringResource(R.string.market_sort_downloads)
                        ),
                        UnifiedMarketDetailMetric(
                            value = likes.toString(),
                            label = stringResource(R.string.market_sort_likes)
                        ),
                        UnifiedMarketDetailMetric(
                            value = formatMarketDetailCompactDate(entry.createdAt ?: entry.updatedAt ?: ""),
                            label = stringResource(R.string.market_detail_published_label)
                        )
                    ),
                statusLabel =
                    if (entry.isOpen()) {
                        stringResource(R.string.market_detail_status_available)
                    } else {
                        stringResource(R.string.market_detail_status_closed)
                    }
            ),
        primaryAction =
            UnifiedMarketDetailAction(
                label = localInstallState.detailActionLabel(),
                onClick = { viewModel.installEntry(entry) },
                enabled =
                    entry.canInstallFromUnifiedMarket() &&
                        !isCurrentAppVersionUnsupported &&
                        installProgress == null &&
                        localInstallState?.kind != MarketLocalInstallStateKind.INSTALLED,
                isLoading = installProgress != null,
                loadingLabel = installProgress?.detailLabel(),
                icon =
                    if (isCurrentAppVersionUnsupported) {
                        Icons.Default.Warning
                    } else if (localInstallState.shouldShowSwitchAction()) {
                        Icons.Default.Update
                    } else {
                        Icons.Default.Check
                    },
                isWarning = isCurrentAppVersionUnsupported
            ),
        secondaryAction =
            sourceUrl.takeIf { it.isNotBlank() }?.let { repositoryUrl ->
                UnifiedMarketDetailAction(
                    label = stringResource(R.string.mcp_plugin_repository),
                    onClick = { openExternalUrl(context, repositoryUrl) },
                    icon = Icons.Default.OpenInNew
                )
            },
        banner =
            if (isPreviewMode) {
                UnifiedMarketDetailBanner(
                    title = stringResource(R.string.market_detail_preview_title),
                    message = buildPreviewBannerMessage(context, review.state),
                    icon = Icons.Default.Warning,
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onTertiaryContainer
                )
            } else {
                null
            },
        sections = entry.detailSections(),
        overviewExtraContent = {
            val mainPublisherId = entry.publisher?.id ?: entry.publisherId
            val entryContributors = entry.contributors.filter { it.id != mainPublisherId && it.id.isNotBlank() }
            if (entryContributors.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.market_detail_contributors),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(entryContributors, key = { it.id }) { contributor ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier =
                                        Modifier
                                            .width(48.dp)
                                            .clickable {
                                                onNavigateToAuthor(
                                                    contributor.id,
                                                    contributor.login,
                                                    contributor.avatarUrl ?: contributor.avatar ?: ""
                                                )
                                            }
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(
                                            (contributor.avatarUrl ?: contributor.avatar ?: "").ifBlank { "" }
                                        ),
                                        contentDescription = contributor.login,
                                        modifier = Modifier.size(32.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                    Text(
                                        text = contributor.login,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (entry.allowPublicUpdates && entry.isOpen()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.market_publish_new_version),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.market_publish_new_version_detail_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { onPublishNewVersion(entry) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.market_publish_new_version))
                        }
                    }
                }
            }
        },
            entry.versions.takeIf { it.isNotEmpty() }?.let {
                UnifiedMarketDetailIconAction(
                    contentDescription = stringResource(R.string.market_detail_view_version_history),
                    onClick = { showVersionHistoryDialog = true },
                    icon = Icons.Default.History
                )
            },
        metadataTitle = stringResource(R.string.metadata_title),
        metadataRows = entry.metadataRows(context, review),
        reactions =
            UnifiedMarketDetailReactionsState(
                title = stringResource(R.string.mcp_plugin_community_feedback),
                helperText = if (currentUser == null) stringResource(R.string.mcp_plugin_login_required) else null,
                isLoading = entryId in isLoadingReactions,
                isMutating = entryId in isReacting,
                options =
                    listOf(
                        UnifiedMarketDetailReactionOption(
                            label = stringResource(R.string.market_detail_like_action),
                            count = likes,
                            icon = Icons.Default.ThumbUp,
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            isSelected = hasThumbsUp,
                            enabled = currentUser != null,
                            onClick = {
                                if (!hasThumbsUp) {
                                    hasThumbsUp = true
                                    viewModel.addReactionToEntry(entryId)
                                }
                            }
                        )
                    )
            ),
        comments =
            UnifiedMarketDetailCommentsState(
                title = stringResource(R.string.mcp_plugin_comments, currentComments.size),
                comments = currentComments,
                isLoading = entryId in isLoadingComments,
                isPosting = entryId in isPostingComment,
                canPost = currentUser != null,
                postHint = if (currentUser == null) stringResource(R.string.mcp_plugin_login_required) else null,
                currentUserLogin = currentUser?.login,
                currentUserAuthorId = currentUser?.id?.let { "gh_$it" },
                entryOwnerLogin = entry.marketPublisherName(),
                entryOwnerAuthorId = entry.marketPublisherId(),
                onRefresh = { viewModel.loadEntryComments(entryId) },
                onRequestPost = {
                    replyingCommentId = null
                    showCommentDialog = true
                },
                onReplyToComment = { comment ->
                    replyingCommentId = comment.id
                    commentText = ""
                    showCommentDialog = true
                },
                onEditComment = { comment ->
                    editingCommentId = comment.id
                    commentText = comment.body
                    showEditCommentDialog = true
                },
                onDeleteComment = { comment ->
                    viewModel.deleteComment(entryId, comment.id)
                }
            )
    )

    if (showEditCommentDialog) {
        UnifiedMarketDetailCommentDialog(
            commentText = commentText,
            onCommentTextChange = { commentText = it },
            isPosting = entryId in isPostingComment,
            onDismiss = {
                showEditCommentDialog = false
                editingCommentId = null
            },
            onPost = {
                editingCommentId?.let { viewModel.editComment(entryId, it, commentText) }
                showEditCommentDialog = false
                editingCommentId = null
            }
        )
    }

    if (showCommentDialog) {
        UnifiedMarketDetailCommentDialog(
            commentText = commentText,
            onCommentTextChange = { commentText = it },
            onDismiss = {
                showCommentDialog = false
                replyingCommentId = null
                commentText = ""
            },
            onPost = {
                if (commentText.isNotBlank()) {
                    waitingForCommentPost = true
                    commentPostStarted = false
                    viewModel.postEntryComment(entryId, commentText, replyingCommentId)
                }
            },
            isPosting = entryId in isPostingComment
        )
    }

    if (isDeletingComment.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.market_detail_deleting_comment)) },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.market_detail_deleting_comment_message))
                }
            },
            confirmButton = {}
        )
    }

    if (showVersionHistoryDialog) {
        MarketVersionHistoryDialog(
            entry = entry,
            onDismiss = { showVersionHistoryDialog = false },
            onNavigateToAuthor = onNavigateToAuthor,
            onSelectVersion = { version ->
                selectedEntry = entry.withSelectedVersion(version)
                showVersionHistoryDialog = false
            }
        )
    }
}

@Composable
private fun MarketVersionHistoryDialog(
    entry: MarketV2Entry,
    onDismiss: () -> Unit,
    onNavigateToAuthor: (String, String, String) -> Unit,
    onSelectVersion: (MarketV2Version) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.market_detail_version_history_title)) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entry.versions, key = { it.id }) { version ->
                    MarketVersionHistoryRow(
                        version = version,
                        selected = version.id == entry.latestVersion?.id,
                        onNavigateToAuthor = onNavigateToAuthor,
                        onClick = { onSelectVersion(version) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_close))
            }
        }
    )
}

@Composable
private fun MarketVersionHistoryRow(
    version: MarketV2Version,
    selected: Boolean,
    onNavigateToAuthor: (String, String, String) -> Unit,
    onClick: () -> Unit
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color =
            if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "v${normalizeDetailVersionBadge(version.version)}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (selected) {
                    Text(
                        text = stringResource(R.string.market_detail_current_version),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            version.publishedAt?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = stringResource(R.string.market_detail_published_label) + " " + formatMarketDetailDate(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text =
                    stringResource(
                        R.string.supported_app_versions_colon,
                        version.minAppVer.orEmpty(),
                        version.maxAppVer.orEmpty()
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            version.publisher?.let { publisher ->
                Row(
                    modifier =
                        Modifier.clickable {
                            if (publisher.id.isNotBlank()) {
                                onNavigateToAuthor(
                                    publisher.id,
                                    publisher.login,
                                    publisher.avatarUrl ?: publisher.avatar ?: ""
                                )
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(
                            (publisher.avatarUrl ?: publisher.avatar ?: "").ifBlank { "" }
                        ),
                        contentDescription = publisher.login,
                        modifier = Modifier.size(18.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Text(
                        text = publisher.login,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            version.changelog.takeIf { it.isNotBlank() }?.let {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MarketInstallProgress.detailLabel(): String {
    return when (stage) {
        MarketInstallStage.CONNECTING -> stringResource(R.string.market_install_connecting)
        MarketInstallStage.FETCHING_METADATA -> stringResource(R.string.market_install_fetching_metadata)
        MarketInstallStage.CHECKING_LOCAL -> stringResource(R.string.market_install_checking_local)
        MarketInstallStage.DOWNLOADING ->
            progress?.let { stringResource(R.string.market_install_downloading_percent, (it * 100).toInt().coerceIn(0, 100)) }
                ?: stringResource(R.string.market_install_downloading)
        MarketInstallStage.VERIFYING -> stringResource(R.string.market_install_verifying)
        MarketInstallStage.IMPORTING_REPOSITORY -> stringResource(R.string.market_install_importing_repository)
        MarketInstallStage.IMPORTING_CONFIG -> stringResource(R.string.market_install_importing_config)
        MarketInstallStage.INSTALLING -> stringResource(R.string.market_install_installing)
        MarketInstallStage.RECORDING -> stringResource(R.string.market_install_recording)
    }
}

@Composable
private fun MarketLocalInstallState?.detailActionLabel(): String {
    return when (this?.kind) {
        MarketLocalInstallStateKind.INSTALLED -> stringResource(R.string.mcp_plugin_installed)
        MarketLocalInstallStateKind.UPDATE_AVAILABLE -> stringResource(R.string.market_install_switch_to_version)
        MarketLocalInstallStateKind.NOT_INSTALLED,
        null -> stringResource(R.string.mcp_plugin_install)
    }
}

private fun MarketLocalInstallState?.shouldShowSwitchAction(): Boolean {
    return when (this?.kind) {
        MarketLocalInstallStateKind.UPDATE_AVAILABLE -> true
        MarketLocalInstallStateKind.INSTALLED,
        MarketLocalInstallStateKind.NOT_INSTALLED,
        null -> false
    }
}

private fun MarketV2Entry.detailSections(): List<UnifiedMarketDetailSection> {
    return buildList {
        val about = detail.ifBlank { description }
        if (about.isNotBlank()) {
            add(
                UnifiedMarketDetailSection(
                    title = "About",
                    body = about,
                    icon = Icons.Default.Info,
                    showTitle = false
                )
            )
        }
        latestVersion?.installConfig?.takeIf { it.isNotBlank() }?.let { config ->
            add(
                UnifiedMarketDetailSection(
                    title = "Install Config",
                    body = config,
                    icon = Icons.Default.Code,
                    isCodeBlock = true
                )
            )
        }
    }
}

private fun MarketV2Entry.metadataRows(
    context: Context,
    review: com.ai.assistance.operit.ui.features.packages.market.MarketReviewSnapshot
): List<UnifiedMarketDetailInfoRow> {
    return buildList {
        add(UnifiedMarketDetailInfoRow(context.getString(R.string.type_label), type.ifBlank { "-" }, Icons.Default.Info))
        latestVersion?.version?.takeIf { it.isNotBlank() }?.let {
            add(UnifiedMarketDetailInfoRow(context.getString(R.string.version_label), it, Icons.Default.Update))
        }
        categoryId.takeIf { it.isNotBlank() }?.let {
            add(
                UnifiedMarketDetailInfoRow(
                    context.getString(R.string.market_detail_category_label),
                    marketCategoryLabel(context, it),
                    Icons.Default.Info
                )
            )
        }
        source?.url?.takeIf { it.isNotBlank() }?.let {
            add(UnifiedMarketDetailInfoRow(context.getString(R.string.mcp_plugin_repository), it, Icons.Default.Code))
        }
        add(
            UnifiedMarketDetailInfoRow(
                context.getString(R.string.market_review_status_label),
                context.getString(review.state.labelResId()),
                Icons.Default.Check
            )
        )
        add(
            UnifiedMarketDetailInfoRow(
                context.getString(R.string.market_detail_published_label),
                formatMarketDetailDate(createdAt ?: updatedAt ?: ""),
                Icons.Default.CalendarToday
            )
        )
        add(
            UnifiedMarketDetailInfoRow(
                context.getString(R.string.updated_at_label),
                formatMarketDetailDate(updatedAt ?: createdAt ?: ""),
                Icons.Default.Update
            )
        )
    }
}

private fun MarketV2Entry.detailBadges(): List<String> {
    return buildList {
        if (type.isNotBlank()) add(type)
        if (categoryId.isNotBlank()) add(categoryId)
        latestVersion?.version?.takeIf { it.isNotBlank() }?.let { add("v${normalizeDetailVersionBadge(it)}") }
    }
}

private fun MarketV2Entry.withSelectedVersion(version: MarketV2Version): MarketV2Entry {
    return copy(
        versions = listOf(version) + versions.filterNot { it.id == version.id },
        latestVersion = version
    )
}

private fun MarketV2Entry.downloadsCount(): Int = stats?.downloads ?: downloads.takeIf { it > 0 } ?: downloadCount

private fun MarketV2Entry.marketLikeCount(): Int {
    return stats?.likes ?: reactions.sumOf { reaction ->
        val key = reaction.reaction.ifBlank { reaction.content }
        if (key == "+1" || key.equals("like", ignoreCase = true)) reaction.total.coerceAtLeast(1) else 0
    }
}

private fun MarketV2Entry.isOpen(): Boolean {
    return stateCode.equals("approved", ignoreCase = true) || stateCode.equals("open", ignoreCase = true)
}

private fun MarketV2Entry.marketAuthorName(): String {
    return author?.login.orEmpty()
        .ifBlank { authorId.removePrefix("gh_") }
        .ifBlank { "unknown" }
}

private fun MarketV2Entry.marketAuthorId(): String {
    return author?.id.orEmpty().ifBlank { authorId }
}

private fun MarketV2Entry.marketAuthorAvatar(): String {
    return author?.avatarUrl ?: author?.avatar ?: ""
}

private fun MarketV2Entry.marketPublisherName(): String {
    return publisher?.login.orEmpty()
        .ifBlank { publisherId.removePrefix("gh_") }
        .ifBlank { "unknown" }
}

private fun MarketV2Entry.marketPublisherId(): String {
    return publisher?.id.orEmpty().ifBlank { publisherId }
}

private fun MarketV2Entry.marketPublisherAvatar(): String {
    return publisher?.avatarUrl ?: publisher?.avatar ?: ""
}

private fun normalizeDetailVersionBadge(value: String): String {
    return value.removePrefix("v").removePrefix("V")
}

private fun openExternalUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private fun buildPreviewBannerMessage(
    context: Context,
    state: MarketReviewState
): String {
    return context.getString(state.labelResId())
}
