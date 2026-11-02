package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.MemorySpace
import com.ai.assistance.operit.data.model.ModelConfigSelection
import com.ai.assistance.operit.data.preferences.FunctionConfigMapping
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.transparency.ThemeTransparencyPreferences
import com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.AttachmentSelectorPopupPanel
import com.ai.assistance.operit.ui.features.conversation.chat.modules.input.agent.AgentExtraSettingsPopup
import com.ai.assistance.operit.ui.features.conversation.chat.modules.input.agent.AgentModelSelectorPopup
import com.ai.assistance.operit.ui.features.conversation.chat.modules.input.agent.agentInputPopupContainerColor
import com.ai.assistance.operit.ui.permissions.PermissionLevel
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import kotlinx.coroutines.flow.first

/** 正式菜单作为聊天场景的覆盖层，沿用原图标、分组、尺寸与底色；宿主阻止业务点击。 */
@Composable
internal fun StudioChatPopupPreview(state: ThemeStudioState) {
    val snapshot = LocalThemePreferenceSnapshot.current
    val color = agentInputPopupContainerColor(
        hasBackgroundImage = snapshot.values.requiredBoolean("use_background_image"),
        chatInputTransparent = snapshot.chatInputTransparent,
        chatInputOpacity = snapshot.values.float(ThemeTransparencyPreferences.INPUT),
    )
    when (state.target.id) {
        "attachmentMenu" -> AttachmentSelectorPopupPanel(
            visible = true,
            containerColor = color,
            onContinueGeneration = {},
            continueGenerationEnabled = false,
            onAttachImage = {},
            onAttachFile = {},
            onAttachScreenContent = {},
            onTakePhoto = {},
            embedded = true,
            onDismiss = {},
        )
        "modelMenu" -> StudioModelPopupPreview(color)
        "chatSettingsMenu" -> StudioSettingsPopupPreview(color)
        else -> error("Unknown chat popup preview: ${state.target.id}")
    }
}

@Composable
private fun StudioModelPopupPreview(color: Color) {
    val context = LocalContext.current
    val modelManager = remember(context) { ModelConfigManager(context) }
    val functionManager = remember(context) { FunctionalConfigManager(context) }
    val selection by modelManager.configSelectionFlow.collectAsState(initial = ModelConfigSelection())
    val mappings by functionManager.functionConfigMappingWithIndexFlow.collectAsState(initial = emptyMap())
    val mapping = mappings[FunctionType.CHAT] ?: FunctionConfigMapping(FunctionalConfigManager.DEFAULT_CONFIG_ID, 0)
    val currentConfig = selection.allConfigs.find { it.id == mapping.configId }
    val ungrouped = stringResource(R.string.ungrouped)
    val boundGroup = if (currentConfig != null && currentConfig.groupId != selection.selectedGroupId) {
        selection.groups.firstOrNull { it.id == currentConfig.groupId }?.name ?: ungrouped
    } else null
    AgentModelSelectorPopup(
        visible = true,
        popupContainerColor = color,
        configSummaries = selection.allConfigs,
        currentConfigMapping = mapping,
        selectedGroupName = selection.selectedGroup?.name ?: ungrouped,
        availableConfigs = selection.availableConfigs,
        boundConfigGroupName = boundGroup,
        enableThinkingMode = false,
        onToggleThinkingMode = {},
        thinkingOptionId = "",
        onThinkingOptionIdChange = {},
        currentChatId = null,
        featureStates = emptyMap(),
        onToggleFeature = {},
        inputMenuRuntime = "theme-preview",
        onSelectModel = { _, _ -> },
        onManageModels = {},
        embedded = true,
        onDismiss = {},
    )
}

@Composable
private fun StudioSettingsPopupPreview(color: Color) {
    val context = LocalContext.current
    val preferences = remember(context) { UserPreferencesManager.getInstance(context) }
    val activeProfile by preferences.activeMemorySpaceIdFlow.collectAsState(initial = "default")
    var profiles by remember { mutableStateOf<List<MemorySpace>>(emptyList()) }
    LaunchedEffect(preferences) {
        profiles = preferences.memorySpaceListFlow.first().map { preferences.getMemorySpaceFlow(it).first() }
    }
    AgentExtraSettingsPopup(
        visible = true,
        runtime = rememberStudioChatInputRuntime(),
        onContextUsageClick = {},
        popupContainerColor = color,
        preferenceProfiles = profiles,
        currentProfileId = activeProfile,
        onSelectMemory = {},
        onManageMemory = {},
        enableMemoryAutoUpdate = false,
        onToggleMemoryAutoUpdate = {},
        currentChatId = null,
        featureStates = emptyMap(),
        onToggleFeature = {},
        inputMenuRuntime = "theme-preview",
        permissionLevel = PermissionLevel.ASK,
        onSetPermissionLevel = {},
        enableTools = true,
        onToggleTools = {},
        disableStreamOutput = false,
        onToggleDisableStreamOutput = {},
        disableUserPreferenceDescription = false,
        onToggleDisableUserPreferenceDescription = {},
        toolPromptVisibility = emptyMap(),
        onSaveToolPromptVisibilityMap = {},
        onNavigateToPackageManager = {},
        onManualMemoryUpdate = {},
        embedded = true,
        onDismiss = {},
    )
}
