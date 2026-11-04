package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.theme.script.ThemeScriptContract

/** 汇集输入区与顶栏全部动作按钮；导航项继续由导航项容器控制。 */
internal val ThemeStudioButtonIcons = ThemeScriptContract.actionButtonIds.map { id ->
    ThemeStudioTargets.single { it.kind == StudioKind.Surface && it.id == id }
}

/** 每行动作分别展示浅深色素材，进入独立按钮编辑器时不修改其他按钮。 */
@Composable
internal fun StudioButtonIcons(state: ThemeStudioState) {
    StudioSection(stringResource(R.string.studio_button_icons)) {
        StudioHint(stringResource(R.string.studio_button_icons_hint))
        StudioColorHead()
        ThemeStudioButtonIcons.forEach { target ->
            val title = target.label()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    Modifier.weight(1f).clickable { state.select(target) }.padding(vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                listOf("light", "dark").forEach { mode ->
                    val icon = state.icon(id = target.id, forMode = mode)
                    val modeLabel = stringResource(if (mode == "light") R.string.studio_light else R.string.studio_dark)
                    IconButton(
                        onClick = { state.select(target); state.mode = mode },
                        modifier = Modifier.width(60.dp),
                    ) {
                        if (icon == null) {
                            Icon(target.glyph, "$title · $modeLabel", Modifier.size(24.dp), tint = StudioAccent)
                        } else {
                            Box(Modifier.semantics { contentDescription = "$title · $modeLabel" }) {
                                StudioAssetThumbnail(state.images.getValue(icon.asset), Modifier.size(28.dp))
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = StudioLine)
        }
    }
}
