@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.ai.assistance.operit.ui.features.management.resources.hub

import com.ai.assistance.operit.ui.theme.script.text.ThemeOutlinedTextField as OutlinedTextField
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.resources.OfflineResources
import com.ai.assistance.operit.data.resources.models.*
import com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench.*
import com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench.SpeechChoice
import com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench.SpeechChoicePanel
import com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench.SpeechWorkbenchSheet

internal fun categoryTitle(category: String): Int = when (category) {
    "stt" -> R.string.resource_catalog_speech
    "tts" -> R.string.hub_category_tts
    "ocr" -> R.string.hub_category_ocr
    "runtime" -> R.string.resource_catalog_environment
    "export" -> R.string.resource_catalog_export
    "template" -> R.string.hub_category_templates
    "engine" -> R.string.hub_category_engines
    "imported" -> R.string.hub_imported
    else -> R.string.hub_all
}
internal fun ModelDownloadSource.displayName(): String = when (this) {
    ModelDownloadSource.HUGGING_FACE -> "Hugging Face"
    ModelDownloadSource.HF_MIRROR -> "HF-Mirror"
    ModelDownloadSource.MODELSCOPE -> "ModelScope"
}
@Composable
internal fun ResourceHubScreen(model: ResourceHubViewModel, requested: String?, maintenance: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val state by model.state.collectAsState()
    val preferences = remember { context.getSharedPreferences("offline_resource_hub", 0) }
    var sourceName by rememberSaveable { mutableStateOf(preferences.getString("source", ModelDownloadSource.HUGGING_FACE.name)!!) }
    val source = ModelDownloadSource.valueOf(sourceName)
    var sources by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(if (requested in setOf("tts", "stt", "ocr", "speech") || requested == null) 0 else 1) }
    var modelFilter by rememberSaveable { mutableStateOf(if (requested in setOf("tts", "stt", "ocr")) requested!! else "all") }
    var resourceFilter by rememberSaveable { mutableStateOf(if (requested == "templates") "template" else "all") }
    var query by rememberSaveable { mutableStateOf("") }
    var readyOnly by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(requested?.takeUnless { it in setOf("tts", "stt", "ocr", "templates", "extensions") }) }
    var exploring by rememberSaveable { mutableStateOf(false) }
    var choosingResource by remember { mutableStateOf(false) }
    var pendingTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPurpose by rememberSaveable { mutableStateOf(SpeechModelPurpose.TTS.name) }
    var pendingName by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmEnvironment by rememberSaveable { mutableStateOf(false) }
    var confirmTemplates by remember { mutableStateOf(false) }
    var download by remember { mutableStateOf<HubEntry?>(null) }
    val busy = state.active != null || state.checking || maintenance
    val input = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val target = pendingTarget
            if (target != null) model.importResource(target, uri)
            else model.preview(uri, SpeechModelPurpose.valueOf(pendingPurpose), pendingName)
        }
    }
    val output = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) pendingTarget?.let { model.export(it, uri) }
    }
    fun importEntry(entry: HubEntry) {
        if (!entry.importable) return
        pendingTarget = entry.id
        input.launch(arrayOf("application/zip", "application/octet-stream", "application/x-bzip2", "application/gzip", "application/x-tar", "application/x-zip-compressed"))
    }
    BackHandler(enabled = state.active != null || maintenance) { if (!maintenance) model.cancel() }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.offline_resources_title)) }, navigationIcon = {
            IconButton(enabled = !state.deleting && !maintenance, onClick = { if (state.active == null) onClose() else model.cancel() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(if (state.active == null) R.string.back else R.string.cancel))
            }
        })
    }, bottomBar = {
        if (state.active != null) Surface(tonalElevation = 4.dp) { HubActiveTask(state, model::cancel) }
    }) { padding ->
        val filter = if (tab == 0) modelFilter else resourceFilter
        val filtered = state.entries.filter { entry ->
            entry.isModel == (tab == 0) && (!readyOnly || entry.ready) &&
                (filter == "all" || entry.category == filter || (filter == "imported" && entry.model?.imported == true)) &&
                (query.isBlank() || listOf(entry.name, entry.description, entry.model?.hfRepository.orEmpty(), entry.model?.languages?.joinToString().orEmpty()).any { it.contains(query, true) })
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                OutlinedCard(shape = RoundedCornerShape(20.dp), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.CloudDownload, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(stringResource(R.string.hub_hero_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.hub_hero_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = model::refresh, enabled = !busy) {
                                if (state.checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Default.Refresh, stringResource(R.string.resource_refresh))
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Android · arm64-v8a", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FilterChip(selected = readyOnly, onClick = { readyOnly = !readyOnly }, label = { Text(stringResource(R.string.hub_ready_count, state.entries.count(HubEntry::ready))) })
                        }
                    }
                }
            }
            item {
                TabRow(selectedTabIndex = tab, modifier = Modifier.fillMaxWidth()) {
                    listOf(R.string.hub_models, R.string.hub_resources).forEachIndexed { index, title ->
                        Tab(selected = index == tab, onClick = { tab = index; query = "" }, text = { Text(stringResource(title)) })
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(query, { query = it }, Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(14.dp),
                        placeholder = { Text(stringResource(R.string.search)) }, leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(20.dp)) })
                    FilledTonalButton(onClick = { if (tab == 0) exploring = true else choosingResource = true }, enabled = !busy) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(if (tab == 0) R.string.hub_explore else R.string.offline_resources_import))
                    }
                }
            }
            if (tab == 0) item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.hub_download_source), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { sources = true }, enabled = !busy) { Text(source.displayName()) }
                }
            }
            item {
                val categories = if (tab == 0) listOf("all", "stt", "tts", "ocr", "imported") else listOf("all", "runtime", "export", "template", "engine")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { category -> FilterChip(selected = filter == category, onClick = { if (tab == 0) modelFilter = category else resourceFilter = category }, label = { Text(stringResource(categoryTitle(category))) }) }
                }
            }
            if (state.message.isNotBlank()) item {
                Surface(shape = RoundedCornerShape(12.dp), color = if (state.failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer) {
                    Text(state.message, Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (maintenance || state.ubuntuPending) item { Text(stringResource(R.string.resource_ubuntu_delete_pending), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            for ((category, entries) in filtered.groupBy(HubEntry::category)) {
                item(key = "section-$category") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(categoryTitle(category)), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (category == "template") TextButton(onClick = { confirmTemplates = true }, enabled = !busy && entries.any { !it.ready }) { Text(stringResource(R.string.hub_prepare_all)) }
                    }
                }
                if (category == "template") {
                    items(entries.chunked(3), key = { "templates-${it.first().id}" }) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { entry ->
                                OutlinedCard(onClick = { details = entry.id }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(entry.pack!!.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                            if (entry.ready) Icon(Icons.Default.CheckCircle, stringResource(R.string.hub_ready), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                        }
                                        Text(stringResource(if (entry.ready) R.string.hub_ready else R.string.hub_unprepared), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(resourceSize(entry.bytes), style = MaterialTheme.typography.labelSmall)
                                        if (state.active != null && state.transferId == entry.id) HubTransferProgress(state)
                                    }
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                } else items(entries, key = HubEntry::id) { entry ->
                    HubResourceCard(entry, state, busy, state.ubuntuInstalled, onDetails = { details = entry.id }, onDownload = { download = entry })
                }
            }
            if (filtered.isEmpty() && !state.checking) item { Text(stringResource(R.string.hub_empty), Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { Text(stringResource(R.string.hub_footer), Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    if (sources) SpeechChoicePanel(stringResource(R.string.hub_download_source), ModelDownloadSource.values().map { SpeechChoice(it.name, it.displayName()) }, source.name,
        onSelect = { sourceName = it; preferences.edit().putString("source", it).apply(); sources = false }, onDismiss = { sources = false })
    state.entries.firstOrNull { it.id == details }?.let { entry ->
        HubDetailSheet(entry, state, busy, onDismiss = { details = null }, onDownload = { download = entry; details = null },
            onImport = { details = null; importEntry(entry) }, onExport = { details = null; pendingTarget = entry.id; output.launch("operit-${entry.id.replace('@', '-')}.zip") },
            onDelete = { environment -> details = null; confirm = entry.id; confirmEnvironment = environment })
    }
    if (exploring) HubModelImportSheet(state.entries.filter { it.model != null && it.model.imported.not() }, source,
        onDismiss = { exploring = false }, onCatalog = { exploring = false; details = it.id },
        onRepository = { input, revision, purpose, name -> exploring = false; model.downloadRepository(input, source, revision, purpose, name) },
        onLocal = { purpose, name -> exploring = false; pendingTarget = null; pendingPurpose = purpose.name; pendingName = name; input.launch(arrayOf("application/zip", "application/octet-stream", "application/gzip", "application/x-bzip2", "application/x-tar")) })
    if (choosingResource) SpeechChoicePanel(stringResource(R.string.hub_import_resource), state.entries.filter { !it.isModel && it.importable }.map { SpeechChoice(it.id, it.name) }, "",
        onSelect = { id -> choosingResource = false; importEntry(state.entries.first { it.id == id }) }, onDismiss = { choosingResource = false })
    state.preview?.let { preview ->
        SpeechWorkbenchSheet(stringResource(R.string.hub_import_preview), model::discardPreview, dismissEnabled = !busy) {
            Text(preview.model.name, style = MaterialTheme.typography.titleMedium)
            Text("${preview.model.driver.type} · ${preview.model.files.size} · ${resourceSize(preview.model.files.sumOf(ModelFile::bytes))}")
            Text(stringResource(R.string.hub_preview_hint), style = MaterialTheme.typography.bodySmall)
            Button(onClick = model::installPreview, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.hub_install)) }
        }
    }
    state.entries.firstOrNull { it.id == confirm }?.let { entry ->
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text(stringResource(R.string.resource_delete_title)) },
            text = { Text(if (confirmEnvironment) stringResource(R.string.resource_delete_ubuntu_confirm) else if (entry.id == OfflineResources.UBUNTU) stringResource(R.string.hub_remove_archive_hint) else stringResource(R.string.resource_delete_confirm, entry.name)) },
            confirmButton = { TextButton(onClick = { confirm = null; model.delete(entry, confirmEnvironment) }) { Text(stringResource(R.string.resource_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } })
    }
    download?.let { entry ->
        AlertDialog(onDismissRequest = { download = null }, title = { Text(entry.name) }, text = { Text(stringResource(R.string.hub_download_confirm, resourceSize(entry.bytes)) + if (entry.model != null) "\n" + stringResource(R.string.hub_engine_dependency) else "") },
            confirmButton = { TextButton(onClick = { download = null; model.download(entry, source) }) { Text(stringResource(if (entry.updateAvailable) R.string.hub_update_resource else R.string.offline_resources_download)) } },
            dismissButton = { TextButton(onClick = { download = null }) { Text(stringResource(R.string.cancel)) } })
    }
    if (confirmTemplates) AlertDialog(onDismissRequest = { confirmTemplates = false }, title = { Text(stringResource(R.string.hub_prepare_all)) },
        text = { Text(stringResource(R.string.hub_prepare_all_hint)) }, confirmButton = { TextButton(onClick = { confirmTemplates = false; model.downloadTemplates() }) { Text(stringResource(R.string.offline_resources_download)) } },
        dismissButton = { TextButton(onClick = { confirmTemplates = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun HubResourceCard(entry: HubEntry, state: HubState, busy: Boolean, ubuntuInstalled: Boolean, onDetails: () -> Unit, onDownload: () -> Unit) {
    OutlinedCard(shape = RoundedCornerShape(18.dp), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium)
                    if (entry.description.isNotBlank()) Text(entry.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (entry.ready) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(stringResource(when { entry.bundled -> R.string.hub_bundled; entry.inUse -> R.string.hub_in_use; entry.updateAvailable -> R.string.hub_update_available; entry.ready -> R.string.hub_ready; entry.stored -> R.string.hub_incomplete; else -> R.string.hub_unprepared }), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                if (entry.bytes > 0) Text(resourceSize(entry.bytes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                entry.model?.let { Text("${it.driver.type} · ${it.languages.joinToString(" / ")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (entry.id == OfflineResources.UBUNTU && ubuntuInstalled) Text(stringResource(R.string.hub_environment_installed), style = MaterialTheme.typography.labelMedium)
            }
            if (state.active != null && state.transferId == entry.id) HubTransferProgress(state)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDetails) { Text(stringResource(R.string.hub_details)) }
                if ((!entry.ready || entry.updateAvailable) && entry.downloadable) FilledTonalButton(onClick = onDownload, enabled = !busy && !entry.inUse) { Text(stringResource(if (entry.updateAvailable) R.string.hub_update_resource else R.string.offline_resources_download)) }
            }
        }
    }
}
