package com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench

import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptActionButton
import com.ai.assistance.operit.ui.theme.script.themeScriptShape
import com.ai.assistance.operit.ui.theme.script.themeScriptAvatarSize
import com.ai.assistance.operit.ui.theme.script.themeScriptContentColor
import com.ai.assistance.operit.ui.theme.script.themeScriptFill
import com.ai.assistance.operit.ui.theme.script.themeScriptMessageFraction
import com.ai.assistance.operit.ui.theme.script.themeScriptTextStyle
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIcon


import com.ai.assistance.operit.ui.theme.script.ThemeScriptSurface
import com.ai.assistance.operit.ui.theme.script.themeScriptDecoration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.glass.GlassRegion
import com.ai.assistance.operit.data.preferences.transparency.ThemeTransparencyPreferences
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.style.bubble.BubbleImageBackgroundSurface
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.style.bubble.BubbleImageStyleConfig
import com.ai.assistance.operit.ui.theme.*

@Composable
internal fun ThemePreviewMessage(values: ThemePreferenceValues, user: Boolean, userName: String, markdownPreview: Boolean = false) {
    ThemePreviewMessageSample(
        values, user, userName,
        message = stringResource(if (user) R.string.chat_style_preview_user_message else R.string.chat_style_preview_ai_message),
        markdownPreview = markdownPreview,
    )
}

/** 工作室使用简洁消息正文，主题页与布局页继续展示完整元信息。 */
@Composable
internal fun ThemePreviewMessageSample(
    values: ThemePreferenceValues,
    user: Boolean,
    userName: String,
    message: String,
    markdownPreview: Boolean = false,
    showDetails: Boolean = true,
) {
    val context = LocalContext.current
    val preferences = remember(context) { DisplayPreferencesManager.getInstance(context) }
    val globalAvatar by preferences.globalUserAvatarUri.collectAsState(initial = null)
    val cursor = values.requiredString(if (user) "user_chat_style" else "ai_chat_style") == UserPreferencesManager.CHAT_STYLE_CURSOR
    val side = if (user) "user" else "ai"
    val prefix = "bubble_" + side
    val region = if (user) GlassRegion.USER_MESSAGE else GlassRegion.AI_MESSAGE
    val glassPrefix = if (cursor) "cursor_user_bubble" else prefix + "_bubble"
    val water =
        (!cursor || user) &&
            values.requiredBoolean(glassPrefix + "_water_glass") &&
            isWaterGlassSupported()
    val liquid =
        (!cursor || user) &&
            !water &&
            values.requiredBoolean(glassPrefix + "_liquid_glass") &&
            isLiquidGlassSupported()
    val opacityKey =
        if (cursor) {
            if (user) ThemeTransparencyPreferences.CURSOR_USER
            else ThemeTransparencyPreferences.CURSOR_AI
        } else if (user) ThemeTransparencyPreferences.BUBBLE_USER
        else ThemeTransparencyPreferences.BUBBLE_AI
    val explicitOpacity = values.float(opacityKey)?.coerceIn(0f, 1f)
    val defaultColor =
        if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val nativeBaseColor =
        if (cursor) {
            if (!user) MaterialTheme.colorScheme.surface.copy(alpha = 0f)
            else if (values.requiredBoolean("cursor_user_bubble_follow_theme")) defaultColor
            else Color(values.int("cursor_user_bubble_color") ?: defaultColor.toArgb())
        } else Color(values.int(prefix + "_bubble_color") ?: defaultColor.toArgb())
    val area = if (user) "userMessage" else "assistantMessage"
    val regionOpacity = explicitOpacity ?: nativeBaseColor.alpha
    val baseColor = themeScriptFill(area, nativeBaseColor, regionOpacity)
    val nativeTextColor =
        if (cursor) MaterialTheme.colorScheme.onSurface
        else
            Color(
                values.int(prefix + "_text_color")
                    ?: getTextColorForBackground(baseColor.copy(alpha = 1f)).toArgb()
            )
    val textColor = themeScriptContentColor(if (user) "userMessage" else "assistantMessage", nativeTextColor)
    val rounded =
        if (user) values.requiredBoolean("bubble_rounded_corners_enabled")
        else values.requiredBoolean("bubble_ai_rounded_corners_enabled")
    val shape = themeScriptShape(if (user) "userMessage" else "assistantMessage", RoundedCornerShape(if (cursor || rounded) 14.dp else 0.dp))
    val family =
        remember(
            context,
            user,
            cursor,
            values.boolean(prefix + "_use_custom_font"),
            values.string(prefix + "_font_type"),
            values.string(prefix + "_system_font_name"),
            values.string(prefix + "_custom_font_path"),
        ) {
            if (cursor) null
            else
                resolveConfiguredFontFamily(
                    context,
                    values.requiredBoolean(prefix + "_use_custom_font"),
                    values.requiredString(prefix + "_font_type"),
                    values.requiredString(prefix + "_system_font_name"),
                    values.string(prefix + "_custom_font_path"),
                )
        }
    val avatar =
        if (user) values.string("custom_user_avatar_uri") ?: globalAvatar
        else values.string("custom_ai_avatar_uri")
    val alignment = if (user && !cursor) Alignment.End else Alignment.Start
    var thoughtExpanded by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        val names =
            if (user) {
                if (values.requiredBoolean("show_user_name")) userName else ""
            } else
                listOfNotNull(
                        if (values.requiredBoolean("show_model_provider")) "Operit" else null,
                        if (values.requiredBoolean("show_model_name"))
                            stringResource(R.string.theme_workbench_example_model)
                        else null,
                        if (values.requiredBoolean("show_role_name"))
                            values.string("custom_chat_title")
                                ?: stringResource(R.string.ai_avatar_label)
                        else null,
                    )
                    .joinToString(" · ")
        if (showDetails && names.isNotBlank())
            Text(
                names,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        if (showDetails && !user && values.requiredBoolean("show_thinking_process")) {
            Text(
                stringResource(R.string.show_thinking_process),
                Modifier.clickable { thoughtExpanded = !thoughtExpanded },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (thoughtExpanded)
                Text(
                    stringResource(R.string.theme_workbench_preview_thought),
                    style = MaterialTheme.typography.bodySmall,
                )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (user && !cursor) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top,
        ) {
            if (!cursor && values.requiredBoolean("bubble_show_avatar") && !user)
                ThemePreviewAvatar(avatar, values, user)
            val wide = !cursor && values.requiredBoolean("bubble_wide_layout_enabled")
            val bubbleModifier =
                if (wide || cursor) Modifier.weight(1f)
                else Modifier.widthIn(max = 250.dp).weight(1f, fill = false)
            val content: @Composable () -> Unit = {
                if (markdownPreview && !user) {
                    com.ai.assistance.operit.ui.common.displays.MarkdownTextComposable(
                        text = stringResource(R.string.layout_adjustment_preview_markdown).replace("\\n", "\n"),
                        textColor = textColor,
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    )
                } else Text(
                    message,
                    style = themeScriptTextStyle(if (user) "userMessage" else "assistantMessage", MaterialTheme.typography.bodyMedium),
                    color = textColor,
                    fontFamily = themeScriptTextStyle(if (user) "userMessage" else "assistantMessage", MaterialTheme.typography.bodyMedium.copy(fontFamily = family)).fontFamily,
                )
            }
            val uri = values.string(prefix + "_image_uri")
            if (
                !cursor &&
                    !water &&
                    !liquid &&
                    values.requiredBoolean(prefix + "_use_image") &&
                    !uri.isNullOrBlank()
            ) {
                val image =
                    BubbleImageStyleConfig(
                        imageUri = uri,
                        cropLeftRatio = values.requiredFloat(prefix + "_image_crop_left"),
                        cropTopRatio = values.requiredFloat(prefix + "_image_crop_top"),
                        cropRightRatio = values.requiredFloat(prefix + "_image_crop_right"),
                        cropBottomRatio = values.requiredFloat(prefix + "_image_crop_bottom"),
                        repeatXStartRatio = values.requiredFloat(prefix + "_image_repeat_start"),
                        repeatXEndRatio = values.requiredFloat(prefix + "_image_repeat_end"),
                        repeatYStartRatio = values.requiredFloat(prefix + "_image_repeat_y_start"),
                        repeatYEndRatio = values.requiredFloat(prefix + "_image_repeat_y_end"),
                        imageScale = values.requiredFloat(prefix + "_image_scale"),
                        renderMode = values.requiredString("bubble_image_render_mode"),
                        backgroundOpacity = explicitOpacity ?: 1f,
                    )
                BubbleImageBackgroundSurface(
                    image,
                    shape,
                    bubbleModifier,
                    contentPadding =
                        PaddingValues(
                            start =
                                values
                                    .requiredFloat(
                                        if (user) "bubble_content_padding_left"
                                        else "bubble_ai_content_padding_left"
                                    )
                                    .dp,
                            end =
                                values
                                    .requiredFloat(
                                        if (user) "bubble_content_padding_right"
                                        else "bubble_ai_content_padding_right"
                                    )
                                    .dp,
                            top = 12.dp,
                            bottom = 12.dp,
                        ),
                ) {
                    ThemeScriptSurface(if (user) "userMessage" else "assistantMessage", explicitOpacity ?: 1f) { content() }
                }
            } else {
                Surface(
                    modifier =
                        bubbleModifier
                            .waterGlass(
                                water,
                                shape,
                                baseColor,
                                backgroundOpacity = explicitOpacity,
                                region = region,
                            )
                            .liquidGlass(
                                liquid,
                                shape,
                                baseColor,
                                backgroundOpacity = explicitOpacity,
                                region = region,
                            ),
                    shape = shape,
                    color =
                        if (water || liquid) Color.Transparent
                        else baseColor,
                ) {
                    Box(Modifier.themeScriptDecoration(area, regionOpacity, drawFill = false).padding(12.dp)) { content() }
                }
            }
            if (!cursor && values.requiredBoolean("bubble_show_avatar") && user)
                ThemePreviewAvatar(avatar, values, user)
        }
        if (showDetails && !user) {
            val meta = buildList {
                if (values.requiredBoolean("show_message_token_stats")) add("128 tokens")
                if (
                    values.requiredBoolean("show_message_token_stats") &&
                        values.requiredBoolean("show_message_token_speed")
                )
                    add("42 tokens/s")
                if (values.requiredBoolean("show_message_timing_stats")) add("0.8 s")
                if (values.requiredBoolean("show_message_timestamp")) add("12:30")
                if (values.requiredBoolean("show_status_tags"))
                    add(stringResource(R.string.theme_workbench_example_complete))
            }
            if (meta.isNotEmpty())
                Text(
                    meta.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
    }
}

@Composable
private fun ThemePreviewAvatar(uri: String?, values: ThemePreferenceValues, user: Boolean) {
    val shape =
        if (values.requiredString("avatar_shape") == UserPreferencesManager.AVATAR_SHAPE_CIRCLE)
            RoundedCornerShape(50)
        else RoundedCornerShape(values.requiredFloat("avatar_corner_radius").dp)
    Box(
        Modifier.padding(horizontal = 6.dp)
            .size(themeScriptAvatarSize(if (user) "userAvatar" else "assistantAvatar", 27.dp))
            .clip(themeScriptShape(if (user) "userAvatar" else "assistantAvatar", shape))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .themeScriptDecoration(if (user) "userAvatar" else "assistantAvatar", originalShape = shape),
        contentAlignment = Alignment.Center,
    ) {
        if (uri != null)
            AsyncImage(
                uri,
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        else Icon(Icons.Default.Person, null, Modifier.size(18.dp))
    }
}

@Composable
internal fun ThemePreviewInput(values: ThemePreferenceValues) {
    val classic = values.requiredString("input_style") == UserPreferencesManager.INPUT_STYLE_CLASSIC
    val rawOpacity = values.float(ThemeTransparencyPreferences.INPUT)?.coerceIn(0f, 1f)
    val transparent =
        rawOpacity?.let { it < 1f } ?: values.requiredBoolean("chat_input_transparent")
    val water =
        transparent && values.requiredBoolean("chat_input_water_glass") && isWaterGlassSupported()
    val liquid =
        transparent &&
            !water &&
            values.requiredBoolean("chat_input_liquid_glass") &&
            isLiquidGlassSupported()
    val floating = values.requiredBoolean("chat_input_floating")
    val shape = themeScriptShape("composer", RoundedCornerShape(if (floating) 28.dp else if (classic) 4.dp else 16.dp))
    val baseColor = themeScriptFill("composer", MaterialTheme.colorScheme.surface, rawOpacity ?: if (transparent) 0f else 0.9f)
    var generating by remember { mutableStateOf(false) }
    LaunchedEffect(generating) {
        if (generating) {
            kotlinx.coroutines.delay(1000)
            generating = false
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (generating && values.requiredBoolean("show_input_processing_status"))
            Text(
                stringResource(R.string.theme_workbench_example_generating),
                style = MaterialTheme.typography.labelSmall,
            )
        if (generating && values.requiredBoolean("show_chat_floating_dots_animation"))
            Text(
                "· · ·",
                modifier = Modifier.padding(top = 8.dp),
                color = Color(values.int("thinking_particle_color") ?: Color.White.toArgb()),
                style = MaterialTheme.typography.titleLarge,
            )
        Surface(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = if (floating) 8.dp else 0.dp)
                    .waterGlass(
                        water,
                        shape,
                        baseColor,
                        backgroundOpacity = rawOpacity,
                        region = GlassRegion.INPUT,
                    )
                    .liquidGlass(
                        liquid,
                        shape,
                        baseColor,
                        backgroundOpacity = rawOpacity,
                        region = GlassRegion.INPUT,
                    ),
            shape = shape,
            color =
                if (water || liquid) Color.Transparent
                else baseColor,
        ) {
            Row(
                Modifier.fillMaxWidth().themeScriptDecoration("composer", rawOpacity ?: if (transparent) 0f else 0.9f, drawFill = false).padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeScriptActionButton(
                    actionId = if (classic) "composer.attachment" else "composer.settings",
                    onClick = {},
                    modifier = Modifier.size(30.dp),
                ) {
                    ThemeScriptIcon(
                        id = if (classic) "composer.attachment" else "composer.settings",
                        imageVector = if (classic) Icons.Default.Add else Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    stringResource(R.string.theme_workbench_example_input),
                    Modifier.weight(1f),
                    style = themeScriptTextStyle("composer", MaterialTheme.typography.bodySmall),
                    color = themeScriptContentColor("composer", MaterialTheme.colorScheme.onSurface),
                )
                ThemeScriptIconButton(
                    actionId = "composer.send",
                    onClick = { generating = true },
                    enabled = !generating,
                ) {
                    ThemeScriptIcon(
                        id = "composer.send",
                        imageVector = Icons.Default.Send,
                        contentDescription = stringResource(R.string.theme_workbench_play_example),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
