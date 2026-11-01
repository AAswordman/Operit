package com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench

import com.ai.assistance.operit.ui.theme.script.themeScriptDecoration

import com.ai.assistance.operit.ui.theme.script.ThemeScriptHost

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.resources.theme.ThemeScriptBundle
import com.ai.assistance.operit.ui.theme.script.ThemeScriptSurface
import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptOutlinedButton
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.ThemeEditorSession
import com.ai.assistance.operit.ui.theme.*
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.rememberLiquidState

/** 预览使用当前草稿及正式颜色、字体、图片气泡和玻璃渲染，不写入角色主题。 */
@Composable
internal fun ThemeWorkbenchPreview(
    editorSession: ThemeEditorSession,
    full: Boolean = false,
    layoutPreview: Boolean = false,
    showDescription: Boolean = true,
    draft: ThemeScriptBundle? = null,
    previewMode: String? = null,
    showComponentSamples: Boolean = false,
) {
    val values by editorSession.values.collectAsState()
    val userName by editorSession.globalUserNameFlow.collectAsState()
    val layoutSettings by editorSession.layout.collectAsState()
    var expanded by rememberSaveable { mutableStateOf(false) }
    ThemeWorkbenchCard(
        stringResource(R.string.theme_workbench_live_preview),
        icon = Icons.Default.Visibility,
        subtitle = if (showDescription) stringResource(R.string.theme_workbench_preview_description) else null,
        onHeaderClick = if (full) null else ({ expanded = !expanded }),
        trailing = {
            if (!full)
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    stringResource(
                        if (expanded) R.string.theme_workbench_collapse
                        else R.string.theme_workbench_expand
                    ),
                )
        },
    ) {
        if (full || expanded) {
            val context = LocalContext.current
            val currentSnapshot = LocalThemePreferenceSnapshot.current
            val snapshot =
                remember(currentSnapshot, values, previewMode) {
                    val previewValues = if (previewMode == null) values else values
                        .withBoolean("use_system_theme", false).withString("theme_mode", previewMode)
                    currentSnapshot.copy(values = previewValues)
                }
            val colors = remember(context, snapshot) { resolveThemeColorScheme(context, snapshot) }
            val typography =
                remember(
                    context,
                    values.boolean("use_custom_font"),
                    values.string("font_type"),
                    values.string("system_font_name"),
                    values.string("custom_font_path"),
                    values.float("font_scale"),
                ) {
                    createCustomTypography(
                        context,
                        values.requiredBoolean("use_custom_font"),
                        values.requiredString("font_type"),
                        values.requiredString("system_font_name"),
                        values.string("custom_font_path"),
                        values.requiredFloat("font_scale"),
                    )
                }
            val backdrop = rememberLayerBackdrop()
            val water = if (isWaterGlassSupported()) rememberLiquidState() else null
            CompositionLocalProvider(
                LocalThemePreferenceSnapshot provides snapshot,
                LocalLiquidGlassBackdrop provides backdrop,
                LocalWaterGlassState provides water,
            ) {
                MaterialTheme(colorScheme = colors, typography = typography) {
                    ThemeScriptHost(snapshot, draft = draft) {
                    Box(Modifier.fillMaxWidth().padding(8.dp).clip(RoundedCornerShape(14.dp))) {
                        Box(
                            Modifier.matchParentSize()
                                .layerBackdrop(backdrop)
                                .then(if (water != null) Modifier.liquefiable(water) else Modifier)
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            ThemePreviewBackground(values)
                            Box(Modifier.fillMaxSize().themeScriptDecoration("background", values.requiredFloat("background_image_opacity")))
                        }
                        if (layoutPreview) {
                            com.ai.assistance.operit.ui.features.management.settings.screens.theme.layout
                                .ThemeLayoutPreviewContent(values, layoutSettings, userName)
                        } else Column(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            ThemePreviewMessage(values, user = true, userName = userName)
                            ThemePreviewMessage(values, user = false, userName = userName)
                            ThemePreviewInput(values)
                            if (showComponentSamples) {
                                ThemeScriptSurface("settingsCard") {
                                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(stringResource(R.string.theme_area_settings_card), style = MaterialTheme.typography.titleSmall)
                                        ThemeScriptSurface("navigationItem", selected = true) {
                                            Text(stringResource(R.string.theme_area_navigation_item), Modifier.fillMaxWidth().padding(8.dp))
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            ThemeScriptButton(onClick = {}) { Text(stringResource(R.string.theme_area_button)) }
                                            ThemeScriptOutlinedButton(onClick = {}, enabled = false) { Text(stringResource(R.string.theme_appearance_disabled)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
            Text(
                stringResource(R.string.theme_message_style_summary,
                    themeMessageStyleLabel(values.requiredString("user_chat_style")),
                    themeMessageStyleLabel(values.requiredString("ai_chat_style"))),
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ThemePreviewBackground(values: ThemePreferenceValues) {
    if (!values.requiredBoolean("use_background_image")) return
    val uri = values.string("background_image_uri") ?: return
    val opacity = values.requiredFloat("background_image_opacity").coerceIn(0f, 1f)
    val blur =
        if (values.requiredBoolean("use_background_blur"))
            values.requiredFloat("background_blur_radius").dp
        else 0.dp
    val modifier = Modifier.fillMaxSize().alpha(opacity).blur(blur)
    if (values.requiredString("background_media_type") == UserPreferencesManager.MEDIA_TYPE_IMAGE) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        // 内嵌预览始终静音，避免与主界面正在播放的角色背景叠加音轨；声音在背景编辑窗口试听。
        ThemePreviewVideo(uri, true, values.requiredBoolean("video_background_loop"), modifier)
    }
}

@Composable
private fun ThemePreviewVideo(uri: String, muted: Boolean, loop: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val focused = LocalWindowInfo.current.isWindowFocused
    val player =
        remember(context, uri) {
            ExoPlayer.Builder(context)
                .setLoadControl(
                    DefaultLoadControl.Builder().setBufferDurationsMs(1000, 3000, 500, 1000).build()
                )
                .build()
                .apply {
                    volume = 0f
                    setMediaItem(MediaItem.fromUri(uri))
                    prepare()
                }
        }
    LaunchedEffect(player, muted, loop) {
        player.volume = if (muted) 0f else 1f
        player.repeatMode = if (loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
    }
    DisposableEffect(player, lifecycle, focused) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> player.playWhenReady = focused
                Lifecycle.Event.ON_STOP -> player.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        player.playWhenReady = focused && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        modifier = modifier,
        factory = {
            PlayerView(it).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                this.player = player
            }
        },
        update = { it.player = player },
    )
}
