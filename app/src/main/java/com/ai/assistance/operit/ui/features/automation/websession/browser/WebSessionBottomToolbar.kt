package com.ai.assistance.operit.ui.features.automation.websession.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

@Composable
internal fun WebSessionBottomToolbar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    currentTabNumber: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onNewTab: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 12.dp,
        border =
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrowserToolbarAction(
                enabled = canGoBack,
                onClick = onBack,
                contentDescription = stringResource(R.string.web_session_back),
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null
                    )
                }
            )
            BrowserToolbarAction(
                enabled = canGoForward,
                onClick = onForward,
                contentDescription = stringResource(R.string.web_session_forward),
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null
                    )
                }
            )
            BrowserToolbarPrimaryAction(
                onClick = onNewTab,
                contentDescription = stringResource(R.string.web_session_new_tab),
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null
                    )
                }
            )
            BrowserToolbarTabAction(
                currentTabNumber = currentTabNumber,
                onClick = onTabs,
                contentDescription =
                    if (currentTabNumber > 0) {
                        stringResource(R.string.web_session_tabs_with_index, currentTabNumber)
                    } else {
                        stringResource(R.string.web_session_tabs)
                    }
            )
            BrowserToolbarAction(
                onClick = onMenu,
                contentDescription = stringResource(R.string.web_session_menu),
                icon = {
                    Icon(
                        imageVector = Icons.Filled.MoreHoriz,
                        contentDescription = null
                    )
                }
            )
        }
    }
}

@Composable
private fun RowScope.BrowserToolbarAction(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val alpha = if (enabled) 1f else 0.42f

    Box(
        modifier =
            modifier
                .weight(1f)
                .alpha(alpha)
                .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier =
                Modifier
                    .clickable(enabled = enabled, onClick = onClick)
                    .semantics(mergeDescendants = true) {
                        this.contentDescription = contentDescription
                        role = Role.Button
                    },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(
                modifier = Modifier.size(46.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
        }
    }
}

@Composable
private fun RowScope.BrowserToolbarPrimaryAction(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier =
            modifier
                .weight(1f)
                .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier =
                Modifier
                    .clickable(onClick = onClick)
                    .semantics(mergeDescendants = true) {
                        this.contentDescription = contentDescription
                        role = Role.Button
                    },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 1.dp,
            shadowElevation = 4.dp
        ) {
            Box(
                modifier = Modifier.size(width = 56.dp, height = 42.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
        }
    }
}

@Composable
private fun RowScope.BrowserToolbarTabAction(
    currentTabNumber: Int,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier =
            modifier
                .weight(1f)
                .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier =
                Modifier
                    .clickable(onClick = onClick)
                    .semantics(mergeDescendants = true) {
                        this.contentDescription = contentDescription
                        role = Role.Button
                    },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border =
                BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)
                )
        ) {
            Box(
                modifier = Modifier.size(width = 44.dp, height = 38.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentTabNumber.coerceAtLeast(0).toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
