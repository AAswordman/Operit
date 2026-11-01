package com.ai.assistance.operit.ui.features.management.settings.components

import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import com.ai.assistance.operit.ui.theme.script.ThemeScriptOutlinedButton as OutlinedButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton

import com.ai.assistance.operit.ui.common.oktoast.OKToast

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.dialogs.ContentSizedTabbedDialog
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.model.CharacterCardChatModelBindingMode
import com.ai.assistance.operit.data.model.CharacterCardMemoryProfileBindingMode
import com.ai.assistance.operit.data.model.CharacterCardToolAccessConfig
import com.ai.assistance.operit.data.model.ModelConfigSummary
import com.ai.assistance.operit.data.model.MemorySpace
import com.ai.assistance.operit.data.model.PromptTag
import com.ai.assistance.operit.data.model.getModelByIndex
import com.ai.assistance.operit.data.model.getModelList
import com.ai.assistance.operit.data.model.getValidModelIndex
import com.ai.assistance.operit.core.config.SystemToolPrompts
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.preferences.WaifuPreferences
import com.ai.assistance.operit.data.preferences.WaifuSettings
import com.ai.assistance.operit.ui.features.management.settings.components.character.speech.CharacterSpeechSettings
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.skill.SkillRepository
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.util.android.LocaleUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// 角色卡名片对话框
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterCardDialog(
    characterCard: CharacterCard,
    allTags: List<PromptTag>,
    userPreferencesManager: UserPreferencesManager,
    avatarPreviewUri: Uri? = null,
    onDismiss: () -> Unit,
    onSave: (CharacterCard, WaifuSettings) -> Unit,
    onExport: (CharacterCard, WaifuSettings) -> Unit,
    onManageTags: () -> Unit,
    onAvatarChange: () -> Unit,
    onAvatarReset: () -> Unit,
    isSaving: Boolean = false,
) {
    var name by remember(characterCard.id) { mutableStateOf(characterCard.name) }
    var description by remember(characterCard.id) { mutableStateOf(characterCard.description) }
    var characterSetting by remember(characterCard.id) { mutableStateOf(characterCard.characterSetting) }
    var openingStatement by remember(characterCard.id) { mutableStateOf(characterCard.openingStatement) } // 新增：开场白
    var otherContentChat by remember(characterCard.id) { mutableStateOf(characterCard.otherContentChat) }
    var otherContentVoice by remember(characterCard.id) { mutableStateOf(characterCard.otherContentVoice) }
    var attachedTagIds by remember(characterCard.id) { mutableStateOf(characterCard.attachedTagIds) }
    var advancedCustomPrompt by remember(characterCard.id) { mutableStateOf(characterCard.advancedCustomPrompt) }
    var marks by remember(characterCard.id) { mutableStateOf(characterCard.marks) }
    var chatModelBindingMode by remember(characterCard.id) {
        mutableStateOf(CharacterCardChatModelBindingMode.normalize(characterCard.chatModelBindingMode))
    }
    var fixedChatModelConfigId by remember(characterCard.id) {
        mutableStateOf(characterCard.chatModelConfigId ?: "")
    }
    var fixedChatModelIndex by remember(characterCard.id) {
        mutableStateOf(characterCard.chatModelIndex.coerceAtLeast(0))
    }
    var memoryProfileBindingMode by remember(characterCard.id) {
        mutableStateOf(CharacterCardMemoryProfileBindingMode.normalize(characterCard.memoryProfileBindingMode))
    }
    var fixedMemoryProfileId by remember(characterCard.id) {
        mutableStateOf(characterCard.memoryProfileId ?: "")
    }
    var toolAccessConfig by remember(characterCard.id) {
        mutableStateOf(characterCard.toolAccessConfig.normalized())
    }
    var showFixedConfigPickerDialog by remember(characterCard.id) { mutableStateOf(false) }
    var popupExpandedFixedConfigId by remember(characterCard.id) { mutableStateOf<String?>(null) }
    var showFixedMemoryProfilePickerDialog by remember(characterCard.id) { mutableStateOf(false) }
    var showToolAccessDialog by remember(characterCard.id) { mutableStateOf(false) }
    val pagerState = key(characterCard.id) { rememberPagerState(pageCount = { 4 }) }
    
    // 翻译相关状态
    var isTranslating by remember { mutableStateOf(false) }
    
    // 大屏编辑状态
    var showFullScreenEdit by remember { mutableStateOf(false) }
    var fullScreenEditTitle by remember { mutableStateOf("") }
    var fullScreenEditValue by remember { mutableStateOf("") }
    var fullScreenEditField by remember { mutableStateOf("") }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val waifuPreferences = remember { WaifuPreferences.getInstance(context) }
    var speechSettings by remember(characterCard.id) { mutableStateOf<WaifuSettings?>(null) }
    var speechLoadFailed by remember(characterCard.id) { mutableStateOf(false) }
    var speechLoadVersion by remember(characterCard.id) { mutableIntStateOf(0) }
    LaunchedEffect(characterCard.id, speechLoadVersion) {
        speechLoadFailed = false
        try {
            speechSettings = waifuPreferences.loadCharacterCardSettings(characterCard.id)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.e("CharacterCardDialog", "读取角色卡发言设置失败", error)
            speechLoadFailed = true
        }
    }

    val useEnglish = remember(context) {
        !LocaleUtils.usesChineseContent(context)
    }
    val toolHandler = remember { AIToolHandler.getInstance(context) }
    val packageManager = remember { PackageManager.getInstance(context, toolHandler) }
    val skillRepository = remember { SkillRepository.getInstance(context) }
    val modelConfigManager = remember { ModelConfigManager(context) }
    var configSummaries by remember { mutableStateOf<List<ModelConfigSummary>>(emptyList()) }
    var preferenceProfiles by remember { mutableStateOf<List<MemorySpace>>(emptyList()) }
    var builtinToolOptions by remember(characterCard.id) {
        mutableStateOf<List<CharacterCardToolAccessOption>>(emptyList())
    }
    var packageOptions by remember(characterCard.id) {
        mutableStateOf<List<CharacterCardToolAccessOption>>(emptyList())
    }
    var skillOptions by remember(characterCard.id) {
        mutableStateOf<List<CharacterCardToolAccessOption>>(emptyList())
    }
    var mcpOptions by remember(characterCard.id) {
        mutableStateOf<List<CharacterCardToolAccessOption>>(emptyList())
    }
    val avatarUri by userPreferencesManager.getAiAvatarForCharacterCardFlow(characterCard.id)
        .collectAsState(initial = null)
    val displayedAvatarUri = if (characterCard.id.isBlank()) avatarPreviewUri?.toString() else avatarUri

    LaunchedEffect(Unit) {
        configSummaries = modelConfigManager.getAllConfigSummaries()
        val profileIds = userPreferencesManager.memorySpaceListFlow.first()
        preferenceProfiles =
            profileIds.map { profileId -> userPreferencesManager.getMemorySpaceFlow(profileId).first() }
    }

    LaunchedEffect(chatModelBindingMode, configSummaries) {
        if (
            chatModelBindingMode == CharacterCardChatModelBindingMode.FIXED_CONFIG &&
            configSummaries.isNotEmpty()
        ) {
            if (fixedChatModelConfigId.isBlank() || configSummaries.none { it.id == fixedChatModelConfigId }) {
                fixedChatModelConfigId = configSummaries.first().id
            }
            val selectedConfig = configSummaries.find { it.id == fixedChatModelConfigId }
            if (selectedConfig != null) {
                fixedChatModelIndex = getValidModelIndex(selectedConfig.modelName, fixedChatModelIndex)
            }
        }
    }

    LaunchedEffect(chatModelBindingMode) {
        if (chatModelBindingMode != CharacterCardChatModelBindingMode.FIXED_CONFIG) {
            showFixedConfigPickerDialog = false
            popupExpandedFixedConfigId = null
        }
    }

    LaunchedEffect(memoryProfileBindingMode, preferenceProfiles) {
        if (memoryProfileBindingMode == CharacterCardMemoryProfileBindingMode.FIXED_PROFILE) {
            if (
                preferenceProfiles.isNotEmpty() &&
                (fixedMemoryProfileId.isBlank() || preferenceProfiles.none { it.id == fixedMemoryProfileId })
            ) {
                fixedMemoryProfileId = preferenceProfiles.first().id
            }
        } else {
            showFixedMemoryProfilePickerDialog = false
        }
    }

    LaunchedEffect(characterCard.id, showToolAccessDialog, useEnglish) {
        builtinToolOptions = SystemToolPrompts.getManageableToolPrompts(useEnglish).map { tool ->
            CharacterCardToolAccessOption(
                key = tool.name,
                title = tool.name,
                subtitle = listOf(tool.categoryName, tool.description)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
            )
        }
        packageOptions = buildCharacterCardPackageToolAccessOptions(context, packageManager, useEnglish)
        skillOptions = skillRepository.getAiVisibleSkillPackages()
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .map { (skillName, skillPackage) ->
                CharacterCardToolAccessOption(
                    key = skillName,
                    title = skillName,
                    subtitle = skillPackage.description
                )
            }
        mcpOptions = packageManager.getAvailableServerPackages()
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .map { (serverName, serverConfig) ->
                CharacterCardToolAccessOption(
                    key = serverName,
                    title = serverName,
                    subtitle = serverConfig.description
                )
            }
    }

    val normalizedToolAccessConfig = toolAccessConfig.normalized()
    val toolAccessSummary = if (!normalizedToolAccessConfig.enabled) {
        stringResource(R.string.character_card_tool_access_follow_global)
    } else if (
        normalizedToolAccessConfig.allowedBuiltinTools.isEmpty() &&
        normalizedToolAccessConfig.allowedPackages.isEmpty() &&
        normalizedToolAccessConfig.allowedSkills.isEmpty() &&
        normalizedToolAccessConfig.allowedMcpServers.isEmpty()
    ) {
        stringResource(R.string.character_card_tool_access_summary_empty)
    } else {
        stringResource(
            R.string.character_card_tool_access_summary_counts,
            normalizedToolAccessConfig.allowedBuiltinTools.size,
            normalizedToolAccessConfig.allowedPackages.size,
            normalizedToolAccessConfig.allowedSkills.size,
            normalizedToolAccessConfig.allowedMcpServers.size
        )
    }

    fun draftCard(): CharacterCard? {
        val selectedFixedConfig = configSummaries.find { it.id == fixedChatModelConfigId }
        val normalizedFixedModelIndex = if (selectedFixedConfig != null) {
            getValidModelIndex(selectedFixedConfig.modelName, fixedChatModelIndex)
        } else {
            0
        }
        val finalToolAccessConfig = normalizedToolAccessConfig
        if (
            finalToolAccessConfig.enabled &&
            finalToolAccessConfig.hasExternalSelections() &&
            !finalToolAccessConfig.allowedBuiltinTools.contains("use_package")
        ) {
            OKToast.warning(
                context,
                context.getString(R.string.character_card_tool_access_requires_use_package),
                OKToast.LENGTH_SHORT
            ).show()
            return null
        }
        return characterCard.copy(
            name = name,
            description = description,
            characterSetting = characterSetting,
            openingStatement = openingStatement,
            otherContentChat = otherContentChat,
            otherContentVoice = otherContentVoice,
            attachedTagIds = attachedTagIds,
            advancedCustomPrompt = advancedCustomPrompt,
            marks = marks,
            chatModelBindingMode = CharacterCardChatModelBindingMode.normalize(chatModelBindingMode),
            chatModelConfigId = if (
                chatModelBindingMode == CharacterCardChatModelBindingMode.FIXED_CONFIG &&
                fixedChatModelConfigId.isNotBlank()
            ) fixedChatModelConfigId else null,
            chatModelIndex = if (chatModelBindingMode == CharacterCardChatModelBindingMode.FIXED_CONFIG) {
                normalizedFixedModelIndex
            } else {
                0
            },
            memoryProfileBindingMode = CharacterCardMemoryProfileBindingMode.normalize(memoryProfileBindingMode),
            memoryProfileId = if (
                memoryProfileBindingMode == CharacterCardMemoryProfileBindingMode.FIXED_PROFILE &&
                fixedMemoryProfileId.isNotBlank()
            ) fixedMemoryProfileId else null,
            toolAccessConfig = finalToolAccessConfig
        )
    }

    ContentSizedTabbedDialog(
        title = stringResource(
            if (characterCard.id.isBlank()) R.string.create_character_card else R.string.edit_character_card
        ),
        tabTitles = listOf(
            stringResource(R.string.character_card_tab_basic),
            stringResource(R.string.character_card_tab_content),
            stringResource(R.string.character_card_tab_speech),
            stringResource(R.string.character_card_tab_binding),
        ),
        pagerState = pagerState,
        onDismiss = { if (!isSaving) onDismiss() },
        actions = {
            TextButton(enabled = speechSettings != null && !isSaving, onClick = {
                val speech = speechSettings ?: return@TextButton
                draftCard()?.let { onExport(it, speech) }
            }) { Text(stringResource(R.string.export)) }
            TextButton(enabled = !isSaving, onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            Spacer(Modifier.width(8.dp))
            Button(enabled = speechSettings != null && !isSaving, onClick = {
                val speech = speechSettings ?: return@Button
                draftCard()?.let { onSave(it, speech) }
            }) { Text(stringResource(R.string.save)) }
        },
    ) { page ->
        if (page == 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CompactAvatarPicker(
                    avatarUri = displayedAvatarUri,
                    onAvatarChange = onAvatarChange,
                    onAvatarReset = onAvatarReset,
                    showAction = displayedAvatarUri != null,
                    size = 64.dp
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompactTextFieldWithExpand(
                        value = name,
                        onValueChange = { name = it },
                        label = stringResource(R.string.character_card_name),
                        singleLine = true,
                        onExpandClick = {
                            fullScreenEditTitle = context.getString(R.string.character_card_edit_name)
                            fullScreenEditValue = name
                            fullScreenEditField = "name"
                            showFullScreenEdit = true
                        }
                    )
                    CompactTextFieldWithExpand(
                        value = description,
                        onValueChange = { description = it },
                        label = stringResource(R.string.character_card_description),
                        maxLines = 2,
                        onExpandClick = {
                            fullScreenEditTitle = context.getString(R.string.character_card_edit_description)
                            fullScreenEditValue = description
                            fullScreenEditField = "description"
                            showFullScreenEdit = true
                        }
                    )
                }
            }

        // 角色设定
        CompactTextFieldWithExpand(
            value = characterSetting,
            onValueChange = { characterSetting = it },
            label = stringResource(R.string.character_card_character_setting),
            placeholder = stringResource(R.string.character_card_character_setting_placeholder),
            minLines = 5,
            maxLines = 8,
            onExpandClick = {
                fullScreenEditTitle = context.getString(R.string.character_card_edit_character_setting)
                fullScreenEditValue = characterSetting
                fullScreenEditField = "characterSetting"
                showFullScreenEdit = true
            }
        )

        // 开场白
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.character_card_opening_statement),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )

            // 翻译按钮
            IconButton(
                onClick = {
                    if (openingStatement.isNotBlank() && !isTranslating) {
                        scope.launch {
                            isTranslating = true
                            try {
                                val enhancedAIService = EnhancedAIService.getInstance(context)
                                val translatedText = enhancedAIService.translateText(openingStatement)
                                openingStatement = translatedText
                            } catch (e: Exception) {
                                // 翻译失败，可以显示错误提示
                            } finally {
                                isTranslating = false
                            }
                        }
                    }
                },
                enabled = openingStatement.isNotBlank() && !isTranslating,
                modifier = Modifier.size(28.dp)
            ) {
                if (isTranslating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = stringResource(R.string.character_card_translate_opening_statement),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        CompactTextFieldWithExpand(
            value = openingStatement,
            onValueChange = { openingStatement = it },
            placeholder = stringResource(R.string.character_card_opening_statement_placeholder),
            minLines = 2,
            maxLines = 4,
            onExpandClick = {
                fullScreenEditTitle = context.getString(R.string.character_card_edit_opening_statement)
                fullScreenEditValue = openingStatement
                fullScreenEditField = "openingStatement"
                showFullScreenEdit = true
            }
        )

        }
        if (page == 1) {
        // 其他内容（聊天）
        Text(
            text = stringResource(R.string.character_card_other_content_chat),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        CompactTextFieldWithExpand(
            value = otherContentChat,
            onValueChange = { otherContentChat = it },
            placeholder = stringResource(R.string.character_card_other_content_chat_placeholder),
            minLines = 2,
            maxLines = 4,
            onExpandClick = {
                fullScreenEditTitle = context.getString(R.string.character_card_edit_other_content_chat)
                fullScreenEditValue = otherContentChat
                fullScreenEditField = "otherContentChat"
                showFullScreenEdit = true
            }
        )

        // 其他内容（语音）
        Text(
            text = stringResource(R.string.character_card_other_content_voice),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        CompactTextFieldWithExpand(
            value = otherContentVoice,
            onValueChange = { otherContentVoice = it },
            placeholder = stringResource(R.string.character_card_other_content_voice_placeholder),
            minLines = 2,
            maxLines = 4,
            onExpandClick = {
                fullScreenEditTitle = context.getString(R.string.character_card_edit_other_content_voice)
                fullScreenEditValue = otherContentVoice
                fullScreenEditField = "otherContentVoice"
                showFullScreenEdit = true
            }
        )

        // 高级自定义提示词
        CompactTextFieldWithExpand(
            value = advancedCustomPrompt,
            onValueChange = { advancedCustomPrompt = it },
            label = stringResource(R.string.character_card_custom_prompt),
            placeholder = stringResource(R.string.character_card_custom_prompt_placeholder),
            minLines = 2,
            maxLines = 3,
            onExpandClick = {
                fullScreenEditTitle = context.getString(R.string.character_card_edit_custom_prompt)
                fullScreenEditValue = advancedCustomPrompt
                fullScreenEditField = "advancedCustomPrompt"
                showFullScreenEdit = true
            }
        )

        CompactTextFieldWithExpand(
            value = marks,
            onValueChange = { marks = it },
            label = stringResource(R.string.character_card_marks),
            placeholder = stringResource(R.string.character_card_marks_placeholder),
            minLines = 1,
            maxLines = 2,
            onExpandClick = {
                fullScreenEditTitle = context.getString(R.string.character_card_edit_marks)
                fullScreenEditValue = marks
                fullScreenEditField = "marks"
                showFullScreenEdit = true
            }
        )
        }
        if (page == 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.character_card_tags),
                    style = MaterialTheme.typography.labelLarge
                )
                TextButton(onClick = onManageTags) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.tag_management))
                }
            }
        val availableTags = allTags
        if (availableTags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                availableTags.forEach { tag ->
                    val isSelected = attachedTagIds.contains(tag.id)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            attachedTagIds = if (isSelected) {
                                attachedTagIds.filter { it != tag.id }
                            } else {
                                attachedTagIds + tag.id
                            }
                        },
                        label = { Text(tag.name, fontSize = 10.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
        } else {
            Text(
                text = stringResource(R.string.character_card_no_custom_tags),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center
            )
        }
        }
        if (page == 2) {
            val speech = speechSettings
            if (speech != null) CharacterSpeechSettings(
                characterCardId = characterCard.id,
                settings = speech,
                onChange = { speechSettings = it },
                onExpandPrompt = { selfie ->
                    fullScreenEditTitle = context.getString(if (selfie) R.string.appearance_prompt else R.string.waifu_custom_prompt)
                    fullScreenEditValue = if (selfie) speech.selfiePrompt else speech.customPrompt
                    fullScreenEditField = if (selfie) "waifuSelfiePrompt" else "waifuCustomPrompt"
                    showFullScreenEdit = true
                },
            ) else if (speechLoadFailed) {
                Text(stringResource(R.string.character_speech_load_failed), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { speechLoadVersion++ }) { Text(stringResource(R.string.action_retry)) }
            } else CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
        if (page == 3) {
            val selectedFixedConfig = configSummaries.find { it.id == fixedChatModelConfigId }
            val effectiveFixedModelIndex = if (selectedFixedConfig != null) {
                getValidModelIndex(selectedFixedConfig.modelName, fixedChatModelIndex)
            } else {
                0
            }
            val selectedFixedMemoryProfile = preferenceProfiles.find { it.id == fixedMemoryProfileId }
            val chatModelBindingSummary = if (
                chatModelBindingMode == CharacterCardChatModelBindingMode.FOLLOW_GLOBAL
            ) {
                context.getString(R.string.character_card_chat_model_follow_global)
            } else {
                val selectedModelName = selectedFixedConfig?.let {
                    getModelByIndex(
                        it.modelName,
                        effectiveFixedModelIndex
                    ).ifBlank { context.getString(R.string.not_selected) }
                } ?: context.getString(R.string.not_selected)
                listOf(
                    context.getString(R.string.character_card_chat_model_fixed_config),
                    selectedFixedConfig?.name,
                    selectedModelName
                ).filter { !it.isNullOrBlank() }.joinToString(" · ")
            }
            val memoryProfileBindingSummary = if (
                memoryProfileBindingMode == CharacterCardMemoryProfileBindingMode.FOLLOW_GLOBAL
            ) {
                context.getString(R.string.character_card_memory_profile_follow_global)
            } else {
                listOf(
                    context.getString(R.string.character_card_memory_profile_fixed_profile),
                    selectedFixedMemoryProfile?.name
                ).filter { !it.isNullOrBlank() }.joinToString(" · ")
            }

            Text(
                text = stringResource(R.string.character_card_chat_model_binding),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        chatModelBindingMode =
                            if (chatModelBindingMode == CharacterCardChatModelBindingMode.FIXED_CONFIG) {
                                CharacterCardChatModelBindingMode.FOLLOW_GLOBAL
                            } else {
                                CharacterCardChatModelBindingMode.FIXED_CONFIG
                            }
                    },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f),
                border = BorderStroke(
                    width = 0.8.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.character_card_chat_model_specify_fixed_config),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = chatModelBindingSummary,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = chatModelBindingMode == CharacterCardChatModelBindingMode.FIXED_CONFIG,
                        onCheckedChange = null
                    )
                }
            }

            if (chatModelBindingMode == CharacterCardChatModelBindingMode.FIXED_CONFIG) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = configSummaries.isNotEmpty()) {
                            showFixedConfigPickerDialog = true
                        },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f),
                    border = BorderStroke(
                        width = 0.8.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedFixedConfig?.name ?: stringResource(R.string.select_model_config),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )

                            if (selectedFixedConfig != null) {
                                Text(
                                    text = getModelByIndex(
                                        selectedFixedConfig.modelName,
                                        effectiveFixedModelIndex
                                    ).ifBlank { stringResource(R.string.not_selected) },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.not_selected),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                                )
                            }
                        }

                        if (configSummaries.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                CharacterCardFixedModelPickerDialog(
                    visible = showFixedConfigPickerDialog,
                    configSummaries = configSummaries,
                    selectedConfigId = fixedChatModelConfigId,
                    selectedModelIndex = fixedChatModelIndex,
                    onSelect = { configId, modelIndex ->
                        fixedChatModelConfigId = configId
                        fixedChatModelIndex = modelIndex
                        showFixedConfigPickerDialog = false
                        popupExpandedFixedConfigId = null
                    },
                    expandedConfigId = popupExpandedFixedConfigId,
                    onExpandedConfigIdChange = { popupExpandedFixedConfigId = it },
                    onDismiss = {
                        showFixedConfigPickerDialog = false
                        popupExpandedFixedConfigId = null
                    }
                )
            }

            Text(
                text = stringResource(R.string.character_card_memory_profile_binding),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        memoryProfileBindingMode =
                            if (memoryProfileBindingMode == CharacterCardMemoryProfileBindingMode.FIXED_PROFILE) {
                                CharacterCardMemoryProfileBindingMode.FOLLOW_GLOBAL
                            } else {
                                CharacterCardMemoryProfileBindingMode.FIXED_PROFILE
                            }
                    },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f),
                border = BorderStroke(
                    width = 0.8.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.character_card_memory_profile_specify_fixed_profile),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = memoryProfileBindingSummary,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = memoryProfileBindingMode == CharacterCardMemoryProfileBindingMode.FIXED_PROFILE,
                        onCheckedChange = null
                    )
                }
            }

            if (memoryProfileBindingMode == CharacterCardMemoryProfileBindingMode.FIXED_PROFILE) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = preferenceProfiles.isNotEmpty()) {
                            showFixedMemoryProfilePickerDialog = true
                        },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f),
                    border = BorderStroke(
                        width = 0.8.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedFixedMemoryProfile?.name
                                    ?: stringResource(R.string.character_card_memory_profile_select),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Text(
                                text = selectedFixedMemoryProfile?.id ?: stringResource(R.string.not_selected),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                            )
                        }

                        if (preferenceProfiles.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                CharacterCardFixedMemoryProfilePickerDialog(
                    visible = showFixedMemoryProfilePickerDialog,
                    preferenceProfiles = preferenceProfiles,
                    selectedProfileId = fixedMemoryProfileId,
                    onSelect = { profileId ->
                        fixedMemoryProfileId = profileId
                        showFixedMemoryProfilePickerDialog = false
                    },
                    onDismiss = { showFixedMemoryProfilePickerDialog = false }
                )
            }

            Text(
                text = stringResource(R.string.character_card_tool_access_title),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        toolAccessConfig = normalizedToolAccessConfig.copy(
                            enabled = !normalizedToolAccessConfig.enabled
                        )
                    },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f),
                border = BorderStroke(
                    width = 0.8.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.character_card_tool_access_enable),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = toolAccessSummary,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = normalizedToolAccessConfig.enabled,
                        onCheckedChange = null
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showToolAccessDialog = true },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(
                    width = 0.8.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.character_card_tool_access_configure),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = toolAccessSummary,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    CharacterCardToolAccessDialog(
        visible = showToolAccessDialog,
        config = normalizedToolAccessConfig,
        builtinOptions = builtinToolOptions,
        packageOptions = packageOptions,
        skillOptions = skillOptions,
        mcpOptions = mcpOptions,
        onConfirm = { toolAccessConfig = it.normalized() },
        onDismiss = { showToolAccessDialog = false }
    )
    
    // 全屏编辑对话框
    if (showFullScreenEdit) {
        FullScreenEditDialog(
            title = fullScreenEditTitle,
            value = fullScreenEditValue,
            onDismiss = { showFullScreenEdit = false },
            onSave = { newValue ->
                when (fullScreenEditField) {
                    "name" -> name = newValue
                    "description" -> description = newValue
                    "characterSetting" -> characterSetting = newValue
                    "openingStatement" -> openingStatement = newValue // 新增
                    "otherContentChat" -> otherContentChat = newValue
                    "otherContentVoice" -> otherContentVoice = newValue
                    "advancedCustomPrompt" -> advancedCustomPrompt = newValue
                    "marks" -> marks = newValue
                    "waifuCustomPrompt" -> speechSettings = speechSettings?.copy(customPrompt = newValue)
                    "waifuSelfiePrompt" -> speechSettings = speechSettings?.copy(selfiePrompt = newValue)
                }
                showFullScreenEdit = false
            }
        )
    }
}

private data class CharacterCardToolAccessOption(
    val key: String,
    val title: String,
    val subtitle: String = ""
)

private fun buildCharacterCardPackageToolAccessOptions(
    context: android.content.Context,
    packageManager: PackageManager,
    useEnglish: Boolean
): List<CharacterCardToolAccessOption> {
    val preferredLanguage = if (useEnglish) "en" else "zh"
    return packageManager.getEnabledPackageNames()
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .filter { packageName ->
            packageManager.getPackageTools(packageName) != null &&
                !packageManager.isToolPkgContainer(packageName)
        }
        .toList()
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
        .mapNotNull { packageName ->
            val toolPackage = packageManager.getPackageTools(packageName) ?: return@mapNotNull null
            val description = runCatching {
                toolPackage.description.resolve(preferredLanguage)
            }.getOrElse {
                toolPackage.description.resolve(context)
            }
            CharacterCardToolAccessOption(
                key = packageName,
                title = packageName,
                subtitle = description
            )
        }
}

private fun toggleCharacterCardToolAccessSelection(values: List<String>, key: String): List<String> {
    return if (values.contains(key)) {
        values.filterNot { it == key }
    } else {
        values + key
    }
}

@Composable
private fun CharacterCardToolAccessDialog(
    visible: Boolean,
    config: CharacterCardToolAccessConfig,
    builtinOptions: List<CharacterCardToolAccessOption>,
    packageOptions: List<CharacterCardToolAccessOption>,
    skillOptions: List<CharacterCardToolAccessOption>,
    mcpOptions: List<CharacterCardToolAccessOption>,
    onConfirm: (CharacterCardToolAccessConfig) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    var selectedTabIndex by remember(visible) { mutableStateOf(0) }
    var localConfig by remember(visible, config) { mutableStateOf(config.normalized()) }
    var searchQuery by remember(visible) { mutableStateOf("") }
    val tabTitles = listOf(
        stringResource(R.string.character_card_tool_access_tab_builtin),
        stringResource(R.string.character_card_tool_access_tab_package),
        stringResource(R.string.character_card_tool_access_tab_skill),
        stringResource(R.string.character_card_tool_access_tab_mcp)
    )
    val currentOptions = when (selectedTabIndex) {
        0 -> builtinOptions
        1 -> packageOptions
        2 -> skillOptions
        else -> mcpOptions
    }
    val selectedValues = when (selectedTabIndex) {
        0 -> localConfig.allowedBuiltinTools
        1 -> localConfig.allowedPackages
        2 -> localConfig.allowedSkills
        else -> localConfig.allowedMcpServers
    }
    val emptyMessage = when (selectedTabIndex) {
        0 -> stringResource(R.string.character_card_tool_access_empty_builtin)
        1 -> stringResource(R.string.character_card_tool_access_empty_packages)
        2 -> stringResource(R.string.character_card_tool_access_empty_skills)
        else -> stringResource(R.string.character_card_tool_access_empty_mcp)
    }
    val searchText = searchQuery.trim()
    val filteredOptions = remember(currentOptions, searchText) {
        if (searchText.isEmpty()) {
            currentOptions
        } else {
            currentOptions.filter { option ->
                option.key.contains(searchText, ignoreCase = true) ||
                    option.title.contains(searchText, ignoreCase = true) ||
                    option.subtitle.contains(searchText, ignoreCase = true)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.character_card_tool_access_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    edgePadding = 0.dp
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = {
                                selectedTabIndex = index
                                searchQuery = ""
                            },
                            text = { Text(title, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (currentOptions.isNotEmpty()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.clear)
                                    )
                                }
                            }
                        },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.character_card_tool_access_search_placeholder),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (currentOptions.isEmpty()) {
                        Text(
                            text = emptyMessage,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            textAlign = TextAlign.Center
                        )
                    } else if (filteredOptions.isEmpty()) {
                        Text(
                            text = stringResource(R.string.character_card_tool_access_empty_search),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        filteredOptions.forEachIndexed { index, option ->
                            val isSelected = selectedValues.contains(option.key)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = isSelected,
                                        onValueChange = {
                                            localConfig = when (selectedTabIndex) {
                                                0 -> localConfig.copy(
                                                    allowedBuiltinTools = toggleCharacterCardToolAccessSelection(
                                                        localConfig.allowedBuiltinTools,
                                                        option.key
                                                    )
                                                )
                                                1 -> localConfig.copy(
                                                    allowedPackages = toggleCharacterCardToolAccessSelection(
                                                        localConfig.allowedPackages,
                                                        option.key
                                                    )
                                                )
                                                2 -> localConfig.copy(
                                                    allowedSkills = toggleCharacterCardToolAccessSelection(
                                                        localConfig.allowedSkills,
                                                        option.key
                                                    )
                                                )
                                                else -> localConfig.copy(
                                                    allowedMcpServers = toggleCharacterCardToolAccessSelection(
                                                        localConfig.allowedMcpServers,
                                                        option.key
                                                    )
                                                )
                                            }.normalized()
                                        },
                                        role = Role.Checkbox
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (option.subtitle.isNotBlank()) {
                                        Text(
                                            text = option.subtitle,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = null
                                )
                            }

                            if (index < filteredOptions.lastIndex) {
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }

                    Button(
                        onClick = {
                            onConfirm(localConfig.normalized())
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterCardFixedModelPickerDialog(
    visible: Boolean,
    configSummaries: List<ModelConfigSummary>,
    selectedConfigId: String,
    selectedModelIndex: Int,
    onSelect: (String, Int) -> Unit,
    expandedConfigId: String?,
    onExpandedConfigIdChange: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = stringResource(R.string.select_config),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(scrollState)
                ) {
                    configSummaries.forEach { summary ->
                        val isSelectedConfig = summary.id == selectedConfigId
                        val modelList = getModelList(summary.modelName)
                        val hasMultipleModels = modelList.size > 1
                        val isExpanded = expandedConfigId == summary.id

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable {
                                        if (hasMultipleModels) {
                                            onExpandedConfigIdChange(if (isExpanded) null else summary.id)
                                        } else {
                                            onSelect(summary.id, 0)
                                        }
                                    },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelectedConfig) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                border = BorderStroke(
                                    width = if (isSelectedConfig) 0.dp else 0.5.dp,
                                    color = if (isSelectedConfig) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSelectedConfig && !hasMultipleModels) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }

                                    Text(
                                        text = summary.name,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelectedConfig) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelectedConfig) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1
                                    )

                                    if (hasMultipleModels) {
                                        Icon(
                                            imageVector = if (isExpanded) {
                                                Icons.Default.KeyboardArrowUp
                                            } else {
                                                Icons.Default.KeyboardArrowDown
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (hasMultipleModels && isExpanded) {
                                val validSelectedIndex = if (isSelectedConfig) {
                                    getValidModelIndex(summary.modelName, selectedModelIndex)
                                } else {
                                    0
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, top = 2.dp, bottom = 2.dp, end = 4.dp)
                                ) {
                                    modelList.forEachIndexed { index, modelName ->
                                        val isModelSelected = isSelectedConfig && validSelectedIndex == index
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 1.dp)
                                                .clickable { onSelect(summary.id, index) },
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (isModelSelected) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (isModelSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                }
                                                Text(
                                                    text = modelName,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isModelSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isModelSelected) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    },
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterCardFixedMemoryProfilePickerDialog(
    visible: Boolean,
    preferenceProfiles: List<MemorySpace>,
    selectedProfileId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = stringResource(R.string.character_card_memory_profile_select),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(scrollState)
                ) {
                    preferenceProfiles.forEach { profile ->
                        val isSelected = profile.id == selectedProfileId
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable { onSelect(profile.id) },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            border = BorderStroke(
                                width = if (isSelected) 0.dp else 0.5.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.name,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 1
                                    )
                                    Text(
                                        text = profile.id,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// 带展开按钮的紧凑输入框
@Composable
fun CompactTextFieldWithExpand(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    onExpandClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = label?.let { { Text(it, fontSize = 11.sp) } },
            placeholder = placeholder?.let { { Text(it, fontSize = 11.sp) } },
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 32.dp), // 为右上角按钮留空间
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.25f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.25f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                unfocusedLabelColor = MaterialTheme.colorScheme.primary,
            )
        )
        
        // 右上角展开按钮
        IconButton(
            onClick = onExpandClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(28.dp)
                .offset(x = (-2).dp, y = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Default.OpenInFull,
                contentDescription = stringResource(R.string.character_card_fullscreen_edit),
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// 全屏编辑对话框
@Composable
fun FullScreenEditDialog(
    title: String,
    value: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var editValue by remember { mutableStateOf(value) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .imePadding(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 编辑区域
                OutlinedTextField(
                    value = editValue,
                    onValueChange = { editValue = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    placeholder = { Text(stringResource(R.string.character_card_fullscreen_edit_placeholder)) }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 底部按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    
                    Button(
                        onClick = { onSave(editValue) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}

// 紧凑型头像选择器
@Composable
fun CompactAvatarPicker(
    avatarUri: String?,
    onAvatarChange: () -> Unit,
    onAvatarReset: () -> Unit,
    showAction: Boolean = true,
    size: Dp = 48.dp
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 头像
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onAvatarChange)
                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (avatarUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(model = Uri.parse(avatarUri)),
                    contentDescription = stringResource(R.string.character_card_avatar),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = stringResource(R.string.character_card_default_avatar),
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 重置按钮
        if (showAction && avatarUri != null) {
            TextButton(
                onClick = onAvatarReset,
                modifier = Modifier.height(24.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(stringResource(R.string.character_card_reset), fontSize = 10.sp)
            }
        } else if (showAction) {
            TextButton(
                onClick = onAvatarChange,
                modifier = Modifier.height(24.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(stringResource(R.string.character_card_add), fontSize = 10.sp)
            }
        }
    }
}
