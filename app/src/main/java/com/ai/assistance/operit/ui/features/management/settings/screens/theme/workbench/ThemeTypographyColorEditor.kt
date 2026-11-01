package com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.net.Uri
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.theme.*
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.resources.theme.ThemeScriptBundle
import com.ai.assistance.operit.data.resources.theme.ThemeScriptBundleStore
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.ThemeEditorSession
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio.StudioColorChip
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio.StudioColorPicker
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 两个入口编辑同一份 typography.color，不另外建立一套颜色偏好。 */
@Composable
internal fun ThemeTypographyColorEditor(session: ThemeEditorSession) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val values by session.values.collectAsState()
    val reference = values.string(ThemePreferenceValues.SCRIPT_URI_KEY)
    var bundle by remember(session, reference) { mutableStateOf<ThemeScriptBundle?>(null) }
    var busy by remember(session) { mutableStateOf(false) }
    var error by remember(session, reference) { mutableStateOf<String?>(null) }
    var selectedMode by remember(session) { mutableStateOf<String?>(null) }
    LaunchedEffect(session, reference) {
        try {
            bundle = withContext(Dispatchers.IO) {
                if (reference == null) ThemeScriptBundle(
                    ThemeScriptManifest(1, "assets/main.js"), ThemeScriptDefaults.SOURCE, emptyMap(),
                ) else ThemeScriptBundleStore.read(ThemeScriptBundleStore.fileForReference(context.filesDir, reference))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            AppLogger.e("ThemeTypographyColor", "读取字体颜色失败", failure)
            error = failure.message.orEmpty()
        }
    }
    ThemeWorkbenchDivider()
    Text(stringResource(R.string.studio_content_color), Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall)
    val current = bundle
    if (current == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (current != null) {
        for (mode in listOf("light", "dark")) {
            val color = current.manifest.appearance.forMode(mode).typography.color
            Row(
                Modifier.fillMaxWidth().clickable(enabled = !busy) { selectedMode = mode }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StudioColorChip(color?.let { Color(android.graphics.Color.parseColor(it)) })
                Text(stringResource(if (mode == "light") R.string.studio_light else R.string.studio_dark), Modifier.weight(1f))
                Text(color ?: stringResource(R.string.studio_inherit), style = MaterialTheme.typography.bodySmall)
            }
        }
        selectedMode?.let { mode ->
            StudioColorPicker(
                state = null,
                title = stringResource(R.string.studio_content_color) + " · " + stringResource(if (mode == "light") R.string.studio_light else R.string.studio_dark),
                initial = current.manifest.appearance.forMode(mode).typography.color,
                seed = MaterialTheme.colorScheme.onSurface,
                onClose = { selectedMode = null },
                onChange = { color ->
                    if (color != current.manifest.appearance.forMode(mode).typography.color) scope.launch {
                        busy = true
                        var staged: File? = null
                        try {
                            val base = current.manifest.appearance
                            val variant = base.modes[mode] ?: ThemeAppearanceMode()
                            val appearance = base.copy(modes = base.modes + (mode to variant.copy(typography = base.forMode(mode).typography.copy(color = color))))
                            val next = current.copy(manifest = current.manifest.copy(appearance = appearance))
                            val file = withContext(Dispatchers.IO) { ThemeScriptBundleStore.create(context.filesDir, next).also { staged = it } }
                            // 草稿在写文件期间可能被放弃，旧任务不能覆盖新的主题引用。
                            check(session.currentValues.string(ThemePreferenceValues.SCRIPT_URI_KEY) == reference) { "Theme draft changed while editing text color" }
                            val uri = Uri.fromFile(file).toString()
                            session.registerStagedAsset(uri)
                            session.setString(ThemePreferenceValues.SCRIPT_URI_KEY, uri)
                            staged = null
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            AppLogger.e("ThemeTypographyColor", "保存字体颜色草稿失败", failure)
                            error = failure.message.orEmpty()
                        } finally {
                            staged?.delete()
                            busy = false
                        }
                    }
                },
            )
        }
    }
    error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}
