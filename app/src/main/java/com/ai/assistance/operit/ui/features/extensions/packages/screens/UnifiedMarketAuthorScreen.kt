package com.ai.assistance.operit.ui.features.extensions.packages.screens

import com.ai.assistance.operit.ui.common.oktoast.OKToast

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.ai.assistance.operit.data.api.MarketV2PublisherEntrySummary
import com.ai.assistance.operit.ui.features.extensions.packages.market.BindMarketSearchToTopBar
import com.ai.assistance.operit.ui.features.extensions.packages.market.MarketStatsType
import com.ai.assistance.operit.ui.features.extensions.packages.market.UnifiedMarketBrowseConfig
import com.ai.assistance.operit.ui.features.extensions.packages.screens.market.viewmodel.UnifiedMarketAuthorViewModel

@Composable
fun UnifiedMarketAuthorScreen(
    authorId: String,
    authorLogin: String,
    authorAvatarUrl: String,
    onNavigateToDetail: (MarketV2Entry) -> Unit
) {
    val context = LocalContext.current
    val viewModel: UnifiedMarketAuthorViewModel =
        viewModel(
            key = "market-author-$authorId",
            factory = UnifiedMarketAuthorViewModel.Factory(context.applicationContext, authorId)
        )
    val entries by viewModel.entries.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasLoaded by viewModel.hasLoaded.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val ownedEntries = entries.filter { it.relation.equals("owner", ignoreCase = true) }
    val contributedEntries = entries.filter { it.relation.equals("contributor", ignoreCase = true) }
    val displayName = authorLogin.ifBlank { authorId.removePrefix("gh_") }

    BindMarketSearchToTopBar(
        enabled = false,
        searchQuery = "",
        onSearchQueryChanged = { _ -> },
        searchPlaceholderRes = UnifiedMarketBrowseConfig.searchPlaceholderRes
    )

    LaunchedEffect(authorId) {
        viewModel.loadEntries()
    }

    errorMessage?.let { error ->
        LaunchedEffect(error) {
            OKToast.error(context, error, OKToast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            MarketAuthorHeaderCard(
                authorId = authorId,
                displayName = displayName,
                avatarUrl = authorAvatarUrl
            )
        }

        if (isLoading && !hasLoaded) {
            item {
                MarketAuthorLoadingCard()
            }
        } else {
            if (ownedEntries.isNotEmpty()) {
                item {
                    MarketAuthorSectionTitle(text = stringResource(R.string.market_author_profile_owned))
                }
                items(ownedEntries, key = { "owned-${it.id}" }) { entry ->
                    MarketAuthorEntryCard(
                        entry = entry,
                        onClick = { viewModel.openEntryDetail(entry, onNavigateToDetail) }
                    )
                }
            }

            if (contributedEntries.isNotEmpty()) {
                item {
                    MarketAuthorSectionTitle(text = stringResource(R.string.market_author_profile_contributed))
                }
                items(contributedEntries, key = { "contributed-${it.id}" }) { entry ->
                    MarketAuthorEntryCard(
                        entry = entry,
                        onClick = { viewModel.openEntryDetail(entry, onNavigateToDetail) }
                    )
                }
            }

            if (ownedEntries.isEmpty() && contributedEntries.isEmpty()) {
                item {
                    MarketAuthorEmptyCard()
                }
            }
        }
    }
}

@Composable
private fun MarketAuthorHeaderCard(
    authorId: String,
    displayName: String,
    avatarUrl: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        ),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (avatarUrl.isNotBlank()) {
                Image(
                    painter = rememberAsyncImagePainter(avatarUrl),
                    contentDescription = stringResource(R.string.user_avatar),
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(58.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.market_author_profile_id, authorId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MarketAuthorSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun MarketAuthorEntryCard(
    entry: MarketV2PublisherEntrySummary,
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
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.marketAuthorEntrySummary(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            MarketAuthorEntryRelationChip(entry.relation)
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MarketAuthorEntryRelationChip(relation: String) {
    val isContributor = relation.equals("contributor", ignoreCase = true)
    Surface(
        shape = RoundedCornerShape(999.dp),
        color =
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
    ) {
        Text(
            text =
                stringResource(
                    if (isContributor) {
                        R.string.market_manage_relation_contributor
                    } else {
                        R.string.market_manage_relation_owner
                    }
                ),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

@Composable
private fun MarketAuthorLoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            Text(
                text = stringResource(R.string.market_author_profile_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MarketAuthorEmptyCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.market_author_profile_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MarketV2PublisherEntrySummary.marketAuthorEntrySummary(): String {
    val typeText =
        when (type.lowercase()) {
            MarketStatsType.SCRIPT.wireValue -> stringResource(R.string.artifact_type_script)
            MarketStatsType.PACKAGE.wireValue -> stringResource(R.string.artifact_type_package)
            MarketStatsType.SKILL.wireValue -> stringResource(R.string.market_category_type_skill)
            MarketStatsType.MCP.wireValue -> stringResource(R.string.market_category_type_mcp)
            else -> type
        }
    val categoryText = categoryId.takeIf { it.isNotBlank() }?.let { marketCategoryLabel(it) }
    val updatedText = updatedAt.take(10).takeIf { it.isNotBlank() }
    return listOfNotNull(typeText, categoryText, updatedText).joinToString(" · ")
}
