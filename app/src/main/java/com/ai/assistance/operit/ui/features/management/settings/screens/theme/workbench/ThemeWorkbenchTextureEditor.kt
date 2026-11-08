package com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.ThemeEditorSession
import com.ai.assistance.operit.ui.theme.isLiquidGlassSupported
import com.ai.assistance.operit.ui.theme.isWaterGlassSupported

@Composable
internal fun themeWorkbenchTextureLabel(liquid: Boolean, water: Boolean): String =
    stringResource(
        when {
            water -> R.string.theme_workbench_water_glass
            liquid -> R.string.theme_workbench_liquid_glass
            else -> R.string.theme_workbench_texture_normal
        }
    )

/** 单选项继续调用原有互斥规则，不引入新的偏好格式。 */
@Composable
internal fun ThemeWorkbenchTextureEditor(
    editorSession: ThemeEditorSession,
    prefix: String,
    liquid: Boolean,
    water: Boolean,
    rows: Boolean = false,
) {
    val selected =
        when {
            water -> "water"
            liquid -> "frosted"
            else -> "normal"
        }
    val options =
        listOf(
            "normal" to stringResource(R.string.theme_workbench_texture_normal),
            "frosted" to stringResource(R.string.theme_workbench_liquid_glass),
            "water" to stringResource(R.string.theme_workbench_water_glass),
        )
    val frostedSupported = isLiquidGlassSupported()
    val waterSupported = isWaterGlassSupported()
    fun enabled(value: String) =
        when (value) {
            "frosted" -> frostedSupported
            "water" -> waterSupported
            else -> true
        }
    fun choose(value: String) {
        editorSession.setBoolean(prefix + "_liquid_glass", value == "frosted")
        editorSession.setBoolean(prefix + "_water_glass", value == "water")
    }
    if (rows)
        Column(Modifier.selectableGroup()) {
            options.forEachIndexed { index, (value, label) ->
                if (index > 0) ThemeWorkbenchDivider()
                ThemeWorkbenchChoiceRow(
                    label,
                    selected == value,
                    subtitle =
                        stringResource(
                            when (value) {
                                "frosted" -> R.string.theme_workbench_frosted_hint
                                "water" -> R.string.theme_workbench_water_hint
                                else -> R.string.theme_workbench_normal_hint
                            }
                        ),
                    enabled = enabled(value),
                    onClick = { choose(value) },
                )
            }
        }
    else
        ThemeWorkbenchSegments(
            options,
            selected,
            onSelected = { choose(it) },
            isOptionEnabled = { enabled(it) },
        )
}
