package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R

/** One place a chat or folder can be moved to. [id] is null for the top level. */
internal data class FolderPickerOption(
    val id: String?,
    val name: String,
    /** Parent path such as "工作 / 项目"; null at the top level. */
    val parentPath: String? = null,
    val isCurrent: Boolean = false,
    val isTopLevel: Boolean = false,
)

/**
 * Folder chooser used by "move to folder". Tapping a row moves right away; the current place is
 * marked and can't be picked again.
 */
@Composable
internal fun FolderPickerDialog(
    title: String,
    subtitle: String?,
    options: List<FolderPickerOption>,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val visible =
        remember(options, query) {
            val q = query.trim()
            if (q.isEmpty()) options
            else options.filter {
                it.isTopLevel ||
                    it.name.contains(q, ignoreCase = true) ||
                    it.parentPath?.contains(q, ignoreCase = true) == true
            }
        }
    val colors = MaterialTheme.colorScheme
    val rowBlock =
        if (colors.surface.luminance() < 0.5f) colors.surfaceContainerHighest
        else colors.surfaceContainerLowest

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.92f),
            shape = RoundedCornerShape(28.dp),
            color = colors.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(top = 20.dp, bottom = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close))
                    }
                }

                if (options.size > 6) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp),
                        placeholder = { Text(stringResource(R.string.search)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_search))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = rowBlock,
                                unfocusedContainerColor = rowBlock,
                            ),
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp).padding(top = 12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    itemsIndexed(visible, key = { _, option -> option.id ?: "__top_level__" }) { index, option ->
                        val first = index == 0
                        val last = index == visible.lastIndex
                        val shape =
                            RoundedCornerShape(
                                topStart = if (first) 18.dp else 0.dp,
                                topEnd = if (first) 18.dp else 0.dp,
                                bottomStart = if (last) 18.dp else 0.dp,
                                bottomEnd = if (last) 18.dp else 0.dp,
                            )
                        Column(modifier = Modifier.clip(shape).background(rowBlock)) {
                            FolderPickerRow(option = option, onClick = { onSelect(option.id) })
                            if (!last) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                                    color = colors.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderPickerRow(option: FolderPickerOption, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val icon: ImageVector = if (option.isTopLevel) Icons.Default.Inbox else Icons.Default.Folder
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(enabled = !option.isCurrent, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier.size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (option.isCurrent) colors.primary.copy(alpha = 0.16f)
                        else colors.primaryContainer
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (option.isCurrent) colors.primary else colors.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = option.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail =
                when {
                    option.isCurrent -> stringResource(R.string.current_location)
                    else -> option.parentPath
                }
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (option.isCurrent) colors.primary else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (option.isCurrent) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
