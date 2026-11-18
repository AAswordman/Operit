package com.ai.assistance.operit.ui.features.settings.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.agent.AgentProfile
import com.ai.assistance.operit.core.agent.AgentProfileRepository
import com.ai.assistance.operit.core.agent.normalizeCustomAgentId
import com.ai.assistance.operit.data.model.ModelConfigSummary
import com.ai.assistance.operit.data.model.getModelList
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import kotlinx.coroutines.launch

@Composable
fun AgentProfileSettingsScreen() {
    val context = LocalContext.current
    val repository =
        remember(context) {
            AgentProfileRepository.instance.apply { initialize(context.applicationContext) }
        }
    val modelConfigManager = remember(context) { ModelConfigManager(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val profiles by repository.profiles.collectAsState()
    var modelConfigs by remember { mutableStateOf<List<ModelConfigSummary>>(emptyList()) }
    var isLoadingModels by remember { mutableStateOf(true) }
    var modelLoadFailed by remember { mutableStateOf(false) }
    var editingProfileId by remember { mutableStateOf<String?>(null) }
    var isCreatingProfile by remember { mutableStateOf(false) }
    var pendingDeleteProfileId by remember { mutableStateOf<String?>(null) }

    suspend fun loadModelConfigs() {
        isLoadingModels = true
        modelLoadFailed = false
        runCatching {
                modelConfigManager.getAllConfigSummaries()
            }
            .onSuccess { modelConfigs = it }
            .onFailure { modelLoadFailed = true }
        isLoadingModels = false
    }

    LaunchedEffect(Unit) { loadModelConfigs() }

    val editingProfile = editingProfileId?.let(repository::getById)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 12.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    CollaborationLimitsSettings(repository)
                }
            }
        }
        item {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.agent_profile_info_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.agent_profile_info_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    FilledTonalButton(onClick = { isCreatingProfile = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.agent_profile_add))
                    }
                }
            }
        }

        items(profiles, key = AgentProfile::id) { profile ->
            AgentProfileCard(
                profile = profile,
                modelLabel = modelBindingLabel(profile, modelConfigs),
                isBuiltIn = repository.isBuiltIn(profile.id),
                onEdit = { editingProfileId = profile.id },
            )
        }
    }

    if (editingProfile != null || isCreatingProfile) {
        AgentProfileEditorDialog(
            profile = editingProfile,
            isBuiltIn = editingProfile?.let { repository.isBuiltIn(it.id) } == true,
            modelConfigs = modelConfigs,
            isLoadingModels = isLoadingModels,
            modelLoadFailed = modelLoadFailed,
            onReloadModels = { scope.launch { loadModelConfigs() } },
            onDismiss = {
                editingProfileId = null
                isCreatingProfile = false
            },
            onSave = { id, name, description, prompt, configId, modelIndex ->
                runCatching {
                        when {
                            editingProfile == null ->
                                repository.createCustomProfile(
                                    id = id,
                                    name = name,
                                    description = description,
                                    systemPrompt = prompt,
                                    modelConfigId = configId,
                                    modelIndex = modelIndex,
                                )
                            repository.isBuiltIn(editingProfile.id) ->
                                repository.updateSettings(
                                    id = editingProfile.id,
                                    systemPrompt = prompt,
                                    modelConfigId = configId,
                                    modelIndex = modelIndex,
                                )
                            else ->
                                repository.updateCustomProfile(
                                    id = editingProfile.id,
                                    name = name,
                                    description = description,
                                    systemPrompt = prompt,
                                    modelConfigId = configId,
                                    modelIndex = modelIndex,
                                )
                        }
                    }
                    .onSuccess {
                        Toast.makeText(
                                context,
                                R.string.agent_profile_saved,
                                Toast.LENGTH_SHORT,
                            )
                            .show()
                        editingProfileId = null
                        isCreatingProfile = false
                    }
                    .onFailure {
                        Toast.makeText(
                                context,
                                R.string.agent_profile_save_failed,
                                Toast.LENGTH_SHORT,
                            )
                            .show()
                    }
            },
            onReset = {
                editingProfile?.let { repository.resetSettings(it.id) }
                Toast.makeText(context, R.string.agent_profile_reset_done, Toast.LENGTH_SHORT).show()
                editingProfileId = null
            },
            onDeleteRequest = {
                pendingDeleteProfileId = editingProfile?.id
            },
        )
    }

    pendingDeleteProfileId?.let { profileId ->
        val profileName = repository.getById(profileId)?.name.orEmpty()
        AlertDialog(
            onDismissRequest = { pendingDeleteProfileId = null },
            title = { Text(stringResource(R.string.agent_profile_delete_title)) },
            text = {
                Text(stringResource(R.string.agent_profile_delete_message, profileName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        runCatching { repository.deleteCustomProfile(profileId) }
                            .onSuccess {
                                Toast.makeText(
                                        context,
                                        R.string.agent_profile_deleted,
                                        Toast.LENGTH_SHORT,
                                    )
                                    .show()
                                editingProfileId = null
                            }
                            .onFailure {
                                Toast.makeText(
                                        context,
                                        R.string.agent_profile_delete_failed,
                                        Toast.LENGTH_SHORT,
                                    )
                                    .show()
                            }
                        pendingDeleteProfileId = null
                    }
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteProfileId = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun AgentProfileCard(
    profile: AgentProfile,
    modelLabel: String,
    isBuiltIn: Boolean,
    onEdit: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text =
                            stringResource(
                                if (isBuiltIn) {
                                    R.string.agent_profile_builtin_badge
                                } else {
                                    R.string.agent_profile_custom_badge
                                }
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = profile.id,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = profile.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.agent_profile_model_value, modelLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text =
                        stringResource(
                            R.string.agent_profile_prompt_preview,
                            profile.systemPrompt.replace('\n', ' '),
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.agent_profile_edit),
                )
            }
        }
    }
}

@Composable
private fun AgentProfileEditorDialog(
    profile: AgentProfile?,
    isBuiltIn: Boolean,
    modelConfigs: List<ModelConfigSummary>,
    isLoadingModels: Boolean,
    modelLoadFailed: Boolean,
    onReloadModels: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String?, Int?) -> Unit,
    onReset: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    val stateKey = profile?.id ?: "new"
    var id by remember(stateKey) { mutableStateOf(profile?.id.orEmpty()) }
    var name by remember(stateKey) { mutableStateOf(profile?.name.orEmpty()) }
    var description by remember(stateKey) { mutableStateOf(profile?.description.orEmpty()) }
    var prompt by remember(stateKey) { mutableStateOf(profile?.systemPrompt.orEmpty()) }
    var selectedConfigId by remember(stateKey) { mutableStateOf(profile?.modelConfigId) }
    var selectedModelIndex by remember(stateKey) { mutableStateOf(profile?.modelIndex) }
    val normalizedId = runCatching { normalizeCustomAgentId(id) }.getOrNull()
    val idIsValid = isBuiltIn || normalizedId != null
    val metadataIsValid = isBuiltIn || (name.isNotBlank() && description.isNotBlank())
    val promptIsValid = prompt.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text =
                    if (profile == null) {
                        stringResource(R.string.agent_profile_create_title)
                    } else {
                        stringResource(R.string.agent_profile_edit_title, profile.name)
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .heightIn(max = 620.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (isBuiltIn) {
                    Text(
                        text = stringResource(R.string.agent_profile_stable_id, id),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    OutlinedTextField(
                        value = id,
                        onValueChange = { if (profile == null) id = it },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = profile != null,
                        label = { Text(stringResource(R.string.agent_profile_id)) },
                        supportingText = {
                            Text(
                                if (idIsValid) {
                                    stringResource(R.string.agent_profile_id_hint)
                                } else {
                                    stringResource(R.string.agent_profile_id_invalid)
                                }
                            )
                        },
                        isError = !idIsValid,
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.agent_profile_name)) },
                        isError = name.isBlank(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.agent_profile_description)) },
                        supportingText = {
                            Text(stringResource(R.string.agent_profile_description_hint))
                        },
                        isError = description.isBlank(),
                        minLines = 2,
                        maxLines = 4,
                    )
                }

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.agent_profile_system_prompt)) },
                    supportingText = {
                        Text(
                            if (promptIsValid) {
                                stringResource(R.string.agent_profile_system_prompt_hint)
                            } else {
                                stringResource(R.string.agent_profile_prompt_required)
                            }
                        )
                    },
                    isError = !promptIsValid,
                    minLines = 7,
                    maxLines = 14,
                )

                AgentModelBindingSelector(
                    modelConfigs = modelConfigs,
                    selectedConfigId = selectedConfigId,
                    selectedModelIndex = selectedModelIndex,
                    onSelectionChange = { configId, modelIndex ->
                        selectedConfigId = configId
                        selectedModelIndex = modelIndex
                    },
                )

                when {
                    isLoadingModels ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Text(
                                text = stringResource(R.string.agent_profile_loading_models),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    modelLoadFailed ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.agent_profile_model_load_failed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onReloadModels) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    else ->
                        Text(
                            text = stringResource(R.string.agent_profile_model_binding_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        normalizedId ?: id,
                        name,
                        description,
                        prompt,
                        selectedConfigId,
                        selectedModelIndex,
                    )
                },
                enabled = idIsValid && metadataIsValid && promptIsValid,
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row {
                when {
                    isBuiltIn ->
                        TextButton(onClick = onReset) {
                            Text(stringResource(R.string.agent_profile_reset_defaults))
                        }
                    profile != null ->
                        TextButton(onClick = onDeleteRequest) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )
}

@Composable
private fun AgentModelBindingSelector(
    modelConfigs: List<ModelConfigSummary>,
    selectedConfigId: String?,
    selectedModelIndex: Int?,
    onSelectionChange: (String?, Int?) -> Unit,
) {
    var expandedConfigId by remember(modelConfigs, selectedConfigId) {
        mutableStateOf(
            selectedConfigId?.takeIf { configId ->
                modelConfigs
                    .firstOrNull { it.id == configId }
                    ?.let { getModelList(it.modelName).size > 1 } == true
            }
        )
    }
    val selectedLabel =
        modelConfigs
            .firstOrNull { it.id == selectedConfigId }
            ?.let { config ->
                getModelList(config.modelName)
                    .getOrNull(selectedModelIndex ?: 0)
                    ?.let { model -> "${config.name} · $model" }
            }
            ?: if (selectedConfigId == null) {
                stringResource(R.string.agent_profile_model_inherit)
            } else {
                stringResource(
                    R.string.agent_profile_model_unavailable,
                    selectedConfigId,
                    selectedModelIndex ?: 0,
                )
            }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.agent_profile_model_binding),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = selectedLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ModelSelectionRow(
            title = stringResource(R.string.agent_profile_model_inherit),
            subtitle = stringResource(R.string.agent_profile_model_inherit_hint),
            selected = selectedConfigId == null,
            onClick = {
                onSelectionChange(null, null)
                expandedConfigId = null
            },
        )
        modelConfigs.forEach { config ->
            val models = getModelList(config.modelName)
            val hasMultipleModels = models.size > 1
            val isConfigSelected = selectedConfigId == config.id
            val isExpanded = expandedConfigId == config.id
            ModelSelectionRow(
                title = config.name,
                subtitle =
                    if (hasMultipleModels) {
                        stringResource(R.string.functional_config_model_count, models.size)
                    } else {
                        models.firstOrNull() ?: config.modelName
                    },
                selected = isConfigSelected && !hasMultipleModels,
                expandable = hasMultipleModels,
                expanded = isExpanded,
                onClick = {
                    if (hasMultipleModels) {
                        expandedConfigId = if (isExpanded) null else config.id
                    } else {
                        onSelectionChange(config.id, 0)
                        expandedConfigId = null
                    }
                },
            )
            AnimatedVisibility(visible = hasMultipleModels && isExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    models.forEachIndexed { index, modelName ->
                        ModelSelectionRow(
                            title = modelName,
                            subtitle = null,
                            selected =
                                isConfigSelected && (selectedModelIndex ?: 0) == index,
                            compact = true,
                            onClick = { onSelectionChange(config.id, index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelSelectionRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    expandable: Boolean = false,
    expanded: Boolean = false,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(if (compact) 6.dp else 8.dp),
        color =
            if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (compact) 0.5f else 0.35f)
            },
        border =
            BorderStroke(
                width = if (selected) 1.dp else 0.5.dp,
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    },
            ),
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        horizontal = 12.dp,
                        vertical = if (compact) 8.dp else 10.dp,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.selected_desc),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (compact) 14.dp else 16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style =
                        if (compact) {
                            MaterialTheme.typography.bodySmall
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (expandable) {
                Icon(
                    imageVector =
                        if (expanded) {
                            Icons.Default.KeyboardArrowUp
                        } else {
                            Icons.Default.KeyboardArrowDown
                        },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun modelBindingLabel(
    profile: AgentProfile,
    modelConfigs: List<ModelConfigSummary>,
): String {
    val configId =
        profile.modelConfigId
            ?: return stringResource(R.string.agent_profile_model_inherit)
    val config = modelConfigs.firstOrNull { it.id == configId }
    val model = config?.let { getModelList(it.modelName).getOrNull(profile.modelIndex ?: 0) }
    return if (config != null && model != null) {
        "${config.name} · $model"
    } else {
        stringResource(
            R.string.agent_profile_model_unavailable,
            configId,
            profile.modelIndex ?: 0,
        )
    }
}
