package com.ai.assistance.operit.ui.theme.script

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.ai.assistance.operit.data.model.theme.ThemeScriptSurface
import com.ai.assistance.operit.data.model.theme.forState

/** 同一原生部件的颜色、正文和装饰必须读取同一个交互状态。 */
internal val LocalThemeSurfaceStates = compositionLocalOf<Map<String, String>> { emptyMap() }
internal val LocalThemeHandledOpacity = compositionLocalOf<Set<String>> { emptySet() }

internal fun themeStateName(pressed: Boolean, focused: Boolean, selected: Boolean, enabled: Boolean, error: Boolean): String = when {
    !enabled -> "disabled"
    error -> "error"
    pressed -> "pressed"
    focused -> "focused"
    selected -> "selected"
    else -> "default"
}

@Composable
internal fun ThemeInteractionScope(
    id: String,
    interactionSource: MutableInteractionSource?,
    enabled: Boolean,
    content: @Composable (MutableInteractionSource) -> Unit,
) {
    val ownSource = remember { MutableInteractionSource() }
    val source = interactionSource ?: ownSource
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val state = LocalThemeSurfaceStates.current[id] ?: themeStateName(pressed, focused, selected = false, enabled = enabled, error = false)
    CompositionLocalProvider(
        LocalThemeSurfaceStates provides (LocalThemeSurfaceStates.current + (id to state)),
        LocalThemeHandledOpacity provides (LocalThemeHandledOpacity.current + id),
    ) { content(source) }
}

@Composable
internal fun resolvedThemeSurface(id: String, base: ThemeScriptSurface): ThemeScriptSurface =
    base.forState(LocalThemeSurfaceStates.current[id] ?: "default")

/** 显式选中状态在读取原生容器配色之前建立，正文和装饰使用同一个值。 */
@Composable
internal fun ThemeSurfaceStateScope(id: String, selected: Boolean, handlesOpacity: Boolean = false, content: @Composable () -> Unit) {
    val states = LocalThemeSurfaceStates.current
    val active = states[id] ?: themeStateName(pressed = false, focused = false, selected = selected, enabled = true, error = false)
    val handled = LocalThemeHandledOpacity.current
    CompositionLocalProvider(
        LocalThemeSurfaceStates provides (states + (id to active)),
        LocalThemeHandledOpacity provides (if (handlesOpacity) handled + id else handled),
        content = content,
    )
}
