package com.ai.assistance.operit.ui.features.management.resources.hub

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.oktoast.OKToast
import com.ai.assistance.operit.data.resources.OfflineResources
import com.ai.assistance.operit.data.resources.models.*
import com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench.*
import com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench.SpeechWorkbenchField
import com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench.SpeechWorkbenchFold
import com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench.SpeechWorkbenchSheet

@Composable
internal fun HubDetailSheet(entry: HubEntry, state: HubState, busy: Boolean, onDismiss: () -> Unit, onDownload: () -> Unit, onImport: () -> Unit, onExport: () -> Unit, onDelete: (Boolean) -> Unit) {
    val context = LocalContext.current
    SpeechWorkbenchSheet(entry.name, onDismiss) {
        val windowView = LocalView.current.rootView
        DisposableEffect(windowView) {
            val unregister = OKToast.registerWindow(windowView)
            onDispose { unregister() }
        }
        if (entry.description.isNotBlank()) Text(entry.description, style = MaterialTheme.typography.bodyMedium)
        HubDetailLine(stringResource(R.string.hub_type), stringResource(categoryTitle(entry.category)))
        HubDetailLine(stringResource(R.string.hub_status), stringResource(when { entry.bundled -> R.string.hub_bundled; entry.updateAvailable -> R.string.hub_update_available; entry.ready -> R.string.hub_ready; entry.stored -> R.string.hub_incomplete; else -> R.string.hub_unprepared }))
        if (entry.bytes > 0) HubDetailLine(stringResource(R.string.hub_download_size), resourceSize(entry.bytes))
        if (state.active != null && state.transferId == entry.id) HubTransferProgress(state)
        if (entry.location.isNotBlank()) {
            Text(stringResource(if (entry.stored) R.string.hub_local_path else R.string.hub_install_path), style = MaterialTheme.typography.labelMedium)
            SelectionContainer { Text(entry.location, style = MaterialTheme.typography.bodySmall) }
        }
        if (entry.pack?.extension != null) Text(stringResource(R.string.hub_builtin_extension_hint), style = MaterialTheme.typography.bodySmall)
        entry.model?.let { model ->
            HubDetailLine(stringResource(R.string.hub_engine), model.driver.type)
            HubDetailLine(stringResource(R.string.hub_version), model.version)
            HubDetailLine(stringResource(R.string.hub_license), model.license.ifBlank { stringResource(R.string.hub_undeclared) })
            HubDetailLine(stringResource(R.string.hub_speakers), model.driver.speakers.toString())
            Text(stringResource(R.string.hub_engine_dependency), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SpeechWorkbenchFold(stringResource(R.string.hub_file_count, model.files.size)) {
                model.files.take(100).forEach { Text("${it.path} · ${resourceSize(it.bytes)}", style = MaterialTheme.typography.bodySmall) }
            }
        }
        entry.pack?.let { pack ->
            HubDetailLine(stringResource(R.string.hub_version), pack.version)
            SpeechWorkbenchFold(stringResource(R.string.hub_file_count, pack.files.size)) {
                pack.files.take(100).forEach { Text("${it.path} · ${resourceSize(it.bytes)}", style = MaterialTheme.typography.bodySmall) }
            }
        }
        if (entry.bundled) Text(stringResource(R.string.hub_bundled_hint), style = MaterialTheme.typography.bodySmall)
        if (entry.inUse) Text(stringResource(if (entry.category in setOf("engine", "ocr")) R.string.hub_restart_resource_hint else R.string.hub_in_use_hint), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        if (entry.id == OfflineResources.UBUNTU) HubDetailLine(stringResource(R.string.hub_environment), stringResource(if (state.ubuntuInstalled) R.string.hub_environment_installed else R.string.hub_unprepared))
        val url = when {
            entry.model?.archive != null -> entry.model.archive.url
            entry.model?.hfRepository?.isNotBlank() == true -> "https://huggingface.co/${entry.model.hfRepository}/tree/${entry.model.version}"
            entry.pack != null -> entry.pack.archive.url.substringBeforeLast('/')
            entry.legacy != null -> OfflineResources.RELEASE_URL
            else -> "https://developers.google.com/ml-kit/vision/text-recognition/v2/android"
        }
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) { Text(stringResource(R.string.offline_resources_source)) }
        if (!entry.bundled) {
            if ((!entry.ready || entry.updateAvailable) && entry.downloadable) Button(onClick = onDownload, enabled = !busy && !entry.inUse, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (entry.updateAvailable) R.string.hub_update_resource else R.string.offline_resources_download)) }
            OutlinedButton(onClick = onImport, enabled = !busy && !entry.inUse, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.offline_resources_import)) }
            if (entry.ready) OutlinedButton(onClick = onExport, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.resource_save_backup)) }
            if (entry.stored) {
                val deleteWarning = when {
                    entry.pack?.extension != null -> R.string.hub_disable_extension_before_delete
                    entry.category in setOf("engine", "ocr") -> R.string.hub_restart_resource_hint
                    else -> R.string.hub_in_use_hint
                }
                val deleteLabel = stringResource(if (entry.id == OfflineResources.UBUNTU) R.string.hub_remove_archive else R.string.resource_delete)
                // 禁用按钮的手势节点仍会消费点击；独立覆盖层承接说明，不能依赖父级 clickable。
                Box(Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { onDelete(false) },
                        enabled = !busy && !entry.inUse,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().then(
                            if (entry.inUse && !busy) Modifier.clearAndSetSemantics {} else Modifier
                        ),
                    ) {
                        Text(deleteLabel)
                    }
                    if (entry.inUse && !busy) Box(
                        Modifier.matchParentSize().semantics { contentDescription = deleteLabel }.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClickLabel = stringResource(deleteWarning),
                        ) { OKToast.warning(context, deleteWarning).show() }
                    )
                }
            }
            if (entry.id == OfflineResources.UBUNTU && (state.ubuntuInstalled || state.ubuntuPending)) TextButton(onClick = { onDelete(true) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.hub_remove_environment), color = MaterialTheme.colorScheme.error) }
        }
    }
}
@Composable
private fun HubDetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(label, Modifier.weight(0.4f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(0.6f), style = MaterialTheme.typography.bodySmall)
    }
}
@Composable
internal fun HubModelImportSheet(entries: List<HubEntry>, source: ModelDownloadSource, onDismiss: () -> Unit, onCatalog: (HubEntry) -> Unit,
    onRepository: (String, String, SpeechModelPurpose, String) -> Unit, onLocal: (SpeechModelPurpose, String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var repository by rememberSaveable { mutableStateOf("") }
    var revision by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var purposeName by rememberSaveable { mutableStateOf(SpeechModelPurpose.TTS.name) }
    val purpose = SpeechModelPurpose.valueOf(purposeName)
    var error by remember { mutableStateOf<String?>(null) }
    SpeechWorkbenchSheet(stringResource(R.string.hub_explore_title), onDismiss) {
        TabRow(selectedTabIndex = tab) {
            listOf(R.string.hub_catalog, R.string.hub_repository, R.string.hub_local_file).forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index; error = null }, text = { Text(stringResource(title)) })
            }
        }
        if (tab == 0) {
            SpeechWorkbenchField(stringResource(R.string.search), query, { query = it })
            Text(stringResource(R.string.hub_catalog_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            entries.filter { it.name.contains(query, true) || it.model!!.hfRepository.contains(query, true) || it.model.languages.any { language -> language.contains(query, true) } }.forEach { entry ->
                OutlinedCard(onClick = { onCatalog(entry) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(entry.name, style = MaterialTheme.typography.titleSmall)
                        Text("${entry.model!!.driver.type} · ${resourceSize(entry.bytes)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpeechModelPurpose.values().forEach { option -> FilterChip(selected = purpose == option, onClick = { purposeName = option.name }, label = { Text(stringResource(categoryTitle(option.name.lowercase()))) }) }
            }
            SpeechWorkbenchField(stringResource(R.string.hub_display_name), name, { name = it })
            if (tab == 1) {
                SpeechWorkbenchField(stringResource(R.string.hub_repository), repository, { repository = it }, placeholder = "owner/repo")
                SpeechWorkbenchField(stringResource(R.string.hub_version_optional), revision, { revision = it })
                Text("${source.displayName()} · ${stringResource(R.string.hub_repository_hint)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Button(onClick = {
                    try {
                        ModelRepository.parse(repository, source, revision)
                        onRepository(repository, revision, purpose, name)
                    } catch (failure: Exception) { error = failure.localizedMessage }
                }, enabled = repository.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.hub_prepare_repository)) }
            } else {
                Text(stringResource(R.string.hub_local_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { onLocal(purpose, name) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.hub_choose_file)) }
            }
        }
    }
}
