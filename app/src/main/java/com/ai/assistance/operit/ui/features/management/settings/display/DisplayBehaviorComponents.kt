package com.ai.assistance.operit.ui.features.management.settings.display

import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton

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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchSlider
import kotlin.math.roundToInt

internal enum class DisplayEditor {
    COLLAPSE,
    ORIENTATION,
    THRESHOLD,
    VIRTUAL,
    INDICATOR,
    SCREENSHOT,
    ROOT,
    SU,
    RESET,
}

/** 数值与形状直接对应定稿；使用原生控件负责点击、键盘和无障碍。 */
@Composable
internal fun DisplayCard(
    title: String,
    icon: ImageVector,
    tail: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth().padding(bottom = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth()
                    .padding(start = 17.dp, end = 17.dp, top = 15.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(icon, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    title,
                    Modifier.weight(1f),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (tail != null)
                    Text(tail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DisplayDivider()
            content()
        }
    }
}

@Composable
internal fun DisplayDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

@Composable
internal fun DisplayToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 73.dp)
            .alpha(if (enabled) 1f else 0.43f)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 17.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        DisplayCopy(title, description, Modifier.weight(1f))
        // 仅缩小可见轨道；整行仍保留足够大的原生开关触摸区域。
        Box(Modifier.size(39.dp, 24.dp), contentAlignment = Alignment.Center) {
            Switch(
                checked,
                onCheckedChange = null,
                enabled = enabled,
                modifier = Modifier.requiredSize(52.dp, 32.dp).scale(0.75f),
                colors =
                    SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedBorderColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.outline,
                        uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                        disabledCheckedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        disabledCheckedTrackColor = MaterialTheme.colorScheme.primary,
                        disabledUncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledUncheckedTrackColor = MaterialTheme.colorScheme.outline,
                    ),
            )
        }
    }
}

@Composable
internal fun DisplayCopy(title: String, description: String?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
        if (description != null)
            Text(
                description,
                fontSize = 12.sp,
                lineHeight = 18.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
}

@Composable
internal fun DisplayNavigationRow(
    title: String,
    description: String?,
    value: String,
    child: Boolean = false,
    leading: ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = if (child) 59.dp else 73.dp)
            .background(
                if (child) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 17.dp, vertical = if (child) 12.dp else 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        if (leading != null)
            Icon(
                leading,
                null,
                Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        Box(Modifier.weight(1f)) {
            if (child) Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            else DisplayCopy(title, description)
        }
        Row(
            Modifier.widthIn(max = 150.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                value,
                Modifier.weight(1f, fill = false),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
            )
            Icon(
                Icons.Default.ChevronRight,
                null,
                Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun DisplayHint(text: String, modifier: Modifier = Modifier, icon: Boolean = false) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon)
            Icon(
                Icons.Outlined.Info,
                null,
                Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        Text(
            text,
            fontSize = 11.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun DisplayChoiceRow(
    title: String,
    description: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 72.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent
            )
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
            if (description != null)
                Text(
                    description,
                    fontSize = 12.sp,
                    lineHeight = 18.6.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
        Box(
            Modifier.size(21.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                .border(
                    1.dp,
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(6.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected)
                Icon(
                    Icons.Default.Check,
                    null,
                    Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
        }
    }
}

@Composable
internal fun DisplayChoiceGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(13.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.selectableGroup(), content = content)
    }
}

@Composable
internal fun DisplayOptionTile(
    title: String,
    description: String,
    selected: Boolean,
    modifier: Modifier,
    preview: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Surface(
        modifier,
        shape = RoundedCornerShape(13.dp),
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent,
        border =
            BorderStroke(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Box(
            Modifier.selectable(selected, role = Role.RadioButton, onClick = onClick).padding(13.dp)
        ) {
            Column {
                preview?.invoke()
                Text(
                    title,
                    Modifier.padding(end = 20.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    description,
                    Modifier.padding(top = 4.dp),
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected)
                Icon(
                    Icons.Default.Check,
                    null,
                    Modifier.size(17.dp).align(Alignment.TopEnd),
                    tint = MaterialTheme.colorScheme.primary,
                )
        }
    }
}

@Composable
internal fun DisplayRange(
    title: String,
    description: String,
    value: Int,
    range: IntRange,
    format: (Int) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onChange: (Int) -> Unit,
    onFinish: () -> Unit,
) {
    Column(modifier.padding(vertical = 16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(format(value), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            description,
            Modifier.padding(top = 4.dp),
            fontSize = 12.sp,
            lineHeight = 18.6.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ThemeWorkbenchSlider(
            value.toFloat(),
            { onChange(it.roundToInt()) },
            modifier = Modifier.height(36.dp),
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
            enabled = enabled,
            onValueChangeFinished = onFinish,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                format(range.first),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                format(range.last),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun DisplayDialog(
    title: String,
    description: String,
    onDismiss: () -> Unit,
    footer: @Composable RowScope.() -> Unit = {
        DisplayPrimaryButton(stringResource(R.string.display_behavior_done), onClick = onDismiss)
    },
    content: @Composable ColumnScope.() -> Unit,
) {
    val height = LocalConfiguration.current.screenHeightDp.dp * 0.85f
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            Modifier.widthIn(max = 540.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .heightIn(max = height),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth()
                        .padding(start = 20.dp, end = 14.dp, top = 18.dp, bottom = 11.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(
                        Modifier.weight(1f).padding(top = 3.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            title,
                            fontSize = 18.sp,
                            lineHeight = 27.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            description,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Default.Close,
                            stringResource(R.string.display_behavior_close),
                            Modifier.size(21.dp),
                        )
                    }
                }
                Column(
                    Modifier.weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 16.dp),
                    content = content,
                )
                DisplayDivider()
                Row(
                    Modifier.fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 13.dp, bottom = 17.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    content = footer,
                )
            }
        }
    }
}

@Composable
internal fun DisplayPrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 42.dp),
        shape = RoundedCornerShape(11.dp),
        contentPadding = PaddingValues(horizontal = 21.dp, vertical = 9.dp),
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun DisplayDialogNote(text: String) {
    Surface(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        DisplayHint(text, Modifier.padding(horizontal = 12.dp, vertical = 11.dp))
    }
}
