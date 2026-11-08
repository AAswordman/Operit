package com.ai.assistance.operit.ui.features.conversation.chat.modules.sidebar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.data.preferences.glass.GlassRegion
import com.ai.assistance.operit.ui.main.components.NavigationDrawerAppearance
import com.ai.assistance.operit.ui.theme.liquidGlass
import com.ai.assistance.operit.ui.theme.script.*

/** 侧栏导航按钮共用磨砂绘制，启用时避免实色 Surface 遮住采样背景。 */
@Composable
internal fun SidebarButtonSurface(
    onClick: () -> Unit,
    modifier: Modifier,
    shape: CornerBasedShape,
    appearance: NavigationDrawerAppearance,
    containerColor: Color,
    contentColor: Color,
    border: BorderStroke? = null,
    selected: Boolean = false,
    content: @Composable () -> Unit,
) {
    ThemeSurfaceStateScope("navigationItem", selected, handlesOpacity = true) {
        val itemShape = themeScriptShape("navigationItem", shape)
        val fill = themeScriptFill("navigationItem", containerColor)
        Surface(
            onClick = onClick,
            modifier = modifier.alpha(themeSurface("navigationItem")?.opacity ?: 1f).liquidGlass(
                enabled = appearance.buttonLiquidGlassEnabled,
                shape = itemShape,
                containerColor = fill,
                enableLens = false,
                region = GlassRegion.SIDEBAR_BUTTONS,
            ),
            shape = itemShape,
            color = if (appearance.buttonLiquidGlassEnabled) Color.Transparent else fill,
            contentColor = themeScriptContentColor("navigationItem", contentColor),
            border = themeScriptBorder("navigationItem", border),
        ) {
            ThemeScriptSurface("navigationItem", selected = selected, drawFill = false, drawStroke = false, originalShape = itemShape, content = content)
        }
    }
}
