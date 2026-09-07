package com.ai.assistance.operit.ui.features.tokenstats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.model.BillingMode
import com.ai.assistance.operit.data.stats.TokenStatsPriceDraft
import com.ai.assistance.operit.data.stats.TokenStatsPriceScope
import com.ai.assistance.operit.data.stats.TokenStatsPriceSetting
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 信息架构重构后弹窗全部回到 M3 默认配色（随主题明暗自动成立），
 * 主色不再作为容器色出现。
 */

internal fun datePickerMillisToLocalDate(utcMidnightMs: Long): java.time.LocalDate =
    Instant.ofEpochMilli(utcMidnightMs).atZone(java.time.ZoneOffset.UTC).toLocalDate()

internal fun customRangeInclusiveEnd(
    startDate: java.time.LocalDate,
    endDate: java.time.LocalDate,
    zone: ZoneId,
): com.ai.assistance.operit.data.stats.TokenStatsTimeRange {
    require(!endDate.isBefore(startDate)) { "end date must not be before start date" }
    val startMs = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
    val endMs = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return com.ai.assistance.operit.data.stats.TokenStatsTimeRanges.customRange(startMs, endMs)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TokenStatsDateRangeDialog(
    zone: ZoneId,
    maxRangeDays: Long,
    initialRange: com.ai.assistance.operit.data.stats.TokenStatsTimeRange?,
    onConfirm: (startMs: Long, endMs: Long) -> Boolean,
    onDismiss: () -> Unit,
) {
    var inlineError by remember { mutableStateOf<String?>(null) }
    val initialStartDateMillis = initialRange
        ?.startMs
        ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        ?.atStartOfDay(java.time.ZoneOffset.UTC)
        ?.toInstant()
        ?.toEpochMilli()
    val initialEndDateMillis = initialRange
        ?.endMs
        ?.minus(1L)
        ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        ?.atStartOfDay(java.time.ZoneOffset.UTC)
        ?.toInstant()
        ?.toEpochMilli()
    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStartDateMillis,
        initialSelectedEndDateMillis = initialEndDateMillis,
    )
    LaunchedEffect(
        pickerState.selectedStartDateMillis,
        pickerState.selectedEndDateMillis,
    ) {
        inlineError = null
    }

    val invalidRangeText = stringResource(R.string.token_stats_custom_range_invalid)
    val rangeTooLongText = stringResource(R.string.token_stats_custom_range_too_long)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 360.dp),
        confirmButton = {
            TextButton(
                enabled =
                    pickerState.selectedStartDateMillis != null &&
                        pickerState.selectedEndDateMillis != null,
                onClick = {
                    val start = pickerState.selectedStartDateMillis ?: return@TextButton
                    val end = pickerState.selectedEndDateMillis ?: return@TextButton
                    val range = customRangeInclusiveEnd(
                        datePickerMillisToLocalDate(start),
                        datePickerMillisToLocalDate(end),
                        zone,
                    )
                    val isUnchangedInitialRange = range == initialRange
                    inlineError =
                        when (validateCustomRange(range.startMs, range.endMs, zone, maxRangeDays)) {
                            CustomRangeValidation.INVALID_BOUNDS -> invalidRangeText
                            CustomRangeValidation.TOO_LONG ->
                                if (isUnchangedInitialRange) null else rangeTooLongText
                            CustomRangeValidation.VALID -> null
                        }
                    if (inlineError == null && onConfirm(range.startMs, range.endMs)) onDismiss()
                },
            ) {
                Text(stringResource(R.string.token_stats_custom_range_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    ) {
        Column {
            DateRangePicker(
                state = pickerState,
                title = {
                    Text(
                        text = stringResource(R.string.token_stats_date_range),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                    )
                },
                headline = {
                    Text(
                        text = formatDatePickerSelection(
                            pickerState.selectedStartDateMillis,
                            pickerState.selectedEndDateMillis,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                    )
                },
                showModeToggle = false,
            )
            inlineError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 24.dp, bottom = 12.dp),
                )
            }
        }
    }
}

private fun formatDatePickerSelection(startMillis: Long?, endMillis: Long?): String {
    if (startMillis == null) return ""
    val start = datePickerMillisToLocalDate(startMillis).format(datePickerSelectionFormatter)
    if (endMillis == null) return start
    val end = datePickerMillisToLocalDate(endMillis).format(datePickerSelectionFormatter)
    return "$start - $end"
}

private val datePickerSelectionFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.getDefault())

@Composable
internal fun PriceSettingsDialog(
    existing: TokenStatsPriceSetting?,
    initialDraft: TokenStatsPriceDraft,
    configurationName: String?,
    onSave: (TokenStatsPriceDraft) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val scope = initialDraft.scope
    val provider = initialDraft.provider
    val model = initialDraft.model
    val configId = initialDraft.configId.orEmpty()
    var billingMode by remember(existing, initialDraft) {
        mutableStateOf(existing?.billingMode ?: initialDraft.billingMode)
    }
    var currency by remember(existing, initialDraft) {
        mutableStateOf(existing?.currency ?: initialDraft.currency)
    }
    var inputPrice by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(existing?.inputPricePerMillion ?: initialDraft.inputPricePerMillion)
        )
    }
    var cachedInputPrice by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(
                existing?.cachedInputPricePerMillion ?: initialDraft.cachedInputPricePerMillion
            )
        )
    }
    var cacheWritePrice by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(
                existing?.cacheWritePricePerMillion ?: initialDraft.cacheWritePricePerMillion
            )
        )
    }
    var outputPrice by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(existing?.outputPricePerMillion ?: initialDraft.outputPricePerMillion)
        )
    }
    var pricePerRequest by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(existing?.pricePerRequest ?: initialDraft.pricePerRequest)
        )
    }
    val priceFields =
        if (billingMode == BillingMode.TOKEN) {
            listOf(inputPrice, cachedInputPrice, cacheWritePrice, outputPrice)
        } else {
            listOf(pricePerRequest)
        }
    val allPricesValid =
        priceFields.all { raw ->
            raw.isBlank() ||
                raw.toDoubleOrNull()?.let { it.isFinite() && it >= 0.0 } == true
        }
    val targetValid = scope != TokenStatsPriceScope.CONFIG || configId.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.token_stats_pricing_edit)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "$provider · $model",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (scope == TokenStatsPriceScope.CONFIG) {
                    Text(
                        text = configurationName.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = billingMode == BillingMode.TOKEN,
                        onClick = {
                            if (billingMode != BillingMode.TOKEN) {
                                pricePerRequest = ""
                                billingMode = BillingMode.TOKEN
                            }
                        },
                        label = { Text(stringResource(R.string.settings_billing_mode_token)) },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = billingMode == BillingMode.COUNT,
                        onClick = {
                            if (billingMode != BillingMode.COUNT) {
                                inputPrice = ""
                                cachedInputPrice = ""
                                cacheWritePrice = ""
                                outputPrice = ""
                                billingMode = BillingMode.COUNT
                            }
                        },
                        label = { Text(stringResource(R.string.settings_billing_mode_count)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = currency == PricingCurrency.CNY,
                        onClick = { currency = PricingCurrency.CNY },
                        label = { Text(stringResource(R.string.token_stats_currency_cny)) },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = currency == PricingCurrency.USD,
                        onClick = { currency = PricingCurrency.USD },
                        label = { Text(stringResource(R.string.token_stats_currency_usd)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                HorizontalDivider()
                if (billingMode == BillingMode.TOKEN) {
                    PriceField(
                        label = stringResource(R.string.token_stats_pricing_input),
                        value = inputPrice,
                        onChange = { inputPrice = it },
                    )
                    PriceField(
                        label = stringResource(R.string.token_stats_pricing_cached),
                        value = cachedInputPrice,
                        onChange = { cachedInputPrice = it },
                    )
                    PriceField(
                        label = stringResource(R.string.token_stats_pricing_cache_write),
                        value = cacheWritePrice,
                        onChange = { cacheWritePrice = it },
                    )
                    PriceField(
                        label = stringResource(R.string.token_stats_pricing_output),
                        value = outputPrice,
                        onChange = { outputPrice = it },
                    )
                } else {
                    PriceField(
                        label = stringResource(R.string.token_stats_pricing_per_request),
                        value = pricePerRequest,
                        onChange = { pricePerRequest = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = targetValid && allPricesValid,
                onClick = {
                    val parse = { raw: String -> raw.trim().toDoubleOrNull() }
                    onSave(
                        TokenStatsPriceDraft(
                            scope = scope,
                            provider = provider,
                            model = model,
                            configId = configId.takeIf { scope == TokenStatsPriceScope.CONFIG },
                            billingMode = billingMode,
                            currency = currency,
                            inputPricePerMillion =
                                if (billingMode == BillingMode.TOKEN) parse(inputPrice) else null,
                            cachedInputPricePerMillion =
                                if (billingMode == BillingMode.TOKEN) {
                                    parse(cachedInputPrice)
                                } else {
                                    null
                                },
                            cacheWritePricePerMillion =
                                if (billingMode == BillingMode.TOKEN) {
                                    parse(cacheWritePrice)
                                } else {
                                    null
                                },
                            outputPricePerMillion =
                                if (billingMode == BillingMode.TOKEN) parse(outputPrice) else null,
                            pricePerRequest =
                                if (billingMode == BillingMode.COUNT) {
                                    parse(pricePerRequest)
                                } else {
                                    null
                                },
                        )
                    )
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.settings_save))
            }
        },
        dismissButton = {
            Row {
                if (existing != null && onDelete != null) {
                    TextButton(
                        onClick = {
                            onDelete()
                            onDismiss()
                        }
                    ) {
                        Text(
                            stringResource(R.string.token_stats_pricing_delete),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
                TextButton(
                    onClick = onDismiss,
                ) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        },
    )
}

@Composable
private fun PriceField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun formatEditablePrice(value: Double?): String =
    value?.let { String.format(Locale.US, "%.6f", it).trimEnd('0').trimEnd('.') } ?: ""
