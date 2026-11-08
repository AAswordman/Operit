package com.ai.assistance.operit.ui.features.extensions.packages.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.features.extensions.packages.market.MarketReviewReason
import com.ai.assistance.operit.ui.features.extensions.packages.market.MarketReviewState
import com.ai.assistance.operit.ui.features.extensions.packages.market.labelResId

@Composable
fun MarketManageScaffold(
    isLoggedIn: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    isEmpty: Boolean,
    onLogin: () -> Unit,
    onPublish: () -> Unit,
    publishContentDescription: String,
    loginDescription: String,
    loadingMessage: String,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptyDescription: String,
    emptyActionLabel: String? = null,
    topContent: @Composable ColumnScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    CustomScaffold(
        floatingActionButton = {
            if (isLoggedIn) {
                FloatingActionButton(onClick = onPublish) {
                    Icon(Icons.Default.Add, contentDescription = publishContentDescription)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            topContent()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                if (!isLoggedIn) {
                    MarketManageLoginRequiredCard(
                        description = loginDescription,
                        onLogin = onLogin
                    )
                }

                errorMessage?.let { message ->
                    MarketManageErrorCard(message = message)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoading -> MarketManageLoadingState(message = loadingMessage)
                        isLoggedIn && isEmpty -> {
                            MarketManageEmptyState(
                                icon = emptyIcon,
                                title = emptyTitle,
                                description = emptyDescription,
                                actionLabel = emptyActionLabel,
                                onAction = onPublish
                            )
                        }

                        isLoggedIn -> content()
                    }
                }
            }
        }
    }
}

@Composable
fun MarketManageItemCard(
    title: String,
    description: String,
    isOpen: Boolean,
    modifier: Modifier = Modifier,
    showActions: Boolean = true,
    onClick: (() -> Unit)? = null,
    headerContent: @Composable RowScope.() -> Unit = {},
    supportingContent: @Composable ColumnScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit
) {
    val clickableModifier =
        if (onClick != null) {
            modifier.clickable(onClick = onClick)
        } else {
            modifier
        }

    Card(
        modifier = clickableModifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor =
                if (isOpen) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surface
                }
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.width(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = headerContent
                )
            }

            if (description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                content = supportingContent
            )

            if (showActions) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            }
        }
    }
}

@Composable
fun MarketManageLabelChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MarketManageReviewFlow(
    reviewState: MarketReviewState,
    isOpen: Boolean,
    listingState: String,
    modifier: Modifier = Modifier
) {
    val reviewColor = marketReviewFlowColor(reviewState)
    val isPendingListing = listingState == "pending_listing"
    val publicationActive = reviewState == MarketReviewState.APPROVED && isOpen && !isPendingListing
    val publicationLabel =
        when (reviewState) {
            MarketReviewState.APPROVED ->
                if (isPendingListing || !isOpen) {
                    stringResource(R.string.market_review_step_scheduled)
                } else {
                    stringResource(R.string.published)
                }
            MarketReviewState.PENDING,
            MarketReviewState.CHANGES_REQUESTED,
            MarketReviewState.REJECTED,
            MarketReviewState.WITHDRAWN -> stringResource(R.string.market_review_step_unlisted)
        }
    val publicationColor =
        if (publicationActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        }
    val secondConnectorColor =
        if (publicationActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MarketReviewFlowStep(
            label = stringResource(R.string.market_review_step_submitted),
            color = MaterialTheme.colorScheme.primary,
            active = true,
            modifier = Modifier.weight(1f)
        )
        MarketReviewFlowConnector(color = reviewColor)
        MarketReviewFlowStep(
            label = stringResource(reviewState.labelResId()),
            color = reviewColor,
            active = true,
            modifier = Modifier.weight(1f)
        )
        MarketReviewFlowConnector(color = secondConnectorColor)
        MarketReviewFlowStep(
            label = publicationLabel,
            color = publicationColor,
            active = publicationActive,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MarketReviewFlowStep(
    label: String,
    color: Color,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val contentColor =
        if (active) {
            color
        } else {
            MaterialTheme.colorScheme.outline
        }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(7.dp),
            shape = RoundedCornerShape(999.dp),
            color = contentColor
        ) {}
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MarketReviewFlowConnector(color: Color) {
    Surface(
        modifier = Modifier
            .width(10.dp)
            .height(1.dp),
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.75f)
    ) {}
}

@Composable
private fun marketReviewFlowColor(reviewState: MarketReviewState): Color {
    return when (reviewState) {
        MarketReviewState.PENDING -> MaterialTheme.colorScheme.tertiary
        MarketReviewState.APPROVED -> MaterialTheme.colorScheme.primary
        MarketReviewState.CHANGES_REQUESTED -> MaterialTheme.colorScheme.secondary
        MarketReviewState.REJECTED -> MaterialTheme.colorScheme.error
        MarketReviewState.WITHDRAWN -> MaterialTheme.colorScheme.outline
    }
}

@Composable
fun MarketManageReviewStatusChip(
    reviewState: MarketReviewState,
    modifier: Modifier = Modifier
) {
    val label =
        stringResource(reviewState.labelResId())
    val containerColor =
        when (reviewState) {
            MarketReviewState.PENDING -> MaterialTheme.colorScheme.tertiaryContainer
            MarketReviewState.APPROVED -> MaterialTheme.colorScheme.secondaryContainer
            MarketReviewState.CHANGES_REQUESTED -> MaterialTheme.colorScheme.primaryContainer
            MarketReviewState.REJECTED -> MaterialTheme.colorScheme.errorContainer
            MarketReviewState.WITHDRAWN -> MaterialTheme.colorScheme.surfaceVariant
        }
    val contentColor =
        when (reviewState) {
            MarketReviewState.PENDING -> MaterialTheme.colorScheme.onTertiaryContainer
            MarketReviewState.APPROVED -> MaterialTheme.colorScheme.onSecondaryContainer
            MarketReviewState.CHANGES_REQUESTED -> MaterialTheme.colorScheme.onPrimaryContainer
            MarketReviewState.REJECTED -> MaterialTheme.colorScheme.onErrorContainer
            MarketReviewState.WITHDRAWN -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    MarketManageLabelChip(
        text = label,
        containerColor = containerColor,
        contentColor = contentColor,
        modifier = modifier
    )
}

@Composable
fun MarketManageReviewReasonChip(
    reason: MarketReviewReason,
    modifier: Modifier = Modifier
) {
    val label =
        stringResource(reason.labelResId())

    MarketManageLabelChip(
        text = label,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
fun RowScope.MarketManageSecondaryActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun RowScope.MarketManageDangerActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
fun RowScope.MarketManagePrimaryActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun MarketManageDeleteDialog(
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    titleText: String = stringResource(R.string.confirm_delete),
    confirmText: String = stringResource(R.string.confirm_delete_action),
    dismissText: String = stringResource(R.string.cancel)
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        }
    )
}

@Composable
private fun MarketManageLoginRequiredCard(
    description: String,
    onLogin: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.please_login_github_first),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onLogin) {
                Text(stringResource(R.string.login_github))
            }
        }
    }
}

@Composable
private fun MarketManageErrorCard(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun MarketManageLoadingState(message: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(8.dp))
        Text(message)
    }
}

@Composable
private fun MarketManageEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            actionLabel?.let { label ->
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onAction) {
                    Text(label)
                }
            }
        }
    }
}


