package com.ai.assistance.operit.ui.features.management.settings.screens.theme

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.model.CharacterGroupCard
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.common.themetarget.ThemeTargetOption
import com.ai.assistance.operit.ui.common.themetarget.ThemeTargetPickerSheet
import com.ai.assistance.operit.ui.common.oktoast.OKToast
import com.ai.assistance.operit.ui.main.MainActivity
import com.ai.assistance.operit.ui.main.navigation.RegisterRouteBackGuard
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.util.android.LocaleUtils
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

internal data class ThemeSettingsShared(
    val context: android.content.Context,
    val editorSession: ThemeEditorSession,
    val displayPreferencesManager: DisplayPreferencesManager,
    val scope: CoroutineScope,
    val projectKey: String,
    val targetName: String,
)

private data class ThemeEditorState(val target: ActivePrompt, val session: ThemeEditorSession)

private sealed interface ThemeEditorPendingAction {
    data class SelectTarget(val target: ActivePrompt) : ThemeEditorPendingAction

    data object ActiveTargetChanged : ThemeEditorPendingAction

    data object LeaveScreen : ThemeEditorPendingAction

    data class ChangeLanguage(val code: String) : ThemeEditorPendingAction
}

@OptIn(ExperimentalMaterial3Api::class)
@NonRestartableComposable
@Composable
internal fun ThemeSettingsContent() {
    val context = LocalContext.current
    val preferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val displayPreferencesManager = remember { DisplayPreferencesManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val characterGroupCardManager = remember { CharacterGroupCardManager.getInstance(context) }
    val activePromptManager = remember { ActivePromptManager.getInstance(context) }
    val activePrompt: ActivePrompt? by
        activePromptManager.activePromptFlow.collectAsState(initial = null)
    val characterCardIds by
        characterCardManager.characterCardListFlow.collectAsState(initial = emptyList())
    val characterGroups by
        characterGroupCardManager.allCharacterGroupCardsFlow.collectAsState(initial = emptyList())
    var characterCards by remember { mutableStateOf(emptyList<CharacterCard>()) }

    LaunchedEffect(characterCardIds) {
        characterCards = characterCardManager.getAllCharacterCards()
    }

    val initialThemeTarget = activePrompt
    if (initialThemeTarget == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        ThemeSettingsContentEditor(
            preferencesManager = preferencesManager,
            displayPreferencesManager = displayPreferencesManager,
            scope = scope,
            activePromptManager = activePromptManager,
            activeThemeTarget = initialThemeTarget,
            characterCards = characterCards,
            characterGroups = characterGroups,
        )
    }
}

@Composable
internal fun ThemeSettingsContentEditor(
    preferencesManager: UserPreferencesManager,
    displayPreferencesManager: DisplayPreferencesManager,
    scope: CoroutineScope,
    activePromptManager: ActivePromptManager,
    activeThemeTarget: ActivePrompt,
    characterCards: List<CharacterCard>,
    characterGroups: List<CharacterGroupCard>,
) {
    val context = LocalContext.current
    val cardColors =
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    var showFullPreview by remember { mutableStateOf(false) }
    var selectedThemeTab by remember { mutableStateOf(ThemeSettingsTab.BASIC) }
    var editorState by remember { mutableStateOf<ThemeEditorState?>(null) }
    var editorReloadToken by remember { mutableStateOf(0) }
    var showSaveSuccessMessage by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<ThemeEditorPendingAction?>(null) }
    var exitContinuation by remember { mutableStateOf<CancellableContinuation<Boolean>?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var isChangingLanguage by remember { mutableStateOf(false) }
    var showApplyToAllConfirm by remember { mutableStateOf(false) }
    var targetSwitchesInFlight by remember { mutableStateOf(0) }
    val scrollState = androidx.compose.foundation.rememberScrollState()

    LaunchedEffect(activeThemeTarget, editorReloadToken) {
        val target = activeThemeTarget
        val currentState = editorState
        if (currentState?.target == target) {
            if (pendingAction == ThemeEditorPendingAction.ActiveTargetChanged) {
                pendingAction = null
            }
            return@LaunchedEffect
        }
        if (currentState != null && currentState.session.hasUnsavedChanges) {
            if (
                pendingAction == null ||
                    pendingAction == ThemeEditorPendingAction.ActiveTargetChanged
            ) {
                pendingAction = ThemeEditorPendingAction.ActiveTargetChanged
            }
            return@LaunchedEffect
        }
        editorState = null
        val snapshot =
            when (target) {
                is ActivePrompt.CharacterCard ->
                    preferencesManager.resolveThemePreferenceSnapshot(characterCardId = target.id)

                is ActivePrompt.CharacterGroup ->
                    preferencesManager.resolveThemePreferenceSnapshot(characterGroupId = target.id)
            }
        if (activeThemeTarget == target) {
            editorState =
                ThemeEditorState(
                    target = target,
                    session =
                        ThemeEditorSession(
                            preferencesManager,
                            snapshot.values,
                            displayPreferencesManager.globalUserName.first().orEmpty(),
                            preferencesManager.chatLayoutSettings.first(),
                        ),
                )
        }
    }
    val draftForDirtyState = editorState?.session
    val hasUnsavedChanges =
        if (draftForDirtyState == null) {
            false
        } else {
            val dirty by
                draftForDirtyState.hasUnsavedChangesFlow.collectAsState(
                    initial = draftForDirtyState.hasUnsavedChanges
                )
            dirty
        }

    val editorTarget = editorState?.target ?: activeThemeTarget
    val selectedCharacterCard =
        (editorTarget as? ActivePrompt.CharacterCard)?.let { target ->
            characterCards.firstOrNull { it.id == target.id }
        }
    val selectedCharacterGroup =
        (editorTarget as? ActivePrompt.CharacterGroup)?.let { target ->
            characterGroups.firstOrNull { it.id == target.id }
        }
    val observedEditorSession = editorState?.session
    val editorValues =
        if (observedEditorSession == null) {
            null
        } else {
            val values by observedEditorSession.values.collectAsState()
            values
        }
    val observedLayout = if (observedEditorSession == null) null else {
        val value by observedEditorSession.layout.collectAsState()
        value
    }
    val selectedAvatarUri = editorValues?.string("custom_ai_avatar_uri")
    val selectedThemeTargetName =
        selectedCharacterGroup?.name
            ?: selectedCharacterCard?.name
            ?: context.getString(R.string.theme_default_character_card)

    fun activateTarget(target: ActivePrompt) {
        if (target == activeThemeTarget && targetSwitchesInFlight == 0) {
            if (editorState?.target != target) {
                editorState = null
                editorReloadToken += 1
            }
            return
        }
        targetSwitchesInFlight += 1
        scope.launch {
            try {
                activePromptManager.setActivePrompt(target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("ThemeSettings", "Failed to activate theme target", e)
                OKToast.error(
                        context,
                        context.getString(R.string.theme_target_switch_failed),
                        OKToast.LENGTH_LONG,
                    )
                    .show()
                editorReloadToken += 1
            } finally {
                targetSwitchesInFlight = (targetSwitchesInFlight - 1).coerceAtLeast(0)
            }
        }
    }

    fun changeLanguage(code: String) {
        if (isChangingLanguage) return
        isChangingLanguage = true
        scope.launch {
            try {
                // 先等待全局语言落盘；重启和系统语言应用都基于同一份已保存的配置。
                withContext(Dispatchers.IO) { preferencesManager.saveAppLanguage(code) }
                LocaleUtils.applyAppLanguage(context, code)
                val intent =
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                context.startActivity(intent)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                AppLogger.e("ThemeSettings", "切换应用语言失败", cause)
                OKToast.error(context, context.getString(R.string.language_change_failed)).show()
            } finally {
                isChangingLanguage = false
            }
        }
    }
    fun finishPendingAction(allowNavigation: Boolean) {
        val action = pendingAction
        pendingAction = null
        when (action) {
            is ThemeEditorPendingAction.SelectTarget -> {
                if (allowNavigation) {
                    activateTarget(action.target)
                } else if (editorState?.target != activeThemeTarget) {
                    editorReloadToken += 1
                }
            }

            ThemeEditorPendingAction.ActiveTargetChanged -> {
                if (allowNavigation) {
                    editorState = null
                    editorReloadToken += 1
                } else {
                    editorState?.target?.let(::activateTarget)
                }
            }

            ThemeEditorPendingAction.LeaveScreen -> {
                val continuation = exitContinuation
                exitContinuation = null
                continuation?.resume(allowNavigation)
                if (!allowNavigation && editorState?.target != activeThemeTarget) {
                    editorReloadToken += 1
                }
            }

            is ThemeEditorPendingAction.ChangeLanguage -> {
                if (allowNavigation) changeLanguage(action.code)
            }
            null -> Unit
        }
    }
    fun saveCurrentDraft() {
        val state = editorState ?: return
        val draft = state.session
        if (isSaving) return
        val target = state.target
        val savedValues = draft.currentValues
        val savedGlobalUserName = draft.currentGlobalUserName
        val savedLayout = draft.currentLayout
        val saveLayout = draft.hasLayoutChanges
        val saveTheme = draft.hasThemeChanges
        val saveGlobalUserName = draft.hasGlobalUserNameChanges
        val resetRequested = draft.isResetRequested
        isSaving = true
        draft.beginSave(savedValues)
        scope.launch {
            try {
                // 只改全局名字时不创建或覆盖角色主题，统一保存完成后再清除草稿的变更标记。
                if (saveTheme) {
                    if (resetRequested) {
                        activePromptManager.resetThemeDraft(target, savedValues)
                    } else {
                        activePromptManager.commitThemeDraft(target, savedValues)
                    }
                }
                if (saveGlobalUserName) {
                    displayPreferencesManager.saveDisplaySettings(
                        globalUserName = savedGlobalUserName
                    )
                }
                if (saveLayout) preferencesManager.saveChatLayoutSettings(savedLayout)
                draft.markSaved(savedValues, savedGlobalUserName, savedLayout)
                showSaveSuccessMessage = true
                finishPendingAction(allowNavigation = true)
            } catch (e: CancellationException) {
                draft.cancelSave()
                throw e
            } catch (e: Exception) {
                draft.cancelSave()
                AppLogger.e("ThemeSettings", "Failed to save theme draft", e)
                OKToast.error(
                        context,
                        context.getString(R.string.theme_save_failed),
                        OKToast.LENGTH_LONG,
                    )
                    .show()
            } finally {
                isSaving = false
            }
        }
    }

    fun applyCurrentThemeToAllCharacterCards() {
        val state = editorState ?: return
        if (state.target !is ActivePrompt.CharacterCard) return
        val draft = state.session
        if (isSaving || pendingAction != null) return
        val savedValues = draft.currentValues
        val savedGlobalUserName = draft.currentGlobalUserName
        val savedLayout = draft.currentLayout
        val saveLayout = draft.hasLayoutChanges
        val saveGlobalUserName = draft.hasGlobalUserNameChanges
        val resetRequested = draft.isResetRequested
        isSaving = true
        showApplyToAllConfirm = false
        draft.beginSave(savedValues)
        scope.launch {
            try {
                if (resetRequested) {
                    activePromptManager.resetThemeDraft(state.target, savedValues)
                } else {
                    activePromptManager.commitThemeDraft(state.target, savedValues)
                }
                activePromptManager.applyVisualThemeToAllCharacterCards(savedValues)
                // 全局名字只保存一次，不加入传播给各角色卡的视觉主题字段。
                if (saveGlobalUserName) {
                    displayPreferencesManager.saveDisplaySettings(
                        globalUserName = savedGlobalUserName
                    )
                }
                if (saveLayout) preferencesManager.saveChatLayoutSettings(savedLayout)
                draft.markSaved(savedValues, savedGlobalUserName, savedLayout)
                showSaveSuccessMessage = true
                OKToast.success(
                        context,
                        context.getString(R.string.theme_applied_to_all_character_cards),
                        OKToast.LENGTH_SHORT,
                    )
                    .show()
            } catch (e: CancellationException) {
                draft.cancelSave()
                throw e
            } catch (e: Exception) {
                draft.cancelSave()
                AppLogger.e("ThemeSettings", "Failed to apply theme to all character cards", e)
                OKToast.error(
                        context,
                        context.getString(R.string.theme_apply_to_all_character_cards_failed),
                        OKToast.LENGTH_LONG,
                    )
                    .show()
            } finally {
                isSaving = false
            }
        }
    }

    RegisterRouteBackGuard {
        if (pendingAction != null) {
            return@RegisterRouteBackGuard false
        }
        val draft = editorState?.session
        if (draft == null || !hasUnsavedChanges) {
            return@RegisterRouteBackGuard true
        }
        suspendCancellableCoroutine<Boolean> { continuation ->
            pendingAction = ThemeEditorPendingAction.LeaveScreen
            exitContinuation = continuation
            continuation.invokeOnCancellation {
                if (exitContinuation === continuation) {
                    exitContinuation = null
                    pendingAction = null
                }
            }
        }
    }

    // 角色选择和页签使用连续底色，避免背景素材把同一编辑区域分割开。
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        if (selectedThemeTab == ThemeSettingsTab.LAYOUT) {
            com.ai.assistance.operit.ui.features.management.settings.screens.theme.layout.ThemeLayoutScopeCard()
        } else ThemeSettingsTargetSelector(
            selectedTarget = editorTarget,
            selectedLabel = selectedThemeTargetName,
            selectedAvatarUri = selectedAvatarUri,
            characterCards = characterCards,
            characterGroups = characterGroups,
            enabled =
                editorState != null &&
                    pendingAction == null &&
                    !isSaving &&
                    targetSwitchesInFlight == 0,
            onTargetSelected = { target ->
                val draft = editorState?.session
                if (pendingAction == null && target != editorTarget) {
                    if (draft != null && hasUnsavedChanges) {
                        pendingAction = ThemeEditorPendingAction.SelectTarget(target)
                    } else {
                        activateTarget(target)
                    }
                }
            },
        )

        val draft = editorState?.session
        if (draft == null) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            DisposableEffect(draft) { onDispose(draft::dispose) }
            val shared =
                ThemeSettingsShared(
                    context = context,
                    editorSession = draft,
                    displayPreferencesManager = displayPreferencesManager,
                    scope = scope,
                    projectKey = when (val target = editorTarget) {
                        is ActivePrompt.CharacterCard -> "card:${target.id}"
                        is ActivePrompt.CharacterGroup -> "group:${target.id}"
                    },
                    targetName = selectedThemeTargetName,
                )

            if (showFullPreview) {
                com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench
                    .ThemeWorkbenchDialog(
                        stringResource(R.string.theme_workbench_full_preview),
                        { showFullPreview = false },
                    ) {
                        com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench
                            .ThemeWorkbenchPreview(draft, full = true, layoutPreview = selectedThemeTab == ThemeSettingsTab.LAYOUT)
                    }
            }
            // 外部文件选择回调保留当前草稿实例，防止切换角色后写入其他角色。
            key(draft) {
                ThemeSettingsTabbedContent(
                    selectedTab = selectedThemeTab,
                    onSelectedTabChange = { selectedThemeTab = it },
                    scrollState = scrollState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    basicContent = {
                        ThemeSettingsBasicTab(
                            shared = shared,
                            cardColors = cardColors,
                            languageChangeEnabled =
                                !isSaving && !isChangingLanguage && pendingAction == null,
                            onLanguageSelected = { code ->
                                if (hasUnsavedChanges)
                                    pendingAction = ThemeEditorPendingAction.ChangeLanguage(code)
                                else changeLanguage(code)
                            },
                        )
                    },
                    backgroundContent = {
                        ThemeSettingsBackgroundTab(
                            shared = shared,
                            cardColors = cardColors,
                            scrollState = scrollState,
                        )
                    },
                    chatContent = {
                        ThemeSettingsChatTab(shared = shared, cardColors = cardColors)
                    },
                    interfaceContent = {
                        ThemeSettingsInterfaceTab(shared = shared, cardColors = cardColors)
                    },
                    layoutContent = {
                        com.ai.assistance.operit.ui.features.management.settings.screens.theme.layout.ThemeLayoutTab(draft)
                    },
                    previewContent = {
                        if (!showFullPreview)
                            com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench
                                .ThemeWorkbenchPreview(draft, layoutPreview = selectedThemeTab == ThemeSettingsTab.LAYOUT)
                    },
                    footerContent = {
                        ThemeSettingsFooter(
                            targetName = selectedThemeTargetName,
                            hasUnsavedChanges = hasUnsavedChanges,
                            globalLayout = selectedThemeTab == ThemeSettingsTab.LAYOUT,
                            hasLayoutChanges = observedLayout != null && draft.hasLayoutChanges,
                            onDiscard = draft::discard,
                            onPreview = { showFullPreview = true },
                            showSaveSuccessMessage = showSaveSuccessMessage,
                            onShowSaveSuccessMessageChange = { showSaveSuccessMessage = it },
                            saveEnabled = hasUnsavedChanges && !isSaving,
                            isSaving = isSaving,
                            showApplyToAll = editorTarget is ActivePrompt.CharacterCard,
                            applyToAllEnabled =
                                editorState != null && !isSaving && pendingAction == null,
                            onSave = ::saveCurrentDraft,
                            onReset = draft::reset,
                            onApplyToAllCharacterCards = { showApplyToAllConfirm = true },
                        )
                    },
                )
            }
        }
    }

    if (isChangingLanguage) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(stringResource(R.string.language_changing))
                }
            },
        )
    }
    if (showApplyToAllConfirm) {
        AlertDialog(
            onDismissRequest = {
                if (!isSaving) {
                    showApplyToAllConfirm = false
                }
            },
            title = { Text(stringResource(R.string.theme_apply_to_all_character_cards_title)) },
            text = { Text(stringResource(R.string.theme_apply_to_all_character_cards_message)) },
            confirmButton = {
                TextButton(onClick = ::applyCurrentThemeToAllCharacterCards, enabled = !isSaving) {
                    Text(stringResource(R.string.theme_apply_to_all_character_cards_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showApplyToAllConfirm = false }, enabled = !isSaving) {
                    Text(stringResource(R.string.cancel_action))
                }
            },
        )
    }

    if (pendingAction != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isSaving) {
                    finishPendingAction(allowNavigation = false)
                }
            },
            title = { Text(stringResource(R.string.theme_unsaved_title)) },
            text = { Text(stringResource(R.string.theme_layout_unsaved_message)) },
            confirmButton = {
                TextButton(onClick = ::saveCurrentDraft, enabled = !isSaving) {
                    Text(stringResource(R.string.theme_save_and_continue))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            editorState?.session?.discard()
                            finishPendingAction(allowNavigation = true)
                        },
                        enabled = !isSaving,
                    ) {
                        Text(stringResource(R.string.theme_discard_and_continue))
                    }
                    TextButton(
                        onClick = { finishPendingAction(allowNavigation = false) },
                        enabled = !isSaving,
                    ) {
                        Text(stringResource(R.string.cancel_action))
                    }
                }
            },
        )
    }
}

@Composable
private fun ThemeSettingsTargetSelector(
    selectedTarget: ActivePrompt,
    selectedLabel: String,
    selectedAvatarUri: String?,
    characterCards: List<CharacterCard>,
    characterGroups: List<CharacterGroupCard>,
    enabled: Boolean,
    onTargetSelected: (ActivePrompt) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val targetTypeLabel =
        if (selectedTarget is ActivePrompt.CharacterGroup) {
            stringResource(R.string.theme_edit_target_group)
        } else {
            stringResource(R.string.theme_edit_target_character)
        }
    val targetIcon =
        if (selectedTarget is ActivePrompt.CharacterGroup) {
            Icons.Default.Groups
        } else {
            Icons.Default.Person
        }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable(enabled = enabled) { expanded = true }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier.size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (selectedAvatarUri != null) {
                    Image(
                        painter = rememberAsyncImagePainter(Uri.parse(selectedAvatarUri)),
                        contentDescription = stringResource(R.string.character_avatar),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = targetIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selectedLabel,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = targetTypeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = stringResource(R.string.theme_select_target),
                tint =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
            )
        }
    }
    if (expanded) {
        val defaultLabel = stringResource(R.string.theme_default_character_card)
        val options = remember(characterCards, characterGroups, defaultLabel) {
            buildList {
                add(ThemeTargetOption(
                    ActivePrompt.CharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID),
                    defaultLabel,
                ))
                characterCards.filter { it.id != CharacterCardManager.DEFAULT_CHARACTER_CARD_ID }
                    .forEach { add(ThemeTargetOption(ActivePrompt.CharacterCard(it.id), it.name)) }
                characterGroups.forEach { add(ThemeTargetOption(ActivePrompt.CharacterGroup(it.id), it.name)) }
            }
        }
        ThemeTargetPickerSheet(
            targets = options,
            selectedTarget = selectedTarget,
            enabled = enabled,
            onDismissRequest = { expanded = false },
            onSelect = { target ->
                expanded = false
                onTargetSelected(target)
            },
        )
    }
}
