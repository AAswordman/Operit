package com.ai.assistance.operit.ui.features.chat.components.part

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R

// Define a local data class for UI purposes to avoid dependency on the core tools package.
data class FileDiff(
    val path: String,
    val diffContent: String,
    val details: String
)

@Composable
fun FileDiffDisplay(
    diff: FileDiff,
    modifier: Modifier = Modifier,
    summaryPrefix: String? = null,
    enableDialog: Boolean = true,
) {
    val diffLines = diff.diffContent.trimEnd().lines()
    var showDetailDialog by remember { mutableStateOf(false) }

    if (showDetailDialog && enableDialog) {
        ContentDetailDialog(
            title = "File Changes: ${diff.path.substringAfterLast('/')}",
            content = diff.diffContent,
            icon = Icons.Default.Difference,
            onDismiss = { showDetailDialog = false },
            isDiffContent = true
        )
    }

    val changeSummary = remember(diffLines) {
        val additions = diffLines.count { it.startsWith("+") }
        val deletions = diffLines.count { it.startsWith("-") }
        when {
            additions > 0 && deletions > 0 -> "$additions insertions(+), $deletions deletions(-)"
            additions > 0 -> "$additions insertions(+)"
            deletions > 0 -> "$deletions deletions(-)"
            else -> "No changes detected"
        }
    }
    val summary =
        listOfNotNull(summaryPrefix?.takeIf { it.isNotBlank() }, changeSummary)
            .joinToString(" · ")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .then(
                if (enableDialog) {
                    Modifier.clickable { showDetailDialog = true }
                } else {
                    Modifier
                }
            )
            .padding(start = 24.dp,bottom = 8.dp) // 左侧的缩进
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 子目录箭头图标，表示这是上个工具的执行结果
            Icon(
                    imageVector = Icons.Default.SubdirectoryArrowRight,
                    contentDescription = stringResource(R.string.filediff_tool_result),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Difference,
                contentDescription = "File Diff",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = diff.path.substringAfterLast('/'),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}
