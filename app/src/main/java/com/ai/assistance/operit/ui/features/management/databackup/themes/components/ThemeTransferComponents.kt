package com.ai.assistance.operit.ui.features.management.databackup.themes.components

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ai.assistance.operit.ui.theme.script.text.ThemeBasicTextField as BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.management.databackup.themes.ThemeTransferMethod

/** 主题抽屉的控件共用圆角、留白和颜色层级，不改变参数与文件处理逻辑。 */
@Composable
internal fun ThemeTransferMethodSelector(
    selected: ThemeTransferMethod,
    enabled: Boolean,
    onSelect: (ThemeTransferMethod) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().selectableGroup().padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ThemeTransferMethod.values().forEach { method ->
                val active = selected == method
                val background by
                    animateColorAsState(
                        if (active) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                        label = "themeTransferMethod",
                    )
                val foreground =
                    if (active) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                Row(
                    Modifier.weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(background)
                        .selectable(active, enabled = enabled, role = Role.Tab) { onSelect(method) }
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        if (method == ThemeTransferMethod.CODE) Icons.Default.Link
                        else Icons.Default.FileOpen,
                        null,
                        Modifier.size(18.dp),
                        tint = foreground,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            if (method == ThemeTransferMethod.CODE) R.string.theme_transfer_code
                            else R.string.theme_transfer_file_tab
                        ),
                        color = foreground,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ThemeTransferTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    multiline: Boolean = false,
    code: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val outline =
        if (focused) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    Column(
        modifier.alpha(if (enabled) 1f else 0.6f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (label.isNotEmpty()) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = !multiline,
            minLines = if (multiline) 4 else 1,
            maxLines = if (multiline) 7 else 1,
            textStyle =
                if (code)
                    MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 21.sp,
                    )
                else
                    MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = if (code) KeyboardType.Ascii else KeyboardType.Text,
                    imeAction = if (multiline) ImeAction.Default else ImeAction.Next,
                ),
            modifier =
                Modifier.fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .border(if (focused) 1.5.dp else 1.dp, outline, shape)
                    .semantics { contentDescription = label.ifEmpty { placeholder } },
            decorationBox = { editor ->
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (leadingIcon != null)
                        Icon(
                            leadingIcon,
                            null,
                            Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        editor()
                    }
                }
            },
        )
    }
}

@Composable
internal fun ThemeTransferIdentity(
    name: String,
    author: String,
    badge: String,
    avatarUri: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarUri.isNullOrBlank()) {
                Icon(
                    Icons.Default.Palette,
                    null,
                    Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else
                AsyncImage(
                    avatarUri,
                    null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (author.isNotBlank())
                Text(
                    author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Text(
                badge,
                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ThemeTransferCodeBlock(code: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.theme_transfer_code),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    stringResource(R.string.theme_transfer_code_length, code.length),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            SelectionContainer {
                Text(
                    code,
                    Modifier.fillMaxWidth()
                        .heightIn(max = 176.dp)
                        .verticalScroll(rememberScrollState()),
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 20.sp,
                        ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun ThemeTransferFileCard(
    name: String?,
    author: String,
    enabled: Boolean,
    onChoose: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(enabled = enabled, role = Role.Button, onClick = onChoose),
    ) {
        Row(
            Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                Icons.Default.FileOpen,
                null,
                Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    name ?: stringResource(R.string.theme_transfer_choose_file),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (author.isNotBlank()) author else ".oktheme",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ThemeTransferRoleAvatar(uri: String?, modifier: Modifier = Modifier) {
    com.ai.assistance.operit.ui.common.themetarget.ThemeTargetImage(uri, modifier)
}
