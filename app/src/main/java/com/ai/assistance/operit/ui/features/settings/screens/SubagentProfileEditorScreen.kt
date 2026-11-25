package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ModelConfigSummary
import com.ai.assistance.operit.data.model.SubagentLimits
import com.ai.assistance.operit.data.model.SubagentProfile
import com.ai.assistance.operit.data.model.SubagentRoster
import com.ai.assistance.operit.data.model.getModelByIndex
import com.ai.assistance.operit.data.model.getModelList
import com.ai.assistance.operit.data.model.getValidModelIndex
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.preferences.SubagentProfileManager
import com.ai.assistance.operit.ui.components.CustomScaffold
import kotlinx.coroutines.launch

/**
 * 子代理档案编辑器：通用与专用共用，以 [SubagentProfile.isBuiltIn] 区分只读项。
 *
 * 草稿（[SubagentProfile.DRAFT_ID]）不落库——所有编辑先在本地状态上累积，
 * 保存时才通过 manager 写入；无效草稿（无名）被 [SubagentRoster.normalize] 丢弃，
 * 不会留下孤儿数据。内置行的名字与简介只读（normalize 会恢复规范值，编辑无意义）。
 */
@Composable
fun SubagentProfileEditorScreen(
    profileId: String,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val profileManager = remember { SubagentProfileManager.getInstance(context) }
    val modelConfigManager = remember { ModelConfigManager(context) }

    val profiles by profileManager.profileListFlow.collectAsState(initial = emptyList())

    var configSummaries by remember { mutableStateOf<List<ModelConfigSummary>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        configSummaries = modelConfigManager.getAllConfigSummaries()
        loaded = true
    }

    CustomScaffold { paddingValues ->
        // 编辑字段在 EditorBody 内做 remember 初始化，依赖 existing 快照。
        // profiles 是异步 flow，若在首次发射前组合编辑字段，remember 会以空档案固化
        // （名字空白 → 保存按钮禁用，表现为「指定模型后不能保存」），且 key 不变时
        // 不会随 flow 发射重新初始化。所以必须等名单加载完成再挂载 EditorBody。
        if (!loaded || profiles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@CustomScaffold
        }

        val existing = profiles.firstOrNull { it.id == profileId }
        EditorBody(
            profileId = profileId,
            existing = existing,
            profiles = profiles,
            configSummaries = configSummaries,
            profileManager = profileManager,
            onBackPressed = onBackPressed
        )
    }
}

@Composable
private fun EditorBody(
    profileId: String,
    existing: SubagentProfile?,
    profiles: List<SubagentProfile>,
    configSummaries: List<ModelConfigSummary>,
    profileManager: SubagentProfileManager,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val isBuiltIn = existing?.isBuiltIn == true
    val isDraft = profileId == SubagentProfile.DRAFT_ID

    // 本地编辑态：此 Composable 只在名单加载完成后挂载，remember 首次执行即拿到真实快照。
    var name by remember(profileId) { mutableStateOf(existing?.name.orEmpty()) }
    var description by remember(profileId) { mutableStateOf(existing?.description.orEmpty()) }
    var instructions by remember(profileId) { mutableStateOf(existing?.instructions.orEmpty()) }
    var bindingMode by remember(profileId) {
        mutableStateOf(
            SubagentProfile.ModelBindingMode.normalize(existing?.modelBindingMode)
        )
    }
    var modelConfigId by remember(profileId) { mutableStateOf(existing?.modelConfigId) }
    var modelIndex by remember(profileId) { mutableStateOf(existing?.modelIndex ?: 0) }

    // 重名校验是纯同步计算，直接在组合期算：依赖的 name/profiles 变化会触发重组重新求值。
    // 校验只对「除自己以外的名单」进行，nameKey 与运行时 resolve 共用同一折叠口径。
    val nameError: String? = run {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            null
        } else {
            val key = SubagentRoster.nameKey(trimmed)
            val clash = profiles.any { it.id != profileId && SubagentRoster.nameKey(it.name) == key }
            if (clash) context.getString(R.string.subagent_error_duplicate_name) else null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // ===== 基本信息 =====
        EditorSection(title = stringResource(R.string.subagent_editor_basic_section)) {
            LabeledCounterField(
                label = stringResource(R.string.subagent_editor_name_label),
                counter = "${name.length}/${SubagentLimits.NAME}",
                value = name,
                readOnly = isBuiltIn,
                maxLength = SubagentLimits.NAME,
                onValueChange = { name = it },
                singleLine = true
            )
            nameError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (isBuiltIn) {
                Text(
                    text = stringResource(R.string.subagent_editor_builtin_name_locked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LabeledCounterField(
                label = stringResource(R.string.subagent_editor_description_label),
                counter = "${description.length}/${SubagentLimits.DESCRIPTION}",
                value = description,
                readOnly = isBuiltIn,
                maxLength = SubagentLimits.DESCRIPTION,
                onValueChange = { description = it },
                singleLine = true
            )
            LabeledCounterField(
                label = stringResource(R.string.subagent_editor_instructions_label),
                counter = "${instructions.length}/${SubagentLimits.INSTRUCTIONS}",
                value = instructions,
                readOnly = false,
                maxLength = SubagentLimits.INSTRUCTIONS,
                onValueChange = { instructions = it },
                singleLine = false,
                minLines = 4
            )
        }

        // ===== 模型配置 =====
        EditorSection(title = stringResource(R.string.subagent_editor_model_section)) {
            ModelBindingSelector(
                bindingMode = bindingMode,
                summaries = configSummaries,
                selectedConfigId = modelConfigId,
                selectedModelIndex = modelIndex,
                followSessionLabel = stringResource(R.string.subagent_model_follow_session),
                fixedConfigLabel = stringResource(R.string.subagent_model_fixed_config),
                selectConfigHint = stringResource(R.string.subagent_editor_select_config),
                onBindingModeChange = { bindingMode = it },
                onModelSelected = { configId, index ->
                    modelConfigId = configId
                    modelIndex = index
                }
            )
        }

        // ===== 删除区（仅专用且非草稿） =====
        if (!isBuiltIn && !isDraft) {
            EditorSection(title = stringResource(R.string.subagent_editor_delete_section)) {
                Button(
                    onClick = {
                        scope.launch {
                            profileManager.deleteProfile(profileId)
                            onBackPressed()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.subagent_editor_delete))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== 保存：点击即写库并返回；重名/无名时按钮禁用 =====
        Button(
            onClick = {
                scope.launch {
                    val targetId = if (isDraft) SubagentProfile.newId() else profileId
                    profileManager.upsertProfile(
                        SubagentProfile(
                            id = targetId,
                            name = name.trim(),
                            description = description.trim(),
                            instructions = instructions,
                            modelBindingMode = bindingMode,
                            modelConfigId = modelConfigId,
                            modelIndex = modelIndex,
                            enabled = existing?.enabled ?: true,
                            sortOrder = existing?.sortOrder ?: 0,
                            // isBuiltIn 由 normalize 按 ID 恢复；内置行的名字/简介只读，
                            // 这里写 false 不会丢失内置身份。
                            isBuiltIn = false,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    onBackPressed()
                }
            },
            enabled = name.trim().isNotEmpty() && nameError == null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.subagent_editor_save))
        }
        Text(
            text = stringResource(R.string.subagent_editor_persist_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 24.dp)
        )
    }
}

@Composable
private fun EditorSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            content()
        }
    }
}

@Composable
private fun LabeledCounterField(
    label: String,
    counter: String,
    value: String,
    readOnly: Boolean,
    maxLength: Int,
    onValueChange: (String) -> Unit,
    singleLine: Boolean,
    minLines: Int = 1
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = counter,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = { next ->
                // 截断在输入层完成：normalize 也会收敛，但编辑时实时截断能保证计数器一致。
                if (next.length <= maxLength) onValueChange(next)
            },
            readOnly = readOnly,
            singleLine = singleLine,
            minLines = minLines,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ModelBindingSelector(
    bindingMode: String,
    summaries: List<ModelConfigSummary>,
    selectedConfigId: String?,
    selectedModelIndex: Int,
    followSessionLabel: String,
    fixedConfigLabel: String,
    selectConfigHint: String,
    onBindingModeChange: (String) -> Unit,
    onModelSelected: (String, Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var expandedConfigId by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        BindingModeRow(
            label = followSessionLabel,
            selected = bindingMode == SubagentProfile.ModelBindingMode.FOLLOW_SESSION,
            onClick = { onBindingModeChange(SubagentProfile.ModelBindingMode.FOLLOW_SESSION) }
        )
        BindingModeRow(
            label = fixedConfigLabel,
            selected = bindingMode == SubagentProfile.ModelBindingMode.FIXED_CONFIG,
            onClick = { onBindingModeChange(SubagentProfile.ModelBindingMode.FIXED_CONFIG) }
        )

        if (bindingMode == SubagentProfile.ModelBindingMode.FIXED_CONFIG) {
            // 触发行：显示当前选中配置，点击展开/收起选择器。
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { expanded = !expanded },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val selectedSummary = summaries.firstOrNull { it.id == selectedConfigId }
                        val selectedModelName = selectedSummary?.let {
                            getModelByIndex(
                                it.modelName,
                                getValidModelIndex(it.modelName, selectedModelIndex)
                            )
                        }
                        Text(
                            text = selectedSummary?.name ?: selectConfigHint,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (selectedModelName != null) {
                            Text(
                                text = selectedModelName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.animation.AnimatedVisibility(visible = expanded) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = selectConfigHint,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        summaries.forEach { summary ->
                            val isSelected = summary.id == selectedConfigId
                            val modelList = getModelList(summary.modelName)
                            val hasMultipleModels = modelList.size > 1
                            val isConfigExpanded = expandedConfigId == summary.id

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            expandedConfigId =
                                                if (hasMultipleModels) {
                                                    if (isConfigExpanded) null else summary.id
                                                } else {
                                                    onModelSelected(summary.id, 0)
                                                    expanded = false
                                                    null
                                                }
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    border = BorderStroke(
                                        width = if (isSelected) 0.dp else 0.5.dp,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isSelected && !hasMultipleModels) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(end = 8.dp).width(16.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = summary.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (hasMultipleModels) {
                                                    selectConfigHint
                                                } else {
                                                    summary.modelName
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (hasMultipleModels) {
                                            Icon(
                                                imageVector = if (isConfigExpanded) {
                                                    Icons.Default.KeyboardArrowUp
                                                } else {
                                                    Icons.Default.KeyboardArrowDown
                                                },
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.width(20.dp)
                                            )
                                        }
                                    }
                                }

                                androidx.compose.animation.AnimatedVisibility(
                                    visible = hasMultipleModels && isConfigExpanded
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 8.dp)
                                    ) {
                                        val validIndex =
                                            getValidModelIndex(summary.modelName, selectedModelIndex)
                                        modelList.forEachIndexed { index, modelName ->
                                            val isModelSelected = isSelected && validIndex == index
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp)
                                                    .clickable {
                                                        onModelSelected(summary.id, index)
                                                        expanded = false
                                                        expandedConfigId = null
                                                    },
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isModelSelected) {
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (isModelSelected) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier
                                                                .padding(end = 6.dp)
                                                                .width(14.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = modelName,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = if (isModelSelected) {
                                                            FontWeight.Bold
                                                        } else {
                                                            FontWeight.Normal
                                                        },
                                                        color = if (isModelSelected) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurface
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BindingModeRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        } else {
            Color.Transparent
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp).width(16.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
