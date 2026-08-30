package com.ai.assistance.operit.ui.features.settings.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.preferences.ThemePreferenceSnapshot
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.ThemeStyleInstancePreferences
import com.ai.assistance.operit.data.preferences.ThemeStyleInstanceRecordV1
import com.ai.assistance.operit.data.preferences.themeStyleInstanceKey
import com.ai.assistance.operit.ui.features.settings.components.ColorPickerDialog
import com.ai.assistance.operit.ui.theme.NativeThemeHostSurface
import com.ai.assistance.operit.ui.theme.NativeThemeOffscreenHost
import com.ai.assistance.operit.ui.theme.resolveNativeThemeForDetachedComposeHost
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleLayerV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleShapeSpecV1
import com.ai.assistance.operit.ui.theme.style.NativeThemeStyleLinkResultV1
import com.ai.assistance.operit.ui.theme.style.compose.NativeThemeNativeV1StyleCompilerV1
import com.ai.assistance.operit.ui.theme.style.compose.NativeThemeStatStylePlanV1
import com.ai.assistance.operit.ui.theme.style.compose.NativeThemeStatStyleInstanceEditorV1
import com.ai.assistance.operit.ui.theme.style.compose.LocalNativeThemeStatStylePlanV1
import com.ai.assistance.operit.ui.theme.style.compose.borderColor
import com.ai.assistance.operit.ui.theme.style.compose.borderWidth
import com.ai.assistance.operit.ui.theme.style.compose.cornerRadiusDp
import com.ai.assistance.operit.ui.theme.style.compose.iconContainerColor
import com.ai.assistance.operit.ui.theme.style.compose.toComposeColorV1
import com.ai.assistance.operit.ui.theme.renderer.data.NativeThemeStatV1
import com.ai.assistance.operit.ui.main.navigation.RegisterRouteBackGuard
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import kotlin.coroutines.resume

private enum class ThemeStudioColorFieldV1 {
    SURFACE,
    VALUE,
    LABEL,
    BORDER,
    ICON_CONTAINER,
    ICON,
}

private sealed interface ThemeStudioRecordLoadStateV1 {
    data object Loading : ThemeStudioRecordLoadStateV1

    data class Loaded(
        val records: Map<String, ThemeStyleInstanceRecordV1>,
    ) : ThemeStudioRecordLoadStateV1
}

private sealed interface ThemeStudioThemeLoadStateV1 {
    data object Loading : ThemeStudioThemeLoadStateV1

    data class Loaded(
        val snapshot: ThemePreferenceSnapshot,
    ) : ThemeStudioThemeLoadStateV1
}

private val themeStudioTargetSaver =
    Saver<ActivePrompt, String>(
        save = { target ->
            when (target) {
                is ActivePrompt.CharacterCard -> "card:${target.id}"
                is ActivePrompt.CharacterGroup -> "group:${target.id}"
            }
        },
        restore = { encodedTarget ->
            when {
                encodedTarget.startsWith("card:") ->
                    ActivePrompt.CharacterCard(encodedTarget.removePrefix("card:"))

                encodedTarget.startsWith("group:") ->
                    ActivePrompt.CharacterGroup(encodedTarget.removePrefix("group:"))

                else -> error("Unsupported saved Theme Studio target: $encodedTarget")
            }
        },
    )

@Composable
fun ThemeStudioScreen() {
    val context = LocalContext.current
    val activePromptManager = remember(context) { ActivePromptManager.getInstance(context) }
    val activeTarget by activePromptManager.activePromptFlow.collectAsState(initial = null)
    val resolvedActiveTarget = activeTarget

    if (resolvedActiveTarget == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    } else {
        ThemeStudioContent(initialTarget = resolvedActiveTarget)
    }
}

@Composable
private fun ThemeStudioContent(initialTarget: ActivePrompt) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val target by rememberSaveable(stateSaver = themeStudioTargetSaver) {
        mutableStateOf(initialTarget)
    }
    val instancePreferences = remember(context) { ThemeStyleInstancePreferences.getInstance(context) }
    val activePromptManager = remember(context) { ActivePromptManager.getInstance(context) }
    val userPreferencesManager = remember(context) { UserPreferencesManager.getInstance(context) }
    val targetThemeSnapshotFlow =
        remember(target, userPreferencesManager) {
            when (target) {
                is ActivePrompt.CharacterCard ->
                    userPreferencesManager.observeThemePreferenceSnapshot(characterCardId = target.id)

                is ActivePrompt.CharacterGroup ->
                    userPreferencesManager.observeThemePreferenceSnapshot(characterGroupId = target.id)
            }
        }
    val targetThemeLoadFlow =
        remember(targetThemeSnapshotFlow) {
            targetThemeSnapshotFlow.map { snapshot -> ThemeStudioThemeLoadStateV1.Loaded(snapshot) }
        }
    val targetThemeLoadState by targetThemeLoadFlow.collectAsState(initial = ThemeStudioThemeLoadStateV1.Loading)
    val targetThemeSnapshot =
        when (val state = targetThemeLoadState) {
            ThemeStudioThemeLoadStateV1.Loading -> target.defaultThemeSnapshot()
            is ThemeStudioThemeLoadStateV1.Loaded -> state.snapshot
        }
    val themeLoaded = targetThemeLoadState is ThemeStudioThemeLoadStateV1.Loaded
    val recordLoadFlow =
        remember(instancePreferences) {
            instancePreferences.recordsFlow.map { records -> ThemeStudioRecordLoadStateV1.Loaded(records) }
        }
    val recordLoadState by recordLoadFlow.collectAsState(initial = ThemeStudioRecordLoadStateV1.Loading)
    val storedRecord =
        when (val state = recordLoadState) {
            ThemeStudioRecordLoadStateV1.Loading -> null
            is ThemeStudioRecordLoadStateV1.Loaded -> state.records[target.themeStyleInstanceKey()]
        }
    val recordsLoaded = recordLoadState is ThemeStudioRecordLoadStateV1.Loaded
    val systemDarkTheme = isSystemInDarkTheme()
    val previewSnapshot =
        remember(targetThemeSnapshot.values) {
            ThemePreferenceSnapshot(
                source = "theme_studio_preview",
                values = targetThemeSnapshot.values,
            )
        }
    val previewResolvedTheme =
        remember(context, previewSnapshot, systemDarkTheme) {
            resolveNativeThemeForDetachedComposeHost(
                context = context,
                snapshot = previewSnapshot,
                hostSurface = NativeThemeHostSurface.EDITOR_PREVIEW,
                systemDarkTheme = systemDarkTheme,
            )
        }
    var draftLayer by remember(target) {
        mutableStateOf(ThemeStyleInstanceRecordV1.empty().instanceLayer)
    }
    var persistedLayer by remember(target) {
        mutableStateOf(ThemeStyleInstanceRecordV1.empty().instanceLayer)
    }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf(false) }
    var resetRequested by remember(target) { mutableStateOf(false) }
    var colorField by remember { mutableStateOf<ThemeStudioColorFieldV1?>(null) }
    var leaveContinuation by remember { mutableStateOf<CancellableContinuation<Boolean>?>(null) }
    var showLeaveConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(saving) {
        if (saving) colorField = null
    }

    LaunchedEffect(target, recordLoadState) {
        val state = recordLoadState
        if (state is ThemeStudioRecordLoadStateV1.Loaded && draftLayer == persistedLayer) {
            val layer = state.records[target.themeStyleInstanceKey()]?.instanceLayer
                ?: ThemeStyleInstanceRecordV1.empty().instanceLayer
            persistedLayer = layer
            draftLayer = layer
            resetRequested = false
            saveError = false
        }
    }

    val linkResult =
        remember(previewResolvedTheme, draftLayer) {
            NativeThemeNativeV1StyleCompilerV1.linkStat(previewResolvedTheme, draftLayer)
        }
    val previewPlan =
        when (linkResult) {
            is NativeThemeStyleLinkResultV1.Linked ->
                NativeThemeNativeV1StyleCompilerV1.resolveStatForEditorPreview(
                    linked = linkResult,
                    darkTheme = previewResolvedTheme.darkTheme,
                )

            is NativeThemeStyleLinkResultV1.Rejected -> null
        }
    val hasUnsavedChanges = draftLayer != persistedLayer || resetRequested

    fun updateLayer(transform: (NativeThemeStyleLayerV1) -> NativeThemeStyleLayerV1) {
        draftLayer = transform(draftLayer)
        resetRequested = false
        saveError = false
    }

    fun completeLeave(allowNavigation: Boolean) {
        val continuation = leaveContinuation
        leaveContinuation = null
        showLeaveConfirmation = false
        continuation?.resume(allowNavigation)
    }

    fun saveDraft(onSaved: (() -> Unit)? = null) {
        if (saving || !recordsLoaded || !themeLoaded || previewPlan == null) return
        val savedTarget = target
        val savedLayer = draftLayer
        val shouldClearInstance = resetRequested
        saving = true
        scope.launch {
            try {
                withContext(NonCancellable) {
                    activePromptManager.runThemeTransitionForExistingTarget(savedTarget) {
                        if (shouldClearInstance) {
                            instancePreferences.clear(savedTarget)
                        } else {
                            instancePreferences.replace(
                                target = savedTarget,
                                record = ThemeStyleInstanceRecordV1(instanceLayer = savedLayer),
                            )
                        }
                    }
                }
                if (target == savedTarget) {
                    val persisted =
                        if (shouldClearInstance) {
                            ThemeStyleInstanceRecordV1.empty().instanceLayer
                        } else {
                            savedLayer
                        }
                    persistedLayer = persisted
                    draftLayer = persisted
                    resetRequested = false
                }
                onSaved?.invoke()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                saveError = true
                AppLogger.e("ThemeStudio", "Failed to save theme style instance", error)
                Toast.makeText(context, context.getString(R.string.theme_studio_save_failed), Toast.LENGTH_LONG)
                    .show()
            } finally {
                saving = false
            }
        }
    }

    fun resetInstance() {
        if (saving || !recordsLoaded || !themeLoaded) return
        draftLayer = ThemeStyleInstanceRecordV1.empty().instanceLayer
        resetRequested = true
        saveError = false
    }

    RegisterRouteBackGuard {
        if (saving) return@RegisterRouteBackGuard false
        if (!hasUnsavedChanges) return@RegisterRouteBackGuard true
        suspendCancellableCoroutine<Boolean> { continuation ->
            leaveContinuation = continuation
            showLeaveConfirmation = true
            continuation.invokeOnCancellation {
                if (leaveContinuation === continuation) {
                    leaveContinuation = null
                    showLeaveConfirmation = false
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        ThemeStudioHeader(target = target)
        HorizontalDivider()
        if (!recordsLoaded || !themeLoaded) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (previewPlan == null) {
            ThemeStudioLinkIssue(linkResult)
        } else {
            ThemeStudioPreview(
                previewSnapshot = previewSnapshot,
                previewResolvedTheme = previewResolvedTheme,
                previewPlan = previewPlan,
            )
            HorizontalDivider()
            ThemeStudioStatControls(
                plan = previewPlan,
                darkTheme = previewResolvedTheme.darkTheme,
                enabled = !saving,
                defaultBorderColor = previewResolvedTheme.contentColorScheme.outline,
                defaultIconContainerColor = previewResolvedTheme.contentColorScheme.primaryContainer,
                defaultIconColor = previewResolvedTheme.contentColorScheme.onPrimaryContainer,
                onSurfaceColorRequested = { colorField = ThemeStudioColorFieldV1.SURFACE },
                onValueColorRequested = { colorField = ThemeStudioColorFieldV1.VALUE },
                onLabelColorRequested = { colorField = ThemeStudioColorFieldV1.LABEL },
                onBorderEnabledChange = { enabled ->
                    if (enabled) {
                        updateLayer {
                            NativeThemeStatStyleInstanceEditorV1.setBorder(
                                layer = it,
                                color = defaultBorderColor,
                                widthDp = 1f,
                            )
                        }
                    } else {
                        updateLayer(NativeThemeStatStyleInstanceEditorV1::clearBorder)
                    }
                },
                onBorderColorRequested = { colorField = ThemeStudioColorFieldV1.BORDER },
                onBorderWidthChange = { width ->
                    updateLayer {
                        NativeThemeStatStyleInstanceEditorV1.setBorder(
                            layer = it,
                            color = plan.borderColor(previewResolvedTheme.darkTheme) ?: defaultBorderColor,
                            widthDp = width,
                        )
                    }
                },
                onRoundedShape = { radius ->
                    updateLayer { NativeThemeStatStyleInstanceEditorV1.setRoundedCorners(it, radius) }
                },
                onCapsuleShape = {
                    updateLayer(NativeThemeStatStyleInstanceEditorV1::setCapsule)
                },
                onOpacityChange = { opacity ->
                    updateLayer { NativeThemeStatStyleInstanceEditorV1.setOpacity(it, opacity) }
                },
                onPaddingChange = { padding ->
                    updateLayer { NativeThemeStatStyleInstanceEditorV1.setContentPadding(it, padding) }
                },
                onIconContainerEnabledChange = { enabled ->
                    if (enabled) {
                        updateLayer {
                            NativeThemeStatStyleInstanceEditorV1.setIconContainer(
                                layer = it,
                                containerColor = defaultIconContainerColor,
                                iconColor = defaultIconColor,
                            )
                        }
                    } else {
                        updateLayer(NativeThemeStatStyleInstanceEditorV1::clearIconContainer)
                    }
                },
                onIconContainerColorRequested = { colorField = ThemeStudioColorFieldV1.ICON_CONTAINER },
                onIconColorRequested = { colorField = ThemeStudioColorFieldV1.ICON },
            )
        }
        HorizontalDivider()
        ThemeStudioCommands(
            saveEnabled = recordsLoaded && themeLoaded && hasUnsavedChanges && previewPlan != null && !saving,
            saving = saving,
            resetEnabled = recordsLoaded && themeLoaded && !saving && (hasUnsavedChanges || storedRecord != null),
            onSave = ::saveDraft,
            onReset = ::resetInstance,
        )
        if (saveError) {
            Text(
                text = stringResource(R.string.theme_studio_save_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        Spacer(modifier = Modifier.heightIn(min = 20.dp))
    }

    val activeColor =
        previewPlan?.let { plan ->
            when (colorField) {
                ThemeStudioColorFieldV1.SURFACE -> plan.surfaceColor
                ThemeStudioColorFieldV1.VALUE -> plan.value.color
                ThemeStudioColorFieldV1.LABEL -> plan.label.color
                ThemeStudioColorFieldV1.BORDER ->
                    plan.borderColor(previewResolvedTheme.darkTheme) ?: previewResolvedTheme.contentColorScheme.outline

                ThemeStudioColorFieldV1.ICON_CONTAINER ->
                    plan.iconContainerColor(previewResolvedTheme.darkTheme)
                        ?: previewResolvedTheme.contentColorScheme.primaryContainer

                ThemeStudioColorFieldV1.ICON ->
                    plan.leadingIconContainer?.contentColor?.toComposeColorV1(previewResolvedTheme.darkTheme)
                        ?: previewPlan.leadingColor

                null -> null
            }
        }
    val selectedColorField = colorField
    val selectedPreviewPlan = previewPlan
    if (selectedColorField != null && activeColor != null && selectedPreviewPlan != null) {
        ColorPickerDialog(
            initialColor = activeColor.toArgb(),
            title = selectedColorField.title(),
            recentColors = listOf(activeColor.toArgb()),
            onColorSelected = { selectedColor ->
                val color = Color(selectedColor)
                when (selectedColorField) {
                    ThemeStudioColorFieldV1.SURFACE ->
                        updateLayer { NativeThemeStatStyleInstanceEditorV1.setSurfaceColor(it, color) }

                    ThemeStudioColorFieldV1.VALUE ->
                        updateLayer { NativeThemeStatStyleInstanceEditorV1.setValueColor(it, color) }

                    ThemeStudioColorFieldV1.LABEL ->
                        updateLayer { NativeThemeStatStyleInstanceEditorV1.setLabelColor(it, color) }

                    ThemeStudioColorFieldV1.BORDER ->
                        updateLayer {
                            NativeThemeStatStyleInstanceEditorV1.setBorder(
                                layer = it,
                                color = color,
                                widthDp = selectedPreviewPlan.borderWidth() ?: 1f,
                            )
                        }

                    ThemeStudioColorFieldV1.ICON_CONTAINER ->
                        updateLayer {
                            NativeThemeStatStyleInstanceEditorV1.setIconContainer(
                                layer = it,
                                containerColor = color,
                                iconColor =
                                    selectedPreviewPlan.leadingIconContainer?.contentColor?.toComposeColorV1(
                                        previewResolvedTheme.darkTheme,
                                    ) ?: previewResolvedTheme.contentColorScheme.onPrimaryContainer,
                            )
                        }

                    ThemeStudioColorFieldV1.ICON ->
                        updateLayer {
                            NativeThemeStatStyleInstanceEditorV1.setIconContainer(
                                layer = it,
                                containerColor =
                                    selectedPreviewPlan.iconContainerColor(previewResolvedTheme.darkTheme)
                                        ?: previewResolvedTheme.contentColorScheme.primaryContainer,
                                iconColor = color,
                            )
                        }

                    null -> Unit
                }
                colorField = null
            },
            onDismiss = { colorField = null },
        )
    }

    if (showLeaveConfirmation) {
        AlertDialog(
            onDismissRequest = {
                if (!saving) completeLeave(allowNavigation = false)
            },
            title = { Text(stringResource(R.string.theme_unsaved_title)) },
            text = { Text(stringResource(R.string.theme_unsaved_message)) },
            confirmButton = {
                TextButton(
                    onClick = { saveDraft { completeLeave(allowNavigation = true) } },
                    enabled = !saving && previewPlan != null,
                ) {
                    Text(stringResource(R.string.theme_save_and_continue))
                }
            },
            dismissButton = {
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = {
                            draftLayer = persistedLayer
                            resetRequested = false
                            completeLeave(allowNavigation = true)
                        },
                        enabled = !saving,
                    ) {
                        Text(stringResource(R.string.theme_discard_and_continue))
                    }
                    TextButton(
                        onClick = { completeLeave(allowNavigation = false) },
                        enabled = !saving,
                    ) {
                        Text(stringResource(R.string.cancel_action))
                    }
                }
            },
        )
    }
}

@Composable
private fun ThemeStudioHeader(target: ActivePrompt) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Text(
            text = stringResource(R.string.theme_studio_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = target.label(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ThemeStudioPreview(
    previewSnapshot: ThemePreferenceSnapshot,
    previewResolvedTheme: com.ai.assistance.operit.ui.theme.ResolvedNativeThemeV1,
    previewPlan: NativeThemeStatStylePlanV1,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Text(
            text = stringResource(R.string.theme_studio_stat_preview),
            style = MaterialTheme.typography.titleMedium,
        )
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            NativeThemeOffscreenHost(
                snapshot = previewSnapshot,
                resolvedTheme = previewResolvedTheme,
            ) {
                CompositionLocalProvider(LocalNativeThemeStatStylePlanV1 provides previewPlan) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        NativeThemeStatV1(
                            label = stringResource(R.string.theme_studio_stat_label),
                            value = stringResource(R.string.theme_studio_stat_value),
                            leading = { modifier ->
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = modifier,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ThemeStudioStatControls(
    plan: NativeThemeStatStylePlanV1,
    darkTheme: Boolean,
    enabled: Boolean,
    defaultBorderColor: Color,
    defaultIconContainerColor: Color,
    defaultIconColor: Color,
    onSurfaceColorRequested: () -> Unit,
    onValueColorRequested: () -> Unit,
    onLabelColorRequested: () -> Unit,
    onBorderEnabledChange: (Boolean) -> Unit,
    onBorderColorRequested: () -> Unit,
    onBorderWidthChange: (Float) -> Unit,
    onRoundedShape: (Float) -> Unit,
    onCapsuleShape: () -> Unit,
    onOpacityChange: (Float) -> Unit,
    onPaddingChange: (Float) -> Unit,
    onIconContainerEnabledChange: (Boolean) -> Unit,
    onIconContainerColorRequested: () -> Unit,
    onIconColorRequested: () -> Unit,
) {
    val borderEnabled = plan.border != null
    val iconContainerEnabled = plan.leadingIconContainer != null
    val cornerRadius = plan.cornerRadiusDp()
    val capsule = plan.shape == NativeThemeStyleShapeSpecV1.Capsule
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Text(
            text = stringResource(R.string.theme_studio_stat_component),
            style = MaterialTheme.typography.titleMedium,
        )
        ThemeStudioColorRow(
            label = stringResource(R.string.theme_studio_surface_color),
            color = plan.surfaceColor,
            enabled = enabled,
            onClick = onSurfaceColorRequested,
        )
        ThemeStudioColorRow(
            label = stringResource(R.string.theme_studio_value_color),
            color = plan.value.color,
            enabled = enabled,
            onClick = onValueColorRequested,
        )
        ThemeStudioColorRow(
            label = stringResource(R.string.theme_studio_label_color),
            color = plan.label.color,
            enabled = enabled,
            onClick = onLabelColorRequested,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        Text(
            text = stringResource(R.string.theme_studio_shape),
            style = MaterialTheme.typography.labelLarge,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            SegmentedButton(
                selected = !capsule,
                onClick = { onRoundedShape(cornerRadius) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) {
                Text(stringResource(R.string.theme_studio_rounded))
            }
            SegmentedButton(
                selected = capsule,
                onClick = onCapsuleShape,
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) {
                Text(stringResource(R.string.theme_studio_capsule))
            }
        }
        if (!capsule) {
            ThemeStudioSlider(
                label = stringResource(R.string.theme_studio_corner_radius),
                value = cornerRadius,
                valueRange = 0f..48f,
                enabled = enabled,
                onValueChange = onRoundedShape,
            )
        }
        ThemeStudioSlider(
            label = stringResource(R.string.theme_studio_opacity),
            value = plan.opacity,
            valueRange = 0.25f..1f,
            enabled = enabled,
            onValueChange = onOpacityChange,
        )
        ThemeStudioSlider(
            label = stringResource(R.string.theme_studio_padding),
            value = plan.contentPaddingDp,
            valueRange = 4f..32f,
            enabled = enabled,
            onValueChange = onPaddingChange,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        ThemeStudioSwitchRow(
            label = stringResource(R.string.theme_studio_border),
            checked = borderEnabled,
            enabled = enabled,
            onCheckedChange = onBorderEnabledChange,
        )
        if (borderEnabled) {
            ThemeStudioColorRow(
                label = stringResource(R.string.theme_studio_border_color),
                color = plan.borderColor(darkTheme) ?: defaultBorderColor,
                enabled = enabled,
                onClick = onBorderColorRequested,
            )
            ThemeStudioSlider(
                label = stringResource(R.string.theme_studio_border_width),
                value = plan.borderWidth() ?: 1f,
                valueRange = 0.25f..4f,
                enabled = enabled,
                onValueChange = onBorderWidthChange,
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        ThemeStudioSwitchRow(
            label = stringResource(R.string.theme_studio_icon_container),
            checked = iconContainerEnabled,
            enabled = enabled,
            onCheckedChange = onIconContainerEnabledChange,
        )
        if (iconContainerEnabled) {
            ThemeStudioColorRow(
                label = stringResource(R.string.theme_studio_icon_container_color),
                color = plan.iconContainerColor(darkTheme) ?: defaultIconContainerColor,
                enabled = enabled,
                onClick = onIconContainerColorRequested,
            )
            ThemeStudioColorRow(
                label = stringResource(R.string.theme_studio_icon_color),
                color = plan.leadingIconContainer?.contentColor?.toComposeColorV1(darkTheme) ?: defaultIconColor,
                enabled = enabled,
                onClick = onIconColorRequested,
            )
        }
    }
}

@Composable
private fun ThemeStudioColorRow(
    label: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .padding(end = 12.dp)
                    .heightIn(min = 24.dp)
                    .background(color, MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 12.dp),
        )
        Text(text = label, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ThemeStudioSwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        color = Color.Transparent,
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}

@Composable
private fun ThemeStudioSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            text = "$label ${"%.1f".format(value)}",
            style = MaterialTheme.typography.labelLarge,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
        )
    }
}

@Composable
private fun ThemeStudioCommands(
    saveEnabled: Boolean,
    saving: Boolean,
    resetEnabled: Boolean,
    onSave: () -> Unit,
    onReset: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Button(
            onClick = onSave,
            enabled = saveEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (saving) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
            }
            Text(stringResource(R.string.save_action))
        }
        OutlinedButton(
            onClick = onReset,
            enabled = resetEnabled,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.theme_reset))
        }
    }
}

@Composable
private fun ThemeStudioLinkIssue(linkResult: NativeThemeStyleLinkResultV1) {
    val issue =
        when (linkResult) {
            is NativeThemeStyleLinkResultV1.Rejected -> linkResult.issues.firstOrNull()
            is NativeThemeStyleLinkResultV1.Linked -> null
        }
    val code = issue?.code?.name ?: "UNKNOWN"
    val path = issue?.path ?: "theme_studio"
    Text(
        text = stringResource(R.string.theme_studio_link_error, code, path),
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(vertical = 20.dp),
    )
}

@Composable
private fun ActivePrompt.label(): String =
    when (this) {
        is ActivePrompt.CharacterCard -> stringResource(R.string.theme_studio_target_character, id)
        is ActivePrompt.CharacterGroup -> stringResource(R.string.theme_studio_target_group, id)
    }

private fun ActivePrompt.defaultThemeSnapshot(): ThemePreferenceSnapshot =
    when (this) {
        is ActivePrompt.CharacterCard ->
            ThemePreferenceSnapshot(
                source = "character_card",
                sourceId = id,
                values = ThemePreferenceValues.defaultVisual(),
            )

        is ActivePrompt.CharacterGroup ->
            ThemePreferenceSnapshot(
                source = "character_group",
                sourceId = id,
                values = ThemePreferenceValues.defaultVisual(),
            )
    }

@Composable
private fun ThemeStudioColorFieldV1.title(): String =
    when (this) {
        ThemeStudioColorFieldV1.SURFACE -> stringResource(R.string.theme_studio_surface_color)
        ThemeStudioColorFieldV1.VALUE -> stringResource(R.string.theme_studio_value_color)
        ThemeStudioColorFieldV1.LABEL -> stringResource(R.string.theme_studio_label_color)
        ThemeStudioColorFieldV1.BORDER -> stringResource(R.string.theme_studio_border_color)
        ThemeStudioColorFieldV1.ICON_CONTAINER -> stringResource(R.string.theme_studio_icon_container_color)
        ThemeStudioColorFieldV1.ICON -> stringResource(R.string.theme_studio_icon_color)
    }
