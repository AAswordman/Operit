package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import com.ai.assistance.operit.ui.theme.script.text.themeTextStroke
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.theme.script.ThemeScriptContract
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.LocalThemePreviewUserName
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemePreviewBackground
import com.ai.assistance.operit.ui.theme.*
import com.ai.assistance.operit.ui.theme.script.*
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.rememberLiquidState

/** 与正式页面共用脚本宿主、角色参数、字体和绘制函数，点击样例只选择制作对象。 */
@Composable
internal fun StudioPreview(state: ThemeStudioState, viewport: IntSize, modifier: Modifier = Modifier, fullScreen: Boolean = false) {
    val context = LocalContext.current
    val values by state.session.values.collectAsState()
    val userName by state.session.globalUserNameFlow.collectAsState()
    val current = LocalThemePreferenceSnapshot.current
    val inputStyle = if (state.target.id in ThemeScriptContract.popupSurfaceIds) "agent" else state.previewInputStyle
    val snapshot = remember(current, values, state.mode, inputStyle) { current.copy(values = values.withBoolean("use_system_theme", false).withString("theme_mode", state.mode).withString("input_style", inputStyle)) }
    val colors = remember(context, snapshot) { resolveThemeColorScheme(context, snapshot) }
    val typography = remember(context, values) {
        createCustomTypography(context, values.requiredBoolean("use_custom_font"), values.requiredString("font_type"), values.requiredString("system_font_name"), values.string("custom_font_path"), values.requiredFloat("font_scale"))
    }
    val backdrop = rememberLayerBackdrop()
    val water = if (isWaterGlassSupported()) rememberLiquidState() else null
    StudioPreviewCanvas(viewport, modifier.padding(horizontal = 12.dp, vertical = 8.dp), fitWidth = !fullScreen, focusTarget = state.target.id) {
        CompositionLocalProvider(LocalThemePreferenceSnapshot provides snapshot, LocalLiquidGlassBackdrop provides backdrop, LocalWaterGlassState provides water) {
            MaterialTheme(colorScheme = colors, typography = typography) {
                ThemeScriptHost(snapshot, draft = state.bundle) {
                    val previewStates = if (state.target.kind == StudioKind.Surface && state.property == StudioProperty.States) {
                        val ids = if (state.target.id == "iconButton") ThemeScriptContract.actionButtonIds + "iconButton" else setOf(state.target.id)
                        ids.associateWith { state.previewState }
                    } else emptyMap()
                    // 预览主题与编辑器独立，默认前景色必须随预览色板一起切换。
                    CompositionLocalProvider(
                        LocalThemeSurfaceStates provides previewStates,
                        LocalThemePreviewUserName provides userName,
                        LocalContentColor provides themeScriptContentColor("background", MaterialTheme.colorScheme.onBackground),
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.matchParentSize().layerBackdrop(backdrop).then(if (water == null) Modifier else Modifier.liquefiable(water)).background(MaterialTheme.colorScheme.background)) {
                                ThemePreviewBackground(values)
                                Box(Modifier.fillMaxSize().themeScriptDecoration("background", values.requiredFloat("background_image_opacity")))
                            }
                            when (state.scene) {
                                StudioScene.Chat -> StudioChatPreview(state)
                                StudioScene.Components -> StudioComponentsPreview(state)
                                StudioScene.Navigation -> StudioNavigationPreview(state)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 选择罩不参与内容测量，也不把点击交给样例内部的业务动作。 */
@Composable
internal fun StudioPick(state: ThemeStudioState, id: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val target = if (state.target.id == id) state.target else ThemeStudioTargets.first { it.id == id }
    val label = target.label()
    val selectionShape = if (id == "appBar" || id.startsWith("appBar.")) themeScriptShape("appBar", androidx.compose.ui.graphics.RectangleShape) else RoundedCornerShape(14.dp)
    Box(modifier.themePreviewFocus(id).focusProperties { canFocus = false }.clearAndSetSemantics {
        contentDescription = label
        onClick { state.select(target); true }
    }) {
        content()
        Box(Modifier.matchParentSize().border(1.dp, if (state.target == target) StudioAccent else Color.Transparent, selectionShape).clickable { state.select(target) })
    }
}

@Composable
internal fun StudioChatPreview(state: ThemeStudioState) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            StudioPreviewAppBar(state)
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    StudioPick(state, if (state.target.id == "userAvatar") "userAvatar" else "userMessage", Modifier.fillMaxWidth()) {
                        StudioNativeMessage(state, true, stringResource(R.string.studio_preview_user_message))
                    }
                    StudioPick(state, when (state.target.id) { "assistantAvatar" -> "assistantAvatar"; "status" -> "status"; else -> "assistantMessage" }, Modifier.fillMaxWidth()) {
                        StudioNativeMessage(state, false, stringResource(R.string.studio_preview_assistant_message))
                    }
                }
            }
            val inputTarget = state.target.id.takeIf { it == "input" || it == "iconButton" || it.startsWith("composer.") } ?: "composer"
            StudioPick(state, inputTarget, Modifier.fillMaxWidth()) { StudioNativeInput(state) }
        }
        if (state.target.id in ThemeScriptContract.popupSurfaceIds) StudioChatPopupPreview(state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudioPreviewAppBar(state: ThemeStudioState) {
    val id = state.target.id.takeIf { it.startsWith("appBar.") } ?: "appBar"
    val pickModifier = if (id == "appBar.back") Modifier.fillMaxWidth() else Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)
    StudioPick(state, id, pickModifier) {
        if (id == "appBar.back") {
            val contentColor = themeScriptContentColor("appBar", MaterialTheme.colorScheme.onBackground)
            TopAppBar(
                modifier = Modifier.clip(themeScriptShape("appBar", androidx.compose.ui.graphics.RectangleShape))
                    .background(themeScriptFill("appBar", MaterialTheme.colorScheme.background.copy(alpha = 1f)))
                    .themeScriptDecoration("appBar", drawFill = false),
                expandedHeight = 48.dp,
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text(state.targetName, style = themeScriptTextStyle("appBar", MaterialTheme.typography.titleSmall), color = contentColor, textStroke = themeTextStroke("appBar")) },
                navigationIcon = {
                    ThemeScriptIconButton(onClick = {}, actionId = "appBar.back") {
                        ThemeScriptIcon(id, Icons.Default.ArrowBack, null, tint = contentColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
            )
        } else {
            com.ai.assistance.operit.ui.features.conversation.chat.modules.sidebar.MainChatControlsContent(
                title = state.targetName, runningTaskCount = 0,
                isFloatingMode = false, isWorkspacePreparing = false,
                workspacePage = com.ai.assistance.operit.ui.features.conversation.workspace.hub.WorkspaceHubPage.CHAT,
                hasWorkspace = false, onOpenSidebar = {}, onLaunchFloatingWindow = {}, onOpenWorkspace = {},
            )
        }
    }
}
