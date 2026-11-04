package com.ai.assistance.operit.ui.features.extensions.packages.management
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.data.preferences.glass.GlassRegion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.ui.theme.getTextColorForBackground
import com.ai.assistance.operit.ui.theme.glass.rememberGlassAppearance
import com.ai.assistance.operit.ui.theme.liquidGlass

/** 市场与导入入口使用一致的按钮样式，底色由当前主题分别配置。 */
@Composable
fun PackageAcquisitionActions(
    marketLabelRes: Int,
    importLabelRes: Int,
    onMarketClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val values = LocalThemePreferenceSnapshot.current.values
    val glassAppearance = rememberGlassAppearance()
    val glassEnabled = glassAppearance.packageCards
    val marketColor = values.int("package_market_button_color")?.let(::Color)
        ?: MaterialTheme.colorScheme.secondaryContainer
    val addColor = values.int("package_add_button_color")?.let(::Color)
        ?: MaterialTheme.colorScheme.primaryContainer
    // 文字按透明底色叠加后的明暗取色，避免亮色玻璃上的文字对比度不足。
    val marketDisplayColor = if (glassEnabled) marketColor.copy(alpha = glassAppearance.tintOpacity)
        .compositeOver(MaterialTheme.colorScheme.background) else marketColor
    val addDisplayColor = if (glassEnabled) addColor.copy(alpha = glassAppearance.tintOpacity)
        .compositeOver(MaterialTheme.colorScheme.background) else addColor
    val shape = RoundedCornerShape(12.dp)
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilledTonalButton(
            onClick = onMarketClick,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).liquidGlass(glassEnabled, shape, marketColor,
                region = GlassRegion.PACKAGE_CARDS,
            ),
            shape = shape,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (glassEnabled) Color.Transparent else marketColor,
                contentColor = getTextColorForBackground(marketDisplayColor),
            ),
        ) {
            Icon(Icons.Default.Store, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(marketLabelRes), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        FilledTonalButton(
            onClick = onImportClick,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).liquidGlass(glassEnabled, shape, addColor,
                region = GlassRegion.PACKAGE_CARDS,
            ),
            shape = shape,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (glassEnabled) Color.Transparent else addColor,
                contentColor = getTextColorForBackground(addDisplayColor),
            ),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(importLabelRes), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
