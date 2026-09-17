package com.ai.assistance.operit.ui.features.settings.keypool

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ApiKeyAvailabilityStatus
import com.ai.assistance.operit.data.model.ApiKeyInfo

internal enum class ApiKeyPoolSelectionPreset {
    ALL,
    AVAILABLE,
    UNAVAILABLE,
    UNTESTED,
    DISABLED,
}

internal enum class ApiKeyPoolSelectionMode {
    EXPORT,
    DELETE,
}

internal fun ApiKeyInfo.poolSelectionPreset(): ApiKeyPoolSelectionPreset {
    return when {
        !isEnabled -> ApiKeyPoolSelectionPreset.DISABLED
        availabilityStatus == ApiKeyAvailabilityStatus.AVAILABLE ->
            ApiKeyPoolSelectionPreset.AVAILABLE
        availabilityStatus == ApiKeyAvailabilityStatus.UNAVAILABLE ->
            ApiKeyPoolSelectionPreset.UNAVAILABLE
        else -> ApiKeyPoolSelectionPreset.UNTESTED
    }
}

internal fun List<ApiKeyInfo>.idsForPreset(preset: ApiKeyPoolSelectionPreset): Set<String> {
    return when (preset) {
        ApiKeyPoolSelectionPreset.ALL -> map { it.id }.toSet()
        else -> filter { it.poolSelectionPreset() == preset }.map { it.id }.toSet()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ApiKeyPoolSelectionSheet(
    mode: ApiKeyPoolSelectionMode,
    keys: List<ApiKeyInfo>,
    onDismiss: () -> Unit,
    onConfirm: (List<ApiKeyInfo>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(keys.map { it.id }) {
        selectedIds = selectedIds.filter { id -> keys.any { it.id == id } }.toSet()
    }

    val selectedKeys = remember(keys, selectedIds) { keys.filter { it.id in selectedIds } }
    val destructive = mode == ApiKeyPoolSelectionMode.DELETE
    val selectedChipColor =
        if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val selectedChipLabelColor =
        if (destructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text =
                        stringResource(
                            if (destructive) {
                                R.string.api_key_pool_delete_keys
                            } else {
                                R.string.export_keys
                            }
                        ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text =
                        stringResource(
                            R.string.api_key_pool_selected_count,
                            selectedIds.size,
                            keys.size
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ApiKeyPoolSelectionPreset.entries.forEach { preset ->
                    val targetIds = keys.idsForPreset(preset)
                    val selected = selectedIds == targetIds && targetIds.isNotEmpty()
                    FilterChip(
                        selected = selected,
                        onClick = {
                            selectedIds =
                                if (selected || targetIds.isEmpty()) {
                                    emptySet()
                                } else {
                                    targetIds
                                }
                        },
                        enabled = targetIds.isNotEmpty() || preset == ApiKeyPoolSelectionPreset.ALL,
                        label = { Text(stringResource(preset.labelRes())) },
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = selectedChipColor,
                                selectedLabelColor = selectedChipLabelColor
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
            ) {
                items(keys, key = { it.id }) { keyInfo ->
                    ApiKeyPoolSelectableRow(
                        keyInfo = keyInfo,
                        checked = keyInfo.id in selectedIds,
                        destructive = destructive,
                        onCheckedChange = { checked ->
                            selectedIds =
                                if (checked) {
                                    selectedIds + keyInfo.id
                                } else {
                                    selectedIds - keyInfo.id
                                }
                        }
                    )
                }
            }

            if (destructive) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.api_key_pool_delete_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel_action))
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = { onConfirm(selectedKeys) },
                    enabled = selectedKeys.isNotEmpty(),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.widthIn(min = 148.dp),
                    colors =
                        if (destructive) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        }
                ) {
                    Text(
                        text =
                            stringResource(
                                if (destructive) {
                                    R.string.api_key_pool_delete_n
                                } else {
                                    R.string.api_key_pool_export_n
                                },
                                selectedKeys.size
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun ApiKeyPoolSelectableRow(
    keyInfo: ApiKeyInfo,
    checked: Boolean,
    destructive: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val statusColor = keyInfo.statusColor()
    val statusLabel = keyInfo.statusLabel()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(if (keyInfo.isEnabled) 1f else 0.62f)
                .clickable { onCheckedChange(!checked) }
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                CheckboxDefaults.colors(
                    checkedColor =
                        if (destructive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                )
        )
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .background(statusColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = keyInfo.name.ifBlank { maskApiKey(keyInfo.key) },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = maskApiKey(keyInfo.key),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .background(statusColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor
            )
        }
    }
}

@Composable
internal fun ApiKeyInfo.statusColor(): Color {
    return when {
        !isEnabled -> MaterialTheme.colorScheme.outline
        availabilityStatus == ApiKeyAvailabilityStatus.AVAILABLE -> Color(0xFF4CAF50)
        availabilityStatus == ApiKeyAvailabilityStatus.UNAVAILABLE ->
            MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
internal fun ApiKeyInfo.statusLabel(): String {
    return stringResource(
        when {
            !isEnabled -> R.string.api_key_pool_status_disabled
            availabilityStatus == ApiKeyAvailabilityStatus.AVAILABLE ->
                R.string.api_key_pool_status_available
            availabilityStatus == ApiKeyAvailabilityStatus.UNAVAILABLE ->
                R.string.api_key_pool_status_unavailable
            else -> R.string.api_key_pool_status_untested
        }
    )
}

private fun ApiKeyPoolSelectionPreset.labelRes(): Int {
    return when (this) {
        ApiKeyPoolSelectionPreset.ALL -> R.string.api_key_pool_filter_all
        ApiKeyPoolSelectionPreset.AVAILABLE -> R.string.api_key_pool_status_available
        ApiKeyPoolSelectionPreset.UNAVAILABLE -> R.string.api_key_pool_status_unavailable
        ApiKeyPoolSelectionPreset.UNTESTED -> R.string.api_key_pool_filter_untested
        ApiKeyPoolSelectionPreset.DISABLED -> R.string.api_key_pool_status_disabled
    }
}
