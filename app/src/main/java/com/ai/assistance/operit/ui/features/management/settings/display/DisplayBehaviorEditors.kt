package com.ai.assistance.operit.ui.features.management.settings.display

import com.ai.assistance.operit.ui.theme.script.ThemeScriptFilledTonalButton as FilledTonalButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptOutlinedButton as OutlinedButton

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R

@Composable
internal fun DisplayBehaviorEditor(
    editor: DisplayEditor,
    controller: DisplayBehaviorController,
    onOpen: (DisplayEditor) -> Unit,
    onDismiss: () -> Unit,
) {
    val values = controller.values
    when (editor) {
        DisplayEditor.COLLAPSE ->
            DisplayDialog(
                stringResource(R.string.display_behavior_collapse_title),
                stringResource(R.string.display_behavior_collapse_editor_desc),
                onDismiss,
            ) {
                DisplayChoiceGroup {
                    listOf(
                            Triple(
                                "read_only",
                                R.string.display_behavior_collapse_read,
                                R.string.display_behavior_collapse_read_desc,
                            ),
                            Triple(
                                "all",
                                R.string.display_behavior_collapse_all,
                                R.string.display_behavior_collapse_all_desc,
                            ),
                            Triple(
                                "full",
                                R.string.display_behavior_collapse_full,
                                R.string.display_behavior_collapse_full_desc,
                            ),
                        )
                        .forEachIndexed { index, (value, title, description) ->
                            if (index > 0) DisplayDivider()
                            DisplayChoiceRow(
                                stringResource(title),
                                stringResource(description),
                                values[DisplayChoice.COLLAPSE] == value,
                            ) {
                                controller.set(DisplayChoice.COLLAPSE, value)
                            }
                        }
                }
                DisplayMicroLabel(stringResource(R.string.display_behavior_reading_preview))
                DisplayToolPreview(values[DisplayChoice.COLLAPSE])
            }
        DisplayEditor.ORIENTATION ->
            DisplayDialog(
                stringResource(R.string.display_behavior_orientation_title),
                stringResource(R.string.display_behavior_orientation_editor_desc),
                onDismiss,
            ) {
                DisplayChoiceGroup {
                    listOf(
                            Triple(
                                "ALWAYS",
                                R.string.display_behavior_orientation_always,
                                R.string.display_behavior_orientation_always_desc,
                            ),
                            Triple(
                                "ASK",
                                R.string.display_behavior_orientation_ask,
                                R.string.display_behavior_orientation_ask_desc,
                            ),
                            Triple(
                                "NEVER",
                                R.string.display_behavior_orientation_never,
                                R.string.display_behavior_orientation_never_desc,
                            ),
                        )
                        .forEachIndexed { index, (value, title, description) ->
                            if (index > 0) DisplayDivider()
                            DisplayChoiceRow(
                                stringResource(title),
                                stringResource(description),
                                values[DisplayChoice.ORIENTATION] == value,
                            ) {
                                controller.set(DisplayChoice.ORIENTATION, value)
                            }
                        }
                }
            }
        DisplayEditor.THRESHOLD -> DisplayTextEditor(controller, threshold = true, onDismiss)
        DisplayEditor.SU -> DisplayTextEditor(controller, threshold = false, onDismiss)
        DisplayEditor.VIRTUAL ->
            DisplayDialog(
                stringResource(R.string.display_behavior_virtual_title),
                stringResource(R.string.display_behavior_virtual_editor_desc),
                onDismiss,
            ) {
                DisplayToggleSetting(
                    controller,
                    DisplayToggle.VIRTUAL_SCREEN,
                    R.string.display_behavior_virtual_enable,
                    R.string.display_behavior_virtual_enable_desc,
                )
                Spacer(Modifier.height(12.dp))
                DisplayDivider()
                Text(
                    stringResource(R.string.display_behavior_bitrate),
                    Modifier.padding(top = 13.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.display_behavior_bitrate_desc),
                    Modifier.padding(top = 4.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DisplayBitrateChoices(controller)
            }
        DisplayEditor.INDICATOR ->
            DisplayDialog(
                stringResource(R.string.display_behavior_indicator_title),
                stringResource(R.string.display_behavior_indicator_editor_desc),
                onDismiss,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 9.dp).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DisplayOptionTile(
                        stringResource(R.string.display_behavior_indicator_rainbow),
                        stringResource(R.string.display_behavior_indicator_rainbow_desc),
                        values[DisplayChoice.INDICATOR] == "FULLSCREEN_RAINBOW",
                        Modifier.weight(1f),
                        preview = { DisplayPhonePreview(true) },
                    ) {
                        controller.set(DisplayChoice.INDICATOR, "FULLSCREEN_RAINBOW")
                    }
                    DisplayOptionTile(
                        stringResource(R.string.display_behavior_indicator_top),
                        stringResource(R.string.display_behavior_indicator_top_desc),
                        values[DisplayChoice.INDICATOR] == "TOP_BAR",
                        Modifier.weight(1f),
                        preview = { DisplayPhonePreview(false) },
                    ) {
                        controller.set(DisplayChoice.INDICATOR, "TOP_BAR")
                    }
                }
                DisplayDialogNote(stringResource(R.string.display_behavior_indicator_note))
            }
        DisplayEditor.SCREENSHOT ->
            DisplayDialog(
                stringResource(R.string.display_behavior_screenshot_title),
                stringResource(R.string.display_behavior_screenshot_editor_desc),
                onDismiss,
            ) {
                DisplayMicroLabel(stringResource(R.string.display_behavior_image_format))
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 9.dp).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DisplayOptionTile(
                        "PNG",
                        stringResource(R.string.display_behavior_png_desc),
                        values[DisplayChoice.FORMAT] == "PNG",
                        Modifier.weight(1f),
                    ) {
                        controller.set(DisplayChoice.FORMAT, "PNG")
                    }
                    DisplayOptionTile(
                        "JPG",
                        stringResource(R.string.display_behavior_jpg_desc),
                        values[DisplayChoice.FORMAT] == "JPG",
                        Modifier.weight(1f),
                    ) {
                        controller.set(DisplayChoice.FORMAT, "JPG")
                    }
                }
                DisplayRange(
                    stringResource(R.string.display_behavior_jpg_quality),
                    stringResource(
                        if (values[DisplayChoice.FORMAT] == "PNG")
                            R.string.display_behavior_png_quality_desc
                        else R.string.display_behavior_jpg_quality_desc
                    ),
                    values[DisplayNumber.QUALITY],
                    DisplayNumber.QUALITY.range,
                    { "$it%" },
                    enabled = values[DisplayChoice.FORMAT] == "JPG",
                    onChange = { controller.set(DisplayNumber.QUALITY, it) },
                    onFinish = controller::flushNumbers,
                )
                DisplayDivider()
                DisplayRange(
                    stringResource(R.string.display_behavior_resolution),
                    stringResource(R.string.display_behavior_resolution_desc),
                    values[DisplayNumber.SCALE],
                    DisplayNumber.SCALE.range,
                    { "$it%" },
                    onChange = { controller.set(DisplayNumber.SCALE, it) },
                    onFinish = controller::flushNumbers,
                )
                DisplayDialogNote(stringResource(R.string.display_behavior_screenshot_note))
            }
        DisplayEditor.ROOT ->
            DisplayDialog(
                stringResource(R.string.display_behavior_root_title),
                stringResource(R.string.display_behavior_root_editor_desc),
                onDismiss,
            ) {
                DisplayChoiceGroup {
                    listOf(
                            Triple(
                                "AUTO",
                                R.string.display_behavior_root_auto,
                                R.string.display_behavior_root_auto_desc,
                            ),
                            Triple(
                                "FORCE_LIBSU",
                                R.string.display_behavior_root_magisk,
                                R.string.display_behavior_root_magisk_desc,
                            ),
                            Triple(
                                "FORCE_EXEC",
                                R.string.display_behavior_root_kernel,
                                R.string.display_behavior_root_kernel_desc,
                            ),
                        )
                        .forEachIndexed { index, (value, title, description) ->
                            if (index > 0) DisplayDivider()
                            DisplayChoiceRow(
                                stringResource(title),
                                stringResource(description),
                                values[DisplayChoice.ROOT] == value,
                            ) {
                                controller.set(DisplayChoice.ROOT, value)
                            }
                        }
                }
                if (values[DisplayChoice.ROOT] == "FORCE_EXEC") {
                    Spacer(Modifier.height(12.dp))
                    DisplayDivider()
                    DisplayNavigationRow(
                        stringResource(R.string.display_behavior_su_title),
                        stringResource(R.string.display_behavior_su_editor_row_desc),
                        values.suCommand,
                    ) {
                        onOpen(DisplayEditor.SU)
                    }
                }
            }
        DisplayEditor.RESET ->
            DisplayDialog(
                stringResource(R.string.display_behavior_reset_title),
                stringResource(R.string.display_behavior_reset_desc),
                onDismiss,
                footer = {
                    DisplayCancelButton(onDismiss)
                    FilledTonalButton(
                        onClick = {
                            controller.reset()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(11.dp),
                        contentPadding = PaddingValues(horizontal = 21.dp, vertical = 9.dp),
                        colors =
                            ButtonDefaults.filledTonalButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                    ) {
                        Text(
                            stringResource(R.string.display_behavior_reset_confirm),
                            fontSize = 13.sp,
                        )
                    }
                },
            ) {
                Surface(
                    Modifier.fillMaxWidth().padding(vertical = 13.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Column(
                        Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.display_behavior_reset_items_one),
                            fontSize = 12.sp,
                        )
                        Text(
                            stringResource(R.string.display_behavior_reset_items_two),
                            fontSize = 12.sp,
                        )
                    }
                }
                Text(
                    stringResource(R.string.display_behavior_reset_preserve),
                    Modifier.padding(vertical = 10.dp),
                    fontSize = 12.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
    }
}

@Composable
private fun DisplayMicroLabel(text: String) {
    Text(
        text,
        Modifier.padding(top = 16.dp, bottom = 8.dp),
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DisplayCancelButton(onDismiss: () -> Unit) {
    OutlinedButton(
        onDismiss,
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentPadding = PaddingValues(horizontal = 21.dp, vertical = 9.dp),
    ) {
        Text(
            stringResource(R.string.display_behavior_cancel),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DisplayTextEditor(
    controller: DisplayBehaviorController,
    threshold: Boolean,
    onDismiss: () -> Unit,
) {
    var text by
        rememberSaveable(threshold) {
            mutableStateOf(
                if (threshold) controller.values[DisplayNumber.TEXT_THRESHOLD].toString()
                else controller.values.suCommand
            )
        }
    var invalid by rememberSaveable(threshold) { mutableStateOf(false) }
    val save = {
        if (threshold) {
            val parsed = parseDisplayThreshold(text)
            if (parsed == null) invalid = true
            else {
                controller.set(DisplayNumber.TEXT_THRESHOLD, parsed)
                onDismiss()
            }
        } else {
            if (text.isBlank()) invalid = true
            else {
                controller.setSuCommand(text)
                onDismiss()
            }
        }
    }
    DisplayDialog(
        stringResource(
            if (threshold) R.string.display_behavior_threshold_title
            else R.string.display_behavior_su_title
        ),
        stringResource(
            if (threshold) R.string.display_behavior_threshold_desc
            else R.string.display_behavior_su_desc
        ),
        onDismiss,
        footer = {
            DisplayCancelButton(onDismiss)
            DisplayPrimaryButton(
                stringResource(
                    if (threshold) R.string.display_behavior_save_threshold
                    else R.string.display_behavior_save_command
                ),
                save,
            )
        },
    ) {
        DisplayMicroLabel(
            stringResource(
                if (threshold) R.string.display_behavior_character_count
                else R.string.display_behavior_command
            )
        )
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                invalid = false
            },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            singleLine = true,
            isError = invalid,
            shape = RoundedCornerShape(12.dp),
            textStyle = LocalTextStyle.current.copy(fontSize = if (threshold) 22.sp else 13.sp),
            suffix =
                if (threshold)
                    ({
                        Text(stringResource(R.string.display_behavior_characters), fontSize = 12.sp)
                    })
                else null,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = if (threshold) KeyboardType.Number else KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions = KeyboardActions(onDone = { save() }),
            colors =
                OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
        )
        Text(
            stringResource(
                if (threshold) R.string.display_behavior_threshold_range
                else R.string.display_behavior_su_example
            ),
            Modifier.padding(top = 12.dp),
            fontSize = 12.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 始终预留错误行，与 HTML 的字段高度和底部留白一致。
        Text(
            if (invalid)
                stringResource(
                    if (threshold) R.string.display_behavior_threshold_error
                    else R.string.display_behavior_su_error
                )
            else "",
            Modifier.fillMaxWidth().heightIn(min = 30.dp).padding(top = 6.dp),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DisplayBitrateChoices(controller: DisplayBehaviorController) {
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 16.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        listOf(1500, 3000, 5000, 10000, 20000).forEach { value ->
            val selected = controller.values[DisplayNumber.BITRATE] == value
            Surface(
                shape = RoundedCornerShape(9.dp),
                color =
                    if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                border =
                    BorderStroke(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                    ),
            ) {
                Box(
                    Modifier.selectable(
                            selected,
                            role = Role.RadioButton,
                            onClick = { controller.set(DisplayNumber.BITRATE, value) },
                        )
                        .heightIn(min = 40.dp)
                        .padding(horizontal = 11.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        displayBitrate(value),
                        fontSize = 12.sp,
                        color =
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun DisplayToolPreview(mode: String) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(13.dp)) {
            Text(
                stringResource(R.string.display_behavior_tool_preview_title),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                Modifier.fillMaxWidth(0.9f).padding(top = 10.dp),
                shape =
                    RoundedCornerShape(
                        topStart = 2.dp,
                        topEnd = 10.dp,
                        bottomStart = 10.dp,
                        bottomEnd = 10.dp,
                    ),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Text(
                    stringResource(R.string.display_behavior_tool_preview_message),
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    fontSize = 12.sp,
                )
            }
            Surface(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(9.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.Build,
                        null,
                        Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(
                            when (mode) {
                                "read_only" -> R.string.display_behavior_preview_reads
                                "all" -> R.string.display_behavior_preview_tools
                                else -> R.string.display_behavior_preview_tool
                            }
                        ),
                        Modifier.weight(1f),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.display_behavior_preview_expand),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (mode == "read_only")
                Text(
                    stringResource(R.string.display_behavior_preview_write),
                    Modifier.padding(start = 12.dp, top = 7.dp),
                    fontSize = 10.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
    }
}

@Composable
private fun DisplayPhonePreview(rainbow: Boolean) {
    val border =
        if (rainbow)
            Brush.sweepGradient(
                listOf(Color(0xFFB6B6DF), Color(0xFF87BEA4), Color(0xFFD0AD8E), Color(0xFFB6B6DF))
            )
        else
            Brush.linearGradient(
                listOf(MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.outline)
            )
    Box(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.size(96.dp, 98.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(2.dp, border, RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(20.dp, 3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline)
            )
            Spacer(Modifier.height(if (rainbow) 10.dp else 4.dp))
            if (!rainbow) {
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.display_behavior_executing),
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.primary,
                        lineHeight = 12.sp,
                    )
                }
                Spacer(Modifier.height(3.dp))
            }
            listOf(0.8f, 0.54f, 0.66f).forEachIndexed { index, width ->
                Box(
                    Modifier.align(if (index == 2) Alignment.End else Alignment.Start)
                        .fillMaxWidth(width)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (index == 2) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.outline
                        )
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
