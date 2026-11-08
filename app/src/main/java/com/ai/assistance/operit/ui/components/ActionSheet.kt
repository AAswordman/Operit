package com.ai.assistance.operit.ui.components

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 操作与单选列表共用的底部面板，长内容可以滚动。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(
    title: String,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                modifier = Modifier.padding(start = 12.dp, bottom = 12.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
fun ActionSheetItem(
    label: String,
    icon: ImageVector,
    selected: Boolean? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val foreground = colors.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).then(
            if (selected != null) Modifier.semantics { this.selected = selected } else Modifier
        ),
        shape = RoundedCornerShape(16.dp),
        color = if (selected == true) colors.surfaceContainerHighest else colors.surfaceContainerLow,
        contentColor = foreground,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape)
                    .background(colors.onSurface.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            if (selected == true) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
    }
}
