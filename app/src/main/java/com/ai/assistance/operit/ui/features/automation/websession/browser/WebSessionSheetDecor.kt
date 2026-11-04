package com.ai.assistance.operit.ui.features.automation.websession.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun WebSessionSheetScaffold(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth(0.12f)
                    .height(4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                        shape = CircleShape
                    )
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        content()
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
internal fun WebSessionSectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(horizontal = 2.dp)
    )
}

@Composable
internal fun WebSessionItemCard(
    onClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    }
                ),
        shape = shape,
        color =
            if (highlighted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        contentColor =
            if (highlighted) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        border =
            BorderStroke(
                width = 1.dp,
                color =
                    if (highlighted) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    }
            ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        content()
    }
}

@Composable
internal fun WebSessionEmptyState(
    icon: ImageVector,
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border =
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Box(
                    modifier = Modifier.padding(10.dp),
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
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
