package com.ai.assistance.operit.ui.theme.script

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.theme.ThemeScriptAppearance
import com.ai.assistance.operit.data.model.theme.ThemeScriptContext
import com.ai.assistance.operit.data.model.theme.ThemeScriptSettings
import com.ai.assistance.operit.data.preferences.ThemePreferenceSnapshot
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.resources.theme.ThemeScriptBundle
import com.ai.assistance.operit.data.resources.theme.ThemeScriptBundleStore
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal sealed interface ThemeScriptState {
    data object Disabled : ThemeScriptState
    data object Loading : ThemeScriptState
    data class Ready(val appearance: ThemeScriptAppearance, val images: Map<String, ThemeScriptImage>) : ThemeScriptState
    data class Failed(val message: String) : ThemeScriptState
}

internal val LocalThemeScriptState = compositionLocalOf<ThemeScriptState> { ThemeScriptState.Disabled }

/** 主界面和草稿预览各自提供快照，异步结果只写回对应的求值会话。 */
@Composable
fun ThemeScriptHost(
    snapshot: ThemePreferenceSnapshot = LocalThemePreferenceSnapshot.current,
    draft: ThemeScriptBundle? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val dark = if (snapshot.useSystemTheme) isSystemInDarkTheme() else snapshot.themeMode == "dark"
    val input = remember(snapshot.values, colors, dark) { themeScriptContext(snapshot.values, colors, dark) }
    val uri = snapshot.values.string(ThemePreferenceValues.SCRIPT_URI_KEY)
    val prepared = remember(uri) { ScriptPreparation() }
    var state by remember(uri, input, draft) {
        mutableStateOf<ThemeScriptState>(if (uri == null && draft == null) ThemeScriptState.Disabled else ThemeScriptState.Loading)
    }
    LaunchedEffect(uri, input, draft) {
        if (uri == null && draft == null) return@LaunchedEffect
        kotlinx.coroutines.delay(75)
        try {
            val loaded = withContext(Dispatchers.IO) {
                if (draft == null) prepared.load(context.filesDir, File(context.cacheDir, "theme-transfer"), requireNotNull(uri))
                else {
                    ThemeScriptBundleStore.validate(draft)
                    prepared.loadDraft(draft, File(context.cacheDir, "theme-fonts"))
                }
            }
            val base = loaded.bundle.manifest.appearance
            val appearance = ThemeScriptResolver.resolve(loaded.bundle, input.copy(
                appearance = base,
                colors = themeColorTokens(applyThemePalette(colors, base.palette, dark)),
            ))
            ThemeScriptAssets.validateAppearance(appearance, loaded.images)
            ensureActive()
            state = ThemeScriptState.Ready(appearance, loaded.images)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.e("ThemeScript", "主题脚本求值失败", error)
            state = ThemeScriptState.Failed(error.message.orEmpty().take(4096))
        }
    }
    val ready = state as? ThemeScriptState.Ready
    val activeColors = if (ready == null) colors else applyThemeTextColors(applyThemePalette(colors, ready.appearance.palette, dark), ready.appearance.typography)
    val typography = if (ready == null) MaterialTheme.typography else applyThemeTypography(MaterialTheme.typography, ready.appearance.typography, ready.images)
    val textColor = ready?.appearance?.typography?.color?.let(::themeColor)
    CompositionLocalProvider(
        LocalThemeScriptState provides state,
        LocalContentColor provides (textColor ?: LocalContentColor.current),
        LocalTextStyle provides (if (textColor == null) LocalTextStyle.current else LocalTextStyle.current.copy(color = textColor)),
    ) {
        MaterialTheme(colorScheme = activeColors, typography = typography) {
        Box {
            content()
            val failure = state as? ThemeScriptState.Failed
            if (failure != null) {
                Text(
                    stringResource(R.string.theme_script_error, failure.message),
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(8.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                )
            }
        }
        }
    }
}

/** 参数来源仍是既有主题，排除资源位置和角色业务元数据。 */
fun themeScriptContext(
    values: ThemePreferenceValues,
    colors: ColorScheme,
    dark: Boolean,
    appearance: ThemeScriptAppearance = ThemeScriptAppearance(1),
): ThemeScriptContext {
    return ThemeScriptContext(
        mode = if (dark) "dark" else "light",
        appearance = appearance,
        colors = themeColorTokens(applyThemePalette(colors, appearance.palette, dark)),
        settings = ThemeScriptSettings(
            strings = values.strings.filterKeys { !it.endsWith("_uri") && !it.endsWith("_path") && it != "custom_chat_title" },
            booleans = values.booleans, ints = values.ints, floats = values.floats,
        ),
    )
}

private data class PreparedScript(val bundle: ThemeScriptBundle, val images: Map<String, ThemeScriptImage>)

private class ScriptPreparation {
    private var loaded: PreparedScript? = null

    @Synchronized
    fun loadDraft(bundle: ThemeScriptBundle, fontDirectory: File): PreparedScript {
        val previous = loaded
        return PreparedScript(bundle, ThemeScriptAssets.prepare(bundle, fontDirectory, previous?.bundle, previous?.images.orEmpty())).also { loaded = it }
    }

    @Synchronized
    fun load(filesDir: File, previewRoot: File, reference: String): PreparedScript {
        loaded?.let { return it }
        val file = ThemeScriptBundleStore.fileForReference(filesDir, reference, previewRoot)
        val bundle = ThemeScriptBundleStore.read(file)
        return PreparedScript(bundle, ThemeScriptAssets.prepare(bundle, File(previewRoot.parentFile, "theme-fonts"))).also { loaded = it }
    }
}
