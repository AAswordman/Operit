package com.ai.assistance.operit.ui.common.markdown

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

@Composable
internal fun ResponseActivityHeader(
    durationMs: Long,
    expanded: Boolean,
    textColor: Color,
    onClick: () -> Unit,
) {
    val seconds = durationMs.coerceAtLeast(0L) / 1000
    val title = when {
        durationMs <= 0L -> stringResource(R.string.collapse_process_title)
        seconds < 60 -> stringResource(R.string.collapse_process_duration_seconds, seconds)
        seconds < 3600 -> stringResource(R.string.collapse_process_duration_minutes, seconds / 60, seconds % 60)
        else -> stringResource(
            R.string.collapse_process_duration_hours,
            seconds / 3600,
            (seconds % 3600) / 60,
            seconds % 60,
        )
    }
    val action = stringResource(
        if (expanded) R.string.collapse_process_hide else R.string.collapse_process_show
    )
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f * direction else 0f,
        animationSpec = tween(200),
        label = "response-activity-arrow",
    )
    val color = textColor.copy(alpha = 0.55f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 32.dp)
            .semantics(mergeDescendants = true) { stateDescription = action }
            .clickable(role = Role.Button, onClickLabel = action, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, color = color, style = MaterialTheme.typography.labelMedium)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp).rotate(rotation),
        )
    }
}
