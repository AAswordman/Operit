package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ModelConfigSummary
import com.ai.assistance.operit.data.model.SubagentLimits
import com.ai.assistance.operit.data.model.SubagentProfile
import com.ai.assistance.operit.data.model.getModelByIndex
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.preferences.SubagentProfileManager
import com.ai.assistance.operit.ui.components.CustomScaffold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 子代理配置页：总开关、内置通用子代理、专用子代理列表（顺序 = 披露顺序）。
 *
 * 开关只控制「是否暴露给模型」；关闭后列表区降透明度但仍可编辑，
 * 与「名单是纯配置数据、暴露是运行时行为」的分层一致。
 */
@Composable
fun SubagentConfigScreen(
    onBackPressed: () -> Unit,
    onNavigateToEditor: (String) -> Unit
) {
    val context = LocalContext.current
    val profileManager = remember { SubagentProfileManager.getInstance(context) }
    val modelConfigManager = remember { ModelConfigManager(context) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val profiles by profileManager.profileListFlow.collectAsState(initial = emptyList())
    val enabled by profileManager.subagentsEnabledFlow.collectAsState(initial = true)

    var configSummaries by remember { mutableStateOf<List<ModelConfigSummary>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // 模型配置摘要只用于展示标签，设置页内不存在并发修改，启动时批量取一次即可。
        configSummaries = modelConfigManager.getAllConfigSummaries()
        loaded = true
    }

    CustomScaffold { paddingValues ->
        if (!loaded) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@CustomScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(scrollState)
        ) {
            val cardColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            // 两个区块共用：通用/专用行的模型标签都要用。
            val followSessionLabel = stringResource(R.string.subagent_model_follow_session)

            // ===== 总开关 =====
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.subagent_enable_toggle),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.subagent_tool_name_display),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { checked ->
                        scope.launch { profileManager.setSubagentsEnabled(checked) }
                    })
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (enabled) 1f else 0.5f)
            ) {
                // ===== 内置通用子代理 =====
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        SectionHeader(title = stringResource(R.string.subagent_general_section))
                        val general = profiles.firstOrNull { it.isBuiltIn }
                        if (general != null) {
                            ProfileRow(
                                profile = general,
                                modelLabel = modelLabelFor(general, configSummaries, followSessionLabel),
                                builtInLabel = stringResource(R.string.subagent_badge_built_in),
                                onClick = { onNavigateToEditor(general.id) },
                                onMoveUp = null,
                                onMoveDown = null
                            )
                        }
                        Text(
                            text = stringResource(R.string.subagent_general_footer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                // ===== 专用子代理 =====
                val specialized = profiles.filterNot { it.isBuiltIn }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        SectionHeader(title = stringResource(R.string.subagent_specialized_section))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                // 上限含内置行：专用名额 = MAX_COUNT - 1。
                                if (specialized.size < SubagentLimits.MAX_COUNT - 1) {
                                    onNavigateToEditor(SubagentProfile.DRAFT_ID)
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(R.string.subagent_add)
                                )
                            }
                        }
                        if (specialized.size >= SubagentLimits.MAX_COUNT - 1) {
                            Text(
                                text = stringResource(R.string.subagent_limit_reached, SubagentLimits.MAX_COUNT),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        specialized.forEachIndexed { index, profile ->
                            ProfileRow(
                                profile = profile,
                                modelLabel = modelLabelFor(profile, configSummaries, followSessionLabel),
                                builtInLabel = stringResource(R.string.subagent_badge_built_in),
                                onClick = { onNavigateToEditor(profile.id) },
                                onMoveUp = if (index > 0) {
                                    { moveProfile(profiles, profile, -1, scope, profileManager) }
                                } else null,
                                onMoveDown = if (index < specialized.size - 1) {
                                    { moveProfile(profiles, profile, 1, scope, profileManager) }
                                } else null
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun moveProfile(
    profiles: List<SubagentProfile>,
    profile: SubagentProfile,
    direction: Int,
    scope: CoroutineScope,
    profileManager: SubagentProfileManager
) {
    scope.launch {
        val builtIns = profiles.filter { it.isBuiltIn }
        val specialized = profiles.filterNot { it.isBuiltIn }
        val index = specialized.indexOfFirst { it.id == profile.id }
        val target = index + direction
        if (index < 0 || target < 0 || target >= specialized.size) return@launch
        val reordered = specialized.toMutableList().apply {
            removeAt(index)
            add(target, profile)
        }
        profileManager.upsertProfiles(builtIns + reordered)
    }
}

private fun modelLabelFor(
    profile: SubagentProfile,
    summaries: List<ModelConfigSummary>,
    followSessionLabel: String
): String {
    return when (SubagentProfile.ModelBindingMode.normalize(profile.modelBindingMode)) {
        SubagentProfile.ModelBindingMode.FOLLOW_SESSION -> followSessionLabel
        SubagentProfile.ModelBindingMode.FIXED_CONFIG -> {
            val summary = summaries.firstOrNull { it.id == profile.modelConfigId }
            val modelName = summary?.let { getModelByIndex(it.modelName, profile.modelIndex) }.orEmpty()
            listOfNotNull(summary?.name, modelName.takeIf { it.isNotBlank() }).joinToString(" · ")
        }
        // normalize 只会产出上述两个值；else 分支满足穷尽性。
        else -> followSessionLabel
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun ProfileRow(
    profile: SubagentProfile,
    modelLabel: String,
    builtInLabel: String,
    onClick: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
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
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (profile.isBuiltIn) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = builtInLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    if (profile.description.isNotBlank()) {
                        Text(
                            text = profile.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = modelLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (onMoveUp != null) {
                    IconButton(onClick = onMoveUp) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.subagent_move_up),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (onMoveDown != null) {
                    IconButton(onClick = onMoveDown) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.subagent_move_down),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
