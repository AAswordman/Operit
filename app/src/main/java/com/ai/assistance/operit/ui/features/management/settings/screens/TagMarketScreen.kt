package com.ai.assistance.operit.ui.features.management.settings.screens

import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.data.model.PromptTag
import com.ai.assistance.operit.data.model.TagType
import com.ai.assistance.operit.data.preferences.PromptTagManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

// Import bilingual preset tags
// Note: presetTags list has been moved to TagMarketBilingualData.kt for better bilingual support
// Use bilingualPresetTags from TagMarketBilingualData.kt instead

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagMarketScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val promptTagManager = remember { PromptTagManager.getInstance(context) }
    var showSaveSuccessHighlight by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedPreset by remember { mutableStateOf<PresetTagBilingual?>(null) }
    var newTagName by remember { mutableStateOf("") }

    CustomScaffold() { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 按分类分组显示标签 (Group tags by category)
            val groupedTags = bilingualPresetTags.groupBy { it.getLocalizedCategory(context) }
            groupedTags.forEach { (category, tags) ->
                item {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }

                items(tags) { preset ->
                    PresetTagCard(
                        preset = preset,
                        context = context,
                        onUseClick = {
                            selectedPreset = it
                            newTagName = it.getLocalizedName(context) // 默认使用预设名称 (Use preset name by default)
                            showCreateDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showCreateDialog && selectedPreset != null) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.add_tag)) },
            text = {
                Column {
                    Text(stringResource(R.string.add_to_tag_library, selectedPreset?.getLocalizedName(context) ?: ""))
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newTagName,
                        onValueChange = { newTagName = it },
                        label = { Text(stringResource(R.string.tag_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTagName.isNotBlank()) {
                            scope.launch {
                                promptTagManager.createPromptTag(
                                    name = newTagName,
                                    description = selectedPreset!!.getLocalizedDescription(context),
                                    promptContent = selectedPreset!!.getLocalizedPromptContent(context),
                                    tagType = selectedPreset!!.tagType
                                )
                                showCreateDialog = false
                                showSaveSuccessHighlight = true
                                // 保留在标签市场页面，用户可继续添加或编辑标签
                            }
                        }
                    }
                ) { Text(stringResource(R.string.add)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // 保存成功的底部高亮提示（1.5s 自动消失）
    if (showSaveSuccessHighlight) {
        LaunchedEffect(Unit) {
            delay(1500)
            showSaveSuccessHighlight = false
        }
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = context.getString(com.ai.assistance.operit.R.string.save_successful), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun PresetTagCard(preset: PresetTagBilingual, context: android.content.Context, onUseClick: (PresetTagBilingual) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = preset.icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = preset.getLocalizedName(context),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                // 标签类型徽章 (Tag type badge)
                AssistChip(
                    onClick = { },
                    label = { Text(preset.tagType.name, fontSize = 10.sp) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.height(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = preset.getLocalizedDescription(context),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.height(40.dp) // 保证差不多两行的高度 (Ensure about two lines height)
            )
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            Text(stringResource(R.string.tag_content), style = MaterialTheme.typography.labelMedium)
            Text(
                text = preset.getLocalizedPromptContent(context),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 6,
                modifier = Modifier.heightIn(max = 100.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onUseClick(preset) },
                modifier = Modifier.align(Alignment.End),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.tag_add))
            }
        }
    }
}
