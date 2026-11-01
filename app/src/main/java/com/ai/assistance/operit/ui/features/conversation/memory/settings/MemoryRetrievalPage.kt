package com.ai.assistance.operit.ui.features.conversation.memory.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.ThemeScriptOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.DimensionCount
import com.ai.assistance.operit.data.model.EmbeddingDimensionUsage
import com.ai.assistance.operit.data.model.EmbeddingRebuildProgress
import com.ai.assistance.operit.data.model.MemorySearchConfig
import kotlin.math.roundToInt

@Composable
internal fun MemoryRetrievalPage(
    config: MemorySearchConfig,
    policy: MemorySettingsPolicy,
    savedPolicy: MemorySettingsPolicy,
    embeddingModelName: String?,
    dimensionUsage: EmbeddingDimensionUsage,
    rebuildProgress: EmbeddingRebuildProgress,
    isRebuilding: Boolean,
    query: String,
    threshold: Float,
    onConfigChange: (MemorySearchConfig) -> Unit,
    onPolicyChange: (MemorySettingsPolicy) -> Unit,
    onQueryChange: (String) -> Unit,
    onThresholdChange: (Float) -> Unit,
    onConfigureEmbedding: () -> Unit,
    onRebuild: () -> Unit,
    onSimulateSearch: () -> Unit,
) {
    MemorySettingsSection(stringResource(R.string.memory_search_weight_section)) {
        MemorySettingsSlider(stringResource(R.string.memory_search_keyword_weight), config.keywordWeight, "%.2f".format(config.keywordWeight), 0f..20f) {
            onConfigChange(config.copy(keywordWeight = it))
        }
        MemorySettingsSlider(stringResource(R.string.memory_search_tag_weight), config.tagWeight, "%.2f".format(config.tagWeight), 0f..20f) {
            onConfigChange(config.copy(tagWeight = it))
        }
        MemorySettingsSlider(stringResource(R.string.memory_search_vector_weight), config.vectorWeight, "%.2f".format(config.vectorWeight), 0f..2f) {
            onConfigChange(config.copy(vectorWeight = it))
        }
        MemorySettingsSlider(
            stringResource(R.string.memory_search_min_semantic_similarity),
            config.minSemanticSimilarity,
            "%.2f".format(config.minSemanticSimilarity),
            0f..1f,
            enabled = config.vectorWeight > 0f && policy.cloudEmbeddingEnabled && embeddingModelName != null,
        ) { onConfigChange(config.copy(minSemanticSimilarity = it)) }
        MemorySettingsSlider(stringResource(R.string.memory_search_edge_weight), config.edgeWeight, "%.2f".format(config.edgeWeight), 0f..2f) {
            onConfigChange(config.copy(edgeWeight = it))
        }
    }
    MemorySettingsSection(stringResource(R.string.function_type_memory_embedding)) {
        MemorySettingsSwitch(
            title = stringResource(R.string.memory_settings_cloud_embedding),
            description = stringResource(R.string.memory_settings_cloud_embedding_hint),
            checked = policy.cloudEmbeddingEnabled,
            onCheckedChange = { onPolicyChange(policy.copy(cloudEmbeddingEnabled = it)) },
        )
        Text(embeddingModelName ?: stringResource(R.string.not_configured), style = MaterialTheme.typography.bodyMedium)
        if (policy.cloudEmbeddingEnabled && embeddingModelName == null) {
            Text(
                stringResource(R.string.memory_embedding_binding_required),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        MemorySettingsHint(stringResource(R.string.memory_embedding_function_settings_desc))
        OutlinedButton(onClick = onConfigureEmbedding, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.functional_model_config_title)) }
    }
    MemorySettingsSection(stringResource(R.string.memory_embedding_dimension_usage)) {
        DimensionUsageBlock(
            title = stringResource(R.string.memory_embedding_dimension_memory_summary, dimensionUsage.memoryTotal, dimensionUsage.memoryMissing),
            dimensions = dimensionUsage.memoryDimensions,
        )
        DimensionUsageBlock(
            title = stringResource(R.string.memory_embedding_dimension_chunk_summary, dimensionUsage.chunkTotal, dimensionUsage.chunkMissing),
            dimensions = dimensionUsage.chunkDimensions,
        )
        MemorySettingsHint(stringResource(R.string.memory_settings_rebuild_saved_hint))
        OutlinedButton(
            onClick = onRebuild,
            enabled = !isRebuilding && savedPolicy.cloudEmbeddingEnabled && policy.cloudEmbeddingEnabled && embeddingModelName != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.memory_embedding_rebuild_action)) }
        if (isRebuilding || rebuildProgress.total > 0) {
            val fraction = rebuildProgress.fraction.coerceIn(0f, 1f)
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            Text("${(fraction * 100f).roundToInt()}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            MemorySettingsHint(stringResource(
                R.string.memory_embedding_rebuild_progress,
                rebuildProgress.processed, rebuildProgress.total, rebuildProgress.failed, embeddingStageText(rebuildProgress.currentStage),
            ))
        }
    }
    MemorySettingsSection(stringResource(R.string.memory_search_debug_tools)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.memory_search_simulation_query)) },
            minLines = 1,
            maxLines = 3,
        )
        MemorySettingsSlider(stringResource(R.string.memory_settings_threshold), threshold, "%.4f".format(threshold), 0f..1f, onValueChange = onThresholdChange)
        MemorySettingsHint(stringResource(R.string.memory_settings_simulation_hint))
        OutlinedButton(onClick = onSimulateSearch, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.memory_search_simulation_open)) }
    }
}

@Composable
private fun DimensionUsageBlock(title: String, dimensions: List<DimensionCount>) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MemorySettingsHint(title)
        Text(
            text = if (dimensions.isEmpty()) stringResource(R.string.memory_embedding_dimension_none)
            else dimensions.joinToString("  ·  ") { context.getString(R.string.memory_embedding_dimension_item, it.dimension, it.count) },
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun embeddingStageText(stage: String): String = when (stage) {
    "preparing" -> stringResource(R.string.memory_embedding_stage_preparing)
    "memory_embedding" -> stringResource(R.string.memory_embedding_stage_memory_embedding)
    "chunk_embedding" -> stringResource(R.string.memory_embedding_stage_chunk_embedding)
    "memory_index" -> stringResource(R.string.memory_embedding_stage_memory_index)
    "chunk_index" -> stringResource(R.string.memory_embedding_stage_chunk_index)
    "done" -> stringResource(R.string.memory_embedding_stage_done)
    else -> stage
}