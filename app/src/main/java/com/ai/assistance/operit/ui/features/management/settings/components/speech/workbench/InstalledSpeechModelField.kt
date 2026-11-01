package com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench

import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton

import android.content.Intent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.resources.models.*
import com.ai.assistance.operit.ui.features.management.resources.OfflineResourcesActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 返回资源页后重新读取同一安装记录，未完成校验的模型不会进入选择器。 */
@Composable
internal fun InstalledSpeechModelField(purpose: SpeechModelPurpose, selected: String, onSelect: (SpeechModelManifest) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    var choosing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var models by remember { mutableStateOf<List<SpeechModelManifest>>(emptyList()) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(purpose, revision) {
        loading = true
        try {
            val loaded = withContext(Dispatchers.IO) {
                val store = SpeechModels(context)
                val installed = store.installed()
                installed.models.filter { it.purpose == purpose && store.isInstalled(it) } to installed.errors
            }
            models = loaded.first
            error = loaded.second.takeIf(List<String>::isNotEmpty)?.joinToString("\n")
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel } catch (failure: Exception) { error = failure.localizedMessage } finally { loading = false }
    }
    SpeechWorkbenchRow(
        stringResource(R.string.hub_installed_model),
        models.firstOrNull { it.key == selected }?.name ?: stringResource(if (selected.isBlank()) R.string.hub_select_model else R.string.hub_selected_missing),
        { choosing = true },
        description = stringResource(R.string.hub_model_picker_hint),
    )
    if (choosing) SpeechChoicePanel(
        title = stringResource(R.string.hub_installed_model),
        choices = models.map { SpeechChoice(it.key, it.name, "${it.driver.type} · ${it.languages.joinToString(" / ")} · ${it.version.take(12)}") },
        selected = selected, loading = loading, error = error,
        onSelect = { key -> onSelect(models.first { it.key == key }); choosing = false },
        onDismiss = { choosing = false }, onRefresh = { revision++ },
        footer = {
            TextButton(onClick = {
                choosing = false
                context.startActivity(Intent(context, OfflineResourcesActivity::class.java).putExtra(OfflineResourcesActivity.EXTRA_GROUP, if (purpose == SpeechModelPurpose.TTS) "tts" else "stt"))
            }) { Text(stringResource(R.string.hub_manage_models)) }
        },
    )
}
