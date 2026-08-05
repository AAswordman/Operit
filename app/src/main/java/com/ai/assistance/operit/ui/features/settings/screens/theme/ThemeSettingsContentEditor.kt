package com.ai.assistance.operit.ui.features.settings.screens.theme

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.ai.assistance.operit.ui.main.navigation.RegisterRouteBackGuard
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal data class ThemeSettingsShared(
    val context: android.content.Context,
    val editorSession: ThemeEditorSession,
    val displayPreferencesManager: DisplayPreferencesManager,
    val scope: CoroutineScope,
)

private data class ThemeEditorState(
    val target: ActivePrompt,
    val session: ThemeEditorSession,
)

private sealed interface ThemeEditorPendingAction {
    data class SelectTarget(val target: ActivePrompt) : ThemeEditorPendingAction

    data object ActiveTargetChanged : ThemeEditorPendingAction

    data object LeaveScreen : ThemeEditorPendingAction
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
    val activePrompt: ActivePrompt? by activePromptManager.activePromptFlow.collectAsState(initial = null)
    val characterCardIds by characterCardManager.characterCardListFlow.collectAsState(initial = emptyList())
    val characterGroups by characterGroupCardManager.allCharacterGroupCardsFlow.collectAsState(
        initial = emptyList(),
    )
    var characterCards by remember { mutableStateOf(emptyList<CharacterCard>()) }

    LaunchedEffect(characterCardIds) {
        characterCards = characterCardManager.getAllCharacterCards()
    }

    val initialThemeTarget = activePrompt
    if (initialThemeTarget == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
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
    val cardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    )
    var selectedThemeTab by remember { mutableStateOf(ThemeSettingsTab.BASIC) }
    var editorState by remember { mutableStateOf<ThemeEditorState?>(null) }
    var editorReloadToken by remember { mutableStateOf(0) }
    var showSaveSuccessMessage by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<ThemeEditorPendingAction?>(null) }
    var exitContinuation by remember { mutableStateOf<CancellableContinuation<Boolean>?>(null) }
    var isSaving by remember { mutableStateOf(false) }
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
            if (pendingAction == null || pendingAction == ThemeEditorPendingAction.ActiveTargetChanged) {
                pendingAction = ThemeEditorPendingAction.ActiveTargetChanged
            }
            return@LaunchedEffect
        }
        editorState = null
        val snapshot = when (target) {
            is ActivePrompt.CharacterCard ->
                preferencesManager.resolveThemePreferenceSnapshot(characterCardId = target.id)

            is ActivePrompt.CharacterGroup ->
                preferencesManager.resolveThemePreferenceSnapshot(characterGroupId = target.id)
        }
        if (activeThemeTarget == target) {
            editorState = ThemeEditorState(
                target = target,
                session = ThemeEditorSession(preferencesManager, snapshot.values),
            )
        }
    }
    val draftForDirtyState = editorState?.session
    val hasUnsavedChanges =
        if (draftForDirtyState == null) {
            false
        } else {
            val dirty by draftForDirtyState.hasUnsavedChangesFlow.collectAsState(
                initial = draftForDirtyState.hasUnsavedChanges,
            )
            dirty
        }

    val editorTarget = editorState?.target ?: activeThemeTarget
    val selectedCharacterCard = (editorTarget as? ActivePrompt.CharacterCard)
        ?.let { target -> characterCards.firstOrNull { it.id == target.id } }
    val selectedCharacterGroup = (editorTarget as? ActivePrompt.CharacterGroup)
        ?.let { target -> characterGroups.firstOrNull { it.id == target.id } }
    val observedEditorSession = editorState?.session
    val editorValues = if (observedEditorSession == null) {
        null
    } else {
        val values by observedEditorSession.values.collectAsState()
        values
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
                Toast.makeText(
                    context,
                    context.getString(R.string.theme_target_switch_failed),
                    Toast.LENGTH_LONG,
                ).show()
                editorReloadToken += 1
            } finally {
                targetSwitchesInFlight = (targetSwitchesInFlight - 1).coerceAtLeast(0)
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

            null -> Unit
        }
    }

    fun saveCurrentDraft() {
        val state = editorState ?: return
        val draft = state.session
        if (isSaving) return
        val target = state.target
        val savedValues = draft.currentValues
        val resetRequested = draft.isResetRequested
        isSaving = true
        draft.beginSave(savedValues)
        scope.launch {
            try {
                if (resetRequested) {
                    activePromptManager.resetThemeDraft(target, savedValues)
                } else {
                    activePromptManager.commitThemeDraft(target, savedValues)
                }
                draft.markSaved(savedValues)
                showSaveSuccessMessage = true
                finishPendingAction(allowNavigation = true)
            } catch (e: CancellationException) {
                draft.cancelSave()
                throw e
            } catch (e: Exception) {
                draft.cancelSave()
                AppLogger.e("ThemeSettings", "Failed to save theme draft", e)
                Toast.makeText(context, context.getString(R.string.theme_save_failed), Toast.LENGTH_LONG)
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

    Column(modifier = Modifier.fillMaxSize()) {
        ThemeSettingsTargetSelector(
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
            DisposableEffect(draft) {
                onDispose(draft::dispose)
            }
            val shared = ThemeSettingsShared(
                context = context,
                editorSession = draft,
                displayPreferencesManager = displayPreferencesManager,
                scope = scope,
            )

            // External picker callbacks retain this exact draft, even after another target is selected.
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
                        ThemeSettingsChatTab(
                            shared = shared,
                            cardColors = cardColors,
                        )
                    },
                    inputContent = {
                        ThemeSettingsInputTab(
                            shared = shared,
                            cardColors = cardColors,
                        )
                    },
                    interfaceContent = {
                        ThemeSettingsInterfaceTab(
                            shared = shared,
                            cardColors = cardColors,
                        )
                    },
                    footerContent = {
                        ThemeSettingsFooter(
                            showSaveSuccessMessage = showSaveSuccessMessage,
                            onShowSaveSuccessMessageChange = { showSaveSuccessMessage = it },
                            saveEnabled = hasUnsavedChanges && !isSaving,
                            isSaving = isSaving,
                            onSave = ::saveCurrentDraft,
                            onReset = draft::reset,
                        )
                    },
                )
            }
        }
    }

    if (pendingAction != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isSaving) {
                    finishPendingAction(allowNavigation = false)
                }
            },
            title = { Text(stringResource(R.string.theme_unsaved_title)) },
            text = { Text(stringResource(R.string.theme_unsaved_message)) },
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
    var expanded by remember { mutableStateOf(false) }
    val targetTypeLabel = if (selectedTarget is ActivePrompt.CharacterGroup) {
        stringResource(R.string.theme_edit_target_group)
    } else {
        stringResource(R.string.theme_edit_target_character)
    }
    val targetIcon = if (selectedTarget is ActivePrompt.CharacterGroup) {
        Icons.Default.Groups
    } else {
        Icons.Default.Person
    }

    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { expanded = true },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
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
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            val defaultTarget = ActivePrompt.CharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID)
            DropdownMenuItem(
                text = { Text(stringResource(R.string.theme_default_character_card)) },
                leadingIcon = { ThemeSettingsTargetMenuAvatar(defaultTarget) },
                trailingIcon = {
                    if (selectedTarget == defaultTarget) {
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                },
                onClick = {
                    expanded = false
                    onTargetSelected(defaultTarget)
                },
            )
            characterCards
                .filter { it.id != CharacterCardManager.DEFAULT_CHARACTER_CARD_ID }
                .forEach { card ->
                    val target = ActivePrompt.CharacterCard(card.id)
                    DropdownMenuItem(
                        text = { Text(card.name) },
                        leadingIcon = { ThemeSettingsTargetMenuAvatar(target) },
                        trailingIcon = {
                            if (selectedTarget == target) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            expanded = false
                            onTargetSelected(target)
                        },
                    )
                }
            if (characterGroups.isNotEmpty()) {
                HorizontalDivider()
                characterGroups.forEach { group ->
                    val target = ActivePrompt.CharacterGroup(group.id)
                    DropdownMenuItem(
                        text = { Text(group.name) },
                        leadingIcon = { ThemeSettingsTargetMenuAvatar(target) },
                        trailingIcon = {
                            if (selectedTarget == target) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            expanded = false
                            onTargetSelected(target)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeSettingsTargetMenuAvatar(target: ActivePrompt) {
    val context = LocalContext.current
    val userPreferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val avatarUriFlow = remember(target, userPreferencesManager) {
        when (target) {
            is ActivePrompt.CharacterCard ->
                userPreferencesManager.getAiAvatarForCharacterCardFlow(target.id)

            is ActivePrompt.CharacterGroup ->
                userPreferencesManager.getAiAvatarForCharacterGroupFlow(target.id)
        }
    }
    val avatarUri by avatarUriFlow.collectAsState(initial = null)

    Box(
        modifier =
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatarUri.isNullOrBlank()) {
            Image(
                painter = rememberAsyncImagePainter(Uri.parse(avatarUri)),
                contentDescription = stringResource(R.string.character_avatar),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
