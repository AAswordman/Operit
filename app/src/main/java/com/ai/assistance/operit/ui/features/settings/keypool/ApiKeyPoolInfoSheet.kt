package com.ai.assistance.operit.ui.features.settings.keypool

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ApiKeyAttemptRecordEntity
import com.ai.assistance.operit.data.model.ApiKeyInfo
import com.ai.assistance.operit.data.stats.ApiKeyAttemptRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val INFO_RECORD_LIMIT = 2000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ApiKeyPoolInfoSheet(
    configId: String,
    keys: List<ApiKeyInfo>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var records by remember { mutableStateOf<List<ApiKeyAttemptRecordEntity>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val nowMs = remember { System.currentTimeMillis() }

    LaunchedEffect(configId, keys.map { it.id }) {
        loading = true
        records =
            runCatching {
                ApiKeyAttemptRepository.getInstance(context)
                    .recentForConfig(configId, sinceMs = 0L, limit = INFO_RECORD_LIMIT)
            }.getOrDefault(emptyList())
        loading = false
    }

    val rows = remember(keys, records, nowMs) {
        buildApiKeyPoolInfoRows(keys, records, nowMs)
    }

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
            Text(
                text = stringResource(R.string.api_key_pool_info_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.api_key_pool_info_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            when {
                loading -> {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
                keys.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.api_key_pool_empty_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 480.dp)
                    ) {
                        items(rows, key = { it.key.id }) { row ->
                            ApiKeyPoolInfoRowItem(row = row, nowMs = nowMs)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(R.string.common_close))
            }
        }
    }
}

@Composable
private fun ApiKeyPoolInfoRowItem(
    row: ApiKeyPoolInfoRow,
    nowMs: Long,
) {
    val statusColor = row.key.statusColor()
    val statusLabel =
        if (row.isRecent) {
            stringResource(R.string.api_key_pool_info_recent)
        } else {
            row.key.statusLabel()
        }
    val chipColor =
        if (row.isRecent) {
            Color(0xFF4CAF50)
        } else {
            statusColor
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(if (row.key.isEnabled) 1f else 0.62f)
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = row.index.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.key.name.ifBlank { maskApiKey(row.key.key) },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = maskApiKey(row.key.key),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatInfoLine(row, nowMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .background(chipColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelMedium,
                color = chipColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun formatInfoLine(row: ApiKeyPoolInfoRow, nowMs: Long): String {
    if (row.lastOccurredAtMs == null || row.lastSuccess == null) {
        return stringResource(R.string.api_key_pool_info_never)
    }
    val whenText = formatRelativeTime(row.lastOccurredAtMs, nowMs)
    return if (row.lastSuccess) {
        stringResource(
            R.string.api_key_pool_info_line_success,
            whenText,
            row.successCount24h,
            row.failCount24h
        )
    } else if (row.lastHttpStatus != null) {
        stringResource(
            R.string.api_key_pool_info_line_failure_status,
            whenText,
            row.lastHttpStatus,
            row.successCount24h,
            row.failCount24h
        )
    } else {
        stringResource(
            R.string.api_key_pool_info_line_failure,
            whenText,
            row.successCount24h,
            row.failCount24h
        )
    }
}

@Composable
private fun formatRelativeTime(occurredAtMs: Long, nowMs: Long): String {
    val delta = (nowMs - occurredAtMs).coerceAtLeast(0L)
    val minutes = delta / 60_000L
    val days = delta / (24L * 60L * 60L * 1000L)
    return when {
        minutes < 1L -> stringResource(R.string.api_key_pool_info_just_now)
        minutes < 60L -> stringResource(R.string.api_key_pool_info_minutes_ago, minutes.toInt())
        days >= 1L -> stringResource(R.string.api_key_pool_info_days_ago, days.toInt())
        else -> {
            val zone = ZoneId.systemDefault()
            val occurredDate = Instant.ofEpochMilli(occurredAtMs).atZone(zone).toLocalDate()
            if (occurredDate == LocalDate.now(zone)) {
                DateTimeFormatter.ofPattern("HH:mm")
                    .format(Instant.ofEpochMilli(occurredAtMs).atZone(zone))
            } else {
                DateTimeFormatter.ofPattern("MM-dd HH:mm")
                    .format(Instant.ofEpochMilli(occurredAtMs).atZone(zone))
            }
        }
    }
}