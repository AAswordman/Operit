package com.ai.assistance.operit.ui.features.packages.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.icons.rememberLogoPainter
import com.ai.assistance.operit.ui.features.packages.market.MarketBrowseCard
import com.ai.assistance.operit.ui.features.packages.market.MarketBrowseCardModel
import com.ai.assistance.operit.ui.features.packages.market.ToolPkgLogoAsset
import com.ai.assistance.operit.ui.features.packages.market.UnifiedMarketDetailHeader
import com.ai.assistance.operit.ui.features.packages.market.UnifiedMarketDetailHeaderCard
import com.ai.assistance.operit.ui.features.packages.market.UnifiedMarketDetailMetric
import com.ai.assistance.operit.ui.features.packages.market.UnifiedMarketDetailParticipant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarketPublishPreviewDialog(
    title: String,
    description: String,
    detail: String,
    version: String,
    author: String,
    logoAsset: ToolPkgLogoAsset?,
    onDismiss: () -> Unit
) {
    var selectedMode by rememberSaveable { mutableIntStateOf(0) }
    var selectedDetailTab by rememberSaveable { mutableIntStateOf(0) }
    val previewTitle = title.trim().ifBlank { stringResource(R.string.artifact_publish_logo_market_preview_untitled) }
    val previewDescription =
        description.trim().ifBlank { stringResource(R.string.artifact_publish_logo_market_preview_empty_description) }
    val previewDetail = detail.trim().ifBlank { previewDescription }
    val previewAuthor = author.trim().ifBlank { stringResource(R.string.artifact_publish_logo_market_preview_account) }
    val previewVersion = version.trim().ifBlank { "1.0.0" }
    val logoKey =
        "market-preview:${previewTitle}:${previewVersion}:${logoAsset?.fileName}:${logoAsset?.bytes?.contentHashCode()}"
    val listLogoPainter =
        rememberLogoPainter(
            logoKey = "$logoKey:list",
            bytes = logoAsset?.bytes,
            mimeType = logoAsset?.contentType,
            fileName = logoAsset?.fileName,
            size = 48.dp
        )
    val detailLogoPainter =
        rememberLogoPainter(
            logoKey = "$logoKey:detail",
            bytes = logoAsset?.bytes,
            mimeType = logoAsset?.contentType,
            fileName = logoAsset?.fileName,
            size = 76.dp
        )
    val fallbackAvatarText = previewAuthor.take(1).ifBlank { "?" }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.artifact_publish_logo_market_preview_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = stringResource(R.string.cancel)
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                )

                TabRow(
                    selectedTabIndex = selectedMode,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedMode == 0,
                        onClick = { selectedMode = 0 },
                        text = { Text(stringResource(R.string.artifact_publish_logo_market_list)) }
                    )
                    Tab(
                        selected = selectedMode == 1,
                        onClick = { selectedMode = 1 },
                        text = { Text(stringResource(R.string.artifact_publish_logo_market_detail)) }
                    )
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .padding(bottom = if (selectedMode == 1) 88.dp else 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.artifact_publish_logo_market_preview_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (selectedMode == 0) {
                            MarketBrowseCard(
                                model =
                                    MarketBrowseCardModel(
                                        title = previewTitle,
                                        description = previewDescription,
                                        ownerUsername = previewAuthor,
                                        showAction = false
                                    ),
                                onViewDetails = {},
                                onInstall = {},
                                logoPainter = listLogoPainter,
                                interactive = false
                            )
                        } else {
                            UnifiedMarketDetailHeaderCard(
                                header =
                                    UnifiedMarketDetailHeader(
                                        title = previewTitle,
                                        fallbackAvatarText = previewTitle.take(1),
                                        badges =
                                            listOf(
                                                stringResource(R.string.artifact_type_package),
                                                "v$previewVersion"
                                            ),
                                        participants =
                                            listOf(
                                                UnifiedMarketDetailParticipant(
                                                    roleLabel = stringResource(R.string.market_detail_author_role),
                                                    name = previewAuthor,
                                                    fallbackAvatarText = fallbackAvatarText
                                                ),
                                                UnifiedMarketDetailParticipant(
                                                    roleLabel = stringResource(R.string.market_detail_sharer_role),
                                                    name = previewAuthor,
                                                    fallbackAvatarText = fallbackAvatarText
                                                )
                                            ),
                                        metrics =
                                            listOf(
                                                UnifiedMarketDetailMetric(
                                                    value = "--",
                                                    label = stringResource(R.string.market_sort_downloads)
                                                ),
                                                UnifiedMarketDetailMetric(
                                                    value = "--",
                                                    label = stringResource(R.string.market_sort_likes)
                                                ),
                                                UnifiedMarketDetailMetric(
                                                    value = "--",
                                                    label = stringResource(R.string.market_detail_published_label)
                                                )
                                            )
                                    ),
                                logoPainter = detailLogoPainter
                            )

                            TabRow(
                                selectedTabIndex = selectedDetailTab,
                                containerColor = MaterialTheme.colorScheme.background,
                                contentColor = MaterialTheme.colorScheme.primary
                            ) {
                                Tab(
                                    selected = selectedDetailTab == 0,
                                    onClick = { selectedDetailTab = 0 },
                                    text = { Text(stringResource(R.string.market_detail_about_title)) }
                                )
                                Tab(
                                    selected = selectedDetailTab == 1,
                                    onClick = { selectedDetailTab = 1 },
                                    text = { Text(stringResource(R.string.comments_with_count, 0)) }
                                )
                            }

                            if (selectedDetailTab == 0) {
                                Text(
                                    text = previewDetail,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.no_comments_yet),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (selectedMode == 1) {
                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                            shadowElevation = 8.dp,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Button(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                                colors = ButtonDefaults.buttonColors()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.artifact_publish_logo_market_preview_action))
                            }
                        }
                    }
                }
            }
        }
    }
}
