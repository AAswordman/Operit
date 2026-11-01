package com.ai.assistance.operit.ui.features.management.settings.sections.glass

import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.glass.GlassRegion
import com.ai.assistance.operit.data.preferences.glass.GlassThemePreferences
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.ThemeEditorSession
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.*
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchSlider
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.ui.theme.isLiquidGlassSupported
import com.ai.assistance.operit.ui.theme.liquidGlass
import kotlin.math.roundToInt
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchDialog
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchRow
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchSwitchRow

internal val GlassRegion.titleRes: Int
    get() =
        when (this) {
            GlassRegion.GLOBAL -> R.string.theme_glass_global_parameters
            GlassRegion.USER_MESSAGE -> R.string.theme_workbench_user_message
            GlassRegion.AI_MESSAGE -> R.string.theme_workbench_ai_message
            GlassRegion.INPUT -> R.string.input_style_title
            GlassRegion.SIDEBAR -> R.string.theme_glass_sidebar_parameters
            GlassRegion.SIDEBAR_BUTTONS -> R.string.theme_glass_sidebar_button_parameters
            GlassRegion.CONVERSATION_LIST -> R.string.glass_conversation_list
            GlassRegion.SETTINGS_CARDS -> R.string.glass_settings_cards
            GlassRegion.PACKAGE_CARDS -> R.string.glass_package_cards
        }

@Composable
internal fun GlassParametersEntry(
    editorSession: ThemeEditorSession,
    region: GlassRegion,
    title: String = stringResource(R.string.theme_glass_parameters),
) {
    val values by editorSession.values.collectAsState()
    var opened by remember { mutableStateOf(false) }
    ThemeWorkbenchRow(
        title = title,
        value =
            stringResource(
                if (region.isCustom(values)) R.string.theme_glass_independent
                else R.string.theme_glass_follow_global
            ),
        enabled = isLiquidGlassSupported(),
        onClick = { opened = true },
    )
    if (opened) GlassParametersDialog(editorSession, region, onDismiss = { opened = false })
}

/** 二级参数窗口编辑同一草稿，关闭窗口不提前保存或丢弃主题。 */
@Composable
internal fun GlassParametersDialog(
    editorSession: ThemeEditorSession,
    region: GlassRegion,
    onDismiss: () -> Unit,
) {
    val values by editorSession.values.collectAsState()
    val global = region == GlassRegion.GLOBAL
    val custom = region.isCustom(values)
    val supported = isLiquidGlassSupported()
    val editable = supported && (global || custom)
    val parameters = region.parameters(values)
    val snapshot = LocalThemePreferenceSnapshot.current.copy(values = values)
    ThemeWorkbenchDialog(stringResource(region.titleRes), onDismiss) {
        if (!global)
            ThemeWorkbenchSwitchRow(
                title = stringResource(R.string.theme_glass_use_independent),
                subtitle = stringResource(R.string.theme_glass_independent_description),
                checked = custom,
                enabled = supported,
                onCheckedChange = { enabled ->
                    editorSession.update { region.setCustom(it, enabled) }
                },
            )
        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(
                    if (global) R.string.theme_glass_global_description
                    else R.string.theme_glass_follow_description
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!supported)
                Text(
                    stringResource(R.string.theme_glass_requires_android13),
                    style = MaterialTheme.typography.bodySmall,
                )
            CompositionLocalProvider(LocalThemePreferenceSnapshot provides snapshot) {
                Box(
                    Modifier.fillMaxWidth()
                        .height(70.dp)
                        .liquidGlass(
                            enabled = supported,
                            shape = RoundedCornerShape(14.dp),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            region = region,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.glass_preview))
                }
            }
            listOf(
                    Triple("glass_blur_radius", R.string.glass_blur, 0f..40f),
                    Triple("glass_edge_width", R.string.glass_edge, 0f..2f),
                    Triple("glass_highlight_alpha", R.string.glass_highlight, 0f..0.8f),
                    Triple("glass_shadow_radius", R.string.glass_shadow, 0f..20f),
                    Triple("glass_tint_opacity", R.string.glass_opacity, 0f..1f),
                )
                .forEach { (key, label, range) ->
                    val value = parameters.getValue(key)
                    val percent = key == "glass_highlight_alpha" || key == "glass_tint_opacity"
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(label),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            if (percent)
                                stringResource(
                                    R.string.glass_value_percent,
                                    (value * 100).roundToInt(),
                                )
                            else stringResource(R.string.glass_value_dp, value),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    ThemeWorkbenchSlider(
                        value.coerceIn(range),
                        valueRange = range,
                        enabled = editable,
                        onValueChange = { editorSession.setFloat(region.parameterKey(key), it) },
                    )
                }
            TextButton(
                enabled = editable,
                onClick = {
                    editorSession.update { current ->
                        current.copy(
                            floats =
                                current.floats +
                                    GlassThemePreferences.globalFloatDefaults.mapKeys {
                                        region.parameterKey(it.key)
                                    }
                        )
                    }
                },
            ) {
                Text(stringResource(R.string.glass_reset_parameters))
            }
        }
    }
}
