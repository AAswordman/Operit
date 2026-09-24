package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.config.FunctionalPrompts
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.model.CharacterCardSummaryBindingMode
import com.ai.assistance.operit.data.model.ContextSummarySettings
import com.ai.assistance.operit.data.model.SummarySectionConfig
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.LocaleUtils
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun ContextSummarySettingsScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val apiPreferences = remember { ApiPreferences.getInstance(context) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var maxImageHistoryUserTurnsInput by remember { mutableStateOf("") }
    var maxMediaHistoryUserTurnsInput by remember { mutableStateOf("") }
    val savedMaxImageHistoryUserTurns by
        apiPreferences.maxImageHistoryUserTurnsFlow.collectAsState(
            initial = ApiPreferences.DEFAULT_MAX_IMAGE_HISTORY_USER_TURNS
        )
    val savedMaxMediaHistoryUserTurns by
        apiPreferences.maxMediaHistoryUserTurnsFlow.collectAsState(
            initial = ApiPreferences.DEFAULT_MAX_MEDIA_HISTORY_USER_TURNS
        )

    val hasBackgroundImage = LocalThemePreferenceSnapshot.current.useBackgroundImage
    val componentBackgroundColor =
        if (hasBackgroundImage) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        }

    LaunchedEffect(Unit) {
        maxImageHistoryUserTurnsInput = apiPreferences.maxImageHistoryUserTurnsFlow.first().toString()
        maxMediaHistoryUserTurnsInput = apiPreferences.maxMediaHistoryUserTurnsFlow.first().toString()
    }

    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val activePromptManager = remember { ActivePromptManager.getInstance(context) }
    val userPreferencesManager = remember { UserPreferencesManager.getInstance(context) }

    // 总结配置归属：null 表示全局默认，否则角色卡 id。初始定位到当前会话使用的角色卡。
    var selectedCardId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val activePrompt = activePromptManager.activePromptFlow.first()
        selectedCardId = (activePrompt as? ActivePrompt.CharacterCard)?.id
    }
    val characterCards by produceState<List<CharacterCard>>(initialValue = emptyList()) {
        characterCardManager.characterCardListFlow.collect { ids ->
            value = ids.mapNotNull { id ->
                runCatching { characterCardManager.getCharacterCard(id) }.getOrNull()
            }
        }
    }
    val activeCardId by produceState<String?>(initialValue = null) {
        activePromptManager.activePromptFlow.collect { prompt ->
            value = (prompt as? ActivePrompt.CharacterCard)?.id
        }
    }
    // 目标为角色卡且跟随全局时，展示的是全局默认配置，编辑区禁用，避免误以为在改卡内配置。
    val summaryTarget =
        rememberSummaryTarget(
            selectedCardId = selectedCardId,
            characterCardManager = characterCardManager,
            userPreferencesManager = userPreferencesManager
        )
    val summaryEditable = summaryTarget?.editable ?: false

    var enableSummary by remember(selectedCardId) {
        mutableStateOf(summaryTarget?.settings?.enableSummary ?: false)
    }
    var summaryTokenThresholdInput by remember(selectedCardId) {
        mutableStateOf(formatFloatValue(summaryTarget?.settings?.summaryTokenThreshold))
    }
    var enableSummaryByMessageCount by remember(selectedCardId) {
        mutableStateOf(summaryTarget?.settings?.enableSummaryByMessageCount ?: false)
    }
    var summaryMessageCountThresholdInput by remember(selectedCardId) {
        mutableStateOf(summaryTarget?.settings?.summaryMessageCountThreshold?.toString().orEmpty())
    }
    var summaryCustomRulesInput by remember(selectedCardId) {
        mutableStateOf(summaryTarget?.settings?.summaryCustomRules.orEmpty())
    }
    var dialogueReviewEnabled by remember(selectedCardId) {
        mutableStateOf(summaryTarget?.settings?.dialogueReviewEnabled ?: true)
    }
    var dialogueReviewTitleInput by remember(selectedCardId) {
        mutableStateOf(summaryTarget?.settings?.dialogueReviewTitle.orEmpty())
    }
    val useEnglish = !LocaleUtils.usesChineseContent(context)
    var summarySectionsInput by remember(selectedCardId) {
        mutableStateOf(emptyList<SummarySectionConfig>())
    }
    var fullscreenTextEditor by remember { mutableStateOf<FullscreenTextEditorRequest?>(null) }
    var summaryError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedCardId, summaryTarget?.settings?.enableSummary) {
        enableSummary = summaryTarget?.settings?.enableSummary ?: false
    }
    LaunchedEffect(selectedCardId, summaryTarget?.settings?.summaryTokenThreshold) {
        summaryTokenThresholdInput = formatFloatValue(summaryTarget?.settings?.summaryTokenThreshold)
    }
    LaunchedEffect(selectedCardId, summaryTarget?.settings?.enableSummaryByMessageCount) {
        enableSummaryByMessageCount = summaryTarget?.settings?.enableSummaryByMessageCount ?: false
    }
    LaunchedEffect(selectedCardId, summaryTarget?.settings?.summaryMessageCountThreshold) {
        summaryMessageCountThresholdInput =
            summaryTarget?.settings?.summaryMessageCountThreshold?.toString().orEmpty()
    }
    LaunchedEffect(selectedCardId, summaryTarget?.settings?.summaryCustomRules) {
        summaryCustomRulesInput = summaryTarget?.settings?.summaryCustomRules.orEmpty()
    }
    LaunchedEffect(selectedCardId, summaryTarget?.settings?.dialogueReviewEnabled) {
        dialogueReviewEnabled = summaryTarget?.settings?.dialogueReviewEnabled ?: true
    }
    LaunchedEffect(selectedCardId, summaryTarget?.settings?.dialogueReviewTitle) {
        dialogueReviewTitleInput = summaryTarget?.settings?.dialogueReviewTitle.orEmpty()
    }
    LaunchedEffect(selectedCardId, summaryTarget?.settings?.summarySectionOverrides, useEnglish) {
        summarySectionsInput = FunctionalPrompts.resolveSummarySections(
            summaryTarget?.settings?.summarySectionOverrides.orEmpty(),
            useEnglish
        )
    }

    val errorSaveFailed = stringResource(id = R.string.model_config_error_save_failed)
    val errorSummaryThresholdRange =
        stringResource(id = R.string.model_config_error_summary_threshold_range)
    val errorValidMessageCount = stringResource(id = R.string.model_config_error_valid_message_count)

    var showSaveSuccessMessage by remember { mutableStateOf(false) }
    var historyError by remember { mutableStateOf<String?>(null) }

    SummarySettingsAutoSaveEffect(
        target = summaryTarget,
        inputsProvider = {
            SummarySettingsInputs(
                enableSummary = enableSummary,
                summaryTokenThresholdInput = summaryTokenThresholdInput,
                enableSummaryByMessageCount = enableSummaryByMessageCount,
                summaryMessageCountThresholdInput = summaryMessageCountThresholdInput,
                summaryCustomRulesInput = summaryCustomRulesInput,
                dialogueReviewEnabled = dialogueReviewEnabled,
                dialogueReviewTitleInput = dialogueReviewTitleInput,
                summarySectionsInput = summarySectionsInput
            )
        },
        useEnglish = useEnglish,
        errorSummaryThresholdRange = errorSummaryThresholdRange,
        errorValidMessageCount = errorValidMessageCount,
        errorSaveFailed = errorSaveFailed,
        onSummaryErrorChange = { summaryError = it },
        onSave = { settings ->
            summaryTarget?.let { target ->
                saveSummarySettings(
                    target = target,
                    settings = settings,
                    characterCardManager = characterCardManager,
                    userPreferencesManager = userPreferencesManager
                )
            }
        }
    )
    HistoryRetentionAutoSaveEffects(
        historyInputsProvider = {
            maxImageHistoryUserTurnsInput to maxMediaHistoryUserTurnsInput
        },
        savedHistoryValuesProvider = {
            savedMaxImageHistoryUserTurns to savedMaxMediaHistoryUserTurns
        },
        apiPreferences = apiPreferences,
        errorSaveFailed = errorSaveFailed,
        onHistoryErrorChange = { historyError = it }
    )

    CustomScaffold() { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .verticalScroll(scrollState)
            ) {
                SettingsInfoBanner(
                    text = stringResource(id = R.string.context_summary_note),
                    backgroundColor = componentBackgroundColor
                )
                Spacer(modifier = Modifier.size(12.dp))

                // 总结配置归属选择器：切换角色卡即切换该卡专属的总结提示词与阈值
                val summaryTargetSection: @Composable () -> Unit = {
                    SummaryTargetSelectorCard(
                        selectedCardId = selectedCardId,
                        characterCards = characterCards,
                        activeCardId = activeCardId,
                        backgroundColor = componentBackgroundColor,
                        onSelect = { selectedCardId = it }
                    )
                    // 仅角色卡目标展示专属开关：全局默认本身就是可编辑目标
                    val target = summaryTarget
                    if (target != null && target.cardId != null) {
                        SettingsSwitchRow(
                            title =
                                stringResource(id = R.string.context_summary_target_custom_switch),
                            subtitle =
                                stringResource(
                                    id = R.string.context_summary_target_custom_switch_desc
                                ),
                            checked = target.editable,
                            onCheckedChange = { enabled ->
                                val cardId = selectedCardId
                                if (cardId != null) {
                                    scope.launch {
                                        characterCardManager.updateCharacterCardSummaryBinding(
                                            cardId,
                                            enabled
                                        )
                                    }
                                }
                            },
                            backgroundColor = componentBackgroundColor
                        )
                    }
                }

                RenderContextSummaryConfigSections(
                    componentBackgroundColor = componentBackgroundColor,
                    summaryEditable = summaryEditable,
                    enableSummary = enableSummary,
                    onEnableSummaryChange = { enableSummary = it },
                    summaryTokenThresholdInput = summaryTokenThresholdInput,
                    onSummaryTokenThresholdInputChange = {
                        summaryTokenThresholdInput = it
                        summaryError = null
                    },
                    enableSummaryByMessageCount = enableSummaryByMessageCount,
                    onEnableSummaryByMessageCountChange = { enableSummaryByMessageCount = it },
                    summaryMessageCountThresholdInput = summaryMessageCountThresholdInput,
                    onSummaryMessageCountThresholdInputChange = {
                        summaryMessageCountThresholdInput = it
                        summaryError = null
                    },
                    summaryCustomRulesInput = summaryCustomRulesInput,
                    onSummaryCustomRulesInputChange = {
                        summaryCustomRulesInput = it
                    },
                    dialogueReviewEnabled = dialogueReviewEnabled,
                    onDialogueReviewEnabledChange = { dialogueReviewEnabled = it },
                    dialogueReviewTitleInput = dialogueReviewTitleInput,
                    onDialogueReviewTitleChange = { dialogueReviewTitleInput = it },
                    summarySectionsInput = summarySectionsInput,
                    onSummarySectionsInputChange = { summarySectionsInput = it },
                    onOpenFullscreenEditor = { title, value, onValueChange ->
                        fullscreenTextEditor = FullscreenTextEditorRequest(
                            title = title,
                            value = value,
                            onValueChange = onValueChange
                        )
                    },
                    summaryTargetSection = summaryTargetSection,
                    summaryError = summaryError
                )

                Spacer(modifier = Modifier.size(12.dp))
                RenderHistoryRetentionSection(
                    componentBackgroundColor = componentBackgroundColor,
                    maxImageHistoryUserTurnsInput = maxImageHistoryUserTurnsInput,
                    onMaxImageHistoryUserTurnsInputChange = { maxImageHistoryUserTurnsInput = it },
                    maxMediaHistoryUserTurnsInput = maxMediaHistoryUserTurnsInput,
                    onMaxMediaHistoryUserTurnsInputChange = { maxMediaHistoryUserTurnsInput = it },
                    onReset = {
                        scope.launch {
                            historyError = null
                            apiPreferences.resetHistoryRetentionSettings()
                            maxImageHistoryUserTurnsInput =
                                apiPreferences.maxImageHistoryUserTurnsFlow.first().toString()
                            maxMediaHistoryUserTurnsInput =
                                apiPreferences.maxMediaHistoryUserTurnsFlow.first().toString()
                            showSaveSuccessMessage = true
                        }
                    },
                    historyError = historyError
                )

                Spacer(modifier = Modifier.size(16.dp))
            }

            RenderContextSummaryDialogs(
                showSaveSuccessMessage = showSaveSuccessMessage,
                onDismissSaveSuccess = { showSaveSuccessMessage = false }
            )
            fullscreenTextEditor?.let { request ->
                FullscreenSettingsTextEditor(
                    request = request,
                    onDismiss = { fullscreenTextEditor = null }
                )
            }
        }
    }
}

/** 总结配置归属目标：null 为全局默认，其余为角色卡。 */
internal data class SummaryTarget(
    val cardId: String?,
    val cardName: String,
    val settings: ContextSummarySettings,
    val bindingMode: String,
    val editable: Boolean
)

/**
 * 观察某个归属目标（角色卡 id，null 为全局默认）的总结配置。
 *
 * 角色卡跟随全局时展示的是全局配置，editable 为 false，界面据此禁用输入，
 * 避免用户误以为在改卡内配置。模型参数界面与总结设置界面共用这一份观察逻辑。
 */
@Composable
internal fun rememberSummaryTarget(
    selectedCardId: String?,
    characterCardManager: CharacterCardManager,
    userPreferencesManager: UserPreferencesManager
): SummaryTarget? {
    val context = LocalContext.current
    val target by produceState<SummaryTarget?>(initialValue = null, selectedCardId) {
        val cardId = selectedCardId
        if (cardId == null) {
            userPreferencesManager.globalContextSummaryFlow.collect { settings ->
                value =
                    SummaryTarget(
                        cardId = null,
                        cardName = context.getString(R.string.context_summary_target_global_default),
                        settings = settings,
                        bindingMode = "",
                        editable = true
                    )
            }
        } else {
            kotlinx.coroutines.flow.combine(
                characterCardManager.getCharacterCardFlow(cardId),
                userPreferencesManager.globalContextSummaryFlow
            ) { card, global ->
                val custom =
                    CharacterCardSummaryBindingMode.normalize(card.summaryBindingMode) ==
                        CharacterCardSummaryBindingMode.CUSTOM
                SummaryTarget(
                    cardId = card.id,
                    cardName = card.name,
                    settings = if (custom) card.summary else global,
                    bindingMode = card.summaryBindingMode,
                    editable = custom
                )
            }.collect { value = it }
        }
    }
    return target
}

/**
 * 把总结配置写入目标归属：角色卡写卡内，全局默认写用户偏好。
 * 写入口径只有这一处，多个界面共用避免漂移。
 */
internal suspend fun saveSummarySettings(
    target: SummaryTarget,
    settings: ContextSummarySettings,
    characterCardManager: CharacterCardManager,
    userPreferencesManager: UserPreferencesManager
) {
    val cardId = target.cardId
    if (cardId == null) {
        userPreferencesManager.saveGlobalContextSummary(settings)
    } else {
        characterCardManager.updateCharacterCardSummarySettings(cardId, settings)
    }
}

/** 总结设置界面的全部输入态，供自动保存组装完整配置对象。 */
private data class SummarySettingsInputs(
    val enableSummary: Boolean,
    val summaryTokenThresholdInput: String,
    val enableSummaryByMessageCount: Boolean,
    val summaryMessageCountThresholdInput: String,
    val summaryCustomRulesInput: String,
    val dialogueReviewEnabled: Boolean,
    val dialogueReviewTitleInput: String,
    val summarySectionsInput: List<SummarySectionConfig>
)

/**
 * 总结配置自动保存。
 *
 * 历史实现把开关、阈值、分段、规则、对话回顾拆成多个 effect，各自按字段读改写模型配置，
 * 多个 effect 竞态时会用旧快照互相覆盖，用户看到改动丢失。现在统一成一个 effect：
 * 任一输入变化后把全部输入组装成完整的 ContextSummarySettings，一次性写入当前归属目标，
 * 不再有中间态与跨字段覆盖。
 */
@Composable
private fun SummarySettingsAutoSaveEffect(
    target: SummaryTarget?,
    inputsProvider: () -> SummarySettingsInputs,
    useEnglish: Boolean,
    errorSummaryThresholdRange: String,
    errorValidMessageCount: String,
    errorSaveFailed: String,
    onSummaryErrorChange: (String?) -> Unit,
    onSave: suspend (ContextSummarySettings) -> Unit
) {
    val latestTarget by rememberUpdatedState(target)

    LaunchedEffect(target?.cardId) {
        snapshotFlow { inputsProvider() }
            .drop(1)
            .debounce(700)
            .distinctUntilChanged()
            .collectLatest { inputs ->
                val current = latestTarget ?: return@collectLatest
                // 角色卡跟随全局时界面展示的是全局配置，输入全部禁用，不发生写入
                if (!current.editable) return@collectLatest

                val threshold = inputs.summaryTokenThresholdInput.toFloatOrNull()
                val messageCount = inputs.summaryMessageCountThresholdInput.toIntOrNull()
                val baseSettings = current.settings
                when {
                    inputs.enableSummary &&
                        (threshold == null || threshold <= 0f || threshold >= 1f) ->
                        onSummaryErrorChange(errorSummaryThresholdRange)
                    inputs.enableSummary &&
                        inputs.enableSummaryByMessageCount &&
                        (messageCount == null || messageCount <= 0) ->
                        onSummaryErrorChange(errorValidMessageCount)
                    else -> {
                        val settings =
                            ContextSummarySettings(
                                enableSummary = inputs.enableSummary,
                                // 关闭总结时保留已存阈值：输入框此时可能留着未完成的编辑
                                summaryTokenThreshold =
                                    if (inputs.enableSummary) {
                                        threshold ?: baseSettings.summaryTokenThreshold
                                    } else {
                                        baseSettings.summaryTokenThreshold
                                    },
                                enableSummaryByMessageCount = inputs.enableSummaryByMessageCount,
                                summaryMessageCountThreshold =
                                    if (inputs.enableSummaryByMessageCount) {
                                        messageCount ?: baseSettings.summaryMessageCountThreshold
                                    } else {
                                        baseSettings.summaryMessageCountThreshold
                                    },
                                summaryCustomRules = inputs.summaryCustomRulesInput,
                                summarySectionOverrides =
                                    FunctionalPrompts.buildSummarySectionOverrides(
                                        inputs.summarySectionsInput,
                                        useEnglish
                                    ),
                                dialogueReviewEnabled = inputs.dialogueReviewEnabled,
                                dialogueReviewTitle = inputs.dialogueReviewTitleInput.trim()
                            )
                        if (settings == baseSettings) {
                            onSummaryErrorChange(null)
                            return@collectLatest
                        }
                        try {
                            onSave(settings)
                            onSummaryErrorChange(null)
                        } catch (e: Exception) {
                            AppLogger.w("ContextSummarySettings", "保存总结配置失败", e)
                            onSummaryErrorChange(e.message ?: errorSaveFailed)
                        }
                    }
                }
            }
    }
}

@Composable
private fun HistoryRetentionAutoSaveEffects(
    historyInputsProvider: () -> Pair<String, String>,
    savedHistoryValuesProvider: () -> Pair<Int, Int>,
    apiPreferences: ApiPreferences,
    errorSaveFailed: String,
    onHistoryErrorChange: (String?) -> Unit
) {
    val latestSavedHistoryValues by rememberUpdatedState(savedHistoryValuesProvider())

    LaunchedEffect(apiPreferences) {
        snapshotFlow { historyInputsProvider() }
            .drop(1)
            .debounce(700)
            .distinctUntilChanged()
            .collectLatest { (imageTurnsText, mediaTurnsText) ->
                val imageTurns = imageTurnsText.toIntOrNull()
                val mediaTurns = mediaTurnsText.toIntOrNull()
                if (imageTurns == null || imageTurns < 0 || mediaTurns == null || mediaTurns < 0) {
                    return@collectLatest
                }

                val (savedImageTurns, savedMediaTurns) = latestSavedHistoryValues
                if (imageTurns == savedImageTurns && mediaTurns == savedMediaTurns) {
                    onHistoryErrorChange(null)
                    return@collectLatest
                }

                try {
                    apiPreferences.saveMaxImageHistoryUserTurns(imageTurns)
                    apiPreferences.saveMaxMediaHistoryUserTurns(mediaTurns)
                    onHistoryErrorChange(null)
                } catch (e: Exception) {
                    onHistoryErrorChange(e.message ?: errorSaveFailed)
                }
            }
    }
}

@Composable
private fun RenderContextSummaryConfigSections(
    componentBackgroundColor: Color,
    summaryEditable: Boolean,
    enableSummary: Boolean,
    onEnableSummaryChange: (Boolean) -> Unit,
    summaryTokenThresholdInput: String,
    onSummaryTokenThresholdInputChange: (String) -> Unit,
    enableSummaryByMessageCount: Boolean,
    onEnableSummaryByMessageCountChange: (Boolean) -> Unit,
    summaryMessageCountThresholdInput: String,
    onSummaryMessageCountThresholdInputChange: (String) -> Unit,
    summaryCustomRulesInput: String,
    onSummaryCustomRulesInputChange: (String) -> Unit,
    dialogueReviewEnabled: Boolean,
    onDialogueReviewEnabledChange: (Boolean) -> Unit,
    dialogueReviewTitleInput: String,
    onDialogueReviewTitleChange: (String) -> Unit,
    summarySectionsInput: List<SummarySectionConfig>,
    onSummarySectionsInputChange: (List<SummarySectionConfig>) -> Unit,
    summaryTargetSection: @Composable () -> Unit,
    onOpenFullscreenEditor: (String, String, (String) -> Unit) -> Unit,
    summaryError: String?
) {
    SectionTitle(
        text = stringResource(id = R.string.settings_summary_title),
        icon = Icons.Default.Summarize
    )
    // 归属选择器与专属开关放在总结设置标题之下：先决定改哪份配置，再改内容
    summaryTargetSection()
    SettingsSwitchRow(
        title = stringResource(id = R.string.settings_enable_summary),
        subtitle = stringResource(id = R.string.settings_enable_summary_desc),
        checked = enableSummary,
        onCheckedChange = onEnableSummaryChange,
        backgroundColor = componentBackgroundColor,
        enabled = summaryEditable
    )
    SettingsInputField(
        title = stringResource(id = R.string.settings_summary_threshold),
        subtitle = stringResource(id = R.string.settings_summary_threshold_subtitle),
        value = summaryTokenThresholdInput,
        onValueChange = onSummaryTokenThresholdInputChange,
        backgroundColor = componentBackgroundColor,
        enabled = summaryEditable && enableSummary,
        allowDecimal = true,
        keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
    )
    SettingsSwitchRow(
        title = stringResource(id = R.string.settings_enable_summary_by_message_count),
        subtitle = stringResource(id = R.string.settings_enable_summary_by_message_count_desc),
        checked = enableSummaryByMessageCount,
        onCheckedChange = onEnableSummaryByMessageCountChange,
        backgroundColor = componentBackgroundColor,
        enabled = summaryEditable && enableSummary
    )
    SettingsInputField(
        title = stringResource(id = R.string.settings_summary_message_count_threshold),
        subtitle = stringResource(id = R.string.settings_summary_message_count_threshold_subtitle),
        value = summaryMessageCountThresholdInput,
        onValueChange = onSummaryMessageCountThresholdInputChange,
        unitText = stringResource(id = R.string.model_config_unit_items),
        backgroundColor = componentBackgroundColor,
        enabled = summaryEditable && enableSummary && enableSummaryByMessageCount
    )
    summaryError?.let {
        Text(
            text = it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }

    Spacer(modifier = Modifier.size(8.dp))
    val globalRulesTitle = stringResource(id = R.string.settings_summary_custom_rules)
    GlobalSummaryRulesEditor(
        title = globalRulesTitle,
        subtitle = stringResource(id = R.string.settings_summary_custom_rules_desc),
        backgroundColor = componentBackgroundColor,
        enabled = summaryEditable && enableSummary,
        value = summaryCustomRulesInput,
        onValueChange = onSummaryCustomRulesInputChange,
        onOpenFullscreenEditor = onOpenFullscreenEditor
    )
    Spacer(modifier = Modifier.size(12.dp))
    SectionTitle(
        text = stringResource(id = R.string.settings_summary_structure),
        icon = Icons.Default.Summarize
    )
    summarySectionsInput.forEachIndexed { index, section ->
        SummarySectionEditor(
            section = section,
            onSectionChange = { updatedSection ->
                onSummarySectionsInputChange(
                    summarySectionsInput.mapIndexed { currentIndex, currentSection ->
                        if (currentIndex == index) updatedSection else currentSection
                    }
                )
            },
            backgroundColor = componentBackgroundColor,
            enabled = summaryEditable && enableSummary,
            onOpenFullscreenEditor = onOpenFullscreenEditor
        )
    }
    DialogueReviewEditor(
        enabled = dialogueReviewEnabled,
        onEnabledChange = onDialogueReviewEnabledChange,
        title = dialogueReviewTitleInput,
        onTitleChange = onDialogueReviewTitleChange,
        backgroundColor = componentBackgroundColor,
        summaryEnabled = summaryEditable && enableSummary
    )
}

@Composable
private fun GlobalSummaryRulesEditor(
    title: String,
    subtitle: String,
    backgroundColor: Color,
    enabled: Boolean,
    value: String,
    onValueChange: (String) -> Unit,
    onOpenFullscreenEditor: (String, String, (String) -> Unit) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SummaryEditorHeader(
        title = title,
        subtitle = subtitle,
        expanded = expanded,
        onExpandedChange = { expanded = it },
        backgroundColor = backgroundColor,
        enabled = enabled
    )
    AnimatedVisibility(visible = expanded) {
        SettingsMultilineTextField(
            title = title,
            subtitle = subtitle,
            value = value,
            onValueChange = onValueChange,
            backgroundColor = backgroundColor,
            enabled = enabled,
            onOpenFullscreen = {
                onOpenFullscreenEditor(title, value, onValueChange)
            }
        )
    }
    Spacer(modifier = Modifier.size(8.dp))
}

@Composable
private fun SummarySectionEditor(
    section: SummarySectionConfig,
    onSectionChange: (SummarySectionConfig) -> Unit,
    backgroundColor: Color,
    enabled: Boolean,
    onOpenFullscreenEditor: (String, String, (String) -> Unit) -> Unit
) {
    var expanded by rememberSaveable(section.id) { mutableStateOf(false) }
    SummaryConfigEditorHeader(
        title = section.title,
        subtitle = stringResource(id = R.string.settings_summary_section_enabled_desc),
        checked = section.enabled,
        onCheckedChange = { onSectionChange(section.copy(enabled = it)) },
        expanded = expanded,
        onExpandedChange = { expanded = it },
        backgroundColor = backgroundColor,
        enabled = enabled
    )
    AnimatedVisibility(visible = expanded) {
        Column {
            SettingsMultilineTextField(
                title = stringResource(id = R.string.settings_summary_section_title),
                subtitle = stringResource(id = R.string.settings_summary_section_title_desc),
                value = section.title,
                onValueChange = { onSectionChange(section.copy(title = it)) },
                backgroundColor = backgroundColor,
                enabled = enabled && section.enabled,
                singleLine = true,
                minHeight = 40.dp
            )
            SettingsMultilineTextField(
                title = stringResource(id = R.string.settings_summary_section_instruction),
                subtitle = stringResource(id = R.string.settings_summary_section_instruction_desc),
                value = section.instruction,
                onValueChange = { onSectionChange(section.copy(instruction = it)) },
                backgroundColor = backgroundColor,
                enabled = enabled && section.enabled,
                onOpenFullscreen = {
                    onOpenFullscreenEditor(section.title, section.instruction) { value ->
                        onSectionChange(section.copy(instruction = value))
                    }
                }
            )
        }
    }
    Spacer(modifier = Modifier.size(8.dp))
}

@Composable
private fun DialogueReviewEditor(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    title: String,
    onTitleChange: (String) -> Unit,
    backgroundColor: Color,
    summaryEnabled: Boolean
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SummaryConfigEditorHeader(
        title = stringResource(id = R.string.settings_summary_dialogue_review),
        subtitle = stringResource(id = R.string.settings_summary_dialogue_review_desc),
        checked = enabled,
        onCheckedChange = onEnabledChange,
        expanded = expanded,
        onExpandedChange = { expanded = it },
        backgroundColor = backgroundColor,
        enabled = summaryEnabled
    )
    AnimatedVisibility(visible = expanded) {
        SettingsMultilineTextField(
            title = stringResource(id = R.string.settings_summary_dialogue_review_title),
            subtitle = stringResource(id = R.string.settings_summary_dialogue_review_title_desc),
            value = title,
            onValueChange = onTitleChange,
            backgroundColor = backgroundColor,
            enabled = summaryEnabled && enabled,
            singleLine = true,
            minHeight = 40.dp
        )
    }
    Spacer(modifier = Modifier.size(8.dp))
}

@Composable
private fun SummaryConfigEditorHeader(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    backgroundColor: Color,
    enabled: Boolean
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(backgroundColor)
                .alpha(contentAlpha)
                .clickable(enabled = enabled) { onExpandedChange(!expanded) }
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        IconButton(onClick = { onExpandedChange(!expanded) }, enabled = enabled) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) R.string.model_config_collapse else R.string.model_config_expand
                )
            )
        }
    }
}

@Composable
private fun SummaryEditorHeader(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    backgroundColor: Color,
    enabled: Boolean
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(backgroundColor)
                .alpha(contentAlpha)
                .clickable(enabled = enabled) { onExpandedChange(!expanded) }
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { onExpandedChange(!expanded) }, enabled = enabled) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) R.string.model_config_collapse else R.string.model_config_expand
                )
            )
        }
    }
}

@Composable
private fun RenderHistoryRetentionSection(
    componentBackgroundColor: Color,
    maxImageHistoryUserTurnsInput: String,
    onMaxImageHistoryUserTurnsInputChange: (String) -> Unit,
    maxMediaHistoryUserTurnsInput: String,
    onMaxMediaHistoryUserTurnsInputChange: (String) -> Unit,
    onReset: () -> Unit,
    historyError: String?
) {
    SectionTitle(
        text = stringResource(id = R.string.settings_history_retention_title),
        icon = Icons.Default.History
    )
    SettingsInputField(
        title = stringResource(id = R.string.settings_max_image_history_user_turns),
        subtitle = stringResource(id = R.string.settings_max_image_history_user_turns_subtitle),
        value = maxImageHistoryUserTurnsInput,
        onValueChange = onMaxImageHistoryUserTurnsInputChange,
        unitText = stringResource(id = R.string.context_unit_times),
        backgroundColor = componentBackgroundColor
    )

    SettingsInputField(
        title = stringResource(id = R.string.settings_max_media_history_user_turns),
        subtitle = stringResource(id = R.string.settings_max_media_history_user_turns_subtitle),
        value = maxMediaHistoryUserTurnsInput,
        onValueChange = onMaxMediaHistoryUserTurnsInputChange,
        unitText = stringResource(id = R.string.context_unit_times),
        backgroundColor = componentBackgroundColor
    )

    Button(
        onClick = onReset,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
    ) {
        Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(id = R.string.context_reset_all_settings), style = MaterialTheme.typography.bodyLarge)
    }

    historyError?.let {
        Text(
            text = it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun BoxScope.RenderContextSummaryDialogs(
    showSaveSuccessMessage: Boolean,
    onDismissSaveSuccess: () -> Unit
) {
    if (showSaveSuccessMessage) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(1500)
            onDismissSaveSuccess()
        }
        Snackbar(
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            action = {
                TextButton(onClick = onDismissSaveSuccess) {
                    Text(stringResource(id = android.R.string.ok))
                }
            }
        ) {
            Text(stringResource(id = R.string.settings_saved))
        }
    }
}

private fun formatFloatValue(value: Float?): String {
    if (value == null) return ""
    return if (value % 1f == 0f) value.toInt().toString() else value.toString()
}

/**
 * 总结配置归属选择器。
 *
 * 总结配置从模型迁移到角色卡后，本卡片决定当前编辑哪份配置：全局默认或某张角色卡的
 * 专属配置。当前会话使用的角色卡带标记，方便用户直奔常用目标。
 */
@Composable
private fun SummaryTargetSelectorCard(
    selectedCardId: String?,
    characterCards: List<CharacterCard>,
    activeCardId: String?,
    backgroundColor: Color,
    onSelect: (String?) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedName =
        if (selectedCardId == null) {
            stringResource(id = R.string.context_summary_target_global_default)
        } else {
            characterCards.firstOrNull { it.id == selectedCardId }?.name ?: selectedCardId
        }

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(backgroundColor)
                .clickable { showDialog = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(id = R.string.context_summary_target_title),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.size(2.dp))
        Text(
            text = stringResource(id = R.string.context_summary_target_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.size(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = selectedName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = stringResource(id = R.string.model_config_expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.context_summary_target_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    SummaryTargetOptionRow(
                        name = stringResource(id = R.string.context_summary_target_global_default),
                        subtitle =
                            stringResource(id = R.string.context_summary_target_global_default_desc),
                        selected = selectedCardId == null,
                        onClick = {
                            onSelect(null)
                            showDialog = false
                        }
                    )
                    characterCards.forEach { card ->
                        SummaryTargetOptionRow(
                            name = card.name,
                            subtitle =
                                if (card.id == activeCardId) {
                                    stringResource(id = R.string.context_summary_target_active_mark)
                                } else {
                                    null
                                },
                            selected = card.id == selectedCardId,
                            onClick = {
                                onSelect(card.id)
                                showDialog = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryTargetOptionRow(
    name: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SettingsInfoBanner(text: String, backgroundColor: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier.fillMaxWidth()
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(backgroundColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun SectionTitle(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    backgroundColor: Color,
    enabled: Boolean = true
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(backgroundColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .alpha(contentAlpha),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun SettingsInputField(
    title: String,
    subtitle: String,
    value: String,
    onValueChange: (String) -> Unit,
    unitText: String? = null,
    backgroundColor: Color,
    enabled: Boolean = true,
    allowDecimal: Boolean = false,
    keyboardOptions: KeyboardOptions =
        KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
) {
    val focusManager = LocalFocusManager.current
    val contentAlpha = if (enabled) 1f else 0.38f
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(backgroundColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .alpha(contentAlpha),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value,
                onValueChange = { newText ->
                    if (enabled) {
                        onValueChange(if (allowDecimal) filterDecimalInput(newText) else newText.filter { it.isDigit() })
                    }
                },
                enabled = enabled,
                modifier =
                    Modifier.width(if (allowDecimal) 88.dp else 72.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                textStyle =
                    TextStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    ),
                keyboardOptions = keyboardOptions,
                keyboardActions = KeyboardActions(onDone = { if (enabled) focusManager.clearFocus() }),
                singleLine = true
            )
            if (unitText != null) {
                Text(
                    text = unitText,
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

private fun filterDecimalInput(input: String): String {
    var dotSeen = false
    val result = StringBuilder()
    input.forEachIndexed { index, c ->
        when {
            c.isDigit() -> result.append(c)
            c == '.' && !dotSeen -> {
                dotSeen = true
                if (result.isEmpty() && index == 0) result.append("0.") else result.append(c)
            }
        }
    }
    return result.toString()
}

@Composable
private fun SettingsMultilineTextField(
    title: String,
    subtitle: String,
    value: String,
    onValueChange: (String) -> Unit,
    backgroundColor: Color,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    minHeight: Dp = 80.dp,
    onOpenFullscreen: (() -> Unit)? = null
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(backgroundColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .alpha(contentAlpha)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = { newText ->
                if (enabled) {
                    onValueChange(newText)
                }
            },
            enabled = enabled,
            modifier =
                Modifier.fillMaxWidth()
                    .let { if (singleLine) it.heightIn(min = minHeight) else it.heightIn(min = 120.dp) }
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            textStyle =
                TextStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp
                ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 5,
            maxLines = if (singleLine) 1 else 5,
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top
                ) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        contentAlignment =
                            if (singleLine) Alignment.CenterStart else Alignment.TopStart
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        innerTextField()
                    }
                    if (!singleLine && onOpenFullscreen != null) {
                        IconButton(
                            onClick = onOpenFullscreen,
                            enabled = enabled,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = stringResource(id = R.string.chat_fullscreen_input),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        )
    }
}

private data class FullscreenTextEditorRequest(
    val title: String,
    val value: String,
    val onValueChange: (String) -> Unit
)

@Composable
private fun FullscreenSettingsTextEditor(
    request: FullscreenTextEditorRequest,
    onDismiss: () -> Unit
) {
    var editorValue by remember(request.title, request.value) { mutableStateOf(request.value) }
    fun finishEditing() {
        request.onValueChange(editorValue)
        onDismiss()
    }

    Dialog(
        onDismissRequest = { finishEditing() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxSize().imePadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { finishEditing() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(id = R.string.common_close)
                        )
                    }
                    Text(
                        text = request.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = { finishEditing() }) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(id = R.string.save)
                        )
                    }
                }
                HorizontalDivider()
                TextField(
                    value = editorValue,
                    onValueChange = { editorValue = it },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                    textStyle = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
