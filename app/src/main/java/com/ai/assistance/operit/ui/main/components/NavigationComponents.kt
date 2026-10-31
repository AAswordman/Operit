package com.ai.assistance.operit.ui.main.components

import com.ai.assistance.operit.ui.theme.script.ThemeScriptIcon
import com.ai.assistance.operit.ui.theme.script.ThemeSurfaceStateScope
import com.ai.assistance.operit.ui.theme.script.themeSurface
import androidx.compose.ui.draw.alpha
import com.ai.assistance.operit.ui.theme.script.themeScriptTextStyle
import com.ai.assistance.operit.ui.theme.script.themeScriptShape
import com.ai.assistance.operit.ui.theme.script.themeScriptFill
import com.ai.assistance.operit.ui.theme.script.themeScriptContentColor
import com.ai.assistance.operit.ui.theme.script.themeScriptDecoration
import com.ai.assistance.operit.data.preferences.glass.GlassRegion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.theme.liquidGlass

/** 导航抽屉的分组标题。 */
@Composable
fun NavigationDrawerItemHeader(title: String, appearance: NavigationDrawerAppearance) {
    Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = appearance.titleColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 8.dp)
    )
}

/** 图标与文字共用导航项的选中状态，业务点击仍由原生 Surface 持有。 */
@Composable
fun CompactNavigationDrawerItem(
        icon: ImageVector,
        label: String,
        selected: Boolean,
        appearance: NavigationDrawerAppearance,
        onClick: () -> Unit
) {
    ThemeSurfaceStateScope("navigationItem", selected, handlesOpacity = true) {
        val itemShape = themeScriptShape("navigationItem", MaterialTheme.shapes.small)
        val glassBaseColor = themeScriptFill("navigationItem", appearance.buttonContainerColor)
        val selectedGlassOverlayColor =
                if (selected) {
                        appearance.selectedContainerColor.copy(alpha = 0.18f)
                } else {
                        Color.Transparent
                }
        Surface(
                modifier =
                        Modifier.fillMaxWidth()
                                .alpha(themeSurface("navigationItem")?.opacity ?: 1f)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .height(40.dp)
                                .liquidGlass(
                                        enabled = appearance.buttonLiquidGlassEnabled,
                                        shape = itemShape,
                                        containerColor = glassBaseColor,
                                        enableLens = false,
                                    region = GlassRegion.SIDEBAR_BUTTONS,
                                )
                                .clip(itemShape)
                                .background(selectedGlassOverlayColor),
                onClick = onClick,
                color =
                        if (appearance.buttonLiquidGlassEnabled) {
                                Color.Transparent
                        } else themeScriptFill("navigationItem", if (selected) appearance.selectedContainerColor else Color.Transparent),
                shape = itemShape
        ) {
            Row(
                    modifier = Modifier.fillMaxSize().themeScriptDecoration("navigationItem", selected = selected, drawFill = false, originalShape = itemShape).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                ThemeScriptIcon(
                        id = "navigation.item",
                        imageVector = icon,
                        contentDescription = null,
                        tint =
                                themeScriptContentColor("navigationItem", if (selected) appearance.selectedContentColor else appearance.itemColor),
                        modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                        text = label,
                        style = themeScriptTextStyle("navigationItem", MaterialTheme.typography.bodyMedium),
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        color =
                                themeScriptContentColor("navigationItem", if (selected) appearance.selectedContentColor else appearance.itemColor)
                )
            }
        }
    }
}
