package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.components.slider.WorkbenchSlider
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.ui.theme.resolveThemeColorScheme
import com.ai.assistance.operit.ui.theme.script.LocalThemeScriptState
import com.ai.assistance.operit.ui.theme.script.ThemeScriptState
import java.util.Locale

internal val StudioAccent: Color
    @Composable get() = MaterialTheme.colorScheme.primary
internal val StudioBackground: Color
    @Composable get() = MaterialTheme.colorScheme.background
internal val StudioPanel: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerLow
internal val StudioLine: Color
    @Composable get() = MaterialTheme.colorScheme.outlineVariant

/** 工作室跟随应用主题；显式提供内容色，防止跨明暗主题继承错误的文字和图标颜色。 */
@Composable
internal fun StudioTheme(content: @Composable () -> Unit) {
    val snapshot = LocalThemePreferenceSnapshot.current
    val dark = if (snapshot.useSystemTheme) isSystemInDarkTheme()
        else snapshot.themeMode == UserPreferencesManager.THEME_MODE_DARK
    val colors = remember(snapshot, dark) { resolveThemeColorScheme(snapshot, dark) }
    CompositionLocalProvider(
        LocalThemeScriptState provides ThemeScriptState.Disabled,
        LocalContentColor provides colors.onSurface,
    ) {
        MaterialTheme(colorScheme = colors, typography = Typography(), shapes = Shapes(), content = content)
    }
}

@Composable
internal fun StudioSection(title: String, note: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            if (note != null) Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
    HorizontalDivider(color = StudioLine)
}

@Composable
internal fun StudioHint(text: String) { Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable
internal fun <T> StudioChoices(value: T, options: List<Pair<T, String>>, onChange: (T) -> Unit) {
    val colors = MaterialTheme.colorScheme
    // 少量选项等分整行，较多字体选项换行，避免小标签挤在左侧或需要横向寻找。
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = minOf(options.size, if (options.size > 5) 3 else 5, (maxWidth / 60.dp).toInt().coerceAtLeast(1))
        Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (key, label) ->
                        val selected = key == value
                        val shape = RoundedCornerShape(12.dp)
                        Box(
                            Modifier.weight(1f).heightIn(min = 52.dp)
                                .border(1.dp, if (selected) colors.primary else colors.outlineVariant, shape)
                                .background(if (selected) colors.primaryContainer else colors.surfaceContainerHigh, shape)
                                .clip(shape)
                                .selectable(selected = selected, role = Role.RadioButton, onClick = { onChange(key) })
                                .padding(horizontal = 6.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selected) colors.onPrimaryContainer else colors.onSurface,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun StudioSlider(state: ThemeStudioState, label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String = "", onChange: (Float) -> Unit) {
    fun formatted(number: Float) = String.format(Locale.ROOT, if (range.endInclusive <= 2f) "%.2f" else "%.0f", number)
    var input by remember(label) { mutableStateOf(formatted(value)) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) { if (!focused) input = formatted(value) }
    val parsed = input.toFloatOrNull()
    val invalid = parsed == null || !parsed.isFinite() || parsed !in range
    DisposableEffect(state) { onDispose { state.endGesture() } }
    Column {
        Text(label + if (unit.isEmpty()) "" else " · $unit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WorkbenchSlider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = { state.beginGesture(); onChange(it) }, valueRange = range, onValueChangeFinished = state::endGesture, modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = input,
                onValueChange = { text ->
                    input = text
                    text.toFloatOrNull()?.takeIf { it.isFinite() && it in range }?.let { state.beginGesture(); onChange(it) }
                },
                singleLine = true,
                isError = focused && invalid,
                textStyle = MaterialTheme.typography.labelSmall,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(76.dp).onFocusChanged { if (!it.isFocused && focused) state.endGesture(); focused = it.isFocused },
            )
        }
    }
}
@Composable
internal fun StudioFullSheet(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        StudioTheme {
            Column(Modifier.fillMaxSize().background(StudioBackground).imePadding()) {
                Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.studio_close)) }
                }
                HorizontalDivider(color = StudioLine)
                content()
            }
        }
    }
}
