package com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench

import com.ai.assistance.operit.ui.theme.script.text.themeTextStroke
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton

import com.ai.assistance.operit.ui.theme.script.themeScriptDecoration
import com.ai.assistance.operit.ui.theme.script.themeScriptShape
import com.ai.assistance.operit.ui.theme.script.themeScriptBorder
import com.ai.assistance.operit.ui.theme.script.themeScriptContentColor
import com.ai.assistance.operit.ui.theme.script.themeScriptTextStyle
import com.ai.assistance.operit.ui.theme.script.themeScriptFill

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import java.util.Locale

/** 设置页共用紧凑行和居中窗口，复杂编辑仍由原有主题组件完成。 */
@Composable
internal fun ThemeWorkbenchCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    onHeaderClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(bottom = 14.dp),
        shape = themeScriptShape("settingsCard", RoundedCornerShape(18.dp)),
        color = themeScriptFill("settingsCard", MaterialTheme.colorScheme.surface),
        border = themeScriptBorder("settingsCard", BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))),
    ) {
        Column(Modifier.themeScriptDecoration("settingsCard", drawFill = false, drawStroke = false, originalShape = RoundedCornerShape(18.dp)).padding(bottom = 6.dp)) {
            Row(
                Modifier.fillMaxWidth()
                    .then(
                        if (onHeaderClick != null)
                            Modifier.clickable(role = Role.Button, onClick = onHeaderClick)
                        else Modifier
                    )
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                if (icon != null)
                    Icon(icon, null, Modifier.size(18.dp), tint = themeScriptContentColor("settingsCard", MaterialTheme.colorScheme.primary))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        title,
                        style = themeScriptTextStyle("settingsCard", MaterialTheme.typography.titleSmall),
                        color = themeScriptContentColor("settingsCard", MaterialTheme.colorScheme.onSurface),
                     textStroke = themeTextStroke("settingsCard"))
                    if (subtitle != null)
                        Text(
                            subtitle,
                            style = themeScriptTextStyle("settingsCard", MaterialTheme.typography.bodySmall),
                            color = themeScriptContentColor("settingsCard", MaterialTheme.colorScheme.onSurfaceVariant),
                         textStroke = themeTextStroke("settingsCard"))
                }
                trailing?.invoke()
            }
            content()
        }
    }
}

/** 与设计稿一致的内缩分段选项，选择状态仍由主题草稿提供。 */
@Composable
internal fun ThemeWorkbenchSegments(
    options: List<Pair<String, String>>,
    selected: String,
    isOptionEnabled: (String) -> Boolean = { true },
    onSelected: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                Modifier.weight(1f)
                    .heightIn(min = 42.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.surface else Color.Transparent
                    )
                    .border(
                        1.dp,
                        if (active) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        else Color.Transparent,
                        RoundedCornerShape(9.dp),
                    )
                    .selectable(
                        selected = active,
                        enabled = isOptionEnabled(value),
                        role = Role.RadioButton,
                        onClick = { onSelected(value) },
                    )
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    color =
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun ThemeWorkbenchDivider() {
    HorizontalDivider(
        Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    )
}

@Composable
internal fun ThemeWorkbenchColorSwatch(color: Color) {
    Box(
        Modifier.size(22.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(color)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                androidx.compose.foundation.shape.CircleShape,
            )
    )
}

@Composable
internal fun ThemeWorkbenchRow(
    title: String,
    value: String? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
    leading: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (leadingContent != null) leadingContent()
        else if (leading != null)
            Icon(leading, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (subtitle != null)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
        if (value != null)
            Text(
                value,
                Modifier.widthIn(max = 130.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        trailing?.invoke()
        Icon(
            Icons.Default.ChevronRight,
            null,
            Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ThemeWorkbenchSwitchRow(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    subtitle: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 58.dp)
            .toggleable(
                checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = null,
            modifier = Modifier.scale(0.85f),
        )
    }
}

@Composable
internal fun ThemeWorkbenchDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.85f
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            Modifier.widthIn(max = 560.dp).fillMaxWidth(0.90f).heightIn(max = maxHeight),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, stringResource(R.string.close))
                    }
                }
                Column(
                    Modifier.weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    content()
                }
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                    ) {
                        Text(stringResource(R.string.done))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ThemeWorkbenchDisclosure(
    title: String,
    summary: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ThemeWorkbenchCard(
        title,
        icon = Icons.Default.Visibility,
        onHeaderClick = { expanded = !expanded },
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    Modifier.size(18.dp),
                )
            }
        },
    ) {
        if (expanded) {
            ThemeWorkbenchDivider()
            content()
        }
    }
}

/** 双颜色缩略图与消息标题放在同一侧，和外观窗口使用相同颜色。 */
@Composable
internal fun ThemeWorkbenchColorPair(background: Color, text: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy((-5).dp)) {
        ThemeWorkbenchColorSwatch(background)
        ThemeWorkbenchColorSwatch(text)
    }
}

@Composable
internal fun ThemeWorkbenchColorRow(title: String, color: Color, onClick: () -> Unit) {
    val argb = color.toArgb()
    val hex =
        if ((argb ushr 24) == 255) String.format(Locale.ROOT, "#%06x", argb and 0xffffff)
        else String.format(Locale.ROOT, "#%08x", argb)
    ThemeWorkbenchRow(
        title = title,
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ThemeWorkbenchColorSwatch(color)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Text(
                        hex,
                        Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        },
        onClick = onClick,
    )
}

@Composable
internal fun ThemeWorkbenchChoiceRow(
    title: String,
    selected: Boolean,
    subtitle: String? = null,
    enabled: Boolean = true,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
                else Color.Transparent
            )
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = fontFamily,
                color =
                    if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (subtitle != null)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
        if (selected)
            Icon(
                Icons.Default.Check,
                null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
    }
}
