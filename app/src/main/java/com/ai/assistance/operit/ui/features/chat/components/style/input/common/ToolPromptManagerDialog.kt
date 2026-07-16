package com.ai.assistance.operit.ui.features.chat.components.style.input.common

import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.config.SystemToolPrompts
import com.ai.assistance.operit.util.LocaleUtils
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun ToolPromptManagerDialog(
    visible: Boolean,
    toolPromptVisibility: Map<String, Boolean>,
    toolOrder: List<String> = emptyList(),
    onSaveToolPromptVisibilityMap: (Map<String, Boolean>) -> Unit,
    onSaveToolOrder: (List<String>) -> Unit = {},
    onDismissRequest: () -> Unit,
    onManagePackagesClick: () -> Unit,
) {
    if (!visible) return

    val context = LocalContext.current
    val useEnglish = remember(context) {
        LocaleUtils.isNonChineseLanguage(context)
    }
    val manageableTools = remember(useEnglish, toolOrder) {
        SystemToolPrompts.getManageableToolPrompts(useEnglish, toolOrder)
    }
    var localToolPromptVisibility by remember(toolPromptVisibility) {
        mutableStateOf(toolPromptVisibility)
    }

    // State for current ordered tools displayed in the list
    var orderedTools by remember(manageableTools) {
        mutableStateOf(manageableTools)
    }

    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        orderedTools = orderedTools.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        // Persist the new order immediately
        val newOrder = orderedTools.map { it.name }
        onSaveToolOrder(newOrder)
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.common_close))
            }
        },
        title = {
            Text(
                text = stringResource(R.string.tool_prompt_manager_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            ) {
                itemsIndexed(
                    items = orderedTools,
                    key = { _, tool -> tool.name }
                ) { index, tool ->
                    ReorderableItem(
                        reorderableState,
                        key = tool.name,
                        animateItemModifier = Modifier.animateItem(
                            fadeInSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
                            placementSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
                            fadeOutSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)
                        )
                    ) { isDragging ->
                        val isVisible = localToolPromptVisibility[tool.name] ?: true
                        val elevation = if (isDragging) 8.dp else 0.dp
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isDragging) {
                                        Modifier.shadow(elevation, RoundedCornerShape(8.dp))
                                    } else {
                                        Modifier
                                    }
                                ),
                            shape = RoundedCornerShape(8.dp),
                            color = if (isDragging) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ) {
                            // Long-press anywhere on the row to drag-reorder
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .longPressDraggableHandle()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tool.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = tool.categoryName,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = isVisible,
                                    onCheckedChange = { checked ->
                                        val updated = localToolPromptVisibility + (tool.name to checked)
                                        localToolPromptVisibility = updated
                                        onSaveToolPromptVisibilityMap(updated)
                                    },
                                    modifier = Modifier.scale(0.8f),
                                )
                            }
                        }
                        if (index < orderedTools.lastIndex) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                            )
                        }
                    }
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .clickable(onClick = onManagePackagesClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.manage_packages),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        },
    )
}
