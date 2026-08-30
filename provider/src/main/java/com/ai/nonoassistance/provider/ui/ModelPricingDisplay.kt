package com.ai.nonoassistance.provider.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.nonoassistance.provider.ModelPricing

/**
 * Displays model pricing information in a compact format.
 *
 * Shows:
 * - Input price per 1M tokens
 * - Output price per 1M tokens
 * - Currency
 *
 * Used in model selection dropdown/list to help users compare costs.
 */
@Composable
fun ModelPricingDisplay(
    pricing: ModelPricing?,
    modifier: Modifier = Modifier
) {
    if (pricing == null) {
        Text(
            text = "Price: Unknown",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier.padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Input:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$${formatPrice(pricing.inputPerMillionTokens)}/1M tokens",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Output:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$${formatPrice(pricing.outputPerMillionTokens)}/1M tokens",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun formatPrice(price: Double): String {
    return when {
        price < 0.01 -> String.format("%.4f", price)
        price < 1.0 -> String.format("%.3f", price)
        else -> String.format("%.2f", price)
    }
}
