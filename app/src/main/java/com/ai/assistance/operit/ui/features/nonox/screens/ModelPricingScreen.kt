package com.ai.assistance.operit.ui.features.nonox.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.ui.features.nonox.viewmodel.NonOXViewModel
import com.ai.nonoassistance.provider.PricingConfig

/**
 * Model pricing display screen.
 * Shows pricing data from ModelPricingService with filtering and comparison.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPricingScreen(
    onGoBack: () -> Unit,
    viewModel: NonOXViewModel = viewModel()
) {
    val pricingState by viewModel.pricingState.collectAsState()

    val filteredModels = if (pricingState.selectedProvider != null) {
        pricingState.pricingData.filter { it.value.providerId == pricingState.selectedProvider }
    } else {
        pricingState.pricingData
    }

    val providers = pricingState.pricingData.values.map { it.providerId }.distinct().sorted()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model Pricing") },
                navigationIcon = {
                    IconButton(onClick = onGoBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Model Pricing",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Pricing data fetched from remote configuration.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Provider filter chips
            if (providers.isNotEmpty()) {
                Text(
                    text = "Filter by Provider",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = pricingState.selectedProvider == null,
                        onClick = { viewModel.selectProvider(null) },
                        label = { Text("All") }
                    )
                    providers.forEach { provider ->
                        FilterChip(
                            selected = pricingState.selectedProvider == provider,
                            onClick = { viewModel.selectProvider(provider) },
                            label = { Text(provider) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (pricingState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (pricingState.error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Error loading pricing data",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = pricingState.error ?: "Unknown error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.refreshPricing() }) {
                            Text("Retry")
                        }
                    }
                }
            } else if (filteredModels.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No pricing data available",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Pull down to refresh or check your network connection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                    }
                }
            } else {
                // Pricing cards
                filteredModels.forEach { (modelId, pricing) ->
                    PricingCard(modelId = modelId, pricing = pricing)
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Refresh button
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { viewModel.refreshPricing() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Refresh Pricing")
                }
            }
        }
    }
}

@Composable
private fun PricingCard(
    modelId: String,
    pricing: PricingConfig.ModelPricing
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pricing.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = pricing.providerId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (pricing.isDeprecated) {
                    AssistChip(
                        onClick = { },
                        label = { Text("Deprecated") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pricing details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PricingColumn(
                    label = "Input",
                    value = "$${String.format("%.2f", pricing.inputPerMillionTokens)}",
                    sublabel = "/ 1M tokens"
                )
                PricingColumn(
                    label = "Output",
                    value = "$${String.format("%.2f", pricing.outputPerMillionTokens)}",
                    sublabel = "/ 1M tokens"
                )
                PricingColumn(
                    label = "Context",
                    value = "${pricing.contextWindow / 1000}K",
                    sublabel = "tokens"
                )
            }

            // Capabilities
            if (pricing.supportsToolCalling || pricing.supportsThinking) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (pricing.supportsToolCalling) {
                        AssistChip(
                            onClick = { },
                            label = { Text("Tools") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Build,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                    if (pricing.supportsThinking) {
                        AssistChip(
                            onClick = { },
                            label = { Text("Thinking") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Psychology,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PricingColumn(
    label: String,
    value: String,
    sublabel: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = sublabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
