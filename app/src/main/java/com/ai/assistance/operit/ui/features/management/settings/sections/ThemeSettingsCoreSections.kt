package com.ai.assistance.operit.ui.features.management.settings.sections

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.style.bubble.BubbleImageStyleConfig
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.ThemeEditorSession
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchCard
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchDisclosure
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchDivider
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchFontEditor
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchSegments
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchSwitchRow

@Composable
internal fun ThemeSettingsThemeModeSection(
    cardColors: CardColors,
    editorSession: ThemeEditorSession,
    useSystemThemeInput: Boolean,
    themeModeInput: String,
) {
    ThemeWorkbenchCard(
        stringResource(R.string.theme_title_mode),
        icon = Icons.Default.Brightness6,
    ) {
        ThemeWorkbenchSegments(
            options =
                listOf(
                    "system" to stringResource(R.string.theme_follow_system),
                    UserPreferencesManager.THEME_MODE_LIGHT to stringResource(R.string.theme_light),
                    UserPreferencesManager.THEME_MODE_DARK to stringResource(R.string.theme_dark),
                ),
            selected = if (useSystemThemeInput) "system" else themeModeInput,
        ) { mode ->
            editorSession.update { current ->
                val updated = current.withBoolean("use_system_theme", mode == "system")
                if (mode == "system") updated else updated.withString("theme_mode", mode)
            }
        }
    }
}

@Composable
internal fun BubbleFontStyleEditor(
    title: String,
    useCustomFont: Boolean,
    onUseCustomFontChange: (Boolean) -> Unit,
    fontType: String,
    onFontTypeChange: (String) -> Unit,
    systemFontName: String,
    onSystemFontNameChange: (String) -> Unit,
    customFontPath: String?,
    onPickFont: () -> Unit,
    onClearFont: () -> Unit,
) {
    ThemeWorkbenchFontEditor(
        custom = useCustomFont,
        onCustomChange = onUseCustomFontChange,
        type = fontType,
        onTypeChange = onFontTypeChange,
        name = systemFontName,
        onNameChange = onSystemFontNameChange,
        path = customFontPath,
        onPick = onPickFont,
        onClear = onClearFont,
        message = true,
    )
}

@Composable
internal fun NineSliceSourcePreviewCard(title: String, imageStyle: BubbleImageStyleConfig) {
    val bitmap = rememberSourcePreviewBitmap(imageStyle.imageUri)

    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
            ),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            bitmap?.let { previewBitmap ->
                Canvas(modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(84.dp)) {
                    val srcWidth = previewBitmap.width.coerceAtLeast(1)
                    val srcHeight = previewBitmap.height.coerceAtLeast(1)
                    val fitScale =
                        min(size.width / srcWidth.toFloat(), size.height / srcHeight.toFloat())

                    val drawWidth = max(1, (srcWidth * fitScale).roundToInt())
                    val drawHeight = max(1, (srcHeight * fitScale).roundToInt())
                    val drawLeft = ((size.width - drawWidth.toFloat()) / 2f).roundToInt()
                    val drawTop = ((size.height - drawHeight.toFloat()) / 2f).roundToInt()

                    drawImage(
                        image = previewBitmap,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(srcWidth, srcHeight),
                        dstOffset = IntOffset(drawLeft, drawTop),
                        dstSize = IntSize(drawWidth, drawHeight),
                    )

                    val cropLeft = imageStyle.cropLeftRatio.coerceIn(0f, 0.45f)
                    val cropTop = imageStyle.cropTopRatio.coerceIn(0f, 0.45f)
                    val cropRight = imageStyle.cropRightRatio.coerceIn(0f, 0.45f)
                    val cropBottom = imageStyle.cropBottomRatio.coerceIn(0f, 0.45f)
                    val repeatXStart = imageStyle.repeatXStartRatio.coerceIn(0.05f, 0.9f)
                    val repeatXEnd =
                        imageStyle.repeatXEndRatio.coerceIn(repeatXStart + 0.01f, 0.95f)
                    val repeatYStart = imageStyle.repeatYStartRatio.coerceIn(0.05f, 0.9f)
                    val repeatYEnd =
                        imageStyle.repeatYEndRatio.coerceIn(repeatYStart + 0.01f, 0.95f)

                    val srcLeft = (srcWidth * cropLeft).roundToInt().coerceIn(0, srcWidth - 1)
                    val srcTop = (srcHeight * cropTop).roundToInt().coerceIn(0, srcHeight - 1)
                    val srcRight =
                        (srcWidth * (1f - cropRight)).roundToInt().coerceIn(srcLeft + 1, srcWidth)
                    val srcBottom =
                        (srcHeight * (1f - cropBottom)).roundToInt().coerceIn(srcTop + 1, srcHeight)
                    val croppedWidth = (srcRight - srcLeft).coerceAtLeast(1)
                    val croppedHeight = (srcBottom - srcTop).coerceAtLeast(1)

                    val repeatStartXPx =
                        if (croppedWidth < 3) {
                            0
                        } else {
                            (croppedWidth * repeatXStart).roundToInt().coerceIn(1, croppedWidth - 2)
                        }
                    val repeatEndXPx =
                        if (croppedWidth < 3) {
                            croppedWidth
                        } else {
                            (croppedWidth * repeatXEnd)
                                .roundToInt()
                                .coerceIn(repeatStartXPx + 1, croppedWidth - 1)
                        }
                    val repeatStartYPx =
                        if (croppedHeight < 3) {
                            0
                        } else {
                            (croppedHeight * repeatYStart)
                                .roundToInt()
                                .coerceIn(1, croppedHeight - 2)
                        }
                    val repeatEndYPx =
                        if (croppedHeight < 3) {
                            croppedHeight
                        } else {
                            (croppedHeight * repeatYEnd)
                                .roundToInt()
                                .coerceIn(repeatStartYPx + 1, croppedHeight - 1)
                        }

                    val cropLeftX = drawLeft + srcLeft * fitScale
                    val cropRightX = drawLeft + srcRight * fitScale
                    val cropTopY = drawTop + srcTop * fitScale
                    val cropBottomY = drawTop + srcBottom * fitScale
                    val leftX = drawLeft + (srcLeft + repeatStartXPx) * fitScale
                    val rightX = drawLeft + (srcLeft + repeatEndXPx) * fitScale
                    val topY = drawTop + (srcTop + repeatStartYPx) * fitScale
                    val bottomY = drawTop + (srcTop + repeatEndYPx) * fitScale
                    val lineColor = Color.Red.copy(alpha = 0.9f)
                    val borderColor = Color.Red.copy(alpha = 0.4f)
                    val strokeWidth = 1.dp.toPx().coerceAtLeast(1f)

                    // Crop border.
                    drawLine(
                        color = borderColor,
                        start = androidx.compose.ui.geometry.Offset(cropLeftX, cropTopY),
                        end = androidx.compose.ui.geometry.Offset(cropRightX, cropTopY),
                        strokeWidth = strokeWidth,
                    )
                    drawLine(
                        color = borderColor,
                        start = androidx.compose.ui.geometry.Offset(cropLeftX, cropBottomY),
                        end = androidx.compose.ui.geometry.Offset(cropRightX, cropBottomY),
                        strokeWidth = strokeWidth,
                    )
                    drawLine(
                        color = borderColor,
                        start = androidx.compose.ui.geometry.Offset(cropLeftX, cropTopY),
                        end = androidx.compose.ui.geometry.Offset(cropLeftX, cropBottomY),
                        strokeWidth = strokeWidth,
                    )
                    drawLine(
                        color = borderColor,
                        start = androidx.compose.ui.geometry.Offset(cropRightX, cropTopY),
                        end = androidx.compose.ui.geometry.Offset(cropRightX, cropBottomY),
                        strokeWidth = strokeWidth,
                    )

                    // 9-slice grid lines inside cropped area.
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(leftX, cropTopY),
                        end = androidx.compose.ui.geometry.Offset(leftX, cropBottomY),
                        strokeWidth = strokeWidth,
                    )
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(rightX, cropTopY),
                        end = androidx.compose.ui.geometry.Offset(rightX, cropBottomY),
                        strokeWidth = strokeWidth,
                    )
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(cropLeftX, topY),
                        end = androidx.compose.ui.geometry.Offset(cropRightX, topY),
                        strokeWidth = strokeWidth,
                    )
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(cropLeftX, bottomY),
                        end = androidx.compose.ui.geometry.Offset(cropRightX, bottomY),
                        strokeWidth = strokeWidth,
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberSourcePreviewBitmap(uriString: String): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(uriString) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(context, uriString) {
        if (uriString.isBlank()) {
            bitmap = null
            return@LaunchedEffect
        }
        bitmap =
            withContext(Dispatchers.IO) {
                runCatching {
                        context.contentResolver.openInputStream(Uri.parse(uriString))?.use { input
                            ->
                            BitmapFactory.decodeStream(input)?.asImageBitmap()
                        }
                    }
                    .getOrNull()
            }
    }

    return bitmap
}

@Composable
internal fun ThemeSettingsDisplayOptionsSection(
    cardColors: CardColors,
    editorSession: ThemeEditorSession,
    showThinkingProcessInput: Boolean,
    showStatusTagsInput: Boolean,
    showModelProviderInput: Boolean,
    showModelNameInput: Boolean,
    showRoleNameInput: Boolean,
    showUserNameInput: Boolean,
    showMessageTokenStatsInput: Boolean,
    showMessageTimingStatsInput: Boolean,
    showMessageTokenSpeedInput: Boolean,
    showMessageTimestampInput: Boolean,
    showInputProcessingStatusInput: Boolean,
    showChatFloatingDotsAnimationInput: Boolean,
) {
    val values by editorSession.values.collectAsState()
    val options =
        listOf(
            Triple("show_thinking_process", R.string.show_thinking_process, R.string.show_thinking_process_desc),
            Triple("show_model_provider", R.string.show_model_provider, R.string.show_model_provider_description),
            Triple("show_model_name", R.string.show_model_name, R.string.show_model_name_description),
            Triple("show_role_name", R.string.show_role_name, R.string.show_role_name_description),
            Triple("show_user_name", R.string.show_user_name, R.string.show_user_name_description),
            Triple("show_message_token_stats", R.string.show_message_token_stats, R.string.show_message_token_stats_description),
            Triple("show_message_token_speed", R.string.show_message_token_speed, R.string.show_message_token_speed_description),
            Triple("show_message_timing_stats", R.string.show_message_timing_stats, R.string.show_message_timing_stats_description),
            Triple("show_message_timestamp", R.string.show_message_timestamp, R.string.show_message_timestamp_description),
            Triple("show_status_tags", R.string.show_status_tags, R.string.show_status_tags_desc),
            Triple("show_input_processing_status", R.string.show_input_processing_status, R.string.show_input_processing_status_desc),
            Triple("show_chat_floating_dots_animation", R.string.show_chat_floating_dots_animation, R.string.show_chat_floating_dots_animation_desc),
        )
    ThemeWorkbenchDisclosure(
        title = stringResource(R.string.theme_workbench_message_information),
        summary =
            stringResource(
                R.string.theme_workbench_enabled_count,
                options.count { values.requiredBoolean(it.first) },
                options.size,
            ),
    ) {
        options.forEachIndexed { index, (key, label, description) ->
            if (index > 0) ThemeWorkbenchDivider()
            ThemeWorkbenchSwitchRow(stringResource(label), values.requiredBoolean(key), subtitle = stringResource(description)) {
                editorSession.setBoolean(key, it)
            }
        }
    }
}

@Composable
internal fun ThemeSettingsSectionTitle(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
}
