package com.ai.assistance.operit.ui.features.conversation.memory.workbench

import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import com.ai.assistance.operit.ui.theme.script.ThemeScriptOutlinedButton as OutlinedButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.ui.features.conversation.memory.screens.graph.model.Edge
import com.ai.assistance.operit.ui.features.conversation.memory.screens.graph.model.Graph
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MemoryEditor(
    memory: Memory?, allFolderPaths: List<String>, initialFolder: String, busy: Boolean, error: String?, onDismiss: () -> Unit,
    onSave: (Memory?, String, String, String, String, Float, Float, String, List<String>) -> Unit,
) {
    val uncategorized = stringResource(R.string.memory_uncategorized)
    var title by remember(memory?.uuid) { mutableStateOf(memory?.title.orEmpty()) }
    var content by remember(memory?.uuid) { mutableStateOf(memory?.content.orEmpty()) }
    var source by remember(memory?.uuid) { mutableStateOf(memory?.source ?: "user_input") }
    var folder by remember(memory?.uuid) { mutableStateOf(memory?.folderPath ?: initialFolder.takeUnless { it == uncategorized }.orEmpty()) }
    var credibility by remember(memory?.uuid) { mutableFloatStateOf(memory?.credibility ?: .8f) }
    var importance by remember(memory?.uuid) { mutableFloatStateOf(memory?.importance ?: .5f) }
    val tags = remember(memory?.uuid) { mutableStateListOf<String>().apply { if (memory != null) addAll(memory.tags.map { it.name }) } }
    var pendingTag by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    fun addTag() { val tag = pendingTag.trim(); if (tag.isNotEmpty() && tag !in tags) tags.add(tag); pendingTag = "" }
    MemoryModal(title = stringResource(if (memory == null) R.string.memory_create_new else R.string.memory_edit_memory), onDismiss = onDismiss, footer = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
            Button(enabled = !busy, onClick = {
                invalid = title.isBlank() || content.isBlank()
                if (!invalid) { addTag(); onSave(memory, title.trim(), content, memory?.contentType ?: "text/plain", source.trim(), credibility, importance, folder, tags.toList()) }
            }) { Text(stringResource(R.string.memory_save)) }
        }
    }) {
        OutlinedTextField(title, { title = it; invalid = false }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.memory_title)) }, maxLines = 3, enabled = !busy)
        OutlinedTextField(content, { content = it; invalid = false }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.memory_content)) }, minLines = 4, maxLines = 9, enabled = !busy && memory?.isDocumentNode != true)
        if (memory?.isDocumentNode == true) Text(stringResource(R.string.memory_workbench_document_readonly), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val paths = (allFolderPaths + folder).filter { it.isNotBlank() && it != uncategorized }.distinct()
        MemoryPicker(
            label = stringResource(R.string.memory_folder),
            value = folder,
            choices = listOf(MemoryChoice("", uncategorized)) + paths.map { MemoryChoice(it, it) },
            enabled = !busy,
            searchable = true,
        ) { folder = it }
        Text(stringResource(R.string.memory_tags), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            tags.forEach { tag ->
                InputChip(selected = false, onClick = { }, label = { Text(tag) }, trailingIcon = {
                    IconButton(onClick = { tags.remove(tag) }, enabled = !busy, modifier = Modifier.size(22.dp)) { Icon(Icons.Default.Close, stringResource(R.string.memory_workbench_tag_remove, tag), Modifier.size(15.dp)) }
                })
            }
        }
        OutlinedTextField(pendingTag, { pendingTag = it }, Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.memory_add_tag_hint)) }, singleLine = true, enabled = !busy,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { addTag() }),
            trailingIcon = { IconButton(onClick = { addTag() }, enabled = !busy) { Icon(Icons.Default.Add, stringResource(R.string.memory_workbench_tag_add)) } })
        OutlinedTextField(source, { source = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.memory_source)) }, singleLine = true, enabled = !busy)
        MemoryValueSlider(stringResource(R.string.memory_credibility), credibility, { credibility = it }, enabled = !busy)
        MemoryValueSlider(stringResource(R.string.memory_importance), importance, { importance = it }, enabled = !busy)
        if (memory != null) {
            Metadata(stringResource(R.string.memory_uuid), memory.uuid)
            Metadata(stringResource(R.string.memory_created_at), memoryDate(memory.createdAt))
        }
        if (invalid) Text(stringResource(R.string.memory_workbench_field_required), color = MaterialTheme.colorScheme.error)
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
internal fun MemoryLinkEditor(graph: Graph, initialIds: List<String>, busy: Boolean, error: String?, onDismiss: () -> Unit, onLink: (String, String, String, Float, String) -> Unit) {
    var from by remember { mutableStateOf(initialIds.firstOrNull() ?: graph.nodes.firstOrNull()?.id.orEmpty()) }
    var to by remember { mutableStateOf(initialIds.getOrNull(1) ?: graph.nodes.firstOrNull { it.id != from }?.id.orEmpty()) }
    var type by remember { mutableStateOf("RELATED_TO") }
    var weight by remember { mutableFloatStateOf(1f) }
    var description by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<Int?>(null) }
    MemoryModal(stringResource(R.string.memory_create_link), onDismiss, footer = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
            Button(enabled = !busy && from.isNotEmpty() && to.isNotEmpty(), onClick = {
                localError = when {
                    from == to -> R.string.memory_workbench_self_link
                    graph.edges.any { it.sourceId == from && it.targetId == to && it.label == type } -> R.string.memory_workbench_duplicate_link
                    else -> null
                }
                if (localError == null) onLink(from, to, type, weight, description)
            }) { Text(stringResource(R.string.memory_create_link)) }
        }
    }) {
        val choices = graph.nodes.map { MemoryChoice(it.id, it.label, it.metadata["folder"].orEmpty()) }
        MemoryPicker(
            label = stringResource(R.string.memory_from), value = from, choices = choices,
            enabled = !busy, searchable = true,
            dialogTitle = stringResource(R.string.memory_workbench_select_source),
        ) { from = it; localError = null }
        RelationPicker(type, enabled = !busy) { type = it; localError = null }
        MemoryPicker(
            label = stringResource(R.string.memory_to), value = to, choices = choices,
            enabled = !busy, searchable = true,
            dialogTitle = stringResource(R.string.memory_workbench_select_target),
        ) { to = it; localError = null }
        MemoryValueSlider(stringResource(R.string.memory_weight), weight, { weight = it }, enabled = !busy)
        OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.memory_description)) }, minLines = 3, maxLines = 6, enabled = !busy)
        localError?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
internal fun MemoryEdgeEditor(edge: Edge, graph: Graph, busy: Boolean, error: String?, onDismiss: () -> Unit, onSave: (String, Float, String) -> Unit) {
    var type by remember(edge.id) { mutableStateOf(edge.label.orEmpty()) }
    var weight by remember(edge.id) { mutableFloatStateOf(edge.weight) }
    var description by remember(edge.id) { mutableStateOf(edge.metadata["description"].orEmpty()) }
    var duplicate by remember { mutableStateOf(false) }
    MemoryModal(stringResource(R.string.memory_edit_link), onDismiss, footer = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
            Button(enabled = !busy, onClick = {
                duplicate = graph.edges.any { it.id != edge.id && it.sourceId == edge.sourceId && it.targetId == edge.targetId && it.label == type }
                if (!duplicate) onSave(type, weight, description)
            }) { Text(stringResource(R.string.memory_save)) }
        }
    }) {
        EdgeEndpoints(edge, graph)
        RelationPicker(type, enabled = !busy) { type = it; duplicate = false }
        MemoryValueSlider(stringResource(R.string.memory_weight), weight, { weight = it }, max(1f, edge.weight), enabled = !busy)
        OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.memory_description)) }, minLines = 3, maxLines = 6, enabled = !busy)
        if (duplicate) Text(stringResource(R.string.memory_workbench_duplicate_link), color = MaterialTheme.colorScheme.error)
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun RelationPicker(type: String, enabled: Boolean, onSelect: (String) -> Unit) {
    MemoryPicker(
        label = stringResource(R.string.memory_type),
        value = type,
        choices = memoryRelationOptions(type).map { MemoryChoice(it, stringResource(memoryRelationName(it)), it) },
        enabled = enabled,
        searchable = false,
        onSelect = onSelect,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MemoryDetails(memory: Memory, graph: Graph, focused: Boolean, onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onFocus: () -> Unit, onLocate: () -> Unit, onRelated: (String) -> Unit, onDocument: () -> Unit) {
    var deleting by remember(memory.uuid) { mutableStateOf(false) }
    val edges = graph.edges.filter { it.sourceId == memory.uuid || it.targetId == memory.uuid }
    val nodes = graph.nodes.associateBy { it.id }
    MemoryModal(stringResource(R.string.memory_details_title), onDismiss, footer = {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedIconButton(onClick = { deleting = true }) { Icon(Icons.Default.DeleteOutline, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error) }
            OutlinedButton(onClick = onEdit) { Icon(Icons.Default.Edit, null, Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.memory_edit)) }
            OutlinedButton(onClick = onFocus) { Text(stringResource(if (focused) R.string.memory_workbench_show_all else R.string.memory_workbench_focus)) }
            Button(onClick = onLocate) { Text(stringResource(R.string.memory_workbench_locate)) }
        }
    }) {
        Text(memory.title, style = MaterialTheme.typography.titleMedium)
        Text(memory.folderPath?.ifBlank { stringResource(R.string.memory_uncategorized) } ?: stringResource(R.string.memory_uncategorized), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer { Text(memory.content, style = MaterialTheme.typography.bodyMedium) }
        if (memory.isDocumentNode) OutlinedButton(onClick = onDocument, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Description, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.memory_workbench_edit_document)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            memory.tags.forEach { tag -> Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) { Text(tag.name, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall) } }
        }
        HorizontalDivider()
        Metadata(stringResource(R.string.memory_folder), memory.folderPath ?: stringResource(R.string.memory_uncategorized))
        Metadata(stringResource(R.string.memory_uuid), memory.uuid)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) { Metadata(stringResource(R.string.memory_source), memory.source) }
            Column(Modifier.weight(1f)) { Metadata(stringResource(R.string.memory_content_type), memory.contentType) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) { Metadata(stringResource(R.string.memory_credibility), "%.2f".format(memory.credibility)) }
            Column(Modifier.weight(1f)) { Metadata(stringResource(R.string.memory_importance), "%.2f".format(memory.importance)) }
        }
        Metadata(stringResource(R.string.memory_created_at), memoryDate(memory.createdAt))
        Metadata(stringResource(R.string.memory_updated_at), memoryDate(memory.updatedAt))
        HorizontalDivider()
        Text(stringResource(R.string.memory_workbench_relations, edges.size), style = MaterialTheme.typography.titleSmall)
        if (edges.isEmpty()) Text(stringResource(R.string.memory_workbench_empty_links), color = MaterialTheme.colorScheme.onSurfaceVariant)
        edges.forEach { edge ->
            val outgoing = edge.sourceId == memory.uuid
            val other = if (outgoing) edge.targetId else edge.sourceId
            TextButton(onClick = { onRelated(other) }, contentPadding = PaddingValues(vertical = 8.dp), modifier = Modifier.fillMaxWidth()) {
                Text((if (outgoing) "→ " else "← ") + stringResource(memoryRelationName(edge.label.orEmpty())), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(10.dp)); Text(nodes[other]?.label.orEmpty(), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
        }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text(stringResource(R.string.confirm_delete)) }, text = { Text(stringResource(R.string.memory_delete_warning)) }, confirmButton = { TextButton(onClick = { deleting = false; onDelete() }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
internal fun MemoryEdgeDetails(edge: Edge, graph: Graph, onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onTarget: () -> Unit) {
    var deleting by remember(edge.id) { mutableStateOf(false) }
    MemoryModal(stringResource(R.string.memory_link_details), onDismiss, footer = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            OutlinedIconButton(onClick = { deleting = true }) { Icon(Icons.Default.DeleteOutline, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error) }
            OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.memory_edit)) }
            Button(onClick = onTarget) { Text(stringResource(R.string.memory_to)) }
        }
    }) {
        EdgeEndpoints(edge, graph)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(stringResource(memoryRelationName(edge.label.orEmpty())), color = MaterialTheme.colorScheme.primary); Text("%.2f".format(edge.weight)) }
        SelectionContainer { Text(edge.label.orEmpty(), style = MaterialTheme.typography.labelMedium) }
        Text(stringResource(R.string.memory_description), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(edge.metadata["description"].orEmpty().ifEmpty { stringResource(R.string.memory_workbench_no_description) })
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text(stringResource(R.string.confirm_delete)) }, text = { Text(stringResource(R.string.memory_workbench_delete_link_confirmation)) }, confirmButton = { TextButton(onClick = { deleting = false; onDelete() }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun EdgeEndpoints(edge: Edge, graph: Graph) {
    Metadata(stringResource(R.string.memory_from), graph.nodes.firstOrNull { it.id == edge.sourceId }?.label.orEmpty())
    Text("↓", color = MaterialTheme.colorScheme.primary)
    Metadata(stringResource(R.string.memory_to), graph.nodes.firstOrNull { it.id == edge.targetId }?.label.orEmpty())
}

@Composable
private fun Metadata(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer { Text(value, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun memoryDate(date: java.util.Date): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(date)