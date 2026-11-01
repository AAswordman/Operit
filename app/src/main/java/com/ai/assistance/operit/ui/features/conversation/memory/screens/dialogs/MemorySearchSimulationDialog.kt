package com.ai.assistance.operit.ui.features.conversation.memory.screens.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog
import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.ThemeScriptOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.MemorySearchDebugInfo

@Composable
fun MemorySearchSimulationDialog(
    query: String,
    isRunning: Boolean,
    result: MemorySearchDebugInfo?,
    error: String?,
    onQueryChange: (String) -> Unit,
    onRun: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.fillMaxHeight(0.9f),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.memory_search_simulation_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.memory_search_simulation_query)) },
                    minLines = 3
                )

                if (isRunning) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }

                if (!error.isNullOrBlank()) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (result != null) {
                    SummaryCard(result = result)
                    TokensCard(result = result)
                    CandidatesCard(result = result)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onRun,
                enabled = !isRunning
            ) {
                Text(stringResource(R.string.memory_search_simulation_run))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.memory_close))
            }
        }
    )
}

@Composable
private fun SummaryCard(result: MemorySearchDebugInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.memory_search_simulation_summary),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(
                    R.string.memory_search_simulation_scope_count,
                    result.memoriesInScopeCount
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(
                    R.string.memory_search_simulation_match_count,
                    result.keywordMatchesCount,
                    result.tagMatchesCount,
                    result.reverseContainmentMatchesCount,
                    result.semanticMatchesCount
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(
                    R.string.memory_search_simulation_result_count,
                    result.scoredCount,
                    result.passedThresholdCount,
                    result.relevanceThreshold
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(
                    R.string.memory_search_simulation_weight_line,
                    result.effectiveKeywordWeight,
                    result.effectiveTagWeight,
                    result.effectiveSemanticWeight,
                    result.effectiveEdgeWeight,
                    result.semanticKeywordNormFactor
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun TokensCard(result: MemorySearchDebugInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.memory_search_simulation_fragments),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(
                    R.string.memory_search_simulation_raw_keywords,
                    result.keywords.size,
                    result.keywords.joinToString(" | ")
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(
                    R.string.memory_search_simulation_lexical_tokens,
                    result.lexicalTokens.size,
                    result.lexicalTokens.joinToString(" | ")
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun CandidatesCard(result: MemorySearchDebugInfo) {
    val candidates = result.candidates.take(30)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.memory_search_simulation_top_candidates,
                    candidates.size,
                    result.candidates.size
                ),
                style = MaterialTheme.typography.titleSmall
            )
            candidates.forEachIndexed { index, candidate ->
                val marker = if (candidate.passedThreshold) "PASS" else "DROP"
                Text(
                    text = stringResource(
                        R.string.memory_search_simulation_candidate_title,
                        index + 1,
                        marker,
                        candidate.title
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(
                        R.string.memory_search_simulation_candidate_scores,
                        candidate.keywordScore,
                        candidate.tagScore,
                        candidate.reverseContainmentScore,
                        candidate.semanticScore,
                        candidate.edgeScore,
                        candidate.totalScore,
                        candidate.matchedKeywordTokenCount
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
